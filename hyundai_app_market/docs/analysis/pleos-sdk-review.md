# Pleos SDK 개발 문서 및 배포 패키지 리뷰

- 조사일: **2026-11-11 KST, Asia/Seoul**. 앞선 공모전 조사 이후 같은 날 수행한 SDK 심화 리뷰다.
- 최신 보충: **2026-11-12 안전 기능 조사**. [상세 기능·권한](../sdk/vehicle-safety-capabilities.md), [안전 앱 세 가지](vehicle-safety-app-ideas-2026-11-12.md), [추가 확인 기록](../sdk/evidence/vehicle-safety-inspection-2026-11-12.json). 아래의 2026-11-11 이력과 새 정적 확인을 구분한다.
- 목적: 공모전에 사용할 수 있는 SDK의 공식 문서를 모으고, 구현에 필요한 계약과 예제의 정확성을 점검한다.
- 문서 입구: [SDK 개발 문서 모음](../sdk/README.md). 원문 링크와 재서술한 개발 노트를 제공한다.
- 이전 기준: [공모전 조사](hyundai-vehicle-app-contest-2026.md), [기존 출처 기록](hyundai-vehicle-app-contest-2026-sources.md).
- 로컬 기준: Git 저장소가 아니며 앱 소스·Gradle 프로젝트는 없다. 기존 Markdown 3개를 읽고 시작했다. 기존 주 보고서 SHA-256은 `d621a373affaaf39c353822c2b20846a359ac9b0fe0057c2cfd9f74b0fa97054`였다.
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

후속 리뷰는 이 문서와 SDK 노트를 먼저 읽고 공지·릴리즈·AAR 해시·샘플 커밋을 갱신한다. 실행 결과가 추가되면 정적 검토와 실행 검증을 구분해 기록한다.

## 10. 2026-11-12 안전 기능 추가 확인

새로 받은 Vehicle 2.3.2의 차량 기능 인터페이스 20개에서 메서드 이름에 `$`가 없는 public/protected 선언 517개를 조사했다. 이 수에는 오버로드·이전 방식·권한 제한이 있으므로 사용 가능한 기능 수로 해석하지 않는다. Vehicle AAR 해시는 2026-11-11과 같았다. STT 3.3.1도 새로 받았다.

| 항목 | 새로 확인한 사실 | 적용할 한계 |
| --- | --- | --- |
| 실제 속도 | Pleos FAQ가 AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED` 조회·구독을 안내 | 권한·차량 지원 미실행. 크루즈 목표값·GPS 속도와 구분 |
| 브레이크 | `getCurrentBrakePedalStatus`·구독에 `CAR_DRIVE` 선언 | 밟음 여부. 제동력·압력·가속 페달 상태가 아님 |
| 에어백 | 전개 조회·구독에 `CAR_SAFETY` 선언 | 지원 영역·새 사건·초기 상태 구분. 모든 사고 감지 보장 없음 |
| 경고 표시 | 조회는 `CAR_SAFETY`, 지원 확인·구독은 `PRIVATE_CAR_SAFETY` 선언 | 단발 조회와 상시 구독을 같은 권한으로 가정하지 않음 |
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

진동·떨림, 결측 시 수동 선택, 카카오/문자 통지, 가로형 UI의 현재 상세 기준은 [통합 PRD v2](integrated-vehicle-care-2026-11-12.md)와 [계약 파일](../specs/vehicle-care/README.md)에 있습니다. 센서 지원·실험 임계값·실행 검증을 구분합니다. 엔진 RPM·휠 tick·전륜 각도 및 발송사·디자인 원문 추가 근거는 [안전 SDK 보충](../sdk/vehicle-safety-capabilities.md#10-prd-v2의-진동수동-선택보호자-통지-보충)에 기록했습니다. 새 바이너리 판독·앱 빌드·실차·실제 메시지 발송은 하지 않았습니다.
