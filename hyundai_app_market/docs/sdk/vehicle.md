# Vehicle SDK 개발 노트

2026-11-12 안전 기능 보충: 속도·브레이크·에어백·페달·기울기·녹화 조건은 [상세 조사](vehicle-safety-capabilities.md)를 함께 읽는다. 아래의 초기 검토 결과를 실행 검증으로 바꾸지 않는다.

기준일: 2026-11-11 KST. 공모전 사용 가능, 배포 기준 **2.3.2**. 패키지와 API의 정적 검토를 했으며 실제 차량·AVD를 실행하지 않았다. [전체 리뷰](../analysis/pleos-sdk-review.md), [문서 전체 목차](README.md).

## 1. 역할과 의존성

Vehicle Service를 통해 차량 상태 조회·변경·알림을 처리한다. `Vehicle`에서 도메인 객체를 얻어 사용한다. [소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro), [의존성 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)

```text
ai.pleos.playground:Vehicle:2.3.2
```

실제 POM의 Pleos 의존성은 `vehicle-aidl:1.1.1`, `vehicle-data:1.1.11`이다. AAR는 약 984KB이며 SDK 버전 메타데이터도 2.3.2다. 라이브러리 매니페스트의 서비스 최소 버전 표시는 2.3.13이고, 소개 문서의 설명 기준은 2.3.21이다. 설치 환경이 이 값들과 일치하는지는 미검증이다. [공개 POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2.pom)

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

## 5. 현재 예제에서 주의할 점

- Callback 예제의 영역 없는 등록·해제는 2.3.2 서명과 맞지 않는다. `DoorArea`와 동일 리스너가 필요하다.
- Java `onPositionUpdated`는 `int`가 아닌 `Integer`다.
- Set Java 예제에는 실제 메서드가 요구하는 완료 콜백이 빠져 있다. Kotlin 기본 인자 처리와 Java 호출을 구분한다.
- 공식 샘플의 현재 고정 커밋은 SDK 2.3.1을 지정한다. 2.3.2 실행 검증으로 인용하지 않는다.

정확한 대조 근거는 [리뷰 F2·F3·F5](../analysis/pleos-sdk-review.md)에 있다.

## 6. 샘플과 실행 확인 과제

[공식 샘플](https://github.com/Pleos-Playground/pleos-sample-vehicle-app), [샘플 가이드](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app)를 참고한다. 샘플은 광범위한 기능 탐색용이다. 출품 앱에는 실제로 사용하는 권한과 기능만 적용한다.

아직 실행하지 않은 확인 과제는 서비스 연결, Capability의 영역·범위, null·권한 거부·주행 제한, 실제 이벤트 수신, 화면 재진입 시 중복 구독, 요청 후 상태 재조회다. 샘플의 초기화 표시를 실제 서비스 연결 완료로 간주하지 않는다.
