# 플레오스 가드 개발 계약 파일

기준: **2026-11-12 / PRD 3.0**. 현재 구현된 서비스나 설치된 데이터베이스가 아닙니다. `validation-2026-11-12.json`은 이전 PRD 2.0 시점의 정적·수치 검사 기록이며 현재 화면 구현이나 실차 동작을 검증한 자료가 아닙니다. 차량 부위 선택은 OpenAPI 2.1.0의 `selected_region` 및 SQL에 반영됐고, 현재 UI 설계값은 `ui-tokens.json` v4.0.0입니다.

| 파일 | 역할 |
| --- | --- |
| [통합 PRD·기술 명세](../../analysis/integrated-vehicle-care-2026-11-12.md) | 요청한 여섯 장의 제품·기술 기준 |
| [api.openapi.json](api.openapi.json) | 이 앱이 개발할 API의 OpenAPI 3.1 계약. Pleos·카카오 공식 API와 구분 |
| [database.sql](database.sql) | PostgreSQL의 초기 테이블·외래키·고유 제약·작업 인덱스 제안 |
| [rules-lab.json](rules-lab.json) | 단위·자료 나이·주파수·임계값·심각도·발송 정책의 개발용 설정 |
| [ui-tokens.json](ui-tokens.json) | 지정 레퍼런스를 반영한 색·크기·가로형 앱 창의 기준 좌표 |
| [acceptance-scenarios.json](acceptance-scenarios.json) | 구현 뒤 실행할 인수 사례 56개. 현재 통과한 앱 시험이 아님 |
| [validation-2026-11-12.json](validation-2026-11-12.json) | 이번 문서·수치·계약 검사의 실제 범위와 한계 |

프론트엔드는 계약 모의 응답으로 화면·상태 복원을, 백엔드는 소유권·사건·작업·전달 상태를, 차량 연동은 실제 Capability와 단위·주기·권한을 먼저 확인합니다. 임계값은 제조사 검증값이 아니며 `production_motion_model_approved=false` 상태입니다. 실제 수신자에게 보내는 시험은 이번 작업에 포함하지 않습니다.

기기/사용자 인증과 보호자의 번호 검증은 서버가 검증한 세션·동의 상태를 사용해야 합니다. 등록 요청만으로 ACTIVE가 되지 않습니다. 인증 구현은 번호 도용·시도 제한·만료·철회 시험을 완료한 뒤 실제 발송을 켭니다. 이 계약은 임의 전화번호로 메시지를 보내는 공개 API를 제공하지 않습니다.

예시 URL의 `example.invalid`, 전부 0으로 된 전화번호·UUID는 가상 값입니다. 비밀키 값은 포함하지 않으며 발송사 키를 차량·휴대폰에 배포하지 않습니다. 후속 작업에서는 [AGENTS.md](../../../AGENTS.md)를 먼저 읽고 PRD와 계약을 함께 갱신합니다.
