# TextToSpeech SDK 개발 노트

기준일: 2026-11-11 KST. 공모전 사용 가능, 배포 기준 **3.3.1**. 음성 합성·오디오 출력·서버 인증은 미실행이다. [전체 리뷰](../analysis/pleos-sdk-review.md), [문서 전체 목차](README.md).

2026-11-12 후속 확인: 오디오 속성·재생 순서·오디오 포커스와 관련된 공개 API와 Pleos 오디오 가이드를 [6절](#6-2026-11-12-후속-오디오-속성포커스와-권한)에 보충했다. 기본 오디오 용도가 MEDIA라는 점, 권한 오류가 `SecurityException`으로 먼저 난다는 점이 새로 확인됐다.

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

## 6. 2026-11-12 후속: 오디오 속성·포커스와 권한

TTS 3.3.1은 소스 묶음이 없어 공개 서명과 바이트코드를 읽었다. 실제 재생은 하지 않았다.

### 6.1 새로 확인한 공개 API

| API | 확인한 내용 | 문서 상태 |
| --- | --- | --- |
| `setAudioAttributes(AudioAttributes)` | 재생 용도를 바꾼다. 지정하지 않으면 `CONTENT_TYPE_SPEECH`와 `USAGE_MEDIA`를 쓴다 | 가이드 본문 없음, API 참조 제목만 있음 |
| `setSpeakerType(String)` | 목소리 종류. 허용 값이 문서·바이너리에 없다 | API 참조 제목만 있음 |
| `setAudioZoneId(Int)` | 차량 오디오 구역 지정. 값 체계가 없다 | API 참조 제목만 있음 |
| `registerApp` 추가 오버로드 2개 | universal-access 경로용 | 가이드에 없음 |

- 재생 우선순위나 끼어들기 인자는 없다. 앞 안내를 끊으려면 `stop()`을 부르고 `onStop`을 받은 뒤 `speak()`를 부른다. 순서 보장은 시험해야 한다.
- SDK 안에는 오디오 포커스를 요청하는 코드가 없다. 포커스는 음성 서비스가 처리하는 것으로 보이며, 서비스가 `setAudioAttributes` 값으로 포커스를 요청하는지는 확인하지 못했다.
- ON_DEVICE 모드에서 `registerApp(clientID, clientSecret, listener)`는 서버에 가지 않고 바로 `onConnected`를 부른다.
- `getAmbientAi()`는 숨김 특권 API라 쓰지 않는다.

### 6.2 권한 오류(2026-11-12 정정)

5절의 "권한 … 오류를 분리한다"는 `ErrorType.PERMISSION_DENIED` 콜백을 전제로 했다. 실제로는 `TTS_SERVICE`가 없으면 모든 공개 메서드가 `SecurityException("Permission pleos.car.permission.TTS_SERVICE is required to perform this action.")`을 먼저 던진다. 권한 문서도 이 점을 적지 않는다. 권한의 보호 수준은 문서·바이너리 어디에도 없다. 음성 서비스 최소 버전 메타데이터는 4.1.29다. 소개 문서의 "인식이 끝나면 stop()을 호출합니다"는 STT 문장을 옮긴 흔적으로 보인다.

### 6.3 Pleos 오디오 가이드와 플레오스 가드 적용

- Pleos 오디오 포커스 표에서 MEDIA 용도는 MUSIC 컨텍스트에 묶인다. 긴급 안내가 음악처럼 다뤄지지 않도록 `setAudioAttributes`로 안내 용도를 지정한다. 후보는 내비 안내 용도(`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`, NAVIGATION 컨텍스트)와 알람 용도(`USAGE_ALARM`)이며 에뮬레이터 시험으로 고른다.
- EMERGENCY·SAFETY 컨텍스트를 일반 앱이 쓸 수 있다는 문장은 Pleos 원문에 없다. Android에서는 시스템 용도로 알려져 있으므로 쓰지 않는다(Android 일반 지식, 이번에 원문을 열어 확인하지는 않음).
- 모든 음성 앞에 150~200ms 무음을 두라는 권장이 있다. TTS SDK는 합성한 소리 데이터를 앱에 주지 않으므로, 미리 만든 오프라인 음원을 쓰거나 재생 방법을 시험으로 정한다.
- Gleo가 불려 `AUDIOFOCUS_LOSS_TRANSIENT`가 오면 바로 멈추고, Gleo가 끝나면 현재 단계를 처음부터 다시 말한다.
- 시동이 꺼진 Suspend 상태에서는 오디오 포커스를 반납한다. 시동이 꺼진 뒤에도 앱 음성 안내가 계속된다고 약속하지 않는다.
- 원문 인용과 출처는 [Pleos 앱 가이드 기준](../analysis/pleos-app-guidelines-2026-11-12.md) 7절, 확인 기록은 [후속 확인 기록](evidence/sdk-followup-2026-11-12.json)에 있다.
