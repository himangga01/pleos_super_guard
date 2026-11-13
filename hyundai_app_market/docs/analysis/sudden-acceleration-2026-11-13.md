# 급발진(원치 않는 가속) 조건 조사와 데모 시뮬레이션 적용

- 작성일: 2026-11-13(금) 밤, Asia/Seoul
- 요청: "급발진에 해당하는 경우가 어떤 것인지 조사하고, 그 조건으로 시뮬레이션을 적용"(사용자, 2026-11-13)
- 범위: 급발진의 정의·원인·관측 신호·사례·규제를 조사하고, 그 조건을 Pleos 에뮬레이터 Virtual Console 시나리오와 데모 앱 판정에 넣어 확인했습니다. 차량 제어·실차 시험은 하지 않았습니다.
- 기준: Git 브랜치 `claude/unzip-files-fq5lu8`, 커밋 `a0396d1`. 시작 때 작업 트리는 깨끗했고, 이 문서와 함께 데모 앱 0.2.0-demo 변경(커밋 전)이 있습니다.
- 조사 방법: 웹 출처 조사는 하위 에이전트가 했고(출처 53개, 12절), 메인 세션이 NHTSA 원문 추출본에서 핵심 수치 4개(정의 문장, 정지 시작 36%, 오조작 EDR 74% 무제동, 브레이크 펌핑 시 배력 고갈)를 다시 확인했습니다. 나머지 수치는 출처에 적힌 그대로이며 메인 세션이 원문을 다시 열어 보지 않았습니다. 한국 언론·제조사·일본 정부 자료는 요약만 했습니다.
- 관련 문서: [PRD 3.3.2 확인 질문 조건](integrated-vehicle-care-2026-11-12.md), [판정 설정 rules-lab](../specs/vehicle-care/rules-lab.json), [데모 검토 기록 5절 로컬 확인](demo-feasibility-2026-11-13.md), [데모 앱 README](../../demo-app/README.md)

## 1. 결론

1. **공식 조사가 확인한 차량 원인은 매트 끼임과 가속페달 복귀 불량뿐입니다.** 전자 스로틀 결함은 확인되지 않았습니다(NHTSA·NASA 2011). 정지·저속에서 시작한 사건은 대부분 페달 오조작으로 결론 났습니다. 한국에서 차량 결함이 공식 인정된 사례는 보도 기준 0건입니다.
2. **실제 사례의 신호는 "가속페달 90~100%, 브레이크 꺼짐, 속도 상승"입니다.** 시청역(2024)과 강릉(2022) 사고의 EDR, 국과수 감정 대부분이 그렇습니다. 앱은 공개 SDK로 가속페달 값을 읽지 못하므로(7절), 이 유형은 정상 가속과 센서로 구별할 수 없습니다. **이때 앱은 운전자의 직접 보고("저절로 빨라져요" 버튼·말)로만 안내를 시작합니다.** 이것은 기존 설계(PRD 3.3.1 "운전자의 직접 보고로만 시작")와 같습니다.
3. **앱의 센서 조합 질문("차가 멈추지 않나요?")이 맞는 유형은 확인된 차량 원인(매트 끼임·페달 복귀 불량)입니다.** 브레이크를 밟는데도 감속이 없는 상황입니다. Virtual Console로 이 유형을 재생하자, 앱은 시험 입력 없이 실제 신호(AAOS 차속, Vehicle SDK 브레이크)만으로 6초 시점에 질문했습니다.
4. **정차 출발 직후·저속 구간(PMSA 구간)은 질문하지 않습니다.** 기준은 15km/h로 정했습니다. 개조형 페달 오조작 장치의 작동 조건("정차 또는 15km/h 이하")과 UN R175 시험 조건(정지·크리프)을 근거로 한 데모 앱의 자체 결정입니다. 브레이크를 밟은 채 15km/h를 넘어 빨라지면 그때부터 관측해 질문합니다.

## 2. 급발진의 정의와 원인

- **정의(NHTSA 1989, Pollard & Sussman).** 급발진 사건은 "high-power accelerations from a stationary position or a very low initial speed"이고, 제동력이 사라진 것처럼 보이는 현상이 함께 따릅니다[S1, S2 p.vi]. NHTSA 2012는 이를 보통 P에서 D·R로 바꿀 때 일어난다고 요약합니다[S3].
- **UA(의도하지 않은 가속).** NHTSA 2011에서는 더 넓은 말로, 고속 사건과 매트 끼임을 포함합니다[S2].
- **한국 공식 정의.** 법령이나 국토부·교통안전공단의 정의는 찾지 못했습니다. "급발진 의심"은 자동차리콜센터 신고·조사 용어로만 확인됩니다[S21, S36].

| 원인 | 판정 | 근거 |
| --- | --- | --- |
| 매트 끼임 | 확인됨(리콜) | 고속·장시간(1분 안팎) 사건의 유력 원인[S2] |
| 가속페달 복귀 불량(sticky pedal) | 확인됨(리콜) | DOT 발표: 이 두 결함만 알려진 원인[S4] |
| 페달 오조작(가속을 브레이크로 착각, 두 페달 동시) | 확인됨(운전자 요인) | 정지·저속 사건의 가장 유력한 원인[S2, S1] |
| 전자 스로틀(ETC) 결함 | 확인 안 됨 | 큰 스로틀 개방을 만드는 전자 원인 없음. 공회전·변속 제어 고장은 5도 미만[S2] |
| 크루즈 컨트롤 고장 | 영향 작음 | 약 0.06g(2.12km/h/s), 취소 수단 있음[S2] |
| 제동력 상실 | 일부 조건만 | 중·고속에서 브레이크를 반복해 밟아 진공 배력이 고갈되거나 장시간 고속에서 과열될 때[S2] |

## 3. 전형 상황과 관측 신호

| # | 조건 | 값 | 맥락 | 출처 |
| --- | --- | --- | --- | --- |
| 1 | 시작 속도 | 민원: 정지 36%, 15mph(24km/h) 미만 33%. 사고 기준 84%가 정지·저속 시작 | NHTSA 민원 5,512건 | S2 |
| 2 | 장소·동작 | 주차 진입·이탈이 정지·저속 민원의 40%, 사고의 64%. "브레이크를 밟은 직후", "변속 직후" 고출력 신고 | NHTSA | S2 |
| 3 | 오조작 EDR(미국) | 오조작 39건 중 74% 제동 기록 없음, 15%는 충돌 1초 전 이내에만 제동, 90%는 가속페달 유지·증가 | 도요타 현장조사 | S2 |
| 4 | 지속 시간 | 저속 사건은 몇 초·짧은 거리 안에 끝남. 매트 끼임은 1분 안팎. 오조작 최대 12초 사례 | NHTSA | S2, S3 |
| 5 | 브레이크 능력 | 전개방에서도 배력이 있으면 작은 힘으로 정지 유지. 반복해 밟으면 5~6번 만에 배력 고갈 | 캠리 시험 | S2 |
| 6 | 국과수 감정 | 2024년 133건 중 분석 가능 120건이 모두 가속페달 오조작. 2021~2026.6 448건 중 85.7% 오조작, 인정 0건 | 한국 | S33, S34 |
| 7 | 시청역(2024-07-01) | 5초 중 약 4초 가속페달, 최대 약 99%, 0.5초쯤 두 번 뗌, 충돌까지 브레이크 작동 없음. 2심: 70→105.3km/h | EDR·판결 | S37, S39 |
| 8 | 강릉(2022-12) | 마지막 5초 가속 100%, 브레이크 OFF, 110→116km/h(약 0.33m/s²). 장구간 40→116km/h/24초(약 0.88m/s²). 항소심 감정 진행 중 | EDR·재연 | S41, S42 |
| 9 | 연령 | 미국: 16~20세와 76세 이상이 많음. 한국 2024 감정 133건 중 60대 이상 96건 | | S3, S33 |
| 10 | EDR 기록 범위 | 사고 전 5초(2Hz). 미국은 20초·10Hz로 단계 도입 | | S8, S9, S31 |

이 조사로 나눈 유형은 다음과 같습니다.

- (a) 정차·저속 출발 페달 오조작: 주차 중 D·R 변속 직후 가속 100%, 브레이크 꺼짐, 몇 초 안에 충돌.
- (b) 도로 주행 중 오조작: 브레이크 꺼짐, 몇 초~수십 초 동안 속도 상승.
- (c) 매트 끼임·페달 복귀 불량: 고속, 브레이크를 밟아도 감속 없음(약하게 밟거나 펌핑으로 배력 저하), 1분 안팎.
- (d) 늦은 제동: 충돌 1초 전 이내에만 브레이크.

## 4. 한국 통계와 정책 (보도·법무법인 요약 기준)

- 급발진 의심 신고: 2010~2022년 766건, 인정 0건[S36]. 자동차리콜센터 2020~2024년 111건[S21].
- 시청역 사고는 대법원이 2025-12-04 형을 확정하며 급발진 주장을 받아들이지 않았습니다[S40]. 강릉 사고는 1심(2025-05-13) 제조사 승소 뒤 항소심 법원 감정이 진행 중이며 다음 기일이 2026-12-07입니다[S41, S45].
- 제조물책임법 입증책임 전환("도현이법")은 통과 근거를 찾지 못했습니다[S48].
- 페달 오조작 방지장치: 자동차규칙 개정(2026-07-10)으로 승용차는 2029-01-01 이후 개발 형식부터 의무[S16]. 기존 차량용 장치 근거를 담은 자동차관리법 개정은 2026-12-17 시행[S16, S53]. 서울시는 70세 이상 200대 시범 사업 중이며, 개조형 장치의 작동 조건이 "정차 또는 15km/h 이하"입니다[S49].
- KNCAP 2025에 정지 상태 페달 오조작 평가(앞뒤 1m·1.5m 장애물)가 들어갔습니다[S20, S21].

## 5. 차량 장치와 규제

| 항목 | 내용 | 출처 |
| --- | --- | --- |
| BTO(브레이크 우선 제어) | 미국 FMVSS 124 개정안(2012)은 모든 MY2018 경차량이 자발적으로 갖춰 2019년 철회 | S7 |
| UN R175(ACPE) | 2024-11-13 채택, 2025-06-23 발효. 시험: 장애물 1.0·1.5m 앞, 정지·크리프에서 가속페달 끝까지. 충돌 없음 또는 8km/h 이하 + 30% 이상 감속 | S11, S14 |
| 현대 PMSA 1.0(캐스퍼 일렉트릭) | 정차 후 출발, 진행 방향 1m 안 장애물, 0.25초 안 가속페달 100%, 조향 430° 이하, 경사 25% 이하. 60~100cm 구동력 제한, 30~60cm 제동. 속도 상한은 공식 자료에 없음 | S22 |
| 현대 PMSA 2.0(디 올 뉴 아반떼) | 앞뒤 1.5m, D·R, 주차·저속 중심. 전 트림 기본 | S24, S25 |
| 일본 | 국산 신형 AT 승용차 2028-09, 수입차 2029-09(이후 R175-01 반영으로 일정 조정) | S13, S14 |

PMSA와 ACPE는 정지·크리프 상태의 출발을 맡습니다. 그래서 앱은 이 구간에서 질문하지 않고 기록만 합니다(PRD 3.3.2, 3.3.8).

## 6. 운전자 권장 행동 (참고)

- NHTSA: 브레이크를 펌핑하지 말고 세게 꾸준히 밟고, 중립(N)으로 바꾸고, 안전한 곳으로 조향해 멈춘 뒤 시동을 끕니다[S6].
- 한국교통안전공단 시연(2023, EPB 장착 15대): 브레이크와 전자식 파킹 브레이크(EPB)를 계속 작동하는 방법이 가장 효과적이었습니다[S30].
- 현대 설명서(MX5·NE1 2026 웹 매뉴얼): 비상시 주행 중 시동 버튼을 2초 이상 누르거나 3번 이상 빠르게 누르면 시동이 꺼집니다[S26, S27]. EPB 비상 제동은 스위치를 당기는 동안만 작동합니다(DN8HEV 2026)[S28].
- **디 올 뉴 아반떼(CN7 2026) 설명서 문구는 확인하지 못했습니다**(PDF 용량 제한)[S29]. 따라서 앱의 긴급 안내 문장은 계속 "문장 자리"로 둡니다. 위 내용은 검토 자료이며, 앱이 읽는 문장은 대상 차량 설명서를 검토한 뒤 확정합니다(PRD F5).

## 7. 앱이 볼 수 있는 신호 (2026-11-13 로컬 에뮬레이터에서 확인)

| 신호 | 공개 경로 | 에뮬레이터 확인 | 급발진 판정에 쓰는지 |
| --- | --- | --- | --- |
| 차속 | AAOS `PERF_VEHICLE_SPEED`(CAR_SPEED) | Virtual Console 값이 그대로 옴(정수 km/h, 약 140ms 간격) | 씀 |
| 브레이크 밟음 | Vehicle SDK `Brake`(Boolean) | VC Brake Pedal 1% 이상이면 true | 씀 |
| 주행 제한 | AAOS `CarUxRestrictions` | VC 기어 D → DO 필요(0x50, idling), 차속 있으면 moving | 화면 전환에 씀 |
| 가속페달 비율 | Android 14(API 34) `android.car`에 공개 속성 없음(Android 15에서 추가) | VC에는 값이 있으나 앱이 읽을 공개 경로 없음 | 못 씀([향후]) |
| 기어 | AAOS `GEAR_SELECTION`(CAR_POWERTRAIN) | 읽기 가능해 보이나 시험하지 않음 | 설계상 쓰지 않음(PRD) |
| 엔진 RPM | `ENGINE_RPM`(signature 권한) | 일반 앱 불가 | 못 씀 |
| 에어백 전개 | VC Telltales의 Airbag은 경고등이며 전개 신호가 아님 | | 시험 입력으로만 |

Virtual Console 재생 방법도 확인했습니다.

- `PUT /vehicle/controls?speed=&accelerator=&brake=&steering=`, `PUT /vehicle/gear?gear=Drive|Neutral|Park|Reverse`로 값을 넣습니다.
- 콘솔은 웹 페이지가 하트비트(`POST /browser/session`, 3,000ms 제한)를 보낼 때만 값을 받습니다. 페이지가 열려 있으면 페이지의 물리 계산이 값을 덮어씁니다(가속 100% = +19km/h/s, 브레이크 100% = −42km/h/s, 관성 1.2+0.035×속도). 그래서 재생기는 페이지를 닫은 상태에서 직접 하트비트를 보냅니다.
- 안내 문서의 주행 제한 명령 `cmd hmg_car_service --service drivingstate driving_regulation on`은 이 이미지에서 "Can't find service"로 실패했습니다. 기어 D로 같은 효과를 냅니다.

## 8. 시뮬레이션 시나리오

정의 파일은 `demo-app/scenarios/sua-scenarios.json`이고, 재생기는 `demo-app/scripts/vc_scenario.py`입니다. replay 모드는 사례 요약값으로 만든 속도점을 잇는 근사이며 EDR 원자료가 아닙니다. physics 모드는 Virtual Console 페이지와 같은 단순 물리식입니다.

| ID | 유형 | 시간표 요약 | 기대 앱 동작 | JVM 시험 | 에뮬레이터 |
| --- | --- | --- | --- | --- | --- |
| `sua-parking-misapplication` | (a) | P→D, 가속 100%(브레이크 0) 3초, 0→약 50km/h | 질문 없음, 급가속 후보 기록 | 후보 3.4초, 질문 없음 | 확인: 음성 0회, "급가속 후보 20:34:20 기록(질문 없음)" |
| `sua-road-misapplication` | (b) 시청역형 | 70km/h → 가속 99%(0.5초씩 두 번 뗌) → 105km/h, 브레이크 0 | 질문 없음, 운전자 보고 필요 | 후보 7.5초, 질문 없음 | 확인: 93km/h·브레이크 꺼짐에서 질문·음성 없음 |
| `sua-mat-entrapment` | (c) | 100km/h, 가속 100% 고착, 브레이크 30%에도 108→112km/h, 15초에 N+브레이크 100% | 질문 "차가 멈추지 않나요?" | 질문 6.0초(속도 변화 0, "줄지 않았습니다") | 확인: 7초 화면에 질문(시험 입력 배지 없음), 음성 재생, "안 멈춰요" → 감속 실패 대처, 단계 음성 1~3, N단 감속 |
| `sua-low-speed-brake-overpowered` | (c) 저속 시작 | 정지에서 가속 고착 + 브레이크 40%, 0→26km/h | 15km/h 아래는 건너뛰고 넘은 뒤 질문("오히려 빨라졌습니다") | 저속 건너뜀, 질문 6.3초(+1.39m/s) | 미확인 |
| `sua-late-brake` | (d) | 30→58km/h 가속, 충돌 0.6초 전 제동 | 질문 없음(관측 1초 미만) | 질문·후보 없음 | 미확인 |
| `normal-merge` | 정상 | 60km/h에서 가속 80% | 후보 기록만 | 후보 3.0초, 질문 없음 | 미확인 |
| `normal-braking` | 정상 | 60km/h에서 브레이크 25% | 질문 없음 | 질문·후보 없음 | 전송 점검판으로 확인(브레이크 25%에서 "밟음", 질문 없음) |
| `creep-light-brake` | 정상 저속 | 10km/h에서 브레이크 5% | 질문 없음(저속 구간) | 저속 건너뜀, 질문 없음 | 미확인 |

## 9. 판정 규칙 적용

| 규칙 | 값 | 근거 | 계약 반영 |
| --- | --- | --- | --- |
| 확인 질문: 브레이크 밟음 1,000ms 동안 감속 0.5m/s 미만, 이동 1.0m/s 이상 | 기존 값 | rules-lab 2.2.0, PRD 3.3.2, T97 | 이미 있음 |
| 질문 이유 구분: 관측 동안 0.5m/s 이상 빨라지면 "오히려 빨라졌습니다" | 0.5m/s | 유형 (c) 저속 시작 | 데모 앱만 |
| 저속(PMSA) 구간 제외 | 15km/h | 개조형 장치 15km/h[S49], R175 정지·크리프[S14] | **데모 앱만(rules-lab 미반영)** |
| 브레이크를 밟은 채 15km/h를 넘으면 그때부터 관측 | | NHTSA 정의(정지·저속 시작 + 제동력 상실처럼 보임) | 데모 앱만 |
| 급가속 후보: 1.0m/s 이상에서 2.5m/s² 이상 700ms | 기존 값, 기록만 | rules-lab 2.2.0. 시험에서 정상 합류 가속도 후보가 됨(정상 가속과 구별 불가 확인) | 이미 있음 |
| 반복 억제 5분 | 기존 값 | rules-lab 2.2.0 | 이미 있음 |

## 10. 검증

| 명령 | 결과 |
| --- | --- |
| `gradlew :app:testDebugUnitTest -Proborazzi.test.record=true` | 통과. RulesTest 11개, ScenarioReplayTest 1개(시나리오 8개), 화면 시험 12개 |
| `python scripts/vc_scenario.py sua-mat-entrapment` | 에뮬레이터에서 질문·안내 흐름 확인(8절) |
| `python scripts/vc_scenario.py sua-road-misapplication` | 질문·음성 없음 확인 |
| `python scripts/vc_scenario.py sua-parking-misapplication` | 급가속 후보 기록 줄 확인 |

- 환경: Windows 11, Pleos Connect v2.1.0 에뮬레이터(로컬, WHPX), 데모 앱 0.2.0-demo 디버그.

## 11. 한계와 남은 일

- 시나리오 속도는 사례 요약값과 단순 물리식으로 만든 근사입니다. 실제 차량 동역학, 매트 끼임 시 제동력, 운전자 반응은 재현하지 않았습니다. 콘솔은 장애물·충돌·에어백 전개를 만들 수 없습니다.
- 가장 흔한 실제 유형(가속페달 100%·브레이크 꺼짐)은 공개 신호로 정상 가속과 구별할 수 없습니다. **사용자 결정(2026-11-13): 지금처럼 유지합니다.** 이 유형에는 자동 질문을 넣지 않고, 급가속 후보는 기록만 하며, 안내는 운전자의 직접 보고("저절로 빨라져요" 버튼·말)로 시작합니다. 정상 가속에 질문이 뜨는 오탐을 피하기 위해서입니다.
- 15km/h 저속 구간 값은 데모 앱의 자체 결정입니다. 계약(rules-lab)·PRD에 넣으려면 계약 판 올림과 검사(`validate_contracts.py`)가 필요합니다.
- 디 올 뉴 아반떼 설명서의 비상 절차 문구를 확인하지 못해 긴급 음성은 "문장 자리"입니다.
- 에뮬레이터에서 재생하지 않은 시나리오 5개는 JVM 시험으로만 확인했습니다.
- 하위 에이전트가 모은 출처 중 메인 세션이 원문으로 다시 확인한 것은 NHTSA 수치 4개뿐입니다. 출처 사이 차이(국과수 연도별 건수, 강릉 재연 최고속도, 비상 시동 끄기 2초·3회 대 5초·5회, PMSA 경사 25% 대 25도)는 원문 대조가 필요합니다.

## 12. 출처 (접속일 2026-11-13)

S1 https://rosap.ntl.bts.gov/view/dot/8617 · S2 https://static.nhtsa.gov/odi/inv/2014/INRP-DP14003-61485.pdf · S3 https://static.nhtsa.gov/odi/inv/2014/INRP-DP14003-61481.pdf · S4 https://www.autosafety.org/us-department-transportation-releases-results-nhtsa-nasa-study-unintended-acceleration-toyota-vehicl/ · S5 https://www.automotiveworld.com/news-releases/nhtsa-safety-advisory-reducing-crashes-caused-pedal-error/ · S6 https://www.automotive-fleet.com/67056/what-to-do-during-a-sudden-acceleration-incident · S7 https://www.govinfo.gov/content/pkg/FR-2019-05-14/html/2019-09820.htm · S8 https://www.govinfo.gov/content/pkg/FR-2024-12-18/html/2024-29862.htm · S9 https://www.govinfo.gov/content/pkg/FR-2026-05-18/html/2026-09849.htm · S10 https://www.consumerreports.org/cro/news/2010/03/video-what-to-do-if-your-car-suddenly-accelerates-and-what-not-to-do/index.htm · S11 https://treaties.un.org/doc/Publication/MTDSG/Volume%20I/Chapter%20XI/XI-B-16-175.en.xml · S12 https://globalautoregs.com/rules/286/documents?year=2024 · S13 https://www.mlit.go.jp/report/press/jidosha10_hh_000330.html · S14 https://www.mlit.go.jp/report/press/jidosha10_hh_000336.html · S15 https://jafmate.jp/car/traffic_topics_20250731_1197730.html · S16 https://www.shinkim.com/kor/media/newsletter/3576 · S17 https://www.lawtimes.co.kr/news/articleView.html?idxno=228944 · S18 https://news.bizwatch.co.kr/article/real_estate/2025/10/23/0012 · S20 https://www.asiae.co.kr/en/article/2024122617034084296 · S21 https://news.bizwatch.co.kr/article/real_estate/2025/04/10/0008 · S22 https://www.hyundaimotorgroup.com/ko/story/CONT0000000000159177 · S23 https://newstomato.com/ReadNews.aspx?no=1237168 · S24 https://hyundaimotorgroup.co.kr/ko/news/hyundai-motor-company-all-new-avante · S25 https://view.asiae.co.kr/en/article/2026081816551752801 · S26 https://ownersmanual.hyundai.com/full_webhelp/MX5/2026/ko_KR/id60802dd3189.html · S27 https://ownersmanual.hyundai.com/full_webhelp/NE1/2026/ko_KR/id60802dd3189c.html · S28 https://ownersmanual.hyundai.com/full_webhelp/DN8HEV/2026/ko_KR/topic_b1s_xgd_2bc.html · S29 https://ownersmanual.hyundai.com/full_pdf/CN7/2026/ko_KR · S30 https://www.seoul.co.kr/news/newsView.php?id=20231209500012 · S31 https://www.mt.co.kr/society/2024/08/07/2024080617050165662 · S32 https://www.newsis.com/view/NISX20241029_0002938667 · S33 https://www.segye.com/newsView/20250610516346 · S34 https://www.seoul.co.kr/news/society/2026/09/23/20260923500181 · S35 https://byline.network/2025/11/14-530/ · S36 https://www.nocutnews.co.kr/news/5914684 · S37 https://www.newsis.com/view/NISX20240801_0002834436 · S38 https://www.segye.com/newsView/20240801509443 · S39 https://www.heraldk.com/article/2025080722215797884 · S40 https://www.heraldk.com/article/2025120318093850869 · S41 https://m.bobaedream.co.kr/board/bbs_view/nnews/229296/2/25 · S42 https://v.daum.net/v/20240527132907111 · S43 https://m.dailian.co.kr/news/view/1352983 · S45 https://www.nocutnews.co.kr/news/6604300 · S46 https://www.kado.net/news/articleView.html?idxno=2083766 · S48 https://opinion.lawmaking.go.kr/gcom/nsmLmSts/out/2219446/detailRP · S49 https://mediahub.seoul.go.kr/archives/2017245 · S53 https://www.heraldk.com/article/2026051018112560350

## 변경 기록

- 2026-11-13: 처음 작성. 조사, 시나리오 8개, 판정 규칙 적용, 에뮬레이터 확인 3개.
- 2026-11-13: 사용자 결정 기록. 브레이크 꺼짐형(가장 흔한 유형)에 자동 질문을 넣지 않고 지금처럼 유지(11절).
