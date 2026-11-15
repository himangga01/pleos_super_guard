# 플레오스 슈퍼 케어 (Pleos Super Care)

Pleos Connect가 들어간 차(디 올 뉴 아반떼 기준)를 위한 차량 관리 앱입니다. 차가 이상한 그 순간의 차량 상태를 기록하고, 폰으로 잰 소리 · 진동을 같은 기록에 붙이며, 대처 방법 → 정비소 → 짐 알림 → 사고 대처까지 이어 줍니다.

현대자동차그룹 차량용 앱 개발 공모전(Code your App into Pleos App Market) 출품작입니다.

## 시연 영상

- [시연 영상 보기 (3분 12초)](https://drive.google.com/file/d/1bj9M9jCjB0rYvvinYQEbLkmEAOpz95Ob/view?usp=drive_link)
- Pleos Connect 에뮬레이터 2.1.0과 폰 에뮬레이터에서 실제로 실행한 화면을 녹화했습니다. 넣은 값은 "시험 입력", 모의 발송 · 모의 연결 · 예시 자료는 화면에 따로 표시되어 있습니다.

## 구성

| 폴더 | 내용 |
| --- | --- |
| `app/` | 차 앱 **플레오스 슈퍼 케어** (패키지 `app.pleosguard.demo`, 0.2.0-demo) |
| `phone-app/` | 폰 앱 **플레오스 슈퍼 케어 모바일** (패키지 `app.pleossupercare.phone`, 0.1.0-demo): 차가 요청한 소리 · 진동 · 사진 기록 |
| `server/` | 모의 서버 `care_server.py` (Python 표준 라이브러리, 127.0.0.1:8766): 차 · 폰 짝 맺기, 기록 중계, 정비소 · 후기 · 리콜 예시 자료 |
| `apk/` | 차 앱 · 폰 앱 APK |
| `tools/` | 3D 아반떼 생성 스크립트(Blender), 로고 · 진입 화면 생성 |
| `scripts/` | 에뮬레이터 보조 명령(위치 맞추기, 폰 에뮬레이터 만들기 등) |

## 주요 기능

| 기능 | 하는 일 | 주로 쓰는 신호 · SDK |
| --- | --- | --- |
| 3D 케어 홈 | 돌려 보고 눌러 고르는 3D 아반떼, 다음 정비 · 리콜 · 짐 알림 · 구독 · 폰 연결 · 증상 기록 카드 | Vehicle(주행거리), AAOS 주행 제한 |
| 차량 관리 | 차 점검 신호, 소모품 · 정기 점검까지 남은 거리 | Vehicle(Safety, Odometer) |
| 리콜 안내 | 차명 · 세대 · 제작연월로 "대상일 수 있음", 위험 단계, 리콜센터 QR | 앱 자료(예시) |
| 구독 연계 | 내 차 기록에 맞춘 소모품 · 정비 구독 추천, 가입은 폰에서 QR로 | Vehicle(Odometer) |
| 증상 기록 | 기록 시점 앞 20초 · 뒤 10초의 차 상태 시간표, 말 메모, 심한 정도 · 빈도, 처리 흐름 | Vehicle(브레이크 · 조향 · 방향지시등 · 공조 · 창문 · 와이퍼), AAOS 차속, FusedLocation, SpeechToText |
| 폰 증거 | 차가 요청하면 폰이 소리 · 진동 · 사진을 기록해 차 기록에 붙임 | 폰 앱 + 모의 서버 |
| 도움이 필요해요 | 고른 증상 조합에 맞춘 확인 순서, 해결이 안 되면 기록 → 정비소 도움 요청 | TextToSpeech |
| 경고등 찾기 | 색 → 모양 → 켜짐(15개 도감, 64개 조합)으로 뜻 찾기, 말 · 폰 사진으로도 찾기 | SpeechToText |
| 정비소 · 후기 | 거리 · 도착 예상, 경유지 추가, 방문을 확인한 후기 · 별점, 기록을 QR로 보여 주기 | NaviHelper, FusedLocation |
| 짐 알림 | 트렁크를 열고 출발했다가 도착해 운전석 문을 열 때 한 번 알림(폰에도) | Vehicle(문 · 트렁크), AAOS 차속 · 주행 제한, TextToSpeech |
| 안심 케어 | 사고 · 에어백 때 10초 확인 뒤 보호자에게 위치 알림 | Vehicle(Safety · 좌석 · 문), FusedLocation, NaviHelper |
| 사고 대처 | 사고 확인 → 내 보험사 접수 번호 연결 돕기 → 2차 사고 예방 · 차 옮기기 · 현장 촬영 안내(터치 또는 음성 답) | TextToSpeech, SpeechToText |

## 실행 방법

1. Pleos Connect 에뮬레이터 2.1.0을 콜드 부트로 실행합니다(Virtual Console은 콜드 부트에서 씁니다).
2. 차 앱을 설치합니다.
   ```
   adb -s emulator-5554 install -r -g "apk/플레오스슈퍼케어_차앱_0.2.0-demo.apk"
   ```
3. 앱을 엽니다. 처음에 차속 · 위치 권한과 내 보험사를 묻습니다(나중에 하기 가능).
4. 차 신호는 Virtual Console로 바꿉니다: `adb forward tcp:8088 tcp:8088` 뒤 브라우저에서 `http://127.0.0.1:8088`(트렁크 · 문 · 기어 · 차속 · 주행거리 · 좌석).
5. 에뮬레이터가 주지 않는 값(에어백 전개, 사고 등)은 오른쪽 위 "에뮬레이터 시연" 패널에서 시험 입력으로 넣습니다. 화면에 "시험 입력"이 표시되고, 섞인 사건은 모의 발송만 합니다.
6. (선택) 폰 연동: 모의 서버를 켜고 폰 앱을 설치한 뒤, 차 홈의 "폰 연결"에서 QR 또는 6자리 코드로 연결합니다.
   ```
   python server/care_server.py --port 8766 --host 127.0.0.1
   adb -s emulator-5556 install -r "apk/플레오스슈퍼케어모바일_폰앱_0.1.0-demo.apk"
   ```
   에뮬레이터에서 서버 주소는 `http://10.0.2.2:8766`입니다. 서버가 없으면 차 앱은 앱 안 예시 자료로 동작합니다.
7. 앱을 다시 설치한 뒤 위치가 맞지 않으면 `scripts/local-emulator.ps1 location`으로 에뮬레이터 GPS(판교 모의 위치)와 Pleos 위치 서비스를 다시 맞춥니다.

## 테스트 환경과 필요한 설정

- 차 앱: compileSdk · targetSdk 34, minSdk 28. 폰 앱: compileSdk · targetSdk 34, minSdk 26.
- 빌드: Gradle 8.14.3(래퍼 포함), Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Jetpack Compose, JDK 17 이상.
- Pleos SDK: Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1, SpeechToText 3.3.1, FusedLocation 1.1.3(공모전 안내의 공개 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/`).
- AAOS: `CarPropertyManager`의 `PERF_VEHICLE_SPEED`(차속), `CarUxRestrictions`(주행 제한).
- 그 밖: Filament 1.75.1(3D), ZXing 3.5.3(QR), 폰 앱 CameraX.
- 차 앱 권한: `android.car.permission.CAR_SPEED`, `pleos.car.permission.CAR_DRIVE`, `CAR_DOORS`, `CAR_SEATS`, `CAR_SAFETY`, `CAR_CLIMATE`, `CAR_WINDOWS`, `CAR_LIGHTS`, `CAR_INFO`, `FUSED_LOCATION`, `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `NAVI_CUSTOM_MAP`, `TTS_SERVICE`, `STT_SERVICE`, `android.permission.INTERNET`(모의 서버, 평문 HTTP는 10.0.2.2 · 127.0.0.1 · localhost만 허용).
- 폰 앱 권한: `CAMERA`, `RECORD_AUDIO`, `HIGH_SAMPLING_RATE_SENSORS`, `POST_NOTIFICATIONS`, `INTERNET`.
- 빌드 · 시험:
  ```
  ./gradlew :app:testDebugUnitTest :app:assembleDebug          # 차 앱, JVM 시험 137개
  cd phone-app && ./gradlew :app:testDebugUnitTest :app:assembleDebug   # 폰 앱, JVM 시험 23개
  python server/selftest.py                                     # 모의 서버 자체 시험
  ```

## 개발에 사용한 에뮬레이터 버전과 화면 규격

- 차: Pleos Connect Emulator 2.1.0(Android 14, API 34, 시스템 이미지 `pleos_car_emulator` x86_64), 콜드 부트.
- 차 화면: 2560 × 1440 px, 160 dpi. 앱은 넓은 창(약 1682 × 1210 dp, 왼쪽 메뉴와 두 칸 배치)과 세로 한 창(841 × 1210 dp, 내비 옆) 두 배치를 모두 지원합니다. 주행 중에는 큰 단추 3개만 보입니다.
- 폰: Android 11(API 30) 에뮬레이터, 1080 × 2340 px.

## APK 위치

| 파일 | 설명 |
| --- | --- |
| `apk/플레오스슈퍼케어_차앱_0.2.0-demo.apk` | 차 앱. Pleos Connect 에뮬레이터 2.1.0에서 확인 |
| `apk/플레오스슈퍼케어모바일_폰앱_0.1.0-demo.apk` | 폰 앱. Android 8.0 이상, 폰 에뮬레이터(Android 11)에서 확인 |

두 APK는 시연용 디버그 빌드입니다.

## 시연 범위

- **실제 신호(에뮬레이터 · Virtual Console)**: 트렁크 · 문, 기어 · 차속(AAOS), 주행 제한 전환, 브레이크, 좌석, 주행거리, 위치(FusedLocation), 내비 주소 · 도착 예상(NaviHelper), 한국어 음성 안내(TextToSpeech).
- **시험 입력(화면에 "시험 입력" 표시)**: 에어백 전개와 사고 알림 시작, 안전벨트, 엔진오일 · 브레이크액 · 워셔액 점검 신호, 폰 진동(에뮬레이터는 실제 진동을 만들 수 없음), 음성 인식으로 들은 말.
- **모의**: 보호자 알림 발송, 보험사 연결(실제 전화는 걸지 않음), 결제, 정비소 QR 공유 링크, 모의 서버.
- **예시 자료**: 리콜 2건(가상), 정비소 · 후기, 소모품 주기, 구독 상품.
- **향후**: 실제 서버와 알림 발송, 실제 폰 센서 · 실차 확인, 경고등 상태 자동 연동(일반 앱에는 권한이 없음), 공공 리콜 자료 연동.

## 자산과 라이선스

- 3D 아반떼는 사진을 붙이지 않고 코드(`tools/car3d/avante_model.py`, Blender)로 만든 근사 형상입니다.
- 진입 화면의 차는 현대자동차그룹 디 올 뉴 아반떼 공식 보도 사진입니다.
- 글꼴 Pretendard(SIL OFL 1.1), 아이콘 Lucide(ISC), ZXing(Apache-2.0), Filament(Apache-2.0). 라이선스 문서는 `app/src/main/assets/licenses/`에 있습니다.
