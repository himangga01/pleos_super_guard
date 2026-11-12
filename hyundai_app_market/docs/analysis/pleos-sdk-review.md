# Pleos SDK 개발 문서 및 배포 패키지 리뷰

- 조사일: **2026-11-11 KST, Asia/Seoul**. 앞선 공모전 조사 이후 같은 날 수행한 SDK 심화 리뷰다.
- 최신 보충: **2026-11-12 안전 기능 조사**. [상세 기능·권한](../sdk/vehicle-safety-capabilities.md), [통합 전 안전 앱 세 가지(이력)](vehicle-safety-app-ideas-2026-11-12.md), [추가 확인 기록](../sdk/evidence/vehicle-safety-inspection-2026-11-12.json). 아래의 2026-11-11 이력과 새 정적 확인을 구분한다. 세 가지 안전 앱 문서는 통합 앱 기획으로 대체된 이력이다.
- **2026-11-12 후속 확인**: Vehicle 2.3.2 소스 전체와 다른 SDK·Pleos 가이드를 다시 확인했다. 결과는 [13절](#13-2026-11-12-후속-확인-전수-분석과-정정), [Vehicle API 전체 목록](../sdk/vehicle-api-catalog.md), [Pleos 앱 가이드 기준](pleos-app-guidelines-2026-11-12.md), [LLM 기록](../sdk/llm.md), [후속 확인 기록](../sdk/evidence/sdk-followup-2026-11-12.json)에 있다. 이 확인으로 10절의 경고 표시 권한 판단을 정정했다.
- 목적: 공모전에 사용할 수 있는 SDK의 공식 문서를 모으고, 구현에 필요한 계약과 예제의 정확성을 점검한다.
- 문서 입구: [SDK 개발 문서 모음](../sdk/README.md). 원문 링크와 재서술한 개발 노트를 제공한다.
- 이전 기준: [공모전 조사](hyundai-vehicle-app-contest-2026.md), [기존 출처 기록](hyundai-vehicle-app-contest-2026-sources.md).
- 로컬 기준(2026-11-11 조사 시작 시점): Git 저장소가 아니었고 앱 소스·Gradle 프로젝트는 없었다. 기존 Markdown 3개를 읽고 시작했다. 2026-11-12 현재 작업 폴더는 Git 저장소이며 앱 소스는 여전히 없다. 기존 주 보고서 SHA-256은 `d621a373affaaf39c353822c2b20846a359ac9b0fe0057c2cfd9f74b0fa97054`였다.
- 검증 방식: 공식 안내·개발 가이드·실제 Chrome 화면, 공개 Maven POM·AAR, 공식 샘플의 고정 커밋을 대조했다. **Android 빌드·에뮬레이터·실차 시험은 하지 않았다.**

## 1. 결론과 앞선 조사에서 달라진 판단

**Vehicle, NaviHelper, TextToSpeech, SpeechToText, FusedLocation 다섯 SDK 모두 공모전 출품작에 사용할 수 있다.** 현재 공모전 개발 범위 공지는 워크스페이스의 ‘준비 중’ 표시와 관계없이 공개 개발 문서의 방법으로 에뮬레이터에서 개발·시험할 수 있다고 명시한다. [공모전 개발 범위 공지](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008)

앞선 18:47 조사에서는 프로젝트 신청 문서와 SDK 상세 문서의 상태가 달라 가용성을 미확정으로 남겼다. 이번 후속 확인에서는 공모전 공지의 명시적 허용을 HTTP 원문과 Chrome 화면에서 확인했다. 따라서 **공모전 사용 허용 여부의 미확정은 해소**한다. 공지의 정확한 게시·수정 시각은 화면에 없으므로 추정하지 않는다.

| SDK | 공모전 허용 | 확인한 배포 버전 | 공개 POM·AAR | 실제 서비스 호출 |
| --- | --- | --- | --- | --- |
| Vehicle | 가능 | 2.3.2 | 확인 | 미실행 |
| NaviHelper | 가능 | 3.0.5 | 확인 | 미실행 |
| TextToSpeech | 가능 | 3.3.1 | 확인 | 미실행 |
| SpeechToText | 가능 | 3.3.1 | 확인 | 미실행 |
| FusedLocation | 가능 | 1.1.3 | 확인 | 미실행 |

버전은 [SDK 릴리즈](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note)와 각 모듈의 실제 배포 패키지를 대조했다. 공개 패키지를 받았다는 사실은 음성 서버 인증이나 특정 차량 기능의 사용 성공을 의미하지 않는다.

현재도 **Pleos LLM SDK·공개되지 않은 SDK는 제공 대상이 아니며, Gleo Interface 승인 신청과 차량 마이크의 외부 음성 인식 전송은 허용되지 않는다.** 이 제한과 다섯 공개 SDK의 허용을 구분해야 한다.

**2026-11-12 갱신 결론.** 다섯 SDK 허용은 그대로다. 다만 허용된 SDK 안에서도 일반 앱이 받을 수 없는 권한이 있다. 경고등 API 4개는 모두 `PRIVATE_CAR_SAFETY`(signature|privileged)를 요구하므로 플레오스 가드는 차량 경고등 상태를 읽지 않고 향후 계획으로 둔다. 차속은 Vehicle SDK에 없고 Pleos FAQ가 안내하는 AAOS 경로로 받는다. LLM SDK 2.1.3.2 파일은 Maven에 있지만 공모전 기간에는 제공되지 않는다. Pleos Platform SDK·SDV OS 참조 문서는 공개되지 않았고, Fleet API는 계약이 필요하며 Vehicle Data API는 유럽 전용이라 쓸 수 없다. 자세한 근거는 13절에 있다.

## 2. 수집한 문서와 읽을 순서

SDK별 가이드와 API 참조 시작점 **49개**를 열었다. 모듈별로 Vehicle 10개, NaviHelper 11개, TTS 10개, STT 11개, FusedLocation 7개다. 이 수에는 제목과 하위 경로를 제공하는 API 시작점 5개가 포함된다. 추가로 패키지·클래스·선택 메서드 페이지, 설치·릴리즈·샘플을 확인했다.

| 개발 노트 | 포함한 내용 |
| --- | --- |
| [Vehicle](../sdk/vehicle.md) | 차량 데이터·제어, Capability, 권한, 비동기 처리, 예제 오류 |
| [NaviHelper](../sdk/navihelper.md) | 경로·경유지·정보 이벤트, 초기 연결, 좌표·생성자 차이 |
| [TextToSpeech](../sdk/text-to-speech.md) | 모드, 등록·인증, 합성, 언어·종료·오류 |
| [SpeechToText](../sdk/speech-to-text.md) | 마이크·PCM 입력, 중간·최종 결과, 권한·오류 |
| [FusedLocation](../sdk/fused-location.md) | 위치·지원 확인, dangerous 권한, null·구독·해제 |

개발을 시작할 때는 공통 환경, 모듈 의존성, 권한, 초기화·해제, 주요 기능, 오류 처리 순서로 읽는다. 마지막에 예제를 해당 버전 AAR의 실제 함수 서명과 맞춘다. 배포 패키지와 샘플의 기준 버전이 항상 같지는 않다.

## 3. 시스템 구조와 사용 가능의 의미

차량 앱은 SDK를 통해 Pleos의 Vehicle·Navigation·Speech·FusedLocation 서비스와 연결된다. SDK는 일반 Android 라이브러리만으로 차량 기능을 완결하는 구조가 아니다. 전용 에뮬레이터에 해당 플랫폼 서비스가 있어야 한다. [SDK 개요](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/)

확인 수준을 다음과 같이 나눈다.

1. **공모전 규칙:** 다섯 SDK 사용이 명시적으로 허용된다.
2. **배포:** 다섯 주 SDK의 POM·AAR를 인증 없이 읽었다.
3. **정적 API:** AAR 내부 `classes.jar`에서 필요한 메서드·타입을 확인했다.
4. **실행:** 사용자 환경의 서비스 연결·권한·음성·위치·제어·화면 동작은 시험하지 않았다.
5. **마켓:** 공모전 허용은 정식 입점·실차·전체 차종·서버 사용 승인을 보장하지 않는다.

## 4. 공통 개발 기준과 의존성

| 항목 | 문서·배포 기준 |
| --- | --- |
| 앱 최소 API | Android API 28 이상 |
| 언어 | Java·Kotlin |
| Gradle 최소 안내 | 8.0 이상 |
| 전용 AVD | Pleos Connect Emulator 2.1.0, API 34 |
| AVD RAM·부팅 | 4GB, Cold boot |
| 배포 형식 | Android 라이브러리 패키지 AAR |

환경 기준: [설치·실행](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator), [설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator).

Maven 주소는 `https://nexus-playground.pleos.ai/repository/maven-releases/`다. 정확한 좌표는 다음과 같다. 각 SDK 노트에 개별 의존성 원문을 연결했다.

```text
ai.pleos.playground:Vehicle:2.3.2
ai.pleos.playground:NaviHelper:3.0.5
ai.pleos.playground:TextToSpeech:3.3.1
ai.pleos.playground:SpeechToText:3.3.1
ai.pleos.playground:FusedLocation:1.1.3
```

주요 Pleos 전이 의존성은 Vehicle의 `vehicle-aidl:1.1.1`, `vehicle-data:1.1.11`, NaviHelper의 `navi-aidl:3.0.1`, `navi-data:3.0.4`, 음성 모듈의 `ai.pleos.playground.security:universal-access:0.6.0`, 위치 모듈의 `fused-location-aidl:0.0.5`, `fused-location-data:0.0.3`이다. 이 7개 POM도 HTTP 200으로 확인했다. Google·Maven Central을 포함한 Gradle 전체 의존성 해석은 실행하지 않았다.

다섯 AAR의 라이브러리 매니페스트는 minSdk 23을 갖고 있다. 이것은 라이브러리 하한이다. **출품·제품 앱의 API 28 이상 기준을 23으로 낮출 근거가 아니다.**

## 5. 중요한 문서 리뷰 결과

아래 지적은 공식 문서·샘플의 정적 호환성 리뷰다. 외부 자료를 수정하거나 별도 제보를 보내지 않았다. 심각도는 출품 앱 구현 시 영향에 대한 연구자 판단이다.

### F1 · P1 · NaviHelper Java 목적지 예제의 생성자·좌표 불일치

[Destinations](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)와 [Routes](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/routes)의 Java 예제는 경도를 먼저 전달하고 9개 인자로 `RouteInfo`를 만든다. 반면 NaviHelper 3.0.5가 참조하는 `navi-data:3.0.4`의 일반 생성자는 **11개 인자이고 latitude가 먼저**다. 클래스의 생성자 로컬 변수 메타데이터까지 대조했다.

```text
latitude, longitude, displayLatitude, displayLongitude,
poiName, poiId, address, poiSubId, routeOption, poiProvider, isMobileRequest
```

Kotlin의 기본 인자 생략을 Java의 짧은 생성자로 취급할 수 없다. Java 예제를 그대로 옮기면 현재 배포 생성자와 맞지 않는다. 좌표 순서를 임의로 뒤집지 말고 IDE에서 실제 생성자 전체를 확인해야 한다. [경유지 포함 경로 문서](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/route-with-waypoints)의 Java 예제도 인자 수를 점검해야 한다. 배포 근거: [navi-data POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/navi-data/3.0.4/navi-data-3.0.4.pom).

### F2 · P1 · Vehicle Callback 예제의 영역 누락과 Java 타입 불일치

[Callback 가이드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/callback)는 리스너만 전달해 등록·해제하는 예제를 제공한다. Vehicle 2.3.2의 실제 공개 서명은 둘 다 **DoorArea와 리스너**를 요구하며, 이 메서드들에 영역을 생략하는 기본 인자용 메서드는 없었다.

```text
registerDoorPosition(DoorArea, DoorPositionListener)
unregisterDoorPosition(DoorArea, DoorPositionListener)
DoorPositionListener.onPositionUpdated(java.lang.Integer)
```

같은 문서의 Java 콜백은 `int`를 사용하지만 실제 메서드는 nullable 값을 담는 `Integer`를 사용한다. 영역·동일 리스너를 보관하고 Java 타입을 맞춰야 한다. Kotlin에서는 `Int?`로 null을 처리한다. [Vehicle AAR](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2.aar)

### F3 · P1 · Vehicle Set 가이드의 Java 콜백 인자 부족

[Set 가이드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/set-property)의 Java 예제는 영역·값·실패 콜백 3개만 전달한다. 실제 2.3.2 인터페이스는 영역·값·완료 콜백·실패 콜백 4개를 갖는다. Java 호출에는 완료 콜백을 명시해야 한다.

Kotlin에는 `setDoorPosition$default`가 있으므로 기본 인자를 쓰는 Kotlin 예제까지 동일 오류로 단정하지 않았다. 완료 콜백을 받더라도 실제 차량 값 변경 여부는 별도 조회·상태 알림으로 확인해야 한다. [공식 샘플의 VehicleClient](https://github.com/Pleos-Playground/pleos-sample-vehicle-app/blob/959981d960ec71144accb27af5ae5d341e72a644/app/src/main/java/ai/pleos/playground/sample/vehicle/vehicle/VehicleClient.kt)는 완료·실패 콜백을 모두 전달한다.

### F4 · P1 · NaviHelper 경유지 Kotlin 콜백 철자

[Waypoints](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/waypoint-settings)의 Kotlin 예제에는 `onWaypointInfo`가 있다. 실제 NaviHelper 3.0.5 리스너의 공개 메서드는 **`onWayPointInfo`**다. 대소문자가 달라 같은 오버라이드가 아니다. 문서 하단과 [공식 샘플](https://github.com/Pleos-Playground/pleos-sample-navi-app/blob/76ed592487a1eab7ba102b9d4b321705426886fe/app/src/main/java/ai/pleos/playground/navidevapp/screen/vm/NaviHelperViewModel.kt)은 실제 철자를 사용한다.

### F5 · P2 · Vehicle 샘플의 버전이 현재 가이드와 다름

샘플 main의 [버전 파일](https://github.com/Pleos-Playground/pleos-sample-vehicle-app/blob/959981d960ec71144accb27af5ae5d341e72a644/gradle/libs.versions.toml)은 Vehicle **2.3.1**을 지정한다. 최신 릴리즈·연동 가이드·이번 패키지 기준은 2.3.2다. 이 샘플의 실행이나 화면을 2.3.2 검증 결과로 인용할 수 없다. 프로젝트에 적용할 때 버전을 고정하고 변경된 enum·Capability·리스너 계약을 점검해야 한다.

### F6 · P2 · 샘플의 앱 최소 API를 그대로 가져오면 안 됨

검토한 샘플 설정은 Vehicle 23, FusedLocation 24, NaviHelper 33이었다. 앞의 두 값은 제품 요구 API 28보다 낮다. 샘플의 학습용 설정과 출품 앱 요구를 구분한다. NaviHelper 샘플의 33은 API 28 이상 조건을 충족하며, 임의로 28로 낮춰도 된다는 뜻은 아니다. 근거는 각 저장소의 고정 커밋 `app/build.gradle.kts`다.

### F7 · P2 · 확인한 일부 API 상세 페이지는 제목만 표시됨

선택한 `setDoorPosition`, `checkDoorPositionCapabilities`, TTS `initialize`, FusedLocation `isSupported` 페이지의 HTTP 본문에는 제목과 경로만 있었다. `setDoorPosition`은 실제 Chrome에서도 본문 코드 블록 0개·iframe 0개인 것을 확인했다. 모든 API 페이지가 그렇다고 확대하지 않는다.

이 페이지의 HTTP 200을 함수 명세 확인 성공으로 계산하지 않았다. 해당 계약은 프로그래밍 가이드와 AAR 서명으로 보완했다. API 목차·클래스 시작점이 하위 문서 경로만 제공하는 것 자체는 오류로 분류하지 않는다.

## 6. SDK별 구현 판단

| SDK | 먼저 구현·확인할 계약 | 실패하기 쉬운 가정 |
| --- | --- | --- |
| Vehicle | 권한 → Capability·영역 → 조회·구독 → 정리 | 도메인이 있으면 전 영역·전 제어를 사용할 수 있다고 가정 |
| NaviHelper | 연결 성공 콜백 → 리스너 → 요청 → 결과 이벤트 | 호출 반환을 경로 탐색 완료로 처리, 좌표·모델 버전 혼용 |
| TTS | 모드 → 권한 → 초기화 → 필요 시 서버 등록 → 합성 | 기본 HYBRID를 무조건 오프라인 모드로 취급 |
| STT | 모드·입력 경로 → 리스너 → 요청 → 최종 결과 | 중간 결과 중복 저장, PCM 입력을 전 모드에서 사용 |
| FusedLocation | 지원·권한·차단 상태 → 초기화 → nullable 위치 → 해제 | null을 0,0 좌표로 저장, 권한 선언만으로 허용 완료로 처리 |

이는 문서에서 도출한 구현 검토 기준이다. 새 앱 기능의 설계·개발이나 실제 실행 완료를 나타내지 않는다.

## 7. 현재의 제한과 미해결 질문

- 다섯 SDK 사용 허용은 확인했다. 사용 가능한 개별 Vehicle 영역·제어 범위는 실제 Capability로 확인해야 한다.
- TTS·STT의 서버 모드는 자격정보·인증·네트워크가 필요하다. 이번에 사용자 자격정보를 읽거나 서버에 등록하지 않았다.
- ON_DEVICE 음성 인식·합성 엔진과 위치·내비 서비스가 사용자 AVD에서 정상 동작하는지는 미시험이다.
- FusedLocation의 위험 권한 요청과 차량 개인정보 보호 설정을 실제 앱에서 어떻게 안내할지는 구현·시험이 필요하다.
- 문서의 예제 불일치가 언제 수정되는지, 현재 게시물의 정확한 수정일은 확인하지 못했다.
- 공모전용 공개 API와 정식 마켓 배포의 승인·권한·차종·지역 조건은 서로 구분해야 한다.
- 2026-11-12 후속 확인에서 남은 질문(AAOS 경로 허용 범위, Virtual Console 주입 결과, 긴급 음성의 오디오 용도 등)은 13.5절에 있다.

## 8. 우선순위와 실제 검증 범위

| 우선순위 | 필요한 확인 | 이번 결과 |
| --- | --- | --- |
| P0 | SDK 허용 범위 확정 | 공식 공지로 5개 허용 확인 |
| P0 | 배포 버전·파일 존재 | 주 SDK POM 5개·AAR 5개 확인 |
| P0 | 문서 예제와 실제 API 일치 | 주요 불일치 F1~F4 정적 확인 |
| P1 | 소스·SDK 버전 고정 | 공식 샘플 3개 커밋과 설정 기록 |
| P1 | 앱 빌드·AVD 연결·권한·정상 응답 | 미실행 |
| P1 | 실패·null·연결 끊김·반복 진입 시험 | 미실행 |
| P2 | 서버 인증·주행 제한·화면 규격 | 미실행 |

실제 수행은 원문 열람, 패키지 다운로드, ZIP·매니페스트·자바 가상머신(JVM) 클래스의 정적 메타데이터 대조다. 바이너리를 실행하거나 앱을 컴파일하지 않았다. 클래스 파일 판독용 임시 스크립트의 속성 읽기 순서 오류는 원인을 확인해 수정한 뒤 동일 파일로 재검증했다. 해당 일시적 도구 오류를 SDK 오류로 분류하지 않았다.

검증 기록·해시·고정 커밋·문서 URL은 [SDK 증거 기록](../sdk/evidence/sdk-inspection-2026-11-11.json)에 보존한다. SDK 바이너리와 외부 샘플 코드를 이 프로젝트의 앱 구현으로 추가하지 않았다.

## 9. 갱신 기록

| 일자 | 변경 |
| --- | --- |
| 2026-11-11 | SDK 심화 조사. 다섯 SDK의 공모전 허용을 확정하고, 개발 문서·공개 배포·샘플을 대조하여 F1~F7 기록 |
| 2026-11-12 | 안전 앱 구체화를 위해 Vehicle 2.3.2와 STT 3.3.1을 새로 받아 속도·브레이크·에어백·권한·마이크·센서·카메라 범위를 추가 확인. 실제 호출·녹화·자동 제동 시험은 미실행 |
| 2026-11-12 후속 | 사용자가 정한 Pleos 중심 통합 앱의 블랙박스·긴급 음성/애니메이션·휴대폰 백업 조건을 공식 원문으로 추가 확인. 배포 파일 재검사와 실행 시험은 하지 않음 |
| 2026-11-12 PRD 보충 | 12절 추가. 진동·수동 선택·보호자 통지 계약의 원문 근거를 보충. 새 바이너리 판독·실행은 하지 않음(이 행은 2026-11-12 후속 확인 때 빠진 기록을 채운 것) |
| 2026-11-12 후속 확인 | 13절 추가. Vehicle 2.3.2 소스·바이트코드 전수 판독, 다른 SDK 계약·Pleos 가이드 재확인. 경고등 권한·보호 수준·조향각 명칭·위치 정확도·STT 이벤트 의미 등을 정정. 실행 시험은 하지 않음 |

후속 리뷰는 이 문서와 SDK 노트를 먼저 읽고 공지·릴리즈·AAR 해시·샘플 커밋을 갱신한다. 실행 결과가 추가되면 정적 검토와 실행 검증을 구분해 기록한다.

## 10. 2026-11-12 안전 기능 추가 확인

새로 받은 Vehicle 2.3.2의 차량 기능 인터페이스 20개에서 메서드 이름에 `$`가 없는 public/protected 선언 517개를 조사했다. 이 수에는 오버로드·이전 방식·권한 제한이 있으므로 사용 가능한 기능 수로 해석하지 않는다. Vehicle AAR 해시는 2026-11-11과 같았다. STT 3.3.1도 새로 받았다.

| 항목 | 새로 확인한 사실 | 적용할 한계 |
| --- | --- | --- |
| 실제 속도 | Pleos FAQ가 AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED` 조회·구독을 안내 | 권한·차량 지원 미실행. 크루즈 목표값·GPS 속도와 구분 |
| 브레이크 | `getCurrentBrakePedalStatus`·구독에 `CAR_DRIVE` 선언 | 밟음 여부. 제동력·압력·가속 페달 상태가 아님 |
| 에어백 | 전개 조회·구독에 `CAR_SAFETY` 선언 | 지원 영역·새 사건·초기 상태 구분. 모든 사고 감지 보장 없음 |
| 경고 표시 | 조회는 `CAR_SAFETY`, 지원 확인·구독은 `PRIVATE_CAR_SAFETY` 선언 | 단발 조회와 상시 구독을 같은 권한으로 가정하지 않음. **2026-11-12 정정:** 구현의 실행 검사는 조회를 포함한 4개 모두 `PRIVATE_CAR_SAFETY`(signature\|privileged)다. 왼쪽 "조회는 `CAR_SAFETY`"는 인터페이스의 린트용 표시만 읽은 결과다. 일반 앱은 경고등 상태를 읽을 수 없다(13절) |
| 가속·브레이크 비율 | 현재 Android 공식 차량 API에 읽기 전용 항목 존재 | Pleos 대상 버전·차량·허용은 미확인. 특수 속성 검토 조건 적용 |
| 음성 | 차량 마이크 인식과 중간·최종 글 전달 확인 | 원본 녹음 출력·외부 마이크 선택·감정 판단·무기한 인식은 확인되지 않음 |
| 기울기·가속도·회전 | Android 센서 후보는 존재 | Pleos 장치 제공·권한·차량 고정 방향·품질 미확인 |
| 카메라·자동 제동 | 조사한 공개 Vehicle 인터페이스에서 녹화·강제 제동 경로 미확인 | 카메라 장착·코덱 제공을 앱 캡처 권한으로 해석하지 않음. 제조사 안전 제어는 별도 개발 |

근거는 [Pleos FAQ](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq), [차종별 호환성](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability), [Android 차량 속성](https://developer.android.com/reference/android/car/VehiclePropertyIds), 공개 AAR의 실제 서명과 권한이다. 음성·센서·원본 기록 조건과 원문 링크는 [상세 보충 자료](../sdk/vehicle-safety-capabilities.md)에 모았다.

예방 효과·사고 판단·실시간 공유의 정확도와 지속 실행은 검증하지 않았다. 실제 수행은 문서 열람·새 배포 파일 다운로드·정적 판독·문서 검사다. 이 결과를 기존 샘플이나 새 앱의 빌드·실차 성공으로 표시하지 않는다.

## 11. 2026-11-12 통합 앱의 추가 확인

사용자가 경고등·이상 가속·사고 기록을 합치고 Pleos 탑재 차량만 대상으로 정했습니다. 현재 제품 기획은 [통합 앱 문서](integrated-vehicle-care-2026-11-12.md), 추가 기술 근거는 [안전 기능 상세의 후속 조사](../sdk/vehicle-safety-capabilities.md#9-2026-11-12-통합-앱-후속-조사)에 있습니다.

블랙박스의 소비자 영상 다운로드와 Pleos 앱의 직접 API는 구분해야 합니다. 일부 외부 업체의 공식 영상 API는 별도 연동 후보로 확인했으나 대상 장치·허용·실행은 미확인입니다. 주행 중 애니메이션과 오디오 포커스, Android 백업의 시작·권한·수명 조건도 제품 구현 전에 확인해야 합니다. 이 보충은 앞선 배포 파일 판독이나 실행 결과를 새로 수행한 것으로 바꾸지 않습니다.

## 12. PRD v2 기술 계약의 추가 확인

진동·떨림, 결측 시 수동 선택, 카카오/문자 통지, 가로형 UI의 현재 상세 기준은 [현재 통합 PRD](integrated-vehicle-care-2026-11-12.md)와 [계약 파일](../specs/vehicle-care/README.md)에 있습니다. (2026-11-12 정정: 이 절을 쓸 때는 같은 경로의 문서를 "PRD v2"라고 불렀습니다. 같은 경로의 문서는 지금 v3이며 1차 비판 반영으로 v4로 고칠 예정입니다. v2 원문은 [PRD v2 이력](integrated-vehicle-care-2026-11-12-prd-v2.md)에 있습니다. 진동 분석은 2026-11-12 1차 비판 반영 결정에서 향후 계획으로 옮겼습니다.) 센서 지원·실험 임계값·실행 검증을 구분합니다. 엔진 RPM·휠 tick·전륜 각도 및 발송사·디자인 원문 추가 근거는 [안전 SDK 보충](../sdk/vehicle-safety-capabilities.md#10-prd-v2의-진동수동-선택보호자-통지-보충)에 기록했습니다. 새 바이너리 판독·앱 빌드·실차·실제 메시지 발송은 하지 않았습니다.

## 13. 2026-11-12 후속 확인: 전수 분석과 정정

### 13.1 확인 방법과 범위

- 공개 Maven 저장소에서 받은 다섯 SDK AAR의 SHA-256은 2026-11-11 기록과 같습니다. 이번에는 Vehicle·NaviHelper·FusedLocation의 소스 묶음(`-sources.jar`)과 문서 묶음(`-javadoc.jar`), `navi-data:3.0.4`, `LLM:2.1.3.2`도 함께 읽었습니다.
- Vehicle은 소스 파일 181개(Kotlin 180개, BuildConfig.java 1개)를 열고 인터페이스 20개와 구현 20개를 모두 대조했습니다. 권한 표시는 바이트코드에서 실행 검사용(`RequiresCustomPermission`)과 개발 도구용(`RequiresPermission`)을 나눠 다시 확인했습니다. TTS·STT·LLM은 소스 묶음이 없어 공개 서명과 바이트코드를 읽었습니다.
- Pleos 기획·개발·디자인·심사·시험 가이드와 릴리즈 노트를 텍스트로 끝까지 읽었습니다. 화면·주행 제한·음성·성능·개인정보 기준은 [Pleos 앱 가이드 기준](pleos-app-guidelines-2026-11-12.md)에 원문 인용과 함께 정리했습니다.
- 확인 방법은 소스·바이트코드 정적 분석입니다. 앱 빌드, 에뮬레이터, Virtual Console 값 주입, 실차 호출, 서버 인증은 **하지 않았습니다.** 해시·출처·결론은 [후속 확인 기록](../sdk/evidence/sdk-followup-2026-11-12.json)에 있습니다. 기존 확인 기록 두 개는 고치지 않고, 새 기록에서 정정 관계를 밝혔습니다.

### 13.2 정정한 판단

이전 문장은 지우지 않고 해당 위치에 "2026-11-12 정정"을 붙였습니다.

| 이전 판단(위치) | 정정 | 근거 |
| --- | --- | --- |
| `getTelltalesInfo`는 `CAR_SAFETY`로 조회 가능(조건부)하고 지원 확인·구독만 `PRIVATE_CAR_SAFETY`(10절 표, [안전 보충](../sdk/vehicle-safety-capabilities.md) 2.1·6절, 안전 확인 기록) | 경고등 API 4개 모두 실행 검사에서 `PRIVATE_CAR_SAFETY`(signature\|privileged)를 요구합니다. 일반 앱은 경고등 상태를 읽을 수 없고, 경고등 상태 읽기는 향후 계획입니다 | 소스 묶음 `api/implement/SafetyImpl.kt` 194·335·542·553행, `api/Safety.kt` KDoc, 바이트코드. 이전 확인은 개발 도구용 표시만 읽었습니다 |
| 권한 보호 수준이 기록되어 있지 않음 | 메서드 KDoc에 적혀 있습니다. "normal" 456곳, "signature\|privileged" 9곳(`PRIVATE_CAR_SAFETY` 8, `setSmartGlassState` 1). `CAR_*`·`CONTROL_CAR_*` 대부분은 normal입니다 | 소스 묶음 KDoc, Sample App 가이드 |
| "핸들 각도"(안전 보충 2.1절) | **조향각(SteeringWheel)**입니다. 앞바퀴 조향각이며 단위는 도, 왼쪽이 음수입니다. 핸들 회전각이 아닙니다 | `api/SteeringWheel.kt` KDoc, 구현이 `PERF_STEERING_ANGLE` 사용 |
| 안전벨트 메서드 미확인 | `Seat.getCurrentSeatBeltBuckledState`(`CAR_SEATS`)와 `Safety.hasSeatBeltNotBuckledWarning`(`CAR_SAFETY`)이 있고 둘 다 normal입니다 | `api/Seat.kt`, `api/Safety.kt` |
| Brake에 주차 브레이크, CarInfo에 차량 기본 정보, Display에 단위 조회·설정이 있음([Vehicle 노트](../sdk/vehicle.md) 2절, 공식 소개 문서) | Brake는 페달 밟음 여부만, CarInfo는 VIN(과 구형 CRN)만, Display는 온도 단위 조회·구독만 있습니다 | 소스 묶음 `api/Brake.kt`, `api/CarInfo.kt`, `api/Display.kt` |
| 권한 오류를 `VehicleException` 코드·`ErrorType.PERMISSION_DENIED`·`onError`로 처리(각 SDK 노트) | 권한이 없으면 SDK가 먼저 `SecurityException`을 만듭니다. Vehicle은 읽기·쓰기·확인에서 `onFailed`로 주고 일반 구독·해제에서는 즉시 던집니다. NaviHelper·FusedLocation·TTS·STT는 공개 메서드 호출 때 즉시 던집니다 | Vehicle `utils/PermissionUtil.kt`, `service/VehicleServiceManager.kt`, 다른 SDK의 권한 검사 코드 |
| 위치에 정확도(반경)를 보관(안전 보충 6절) | `LocationData`에는 미터 단위 정확도가 없습니다. 뜻과 단위가 공개되지 않은 `confidence`와 `timestampMillis`만 있습니다 | FusedLocation 소스 `model/LocationData.kt` |
| 일반 휴대폰에서 같은 서비스를 보장하지 않음([FusedLocation 노트](../sdk/fused-location.md) 1절) | AAR 매니페스트가 `android.hardware.type.automotive`를 필수로 선언하므로 같은 APK는 휴대폰에 설치할 수 없습니다. 휴대폰 보조 앱은 Pleos SDK가 없는 별도 APK입니다 | FusedLocation AAR 매니페스트 |
| `onEndedRecognition`은 인식 세션 종료([STT 노트](../sdk/speech-to-text.md) 4절) | 발화 끝을 감지한 이벤트(EPD_OFF)입니다. `onStartedRecognition`은 발화 시작 감지(EPD_ON)입니다 | STT 3.3.1 바이트코드 |
| NaviHelper 오류 4종, 경유지 포함 Java 예제는 인자 수 점검 대상(F1) | 오류는 `CAUSE_SEARCH_TRIP`을 더해 5종입니다. 경유지 포함 Java 예제는 `RouteInfo` 8개·`RequestWaypointInfo` 7개 인자로, 실제 11개·8개와 달라 그대로는 컴파일되지 않습니다 | NaviHelper·navi-data 소스 |
| "통합 PRD v2"라는 링크 이름(12절, 안전 보충 10절) | 같은 경로는 현재 통합 PRD입니다(2026-11-12 v3, 1차 비판 반영으로 v4 예정). v2 원문은 [PRD v2 이력](integrated-vehicle-care-2026-11-12-prd-v2.md)입니다 | 저장소 문서 머리말 |
| PRD의 64dp 터치(안전 보충 10절) | 주요 버튼 80dp는 자체 설계값이며 Pleos 공식 수치가 아닙니다. Pleos 원문 텍스트에는 버튼 dp 수치가 없습니다 | [Pleos 앱 가이드 기준](pleos-app-guidelines-2026-11-12.md) 6절 |

### 13.3 새로 확인한 사실

- **차속**: Vehicle 2.3.2에는 차속 API가 없습니다(`VehicleSpeedListener` 삭제). Pleos FAQ는 AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED`(m/s, 권한 `android.car.permission.CAR_SPEED`)를 안내합니다. 다섯 SDK 목록 밖의 경로이므로 공모전 허용 범위는 기술 문의 대상입니다. Virtual Console 사용 가이드에도 차속 카드가 없으므로 시연은 앱 안 시험 입력 재생기로 합니다.
- **Vehicle 서비스 연결**: 연결 중에 부른 호출은 대기열에 쌓였다가 연결 뒤 실행되며 대기 시간 제한이 없습니다. 앱이 직접 시간 제한을 둡니다. 연결은 500ms 간격으로 최대 30번 다시 시도합니다.
- **콜백 스레드**: Vehicle 결과·이벤트는 Binder 스레드에서 불립니다. 화면을 바꾸기 전에 메인 스레드로 넘깁니다. 값에는 측정 시각이 없으므로 앱이 받은 시각을 기록합니다.
- **Vehicle 공개 범위**: 인터페이스 메서드 517개, 숨김 이름 18개와 `Vehicle.getGear()`가 있습니다. 숨김을 뺀 고유 이름 458개는 공식 API 참조 이름과 같습니다. 기어·주행 가능 거리·EV 에너지·타이어 현재 공기압은 숨김입니다. 비상등·가속 페달·주차 브레이크·시동 상태·충격 API는 없습니다. 전체 표는 [Vehicle API 전체 목록](../sdk/vehicle-api-catalog.md)에 있습니다.
- **FusedLocation**: `isSupported()`는 위치 서비스 패키지가 있는지만 봅니다. `initialize()`에는 결과 콜백이 없습니다. 구독 경로는 빈 위치(null)를 전달하지 않습니다. 근접 알림 2개도 `FUSED_LOCATION`이 필요합니다. `showLocationAccessBlockedPopup()`은 Activity Context로 만든 객체에서 불러야 합니다.
- **TextToSpeech**: 기본 오디오 속성은 `CONTENT_TYPE_SPEECH`와 `USAGE_MEDIA`입니다. Pleos 오디오 포커스 표에서 MEDIA 용도는 MUSIC 컨텍스트입니다. `setAudioAttributes`로 바꿀 수 있지만 이 메서드는 가이드 본문 없이 API 참조 제목만 있습니다. 재생 우선순위 인자는 없고, SDK 안에는 오디오 포커스 코드가 없습니다.
- **SpeechToText**: 결과는 `onUpdated(stt: String, completed: Boolean)`의 글 하나와 완료 여부뿐입니다. 신뢰도·후보 목록·언어 코드는 없습니다. `sendAudio`는 ON_DEVICE가 아니면 오류 없이 무시됩니다. `getCallApi`, `getAmbientAi`는 특권 앱 전용입니다.
- **NaviHelper**: `requestTrip`으로 후보의 거리·도착 예상 시간을 받을 수 있습니다. `requestNavigationScreen`은 장소 검색 화면을 포함한 13종 화면을 엽니다. 권한 보호 수준은 KDoc에 Normal로 적혀 있습니다. 초기화 전 호출은 앱에 알리지 않고 버려집니다.
- **LLM SDK**: `ai.pleos.playground:LLM:2.1.3.2` 파일은 Maven에 있지만 공모전 기간에는 제공되지 않습니다(공지 8). 에뮬레이터 2.1.0에서 제공이 종료됐습니다. [LLM 기록](../sdk/llm.md)
- **공개되지 않은 문서**: Pleos Platform SDK(사이트맵 경로 약 9,500개)와 SDV OS(약 2,200개) 참조 문서는 경로만 있고, 요청하면 HTTP 302로 넘어가 본문을 받을 수 없었습니다(404 페이지). 공개 범위가 아니므로 쓰지 않습니다.
- **서버 API**: Fleet API는 「Pleos Fleet 사용 계약 후」 엔드포인트를 받습니다. Vehicle Data API는 「현재(2026.8월) 유럽 지역에서만 사용 가능합니다」. 둘 다 차량 앱 SDK가 아니며 공모전에 쓸 수 없습니다. [Fleet API 시작하기](https://document.pleos.ai/api-reference/fleet-api/getting-started), [Vehicle Data API 소개](https://document.pleos.ai/api-reference/vehicle-data-api/intro)
- **에뮬레이터**: Pleos Connect Emulator v2.1.0(2026-10-19)에 Virtual Console과 폼 팩터 시험 기능이 들어갔습니다. 4절의 "2.1.0" 표기는 릴리즈 노트로 확인했습니다. quick boot로 만든 AVD는 VHAL을 쓸 수 없으므로 cold boot로 실행합니다. [에뮬레이터 릴리즈 노트](https://document.pleos.ai/docs/pleos-only/release-note/emulator-release-note)
- **Vehicle Service 버전**: Vehicle 소개 문서는 2.3.21, Sample App 가이드는 2.3.22, AAR 매니페스트의 최소 버전은 2.3.13입니다. 서로 다른 값이므로 실제 AVD의 서비스 버전을 기록해야 합니다.

### 13.4 플레오스 가드에 적용하는 현재 결론

등급은 2026-11-12 1차 비판 반영 결정을 따릅니다. [시연]은 공개 SDK와 Pleos 문서 경로로 지금 만들어 에뮬레이터에서 보여 줄 수 있는 기능, [향후]는 공개 문서에 없는 API·권한·장치가 필요해 기획서에 향후 계획으로만 적는 기능입니다.

| 신호 | API와 권한(보호 수준) | Virtual Console | 등급 |
| --- | --- | --- | --- |
| 차속 | AAOS `CarPropertyManager` `PERF_VEHICLE_SPEED`(`android.car.permission.CAR_SPEED`), Pleos FAQ 안내 경로 | 카드 없음 | [시연: 시험 입력] |
| 브레이크 밟음 | `Brake.getCurrentBrakePedalStatus`·`registerBrakePedalStatus`(`CAR_DRIVE`, normal) | Brake Pedal | [시연: VC] |
| 조향각 | `SteeringWheel.getCurrentSteeringWheelAngle`(`CAR_DRIVE`, normal) | Steering Angle | [시연: VC] |
| 주행거리 | `Odometer.getCurrentDistanceDriven`(`CAR_DRIVE`, normal) | Odometer | [시연: VC] |
| 방향지시등 | `TurnSignal.registerTurnSignalState`(`CAR_LIGHTS`, normal) | Turn Signal State | [시연: VC] |
| 도어 | `Door.getCurrentDoorPosition` 등(`CAR_DOORS`, normal) | Door Position, Trunk / Frunk Position | [시연: VC] |
| 좌석 점유 | `Seat.getCurrentSeatOccupancy`(`CAR_SEATS`, normal) | Seat Occupancy | [시연: VC] |
| 안전벨트 | `Seat.getCurrentSeatBeltBuckledState`(`CAR_SEATS`), `Safety.hasSeatBeltNotBuckledWarning`(`CAR_SAFETY`), normal | 카드 없음 | [시연: 시험 입력] |
| 에어백 전개 | `Safety.hasSeatAirbagBeenDeployed`(`CAR_SAFETY`, normal) | 카드 없음 | [시연: 시험 입력] |
| 엔진오일·브레이크액·워셔액·출력 저하 | `Safety.getEngineOilLevelState`, `getBrakeFluidLevelState`, `hasLowWasherFluidWarning`, `hasPowerDownWarningIndicator`(`CAR_SAFETY`, normal) | 카드 없음 | [시연: 시험 입력] |
| 연료·배터리 % | `Fuel.getFuelPercent`, `EvBattery.getEvBatteryStateOfCharge`(`CAR_ENERGY`, normal) | Battery SOC만 있음 | [시연] |
| 위치 | FusedLocation(`pleos.car.permission.FUSED_LOCATION`, dangerous), `confidence`만 있음 | 해당 없음 | [시연] |
| 음성 출력·입력 | TextToSpeech·SpeechToText 3.3.1(`TTS_SERVICE`·`STT_SERVICE`, 보호 수준 미기재) | 마이크는 에뮬레이터 설정 | [시연] |
| 경로·경유지 | NaviHelper 3.0.5 `requestRoute`, `requestRouteWithWaypoints`, `addWaypoint`, `requestTrip`(`NAVI_ROUTE`·`NAVI_ROUTE_SEARCH`, Normal) | 해당 없음 | [시연] |
| 경고등 상태 | `Safety.getTelltalesInfo` 등(`PRIVATE_CAR_SAFETY`, signature\|privileged) | Telltales 카드는 있으나 읽을 수 없음 | [향후] |
| 원본 녹음·카메라·블랙박스·가속도·충격·가속 페달·기어·주차 브레이크·타이어 현재 공기압·시동 상태·비상등·전원 차단 뒤 차량 신호 | 공개 경로 없음 또는 숨김 API | – | [향후] |

### 13.5 남은 확인

1. Pleos 문서가 안내하는 AAOS 경로(`PERF_VEHICLE_SPEED`, `CarUxRestrictions`)가 공모전의 공개 SDK·API 범위에 드는지.
2. Virtual Console 카드 값이 Vehicle SDK 리스너로 실제 들어오는지, 카드가 없는 신호를 AVD에서 읽을 수 있는지.
3. `hasSeatAirbagEnabled`가 true일 때의 뜻(KDoc은 "문제가 있음"), 좌석 점유 값 변환의 뜻.
4. TTS 서비스가 `setAudioAttributes` 값으로 오디오 포커스를 요청하는지, 일반 앱이 긴급 안내에 쓸 수 있는 오디오 용도.
5. `TTS_SERVICE`·`STT_SERVICE`의 보호 수준, 에뮬레이터의 ON_DEVICE 한국어 엔진 유무.
6. `LocationData.confidence`와 `timestampMillis`의 뜻.
