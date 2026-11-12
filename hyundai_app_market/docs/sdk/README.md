# Pleos SDK 개발 문서 모음

기준일: **2026-11-11 KST**. 다섯 SDK의 공모전 사용 허용을 현재 공식 공지와 대조했다. 사용 허용과 실제 실행 성공은 구분한다. [공모전 개발 범위](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008)

[종합 리뷰](../analysis/pleos-sdk-review.md) · [공모전 조사](../analysis/hyundai-vehicle-app-contest-2026.md) · [검증 메타데이터](evidence/sdk-inspection-2026-11-11.json)

2026-11-12 안전 기능 추가 조사: [마이크·속도·페달·기울기·카메라의 제공 범위](vehicle-safety-capabilities.md), [세 가지 앱 기획](../analysis/vehicle-safety-app-ideas-2026-11-12.md), [새 배포 파일과 권한 확인 기록](evidence/vehicle-safety-inspection-2026-11-12.json). 공식 경로·파일 확인과 실제 차량 실행은 구분한다.

## 1. SDK별 개발 노트

| SDK | 버전 | 개발 노트 | 공모전 사용 |
| --- | --- | --- | --- |
| Vehicle | 2.3.2 | [개발 노트](vehicle.md) | 가능 |
| NaviHelper | 3.0.5 | [개발 노트](navihelper.md) | 가능 |
| TextToSpeech | 3.3.1 | [개발 노트](text-to-speech.md) | 가능 |
| SpeechToText | 3.3.1 | [개발 노트](speech-to-text.md) | 가능 |
| FusedLocation | 1.1.3 | [개발 노트](fused-location.md) | 가능 |

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

## 7. 검증과 다음 갱신

- 공식 사용 허용, 주 SDK POM 5개·AAR 5개, Pleos 전이 POM 7개를 확인했다.
- 선택한 공개 JVM 메서드와 모델 생성자를 대조해 예제 불일치를 기록했다.
- Gradle 전체 의존성 해석·Android 컴파일·설치·실제 호출·서버 인증은 미실행이다.
- 다음 갱신은 종합 리뷰를 먼저 읽고 문서 변경·AAR 해시·샘플 커밋을 다시 확인한다.
