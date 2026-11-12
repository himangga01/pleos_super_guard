-- 플레오스 가드 PostgreSQL 스키마 제안 v3.1 (2026-11-12). 실제 DB에 적용하지 않았습니다.
-- 이전 판은 history/2026-11-12-v3.0/database.sql(v3), history/2026-11-12-v2.1/database.sql(v2.1)에 그대로 보관합니다.
-- 상태·열거형 이름은 1차 비판 반영 결정 9장(계약 고정 사항)·2차 비판 결정(D1~D6·H7·H8)과 api.openapi.json 3.1.0 enum과 같아야 합니다.
-- validate_contracts.py가 OpenAPI enum과 아래 CHECK 목록이 같은지 정적으로 확인합니다.
-- UUID는 앱이 만듭니다. 확장 기능·비밀값이 필요 없습니다.
-- 인증, 동의 유효성, 프로필 승인, 암호화 키, 작업 실행은 앱 서버 책임입니다.
-- [향후] 입력(경고등 상태, 원본 녹음·영상, IMU·충격, 가속 페달, 기어, 주차 브레이크, 타이어 현재 공기압, 시동, 비상등)은 열·값으로 두지 않습니다.
-- v3.1 요점: 시험 입력이 섞인 사건은 test_mode=true·모의 발송만(D2), 같은 차량 열린 알림 하나는 2시간 창 안에서만(D4),
-- 위치 제공 기록은 삭제 대신 익명화(D5), QR 연결은 차 화면 확인 뒤 확정(D6), 게스트 위치 동의는 이번 trip만·정차 중에만(D3).
BEGIN;

-- 계정. 차량 등록 직후에는 차량 설치용 임시 계정이 소유자이며,
-- 휴대폰 연결이 차 화면 확인(vehicle-confirm)까지 끝나면 휴대폰 소유자 계정의 새 등록으로 옮깁니다(앱 서버 책임, D6).
CREATE TABLE app_users (
  user_id uuid PRIMARY KEY,
  created_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE TABLE vehicle_profiles (
  profile_id text PRIMARY KEY,
  model_code text NOT NULL,
  model_year integer NOT NULL CHECK (model_year BETWEEN 2000 AND 2100),
  powertrain text NOT NULL CHECK (powertrain IN ('ICE','HEV','PHEV','BEV','FCEV','UNKNOWN')),
  specification jsonb NOT NULL DEFAULT '{}'::jsonb,
  pleos_fitment_verified boolean NOT NULL DEFAULT false,
  software_range jsonb NOT NULL DEFAULT '{}'::jsonb,
  official_source_url text,
  reviewed_at timestamptz
);

-- 차량 등록. 원본 VIN은 받지 않고, 차량이 보낸 VIN SHA-256을 서버 비밀 키로 HMAC한 값만 둡니다.
-- 소유자 이전(D6)은 owner_id를 바꾸지 않습니다. 새 소유자에게 새 vehicle_id 등록을 만들고 이전 등록은 revoked_at으로 닫습니다.
-- 그래서 (owner_id, vehicle_id) 복합 외래키로 묶인 이전 소유자의 기록·프로필·기기는 새 소유자에게 옮겨지지 않습니다.
CREATE TABLE vehicles (
  vehicle_id uuid PRIMARY KEY,
  client_vehicle_id uuid NOT NULL,
  owner_id uuid NOT NULL REFERENCES app_users(user_id),
  profile_id text REFERENCES vehicle_profiles(profile_id),
  vin_hash text CHECK (vin_hash ~ '^[a-f0-9]{64}$'),
  vin_key_version smallint CHECK (vin_key_version >= 1),
  model_display text NOT NULL CHECK (char_length(model_display) BETWEEN 1 AND 60),
  claimed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  revoked_at timestamptz,
  UNIQUE (owner_id, vehicle_id),
  UNIQUE (client_vehicle_id),
  CHECK ((vin_hash IS NULL) = (vin_key_version IS NULL))
);

-- 공유 차량의 '이번 운전자'. GUEST·UNKNOWN은 계정이 없을 수 있고 자동 위치 발송을 하지 않습니다.
CREATE TABLE driver_profiles (
  driver_profile_id uuid PRIMARY KEY,
  owner_id uuid NOT NULL,
  vehicle_id uuid NOT NULL,
  user_id uuid REFERENCES app_users(user_id),
  driver_mode text NOT NULL CHECK (driver_mode IN ('OWNER','REGISTERED_DRIVER','GUEST','UNKNOWN')),
  display_name text NOT NULL CHECK (char_length(display_name) BETWEEN 1 AND 30),
  created_at timestamptz NOT NULL DEFAULT now(),
  revoked_at timestamptz,
  UNIQUE (vehicle_id, driver_profile_id),
  UNIQUE (driver_profile_id, driver_mode),
  FOREIGN KEY (owner_id, vehicle_id) REFERENCES vehicles(owner_id, vehicle_id),
  CHECK (driver_mode IN ('GUEST','UNKNOWN') OR user_id IS NOT NULL)
);

CREATE TABLE devices (
  device_id uuid PRIMARY KEY,
  owner_id uuid NOT NULL,
  vehicle_id uuid NOT NULL,
  device_role text NOT NULL CHECK (device_role IN ('IVI','PHONE')),
  public_key_ref text,
  capability_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,
  paired_at timestamptz,
  last_seen_at timestamptz,
  revoked_at timestamptz,
  wipe_requested_at timestamptz,
  wipe_confirmed_at timestamptz,
  UNIQUE (owner_id, vehicle_id, device_id),
  CHECK (wipe_requested_at IS NULL OR revoked_at IS NOT NULL),
  CHECK (wipe_confirmed_at IS NULL OR wipe_requested_at IS NOT NULL),
  FOREIGN KEY (owner_id, vehicle_id) REFERENCES vehicles(owner_id, vehicle_id)
);

-- 소유자 이전(D6). 차 화면에서 시작하면 현재 소유자에게 알리고 이의 기간을 거칩니다. 차량마다 진행 중 이전은 하나입니다.
CREATE TABLE ownership_transfers (
  transfer_id uuid PRIMARY KEY,
  client_transfer_id uuid NOT NULL UNIQUE,
  from_vehicle_id uuid NOT NULL REFERENCES vehicles(vehicle_id),
  to_vehicle_id uuid REFERENCES vehicles(vehicle_id),
  initiated_by text NOT NULL CHECK (initiated_by IN ('CURRENT_OWNER','VEHICLE_SCREEN')),
  state text NOT NULL CHECK (state IN ('PENDING_NEW_OWNER','OBJECTION_WINDOW','COMPLETED','CANCELLED','EXPIRED')),
  reason text NOT NULL CHECK (reason IN ('SALE','FAMILY','OTHER')),
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  objection_window_ends_at timestamptz,
  completed_at timestamptz,
  CHECK (expires_at <= created_at + interval '7 days'),
  CHECK ((initiated_by = 'VEHICLE_SCREEN') = (objection_window_ends_at IS NOT NULL)),
  CHECK ((state = 'COMPLETED') = (completed_at IS NOT NULL AND to_vehicle_id IS NOT NULL)),
  CHECK (to_vehicle_id IS NULL OR to_vehicle_id <> from_vehicle_id)
);

-- 휴대폰 연결: 정차 중 차량 화면의 일회용 QR 한 번 + 서버 중계(D21). 코드는 해시만 둡니다.
-- QR을 먼저 찍은 사람이 소유자가 되지 않도록 휴대폰 확인 뒤 차 화면 [연결]이 있어야 CONFIRMED입니다(D6).
CREATE TABLE device_pairings (
  pairing_id uuid PRIMARY KEY,
  vehicle_id uuid NOT NULL REFERENCES vehicles(vehicle_id),
  ivi_device_id uuid NOT NULL REFERENCES devices(device_id),
  code_hash text NOT NULL UNIQUE,
  state text NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','AWAITING_VEHICLE_CONFIRM','CONFIRMED','REJECTED','EXPIRED','REVOKED')),
  requested_role text CHECK (requested_role IN ('OWNER','REGISTERED_DRIVER')),
  transfer_id uuid REFERENCES ownership_transfers(transfer_id),
  phone_device_id uuid REFERENCES devices(device_id),
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  phone_confirmed_at timestamptz,
  vehicle_confirmed_at timestamptz,
  confirmed_at timestamptz,
  CHECK (expires_at <= created_at + interval '5 minutes'),
  CHECK (state NOT IN ('AWAITING_VEHICLE_CONFIRM','CONFIRMED','REJECTED') OR (phone_confirmed_at IS NOT NULL AND requested_role IS NOT NULL)),
  CHECK (state <> 'CONFIRMED' OR (phone_device_id IS NOT NULL AND confirmed_at IS NOT NULL AND vehicle_confirmed_at IS NOT NULL))
);

-- 출발 준비. 차량·휴대폰이 같은 trip을 나눕니다. 차량마다 열린 trip은 하나입니다.
-- 운전자가 바뀌면 이전 trip을 DRIVER_CHANGED로 닫고 새 trip을 엽니다. 장시간 정차·기기 연락 끊김이면 서버가 자동 종료합니다(D3).
CREATE TABLE trips (
  trip_id uuid PRIMARY KEY,
  client_trip_id uuid NOT NULL,
  vehicle_id uuid NOT NULL REFERENCES vehicles(vehicle_id),
  driver_profile_id uuid REFERENCES driver_profiles(driver_profile_id),
  driver_mode text NOT NULL CHECK (driver_mode IN ('OWNER','REGISTERED_DRIVER','GUEST','UNKNOWN')),
  ivi_device_id uuid REFERENCES devices(device_id),
  phone_device_id uuid REFERENCES devices(device_id),
  auto_location_send_enabled boolean NOT NULL DEFAULT false,
  test_mode boolean NOT NULL DEFAULT true,
  started_at timestamptz NOT NULL,
  last_device_seen_at timestamptz,
  ended_at timestamptz,
  ended_reason text CHECK (ended_reason IN ('USER_ENDED','DRIVER_CHANGED','IGNITION_OFF','LONG_PARKED','DEVICE_SILENT','OWNERSHIP_TRANSFER')),
  arrived_at timestamptz,
  arrival_alert_sent_at timestamptz,
  UNIQUE (vehicle_id, client_trip_id),
  CHECK (driver_mode NOT IN ('GUEST','UNKNOWN') OR auto_location_send_enabled = false),
  CHECK (ended_at IS NULL OR ended_at >= started_at),
  CHECK ((ended_at IS NULL) = (ended_reason IS NULL)),
  CHECK (arrival_alert_sent_at IS NULL OR (arrived_at IS NOT NULL AND driver_mode IN ('OWNER','REGISTERED_DRIVER') AND test_mode = false))
);

-- 보호자는 등록한 운전자 계정(user_id)에 속합니다. 전화번호는 암호문과 조회용 HMAC만 둡니다.
-- 보호자 삭제·탈퇴(D5)는 즉시 REVOKED와 번호 암호문 지우기(erased_at), 행 삭제는 파기 작업(PURGE_GUARDIAN)입니다.
CREATE TABLE guardians (
  guardian_id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES app_users(user_id),
  display_name text NOT NULL CHECK (char_length(display_name) BETWEEN 1 AND 30),
  phone_ciphertext bytea,
  phone_lookup_hmac text NOT NULL,
  key_version smallint NOT NULL DEFAULT 1 CHECK (key_version >= 1),
  state text NOT NULL CHECK (state IN ('PENDING_VERIFICATION','PENDING_CONSENT','ACTIVE','REVOKED')),
  phone_verified_at timestamptz,
  consented_at timestamptz,
  consent_policy_version text,
  quick_reply_enabled boolean NOT NULL DEFAULT true,
  practice_alert_sent_at timestamptz,
  revoked_at timestamptz,
  erased_at timestamptz,
  UNIQUE (user_id, guardian_id),
  UNIQUE (user_id, phone_lookup_hmac),
  CHECK (state <> 'ACTIVE' OR (phone_verified_at IS NOT NULL AND consented_at IS NOT NULL AND revoked_at IS NULL)),
  CHECK ((phone_ciphertext IS NULL) = (erased_at IS NOT NULL)),
  CHECK (erased_at IS NULL OR state = 'REVOKED')
);

-- 보호자 번호 인증(OTP). 5회 실패하면 15분 잠급니다(rules-lab guardian_otp).
CREATE TABLE guardian_verifications (
  verification_id uuid PRIMARY KEY,
  guardian_id uuid NOT NULL REFERENCES guardians(guardian_id) ON DELETE CASCADE,
  invite_token_hash text NOT NULL UNIQUE,
  otp_hash text NOT NULL,
  state text NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','VERIFIED','EXPIRED','LOCKED')),
  attempt_count smallint NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 5),
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  locked_until timestamptz,
  verified_at timestamptz,
  CHECK (state <> 'LOCKED' OR locked_until IS NOT NULL),
  CHECK (state <> 'VERIFIED' OR verified_at IS NOT NULL)
);

-- 운전자 프로필별 보호자. slot 1~5와 UNIQUE로 프로필당 5명 상한을 DB에서 지킵니다.
-- 위치 범위 기본값은 false이며, 운전자 LOCATION_SHARE_GUARDIAN 동의가 있을 때만 켭니다.
CREATE TABLE vehicle_guardians (
  driver_profile_id uuid NOT NULL,
  vehicle_id uuid NOT NULL,
  guardian_id uuid NOT NULL REFERENCES guardians(guardian_id) ON DELETE CASCADE,
  slot smallint NOT NULL CHECK (slot BETWEEN 1 AND 3),
  scopes jsonb NOT NULL DEFAULT '{"location":false,"summary":true}'::jsonb,
  active boolean NOT NULL DEFAULT true,
  PRIMARY KEY (driver_profile_id, guardian_id),
  UNIQUE (driver_profile_id, slot),
  FOREIGN KEY (vehicle_id, driver_profile_id) REFERENCES driver_profiles(vehicle_id, driver_profile_id)
);

-- 운전자·소유자 동의(운전자 본인의 위치 제공 동의 포함). 항목마다 유효한 동의는 하나입니다.
-- driver_mode는 driver_profiles와 복합 외래키로 맞춰 둡니다. GUEST·UNKNOWN은 이번 trip 동의(scope=TRIP)만, 위치 제공 동의는 정차 중에만(D3).
CREATE TABLE consents (
  consent_id uuid PRIMARY KEY,
  client_consent_id uuid NOT NULL UNIQUE,
  driver_profile_id uuid NOT NULL,
  driver_mode text NOT NULL CHECK (driver_mode IN ('OWNER','REGISTERED_DRIVER','GUEST','UNKNOWN')),
  user_id uuid REFERENCES app_users(user_id),
  subject_role text NOT NULL CHECK (subject_role IN ('OWNER','DRIVER')),
  scope text NOT NULL DEFAULT 'STANDING' CHECK (scope IN ('STANDING','TRIP')),
  trip_id uuid REFERENCES trips(trip_id),
  recorded_while_parked boolean NOT NULL,
  item text NOT NULL CHECK (item IN ('TERMS','PRIVACY_POLICY','LOCATION_COLLECT','LOCATION_SHARE_GUARDIAN','SPEECH_TEXT_STORE','PHONE_PAIRING','MAINTENANCE_SHARE')),
  policy_version text NOT NULL,
  granted_at timestamptz,
  withdrawn_at timestamptz,
  source_device_id uuid REFERENCES devices(device_id),
  created_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (driver_profile_id, driver_mode) REFERENCES driver_profiles(driver_profile_id, driver_mode),
  CHECK (granted_at IS NOT NULL OR withdrawn_at IS NOT NULL),
  CHECK ((scope = 'TRIP') = (trip_id IS NOT NULL)),
  CHECK (driver_mode NOT IN ('GUEST','UNKNOWN') OR scope = 'TRIP'),
  CHECK (subject_role <> 'OWNER' OR driver_mode = 'OWNER'),
  CHECK (item <> 'LOCATION_SHARE_GUARDIAN' OR granted_at IS NULL OR recorded_while_parked)
);

CREATE TABLE rule_profiles (
  rule_profile_id text PRIMARY KEY,
  config jsonb NOT NULL,
  sha256 char(64) NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
  production_motion_model_approved boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  approved_at timestamptz
);

-- 안내 묶음(9.1). 경고등 설명(WARNING)·긴급 안내(EMERGENCY)·증상 안내(SYMPTOM)를 같은 G_ 키로 둡니다.
-- 이전 guide_versions(uuid 키)를 대신합니다. telltale_type은 공개 열거형 상수 이름 참조이며 경고등 상태를 읽지 않습니다.
CREATE TABLE guide_bundles (
  guide_id text NOT NULL CHECK (guide_id ~ '^G_[A-Z_]+$'),
  version integer NOT NULL CHECK (version >= 1),
  vehicle_profile_id text NOT NULL REFERENCES vehicle_profiles(profile_id),
  guide_kind text NOT NULL CHECK (guide_kind IN ('WARNING','EMERGENCY','SYMPTOM')),
  availability text NOT NULL CHECK (availability IN ('VERIFIED','DRAFT_DEMO','UNAVAILABLE','FUTURE')),
  source_manual_ref text NOT NULL,
  title text NOT NULL CHECK (char_length(title) BETWEEN 1 AND 40),
  telltale_type text CHECK (telltale_type ~ '^[A-Z0-9_]+$'),
  warning_color text CHECK (warning_color IN ('RED','AMBER','GREEN','BLUE','WHITE')),
  warning_shape_key text,
  blink_pattern text CHECK (blink_pattern IN ('STEADY','BLINKING','EITHER')),
  mobility_action text CHECK (mobility_action IN ('STOP_SAFE','SLOW_CHECK','CAUTION','UNKNOWN')),
  steps_json jsonb NOT NULL CHECK (jsonb_typeof(steps_json) = 'array' AND jsonb_array_length(steps_json) BETWEEN 1 AND 12),
  tts_lines_json jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(tts_lines_json) = 'array'),
  content_sha256 char(64) NOT NULL CHECK (content_sha256 ~ '^[a-f0-9]{64}$'),
  reviewed_by uuid REFERENCES app_users(user_id),
  reviewed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (guide_id, version, vehicle_profile_id),
  CHECK (availability <> 'VERIFIED' OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
  CHECK (guide_kind <> 'WARNING' OR (warning_color IS NOT NULL AND warning_shape_key IS NOT NULL AND blink_pattern IS NOT NULL AND mobility_action IS NOT NULL))
);

-- [향후] 고장 코드는 공개 경로가 없습니다. 제조사 승인 경로가 생길 때만 채웁니다.
CREATE TABLE diagnostic_mappings (
  mapping_id uuid PRIMARY KEY,
  profile_id text NOT NULL REFERENCES vehicle_profiles(profile_id),
  code_namespace text NOT NULL,
  diagnostic_code text NOT NULL,
  guide_id text NOT NULL CHECK (guide_id ~ '^G_[A-Z_]+$'),
  availability text NOT NULL DEFAULT 'FUTURE' CHECK (availability IN ('VERIFIED','DRAFT_DEMO','UNAVAILABLE','FUTURE')),
  source_url text NOT NULL,
  reviewed_at timestamptz,
  active boolean NOT NULL DEFAULT false,
  UNIQUE (profile_id, code_namespace, diagnostic_code, guide_id),
  CHECK (active = false OR availability = 'VERIFIED')
);

CREATE TABLE incidents (
  incident_id uuid PRIMARY KEY,
  client_incident_id uuid NOT NULL,
  owner_id uuid NOT NULL,
  vehicle_id uuid NOT NULL,
  first_device_id uuid NOT NULL,
  trip_id uuid REFERENCES trips(trip_id),
  driver_profile_id uuid REFERENCES driver_profiles(driver_profile_id),
  driver_mode text NOT NULL CHECK (driver_mode IN ('OWNER','REGISTERED_DRIVER','GUEST','UNKNOWN')),
  rule_profile_id text NOT NULL REFERENCES rule_profiles(rule_profile_id),
  request_sha256 char(64) NOT NULL CHECK (request_sha256 ~ '^[a-f0-9]{64}$'),
  incident_trigger text NOT NULL CHECK (incident_trigger IN ('USER_RECORD','USER_SYMPTOM','WARNING_LOOKUP','VEHICLE_MAINTENANCE_SIGNAL','USER_EMERGENCY_GUIDANCE','USER_COLLISION','AIRBAG_DEPLOYED','USER_HELP_REQUEST')),
  lifecycle text NOT NULL DEFAULT 'OPEN' CHECK (lifecycle IN ('OPEN','MONITORING','RESOLVED','ARCHIVED')),
  severity text CHECK (severity IN ('L1_RECORD','L2_CHECK','L3_URGENT')),
  severity_reasons jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(severity_reasons) = 'array'),
  driver_response text NOT NULL CHECK (driver_response IN ('NOT_REQUESTED','RESPONDED_SAFE','RESPONDED_HELP','NO_RESPONSE','UNKNOWN')),
  vehicle_snapshot jsonb NOT NULL CHECK (jsonb_typeof(vehicle_snapshot) = 'object'),
  occurred_at timestamptz NOT NULL,
  accepted_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
  notification_revision integer NOT NULL DEFAULT 1 CHECK (notification_revision >= 1),
  test_mode boolean NOT NULL DEFAULT true,
  contains_test_input boolean NOT NULL DEFAULT false,
  -- 같은 차량 열린 알림 시간 창 안에서 묶인 사건이면, 열린 알림을 가진 사건(API alert_incident_id)입니다(D4).
  related_incident_id uuid REFERENCES incidents(incident_id),
  purge_after timestamptz,
  UNIQUE (owner_id, client_incident_id),
  UNIQUE (owner_id, incident_id),
  UNIQUE (incident_id, test_mode),
  -- 시험 입력이 신호 하나라도 섞인 사건은 test_mode=true(D2). 스냅샷 안 어디든 source TEST_INPUT이 있으면 test_mode여야 합니다.
  CHECK (NOT contains_test_input OR test_mode),
  CHECK (test_mode OR NOT jsonb_path_exists(vehicle_snapshot, '$.** ? (@.source == "TEST_INPUT")')),
  FOREIGN KEY (owner_id, vehicle_id) REFERENCES vehicles(owner_id, vehicle_id),
  FOREIGN KEY (owner_id, vehicle_id, first_device_id) REFERENCES devices(owner_id, vehicle_id, device_id)
);

-- 공개 신호 관측. Vehicle SDK 값은 측정 시각이 없어 measured_at이 NULL이고 received_at만 있습니다(D18).
-- incident_test_mode는 사건의 test_mode를 복합 외래키로 따라가며, 실제 사건에는 TEST_INPUT 관측을 붙일 수 없습니다(D2).
CREATE TABLE observations (
  observation_id uuid PRIMARY KEY,
  incident_id uuid NOT NULL,
  incident_test_mode boolean NOT NULL,
  source_device_id uuid NOT NULL REFERENCES devices(device_id),
  sequence bigint NOT NULL CHECK (sequence >= 0),
  code text NOT NULL CHECK (code IN ('VEHICLE_SPEED','BRAKE_PEDAL','STEERING_ANGLE','TURN_SIGNAL','DOOR_POSITION','SEAT_OCCUPANCY','SEATBELT_UNBUCKLED','AIRBAG_DEPLOYED','ODOMETER','FUEL_PERCENT','EV_SOC','ENGINE_OIL_LEVEL','BRAKE_FLUID_LEVEL','WASHER_FLUID_LOW','POWER_DOWN_WARNING','LOCATION','UX_RESTRICTION','WARNING_USER_SELECTED','STT_TEXT','CLIMATE_AC','CLIMATE_FAN_SPEED','WINDOW_POSITION','WIPER_STATE')),
  area text,
  source text NOT NULL CHECK (source IN ('VEHICLE_SDK','AAOS_PROPERTY','FUSED_LOCATION','NAVI_HELPER','STT','USER_SELECTION','PHONE','TEST_INPUT')),
  quality text NOT NULL CHECK (quality IN ('OK','STALE','UNAVAILABLE','PERMISSION_DENIED','NOT_SUPPORTED','TEST_INPUT')),
  measured_at timestamptz,
  received_at timestamptz NOT NULL,
  freshness_basis text NOT NULL CHECK (freshness_basis IN ('SOURCE_TIMESTAMP','ARRIVAL_ONLY','POLLED_STATE')),
  unit text,
  value jsonb,
  payload_sha256 char(64) NOT NULL CHECK (payload_sha256 ~ '^[a-f0-9]{64}$'),
  UNIQUE (incident_id, source_device_id, sequence),
  FOREIGN KEY (incident_id, incident_test_mode) REFERENCES incidents(incident_id, test_mode) ON DELETE CASCADE,
  CHECK (quality NOT IN ('OK','TEST_INPUT') OR (value IS NOT NULL AND value <> 'null'::jsonb)),
  CHECK (source <> 'TEST_INPUT' OR quality = 'TEST_INPUT'),
  CHECK (source <> 'TEST_INPUT' OR incident_test_mode),
  CHECK (freshness_basis <> 'SOURCE_TIMESTAMP' OR measured_at IS NOT NULL)
);

CREATE TABLE observation_batches (
  batch_id uuid PRIMARY KEY,
  incident_id uuid NOT NULL REFERENCES incidents(incident_id) ON DELETE CASCADE,
  submitting_device_id uuid NOT NULL REFERENCES devices(device_id),
  client_batch_id uuid NOT NULL,
  request_sha256 char(64) NOT NULL CHECK (request_sha256 ~ '^[a-f0-9]{64}$'),
  resulting_incident_version bigint NOT NULL CHECK (resulting_incident_version >= 1),
  received_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (incident_id, submitting_device_id, client_batch_id)
);

-- 안내 출력 기록. 두 긴급 안내 상태는 운전자의 직접 보고(entry)가 있어야 합니다(D8).
CREATE TABLE guidance_events (
  guidance_event_id uuid PRIMARY KEY,
  incident_id uuid NOT NULL REFERENCES incidents(incident_id) ON DELETE CASCADE,
  source_device_id uuid NOT NULL REFERENCES devices(device_id),
  client_event_id uuid NOT NULL,
  guide_id text NOT NULL CHECK (guide_id ~ '^G_[A-Z_]+$'),
  guide_version integer NOT NULL CHECK (guide_version >= 1),
  step_id text NOT NULL,
  guidance_state text NOT NULL CHECK (guidance_state IN ('OBSERVING','CONFIRM_PROMPT','UNWANTED_ACCEL_GUIDE','DECEL_FAILURE_GUIDE','MANUAL_SELECTION','HELP_CONFIRM','STOPPED_REVIEW','ENDED')),
  entry text CHECK (entry IN ('USER_BUTTON','USER_VOICE','PROMPT_CONFIRMED')),
  output text NOT NULL CHECK (output IN ('AUDIO','VISUAL')),
  result text NOT NULL CHECK (result IN ('REQUESTED','STARTED','COMPLETED','FAILED','BLOCKED_BY_HOST','INTERRUPTED')),
  error_code text,
  occurred_at timestamptz NOT NULL,
  received_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (incident_id, source_device_id, client_event_id),
  CHECK (guidance_state NOT IN ('UNWANTED_ACCEL_GUIDE','DECEL_FAILURE_GUIDE') OR entry IS NOT NULL)
);

-- 증상 고르기. 주행 중 상황 선택 카드는 5개입니다(2차 결정 B9): 긴급 3개(안 멈춰요·저절로 빨라져요 → PEDAL_PROBLEM, 사고가 났어요 → COLLISION)
-- + 증상 2개(VIBRATION·BURNING_SMELL). 말로 하기(source STT)는 카드 제한 없이 문자열로 분류합니다.
CREATE TABLE manual_reports (
  report_id uuid PRIMARY KEY,
  incident_id uuid NOT NULL REFERENCES incidents(incident_id) ON DELETE CASCADE,
  client_report_id uuid NOT NULL,
  symptoms text[] NOT NULL,
  body_area text NOT NULL DEFAULT 'UNKNOWN' CHECK (body_area IN ('FRONT','FRONT_LEFT_TIRE','FRONT_RIGHT_TIRE','REAR_LEFT_TIRE','REAR_RIGHT_TIRE','CABIN','REAR','UNDERBODY','UNKNOWN')),
  source text NOT NULL DEFAULT 'USER_SELECTION' CHECK (source IN ('USER_SELECTION','STT')),
  memo_text text CHECK (memo_text IS NULL OR char_length(memo_text) <= 200),
  selected_while_driving boolean NOT NULL,
  reported_stopped boolean,
  request_help boolean NOT NULL DEFAULT false,
  selected_at timestamptz NOT NULL,
  received_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (incident_id, client_report_id),
  CONSTRAINT manual_reports_symptoms_enum CHECK (cardinality(symptoms) BETWEEN 1 AND 3 AND symptoms <@ ARRAY['VIBRATION','PULLING','NOISE','BURNING_SMELL','PEDAL_PROBLEM','TIRE_DAMAGE','COLLISION','POWER_LOSS','UNKNOWN']::text[]),
  CONSTRAINT manual_reports_driving_symptoms CHECK (NOT selected_while_driving OR source = 'STT' OR symptoms <@ ARRAY['VIBRATION','BURNING_SMELL','COLLISION','PEDAL_PROBLEM']::text[])
);

CREATE TABLE incident_actions (
  action_id uuid PRIMARY KEY,
  incident_id uuid NOT NULL REFERENCES incidents(incident_id) ON DELETE CASCADE,
  client_action_id uuid NOT NULL,
  actor_device_id uuid REFERENCES devices(device_id),
  action text NOT NULL CHECK (action IN ('SEND_NOW','CANCEL','RESPOND_SAFE','SEND_CORRECTION','REQUEST_HELP','END_GUIDANCE')),
  correction_reason text CHECK (correction_reason IN ('DRIVER_SAFE','FALSE_ALARM','SITUATION_UPDATE')),
  recipients_shown_guardian_ids uuid[] NOT NULL DEFAULT '{}',
  expected_version bigint NOT NULL CHECK (expected_version >= 1),
  result_version bigint NOT NULL CHECK (result_version >= expected_version),
  request_sha256 char(64) NOT NULL CHECK (request_sha256 ~ '^[a-f0-9]{64}$'),
  occurred_at timestamptz NOT NULL,
  committed_at timestamptz NOT NULL DEFAULT now(),
  result jsonb NOT NULL,
  UNIQUE (incident_id, client_action_id),
  CHECK ((action IN ('SEND_CORRECTION','RESPOND_SAFE')) = (correction_reason IS NOT NULL)),
  CHECK (action <> 'REQUEST_HELP' OR cardinality(recipients_shown_guardian_ids) BETWEEN 1 AND 5)
);

-- 위치(9.2). accuracy_m는 없습니다. location_confidence는 SDK 원값 그대로입니다.
CREATE TABLE incident_locations (
  location_id uuid PRIMARY KEY,
  incident_id uuid NOT NULL REFERENCES incidents(incident_id) ON DELETE CASCADE,
  client_location_id uuid NOT NULL,
  latitude double precision CHECK (latitude BETWEEN -90 AND 90),
  longitude double precision CHECK (longitude BETWEEN -180 AND 180),
  measured_at timestamptz,
  received_at timestamptz NOT NULL,
  location_confidence real,
  location_source text NOT NULL CHECK (location_source IN ('VEHICLE','PHONE')),
  quality text NOT NULL CHECK (quality IN ('OK','STALE','UNAVAILABLE','PERMISSION_DENIED','NOT_SUPPORTED','TEST_INPUT')),
  address_status text NOT NULL DEFAULT 'PENDING' CHECK (address_status IN ('PENDING','AVAILABLE','UNAVAILABLE','FAILED')),
  address_source text NOT NULL DEFAULT 'NONE' CHECK (address_source IN ('NAVI_HELPER','SERVER_GEOCODE','NONE')),
  road_address text,
  geocoded_at timestamptz,
  UNIQUE (incident_id, client_location_id),
  CHECK ((latitude IS NULL) = (longitude IS NULL)),
  CHECK (quality NOT IN ('OK','TEST_INPUT') OR latitude IS NOT NULL)
);

-- 업로드 자료: 휴대폰 사진, 신호 시간표, 안내 기록만. 원본 녹음·영상·IMU는 [향후]입니다.
CREATE TABLE evidence_assets (
  asset_id uuid PRIMARY KEY,
  incident_id uuid NOT NULL REFERENCES incidents(incident_id) ON DELETE CASCADE,
  client_asset_id uuid NOT NULL,
  channel text NOT NULL CHECK (channel IN ('PHOTO','SIGNAL_TIMELINE','GUIDANCE_LOG')),
  content_type text NOT NULL,
  source text NOT NULL CHECK (source IN ('VEHICLE_SDK','AAOS_PROPERTY','FUSED_LOCATION','NAVI_HELPER','STT','USER_SELECTION','PHONE','TEST_INPUT')),
  object_key text NOT NULL UNIQUE,
  byte_count bigint NOT NULL CHECK (byte_count BETWEEN 1 AND 536870912),
  sha256 char(64) NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
  state text NOT NULL CHECK (state IN ('UPLOADING','VERIFYING','COMPLETE','FAILED')),
  started_at timestamptz NOT NULL,
  ended_at timestamptz NOT NULL,
  protected boolean NOT NULL DEFAULT true,
  retained_until timestamptz,
  held_by_user boolean NOT NULL DEFAULT false,
  UNIQUE (incident_id, client_asset_id),
  CHECK (ended_at >= started_at)
);

-- 알림 정책(9.1). 같은 차량의 열린 알림은 시간 창(2시간, rules-lab notification.open_alert_window_ms) 안에서 하나입니다(D4).
-- 창은 alert_window_ends_at까지이며, 끝나면 서버 작업(ALERT_WINDOW_CLOSE)이 alert_window_closed_at을 채워 다음 사고 알림을 막지 않습니다.
-- 정정 알림은 정책 상태를 되돌리지 않고 deliveries의 새 revision만 만들므로 이 제약의 예외입니다.
-- test_mode는 사건과 복합 외래키로 맞추고, 시험 사건은 모의 발송(dispatch_mode=MOCK)만 합니다(D2).
CREATE TABLE notification_policies (
  incident_id uuid PRIMARY KEY,
  test_mode boolean NOT NULL,
  dispatch_mode text NOT NULL DEFAULT 'MOCK' CHECK (dispatch_mode IN ('REAL','MOCK')),
  vehicle_id uuid NOT NULL REFERENCES vehicles(vehicle_id),
  trip_id uuid REFERENCES trips(trip_id),
  state text NOT NULL CHECK (state IN ('RECORD_ONLY','COUNTDOWN','RELEASED','DISPATCHING','SETTLED','CANCELLED','BLOCKED_NO_RECIPIENT','BLOCKED_NO_CONSENT','EXPIRED')),
  outcome text CHECK (outcome IN ('ALL_CONFIRMED','PARTIAL','NONE_CONFIRMED')),
  recipient_guardian_ids uuid[] NOT NULL DEFAULT '{}',
  pre_send_confirmation_ms integer NOT NULL DEFAULT 10000 CHECK (pre_send_confirmation_ms BETWEEN 0 AND 10000),
  clock_confidence text NOT NULL CHECK (clock_confidence IN ('SYNCHRONIZED','MONOTONIC_ONLY','UNKNOWN')),
  countdown_clock jsonb NOT NULL,
  release_at timestamptz,
  cancelled_at timestamptz,
  settled_at timestamptz,
  expires_at timestamptz NOT NULL,
  timing_uncertain boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
  alert_generation integer NOT NULL DEFAULT 1 CHECK (alert_generation >= 1),
  alert_window_ends_at timestamptz,
  alert_window_closed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (incident_id, test_mode) REFERENCES incidents(incident_id, test_mode) ON DELETE CASCADE,
  CHECK (NOT test_mode OR dispatch_mode = 'MOCK'),
  CHECK (state NOT IN ('COUNTDOWN','RELEASED','DISPATCHING') OR alert_window_ends_at IS NOT NULL),
  CHECK (alert_window_ends_at IS NULL OR (alert_window_ends_at > created_at AND alert_window_ends_at <= created_at + interval '2 hours')),
  CHECK (alert_window_closed_at IS NULL OR alert_window_ends_at IS NOT NULL),
  CHECK (cardinality(recipient_guardian_ids) <= 5),
  CHECK (state NOT IN ('COUNTDOWN','RELEASED','DISPATCHING') OR release_at IS NOT NULL),
  CHECK ((state = 'SETTLED') = (outcome IS NOT NULL)),
  CHECK (state <> 'SETTLED' OR settled_at IS NOT NULL)
);

-- 전달(9.1). 열람은 상태가 아니라 opened_at(보호자 링크 1단계 POST)입니다. 연습 알림·도착 알림(H7)은 사건이 없습니다.
-- 정정이 이전 판을 대신하면 supersedes_delivery_id로 순서를 남기고 이전 판 미제출 작업을 취소합니다.
CREATE TABLE deliveries (
  delivery_id uuid PRIMARY KEY,
  incident_id uuid REFERENCES incidents(incident_id) ON DELETE CASCADE,
  practice_request_id uuid,
  arrival_trip_id uuid REFERENCES trips(trip_id) ON DELETE CASCADE,
  guardian_id uuid NOT NULL REFERENCES guardians(guardian_id) ON DELETE CASCADE,
  dispatch_mode text NOT NULL DEFAULT 'MOCK' CHECK (dispatch_mode IN ('REAL','MOCK')),
  supersedes_delivery_id uuid REFERENCES deliveries(delivery_id),
  channel text NOT NULL CHECK (channel IN ('KAKAO','SMS','LMS')),
  transport_group text NOT NULL CHECK (transport_group IN ('KAKAO','TEXT')),
  revision integer NOT NULL CHECK (revision >= 1),
  is_correction boolean NOT NULL DEFAULT false,
  is_practice boolean NOT NULL DEFAULT false,
  is_arrival boolean NOT NULL DEFAULT false,
  state text NOT NULL CHECK (state IN ('PENDING','ACCEPTED','SUBMISSION_UNKNOWN','DELIVERED','DELIVERY_UNCONFIRMED','FAILED','CANCELLED')),
  template_code text,
  first_submitted_at timestamptz,
  fallback_deadline_at timestamptz,
  provider_request_id text,
  provider_recipient_seq integer,
  provider_result_code text,
  attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 3),
  accepted_at timestamptz,
  delivered_at timestamptz,
  opened_at timestamptz,
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (incident_id, guardian_id, transport_group, revision),
  UNIQUE (practice_request_id, guardian_id, transport_group),
  UNIQUE (arrival_trip_id, guardian_id, transport_group),
  CHECK ((channel = 'KAKAO') = (transport_group = 'KAKAO')),
  CHECK ((is_practice OR is_arrival) = (incident_id IS NULL)),
  CHECK (is_practice = (practice_request_id IS NOT NULL)),
  CHECK (is_arrival = (arrival_trip_id IS NOT NULL)),
  CHECK (NOT (is_practice AND is_arrival)),
  CHECK (NOT ((is_practice OR is_arrival) AND is_correction)),
  CHECK (supersedes_delivery_id IS NULL OR is_correction)
);

CREATE TABLE outbox_jobs (
  job_id uuid PRIMARY KEY,
  incident_id uuid REFERENCES incidents(incident_id) ON DELETE CASCADE,
  delivery_id uuid REFERENCES deliveries(delivery_id) ON DELETE CASCADE,
  dedupe_key text NOT NULL UNIQUE,
  job_type text NOT NULL CHECK (job_type IN ('NOTIFY_RELEASE','KAKAO_SUBMIT','KAKAO_RESULT_CHECK','SMS_SUBMIT','SMS_RESULT_CHECK','CORRECTION_SUBMIT','PRACTICE_ALERT_SUBMIT','GUARDIAN_OTP_SUBMIT','DISCLOSURE_NOTIFY','REPLY_RELAY','POLICY_EXPIRE','ASSET_VERIFY','REVERSE_GEOCODE','SHARE_UPDATE','REVOKE_SHARES','PURGE_INCIDENT','ALERT_WINDOW_CLOSE','TRIP_AUTO_END','ARRIVAL_ALERT_SUBMIT','PURGE_ACCOUNT','PURGE_GUARDIAN','PURGE_SPEECH_TEXT','DEVICE_WIPE','ANONYMIZE_DISCLOSURES','TRANSFER_EXPIRE')),
  payload jsonb NOT NULL,
  state text NOT NULL DEFAULT 'READY' CHECK (state IN ('READY','RUNNING','DONE','RETRY','FAILED','CANCELLED')),
  available_at timestamptz NOT NULL,
  lease_until timestamptz,
  lease_owner text,
  attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  last_error_code text,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- 보호자 공유(보호자 웹). 위치 갱신은 location_stream_until까지만(C7-21). '공유 중지'는 동의를 바꾸지 않고 이 값을 지금으로 당깁니다(D5).
CREATE TABLE share_grants (
  share_id uuid PRIMARY KEY,
  incident_id uuid NOT NULL REFERENCES incidents(incident_id) ON DELETE CASCADE,
  guardian_id uuid NOT NULL REFERENCES guardians(guardian_id) ON DELETE CASCADE,
  token_hash text NOT NULL UNIQUE,
  scopes jsonb NOT NULL,
  expires_at timestamptz NOT NULL,
  location_stream_until timestamptz,
  revoked_at timestamptz,
  location_stopped_at timestamptz,
  link_opened_at timestamptz,
  authenticated_opened_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (expires_at <= created_at + interval '24 hours'),
  CHECK (location_stream_until IS NULL OR location_stream_until <= expires_at),
  CHECK (location_stopped_at IS NULL OR location_stream_until <= location_stopped_at),
  CHECK (authenticated_opened_at IS NULL OR link_opened_at IS NOT NULL)
);

-- 보호자 링크 2단계 열람(D1·H5). 1단계: 링크 열기(토큰), 2단계: 휴대폰 번호 뒷자리 확인. 5회 틀리면 15분 잠급니다.
CREATE TABLE guardian_share_sessions (
  share_session_id uuid PRIMARY KEY,
  share_id uuid NOT NULL REFERENCES share_grants(share_id) ON DELETE CASCADE,
  stage text NOT NULL DEFAULT 'PHONE_CHECK_REQUIRED' CHECK (stage IN ('PHONE_CHECK_REQUIRED','VERIFIED','LOCKED','EXPIRED')),
  attempt_count smallint NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 5),
  session_token_hash text UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  locked_until timestamptz,
  verified_at timestamptz,
  CHECK (expires_at <= created_at + interval '24 hours'),
  CHECK (stage <> 'LOCKED' OR locked_until IS NOT NULL),
  CHECK ((stage = 'VERIFIED') = (verified_at IS NOT NULL AND session_token_hash IS NOT NULL))
);

-- 정비사 24시간 읽기 전용 요약 링크(F5). 토큰은 해시만 둡니다.
CREATE TABLE share_links (
  link_id uuid PRIMARY KEY,
  client_link_id uuid NOT NULL,
  incident_id uuid NOT NULL REFERENCES incidents(incident_id) ON DELETE CASCADE,
  created_by_device_id uuid REFERENCES devices(device_id),
  token_hash text NOT NULL UNIQUE,
  scope text[] NOT NULL CHECK (cardinality(scope) >= 1 AND scope <@ ARRAY['SUMMARY','SIGNAL_TIMELINE','PATTERN_CARD','MAINTENANCE_NOTES']::text[]),
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  view_count integer NOT NULL DEFAULT 0 CHECK (view_count >= 0),
  last_viewed_at timestamptz,
  UNIQUE (incident_id, client_link_id),
  CHECK (expires_at <= created_at + interval '24 hours')
);

-- 보호자 빠른 답(N11): [확인했어요] [전화할게요].
CREATE TABLE guardian_replies (
  reply_id uuid PRIMARY KEY,
  client_reply_id uuid NOT NULL,
  incident_id uuid NOT NULL REFERENCES incidents(incident_id) ON DELETE CASCADE,
  guardian_id uuid NOT NULL REFERENCES guardians(guardian_id) ON DELETE CASCADE,
  share_id uuid NOT NULL REFERENCES share_grants(share_id) ON DELETE CASCADE,
  reply_kind text NOT NULL CHECK (reply_kind IN ('SEEN','WILL_CALL')),
  replied_at timestamptz NOT NULL DEFAULT now(),
  relayed_to_vehicle_at timestamptz,
  UNIQUE (guardian_id, client_reply_id)
);

-- 위치 제공 기록(N10·H6 내 위치 영수증함). 사건 좌표를 지운 뒤에도 남기므로 incidents에 외래키를 걸지 않습니다.
-- 삭제 대신 익명화합니다(D5): 보호자·운전자 프로필이 지워지면 외래키가 NULL이 되고(ON DELETE SET NULL),
-- 가린 받는 사람 표시(recipient_label_masked)와 시각·경로·정밀도는 남습니다. 보관 기간은 법령 확인 뒤 정합니다(rules-lab retention).
CREATE TABLE location_disclosures (
  disclosure_id uuid PRIMARY KEY,
  incident_id uuid,
  driver_profile_id uuid REFERENCES driver_profiles(driver_profile_id) ON DELETE SET NULL,
  recipient_guardian_id uuid REFERENCES guardians(guardian_id) ON DELETE SET NULL,
  recipient_label_masked text NOT NULL CHECK (char_length(recipient_label_masked) BETWEEN 1 AND 40),
  delivery_id uuid,
  location_id uuid,
  channel text NOT NULL CHECK (channel IN ('KAKAO','SMS','LMS','SHARE_PAGE')),
  purpose text NOT NULL CHECK (purpose IN ('GUARDIAN_ALERT','GUARDIAN_PAGE_VIEW','CORRECTION','ARRIVAL_ALERT')),
  location_precision text NOT NULL CHECK (location_precision IN ('ADDRESS','COORDINATES','NONE')),
  disclosed_at timestamptz NOT NULL,
  driver_notified_at timestamptz,
  anonymized_at timestamptz,
  CHECK (driver_notified_at IS NULL OR driver_notified_at >= disclosed_at),
  CHECK (anonymized_at IS NULL OR (incident_id IS NULL AND driver_profile_id IS NULL AND recipient_guardian_id IS NULL AND location_id IS NULL)),
  CHECK (purpose <> 'ARRIVAL_ALERT' OR location_precision = 'NONE'),
  CHECK (location_precision <> 'NONE' OR location_id IS NULL)
);

-- 도착 알림 설정(H7). 기본 꺼짐. 운전자 본인이 정차 중에만 켭니다. GUEST·UNKNOWN 프로필은 켤 수 없습니다.
CREATE TABLE arrival_alert_settings (
  driver_profile_id uuid PRIMARY KEY,
  driver_mode text NOT NULL CHECK (driver_mode IN ('OWNER','REGISTERED_DRIVER','GUEST','UNKNOWN')),
  enabled boolean NOT NULL DEFAULT false,
  guardian_ids uuid[] NOT NULL DEFAULT '{}',
  updated_while_parked boolean NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (driver_profile_id, driver_mode) REFERENCES driver_profiles(driver_profile_id, driver_mode) ON DELETE CASCADE,
  CHECK (NOT enabled OR (driver_mode IN ('OWNER','REGISTERED_DRIVER') AND cardinality(guardian_ids) BETWEEN 1 AND 5 AND updated_while_parked)),
  CHECK (cardinality(guardian_ids) <= 5)
);

-- 정비소 도착 체크인(H8). 근접 알림 좌표는 기기 안에서만 쓰고 여기에는 두지 않습니다.
CREATE TABLE service_checkins (
  checkin_id uuid PRIMARY KEY,
  client_checkin_id uuid NOT NULL,
  vehicle_id uuid NOT NULL REFERENCES vehicles(vehicle_id),
  trip_id uuid REFERENCES trips(trip_id),
  driver_profile_id uuid REFERENCES driver_profiles(driver_profile_id) ON DELETE SET NULL,
  shop_ref text NOT NULL CHECK (char_length(shop_ref) BETWEEN 1 AND 80),
  shop_display_name text CHECK (shop_display_name IS NULL OR char_length(shop_display_name) <= 60),
  detected_by text NOT NULL CHECK (detected_by IN ('FUSED_PROXIMITY_ENTER','NAVI_WAYPOINT_ARRIVED','USER_TAP')),
  detected_while_parked boolean NOT NULL,
  arrived_at timestamptz NOT NULL,
  pattern_card_shown_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (vehicle_id, client_checkin_id),
  CHECK (pattern_card_shown_at IS NULL OR detected_while_parked)
);

-- 정비 노트와 재발 확인(N6). 재발 확인은 정비 뒤 500km까지입니다.
CREATE TABLE maintenance_notes (
  note_id uuid PRIMARY KEY,
  client_note_id uuid NOT NULL,
  vehicle_id uuid NOT NULL REFERENCES vehicles(vehicle_id),
  driver_profile_id uuid REFERENCES driver_profiles(driver_profile_id),
  incident_ids uuid[] NOT NULL DEFAULT '{}',
  service_date date NOT NULL,
  odometer_km_at_service numeric(10,1) NOT NULL CHECK (odometer_km_at_service >= 0),
  recheck_until_km numeric(10,1) NOT NULL,
  summary text NOT NULL CHECK (char_length(summary) BETWEEN 1 AND 500),
  shop_name text CHECK (shop_name IS NULL OR char_length(shop_name) <= 60),
  recheck_state text NOT NULL DEFAULT 'PENDING' CHECK (recheck_state IN ('PENDING','NO_RECURRENCE','RECURRED','SKIPPED')),
  recheck_answered_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (vehicle_id, client_note_id),
  CHECK (recheck_until_km = odometer_km_at_service + 500),
  CHECK (recheck_state = 'PENDING' OR recheck_answered_at IS NOT NULL)
);

CREATE TABLE upload_sessions (
  upload_id uuid PRIMARY KEY,
  asset_id uuid NOT NULL UNIQUE REFERENCES evidence_assets(asset_id) ON DELETE CASCADE,
  expected_bytes bigint NOT NULL CHECK (expected_bytes BETWEEN 1 AND 536870912),
  committed_offset bigint NOT NULL DEFAULT 0 CHECK (committed_offset >= 0),
  state text NOT NULL DEFAULT 'OPEN' CHECK (state IN ('OPEN','VERIFYING','COMPLETE','FAILED','EXPIRED')),
  expires_at timestamptz NOT NULL,
  version bigint NOT NULL DEFAULT 1 CHECK (version >= 1),
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK (committed_offset <= expected_bytes)
);

CREATE TABLE upload_chunks (
  upload_id uuid NOT NULL REFERENCES upload_sessions(upload_id) ON DELETE CASCADE,
  chunk_offset bigint NOT NULL CHECK (chunk_offset >= 0),
  byte_count integer NOT NULL CHECK (byte_count BETWEEN 1 AND 4194304),
  sha256 char(64) NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
  staging_object_key text NOT NULL UNIQUE,
  committed_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (upload_id, chunk_offset)
);

-- 증상 재현 조건 카드(N2)의 서버 요약 뷰. 같은 차량·운전자·증상·부위·시험 여부가 3번 이상일 때만 행이 생깁니다.
-- 평소 주행 대비 비교(B12)는 차량이 하고, 이 뷰는 횟수·속도 요약만 줍니다(computed_by=SERVER_SUMMARY). 원인 진단이 아닙니다.
-- 운전자끼리, 시험 입력 기록과 실제 기록끼리 섞지 않습니다. 속도는 후진(음수)을 고려해 절댓값을 씁니다.
CREATE VIEW symptom_patterns AS
SELECT
  i.vehicle_id,
  i.driver_profile_id,
  i.test_mode,
  s.symptom,
  mr.body_area,
  count(*) AS occurrences,
  min(i.occurred_at) AS first_at,
  max(i.occurred_at) AS last_at,
  min(CASE WHEN i.vehicle_snapshot->'speed_mps'->>'quality' IN ('OK','TEST_INPUT')
           THEN abs((i.vehicle_snapshot->'speed_mps'->>'value')::numeric) * 3.6 END) AS speed_kmh_min,
  max(CASE WHEN i.vehicle_snapshot->'speed_mps'->>'quality' IN ('OK','TEST_INPUT')
           THEN abs((i.vehicle_snapshot->'speed_mps'->>'value')::numeric) * 3.6 END) AS speed_kmh_max,
  avg(CASE WHEN i.vehicle_snapshot->'brake_pressed'->>'value' = 'true' THEN 1.0
           WHEN i.vehicle_snapshot->'brake_pressed'->>'value' = 'false' THEN 0.0 END) AS brake_pressed_ratio,
  bool_or(i.contains_test_input) AS test_input_included
FROM manual_reports mr
JOIN incidents i ON i.incident_id = mr.incident_id
CROSS JOIN LATERAL unnest(mr.symptoms) AS s(symptom)
WHERE i.lifecycle <> 'ARCHIVED'
GROUP BY i.vehicle_id, i.driver_profile_id, i.test_mode, s.symptom, mr.body_area
HAVING count(*) >= 3;

CREATE UNIQUE INDEX vehicles_active_vin ON vehicles(vin_hash) WHERE vin_hash IS NOT NULL AND revoked_at IS NULL;
CREATE UNIQUE INDEX one_open_trip_per_vehicle ON trips(vehicle_id) WHERE ended_at IS NULL;
CREATE UNIQUE INDEX one_active_consent_per_item ON consents(driver_profile_id, item) WHERE scope = 'STANDING' AND withdrawn_at IS NULL AND granted_at IS NOT NULL;
CREATE UNIQUE INDEX one_active_trip_consent_per_item ON consents(driver_profile_id, item, trip_id) WHERE scope = 'TRIP' AND withdrawn_at IS NULL AND granted_at IS NOT NULL;
CREATE UNIQUE INDEX one_open_alert_per_vehicle ON notification_policies(vehicle_id) WHERE state IN ('COUNTDOWN','RELEASED','DISPATCHING') AND alert_window_closed_at IS NULL;
CREATE UNIQUE INDEX one_open_transfer_per_vehicle ON ownership_transfers(from_vehicle_id) WHERE state IN ('PENDING_NEW_OWNER','OBJECTION_WINDOW');
CREATE INDEX incidents_vehicle_time ON incidents(vehicle_id, occurred_at DESC);
CREATE INDEX incidents_driver_time ON incidents(driver_profile_id, occurred_at DESC);
CREATE INDEX observations_incident_time ON observations(incident_id, received_at);
CREATE INDEX guidance_incident_time ON guidance_events(incident_id, occurred_at);
CREATE INDEX locations_incident_time ON incident_locations(incident_id, received_at DESC);
CREATE INDEX policies_release_due ON notification_policies(release_at) WHERE state IN ('COUNTDOWN','RELEASED');
CREATE INDEX policies_expiry_due ON notification_policies(expires_at) WHERE state IN ('RELEASED','DISPATCHING');
CREATE INDEX outbox_ready_due ON outbox_jobs(available_at) WHERE state IN ('READY','RETRY');
CREATE INDEX deliveries_result_due ON deliveries(state, fallback_deadline_at);
CREATE INDEX assets_incident_channel ON evidence_assets(incident_id, channel, started_at);
CREATE INDEX disclosures_driver_time ON location_disclosures(driver_profile_id, disclosed_at DESC);
CREATE INDEX replies_incident_time ON guardian_replies(incident_id, replied_at);
CREATE INDEX maintenance_recheck_due ON maintenance_notes(vehicle_id, recheck_until_km) WHERE recheck_state = 'PENDING';
CREATE INDEX policies_alert_window_due ON notification_policies(alert_window_ends_at) WHERE alert_window_closed_at IS NULL AND alert_window_ends_at IS NOT NULL;
CREATE INDEX trips_auto_end_due ON trips(last_device_seen_at) WHERE ended_at IS NULL;
CREATE INDEX share_sessions_share ON guardian_share_sessions(share_id, created_at DESC);
CREATE INDEX checkins_vehicle_time ON service_checkins(vehicle_id, arrived_at DESC);

COMMIT;
