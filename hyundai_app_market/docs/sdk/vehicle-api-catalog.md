# Vehicle SDK 2.3.2 API 전체 목록

- 기준일: **2026-11-12 KST**. 대상: `ai.pleos.playground:Vehicle:2.3.2`.
- 확인 방법: 공개 Maven 저장소의 AAR, 소스 묶음(`-sources.jar`), 문서 묶음(`-javadoc.jar`)을 정적으로 읽었습니다. 인터페이스 20개와 구현 클래스 20개의 선언을 모두 대조했고, 권한 표시는 바이트코드(`javap -v`)로 다시 확인했습니다. **앱 빌드·에뮬레이터·실차 호출은 하지 않았습니다.**
- 함께 읽을 문서: [종합 SDK 리뷰](../analysis/pleos-sdk-review.md), [Vehicle 개발 노트](vehicle.md), [안전 기능 상세](vehicle-safety-capabilities.md), [Pleos 앱 가이드 기준](../analysis/pleos-app-guidelines-2026-11-12.md), [확인 기록](evidence/sdk-followup-2026-11-12.json).

## 1. 표 읽는 방법

- 한 줄은 차량 속성 하나입니다. 같은 속성을 다루는 확인·읽기·구독·해제·쓰기 메서드를 한 칸에 모았습니다. 메서드 이름 옆 "2개"는 같은 이름의 오버로드 수, "구형"은 `@Deprecated`, "숨김"은 `@suppress @hide`입니다.
- **종류**: 확인은 지원 여부·영역·값 범위를 묻는 Capability 호출입니다. 읽기는 현재 값을 한 번 받습니다. 구독은 값이 바뀔 때마다 받으며, 같은 리스너·같은 영역으로 해제합니다. 쓰기는 차량 상태를 바꾸는 요청입니다.
- **권한(실행 검사)**: 구현 클래스(`api/implement/*Impl.kt`)의 `@RequiresCustomPermission` 값입니다. SDK는 호출하는 순간 이 값을 검사합니다. 앞의 `pleos.car.permission.`은 줄였습니다. 인터페이스의 `@RequiresPermission`(개발 도구용 표시)이 실행 검사와 다른 6개 메서드는 "주의"로 적었습니다.
- **보호 수준**: 각 메서드 설명(KDoc)의 "(protection level: …)" 문구입니다. normal은 매니페스트에 적으면 설치할 때 주어지는 권한입니다. signature|privileged는 같은 서명으로 만든 앱이나 시스템에 미리 설치된 앱만 받을 수 있는 권한이므로, 일반 출품 앱은 받을 수 없다고 봅니다. 구형 오버로드와 일부 숨김 메서드는 설명이 없어 "KDoc 없음"으로 적었습니다.
- **Virtual Console**: 에뮬레이터 2.1.0의 [Virtual Console 사용 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide) 카드 이름과 속성을 작성자가 맞춰 본 결과입니다. 카드로 값을 바꿨을 때 SDK가 그 값을 실제로 받는지는 실행하지 않았습니다. "확인 필요"는 카드는 있지만 같은 속성인지 분명하지 않은 경우입니다.
- **플레오스 가드**: 2026-11-12 1차 비판 반영 결정 기준의 사용 여부입니다. 기능 ID는 F1 경고등 찾기, F2 증상 기록, F3 긴급 안내, F4 보호자 알림입니다. [시연: VC]는 Virtual Console로, [시연: 시험 입력]은 앱 안 시험 입력 재생기로 보여 준다는 뜻입니다. [향후]는 공개 범위 밖이라 기획서에 향후 계획으로만 적습니다. 플레오스 가드는 차량을 제어하지 않으므로 쓰기 메서드는 쓰지 않습니다.
- 숨김 메서드는 바이너리에서는 부를 수 있어도 공개 문서·API 참조에 없습니다. 공모전 공지는 공개 문서 범위에서만 개발하도록 하므로 쓰지 않습니다. [공모전 개발 범위](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008)

## 2. 한눈에 보는 수

| 항목 | 값 |
| --- | --- |
| 도메인 | 20개. Gear는 도메인 전체가 숨김이라 공개 문서상으로는 19개 |
| 인터페이스 메서드 | 517개(오버로드·구형·숨김 포함). 인터페이스의 권한 표시 517개와 구현의 실행 검사 517개가 하나씩 대응 |
| 숨김 메서드 | 이름 18개(EvBattery 7, Tire 4, Gear 3, Odometer 3, CarInfo 1)와 `Vehicle.getGear()` |
| 공개 고유 이름 | 숨김을 뺀 458개. 공식 API 참조의 메서드 페이지 458개와 이름이 같음 |
| 실행 권한별 수 | `CAR_*` 452개, `CONTROL_CAR_*` 56개, `PRIVATE_CAR_*` 9개 |
| signature\|privileged 표시 | `PRIVATE_CAR_SAFETY` 8곳(경고등 4, 숨김 타이어 공기압 4), `CONTROL_CAR_WINDOWS`의 `setSmartGlassState` 1곳. 숨김 `PRIVATE_CAR_INFO`는 설명 없음 |
| 표시와 실행이 다른 메서드 | 6개: `getTelltalesInfo`, `setAmbientLightBrightness`, `setAmbientLightBrightnessColor`, `setAmbientLightSwitch`, `registerHvacSideMirrorHeatState`, `unregisterHvacSideMirrorHeatState` |
| 표의 줄(속성 묶음) | 126줄. 플레오스 가드 사용 15줄, 후보 3줄, 미사용 108줄 |

## 3. 모든 도메인에 공통인 동작

소스 위치는 소스 묶음 안의 `ai/pleos/playground/vehicle/` 아래 경로입니다.

- **권한이 없을 때**: SDK가 `ContextCompat.checkSelfPermission`으로 먼저 검사합니다(`utils/PermissionUtil.kt`). 읽기·쓰기·확인은 `onFailed(SecurityException("<메서드 이름> need Permission"))`로 알립니다. 일반 `register*`/`unregister*`는 호출하는 순간 `SecurityException`을 던지므로 `try/catch`가 필요합니다(`service/VehicleServiceManager.kt`). 트립·Current 계열 구독과 글러브박스 구독은 리스너의 `onFailed`로 알립니다. 어느 경우든 `VehicleException`이 아니어서 `ERROR_SECURITY_PERMISSION` 코드로 오지 않습니다.
- **서비스 연결 전**: `initialize()`를 부르기 전의 읽기·쓰기는 `Exception("Service connection failed")`로 실패합니다. 연결 중에 부른 호출은 대기열에 쌓였다가 연결 뒤 실행됩니다. 대기에는 시간 제한이 없으므로 앱이 직접 시간 제한을 둡니다(`service/BaseServiceConnector.kt`). 서비스 연결은 500ms 간격으로 최대 30번 다시 시도합니다.
- **콜백 스레드**: 결과와 이벤트는 AIDL Binder 스레드에서 바로 불립니다. 화면을 바꾸기 전에 메인 스레드로 넘깁니다.
- **측정 시각 없음**: 리스너는 값만 줍니다. 예: `BrakePedalStatusListener.onBrakePedalStatusUpdated(status: Boolean?)`. 앱이 받은 시각과 서비스 연결 상태를 함께 기록합니다.
- **구독**: 같은 속성에 두 번째 리스너를 등록하면 SDK가 현재 값을 한 번 보내 줍니다. 첫 등록 때 초기값이 오는지는 서비스 동작에 달려 있어 확인하지 못했습니다. 서비스가 다시 연결되면 등록한 리스너를 자동으로 다시 등록합니다.
- **쓰기 완료**: `onComplete`는 요청이 받아들여졌다는 뜻이고 실제 상태가 바뀌었다는 뜻이 아닙니다. Sample App 가이드도 같은 점을 안내합니다.
- **Capability 먼저**: `accessType`(NONE, READ, WRITE, READ_WRITE)과 지원 영역·값 범위를 먼저 확인합니다. 에뮬레이터에서도 기능은 지원하지만 특정 영역은 지원하지 않을 수 있습니다.
- **Java 호출**: Kotlin 기본 인자를 Java에서는 쓸 수 없으므로 쓰기 메서드의 완료·실패 콜백을 모두 넘깁니다.

## 4. 도메인별 표

### Brake — 브레이크

- 얻는 방법: `vehicle.getBrake()`. 인터페이스 메서드 4개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 0개, 숨김 0개.
- 실행 검사 권한: `CAR_DRIVE` 4개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 브레이크 페달 밟음<br>`BRAKE_PEDAL_STATUS` | `checkBrakePedalStatusCapabilities`<br>`getCurrentBrakePedalStatus`<br>`registerBrakePedalStatus`<br>`unregisterBrakePedalStatus` | 확인·읽기·구독 | `Boolean?` (true=밟음). 압력·% 아님 | `CAR_DRIVE` | normal | 있음: Brake Pedal(0~100%, 파생 작동 상태) | 사용 — F3 확인 질문·비상 절차 연습, F2 스냅샷 [시연: VC] |

### CarInfo — 차량 정보

- 얻는 방법: `vehicle.getCarInfo()`. 인터페이스 메서드 5개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 2개, 숨김 1개.
- 실행 검사 권한: `CAR_INFO` 4개, `PRIVATE_CAR_INFO` 1개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CRN(구형)<br>`INFO_CRN` | `checkCRNInfoCapabilities`(구형)<br>`getCRNInfo`(구형) | 확인·읽기 | `String?`. Deprecated(→VIN) | `CAR_INFO` | normal | 없음(에뮬레이터 2.1.0에서 CRN 제공 종료) | 미사용 |
| 차대번호(VIN)<br>`INFO_VIN` | `checkVINInfoCapabilities`<br>`getVINInfo` | 확인·읽기 | `String?` | `CAR_INFO` | normal | 없음 | 미사용 — 개인정보. 차량 등록에 쓸지 별도 결정 |
| 전체 지원 정보(숨김)<br>(속성 없음) | `getCapabilityInfo`(숨김) | 읽기 | `Map<String, CapabilityConfig>?` | `PRIVATE_CAR_INFO` | KDoc 없음 | 없음 | 미사용 — 숨김·라이브러리 내부 전용 |

### Display — 표시 단위

- 얻는 방법: `vehicle.getDisplay()`. 인터페이스 메서드 4개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 0개, 숨김 0개.
- 실행 검사 권한: `CAR_INFO` 4개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 온도 표시 단위<br>`HVAC_TEMPERATURE_DISPLAY_UNITS` | `checkTemperatureDisplayUnitCapabilities`<br>`getCurrentTemperatureDisplayUnit`<br>`registerTemperatureDisplayUnit`<br>`unregisterTemperatureDisplayUnit` | 확인·읽기·구독 | `TemperatureUnit?` (°C/°F). 설정 API 없음 | `CAR_INFO` | normal | 없음 | 미사용 |

### Door — 도어·글러브박스

- 얻는 방법: `vehicle.getDoor()`. 인터페이스 메서드 17개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 2개, 숨김 0개.
- 실행 검사 권한: `CAR_DOORS` 14개, `CONTROL_CAR_DOORS` 3개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 글러브박스 잠금<br>`GLOVE_BOX_LOCKED` | `checkGloveBoxLockCapabilities`<br>`setGloveBoxLock`<br>`getCurrentGloveBoxLockState`<br>`registerGloveBoxLockState`<br>`unregisterGloveBoxLockState` | 확인·읽기·구독·쓰기 | `Boolean?` | `CAR_DOORS` / 쓰기 `CONTROL_CAR_DOORS` | normal | 카드 Glove Box는 열림·닫힘. 잠금 속성과 같은지 미확인 | 미사용 |
| 도어 잠금<br>`DOOR_LOCK` | `checkDoorLockCapabilities`(2개, 구형 1)<br>`getDoorLockState`<br>`setDoorLock`<br>`registerDoorLockState`<br>`unregisterDoorLockState` | 확인·읽기·구독·쓰기 | `Boolean?` | `CAR_DOORS` / 쓰기 `CONTROL_CAR_DOORS` | normal | 있음: Door Lock(모든 승객 도어 한꺼번에) | 후보 — 사고 스냅샷 보조. 잠금 제어는 쓰지 않음 |
| 도어 위치(열림)<br>`DOOR_POS` | `checkDoorPositionCapabilities`(2개, 구형 1)<br>`getCurrentDoorPosition`<br>`setDoorPosition`<br>`registerDoorPosition`<br>`unregisterDoorPosition` | 확인·읽기·구독·쓰기 | `Int?` 영역별 값. 범위는 Capability의 `AreaBounds` | `CAR_DOORS` / 쓰기 `CONTROL_CAR_DOORS` | normal | 있음: Door Position, Trunk / Frunk Position | 사용 — F1 점검 항목, F4 사고 스냅샷 [시연: VC] |

### DrivingMode — 주행 모드·크루즈

- 얻는 방법: `vehicle.getDrivingMode()`. 인터페이스 메서드 12개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 0개, 숨김 0개.
- 실행 검사 권한: `CAR_DRIVE` 12개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 크루즈 목표 속도<br>`CRUISE_CONTROL_TARGET_SPEED` | `checkCruiseControlTargetSpeedCapabilities`<br>`getCruiseControlTargetSpeed`<br>`registerCruiseControlTargetSpeed`<br>`unregisterCruiseControlTargetSpeed` | 확인·읽기·구독 | `Float?` m/s. 실제 차속 아님 | `CAR_DRIVE` | normal | 있음: Cruise Target Speed(5.556~27.778m/s) | 미사용 — 차속으로 쓰지 않음 |
| 크루즈 상태<br>`CRUISE_CONTROL_STATE` | `checkCruiseControlStateCapabilities`<br>`getCruiseControlState`<br>`registerCruiseControlState`<br>`unregisterCruiseControlState` | 확인·읽기·구독 | `CruiseControl?` (OTHER, ENABLED, ACTIVATED, USER_OVERRIDE, SUSPENDED, FORCED_DEACTIVATION_WARNING) | `CAR_DRIVE` | normal | 있음: Cruise Control State(카드의 상태 이름과 SDK 열거형이 다름) | 미사용 |
| 주행 모드<br>`DRIVING_MODE` | `checkDrivingModeStateCapabilities`<br>`getCurrentDrivingModeState`<br>`registerDrivingModeState`<br>`unregisterDrivingModeState` | 확인·읽기·구독 | `DrivingModeType?` (NORMAL, ECO, SPORT). 주행·정차 상태 아님 | `CAR_DRIVE` | normal | 없음 | 미사용 |

### EvBattery — 전기차 배터리·충전

- 얻는 방법: `vehicle.getEvBattery()`. 인터페이스 메서드 38개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 8개, 숨김 7개.
- 실행 검사 권한: `CAR_ENERGY` 33개, `CAR_INFO` 1개, `CONTROL_CAR_ENERGY` 4개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 순간 전비<br>`INSTANTANEOUS_EV_EFFICIENCY` | `checkInstantaneousEvEfficiencyCapabilities`<br>`getInstantaneousEvEfficiency`<br>`registerInstantaneousEvEfficiency`<br>`unregisterInstantaneousEvEfficiency` | 확인·읽기·구독 | `Float?` km/kWh | `CAR_ENERGY` | normal | 없음 | 미사용 |
| 충전 후 전비<br>`SINCE_CHARGE_EV_EFFICIENCY` | `checkSinceChargeEvEfficiencyCapabilities`<br>`getSinceChargeEvEfficiency`<br>`registerSinceChargeEvEfficiency`<br>`unregisterSinceChargeEvEfficiency` | 확인·읽기·구독 | `Float?` km/kWh | `CAR_ENERGY` | normal | 없음 | 미사용 |
| 누적 전비<br>`CUMULATIVE_EV_EFFICIENCY` | `checkCumulativeEvEfficiencyCapabilities`<br>`getCumulativeEvEfficiency`<br>`registerCumulativeEvEfficiency`<br>`unregisterCumulativeEvEfficiency` | 확인·읽기·구독 | `Float?` km/kWh | `CAR_ENERGY` | normal | 없음 | 미사용 |
| 이번 주행 전비<br>`TRIP_EV_EFFICIENCY` | `checkCurrentEvEfficiencyCapabilities`<br>`getCurrentEvEfficiency`<br>`unregisterCurrentEvEfficiency`<br>`registerCurrentEvEfficiency` | 확인·읽기·구독 | `Float?` km/kWh | `CAR_ENERGY` | normal | 없음 | 미사용 |
| 구동 배터리 충전량<br>`EV_BATTERY_SOC` | `checkEvBatteryStateOfChargeCapabilities`<br>`getEvBatteryStateOfCharge`<br>`registerEvBatteryStateOfCharge`<br>`unregisterEvBatteryStateOfCharge` | 확인·읽기·구독 | `Int?` % | `CAR_ENERGY` | normal | 있음: Battery SOC | 사용 — F2 스냅샷(전기차) [시연: VC] |
| 충전 전류 한도<br>`EV_CHARGE_CURRENT_DRAW_LIMIT` | `checkEvChargeCurrentDrawLimitCapabilities`(2개, 구형 1)<br>`setEvChargeCurrentDrawLimit`<br>`getEvChargeCurrentDrawLimit` | 확인·읽기·쓰기 | `Float?` A | `CAR_ENERGY` / 쓰기 `CONTROL_CAR_ENERGY` | normal | 있음: Charge Current Limit | 미사용 — 앱은 차량을 제어하지 않음 |
| 충전 목표 %<br>`EV_CHARGE_PERCENT_LIMIT` | `checkEvChargePercentLimitCapabilities`<br>`setEvChargePercentLimit` | 확인·쓰기 | `Float` % (설정만, 조회 API 없음) | `CAR_ENERGY` / 쓰기 `CONTROL_CAR_ENERGY` | normal | 있음: Charge Percent Limit | 미사용 — 앱은 차량을 제어하지 않음 |
| 충전구 열림<br>`EV_CHARGE_PORT_OPEN` | `checkEvChargePortOpenCapabilities`<br>`isEvChargePortOpen`(숨김, 구형)<br>`setEvChargePortOpen`<br>`registerEvChargePortOpen`(숨김, 구형)<br>`unregisterEvChargePortOpen`(숨김, 구형) | 확인·읽기·구독·쓰기 | `Boolean?`. 조회·구독은 숨김, 설정만 공개 | `CAR_ENERGY` / 쓰기 `CONTROL_CAR_ENERGY` | normal | 있음: Charge Port(조회 API는 숨김) | 미사용 — 앱은 차량을 제어하지 않음 |
| 충전 켜짐<br>`EV_CHARGE_SWITCH` | `checkEvChargeSwitchCapabilities`<br>`setEvChargeSwitch`<br>`getEvChargeSwitchState` | 확인·읽기·쓰기 | `Boolean?` | `CAR_ENERGY` / 쓰기 `CONTROL_CAR_ENERGY` | normal | 있음: Charge Switch | 미사용 — 앱은 차량을 제어하지 않음 |
| 배터리 에너지(숨김)<br>`EV_BATTERY_LEVEL` | `getEvBatteryLevel`(숨김, 구형)<br>`registerEvBatteryLevel`(숨김, 구형)<br>`unregisterEvBatteryLevel`(숨김, 구형) | 읽기·구독 | `Float?` Wh | `CAR_ENERGY` | normal | 있음: Battery Energy(API는 숨김) | 미사용 — 숨김 API |
| 배터리 용량(숨김)<br>`INFO_EV_BATTERY_CAPACITY` | `getEvBatteryCapacity`(숨김, 구형) | 읽기 | `Float?` Wh | `CAR_INFO` | normal | 있음: Battery Capacity(API는 숨김) | 미사용 — 숨김 API |

### Fuel — 연료·연비

- 얻는 방법: `vehicle.getFuel()`. 인터페이스 메서드 20개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 0개, 숨김 0개.
- 실행 검사 권한: `CAR_ENERGY` 20개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 순간 연비<br>`INSTANTANEOUS_FUEL_ECONOMY` | `checkInstantaneousFuelEconomyCapabilities`<br>`getInstantaneousFuelEconomy`<br>`registerInstantaneousFuelEconomy`<br>`unregisterInstantaneousFuelEconomy` | 확인·읽기·구독 | `Float?` L/100 km | `CAR_ENERGY` | normal | 없음 | 미사용 |
| 주유 후 연비<br>`SINCE_REFUEL_FUEL_ECONOMY` | `checkSinceChargeFuelEconomyCapabilities`<br>`getSinceChargeFuelEconomy`<br>`registerSinceChargeFuelEconomy`<br>`unregisterSinceChargeFuelEconomy` | 확인·읽기·구독 | `Float?` L/100 km | `CAR_ENERGY` | normal | 없음 | 미사용 |
| 누적 연비<br>`CUMULATIVE_FUEL_ECONOMY` | `checkCumulativeFuelEconomyCapabilities`<br>`getCumulativeFuelEconomy`<br>`registerCumulativeFuelEconomy`<br>`unregisterCumulativeFuelEconomy` | 확인·읽기·구독 | `Float?` L/100 km | `CAR_ENERGY` | normal | 없음 | 미사용 |
| 이번 주행 연비<br>`TRIP_FUEL_ECONOMY` | `checkCurrentFuelEconomyCapabilities`<br>`getCurrentFuelEconomy`<br>`unregisterCurrentFuelEconomy`<br>`registerCurrentFuelEconomy` | 확인·읽기·구독 | `Float?` L/100 km | `CAR_ENERGY` | normal | 없음 | 미사용 |
| 연료량<br>`FUEL_PERCENT` | `checkFuelPercentCapabilities`<br>`getFuelPercent`<br>`registerFuelPercent`<br>`unregisterFuelPercent` | 확인·읽기·구독 | `Int?` % | `CAR_ENERGY` | normal | 없음 | 사용 — F2 스냅샷 [시연: 시험 입력] |

### Gear — 기어(숨김)

- 얻는 방법: `vehicle.getGear()`. 인터페이스 메서드 3개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 3개, 숨김 3개. 도메인 전체가 숨김(`@suppress @hide`)이며 공개 문서·API 참조 목록에 없습니다.
- 실행 검사 권한: `CAR_DRIVE` 3개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 기어(숨김)<br>`VENDOR_GEAR_SELECTION` | `getCurrentGearSelectionState`(숨김, 구형)<br>`registerGearSelection`(숨김, 구형)<br>`unregisterGearSelection`(숨김, 구형) | 읽기·구독 | `VehicleGear?` (숨김 열거형) | `CAR_DRIVE` | normal | 있음: Gear Selection(P/R/N/D). SDK API는 숨김 | 미사용 — 숨김·사용 중단 예정 API. 기어는 [향후] |

### HVAC — 공조

- 얻는 방법: `vehicle.getHVAC()`. 인터페이스 메서드 86개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 14개, 숨김 0개.
- 실행 검사 권한: `CAR_CLIMATE` 73개, `CONTROL_CAR_CLIMATE` 13개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 에어컨<br>`HVAC_AC_ON` | `checkHvacAcCapabilities`(2개, 구형 1)<br>`setHvacAc`<br>`getHvacAcState`<br>`registerHvacAcState`<br>`unregisterHvacAcState` | 확인·읽기·구독·쓰기 | `Boolean?` | `CAR_CLIMATE` / 쓰기 `CONTROL_CAR_CLIMATE` | normal | 있음: Air Conditioning | 미사용 — 앱은 차량을 제어하지 않음 |
| 자동 공조<br>`HVAC_AUTO_ON` | `checkHvacAutoCapabilities`(2개, 구형 1)<br>`setHvacAuto`<br>`getHvacAutoState`<br>`registerHvacAutoState`<br>`unregisterHvacAutoState` | 확인·읽기·구독·쓰기 | `Boolean?` | `CAR_CLIMATE` / 쓰기 `CONTROL_CAR_CLIMATE` | normal | 있음: HVAC Auto | 미사용 — 앱은 차량을 제어하지 않음 |
| 내기 순환<br>`HVAC_RECIRC_ON` | `checkHvacRecirculationCapabilities`(2개, 구형 1)<br>`setHvacRecirculation`<br>`getHvacRecirculationState`<br>`registerHvacRecirculationState`<br>`unregisterHvacRecirculationState` | 확인·읽기·구독·쓰기 | `Boolean?` | `CAR_CLIMATE` / 쓰기 `CONTROL_CAR_CLIMATE` | normal | 있음: Air Recirculation | 미사용 — 앱은 차량을 제어하지 않음 |
| 성에 제거(창 영역)<br>`HVAC_DEFROSTER` | `checkHvacDefrosterCapabilities`(2개, 구형 1)<br>`setHvacDefroster`<br>`getHvacDefrosterState`<br>`registerHvacDefrosterState`<br>`unregisterHvacDefrosterState` | 확인·읽기·구독·쓰기 | `Boolean?` | `CAR_CLIMATE` / 쓰기 `CONTROL_CAR_CLIMATE` | normal | 있음: Defroster | 미사용 — 앱은 차량을 제어하지 않음 |
| 운전석·동승석 동기화<br>`HVAC_DUAL_ON` | `checkHvacDualCapabilities`(2개, 구형 1)<br>`setHvacDual`<br>`getHvacDualState`<br>`registerHvacDualState`<br>`unregisterHvacDualState` | 확인·읽기·구독·쓰기 | `Boolean?` | `CAR_CLIMATE` / 쓰기 `CONTROL_CAR_CLIMATE` | normal | 있음: HVAC Dual | 미사용 — 앱은 차량을 제어하지 않음 |
| 송풍 방향<br>`HVAC_FAN_DIRECTION` | `checkHvacFanDirectionCapabilities`(2개, 구형 1)<br>`setHvacFanDirection`<br>`getHvacFanDirection` | 확인·읽기·쓰기 | `FanDirectionType?` (8종) | `CAR_CLIMATE` / 쓰기 `CONTROL_CAR_CLIMATE` | normal | 있음: Fan Direction | 미사용 — 앱은 차량을 제어하지 않음 |
| 풍량<br>`HVAC_FAN_SPEED` | `checkHvacFanSpeedCapabilities`(2개, 구형 1)<br>`setHvacFanSpeed`<br>`getHvacFanSpeed`<br>`registerHvacFanSpeed`<br>`unregisterHvacFanSpeed` | 확인·읽기·구독·쓰기 | `Int?` 단계. 범위는 Capability | `CAR_CLIMATE` / 쓰기 `CONTROL_CAR_CLIMATE` | normal | 있음: Fan Speed(0~8단계) | 미사용 — 앱은 차량을 제어하지 않음 |
| 최대 냉방<br>`HVAC_MAX_AC_ON` | `checkHvacMaxAcCapabilities`(2개, 구형 1)<br>`setHvacMaxAc`<br>`getHvacMaxAcState`<br>`registerHvacMaxAcState`<br>`unregisterHvacMaxAcState` | 확인·읽기·구독·쓰기 | `Boolean?` | `CAR_CLIMATE` / 쓰기 `CONTROL_CAR_CLIMATE` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 최대 성에 제거<br>`HVAC_MAX_DEFROST_ON` | `checkHvacMaxDefrostCapabilities`(2개, 구형 1)<br>`setHvacMaxDefrost`<br>`getHvacMaxDefrostState`<br>`registerHvacMaxDefrostState`<br>`unregisterHvacMaxDefrostState` | 확인·읽기·구독·쓰기 | `Boolean?` | `CAR_CLIMATE` / 쓰기 `CONTROL_CAR_CLIMATE` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 공조 전원<br>`HVAC_POWER_ON` | `checkHvacPowerCapabilities`(2개, 구형 1)<br>`setHvacPower`<br>`getHvacPowerState`<br>`registerHvacPowerState`<br>`unregisterHvacPowerState` | 확인·읽기·구독·쓰기 | `Boolean?` | `CAR_CLIMATE` / 쓰기 `CONTROL_CAR_CLIMATE` | normal | 있음: HVAC Power | 미사용 — 앱은 차량을 제어하지 않음 |
| 자동 내기 순환<br>`HVAC_AUTO_RECIRC_ON` | `checkHvacAutoRecirculationCapabilities`(2개, 구형 1)<br>`setHvacAutoRecirculation`<br>`getHvacAutoRecirculationState`<br>`registerHvacAutoRecirculationState`<br>`unregisterHvacAutoRecirculationState` | 확인·읽기·구독·쓰기 | `Boolean?` | `CAR_CLIMATE` / 쓰기 `CONTROL_CAR_CLIMATE` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 공기 청정<br>`HVAC_AIR_CLEANER` | `checkHvacAirCleanerCapabilities`(2개, 구형 1)<br>`setHvacAirCleaner`<br>`getHvacAirCleanerState`<br>`registerHvacAirCleanerState`<br>`unregisterHvacAirCleanerState` | 확인·읽기·구독·쓰기 | `Boolean?` | `CAR_CLIMATE` / 쓰기 `CONTROL_CAR_CLIMATE` | normal | 있음: Air Cleaner | 미사용 — 앱은 차량을 제어하지 않음 |
| 목표 온도<br>`HVAC_TEMPERATURE_SET` | `checkHvacTemperatureCapabilities`(3개, 구형 2)<br>`getHvacTemperature`<br>`setHvacTemperature`<br>`registerHvacTemperature`(2개)<br>`unregisterHvacTemperature`(2개) | 확인·읽기·구독·쓰기 | `Float?` °C 또는 °F(인자로 지정) | `CAR_CLIMATE` / 쓰기 `CONTROL_CAR_CLIMATE` | normal | 있음: Target Temperature(16~30°C) | 미사용 — 앱은 차량을 제어하지 않음 |
| 실내 현재 온도<br>`HVAC_TEMPERATURE_CURRENT` | `checkHvacTemperatureCurrentCapabilities`<br>`getHvacTemperatureCurrent`(2개)<br>`registerHvacTemperatureCurrent`(2개)<br>`unregisterHvacTemperatureCurrent`(2개) | 확인·읽기·구독 | `Float?` °C(단위 인자 오버로드 있음) | `CAR_CLIMATE` | normal | 없음 | 미사용 |

### Light — 조명

- 얻는 방법: `vehicle.getLight()`. 인터페이스 메서드 78개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 7개, 숨김 0개.
- 실행 검사 권한: `CAR_LIGHTS` 67개, `CONTROL_CAR_LIGHTS` 11개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 독서등 스위치<br>`READING_LIGHTS_SWITCH` | `checkReadingLightSwitchCapabilities`(2개, 구형 1)<br>`getReadingLightSwitch`<br>`setReadingLightSwitch` | 확인·읽기·쓰기 | `LightSwitch?` | `CAR_LIGHTS` / 쓰기 `CONTROL_CAR_LIGHTS` | normal | 확인 필요(카드 Reading Lights) | 미사용 — 앱은 차량을 제어하지 않음 |
| 실내등 스위치<br>`CABIN_LIGHTS_SWITCH` | `checkCabinLightSwitchCapabilities`(2개, 구형 1)<br>`getCabinLightSwitch`<br>`setCabinLightSwitch` | 확인·읽기·쓰기 | `LightSwitch?` | `CAR_LIGHTS` / 쓰기 `CONTROL_CAR_LIGHTS` | normal | 있음: Cabin Lights Switch | 미사용 — 앱은 차량을 제어하지 않음 |
| 상향등 스위치<br>`HIGH_BEAM_LIGHTS_SWITCH` | `checkHighBeamLightSwitchCapabilities`(2개, 구형 1)<br>`getHighBeamLightSwitch`<br>`setHighBeamLightSwitch` | 확인·읽기·쓰기 | `LightSwitch?` | `CAR_LIGHTS` / 쓰기 `CONTROL_CAR_LIGHTS` | normal | 확인 필요(카드 High Beam은 실제 상태) | 미사용 — 앱은 차량을 제어하지 않음 |
| 하향등 스위치<br>`LOW_BEAM_LIGHTS_SWITCH` | `checkLowBeamLightSwitchCapabilities`(2개, 구형 1)<br>`setLowBeamLightSwitch`<br>`getLowBeamLightSwitch` | 확인·읽기·쓰기 | `LightSwitch?` | `CAR_LIGHTS` / 쓰기 `CONTROL_CAR_LIGHTS` | normal | 확인 필요(카드 Exterior Lights Mode) | 미사용 — 앱은 차량을 제어하지 않음 |
| 미등 스위치<br>`TAIL_LAMP_LIGHTS_SWITCH` | `checkTailLampLightSwitchCapabilities`(2개, 구형 1)<br>`getTailLampLightSwitch`<br>`setTailLampLightSwitch` | 확인·읽기·쓰기 | `LightSwitch?` | `CAR_LIGHTS` / 쓰기 `CONTROL_CAR_LIGHTS` | normal | 확인 필요(카드 Exterior Lights Mode) | 미사용 — 앱은 차량을 제어하지 않음 |
| 전방 안개등 스위치<br>`FRONT_FOG_LIGHTS_SWITCH` | `checkFrontFogLightSwitchCapabilities`(2개, 구형 1)<br>`getFrontFogLightSwitch`<br>`setFrontFogLightSwitch` | 확인·읽기·쓰기 | `LightSwitch?` | `CAR_LIGHTS` / 쓰기 `CONTROL_CAR_LIGHTS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 후방 안개등 스위치<br>`REAR_FOG_LIGHTS_SWITCH` | `checkRearFogLightSwitchCapabilities`(2개, 구형 1)<br>`getRearFogLightSwitch`<br>`setRearFogLightSwitch` | 확인·읽기·쓰기 | `LightSwitch?` | `CAR_LIGHTS` / 쓰기 `CONTROL_CAR_LIGHTS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 독서등 상태<br>`READING_LIGHTS_STATE` | `checkReadingLightStateCapabilities`<br>`getCurrentReadingLightState`<br>`registerReadingLightState`<br>`unregisterReadingLightState` | 확인·읽기·구독 | `LightState?` | `CAR_LIGHTS` | normal | 있음: Reading Lights | 미사용 |
| 실내등 상태<br>`CABIN_LIGHTS_STATE` | `checkCabinLightStateCapabilities`<br>`getCurrentCabinLightState`<br>`registerCabinLightState`<br>`unregisterCabinLightState` | 확인·읽기·구독 | `LightState?` | `CAR_LIGHTS` | normal | 확인 필요(카드 Cabin Lights Switch) | 미사용 |
| 상향등 상태<br>`HIGH_BEAM_LIGHTS_STATE` | `checkHighBeamLightStateCapabilities`<br>`getCurrentHighBeamLightState`<br>`registerHighBeamLightState`<br>`unregisterHighBeamLightState` | 확인·읽기·구독 | `LightState?` | `CAR_LIGHTS` | normal | 있음: High Beam | 미사용 |
| 하향등 상태<br>`LOW_BEAM_LIGHTS_STATE` | `checkLowBeamLightStateCapabilities`<br>`getCurrentLowBeamLightState`<br>`registerLowBeamLightState`<br>`unregisterLowBeamLightState` | 확인·읽기·구독 | `LightState?` | `CAR_LIGHTS` | normal | 있음: Exterior Lights Mode | 미사용 |
| 미등 상태<br>`TAIL_LAMP_LIGHTS_STATE` | `checkTailLampLightStateCapabilities`<br>`getCurrentTailLampLightState`<br>`registerTailLampLightState`<br>`unregisterTailLampLightState` | 확인·읽기·구독 | `LightState?` | `CAR_LIGHTS` | normal | 있음: Exterior Lights Mode | 미사용 |
| 전방 안개등 상태<br>`FRONT_FOG_LIGHTS_STATE` | `checkFrontFogLightStateCapabilities`<br>`getCurrentFrontFogLightState`<br>`registerFrontFogLightState`<br>`unregisterFrontFogLightState` | 확인·읽기·구독 | `LightState?` | `CAR_LIGHTS` | normal | 없음 | 미사용 |
| 후방 안개등 상태<br>`REAR_FOG_LIGHTS_STATE` | `checkRearFogLightStateCapabilities`<br>`getCurrentRearFogLightState`<br>`registerRearFogLightState`<br>`unregisterRearFogLightState` | 확인·읽기·구독 | `LightState?` | `CAR_LIGHTS` | normal | 없음 | 미사용 |
| 앰비언트 밝기<br>`INTERIOR_LIGHTS_AMBIENT_BRIGHTNESS` | `checkAmbientLightBrightnessCapabilities`<br>`getAmbientLightBrightness`<br>`setAmbientLightBrightness`<br>`registerAmbientLightBrightnessListener`<br>`unregisterAmbientLightBrightnessListener` | 확인·읽기·구독·쓰기 | `Int?`. 범위는 Capability | `CAR_LIGHTS` / 쓰기 `CONTROL_CAR_LIGHTS` (주의: `setAmbientLightBrightness`의 인터페이스 표시는 CAR_LIGHTS) | normal | 있음: Ambient Brightness(0~100%) | 미사용 — 앱은 차량을 제어하지 않음 |
| 앰비언트 색<br>`INTERIOR_LIGHTS_AMBIENT_BRIGHTNESS_COLOR` | `checkAmbientLightBrightnessColorCapabilities`<br>`getAmbientLightBrightnessColor`<br>`setAmbientLightBrightnessColor`<br>`registerAmbientLightBrightnessColorListener`<br>`unregisterAmbientLightBrightnessColorListener` | 확인·읽기·구독·쓰기 | `Int?` 32비트 ARGB | `CAR_LIGHTS` / 쓰기 `CONTROL_CAR_LIGHTS` (주의: `setAmbientLightBrightnessColor`의 인터페이스 표시는 CAR_LIGHTS) | normal | 있음: Ambient Color(4색) | 미사용 — 앱은 차량을 제어하지 않음 |
| 앰비언트 스위치<br>`INTERIOR_LIGHTS_AMBIENT_SWITCH` | `checkAmbientLightSwitchCapabilities`<br>`getAmbientLightSwitch`<br>`setAmbientLightSwitch` | 확인·읽기·쓰기 | `LightSwitch?` | `CAR_LIGHTS` / 쓰기 `CONTROL_CAR_LIGHTS` (주의: `setAmbientLightSwitch`의 인터페이스 표시는 CAR_LIGHTS) | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 앰비언트 상태<br>`INTERIOR_LIGHTS_AMBIENT_STATE` | `checkAmbientLightStateCapabilities`<br>`getAmbientLightState`<br>`registerAmbientLightStateListener`<br>`unregisterAmbientLightStateListener` | 확인·읽기·구독 | `LightState?` | `CAR_LIGHTS` | normal | 없음 | 미사용 |
| 주행 모드별 앰비언트 자동 색<br>`AMBIENT_COLOR_AUTO_BY_DRIVE_MODE_ENABLED` | `checkAmbientColorAutoByDriveModeEnabledCapabilities`<br>`getAmbientColorAutoByDriveModeEnabled`<br>`setAmbientColorAutoByDriveModeEnabled`<br>`registerAmbientColorAutoByDriveModeEnabled`<br>`unregisterAmbientColorAutoByDriveModeEnabled` | 확인·읽기·구독·쓰기 | `Boolean?` | `CAR_LIGHTS` / 쓰기 `CONTROL_CAR_LIGHTS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |

### Odometer — 주행거리·트립

- 얻는 방법: `vehicle.getOdometer()`. 인터페이스 메서드 38개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 8개, 숨김 3개.
- 실행 검사 권한: `CAR_DRIVE` 37개, `CONTROL_CAR_DRIVE` 1개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 누적 주행거리<br>`PERF_ODOMETER` | `checkCurrentDistanceDrivenCapabilities`<br>`getCurrentDistanceDriven`<br>`registerDistanceDriven`<br>`unregisterDistanceDriven` | 확인·읽기·구독 | `Float?` km | `CAR_DRIVE` | normal | 있음: Odometer(0~1,000,000km) | 사용 — F2 정비 노트·재발 확인, 스냅샷 [시연: VC] |
| 트립 주행 시간 지원 확인(통합)<br>`TRIP_DRIVING_TIME` | `checkTripDrivingTimeCapabilities` | 확인 | `CapabilityConfig` | `CAR_DRIVE` | normal | 없음 | 미사용 |
| 이번 주행 시간<br>`TRIP_DRIVING_TIME` | `checkTripCurrentDrivingTimeCapabilities`<br>`getTripCurrentDrivingTime`<br>`unregisterTripCurrentDrivingTime`<br>`registerTripCurrentDrivingTime` | 확인·읽기·구독 | `Int?` 분 | `CAR_DRIVE` | normal | 없음 | 미사용 |
| 누적 트립 시간<br>`TRIP_DRIVING_TIME` | `checkTripCumulativeDrivingTimeCapabilities`<br>`getTripCumulativeDrivingTime`<br>`unregisterTripCumulativeDrivingTime`<br>`registerTripCumulativeDrivingTime` | 확인·읽기·구독 | `Int?` 분 | `CAR_DRIVE` | normal | 없음 | 미사용 |
| 주유 후 주행 시간<br>`TRIP_DRIVING_TIME` | `checkTripSinceRefillDrivingTimeCapabilities`<br>`getTripSinceRefillDrivingTime`<br>`unregisterTripSinceRefillDrivingTime`<br>`registerTripSinceRefillDrivingTime` | 확인·읽기·구독 | `Int?` 분 | `CAR_DRIVE` | normal | 없음 | 미사용 |
| 트립 주행 거리 지원 확인(통합)<br>`TRIP_DRIVING_DISTANCE` | `checkTripDrivingDistanceCapabilities` | 확인 | `CapabilityConfig` | `CAR_DRIVE` | normal | 없음 | 미사용 |
| 이번 주행 거리<br>`TRIP_DRIVING_DISTANCE` | `checkTripCurrentDrivingDistanceCapabilities`<br>`getTripCurrentDrivingDistance`<br>`unregisterTripCurrentDrivingDistance`<br>`registerTripCurrentDrivingDistance` | 확인·읽기·구독 | `Float?` m | `CAR_DRIVE` | normal | 없음 | 미사용 |
| 순간 트립 거리(사용 중단 예정)<br>`TRIP_DRIVING_DISTANCE` | `checkTripInstantaneousDrivingDistanceCapabilities`(구형)<br>`getTripInstantaneousDrivingDistance`(구형)<br>`unregisterTripInstantaneousDrivingDistance`(구형)<br>`registerTripInstantaneousDrivingDistance`(구형) | 확인·읽기·구독 | `Float?` m | `CAR_DRIVE` | normal | 없음 | 미사용 |
| 누적 트립 거리<br>`TRIP_DRIVING_DISTANCE` | `checkTripCumulativeDrivingDistanceCapabilities`<br>`getTripCumulativeDrivingDistance`<br>`unregisterTripCumulativeDrivingDistance`<br>`registerTripCumulativeDrivingDistance` | 확인·읽기·구독 | `Float?` m | `CAR_DRIVE` | normal | 없음 | 미사용 |
| 주유 후 주행 거리<br>`TRIP_DRIVING_DISTANCE` | `checkTripSinceRefillDrivingDistanceCapabilities`<br>`getTripSinceRefillDrivingDistance`<br>`unregisterTripSinceRefillDrivingDistance`<br>`registerTripSinceRefillDrivingDistance` | 확인·읽기·구독 | `Float?` m | `CAR_DRIVE` | normal | 없음 | 미사용 |
| 주행 가능 거리(숨김)<br>`RANGE_REMAINING` | `setRangeRemainingDistance`(구형)<br>`getRangeRemainingDistance`(숨김, 구형)<br>`registerRangeRemainingListener`(숨김, 구형)<br>`unregisterRangeRemainingListener`(숨김, 구형) | 읽기·구독·쓰기 | `Float?` m. 설정은 항상 예외 | `CAR_DRIVE` / 쓰기 `CONTROL_CAR_DRIVE` | KDoc 없음 | 있음: Remaining Range(API는 숨김) | 미사용 — 숨김 API |

### Safety — 안전·경고

- 얻는 방법: `vehicle.getSafety()`. 인터페이스 메서드 39개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 2개, 숨김 0개.
- 실행 검사 권한: `CAR_SAFETY` 35개, `PRIVATE_CAR_SAFETY` 4개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 좌석별 에어백 상태<br>`SEAT_AIRBAG_ENABLED` | `checkAirbagCapabilities`(2개, 구형 1)<br>`hasSeatAirbagEnabled`<br>`registerSeatAirbagEnabled`<br>`unregisterSeatAirbagEnabled` | 확인·읽기·구독 | `Boolean?`. KDoc: true면 해당 좌석 에어백에 문제가 있음(이름과 뜻이 어긋남) | `CAR_SAFETY` | normal | 없음 | 후보 — F1 점검 항목. true의 뜻을 확인하기 전에는 표시만 |
| 좌석별 에어백 전개<br>`SEAT_AIRBAGS_DEPLOYED` | `checkAirbagDeployedCapabilities`<br>`hasSeatAirbagBeenDeployed`<br>`registerSeatAirbagDeployed`<br>`unregisterSeatAirbagDeployed` | 확인·읽기·구독 | `Boolean?` (true=전개) | `CAR_SAFETY` | normal | 없음 | 사용 — F4 발송 확인 10초 시작 신호 [시연: 시험 입력] |
| 안전벨트 체결 지원 확인(Safety 쪽)<br>`SEAT_BELT_BUCKLED` | `checkSeatBuckledCapabilities`(2개, 구형 1) | 확인 | `CapabilityConfig`, 좌석 목록 | `CAR_SAFETY` | normal | 없음 | 미사용 — Seat 쪽 체결 API를 씀 |
| 안전벨트 미체결 경고<br>`SEAT_BELT_NOT_BUCKLED_WARNING` | `checkSeatNotBuckledWarningCapabilities`<br>`hasSeatBeltNotBuckledWarning`<br>`registerSeatBeltNotBuckledWarning`<br>`unregisterSeatBeltNotBuckledWarning` | 확인·읽기·구독 | `Boolean?` (true=미체결) | `CAR_SAFETY` | normal | 없음(경고등 카드 SEAT_BELT는 텔테일) | 사용 — F1 점검 항목, F4 탑승자 확인 [시연: 시험 입력] |
| 엔진오일 수준<br>`ENGINE_OIL_LEVEL` | `checkEngineOilLevelStateCapabilities`<br>`getEngineOilLevelState`<br>`registerEngineOilLevelState`<br>`unregisterEngineOilLevelState` | 확인·읽기·구독 | `OilLevelState?` (LOW, HIGH, NORMAL, ERROR, CRITICALLY_LOW) | `CAR_SAFETY` | normal | 없음 | 사용 — F1 차가 알려 준 점검 항목 [시연: 시험 입력] |
| 브레이크액 수준<br>`BRAKE_FLUID_LEVEL` | `checkBrakeFluidLevelStateCapabilities`<br>`getBrakeFluidLevelState`<br>`registerBrakeFluidLevelState`<br>`unregisterBrakeFluidLevelState` | 확인·읽기·구독 | `OilLevelState?` | `CAR_SAFETY` | normal | 없음 | 사용 — F1 점검 항목 [시연: 시험 입력] |
| 변속기오일 수준<br>`TRANSMISSION_OIL_LEVEL` | `checkTransmissionOilLevelStateCapabilities`<br>`getTransmissionOilLevelState`<br>`registerTransmissionOilLevelState`<br>`unregisterTransmissionOilLevelState` | 확인·읽기·구독 | `OilLevelState?` | `CAR_SAFETY` | normal | 없음 | 후보 — F1 점검 항목 확장 |
| 워셔액 부족 경고<br>`WASHER_FLUID_LOW` | `checkLowWasherFluidWarningStateCapabilities`<br>`hasLowWasherFluidWarning`<br>`registerLowWasherFluidWaringState`<br>`unregisterLowWasherFluidWaringState` | 확인·읽기·구독 | `Boolean?` (true=문제 있음) | `CAR_SAFETY` | normal | 없음 | 사용 — F1 점검 항목 [시연: 시험 입력] |
| 출력 저하 경고<br>`POWER_DOWN_WARNING_INDICATOR` | `checkPowerDownWarningIndicatorCapabilities`<br>`hasPowerDownWarningIndicator`<br>`registerPowerDownWarningIndicator`<br>`unregisterPowerDownWarningIndicator` | 확인·읽기·구독 | `Boolean?` (true=엔진 문제) | `CAR_SAFETY` | normal | 없음 | 사용 — F1 점검 항목 [시연: 시험 입력] |
| 경고등(텔테일) 목록<br>`TELLTALES` | `checkTelltalesCapabilities`<br>`getTelltalesInfo`<br>`registerTelltales`<br>`unregisterTelltales` | 확인·읽기·구독 | `Telltales` (`TelltaleData` 목록: 종류·상태·색·우선순위·경고 횟수) | `PRIVATE_CAR_SAFETY` (주의: `getTelltalesInfo`의 인터페이스 표시는 CAR_SAFETY) | signature\|privileged | 있음: Telltales(Off/On/Blink). 일반 앱은 읽을 수 없음 | 미사용 [향후] — PRIVATE 권한 |

### Seat — 좌석

- 얻는 방법: `vehicle.getSeat()`. 인터페이스 메서드 101개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 15개, 숨김 0개.
- 실행 검사 권한: `CAR_SEATS` 87개, `CONTROL_CAR_SEATS` 14개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 좌석 점유<br>`SEAT_OCCUPANCY` | `checkSeatOccupancyCapabilities`(2개, 구형 1)<br>`getCurrentSeatOccupancy`<br>`registerSeatOccupancy`<br>`unregisterSeatOccupancy` | 확인·읽기·구독 | `Boolean?`. 서비스 값이 1일 때만 true(뜻은 VC로 확인 필요) | `CAR_SEATS` | normal | 있음: Seat Occupancy(Empty/Occupied) | 사용 — F4 탑승자 확인 [시연: VC] |
| 안전벨트 체결<br>`SEAT_BELT_BUCKLED` | `checkSeatBeltBuckledCapabilities`(2개, 구형 1)<br>`getCurrentSeatBeltBuckledState`<br>`registerSeatBeltBuckledState`<br>`unregisterSeatBeltBuckledState` | 확인·읽기·구독 | `Boolean?` (true=체결) | `CAR_SEATS` | normal | 없음 | 사용 — F4 탑승자 확인 [시연: 시험 입력] |
| 등받이 각도 위치<br>`SEAT_BACKREST_ANGLE_1_POS` | `checkSeatBackrestAngleCapabilities`(구형)<br>`checkSeatBackrestAnglePositionCapabilities`<br>`getCurrentSeatBackrestAnglePosition`<br>`registerSeatBackrestAnglePosition`<br>`unregisterSeatBackrestAnglePosition` | 확인·읽기·구독 | `Int?`. 범위는 Capability | `CAR_SEATS` | normal | 없음 | 미사용 |
| 머리받침 각도 위치<br>`SEAT_HEADREST_ANGLE_POS` | `checkSeatHeadrestAngleCapabilities`(구형)<br>`checkSeatHeadrestAnglePositionCapabilities`<br>`getCurrentSeatHeadrestAnglePosition`<br>`registerSeatHeadrestAnglePosition`<br>`unregisterSeatHeadrestAnglePosition` | 확인·읽기·구독 | `Int?` | `CAR_SEATS` | normal | 없음 | 미사용 |
| 머리받침 앞뒤 위치<br>`SEAT_HEADREST_FORE_AFT_POS` | `checkSeatHeadrestForeAftCapabilities`(구형)<br>`checkSeatHeadrestForeAftPositionCapabilities`<br>`getCurrentSeatHeadrestForeAftPosition`<br>`registerSeatHeadrestForeAftPosition`<br>`unregisterSeatHeadrestForeAftPosition` | 확인·읽기·구독 | `Int?` | `CAR_SEATS` | normal | 없음 | 미사용 |
| 머리받침 높이 위치<br>`SEAT_HEADREST_HEIGHT_POS_V2` | `checkSeatHeadrestHeightCapabilities`(구형)<br>`checkSeatHeadrestHeightPositionCapabilities`<br>`getCurrentSeatHeadrestHeightAftPosition`<br>`registerSeatHeadrestHeightAftPosition`<br>`unregisterSeatHeadrestHeightAftPosition` | 확인·읽기·구독 | `Int?` | `CAR_SEATS` | normal | 없음 | 미사용 |
| 좌석 높이 위치<br>`SEAT_HEIGHT_POS` | `checkSeatHeightCapabilities`(구형)<br>`checkSeatHeightPositionCapabilities`<br>`getCurrentSeatHeightPosition`<br>`registerSeatHeightPosition`<br>`unregisterSeatHeightPosition` | 확인·읽기·구독 | `Int?` | `CAR_SEATS` | normal | 없음 | 미사용 |
| 허리받침 앞뒤 위치<br>`SEAT_LUMBAR_FORE_AFT_POS` | `checkSeatLumbarForeAftCapabilities`(구형)<br>`checkSeatLumbarForeAftPositionCapabilities`<br>`getCurrentSeatLumbarForeAftPosition`<br>`registerSeatLumbarForeAftPosition`<br>`unregisterSeatLumbarForeAftPosition` | 확인·읽기·구독 | `Int?` | `CAR_SEATS` | normal | 없음 | 미사용 |
| 허리 옆 지지 위치<br>`SEAT_LUMBAR_SIDE_SUPPORT_POS` | `checkSeatLumbarSideSupportCapabilities`(구형)<br>`checkSeatLumbarSideSupportPositionCapabilities`<br>`getCurrentSeatLumbarSideSupportPosition`<br>`registerSeatLumbarSideSupportPosition`<br>`unregisterSeatLumbarSideSupportPosition` | 확인·읽기·구독 | `Int?` | `CAR_SEATS` | normal | 없음 | 미사용 |
| 좌석 기울기 위치<br>`SEAT_TILT_POS` | `checkSeatTiltCapabilities`(구형)<br>`checkSeatTiltPositionCapabilities`<br>`getCurrentSeatTiltPosition`<br>`registerSeatTiltPosition`<br>`unregisterSeatTiltPosition` | 확인·읽기·구독 | `Int?` | `CAR_SEATS` | normal | 없음 | 미사용 |
| 좌석 깊이 위치<br>`SEAT_DEPTH_POS` | `checkSeatDepthCapabilities`(구형)<br>`checkSeatDepthPositionCapabilities`<br>`getCurrentSeatDepthPosition`<br>`registerSeatDepthPosition`<br>`unregisterSeatDepthPosition` | 확인·읽기·구독 | `Int?` | `CAR_SEATS` | normal | 없음 | 미사용 |
| 좌석 앞뒤 위치<br>`SEAT_FORE_AFT_POS` | `checkSeatForeAftCapabilities`(구형)<br>`checkSeatForeAftPositionCapabilities`<br>`getCurrentSeatForeAftPosition`<br>`registerSeatForeAftPosition`<br>`unregisterSeatForeAftPosition` | 확인·읽기·구독 | `Int?` | `CAR_SEATS` | normal | 없음 | 미사용 |
| 안전벨트 높이 위치<br>`SEAT_BELT_HEIGHT_POS` | `checkSeatBeltHeightCapabilities`(구형)<br>`checkSeatBeltHeightPositionCapabilities`<br>`getCurrentSeatBeltHeightPosition`<br>`registerSeatBeltHeightPosition`<br>`unregisterSeatBeltHeightPosition` | 확인·읽기·구독 | `Int?` | `CAR_SEATS` | normal | 없음 | 미사용 |
| 좌석 열선·냉방 단계<br>`HVAC_SEAT_TEMPERATURE` | `checkSeatTemperatureCapabilities`(2개, 구형 1)<br>`setHvacSeatTemperature`<br>`getHvacSeatTemperature`<br>`registerHvacSeatTemperature`<br>`unregisterHvacSeatTemperature` | 확인·읽기·구독·쓰기 | `Int?` 단계 | `CAR_SEATS` / 쓰기 `CONTROL_CAR_SEATS` | normal | 있음: Seat Temperature | 미사용 — 앱은 차량을 제어하지 않음 |
| 좌석 통풍 단계<br>`HVAC_SEAT_VENTILATION` | `checkSeatVentilationCapabilities`(2개, 구형 1)<br>`getHvacSeatVentilationState`<br>`setHvacSeatVentilation`<br>`registerHvacSeatVentilation`<br>`unregisterHvacSeatVentilation` | 확인·읽기·구독·쓰기 | `Int?` 단계 | `CAR_SEATS` / 쓰기 `CONTROL_CAR_SEATS` | normal | 확인 필요(카드 Seat Temperature에 통풍 단계 포함) | 미사용 — 앱은 차량을 제어하지 않음 |
| 등받이 각도 움직이기<br>`SEAT_BACKREST_ANGLE_1_MOVE` | `checkMoveSeatBackrestAngleCapabilities`<br>`moveSeatBackrestAngle` | 확인·쓰기 | `MoveControl` (BACKWARD, STOP, FORWARD) | `CAR_SEATS` / 쓰기 `CONTROL_CAR_SEATS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 머리받침 앞뒤 움직이기<br>`SEAT_HEADREST_FORE_AFT_MOVE` | `checkMoveSeatHeadrestForeAftCapabilities`<br>`moveSeatHeadrestForeAft` | 확인·쓰기 | `MoveControl` | `CAR_SEATS` / 쓰기 `CONTROL_CAR_SEATS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 안전벨트 높이 움직이기<br>`SEAT_BELT_HEIGHT_MOVE` | `checkMoveSeatBeltHeightCapabilities`<br>`moveSeatBeltHeight` | 확인·쓰기 | `MoveControl` | `CAR_SEATS` / 쓰기 `CONTROL_CAR_SEATS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 머리받침 높이 움직이기<br>`SEAT_HEADREST_HEIGHT_MOVE` | `checkMoveSeatHeadrestHeightCapabilities`<br>`moveSeatHeadrestHeight` | 확인·쓰기 | `MoveControl` | `CAR_SEATS` / 쓰기 `CONTROL_CAR_SEATS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 좌석 높이 움직이기<br>`SEAT_HEIGHT_MOVE` | `checkMoveSeatHeightCapabilities`<br>`moveSeatHeight` | 확인·쓰기 | `MoveControl` | `CAR_SEATS` / 쓰기 `CONTROL_CAR_SEATS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 허리받침 앞뒤 움직이기<br>`SEAT_LUMBAR_FORE_AFT_MOVE` | `checkMoveSeatLumbarForeAftCapabilities`<br>`moveSeatLumbarForeAft` | 확인·쓰기 | `MoveControl` | `CAR_SEATS` / 쓰기 `CONTROL_CAR_SEATS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 허리 옆 지지 움직이기<br>`SEAT_LUMBAR_SIDE_SUPPORT_MOVE` | `checkMoveSeatLumbarSideSupportCapabilities`<br>`moveSeatLumbarSideSupport` | 확인·쓰기 | `MoveControl` | `CAR_SEATS` / 쓰기 `CONTROL_CAR_SEATS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 좌석 기울기 움직이기<br>`SEAT_TILT_MOVE` | `checkMoveSeatTiltCapabilities`<br>`moveSeatTilt` | 확인·쓰기 | `MoveControl` | `CAR_SEATS` / 쓰기 `CONTROL_CAR_SEATS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 좌석 깊이 움직이기<br>`SEAT_DEPTH_MOVE` | `checkMoveSeatDepthCapabilities`<br>`moveSeatDepth` | 확인·쓰기 | `MoveControl` | `CAR_SEATS` / 쓰기 `CONTROL_CAR_SEATS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 머리받침 각도 움직이기<br>`SEAT_HEADREST_ANGLE_MOVE` | `checkMoveSeatHeadrestAngleCapabilities`<br>`moveSeatHeadrestAngle` | 확인·쓰기 | `MoveControl` | `CAR_SEATS` / 쓰기 `CONTROL_CAR_SEATS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 좌석 앞뒤 움직이기<br>`SEAT_FORE_AFT_MOVE` | `checkMoveSeatForeAftCapabilities`<br>`moveSeatForeAft` | 확인·쓰기 | `MoveControl` | `CAR_SEATS` / 쓰기 `CONTROL_CAR_SEATS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 좌석 메모리 불러오기<br>`SEAT_MEMORY_SELECT` | `checkSeatMemoryCapabilities`<br>`selectSeatMemory` | 확인·쓰기 | `Int` 메모리 번호(완료 콜백 없음) | `CAR_SEATS` / 쓰기 `CONTROL_CAR_SEATS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |

### SideMirror — 사이드미러

- 얻는 방법: `vehicle.getSideMirror()`. 인터페이스 메서드 6개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 1개, 숨김 0개.
- 실행 검사 권한: `CAR_MIRRORS` 5개, `CONTROL_CAR_MIRRORS` 1개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 사이드미러 열선<br>`HVAC_SIDE_MIRROR_HEAT` | `checkHvacSideMirrorHeatCapabilities`(2개, 구형 1)<br>`getHvacSideMirrorHeatState`<br>`setHvacSideMirrorHeat`<br>`registerHvacSideMirrorHeatState`<br>`unregisterHvacSideMirrorHeatState` | 확인·읽기·구독·쓰기 | `Int?` 단계 | `CAR_MIRRORS` / 쓰기 `CONTROL_CAR_MIRRORS` (주의: `registerHvacSideMirrorHeatState`, `unregisterHvacSideMirrorHeatState`의 인터페이스 표시는 CAR_CLIMATE) | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |

### SteeringWheel — 조향각·핸들 열선

- 얻는 방법: `vehicle.getSteeringWheel()`. 인터페이스 메서드 10개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 1개, 숨김 0개.
- 실행 검사 권한: `CAR_CLIMATE` 5개, `CAR_DRIVE` 4개, `CONTROL_CAR_CLIMATE` 1개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 핸들 열선<br>`HVAC_STEERING_WHEEL_HEAT` | `checkHvacSteeringWheelHeatCapabilities`(2개, 구형 1)<br>`getHvacSteeringWheelHeatState`<br>`setHvacSteeringWheelHeat`<br>`registerHvacSteeringWheelHeatState`<br>`unregisterHvacSteeringWheelHeatState` | 확인·읽기·구독·쓰기 | `Int?` 단계 | `CAR_CLIMATE` / 쓰기 `CONTROL_CAR_CLIMATE` | normal | 있음: Steering Wheel Heat | 미사용 — 앱은 차량을 제어하지 않음 |
| 조향각(전륜)<br>`PERF_STEERING_ANGLE` | `checkSteeringWheelAngleCapabilities`<br>`getCurrentSteeringWheelAngle`<br>`registerSteeringWheelAngle`<br>`unregisterSteeringWheelAngle` | 확인·읽기·구독 | `Float?` 도(°). 왼쪽이 음수. 핸들 회전각이 아님 | `CAR_DRIVE` | normal | 있음: Steering Angle(-540~540도, 카드 설명은 “스티어링 휠 각도”) | 사용 — F2 재현 조건 카드 [시연: VC] |

### Tire — 타이어

- 얻는 방법: `vehicle.getTire()`. 인터페이스 메서드 7개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 1개, 숨김 4개.
- 실행 검사 권한: `CAR_SAFETY` 3개, `PRIVATE_CAR_SAFETY` 4개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 타이어 저압 기준값<br>`CRITICALLY_LOW_TIRE_PRESSURE` | `checkWheelAreaCapabilities`(구형)<br>`checkLowTirePressureCapabilities`<br>`getLowTirePressure` | 확인·읽기 | `Float?` kPa. 현재 공기압이 아닌 기준값 | `CAR_SAFETY` | normal | 없음 | 미사용 — 현재 공기압이 아님 |
| 타이어 현재 공기압(숨김)<br>`TIRE_PRESSURE` | `checkTirePressureCapabilities`(숨김)<br>`getTirePressure`(숨김)<br>`registerTirePressure`(숨김)<br>`unregisterTirePressure`(숨김) | 확인·읽기·구독 | `Float?` kPa | `PRIVATE_CAR_SAFETY` | signature\|privileged | 없음 | 미사용 [향후] — 숨김·PRIVATE 권한 |

### Toll — 하이패스(ETCS)

- 얻는 방법: `vehicle.getToll()`. 인터페이스 메서드 5개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 0개, 숨김 0개.
- 실행 검사 권한: `CAR_TOLL` 4개, `CONTROL_CAR_TOLL` 1개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 하이패스 안내 음량<br>`ETCS_VOICE_GUIDANCE_VOLUME` | `checkEtcsVoiceGuidanceVolumeCapabilities`<br>`getCurrentEtcsVoiceGuidanceVolume`<br>`setEtcsVoiceGuidanceVolume`<br>`registerEtcsVoiceGuidanceVolume`<br>`unregisterEtcsVoiceGuidanceVolume` | 확인·읽기·구독·쓰기 | `Int?` | `CAR_TOLL` / 쓰기 `CONTROL_CAR_TOLL` | normal | 없음(ETCS Power·Scenario 카드는 다른 기능) | 미사용 — 앱은 차량을 제어하지 않음 |

### TurnSignal — 방향지시등

- 얻는 방법: `vehicle.getTurnSignal()`. 인터페이스 메서드 4개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 0개, 숨김 0개.
- 실행 검사 권한: `CAR_LIGHTS` 4개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 방향지시등<br>`TURN_SIGNAL_STATE` | `checkTurnSignalStateCapabilities`<br>`getCurrentTurnSignalState`<br>`registerTurnSignalState`<br>`unregisterTurnSignalState` | 확인·읽기·구독 | `TurnSignalValue?` (OFF, RIGHT, LEFT). 비상등 값 없음 | `CAR_LIGHTS` | normal | 있음: Turn Signal State | 사용 — F2 스냅샷 [시연: VC] |

### Window — 창문·스마트글라스

- 얻는 방법: `vehicle.getWindow()`. 인터페이스 메서드 31개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 2개, 숨김 0개.
- 실행 검사 권한: `CAR_WINDOWS` 25개, `CONTROL_CAR_WINDOWS` 6개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 창 블라인드 움직이기<br>`WINDOW_BLIND_MOVE` | `checkMoveWindowBlindCapabilities`<br>`moveWindowBlind` | 확인·쓰기 | `MoveControl` | `CAR_WINDOWS` / 쓰기 `CONTROL_CAR_WINDOWS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 창 블라인드 위치<br>`WINDOW_BLIND_POS` | `checkWindowBlindPositionCapabilities`<br>`getCurrentWindowBlindPosition`<br>`registerWindowBlindPosition`<br>`unregisterWindowBlindPosition` | 확인·읽기·구독 | `Int?` | `CAR_WINDOWS` | normal | 없음 | 미사용 |
| 스마트글라스 켜짐<br>`SMART_GLASS_ON` | `checkSmartGlassStateCapabilities`<br>`getCurrentSmartGlassState`<br>`setSmartGlassState`<br>`registerSmartGlassState`<br>`unregisterSmartGlassState` | 확인·읽기·구독·쓰기 | `Boolean?` | `CAR_WINDOWS` / 쓰기 `CONTROL_CAR_WINDOWS` | normal, `setSmartGlassState`만 signature\|privileged | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 스마트글라스 구역 켜기<br>`SMART_GLASS_ZONE_ON` | `checkSmartGlassZoneStateCapabilities`<br>`setSmartGlassZoneState` | 확인·쓰기 | `List<SmartGlassZoneCommand>` | `CAR_WINDOWS` / 쓰기 `CONTROL_CAR_WINDOWS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 스마트글라스 단계<br>`SMART_GLASS_LEVEL` | `checkSmartGlassLevelCapabilities`<br>`getCurrentSmartGlassLevel`<br>`setSmartGlassLevel`<br>`registerSmartGlassLevel`<br>`unregisterSmartGlassLevel` | 확인·읽기·구독·쓰기 | `Int?` | `CAR_WINDOWS` / 쓰기 `CONTROL_CAR_WINDOWS` | normal | 없음 | 미사용 — 앱은 차량을 제어하지 않음 |
| 창문 잠금<br>`WINDOW_LOCK` | `checkWindowLockCapabilities`(2개, 구형 1)<br>`setWindowLock`<br>`getCurrentWindowLockState`<br>`registerWindowLockState`<br>`unregisterWindowLockState` | 확인·읽기·구독·쓰기 | `Boolean?` | `CAR_WINDOWS` / 쓰기 `CONTROL_CAR_WINDOWS` | normal | 있음: Window Lock | 미사용 — 앱은 차량을 제어하지 않음 |
| 창문 위치<br>`WINDOW_POS` | `checkWindowPositionCapabilities`(2개, 구형 1)<br>`getCurrentWindowPosition`<br>`setWindowPosition`<br>`registerWindowPosition`<br>`unregisterWindowPosition` | 확인·읽기·구독·쓰기 | `Int?`. 범위는 Capability | `CAR_WINDOWS` / 쓰기 `CONTROL_CAR_WINDOWS` | normal | 있음: Window Position(0~100%) | 미사용 — 앱은 차량을 제어하지 않음 |
| 스마트글라스 구성<br>`SMART_GLASS_CONFIG` | `getSmartGlassConfig` | 읽기 | `SmartGlassConfig?` (구역 수·단계 수) | `CAR_WINDOWS` | normal | 없음 | 미사용 |

### Wiper — 와이퍼

- 얻는 방법: `vehicle.getWiper()`. 인터페이스 메서드 9개(오버로드·구형·숨김 포함). 이 중 구형(Deprecated) 2개, 숨김 0개.
- 실행 검사 권한: `CAR_WINDOWS` 8개, `CONTROL_CAR_WINDOWS` 1개

| 기능(속성) | 메서드 | 종류 | 값·단위 | 권한(실행 검사) | 보호 수준 | Virtual Console | 플레오스 가드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 와이퍼 동작 상태<br>`WINDSHIELD_WIPERS_STATE` | `checkWiperStateCapabilities`(2개, 구형 1)<br>`getCurrentWiperState`<br>`registerWiperState`<br>`unregisterWiperState` | 확인·읽기·구독 | `WiperState?` | `CAR_WINDOWS` | normal | 확인 필요(카드 Windshield Wiper Switch) | 미사용 |
| 와이퍼 스위치<br>`WINDSHIELD_WIPERS_SWITCH` | `checkWiperSwitchCapabilities`(2개, 구형 1)<br>`getCurrentWiperSwitch`<br>`setWiperSwitch` | 확인·읽기·쓰기 | `WiperSwitch?` | `CAR_WINDOWS` / 쓰기 `CONTROL_CAR_WINDOWS` | normal | 있음: Windshield Wiper Switch | 미사용 — 앱은 차량을 제어하지 않음 |

## 5. 경고등(텔테일) 상수 95종

**일반 앱은 차량의 경고등 상태를 읽을 수 없습니다.** 경고등 API 4개(`checkTelltalesCapabilities`, `getTelltalesInfo`, `registerTelltales`, `unregisterTelltales`)는 실행할 때 모두 `PRIVATE_CAR_SAFETY`(보호 수준 signature|privileged)를 검사합니다(`api/implement/SafetyImpl.kt`의 194·335·542·553행). `getTelltalesInfo`의 인터페이스 표시만 `CAR_SAFETY`인데, 이것은 개발 도구용 표시이고 KDoc에도 `PRIVATE_CAR_SAFETY`라고 적혀 있습니다. Sample App 가이드도 `PRIVATE_*` 권한을 예제에 넣지 않는다고 밝힙니다.

- 에뮬레이터 Virtual Console의 Telltales 카드로 경고등을 켜고 깜박이게 할 수는 있습니다. 그래도 플레오스 가드는 그 상태를 읽지 못합니다. 경고등 상태 읽기는 [향후]입니다.
- 아래 상수 이름은 공개 열거형(`constant/telltale/TelltaleType.kt`)이라 권한 없이 참조할 수 있습니다. 경고등 도감의 ID 체계를 정할 때 참고만 합니다. 숫자 id는 SDK 내부(`internal`) 값입니다.
- 원문에는 상수별 설명이 없습니다. 오른쪽 해석은 상수 이름에서 추정한 것이므로, 도감에 쓰기 전에 대상 차량 취급설명서와 대조합니다.
- 자료 모양(참고): `TelltaleData(type, state, color, priority, warningCount)`. `state`는 `OFF`, `ON`, `BLINK`이고 `color`는 정수(0 흰색, 1 초록, 2 파랑, 3 노랑, 4 빨강, 5 회색)입니다.
- id 범위: 0~36 기본 경고·표시등 37개, 100~121 동력·연료·보조 22개, 200~236 주행 모드 28개, 300~306 운전자 보조 7개, 320 운전자 주의 1개.

| id | 상수 | 참고 해석(상수 이름에서 추정, 원문 설명 없음) |
| ---: | --- | --- |
| 0 | `PARKING_BRAKE` | 주차 브레이크 |
| 1 | `ELECTRONIC_PARKING_BRAKE` | 전자식 주차 브레이크(EPB) |
| 2 | `SEAT_BELT` | 안전벨트 |
| 3 | `DOOR_OPEN` | 도어 열림 |
| 4 | `ABS` | ABS |
| 5 | `MDPS` | 전동식 조향(MDPS) |
| 6 | `TPMS` | 타이어 공기압(TPMS) |
| 7 | `LOW_VOLT_BATTERY` | 저전압(보조) 배터리 |
| 8 | `ESC` | 차체자세제어(ESC) |
| 9 | `ESC_OFF` | ESC OFF |
| 10 | `AIRBAG` | 에어백 |
| 11 | `AFS` | 적응형 전조등(AFS) |
| 12 | `MASTER_WARNING` | 마스터 경고 |
| 13 | `AUTO_HOLD` | 오토 홀드 |
| 14 | `AUTO_HIGH_BEAM` | 오토 하이빔 |
| 15 | `HEADLAMP_LOW` | 하향등 |
| 16 | `HEADLAMP_HIGH` | 상향등 |
| 17 | `POSITION_LAMP` | 미등(포지션 램프) |
| 18 | `FRONT_FOG_LAMP` | 전방 안개등 |
| 19 | `REAR_FOG_LAMP` | 후방 안개등 |
| 20 | `ENGINE_OIL_PRESSURE` | 엔진 오일 압력 |
| 21 | `ENGINE_TEMPERATURE` | 엔진(냉각수) 온도 |
| 22 | `EMERGENCY_CALL` | 긴급 호출 |
| 23 | `EV_SYSTEM` | EV 시스템 |
| 24 | `POWER_DOWN` | 출력 제한 |
| 25 | `RBS` | RBS(회생제동 계열 약어로 추정) |
| 26 | `IMMOBILIZER` | 이모빌라이저 |
| 27 | `ENGINE` | 엔진(엔진 점검) |
| 28 | `LED_HEADLAMP` | LED 헤드램프 |
| 29 | `PREHEAT` | 예열 |
| 30 | `PARTICLE_FILTER` | 배기 미립자 필터 |
| 31 | `H2_SENSOR` | 수소 센서 |
| 32 | `H2_LEAK` | 수소 누출 |
| 33 | `PASSENGER_AIRBAG_OFF` | 동승석 에어백 OFF |
| 34 | `PASSENGER_AIRBAG_ON` | 동승석 에어백 ON |
| 35 | `TURN_SIGNAL_LEFT` | 좌측 방향지시등 |
| 36 | `TURN_SIGNAL_RIGHT` | 우측 방향지시등 |
| 100 | `ICY_ROAD` | 노면 결빙 |
| 101 | `OVER_SPEED` | 과속 |
| 102 | `WATER_SEPARATOR` | 연료 수분 분리기 |
| 103 | `SCR` | SCR(요소수 계열 약어로 추정) |
| 104 | `LOW_EV_BATTERY` | 구동 배터리 부족 |
| 105 | `HEV_SYSTEM` | 하이브리드 시스템 |
| 106 | `FCEV_SYSTEM` | 수소전기차 시스템 |
| 107 | `EREV_SYSTEM` | EREV 시스템 |
| 108 | `LPI` | LPI |
| 109 | `EV_CHARGING_CONNECTOR` | 충전 커넥터 |
| 110 | `AWD_LOCK` | AWD 잠금 |
| 111 | `HEV_MODE` | HEV 모드 |
| 112 | `ISG` | ISG |
| 113 | `DBC` | DBC(경사로 저속 주행 계열 약어로 추정) |
| 114 | `REV` | REV(의미 미기재) |
| 115 | `XEV_READY` | xEV 주행 준비(READY) |
| 116 | `I_PEDAL` | i-PEDAL |
| 117 | `LOW_FUEL` | 연료 부족 |
| 118 | `LOW_FUEL_FCEV` | 수소 연료 부족 |
| 119 | `EV_BATTERY_CONDITIONING` | 배터리 컨디셔닝 |
| 120 | `TRUNK_OPEN` | 트렁크 열림 |
| 121 | `CHILD_PRESENCE_DETECTION_OFF` | 어린이 감지 OFF |
| 200 | `DRIVE_MODE` | 주행 모드 |
| 201 | `DRIVE_MODE_SPORT` | 주행 모드 스포츠 |
| 202 | `DRIVE_MODE_ECO` | 주행 모드 에코 |
| 203 | `DRIVE_MODE_COMFORT` | 주행 모드 컴포트 |
| 204 | `DRIVE_MODE_TERRAIN` | 주행 모드 지형 |
| 210 | `FORCED_ALL_WHEEL_DRIVE` | 강제 AWD |
| 211 | `FOUR_WHEEL_DRIVE_MODE_2H` | 4WD 2H |
| 212 | `FOUR_WHEEL_DRIVE_MODE_4A` | 4WD 4A |
| 213 | `FOUR_WHEEL_DRIVE_MODE_4H` | 4WD 4H |
| 214 | `FOUR_WHEEL_DRIVE_MODE_4L` | 4WD 4L |
| 219 | `TOW_MODE` | 견인 모드 |
| 220 | `ESC_SPORT` | ESC 스포츠 |
| 221 | `CREEP_OFF` | 크리프 OFF |
| 222 | `DRIFT_MODE` | 드리프트 모드 |
| 223 | `TRACK_MODE` | 트랙 모드 |
| 224 | `UTILITY_MODE` | 유틸리티 모드 |
| 225 | `GREEN_ZONE` | 그린 존 |
| 226 | `PHEV_MODE` | PHEV 모드 |
| 227 | `PHEV_MODE_HEV` | PHEV 모드 HEV |
| 228 | `PHEV_MODE_AUTO` | PHEV 모드 AUTO |
| 229 | `PHEV_MODE_EV_PLUS` | PHEV 모드 EV+ |
| 230 | `EREV_MODE` | EREV 모드 |
| 231 | `LAUNCH_MODE` | 런치 모드 |
| 232 | `ACCEL_RESPONSE_DASH` | 가속 응답 DASH |
| 233 | `ACCEL_RESPONSE_ACCEL` | 가속 응답 ACCEL |
| 234 | `N_BATTERY_DRAG` | N 배터리 DRAG |
| 235 | `N_BATTERY_SPRINT` | N 배터리 SPRINT |
| 236 | `N_BATTERY_ENDURANCE` | N 배터리 ENDURANCE |
| 300 | `FCA` | FCA(전방 충돌 계열 ADAS 약어) |
| 301 | `LKA` | LKA(차로 이탈 계열) |
| 302 | `LFA` | LFA(차로 유지 계열) |
| 303 | `LCA_LEFT` | LCA 좌 |
| 304 | `LCA_RIGHT` | LCA 우 |
| 305 | `ELK` | ELK |
| 306 | `ICC` | ICC |
| 320 | `DRIVER_ATTENTION` | 운전자 주의 |

## 6. 공개 Vehicle SDK로 읽을 수 없는 것

| 필요한 정보 | 공개 Vehicle SDK 2.3.2 | 대신 쓸 수 있는 경로 | 플레오스 가드 등급 |
| --- | --- | --- | --- |
| 실제 차속 | 없음. 2.3.2에서 `VehicleSpeedListener`와 `onSpeedUpdated`가 삭제됨 | Pleos FAQ가 안내하는 AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED`(m/s, 권한 `android.car.permission.CAR_SPEED`). 다섯 SDK 목록 밖의 경로라 허용 범위는 기술 문의 대상 | [시연: 시험 입력]. Virtual Console에 차속 카드 없음 |
| 기어 | `Gear` 도메인 전체가 숨김·사용 중단 예정 | Pleos 정책 문서가 기어 같은 표준 데이터는 AAOS Car API로 받으라고 안내 | [향후] |
| 시동 상태, 주행·정차 판단 | `IgnitionState` 열거형만 있고 API 없음(숨김) | 주행 제한은 AAOS `CarUxRestrictions` | 주행 제한만 사용 |
| 가속 페달, 브레이크 압력·% | 없음. 브레이크는 밟음 여부(Boolean)만 | 없음 | [향후] |
| 주차 브레이크 | 없음(공식 소개 문서의 설명과 다름). 경고등 상수에만 있음 | 없음 | [향후] |
| 비상등 | 없음. `TurnSignalValue`는 OFF, RIGHT, LEFT뿐 | Virtual Console에는 Hazard Lights 카드가 있으나 SDK로 읽을 수 없음 | [향후] |
| 타이어 현재 공기압 | 숨김·`PRIVATE_CAR_SAFETY` | 공개는 저압 기준값(kPa)만 | [향후] |
| 충격·가속도·기울기 | 없음 | 없음(일부 센서는 차량에서 제한될 수 있다고 안내됨) | [향후] |
| 경고등 상태 | `PRIVATE_CAR_SAFETY` | "같은 원인의 공개 신호"(엔진오일·브레이크액·워셔액·출력 저하·안전벨트·도어)만 사용 | [향후] |
| 12V 배터리, 차종·연식·크기 | 없음(경고등 상수 `LOW_VOLT_BATTERY`만) | 없음 | 미사용 |

## 7. 근거

- 배포 파일: [Vehicle 2.3.2 AAR](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2.aar), [POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2.pom), 같은 경로의 `Vehicle-2.3.2-sources.jar`, `Vehicle-2.3.2-javadoc.jar`. SHA-256은 [확인 기록](evidence/sdk-followup-2026-11-12.json)에 있습니다.
- 소스 위치(소스 묶음 안): `api/*.kt`(인터페이스), `api/implement/*Impl.kt`(실행 권한), `constant/PleosPermission.kt`(권한 33개), `constant/telltale/TelltaleType.kt`, `listener/*.kt`, `service/VehicleServiceManager.kt`, `service/BaseServiceConnector.kt`, `utils/PermissionUtil.kt`.
- 공식 문서: [SDK 릴리즈 노트](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note), [차량 앱 FAQ](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq), [SDK API 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api), [Vehicle 권한 안내](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission), [Sample App 가이드](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app), [Virtual Console 사용 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide).
- 공식 API 참조의 각 메서드 페이지는 제목만 있고 본문이 없습니다. 그래서 단위·의미는 소스 묶음의 KDoc을 근거로 했습니다.

## 8. 갱신 기록

| 날짜 | 내용 |
| --- | --- |
| 2026-11-12 | 처음 작성. 517개 메서드를 126개 속성 묶음으로 정리하고, 실행 권한·보호 수준·Virtual Console 카드·플레오스 가드 사용 여부를 붙였습니다. 실행 시험은 하지 않았습니다. |
