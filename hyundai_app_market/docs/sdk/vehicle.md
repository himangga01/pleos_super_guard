# Vehicle SDK 개발 노트

2026-11-12 안전 기능 보충: 속도·브레이크·에어백·페달·기울기·녹화 조건은 [상세 조사](vehicle-safety-capabilities.md)를 함께 읽는다. 아래의 초기 검토 결과를 실행 검증으로 바꾸지 않는다.

2026-11-12 후속 확인: 20개 도메인의 메서드 517개를 권한·보호 수준·Virtual Console 카드와 함께 [Vehicle API 전체 목록](vehicle-api-catalog.md)에 정리했다. 경고등(텔테일) API 4개는 모두 `PRIVATE_CAR_SAFETY`(signature|privileged)라 일반 앱이 쓸 수 없고, 2.3.2에서 `VehicleSpeedListener`가 삭제되어 차속은 Vehicle SDK로 받을 수 없다. 아래 2·4절의 이전 설명 중 소스와 다른 부분은 "2026-11-12 정정"으로 표시했다. 정정 근거는 [종합 리뷰 13절](../analysis/pleos-sdk-review.md#13-2026-11-12-후속-확인-전수-분석과-정정)에 있다.

기준일: 2026-11-11 KST. 공모전 사용 가능, 배포 기준 **2.3.2**. 패키지와 API의 정적 검토를 했으며 실제 차량·AVD를 실행하지 않았다. [전체 리뷰](../analysis/pleos-sdk-review.md), [문서 전체 목차](README.md).

## 1. 역할과 의존성

Vehicle Service를 통해 차량 상태 조회·변경·알림을 처리한다. `Vehicle`에서 도메인 객체를 얻어 사용한다. [소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro), [의존성 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)

```text
ai.pleos.playground:Vehicle:2.3.2
```

실제 POM의 Pleos 의존성은 `vehicle-aidl:1.1.1`, `vehicle-data:1.1.11`이다. AAR는 약 984KB이며 SDK 버전 메타데이터도 2.3.2다. 라이브러리 매니페스트의 서비스 최소 버전 표시는 2.3.13이고, 소개 문서의 설명 기준은 2.3.21이다. 설치 환경이 이 값들과 일치하는지는 미검증이다. (2026-11-12 확인: 2.3.21은 [소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro) 본문의 Vehicle Service 버전이다. [Sample App 가이드](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app)는 Vehicle Service 2.3.22, AVD Pleos_Connect_v2.1을 기준으로 적는다.) [공개 POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2.pom)

## 2. 데이터 영역

| 영역 | 문서에 소개되는 대상 |
| --- | --- |
| Brake·DrivingMode | 브레이크·주차 브레이크·운전 모드·크루즈 관련 상태 |
| CarInfo·Display | 차량 기본정보·표시 단위 |
| Door·Window | 문·잠금·창문 상태와 지원되는 제어 |
| EvBattery·Fuel | 배터리·충전·연료·효율 |
| HVAC·Seat | 공조·좌석 상태와 기능 |
| Light·TurnSignal | 조명·방향지시등 |
| Odometer | 거리·시간 |
| Safety·Tire | 경고·타이어 |
| SideMirror·SteeringWheel | 미러·핸들 관련 상태와 기능 |
| Toll·Wiper | 통행료 장치·와이퍼 |

각 도메인 전체가 무조건 사용 가능한 것이 아니다. 영역과 API별 지원 여부를 확인한다.

**2026-11-12 정정.** 위 표는 공식 소개 문서의 설명을 옮긴 것이다. 소스를 대조하니 다음이 달랐다.

| 도메인 | 소스에 실제로 있는 것 |
| --- | --- |
| Brake | 브레이크 페달 밟음 여부(Boolean)만 있다. 주차 브레이크 API는 없다 |
| CarInfo | 차대번호(VIN)와 구형 CRN만 있다. 외부 크기·제조사·연식 API는 없다 |
| Display | 온도 표시 단위 조회·구독만 있다. 설정 API와 속도·거리 단위 API는 없다 |
| DrivingMode | 주행 모드(NORMAL·ECO·SPORT)와 크루즈 상태·목표 속도(m/s)다. 크루즈 목표 속도는 실제 차속이 아니다 |
| Safety | 에어백 전개·상태, 안전벨트 미체결 경고, 엔진오일·브레이크액·변속기오일 수준, 워셔액 부족, 출력 저하 경고, 경고등(텔테일)이다. 경고등 4개 API만 `PRIVATE_CAR_SAFETY`다 |
| Seat | 좌석 점유, 안전벨트 체결, 좌석 위치·움직임, 열선·통풍, 메모리다 |
| SteeringWheel | **조향각**(앞바퀴 조향각, 도, 왼쪽 음수, 핸들 회전각 아님)과 핸들 열선이다 |
| Tire | 공개 API는 저압 기준값(kPa)뿐이다. 현재 공기압 API는 숨김이며 `PRIVATE_CAR_SAFETY`가 필요하다 |
| Gear | 20번째 도메인이지만 도메인 전체가 숨김이고 사용 중단 예정이다. 공개 문서 범위가 아니다 |

전체 메서드는 [Vehicle API 전체 목록](vehicle-api-catalog.md)에 있다.

## 3. 사용 순서와 네 종류의 API

1. 앱에서 사용하는 API의 권한을 선언한다.
2. `Vehicle(context)`를 만들고 `initialize()`한다.
3. `getDoor()` 등으로 도메인을 얻는다.
4. Capability로 읽기·쓰기 접근 수준과 지원 영역·값 범위를 받는다.
5. 조회·변경·구독을 수행하고 결과 콜백을 처리한다.
6. 등록한 영역·리스너로 구독을 해제하고 `release()`한다.

[도메인 획득](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object), [Capability](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability), [Get](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/get-property), [Set](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/set-property), [Callback](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/callback)

| 종류 | 의미 | 확인할 결과 |
| --- | --- | --- |
| Capability | 기능 지원·접근 수준·영역·범위 | `AccessType`과 `AreaBounds` |
| Get | 현재 값 조회 | 성공값·null·예외 |
| Set | 변경 요청 | 요청 완료·실패와 실제 상태 변경을 구분 |
| Callback | 상태 변경 구독 | 실제 이벤트 수신과 등록 호출 완료를 구분 |

접근 수준은 `NONE`, `READ`, `WRITE`, `READ_WRITE`다. 읽기 가능 여부와 쓰기 가능 여부를 각각 판단해야 한다. 차량 통신 결과는 비동기이므로 결과 처리 전에 객체를 해제하지 않는다.

## 4. 권한과 오류

권한은 일반적으로 `pleos.car.permission.CAR_*` 조회 계열과 `CONTROL_CAR_*` 제어 계열로 구분된다. 예를 들어 문 상태는 `CAR_DOORS`, 제어는 `CONTROL_CAR_DOORS`다. 도메인 대표표가 모든 세부 API 권한을 보장하지는 않는다. 실제 API 요구와 샘플·설치 서비스의 허용을 함께 확인한다. [권한 안내](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)

`onFailed`는 일반 `Exception`을 받는다. `VehicleException`인 경우에만 `errorCode`를 읽는다. 미지원 기능·영역, 권한, 서비스 연결, 주행 제한, 결과 없음 등을 구분한다. 모든 예외를 권한 오류나 재시도 대상으로 처리하면 원인을 숨길 수 있다. [오류 안내](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code)

**2026-11-12 정정과 보충.** 소스로 확인한 실제 동작은 다음과 같다.

- **보호 수준**: 메서드 KDoc에 적혀 있다. `CAR_*`·`CONTROL_CAR_*` 대부분은 normal이다. normal은 매니페스트 선언만으로 설치 때 주어지며, Sample App 가이드도 실행 중 승인 창을 띄우는 방식이 아니라고 설명한다. `PRIVATE_CAR_SAFETY`(경고등 4개, 숨김 타이어 공기압 4개)와 `setSmartGlassState`의 `CONTROL_CAR_WINDOWS`는 signature|privileged라 일반 앱이 받을 수 없다.
- **권한 실패**: SDK가 호출 순간 권한을 검사한다. 읽기·쓰기·확인은 `onFailed(SecurityException("<메서드> need Permission"))`로 오고, 일반 `register*`/`unregister*`는 호출 즉시 `SecurityException`을 던진다. 어느 쪽도 `VehicleException`이 아니므로 `ERROR_SECURITY_PERMISSION`으로 오지 않는다.
- **표시와 실행이 다른 권한**: 인터페이스의 `@RequiresPermission` 표시와 실제 실행 검사가 다른 메서드가 6개다(`getTelltalesInfo`, 앰비언트 조명 쓰기 3개, 사이드미러 열선 구독·해제 2개). 실행 검사 값을 기준으로 선언한다.
- **기타 실패**: 서비스가 읽기·쓰기 실패를 알리면 대부분 오류 문자열을 담은 일반 `Exception`이다. `initialize()` 전 읽기·쓰기는 `Exception("Service connection failed")`다.
- **연결 대기**: 연결 중에 부른 호출은 대기열에 쌓였다가 연결 뒤 실행되며, 대기 시간 제한이 없다. 앱이 직접 시간 제한을 둔다. 연결은 500ms 간격으로 최대 30번 다시 시도한다.
- **콜백 스레드**: 결과와 이벤트는 Binder 스레드에서 바로 불린다. 화면 갱신 전에 메인 스레드로 넘긴다.
- **측정 시각 없음**: 리스너는 값만 준다. 받은 시각과 연결 상태를 앱이 기록한다.
- **입점 조건**: App Market 심사 가이드는 Vehicle SDK 권한을 사전 협의·승인된 경우에만 선언할 수 있다고 한다. 공모전 시연과 실제 입점을 구분한다.

## 5. 현재 예제에서 주의할 점

- Callback 예제의 영역 없는 등록·해제는 2.3.2 서명과 맞지 않는다. `DoorArea`와 동일 리스너가 필요하다.
- Java `onPositionUpdated`는 `int`가 아닌 `Integer`다.
- Set Java 예제에는 실제 메서드가 요구하는 완료 콜백이 빠져 있다. Kotlin 기본 인자 처리와 Java 호출을 구분한다.
- 공식 샘플의 현재 고정 커밋은 SDK 2.3.1을 지정한다. 2.3.2 실행 검증으로 인용하지 않는다.

정확한 대조 근거는 [리뷰 F2·F3·F5](../analysis/pleos-sdk-review.md)에 있다.

## 6. 샘플과 실행 확인 과제

[공식 샘플](https://github.com/Pleos-Playground/pleos-sample-vehicle-app), [샘플 가이드](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app)를 참고한다. 샘플은 광범위한 기능 탐색용이다. 출품 앱에는 실제로 사용하는 권한과 기능만 적용한다.

아직 실행하지 않은 확인 과제는 서비스 연결, Capability의 영역·범위, null·권한 거부·주행 제한, 실제 이벤트 수신, 화면 재진입 시 중복 구독, 요청 후 상태 재조회다. 샘플의 초기화 표시를 실제 서비스 연결 완료로 간주하지 않는다.

## 7. 2026-11-12 후속: 플레오스 가드가 쓰는 Vehicle API

모두 보호 수준이 normal인 공개 API다. 에뮬레이터 2.1.0 [Virtual Console 사용 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)에 카드가 있으면 그 카드로 값을 바꿔 시연하고, 카드가 없으면 앱 안 시험 입력 재생기로 보여 주며 화면에 [시험 입력]이라고 표시한다. 카드 값이 SDK 리스너로 실제 들어오는지는 아직 시험하지 않았다.

| 신호 | 메서드 | 권한 | 값 | Virtual Console |
| --- | --- | --- | --- | --- |
| 브레이크 밟음 | `Brake.getCurrentBrakePedalStatus`, `registerBrakePedalStatus` | `CAR_DRIVE` | `Boolean?` | Brake Pedal |
| 조향각 | `SteeringWheel.getCurrentSteeringWheelAngle`, `registerSteeringWheelAngle` | `CAR_DRIVE` | `Float?` 도, 왼쪽 음수 | Steering Angle |
| 누적 주행거리 | `Odometer.getCurrentDistanceDriven`, `registerDistanceDriven` | `CAR_DRIVE` | `Float?` km | Odometer |
| 방향지시등 | `TurnSignal.getCurrentTurnSignalState`, `registerTurnSignalState` | `CAR_LIGHTS` | `TurnSignalValue?`(OFF·RIGHT·LEFT) | Turn Signal State |
| 도어 | `Door.getCurrentDoorPosition`, `registerDoorPosition`(영역 필요) | `CAR_DOORS` | `Int?` | Door Position, Trunk / Frunk Position |
| 좌석 점유 | `Seat.getCurrentSeatOccupancy`, `registerSeatOccupancy` | `CAR_SEATS` | `Boolean?` | Seat Occupancy |
| 안전벨트 | `Seat.getCurrentSeatBeltBuckledState`, `Safety.hasSeatBeltNotBuckledWarning` | `CAR_SEATS`, `CAR_SAFETY` | `Boolean?` | 없음 |
| 에어백 전개 | `Safety.hasSeatAirbagBeenDeployed`, `registerSeatAirbagDeployed` | `CAR_SAFETY` | `Boolean?` | 없음 |
| 점검 항목 | `Safety.getEngineOilLevelState`, `getBrakeFluidLevelState`, `hasLowWasherFluidWarning`, `hasPowerDownWarningIndicator` | `CAR_SAFETY` | `OilLevelState?`, `Boolean?` | 없음 |
| 연료·배터리 | `Fuel.getFuelPercent`, `EvBattery.getEvBatteryStateOfCharge` | `CAR_ENERGY` | `Int?` % | 연료 없음, Battery SOC 있음 |

쓰지 않는 것: 경고등 상태(`PRIVATE_CAR_SAFETY`), 숨김 API(기어·주행 가능 거리·타이어 현재 공기압 등), 모든 쓰기(차량 제어) API. 차속은 Vehicle SDK가 아니라 Pleos FAQ가 안내하는 AAOS `PERF_VEHICLE_SPEED`로 받는다. [차량 앱 FAQ](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq)
