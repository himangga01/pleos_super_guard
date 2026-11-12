# 현대자동차그룹 차량용 앱 개발 공모전 원문 링크와 검증 기록

- 기준일: **2026-11-11 KST, Asia/Seoul**.
- 상세 조사: [공모전 상세 보고서](hyundai-vehicle-app-contest-2026.md).
- 프로젝트 인계: [AGENTS.md](../../AGENTS.md).
- 사용자 후속 지시: 전문 복제 대신 원문 링크를 제공한다.
- 같은 날 SDK 후속 조사: [SDK 문서 모음](../sdk/README.md), [SDK 리뷰](pleos-sdk-review.md). 아래의 41개·42개 수량과 18:47 검증 결과는 최초 공모전 조사의 이력이며, SDK 심화 조사 수량·증거는 별도로 기록했다.
- 조사 범위: 공모전 공개 HTML **20개**, 첨부 원본 **2개**, 그룹 보도자료 **1개**, 정상 열람한 연결 개발 문서 **18개**. 합계 **41개 원문 자료**다. 별도로 본문을 확인하지 못한 구형 튜토리얼 링크 1개를 기록한다.
- 아래의 ‘열람’은 원문 내용을 확인했다는 뜻이다. 해당 SDK·계정 권한·앱 동작을 실제 시험했다는 뜻이 아니다.

## 1. 공모전 사이트의 전체 공개 페이지 20개

| ID | 페이지 | 원문 | 확인 범위 |
| --- | --- | --- | --- |
| C01 | 홈페이지 | [공모전 홈](https://appdevchallenge.auto-hmg.io/) | 소개·일정·상금·포스터·주요 링크 |
| C02 | 참가 가이드 목차 | [전체 가이드](https://appdevchallenge.auto-hmg.io/guide) | 하위 안내 8개 확인 |
| C03 | 공모전 개요 | [개요](https://appdevchallenge.auto-hmg.io/guide/overview) | 대상·목적·예시 주제 |
| C04 | 일정·설명회 | [일정](https://appdevchallenge.auto-hmg.io/guide/schedule) | 접수·PT·전시·설명회 |
| C05 | 숏폼 챌린지 | [숏폼](https://appdevchallenge.auto-hmg.io/guide/shortform) | 규격·참여·태그·시상·권리 |
| C06 | 지원·제출 방법 | [이메일 제출](https://appdevchallenge.auto-hmg.io/guide/submission) | 양식·서명·메일·저장소·APK |
| C07 | 개발 환경 | [개발 안내](https://appdevchallenge.auto-hmg.io/guide/development) | SDK·환경·외부 문서 링크 |
| C08 | 심사·시상 | [심사와 혜택](https://appdevchallenge.auto-hmg.io/guide/judging-awards) | 평가·가산점·상금·후속 의무 |
| C09 | 참가 규정 | [규정](https://appdevchallenge.auto-hmg.io/guide/rules) | 자격·수정·마감·현장 전시 |
| C10 | 도움말·문의 | [도움말](https://appdevchallenge.auto-hmg.io/guide/help) | FAQ 분류·연락처 |
| C11 | 공지사항 목록 | [공지 전체](https://appdevchallenge.auto-hmg.io/notice) | 전체 5건. 브라우저 DOM 대조 |
| C12 | 최초 모집 공고 | [모집 공고](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000001) | 자격·제출·심사·장소·일정 |
| C13 | 온라인 설명회 | [설명회 공지](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000005) | 10월 28일 14시·프로그램·다시보기 예정 |
| C14 | 참가·출품 유의사항 | [유의사항 공지](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000006) | 20팀·현장 의무·IP·상금·보관 |
| C15 | 저장소·접수 추가 안내 | [접수 추가 공지](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000007) | 공개 전환·커밋·서명·파일·자료·PT |
| C16 | 개발 범위·기술 문의 | [기술 범위 공지](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008) | 후속 확인에서 다섯 SDK의 공모전 허용 명시. Gleo·마이크·미공개 SDK 제한 유지 |
| C17 | FAQ | [21문항 전체](https://appdevchallenge.auto-hmg.io/faq) | 6개 분류·21문항. 실제 화면 질문 수 대조 |
| C18 | 별도 문의 페이지 | [문의 안내](https://appdevchallenge.auto-hmg.io/qna) | FAQ·공지·이메일 |
| C19 | 이용약관 | [13개 조항](https://appdevchallenge.auto-hmg.io/terms) | 제출·권리·팀·금지·이의·책임·분쟁 |
| C20 | 개인정보 처리방침 | [9개 항목](https://appdevchallenge.auto-hmg.io/privacy) | 수집·위탁·보관·보호·권리·연락처 |

사이트의 상단 ‘공모전 소개’는 홈페이지 안의 구역으로 연결된다. ‘지원하기’는 C06으로 연결된다. 같은 페이지의 모바일 메뉴·목차·섹션 앵커를 새로운 원문 자료로 중복 계산하지 않았다.

## 2. 공식 첨부 원본 2개

| ID | 자료 | 원문 링크 | 실제 확인 |
| --- | --- | --- | --- |
| A01 | 접수양식·개인정보 동의서 | [DOCX 원본](https://appdevchallenge.auto-hmg.io/downloads/contest-application-consent.docx) | HTTP 200, 55,208바이트. 내부 XML의 4개 작성 영역·10개 표 확인 |
| A02 | 최종 공모전 포스터 | [WEBP 이미지 원본](https://appdevchallenge.auto-hmg.io/images/poster/final-contest-poster.webp) | HTTP 200, 279,532바이트. 1800×2548 이미지 시각 확인 |

원본의 조사 시점 SHA-256:

```text
A01 DOCX
07be893d58b0e96ab7788f0d189d6829f0e59a2cee4bc18cb90ec37eeda2f5fd

A02 WEBP
0fa6f00a386858b6897585603655680212e629f75a047dc00d88c2dd88ff86dc
```

해시는 조사 시점 파일의 식별값이다. 이후 공식 파일이 갱신되면 달라질 수 있다. 다운로드한 사본은 내부 조사용 임시 폴더에서 읽었다. 제공물은 Markdown 보고서와 원문 링크이며 원본의 인쇄·기능 검증이나 별도 재배포본을 뜻하지 않는다.

## 3. 현대자동차그룹 공식 보도자료

| ID | 원문 | 게시일·용도 |
| --- | --- | --- |
| N01 | [현대차·기아, 차량용 앱 개발 공모전 개최](https://www.hyundaimotorgroup.com/ko/news/hyundai-motor-company-kia-vehicle-app-development-contest) | 2026-10-21. 주최 맥락·대외 모집·공식 사이트 연결 교차 확인 |

기사 상단의 AI 요약과 별도로 실제 본문을 읽었다. 구체적인 제출 방식·추가 제한은 보도자료의 포괄 설명보다 C06·C14~C16의 구체 안내를 참조했다.

## 4. 연결된 개발 문서 18개

공모전 안내에 직접 연결된 문서에서 참가 조건에 영향을 주는 소개·신청·설정·릴리즈를 따라 확인했다. 전체 Pleos API 문서의 모든 하위 페이지를 조사했다는 뜻은 아니다.

| ID | 주제 | 원문 | 조사 용도 |
| --- | --- | --- | --- |
| D01 | 개발자 포털 | [Pleos Playground](https://developers.pleos.ai/playground) | 개발 포털·제품·문서 경로 |
| D02 | 빠른 시작 | [앱 개발 시작하기](https://document.pleos.ai/docs/pleos-only/getting-started) | 프로젝트·AVD·API 호출 흐름. 2.3.1 예제 차이 |
| D03 | 계정·프로젝트·권한 신청 | [프로젝트 생성 및 SDK 사용 신청](https://document.pleos.ai/docs/pleos-only/create-my-project) | 이용 상태·준비 중 표기·서버 자격정보 조건 |
| D04 | 에뮬레이터 설치 | [설치·실행](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator) | API 28·Gradle 8·AVD 2.1.0·RAM·시스템 이미지 |
| D05 | 에뮬레이터 설정 | [AVD 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator) | GPU·마이크·Cold boot·Bluetooth |
| D06 | 차량 상태 시험 도구 | [Virtual Console 셋업](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator) | 버전·포트·기능 영역·시연 |
| D07 | SDK 전체 구조 | [SDK Overview](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/) | AAOS·5개 SDK·권한·호환 정책 |
| D08 | Vehicle 소개 | [Vehicle Introduction](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro) | 도메인·비동기·Capability·서비스 기준 버전 |
| D09 | Vehicle 의존성 | [Importing Vehicle SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup) | Maven·2.3.2 연동 |
| D10 | 내비게이션 | [NaviHelper Introduction](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro) | 경로·경유지·이벤트·환경 조건 |
| D11 | 음성 합성 | [TextToSpeech Introduction](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro) | 모드·서버 등록·연결 조건 |
| D12 | 음성 인식 | [SpeechToText Introduction](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro) | 발화 인식·명령 실행의 차이·모드 |
| D13 | 위치 | [FusedLocation Introduction](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro) | 지원 확인·위치·권한·null 처리 |
| D14 | 에뮬레이터 릴리즈 | [2.1.0 릴리즈](https://document.pleos.ai/docs/pleos-only/release-note/emulator-release-note) | 2026-10-19. 추가 도구·종료 기능 |
| D15 | SDK 릴리즈 | [SDK 릴리즈 노트](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note) | 2026-10-19. 다섯 버전·파괴적 변경 |
| D16 | Vehicle 샘플 | [Sample App 사용 가이드](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app) | 제품·샘플 요구의 차이·실제 상태 검증 방법 |
| D17 | 화면 변화 시험 | [폼 팩터 테스트](https://document.pleos.ai/docs/pleos-only/app-test-guide/form-factor-test-guide) | 화면 규격·브리지·Go·실행 방식 |
| D18 | Gleo의 일반 구조 | [Gleo Interface](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts) | 일반 설명과 공모전 승인 제한 구분 |

문서에 나오는 공개 시스템 이미지·Maven 설정 주소는 상세 보고서 12절에 보존했다. 저장소 전체·SDK 바이너리를 내려받거나 자격정보를 사용하지 않았으므로 이 출처 수에는 별도 검증 자료로 추가하지 않았다.

## 5. 본문을 확인하지 못한 링크와 미공개 자료

| ID | 대상 | 확인 결과 | 처리 |
| --- | --- | --- | --- |
| X01 | [구형 Vehicle 튜토리얼 주소](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk) | 공모전 개발 안내에서 연결되지만 웹 읽기 결과 본문 0행 | 열람 성공으로 계산하지 않음. 정상 D16을 별도 확인 |
| X02 | 설명회 다시보기 | C13·C04에는 제공 예정 안내만 확인 | 임의의 YouTube URL을 만들거나 시청했다고 기록하지 않음 |
| X03 | 로그인한 My Project의 SDK 상태 | 개인 계정에 접근하지 않음 | D03의 공개 안내와 실제 상태를 구분 |
| X04 | 2차 배점·장비·최종 산출물 기한 | 추후 또는 본선 진출팀 안내 | 공개된 확정 요건으로 기재하지 않음 |
| X05 | 2027년 2월 행사 상세 | 월·참석 의무만 확인 | 정확한 일시·장소·교통 지원은 미확정으로 기록 |

## 6. 중요한 사실의 근거 대조표

| 확인한 주장 | 근거 | 판단 범위 |
| --- | --- | --- |
| 접수 마감은 11월 15일 23:59 | A01·C15·C17 | 원문 확인 |
| 1차는 기획서만 가능 | C03·C06·C12·C17 | 원문 확인 |
| 2차는 실제 앱 시연 | C08·C14·C16 | 원문 확인, 앱 실행 미검증 |
| 개인 또는 최대 5인 | C03·C09·A01 | 원문 확인 |
| 1차 합격 20팀 안내 | C14 | 해당 공지 내용. 실제 선발 결과 아님 |
| 10팀·5,100만원, 숏폼 별도 | C01·C08·C05·A02 | 표·포스터 금액 대조 |
| 11월 16일 공개, 23일까지 유지 | C06·C15·C17 | 원문 확인 |
| Gleo 신청·외부 STT·미공개 SDK 제한 | C16 | 공모전 범위만 적용하여 기록 |
| 제품 minSdk 28, 샘플 23 | D04·D16 | 서로 다른 대상임을 확인 |
| 일부 SDK 상태 설명과 공모전 허용 | C16·D03·D11~D15 | 후속 공지 확인으로 다섯 SDK의 공모전 사용 허용 확정. 실제 호출·서버 인증 미검증 |
| Vehicle 2.3.1·2.3.2 예제 차이 | D02·D09·D15 | 현재 릴리즈·개별 연동 문서를 기준으로 정리 |
| 수상작 IP·우선협상·상금 지급 조건 | C14·C19 | 안내 내용 확인. 개별 계약·세금 계산 미실행 |
| 2월 현장 의무 | C08·C09·C14 | 행사 개별 세부사항은 미확인 |

## 7. 조사 방법과 실제 도구 결과

### 검색과 원문 확인

조사 깊이는 종합 조사로 분류했다. 주최·행사 식별, 공모 조건, 제출·FAQ·공지, SDK 제공 범위, 권리·운영 조건으로 조사 범위를 나누었다. 기본 검색은 ‘현대자동차 차량용 앱 개발 공모전 2026 앱마켓’, ‘현대자동차 기아 AAOS 앱 개발 공모전 공식’으로 후보를 찾고 공식 원문으로 이동했다. 제3자 공모전 모음 사이트의 요약을 확정 근거로 사용하지 않았다.

확인 순서는 공식 홈페이지·보도자료, 가이드·공지·FAQ·첨부, 연결된 SDK·릴리즈·신청 문서였다. 공식 사이트의 20개 페이지와 확인된 첨부·핵심 개발 자료를 대조한 뒤, 비공개·추후 안내 영역을 미확인으로 남기는 범위에서 종료했다. 다른 AI 서비스에 조사 내용을 전송하지 않았다.

### HTTP·브라우저

다음 명령군으로 공개 원문과 실제 화면을 확인했다.

```powershell
agbrowse search --verify 'https://appdevchallenge.auto-hmg.io/' --json --browser never
agbrowse search --verify 'https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008' --json --browser never
agbrowse start --headless
agbrowse navigate 'https://appdevchallenge.auto-hmg.io/guide/submission'
agbrowse snapshot --interactive --max-nodes 100
agbrowse navigate 'https://appdevchallenge.auto-hmg.io/notice'
agbrowse navigate 'https://appdevchallenge.auto-hmg.io/faq'
agbrowse stop
```

- 단일 URL CLI 검증: 두 URL 모두 `ok: true`, `verdict: weak_ok`, `source: fetch`, 경고 없음. 이 응답 스키마에는 전체 조사 `evidenceStatus` 필드가 없었다.
- 따라서 자동 증거 원장의 `sufficient` 판정을 받았다고 주장하지 않는다. 원문과 실제 DOM의 수동 대조를 별도로 수행했다.
- 실제 브라우저에서 공지 링크 5개, FAQ `main h3` 질문 요소 21개를 확인했다. 제출·문의 화면의 실제 연결도 확인했다.
- 조사용 Chrome은 시작 전 실행 중이 아니었으며, 조사 후 종료했다. 다른 앱·에이전트 프로세스를 정리하거나 재시작하지 않았다.
- 원문 내용은 웹 읽기 도구에서도 직접 열었다. 브라우저만으로 20개 페이지의 모든 상호작용을 시험했다는 뜻은 아니다.

### 첨부 파일

Codex 앱의 `load_workspace_dependencies`로 찾은 **번들 Python**을 사용했다. DOCX는 HTTP 응답을 받은 뒤 표준 ZIP·XML 모듈로 텍스트를 확인했고, 포스터는 로컬 이미지 뷰어로 읽었다. 최초 콘솔 출력의 한글 인코딩 깨짐은 UTF-8로 다시 읽어 수정했다. 인쇄용 DOCX 렌더·APK 빌드·SDK 호출 시험은 수행하지 않았다.

### 로컬 기준 상태

`Get-ChildItem -Force`로 최초 폴더가 비어 있음을 확인했다. `git rev-parse --show-toplevel`, `git status --short`, `git branch --show-current`, `git rev-parse HEAD`는 Git 저장소가 아니라는 결과였다. 기존 보고서나 소스 기준 커밋이 없으므로 웹 자료와 조사일을 기준선으로 삼았다.

## 8. 저장 문서 검증

**검증 시각: 2026-11-11 18:47 KST.** 저장 문서와 원문 URL을 번들 Python의 표준 모듈로 확인했다.

| 검사 | 결과 |
| --- | --- |
| UTF-8 읽기·문자 깨짐 | AGENTS와 보고서 2개 모두 정상. 대체문자 없음 |
| 파일 간 상대 링크 | 끊어진 링크 0개 |
| 목록 수 | C 20개·A 2개·N 1개·D 18개, 합계 41개 |
| FAQ 대조 | 21개 행. 실제 브라우저 질문 수와 일치 |
| 외부 원문 링크 | 중복 제거 42개에 GET, **42개 모두 HTTP 200** |
| 예외 링크 | X01은 HTTP 응답 성공과 별개로 원문 읽기 본문이 비어 있음. 정상 자료 41개에 넣지 않음 |
| 상금 계산 | 2×1,000 + 2×600 + 4×400 + 2×150 = 5,100만원. 숏폼 포함 5,150만원 |
| 날짜 계산 | 11월 11일→15일 4일, 11월 23일→12월 3일 10일 |
| 요일 | 11월 15일 일·23일 월, 12월 3일 목·4일 금·18일 금 확인 |
| 민감정보 점검 | 실제 API 키·토큰 형태의 문자열 없음. 공개 연락처·공개 파일 해시만 기록 |

HTTP 200은 링크 응답을 확인하는 검사다. 본문 완전성·자료의 최신성·실제 SDK 사용 허가·APK 실행을 증명하지 않는다. 앱 코드나 실행 산출물이 없는 문서 조사이므로 앱 테스트·빌드 성공으로 보고하지 않는다.

## 9. 다음 갱신에서 확인할 항목

1. C11에 새 공지·정정·마감 변경이 있는지 확인한다.
2. A01 파일 해시·양식 버전과 D14·D15 릴리즈가 바뀌었는지 확인한다.
3. SDK 가용성은 공개 설명과 계정 상태·실제 실행 결과를 구분해서 갱신한다.
4. 설명회 다시보기·2차 지침·2월 행사가 공개되면 해당 미확인 항목만 갱신한다.
5. 날짜·출처·한계·실제 실행 증거와 변경 이력을 보존한다. 앱 코드가 생기면 새 Git 기준과 작업 상태도 기록한다.

## 10. 같은 날 SDK 후속 조사

공모전 공지 C16을 HTTP 원문과 Chrome에서 다시 확인했다. 공개 다섯 SDK는 워크스페이스 준비 중 표기와 무관하게 에뮬레이터에서 개발·시험할 수 있다고 명시되어 있다. 최초 보고서의 가용성 미확정은 이 근거로 갱신했다. 공지의 정확한 수정 시각은 추정하지 않았다.

후속 조사에서는 SDK 가이드·참조 시작점 49개, 추가 API 경로 18개, 공통·공모전 자료 9개를 확인했다. 공개 주 SDK POM·AAR 각 5개, Pleos 전이 POM 7개와 공식 샘플 3개 고정 커밋을 대조했다. 원문 목록은 [SDK 문서 모음](../sdk/README.md), 정적 리뷰·실행 한계는 [SDK 리뷰](pleos-sdk-review.md), 해시·메서드 메타데이터는 [증거 JSON](../sdk/evidence/sdk-inspection-2026-11-11.json)을 참조한다.
