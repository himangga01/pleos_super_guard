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

2026-11-12 소스 확인: 세 권한의 보호 수준은 메서드 KDoc에 "Protection level: Normal"로 적혀 있다(권한 문서에는 없음). 권한이 없으면 요청 메서드가 `SecurityException("Permission [<권한>] is required")`을 바로 던지는데, 가이드에는 이 설명이 없다. 내부 권한 `NAVI_CUSTOM_ETC`가 필요한 숨김 API(`getInternalApi`)는 시스템 앱 전용이라 쓰지 않는다.

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

2026-11-12 보충(위 표에서 빠진 기능과 용어):

| 기능 | 요청 | 결과 | 권한 |
| --- | --- | --- | --- |
| 도착 예상 비교 | `requestTrip(TripRequest)` | `onTripInfo(TripInfo)`: 거리(m), 도착까지 시간(s), 도착 때 배터리(%), 소비 에너지(Wh). 실패는 `CAUSE_SEARCH_TRIP` | `NAVI_ROUTE` |
| 내비 화면 열기 | `requestNavigationScreen(NavigationScreenRequest)` | 결과 이벤트 없음. 장소 검색(`SearchPlace(keyword)`), 장소 상세(`POIDetailPopup`) 등 13종 화면 | `NAVI_CUSTOM_MAP` |
| 현재 위치 주소 | `getCurrentLocationInfo()` | `onCurrentLocationInfo`: 위도·경도·방향·주소 문자열. 측정 시각 없음 | `NAVI_ROUTE` |

- 위 표 "운행 정보"의 **카메라는 도로 단속 카메라 알림**(`onCameraAlert`)이다. 차량 카메라나 블랙박스가 아니다.
- `requestTrip` 결과에는 요청을 구분하는 값이 없으므로 후보 여러 곳을 비교할 때는 한 번에 하나씩 요청한다. 내연기관 차량에서 결과가 오는지는 확인하지 못했다.
- 앱이 검색 결과 목록을 돌려받는 장소 검색 API는 없다. `SearchPlace`는 내비 화면을 열 뿐이다.

## 5. 모델과 예제 리뷰

3.0.5 계열 모델의 좌표는 **위도·경도** 순서다. Kotlin에서는 이름 있는 인자로 혼동을 줄일 수 있다. Java는 현재 배포 모델의 생성자 전체를 확인해야 한다.

실제 `RouteInfo` 일반 생성자는 다음 순서의 11개 인자를 갖는다.

```text
latitude, longitude, displayLatitude, displayLongitude,
poiName, poiId, address, poiSubId, routeOption, poiProvider, isMobileRequest
```

목적지·경로 문서의 Java 예제는 9개 인자와 과거 좌표 순서를 사용한다. 경유지 Kotlin 예제의 `onWaypointInfo`도 실제 메서드 `onWayPointInfo`와 철자가 다르다. 문서를 그대로 복사하기 전에 [리뷰 F1·F4](../analysis/pleos-sdk-review.md)를 확인한다.

2026-11-12 추가 확인: 경유지 문서의 Java `RequestWaypointInfo` 예제는 경도를 먼저 넣고 인자 7개를 쓴다. 실제는 위도 먼저, `poiProvider`를 포함한 8개다. 경유지 포함 경로 문서의 Java 예제는 좌표 순서는 맞지만 `RouteInfo` 8개·`RequestWaypointInfo` 7개 인자로 실제(11개·8개)와 달라 그대로는 컴파일되지 않는다. Kotlin 기본 인자에 `@JvmOverloads`가 없어 Java에서는 전체 인자 생성자만 쓸 수 있다.

## 6. 오류와 시험 과제

초기화 실패는 연결 콜백, 요청 오류는 `NaviHelperEventListener.onError(NaviErrorCode)`에서 처리한다. 안내에는 배터리 부족·거리 초과·너무 짧은 경로·서버 탐색 오류가 예시로 나온다. 좌표·POI·경유지 인덱스·권한·현재 경로 상태를 구분해 확인한다.

2026-11-12 보충: 실제 `NaviErrorCode`는 가이드 표의 4종에 `CAUSE_SEARCH_TRIP`(도착 예상 조회 실패)을 더한 5종이다. 초기화 전에 부른 요청은 앱에 오류를 알리지 않고 로그만 남긴 채 버려진다. 연결은 500ms 간격으로 최대 30번 다시 시도한 뒤 `onFailed`로 알린다. 서비스가 알 수 없는 응답을 보내면 SDK가 버린다. 서비스 최소 버전 메타데이터는 2.0.1이다.

[공식 샘플](https://github.com/Pleos-Playground/pleos-sample-navi-app)은 SDK 3.0.5, minSdk 33을 지정한다. 아직 실행하지 않은 과제는 연결 실패, 목적지 교체·취소, 두 경유지 추가·삭제, 좌표 순서, 완료·오류 이벤트, 화면 재진입 시 리스너 중복 여부다.

## 7. 2026-11-12 후속: 플레오스 가드 적용

"가는 길에 정비소 들르기"는 다음 순서로 설계한다. 정비소 목록(이름·주소·좌표)은 앱 안 자료로 둔다. 제휴 정비소 API는 향후 계획이다.

1. `getRouteStateInfo()`로 현재 안내 중인지 확인한다.
2. 안내 중이면 `addWaypoint`로 정비소를 경유지로 넣어 기존 목적지를 지킨다. 안내 중이 아니면 `requestRoute`로 목적지를 정한다. `requestRoute`는 목적지를 바꾼다.
3. 후보가 여럿이면 `requestTrip`을 하나씩 불러 도착 예상 시간을 비교한다.
4. 결과는 `onRouteStarted`, `onRoutingCompleted`, `onError`로 확인하고, 요청이 반환됐다는 사실만으로 경로가 잡혔다고 표시하지 않는다.

주행 중 검색·입력은 주행 제한 대상이므로 정차 중에만 열거나 음성·프리셋을 쓴다. 근거: [종합 리뷰 13절](../analysis/pleos-sdk-review.md#13-2026-11-12-후속-확인-전수-분석과-정정), [후속 확인 기록](evidence/sdk-followup-2026-11-12.json). 경로 요청은 여전히 미실행이다.
