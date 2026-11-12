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

EPD 음성 시작·종료 위치는 입력 버퍼의 **바이트 위치**다. 시간 단위로 바로 읽으면 안 된다. RMS는 신호 크기이고 dB가 아니다.

PCM 예제의 3,200바이트는 16kHz·16bit·mono 오디오 100ms 분량이다.

```text
16,000 samples/s × 2 bytes/sample × 1 channel × 0.1 s = 3,200 bytes
```

다른 형식에도 같은 청크 크기를 무조건 쓰지 않는다. 예제의 `Thread.sleep(100)`을 앱 UI 스레드에 옮기면 화면을 멈추게 할 수 있으므로 전송 작업의 실행 위치를 정해야 한다. 이 문서는 지원하는 모든 오디오 형식·처리량을 검증한 결과가 아니다.

## 5. 언어·오류·시험 과제

언어 표기는 `ko-KR`, `en-US`, `fr-CA`, `es-US`, `es-MX`다. 요청 전에 언어를 정한다. [언어 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/set-locale)

`ErrorType`으로 초기화·등록·인식 오류를 처리한다. 마이크 차단, 권한, 인증, 모드 미지원, 입력 오디오, 네트워크·시간 초과, 기기 내 엔진 실패를 구분한다. [오류](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/error-code)

미실행 과제는 ON_DEVICE 고정 PCM 인식, 마이크의 센서 개인정보 설정, 중간·최종 결과, stop 후 재시작, 입력 종료 통지, 권한·인증 실패, 리스너 중복 제거다. SDK가 만들어 주는 것은 텍스트이므로 그 뒤 앱 작업 실행은 별도로 구현해야 한다.
