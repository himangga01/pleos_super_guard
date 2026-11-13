# 플레오스 가드 데모 앱 (0.2.0-demo)

Pleos 에뮬레이터에서 시연할 차량 앱입니다. 2026-11-13 클라우드에서 뼈대(SDK 점검 화면)를 만들고, 같은 날 저녁 사용자 로컬 PC에서 시연 흐름을 넣어 에뮬레이터로 확인했습니다. 서버·휴대폰 앱은 없고, 보호자 발송은 앱 안 모의 상태입니다.

## 들어 있는 것

| 화면 | 설명 | 데이터 |
| --- | --- | --- |
| 홈(정차 중) | 아반떼 그림 부위 점, 차가 알려 준 점검 항목, 지금 기록 · 도움이 필요해요 | 점검 항목은 [시험 입력] |
| 홈(주행 중) | 큰 버튼 3개(지금 기록 · 말로 하기 · 도움이 필요해요), 실제 차속·브레이크 한 줄, 급가속 후보 기록 줄 | CarUxRestrictions로 자동 전환 |
| 경고등 찾기 · 설명 | 색 → 모양 → 켜짐·깜박임, 운행 판단 한 줄 | 대표 사례(주황·타이어 공기압)만, "안내 예시 · 검토 전" |
| 지금 기록 | 기록 시점 앞 20초·뒤 10초의 차속 · 브레이크 · 바퀴 방향 · 방향지시등 시간표, 말로 메모 | 실제 SDK 값(없으면 비움) |
| 상황 선택 | 주행 중 5개(안 멈춰요 · 저절로 빨라져요 · 사고가 났어요 · 차체 떨림 · 타는 냄새) | |
| 확인 질문 | "차가 멈추지 않나요?" 화면·음성, 버튼 또는 말(8초)로 답함 | 브레이크 1초 + 감속 없음, 15km/h 아래 제외 |
| 감속 실패 대처 · 원치 않는 가속 안내 | 아반떼 뒷모습 사진 + 페달 그림 3장면(1초에 한 장 이하, 반복 없음), 음성 단계 | 안내 문장은 "문장 자리"(설명서 검토 전) |
| 받는 사람 확인 · 발송 확인 10초 · 보호자 알림 상태 | 모의 발송, 접수 → 전달 → 열람, 알림톡 10초 뒤 문자 | 모의 |
| 시연 패널 | 오른쪽 위 "에뮬레이터 시연"을 누르면 열림. 주행 상태·차속 시험 입력, 센서 조합 시험, 에어백 신호 시험, SDK 점검, 실제 신호 값 | |

- 판정 규칙: `app/src/main/java/app/pleosguard/demo/logic/Rules.kt`(rules-lab 2.2.0 값 + 급발진 조사 값). 근거는 [급발진 조사](../docs/analysis/sudden-acceleration-2026-11-13.md).
- SDK: Vehicle 2.3.2(브레이크·조향·방향지시등), AAOS `PERF_VEHICLE_SPEED`(CAR_SPEED), `CarUxRestrictions`, TextToSpeech·SpeechToText 3.3.1(ON_DEVICE, 한국어). 매니페스트에 `distractionOptimized`를 선언했습니다.
- 자산: 차량 사진(현대자동차 보도자료 공식 사진, 번호판 지움), 페달 그림(AI 설명 그림 시안), Pretendard(OFL), Lucide 아이콘(ISC). 라이선스 글은 `app/src/main/assets/licenses/`.

## 명령 (Windows PowerShell 기준)

```text
$env:JAVA_HOME = "<JDK 17 이상>"; $env:ANDROID_SERIAL = "emulator-5554"
.\gradlew.bat :app:assembleDebug
adb -s emulator-5554 install -r -g app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat :app:testDebugUnitTest -Proborazzi.test.record=true   # 판정·시나리오·화면 시험, 화면 PNG는 app\build\shots\
```

- 휴대폰이 adb에 함께 붙어 있을 수 있으므로 늘 `-s emulator-5554`(또는 `ANDROID_SERIAL`)를 씁니다. Gradle `installDebug`는 연결된 모든 기기에 설치하므로 쓰지 않습니다.
- Windows 사용자 폴더 이름에 한글이 있으면 테스트가 실패하던 문제는 `gradle.properties`(`-Dfile.encoding=COMPAT`)와 `app/build.gradle.kts`(테스트 임시 폴더)로 고쳤습니다.
- 앱 창 오른쪽 가장자리의 "⋮" 손잡이를 끌면 앱이 세로 한 창(841×1210dp)이 되고 옆에 내비가 붙습니다. 시안 크기와 같습니다.

## 급발진 시나리오 재생 (Virtual Console)

```text
adb -s emulator-5554 forward tcp:8088 tcp:8088
python scripts\vc_scenario.py --list
python scripts\vc_scenario.py --dry-run sua-mat-entrapment
python scripts\vc_scenario.py sua-mat-entrapment
```

- 브라우저의 Virtual Console 페이지는 닫고 재생합니다. 페이지가 열려 있으면 페이지의 물리 계산이 값을 덮어씁니다. 재생기는 콘솔 하트비트를 직접 보냅니다.
- 시나리오 8개와 기대 결과는 `scenarios/sua-scenarios.json`, 근거는 [급발진 조사 8절](../docs/analysis/sudden-acceleration-2026-11-13.md)에 있습니다. `ScenarioReplayTest`가 같은 파일로 판정을 확인합니다.
- 시연 추천: `sua-mat-entrapment`(확인 질문 → "안 멈춰요" → 감속 실패 대처), `sua-road-misapplication`(질문 없음 → 운전자가 "저절로 빨라져요"로 알림), `sua-parking-misapplication`(저속·PMSA 구간, 기록만).

## 시험 입력 (디버그 빌드만)

```text
adb -s emulator-5554 shell am broadcast -a app.pleosguard.demo.TEST --es cmd airbag
```

`cmd`: `speed-on`(`--es kmh 60`), `speed-off`, `prompt`(센서 조합 시험), `airbag`, `drive`, `park`, `real`, `home`, `answer`(`--es text '안 멈춰요'`), `case-time`(`--es at 21:00:03`), `real-time`, `tab`(`--es screen RECORD`). 여기서 넣은 값은 화면에 [시험 입력]으로 표시되고, 섞인 사건은 모의 발송만 합니다.

- 글에 띄어쓰기가 있으면 기기 셸이 나누므로 `adb shell "am broadcast ... --es text '앞 타이어 쪽 끼익 소리'"`처럼 한 줄로 감쌉니다.
- **대표 사례 시각**(`case-time`, 시연 패널의 "시각" 버튼): 문서 대표 사례와 같은 시각(21:00:03 에어백 신호, 08:42:15 소리 기록)이 화면에 나오도록 표시용 시계를 옮깁니다. 제출 문서 그림(`output/submission/assets/app-demo/`)을 찍을 때 썼고, 실제 시각이 아님을 문서 그림 설명과 검증 기록에 남겼습니다.
- 대표 사례 소리 기록 장면은 `python scripts\vc_scenario.py case-record`를 재생하며 20초 시점에 "지금 기록"을 누르면 됩니다(08:42:15, 시속 14km, 브레이크 밟음).

## 확인한 것과 남은 것

- 에뮬레이터 확인 결과: [데모 검토 기록 5절](../docs/analysis/demo-feasibility-2026-11-13.md), [급발진 조사 8·10절](../docs/analysis/sudden-acceleration-2026-11-13.md).
- 남은 것: 화면 녹화, STT 인식 글 확인, 아반떼 설명서 비상 절차 문장, 가로 배치 전용 화면(지금은 넓은 창에서 가운데 960dp로 그림), 정비소 화면(설계만).
