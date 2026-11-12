# LLM SDK 기록 노트 (공모전 미제공)

기준일: **2026-11-12 KST**. 결론부터 적습니다. **플레오스 가드는 LLM SDK를 쓰지 않습니다.** 공모전 기간에는 제공되지 않는 SDK이기 때문입니다. 이 노트는 "왜 쓰지 않는지"와 "대신 어떻게 하는지"를 남기기 위한 기록입니다. [종합 리뷰](../analysis/pleos-sdk-review.md), [문서 모음](README.md).

## 1. LLM SDK는 무엇인가

LLM SDK는 차량의 음성 서비스(`ai.pleos.playground.caas`)에 붙어 큰 언어 모델에게 글을 보내고 답을 받는 Android 라이브러리입니다. 공개 Maven 저장소에는 `ai.pleos.playground:LLM:2.1.3.2` 배포 파일이 남아 있습니다. 2026-11-12에 받은 파일을 정적으로만 읽었고, 실행하거나 서버에 연결하지 않았습니다.

| 항목 | 확인한 내용 |
| --- | --- |
| 배포 파일 | [LLM 2.1.3.2 AAR](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/LLM/2.1.3.2/LLM-2.1.3.2.aar), 94,274바이트. SHA-256은 [확인 기록](evidence/sdk-followup-2026-11-12.json)에 있음 |
| 주요 공개 메서드 | `initialize`, `release`, `generateContent`, `generateContentStream`, `startChat`, `sendMessage`, `sendMessageStream`, `getPromptsContents`, `setModelParameter`, `registerApp`, `setServerType` |
| 모드 | `SERVER`, `ON_DEVICE` |
| 권한 | `pleos.car.permission.LLM_SERVICE`. 없으면 공개 메서드가 `SecurityException`을 던짐 |
| 공식 문서 | 사용 가이드 본문은 없고 [API 참조 목차](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/LLM/entry)의 제목만 있음. API 참조의 메서드 이름 일부(`generate-content`, `send-message`, `start-chat`, `set-server-type`, `get-on-device-model-prompts-contents`)와 2.1.3.2 바이너리 이름이 서로 다름 |

## 2. 공모전에서 쓸 수 없는 이유

- 공모전 개발 범위 공지(공지 8)는 「개발 문서에 공개되지 않은 SDK와 Pleos LLM SDK는 공모전 기간 중 제공되지 않습니다」라고 밝힙니다. [공모전 개발 범위](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008)
- 에뮬레이터 정식 릴리즈(v2.1.0, 2026-10-19)의 제거 항목에 「LLM SDK 제공 종료」가 있습니다. [에뮬레이터 릴리즈 노트](https://document.pleos.ai/docs/pleos-only/release-note/emulator-release-note)
- Pleos Playground 릴리즈 노트도 「LLM SDK 제공을 종료합니다. Gleo AI와의 연동이 필요한 경우 Gleo Interface를 이용할 수 있습니다.」라고 적습니다. 그러나 공모전 참가팀은 Gleo Interface 승인을 신청할 수 없습니다. [Playground 릴리즈 노트](https://document.pleos.ai/docs/pleos-only/release-note/playground-release-note)
- Maven에 파일이 남아 있다는 사실은 사용 허가가 아닙니다. 제품 소개 페이지에 LLM SDK 이름이 남아 있는 것도 마찬가지입니다.

## 3. 플레오스 가드가 대신 하는 방법

| LLM이 있었다면 할 일 | 플레오스 가드의 방법 |
| --- | --- |
| 말한 내용의 뜻 이해 | SpeechToText 결과 글을 닫힌 선택지(예, 아니요, 도와주세요, 괜찮아요, 취소)와 유사어 목록으로 맞춥니다. 맞지 않으면 다시 묻고 화면 버튼을 함께 보여 줍니다. |
| 증상 요약 | 시각·차량 신호·사용자가 고른 증상·인식한 글 원문을 정해진 칸에 그대로 나열합니다. |
| 상황별 안내 문장 만들기 | 미리 검토한 문장만 읽습니다. 긴급 음성은 대상 차량 취급설명서의 비상 절차 문장을 그대로 씁니다. |
| 경고등 질문에 답하기 | 경고등 찾기(색 → 모양 → 켜짐·깜박임)와 문구·말로 찾기로 도감 후보를 보여 줍니다. |

인식한 글을 외부 언어 모델 서비스로 보내는 방법도 1차 범위에서 쓰지 않습니다. 공지는 공개 SDK·API 범위에서만 개발하도록 하고, 차량 마이크 음성을 외부 음성 인식 서비스로 보내는 방식도 금지하기 때문입니다.

## 4. 갱신 기록

| 날짜 | 내용 |
| --- | --- |
| 2026-11-12 | 처음 작성. Maven의 LLM 2.1.3.2를 정적으로 읽고, 공모전 공지·릴리즈 노트와 대조해 사용하지 않는다고 정했습니다. |
