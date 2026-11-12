# NaviHelper SDK 개발 노트

기준일: 2026-11-11 KST. 공모전 사용 가능, 배포 기준 **3.0.5**. 정적 검토 결과이며 경로 탐색·차량 위치를 실제 시험하지 않았다. [전체 리뷰](../analysis/pleos-sdk-review.md), [문서 전체 목차](README.md).

## 1. 역할과 의존성

내장 Navigation 앱에 목적지·경로·경유지·정보·화면 요청을 전달하고 이벤트로 결과를 받는다. 독립 지도 엔진이나 임의 경로 계산기를 직접 제공한다고 가정하지 않는다. [소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro), [의존성](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk)

```text
ai.pleos.playground:NaviHelper:3.0.5
```

실제 POM은 `navi-data:3.0.4`, `navi-aidl:3.0.1`을 사용한다. 모델 라이브러리 버전은 SDK와 숫자가 같지 않다. [공개 POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/NaviHelper/3.0.5/NaviHelper-3.0.5.pom)

## 2. 연결과 수명

`NaviHelper(applicationContext)` 생성 후 연결 결과를 받는 `initialize(onConnected, onFailed)`를 우선 검토한다. `onConnected`에서 리스너를 등록한 뒤 API를 사용한다. 초기 연결 재시도 실패와 내비 앱의 요청 처리 오류는 서로 다른 경로다. 종료할 때 등록한 리스너를 제거하고 `release()`한다. [초기화](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release), [오류 처리](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/error-handling)

인자 없는 초기화 호출이 반환됐다는 사실만으로 차량 서비스 연결 성공을 표시하면 실제 상태를 잘못 설명할 수 있다.

## 3. 권한

| 권한 | 문서상 적용 예 |
| --- | --- |
| `pleos.car.permission.NAVI_ROUTE` | 정보 조회·`requestTrip` |
| `pleos.car.permission.NAVI_ROUTE_SEARCH` | 경로·재탐색·경유지·취소·옵션 변경 |
| `pleos.car.permission.NAVI_CUSTOM_MAP` | 내비게이션 화면 요청 |

위 표는 [권한 문서](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)의 범위다. 공식 샘플에는 추가 권한도 있지만 출품 앱에 전체를 복사할 근거는 아니다.

## 4. 주요 기능과 비동기 결과

| 기능 | 요청 | 결과를 확인할 경로 |
| --- | --- | --- |
| 목적지 | `requestRoute`, `getDestinationInfo`, `cancelRoute` | 경로 시작·완료, 목적지 정보·변경, 오류 |
| 경로 조정 | `changeRouteOption`, `requestReRoute` | 옵션 변경·재탐색 이벤트 |
| 경유지 | `addWaypoint`, `getWaypointInfo`, `removeWaypoint` | `onWayPointInfo` 등 |
| 목적지·경유지 일괄 | `requestRouteWithWaypoints` | 경로 시작·완료·오류 |
| 운행 정보 | 위치·주행·속도제한·카메라·충전·안내 정보 API | 대응 리스너 이벤트 |

[목적지](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations), [경로](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/routes), [경유지](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/waypoint-settings), [경유지 포함 요청](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/route-with-waypoints), [전체 이벤트](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners)

경유지는 문서상 최대 2개로 `FIRST`·`SECOND`를 사용하고, 전체 삭제는 `ALL`을 사용한다. 활성 경로가 필요한 요청은 상태를 먼저 확인한다. 요청의 정상 반환은 경로 탐색 완료가 아니다. 실제 이벤트 제공은 Navigation 앱의 상태·구현에 영향을 받는다.

## 5. 모델과 예제 리뷰

3.0.5 계열 모델의 좌표는 **위도·경도** 순서다. Kotlin에서는 이름 있는 인자로 혼동을 줄일 수 있다. Java는 현재 배포 모델의 생성자 전체를 확인해야 한다.

실제 `RouteInfo` 일반 생성자는 다음 순서의 11개 인자를 갖는다.

```text
latitude, longitude, displayLatitude, displayLongitude,
poiName, poiId, address, poiSubId, routeOption, poiProvider, isMobileRequest
```

목적지·경로 문서의 Java 예제는 9개 인자와 과거 좌표 순서를 사용한다. 경유지 Kotlin 예제의 `onWaypointInfo`도 실제 메서드 `onWayPointInfo`와 철자가 다르다. 문서를 그대로 복사하기 전에 [리뷰 F1·F4](../analysis/pleos-sdk-review.md)를 확인한다.

## 6. 오류와 시험 과제

초기화 실패는 연결 콜백, 요청 오류는 `NaviHelperEventListener.onError(NaviErrorCode)`에서 처리한다. 안내에는 배터리 부족·거리 초과·너무 짧은 경로·서버 탐색 오류가 예시로 나온다. 좌표·POI·경유지 인덱스·권한·현재 경로 상태를 구분해 확인한다.

[공식 샘플](https://github.com/Pleos-Playground/pleos-sample-navi-app)은 SDK 3.0.5, minSdk 33을 지정한다. 아직 실행하지 않은 과제는 연결 실패, 목적지 교체·취소, 두 경유지 추가·삭제, 좌표 순서, 완료·오류 이벤트, 화면 재진입 시 리스너 중복 여부다.
