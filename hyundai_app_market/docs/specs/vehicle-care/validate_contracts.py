#!/usr/bin/env python3
"""플레오스 가드 개발 계약 v3.1 정적 검사기 (python3 표준 라이브러리만).

검사 항목
  1. JSON 파싱(현재 판·이력 판)
  2. OpenAPI: $ref 해석, operationId 중복, 경로 매개변수, security 이름, default 응답, 개수 집계
  3. 결정 9장 고정 enum과 OpenAPI enum 일치, OpenAPI enum <-> SQL CHECK 일치
  4. OpenAPI 스키마 부분 검사기로 긍정·부정 예시 확인(사용한 키워드만 구현)
  5. rules-lab·ui-tokens·인수 시나리오 사이의 이름·수치 교차 확인
  6. 인수 시나리오 ID 중복·참조(api/sql/rules/tokens) 확인, v2 사례 56개 승계 확인
  7. ui-tokens 대비 재계산, 배치 영역 계산, 화면 목록 확인
  8. 개인 로컬 경로 문자열 검사
  9. (선택) --pg-host/--pg-port: 로컬 PostgreSQL에 새 DB를 만들어 DDL 실행과 제약 시험
 10. 2차 비판 결정(D1~D6·B·F·G·H) 반영 확인: 새 작업 18개, 새 enum ↔ SQL CHECK, 시안 폴더 실제 목록, G 화면 문구표, 새 인수 사례 주제
 PRD와의 대조는 하지 않는다(계약 파일끼리만).

사용
  python3 validate_contracts.py                 # 정적 검사, 결과를 표준 출력에 요약
  python3 validate_contracts.py --write validation-2026-11-12-v3.1.json
  python3 validate_contracts.py --pg-host /path/to/socketdir --pg-port 5432 --write validation-2026-11-12-v3.1.json

이 검사는 정적 검사입니다. 앱 빌드·실행, 서버 실행, 실제 발송, 실차 동작의 증거가 아닙니다.
"""
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
HISTORY = os.path.join("history", "2026-11-12-v2.1")
HISTORY_V30 = os.path.join("history", "2026-11-12-v3.0")
HISTORY_V30_FILES = ["api.openapi.json", "database.sql", "rules-lab.json", "ui-tokens.json", "acceptance-scenarios.json", "README.md", "validate_contracts.py", "validation-2026-11-12-v3.json"]
ASSETS_V4 = os.path.join("..", "..", "..", "output", "submission", "assets", "v4")
VERSIONS = {"openapi": "3.1.0", "rules": "2.1.0", "tokens": "5.1.0", "acceptance": "3.1.0", "contract": "3.1.0", "db": "v3.1"}
CONTRACT_FILES = ["api.openapi.json", "database.sql", "rules-lab.json", "ui-tokens.json", "acceptance-scenarios.json"]
HISTORY_FILES = ["api.openapi.json", "database.sql", "rules-lab.json", "ui-tokens.json", "acceptance-scenarios.json", "README.md", "validation-2026-11-12.json"]

# 결정 9장(계약 고정 사항) 9.1·9.2의 이름. PRD v4와 같은 표를 쓴다.
DECISION_ENUMS = {
    "Severity": ["L1_RECORD", "L2_CHECK", "L3_URGENT"],
    "Availability": ["VERIFIED", "DRAFT_DEMO", "UNAVAILABLE", "FUTURE"],
    "InputSource": ["VEHICLE_SDK", "AAOS_PROPERTY", "FUSED_LOCATION", "NAVI_HELPER", "STT", "USER_SELECTION", "PHONE", "TEST_INPUT"],
    "Quality": ["OK", "STALE", "UNAVAILABLE", "PERMISSION_DENIED", "NOT_SUPPORTED", "TEST_INPUT"],
    "PolicyState": ["RECORD_ONLY", "COUNTDOWN", "RELEASED", "DISPATCHING", "SETTLED", "CANCELLED", "BLOCKED_NO_RECIPIENT", "BLOCKED_NO_CONSENT", "EXPIRED"],
    "PolicyOutcome": ["ALL_CONFIRMED", "PARTIAL", "NONE_CONFIRMED"],
    "DeliveryState": ["PENDING", "ACCEPTED", "SUBMISSION_UNKNOWN", "DELIVERED", "DELIVERY_UNCONFIRMED", "FAILED", "CANCELLED"],
    "TransportGroup": ["KAKAO", "TEXT"],
    "DriverAction": ["SEND_NOW", "CANCEL", "RESPOND_SAFE", "SEND_CORRECTION", "REQUEST_HELP", "END_GUIDANCE"],
    "GuidanceState": ["OBSERVING", "CONFIRM_PROMPT", "UNWANTED_ACCEL_GUIDE", "DECEL_FAILURE_GUIDE", "MANUAL_SELECTION", "HELP_CONFIRM", "STOPPED_REVIEW", "ENDED"],
    "Symptom": ["VIBRATION", "PULLING", "NOISE", "BURNING_SMELL", "PEDAL_PROBLEM", "TIRE_DAMAGE", "COLLISION", "POWER_LOSS", "UNKNOWN"],
    "BodyArea": ["FRONT", "FRONT_LEFT_TIRE", "FRONT_RIGHT_TIRE", "REAR_LEFT_TIRE", "REAR_RIGHT_TIRE", "CABIN", "REAR", "UNDERBODY", "UNKNOWN"],
    "LocationSource": ["VEHICLE", "PHONE"],
}
DECISION_DRIVING5 = ["VIBRATION", "BURNING_SMELL", "TIRE_DAMAGE", "COLLISION", "UNKNOWN"]  # 1차 9.1(이력). 2차 B9가 대신한다.
# 2차 결정 B9: 주행 중 상황 선택 5개 = 긴급 3(안 멈춰요·저절로 빨라져요 → PEDAL_PROBLEM, 사고가 났어요 → COLLISION) + 증상 2
R2_DRIVING_CHOICES = ["NOT_STOPPING", "SPEEDING_UP", "COLLISION", "VIBRATION", "BURNING_SMELL"]
R2_DRIVING_SELECTABLE = ["VIBRATION", "BURNING_SMELL", "COLLISION", "PEDAL_PROBLEM"]
DECISION_GUIDE_PATTERN = "^G_[A-Z_]+$"
DECISION_LOCATION_FIELDS = ["latitude", "longitude", "measured_at", "received_at", "location_confidence", "location_source"]
DECISION_SNAPSHOT_FIELDS = ["speed_mps", "brake_pressed", "steering_angle_deg", "turn_signal", "doors_open", "seat_occupied", "seatbelt_unbuckled", "airbag_deployed", "odometer_km", "fuel_or_soc_pct", "maintenance_flags"]
DECISION_MAINTENANCE_FLAGS = ["engine_oil", "brake_fluid", "washer_fluid_low", "power_down_warning"]
DECISION_PATHS = [
    ("post", "/v1/vehicles"), ("get", "/v1/incidents"), ("post", "/v1/trips"), ("post", "/v1/pairing/qr"), ("post", "/v1/pairing/confirm"),
    ("post", "/v1/guardians"), ("post", "/v1/guardians/{guardianId}/verify"), ("post", "/v1/guardians/{guardianId}/practice-alert"),
    ("post", "/v1/consents"), ("get", "/v1/consents"), ("get", "/v1/location-disclosures"), ("post", "/v1/incidents/{incidentId}/share-links"),
    ("post", "/v1/incidents/{incidentId}/corrections"), ("post", "/v1/guardian-replies"), ("get", "/v1/guide-bundles/{guide_id}"),
    ("post", "/v1/maintenance-notes"), ("get", "/v1/symptom-patterns"),
]
DECISION_SCREENS = ["홈", "경고등 찾기", "경고등 설명", "점검 항목", "출발 전 점검", "지금 기록", "재현 조건", "정비 노트", "상황 선택", "긴급 안내",
                    "감속 실패 대처", "비상 절차 미리 익히기", "발송 확인(10초)", "보호자 알림 상태", "정비소 들르기", "설정·동의", "휴대폰 연결", "시연 모드"]
# 2차 결정 G 화면 문구표 기준 화면 이름(1차 7장 18개 + 홈(주행 중)·확인 질문, '긴급 안내'는 '원치 않는 가속 안내')
R2_SCREENS = ["홈(정차)", "홈(주행 중)", "경고등 찾기", "경고등 설명", "점검 항목", "출발 전 점검", "지금 기록", "재현 조건", "정비 노트", "상황 선택", "확인 질문",
              "원치 않는 가속 안내", "감속 실패 대처", "비상 절차 미리 익히기", "발송 확인(10초)", "보호자 알림 상태", "정비소 들르기", "설정·동의", "휴대폰 연결", "시연 모드"]
# G 화면 문구표(화면, 왼쪽 버튼, 오른쪽 버튼)
R2_SCREEN_COPY = [
    ("홈(정차)", "지금 기록", "도움이 필요해요"), ("홈(주행 중)", "지금 기록", "도움이 필요해요"), ("상황 선택", "고른 증상 기록", "지금 도움 요청"),
    ("확인 질문", "괜찮아요", "안 멈춰요"), ("원치 않는 가속 안내", "괜찮아요(안내 끝내기)", "안 멈춰요(감속 실패 대처)"),
    ("감속 실패 대처", "차가 멈췄어요(안내 끝내기)", "도움 요청"), ("발송 확인(10초)", "괜찮아요(보내지 않음)", "지금 보내기"),
    ("보호자 알림 상태", "기록 보기", "공유 중지"), ("경고등 찾기", "문구로 찾기", "말로 찾기"), ("경고등 설명", "정차 후 자세히", "정비소 들르기"),
    ("출발 전 점검", "확인했어요", "정비소 들르기"), ("지금 기록", "기록 끝내기", "말로 메모"), ("재현 조건", "기록 목록", "정비소에 보여 주기"),
    ("비상 절차 미리 익히기", "그만하기", "다음 단계"), ("정비소 들르기", "내비에서 보기", "경유지로 추가"),
]
R2_RED_RIGHT = ["홈(정차)", "홈(주행 중)", "상황 선택", "확인 질문", "원치 않는 가속 안내", "감속 실패 대처", "발송 확인(10초)"]
# 2차 결정으로 더한 작업(D1·D3·D5·D6·H7·H8)
R2_PATHS = [
    ("post", "/v1/shares/{shareId}/session"), ("post", "/v1/shares/{shareId}/session/verify"),
    ("post", "/v1/driver-profiles"), ("get", "/v1/vehicles/{vehicleId}/driver-profiles"), ("put", "/v1/trips/{tripId}/driver"), ("post", "/v1/trips/{tripId}/end"),
    ("post", "/v1/incidents/{incidentId}/location-sharing/stop"), ("post", "/v1/share-links/{linkId}/revoke"), ("delete", "/v1/guardians/{guardianId}"),
    ("delete", "/v1/devices/{deviceId}"), ("delete", "/v1/me"),
    ("post", "/v1/pairing/{pairingId}/vehicle-confirm"), ("post", "/v1/vehicles/{vehicleId}/ownership-transfers"),
    ("post", "/v1/vehicles/{vehicleId}/ownership-transfers/{transferId}/cancel"), ("post", "/v1/time-sync"),
    ("put", "/v1/driver-profiles/{driverProfileId}/arrival-alert"), ("post", "/v1/trips/{tripId}/arrival"), ("post", "/v1/service-checkins"),
]
R2_ACCEPTANCE_AREAS = {"guardian_session": "D1", "demo_mode": "D2", "trip": "D3", "open_alert": "D4", "deletion": "D5", "pairing": "D6", "ownership": "D6",
                       "clock": "D6", "confirm_prompt": "F2", "voice_talk": "H1", "pattern": "H3", "arrival_alert": "H7", "service_checkin": "H8",
                       "no_map": "B11", "predrive": "B10", "permission": "B4", "button_side": "F1"}
DEMO_METHODS = ["VIRTUAL_CONSOLE", "TEST_INPUT", "MOCK_SERVER", "ADB", "MANUAL"]
FUTURE_SIGNAL_WORDS = ["TELLTALE", "IMU", "GYRO", "ACCEL_PEDAL", "GEAR", "PARKING_BRAKE", "TIRE_PRESSURE", "IGNITION", "HAZARD", "IMPACT", "RAW_AUDIO", "VIDEO"]

# OpenAPI enum(JSON 포인터) <-> SQL table.column. relation: equal = 같은 집합, subset = SQL이 부분집합
ENUM_PAIRS = [
    ("/components/schemas/Severity", "incidents", "severity", "equal"),
    ("/components/schemas/Availability", "guide_bundles", "availability", "equal"),
    ("/components/schemas/Availability", "diagnostic_mappings", "availability", "equal"),
    ("/components/schemas/InputSource", "observations", "source", "equal"),
    ("/components/schemas/InputSource", "evidence_assets", "source", "equal"),
    ("/components/schemas/ManualReport/properties/input_source/allOf/1", "manual_reports", "source", "equal"),
    ("/components/schemas/Quality", "observations", "quality", "equal"),
    ("/components/schemas/Quality", "incident_locations", "quality", "equal"),
    ("/components/schemas/PolicyState", "notification_policies", "state", "equal"),
    ("/components/schemas/PolicyOutcome", "notification_policies", "outcome", "equal"),
    ("/components/schemas/DeliveryState", "deliveries", "state", "equal"),
    ("/components/schemas/TransportGroup", "deliveries", "transport_group", "equal"),
    ("/components/schemas/DeliveryChannel", "deliveries", "channel", "equal"),
    ("/components/schemas/DriverAction", "incident_actions", "action", "equal"),
    ("/components/schemas/CorrectionReason", "incident_actions", "correction_reason", "equal"),
    ("/components/schemas/GuidanceState", "guidance_events", "guidance_state", "equal"),
    ("/components/schemas/GuidanceEntry", "guidance_events", "entry", "equal"),
    ("/components/schemas/GuidanceOutput", "guidance_events", "output", "equal"),
    ("/components/schemas/GuidanceOutputResult", "guidance_events", "result", "equal"),
    ("/components/schemas/Symptom", "manual_reports", "symptoms", "equal"),
    ("/components/schemas/BodyArea", "manual_reports", "body_area", "equal"),
    ("/components/schemas/LocationSource", "incident_locations", "location_source", "equal"),
    ("/components/schemas/AddressStatus", "incident_locations", "address_status", "equal"),
    ("/components/schemas/AddressSource", "incident_locations", "address_source", "equal"),
    ("/components/schemas/IncidentTrigger", "incidents", "incident_trigger", "equal"),
    ("/components/schemas/DriverResponse", "incidents", "driver_response", "equal"),
    ("/components/schemas/DriverMode", "incidents", "driver_mode", "equal"),
    ("/components/schemas/DriverMode", "trips", "driver_mode", "equal"),
    ("/components/schemas/DriverMode", "driver_profiles", "driver_mode", "equal"),
    ("/components/schemas/Lifecycle", "incidents", "lifecycle", "equal"),
    ("/components/schemas/SignalCode", "observations", "code", "equal"),
    ("/components/schemas/FreshnessBasis", "observations", "freshness_basis", "equal"),
    ("/components/schemas/GuideKind", "guide_bundles", "guide_kind", "equal"),
    ("/components/schemas/WarningColor", "guide_bundles", "warning_color", "equal"),
    ("/components/schemas/BlinkPattern", "guide_bundles", "blink_pattern", "equal"),
    ("/components/schemas/MobilityAction", "guide_bundles", "mobility_action", "equal"),
    ("/components/schemas/ConsentItem", "consents", "item", "equal"),
    ("/components/schemas/ConsentSubjectRole", "consents", "subject_role", "equal"),
    ("/components/schemas/GuardianState", "guardians", "state", "equal"),
    ("/components/schemas/GuardianReplyKind", "guardian_replies", "reply_kind", "equal"),
    ("/components/schemas/PairingState", "device_pairings", "state", "equal"),
    ("/components/schemas/DeviceRole", "devices", "device_role", "equal"),
    ("/components/schemas/AssetChannel", "evidence_assets", "channel", "equal"),
    ("/components/schemas/AssetState", "evidence_assets", "state", "equal"),
    ("/components/schemas/DisclosureChannel", "location_disclosures", "channel", "equal"),
    ("/components/schemas/DisclosurePurpose", "location_disclosures", "purpose", "equal"),
    ("/components/schemas/RecheckState", "maintenance_notes", "recheck_state", "equal"),
    ("/components/schemas/ClockConfidence", "notification_policies", "clock_confidence", "equal"),
    ("/components/schemas/ShareLinkScope", "share_links", "scope", "equal"),
    # 2차 결정(D1~D6·H6~H8)
    ("/components/schemas/DispatchMode", "notification_policies", "dispatch_mode", "equal"),
    ("/components/schemas/DispatchMode", "deliveries", "dispatch_mode", "equal"),
    ("/components/schemas/TripEndReason", "trips", "ended_reason", "equal"),
    ("/components/schemas/ConsentScope", "consents", "scope", "equal"),
    ("/components/schemas/DriverMode", "consents", "driver_mode", "equal"),
    ("/components/schemas/DriverMode", "arrival_alert_settings", "driver_mode", "equal"),
    ("/components/schemas/ShareSessionStage", "guardian_share_sessions", "stage", "equal"),
    ("/components/schemas/TransferState", "ownership_transfers", "state", "equal"),
    ("/components/schemas/TransferInitiator", "ownership_transfers", "initiated_by", "equal"),
    ("/components/schemas/OwnershipTransferCreate/properties/reason", "ownership_transfers", "reason", "equal"),
    ("/components/schemas/PairingRole", "device_pairings", "requested_role", "equal"),
    ("/components/schemas/LocationPrecision", "location_disclosures", "location_precision", "equal"),
    ("/components/schemas/CheckinDetection", "service_checkins", "detected_by", "equal"),
]
REGEX_PAIRS = [
    ("/components/schemas/GuideId", "guide_bundles", "guide_id"),
    ("/components/schemas/GuideId", "guidance_events", "guide_id"),
    ("/components/schemas/GuideId", "diagnostic_mappings", "guide_id"),
]


class Report:
    def __init__(self):
        self.checks = []
        self.errors = []
        self.warnings = []

    def check(self, name, ok, detail=None):
        self.checks.append({"name": name, "status": "PASS" if ok else "FAIL", **({"detail": detail} if detail is not None else {})})
        if not ok:
            self.errors.append(name + (": " + str(detail) if detail is not None else ""))
        return ok

    def warn(self, msg):
        self.warnings.append(msg)


def path(*p):
    return os.path.join(HERE, *p)


def sha256_file(p):
    with open(p, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def load_json(rep, rel):
    try:
        with open(path(rel), encoding="utf-8") as f:
            data = json.load(f)
        rep.check("json_parse:" + rel, True)
        return data
    except Exception as e:  # noqa: BLE001
        rep.check("json_parse:" + rel, False, repr(e))
        return None


def pointer(doc, ptr):
    node = doc
    for part in ptr.lstrip("/").split("/"):
        part = part.replace("~1", "/").replace("~0", "~")
        if isinstance(node, list):
            node = node[int(part)]
        else:
            node = node[part]
    return node


def walk_refs(node, out):
    if isinstance(node, dict):
        for k, v in node.items():
            if k == "$ref" and isinstance(v, str):
                out.append(v)
            else:
                walk_refs(v, out)
    elif isinstance(node, list):
        for v in node:
            walk_refs(v, out)


def walk_property_names(node, out):
    if isinstance(node, dict):
        props = node.get("properties")
        if isinstance(props, dict):
            out.extend(props.keys())
        for v in node.values():
            walk_property_names(v, out)
    elif isinstance(node, list):
        for v in node:
            walk_property_names(v, out)


# ---------------------------------------------------------------- JSON Schema 부분 검사기
def jtype(v):
    if v is None:
        return "null"
    if isinstance(v, bool):
        return "boolean"
    if isinstance(v, int):
        return "integer"
    if isinstance(v, float):
        return "number"
    if isinstance(v, str):
        return "string"
    if isinstance(v, list):
        return "array"
    if isinstance(v, dict):
        return "object"
    return "unknown"


def type_ok(v, t):
    jt = jtype(v)
    types = t if isinstance(t, list) else [t]
    return jt in types or (jt == "integer" and "number" in types)


def sv(inst, schema, root, where="$"):
    """Return list of error strings. Supports keywords used in this contract."""
    errs = []
    if schema is True or schema == {}:
        return errs
    if "$ref" in schema:
        errs += sv(inst, pointer(root, schema["$ref"][1:]), root, where)
    if "type" in schema and not type_ok(inst, schema["type"]):
        return errs + [f"{where}: type {jtype(inst)} not in {schema['type']}"]
    if "const" in schema and inst != schema["const"]:
        errs.append(f"{where}: const {schema['const']!r}")
    if "enum" in schema and inst not in schema["enum"]:
        errs.append(f"{where}: {inst!r} not in enum")
    for sub in schema.get("allOf", []):
        errs += sv(inst, sub, root, where)
    if "anyOf" in schema and not any(not sv(inst, s, root, where) for s in schema["anyOf"]):
        errs.append(f"{where}: anyOf no match")
    if "not" in schema and not sv(inst, schema["not"], root, where):
        errs.append(f"{where}: matches not")
    if "if" in schema:
        if not sv(inst, schema["if"], root, where):
            if "then" in schema:
                errs += sv(inst, schema["then"], root, where)
        elif "else" in schema:
            errs += sv(inst, schema["else"], root, where)
    if isinstance(inst, dict):
        props = schema.get("properties", {})
        for r in schema.get("required", []):
            if r not in inst:
                errs.append(f"{where}: missing {r}")
        if schema.get("additionalProperties") is False:
            for k in inst:
                if k not in props:
                    errs.append(f"{where}: additional property {k}")
        for k, s in props.items():
            if k in inst:
                errs += sv(inst[k], s, root, f"{where}.{k}")
    if isinstance(inst, list):
        if "minItems" in schema and len(inst) < schema["minItems"]:
            errs.append(f"{where}: minItems")
        if "maxItems" in schema and len(inst) > schema["maxItems"]:
            errs.append(f"{where}: maxItems")
        if schema.get("uniqueItems") and len({json.dumps(x, sort_keys=True) for x in inst}) != len(inst):
            errs.append(f"{where}: uniqueItems")
        if "items" in schema:
            for i, x in enumerate(inst):
                errs += sv(x, schema["items"], root, f"{where}[{i}]")
        if "contains" in schema and not any(not sv(x, schema["contains"], root, where) for x in inst):
            errs.append(f"{where}: contains no match")
    if isinstance(inst, str):
        if "minLength" in schema and len(inst) < schema["minLength"]:
            errs.append(f"{where}: minLength")
        if "maxLength" in schema and len(inst) > schema["maxLength"]:
            errs.append(f"{where}: maxLength")
        if "pattern" in schema and not re.search(schema["pattern"], inst):
            errs.append(f"{where}: pattern")
    if jtype(inst) in ("integer", "number"):
        if "minimum" in schema and inst < schema["minimum"]:
            errs.append(f"{where}: minimum")
        if "maximum" in schema and inst > schema["maximum"]:
            errs.append(f"{where}: maximum")
    return errs


T = "2026-11-12T12:00:00Z"
U = ["00000000-0000-0000-0000-00000000000" + str(i) for i in range(10)]


def rd(v, q="OK", src="VEHICLE_SDK"):
    return {"value": v, "quality": q, "received_at": T, "source": src}


def seat(area, v, q="OK", src="VEHICLE_SDK"):
    return {"area": area, "value": v, "quality": q, "received_at": T, "source": src}


def sample_snapshot():
    return {
        "captured_at": T, "speed_mps": rd(16.7, "TEST_INPUT", "TEST_INPUT"), "brake_pressed": rd(False), "steering_angle_deg": rd(2.5),
        "turn_signal": rd("OFF"), "doors_open": [seat("ROW_1_LEFT", True)], "seat_occupied": [seat("ROW_1_LEFT", True), seat("ROW_1_RIGHT", True)],
        "seatbelt_unbuckled": [seat("ROW_1_LEFT", False, "TEST_INPUT", "TEST_INPUT")], "airbag_deployed": [seat("ROW_1_LEFT", True, "TEST_INPUT", "TEST_INPUT")],
        "odometer_km": rd(32140.5), "fuel_or_soc_pct": {"kind": "FUEL", "value": 54, "quality": "TEST_INPUT", "received_at": T, "source": "TEST_INPUT"},
        "maintenance_flags": {"engine_oil": rd("NORMAL", "TEST_INPUT", "TEST_INPUT"), "brake_fluid": rd("NORMAL", "TEST_INPUT", "TEST_INPUT"),
                              "washer_fluid_low": rd(False, "TEST_INPUT", "TEST_INPUT"), "power_down_warning": rd(None, "UNAVAILABLE")},
    }


def sample_incident():
    return {
        "client_incident_id": U[1], "vehicle_id": U[2], "device_id": U[3], "trip_id": U[4], "driver_profile_id": U[5], "driver_mode": "OWNER",
        "occurred_at": T, "incident_trigger": "AIRBAG_DEPLOYED", "rule_profile_id": "pleos-care-lab-20261112-r3", "severity": "L3_URGENT",
        "severity_reasons": ["NEW_AIRBAG_DEPLOYMENT"], "vehicle_snapshot": sample_snapshot(),
        "observations": [{"sequence": 0, "code": "AIRBAG_DEPLOYED", "area": "ROW_1_LEFT", "source": "TEST_INPUT", "quality": "TEST_INPUT",
                          "measured_at": None, "received_at": T, "freshness_basis": "ARRIVAL_ONLY", "unit": None, "value": True}],
        "manual_report": None,
        "location": {"client_location_id": U[6], "latitude": 37.5665, "longitude": 126.978, "measured_at": T, "received_at": T,
                     "location_confidence": 0.8, "location_source": "VEHICLE", "quality": "OK"},
        "driver_response": "NOT_REQUESTED",
        "notification": {"guardian_ids": [U[7]], "requested_state": "COUNTDOWN", "countdown_remaining_ms": 10000, "countdown_started_at": T,
                         "elapsed_ms_at_submit": 0, "clock_confidence": "SYNCHRONIZED", "clock_domain": "server-synced", "clock_error_bound_ms": 120,
                         "cancel_action_id": None, "recipients_shown_at": None, "clock_sync": {"offset_ms": -35, "rtt_ms": 180, "synced_at": T}},
        "contains_test_input": True,
        "test_mode": True,
    }


def real_snapshot():
    """시험 입력이 하나도 없는 실제 값 스냅샷."""
    sn = sample_snapshot()

    def fix(v):
        if isinstance(v, dict) and v.get("source") == "TEST_INPUT":
            v["source"], v["quality"] = ("AAOS_PROPERTY", "OK") if v is sn["speed_mps"] else ("VEHICLE_SDK", "OK")
        return v
    for k, v in sn.items():
        if isinstance(v, list):
            for x in v:
                fix(x)
        elif isinstance(v, dict) and k == "maintenance_flags":
            for x in v.values():
                fix(x)
        else:
            fix(v)
    return sn


def mutate(obj, fn):
    o = json.loads(json.dumps(obj))
    fn(o)
    return o


def schema_examples():
    inc = sample_incident()
    real = mutate(inc, lambda o: (o.update({"vehicle_snapshot": real_snapshot(), "test_mode": False, "contains_test_input": False}),
                                  o["observations"][0].update({"source": "VEHICLE_SDK", "quality": "OK"})))
    mr = {"client_report_id": U[1], "symptoms": ["PULLING"], "body_area": "FRONT_LEFT_TIRE", "input_source": "USER_SELECTION", "memo_text": None,
          "selected_while_driving": False, "reported_stopped": None, "request_help": False, "selected_at": T}
    ge = {"client_event_id": U[1], "guide_id": "G_DECEL_FAILURE", "guide_version": 1, "step_id": "S1", "guidance_state": "DECEL_FAILURE_GUIDE",
          "entry": "USER_BUTTON", "occurred_at": T, "output": "AUDIO", "result": "STARTED", "error_code": None}
    act = {"client_action_id": U[1], "expected_version": 1, "action": "CANCEL", "occurred_at": T, "recipients_shown_guardian_ids": [], "countdown_elapsed_ms": 4200}
    dl = {"delivery_id": U[1], "guardian_id": U[2], "guardian_display_name": "딸", "channel": "SMS", "transport_group": "TEXT", "revision": 1,
          "is_correction": False, "is_practice": False, "is_arrival": False, "dispatch_mode": "MOCK", "supersedes_delivery_id": None,
          "state": "DELIVERED", "accepted_at": T, "delivered_at": T, "opened_at": None, "fallback_deadline_at": None}
    ack = {"incident_id": U[1], "version": 2, "accepted_at": T, "severity": "L3_URGENT", "notification_state": "SETTLED", "notification_outcome": "PARTIAL",
           "notification_revision": 1, "release_at": T, "timing_uncertain": False, "test_mode": True, "related_incident_id": None,
           "alert_incident_id": U[1], "dispatch_mode": "MOCK", "server_time": T}
    step = {"step_id": "S1", "ordinal": 1, "text_lines": ["전자식 파킹 브레이크 스위치를 계속 당기세요"], "still_image_asset_id": "img-epb",
            "parked_animation_asset_id": None, "motion_while_driving": False, "tts_line_ids": ["L1"]}
    tts = {"line_id": "L1", "text": "예시 문장", "leading_pause_text": None, "bundled_audio_asset_id": None, "bundled_audio_preroll_silence_ms": None,
           "audio_usage_candidates": ["USAGE_ASSISTANCE_NAVIGATION_GUIDANCE"]}
    obs = inc["observations"][0]
    help_inc = mutate(inc, lambda o: (o.update({"incident_trigger": "USER_HELP_REQUEST", "severity": "L3_URGENT"}),
                                      o["notification"].update({"requested_state": "RELEASED", "recipients_shown_at": T})))
    consent = {"client_consent_id": U[1], "driver_profile_id": U[2], "subject_role": "DRIVER", "item": "LOCATION_SHARE_GUARDIAN", "granted": True,
               "policy_version": "2026-11", "decided_at": T, "scope": "STANDING", "trip_id": None, "recorded_while_parked": True}
    drv = {"client_change_id": U[1], "driver_profile_id": None, "driver_mode": "GUEST", "chip_answer": "SELECTED", "changed_while_parked": True, "occurred_at": T}
    arrival = {"enabled": True, "guardian_ids": [U[3]], "updated_while_parked": True}
    transfer = {"client_transfer_id": U[1], "initiated_by": "VEHICLE_SCREEN", "reason": "SALE", "requested_while_parked": True}
    return [
        ("IncidentCreate", "에어백 COUNTDOWN(시험 입력, test_mode=true)", inc, True),
        ("IncidentCreate", "실제 값만, test_mode=false", real, True),
        ("IncidentCreate", "USER_SYMPTOM이 COUNTDOWN 요청", mutate(inc, lambda o: o.update({"incident_trigger": "USER_SYMPTOM"})), False),
        ("IncidentCreate", "USER_HELP_REQUEST가 COUNTDOWN 요청", mutate(inc, lambda o: o.update({"incident_trigger": "USER_HELP_REQUEST"})), False),
        ("IncidentCreate", "도움 요청 RELEASED(받는 사람 확인)", help_inc, True),
        ("IncidentCreate", "도움 요청 RELEASED인데 recipients_shown_at 없음", mutate(help_inc, lambda o: o["notification"].update({"recipients_shown_at": None})), False),
        ("IncidentCreate", "TEST_INPUT 차속인데 test_mode=false", mutate(inc, lambda o: o.update({"test_mode": False, "contains_test_input": False})), False),
        ("IncidentCreate", "D2: 에어백만 시험 입력인데 test_mode=false", mutate(real, lambda o: o["vehicle_snapshot"]["airbag_deployed"][0].update({"source": "TEST_INPUT", "quality": "TEST_INPUT"})), False),
        ("IncidentCreate", "D2: 워셔액만 시험 입력인데 test_mode=false", mutate(real, lambda o: o["vehicle_snapshot"]["maintenance_flags"]["washer_fluid_low"].update({"source": "TEST_INPUT", "quality": "TEST_INPUT"})), False),
        ("IncidentCreate", "D2: 관측만 시험 입력인데 test_mode=false", mutate(real, lambda o: o["observations"][0].update({"source": "TEST_INPUT", "quality": "TEST_INPUT"})), False),
        ("IncidentCreate", "D2: contains_test_input=true인데 test_mode=false", mutate(real, lambda o: o.update({"contains_test_input": True})), False),
        ("IncidentCreate", "D6: SYNCHRONIZED인데 clock_sync 없음", mutate(inc, lambda o: o["notification"].update({"clock_sync": None})), False),
        ("IncidentCreate", "D6: clock_sync 왕복 1,500ms", mutate(inc, lambda o: o["notification"]["clock_sync"].update({"rtt_ms": 1500})), False),
        ("IncidentCreate", "옛 trigger 필드", mutate(inc, lambda o: o.update({"trigger": "AIRBAG_DEPLOYED"})), False),
        ("Location", "accuracy_m 추가", mutate(inc["location"], lambda o: o.update({"accuracy_m": 25})), False),
        ("Location", "위도 91", mutate(inc["location"], lambda o: o.update({"latitude": 91})), False),
        ("Location", "좌표 한쪽만", mutate(inc["location"], lambda o: o.update({"longitude": None, "quality": "UNAVAILABLE"})), False),
        ("ManualReport", "정차 중 PULLING", mr, True),
        ("ManualReport", "주행 중 카드 PULLING(5개 밖)", mutate(mr, lambda o: o.update({"selected_while_driving": True})), False),
        ("ManualReport", "주행 중 카드 VIBRATION(차체 떨림)", mutate(mr, lambda o: o.update({"selected_while_driving": True, "symptoms": ["VIBRATION"]})), True),
        ("ManualReport", "B9: 주행 중 카드 PEDAL_PROBLEM(안 멈춰요)", mutate(mr, lambda o: o.update({"selected_while_driving": True, "symptoms": ["PEDAL_PROBLEM"]})), True),
        ("ManualReport", "B9: 주행 중 카드 TIRE_DAMAGE(1차 5개였으나 제외)", mutate(mr, lambda o: o.update({"selected_while_driving": True, "symptoms": ["TIRE_DAMAGE"]})), False),
        ("ManualReport", "H1: 주행 중 말로 하기 PULLING", mutate(mr, lambda o: o.update({"selected_while_driving": True, "input_source": "STT", "memo_text": "쏠려요"})), True),
        ("ManualReport", "옛 이름 SHAKE", mutate(mr, lambda o: o.update({"symptoms": ["SHAKE"]})), False),
        ("ManualReport", "증상 4개", mutate(mr, lambda o: o.update({"symptoms": ["NOISE", "PULLING", "VIBRATION", "UNKNOWN"]})), False),
        ("ManualReport", "옛 부위 FRONT_TIRE", mutate(mr, lambda o: o.update({"body_area": "FRONT_TIRE"})), False),
        ("GuidanceEvent", "감속 실패 안내(버튼 진입)", ge, True),
        ("GuidanceEvent", "감속 실패 안내인데 entry 없음", mutate(ge, lambda o: o.update({"entry": None})), False),
        ("GuidanceEvent", "guide_id 옛 형식", mutate(ge, lambda o: o.update({"guide_id": "brake-example-v1"})), False),
        ("IncidentActionRequest", "CANCEL", act, True),
        ("IncidentActionRequest", "SEND_CORRECTION은 corrections 경로만", mutate(act, lambda o: o.update({"action": "SEND_CORRECTION"})), False),
        ("IncidentActionRequest", "REQUEST_HELP 받는 사람 없음", mutate(act, lambda o: o.update({"action": "REQUEST_HELP"})), False),
        ("IncidentActionRequest", "옛 REPORT_STOPPED", mutate(act, lambda o: o.update({"action": "REPORT_STOPPED"})), False),
        ("Delivery", "문자 전달", dl, True),
        ("Delivery", "SMS인데 transport_group KAKAO", mutate(dl, lambda o: o.update({"transport_group": "KAKAO"})), False),
        ("Delivery", "옛 상태 QUEUED", mutate(dl, lambda o: o.update({"state": "QUEUED"})), False),
        ("Delivery", "D2: 발송 방식 이름 밖", mutate(dl, lambda o: o.update({"dispatch_mode": "LIVE"})), False),
        ("IncidentAck", "SETTLED/PARTIAL", ack, True),
        ("IncidentAck", "SETTLED인데 outcome 없음", mutate(ack, lambda o: o.update({"notification_outcome": None})), False),
        ("IncidentAck", "RELEASED인데 outcome 있음", mutate(ack, lambda o: o.update({"notification_state": "RELEASED"})), False),
        ("IncidentAck", "D4: alert_incident_id 빠짐", mutate(ack, lambda o: o.pop("alert_incident_id")), False),
        ("GuideStep", "정지 그림 단계", step, True),
        ("GuideStep", "주행 중 움직임 true", mutate(step, lambda o: o.update({"motion_while_driving": True})), False),
        ("GuideStep", "글 4줄", mutate(step, lambda o: o.update({"text_lines": ["가", "나", "다", "라"]})), False),
        ("TtsLine", "TTS 문장(무음 값 없음)", tts, True),
        ("TtsLine", "B5: 잘림 대비 쉼표 문구", mutate(tts, lambda o: o.update({"leading_pause_text": "자,"})), True),
        ("TtsLine", "B5: 앱 음원 없이 무음 180ms", mutate(tts, lambda o: o.update({"bundled_audio_preroll_silence_ms": 180})), False),
        ("TtsLine", "앱 고정 음원 무음 180ms", mutate(tts, lambda o: o.update({"bundled_audio_asset_id": "a-epb", "bundled_audio_preroll_silence_ms": 180})), True),
        ("TtsLine", "앱 고정 음원 무음 100ms", mutate(tts, lambda o: o.update({"bundled_audio_asset_id": "a-epb", "bundled_audio_preroll_silence_ms": 100})), False),
        ("TtsLine", "옛 preroll_silence_ms", mutate(tts, lambda o: o.update({"preroll_silence_ms": 180})), False),
        ("TtsLine", "EMERGENCY 용도", mutate(tts, lambda o: o.update({"audio_usage_candidates": ["USAGE_EMERGENCY"]})), False),
        ("Observation", "TEST_INPUT인데 quality OK", mutate(obs, lambda o: o.update({"quality": "OK"})), False),
        ("Observation", "경고등 상태 코드", mutate(obs, lambda o: o.update({"code": "TELLTALE_STATE"})), False),
        ("Observation", "H3: 풍량 관측", mutate(obs, lambda o: o.update({"code": "CLIMATE_FAN_SPEED", "source": "VEHICLE_SDK", "quality": "OK", "value": 3, "area": "ROW_1_LEFT"})), True),
        ("ConsentRequest", "정차 중 상시 위치 동의", consent, True),
        ("ConsentRequest", "D3: 이번 trip 동의인데 trip_id 없음", mutate(consent, lambda o: o.update({"scope": "TRIP"})), False),
        ("ConsentRequest", "D3: 위치 동의를 주행 중 기록", mutate(consent, lambda o: o.update({"recorded_while_parked": False})), False),
        ("ConsentRequest", "D3: 위치 동의 철회는 주행 중에도", mutate(consent, lambda o: o.update({"granted": False, "recorded_while_parked": False})), True),
        ("TripDriverChange", "정차 중 게스트 지정", drv, True),
        ("TripDriverChange", "D3: 주행 중 운전자 변경", mutate(drv, lambda o: o.update({"changed_while_parked": False})), False),
        ("TripDriverChange", "D3: OWNER인데 프로필 없음", mutate(drv, lambda o: o.update({"driver_mode": "OWNER"})), False),
        ("TripEndRequest", "D3: 기기가 LONG_PARKED로 종료", {"client_end_id": U[1], "reason": "LONG_PARKED", "ended_at": T}, False),
        ("PairingVehicleConfirm", "D6: 주행 중 차 화면 확인", {"decision": "ACCEPT", "shown_phone_label_masked": "010-****-1234", "confirmed_while_parked": False, "occurred_at": T}, False),
        ("GuardianShareSessionVerify", "D1: 번호 뒷자리 5자리", {"share_session_id": U[1], "token": "t" * 20, "phone_last4": "12345"}, False),
        ("GuardianShareSessionVerify", "D1: 번호 뒷자리 4자리", {"share_session_id": U[1], "token": "t" * 20, "phone_last4": "1234"}, True),
        ("ArrivalAlertSettingUpdate", "H7: 받는 사람 1명 켜기", arrival, True),
        ("ArrivalAlertSettingUpdate", "H7: 받는 사람 없이 켜기", mutate(arrival, lambda o: o.update({"guardian_ids": []})), False),
        ("TripArrivalReport", "H7: 도착 알림에 위치 포함", {"client_arrival_id": U[1], "arrived_at": T, "trigger": "NAVI_DESTINATION_ARRIVED", "includes_location": True}, False),
        ("OwnershipTransferCreate", "D6: 차 화면 시작(정차 중)", transfer, True),
        ("OwnershipTransferCreate", "D6: 차 화면 시작(주행 중)", mutate(transfer, lambda o: o.update({"requested_while_parked": False})), False),
        ("ServiceCheckinCreate", "H8: 좌표를 담은 체크인", {"client_checkin_id": U[1], "vehicle_id": U[2], "trip_id": None, "shop_ref": "poi-1", "shop_display_name": "블루핸즈",
                                                      "detected_by": "FUSED_PROXIMITY_ENTER", "arrived_at": T, "detected_while_parked": True, "latitude": 37.5}, False),
    ]


# ---------------------------------------------------------------- SQL 파싱
def strip_sql_comments(sql):
    return "\n".join(line.split("--", 1)[0] for line in sql.splitlines())


def parse_sql(sql):
    clean = strip_sql_comments(sql)
    tables = {}
    for m in re.finditer(r"CREATE TABLE (\w+) \((.*?)\n\);", clean, re.S):
        name, body = m.group(1), m.group(2)
        cols, enums, regex, arrays = [], {}, {}, {}
        for line in body.split("\n"):
            s = line.strip().rstrip(",")
            if not s:
                continue
            first = s.split()[0]
            if first.upper() in ("CONSTRAINT", "UNIQUE", "CHECK", "PRIMARY", "FOREIGN"):
                for am in re.finditer(r"(\w+) <@ ARRAY\[([^\]]*)\]", s):
                    arrays.setdefault(am.group(1), []).append(re.findall(r"'([^']*)'", am.group(2)))
                continue
            cols.append(first)
            im = re.search(r"CHECK \(" + re.escape(first) + r" IN \(([^)]*)\)\)", s)
            if im:
                enums[first] = re.findall(r"'([^']*)'", im.group(1))
            am = re.search(re.escape(first) + r" <@ ARRAY\[([^\]]*)\]", s)
            if am:
                arrays.setdefault(first, []).append(re.findall(r"'([^']*)'", am.group(1)))
            rm = re.search(re.escape(first) + r" ~ '([^']*)'", s)
            if rm:
                regex[first] = rm.group(1)
        for col, lists in arrays.items():
            biggest = max(lists, key=len)
            enums.setdefault(col, biggest)
        tables[name] = {"columns": cols, "enums": enums, "regex": regex, "arrays": arrays, "body": body}
    fks = re.findall(r"REFERENCES (\w+)\(", clean)
    views = re.findall(r"CREATE VIEW (\w+)", clean)
    indexes = re.findall(r"CREATE (?:UNIQUE )?INDEX (\w+)", clean)
    unique_indexes = re.findall(r"CREATE UNIQUE INDEX (\w+)", clean)
    checks = len(re.findall(r"\bCHECK \(", clean))
    return {"tables": tables, "fks": fks, "views": views, "indexes": indexes, "unique_indexes": unique_indexes, "checks": checks, "clean": clean}


# ---------------------------------------------------------------- 대비
def lum(h):
    h = h.lstrip("#")
    r, g, b = [int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    f = lambda c: c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4  # noqa: E731
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)


def contrast(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def rects_overlap(a, b):
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    return ax < bx + bw and bx < ax + aw and ay < by + bh and by < ay + ah


def get_path(doc, dotted):
    node = doc
    for part in dotted.split("."):
        if isinstance(node, dict) and part in node:
            node = node[part]
        else:
            raise KeyError(dotted)
    return node


# ---------------------------------------------------------------- PostgreSQL(선택)
PG_FIXTURE = """
INSERT INTO app_users(user_id) VALUES ('00000000-0000-0000-0000-0000000000a1');
INSERT INTO vehicle_profiles(profile_id, model_code, model_year, powertrain) VALUES ('AVANTE_TEST','TEST',2026,'ICE');
INSERT INTO vehicles(vehicle_id, client_vehicle_id, owner_id, profile_id, model_display) VALUES ('00000000-0000-0000-0000-0000000000b1','00000000-0000-0000-0000-0000000000b2','00000000-0000-0000-0000-0000000000a1','AVANTE_TEST','아반떼');
INSERT INTO driver_profiles(driver_profile_id, owner_id, vehicle_id, user_id, driver_mode, display_name) VALUES ('00000000-0000-0000-0000-0000000000c1','00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-0000000000b1','00000000-0000-0000-0000-0000000000a1','OWNER','운전자');
INSERT INTO devices(device_id, owner_id, vehicle_id, device_role) VALUES ('00000000-0000-0000-0000-0000000000d1','00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-0000000000b1','IVI');
INSERT INTO rule_profiles(rule_profile_id, config, sha256) VALUES ('pleos-care-lab-20261112-r2','{}', repeat('a',64));
INSERT INTO incidents(incident_id, client_incident_id, owner_id, vehicle_id, first_device_id, driver_mode, rule_profile_id, request_sha256, incident_trigger, driver_response, vehicle_snapshot, occurred_at, severity) VALUES
 ('00000000-0000-0000-0000-0000000000e1','00000000-0000-0000-0000-0000000000e2','00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-0000000000b1','00000000-0000-0000-0000-0000000000d1','OWNER','pleos-care-lab-20261112-r2',repeat('b',64),'AIRBAG_DEPLOYED','NOT_REQUESTED','{"speed_mps":{"value":19.4,"quality":"TEST_INPUT"}}','2026-11-12T12:00:00Z','L3_URGENT'),
 ('00000000-0000-0000-0000-0000000000e3','00000000-0000-0000-0000-0000000000e4','00000000-0000-0000-0000-0000000000a1','00000000-0000-0000-0000-0000000000b1','00000000-0000-0000-0000-0000000000d1','OWNER','pleos-care-lab-20261112-r2',repeat('c',64),'USER_COLLISION','NOT_REQUESTED','{"speed_mps":{"value":16.7,"quality":"TEST_INPUT"}}','2026-11-12T12:05:00Z','L3_URGENT');
INSERT INTO guardians(guardian_id, user_id, display_name, phone_ciphertext, phone_lookup_hmac, state) VALUES ('00000000-0000-0000-0000-0000000000f1','00000000-0000-0000-0000-0000000000a1','보호자','\\x00','h1','PENDING_VERIFICATION');
INSERT INTO notification_policies(incident_id, vehicle_id, state, clock_confidence, countdown_clock, release_at, expires_at) VALUES ('00000000-0000-0000-0000-0000000000e1','00000000-0000-0000-0000-0000000000b1','COUNTDOWN','SYNCHRONIZED','{}', now()+interval '10 seconds', now()+interval '1 day');
"""
I1, I2, V1, D1, DEV1, G1 = ("00000000-0000-0000-0000-0000000000e1", "00000000-0000-0000-0000-0000000000e3", "00000000-0000-0000-0000-0000000000b1",
                            "00000000-0000-0000-0000-0000000000c1", "00000000-0000-0000-0000-0000000000d1", "00000000-0000-0000-0000-0000000000f1")


def mr_sql(sym, driving, cid):
    return (f"INSERT INTO manual_reports(report_id, incident_id, client_report_id, symptoms, body_area, selected_while_driving, selected_at) "
            f"VALUES (gen_random_uuid(), '{I1}', '{cid}', ARRAY[{','.join(repr(s) for s in sym)}]::text[], 'FRONT_LEFT_TIRE', {str(driving).lower()}, now())")


PG_SMOKE = [
    ("같은 차량 두 번째 열린 알림(RELEASED) 거부", f"INSERT INTO notification_policies(incident_id, vehicle_id, state, clock_confidence, countdown_clock, release_at, expires_at) VALUES ('{I2}','{V1}','RELEASED','UNKNOWN','{{}}', now(), now()+interval '1 day')", False),
    ("같은 차량 두 번째 RECORD_ONLY 허용", f"INSERT INTO notification_policies(incident_id, vehicle_id, state, clock_confidence, countdown_clock, expires_at) VALUES ('{I2}','{V1}','RECORD_ONLY','UNKNOWN','{{}}', now()+interval '1 day')", True),
    ("SETTLED인데 outcome 없음 거부", f"INSERT INTO notification_policies(incident_id, vehicle_id, state, clock_confidence, countdown_clock, settled_at, expires_at) VALUES ('{I2}','{V1}','SETTLED','UNKNOWN','{{}}', now(), now()+interval '1 day')", False),
    ("SETTLED/PARTIAL 허용", f"INSERT INTO notification_policies(incident_id, vehicle_id, state, outcome, clock_confidence, countdown_clock, settled_at, expires_at) VALUES ('{I2}','{V1}','SETTLED','PARTIAL','UNKNOWN','{{}}', now(), now()+interval '1 day')", True),
    ("옛 정책 상태 이름 거부", f"INSERT INTO notification_policies(incident_id, vehicle_id, state, clock_confidence, countdown_clock, expires_at) VALUES ('{I2}','{V1}','DELIVERED','UNKNOWN','{{}}', now()+interval '1 day')", False),
    ("주행 중 PULLING 거부", mr_sql(["PULLING"], True, "00000000-0000-0000-0000-000000000101"), False),
    ("정차 중 PULLING 허용", mr_sql(["PULLING"], False, "00000000-0000-0000-0000-000000000102"), True),
    ("옛 증상 SHAKE 거부", mr_sql(["SHAKE"], False, "00000000-0000-0000-0000-000000000103"), False),
    ("보호자 6번째 자리 거부", f"INSERT INTO vehicle_guardians(driver_profile_id, vehicle_id, guardian_id, slot) VALUES ('{D1}','{V1}','{G1}',6)", False),
    ("보호자 1번 자리 허용", f"INSERT INTO vehicle_guardians(driver_profile_id, vehicle_id, guardian_id, slot) VALUES ('{D1}','{V1}','{G1}',1)", True),
    ("guide_id 옛 형식 거부", "INSERT INTO guide_bundles(guide_id, version, vehicle_profile_id, guide_kind, availability, source_manual_ref, title, steps_json, content_sha256) VALUES ('brake-example-v1',1,'AVANTE_TEST','EMERGENCY','DRAFT_DEMO','예시','예시','[{}]',repeat('d',64))", False),
    ("G_DECEL_FAILURE DRAFT_DEMO 허용", "INSERT INTO guide_bundles(guide_id, version, vehicle_profile_id, guide_kind, availability, source_manual_ref, title, steps_json, content_sha256) VALUES ('G_DECEL_FAILURE',1,'AVANTE_TEST','EMERGENCY','DRAFT_DEMO','예시','감속 실패 대처','[{}]',repeat('d',64))", True),
    ("검토자 없는 VERIFIED 거부", "INSERT INTO guide_bundles(guide_id, version, vehicle_profile_id, guide_kind, availability, source_manual_ref, title, steps_json, content_sha256) VALUES ('G_STOP_SAFE',1,'AVANTE_TEST','EMERGENCY','VERIFIED','예시','정차','[{}]',repeat('d',64))", False),
    ("정비사 링크 25시간 거부", f"INSERT INTO share_links(link_id, client_link_id, incident_id, token_hash, scope, expires_at) VALUES (gen_random_uuid(), gen_random_uuid(), '{I1}', 't1', ARRAY['SUMMARY'], now()+interval '25 hours')", False),
    ("정비사 링크 24시간 허용", f"INSERT INTO share_links(link_id, client_link_id, incident_id, token_hash, scope, expires_at) VALUES (gen_random_uuid(), gen_random_uuid(), '{I1}', 't2', ARRAY['SUMMARY'], now()+interval '24 hours')", True),
    ("GUEST 자동 위치 발송 거부", f"INSERT INTO trips(trip_id, client_trip_id, vehicle_id, driver_mode, auto_location_send_enabled, started_at) VALUES (gen_random_uuid(), gen_random_uuid(), '{V1}', 'GUEST', true, now())", False),
    ("감속 실패 안내 진입 없음 거부", f"INSERT INTO guidance_events(guidance_event_id, incident_id, source_device_id, client_event_id, guide_id, guide_version, step_id, guidance_state, output, result, occurred_at) VALUES (gen_random_uuid(), '{I1}', '{DEV1}', gen_random_uuid(), 'G_DECEL_FAILURE', 1, 'S1', 'DECEL_FAILURE_GUIDE', 'AUDIO', 'STARTED', now())", False),
    ("감속 실패 안내 버튼 진입 허용", f"INSERT INTO guidance_events(guidance_event_id, incident_id, source_device_id, client_event_id, guide_id, guide_version, step_id, guidance_state, entry, output, result, occurred_at) VALUES (gen_random_uuid(), '{I1}', '{DEV1}', gen_random_uuid(), 'G_DECEL_FAILURE', 1, 'S1', 'DECEL_FAILURE_GUIDE', 'USER_BUTTON', 'AUDIO', 'STARTED', now())", True),
    ("재발 확인 거리 500km 아님 거부", f"INSERT INTO maintenance_notes(note_id, client_note_id, vehicle_id, service_date, odometer_km_at_service, recheck_until_km, summary) VALUES (gen_random_uuid(), gen_random_uuid(), '{V1}', '2026-11-12', 32000, 32400, '앞 타이어 교체')", False),
    ("재발 확인 32,500km 허용", f"INSERT INTO maintenance_notes(note_id, client_note_id, vehicle_id, service_date, odometer_km_at_service, recheck_until_km, summary) VALUES (gen_random_uuid(), gen_random_uuid(), '{V1}', '2026-11-12', 32000, 32500, '앞 타이어 교체')", True),
    ("SMS인데 KAKAO 묶음 거부", f"INSERT INTO deliveries(delivery_id, incident_id, guardian_id, channel, transport_group, revision, state) VALUES (gen_random_uuid(), '{I1}', '{G1}', 'SMS', 'KAKAO', 1, 'PENDING')", False),
    ("accuracy_m 열 없음", f"INSERT INTO incident_locations(location_id, incident_id, client_location_id, received_at, location_source, quality, accuracy_m) VALUES (gen_random_uuid(), '{I1}', gen_random_uuid(), now(), 'VEHICLE', 'UNAVAILABLE', 25)", False),
    ("위치 원값 confidence 허용", f"INSERT INTO incident_locations(location_id, incident_id, client_location_id, latitude, longitude, measured_at, received_at, location_confidence, location_source, quality) VALUES (gen_random_uuid(), '{I1}', gen_random_uuid(), 37.5665, 126.978, now(), now(), 0.8, 'VEHICLE', 'OK')", True),
    ("TEST_INPUT인데 quality OK 거부", f"INSERT INTO observations(observation_id, incident_id, source_device_id, sequence, code, source, quality, received_at, freshness_basis, value, payload_sha256) VALUES (gen_random_uuid(), '{I1}', '{DEV1}', 1, 'VEHICLE_SPEED', 'TEST_INPUT', 'OK', now(), 'ARRIVAL_ONLY', '19.4', repeat('e',64))", False),
    ("경고등 상태 코드 거부", f"INSERT INTO observations(observation_id, incident_id, source_device_id, sequence, code, source, quality, received_at, freshness_basis, value, payload_sha256) VALUES (gen_random_uuid(), '{I1}', '{DEV1}', 2, 'TELLTALE_STATE', 'VEHICLE_SDK', 'OK', now(), 'ARRIVAL_ONLY', 'true', repeat('e',64))", False),
    ("받는 사람 없는 REQUEST_HELP 거부", f"INSERT INTO incident_actions(action_id, incident_id, client_action_id, action, expected_version, result_version, request_sha256, occurred_at, result) VALUES (gen_random_uuid(), '{I1}', gen_random_uuid(), 'REQUEST_HELP', 1, 1, repeat('f',64), now(), '{{}}')", False),
    ("휴대폰 연결 QR 10분 거부", f"INSERT INTO device_pairings(pairing_id, vehicle_id, ivi_device_id, code_hash, expires_at) VALUES (gen_random_uuid(), '{V1}', '{DEV1}', 'h', now()+interval '10 minutes')", False),
    ("OTP 6회 시도 거부", f"INSERT INTO guardian_verifications(verification_id, guardian_id, invite_token_hash, otp_hash, attempt_count, expires_at) VALUES (gen_random_uuid(), '{G1}', 'i1', 'o1', 6, now()+interval '5 minutes')", False),
]
PG_VIEW_TEST = ("재현 조건 뷰: 같은 증상 3회면 1행", ";".join([
    mr_sql(["VIBRATION"], True, "00000000-0000-0000-0000-000000000201"),
    mr_sql(["VIBRATION"], True, "00000000-0000-0000-0000-000000000202"),
    mr_sql(["VIBRATION"], False, "00000000-0000-0000-0000-000000000203"),
]), "SELECT occurrences || '|' || round(speed_kmh_min) FROM symptom_patterns WHERE symptom = 'VIBRATION'", "3|70")


def run_pg(rep, host, port, sql_path):
    def psql(db, *args, capture=True):
        cmd = ["psql", "-h", host, "-p", str(port), "-U", "postgres", "-d", db, "-X", "-q", "-At", "-v", "ON_ERROR_STOP=1"] + list(args)
        return subprocess.run(cmd, capture_output=capture, text=True, timeout=120)

    db = "guard_contract_check"
    out = {"executed": False}
    r = psql("postgres", "-c", f"DROP DATABASE IF EXISTS {db}", "-c", f"CREATE DATABASE {db}")
    if r.returncode != 0:
        rep.check("pg:create_database", False, r.stderr.strip()[:300])
        return out
    try:
        ver = psql(db, "-c", "SHOW server_version").stdout.strip()
        r = psql(db, "-f", sql_path)
        ok = rep.check("pg:ddl_executes", r.returncode == 0, None if r.returncode == 0 else r.stderr.strip()[:500])
        out.update({"executed": ok, "server_version": ver})
        if not ok:
            return out
        q = lambda s: psql(db, "-c", s).stdout.strip()  # noqa: E731
        out["catalog_counts"] = {
            "tables": int(q("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'")),
            "views": int(q("SELECT count(*) FROM information_schema.views WHERE table_schema='public'")),
            "foreign_keys": int(q("SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='public' AND contype='f'")),
            "check_constraints": int(q("SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='public' AND contype='c'")),
            "indexes_including_keys": int(q("SELECT count(*) FROM pg_indexes WHERE schemaname='public'")),
        }
        r = psql(db, "-c", PG_FIXTURE)
        if not rep.check("pg:fixture", r.returncode == 0, None if r.returncode == 0 else r.stderr.strip()[:500]):
            return out
        results = []
        for name, stmt, expect_ok in PG_SMOKE:
            r = psql(db, "-c", "BEGIN", "-c", stmt, "-c", "ROLLBACK")
            got = r.returncode == 0
            passed = got == expect_ok
            results.append({"name": name, "expect": "accept" if expect_ok else "reject", "result": "accept" if got else "reject", "status": "PASS" if passed else "FAIL"})
            rep.check("pg:" + name, passed, None if passed else r.stderr.strip()[:300])
        name, inserts, select, expect = PG_VIEW_TEST
        r = psql(db, "-c", "BEGIN", "-c", inserts, "-c", select, "-c", "ROLLBACK")
        got = r.stdout.strip().splitlines()[-1] if r.stdout.strip() else r.stderr.strip()[:200]
        passed = r.returncode == 0 and got == expect
        results.append({"name": name, "expect": expect, "result": got, "status": "PASS" if passed else "FAIL"})
        rep.check("pg:" + name, passed, None if passed else got)
        out["constraint_tests"] = results
        return out
    finally:
        psql("postgres", "-c", f"DROP DATABASE IF EXISTS {db}")


# ---------------------------------------------------------------- 본 검사
def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--write", help="결과 JSON 파일 이름(이 폴더 기준)")
    ap.add_argument("--pg-host", help="PostgreSQL 소켓 폴더 또는 호스트(선택)")
    ap.add_argument("--pg-port", type=int, default=5432)
    args = ap.parse_args()
    rep = Report()
    counts = {}

    api = load_json(rep, "api.openapi.json")
    rules = load_json(rep, "rules-lab.json")
    tokens = load_json(rep, "ui-tokens.json")
    acc = load_json(rep, "acceptance-scenarios.json")
    load_json(rep, "ui-tokens-prd-v2.json")
    load_json(rep, "validation-2026-11-12.json")
    hist = {}
    for f in HISTORY_FILES:
        p = path(HISTORY, f)
        rep.check("history_exists:" + f, os.path.exists(p))
        if f.endswith(".json") and os.path.exists(p):
            hist[f] = load_json(rep, os.path.join(HISTORY, f))
    with open(path("database.sql"), encoding="utf-8") as fh:
        sql_text = fh.read()
    sql = parse_sql(sql_text)

    # ---------------- OpenAPI
    if api:
        rep.check("openapi:version_3_1", str(api.get("openapi", "")).startswith("3.1"))
        rep.check("openapi:info_version", api["info"]["version"] == "3.0.0", api["info"]["version"])
        refs = []
        walk_refs(api, refs)
        bad = []
        for r in refs:
            if not r.startswith("#/"):
                bad.append(r)
                continue
            try:
                pointer(api, r[1:])
            except (KeyError, IndexError, ValueError):
                bad.append(r)
        rep.check("openapi:refs_resolve", not bad, bad[:10] or None)
        counts["openapi_refs_total"] = len(refs)
        counts["openapi_refs_unique"] = len(set(refs))
        methods = ("get", "post", "put", "patch", "delete", "head")
        ops, op_ids = [], []
        schemes = set(api["components"]["securitySchemes"].keys())
        for p, item in api["paths"].items():
            params_path = item.get("parameters", [])
            for m, op in item.items():
                if m not in methods:
                    continue
                ops.append((m, p))
                op_ids.append(op.get("operationId"))
                rep.check(f"openapi:default_response:{m} {p}", "default" in op.get("responses", {}))
                sec = op.get("security", api.get("security", []))
                names = {k for s in sec for k in s}
                rep.check(f"openapi:security_known:{m} {p}", names <= schemes, sorted(names - schemes) or None)
                declared = set()
                for prm in params_path + op.get("parameters", []):
                    prm = pointer(api, prm["$ref"][1:]) if "$ref" in prm else prm
                    if prm.get("in") == "path":
                        declared.add(prm["name"])
                needed = set(re.findall(r"\{(\w+)\}", p))
                rep.check(f"openapi:path_params:{m} {p}", needed == declared, {"needed": sorted(needed), "declared": sorted(declared)} if needed != declared else None)
        dup = sorted({x for x in op_ids if op_ids.count(x) > 1 or x is None}, key=str)
        rep.check("openapi:operation_ids_unique", not dup, dup or None)
        counts["openapi_paths"] = len(api["paths"])
        counts["openapi_operations"] = len(ops)
        counts["openapi_schemas"] = len(api["components"]["schemas"])
        counts["openapi_security_schemes"] = len(schemes)
        counts["openapi_operations_by_scheme"] = {s: sum(1 for p, item in api["paths"].items() for m, op in item.items()
                                                          if m in methods and any(s in d for d in op.get("security", api["security"]))) for s in sorted(schemes)}
        counts["openapi_operations_public_body_token"] = sum(1 for p, item in api["paths"].items() for m, op in item.items() if m in methods and op.get("security") == [])
        missing = [f"{m.upper()} {p}" for m, p in DECISION_PATHS if p not in api["paths"] or m not in api["paths"][p]]
        rep.check("openapi:decision_9_4_paths_present", not missing, missing or None)
        counts["decision_9_4_operations_added"] = len(DECISION_PATHS) - len(missing)
        sch = api["components"]["schemas"]
        for name, values in DECISION_ENUMS.items():
            rep.check(f"decision_enum:{name}", sch.get(name, {}).get("enum") == values, sch.get(name, {}).get("enum"))
        rep.check("decision_enum:GuideId_pattern", sch.get("GuideId", {}).get("pattern") == DECISION_GUIDE_PATTERN)
        loc = sch["Location"]["properties"]
        rep.check("decision_9_2:location_fields", all(f in loc for f in DECISION_LOCATION_FIELDS) and "accuracy_m" not in loc)
        snap = sch["VehicleSnapshot"]["properties"]
        rep.check("decision_9_3:snapshot_fields", all(f in snap for f in DECISION_SNAPSHOT_FIELDS), [f for f in DECISION_SNAPSHOT_FIELDS if f not in snap] or None)
        rep.check("decision_9_3:maintenance_flags", list(sch["MaintenanceFlags"]["properties"].keys()) == DECISION_MAINTENANCE_FLAGS)
        reading_ok = all(set(sch[n]["required"]) >= {"quality", "received_at"} for n in ("NumberReading", "BooleanReading", "TurnSignalReading", "OilLevelReading", "SeatBooleanReading", "DoorBooleanReading", "EnergyReading"))
        rep.check("decision_9_3:each_value_has_quality_received_at", reading_ok)
        bundle = sch["GuideBundle"]["properties"]
        rep.check("decision_9_1:guide_bundle_fields", all(k in bundle for k in ("guide_id", "version", "vehicle_profile_id", "availability", "source_manual_ref", "steps", "tts_lines")))
        props = []
        walk_property_names(api["components"]["schemas"], props)
        rep.check("d17:no_accuracy_m_property", "accuracy_m" not in props)
        bad_sig = [v for v in sch["SignalCode"]["enum"] if any(w in v for w in FUTURE_SIGNAL_WORDS)]
        rep.check("d1:no_future_signal_codes", not bad_sig, bad_sig or None)
        fut = api.get("x-future-inputs", [])
        rep.check("d1:future_inputs_marked", bool(fut) and all(x.get("availability") == "FUTURE" for x in fut))
        counts["openapi_future_inputs_listed"] = len(fut)
        # schema examples
        ex_results = []
        for name, label, inst, expect in schema_examples():
            errs = sv(inst, sch[name], api)
            ok = (not errs) == expect
            ex_results.append({"schema": name, "case": label, "expect": "valid" if expect else "invalid", "status": "PASS" if ok else "FAIL"})
            rep.check(f"schema_example:{name}:{label}", ok, None if ok else errs[:3])
        counts["schema_examples_positive"] = sum(1 for x in ex_results if x["expect"] == "valid")
        counts["schema_examples_negative"] = sum(1 for x in ex_results if x["expect"] == "invalid")

    # ---------------- SQL
    tables = sql["tables"]
    counts["sql_tables"] = len(tables)
    counts["sql_views"] = len(sql["views"])
    counts["sql_foreign_key_references"] = len(sql["fks"])
    counts["sql_check_clauses"] = sql["checks"]
    counts["sql_explicit_indexes"] = len(sql["indexes"])
    counts["sql_partial_unique_indexes"] = len(sql["unique_indexes"])
    missing_fk = sorted({t for t in sql["fks"] if t not in tables})
    rep.check("sql:foreign_keys_target_tables", not missing_fk, missing_fk or None)
    required_tables = ["vehicles", "trips", "device_pairings", "guardian_verifications", "consents", "location_disclosures", "share_links",
                       "guardian_replies", "guide_bundles", "maintenance_notes", "driver_profiles", "notification_policies", "deliveries"]
    rep.check("sql:required_tables", all(t in tables for t in required_tables), [t for t in required_tables if t not in tables] or None)
    rep.check("sql:symptom_patterns_view", "symptom_patterns" in sql["views"])
    rep.check("sql:vehicles_vin_hash", "vin_hash" in tables["vehicles"]["columns"])
    rep.check("sql:driver_profile_id_on_incidents_trips", all("driver_profile_id" in tables[t]["columns"] for t in ("incidents", "trips", "consents", "vehicle_guardians")))
    rep.check("sql:guide_bundles_columns", all(c in tables["guide_bundles"]["columns"] for c in ("guide_id", "version", "vehicle_profile_id", "availability", "source_manual_ref", "steps_json", "tts_lines_json")))
    rep.check("sql:one_open_alert_per_vehicle", re.search(r"CREATE UNIQUE INDEX one_open_alert_per_vehicle ON notification_policies\(vehicle_id\) WHERE state IN \('COUNTDOWN','RELEASED','DISPATCHING'\)", sql["clean"]) is not None)
    rep.check("d17:no_accuracy_m_column", all("accuracy_m" not in t["columns"] for t in tables.values()))
    rep.check("sql:no_guide_versions_uuid_key", "guide_versions" not in tables)
    if api:
        enum_results = []
        for ptr, t, col, rel in ENUM_PAIRS:
            api_vals = pointer(api, ptr).get("enum")
            sql_vals = tables.get(t, {}).get("enums", {}).get(col)
            if api_vals is None or sql_vals is None:
                ok = False
            elif rel == "equal":
                ok = set(api_vals) == set(sql_vals) and len(api_vals) == len(sql_vals)
            else:
                ok = set(sql_vals) <= set(api_vals)
            enum_results.append(ok)
            rep.check(f"enum_pair:{ptr.split('/')[-1] if 'allOf' not in ptr else 'ManualReport.input_source'}<->{t}.{col}", ok,
                      None if ok else {"openapi": api_vals, "sql": sql_vals})
        counts["enum_pairs_checked"] = len(enum_results)
        for ptr, t, col in REGEX_PAIRS:
            a = pointer(api, ptr).get("pattern")
            s = tables.get(t, {}).get("regex", {}).get(col)
            rep.check(f"regex_pair:GuideId<->{t}.{col}", a == s, None if a == s else {"openapi": a, "sql": s})
        counts["regex_pairs_checked"] = len(REGEX_PAIRS)
        drive_sets = tables["manual_reports"]["arrays"].get("symptoms", [])
        rep.check("sql:driving_five_symptoms", any(x == DECISION_DRIVING5 for x in drive_sets))

    # ---------------- rules-lab
    if rules and api:
        sch = api["components"]["schemas"]
        sym, areas = sch["Symptom"]["enum"], sch["BodyArea"]["enum"]
        r = rules
        rep.check("rules:version", r["schema_version"] == "2.0.0")
        rep.check("rules:symptoms_values", r["symptoms"]["values"] == sym)
        rep.check("rules:driving_quick_cards", r["symptoms"]["driving_quick_cards"] == DECISION_DRIVING5)
        rep.check("rules:minimum_severity_keys", list(r["symptoms"]["minimum_severity"]) == sym and set(r["symptoms"]["minimum_severity"].values()) <= set(sch["Severity"]["enum"]))
        rep.check("rules:guide_id_by_symptom", list(r["symptoms"]["guide_id_by_symptom"]) == sym and all(re.match(DECISION_GUIDE_PATTERN, v) for v in r["symptoms"]["guide_id_by_symptom"].values()))
        rep.check("rules:guide_id_by_state", set(r["guidance"]["guide_id_by_state"]) <= set(sch["GuidanceState"]["enum"]) and all(re.match(DECISION_GUIDE_PATTERN, v) for v in r["guidance"]["guide_id_by_state"].values()))
        bam = r["symptoms"]["body_area_symptom_map"]
        rep.check("rules:body_area_symptom_map", list(bam) == areas and all(set(v) <= set(sym) for v in bam.values()) and bam["UNKNOWN"] == sym)
        rep.check("rules:body_area_warning_links_keys", list(r["symptoms"]["body_area_warning_links"]) == areas)
        rep.check("rules:collision_is_l3", r["symptoms"]["minimum_severity"]["COLLISION"] == "L3_URGENT")
        codes = [s["code"] for s in r["signal_catalog"]]
        rep.check("rules:signal_catalog_matches_SignalCode", codes == sch["SignalCode"]["enum"], None if codes == sch["SignalCode"]["enum"] else codes)
        rep.check("rules:signal_sources", all(s["source"] in sch["InputSource"]["enum"] for s in r["signal_catalog"]))
        rep.check("rules:signal_availability", all(s["availability"] in sch["Availability"]["enum"] for s in r["signal_catalog"]))
        rep.check("rules:signal_demo_method", all(s["demo_method"] in DEMO_METHODS for s in r["signal_catalog"]))
        rep.check("rules:signal_delivery_kind", all(s["delivery_kind"] in sch["SignalDeliveryKind"]["enum"] for s in r["signal_catalog"]))
        sv_ = r["signal_validity"]
        rep.check("d18:periodic_3000_speed_only", sv_["periodic"]["applies_to"] == ["VEHICLE_SPEED"] and sv_["periodic"]["freshness_ms"] == 3000)
        onch = [s["code"] for s in r["signal_catalog"] if s["delivery_kind"] == "ON_CHANGE"]
        rep.check("d18:on_change_rule_covers_on_change_signals", sv_["on_change"]["applies_to"] == onch and sv_["on_change"]["time_based_staleness"] is False)
        rep.check("d18:no_other_freshness_ms", "urgent_brake_max_age_ms" not in json.dumps({k: v for k, v in r.items() if k != "signal_validity"}))
        rep.check("d8:guidance_requires_user_report", r["guidance"]["start_requires_user_report"] is True and r["guidance"]["no_deceleration_auto_guidance_allowed"] is False and r["guidance"]["sensor_combination_output"] == "CONFIRM_PROMPT_ONLY")
        sc = r["notification"]["scope"]
        rep.check("d10:scope", sc["auto_send_after_confirmation_triggers"] == ["AIRBAG_DEPLOYED", "USER_COLLISION"] and sc["immediate_send_triggers"] == ["USER_HELP_REQUEST"]
                  and sc["manual_symptom_auto_send"] is False and sc["immediate_send_requires_recipient_list_confirmation"] is True)
        rep.check("d10:scope_triggers_known", set(sc["auto_send_after_confirmation_triggers"] + sc["immediate_send_triggers"] + r["severity"]["l3_triggers"]) <= set(sch["IncidentTrigger"]["enum"]))
        rep.check("rules:quick_replies", r["notification"]["quick_replies"] == sch["GuardianReplyKind"]["enum"])
        perf = r["performance"]
        rep.check("d24:performance_budget", perf["driving_cpu_budget_pct_lt"] == 5 and perf["ram_budget_mb_max"] == 400 and perf["always_on_speech_recognition"] is False)
        fm = r["future_motion_analysis"]
        rep.check("d1:motion_block_future_disabled", fm["availability"] == "FUTURE" and fm["enabled"] is False and "vibration" in fm and "impact_severity" in fm)
        rep.check("n2:pattern_min_3", r["symptom_patterns"]["minimum_occurrences"] == 3 == sch["SymptomPatternPage"]["properties"]["minimum_occurrences"]["const"])
        rep.check("n6:recheck_500km", r["maintenance"]["recheck_distance_km"] == 500 and "odometer_km_at_service + 500" in sql["clean"])
        day_ms = 86400000
        rep.check("f5:share_link_24h", r["share_links"]["maintenance_link_expiry_ms"] == day_ms == sch["ShareLinkCreate"]["properties"]["expires_in_ms"]["maximum"]
                  and "created_at + interval '24 hours'" in tables["share_links"]["body"])
        tts = sch["TtsLine"]["properties"]["preroll_silence_ms"]
        rep.check("d11:tts_preroll_150_200", r["audio"]["preroll_silence_ms_min"] == 150 == tts["minimum"] and r["audio"]["preroll_silence_ms_max"] == 200 == tts["maximum"])
        n = r["notification"]
        rep.check("notification:pre_send_confirmation_10s", n["pre_send_confirmation_ms"] == 10000 == sch["NotificationIntent"]["properties"]["countdown_remaining_ms"]["maximum"]
                  and "pre_send_confirmation_ms integer NOT NULL DEFAULT 10000" in sql["clean"])
        old_n = (hist.get("rules-lab.json") or {}).get("notification", {})
        rep.check("notification:kakao_deadline_value_kept_from_v2", n["kakao_delivery_confirmation_deadline_ms"] == old_n.get("delivery_unknown_fallback_after_ms"),
                  {"v3": n["kakao_delivery_confirmation_deadline_ms"], "v2": old_n.get("delivery_unknown_fallback_after_ms")})
        rep.check("notification:guardian_limit_5", n["guardian_limit"] == 5 and "slot BETWEEN 1 AND 5" in tables["vehicle_guardians"]["body"]
                  and sch["NotificationIntent"]["properties"]["guardian_ids"]["maxItems"] == 5)
        rep.check("pairing:qr_ttl_5min", r["pairing"]["qr_code_ttl_ms"] == 300000 and "created_at + interval '5 minutes'" in tables["device_pairings"]["body"])
        rep.check("guardian_otp:max_5", r["guardian_otp"]["max_failed_attempts"] == 5 and "attempt_count BETWEEN 0 AND 5" in tables["guardian_verifications"]["body"])

    # ---------------- ui-tokens
    if tokens:
        t = tokens
        rep.check("tokens:version", t["version"] == "5.0.0")
        ol = t["official_limits"]
        exp = {"contrast_min": 4.5, "min_font": 12, "glyph_height_primary": 5, "glyph_height_secondary": 3, "input_response": 0.25,
               "driving_text_max_lines": 3, "button_response": 2, "app_launch": 10, "content_load": 10}
        rep.check("tokens:pleos_official_values", all(ol[k]["value"] == v and ol[k]["source"].startswith("PLEOS_") for k, v in exp.items()))
        sd = t["self_defined"]
        rep.check("tokens:self_defined_values", sd["primary_touch"]["value"] == 80 and sd["major_gap"]["value"] == 24 and sd["body_text_min"]["value"] == 22
                  and all(v["source"] == "SELF_DEFINED" for v in sd.values()))
        cmin = ol["contrast_min"]["value"]
        bad, pair_hex = [], set()
        for p in t["contrast_pairs"]:
            fg, bg = t["colors"][p["fg"]], t["colors"][p["bg"]]
            ratio = contrast(fg, bg)
            pair_hex.add((fg.upper(), bg.upper()))
            if fg != p["fg_hex"] or bg != p["bg_hex"] or abs(ratio - p["ratio"]) > 0.001 or ratio < cmin:
                bad.append({"pair": f"{p['fg']}/{p['bg']}", "recomputed": round(ratio, 3), "recorded": p["ratio"]})
        rep.check("tokens:contrast_recomputed_ge_4_5", not bad, bad or None)
        counts["ui_contrast_pairs"] = len(t["contrast_pairs"])
        counts["ui_contrast_min_ratio"] = round(min(contrast(t["colors"][p["fg"]], t["colors"][p["bg"]]) for p in t["contrast_pairs"]), 3)
        fb_ok = True
        for fp in t["forbidden_pairs"]:
            rr = contrast(fp["fg_hex"], fp["bg_hex"])
            fb_ok &= rr < cmin and abs(rr - fp["ratio"]) <= 0.001 and (fp["fg_hex"].upper(), fp["bg_hex"].upper()) not in pair_hex
        rep.check("d12:forbidden_FF5B6B_on_702132_replaced", fb_ok and ("#FF5B6B", "#702132") not in pair_hex
                  and contrast(t["colors"]["emergency_band_accent"], t["colors"]["emergency_band"]) >= cmin)
        por = t["layouts"]["portrait"]
        cw, ch = por["reference_canvas"]["width_px"], por["reference_canvas"]["height_px"]
        rg = por["regions_px"]
        stack = [rg[k] for k in por["stack_order"]]
        y, contiguous = 0, True
        for x0, y0, w, h in stack:
            contiguous &= (y0 == y and x0 == 0 and w == cw)
            y += h
        rep.check("tokens:portrait_regions_stack", contiguous and y == ch and (cw, ch) == (840, 1212), {"sum_height": y})
        land = t["layouts"]["landscape"]
        vw, vh = land["example_viewport"]["width_dp"], land["example_viewport"]["height_dp"]
        for key in ("regions_dp", "emergency_regions_dp"):
            rects = list(land[key].values())
            inside = all(x >= 0 and yy >= 0 and x + w <= vw and yy + h <= vh for x, yy, w, h in rects)
            overlap = any(rects_overlap(a, b) for i, a in enumerate(rects) for b in rects[i + 1:])
            rep.check(f"tokens:landscape_{key}_inside_no_overlap", inside and not overlap and (vw, vh) == (1280, 720))
        names = [s["name_ko"] for s in t["screens"]]
        rep.check("tokens:screens_match_decision_names", names == DECISION_SCREENS, [n for n in DECISION_SCREENS if n not in names] or None)
        rep.check("tokens:mockup_file_lists", t["mockup_files"]["portrait"] == PORTRAIT_FILES and t["mockup_files"]["landscape"] == LANDSCAPE_FILES)
        used_p = {s["portrait_file"] for s in t["screens"] if s["portrait_file"]}
        used_l = {s["landscape_file"] for s in t["screens"] if s["landscape_file"]}
        rep.check("tokens:screen_files_known_and_all_used", used_p == set(PORTRAIT_FILES) and used_l == set(LANDSCAPE_FILES),
                  {"unused_portrait": sorted(set(PORTRAIT_FILES) - used_p), "unused_landscape": sorted(set(LANDSCAPE_FILES) - used_l)})
        rep.check("d7:driving_motion_none", t["driving_mode"]["motion"] == "none" and t["animation"]["driving_motion_ms"] == 0 and t["driving_mode"]["foot_or_hand_motion_images"] is False)
        rep.check("d13:parity_rules_present", len(t["parity_rules"]) >= 3)
        counts["ui_screens"] = len(t["screens"])
        counts["ui_portrait_mockups"] = len(PORTRAIT_FILES)
        counts["ui_landscape_mockups"] = len(LANDSCAPE_FILES)

    # ---------------- acceptance
    if acc and api and rules and tokens:
        cases = acc["cases"]
        ids = [c["id"] for c in cases]
        rep.check("acceptance:version", acc["version"] == "3.0.0")
        dup = sorted({i for i in ids if ids.count(i) > 1})
        rep.check("acceptance:ids_unique", not dup, dup or None)
        rep.check("acceptance:ids_format_sequential", ids == [f"T{i:02d}" if i < 100 else f"T{i}" for i in range(1, len(ids) + 1)])
        avail = api["components"]["schemas"]["Availability"]["enum"]
        op_ids = {op.get("operationId") for item in api["paths"].values() for m, op in item.items() if isinstance(op, dict) and "operationId" in op}
        bad_refs, bad_fields = [], []
        for c in cases:
            if c["demo_method"] not in DEMO_METHODS or c["availability"] not in avail or c["grade"] not in ("DEMO", "DESIGN", "FUTURE") \
                    or (c["grade"] == "FUTURE") != (c["availability"] == "FUTURE") or c["feature"] not in [f"F{i}" for i in range(7)]:
                bad_fields.append(c["id"])
            for ref in c["refs"]:
                kind, _, target = ref.partition(":")
                try:
                    if kind == "api":
                        assert target in op_ids
                    elif kind == "sql":
                        assert target in tables or target in sql["views"]
                    elif kind == "rules":
                        get_path(rules, target)
                    elif kind == "tokens":
                        get_path(tokens, target)
                    else:
                        raise AssertionError
                except (AssertionError, KeyError):
                    bad_refs.append(f"{c['id']}:{ref}")
        rep.check("acceptance:fields_valid", not bad_fields, bad_fields or None)
        rep.check("acceptance:refs_resolve", not bad_refs, bad_refs or None)
        old_ids = [c["id"] for c in (hist.get("acceptance-scenarios.json") or {}).get("cases", [])]
        v2 = [c.get("v2_id") for c in cases if c.get("v2_id")]
        rep.check("acceptance:all_v2_cases_carried", sorted(v2) == sorted(old_ids) and len(old_ids) == 56, {"v2_cases": len(old_ids), "carried": len(v2)})
        text = json.dumps(cases, ensure_ascii=False)
        old_names = ["SHAKE", "STEERING_PULL", "METAL_NOISE", "STOPPING_FAILED", "UNKNOWN_PEDAL", "PEDAL_ABNORMAL", "REPORT_STOPPED", "QUEUED", "SUBMITTING", "G_STOPPING_FAILURE", "G_EMERGENCY_HELP", "accent backgrounds"]
        leftover = [w for w in old_names if re.search(r"(?<![A-Z_])" + re.escape(w) + r"(?![A-Z_])", text) and not re.search(r"옛 이름\(" + re.escape(w), text)]
        rep.check("acceptance:no_old_enum_names_as_current", all(w in ("SHAKE", "STEERING_PULL") for w in leftover), leftover or None)
        required_topics = {"driving_restriction": "ADB", "parity": None, "consent": None, "correction": None, "quick_reply": None,
                           "demo_mode": "TEST_INPUT", "security_exception": None, "service_connection": None}
        missing_topics = [a for a, dm in required_topics.items() if not any(c["area"] == a and (dm is None or c["demo_method"] == dm) for c in cases)]
        rep.check("acceptance:required_topics", not missing_topics, missing_topics or None)
        new_feature_areas = ["check_items", "predrive", "pattern", "voice_record", "voice_search", "snapshot", "service_stop", "maintenance", "practice", "demo_mode", "disclosure", "practice_alert", "quick_reply", "weekly_card"]
        rep.check("acceptance:new_features_N1_N12", all(any(c["area"] == a for c in cases) for a in new_feature_areas))
        counts["acceptance_cases"] = len(cases)
        counts["acceptance_by_grade"] = {g: sum(1 for c in cases if c["grade"] == g) for g in ("DEMO", "DESIGN", "FUTURE")}
        counts["acceptance_by_availability"] = {a: sum(1 for c in cases if c["availability"] == a) for a in avail if any(c["availability"] == a for c in cases)}
        counts["acceptance_by_demo_method"] = {d: sum(1 for c in cases if c["demo_method"] == d) for d in DEMO_METHODS}
        counts["acceptance_by_feature"] = {f"F{i}": sum(1 for c in cases if c["feature"] == f"F{i}") for i in range(7)}
        counts["acceptance_v2_carried"] = len(v2)
        counts["acceptance_new"] = len(cases) - len(v2)
        if rules:
            counts["rules_signal_catalog"] = len(rules["signal_catalog"])

    # ---------------- 개인 로컬 경로
    leaks = []
    for f in sorted(os.listdir(HERE)):
        p = path(f)
        if os.path.isfile(p) and f.endswith((".json", ".sql", ".md", ".py")):
            with open(p, encoding="utf-8") as fh:
                s = fh.read()
            if f == "validate_contracts.py":
                continue
            for pat in (r"/home/[a-z]", r"[A-Za-z]:\\\\Users", r"/Users/[A-Za-z]", r"/tmp/claude", r"scratchpad"):
                if re.search(pat, s):
                    leaks.append(f"{f}:{pat}")
    rep.check("privacy:no_local_paths", not leaks, leaks or None)

    files = {}
    for f in CONTRACT_FILES + ["README.md", "validate_contracts.py"]:
        files[f] = {"bytes": os.path.getsize(path(f)), "sha256": sha256_file(path(f))}
    files["api.openapi.json"]["version"] = api["info"]["version"] if api else None
    files["rules-lab.json"]["version"] = rules["schema_version"] if rules else None
    files["ui-tokens.json"]["version"] = tokens["version"] if tokens else None
    files["acceptance-scenarios.json"]["version"] = acc["version"] if acc else None
    hist_files = {f: {"bytes": os.path.getsize(path(HISTORY, f)), "sha256": sha256_file(path(HISTORY, f))} for f in HISTORY_FILES if os.path.exists(path(HISTORY, f))}

    pg = {"executed": False, "note": "--pg-host를 주지 않아 DDL을 실행하지 않음"}
    if args.pg_host:
        pg = run_pg(rep, args.pg_host, args.pg_port, path("database.sql"))
        pg["note"] = "검사용 임시 DB를 만들어 DDL·제약을 시험하고 지웠다. 운영 DB·서버 실행 증거가 아님"

    result = {
        "date_kst": "2026-11-12",
        "contract_version": "3.0.0",
        "scope": "정적 계약 검사(JSON 파싱, OpenAPI $ref·경로, enum↔SQL CHECK, 스키마 예시, 파일 간 이름·수치, 인수 시나리오 참조, 대비·배치 계산)와 선택적 로컬 PostgreSQL DDL·제약 시험. 앱 빌드·실행, 서버 실행, 실제 발송, 실차·에뮬레이터 동작의 증거가 아니다.",
        "validator": "validate_contracts.py (python3 표준 라이브러리만, JSON Schema는 이 계약이 쓰는 키워드만 구현)",
        "status": "PASS" if not rep.errors else "FAIL",
        "counts": counts,
        "files": files,
        "history": {"directory": HISTORY.replace(os.sep, "/"), "files": hist_files, "previous_validation": "validation-2026-11-12.json(v2 시점 기록)"},
        "postgresql": pg,
        "checks_total": len(rep.checks),
        "checks_failed": sum(1 for c in rep.checks if c["status"] == "FAIL"),
        "errors": rep.errors,
        "warnings": rep.warnings,
        "checks": rep.checks,
        "not_executed": [
            "Android 앱 빌드·실행, Pleos 에뮬레이터·Virtual Console 시연",
            "서버 구현·실행, 생성 클라이언트 빌드",
            "외부 OpenAPI·JSON Schema 표준 검사기",
            "카카오·문자 실제 발송, 주소 변환 API 호출",
            "실차 신호 수집, 성능(CPU·RAM) 측정, 물리 글자 높이 측정",
            f"인수 시나리오 {counts.get('acceptance_cases', 0)}개의 앱 실행(계획된 사례일 뿐임)",
            "위치정보법 등 법 조문 해석",
        ],
    }
    if args.write:
        with open(path(args.write), "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
            f.write("\n")
    print(json.dumps({"status": result["status"], "checks_total": result["checks_total"], "checks_failed": result["checks_failed"],
                      "errors": rep.errors[:20], "counts": counts, "postgresql_executed": pg.get("executed")}, ensure_ascii=False, indent=1))
    return 0 if not rep.errors else 1


if __name__ == "__main__":
    sys.exit(main())
