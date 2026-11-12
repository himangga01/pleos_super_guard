# FusedLocation SDK 개발 노트

기준일: 2026-11-11 KST. 공모전 사용 가능, 배포 기준 **1.1.3**. 실제 위치 조회·권한 요청·영역 알림은 미시험이다. [전체 리뷰](../analysis/pleos-sdk-review.md), [문서 전체 목차](README.md).

## 1. 역할과 의존성

Pleos FusedLocation Service가 제공하는 차량 위치를 조회·구독한다. Google Play 서비스의 Fused Location과 같은 구현·정책으로 취급하면 안 된다. [소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro), [설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup)

```text
ai.pleos.playground:FusedLocation:1.1.3
```

POM의 Pleos 의존성은 `fused-location-aidl:0.0.5`, `fused-location-data:0.0.3`이다. AAR 매니페스트에는 automotive 하드웨어와 FusedLocation 서비스 패키지·바인딩, 최소 서비스 버전 0.5.0 표기가 있다. 일반 휴대폰에서 같은 서비스를 사용할 수 있다고 보장하지 않는다.

**2026-11-12 정정.** "보장하지 않는다"보다 강한 제약이다. AAR 매니페스트가 `<uses-feature android:name="android.hardware.type.automotive" android:required="true"/>`를 선언하고, 이 선언은 앱 매니페스트에 합쳐진다. 따라서 이 SDK를 넣은 APK는 일반 휴대폰에 설치·배포할 수 없다. 플레오스 가드의 휴대폰 보조 앱은 Pleos SDK를 넣지 않은 별도 APK로 만든다. [POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/FusedLocation/1.1.3/FusedLocation-1.1.3.pom)

## 2. 권한과 개인정보 설정

필요 권한은 **`pleos.car.permission.FUSED_LOCATION`**이며 문서에서 위험 권한인 `dangerous`로 분류한다. 매니페스트 선언과 실제 허용 결과를 구분해 처리한다. 위치 업데이트가 없다고 바로 GPS 고장으로 판단하지 말고 권한·차량 개인정보 보호 설정·서비스 상태를 확인한다. [권한 문서](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions)

`showLocationAccessBlockedPopup`은 차단 상태를 알리는 기능이다. 호출로 권한이 부여되거나 차단이 해제되는 것은 아니다. Android의 정밀·대략·백그라운드 위치 권한 정책이 Pleos 동작과 동일하다고 이 문서가 보장하지 않는다.

2026-11-12 소스 확인으로 보충한다.

- 권한이 없으면 위치 API는 `onError`로 알리지 않고 호출 즉시 `SecurityException("need Permission pleos.car.permission.FUSED_LOCATION")`을 던진다. 호출부에 `try/catch`와 권한 요청 흐름이 필요하다.
- 가이드 권한 표는 조회·구독·해제만 적지만, `addProximityAlert`·`removeProximityAlert`도 같은 권한이 필요하다.
- `showLocationAccessBlockedPopup`은 받은 Context로 화면을 연다. Application Context로 만든 객체에서 부르면 Android가 예외를 낼 수 있으므로 Activity Context로 만든 객체에서 부른다.
- 위치는 개인정보다. App Market 심사 가이드는 위치를 쓰는 앱에 위치정보 이용약관 제출과 명시적 동의를 요구한다. [Pleos 앱 가이드 기준](../analysis/pleos-app-guidelines-2026-11-12.md) 9절

## 3. 실행 순서

1. Context로 객체를 만들고 `isSupported()`를 확인한다.
2. 필요한 권한과 접근 상태를 확인한다.
3. `initialize()`한다.
4. 단발 조회 또는 위치 업데이트를 등록한다.
5. 더 이상 필요하지 않으면 동일 리스너로 업데이트를 해제한다.
6. `release()`한다. 재사용하려면 새 객체로 다시 확인·초기화한다.

`unregisterFusedLocationCallback`은 구독 해제, `release`는 SDK 전체 해제이므로 서로 대체하지 않는다. [수명 관리](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/initialization-and-release)

## 4. 데이터와 API

| API | 계약 |
| --- | --- |
| `getLastKnownLocation(listener)` | 마지막 사용 가능한 위치. 새 측정 결과 보장 없음 |
| `registerFusedLocationCallback(listener)` | 위치 업데이트 수신 |
| `unregisterFusedLocationCallback(listener)` | 등록한 동일 리스너 해제 |
| `isSupported()` | 실행환경 지원 여부 |
| `addProximityAlert(request, listener)` | 지정 영역의 진입·이탈 알림 등록 |
| `removeProximityAlert(alertId)` | 지정 알림 제거 |
| `showLocationAccessBlockedPopup()` | 위치 접근 차단 안내 |

API 존재와 주요 서명은 AAR에서도 대조했다. 위치 결과의 필드는 위도·경도·고도·방향·신뢰도·시각이다. 고정 갱신 주기·정확도·항상 최신인 위치를 약속하는 계약은 확인하지 못했다.

**2026-11-12 정정.** 여기서 "신뢰도"는 `confidence: Float`이며 미터 단위 정확도(오차 반경)가 아니다. 소스의 `LocationData`는 `latitude`, `longitude`, `altitude`, `heading`, `confidence`, `timestampMillis` 6개뿐이고 설명(KDoc)이 없어 `confidence`의 뜻·단위와 `timestampMillis`의 기준 시계가 공개되지 않았다. 따라서 "오차 반경 25m" 같은 표시나 `accuracy_m` 값은 만들 수 없다. 원값 `confidence`와 측정 시각, 앱이 받은 시각을 그대로 저장한다. `registerFusedLocationCallback`에는 갱신 주기·우선순위 인자가 없다. [위치 접근](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/location-data-access)

`LocationData?`는 null일 수 있다. null을 0,0으로 바꾸면 실제 좌표처럼 잘못 저장될 수 있다. `onChanged`의 빈 상태와 `onError(Exception)`을 각각 처리한다. 예외의 재시도 가능 여부는 실제 원인을 보고 판단해야 한다. (2026-11-12 보충: 소스상 null은 `getLastKnownLocation`에서만 오고, 구독 경로는 null 위치를 앱에 전달하지 않고 버린다. `isSupported()`는 위치 서비스 패키지가 설치돼 있는지만 보고, `initialize()`는 연결 결과 콜백이 없다. 연결 실패는 이후 호출의 오류로만 드러난다.) [오류 처리](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/error-handling)

## 5. 샘플과 리뷰

[공식 샘플](https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app)은 SDK 1.1.3을 지정하고 단발 조회·구독·영역 알림·시험용 mock 위치 흐름을 포함한다. mock 좌표 주입을 실제 차량 GPS 동작으로 해석하지 않는다. 샘플 minSdk 24는 출품 앱의 API 28 이상 조건과 구분해야 한다.

실행할 과제는 지원하지 않는 AVD, 권한 미허용·위치 차단, 마지막 위치 없음, 실제 위치 업데이트, 반복 진입과 구독 해제, 영역 알림의 등록·진입·이탈·삭제다. 실제 샘플 빌드나 이러한 동작 확인은 이번 리뷰에 포함하지 않았다.

## 6. 2026-11-12 후속: 플레오스 가드 적용

- 사고·증상 기록과 보호자 알림에는 좌표, `confidence` 원값(뜻 미공개), 측정 시각(`timestampMillis`), 앱이 받은 시각을 남긴다. 미터 단위 정확도는 저장하거나 표시하지 않는다.
- 사고 순간에 `getLastKnownLocation`의 값은 새로 잰 위치가 아닐 수 있으므로, 출발 때부터 구독을 유지하고 마지막으로 받은 값과 그 나이를 함께 기록한다.
- 휴대폰 위치는 별도 출처로 표시한다. 휴대폰 보조 앱은 이 SDK를 넣을 수 없다.
- 주소 문자열이 필요하면 NaviHelper `getCurrentLocationInfo()`의 `address`를 보조로 쓸 수 있다. 이 값에는 측정 시각이 없다. [NaviHelper 노트](navihelper.md)
- 근거와 해시: [후속 확인 기록](evidence/sdk-followup-2026-11-12.json), [종합 리뷰 13절](../analysis/pleos-sdk-review.md#13-2026-11-12-후속-확인-전수-분석과-정정). 위치 조회·권한 요청은 여전히 미실행이다.
