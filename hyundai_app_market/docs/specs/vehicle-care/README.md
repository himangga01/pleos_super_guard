# 플레오스 가드 개발 계약 파일 (계약 v3.1.1)

기준: **2026-11-13 사용자 결정(확인 질문의 말 답, 긴급 안전 예외)** 위에 **2026-11-12 2차 비판 반영 결정(B·C·D·F·G·H)**. 1차 비판 결정 9장(계약 고정 사항)의 이름 규칙은 그대로 쓰고, 2차 결정으로 더한 이름은 OpenAPI `x-contract-source: DECISIONS-R2`로 구분합니다.

이 폴더는 앞으로 만들 앱과 서버의 **설계 계약**입니다. 구현된 서비스나 설치된 데이터베이스가 아닙니다. 아래 검사는 **정적 검사**(와 검사용 임시 PostgreSQL에서 DDL·제약을 시험해 본 것)이며, **앱·서버를 실행한 증거가 아닙니다**. 에뮬레이터 시연, 실제 발송, 실차 동작도 확인하지 않았습니다.

## 파일 목록

| 파일 | 판 | 개수 | 역할 |
| --- | --- | --- | --- |
| [api.openapi.json](api.openapi.json) | 3.1.1 (형식 OpenAPI 3.1.0) | 경로 56개, 작업 59개, 스키마 171개, 보안 방식 3개 | 이 앱이 만들 서버 API. Pleos·카카오 공식 API가 아님 |
| [database.sql](database.sql) | v3.1 | 테이블 36개, 뷰 1개, 외래키 70개, CHECK 198개, 명시 인덱스 23개(부분 고유 6개) | PostgreSQL 스키마 제안 |
| [rules-lab.json](rules-lab.json) | 2.2.0 (`pleos-care-lab-20261112-r3`, 판정 값이 같아 그대로) | 공개 신호 목록 23개 | 신호 유효성·발송 범위·성능 예산·수치의 개발용 시작값 |
| [ui-tokens.json](ui-tokens.json) | 5.2.0 | 화면 20개, 시안 세로 17장·가로 16장(폴더 실제 목록), 화면 문구 15행, 대비 조합 18개(최소 5.674:1) | 세로·가로 배치, 버튼 자리(F1), G 화면 문구표, 긴급 안전 예외, Pleos 공식 수치와 자체 수치 |
| [acceptance-scenarios.json](acceptance-scenarios.json) | 3.2.0 | 135개(v2 승계 56 + v3 47 + 2차 30 + 2026-11-13 2). 시연 71·설계 56·향후 8 | 구현 뒤 실행할 인수 사례. **통과한 앱 시험이 아님** |
| [validate_contracts.py](validate_contracts.py) | - | 정적 검사 518개(2026-11-13). v3.1 판은 560개(PostgreSQL 포함, 정적만 493개) | 계약 검사기(python3 표준 라이브러리만) |
| [validation-2026-11-12-v3.1-final.json](validation-2026-11-12-v3.1-final.json) | - | PASS, 실패 0 | **v3.1 판** 최종 검사 결과. v3.1.1의 결과 파일은 아직 만들지 않았습니다(정적 검사 518개 PASS만 확인) |
| [validation-2026-11-12-v3.1.json](validation-2026-11-12-v3.1.json) | - | PASS, 실패 0 | v3.1 판 중간 기록(보호자 3명 정정 전) |
| [validation-2026-11-12-v3.json](validation-2026-11-12-v3.json) | - | - | **v3 시점 기록**(353개 PASS). 이번 판 검사가 아님 |
| [validation-2026-11-12.json](validation-2026-11-12.json) | - | - | **v2 시점 기록**. 이번 판 검사가 아님 |
| [ui-tokens-prd-v2.json](ui-tokens-prd-v2.json) | 1.0.0 | - | 이력. 그대로 둠 |
| [history/2026-11-12-v3.0/](history/2026-11-12-v3.0/) | 계약 v3 | 파일 8개 | v3.1로 바꾸기 전 원본(바이트 동일 사본: 계약 5개·README·검사기·v3 검사 결과) |
| [history/2026-11-12-v2.1/](history/2026-11-12-v2.1/) | 2.1.0 등 | 파일 7개 | v3로 바꾸기 전 원본(바이트 동일 사본) |

이전 판 수치는 이력입니다. v3: OpenAPI 3.0.0 작업 41개·경로 38개·스키마 132개, DB v3 테이블 32개, rules-lab 2.0.0 신호 19개, ui-tokens 5.0.0 시안 세로 11·가로 7(실제 폴더와 달랐음), 인수 사례 103개, 검사 353개. v2.1: 작업 23개, 테이블 23개, 인수 사례 56개.

## 2026-11-13 판(v3.1.1)에서 바뀐 점

사용자가 2026-11-13에 내린 두 결정을 반영했습니다. 이 결정은 이전 결정보다 우선합니다. 질문 조건·억제 값, 버튼 자리, DB(v3.1)는 바꾸지 않았습니다. v3.1 원본은 Git 이력에 있습니다.

- **확인 질문은 화면이나 말로 답합니다.** 센서 조합(브레이크 밟음 + 1,000ms 동안 감속 미관측 등)이 맞으면 앱이 화면과 음성으로 "차가 멈추지 않나요?"를 묻고, 운전자는 버튼을 누르거나 버튼 없이 바로 말로 답합니다. 마이크는 "말로 하기"를 누른 뒤나 확인 질문 직후에만 최대 8초 열립니다(상시 듣기 없음). rules-lab `guidance.confirm_prompt_answers`(`answer_modes`, `voice_listen_window_ms = 8000`, `voice_listen_starts`, `no_answer_result`), `guidance.voice_entry.confirm_prompt_voice_answer_without_button`, `voice.mic_open_only_after`, ui-tokens `screens[confirm_prompt].answer_modes`, OpenAPI `GuidanceEntry` 설명(옛 '예' 버튼 문구 정리)과 `x-sdk-notes`에 적었습니다.
- **긴급 안전 예외(사용자 결정 2026-11-13).** 원치 않는 가속 안내(`UNWANTED_ACCEL_GUIDE`)와 감속 실패 대처(`DECEL_FAILURE_GUIDE`)는 주행 중에도 음성 단계와 같은 페달 그림·설명 애니메이션을 보여 주고, 시각 영역 왼쪽에 아반떼 실사 뒷모습 사진을 둡니다. Pleos 디자인 가이드(G7·G8)와 의도적으로 다른 결정입니다. ui-tokens `emergency_safety_exception`(원문 인용, 이유, 위험, 완화, 대체 조건), `driving_mode.motion_exceptions`, `animation.driving_motion_exception`, rules-lab `guidance.emergency_visual`, OpenAPI `GuideStep.motion_while_driving`(boolean, `GuideBundle.allOf`로 `G_UNWANTED_ACCEL`·`G_DECEL_FAILURE`만 true)에 같은 이름과 값을 씁니다. Pleos가 허용하지 않거나 `NO_VIDEO` 같은 주행 제한이 걸리면 정지 페달 그림(`still_image_asset_id`)으로 자동으로 바꿉니다. 그 밖의 주행 중 화면은 움직임이 없습니다.
- **비상 절차 미리 익히기**는 긴급 안내와 같은 그림을 천천히 따라 해 보는 연습으로 설명을 맞췄습니다(`animation.parked_practice_reuses_emergency_animation`, `guidance.practice.purpose`).
- **acceptance 3.2.0**: T60·T65·T97·T103·T126의 기대값을 고쳤고(`changed_in` 3.2.0), T134(확인 질문에 버튼 없이 말로 답하기, 8초 뒤 닫힘)와 T135(애니메이션 제한 때 정지 페달 그림 대체)를 더했습니다(`added_in` 3.2.0). T103은 `motion_while_driving=true`를 기대합니다.
- **검사기**: 판 번호, `d7:driving_motion_none_except_u13_exception`(움직임 없음 규칙의 예외는 긴급 안전 예외 하나뿐), 새 검사 `u13:*` 17개(말 답 8초, 마이크가 열리는 두 경우, 예외 범위 두 화면, 묶음 이름과 상태 대응, 정지 그림 대체, 완화 항목, G7·G8 원문 유지, 시각 영역 배치, 차 사진 파일, OpenAPI 조건, 미리 익히기, 새 인수 사례, 옛 규칙 문장 없음), 스키마 예시 8개(정상 4·위반 4)를 더했습니다. 정적 검사 518개 PASS(2026-11-13)입니다. PostgreSQL 포함 검사와 결과 파일 작성은 이번에 하지 않았습니다.

## v3.1에서 바뀐 점

- **보호자 링크 2단계 열람(D1·H5)**: 알림 링크의 조각 토큰을 본문으로 보내는 `openGuardianShareSession`(1단계: 무슨 일·시각·먼저 할 일만, 위치 없음, 이때 `opened_at` 기록)과 `verifyGuardianShareSession`(2단계: 휴대폰 번호 끝 4자리, 5회 틀리면 15분 잠금)을 더했습니다. 테이블 `guardian_share_sessions`.
- **시험 입력은 신호 단위(D2)**: 관측·스냅샷 값 중 하나라도 `source=TEST_INPUT`이면 `test_mode=true`·`contains_test_input=true`이고 모의 발송(`dispatch_mode=MOCK`)만 합니다. OpenAPI `IncidentCreate.allOf`(`contains` 포함), SQL `jsonb_path_exists` CHECK, 관측·정책의 `(incident_id, test_mode)` 복합 외래키가 같은 규칙을 지킵니다.
- **이번 운전자·trip(D3)**: `createDriverProfile`, `listVehicleDriverProfiles`, `setTripDriver`(운전자가 바뀌면 이전 trip을 `DRIVER_CHANGED`로 닫고 새 trip), `endTrip`(서버 자동 종료 `LONG_PARKED`·`DEVICE_SILENT`). 게스트는 자동 위치 발송 꺼짐, 위치 동의는 정차 중에만·이번 trip만(`ConsentScope TRIP`). 여러 운전자 차량에서 칩에 답이 없으면 `UNKNOWN`.
- **열린 알림 하나 + 2시간 창(D4)**: `one_open_alert_per_vehicle`는 `alert_window_closed_at IS NULL`인 열린 알림에만 걸리고, 창은 생성 뒤 2시간 이하(`rules-lab notification.open_alert_window_ms`). 정정은 정책 상태를 되돌리지 않아 예외입니다. 묶인 사건의 CANCEL은 같은 창의 열린 알림 전체를 멈추고 `cancelled_incident_ids`로 돌려줍니다(`alert_incident_id`).
- **공유 중지·삭제(D5·H6)**: `stopIncidentLocationSharing`(동의는 그대로), `revokeMaintenanceShareLink`, `removeGuardian`(소유자 삭제·보호자 탈퇴), `unpairDevice`(기기 해제·차량 기록 삭제 명령), `deleteAccount`(72시간 안 파기). 위치 제공 기록은 지우지 않고 익명화합니다: 외래키 `ON DELETE SET NULL`, 가린 받는 사람 표시·정밀도(`location_precision`)는 남김.
- **QR 연결·소유자 이전·시간 동기화(D6)**: 휴대폰 확인 뒤 차 화면 [연결](`confirmPairingOnVehicle`)이 있어야 `CONFIRMED`. 소유자 이전(`startOwnershipTransfer`·`cancelOwnershipTransfer`)은 owner_id를 바꾸지 않고 새 등록을 만들며 이전 기록을 옮기지 않습니다. `syncServerTime`의 서버 시각으로 오차를 재고, `SYNCHRONIZED`는 `clock_sync` 표본이 있어야 합니다.
- **도착 알림(H7)·정비소 도착 체크인(H8)**: `updateArrivalAlertSetting`(기본 꺼짐, 사용자 결정 항목), `reportTripArrival`(NaviHelper `onDestinationArrived`, 장소 없음), `recordServiceCheckin`(FusedLocation `addProximityAlert` ENTER·`onWayPointArrived`, 좌표는 서버로 보내지 않음).
- **SDK 설명 정정(B1~B5·F9)**: OpenAPI `x-sdk-notes`와 rules-lab `service_stop`에 `addWaypoint`·`requestRoute`·`requestRouteWithWaypoints` = `NAVI_ROUTE_SEARCH`, `requestTrip` = `NAVI_ROUTE`, `requestNavigationScreen` = `NAVI_CUSTOM_MAP`을 적었습니다. 권한 없음(SecurityException·onFailed)과 미지원(Capability)을 나눴고, `CAR_SPEED`는 dangerous(거부 시 확인 질문·재현 조건 속도 꺼짐), TTS 음성 앞 무음은 앱이 넣지 못함(`TtsLine.leading_pause_text`, 고정 음원에만 150~200ms), 기어는 쓰지 않음.
- **rules-lab 2.1.0**: 확인 질문 반복 억제(같은 trip 5분에 1회), 확인 질문 버튼(왼쪽 괜찮아요·오른쪽 안 멈춰요), 브레이크 눌림 감속 미관측 1,000ms 유지, 알림톡 전달 확인 기한 10,000ms 유지, 말로 하기 = 버튼 한 번 누르면 최대 8초(누르고 있기 없음)와 경로 나누기(H1), 주행 중 상황 선택 5개(B9), 재현 조건의 평소 주행 대비 비교·누적 값 제외(B12), 공조·창문·와이퍼 신호 4개(H3), 위치 신선도 60초(`location_fix`), 보관 규칙(`retention`).
- **ui-tokens 5.1.0**: 시안 목록을 `output/submission/assets/v4` 실제 폴더(세로 17·가로 16, 새 `p-confirm`·`ui-confirm`·`p-manual-parked`·`ui-manual-parked` 포함)와 맞췄고, 화면 이름을 G표 기준으로 바꿨습니다(긴급 안내 → 원치 않는 가속 안내, 홈(주행 중)·확인 질문 추가). 버튼 자리 규칙 F1(왼쪽 끝내기·취소·기록 중립, 오른쪽 도움·보내기·감속 실패 빨강), `screen_copy`(G 화면 문구표 15행), 탭(경고등 찾기·증상 기록·보호자 알림·정비소), 주행 중 홈 버튼 3개(지금 기록·말로 하기·도움이 필요해요, 보호자에게 알리기 없음), 앱 안 지도 없음(B11).
- **acceptance 3.1.0**: T71(주행 중 홈 보호자 알리기 삭제)·T97(브레이크 600ms → 1,000ms) 등 24개 사례의 기대값을 고쳤고(`changed_in`), D1~D6·F1·F2·H1·H3·H7·H8·B4·B11 사례 30개(T104~T133, `added_in`)를 더했습니다. 고친 사례의 `demo_method`·`availability`는 그대로입니다. B10(출발 전 점검)은 T58을 고쳐 반영했습니다.

결정끼리 어긋난 곳은 G 화면 문구표와 F1을 따랐습니다. B8은 원치 않는 가속 안내의 왼쪽을 '안 멈춰요', 감속 실패 대처의 왼쪽을 '도움 요청'으로 적었지만, G표·F1은 빨간 '안 멈춰요'·'도움 요청'을 오른쪽에 둡니다. 1차 9.1의 주행 중 증상 카드 5개(떨림·냄새·타이어·사고·잘 모르겠음)는 2차 B9의 5개(안 멈춰요·저절로 빨라져요·사고가 났어요·차체 떨림·타는 냄새)로 바꿨습니다. `guide_id` 정규식(`^G_[A-Z_]+$`)은 1차 9장 규칙대로 두었습니다.

## API 작업 59개

권한: 기기 = `deviceSession`(차량·휴대폰 기기), 소유자 = `ownerSession`(휴대폰 앱 계정), 보호자 = `guardianSession`(2단계 확인 뒤 보호자 웹). '(운전자 본인)'은 `x-authz` 규칙(그 사건 trip 중인 차량 기기 또는 그 사건 운전자의 휴대폰 계정만, 다른 계정은 403 `DRIVER_MISMATCH`)입니다. 구분: 9.4 추가 17개, 유지·정리 22개, 9.4 밖 보강 2개, 2차 결정 추가 18개. 기기 세션을 발급하는 인증 경로는 이 판의 범위 밖입니다.

| 방식 | 경로 | operationId | 권한 | 구분 |
| --- | --- | --- | --- | --- |
| POST | `/v1/vehicles` | `registerVehicle` | 기기 | 9.4 추가 |
| POST | `/v1/trips` | `startTrip` | 기기 | 9.4 추가 |
| POST | `/v1/pairing/qr` | `createPairingQr` | 기기 | 9.4 추가 |
| POST | `/v1/pairing/confirm` | `confirmPairing` | 소유자 | 9.4 추가 |
| POST | `/v1/incidents` | `createIncident` | 기기 | 유지·정리 |
| GET | `/v1/incidents` | `listIncidents` | 기기·소유자 | 9.4 추가 |
| GET | `/v1/incidents/{incidentId}` | `getIncident` | 기기·소유자 | 유지·정리 |
| POST | `/v1/incidents/{incidentId}/actions` | `applyIncidentAction` | 기기·소유자(운전자 본인) | 유지·정리 |
| POST | `/v1/incidents/{incidentId}/corrections` | `sendCorrection` | 기기·소유자(운전자 본인) | 9.4 추가 |
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
| GET | `/v1/consents` | `listConsents` | 기기·소유자(운전자 본인) | 9.4 추가 |
| GET | `/v1/location-disclosures` | `listLocationDisclosures` | 기기·소유자(운전자 본인) | 9.4 추가 |
| GET | `/v1/guide-bundles/{guide_id}` | `getGuideBundle` | 기기 | 9.4 추가 |
| POST | `/v1/maintenance-notes` | `createMaintenanceNote` | 기기·소유자 | 9.4 추가 |
| POST | `/v1/maintenance-notes/{noteId}/recheck` | `answerMaintenanceRecheck` | 기기·소유자 | 보강 |
| GET | `/v1/symptom-patterns` | `listSymptomPatterns` | 기기·소유자 | 9.4 추가 |
| GET | `/v1/shares/{shareId}` | `getAuthenticatedGuardianProjection` | 보호자 | 유지·정리 |
| POST | `/v1/shares/{shareId}/ack` | `acknowledgeAuthenticatedView` | 보호자 | 유지·정리 |
| GET | `/v1/shares/{shareId}/assets/{assetId}/download` | `getConsentedGuardianEvidenceDownload` | 보호자 | 유지·정리 |
| POST | `/v1/shares/{shareId}/session` | `openGuardianShareSession` | 없음(본문 토큰) | 2차 D1 |
| POST | `/v1/shares/{shareId}/session/verify` | `verifyGuardianShareSession` | 없음(본문 토큰) | 2차 D1 |
| POST | `/v1/driver-profiles` | `createDriverProfile` | 소유자 | 2차 D3 |
| GET | `/v1/vehicles/{vehicleId}/driver-profiles` | `listVehicleDriverProfiles` | 기기·소유자 | 2차 D3 |
| PUT | `/v1/trips/{tripId}/driver` | `setTripDriver` | 기기 | 2차 D3 |
| POST | `/v1/trips/{tripId}/end` | `endTrip` | 기기·소유자 | 2차 D3 |
| POST | `/v1/incidents/{incidentId}/location-sharing/stop` | `stopIncidentLocationSharing` | 기기·소유자(운전자 본인) | 2차 D5 |
| POST | `/v1/share-links/{linkId}/revoke` | `revokeMaintenanceShareLink` | 기기·소유자(운전자 본인) | 2차 D5 |
| DELETE | `/v1/guardians/{guardianId}` | `removeGuardian` | 소유자·보호자 | 2차 D5 |
| DELETE | `/v1/devices/{deviceId}` | `unpairDevice` | 소유자·기기 | 2차 D5 |
| DELETE | `/v1/me` | `deleteAccount` | 소유자 | 2차 D5 |
| POST | `/v1/pairing/{pairingId}/vehicle-confirm` | `confirmPairingOnVehicle` | 기기 | 2차 D6 |
| POST | `/v1/vehicles/{vehicleId}/ownership-transfers` | `startOwnershipTransfer` | 소유자·기기 | 2차 D6 |
| POST | `/v1/vehicles/{vehicleId}/ownership-transfers/{transferId}/cancel` | `cancelOwnershipTransfer` | 소유자 | 2차 D6 |
| POST | `/v1/time-sync` | `syncServerTime` | 기기·소유자 | 2차 D6 |
| PUT | `/v1/driver-profiles/{driverProfileId}/arrival-alert` | `updateArrivalAlertSetting` | 소유자·기기 | 2차 H7 |
| POST | `/v1/trips/{tripId}/arrival` | `reportTripArrival` | 기기 | 2차 H7 |
| POST | `/v1/service-checkins` | `recordServiceCheckin` | 기기·소유자 | 2차 H8 |

## 검사 방법

```bash
cd docs/specs/vehicle-care
python3 -I validate_contracts.py --write validation-2026-11-13-v3.1.1.json
# 선택: 로컬 PostgreSQL이 있으면 검사용 DB를 만들어 DDL과 제약 시험 65개를 돌리고 지웁니다(postgres 계정으로 실행).
python3 -I validate_contracts.py --pg-host <소켓 폴더 또는 호스트> --pg-port <포트> --write validation-2026-11-13-v3.1.1.json
```

결과 파일 이름은 판마다 새로 정합니다. 이전 판의 결과 파일(`validation-2026-11-12-v3.1*.json`)을 덮어쓰지 않습니다.

검사 내용은 JSON 파싱(현재 판·이력 v3.0·v2.1), OpenAPI `$ref` 752개 해석, operationId·경로 매개변수·보안 이름, 결정 9장 enum, v3 작업 41개 유지와 2차 작업 18개 존재, OpenAPI enum ↔ SQL CHECK 62쌍과 정규식 3쌍, 스키마 예시 71개(정상 22·위반 49), 2차 결정 반영(시험 입력 규칙, 열린 알림 2시간 창, 익명화 외래키, QR 차 화면 확인, 게스트 이번 trip 동의, 확인 질문 반복 억제, 말로 하기 8초, CAR_SPEED dangerous, 재현 조건 누적 값 제외, 공조·창문·와이퍼 신호), 2026-11-13 사용자 결정 반영(확인 질문의 말 답 8초와 마이크가 열리는 두 경우, 긴급 안전 예외의 범위·대체·완화와 파일 사이 일치), ui-tokens 시안 목록 = `output/submission/assets/v4` 실제 폴더, G 화면 문구표·버튼 자리, rules-lab·ui-tokens·인수 사례 사이 이름과 수치(10초, 알림톡 전달 확인 기한 10,000ms, 3,000ms, 1,000ms, 5분 1회, 8초, 3회, 500km, 24시간, 2시간, 72시간, 고정 음원 150~200ms, CPU 5%·정차 25%·RAM 400MB, 보호자 3명), 인수 사례 ID·참조·승계, 대비 재계산, 배치 계산, 로컬 경로 문자열입니다. PRD와는 대조하지 않습니다(계약 파일끼리만). 이번 결과는 `validation-2026-11-12-v3.1.json`에 있습니다(PostgreSQL 16 임시 DB에서 DDL 실행과 제약 시험 65개 포함, 실행 뒤 DB를 지우고 클러스터를 껐습니다).

검사기 밖에서 한 번 더 확인한 것: 로컬 가상환경의 `openapi-spec-validator`로 `api.openapi.json`이 OpenAPI 3.1 문법상 유효함을, `jsonschema`(2020-12)로 스키마 171개의 형식과 위 스키마 예시 71개의 판정이 검사기와 같음을 확인했습니다. 이 두 도구는 검사기에 들어 있지 않습니다.

하지 않은 것: 앱 빌드·실행, 에뮬레이터·Virtual Console 시연, 서버 실행, 카카오·문자 발송, 실차 신호·성능 측정, 인수 사례 실행, PRD 대조, 법 조문 해석.

## 지켜야 할 원칙

- 안내 선택의 권위는 차량입니다. 서버 재계산 값은 기록에만 쓰고 `requested_state`를 바꾸지 않습니다.
- 기기·소유자·보호자 세션은 서버가 검증한 상태를 씁니다. 보호자는 등록만으로 ACTIVE가 되지 않고, 알림 페이지도 2단계 확인 뒤에만 위치를 보여 줍니다. 이 계약은 임의 전화번호로 사고 알림을 보내는 API를 주지 않습니다.
- 토큰(보호자 초대·알림 링크·정비사 링크)은 URL 경로·쿼리에 넣지 않고 URL 조각(#)과 요청 본문으로만 다룹니다.
- 시험 입력은 신호마다 `source`·`quality`가 `TEST_INPUT`이고 화면에 [시험 입력], 보호자 화면에 [시험]을 붙입니다. 시험 입력이 섞인 사건은 모의 발송만 합니다.
- 버튼 자리는 앱 전체에서 같습니다: 왼쪽 = 끝내기·취소·기록(중립색), 오른쪽 = 도움·보내기·감속 실패(빨강). 화면 문구는 ui-tokens `screen_copy`를 씁니다. 앱 안에는 지도를 그리지 않습니다.
- 마이크는 "말로 하기"를 누른 뒤나 앱이 확인 질문을 한 직후에만 최대 8초 열립니다. 확인 질문에는 화면이나 말로 답합니다.
- 주행 중 움직임은 긴급 안전 예외(긴급 안내 두 화면의 페달 그림·설명 애니메이션) 하나뿐입니다. 제한이 걸리면 정지 페달 그림으로 바꿉니다.
- 위치 제공 기록은 삭제 대신 익명화합니다. '공유 중지'는 이번 사건 위치만 멈추고 동의는 그대로 둡니다.
- 수치는 제조사 검증값이 아닌 개발용 시작값입니다. 앱은 차를 제어하지 않고 119에 자동 신고하지 않습니다.
- 예시의 `example.invalid`, 0으로 채운 UUID는 가상 값입니다. 비밀키 값은 없으며 발송사 키를 차량·휴대폰에 두지 않습니다.

관련 문서: [통합 PRD](../../analysis/integrated-vehicle-care-2026-11-12.md). 후속 작업 전에 [AGENTS.md](../../../AGENTS.md)를 먼저 읽고 PRD와 계약을 함께 갱신합니다. PRD v4.2(2026-11-13)는 이 판(계약 v3.1.1)과 이름을 맞췄습니다.

## 2026-11-12 추가 정정: 보호자 최대 3명

결정 C4에 맞춰 보호자 수를 최대 5명에서 **3명(모두 무료)**으로 바꿨습니다(`rules-lab.json` `guardian_limit`, OpenAPI `guardian_ids`·`recipients_shown_guardian_ids`·보호자 목록 `maxItems`, `database.sql` `vehicle_guardians.slot`). 바꾼 뒤 정적 검사만 다시 실행했고 결과는 `validation-2026-11-12-v3.1-guardian3-static.json`(PASS)입니다. PostgreSQL 제약 시험이 들어 있는 `validation-2026-11-12-v3.1.json`은 이 정정 전 실행 기록입니다. 이어서 남은 DB CHECK 4곳(보호자 목록 길이)도 3명으로 고치고, PostgreSQL 시험의 보호자 자리 사례를 4번째 자리 거부로 바꾼 뒤 PostgreSQL 16 임시 DB까지 포함해 다시 검사했습니다. **현재 판의 최종 결과는 `validation-2026-11-12-v3.1-final.json`(560개 PASS)입니다.**
