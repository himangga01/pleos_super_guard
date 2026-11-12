# TextToSpeech SDK 개발 노트

기준일: 2026-11-11 KST. 공모전 사용 가능, 배포 기준 **3.3.1**. 음성 합성·오디오 출력·서버 인증은 미실행이다. [전체 리뷰](../analysis/pleos-sdk-review.md), [문서 전체 목차](README.md).

## 1. 역할과 의존성

텍스트를 Speech Service에 전달해 차량 오디오로 합성·재생한다. 문서의 진입점은 `ai.pleos.playground.tts.TextToSpeech`다. [소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro), [의존성](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/importing-sdk)

```text
ai.pleos.playground:TextToSpeech:3.3.1
```

공개 POM·AAR와 `initialize`, `speak` 등 클래스 서명을 확인했다. Pleos 전이 의존성에는 `ai.pleos.playground.security:universal-access:0.6.0`이 있다. 이는 자격정보를 대신 제공한다는 뜻이 아니다. [공개 POM](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/TextToSpeech/3.3.1/TextToSpeech-3.3.1.pom)

## 2. 모드와 등록 조건

| 모드 | 처리 방식 | 조건 |
| --- | --- | --- |
| `HYBRID` | 기본값. 서버 우선, 네트워크 연결 실패 시 기기 내 엔진으로 전환 | 서버 이용에는 앱 등록·연결 필요 |
| `SERVER` | 서버 합성 전용 | 등록·네트워크 필요. 기기 내 자동 전환 없음 |
| `ON_DEVICE` | 기기 내 합성 | 서버 앱 등록 불필요. 실행환경의 엔진 지원 필요 |

초기 오프라인 경로를 검증하려면 모드를 명시하는 것이 좋다. HYBRID라는 이름만으로 인증 오류까지 모두 자동 복구된다고 가정하지 않는다.

## 3. 초기화부터 종료까지

1. `pleos.car.permission.TTS_SERVICE`를 매니페스트에 선언한다.
2. 애플리케이션 Context·Mode로 객체를 생성한다.
3. `initialize(OnInitializeListener)`의 성공·실패를 처리한다.
4. `EventListener`를 등록하고 언어를 설정한다.
5. 서버 모드라면 My Project 자격정보로 `registerApp`하고 `onConnected`를 기다린다.
6. `speak`를 호출하고 재생·완료·오류를 이벤트로 확인한다.
7. 필요할 때 `stop`, 종료할 때 동일 리스너 해제와 `release`를 호출한다.

[권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/declaring-permissions), [초기화·해제](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/initialization-and-release), [앱 등록](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/app-registration)

## 4. 합성·언어·이벤트

`speak(text, isFinal)`에서 기본 완결 요청은 `isFinal=true`다. 문장을 나눠 전달하면 앞 조각은 false, 마지막 조각은 true로 표시한다. Java에서 확인한 실제 메서드는 String·boolean 두 인자를 갖는다. 진행 중 합성과 재생을 중지하면 `onStop`으로 결과를 받는다. [합성 시작·중지](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/start-and-stop)

지원 언어 표기는 `ko-KR`, `en-US`, `fr-CA`, `es-US`, `es-MX`다. `setLocale`는 다음 요청에 적용되고, 생략 시 같은 앱의 기존 설정 또는 시스템 언어를 사용한다고 설명한다. 한국어 데모라면 `Locale.KOREA`를 명시해 재현성을 높일 수 있다. [언어 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/set-locale)

이벤트에는 준비·재생 시작·RMS 갱신·정상 완료·중지·오류가 있다. 요청을 보냈다는 사실과 재생이 끝났다는 사실을 구분한다. [이벤트 가이드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/event-listeners)

## 5. 오류와 리뷰

초기화·서버 연결·재생은 서로 다른 리스너 경로에서 `ErrorType`을 받는다. 권한, 인증, 네트워크·시간 초과, 지원하지 않는 모드·언어, 기기 내 엔진, 오디오·요청 오류를 분리한다. 구버전 String 오류 콜백을 그대로 쓰지 않는다. [오류 목록](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/error-code)

서버형 인증은 실제로 검증하지 않았다. 저장소·기획서에는 실제 Client Secret을 기록하지 않는다. 최소 실행 과제는 ON_DEVICE 한국어 합성, 정상 완료·중지, 권한 거부, 잘못된 언어, 재진입·해제 후 재생성이다. 서버형은 등록·연결 성공·실패·네트워크 끊김을 추가로 확인해야 한다.
