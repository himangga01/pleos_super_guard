# 1차 비판 반영 결정 (메인 세션 재검증 결과) — v4 설계·기획 기준

작성: 2026-11-12. 비판 9개(C1~C9)와 정리 색인(INDEX.md)을 메인 세션이 원문으로 다시 확인한 결과입니다. 아래 "확인 근거"는 메인 세션이 직접 열어 본 원문입니다. 모든 수정 작업은 이 문서를 기준으로 합니다.

## 0. 바꾸지 않는 것 (사용자 확정 사항)
- 앱 이름 플레오스 가드, Pleos 탑재 차량 전용, 차량 앱 주제품·휴대폰 보조, 아이디어 2·5·6 통합(경고등 도감, 순간 증상 기록·긴급 안내, 사고·보호자 통지).
- 개인 참가, 개인정보·부문·날짜·동의·서명 공란. 01~04 구조와 1~8번 순서. 03·04 원본 디자인.
- 2~6번 각 3,000자(Word 공백 포함, 줄바꿈 1자), 각 12문단. 1·7·8번은 짧게.
- 페이퍼로지(문서)·Pretendard(앱), 공모전 양식 제목 계층, 아이콘 사용(이모지 금지), 진입 화면 구성(실제 홈 뒤·큰 앱 이름·디 올 뉴 아반떼 앞, PLEOS·GUARD 외곽선).
- 보호자 통지 10초 확인·즉시 발송·취소, 알림톡 우선·문자 전환(서버 담당), 접수·전달·열람 구분. (적용 범위만 아래 D7에서 정리)
- 쉬운 한국어·존댓말·짧은 평서문·반복 금지·구현과 계획 분리.
- 앱 이름의 'Pleos' 사용은 실제 입점 때 상표 검토가 필요하다는 위험만 기록하고 이름은 유지합니다(사용자 결정).

## 1. 공모전 규정 준수 (치명)
- D1. **공지 8 분리 원칙**: 모든 문서에서 기능을 세 등급으로 표시합니다.
  - [시연] 공개 SDK·Pleos 문서 경로로 지금 만들고 에뮬레이터에서 보여 줄 수 있는 기능
  - [설계] 공개 SDK로 가능하지만 서버·휴대폰 앱 등 추가 구현이 필요한 기능
  - [향후] 공개 문서에 없는 API·권한·장치가 필요한 기능 → 기획서에 "향후 계획"으로만 적습니다.
  - [향후]로 옮길 것(확인 근거: 14-sdk-vehicle.md, SafetyImpl.kt:335, 15-sdk-others.md): 경고등 상태 읽기(`PRIVATE_CAR_SAFETY`, signature|privileged), 원본 녹음·영상·차량 카메라·블랙박스, 진동 분석용 가속도·자이로·휠 신호, 충격 센서, 가속 페달 값, 기어(숨김 API), 주차 브레이크, 타이어 공기압 현재값, 시동 상태, 비상등, 차량 전원 차단 뒤 차량 신호 이어받기.
- D2. **1번 한 줄 소개 수정**: 현재 "경고등과 순간적인 이상 증상을 기록하고"는 앱이 경고등을 읽는 것처럼 읽힙니다. 새 문구는 "보이는 경고등을 찾아 이해하고, 순간 증상을 그때의 차량 상태와 함께 기록하며, 위급할 때 음성으로 안내하고 보호자에게 위치를 알리는 Pleos 차량 앱"의 뜻으로 짧게 씁니다.
- D3. **5번 개발 계획**: SDK별 사용 계획(Vehicle·AAOS 차속·FusedLocation·NaviHelper·TextToSpeech·SpeechToText), 권한, 에뮬레이터 검증 방법, 2차 PT 시연 흐름, 개발 일정(1차 결과 11/23 → 2차 PT 12/3), [향후] 목록을 넣습니다. "Pleos Connect"와 "에뮬레이터"를 명시합니다. 23개 API·23개 테이블 같은 내부 숫자는 빼거나 별첨으로 넘깁니다.
- D4. **6번 사업성**: 타깃 세그먼트, 가정을 밝힌 예상 MAU 산식, Retention 목표(가설), 경쟁·대체재와의 관계, 수익 모델 후보, 확대 단계를 넣습니다.
  - 확인한 공개 자료: 현대차 2025년 국내 판매 아반떼 7만 9,335대·그랜저 7만 1,775대(현대차그룹 뉴스룸 2026-01-05), Pleos Connect 2030년까지 약 2천만 대 적용 계획(뉴스룸 2026-04-30), 디 올 뉴 아반떼 전 트림 기본 적용(2026-08-05 보도).
  - 산식(모두 가정 표시): 국내 1단계 = 연간 신차 약 15만 대(아반떼+그랜저 판매 규모를 대리값으로 사용) × 설치율 5% × 월 활성 40% ≈ 3,000명. 확대 단계 = 2030년 약 2천만 대 × 설치율 1% × 월 활성 40% ≈ 8만 명(1단계와 같은 활성률 가정).
  - Retention 가설: D7 35%·D30 20%·M3 15%. 측정은 App Market 입점 뒤.
  - 경쟁 관계: 블루링크(에어백 전개 자동 통보·SOS, 에어백이 터지지 않는 사고·전원 차단 때 한계) 및 차량의 페달 오조작 안전 보조와는 대체가 아니라 보완. 119 신고를 대신하지 않음.
  - 수익 모델 후보(검토 방향, 계약 아님): 기본 무료 / 가족 보호자 플랜 / 정비소 제휴(정비 자료 전달) / 법인·렌터카 차량 관리.
- D5. **실명 노출 제거**: AGENTS.md:37, docs/HANDOFF.md:30, docs/analysis/brainstorm/2026-11-11/p01.md:6의 Windows 사용자 경로를 "사용자 로컬 분석 정책 파일(`~/.agents/analysis-policy.md`)"로 바꿉니다. Git 이력에는 남아 있으므로, 가산점용 GitHub 저장소는 새로 만들기를 권고(HANDOFF에 기록).
- D6. **별첨 정리**: 내부 작업 기록 문장(사용자 지시, SHA-256, AGENTS.md, Git, 폐기된 3개 제한 이력) 삭제. 제출 ZIP에 없는 파일을 "첨부"·"제공"이라고 쓰지 않음("설계 계약 파일은 요청 시 제공" 수준). 그림 중복(그림 14·15)과 같은 캡션 3개 수정. 목차·장 번호 정리. 링크 색 복원(build_v4.py에서 처리).

## 2. 안전·HMI (치명)
- D7. **주행 중 화면 원칙**(확인 근거: docs-design/Introduction/intro.txt:6-7): 주행 중에는 애니메이션·동영상을 쓰지 않습니다. 단계별 정지 그림 1장과 짧은 음성만 씁니다. 발·손 동작을 유도하는 그림(발 이동 사진·화살표)은 주행 중 화면에서 뺍니다.
  - 긴급 음성은 대상 차량 취급설명서의 비상 절차 문장만 그대로 읽습니다. 앱이 새 조작 순서를 만들지 않습니다.
  - Pleos 운전자 주의 분산 원칙과 긴급 안전 안내의 관계는 입점 전 Pleos 기술 문의로 확인할 항목으로 기록합니다.
  - 새 기능 **정차 중 비상 절차 미리 익히기**: 페달 그림·설명 애니메이션은 이 화면(정차 확인 시에만 열림)으로 옮깁니다. 브레이크 페달 신호(`Brake.registerBrakePedalStatus`, CAR_DRIVE normal)로 "브레이크를 밟아 보세요" 연습을 확인합니다.
- D8. **긴급 안내 시작 조건**: 안내는 운전자의 직접 보고(버튼 또는 음성)로만 시작합니다. 센서 조합(차속 증가·브레이크 눌림 등)은 "차가 멈추지 않나요?"라는 확인 질문만 띄웁니다. 브레이크 Boolean과 감속 미관측만으로 비상 제동(전자식 파킹 브레이크) 안내에 자동 진입하지 않습니다(C4-03 반영).
- D9. **주행 상태를 알 수 없으면 주행 중으로 간주**(PRD 6.2절 839행 오류 수정). `CarUxRestrictions`를 기준으로 하고, 매니페스트에 주행 중 사용(DO) 선언을 계획합니다.
- D10. **보호자 통지 적용 범위**(C5-03, C4-09 반영):
  - 10초 확인 뒤 자동 발송: 에어백 전개 신호, 사용자의 "충돌"·"사고" 선택.
  - 즉시 발송: 사용자가 "지금 도움 요청"을 누르고, 받는 사람 목록이 보이는 확인 화면에서 "보내기"를 누른 경우(받는 사람 표시 추가). 보호자가 없으면 119·블루링크 SOS 이용 방법만 안내.
  - 자동 발송하지 않음: 떨림·소음·냄새 등 수동 증상 선택. 기록과 "보호자에게 알리기" 선택지만 제공.
  - 에어백 전개 때도 10초를 둘지는 사용자 결정으로 남깁니다(현재 정책 유지, HANDOFF 질문 목록에 기록).
- D11. **오디오**: TTS `setAudioAttributes`로 안내 용도(내비 안내 또는 알람 용도, 시험으로 결정) 지정, 음성 앞 150~200ms 무음, Gleo 호출 시 즉시 중단, 시동 종료(Suspend) 때 오디오 포커스 반납. EMERGENCY 용도는 일반 앱이 쓸 수 없음.
- D12. 색 대비 4.5:1 미달 조합(#702132 위 #FF5B6B) 교체. 긴급 화면은 앱 창 안에서만 바꾸고 시스템 전체 화면 팝업을 쓰지 않음. 주행 중 메뉴 깊이 3단계 이하.

## 3. 화면 구조
- D13. **세로 한 창을 기본 배치로 추가**(확인 근거: app-structure-screen-model.txt:21, layout.txt:8, 앱 심사 레터박스 금지). 세로: 위쪽 탭, 가운데 차 그림(주행 중에는 작게 또는 숨김), 아래 고정 주 버튼 2개. 가로: 기존 승인 시안 구조(왼쪽 도크·가운데 차·오른쪽 맥락 패널). 두 배치의 기능은 같게(기능 패리티). 1280×720dp는 "가로 시안 예시"로 낮추고, 실제 창 크기는 에뮬레이터 FormFactorConsole로 확인.
- D14. 80dp 주요 버튼·24dp 간격은 자체 설계값으로 유지하되 "Pleos 기준"이라고 쓰지 않음. Pleos 공식 원문 수치(대비 4.5:1, 기본 글자 5mm·보조 3mm, 최소 12sp, 응답 0.25초, 3줄 이하, 버튼 반응 2초, 실행 10초)를 토큰·PRD에 근거와 함께 적음. 64dp·296×144dp·12열·P0~P9·C01~C08 잔재 정리(PRD 354·690·885·895·909행 등).
- D15. **경고등 찾기 흐름**: 계통 이름부터 고르지 않고 "색 → 모양 → 켜짐·깜박임" 3번 누르기 → 후보. 문구 검색과 말로 찾기(STT 결과 문자열로 후보 검색). 사진 찾기는 휴대폰 보조 앱 기능(정차 후)으로 옮기고, 인식 엔진은 [향후].
- D16. 시안 오류 수정: 보호자 화면의 "카카오톡 전달됨"과 "문자 대체 준비" 동시 표시 모순 → 보호자별 상태 한 줄씩. 긴급 화면 하단 같은 자리 버튼의 뜻이 화면마다 바뀌는 문제 → 긴급 흐름에서 같은 자리 = 같은 뜻. 오탐 때 "괜찮아요, 안내 끝내기" 경로 추가. "지금 도움 요청"의 받는 사람 표시.

## 4. 기술 계약
- D17. 위치: FusedLocation에 미터 단위 정확도가 없으므로 `accuracy_m`·"오차 반경 25m"를 지우고 `confidence`(원값, 의미 미공개)와 측정 시각을 저장·표시. 휴대폰 위치는 별도 출처로 표시.
- D18. 신호 신선도: Vehicle SDK 값에는 측정 시각이 없음(확인: BrakePedalStatusListener `onBrakePedalStatusUpdated(status: Boolean?)`). 변화 알림형 신호는 "마지막 수신 시각"과 "서비스 연결 상태"로 유효성을 판단하고, 3,000ms 신선도 규칙은 주기형 신호(차속 `PERF_VEHICLE_SPEED`)에만 적용.
- D19. 차속: Vehicle SDK 2.3.2에서 `VehicleSpeedListener` 삭제. Pleos FAQ(vehicle-app-faq.txt:17-21)가 안내하는 AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED`(권한 `android.car.permission.CAR_SPEED`)를 사용. 에뮬레이터 Virtual Console에 차속 카드가 없으므로 시연은 앱 안 "시험 입력 재생기"로 보여 주고 화면에 [시험 입력] 표시.
- D20. 오류 처리: 권한이 없으면 SDK가 `SecurityException`을 바로 던짐. 서비스 연결 전 호출 대기, 콜백은 메인 스레드가 아님 → 설계 반영.
- D21. 휴대폰 보조 앱은 별도 APK(Pleos SDK 미포함). 연결은 정차 중 QR 한 번 + 서버 중계. 차량 전원이 끊긴 뒤 휴대폰이 이어받는 것은 "마지막으로 받은 사건과 남은 확인 시간으로 보호자 발송을 이어감" 수준으로 한정하고, 통신이 없으면 문자 작성 화면을 미리 채워 여는 것은 [향후].
- D22. 상태·ID 통일: 알림 상태 enum 하나로 통일(C7 통일표), `POWER_LOSS` 중복 제거, `UNWANTED_ACCEL`의 상태 경로 수정, `guide_id`를 문자열 키(`G_…`)로 통일하고 안내 묶음 테이블 추가, 진동 의사코드 도달 불가 상태 정리(진동 기능 자체가 [향후]로 이동), 부위·증상·최소 등급 대응표 추가.
- D23. 개인정보·위치정보: 운전자 본인의 위치 제공 동의, 보낼 때마다 운전자에게 "누구에게·언제" 알림과 공유 중지, 제공 기록 보관, 위치기반서비스 사업 신고는 출시 전 요건으로 기록(법 조문 해석은 확인 필요로 표시). 공유 차량: "이번 운전자" 확인, 게스트 모드에서는 자동 위치 발송 끔.
- D24. 성능 예산(Pleos 심사: 주행 중 CPU 5% 미만, RAM 400MB 이하)을 rules-lab에 추가. 상시 음성 인식 금지(누를 때만 듣기).
- D25. 빠진 API 보강: 차량 등록, 사건 목록, 휴대폰 연결(QR), 보호자 번호 인증·동의, 정비 공유 링크, 위치 제공 기록, 정정·취소 알림.

## 5. 새 기능 (사용자 요청 "새 아이디어 추가") — 채택
모두 [시연] 또는 [설계] 등급이며 공개 SDK 근거가 있습니다.
- N1. **차가 알려 준 점검 항목**: 일반 권한(CAR_SAFETY normal 등)으로 읽을 수 있는 신호(엔진오일 수준, 브레이크액 수준, 워셔액 부족, 출력 저하 경고, 안전벨트 미체결, 도어 열림, 에어백 사용 가능 상태)가 바뀌면 관련 도감 항목을 후보 맨 앞에 보여 주고 사건으로 기록. 경고등 상태를 읽는 것이 아니라 "같은 원인의 공개 신호"라고 표현. Virtual Console로 일부 시연(도어·좌석 등), 나머지는 시험 입력.
- N2. **증상 재현 조건 카드**: "지금 기록" 순간의 공개 신호(차속, 브레이크, 조향각, 방향지시등, 도어, 주행거리, 연료·배터리 %, 위치)를 묶어 남기고, 같은 증상이 3번 이상 쌓이면 공통 조건(예: "시속 60~80km에서 조향 중")을 카드로 보여 줌. 원본 녹음·진동 분석을 대신하는 정비 설명 자료.
- N3. **말로 기록·말로 찾기**: 버튼을 누른 뒤 말한 내용을 SpeechToText 결과 문자열로 증상 메모와 경고등 검색에 사용(상시 듣기 없음, 외부 음성 인식 없음).
- N4. **사고 순간 차량 스냅샷과 탑승자 확인 카드**: 좌석별 에어백 전개, 좌석 점유, 안전벨트, 도어 열림, 위치·측정 시각을 묶어 보호자 요약에 첨부.
- N5. **가는 길에 정비소 들르기**: NaviHelper로 현재 목적지를 유지한 채 정비소를 경유지로 넣고(`requestRouteWithWaypoints` 계열), 후보별 도착 예상 시간을 비교(`requestTrip`). 정비소 목록은 앱 안 데이터(공개 서비스망 정보 정리), 제휴 API는 [향후].
- N6. **정비 노트와 재발 확인**: 정비 결과를 기록하고 주행거리(Odometer) 기준으로 "정비 뒤 500km 동안 같은 증상이 있었나요?"를 정차 중에 물음. 반복 이용 이유(Retention)의 핵심.
- N7. **출발 전 점검 카드**(정차 중): 시스템의 출발 전 안내(Pre-drive guide: 문·안전벨트·브레이크 등)와 겹치지 않게, 플레오스 가드는 N1 점검 항목·정비 일정·보호자 준비·휴대폰 연결 상태만 한 장으로 보여 줌.
- N8. **정차 중 비상 절차 미리 익히기**(D7).
- N9. **시연 모드**: 앱 안 시험 입력 재생기·모의 서버·모의 발송기. 화면에 [시험 입력] 표시. 2차 PT와 시연 영상용.
- N10. **위치 제공 알림과 공유 중지**(D23).
보류([향후] 또는 다음 라운드 판단): 살아 있는 내 차 상태 표시(시스템 Driving view의 3D 차량 상태와 겹침 → 사고·증상 화면에서만 관련 상태 표시로 축소), 주 1회 경고등 익히기 카드, 가족 차량 인계 노트, 보호자 주간 안부, 내비 주의 지점 연계, Fleet API·Vehicle Data API 연동.

## 6. 문서 품질
- D26. 용어 통일표(C8 A-1)를 적용: 기능 이름 4개+새 기능 이름 고정, "긴급 제동 가이드"→"감속 실패 대처 안내", "응급"→"긴급 도움", 위험 등급 이름 PRD와 일치(L1 기록 / L2 주의·확인 / L3 긴급 도움), 10초의 두 의미 분리("발송 전 확인 10초"와 "알림톡 전달 확인 기한").
- D27. 2번 기획 배경은 문제·대상·필요성 중심으로 재구성(해결책·화면·시험 계획은 3·5·6번으로 이동). 반복(9개 상황 목록, 이용 시험, 10초 흐름) 제거, 부정 표현 줄이기, 글자 수 맞추기용 문장 삭제.
- D28. 4번 시나리오는 사람·장면·동기 중심 이야기(예: 첫 차 초보, 혼자 운전하는 고령 부모와 자녀 보호자, 가족 공유 차)로 재작성.
- D29. 저장소 문서의 오래된 상태 표현(8쪽, 파일 미작성, PRD v2 호칭, 경고등 3개 제한, CAR_SAFETY 조건부 오류) 갱신.

## 7. 용어·ID 고정표 (모든 문서·계약·화면 공통)
### 기능 구성 (심사위원이 한눈에 보도록 4대 기능 + 2 연결 기능)
| ID | 이름(화면·문서) | 포함하는 하위 기능 |
|---|---|---|
| F1 | 경고등 찾기 | 색→모양→켜짐·깜박임 찾기, 문구·말로 찾기, 공식 설명(정차 후 상세), 차가 알려 준 점검 항목(N1), 출발 전 점검 카드(N7) |
| F2 | 증상 기록 | 지금 기록(버튼·말), 차량 상태 스냅샷 시간표, 증상 재현 조건 카드(N2), 정비 노트·재발 확인(N6) |
| F3 | 긴급 안내 | 원치 않는 가속 안내, 감속 실패 대처 안내, 상황 직접 선택, 비상 절차 미리 익히기(N8) |
| F4 | 보호자 알림 | 10초 발송 확인, 지금 도움 요청, 사고 순간 차량 스냅샷(N4), 위치 제공 알림·공유 중지(N10) |
| F5 | 정비소 연결 | 가는 길에 정비소 들르기(N5), 정비 자료 공유 링크 |
| F6 | 휴대폰 보조 앱 | 별도 APK, QR 연결, 사진으로 경고등 찾기(정차 후), 보호자 발송 이어받기(조건부) |
| F0 | 시연 모드 | 시험 입력 재생기, 모의 서버·발송기, [시험 입력] 표시 |

### 위험 등급
- L1 기록: 기록만, 발송 없음
- L2 확인 필요: 사용자가 선택하면 보호자 알림(자동 발송 없음). 단, "충돌·사고" 선택은 L3로 처리
- L3 긴급 도움: 10초 발송 확인 뒤 자동 발송(에어백 전개 신호, 충돌·사고 선택) / "지금 도움 요청"은 받는 사람 확인 뒤 즉시 발송

### 시간 용어
- "발송 전 확인 10초": 보호자 알림을 보내기 전 운전자가 취소·즉시 발송을 고르는 시간
- "알림톡 전달 확인 기한": 서버가 알림톡 전달 결과를 기다린 뒤 문자로 바꾸는 기한(별도 값, rules-lab 기준)
- "주기형 신호 신선도 3초": 차속처럼 주기적으로 오는 값이 3초(3,000ms) 넘게 끊기면 오래된 값

### 화면 이름 (세로·가로 공통)
홈, 경고등 찾기, 경고등 설명, 점검 항목, 출발 전 점검, 지금 기록, 재현 조건, 정비 노트, 상황 선택, 긴급 안내, 감속 실패 대처, 비상 절차 미리 익히기, 발송 확인(10초), 보호자 알림 상태, 정비소 들르기, 설정·동의, 휴대폰 연결, 시연 모드

### 신호 이름 (공개 경로만)
| 신호 | API (권한, 보호 수준) | 등급 |
|---|---|---|
| 차속 | AAOS `CarPropertyManager` `PERF_VEHICLE_SPEED` (`android.car.permission.CAR_SPEED`) — Pleos FAQ 안내 경로 | [시연: 시험 입력] |
| 브레이크 밟음 | `Brake.getCurrentBrakePedalStatus`/`registerBrakePedalStatus` (CAR_DRIVE, normal) | [시연: Virtual Console] |
| 조향각 | `SteeringWheel.getCurrentSteeringWheelAngle` (CAR_DRIVE, normal) | [시연] |
| 주행거리 | `Odometer.getCurrentDistanceDriven` (CAR_DRIVE, normal) | [시연] |
| 방향지시등 | `TurnSignal.registerTurnSignalState` (CAR_LIGHTS, normal) | [시연] |
| 도어 | `Door.getCurrentDoorPosition` 등 (CAR_DOORS) | [시연] |
| 좌석 점유·안전벨트 | `Seat.getCurrentSeatOccupancy` (CAR_SEATS, normal), `Safety.hasSeatBeltNotBuckledWarning` (CAR_SAFETY, normal) | [시연] |
| 에어백 전개 | `Safety.hasSeatAirbagBeenDeployed` (CAR_SAFETY, normal) | [시연: 시험 입력] |
| 엔진오일·브레이크액·워셔액·출력 저하 | `Safety.getEngineOilLevelState`, `getBrakeFluidLevelState`, `hasLowWasherFluidWarning`, `hasPowerDownWarningIndicator` (CAR_SAFETY, normal) | [시연: 시험 입력] |
| 연료·배터리 % | Fuel·EvBattery 도메인 | [시연] |
| 위치 | FusedLocation (`pleos.car.permission.FUSED_LOCATION`, dangerous), `confidence`만 있음 | [시연] |
| 음성 출력·입력 | TextToSpeech·SpeechToText 3.3.1 | [시연] |
| 경로·경유지 | NaviHelper 3.0.5 `requestRoute`, `requestRouteWithWaypoints`, `addWaypoint`, `requestTrip` | [시연] |
| 경고등 상태 | `Safety.getTelltalesInfo` 등 (`PRIVATE_CAR_SAFETY`, signature|privileged) | [향후] |
| 원본 녹음·카메라·블랙박스·IMU·충격·가속 페달·기어 | 공개 경로 없음 | [향후] |

## 8. 추가 채택 (C5 검토 뒤)
- N11. **보호자 연습 알림과 빠른 답**(F4): 보호자 등록 때 "[연습]" 알림을 한 번 보내 발신 이름과 모양을 익히게 함. 실제 알림 페이지에 [확인했어요][전화할게요] 버튼 → 차 화면과 짧은 음성으로 운전자에게 전달. 알림 첫 줄에 보호자의 할 일("먼저 전화해 보세요. 받지 않고 사고가 의심되면 119에 이 위치를 알려 주세요"). 에뮬레이터 시연은 모의 서버와 PC 브라우저.
- N12. **주 1회 경고등 익히기 카드**(F1): 정차 중 홈에 내 차 경고등 하나를 보여 주고 뜻과 "계속 가도 되나요?"를 고르게 함. 도감 항목 ID는 공개 열거형 `TelltaleType` 이름을 참조(상수 참조만, 상태 읽기 아님).
- 정비 공유: 정차 중 차 화면 요약 + QR로 24시간 읽기 전용 요약 링크(F5).
- 주행 중 상황 선택은 큰 카드 5개(떨림·냄새·타이어·사고·잘 모르겠음), 정차 후 9개 전체.
- 보류 유지: 가족 차량 인계 노트(공유 차량의 "이번 운전자" 확인과 함께 [설계] 수준으로 PRD에만 기록).

## 9. 계약 고정 사항 (PRD v4와 계약 v3가 똑같이 써야 하는 이름)
### 9.1 상태·열거형
- 위험 등급 `severity`: `L1_RECORD`, `L2_CHECK`, `L3_URGENT`
- 기능 등급 `availability`(기능·안내 묶음·신호 공통): `VERIFIED`(검토 완료), `DRAFT_DEMO`(시연용 초안), `UNAVAILABLE`(이 차에서 확인 불가), `FUTURE`(공개 SDK 밖, 향후)
- 입력 출처 `source`: `VEHICLE_SDK`, `AAOS_PROPERTY`, `FUSED_LOCATION`, `NAVI_HELPER`, `STT`, `USER_SELECTION`, `PHONE`, `TEST_INPUT`
- 신호 품질 `quality`: `OK`, `STALE`, `UNAVAILABLE`, `PERMISSION_DENIED`, `NOT_SUPPORTED`, `TEST_INPUT`
- 알림 정책 `policy_state`: `RECORD_ONLY`, `COUNTDOWN`, `RELEASED`, `DISPATCHING`, `SETTLED`(+`outcome`: `ALL_CONFIRMED`·`PARTIAL`·`NONE_CONFIRMED`), `CANCELLED`, `BLOCKED_NO_RECIPIENT`, `BLOCKED_NO_CONSENT`, `EXPIRED`
- 전달 `delivery_state`: `PENDING`, `ACCEPTED`, `SUBMISSION_UNKNOWN`, `DELIVERED`, `DELIVERY_UNCONFIRMED`, `FAILED`, `CANCELLED` (+`transport_group`: `KAKAO`·`TEXT`), 열람은 상태가 아니라 `opened_at`
- 운전자 행동 `action`: `SEND_NOW`, `CANCEL`(카운트다운 중 괜찮아요), `RESPOND_SAFE`(발송 뒤 괜찮아요 → 정정 알림), `SEND_CORRECTION`, `REQUEST_HELP`, `END_GUIDANCE`
- 긴급 안내 상태 `guidance_state`: `OBSERVING`, `CONFIRM_PROMPT`, `UNWANTED_ACCEL_GUIDE`, `DECEL_FAILURE_GUIDE`, `MANUAL_SELECTION`, `HELP_CONFIRM`, `STOPPED_REVIEW`, `ENDED`
- 수동 증상 `symptom`(9개, 주행 중 큰 카드 5개 표시): `VIBRATION`(떨림), `PULLING`(쏠림), `NOISE`(소음), `BURNING_SMELL`(냄새), `PEDAL_PROBLEM`(페달 이상), `TIRE_DAMAGE`(타이어), `COLLISION`(사고), `POWER_LOSS`(출력 저하), `UNKNOWN`(잘 모르겠음). 주행 중 5개 = VIBRATION·BURNING_SMELL·TIRE_DAMAGE·COLLISION·UNKNOWN. `POWER_LOSS`는 한 곳만.
- 부위 `body_area`: `FRONT`, `FRONT_LEFT_TIRE`, `FRONT_RIGHT_TIRE`, `REAR_LEFT_TIRE`, `REAR_RIGHT_TIRE`, `CABIN`, `REAR`, `UNDERBODY`, `UNKNOWN`
- 안내 묶음 ID `guide_id`: 문자열 `G_[A-Z_]+` (예: `G_UNWANTED_ACCEL`, `G_DECEL_FAILURE`, `G_STOP_SAFE`), `version` 정수, `step_id` 문자열. 테이블 `guide_bundles(guide_id, version, vehicle_profile_id, availability, source_manual_ref, steps_json, tts_lines_json)`.
### 9.2 위치 필드
- `latitude`, `longitude`, `measured_at`(SDK가 준 측정 시각), `received_at`, `location_confidence`(원값 그대로, 의미 미공개), `location_source`(`VEHICLE`·`PHONE`). `accuracy_m` 삭제.
### 9.3 차량 스냅샷 필드 `vehicle_snapshot`
- `speed_mps`(AAOS), `brake_pressed`(true/false/null), `steering_angle_deg`, `turn_signal`, `doors_open[]`, `seat_occupied[]`, `seatbelt_unbuckled[]`, `airbag_deployed[]`(좌석 영역별), `odometer_km`, `fuel_or_soc_pct`, `maintenance_flags`(engine_oil, brake_fluid, washer_fluid_low, power_down_warning), 각 값에 `quality`와 `received_at`.
### 9.4 API 경로 (기존 23개에 더하거나 정리)
- 유지·정리: 사건 생성·조회, 증거 업로드(분할), 알림 정책·행동, 전달 상태, 보호자 공유 열람.
- 추가: `POST /v1/vehicles`(차량 등록·VIN 해시), `GET /v1/incidents`(목록), `POST /v1/trips`(출발 준비·두 기기 공유), `POST /v1/pairing/qr`·`POST /v1/pairing/confirm`(휴대폰 연결), `POST /v1/guardians`·`POST /v1/guardians/{id}/verify`·`POST /v1/guardians/{id}/practice-alert`(보호자 인증·연습 알림), `POST /v1/consents`·`GET /v1/consents`(운전자 위치 제공 동의 등), `GET /v1/location-disclosures`(위치 제공 기록), `POST /v1/incidents/{id}/share-links`(정비사 24시간 요약), `POST /v1/incidents/{id}/corrections`(정정 알림), `POST /v1/guardian-replies`(보호자 빠른 답), `GET /v1/guide-bundles/{guide_id}`, `POST /v1/maintenance-notes`(정비 노트), `GET /v1/symptom-patterns`(재현 조건 카드).
### 9.5 수치
- 주요 버튼 80dp(자체), 간격 24dp(자체), 세로 기준 시안 840×1212px, 가로 시안 1280×720dp(예시), 최소 글자 12sp(Pleos)·앱 기준 본문 22sp 이상(자체), 대비 4.5:1(Pleos), 입력 응답 0.25초·버튼 2초·실행 10초(Pleos 심사), 주행 중 텍스트 3줄 이하(Pleos), 주행 중 CPU 5% 미만·RAM 400MB 이하(Pleos 심사), 주기형 신호 신선도 3,000ms, 발송 전 확인 10초, 알림톡 전달 확인 기한은 rules-lab 값(기존 값 유지), 재현 조건 카드 최소 3회, 정비 후 재발 확인 500km, 정비 공유 링크 24시간, TTS 앞 무음 150~200ms.

## 10. 정리 목록(INDEX) 대조 뒤 추가 결정
- T47 제출 절차: HANDOFF에 제출 체크리스트(메일 주소·제목 형식, 수정 제출 규칙, 서명·날짜 직접 작성, DOCX 또는 PDF, 개인 참가 표시, 첨부 실명 확인)를 둡니다.
- T49 번호판: 공식 보도 사진(avante-exterior.jpg)과 사용자 승인 진입 화면에 보도용 차량 번호판이 보입니다. 공식 공개 자료이고 승인 자산이라 원본은 바꾸지 않고, v4 새 화면에는 번호판이 보이지 않게 합니다. HANDOFF에 기록합니다.
- T50 출처·라이선스: 별첨 부록 출처에 진입 화면이 생성 이미지를 바탕으로 한 시안이라는 점, 공식 사진 출처, 글꼴(Pretendard OFL, Paperlogy)과 아이콘(Lucide) 출처를 적습니다. Paperlogy 라이선스 파일이 저장소에 없다는 점을 HANDOFF 할 일로 기록합니다.
- T16 가산점: 이번 작업은 문서 수정 범위입니다. 시연 영상(목업·화면 녹화 인정)과 새 GitHub 저장소(실명 없는 새 이력)를 권고하고, README 필수 항목 템플릿을 `docs/submission/github-readme-template.md`로 제공합니다.
- INDEX 4절의 사용자 결정 질문은 HANDOFF "사용자 결정 필요" 표로 옮깁니다.
