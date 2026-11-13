# 로컬 PC에서 이어 하기: Pleos 에뮬레이터·데모 앱·시연 영상

작성일: 2026-11-13. 클라우드 작업 환경에는 하드웨어 가속(KVM)이 없어 Pleos 에뮬레이터를 실행할 수 없었습니다([검토 기록](../analysis/demo-feasibility-2026-11-13.md)). 사용자가 에뮬레이터 실행과 녹화를 로컬 PC에서 하기로 정했습니다. 이 문서는 로컬에서 이어 할 순서입니다. 아래 명령은 Pleos 공식 문서와 공모전 안내를 옮긴 것이며, **이 저장소 작업 중에 로컬에서 실행해 본 기록은 아직 없습니다.**

## 0. 로컬 Claude Code로 이어 할 때

저장소를 내려받은 폴더에서 Claude Code를 열고 이렇게 요청하면 됩니다.

> `docs/HANDOFF.md`, `docs/local-setup/README.md`, `demo-app/README.md`를 읽고 데모 앱 작업을 이어서 해 줘.

이 저장소에는 큰 파일(에뮬레이터 이미지, 빌드 결과)이 없습니다. 에뮬레이터 이미지는 아래 2장대로 Android Studio가 내려받습니다.

## 1. 준비물

| 항목 | 내용 |
| --- | --- |
| PC | Windows 10·11(x86_64) 또는 macOS(Apple Silicon). 공모전 안내의 AVD 메모리 설정은 4GB입니다. 시스템 이미지 압축 파일만 약 4.1GB, 풀면 약 19GB이므로 디스크 여유를 30GB 이상 두는 것을 권합니다(클라우드에서 잰 값) |
| 가속 | Windows: BIOS의 가상화(Intel VT-x·AMD-V)를 켜고, Windows 하이퍼바이저 플랫폼(WHPX) 또는 Android Emulator hypervisor driver(AEHD)를 씁니다. macOS는 기본 가속(HVF)을 씁니다 |
| 도구 | Android Studio(최신 안정판), Android SDK Platform 34, Platform-Tools(adb). 데모 앱은 Gradle 래퍼가 들어 있어 Gradle을 따로 설치하지 않아도 됩니다(JDK는 Android Studio 내장 JBR 사용) |
| 녹화 | 에뮬레이터 자체 녹화(`adb emu screenrecord`, Extended Controls의 Record and Playback) 또는 OBS Studio(음성 안내 소리까지 담으려면 OBS 권장) |

## 2. Pleos 에뮬레이터 2.1.0 설치

출처: [에뮬레이터 설치·실행](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator), 공모전 개발 환경 안내. 자세한 원문 정리는 [공모전 조사 12장](../analysis/hyundai-vehicle-app-contest-2026.md)에 있습니다.

1. Android Studio → Settings → Languages & Frameworks → Android SDK → **SDK Update Sites**에서 `+`를 누르고 아래 주소를 추가합니다.
   - Windows·Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - Mac(Apple Silicon): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
2. **SDK Platforms**에서 "Show Package Details"를 켜고 Android 14(API 34)의 "Pleos Connect v2.1.0 … System Image"를 설치합니다. 클라우드에서 확인한 Windows·Linux용 패키지 경로는 `system-images;android-34;pleos_car_emulator;x86_64`, 압축 파일은 4,087,751,964바이트였습니다.
3. Device Manager → Create Virtual Device → **Automotive** → "Pleos Connect v2.1.0" 기기 → 위 시스템 이미지를 고릅니다. 고급 설정에서 RAM 4GB, Boot option은 **Cold boot**로 둡니다(Virtual Console은 cold boot로 만든 AVD에서 씁니다).
4. 에뮬레이터를 실행합니다. 첫 부팅은 오래 걸릴 수 있습니다. 화면이 뜨면 `adb devices`에 `emulator-5554 device`로 보여야 합니다.
   - "unauthorized"로 보이면 에뮬레이터 화면에 뜨는 USB 디버깅 허용 창을 확인하세요. 이 이미지는 사용자용 빌드(`ro.build.type=user`, `ro.adb.secure=1`)여서 인증이 필요합니다.

## 3. Virtual Console과 주행 제한

출처: [Virtual Console](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator), [Virtual Console 사용 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide).

```text
adb forward tcp:8088 tcp:8088        # 그다음 브라우저에서 http://127.0.0.1:8088/
adb shell cmd hmg_car_service --service drivingstate driving_regulation on
```

- Virtual Console에서 브레이크 페달, 문, 방향지시등, 좌석 점유, 주행거리, 조향각 등을 바꿀 수 있습니다. 카드 이름과 속성 대조는 [Vehicle API 목록](../sdk/vehicle-api-catalog.md)에 있습니다.
- 차속, 에어백 전개, 안전벨트처럼 카드가 없거나 확인되지 않은 값은 데모 앱의 시연 모드 시험 입력으로 넣고 화면에 [시험 입력]을 붙입니다(설계 기준).
- 주행 제한을 끄는 명령은 공식 문서에서 확인하지 못했습니다. `demo-app/scripts`의 `drive-off`는 `on`을 `off`로 바꾼 추정 명령입니다. 안 되면 에뮬레이터를 다시 시작하세요.

## 4. 데모 앱 빌드·설치·점검

```text
cd demo-app
.\gradlew.bat :app:installDebug          # Windows (macOS·Linux는 ./gradlew)
```

- 앱 "플레오스 가드 데모"를 열면 SDK 점검 화면이 나옵니다. 순서와 기대 결과는 [demo-app/README.md](../../demo-app/README.md)의 "로컬 에뮬레이터에서 처음 할 일"에 있습니다.
- 점검 결과(성공·실패 메시지)를 [데모 검토 기록](../analysis/demo-feasibility-2026-11-13.md)에 "로컬 확인" 절로 남기고 커밋해 주세요. 이 결과가 데모 흐름을 만들 때의 출발점입니다.

## 5. 데모 흐름 만들기(다음 작업)

설계 기준은 [PRD v4.2](../analysis/integrated-vehicle-care-2026-11-12.md), [화면 명세 v4.2](../analysis/ux-v4-section.md), [개발 계약](../specs/vehicle-care/README.md), 화면 시안(`output/submission/assets/v4/`)입니다. 시연 각본은 [별첨](../submission/annex-v4/annex.md)의 에뮬레이터 시연 계획 장과 같은 대표 사례(21:00:03 에어백 신호 → 21:00:13 발송 → 보호자 2 문자 21:00:23 등)를 씁니다.

제안 범위(약 3분 영상 분량): 홈(세로 한 창) → 경고등 찾기(색·모양) → 지금 기록(차량 상태 시간표) → 확인 질문(화면·말로 답하기) → 감속 실패 대처(아반떼 실사 뒷모습 + 페달 그림, 1초에 한 장 이하 단계형 애니메이션, 음성) → 보호자 발송 확인 10초 → 모의 발송. 서버·카카오톡·문자는 모의로 둡니다.

## 6. 시연 영상 녹화

1. 에뮬레이터 창 크기와 세로 한 창 배치를 정하고, 앱을 시연 시작 화면에 둡니다.
2. 녹화 방법 중 하나를 고릅니다.
   - 에뮬레이터 자체 녹화: `adb emu screenrecord start --time-limit 180 <저장 경로>.webm` → 시연 → `adb emu screenrecord stop`. 소리는 담기지 않을 수 있습니다.
   - OBS Studio: 에뮬레이터 창과 PC 소리(음성 안내)를 함께 녹화합니다.
3. 영상에는 에뮬레이터 화면임과 [시험 입력]이 보이게 하고, 실차 동작으로 오해할 표현을 넣지 않습니다. 공모전 안내상 시연 영상은 목업·화면 녹화도 인정됩니다.
4. 영상과 APK를 공모전 가산점에 쓰려면 **새 GitHub 저장소**를 만들어 소스, `apk/` 폴더의 APK, README 필수 항목([템플릿](../submission/github-readme-template.md))을 넣습니다. 이 작업 저장소는 공개 저장소이고 이력에 개인 경로가 있으므로 제출용으로 쓰지 않습니다.

## 7. 마감과 남은 결정

- 접수 마감: 2026-11-15(일) 23:59 KST. 제출 문서(공식 양식 14쪽, 별첨 45쪽)는 이미 완성돼 있습니다([HANDOFF 3장](../HANDOFF.md)).
- 남은 결정: 데모 영상·새 저장소를 낼지, APK 파일명 규칙(개인은 성명)과 실명 비노출 안내의 충돌(운영사무국 문의).
