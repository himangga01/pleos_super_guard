# Pleos SDK 개발 문서 모음

기준일: **2026-11-11 KST**. 다섯 SDK의 공모전 사용 허용을 현재 공식 공지와 대조했다. 사용 허용과 실제 실행 성공은 구분한다. [공모전 개발 범위](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008)

[종합 리뷰](../analysis/pleos-sdk-review.md) · [공모전 조사](../analysis/hyundai-vehicle-app-contest-2026.md) · [검증 메타데이터](evidence/sdk-inspection-2026-11-11.json)

2026-11-12 안전 기능 추가 조사: [마이크·속도·페달·기울기·카메라의 제공 범위](vehicle-safety-capabilities.md), [통합 전 세 가지 앱 기획(이력)](../analysis/vehicle-safety-app-ideas-2026-11-12.md), [새 배포 파일과 권한 확인 기록](evidence/vehicle-safety-inspection-2026-11-12.json). 공식 경로·파일 확인과 실제 차량 실행은 구분한다. 세 가지 앱 기획은 통합 앱 기획으로 대체된 이력이다.

2026-11-12 후속 확인: [Vehicle API 전체 목록](vehicle-api-catalog.md), [LLM SDK 기록(공모전 미제공)](llm.md), [Pleos 앱 가이드 기준](../analysis/pleos-app-guidelines-2026-11-12.md), [후속 확인 기록](evidence/sdk-followup-2026-11-12.json). 소스·바이트코드 정적 분석이며 실행하지 않았다. 이 확인으로 경고등 API 4개가 모두 `PRIVATE_CAR_SAFETY`(signature|privileged)를 요구한다는 점을 확인해, `getTelltalesInfo`를 `CAR_SAFETY`로 조회할 수 있다는 앞선 판단을 정정했다. 자세한 정정 목록은 [종합 리뷰 13절](../analysis/pleos-sdk-review.md#13-2026-11-12-후속-확인-전수-분석과-정정)에 있다.

## 1. SDK별 개발 노트

| SDK | 버전 | 개발 노트 | 공모전 사용 |
| --- | --- | --- | --- |
| Vehicle | 2.3.2 | [개발 노트](vehicle.md) | 가능 |
| NaviHelper | 3.0.5 | [개발 노트](navihelper.md) | 가능 |
| TextToSpeech | 3.3.1 | [개발 노트](text-to-speech.md) | 가능 |
| SpeechToText | 3.3.1 | [개발 노트](speech-to-text.md) | 가능 |
| FusedLocation | 1.1.3 | [개발 노트](fused-location.md) | 가능 |
| Vehicle 전체 API | 2.3.2 | [API 전체 목록](vehicle-api-catalog.md) | 가능. 단 `PRIVATE_*` 권한 API와 숨김 API는 일반 앱 불가 |
| LLM | 2.1.3.2(Maven에만 있음) | [기록 노트](llm.md) | **불가**. 공모전 기간 미제공(공지 8) |

2026-11-12 기준 각 SDK의 권한 보호 수준은 다음과 같다. Vehicle은 메서드 KDoc에 normal 또는 signature|privileged가 적혀 있다. NaviHelper는 KDoc에 Normal, FusedLocation은 가이드와 KDoc에 dangerous로 적혀 있다. TTS·STT는 문서와 바이너리 어디에도 적혀 있지 않다. 다섯 SDK 모두 권한이 없으면 `SecurityException`이 난다.

## 2. 공통 원문

| 문서 | 원문 |
| --- | --- |
| 프로젝트 생성 및 SDK 사용 신청 | [열기](https://document.pleos.ai/docs/pleos-only/create-my-project) |
| Overview | [열기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/) |
| Pleos Connect SDK 릴리즈 노트 | [열기](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note) |
| Pleos Connect Emulator 정식 릴리즈 | [열기](https://document.pleos.ai/docs/pleos-only/release-note/emulator-release-note) |
| Pleos Connect Emulator 설치 및 실행 | [열기](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator) |
| Pleos Connect Emulator 설정 | [열기](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator) |
| Pleos Connect Virtual Console 셋업 가이드 | [열기](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator) |
| Vehicle SDK Sample App 사용 가이드 | [열기](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app) |
| 공모전 개발 범위 및 기술 문의 안내 | [열기](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008) |

### 2026-11-12에 추가로 읽은 공통 원문

안전 기능 결론과 화면·음성 기준의 근거 문서다. 인용과 설계 대응은 [Pleos 앱 가이드 기준](../analysis/pleos-app-guidelines-2026-11-12.md)에 있다.

| 문서 | 원문 |
| --- | --- |
| 차량 앱 FAQ(차속 `PERF_VEHICLE_SPEED`) | [열기](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq) |
| SDK API 정책(표준 데이터는 AAOS Car API) | [열기](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api) |
| 차종별 API 호환성 | [열기](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability) |
| 하드웨어 접근 정책 | [열기](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/platform-api-architecture) |
| 앱 구조 및 동작 모델(세로 기본 실행) | [열기](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model) |
| 주행 안전을 고려한 설계 | [열기](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline) |
| 지역별 주행 규제 정책 | [열기](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy) |
| 주행 규제 개발 가이드 | [열기](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide) |
| 성능 및 리소스 효율 | [열기](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/performance-resource-mgmt) |
| 보안·개인정보 설계 | [열기](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/security-privacy) |
| 오디오 포커스 개발 가이드 | [열기](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development) |
| Suspend 모드 오디오 포커스 | [열기](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/suspend-mode-audio-focus) |
| TTS·음성 안내 선행 묵음 | [열기](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/voice-guidance-preroll-silence-guide) |
| adb 명령(주행 규제 on/off) | [열기](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/adb-command-reference-guide) |
| Virtual Console 사용 가이드(카드 목록) | [열기](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide) |
| 폼 팩터 테스트 가이드 | [열기](https://document.pleos.ai/docs/pleos-only/app-test-guide/form-factor-test-guide) |
| UI Design Guide 원칙 | [열기](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro) |
| Display Layout | [열기](https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout) |
| Vehicle Brake API 시작점 | [열기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/brake/entry) |
| Vehicle Safety API 시작점 | [열기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/entry) |
| Pleos Playground 릴리즈 노트(LLM SDK 종료) | [열기](https://document.pleos.ai/docs/pleos-only/release-note/playground-release-note) |

## 3. 모듈별 공식 가이드 49개

소개·설정·권한·생명주기·기능·오류와 API 참조 시작점을 모았다. `entry` 시작점 5개는 제목과 하위 문서 경로를 제공하는 목차다. 원문 열람과 SDK 실행 시험을 같은 것으로 계산하지 않았다.

### Vehicle · 10개

| 문서 | 원문 |
| --- | --- |
| Callback | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/callback) |
| Capability | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability) |
| Vehicle | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/entry) |
| Get | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/get-property) |
| Introduction | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro) |
| Obtaining a domain object | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object) |
| Importing Vehicle SDK | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup) |
| Set | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/set-property) |
| Error code | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code) |
| Declaring permission | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission) |

### NaviHelper · 11개

| 문서 | 원문 |
| --- | --- |
| Declaring permissions | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions) |
| Destinations | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations) |
| NaviHelper | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/entry) |
| Error handling | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/error-handling) |
| Event listeners | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners) |
| Importing NaviHelper SDK | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk) |
| Initialization | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release) |
| Introduction | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro) |
| Route requests with waypoints | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/route-with-waypoints) |
| Routes | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/routes) |
| Waypoints | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/waypoint-settings) |

### TextToSpeech · 10개

| 문서 | 원문 |
| --- | --- |
| App registration | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/app-registration) |
| Declaring permissions | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/declaring-permissions) |
| TextToSpeech | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/entry) |
| Error code | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/error-code) |
| Event listeners | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/event-listeners) |
| Importing TextToSpeech SDK | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/importing-sdk) |
| Initialization | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/initialization-and-release) |
| Introduction | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro) |
| Set locale | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/set-locale) |
| Start and stop synthesis | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/start-and-stop) |

### SpeechToText · 11개

| 문서 | 원문 |
| --- | --- |
| App registration | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/app-registration) |
| Declaring permissions | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions) |
| SpeechToText | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/entry) |
| Error code | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/error-code) |
| Importing SpeechToText SDK | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/importing-sdk) |
| Initialization | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/initialization-and-release) |
| Introduction | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro) |
| Event listeners | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/result-listeners) |
| Sending audio data | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/sending-audio-data) |
| Set locale | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/set-locale) |
| Start and stop recognition | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/start-and-stop) |

### FusedLocation · 7개

| 문서 | 원문 |
| --- | --- |
| Declaring permission | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions) |
| FusedLocation | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/entry) |
| 오류 처리 | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/error-handling) |
| Initialization / Release | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/initialization-and-release) |
| Introduction | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro) |
| 위치 데이터 접근 | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/location-data-access) |
| Importing FusedLocation SDK | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup) |

## 4. 추가로 확인한 API 패키지·클래스·메서드

일부 상세 페이지는 제목만 제공했다. 본문 확인의 한계와 배포 패키지로 보완한 항목은 종합 리뷰 F7에 기록했다. API 목차가 비어 보인다는 이유로 패키지가 없다고 판단하지 않는다.

| 문서 | 원문 | 본문 확인 범위 |
| --- | --- | --- |
| ai.pleos.playground.location.fused | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused/entry) | 제목·하위 경로 또는 선택한 API 페이지 |
| FusedLocation | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/entry) | 제목·하위 경로 또는 선택한 API 페이지 |
| isSupported | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/fused-location/ai.pleos.playground.location.fused/fused-location/is-supported) | 제목·하위 경로 또는 선택한 API 페이지 |
| ai.pleos.playground.navi.data | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/entry) | 제목·하위 경로 또는 선택한 API 페이지 |
| RouteInfo | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.data/route-info/entry) | 제목·하위 경로 또는 선택한 API 페이지 |
| ai.pleos.playground.navi.helper | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/entry) | 제목·하위 경로 또는 선택한 API 페이지 |
| NaviHelper | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/entry) | 제목·하위 경로 또는 선택한 API 페이지 |
| ai.pleos.playground.stt | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/entry) | 제목·하위 경로 또는 선택한 API 페이지 |
| SpeechToText | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/speech-to-text/ai.pleos.playground.stt/speech-to-text/entry) | 제목·하위 경로 또는 선택한 API 페이지 |
| ai.pleos.playground.tts | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/entry) | 제목·하위 경로 또는 선택한 API 페이지 |
| TextToSpeech | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/entry) | 제목·하위 경로 또는 선택한 API 페이지 |
| initialize | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/text-to-speech/ai.pleos.playground.tts/text-to-speech/initialize) | 제목·하위 경로 또는 선택한 API 페이지 |
| checkDoorPositionCapabilities | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/check-door-position-capabilities) | 제목·하위 경로 또는 선택한 API 페이지 |
| Door | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/entry) | 제목·하위 경로 또는 선택한 API 페이지 |
| setDoorPosition | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/door/set-door-position) | 제목·하위 경로 또는 선택한 API 페이지 |
| ai.pleos.playground.vehicle.api | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/entry) | 제목·하위 경로 또는 선택한 API 페이지 |
| ai.pleos.playground.vehicle | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/entry) | 제목·하위 경로 또는 선택한 API 페이지 |
| Vehicle | [원문](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle/vehicle/entry) | 제목·하위 경로 또는 선택한 API 페이지 |

## 5. 공식 샘플과 고정 기준

| 샘플 | 고정 커밋 |
| --- | --- |
| [Pleos-Playground/pleos-sample-vehicle-app](https://github.com/Pleos-Playground/pleos-sample-vehicle-app) | [959981d960ec](https://github.com/Pleos-Playground/pleos-sample-vehicle-app/tree/959981d960ec71144accb27af5ae5d341e72a644) |
| [Pleos-Playground/pleos-sample-navi-app](https://github.com/Pleos-Playground/pleos-sample-navi-app) | [76ed592487a1](https://github.com/Pleos-Playground/pleos-sample-navi-app/tree/76ed592487a1eab7ba102b9d4b321705426886fe) |
| [Pleos-Playground/pleos-sample-fusedlocation-app](https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app) | [c955c3a81137](https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app/tree/c955c3a811371892e8803f3b1d9ad3f0426e832c) |

고정 커밋의 버전 파일·앱 Gradle·매니페스트와 선택한 클라이언트 코드를 읽었다. 현재 샘플을 빌드·실행한 것은 아니다. 외부 소스를 출품 앱으로 복사하지 않았다.

## 6. 실제 배포 정보

| SDK | POM | AAR | 바이트 | SHA-256 |
| --- | --- | --- | ---: | --- |
| Vehicle 2.3.2 | [POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2.pom) | [AAR](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2.aar) | 983,759 | `e11fdfe498cad3ecc0d5a2a6992b0c4a4d135195f1455018127bc069fd5ef937` |
| NaviHelper 3.0.5 | [POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/NaviHelper/3.0.5/NaviHelper-3.0.5.pom) | [AAR](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/NaviHelper/3.0.5/NaviHelper-3.0.5.aar) | 65,944 | `15fadd11c7c611b1abdf7ebf9ce91f2a6ce2728e60b4e38b4343e11bb9c12e97` |
| TextToSpeech 3.3.1 | [POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/TextToSpeech/3.3.1/TextToSpeech-3.3.1.pom) | [AAR](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/TextToSpeech/3.3.1/TextToSpeech-3.3.1.aar) | 139,133 | `bfbfb58376c69604afad3389878980461971683f0c51f619d03ab74b5e6a0375` |
| SpeechToText 3.3.1 | [POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/SpeechToText/3.3.1/SpeechToText-3.3.1.pom) | [AAR](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/SpeechToText/3.3.1/SpeechToText-3.3.1.aar) | 188,560 | `35109ad13400a7fa103cca9fabbf9b785e63a6a98b17a0698a1aef51458d37bf` |
| FusedLocation 1.1.3 | [POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/FusedLocation/1.1.3/FusedLocation-1.1.3.pom) | [AAR](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/FusedLocation/1.1.3/FusedLocation-1.1.3.aar) | 57,604 | `20ff6dc2efbea53f32d1ce4712d517fc4abac3a9ce4b990f4aca52c35ebef081` |

POM은 의존성 명세, AAR는 실제 Android 라이브러리다. 모두 공개 접근으로 확인했다. 바이너리는 내부 임시 폴더에서 정적 분석했고, 이 문서 모음에는 링크와 식별 정보만 보존했다.

2026-11-12 후속 확인에서 다시 받은 다섯 AAR의 SHA-256은 위 표와 같았다. 이번에는 같은 Maven 경로의 소스 묶음(`-sources.jar`: Vehicle·NaviHelper·FusedLocation·navi-data)과 문서 묶음(`-javadoc.jar`: Vehicle·NaviHelper·FusedLocation), 그리고 공모전에 쓰지 않는 `LLM:2.1.3.2` AAR(94,274바이트)도 기록했다. 해시는 [후속 확인 기록](evidence/sdk-followup-2026-11-12.json)에 있다. 서비스 최소 버전은 AAR 매니페스트 기준으로 Vehicle 2.3.13, NaviHelper 2.0.1, TTS·STT 4.1.29(음성 서비스), FusedLocation 0.5.0이다.

## 7. 검증과 다음 갱신

- 공식 사용 허용, 주 SDK POM 5개·AAR 5개, Pleos 전이 POM 7개를 확인했다.
- 선택한 공개 JVM 메서드와 모델 생성자를 대조해 예제 불일치를 기록했다.
- Gradle 전체 의존성 해석·Android 컴파일·설치·실제 호출·서버 인증은 미실행이다.
- 다음 갱신은 종합 리뷰를 먼저 읽고 문서 변경·AAR 해시·샘플 커밋을 다시 확인한다.
- 2026-11-12 후속: Vehicle 소스 파일 181개와 바이트코드의 권한 표시를 전수 대조하고, 다른 SDK의 공개 서명·바이트코드와 Pleos 가이드를 다시 읽었다. Pleos Platform SDK·SDV OS 참조 문서는 사이트맵에 경로만 있고 본문을 받을 수 없어(302 후 404) 공개 범위로 보지 않는다. Fleet API(계약 필요)와 Vehicle Data API(유럽 전용)는 공모전에 쓸 수 없다. 에뮬레이터·Virtual Console·앱 실행은 여전히 미실행이다.
