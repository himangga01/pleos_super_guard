# 플레오스 슈퍼 케어 모바일 (0.1.0-demo)

차 앱 "플레오스 슈퍼 케어"를 돕는 안드로이드 폰 앱 "플레오스 슈퍼 케어 모바일"입니다(2026-11-14 사용자가 정한 이름). 차 앱과 따로 빌드하는 별도 APK입니다(패키지 `app.pleossupercare.phone`).
운전자나 동승자가 폰의 마이크 · 가속도 센서 · 카메라로 증상을 기록하면, PC의 모의 서버를 거쳐 차 앱의 증상 기록에 붙습니다.
2026-11-14에 만들었고, 같은 날 폰 에뮬레이터(Android 11)에서 확인했습니다. 아래 "확인한 것과 한계"를 함께 읽어 주세요.

- 연동 규약: [전환 기록 5절](../../docs/analysis/super-care-pivot-2026-11-14.md). 서버 쪽 설명은 [care_server.py](../server/care_server.py) 머리말에 있습니다.
- 측정 값의 근거: [폰 센서 조사](../../docs/analysis/phone-sensing-research-2026-11-14.md)(진동 7.2절, 소리 7.3절, 경고등 6.2절, 연결 8.2절).
- 데모 포트는 **8766**입니다. 규약 문서의 8765는 데모 PC에서 다른 프로그램이 쓰고 있어 2026-11-14에 8766으로 바꿨습니다.

## 들어 있는 것

| 화면 | 하는 일 | 진짜 · 흉내 |
| --- | --- | --- |
| 차와 연결 | 차 화면의 6자리 번호를 넣거나 QR을 찍습니다. 그다음 차 화면에서 [연결]을 눌러야 연결됩니다(2초마다 확인). QR 글(`pleoscare://pair?code=…&server=…`)을 폰 기본 카메라로 찍어도 이 앱이 열립니다 | 서버를 거친 진짜 짝 맺기. QR 읽기는 ZXing(Play 서비스 없이) |
| 홈 | 큰 타일 4개(소리 기록 · 진동 기록 · 경고등 찍기 · 사진 기록)와 정비 영수증, 맨 위에 "차에서 요청한 기록", 아래에 최근 기록과 상태(보내는 중 · 차로 보냄 · 차에서 확인 · 다시 보내기) | 칸 안의 아이콘 · 글자는 칸 크기에 비례합니다(`ui/Tiles.kt`, 차 앱과 같은 규칙) |
| 소리 기록 | "소리 나는 곳에 폰을 가까이" 안내 → 11초 녹음(앞 1초는 버림) → 평균 · 가장 큰 순간 크기, 가장 센 높이, 높이별 크기(64칸), 시간에 따른 크기, 1/3옥타브 | 진짜 마이크. 크기는 **dBFS 상대 크기**(폰 마이크의 최대 입력 = 0, 그래서 음수)이고 실제 소음 크기(dB SPL · dB(A))가 아닙니다 |
| 진동 기록 | "조수석 바닥이나 가운데 콘솔에 폰을 올려 두세요"(운전석 발밑 · 핸들 · 송풍구 · 유리 거치대는 피함) → 3초 뒤 10 · 20 · 30초 측정 → 흔들림(RMS) · 가장 큰 값(g), 가장 센 떨림(Hz), 대역 3개, 봉우리 3개, 그래프 | **실험 기능.** 실제 폰에서는 진짜 센서입니다. 에뮬레이터는 진짜 진동을 만들지 못하므로 "시험 입력 재생"(넣어 둔 14Hz 합성 파일)을 씁니다 |
| 경고등 찍기 | 찍기(또는 사진 불러오기) → 사진 속 켜진 불빛 누르기 → 폰이 색을 고르고 운전자가 확인 → 켜짐 · 깜박임 묻기 → 그 색에 남는 모양 중에서 운전자가 고르기 → 도감 뜻 보기 → 차로 보내기 | 색은 폰이 고르고, **모양은 운전자가 고릅니다**(자동 인식이 아님). 도감은 차 앱과 같은 15개(`logic/WarningCatalog.kt` 사본) |
| 사진 기록 · 정비 영수증 | 종류(차체 손상 · 타이어 상태 · 액체 · 누유 · 기타)와 부위, 메모를 붙여 보냅니다. 영수증은 금액 · 주행거리를 직접 적습니다 | 진짜 카메라. 영수증 글자 읽기(OCR)와 타이어 홈 깊이 재기는 넣지 않았습니다 |
| 차에서 온 알림 | 차 앱이 보낸 "트렁크에 짐이 남아 있지 않나요?" 같은 알림을 중요도 높은 알림과 화면 맨 위 띠로 띄웁니다. [확인했어요]를 누르면 차에 알립니다 | 앱이 떠 있는 동안(앞 2초, 뒤 10초마다) 서버를 봅니다. 푸시(FCM)는 실제 서비스 설계입니다 |
| 설정 | 서버 주소(바꾸기 · 연결 확인), 연결된 차, 연결 끊기, 라이선스 | |

정직성 꼬리표는 차 앱과 같이 회색 점선 테두리로 붙입니다(상대 크기(dBFS) · 실제 소음 크기 아님 · 실험 기능 · 시험 입력 · 안내 예시 · 검토 전 · 자동 인식 아님 · 원인 판단 아님).
부품 고장 판단, 합격 · 불합격 기준, 다른 폰과의 크기 비교, 정확도 숫자는 화면에 내지 않습니다.

## 측정 방법(요약)

- **소리**: 소스는 `UNPROCESSED`(기기가 지원한다고 알릴 때), 아니면 `VOICE_RECOGNITION`. 48kHz(안 되면 44.1kHz) 모노 16비트. 2,048점 창(512 간격)으로 0~8kHz 64칸, 8,192점 창으로 또렷한 봉우리와 1/3옥타브(25Hz~20kHz)를 구합니다. 0.1초마다 크기를 그래프로 그립니다. 크기가 0.05~2초마다 되풀이되면 그 간격을 함께 보입니다.
- **원본 소리**: 기본은 보내지 않습니다. 사용자가 "원본 소리 10초도 보내기"를 켜고, 간단한 목소리 구간 검출에서 말소리가 없을 때만 AAC 64kbps M4A(약 80KB)로 보냅니다. 말소리로 보이면 숫자 결과만 보냅니다(조심하는 쪽으로 기울여, 끊겼다 이어지는 끽 소리도 말소리로 볼 수 있습니다).
- **진동**: `TYPE_ACCELEROMETER` 원값을 400Hz(2,500µs)로 요청합니다(`HIGH_SAMPLING_RATE_SENSORS` 선언). 실제 주기는 타임스탬프 간격의 중앙값으로 재서 `sampleHz`로 보내고, 100Hz 밑이면 `lowQuality: 1`입니다. 400 · 200 · 100 · 50Hz 격자로 고르게 맞춘 뒤 2.56초 Hann 창(50% 겹침)마다 평균(중력)을 빼고 세 축 스펙트럼을 더합니다. 중력 방향이 처음보다 5도 넘게 바뀐 창(폰이 움직임)과 10ms 넘는 빈틈이 든 창은 뺍니다. 대역은 1~5 · 5~20 · 20~80Hz RMS(g)이고, 20~80Hz는 격자 200Hz 이상일 때만 냅니다.
- **경고등 색**: 누른 점을 가운데로 사진 짧은 변의 15% 정사각형을 256화소로 줄여 봅니다. 명도 0.55 이상 · 채도 0.40 이상인 화소만 셉니다. 빨강 0~15° · 340~360°, 주황 16~65°, 초록 70~170°, 파랑 180~260°. 가장 많은 색의 비율이 0.6 밑이거나 화소가 40개 밑이면 "직접 골라 주세요"로 넘깁니다. 화면과 차에 보내는 사진은 누른 불빛 전체가 보이도록 조금 넓게 잘라(최대 512화소, EXIF 없음) 보냅니다.

## 차로 보내는 값(서버 `POST /api/captures`)

공통: `carId, kind, requestId, recordId, startedAt, durationMs, placement, partHint, note, metrics(숫자만), spectrum(64칸 이하), series(200점 이하), candidates`, 더해서 `summary, phoneName, labels, clockOffsetMs`.

| kind | metrics | 그 밖 |
| --- | --- | --- |
| `noise` | `avgDb, peakDb, dominantHz, spectrumMaxHz`(8000) + `samplePeakDb, l10Db, l90Db, centroidHz, lowPct, midPct, highPct, sampleRateHz, clipPct, speechPct, silent, tooClose, peak1Hz~peak3Hz, repeatSec` | `spectrumHz, spectrumUnit: dBFS, seriesStepMs: 100, thirdOctave[], thirdOctaveHz[]`, 켰을 때만 `audioM4aBase64` |
| `vibration` | `rmsG, peakG, dominantHz, spectrumMaxHz` + `sampleHz, lowQuality, gridHz, band1~band3, peak1Hz~peak3Hz, peak1G~, peak1Ratio~, gravityXG/YG/ZG, tiltDeg, windows, movedWindows, gappedWindows` | `spectrumUnit: g, seriesUnit: g`, `labels.quality`(OK · LOW_RATE · MOVED · GAPPED · TEST_INPUT) |
| `warning_photo` | `confidence`(0~1, 색 판정 비율), `litPixels, colorAuto` | `candidates`(도감 id, 좋은 순서), `confirmed`(운전자가 고른 결과 id), `confirmedName`, `imageJpegBase64`(잘라 낸 사진) |
| `photo` · `receipt` | (영수증) `amountWon, mileageKm` | `imageJpegBase64`(최대 1,280화소, EXIF 없음), `labels.category` |

- 에뮬레이터에서 만든 진동 · 사진 기록과 "시험 입력 재생" 기록은 `note`가 **"시험 입력"**입니다. 차 앱은 이 글이면 시험 입력 꼬리표를 붙입니다. 소리는 에뮬레이터에서도 PC 마이크의 진짜 소리이므로 붙이지 않습니다.
- 소리 · 진동 원본 표본은 폰 안에서 분석한 뒤 버리고 서버에 보내지 않습니다. 보내지 못한 기록은 폰에 남겨 두었다가 서버가 다시 닿으면 보냅니다.

## 명령 (Windows PowerShell 기준, `demo-app` 폴더에서)

```text
# 1) 모의 서버(PC 안 127.0.0.1:8766, 에뮬레이터는 http://10.0.2.2:8766)
python demo-app\server\care_server.py            # 저장소 루트에서. --reset 은 실행 중 자료를 지우고 시작
python -I demo-app\server\selftest.py            # 서버 자체 시험(임시 폴더, 65개)

# 2) 폰 에뮬레이터(처음 한 번 create). 차 에뮬레이터 emulator-5554 는 건드리지 않습니다
.\scripts\phone-emulator.ps1 create              # AVD Pleos_SuperCare_Phone(Android 11 Google APIs x86_64)
.\scripts\phone-emulator.ps1 start               # emulator-5556, 뒤 카메라 = 가상 장면, PC 마이크 허용
.\scripts\phone-emulator.ps1 wait

# 3) 빌드 · 설치(폰 프로젝트만, Gradle installDebug 는 쓰지 않습니다)
$env:JAVA_HOME = "<JDK 17 이상>"
.\scripts\phone-emulator.ps1 install             # = phone-app\gradlew.bat :app:assembleDebug + adb -s emulator-5556 install -r
cd phone-app; .\gradlew.bat :app:testDebugUnitTest   # 분석 계산 JVM 시험(23개)

# 실제 폰(USB 또는 무선 adb): 폰 앱 설정의 서버를 http://127.0.0.1:8766 으로
.\scripts\phone-emulator.ps1 reverse -Serial <폰 시리얼>

# 시험 도구
.\scripts\phone-emulator.ps1 vibrate -Hz 14 -Amp 0.3 -Seconds 20   # 에뮬레이터 가속도에 사인파(시험 입력)
.\scripts\phone-emulator.ps1 push-image -Image <그림 파일>          # 폰 사진함에 시험 그림 넣기
.\scripts\phone-emulator.ps1 shot -Out <폴더>                       # 폰 화면 캡처
```

- `adb`는 늘 `-s emulator-5556`(폰) 또는 실제 폰 시리얼을 붙입니다. 차 에뮬레이터는 `emulator-5554`입니다.
- 에뮬레이터 소리 기록에는 PC 마이크가 들어갑니다(`start`가 `-allow-host-audio`로 엽니다. 끄려면 `-NoHostAudio`).
- 사진 불러오기에 넣은 그림이 안 보이면 `adb -s emulator-5556 shell content call --uri content://media --method scan_volume --arg external_primary`로 사진함을 다시 읽힙니다.
- 경고등 시험 그림은 직접 만든 합성 그림을 씁니다(`python -I phone-app\tools\make_warning_test_image.py <파일.jpg>`, 주황 엔진 · 빨강 브레이크). 남의 사진을 쓰지 않습니다. 진동 시험 파일은 `python -I phone-app\tools\make_vibration_sample.py`로 다시 만듭니다.
- 사진 불러오기 창(안드로이드 기본 파일 창)은 이 에뮬레이터에서 adb 누르기를 받지 않았습니다. 손으로 고르거나, 검색 뒤 Tab · Enter 키로 고릅니다.

## 확인한 것과 한계 (2026-11-14, 폰 에뮬레이터 emulator-5556)

확인한 것(차 쪽 동작은 서버 API로 대신했습니다. 차 에뮬레이터에 명령을 보내는 일은 이번 작업에서 허락되지 않았습니다):

- 짝 맺기: 서버 `POST /api/pair/start`(차 대신) → 폰에 번호 입력 → 서버 상태 `pending: true` → `POST /api/pair/confirm`(차 화면 [연결] 대신) → 폰이 2초 안에 홈으로 바뀜.
- 차 요청 → 폰 홈 맨 위 카드 → 소리 기록(마이크 권한을 그 순간에 물음) → 차로 보내기 → 서버에 `requestId` · `recordId`와 함께 들어오고 요청이 `done`으로 바뀜. 48kHz `VOICE_RECOGNITION`, 64칸, 100점, 1/3옥타브 30칸, `clockOffsetMs` 확인.
- 진동 "시험 입력 재생": 14.0Hz, 5~20Hz 0.022g(넣은 값 0.3m/s²와 맞음), `note: "시험 입력"`, `sampleHz` 400.
- 경고등: 가상 장면 카메라로 찍은 초록 네모 → "초록 불빛이 뚜렷합니다" → 도감에 없음 안내와 모든 모양. 사진함의 합성 계기판 그림 → 주황 → 남는 모양 4개 → "엔진 점검 경고" → 서버에 후보 6개 · `confirmed: engine` · 잘라 낸 JPEG.
- 사진 기록(가상 장면, 앞 타이어) 전송, 트렁크 알림(중요도 높은 알림 + 화면 띠 → [확인했어요] → 요청 `done`), 서버의 확인(ack) 뒤 최근 기록이 "차에서 확인"으로 바뀜.

한계:

- **진짜 진동은 재지 못했습니다.** 에뮬레이터 가속도는 표본이 100Hz로 오고, 콘솔로 넣은 9Hz 사인파는 2.4Hz처럼 보였습니다(넣은 값이 게스트에 제때 닿지 않음). 그래서 에뮬레이터 진동 기록은 모두 "시험 입력"으로 보냅니다. 400Hz 요청 · 200Hz 제한은 실제 폰(Android 12 이상)에서 확인해야 합니다.
- **진짜 경고등 사진은 찍지 못했습니다.** 가상 장면 카메라와 합성 그림만 썼습니다. 이 에뮬레이터에서는 CameraX 정지 사진 요청이 30초 넘게 돌아오지 않아, 2.5초 안에 오지 않으면 미리보기 화면을 쓰도록 했습니다(실제 폰은 정지 사진을 씁니다). 색 판정 비율은 사진 30장 이상으로 따로 재야 합니다(폰 센서 조사 6.4절).
- 소리는 PC 마이크의 실제 소리로 확인했지만, 폰마다 마이크가 다르므로 다른 폰과 크기를 견주지 않습니다. 말소리 검출은 간단한 규칙이라 놓치거나 지나치게 잡을 수 있습니다.
- QR 읽기는 JVM 시험(ZXing으로 만든 QR 풀기)만 했고, 카메라로 차 화면 QR을 읽어 보지는 못했습니다. 번호 입력은 확인했습니다.
- 알림 권한(Android 13 이상)과 백그라운드에서 오래 둔 뒤 알림이 오는지는 Android 11 에뮬레이터라 확인하지 못했습니다.

## 폴더

- `app/src/main/java/app/pleossupercare/phone/`: `Controller.kt`(화면 이동 · 짝 맺기 · 요청 받기 · 보내기), `data/`(설정 · 서버 · 기록 목록), `logic/`(FFT · 소리 · 진동 · 경고등 색 · 도감 사본 · QR), `sensing/`(마이크 · 가속도 · 시험 입력 재생 · AAC · 사진), `screens/`, `ui/`(색 · 글꼴 · 아이콘 · 칸 비례 타일 · 그래프 · 카메라).
- `app/src/main/assets/samples/vibration_test_14hz.csv`: 시험 입력 재생 파일(합성, 실측 아님). `tools/make_vibration_sample.py`가 만듭니다.
- 라이선스: Pretendard(SIL OFL 1.1), Lucide 아이콘(ISC), ZXing core 3.5.3 · CameraX 1.3.4(Apache-2.0). 글은 `app/src/main/assets/licenses/`에 있습니다.
