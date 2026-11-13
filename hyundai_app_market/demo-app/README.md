# 플레오스 가드 데모 앱 (뼈대)

2026-11-13 클라우드 작업 환경에서 만든 **데모 앱 뼈대**입니다. 지금 들어 있는 것은 "SDK 점검" 화면 하나입니다. 이 화면은 로컬 Pleos 에뮬레이터에서 공개 SDK가 실제로 붙는지 먼저 확인하는 용도입니다. 설계 문서의 데모 흐름(홈 → 경고등 찾기 → 지금 기록 → 확인 질문 → 긴급 안내 → 보호자 발송 확인)은 아직 만들지 않았습니다.

## 확인된 것과 확인하지 못한 것

| 항목 | 상태 |
| --- | --- |
| 빌드(`assembleDebug`) | 클라우드 환경에서 통과(디버그 APK 약 47MB) |
| 화면 렌더링 시험(Robolectric·Roborazzi) | 통과. 에뮬레이터 없이 실제 Compose 화면을 PNG로 저장 |
| 에뮬레이터 설치·실행·SDK 실제 호출 | **확인하지 못함.** 클라우드 환경에는 하드웨어 가속이 없어 Pleos 에뮬레이터가 85분 동안 화면을 띄우지 못했습니다([검토 기록](../docs/analysis/demo-feasibility-2026-11-13.md)). 로컬 PC에서 처음 확인해야 합니다 |
| 주행 중 사용 선언(DO) | 매니페스트에 아직 없음. 선언 방법은 Pleos 문서(가이드 G4·G5) 확인 뒤 추가 |

## 구성

- Gradle 8.14.3(래퍼 포함), Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Jetpack Compose(BOM 2024.10.01), compileSdk 34, minSdk 28.
- Pleos Playground SDK 5종: Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1, SpeechToText 3.3.1, FusedLocation 1.1.3. 저장소는 `settings.gradle.kts`의 Pleos Nexus 공개 주소입니다.
- 차속은 AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED`를 씁니다(`useLibrary("android.car")`, `android.car.permission.CAR_SPEED`는 실행 중 허락).
- `app/src/main/java/app/pleosguard/demo/SdkCheck.kt`: SDK 점검 4가지(Vehicle 연결·브레이크 읽기, 브레이크 구독, 차속, TTS 기기 안 모드 한국어). SDK 콜백은 메인 스레드로 넘기고, 서비스 연결 대기에 5초 제한을 둡니다. null을 false·0으로 바꾸지 않습니다.
- `app/src/test/.../SdkCheckScreenShotTest.kt`: 화면을 `app/build/shots/sdk-check.png`로 그립니다.

## 명령

```text
# Windows PowerShell은 .\gradlew.bat, macOS·Linux는 ./gradlew
./gradlew :app:assembleDebug                                   # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true  # 화면 PNG: app/build/shots/
./gradlew :app:installDebug                                    # 실행 중인 에뮬레이터에 설치
```

Android Studio로 이 `demo-app` 폴더를 열어도 됩니다. Android SDK 위치는 Android Studio가 `local.properties`에 적습니다(이 파일은 Git에 넣지 않습니다).

## 로컬 에뮬레이터에서 처음 할 일

1. [로컬 실행 안내](../docs/local-setup/README.md)대로 Pleos 에뮬레이터 2.1.0을 띄웁니다.
2. `installDebug`로 설치하고 앱을 엽니다.
3. "차량 SDK"를 누르고, Virtual Console에서 브레이크 페달 카드를 바꿔 "브레이크 변화 구독"이 성공으로 바뀌는지 봅니다.
4. "차속"을 눌러 CAR_SPEED 허락 창이 뜨는지, 허락 뒤 값이 오는지 봅니다(에뮬레이터 기본 상태에서는 차속 카드가 없을 수 있습니다).
5. "음성"을 눌러 한국어 합성이 들리는지 봅니다.
6. 결과를 `docs/analysis/demo-feasibility-2026-11-13.md`에 "로컬 확인" 절로 적고 커밋합니다.

`scripts/` 폴더에 adb 보조 명령이 있습니다. 클라우드에서 실행해 보지 못한 명령이므로 처음에는 한 줄씩 실행해 결과를 확인하세요.
