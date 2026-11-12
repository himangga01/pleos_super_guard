# Pleos 앱 가이드 중 플레오스 가드 설계에 영향을 주는 기준

- 작성일: **2026-11-12, Asia/Seoul**.
- 목적: Pleos 공식 기획·개발·디자인·심사 가이드와 에뮬레이터 안내에서 플레오스 가드의 화면·주행 중 동작·음성·성능·개인정보·시연 방법을 정하는 원문 기준을 모읍니다. 그리고 현재 설계가 각 기준에 어떻게 대응하는지 정리합니다.
- 읽은 방법: 공개 Pleos 개발 문서를 텍스트로 받아 끝까지 읽었습니다. 이 문서의 「 」 안 문장은 모두 원문 텍스트와 글자 단위로 다시 대조했습니다(공백·줄바꿈만 정규화). 줄임표(…)는 쓰지 않았습니다.
- 표기: 「 」는 원문 그대로입니다. **[해석]**은 작성자의 판단입니다. 원문에 없는 수치는 원문 수치처럼 쓰지 않습니다.
- 한계: 이미지·Figma로만 제공되는 수치(버튼 dp, 그리드, 색 HEX, 창 크기, 오디오 매트릭스 칸 값)는 텍스트에 없어 확인하지 못했습니다. 에뮬레이터·앱을 실행하지 않았습니다. 이 문서는 공모전 심사나 App Market 입점 통과를 보장하지 않습니다.
- 관련 문서: [종합 SDK 리뷰](pleos-sdk-review.md), [Vehicle API 전체 목록](../sdk/vehicle-api-catalog.md), [현재 통합 PRD](integrated-vehicle-care-2026-11-12.md), [이번 확인 기록](../sdk/evidence/sdk-followup-2026-11-12.json).

## 1. 원문 목록

아래 ID로 인용 위치를 표시합니다. 경로는 공개 문서 주소의 경로와 같습니다.

| ID | 문서 | 공개 주소 |
| --- | --- | --- |
| G1 | 앱 구조 및 동작 모델 | [docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model) |
| G2 | Display Layout | [docs/connect/guide/docs-design/getting-started/layout](https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout) |
| G3 | 앱 리뷰: 기능·성능·안정성 | [docs/pleos-only/distribute/app-review-guide/review-functionality-performance-stability](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-functionality-performance-stability) |
| G4 | 지역별 주행 규제 정책 | [docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy) |
| G5 | 주행 규제 개발 가이드 | [docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide) |
| G6 | 용어집 | [docs/connect/guide/glossary](https://document.pleos.ai/docs/connect/guide/glossary) |
| G7 | Pleos Connect UI Design Guide | [docs/connect/guide/docs-design/Introduction/intro](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro) |
| G8 | 주행 안전을 고려한 설계 | [docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline) |
| G9 | 타이포그래피 | [docs/connect/guide/docs-design/foundations/typography](https://document.pleos.ai/docs/connect/guide/docs-design/foundations/typography) |
| G10 | Maps/Navigation 앱 개발 가이드 | [docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/navigation-app-development-guide) |
| G11 | 오디오 포커스 개발 가이드 | [docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development) |
| G12 | TTS·음성 안내 선행 묵음 적용 가이드 | [docs/pleos-only/vehicle-app-developer-guide/voice-guidance-preroll-silence-guide](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/voice-guidance-preroll-silence-guide) |
| G13 | Suspend 모드 오디오 포커스 | [docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/suspend-mode-audio-focus](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/suspend-mode-audio-focus) |
| G14 | 성능 및 리소스 효율 | [docs/pleos-only/vehicle-app-planning-guide/performance-resource-mgmt](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/performance-resource-mgmt) |
| G15 | 앱 리뷰: 사용자 개인정보 및 보안 | [docs/pleos-only/distribute/app-review-guide/review-user-privacy-security](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-user-privacy-security) |
| G16 | 앱 리뷰: 등록 정보 | [docs/pleos-only/distribute/app-review-guide/review-app-registration-info](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-app-registration-info) |
| G17 | 보안·개인정보 설계 | [docs/pleos-only/vehicle-app-planning-guide/security-privacy](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/security-privacy) |
| G18 | 에뮬레이터 정식 릴리즈 | [docs/pleos-only/release-note/emulator-release-note](https://document.pleos.ai/docs/pleos-only/release-note/emulator-release-note) |
| G19 | Virtual Console 셋업 가이드 | [docs/connect/guide/getting-started/application-development/use-vehicle-emulator](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator) |
| G20 | Virtual Console 사용 가이드 | [docs/pleos-only/app-test-guide/virtual-console-guide](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide) |
| G21 | 폼 팩터 테스트 가이드 | [docs/pleos-only/app-test-guide/form-factor-test-guide](https://document.pleos.ai/docs/pleos-only/app-test-guide/form-factor-test-guide) |
| G22 | adb 명령 가이드 | [docs/pleos-only/vehicle-app-developer-guide/adb-command-reference-guide](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/adb-command-reference-guide) |
| G23 | 에뮬레이터 설정 | [docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator) |
| G24 | 에뮬레이터 설치 및 실행 | [docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator) |
| G25 | 앱 카테고리 | [docs/pleos-only/vehicle-app-planning-guide/app-category](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-category) |
| G26 | 차량 앱 FAQ | [docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq) |
| G27 | 하드웨어 접근 정책 | [docs/pleos-only/vehicle-app-planning-guide/platform-api-architecture](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/platform-api-architecture) |
| G28 | Vehicle SDK Sample App 사용 가이드 | [docs/pleos-only/tutorial/vehicle-sdk-sample-app](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app) |

## 2. 한눈에 보는 결론

1. **세로 한 창이 기본입니다.** 따로 정하지 않은 앱은 세로(portrait)로 한 창에서 실행됩니다. 모든 앱은 가로·세로 두 배치를 갖춰야 하고, 두 배치의 기능은 같아야 합니다. 그래서 플레오스 가드는 세로 한 창 배치를 기본으로 추가합니다.
2. **주행 중에 쓰려면 DO 선언이 필요합니다.** 선언하지 않은 앱은 주행 중에 쓸 수 없습니다. 선언한 앱도 `CarUxRestrictions` 값에 따라 기능을 줄여야 합니다. 긴 설명 글(「매뉴얼 (대용량 텍스트)」)은 세 지역 표 모두에서 주행 중 제한입니다.
3. **주행 중에는 애니메이션·동영상을 쓰지 않습니다.** 운전자의 손이나 발을 움직이는 동작을 유도해도 안 됩니다. 그래서 주행 중 긴급 안내는 정지 그림 1장과 짧은 음성으로 바꾸고, 페달 그림과 설명 애니메이션은 정차 중 연습 화면으로 옮깁니다.
4. **눈으로 확인하는 기준이 숫자로 있습니다.** 대비 4.5:1 이상, 중요 글자 높이 5mm 이상·보조 3mm 이상, 최소 12sp, 입력 응답 0.25초 이하, 한 문단 3줄 이하, 정보 파악 1~2초입니다.
5. **버튼 dp 수치는 원문 텍스트에 없습니다.** 원문은 「Pleos Connect UI Design Guide의 기준」을 따르라고만 합니다. 플레오스 가드의 주요 버튼 80dp와 간격 24dp는 자체 설계값이며 Pleos 공식 수치가 아닙니다.
6. **음성은 오디오 포커스 정책을 따릅니다.** 재생 전 포커스를 요청하고, 포커스 변화 처리를 반드시 구현합니다. Gleo가 불리면 바로 멈춥니다. 모든 음성 앞에 150~200ms 무음을 두고, 시동이 꺼진 Suspend 상태에서는 포커스를 반납합니다.
7. **성능 예산이 작습니다.** 내비게이션이 아닌 앱은 주행 중 CPU 5% 미만, RAM 400MB 이하입니다. 상시 음성 인식이나 계속 도는 분석은 이 예산 안에서 측정해야 합니다.
8. **개인정보·위치는 명시적 동의가 필요합니다.** 동의 버튼을 누르는 것 외의 행동을 동의로 보면 안 됩니다. 동의를 거두면 바로 사용을 멈추고 저장 자료를 지웁니다. 위치를 쓰면 위치정보 이용약관을 함께 내야 합니다.
9. **에뮬레이터 2.1.0(2026-10-19)에서 Virtual Console과 폼 팩터 시험을 쓸 수 있습니다.** 브레이크 페달·도어·좌석 탑승·방향지시등·주행거리·조향각 등은 카드로 바꿀 수 있습니다. 사용 가이드 카드 표에는 차량 속도·에어백 카드가 없습니다. 주행 제한은 adb 명령으로 켜고 끕니다.
10. **Vehicle SDK 권한은 실제 입점 때 사전 협의가 필요합니다.** 공모전 2차 PT는 에뮬레이터 시연이며 입점 심사가 아닙니다. 그래도 입점 단계의 권한 승인 조건을 기획서에 위험으로 적어 둡니다.

## 3. 화면 모델: 세로·가로

- 「Pleos Connect는 Driving view와 앱 영역을 함께 표시하고, 앱 영역에 최대 2개의 앱을 배치할 수 있는 차량용 멀티 윈도우 환경을 제공합니다.」 (G1)
- 「Driving view는 운전자와 가까운 고정 영역에 표시되며, 앱이 임의로 가리거나 종료할 수 없습니다.」 (G1)
- 「portrait: 앱이 앱 영역 중 하나의 window를 사용하는 화면 형태입니다.」 (G1)
- 「screenOrientation을 별도로 지정하지 않으면 Activity는 기본적으로 portrait로 실행되며 하나의 window를 사용합니다.」 (G1)
- 「Portrait에서는 핵심 기능과 주요 정보를 제공하고, landscape에서는 확장된 영역을 활용해 부가 정보와 확장 기능을 제공하도록 구분해 설계합니다.」 (G1)
- 「Window 크기가 변경되어도 핵심 기능을 사용할 수 있도록 화면을 유연하게 구성해야 합니다.」 (G1)
- 「가로 전용 게임 등 한 가지 모드로만 실행 가능한 애플리케이션을 제외한 모든 앱은 Landscape, Portrait 두 가지 레이아웃에 적절하게 조절되도록 설계해야 합니다.」 (G2)
- 「앱이 레터박스 처리 없이 최대한 디스플레이 영역을 채워야 합니다.」 (G3)
- 「앱이 호환성 모드로 실행되어서는 안 됩니다.」 (G3)
- 「어떠한 화면 상태 또는 화면 전환간에도 모두 동일한 기능과 작업을 노출하여 기능 패리티를 보존해야 합니다.」 (G3)
- 「백그라운드 전환 시 화면 갱신, 애니메이션, 불필요한 센서 수집 등 즉시 중단할 수 있는 작업을 정리합니다.」 (G1)
- 「앱이 종료되거나 프로세스가 재생성되는 경우에도 사용자 데이터와 진행 상태를 복원할 수 있도록 상태 저장 전략을 마련합니다.」 (G1)

**[해석]** G1은 세로에서 핵심, 가로에서 확장 기능을 두라고 하고, G3는 기능 패리티를 요구합니다. 두 문장을 함께 지키려면 세로에서도 긴급 안내·기록 시작·보호자 알림·경고등 찾기를 모두 쓸 수 있어야 하고, 가로는 같은 기능을 더 넓게 보여 주는 배치여야 합니다.

## 4. 주행 제한과 Distraction Optimization(DO)

- 「Pleos Connect의 주행 규제는 차량의 속도·기어·브레이크 등 하드웨어 신호와 지역별 안전 규정에 따라 적용됩니다.」 (G4)
- 「플랫폼 버전과 양산 차량에 따라 주행 규제 정책이 다를 수 있습니다.」 (G4)
- 「앱에서 Distraction Optimization(DO)를 선언하지 않았을 때, 주행 상황에 따라 앱 사용 가능 여부가 달라집니다.」 표의 값은 「Parking(주차) | 사용 가능 | Drive(주행) | 사용 불가」입니다. (G4)
- 기능별 제한표의 마지막 열은 「매뉴얼 (대용량 텍스트)」이고 플래그는 「UX_RESTRICTIONS_LIMIT_CONTENT(32)」, 「UX_RESTRICTIONS_LIMIT_STRING_LENGTH(4)」입니다. 북미·유럽·그외 지역 세 표의 Driving 행에서 이 열은 모두 ⛔입니다. (G4)
- 같은 표에서 「QR 로그인」 열(플래그 `NO_SETUP(64)`)과 동영상 시청 열(`NO_VIDEO(16)`)도 세 지역 모두 Driving ⛔입니다. (G4)
- 「앱은 AAOS CarUxRestrictionsManager를 통해 CarUxRestrictions 값을 얻어 주행 규제 상태를 확인하고, 사양에 맞게 동작하도록 구현합니다.」 (G5)
- 「DO를 선언한 앱도 주행 규제가 활성화되면 CarUxRestrictions와 ActiveRestrictions를 확인하여 기능별 UX 제한을 직접 적용해야 합니다.」 (G5)
- 「플랫폼이 지역별 규제 사양에 맞는 ActiveRestrictions를 내려주므로, 앱은 notify로 수신한 CarUxRestrictions만 반영하면 됩니다.」 (G5)
- 용어집의 주행 상태는 「parked(기어 PARK), idling(비주차·속도 0), moving(비주차·속도 非0), unknown(판단 불가) 네 가지로 구분됩니다.」 (G6)
- 앱 카테고리 중 Mobility Services의 제한적 허용 예시에 「주행과 밀접한 관련이 있는 차량 관리 서비스」가 있습니다. (G25)

**[해석]** 주행 상태를 알 수 없으면(unknown) 앱은 주행 중으로 보고 기능을 줄여야 안전합니다. 한국이 표의 「그외 지역」에 속하는지는 원문에 없습니다. 앱이 지역을 스스로 판단하지 않고 플랫폼이 주는 `ActiveRestrictions`를 따르는 것이 원문 권고와 맞습니다.

## 5. 애니메이션과 손·발 동작 유도 금지

- 「운전 중 동영상, 애니메이션 이미지, 텍스트 자동 스크롤과 같이 운전자의 시선을 유도하면 안 됩니다.」 (G7)
- 「앱 조작은 한 손만으로도 가능해야 하며, 양손을 모두 사용하게 해서는 안 됩니다.」 (G7)
- 「운전자의 손이나 발을 움직이는 동작을 유도해서는 안 됩니다.」 (G7)
- 「주행 중에는 운전자의 시선을 과도하게 끄는 동영상, 애니메이션 및 움직이는 이미지를 최소화하거나 제한합니다.」 (G8)
- 「이미지는 정보 전달에 필요한 범위에서 최소한으로 사용하고, 장식적이거나 반복적으로 움직이는 시각 요소는 지양합니다.」 (G8)
- 「주행 중에는 시선을 과도하게 끄는 자동 애니메이션이나 반복적인 화면 전환을 최소화합니다.」 (G8)
- 「자주 쓰는 기능은 첫 화면·상단에 두고, 드롭다운·다단 메뉴는 남용하지 않습니다.」 (G8)
- 내비 앱 가이드의 UX 심사 항목에 「주행 중 전체 화면 팝업 금지」가 있습니다. (G10)

**[해석]** 긴급 상황에서 브레이크를 밟도록 안내하는 것도 이 조항과 충돌할 수 있습니다. 그래서 주행 중에는 대상 차량 취급설명서의 비상 절차 문장만 그대로 읽고, 앱이 새 조작 순서를 만들지 않습니다. 이 해석이 심사 기준에 맞는지는 입점 전 Pleos 기술 문의로 확인합니다.

## 6. 대비·글자 크기·응답 시간·글 길이

- 「아이콘, 텍스트 및 기타 이미지의 대비율이 4.5:1 이상이어야 합니다.」 (G7)
- 「중요 정보(예: 속도, 내비게이션 안내, 경고 메시지 등)를 표시하는 기본 텍스트는 높이 5 mm 이상, 보조 텍스트는 최소 3 mm 이상으로 설정하여 주행 중 빠르게 인식할 수 있도록 합니다.」 (G7)
- 「문장 또는 단락은 텍스트 3줄을 넘지 않아야 합니다.」 (G7)
- 「한 줄의 텍스트는 80자(영문 기준) 이하여야 합니다.」 (G7)
- 「운전자는 짧은 시간(1~2초) 내에 정보를 파악해야 하므로, 핵심 정보만 노출하고 불필요한 요소는 배제합니다.」 (G7)
- 「사용자 입력 후 시스템 응답 시간이 0.25초를 초과하지 않아야 합니다.」 (G7)
- 「야간/주간에 따른 조명 환경에 따라 UI 밝기와 대비를 자동 조정하여 가독성을 유지합니다.」 (G7)
- 「최소 폰트 크기는 12sp 이상을 권장합니다.」, 「실제 픽셀 기준 최소 높이: 18px 이상」 (G9)
- 「버튼 크기와 터치 영역 간 간격은 Pleos Connect UI Design Guide의 기준을 따릅니다.」 (G8) 이 디자인 가이드 텍스트에는 dp 수치가 없습니다.
- 「입력 후 차량 시스템의 응답 시간이 0.25초를 넘지 않게 하세요.」, 「앱은 10초 이내에 앱이 실행되어야 합니다.」, 「앱 내 특정 버튼은 2초 이내에 반응해야 합니다.」 (G3)
- 「사용자의 동작 이후 2초 이내에 버튼 동작 또는 콘텐츠를 로드하지 못하는 경우, UI를 통해 앱이 동작 중임을 알려야 합니다.」 (G3)

**[해석]** 한국어 글 길이 기준은 원문에 없습니다. 영문 80자와 3줄 기준을 참고해 긴급 문구는 한 문장, 두 줄 이하를 내부 규칙으로 둡니다. 실제 글자 높이(mm)는 창의 DPI를 FormFactorConsole에서 확인한 뒤 계산합니다.

## 7. 오디오: 포커스·선행 무음·Gleo·Suspend

- 「각 오디오 스트림은 재생 시작 전 AudioAttributes + AudioFocusRequest로 포커스를 요청해야 합니다.」 (G11)
- 「포커스 상실/획득 이벤트를 반드시 처리해야 합니다.」 (G11)
- 「Gleo가 호출되어 앱이 AUDIOFOCUS_LOSS_TRANSIENT를 수신하면, 즉시 재생을 중단하고 필요 시 오디오 포커스를 반납(abandon)해야 합니다.」 (G11)
- 오디오 용도와 컨텍스트 대응표에서 MEDIA 용도는 MUSIC 컨텍스트, ASSISTANCE_NAVIGATION_GUIDANCE 용도는 NAVIGATION 컨텍스트에 묶입니다(「MUSIC | MUSIC, UNKNOWN, GAME, MEDIA」, 「NAVIGATION | NAVIGATION, ASSISTANCE_NAVIGATION_GUIDANCE」). (G11)
- 「모든 음성 안내 재생 시작 전 선행 묵음 150 ms ~ 200 ms 적용을 권장합니다.」 (G12)
- 「음성 안내는 묵음 없이 즉시 재생하지 않습니다.」 (G12)
- 「스트림 재생이 끊기거나 새 세션으로 시작되는 경우에는 다시 적용합니다.」 (G12)
- 「Suspend 모드 진입 시 모든 일반 오디오 세션은 사용자에게 출력되지 않아야 하며 각 앱은 보유 중인 오디오 포커스를 반드시 반납해야 합니다.」 (G13)
- 「이 과정에서 새로운 오디오 재생 또는 오디오 포커스 요청은 허용되지 않습니다.」 (G13)
- 예외는 「다음 기능은 시스템 정책에 따라 제한적으로 예외가 허용될 수 있습니다.」 아래의 통화·긴급 통신, 「안전 관련 경고음」 등 시스템 기능입니다. (G13)

**[해석]** TextToSpeech SDK 3.3.1의 기본 오디오 용도는 `USAGE_MEDIA`(MUSIC 컨텍스트)입니다. 그래서 `setAudioAttributes`로 안내 용도(내비 안내 또는 알람)를 지정하고 시험으로 고릅니다. EMERGENCY·SAFETY 컨텍스트를 일반 앱이 쓸 수 있다는 문장은 원문에 없습니다. 시동이 꺼진 뒤에도 앱이 음성 안내를 계속한다고 기획서에 쓰지 않습니다.

## 8. 성능

- 「Cold start 시 스플래시에서 오래 묶이지 않도록 필요한 최소한만 초기화합니다.」 (G14)
- CPU 기준은 「내비게이션 앱(주행 중): 15% 이하」, 「내비게이션 앱이 아닌 앱(주행 중): 5% 미만」, 「모든 앱(정차 중): 25% 이하」입니다. (G14)
- 「RAM 사용량은 400 MB 이하로 유지합니다.」 (G14)
- 「백그라운드에서의 긴 작업, 불필요한 타이머·폴링은 최소화합니다.」 (G14)
- 「포커스 획득·상실 등 포커스 변화에 대응하는 로직을 반드시 구현합니다.」 (G14)
- 「앱 내에는 테스트를 위한 더미 데이터나 임시 파일이 존재하면 안 됩니다.」 (G3)

**[해석]** 플레오스 가드의 시연 모드(시험 입력 재생기·모의 서버)는 공모전 시연용입니다. 실제 입점용 빌드에서는 빼거나 꺼야 G3의 더미 데이터 조항과 충돌하지 않습니다.

## 9. 개인정보·위치 동의

- 「이용자에게 법적 고지사항(수집하는 개인정보의 항목과 처리목적, 보유기간, 동의를 거부할 수 있는 권리)을 알리고 명시적인 동의 절차를 구현하여야 합니다.」 (G15)
- 「제시된 동의 버튼을 누르는 행동 외의 행동을 동의로 간주해서는 안 됩니다.」 (G15)
- 「사용자가 개인정보 제공을 취소한 경우 즉시 앱 내에서 활용 중인 경로를 끊어야하며, 저장된 사용자의 데이터를 삭제해야 합니다.」 (G15)
- 「위치정보를 저장 및 이용하는 서비스의 경우 서비스 국가의 법률 및 규제에 따라 등록/신고 내용의 확인을 요청할 수 있으며, 확인되지 않거나 거짓으로 증명한 경우 상품의 판매가 중단될 수 있습니다.」 (G15)
- 「앱에서 위치정보 서비스를 사용하는 경우, ‘위치정보 이용약관 및 기타 정보 처리방침’을 함께 제출해야 합니다.」 (G16)
- 「앱에서 사용자의 위치, 음성, 계정 정보 등을 요구할 때 “왜 필요한지” 명확히 해야 하며, 동의 플로우를 간단하고 이해하기 쉽게 설계해야 합니다.」 (G17)
- 「제한될 수 있는 기능: Telephony(전화), SMS, 일부 센서(자이로, 근접 등), 캘린더, 주소록 등」 (G27)
- 내비 앱 가이드의 권한 항목에는 「마이크: 음성 입력 시에만 허용」이 있습니다. (G10)

**[해석]** 차량 앱에서 문자 발송이나 전화 걸기를 기대할 수 없으므로 보호자 알림은 서버가 보냅니다. 보호자에게 위치를 보낼 때마다 운전자에게 알리고 공유를 멈출 수 있게 하는 설계는 G15의 철회 조항과 맞습니다. 위치기반서비스 사업 신고 등 국내 법 적용은 출시 전 확인할 요건으로 둡니다.

## 10. 에뮬레이터 2.1.0: Virtual Console·FormFactorConsole·주행 제한 시험

- 「Release version: Pleos Connect Emulator v2.1.0」, 「Release date: 2026년 10월 19일」 (G18)
- 새 기능은 「Pleos Connect Virtual Console 제공」, 「화면 크기/해상도/레이아웃 변화 검증을 위한 폼팩터 테스트용 에뮬레이터 기능 추가」이고, 제거 항목에 「LLM SDK 제공 종료」가 있습니다. (G18)
- 「Pleos Connect Virtual Console를 지원하는 Pleos Connect Emulator 최소 버전은 v2.1.0입니다.」 접속은 「adb forward tcp:8088 tcp:8088」 후 「http://127.0.0.1:8088/」입니다. (G19)
- 「quick boot로 설정할 경우에는 VHAL을 이용할 수 없습니다.」 (G23)
- 「minSDK 버전은 Android API 28 이상이어야 합니다.」 (G24)
- Motion 카드: 「브레이크 페달 위치를 0~100% 범위에서 설정하고, 해당 값에서 파생되는 페달 작동 상태를 재현합니다.」, 「누적 주행 거리를 0~1,000,000km 범위에서 1km 단위로 설정합니다.」, 「스티어링 휠 각도를 -540~540도 범위에서 1도 단위로 설정합니다.」 (G20)
- Convenience 카드: 「방향지시등을 Off, 좌·우 Blink, 좌·우 Flash 중 하나로 설정합니다.」, 「운전석, 동승석 또는 뒷좌석 좌우를 선택하여 탑승 상태를 Empty 또는 Occupied로 설정합니다.」, 「비상등 스위치와 실제 상태를 함께 Off 또는 On으로 설정합니다.」 (G20)
- Safety 카드: 「주차 브레이크, 안전벨트, ABS, TPMS 등 지원되는 경고등을 선택하여 Off, On, Blink 상태로 설정합니다.」 (G20)
- 셋업 가이드 소개에는 「차량 속도, 도어, 조명 등 차량 상태 변경」이 있으나 사용 가이드의 카드 표에는 차량 속도 카드가 없습니다. 에어백 카드도 없습니다. (G19, G20)
- 폼 팩터 시험: 「Orientation에서 Portrait 또는 Landscape를 선택합니다.」, 「테스트 화면 크기에 표시된 dp, DPI, px 값을 확인합니다.」, 확인 항목 「가로·세로 방향에 맞게 레이아웃이 배치되는지」 (G21)
- 「가상 디스플레이를 제거하면 해당 디스플레이에서 실행한 앱이 강제 종료됩니다.」 (G21)
- 주행 제한: 「adb shell cmd hmg_car_service --service drivingstate driving_regulation on」, 해제 「adb shell cmd hmg_car_service --service drivingstate driving_regulation off」, 「모든 차종에서 공통으로 사용할 수 있는 명령어입니다.」 (G22)
- 차속: 「클러스터와 동일한 속도를 쓰려면 CarPropertyManager로 PERF_VEHICLE_SPEED를 읽습니다.」 (G26)
- Vehicle 권한: 「이 권한은 일반적인 Android 위험 권한처럼 실행 중 승인 창을 띄우는 방식이 아닙니다.」, 「PRIVATE_* permission은 예제에 포함하지 않습니다.」 (G28)

**[해석]** Virtual Console의 경고등 카드로 경고등을 켤 수는 있습니다. 하지만 Vehicle SDK 2.3.2의 경고등 API 4개는 모두 `PRIVATE_CAR_SAFETY`(signature|privileged)를 요구하므로 일반 앱인 플레오스 가드는 그 값을 읽을 수 없습니다. 차속·에어백·연료량·오일 수준처럼 카드가 없는 신호는 앱 안 시험 입력 재생기로 보여 주고 화면에 [시험 입력]이라고 표시합니다. 카드 이름과 SDK 속성의 대응은 [Vehicle API 전체 목록](../sdk/vehicle-api-catalog.md)에 적었고, 실제 주입 결과는 아직 시험하지 않았습니다.

## 11. 현재 설계와의 대응

"현재 설계"는 2026-11-12 1차 비판 반영 결정 기준입니다. PRD·계약·화면 시안에 반영하는 작업은 별도로 진행하며, 이 표는 반영 완료를 뜻하지 않습니다.

| 원문 기준 | 근거 | 이전 설계(PRD v3·시안) | 현재 설계 결정 | 상태 |
| --- | --- | --- | --- | --- |
| 세로 한 창 기본, 두 배치, 레터박스 금지, 기능 패리티 | G1, G2, G3 | 1280×720dp 가로 시안 하나 | 세로 한 창 배치 추가(위 탭·가운데 작은 차 그림·아래 고정 주 버튼 2개). 가로는 왼쪽 도크·가운데 차·오른쪽 패널. 두 배치의 기능 동일. 1280×720dp는 가로 시안 예시로 낮춤 | 결정. 실제 창 dp는 FormFactorConsole로 확인 필요 |
| Driving view를 가리지 않음, 주행 중 전체 화면 팝업 금지 | G1, G10 | 긴급 화면 전환 | 긴급 화면은 앱 창 안에서만 바꾸고 시스템 전체 화면 팝업을 쓰지 않음 | 결정 |
| DO 선언, `CarUxRestrictions` 반영 | G4, G5 | 일부 반영 | 주행 중 사용 Activity에 DO 선언 계획. 설정·보호자 등록·QR·긴 설명은 주행 중 차단 | 결정 |
| unknown 상태 | G6 | PRD 6.2가 "정차 중으로 간주" | 주행 상태를 알 수 없으면 주행 중으로 간주 | 결정(PRD 문장 수정 필요) |
| 긴 설명 글 주행 중 제한 | G4 | 경고등 도감 상세 | 주행 중에는 아이콘·한 줄·음성, 상세는 정차 후 | 결정 |
| 애니메이션·동영상·손발 동작 유도 금지 | G7, G8 | 음성과 설명 애니메이션 동기화, 발 이동 그림 | 주행 중에는 단계별 정지 그림 1장과 짧은 음성. 페달 그림·애니메이션은 "정차 중 비상 절차 미리 익히기" 화면으로 이동 | 결정. 비상 절차 음성의 허용 범위는 기술 문의 |
| 대비 4.5:1 | G7 | #702132 위 #FF5B6B 조합 | 해당 조합 교체 | 결정 |
| 글자 5mm·3mm, 12sp, 3줄, 1~2초 | G7, G9 | 28~46sp 자체 값 | 원문 수치를 토큰·PRD에 근거와 함께 적음 | 결정 |
| 버튼·간격 수치 없음 | G8 | 주요 터치 80dp(이전 v2 문서에는 64dp) | 주요 버튼 80dp·간격 24dp는 자체 설계값으로 유지하고 "Pleos 기준"이라고 쓰지 않음 | 결정 |
| 응답 0.25초, 버튼 2초, 실행 10초 | G3, G7 | 일부 | 성능 예산에 포함, 2초를 넘으면 진행 표시 | 결정 |
| 주간/야간 대응 | G7 | 다크 단일 팔레트 | 주간 토큰 추가 필요 | 확인 필요 |
| 오디오 포커스 요청·처리, Gleo 즉시 중단 | G11 | "포커스 정책에 맞춰 시험" | TTS `setAudioAttributes`로 안내 용도 지정(내비 안내 또는 알람, 시험으로 결정), Gleo 호출 시 즉시 중단 | 결정. 용도별 매트릭스 결과는 시험 필요 |
| 선행 무음 150~200ms | G12 | 없음 | 음성 앞 150~200ms 무음 | 결정. TTS가 PCM을 주지 않아 삽입 방법은 시험 필요 |
| Suspend 포커스 반납 | G13 | 시동 꺼진 뒤 안내 지속 가정 일부 | Suspend 때 포커스 반납, 시동 꺼진 뒤 음성 안내를 약속하지 않음 | 결정 |
| CPU 5% 미만, RAM 400MB | G14 | 상시 분석 후보 | 성능 예산을 개발용 규칙에 추가, 상시 음성 인식 금지(누를 때만 듣기) | 결정 |
| 더미 데이터 금지 | G3 | 해당 없음 | 시연 모드는 공모전용. 입점 빌드에서 제거 | 기록 |
| 명시적 동의·철회 시 삭제·위치 약관 | G15, G16, G17 | 동의 화면 일부 | 운전자 위치 제공 동의, 보낼 때마다 알림과 공유 중지, 제공 기록 보관, 위치기반서비스 신고는 출시 전 요건 | 결정. 법 해석은 확인 필요 |
| 문자·전화 제한 가능 | G27 | 서버 발송 | 보호자 알림은 서버가 보냄 | 맞음 |
| 마이크는 음성 입력 때만 | G10 | 상시 인식 후보 | 버튼을 누른 뒤에만 듣기 | 결정 |
| Virtual Console 카드 범위 | G19, G20 | 시연 방법 미정 | 카드가 있는 신호는 VC로, 없는 신호는 앱 안 [시험 입력] 재생기로 시연 | 결정 |
| 주행 제한 시험 | G22 | 없음 | adb `driving_regulation on/off`로 화면 축소 시연 | 결정 |
| Vehicle 권한 사전 협의 | G3, G28 | 언급 없음 | 실제 입점 위험으로 기록. 최소 권한만 선언 | 기록 |

## 12. 기술 문의가 필요한 것

1. 일반 앱이 긴급 음성에 어떤 오디오 용도를 써야 하는지, Interaction Matrix v1.0의 해당 칸 값.
2. 차량 관리·안전 앱이 DO를 선언하면 주행 중 정지 그림·짧은 문장·음성이 허용되는지, 취급설명서의 비상 절차 음성이 손·발 동작 유도 조항에 걸리는지.
3. 한국(KOR)이 주행 규제 표의 「그외 지역」에 해당하는지.
4. Connect-S·Connect-W의 실제 앱 창 dp, 버튼·간격 dp(디자인 자산).
5. Virtual Console에서 차속·에어백·연료량을 바꿀 방법이 있는지.
6. AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED`처럼 Pleos 문서가 안내하는 AAOS 경로가 공모전 허용 범위(공개 SDK·API)에 드는지.

## 13. 갱신 기록

| 날짜 | 내용 |
| --- | --- |
| 2026-11-12 | 처음 작성. 공식 원문 인용을 원문 텍스트와 대조했고, 1차 비판 반영 결정과 대응표를 만들었습니다. 앱·에뮬레이터는 실행하지 않았습니다. |
