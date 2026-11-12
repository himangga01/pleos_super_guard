# FusedLocation SDK 개발 노트

기준일: 2026-11-11 KST. 공모전 사용 가능, 배포 기준 **1.1.3**. 실제 위치 조회·권한 요청·영역 알림은 미시험이다. [전체 리뷰](../analysis/pleos-sdk-review.md), [문서 전체 목차](README.md).

## 1. 역할과 의존성

Pleos FusedLocation Service가 제공하는 차량 위치를 조회·구독한다. Google Play 서비스의 Fused Location과 같은 구현·정책으로 취급하면 안 된다. [소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro), [설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup)

```text
ai.pleos.playground:FusedLocation:1.1.3
```

POM의 Pleos 의존성은 `fused-location-aidl:0.0.5`, `fused-location-data:0.0.3`이다. AAR 매니페스트에는 automotive 하드웨어와 FusedLocation 서비스 패키지·바인딩, 최소 서비스 버전 0.5.0 표기가 있다. 일반 휴대폰에서 같은 서비스를 사용할 수 있다고 보장하지 않는다. [POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/FusedLocation/1.1.3/FusedLocation-1.1.3.pom)

## 2. 권한과 개인정보 설정

필요 권한은 **`pleos.car.permission.FUSED_LOCATION`**이며 문서에서 위험 권한인 `dangerous`로 분류한다. 매니페스트 선언과 실제 허용 결과를 구분해 처리한다. 위치 업데이트가 없다고 바로 GPS 고장으로 판단하지 말고 권한·차량 개인정보 보호 설정·서비스 상태를 확인한다. [권한 문서](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions)

`showLocationAccessBlockedPopup`은 차단 상태를 알리는 기능이다. 호출로 권한이 부여되거나 차단이 해제되는 것은 아니다. Android의 정밀·대략·백그라운드 위치 권한 정책이 Pleos 동작과 동일하다고 이 문서가 보장하지 않는다.

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

API 존재와 주요 서명은 AAR에서도 대조했다. 위치 결과의 필드는 위도·경도·고도·방향·신뢰도·시각이다. 고정 갱신 주기·정확도·항상 최신인 위치를 약속하는 계약은 확인하지 못했다. [위치 접근](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/location-data-access)

`LocationData?`는 null일 수 있다. null을 0,0으로 바꾸면 실제 좌표처럼 잘못 저장될 수 있다. `onChanged`의 빈 상태와 `onError(Exception)`을 각각 처리한다. 예외의 재시도 가능 여부는 실제 원인을 보고 판단해야 한다. [오류 처리](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/error-handling)

## 5. 샘플과 리뷰

[공식 샘플](https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app)은 SDK 1.1.3을 지정하고 단발 조회·구독·영역 알림·시험용 mock 위치 흐름을 포함한다. mock 좌표 주입을 실제 차량 GPS 동작으로 해석하지 않는다. 샘플 minSdk 24는 출품 앱의 API 28 이상 조건과 구분해야 한다.

실행할 과제는 지원하지 않는 AVD, 권한 미허용·위치 차단, 마지막 위치 없음, 실제 위치 업데이트, 반복 진입과 구독 해제, 영역 알림의 등록·진입·이탈·삭제다. 실제 샘플 빌드나 이러한 동작 확인은 이번 리뷰에 포함하지 않았다.
