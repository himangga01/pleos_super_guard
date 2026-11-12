# SpeechToText SDK 개발 노트

2026-11-12 안전 기능 보충: [놀란 말의 인식과 내부·외부 마이크 조사](vehicle-safety-capabilities.md)를 추가했다. 새로 받은 3.3.1 파일에서 주 클래스와 결과 리스너를 다시 확인했다. `sendAudio`는 인식기에 소리를 넣는 기능이며 마이크 원본 소리를 꺼내는 기능이 아니다. 인식한 글만으로 사고·감정·운전 의도를 확정하거나 무기한 듣기·녹음을 보장하지 않는다.

기준일: 2026-11-11 KST. 공모전 사용 가능, 배포 기준 **3.3.1**. 마이크·PCM 인식·서버 인증은 미실행이다. [전체 리뷰](../analysis/pleos-sdk-review.md), [문서 전체 목차](README.md).

## 1. 역할과 의존성

Speech Service를 통해 발화를 텍스트로 인식한다. 발화를 인식하는 것과 앱 기능을 실제 실행하는 것은 별개다. 문서의 진입점은 `ai.pleos.playground.stt.SpeechToText`다. [소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro), [의존성](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/importing-sdk)

```text
ai.pleos.playground:SpeechToText:3.3.1
```

공개 POM·AAR에서 초기화·마이크 요청·오디오 입력·입력 종료 메서드를 확인했다. Pleos 전이 의존성은 `ai.pleos.playground.security:universal-access:0.6.0`을 포함한다. [POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/SpeechToText/3.3.1/SpeechToText-3.3.1.pom)

## 2. 모드와 두 입력 경로

| 구분 | 계약 |
| --- | --- |
| HYBRID | 기본 모드. 서버 인식 우선, 네트워크 연결 실패 시 기기 내 엔진으로 전환 |
| SERVER | 서버 인식 전용. 인증·네트워크 필요 |
| ON_DEVICE | 기기 내 인식. 이 모드만 쓰면 서버 앱 등록 불필요 |
| `request()` | 차량 마이크 입력을 시작 |
| `sendAudio(ByteArray)` | 앱이 가진 PCM 입력. **ON_DEVICE 전용** |
| `completeAudioSend()` | 마지막 PCM 입력 뒤 반드시 호출 |

[마이크 시작·중지](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/start-and-stop), [오디오 데이터 입력](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/sending-audio-data)

공모전에서는 차량 마이크를 외부 음성 인식 업체로 전송하는 방식을 사용할 수 없다. 일반 Gleo 문서를 봤다는 이유로 공모전의 Gleo 승인 신청 제한을 생략해서도 안 된다. [공모전 개발 범위](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008)

## 3. 초기화·권한·인증

`pleos.car.permission.STT_SERVICE`를 선언하고 Context·Mode로 객체를 만든다. 초기화 성공 후 `ResultListener`를 등록하고 언어를 설정한다. 서버 모드는 My Project 자격정보로 등록·연결을 마친 뒤 인식을 요청한다. 종료 시 마이크 사용을 중지하고 동일 리스너를 제거한 다음 `release`한다. 다시 쓰려면 새 객체를 생성한다.

[권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions), [초기화](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/initialization-and-release), [서버 등록](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/app-registration)

## 4. 인식 결과와 오디오 수치

`onUpdated(stt, completed)`에서 completed=false는 중간 결과, true는 완료 결과다. 중간 결과를 매번 새 메모로 저장하면 중복될 수 있으므로 세션 상태와 완료 여부를 구분한다. `onEndedRecognition`은 인식 수명 종료 이벤트다. [리스너](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/result-listeners)

**2026-11-12 정정.** 바이트코드로 서비스 이벤트와의 대응을 확인했다. `onStartedRecognition`은 발화 시작을 감지했을 때(EPD_ON), `onEndedRecognition`은 발화 끝을 감지했을 때(EPD_OFF) 불린다. 위 문장의 "인식 수명 종료"보다 좁은 뜻이다. `stop()`을 부르면 이 이벤트가 오는지는 서비스 구현에 달려 있다.

**결과 형식(2026-11-12 확인).** 공개 결과는 `onUpdated(stt: String, completed: Boolean)`의 글 하나와 완료 여부뿐이다. 신뢰도, 여러 후보(n-best), 언어 코드, 화자 정보는 오지 않는다. 이런 값은 특권 앱 전용 리스너에만 있다. 연속 인식 설정이나 무음 시간 설정 인자도 없다. 그래서 앱은 짧은 닫힌 선택지와 유사어 목록으로 뜻을 맞추고, 맞지 않으면 다시 묻고 화면 버튼을 함께 보여 준다.

EPD 음성 시작·종료 위치는 입력 버퍼의 **바이트 위치**다. 시간 단위로 바로 읽으면 안 된다. RMS는 신호 크기이고 dB가 아니다.

PCM 예제의 3,200바이트는 16kHz·16bit·mono 오디오 100ms 분량이다.

```text
16,000 samples/s × 2 bytes/sample × 1 channel × 0.1 s = 3,200 bytes
```

다른 형식에도 같은 청크 크기를 무조건 쓰지 않는다. 예제의 `Thread.sleep(100)`을 앱 UI 스레드에 옮기면 화면을 멈추게 할 수 있으므로 전송 작업의 실행 위치를 정해야 한다. 이 문서는 지원하는 모든 오디오 형식·처리량을 검증한 결과가 아니다.

## 5. 언어·오류·시험 과제

언어 표기는 `ko-KR`, `en-US`, `fr-CA`, `es-US`, `es-MX`다. 요청 전에 언어를 정한다. [언어 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/set-locale)

`ErrorType`으로 초기화·등록·인식 오류를 처리한다. 마이크 차단, 권한, 인증, 모드 미지원, 입력 오디오, 네트워크·시간 초과, 기기 내 엔진 실패를 구분한다. [오류](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/error-code)

**2026-11-12 정정과 보충.**

- `STT_SERVICE` 권한이 없으면 `ErrorType.PERMISSION_DENIED` 콜백보다 먼저, 모든 공개 메서드가 `SecurityException("Permission pleos.car.permission.STT_SERVICE is required to perform this action.")`을 바로 던진다. 호출부에 `try/catch`가 필요하다. 권한의 보호 수준은 문서·바이너리 어디에도 적혀 있지 않다.
- `sendAudio`는 ON_DEVICE가 아닌 모드에서 부르면 로그만 남기고 오류 콜백 없이 무시된다.
- `getCallApi()`(통화 음성 인식)와 `getAmbientAi()`(호출어·어휘 강화·화자 인식 등)는 시스템 앱이나 지정된 Pleos 패키지만 쓸 수 있는 특권 API다. 일반 앱에서는 `SecurityException`이 난다.
- 마이크 차단 안내 팝업을 여는 공개 API는 없다. `MIC_ERROR`가 오면 앱 화면에서 차량의 센서 개인정보 설정을 안내한다.
- 음성 서비스 최소 버전 메타데이터는 4.1.29다. 에뮬레이터에서는 Extended Controls의 Microphone 메뉴에서 호스트 오디오 입력을 켜야 한다. [에뮬레이터 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator)

미실행 과제는 ON_DEVICE 고정 PCM 인식, 마이크의 센서 개인정보 설정, 중간·최종 결과, stop 후 재시작, 입력 종료 통지, 권한·인증 실패, 리스너 중복 제거다. SDK가 만들어 주는 것은 텍스트이므로 그 뒤 앱 작업 실행은 별도로 구현해야 한다.

## 6. 2026-11-12 후속: 플레오스 가드 적용

- "말로 기록·말로 찾기"는 사용자가 버튼을 누른 뒤에만 `request()`로 듣는다. 계속 듣는 기능은 만들지 않는다. Pleos 내비 앱 가이드도 마이크를 음성 입력 때만 쓰도록 한다.
- ON_DEVICE 모드로 고정해 외부 서버로 소리를 보내지 않는다. 차량 마이크 음성을 외부 음성 인식 서비스로 보내는 방식은 공모전에서 금지돼 있다.
- 인식한 글은 증상 메모와 경고등 문구 찾기의 검색어로만 쓴다. 감정·사고 원인·운전 의도를 판정하지 않는다.
- TTS가 말하는 동안(`onStart`부터 `onDone`·`onStop`까지)은 인식을 멈춰 자기 음성을 다시 알아듣지 않게 한다.
- LLM SDK는 공모전에서 제공되지 않으므로 뜻 이해를 맡길 수 없다. [LLM 기록](llm.md)
- 근거: [종합 리뷰 13절](../analysis/pleos-sdk-review.md#13-2026-11-12-후속-확인-전수-분석과-정정), [후속 확인 기록](evidence/sdk-followup-2026-11-12.json). 인식은 여전히 미실행이다.
