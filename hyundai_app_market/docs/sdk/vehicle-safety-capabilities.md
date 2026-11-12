# 차량 안전 아이디어에 필요한 SDK 기능 조사

조사일: **2026-11-12, Asia/Seoul**.

목적은 이상 가속 대응, 페달 착각 예방, 사고 전후 기록에 필요한 차량·음성·위치·센서 기능의 제공 범위를 확인하는 것이다. 사용자 요청에 따라 내부·외부 마이크와 기울기도 포함한다. 이용 장면과 화면은 [세 가지 안전 앱 기획(통합 전 이력)](../analysis/vehicle-safety-app-ideas-2026-11-12.md)에 정리했다. 이 기획은 이후 통합 앱으로 대체됐으며 현재 기획은 [현재 통합 PRD](../analysis/integrated-vehicle-care-2026-11-12.md)를 따른다.

**2026-11-12 후속 확인 안내:** Vehicle 2.3.2 소스 전수 판독으로 이 문서의 일부 판단을 정정했다. 경고 표시 권한, 핸들 각도 명칭, 위치 정확도, 64dp 표현이 해당한다. 정정 표시는 각 위치에 붙였고, 전체 결과는 [11절](#11-2026-11-12-후속-확인-공개-안전-신호와-정정)과 [Vehicle API 전체 목록](vehicle-api-catalog.md)에 있다.

이 문서는 [종합 SDK 리뷰](../analysis/pleos-sdk-review.md)의 안전 기능 상세 보충 자료다. 전체 SDK의 허용 여부나 기존 리뷰를 대체하는 별도 기준 문서로 사용하지 않는다.

## 1. 확인 수준과 자료 기준

| 구분 | 뜻 |
| --- | --- |
| 문서 확인 | 공식 안내에 사용 방법이 적혀 있다. |
| 배포 확인 | 공개 라이브러리 파일에 메서드·타입·권한 선언이 있다. |
| 대상 환경 확인 필요 | 사용하려는 차량·에뮬레이터에 기능이 있고 앱에 권한이 허용되는지는 미확인이다. |
| 공개 경로 미확인 | 조사한 문서·인터페이스에서 해당 기능의 사용 경로를 찾지 못했다. 제조사 내부 기능의 부재를 뜻하지 않는다. |

공모전이 다섯 공개 SDK 사용을 허용한다는 사실과 개별 센서·제어 권한이 허용된다는 사실은 다르다. 공식 호환성 안내는 차량별 지원과 접근 속성을 확인하고, SDK에서 제공하지 않는 특수 차량 속성은 구현 전에 기술 지원 검토를 받도록 안내한다. [공모전 범위](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008), [차종별 API 호환성](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability)

### 이번에 새로 받은 배포 파일

| 파일 | 크기 | SHA-256 |
| --- | --- | --- |
| Vehicle 2.3.2 AAR | 983,759바이트 | `e11fdfe498cad3ecc0d5a2a6992b0c4a4d135195f1455018127bc069fd5ef937` |
| SpeechToText 3.3.1 AAR | 188,560바이트 | `35109ad13400a7fa103cca9fabbf9b785e63a6a98b17a0698a1aef51458d37bf` |

출처: [Vehicle 배포 파일](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2.aar), [SpeechToText 배포 파일](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/SpeechToText/3.3.1/SpeechToText-3.3.1.aar), [SDK 릴리즈 노트](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note).

Vehicle의 `classes.jar`에는 클래스 파일 706개가 있었다. 그중 `vehicle.api` 바로 아래의 기능 인터페이스 20개를 조사했다. 메서드 이름에 `$`가 없는 public/protected 선언은 오버로드를 포함해 517개였다. 이를 사용 가능한 고유 API 517개로 세지 않는다. 이전 방식과 권한 제한을 포함한다. 모든 클래스의 내부 동작이나 차량 제어 장치까지 역으로 분석한 결과도 아니다.

조사한 인터페이스는 Brake, CarInfo, Display, Door, DrivingMode, EvBattery, Fuel, Gear, HVAC, Light, Odometer, Safety, Seat, SideMirror, SteeringWheel, Tire, Toll, TurnSignal, Window, Wiper다. 시작 시 로컬에는 Git 저장소나 앱 소스가 없었다. 기존 문서의 식별값과 선택한 메서드의 실제 서명·권한은 [확인 기록](evidence/vehicle-safety-inspection-2026-11-12.json)에 저장한다.

## 2. 차량 기능과 정확한 의미

### 2.1 공개 Vehicle 메서드에서 확인한 기능

아래 권한은 배포 파일의 `RequiresPermission` 선언에서 읽었다. 매니페스트에 이름을 적는 것만으로 실제 허용된다고 보지 않는다. 시작·종료 시 서비스 연결과 같은 리스너의 등록·해제도 필요하다.

| 정보 | 확인한 메서드 | 필요한 권한 | 읽을 수 있는 것과 한계 |
| --- | --- | --- | --- |
| 브레이크 입력 여부 | `Brake.checkBrakePedalStatusCapabilities`, `getCurrentBrakePedalStatus`, `registerBrakePedalStatus` | `pleos.car.permission.CAR_DRIVE` | 밟음 여부. 압력·제동력·가속 페달 상태는 아님 |
| 에어백 전개 여부 | `Safety.checkAirbagDeployedCapabilities`, `hasSeatAirbagBeenDeployed`, `registerSeatAirbagDeployed` | `pleos.car.permission.CAR_SAFETY` | 지원되는 좌석 영역별 전개 상태. 사고 전체를 직접 판정하는 기능은 아님 |
| 핸들 각도 | `SteeringWheel.getCurrentSteeringWheelAngle`, `registerSteeringWheelAngle` | `pleos.car.permission.CAR_DRIVE` | 핸들이 돌아간 각도. 차체 기울기와 구분. **2026-11-12 정정:** 명칭은 "조향각(SteeringWheel)"이다. 값은 앞바퀴 조향각(도, 왼쪽 음수)이며 핸들 회전각이 아니다(KDoc, 구현은 `PERF_STEERING_ANGLE`) |
| 크루즈 목표 속도 | `DrivingMode.getCruiseControlTargetSpeed` | `pleos.car.permission.CAR_DRIVE` | 크루즈에 설정한 목표값. 실제 차량 속도로 대체할 수 없음 |
| 경고 표시 단발 조회 | `Safety.getTelltalesInfo` | `pleos.car.permission.CAR_SAFETY` | 지원·허용된 경고 표시 자료 조회. 조회 성공은 미실행. **2026-11-12 정정:** `CAR_SAFETY`는 인터페이스의 린트용 표시일 뿐이다. 구현의 실행 검사(`SafetyImpl.kt` 335행)와 KDoc은 `PRIVATE_CAR_SAFETY`(signature\|privileged)다. 일반 앱은 조회할 수 없다 |
| 경고 표시 지원 확인·변경 알림 | `Safety.checkTelltalesCapabilities`, `registerTelltales` | `pleos.car.permission.PRIVATE_CAR_SAFETY` | 단발 조회와 권한이 다름. 일반 출품 앱에서 계속 받을 수 있다고 가정하지 않음. **2026-11-12 정정:** 단발 조회와 해제(`unregisterTelltales`)를 포함한 4개 모두 같은 `PRIVATE_CAR_SAFETY`다. 경고등 상태 읽기는 향후 계획이다 |

공식 진입점: [Brake](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/brake/entry), [Safety](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/safety/entry), [Capability 안내](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability). 선택한 상세 메서드 페이지 일부는 제목만 제공해 배포 파일의 실제 선언으로 보완했다.

에어백을 사용할 수 있는 상태와 실제로 펼쳐진 상태를 구분한다. `hasSeatAirbagEnabled`를 전개 신호로 사용하지 않는다. 처음 읽은 전개 상태가 이미 참이라면 새 사건인지 알 수 없으므로 이전 상태·세션·시각을 함께 관리해야 한다.

Gear 인터페이스는 배포 파일에 있지만 공개 기능 목차에서는 확인되지 않았다. 해당 조회·구독에는 Google의 `GEAR_SELECTION`을 사용하라는 사용 중단 예정 안내가 있었다. 바이너리에 이름이 있다는 이유로 정식 제공·승인된 새 구현 경로라고 판단하지 않는다.

### 2.2 실제 속도는 별도 공식 경로가 있다

Pleos 공식 FAQ는 계기판과 같은 출처의 속도를 받으려면 AAOS의 `CarPropertyManager`에서 `PERF_VEHICLE_SPEED`를 조회하거나 구독하도록 안내한다. AAOS는 차량에 탑재되는 Android 운영체제다. [Pleos 속도 FAQ](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq)

| 항목 | 공식 계약 | 적용 조건 |
| --- | --- | --- |
| `PERF_VEHICLE_SPEED` | 읽기 전용 Float, m/s. 주행 방향에 따라 부호가 있음 | `Car.PERMISSION_SPEED`, 차량 속성 지원·허용·정상 응답 확인 |
| `ACCELERATOR_PEDAL_COMPRESSION_PERCENTAGE` | 가속 페달이 눌린 비율, Float 0~100, 읽기 전용 | `Car.PERMISSION_READ_CAR_PEDALS`. Pleos 대상 버전·차량 제공 여부는 미확인 |
| `BRAKE_PEDAL_COMPRESSION_PERCENTAGE` | 브레이크 페달이 눌린 비율, Float 0~100, 읽기 전용 | 위와 같음. 압력·힘·실제 제동력과 구분 |
| `WHEEL_TICK` | 바퀴별 누적 회전 입력과 초기화 횟수, 읽기 전용 | Android에 정의된 선택 후보. 대상 차량 제공은 미확인 |
| `ABS_ACTIVE` | ABS 작동 상태 | 시스템 수준 권한이 필요한 항목. 일반 앱의 기본 수집 항목으로 가정하지 않음 |

출처: [Google VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds). 현재 Google 문서의 항목이 Pleos API 34 에뮬레이터의 차량 라이브러리에 모두 존재한다는 뜻은 아니다. 사용할 Car API 버전·속성 설정·권한·갱신 주기·값을 실제로 확인해야 한다. 특수 속성은 Pleos 검토 조건도 적용한다.

속도 10m/s는 36km/h다. 기록에는 원래 단위와 출처를 남긴다. 서로 다른 시각의 유효한 속도 두 값을 이용해 속도 변화율을 계산할 수 있지만, 가속도 센서 측정이나 사고 원인 판정과 같은 것으로 취급하지 않는다. 오래된 값·수신 간격 불명·오류 상태에서는 계산하지 않는다.

릴리즈 노트는 Vehicle 2.3.2에서 `VehicleSpeedListener`가 제거됐다고 명시한다. 예전 리스너나 크루즈 목표값을 현재 속도 기능으로 소개하지 않는다. [제거 항목](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note)

2026-11-12 보충: `Car.PERMISSION_SPEED`의 권한 문자열은 `android.car.permission.CAR_SPEED`다. 이 AAOS 경로는 Pleos FAQ가 안내하지만 공모전 공지의 다섯 SDK 목록 밖이므로, 허용 범위는 기술 문의 대상으로 둔다. 에뮬레이터 2.1.0 Virtual Console 사용 가이드의 카드 표에는 차속 카드가 없다. 그래서 시연에서는 앱 안 시험 입력 재생기로 속도 변화를 보여 주고 화면에 [시험 입력]이라고 표시한다.

### 2.3 차량을 직접 멈추는 기능

조사한 Vehicle 2.3.2의 20개 기능 인터페이스에서 가속 페달 직접 조회, 브레이크 강제 작동, 엔진·모터 구동력 차단, 가속 페달 무시 명령은 확인되지 않았다. 이 판단은 조사한 공개 버전·범위에 한정한다. Android의 페달 비율 항목은 앞 표처럼 별도로 조건을 확인해야 한다.

Google 문서에 자동 긴급 제동(AEB)의 활성화·상태 항목이 있어도 일반 앱이 즉시 브레이크를 작동시키는 경로가 되지는 않는다. 관련 항목에는 시스템 수준 권한이 필요하다. 차량의 기존 안전 기능과 공모전 앱의 제어 범위를 섞지 않는다.

제안하는 첫 앱은 읽기·기록·알림 범위다. 자동 제동·가속 제한을 제품 목표에 넣으려면 제조사의 안전 제어 장치와 별도 개발·검증이 필요하다. 현대차그룹의 PMSA는 관련 기존 기능으로 참고하되 공개 앱 제어 기능으로 인용하지 않는다. [PMSA 자료](https://www.hyundaimotorgroup.com/ko/tag/pedal-misapplication-safety-assist)

## 3. 내부·외부 마이크와 음성 내용 파악

| 기능 | 확인 결과 | 설계에서 지킬 조건 |
| --- | --- | --- |
| 차량 마이크로 말 인식 | `request()` 시작, `stop()` 종료. `STT_SERVICE` 권한 필요 | 초기화·결과 리스너·모드 설정과 오류 처리 필요 |
| 인식한 글 | `ResultListener.onUpdated(stt, completed)` | 중간·최종 결과와 한 인식 세션을 구분 |
| 인식 종료 | `onEndedRecognition()` | 세션이 종료됨. 무기한 계속 듣기를 보장한 문서로 읽지 않음. **2026-11-12 정정:** 바이트코드상 발화 끝 감지(EPD_OFF) 이벤트다. 세션 전체의 종료로 단정하지 않는다 |
| 신호 크기 | `onUpdatedRms(rms)` | dB·충격량·소리 종류로 해석하지 않음 |
| 음성 구간 위치 | `onUpdatedEpdData(on, off)` | 바이트 위치. 실제 발화 시각으로 바로 해석하지 않음 |
| 앱이 가진 소리를 인식기로 입력 | `sendAudio(byte[])`, `completeAudioSend()` | ON_DEVICE 입력 기능. 차량 마이크의 원본 소리를 꺼내는 기능이 아님 |
| 내부·외부 마이크 선택과 구역 선택 | 읽은 가이드와 확인한 주 클래스에서 해당 선택 API를 확인하지 못함 | 장치 존재·접근·동시 사용을 확정하지 않음 |
| 운전자 식별·감정·사고 의도 판단 | 확인한 결과 전달 기능에 해당 판정값이 없음 | 앱이 인식 문장과 다른 자료를 조합해야 함 |

공식 원문: [소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro), [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions), [시작·중지](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/start-and-stop), [결과](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/result-listeners), [오디오 입력](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/sending-audio-data).

필요한 권한 이름은 `pleos.car.permission.STT_SERVICE`다. ON_DEVICE는 서버 등록 없이 쓰는 기기 내 인식 모드다. 실제 엔진·한국어 인식 품질·오프라인 작동·장시간 사용은 시험하지 않았다. SERVER/HYBRID의 등록·인증 조건과 구분한다.

놀란 말의 인식은 ‘글을 받기 → 같은 표현 정리 → 중복 결과 묶기 → 차량 상태와 시각 비교 → 의심 구간 표시’로 제안한다. ‘어 이거 왜이래!’, ‘아이고!’, ‘안돼!’만으로 사고·급발진·페달 착각을 판정하지 않는다. 원본 소리가 없으면 억양을 사후 재분석할 수 있다고 약속하지 않는다.

장시간 인식에는 내비 안내·통화·다른 음성 기능과의 충돌, 오류 후 복구, 앱이 화면 뒤로 간 상황을 확인해야 한다. 계속 듣기가 허용되지 않으면 제한된 세션 기능으로 범위를 낮추고 사고 신호가 들어올 때 인식이 꺼져 있을 수 있음을 표시한다. 새 인식이 과거의 말을 복구하지는 못한다.

직접 녹음하는 별도 Android 구현을 검토할 경우에는 `RECORD_AUDIO`와 해당 전경 서비스 조건을 별도로 확인한다. 이는 Pleos의 STT 권한과 같은 계약이 아니다. Android 14는 서비스 종류와 사용 중 권한에 따라 백그라운드 시작을 제한한다. 녹음 권한을 선언하는 것만으로 차량 내부·외부 모든 마이크를 쓸 수는 없다. [Android 전경 서비스 종류](https://developer.android.com/develop/background-work/services/fgs/service-types)

## 4. 기울기·충격·회전 센서

| 필요한 값 | Android 후보 | 뜻과 한계 |
| --- | --- | --- |
| 3축 가속도 | `TYPE_ACCELEROMETER` | 중력을 포함한 장치의 움직임. m/s² |
| 중력을 뺀 가속도 | `TYPE_LINEAR_ACCELERATION` | 계산된 움직임 값. 센서 구성에 따라 제공 여부가 다름 |
| 회전 속도 | `TYPE_GYROSCOPE` | 장치가 각 축으로 회전하는 빠르기. rad/s |
| 중력 방향 | `TYPE_GRAVITY` | 장치 기준 중력 방향. 방향 추정의 보조 자료 |
| 회전·기울기 추정 | `TYPE_ROTATION_VECTOR` 등 | 장치의 방향을 계산하는 후보. 곧바로 차체의 기울기가 되지는 않음 |

출처: [Android 센서 개요](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview), [움직임 센서](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion). 센서 목록을 확인해야 하며 기본 센서 조회 결과가 없을 수도 있다. Pleos는 일부 모바일 센서가 없거나 제한될 수 있다고 안내한다. [플랫폼 구조](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/platform-api-architecture)

현재 조사한 Pleos Vehicle 인터페이스에는 차체의 좌우·앞뒤 기울기나 전용 충격 센서 값을 직접 읽는 메서드가 확인되지 않았다. Android 센서도 ‘장치’ 기준이다. 차량과 단단히 고정되어 있는지, 좌표축이 차량 방향과 어떻게 맞는지, 오차가 어느 정도인지 확인해야 한다. 휴대폰을 추가하면 휴대폰 자체 움직임과 차량 움직임을 구분해야 한다.

핸들 각도는 차체 기울기가 아니다. GPS 방향도 차체 기울기를 알려 주지 않는다. 언덕·기울어진 도로·장치 낙하를 전복으로 오인할 수 있으므로 한 값으로 사고를 확정하지 않는다. 지원 여부와 갱신 주기·정확도를 확인하기 전에는 고속 사고 기록 센서로 설명하지 않는다.

## 5. 카메라·원본 소리·상시 기록

Vehicle 2.3.2의 조사한 기능 인터페이스에서 실내·외 카메라 열기와 동영상 녹화 기능은 확인되지 않았다. 차량에 카메라가 설치되어 있거나 재생 코덱이 있다는 사실은 앱에 캡처 권한이 있다는 뜻이 아니다.

Android 시스템 카메라는 일반 카메라 권한 외에 시스템 수준 권한이 필요할 수 있다. 어느 카메라가 일반 앱에 공개되는지는 대상 기기에서 확인해야 한다. 제조사 내부 카메라를 일반 카메라처럼 열 수 있다고 가정하지 않는다. [Android 시스템 카메라](https://source.android.com/docs/core/camera/system-cameras)

차량 앱이 물리 차량 센서와 제어 장치에 직접 접근하는 방식도 Pleos 플랫폼 구조와 맞지 않는다. 카메라·블랙박스·외부 마이크는 승인된 플랫폼 기능이나 장치 업체의 공식 연결 방법이 있을 때 검토한다. 해당 장치를 우회해서 읽는 방법은 제안하지 않는다.

Android 전경 서비스 조건을 맞춰도 앱 종료·전원 차단 이후의 캡처가 보장되지는 않는다. Pleos의 화면·프로세스 변화와 저장 공간을 함께 시험해야 한다. [앱 구조와 실행 상태](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model)

## 6. 위치와 추가 차량 정보

위치는 FusedLocation의 지원 확인·권한 처리 후 읽는다. 값이 없거나 오래될 수 있으므로 좌표·정확도·측정 시각·수신 시각을 보관한다. (2026-11-12 정정: `LocationData`에는 미터 단위 정확도가 없다. "정확도" 대신 뜻이 공개되지 않은 `confidence` 원값을 보관한다.) 현재 위치가 없을 때 마지막 위치를 ‘현재 위치’로 표시하지 않는다. 음성 결과·속도·센서마다 시계가 다를 수도 있어 시간 오차를 처리해야 한다. [위치 데이터](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/location-data-access)

안전벨트, 문, 배터리, 연료, 타이어 등의 상태는 사고 기록의 보조 후보다. 지원되지 않는 항목을 전부 수집한다고 약속하지 않는다. 타이어 경고가 곧바로 사고 신호가 되거나 경고 표시 전체를 상시 수신할 수 있다고도 가정하지 않는다. 특히 경고 표시 조회와 PRIVATE 권한의 지원 확인·구독을 구분한다. (2026-11-12 정정: 조회도 실행 검사에서 `PRIVATE_CAR_SAFETY`를 요구하므로 구분할 필요가 없다. 일반 앱은 경고 표시를 조회·구독할 수 없다. 대신 normal 권한의 안전벨트·도어·엔진오일·브레이크액·워셔액·출력 저하 신호를 쓴다.)

## 7. 실제 개발 전에 확인할 항목

| 우선순위 | 확인할 항목 | 현재 결과 |
| --- | --- | --- |
| P0 | 실제 속도·브레이크·에어백의 권한과 지원 | 공식 경로·배포 선언 확인. 호출 미실행 |
| P0 | 음성 세션·노이즈·한국어 예문의 인식·지연 | 문서·메서드 확인. 인식 미실행 |
| P0 | 앱 실행 상태별 수집 유지와 사건 전후 보관 | 설계만 작성. 실행 보장 없음 |
| P0 | 보호자 등록·동의·서버 수신·휴대폰 도착 | 앞으로 개발할 기능. 서버·알림 없음 |
| P1 | 가속·브레이크 비율의 대상 Car API 지원과 검토 | Android 항목 확인. Pleos 환경 미확인 |
| P1 | 장치 기울기·가속도·회전 센서와 차량 방향 보정 | Android 후보 확인. 센서 조회 미실행 |
| P1 | 원본 소리·외부 마이크·차량 카메라 접근 | 공개 제공 경로 미확인 |
| 별도 개발 | 자동 제동·가속 제한 | 일반 공모전 앱의 확인된 범위에 포함하지 않음 |

실행하지 않은 시험은 음성·차량 데이터 수신, 런타임 권한, AVD·실차, 백그라운드 녹음, 사고 판정 정확도, 영상 저장, 네트워크 재전송, 서버 인증, 보호자 통지다. 직접 수행한 것은 공식 자료 열람, 새 공개 패키지 다운로드, ZIP 내부 클래스·서명·권한의 정적 판독, 보고서 검사다.

## 8. 갱신 안내

후속 조사에서는 이 문서와 종합 SDK 리뷰를 먼저 읽는다. 2026-11-12 후속 확인 결과는 11절과 [후속 확인 기록](evidence/sdk-followup-2026-11-12.json)에 있다. 대상 버전·차종·실제 호출 결과가 생기면 확인 수준과 날짜를 함께 고친다. [확인 기록](evidence/vehicle-safety-inspection-2026-11-12.json)에 새 증거를 남기되 2026-11-11의 SDK 검사 이력을 실제 실행 결과로 바꾸지 않는다. 공통 읽기·갱신 경로는 [AGENTS.md](../../AGENTS.md)에 있다.

## 9. 2026-11-12 통합 앱 후속 조사

현재 제품 범위는 [Pleos 차량 이상 기록·긴급 안내 앱](../analysis/integrated-vehicle-care-2026-11-12.md)을 따릅니다. 지원 대상은 Pleos 탑재 차량이며 휴대폰은 백업과 보조 입력을 맡습니다. 경고등 전체 수록은 도감 자료의 범위이며, 모든 경고·진단 코드의 실시간 조회가 허용됐다는 뜻은 아닙니다.

- 현대 빌트인 캠의 한 공식 설명서에서 마이현대의 영상 재생·다운로드·공유와 Wi-Fi 연결 중 모바일 네트워크 중단 안내를 확인했습니다. Pleos 제3자 앱의 직접·실시간 API는 확인하지 못했습니다. 해당 설명서의 차량을 지원 Pleos 모델로 채택한 것은 아닙니다. [공식 연결 안내](https://ownersmanual.hyundai.com/full_webhelp/ME/2025/ko_KR/id23C63000W3M.html)
- FLEETA의 공식 제품 페이지와 개발자 문서 연결을 확인했습니다. 지원되는 외부 블랙박스를 Pleos 차량에 설치한 경우의 별도 연동 후보입니다. 클라우드 영상·이벤트 API의 존재와 실제 대상 기기·구독·권한·지연 검증은 구분합니다. 로컬 Wi-Fi 문서는 개요를 제공하지만 상세 명세·가이드는 준비 중으로 안내합니다. [제품의 개발자 연결](https://fleeta.io/pages/developers), [현재 API](https://developers.fleeta.io/), [Wi-Fi 개요](https://developers.fleeta.io/docs/wifi)
- Pleos 주행 정책은 움직이는 안내를 제한할 수 있습니다. 음성과 설명 애니메이션을 제품 목표로 두되 `CarUxRestrictions`, 앱 분류와 지역에 맞는 표시 형식을 확인해야 합니다. (2026-11-12 정정: Pleos 디자인 원칙은 운전 중 동영상·애니메이션 이미지로 시선을 끌거나 손·발 동작을 유도하지 말라고 합니다. 그래서 주행 중에는 단계별 정지 그림 1장과 짧은 음성만 쓰고, 설명 애니메이션은 정차 중 연습 화면으로 옮깁니다. 원문은 [Pleos 앱 가이드 기준](../analysis/pleos-app-guidelines-2026-11-12.md) 5절에 있습니다.) 차량의 Driving view를 앱이 임의로 가리거나 종료할 수도 없습니다. [주행 안전](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline), [화면·실행 구조](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model)
- Android의 마이크·카메라·위치 전경 서비스 조건 때문에 사고 뒤 휴대폰을 무조건 깨워 녹음을 시작하는 구조로 보장할 수 없습니다. 출발 전에 백업을 준비하고 실제 실행 상태를 확인하는 설계가 필요합니다. [Android 공식 조건](https://developer.android.com/develop/background-work/services/fgs/service-types)

이번에는 공식 원문을 추가 확인했으며 새 앱·폰·블랙박스 연결·녹음·녹화·사고 판단·긴급 안내를 실행하지 않았습니다. SDK 버전은 [현재 릴리즈](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note)와 다시 대조했으나 AAR 재다운로드·바이너리 재검사는 하지 않았습니다. 추가 근거는 [통합 앱 확인 기록](../analysis/integrated-vehicle-care-2026-11-12-evidence.json)에 남깁니다.

## 10. PRD v2의 진동·수동 선택·보호자 통지 보충

현재 구현 기준은 [현재 통합 PRD](../analysis/integrated-vehicle-care-2026-11-12.md)와 [개발 계약](../specs/vehicle-care/README.md)입니다. (2026-11-12 정정: 이 절을 쓸 때는 같은 경로의 문서를 "통합 PRD v2"라고 불렀습니다. 지금 같은 경로의 문서는 v3이며 1차 비판 반영으로 v4로 고칠 예정입니다. v2 원문은 [PRD v2 이력](../analysis/integrated-vehicle-care-2026-11-12-prd-v2.md)에 있습니다. 진동 분석은 1차 비판 반영 결정에서 향후 계획으로 옮겼습니다.) 새 요청의 IMU·휠·회전수는 모두 제공된다고 가정하지 않습니다. 현재 Google 원문에서 `ENGINE_RPM`과 3P/특권 읽기 권한, `WHEEL_TICK` 누적값, 전륜 각도와 핸들 각도의 차이를 확인했습니다. 실제 Pleos 대상·권한·수집 속도·고정/보정은 여전히 미확인입니다. 범용 모터 RPM·차체 전용 진동·DTC 공개 경로도 확정하지 않았습니다. [Google 차량 속성](https://developer.android.com/reference/android/car/VehiclePropertyIds)

실험용 200Hz·512샘플·대역 RMS와 수치는 제조사 기준이 아닙니다. 단발 충격의 겹치는 분석 창을 지속 진동으로 오인하는 반례를 합성 데이터로 확인해 충격 구간 제외를 추가했습니다. 실제 고장 구분 정확도나 사고 성능을 시험한 결과는 아닙니다.

Pleos 디자인 원문은 앱 창의 가로/세로 대응, 중요 글자 실물 높이 5mm·보조 3mm, 대비 4.5:1 이상을 안내합니다. PRD의 1280×720dp(가로 시안 예시)와 주요 버튼 80dp(자체 설계값, Pleos 공식 수치 아님)는 작성자가 정한 기준입니다. (2026-11-12 정정: 이전 문장은 "64dp 터치"였습니다. 64dp는 PRD v2 시점의 값이며 현재 기준은 주요 버튼 80dp입니다. Pleos 원문 텍스트에는 버튼 dp 수치가 없습니다. 또 Pleos 앱은 따로 정하지 않으면 세로 한 창으로 실행되므로 세로 배치를 기본으로 추가합니다.) [화면 구성](https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout), [UI 원칙](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro)

알림톡 일반 메시지와 인증 전용 경로, 수신 결과 조회·문자 길이와 대체 조건을 공식 발송사 문서로 확인했습니다. 카카오 요청 접수는 전달/열람이 아니며, 템플릿 승인·실제 발송은 미실행입니다. [알림톡](https://docs.nhncloud.com/ko/Notification/KakaoTalk%20Bizmessage/ko/alimtalk-api-guide/), [문자](https://docs.nhncloud.com/ko/Notification/SMS/ko/api-guide/), [이번 확인 기록](../analysis/integrated-vehicle-care-prd-2026-11-12-evidence.json)

## 11. 2026-11-12 후속 확인: 공개 안전 신호와 정정

Vehicle 2.3.2의 소스 파일 181개와 바이트코드를 모두 대조했습니다. 확인 방법은 정적 분석이며 에뮬레이터·실차 호출은 하지 않았습니다. 근거와 해시는 [후속 확인 기록](evidence/sdk-followup-2026-11-12.json)에 있고, 앞선 [안전 확인 기록](evidence/vehicle-safety-inspection-2026-11-12.json)은 고치지 않았습니다.

### 11.1 이 문서에서 정정한 것

| 위치 | 이전 판단 | 정정 |
| --- | --- | --- |
| 2.1절 표, 6절 | `getTelltalesInfo`는 `CAR_SAFETY`로 단발 조회 가능 | 경고등 API 4개 모두 실행 검사에서 `PRIVATE_CAR_SAFETY`(signature\|privileged). 일반 앱은 읽을 수 없고 [향후]로 둡니다 |
| 2.1절 표 | 핸들 각도 = 핸들이 돌아간 각도 | 조향각(SteeringWheel) = 앞바퀴 조향각(도, 왼쪽 음수). 핸들 회전각이 아닙니다 |
| 6절 | 위치에 정확도 보관 | 정확도 필드가 없고 `confidence`만 있습니다 |
| 9절 | 음성과 설명 애니메이션을 주행 중 목표로 둠 | 주행 중에는 정지 그림 1장과 짧은 음성만, 애니메이션은 정차 중 연습 화면으로 |
| 10절 | 통합 PRD v2, 64dp 터치 | 현재 통합 PRD(v3, v4 예정), 주요 버튼 80dp(자체 설계값, Pleos 공식 수치 아님) |

### 11.2 일반 앱이 쓸 수 있는 공개 안전 신호

모두 KDoc 보호 수준이 normal입니다. 차량·에뮬레이터가 해당 기능과 영역을 지원해야 하므로 먼저 Capability로 확인합니다. Virtual Console 열은 [사용 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)의 카드 이름과 맞춘 결과이며 실제 주입은 시험하지 않았습니다.

| 신호 | 메서드 | 권한 | Virtual Console | 플레오스 가드 등급 |
| --- | --- | --- | --- | --- |
| 브레이크 밟음 | `Brake.getCurrentBrakePedalStatus`, `registerBrakePedalStatus` | `CAR_DRIVE` | Brake Pedal(0~100%, 파생 작동 상태) | [시연: VC] |
| 조향각 | `SteeringWheel.getCurrentSteeringWheelAngle`, `registerSteeringWheelAngle` | `CAR_DRIVE` | Steering Angle | [시연: VC] |
| 누적 주행거리(km) | `Odometer.getCurrentDistanceDriven`, `registerDistanceDriven` | `CAR_DRIVE` | Odometer | [시연: VC] |
| 방향지시등 | `TurnSignal.getCurrentTurnSignalState`, `registerTurnSignalState` | `CAR_LIGHTS` | Turn Signal State | [시연: VC] |
| 도어 열림 | `Door.getCurrentDoorPosition`, `registerDoorPosition`(HOOD·REAR 영역 포함) | `CAR_DOORS` | Door Position, Trunk / Frunk Position | [시연: VC] |
| 좌석 점유 | `Seat.getCurrentSeatOccupancy`, `registerSeatOccupancy` | `CAR_SEATS` | Seat Occupancy | [시연: VC] |
| 안전벨트 체결 | `Seat.getCurrentSeatBeltBuckledState`, `registerSeatBeltBuckledState` | `CAR_SEATS` | 없음 | [시연: 시험 입력] |
| 안전벨트 미체결 경고 | `Safety.hasSeatBeltNotBuckledWarning`, `registerSeatBeltNotBuckledWarning` | `CAR_SAFETY` | 없음 | [시연: 시험 입력] |
| 에어백 전개 | `Safety.hasSeatAirbagBeenDeployed`, `registerSeatAirbagDeployed` | `CAR_SAFETY` | 없음 | [시연: 시험 입력] |
| 엔진오일·브레이크액·변속기오일 수준 | `Safety.getEngineOilLevelState`, `getBrakeFluidLevelState`, `getTransmissionOilLevelState` | `CAR_SAFETY` | 없음 | [시연: 시험 입력](변속기오일은 후보) |
| 워셔액 부족·출력 저하 경고 | `Safety.hasLowWasherFluidWarning`, `hasPowerDownWarningIndicator` | `CAR_SAFETY` | 없음 | [시연: 시험 입력] |
| 연료·구동 배터리 % | `Fuel.getFuelPercent`, `EvBattery.getEvBatteryStateOfCharge` | `CAR_ENERGY` | Battery SOC만 있음 | [시연] |

- `hasSeatAirbagEnabled`는 이름과 달리 KDoc이 "true면 해당 좌석 에어백에 문제가 있다"고 설명합니다. 뜻을 확인하기 전에는 사건 판단에 쓰지 않습니다.
- 좌석 점유는 서비스 값이 1일 때만 true로 바뀝니다. Virtual Console의 Empty/Occupied로 뜻을 확인해야 합니다.
- 경고등 상태를 읽는 것이 아니라 "같은 원인의 공개 신호"를 읽는다고 표현합니다.

### 11.3 공개 경로가 없거나 일반 앱이 쓸 수 없는 것

차속(Vehicle SDK에 없음, AAOS 경로는 2.2절), 기어(숨김), 시동 상태(API 없음), 가속 페달·브레이크 압력(없음), 주차 브레이크(없음), 비상등(없음, Virtual Console 카드는 있으나 읽을 수 없음), 타이어 현재 공기압(숨김·PRIVATE), 경고등 상태(PRIVATE), 충격·가속도·기울기(없음)입니다. 공개 Tire API는 현재 값이 아니라 저압 기준값(kPa)만 줍니다. 이 항목은 기획서에 향후 계획으로만 적습니다. 숨김 API는 바이너리에서 부를 수 있어도 공개 문서 범위가 아니므로 쓰지 않습니다.

### 11.4 설계에 반영할 동작

- **권한 실패**: 읽기·확인은 `onFailed(SecurityException)`로, 일반 구독·해제는 호출 즉시 `SecurityException`으로 옵니다. 구독 코드에 `try/catch`를 둡니다.
- **연결 대기**: 서비스 연결 중 호출은 시간 제한 없이 기다리므로 앱이 시간 제한을 둡니다.
- **콜백 스레드**: Binder 스레드에서 불리므로 화면 갱신 전에 메인 스레드로 넘깁니다.
- **신선도**: 값에 측정 시각이 없습니다(예: `onBrakePedalStatusUpdated(status: Boolean?)`). 브레이크·도어처럼 바뀔 때만 오는 신호는 "마지막 수신 시각"과 "서비스 연결 상태"로 유효성을 판단합니다. 3,000ms 신선도 규칙은 주기적으로 오는 차속에만 적용합니다.
- **시연**: Virtual Console은 에뮬레이터 2.1.0 이상, cold boot로 만든 AVD에서 씁니다. 주행 제한은 `adb shell cmd hmg_car_service --service drivingstate driving_regulation on`으로 켭니다. 원문 인용은 [Pleos 앱 가이드 기준](../analysis/pleos-app-guidelines-2026-11-12.md) 10절에 있습니다.
