# 플레오스 가드 개발 계약 파일 (계약 v3)

기준: **2026-11-12 / 1차 비판 반영 결정 9장(계약 고정 사항)**. 상태·열거형·위치·스냅샷·API 경로 이름은 PRD v4와 같은 9장 표를 그대로 씁니다.

이 폴더는 앞으로 만들 앱과 서버의 **설계 계약**입니다. 구현된 서비스나 설치된 데이터베이스가 아닙니다. 아래 검사는 **정적 검사**(와 검사용 임시 PostgreSQL에서 DDL을 실행해 본 것)이며, **앱·서버를 실행한 증거가 아닙니다**. 에뮬레이터 시연, 실제 발송, 실차 동작도 확인하지 않았습니다.

## 파일 목록

| 파일 | 판 | 개수 | 역할 |
| --- | --- | --- | --- |
| [api.openapi.json](api.openapi.json) | 3.0.0 (OpenAPI 3.1) | 경로 38개, 작업 41개, 스키마 132개, 보안 방식 3개 | 이 앱이 만들 서버 API. Pleos·카카오 공식 API가 아님 |
| [database.sql](database.sql) | v3 | 테이블 32개, 뷰 1개, 외래키 59개, CHECK 145개, 명시 인덱스 17개 | PostgreSQL 스키마 제안 |
| [rules-lab.json](rules-lab.json) | 2.0.0 (`pleos-care-lab-20261112-r2`) | 공개 신호 목록 19개 | 신호 유효성·발송 범위·성능 예산·수치의 개발용 시작값 |
| [ui-tokens.json](ui-tokens.json) | 5.0.0 | 화면 18개, 세로 시안 11장·가로 시안 7장, 대비 조합 18개(최소 5.674:1) | 세로·가로 배치, 기능 패리티, Pleos 공식 수치와 자체 수치 |
| [acceptance-scenarios.json](acceptance-scenarios.json) | 3.0.0 | 103개(v2 승계 56 + 새 47). 시연 58·설계 37·향후 8 | 구현 뒤 실행할 인수 사례. **통과한 앱 시험이 아님** |
| [validate_contracts.py](validate_contracts.py) | - | 검사 353개(PostgreSQL 포함) | 계약 검사기(python3 표준 라이브러리만) |
| [validation-2026-11-12-v3.json](validation-2026-11-12-v3.json) | - | PASS, 실패 0 | 이번 판 검사 결과 |
| [validation-2026-11-12.json](validation-2026-11-12.json) | - | - | **v2 시점 기록**(PRD 2.0·prd-v2 토큰 기준). 이번 판 검사가 아님 |
| [ui-tokens-prd-v2.json](ui-tokens-prd-v2.json) | 1.0.0 | - | 이력. 그대로 둠 |
| [history/2026-11-12-v2.1/](history/2026-11-12-v2.1/) | 2.1.0 등 | 파일 7개 | 바꾸기 전 원본(바이트 동일 사본) |

이전 판 수치(API 23개 작업·21개 경로, 테이블 23개, 인수 사례 56개, OpenAPI 2.1.0, ui-tokens 4.0.0)는 이력입니다.

## 이번 판에서 바뀐 점

- **이름 통일(9.1)**: `severity`(L1_RECORD·L2_CHECK·L3_URGENT), `availability`(VERIFIED·DRAFT_DEMO·UNAVAILABLE·FUTURE), `source`, `quality`, `policy_state`(+`outcome`), `delivery_state`(+`transport_group`, 열람은 `opened_at`), `action`, `guidance_state`, `symptom` 9개(주행 중 큰 카드 5개), `body_area` 9개를 OpenAPI enum과 SQL CHECK에 똑같이 넣었습니다.
- **위치(9.2)**: `accuracy_m`를 지우고 `latitude`·`longitude`·`measured_at`·`received_at`·`location_confidence`(원값)·`location_source`(VEHICLE·PHONE)로 바꿨습니다.
- **차량 스냅샷(9.3)**: `VehicleSnapshot`의 값마다 `quality`·`received_at`·`source`가 있습니다. 차속은 AAOS `PERF_VEHICLE_SPEED`(m/s), 조향각은 앞바퀴 각도입니다.
- **안내 묶음**: `guide_id`는 `^G_[A-Z_]+$` 문자열입니다. uuid 키였던 `guide_versions`를 `guide_bundles`로 바꾸고 `GET /v1/guide-bundles/{guide_id}`를 넣었습니다.
- **발송 범위(D10)**: 10초 확인 뒤 자동 발송은 에어백 전개·사고 선택만, '지금 도움 요청'은 받는 사람 확인 뒤 즉시, 수동 증상은 자동 발송하지 않습니다. OpenAPI 조건문, SQL 제약, rules-lab `notification.scope`가 같은 규칙을 씁니다.
- **신호 유효성(D18)**: 3,000ms 신선도는 주기형 신호(차속)에만 씁니다. 변화 알림형 신호는 마지막 수신 시각과 서비스 연결 상태로 판단합니다.
- **개인정보(D23)**: 운전자 위치 제공 동의(`consents`), 위치 제공 기록(`location_disclosures`), 공유 차량의 `driver_profile_id`·`driver_mode`(GUEST는 자동 위치 발송 끔), 같은 차량 열린 알림 하나(`one_open_alert_per_vehicle`).
- **[향후] 입력**: 경고등 상태, 원본 녹음·영상, IMU·충격, 가속 페달, 기어, 주차 브레이크, 타이어 현재 공기압, 시동, 비상등은 스키마에서 빼고 OpenAPI `x-future-inputs`(12개)에 `availability: FUTURE`로 적었습니다. 진동·충격 판정 값은 rules-lab `future_motion_analysis`(`enabled: false`)에만 보존합니다.
- **화면(D7·D12·D13·D14)**: 세로(840×1212 시안 기준)를 기본, 가로(1280×720dp 예시)를 넓은 배치로 두고 기능 패리티 규칙을 적었습니다. 주행 중 움직임 0·단계별 정지 그림 1장입니다. #702132 위 #FF5B6B(3.565:1)는 #FFB3BA(6.355:1)로 바꿨습니다. Pleos 공식 수치와 자체 수치(80dp·24dp·22sp)는 `source`로 구분합니다.

이름의 출처는 OpenAPI 스키마의 `x-contract-source`로 구분합니다. `DECISIONS-9`는 결정 9장 고정 이름이고, `CONTRACT-V3`는 이번 계약에서 정한 보조 이름입니다(예: 증상별 안내 `G_VIBRATION_STOP` 등, `incident_trigger`, `driver_mode`).

## API 작업 41개

권한: 기기 = `deviceSession`(차량·휴대폰 기기), 소유자 = `ownerSession`(휴대폰 앱 계정), 보호자 = `guardianSession`(OTP 확인 뒤 보호자 웹). 9.4 추가 17개, 기존 유지·정리 22개, 9.4 밖 보강 2개입니다. 기존 `POST /v1/vehicles/{vehicleId}/guardians`는 `POST /v1/guardians`로 옮겼습니다. 기기 세션을 발급하는 인증 경로는 이 판의 범위 밖입니다.

| 방식 | 경로 | operationId | 권한 | 구분 |
| --- | --- | --- | --- | --- |
| POST | `/v1/vehicles` | `registerVehicle` | 기기 | 9.4 추가 |
| POST | `/v1/trips` | `startTrip` | 기기 | 9.4 추가 |
| POST | `/v1/pairing/qr` | `createPairingQr` | 기기 | 9.4 추가 |
| POST | `/v1/pairing/confirm` | `confirmPairing` | 소유자 | 9.4 추가 |
| POST | `/v1/incidents` | `createIncident` | 기기 | 유지·정리 |
| GET | `/v1/incidents` | `listIncidents` | 기기·소유자 | 9.4 추가 |
| GET | `/v1/incidents/{incidentId}` | `getIncident` | 기기·소유자 | 유지·정리 |
| POST | `/v1/incidents/{incidentId}/actions` | `applyIncidentAction` | 기기·소유자 | 유지·정리 |
| POST | `/v1/incidents/{incidentId}/corrections` | `sendCorrection` | 기기·소유자 | 9.4 추가 |
| POST | `/v1/incidents/{incidentId}/observations` | `appendObservationBatch` | 기기 | 유지·정리 |
| GET | `/v1/incidents/{incidentId}/timeline` | `getAuthorizedIncidentTimeline` | 소유자 | 유지·정리 |
| POST | `/v1/incidents/{incidentId}/manual-reports` | `appendManualReport` | 기기 | 유지·정리 |
| POST | `/v1/incidents/{incidentId}/locations` | `appendIncidentLocation` | 기기 | 유지·정리 |
| GET | `/v1/incidents/{incidentId}/locations/latest` | `getLatestAuthorizedLocation` | 기기·소유자 | 유지·정리 |
| POST | `/v1/incidents/{incidentId}/assets` | `prepareEvidenceUpload` | 기기 | 유지·정리 |
| POST | `/v1/incidents/{incidentId}/assets/{assetId}/complete` | `completeEvidenceUpload` | 기기 | 유지·정리 |
| GET | `/v1/incidents/{incidentId}/assets/{assetId}/download` | `getOwnerEvidenceDownload` | 소유자 | 유지·정리 |
| GET | `/v1/incidents/{incidentId}/deliveries` | `getIncidentDeliveries` | 기기·소유자 | 유지·정리 |
| POST | `/v1/incidents/{incidentId}/share-links` | `createMaintenanceShareLink` | 기기·소유자 | 9.4 추가 |
| POST | `/v1/share-links/{linkId}/view` | `viewMaintenanceShareLink` | 없음(본문 토큰) | 보강 |
| HEAD | `/v1/uploads/{uploadId}` | `getUploadOffset` | 기기 | 유지·정리 |
| PATCH | `/v1/uploads/{uploadId}` | `appendUploadChunk` | 기기 | 유지·정리 |
| POST | `/v1/uploads/{uploadId}/renew` | `renewAuthorizedUpload` | 기기 | 유지·정리 |
| GET | `/v1/vehicles/{vehicleId}/warning-guides` | `listVerifiedWarningGuides` | 기기·소유자 | 유지·정리 |
| PUT | `/v1/vehicles/{vehicleId}/capabilities` | `reportCapabilitySnapshot` | 기기 | 유지·정리 |
| GET | `/v1/vehicles/{vehicleId}/guardians` | `listVehicleGuardians` | 기기·소유자 | 유지·정리 |
| POST | `/v1/guardians` | `registerGuardian` | 소유자 | 9.4 추가 |
| POST | `/v1/guardians/{guardianId}/verify` | `verifyGuardianPhone` | 없음(본문 토큰) | 9.4 추가 |
| PUT | `/v1/guardians/{guardianId}/consent` | `updateVerifiedGuardianConsent` | 보호자 | 유지·정리 |
| POST | `/v1/guardians/{guardianId}/practice-alert` | `sendGuardianPracticeAlert` | 소유자 | 9.4 추가 |
| POST | `/v1/guardian-replies` | `postGuardianReply` | 보호자 | 9.4 추가 |
| POST | `/v1/consents` | `recordConsent` | 기기·소유자 | 9.4 추가 |
| GET | `/v1/consents` | `listConsents` | 기기·소유자 | 9.4 추가 |
| GET | `/v1/location-disclosures` | `listLocationDisclosures` | 기기·소유자 | 9.4 추가 |
| GET | `/v1/guide-bundles/{guide_id}` | `getGuideBundle` | 기기 | 9.4 추가 |
| POST | `/v1/maintenance-notes` | `createMaintenanceNote` | 기기·소유자 | 9.4 추가 |
| POST | `/v1/maintenance-notes/{noteId}/recheck` | `answerMaintenanceRecheck` | 기기·소유자 | 보강 |
| GET | `/v1/symptom-patterns` | `listSymptomPatterns` | 기기·소유자 | 9.4 추가 |
| GET | `/v1/shares/{shareId}` | `getAuthenticatedGuardianProjection` | 보호자 | 유지·정리 |
| POST | `/v1/shares/{shareId}/ack` | `acknowledgeAuthenticatedView` | 보호자 | 유지·정리 |
| GET | `/v1/shares/{shareId}/assets/{assetId}/download` | `getConsentedGuardianEvidenceDownload` | 보호자 | 유지·정리 |

## 검사 방법

```bash
cd docs/specs/vehicle-care
python3 validate_contracts.py --write validation-2026-11-12-v3.json
# 선택: 로컬 PostgreSQL이 있으면 검사용 DB를 만들어 DDL과 제약 시험 29개를 돌리고 지웁니다.
python3 validate_contracts.py --pg-host <소켓 폴더 또는 호스트> --pg-port <포트> --write validation-2026-11-12-v3.json
```

검사 내용은 JSON 파싱, OpenAPI `$ref` 558개 해석, operationId·경로 매개변수·보안 이름, 결정 9장 enum, OpenAPI enum ↔ SQL CHECK 49쌍과 정규식 3쌍, 스키마 예시 37개(정상 10·위반 27), rules-lab·ui-tokens·인수 사례 사이 이름과 수치(10초, 알림톡 전달 확인 기한 10,000ms, 3,000ms, 3회, 500km, 24시간, 150~200ms, CPU 5%·RAM 400MB, 보호자 5명), 인수 사례 ID 중복·참조, 대비 재계산, 배치 계산, 로컬 경로 문자열입니다. 이번 결과는 `validation-2026-11-12-v3.json`에 있습니다(PostgreSQL 16 임시 DB에서 DDL 실행과 제약 시험 포함).

하지 않은 것: 앱 빌드·실행, 에뮬레이터·Virtual Console 시연, 서버 실행, 외부 OpenAPI 표준 검사기, 카카오·문자 발송, 실차 신호·성능 측정, 인수 사례 실행, 법 조문 해석.

## 지켜야 할 원칙

- 안내 선택의 권위는 차량입니다. 서버 재계산 값은 기록·발송 정책에만 씁니다.
- 기기·소유자·보호자 세션은 서버가 검증한 상태를 씁니다. 보호자는 등록만으로 ACTIVE가 되지 않습니다. 이 계약은 임의 전화번호로 메시지를 보내는 API를 주지 않습니다.
- 토큰(보호자 초대·정비사 링크)은 URL 경로·쿼리에 넣지 않고 URL 조각(#)과 요청 본문으로만 다룹니다.
- 시험 입력은 `source`·`quality`가 `TEST_INPUT`이고 화면에 [시험 입력], 보호자 화면에 [시험]을 붙입니다. 시연 모드에서는 실제 발송을 끕니다.
- 수치는 제조사 검증값이 아닌 개발용 시작값입니다. 앱은 차를 제어하지 않고 119에 자동 신고하지 않습니다.
- 예시의 `example.invalid`, 0으로 채운 UUID는 가상 값입니다. 비밀키 값은 없으며 발송사 키를 차량·휴대폰에 두지 않습니다.

관련 문서: [통합 PRD](../../analysis/integrated-vehicle-care-2026-11-12.md). 후속 작업 전에 [AGENTS.md](../../../AGENTS.md)를 먼저 읽고 PRD와 계약을 함께 갱신합니다.
