# 비판 검토 2차 라운드 공통 지침 (플레오스 가드 v4)

먼저 1차 지침 `S/critique/BRIEF.md`를 끝까지 읽으세요(상황, 사용자 확정 사항, 공모전 조건, 메인 세션 재확인 사실, 산출물 형식, 규칙은 그대로 적용). 아래는 2차 라운드에서 달라진 점입니다.

S = `/tmp/claude-0/-home-user-pleos-super-guard/d80986fe-1ca5-544d-bfbe-5e328b20367c/scratchpad`, 저장소 = `/home/user/pleos_super_guard/hyundai_app_market`

## 1차 라운드 뒤 바뀐 것
- 메인 세션이 1차 비판 214개를 재검증해 결정한 기준: `S/critique/r1/DECISIONS.md` (1차 정리 색인은 `S/critique/r1/INDEX.md`).
- 이 결정에 따라 모든 문서를 v4로 고쳤습니다. **2차 라운드의 비판 대상은 아래 v4 파일입니다.**
  - 공식 양식 v4(14쪽): 텍스트 `S/current/form01-v4-docx.txt`, 원고 `docs/submission/official-content-3000/0[2-6]-*.txt`(각 3,000자), 1·7·8번과 그림 목록 `docs/submission/official-form-v4.json`, 쪽 이미지 `output/submission/qa/v4-r1/render/01/`
  - 상세 별첨 v4(37쪽): 원고 `docs/submission/annex-v4/annex.md`, 텍스트 `S/current/annex02-v4-docx.txt`, 쪽 이미지 `output/submission/qa/v4-r1/render/02/`
  - PRD v4.0: `docs/analysis/integrated-vehicle-care-2026-11-12.md` (v3 이력 `-prd-v3.md`)
  - 개발 계약: `docs/specs/vehicle-care/` (OpenAPI 3.0.0, DB v3, rules-lab 2.0.0, ui-tokens 5.0.0, acceptance 3.0.0, `validate_contracts.py`, `validation-2026-11-12-v3.json`, README)
  - 화면: `output/submission/assets/v4/portrait/`(15장), `landscape/`(14장), `diagrams/`(4장), `composite/`, 화면 명세 `docs/analysis/ux-v4-section.md`
  - SDK 문서: `docs/analysis/pleos-sdk-review.md`, `docs/sdk/*`(새 `vehicle-api-catalog.md`, `llm.md`), `docs/analysis/pleos-app-guidelines-2026-11-12.md`
  - 공모전 문서: `docs/analysis/hyundai-vehicle-app-contest-2026.md`, `-sources.md`
  - 기록: `docs/HANDOFF.md`, `AGENTS.md`, `docs/analysis/submission-documents-2026-11-12.md`, `docs/analysis/submission-documents-verification-2026-11-12-v4.json`, `docs/submission/official-content-3000/README.md`, `docs/submission/github-readme-template.md`
  - 생성기: `output/submission/build_v4.py`, `build_ui_v4.py`, `build_diagrams_v4.py`, `build_composites_v4.py`
- 메인 세션이 이미 알고 있는 v4 불일치(중복 지적하지 말고, 고칠 방향이 있으면 제안만):
  1. 주행 중 상황 선택 카드 수: DECISIONS 9.1은 5개, ux-v4-section은 긴급 3개만.
  2. "안 멈춰요"의 뜻: 도표 guidance-state-v4는 원치 않는 가속 안내로, ux-v4-section은 감속 실패 대처로 연결. 공식 양식 4번 10문단은 "원하지 않게 차가 빨라지는 느낌이 들 때 '안 멈춰요' 버튼"이라고 씀.
  3. ui-tokens 5.0.0의 시안 파일 목록(세로 11·가로 7)이 실제 v4 폴더(세로 15·가로 14)와 다름.
  4. 안전벨트 착용은 Virtual Console 카드가 없어 화면은 [시험 입력]인데 DECISIONS 7장 신호 표는 [시연]만 표기.

## 2차 라운드에서 할 일
1. **1차 결정이 제대로 반영됐는지 검증**: 당신 관점과 관련된 DECISIONS 항목(D·N 번호)이 v4 파일에 실제로 반영됐는지 파일:줄로 확인하고, 빠졌거나 잘못 반영된 곳을 지적합니다.
2. **v4에서 새로 생긴 문제**: 고치면서 생긴 오류, 과장, 문서 간 불일치(양식 ↔ 별첨 ↔ PRD ↔ 계약 ↔ 화면 ↔ UX 문서 ↔ HANDOFF), 숫자 불일치를 찾습니다.
3. **아직 약한 부분**: 심사·규정·안전·사업성 관점에서 v4도 여전히 약한 곳.
4. **새 아이디어**: 1차에서 채택되지 않았거나 새로 떠오른 아이디어(3~6개). 이미 채택된 N1~N12와 겹치지 않게.

## 산출물
`S/critique/r2/<ID>.md`. 형식은 1차와 같고, 각 비판에 `유형: 반영 누락 | 잘못 반영 | 새 문제 | 여전히 약함`을 덧붙입니다. 공식 양식 원고 수정 제안은 3,000자를 유지하도록 같은 분량의 교체 문장으로 제시합니다. 비판은 10~25개로, 중요한 것부터.

마지막 응답: 산출물 경로와 400~700자 요약.
