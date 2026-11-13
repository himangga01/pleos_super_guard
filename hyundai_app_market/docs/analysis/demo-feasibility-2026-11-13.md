# 데모 앱·시연 영상 가능 여부 검토 (2026-11-13)

사용자 요청: 지금 설계 문서를 바탕으로 데모 앱과 시연 영상을 만들 수 있는지, 에뮬레이터를 올려 앱을 돌릴 수 있는지 먼저 확인합니다. 이 문서는 클라우드 작업 환경에서 직접 실행해 본 결과입니다. 앱 기능 구현은 아직 하지 않았습니다.

## 1. 결론

| 항목 | 결과 | 근거 |
| --- | --- | --- |
| 데모 앱 빌드 | **가능** | Pleos SDK 5종(Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1, SpeechToText 3.3.1, FusedLocation 1.1.3)을 넣은 시험 APK가 약 2분 만에 빌드됨 |
| 실제 앱 화면 캡처(에뮬레이터 없이) | **가능** | Jetpack Compose 화면을 Robolectric과 Roborazzi로 JVM에서 그려 PNG로 저장함. 한글도 바르게 나옴 |
| 이 클라우드 환경에서 Pleos 에뮬레이터 실행 | **사실상 불가** | 하드웨어 가속(KVM)이 없어 소프트웨어 방식(TCG)으로만 돎. {BOOT_RESULT} |
| 실제 SDK 호출 확인(Virtual Console 값 수신 등) | **이 환경에서는 불가** | 에뮬레이터가 부팅되지 않아 확인 못 함. 가속되는 PC에서 확인해야 함 |
| 시연 영상 | **조건부 가능** | (가) 사용자 PC의 에뮬레이터 녹화, 또는 (나) 실제 앱 코드로 그린 화면을 이어 붙인 자막 영상. (나)는 실행 녹화가 아니므로 영상에 표시해야 함 |

## 2. 실행 환경과 확인 내용

- 작업 환경: x86_64 Intel Xeon 2.1GHz 4코어, 메모리 15GB. `/dev/kvm` 없음, CPU 가상화 플래그(vmx·svm) 없음. 에뮬레이터의 가속 확인 결과는 "KVM requires a CPU that supports vmx or svm"입니다.
- 네트워크: Android SDK 저장소(dl.google.com), Google Maven, Pleos Nexus 저장소, Gradle 배포 서버, Maven Central(repo.maven.apache.org)에 접근됩니다.
- 설치한 도구: Android Emulator 37.2.13(안정판), platform-tools 37.0.3, cmdline-tools 23.0, platform 34, build-tools 34.0.0. 기존 Gradle 8.14.3과 Java 21을 썼습니다.

### 2.1 Pleos 에뮬레이터 이미지

- 공식 저장소 목록(`connect-avd/2.x/crp-sys-img.xml`)의 Windows·Linux용 패키지는 `system-images;android-34;pleos_car_emulator;x86_64`("Pleos Connect v2.1.0") 하나입니다. Mac용 목록은 arm64 이미지입니다.
- 내려받은 압축 파일은 4,087,751,964바이트(목록의 크기와 같음)이고 SHA-256은 `75a00230648d37c65a0233f5e17024bd61f45aa5a79f9a7dfbe3de9b8f69edbb`입니다. 압축을 풀면 `system.img`만 18.26GB입니다. 빈 영역을 건너뛰어(sparse) 풀어 디스크에는 7GB를 썼습니다.
- `build.prop` 기준으로 사용자용 빌드입니다(`ro.build.type=user`, `ro.adb.secure=1`, `ro.debuggable=0`). 화면은 2560×1440, 160dpi이고 제조사 표시는 42dot입니다.

### 2.2 가속 없이 부팅해 본 결과

- `-accel off -no-window -gpu swiftshader_indirect`로 실행하면 에뮬레이터는 시작됩니다. QEMU 프로세스는 CPU 1코어 남짓(약 100%)만 씁니다.
- 첫 시도에서 커널과 adb 데몬은 약 2분 반 만에 올라왔지만 adb 상태가 "unauthorized"였습니다. adb 공개 키를 에뮬레이터 설정 폴더에 넣고(`androidboot.qemu.adb.pubkey`로 전달됨을 로그에서 확인) 다시 시작했습니다.
- 두 번째 시도: {BOOT_DETAIL}
- 따라서 이 환경에서는 앱 설치, Virtual Console(에뮬레이터 안 8088 포트 웹 화면) 조작, 화면 녹화를 시험하지 못했습니다.

### 2.3 데모 앱 빌드 시험

- 최소 Android 프로젝트(AGP 8.7.3, Kotlin 2.0.21, compileSdk 34, minSdk 28)에 Pleos Nexus 저장소와 SDK 5종을 넣어 `assembleDebug`가 성공했습니다. 디버그 APK는 29.7MB이며, 음성 SDK의 보안 모듈(`universal-access`)이 기기 종류별 원시 라이브러리를 함께 넣어 커졌습니다.
- 같은 프로젝트에 Jetpack Compose, Robolectric 4.13, Roborazzi 1.32.2를 넣고 확인 질문 화면을 JVM에서 그려 PNG로 저장했습니다. 에뮬레이터 없이 실제 앱 코드의 화면을 만들 수 있습니다. 다만 이 방식은 Pleos 차량 서비스와 연결되지 않으므로 SDK 값은 시험 입력으로 넣어야 합니다.

### 2.4 영상 도구

- ffmpeg와 Chromium(Playwright)은 있습니다. 한국어 음성 합성 도구는 없어 해설 음성은 만들 수 없고, 자막 영상은 만들 수 있습니다.

## 3. 선택지

| 선택지 | 하는 일 | 장점 | 한계 |
| --- | --- | --- | --- |
| A. 데모 앱은 여기서, 실행·녹화는 사용자 PC에서 | 여기서 APK와 시연 순서표, Virtual Console 입력 목록을 만듦. 사용자가 가속되는 PC(Windows·Mac)에서 Pleos 에뮬레이터 2.1.0으로 실행·녹화 | 실제 SDK 수신과 Virtual Console 시험을 그대로 보여 줌. 2차 PT와 같은 방식 | 사용자 PC에서 Android Studio·에뮬레이터 설치와 녹화가 필요함. SDK 동작은 사용자 PC에서 처음 확인됨 |
| B. 여기서 앱 화면 렌더링 영상 | 실제 앱 코드로 시연 순서의 화면을 그려 자막 영상으로 이어 붙임 | 사용자 작업 없이 만들 수 있음 | 실행 녹화가 아님. "앱 화면 렌더링 · 시험 입력" 표시 필요. SDK 실제 동작을 증명하지 못함 |
| C. 클릭형 HTML 체험판과 녹화 | v4.2 화면 시안으로 클릭 체험판을 만들고 브라우저로 녹화 | 가장 빠름 | 앱이 아닌 목업. 공모전 공지상 목업 영상도 인정되지만 기술 신뢰도는 가장 낮음 |

- 공모전 가산점은 시연 영상(목업·화면 녹화 인정)과 새 GitHub 저장소(소스와 `apk/` 폴더의 APK)입니다. A와 B는 같은 데모 앱 소스를 쓰므로, 소스 저장소 가산점에도 함께 쓸 수 있습니다.
- 마감은 2026-11-15(일) 23:59 KST입니다. 이 문서 작성 시점(2026-11-13 낮)부터 약 2.5일 남았습니다.

## 4. 데모 앱 최소 범위(제안)

설계 문서의 [시연] 기능 가운데 한 흐름만 만듭니다. 홈 → 경고등 찾기(색·모양) → 지금 기록(차량 상태 시간표) → 확인 질문(화면·말로 답하기) → 긴급 안내(페달 그림 1fps 이하 단계형 애니메이션, 음성) → 보호자 발송 확인 10초 → 모의 발송입니다. SDK 값이 없으면 시연 모드의 시험 입력을 쓰고 화면에 [시험 입력]을 붙입니다. 서버·카카오톡·문자 발송은 모의로 둡니다.
