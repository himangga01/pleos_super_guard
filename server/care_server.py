#!/usr/bin/env python3
"""플레오스 슈퍼 케어 데모용 모의 서버(Python 3 표준 라이브러리만 씁니다).

폰 앱 · 이 서버 · 차 앱의 연동 규약은 docs/analysis/super-care-pivot-2026-11-14.md 5절을 따릅니다.
규약의 끝점과 필드는 이름을 바꾸거나 빼지 않습니다. 더한 것은 아래 "더한 것"에 적습니다.

실행: python demo-app/server/care_server.py [--port 8766] [--host 127.0.0.1] [--data <폴더>] [--reset]
- 기본으로 127.0.0.1(PC 안)에만 엽니다. 에뮬레이터 안의 10.0.2.2가 PC의 127.0.0.1이므로 두 에뮬레이터(차 · 폰)는
  http://10.0.2.2:8766 로 접속합니다. 실제 폰은 `adb reverse tcp:8766 tcp:8766` 뒤 http://127.0.0.1:8766 로 접속합니다.
- 실행 중 자료는 data/ 아래 JSON 파일과 blobs/(사진 · 소리 조각)에 둡니다. 저장소에는 올리지 않습니다(data/.gitignore).
- 근거: 폰 센서 조사 docs/analysis/phone-sensing-research-2026-11-14.md 8.2절(127.0.0.1, serverTime, 차 화면 확인, 5분 번호).

규약 끝점(JSON):
  GET  /api/health
  POST /api/pair/start {carId}                    -> {code, expiresAt}
  POST /api/pair/claim {code, phoneName}          -> {carId}
  GET  /api/pair/status?carId=
  POST /api/requests {carId, kind, recordId?, partHint?}
  GET  /api/requests?carId=&pending=1
  POST /api/requests/{id}/done
  POST /api/captures {carId, requestId?, recordId?, kind, startedAt, durationMs, placement, partHint, metrics,
                      spectrum[], series[], imageJpegBase64?, audioM4aBase64?, candidates[], note}
  GET  /api/captures?carId=&since=
  POST /api/captures/{id}/ack
  GET  /api/shops,  GET /api/shops/{id},  GET /api/shops/{id}/reviews
  POST /api/shops/{id}/reviews {author, rating, dims, tags, text, visitId?}
  GET  /api/recalls?model=&year=

차 앱(sdk/CareLink.kt)과 맞춘 약속(2026-11-14):
  - GET /api/captures?carId=&since= 는 사진 · 소리 base64까지 든 전체 기록을 receivedAt 순서로 줍니다. 차는 저장한 뒤 ack를 부릅니다.
  - spectrum은 64칸 이하, series는 200점 이하입니다(더 길게 오면 서버가 칸을 묶어 줄입니다). metrics는 숫자만 둡니다.
  - 지표 이름: noise = avgDb · peakDb · dominantHz · spectrumMaxHz(spectrum = 0~spectrumMaxHz 64칸, dB 상대값),
    vibration = rmsG · peakG · dominantHz · spectrumMaxHz(series = 가속도 크기 g), warning_photo = confidence(candidates = 도감 id).
  - 진동은 실험 기능: sampleHz(실제 표본 주기), lowQuality(100Hz 밑이면 1), band1 · band2 · band3(1~5 · 5~20 · 20~80Hz RMS, g),
    peak1Hz · peak2Hz · peak3Hz. 소리는 상대 크기(dBFS, 음수)이며 thirdOctave[] · thirdOctaveHz[](1/3옥타브)를 더 보냅니다.
    에뮬레이터 진동 · 시험 입력 재생으로 만든 기록은 note가 "시험 입력"입니다(차 앱이 시험 입력 꼬리표를 붙임).
  - 요청 kind: noise · vibration · warning_photo · photo(· receipt) = 기록 요청, trunk_reminder · rear_seat_reminder = 알림만(text).
  - 차 화면 QR: pleoscare://pair?code=123456&server=http://10.0.2.2:8766
  - 짝 맺기는 차 화면 확인을 거칩니다: pair/claim -> 확인 기다림(pending) -> 차가 POST /api/pair/confirm {carId}(또는 reject)
    -> GET /api/pair/status 가 {paired: true, phoneName}. 확인 전에는 {paired: false, pending: true, phoneName}입니다.

더한 것(규약에 없는 필드 · 끝점, 빼거나 이름을 바꾼 것은 없음):
  - 모든 JSON 응답(객체)에 serverTime(서버 시각, epoch ms)을 붙입니다. 폰은 이것으로 시계 차이를 재서 clockOffsetMs로 보냅니다.
  - 목록 응답은 {"requests": [...]}, {"captures": [...]}, {"shops": [...]}, {"reviews": [...]}, {"recalls": [...]}처럼 감쌉니다.
    captures · requests 목록에는 다음 since로 쓸 "now"(서버 시각, ms)를 같이 줍니다. since는 서버가 받은 시각(receivedAt) 기준입니다.
  - pair/start 응답: carId, createdAt, qrText(위 QR 모양, server는 요청의 Host 머리글로 만듦). 번호는 5분 동안 살아 있습니다.
    pair/claim 응답: carId, phoneId, phoneName, state, pending: true, paired: false. 번호가 5번 틀리면 429(too_many_attempts)와 함께
    살아 있는 번호를 모두 지웁니다(차에서 새 번호를 받음).
    POST /api/pair/confirm {carId}, POST /api/pair/reject {carId}: 차 화면의 [연결] · [거절]. 기다리는 폰이 없으면 409.
    pair/status 응답: paired, pending, rejected, phoneName, phoneId, claimedAt, pairedAt, lastSeenAt, phoneOnline, pendingCode, expiresAt.
    폰이 X-Phone-Id 머리글을 붙여 GET /api/requests · /api/pair/status 를 부르면 lastSeenAt이 갱신됩니다(차 화면의 "폰 연결됨" 표시용).
    확인 전(pending)이나 거절된 폰의 X-Phone-Id로 기록을 올리면 403(pair_not_confirmed)입니다.
  - POST /api/pair/unpair {carId}: 연결을 끊습니다.
  - requests: text(알림 문구), note, placement(선택), reminder(알림 요청이면 true), status(pending|done|cancelled), createdAt, doneAt,
    captureId. POST /api/requests/{id}/cancel. 폰이 requestId를 붙여 기록을 올리면 그 요청은 자동으로 done이 됩니다.
    알림 요청은 폰에서 운전자가 "확인"을 누르면 done이 됩니다.
  - captures: summary(한 줄 요약), spectrumHz[](spectrum 칸의 중심 주파수), spectrumUnit, seriesStepMs, seriesUnit,
    confirmed(사용자가 고른 경고등 도감 id), confirmedName, phoneName, phoneId, labels(글자 값), ackedAt, ackRecordId, ackBy,
    hasImage, hasAudio, imageBytes, audioBytes, imageUrl, audioUrl. 숫자가 아닌 metrics 값은 labels로 옮깁니다.
    규약 밖의 작은 JSON 필드(32KB 이하, 예: thirdOctave[], clockOffsetMs)는 받은 그대로 둡니다.
    GET /api/captures?lite=1 은 사진 · 소리 base64를 빼고 줍니다(&kind= 거르기). GET /api/captures/{id}, /api/captures/{id}/image,
    /api/captures/{id}/audio. ack 본문(선택): {recordId, by}.
  - shops: partner(제휴 정비소, shop-a만 true), unverifiedCount, address, phoneLabel, prices[], evDetail, label,
    tags(많이 나온 후기 꼬리표 3개). rating · reviewCount는 방문이 확인된 후기(verified)만 셉니다(차 앱은 5개 미만이면 별점을 숨김).
    상세는 dimAverages(사실 질문마다 "예" 비율 0~1), dimAnswered(답한 후기 수), dimKeys, tagCounts, ratingHistogram,
    recentReviews, reviewTags도 줍니다.
  - 후기 dims는 사실 질문 4개(조사 6.4절, 차 앱 CareData.REVIEW_FACTS): "견적대로 결제" · "설명을 들음" · "약속 시간에 끝남" · "증상 해결".
    값은 1(예) 또는 0(아니요)만 받고, 열쇠가 없으면 답하지 않은 것입니다. 별점(rating)은 1~5 그대로입니다.
    후기에는 createdAt(ms), example, label, verified, visitId, carId가 있습니다. POST 본문의 visitId가 같은 정비소(carId를 주면 같은 차)의
    방문 기록이면 verified: true, 아니면 false(목록에는 남고 별점 · 후기 수에는 들어가지 않음).
    GET .../reviews?carId= 로 부르면 그 차가 쓴 후기에 mine: true가 붙습니다.
  - recalls: 차 앱 Recall 필드(id, title, part, risk, action, period, done, example, source, level, fix, gen, produced, ota, checked)
    + cause, model, year, label. 가상-001 · 가상-002(차 앱 CareData.RECALLS와 같은 내용).
  - POST /api/visits {carId, shopId, work, visitedAt?, costWon?, mileageKm?, note?}, GET /api/visits?carId=: 정비 방문 기록.
    차 앱은 방문을 남길 때 이것을 부르고, 후기를 쓸 때 visitId를 함께 보냅니다.
  - 모든 예시 자료에는 example: true와 정직성 꼬리표(label)를 붙입니다.
"""

import argparse
import base64
import binascii
import json
import os
import re
import secrets
import sys
import threading
import time
import traceback
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

VERSION = "0.1.0-demo"
DEFAULT_HOST = "127.0.0.1"  # PC 안에서만 엽니다. 두 에뮬레이터는 10.0.2.2, 실제 폰은 adb reverse로 같은 루프백에 붙습니다.
# 데모 포트는 8766입니다(2026-11-14 결정: 전환 기록 5절의 8765는 데모 PC에서 다른 프로그램이 쓰고 있었음). --port로 바꿀 수 있습니다.
DEFAULT_PORT = 8766
CODE_TTL_MS = 5 * 60 * 1000  # 짝 맺기 번호 유효 시간 5분(폰 센서 조사 8.2절)
MAX_WRONG_CODES = 5  # 번호가 5번 틀리면 살아 있는 번호를 모두 지웁니다(차에서 새 번호를 받음)
PHONE_ONLINE_MS = 20 * 1000  # 폰이 20초 안에 요청 목록 · 짝 상태를 물었으면 "연결됨"
MAX_BODY = 25 * 1024 * 1024
MAX_IMAGE = 6 * 1024 * 1024
MAX_AUDIO = 4 * 1024 * 1024
KINDS = ("noise", "vibration", "warning_photo", "photo", "receipt")
# 차 -> 폰 요청. 앞의 다섯은 기록 요청, 뒤의 둘은 기록 없이 폰에 알림만 띄우는 요청(text 필드)입니다.
REMINDER_KINDS = ("trunk_reminder", "rear_seat_reminder")
REQUEST_KINDS = KINDS + REMINDER_KINDS
SPECTRUM_MAX = 64  # 차 앱이 그리는 칸 수
SERIES_MAX = 200
# 후기 사실 질문 4개(차 앱 CareData.REVIEW_FACTS, 조사 6.4절). 값은 1 = 예, 0 = 아니요, 열쇠가 없으면 답하지 않음.
DIM_KEYS = ("견적대로 결제", "설명을 들음", "약속 시간에 끝남", "증상 해결")
DIM_ALIASES = {
    "paidasquoted": "견적대로 결제", "explained": "설명을 들음", "ontime": "약속 시간에 끝남", "fixed": "증상 해결",
    "견적대로결제": "견적대로 결제", "설명을들음": "설명을 들음", "약속시간에끝남": "약속 시간에 끝남", "증상해결": "증상 해결",
}
SHOP_LABEL = "예시 정비소 · 실제 업체 아님"
REVIEW_LABEL = "예시 후기 · 가상 사용자"
RECALL_LABEL = "가상 예시 · 실제 리콜 아님"
DAY_MS = 86_400_000


def now_ms():
    return int(time.time() * 1000)


class ApiError(Exception):
    def __init__(self, status, code, message):
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message


class Raw:
    """JSON이 아닌 응답(사진 · 소리 · 안내 글)."""

    def __init__(self, data, content_type):
        self.data = data
        self.content_type = content_type


# ---------------------------------------------------------------- 씨앗 자료(예시)

SHOPS = [
    {
        "id": "shop-a", "name": "정비소 A (예시)",
        "services": ["엔진오일", "타이어", "브레이크", "전기 장치"],
        "hours": "평일 09:00~18:00 · 토 09:00~13:00", "ev": True,
        "priceNote": "엔진오일 교체 공임 2만 원대(예시)",
        "certs": ["제조사 협력 정비(예시)", "전기차 정비 교육 이수(예시)"],
        "evDetail": "전기차 · 하이브리드 일반 점검과 고전압 장치 안전 점검(예시)",
        "prices": [
            {"item": "엔진오일 교체 공임", "price": "2만 원대(예시)"},
            {"item": "브레이크 패드 교체 공임", "price": "4만 원대(예시)"},
            {"item": "타이어 위치 교환", "price": "2만 원대(예시)"},
        ],
        "address": "예시 주소 · 차 위치에서 약 3km", "phoneLabel": "예시 번호(연결 안 됨)", "partner": True,
    },
    {
        "id": "shop-b", "name": "정비소 B (예시)",
        "services": ["타이어", "휠 얼라인먼트", "에어컨"],
        "hours": "매일 08:30~20:00", "ev": False,
        "priceNote": "얼라인먼트 5만 원대(예시)",
        "certs": ["타이어 전문(예시)"],
        "evDetail": "전기차 전용 정비는 하지 않음(예시). 타이어 · 얼라인먼트는 가능",
        "prices": [
            {"item": "휠 얼라인먼트", "price": "5만 원대(예시)"},
            {"item": "타이어 교체 공임(1개)", "price": "1만 원대(예시)"},
            {"item": "에어컨 필터 교체", "price": "2만 원대(예시)"},
        ],
        "address": "예시 주소 · 차 위치에서 약 6km", "phoneLabel": "예시 번호(연결 안 됨)", "partner": False,
    },
    {
        "id": "shop-c", "name": "정비소 C (예시)",
        "services": ["판금 · 도장", "유리", "실내"],
        "hours": "평일 09:00~19:00", "ev": True,
        "priceNote": "견적 후 진행(예시)",
        "certs": ["사고 수리 전문(예시)"],
        "evDetail": "전기차 사고 수리 시 고전압 차단 절차를 따름(예시)",
        "prices": [
            {"item": "문 긁힘 부분 도장", "price": "견적 후 결정(예시)"},
            {"item": "앞유리 작은 손상 수리", "price": "5만 원대(예시)"},
            {"item": "실내 클리닝", "price": "10만 원대(예시)"},
        ],
        "address": "예시 주소 · 차 위치에서 약 8km", "phoneLabel": "예시 번호(연결 안 됨)", "partner": False,
    },
]

# (shopId, 작성자 번호, 별점, (견적대로 결제, 설명을 들음, 약속 시간에 끝남, 증상 해결), 꼬리표, 글, 며칠 전)
# 사실 답: 1 = 예, 0 = 아니요, None = 답하지 않음. 모두 예시 사용자의 가상 후기입니다.
_SEED_REVIEWS = [
    ("shop-a", 1, 5, (1, 1, 1, 1), ["기록을 보고 바로 찾음", "설명이 자세함"], "앱에 남긴 소리 기록을 보여 줬더니 원인을 빨리 찾았습니다.", 2),
    ("shop-a", 2, 5, (1, None, 1, 1), ["빠름", "재방문 의사"], "엔진오일 교체가 예약 시간 안에 끝났습니다.", 5),
    ("shop-a", 3, 5, (1, 1, 1, 1), ["설명이 자세함", "가격이 투명함"], "바꾼 부품을 보여 주며 설명해 줘서 믿음이 갔습니다.", 9),
    ("shop-a", 4, 4, (1, 1, 0, 1), ["재방문 의사"], "전기차 점검도 해 줘서 좋았습니다. 약속보다 조금 늦게 끝났습니다.", 14),
    ("shop-a", 5, 5, (1, 1, 1, 1), ["기록을 보고 바로 찾음", "빠름"], "진동 기록 그래프를 보고 바로 바퀴 쪽을 살폈습니다.", 20),
    ("shop-a", 6, 4, (0, 1, 1, 1), ["가격 확인 필요"], "친절했지만 부품값이 견적보다 조금 더 나왔습니다.", 27),
    ("shop-a", 7, 5, (1, 1, 1, None), ["빠름", "설명이 자세함"], "리콜 점검을 같이 받았습니다. 끝나는 시간을 미리 알려 줬습니다.", 33),
    ("shop-a", 8, 4, (1, 1, 1, 1), ["재방문 의사"], "브레이크 패드를 바꾼 뒤 끼익 소리가 없어졌습니다.", 41),
    ("shop-b", 1, 5, (1, 1, 1, 1), ["빠름", "가격이 투명함"], "타이어 교체와 위치 교환을 한 번에 했습니다.", 3),
    ("shop-b", 2, 4, (1, 1, 1, 1), ["설명이 자세함"], "얼라인먼트 뒤 핸들 쏠림이 줄었습니다.", 8),
    ("shop-b", 3, 4, (1, None, 0, 1), ["재방문 의사"], "주말에도 열어서 편했습니다. 손님이 많아 약속보다 늦었습니다.", 12),
    ("shop-b", 4, 4, (1, 1, 1, 0), ["설명이 자세함"], "에어컨 냄새 점검을 받았습니다. 냄새가 조금 남아 다시 가 볼 생각입니다.", 18),
    ("shop-b", 5, 4, (1, 1, 1, None), ["기록을 보고 바로 찾음"], "타이어 사진을 보여 주니 교체 시기를 알려 줬습니다.", 25),
    ("shop-b", 6, 4, (1, 0, 1, 1), ["가격이 투명함"], "가격표가 붙어 있어서 고르기 쉬웠습니다. 설명은 짧았습니다.", 31),
    ("shop-c", 1, 4, (1, 1, 0, 1), ["재방문 의사"], "문 긁힘 도장이 깔끔하게 됐습니다. 하루 늦게 끝났습니다.", 4),
    ("shop-c", 2, 4, (1, 1, 1, 1), ["가격이 투명함"], "견적을 먼저 받고 진행해서 좋았습니다.", 10),
    ("shop-c", 3, 3, (0, 1, 0, 1), ["가격 확인 필요"], "수리는 잘 됐지만 생각보다 오래 걸리고 값도 더 나왔습니다.", 16),
    ("shop-c", 4, 4, (1, None, 1, 1), ["빠름"], "앞유리 작은 손상을 빨리 고쳐 줬습니다.", 22),
    ("shop-c", 5, 4, (1, 1, 1, 1), ["기록을 보고 바로 찾음"], "사진 기록을 보여 주니 손상 부위를 바로 알아봤습니다.", 29),
    ("shop-c", 6, 4, (None, 1, 1, 1), ["설명이 자세함"], "실내 클리닝 결과가 마음에 들었습니다.", 36),
    ("shop-c", 7, 4, (1, 1, 0, None), ["설명이 자세함"], "보험 처리 순서를 차근차근 알려 줬습니다.", 44),
]

REVIEW_TAGS = ["설명이 자세함", "기록을 보고 바로 찾음", "빠름", "가격이 투명함", "재방문 의사", "가격 확인 필요"]

# 차 앱 logic/CareData.kt의 가상 리콜 2건(가상-001 · 가상-002)과 같은 내용입니다. 실제 리콜이 아닙니다.
# level = 위험 표시(일반 · 실외 주차 · 운행 자제), fix = 입고 수리 · 무선 업데이트, ota = 제조사가 차 화면에서 이미 안내함(앱은 다시 알리지 않음).
# 실제 서비스의 출처는 공공 리콜 자료입니다(차량 케어 조사 보고서 5.5절).
RECALLS = [
    {
        "id": "가상-001", "title": "계기판 표시 소프트웨어 점검", "part": "계기판",
        "cause": "계기판 표시 소프트웨어의 시작 순서 문제 가능성(가상)",
        "risk": "계기판 표시가 늦게 켜질 수 있어요(가상)",
        "action": "무선 업데이트 또는 정비소 업데이트(약 30분, 무상)",
        "period": "2026-12-01 시정 시작(가상)", "done": False,
        "level": "일반", "fix": "무선 업데이트", "gen": "CN8", "produced": "2026-08 ~ 2026-09 생산(가상)", "ota": True, "checked": "2026-11-14",
    },
    {
        "id": "가상-002", "title": "하이브리드 제어기 소프트웨어 점검", "part": "하이브리드 제어기",
        "cause": "제어기 냉각 제어 소프트웨어 문제 가능성(가상)",
        "risk": "드물게 과열될 수 있어 실외 주차를 권해요(가상)",
        "action": "정비소에서 제어기 소프트웨어 업데이트(약 40분, 무상)",
        "period": "2026-11-20 시정 시작(가상)", "done": False,
        "level": "실외 주차", "fix": "입고 수리", "gen": "CN8", "produced": "2026-08 ~ 2026-10 생산 · 하이브리드(가상)", "ota": False, "checked": "2026-11-14",
    },
]
RECALL_MODELS = {"AVANTE": ("avante", "아반떼", "디 올 뉴 아반떼", "더 올 뉴 아반떼", "cn8")}
RECALL_YEARS = (2026,)


def build_seed_reviews(base_ms):
    out = []
    for shop_id, n, rating, facts, tags, text, days in _SEED_REVIEWS:
        out.append({
            "id": "rv-%s-%02d" % (shop_id[-1], n), "shopId": shop_id, "author": "예시 사용자 %d" % n,
            "rating": rating, "dims": {k: v for k, v in zip(DIM_KEYS, facts) if v is not None}, "tags": list(tags), "text": text,
            "createdAt": base_ms - days * DAY_MS - n * 3_600_000, "example": True, "label": REVIEW_LABEL,
            "verified": True, "visitId": None, "carId": None,
        })
    return out


# ---------------------------------------------------------------- 저장소

class Store:
    """메모리 자료 + data/ 폴더의 JSON 파일. 모든 변경은 잠금 안에서 하고 바로 파일에 씁니다."""

    FILES = ("pairs", "requests", "captures", "reviews", "visits")

    def __init__(self, data_dir):
        self.dir = os.path.abspath(data_dir)
        self.blob_dir = os.path.join(self.dir, "blobs")
        os.makedirs(self.blob_dir, exist_ok=True)
        self.lock = threading.RLock()
        self.clock = 0
        self.codes = {}  # code -> {code, carId, createdAt, expiresAt}
        # carId -> {carId, phoneId, phoneName, state(pending|paired|rejected), claimedAt, pairedAt, lastSeenAt}
        self.pairs = {}
        self.wrong_codes = 0
        self.requests = []
        self.captures = []  # 사진 · 소리는 blobs/에 따로 둡니다
        self.reviews = []  # 사용자가 쓴 후기(씨앗 후기는 코드에서 만듭니다)
        self.visits = []
        self.started = now_ms()
        self.seed_reviews = build_seed_reviews(self.started)
        self._load()

    # --- 파일
    def _path(self, name):
        return os.path.join(self.dir, name + ".json")

    def _load(self):
        def read(name, default):
            p = self._path(name)
            if not os.path.exists(p):
                return default
            try:
                with open(p, "r", encoding="utf-8") as f:
                    return json.load(f)
            except (OSError, ValueError) as e:
                print("  [경고] %s.json을 읽지 못해 비워 둡니다: %s" % (name, e), flush=True)
                return default

        pairs = read("pairs", {})
        self.codes = pairs.get("codes", {})
        self.pairs = pairs.get("pairs", {})
        self.requests = read("requests", [])
        self.captures = read("captures", [])
        self.reviews = read("reviews", [])
        self.visits = read("visits", [])
        for rec in self.requests + self.captures:
            self.clock = max(self.clock, int(rec.get("receivedAt") or rec.get("createdAt") or 0))

    def _save(self, name):
        data = {
            "pairs": {"codes": self.codes, "pairs": self.pairs},
            "requests": self.requests, "captures": self.captures, "reviews": self.reviews, "visits": self.visits,
        }[name]
        p = self._path(name)
        tmp = p + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=1)
        os.replace(tmp, p)

    def tick(self):
        """서버 시각(ms). 같은 ms에 두 건이 들어와도 since 비교가 어긋나지 않게 늘 1 이상 늘립니다."""
        self.clock = max(now_ms(), self.clock + 1)
        return self.clock

    def counts(self):
        with self.lock:
            return {"pairs": len(self.pairs), "requests": len(self.requests), "captures": len(self.captures),
                    "reviews": len(self.reviews) + len(self.seed_reviews), "visits": len(self.visits)}

    # --- 짝 맺기
    def pair_start(self, car_id, server_url):
        with self.lock:
            t = now_ms()
            self.codes = {c: v for c, v in self.codes.items() if v["expiresAt"] > t and v["carId"] != car_id}
            while True:
                code = "%06d" % secrets.randbelow(1_000_000)
                if code not in self.codes and code != "000000":
                    break
            rec = {"code": code, "carId": car_id, "createdAt": t, "expiresAt": t + CODE_TTL_MS}
            self.codes[code] = rec
            self._save("pairs")
            # 차 화면 QR과 같은 모양(차 앱 CareLink): pleoscare://pair?code=123456&server=http://10.0.2.2:8766
            return dict(rec, qrText="pleoscare://pair?code=%s&server=%s" % (code, server_url))

    def pair_claim(self, code, phone_name):
        """폰이 번호를 넣으면 '확인 기다림'(pending)이 됩니다. 차 화면에서 [연결]을 눌러야(pair/confirm) 연결됩니다."""
        with self.lock:
            rec = self.codes.get(code)
            if rec is None:
                self.wrong_codes += 1
                if self.wrong_codes >= MAX_WRONG_CODES:
                    self.wrong_codes = 0
                    self.codes = {}
                    self._save("pairs")
                    raise ApiError(429, "too_many_attempts", "번호가 %d번 틀려 잠갔습니다. 차에서 새 번호를 받으세요" % MAX_WRONG_CODES)
                raise ApiError(404, "code_not_found", "연결 번호가 맞지 않습니다")
            t = now_ms()
            if rec["expiresAt"] <= t:
                self.codes.pop(code, None)
                self._save("pairs")
                raise ApiError(410, "code_expired", "연결 번호의 시간이 지났습니다. 차에서 새 번호를 받으세요")
            self.codes.pop(code, None)
            self.wrong_codes = 0
            pair = {"carId": rec["carId"], "phoneId": "ph-" + secrets.token_hex(4), "phoneName": phone_name,
                    "state": "pending", "claimedAt": t, "pairedAt": None, "lastSeenAt": t}
            self.pairs[rec["carId"]] = pair
            self._save("pairs")
            return dict(pair, pending=True, paired=False)

    def pair_decide(self, car_id, accept):
        with self.lock:
            pair = self.pairs.get(car_id)
            if pair is None or pair.get("state") != "pending":
                raise ApiError(409, "no_pending_phone", "확인을 기다리는 폰이 없습니다")
            pair["state"] = "paired" if accept else "rejected"
            pair["pairedAt"] = now_ms() if accept else None
            self._save("pairs")
            return self.pair_status(car_id)

    def pair_status(self, car_id):
        with self.lock:
            t = now_ms()
            pair = self.pairs.get(car_id)
            code = next((v for v in self.codes.values() if v["carId"] == car_id and v["expiresAt"] > t), None)
            state = pair.get("state", "paired") if pair else None
            out = {"carId": car_id, "paired": state == "paired", "pending": state == "pending", "rejected": state == "rejected",
                   "phoneName": None, "phoneId": None, "claimedAt": None, "pairedAt": None, "lastSeenAt": None,
                   "phoneOnline": False, "pendingCode": code["code"] if code else None,
                   "expiresAt": code["expiresAt"] if code else None, "now": t}
            if pair:
                out.update(phoneName=pair["phoneName"], phoneId=pair["phoneId"], claimedAt=pair.get("claimedAt"),
                           pairedAt=pair.get("pairedAt"), lastSeenAt=pair.get("lastSeenAt"),
                           phoneOnline=state == "paired" and bool(pair.get("lastSeenAt")) and t - pair["lastSeenAt"] <= PHONE_ONLINE_MS)
            return out

    def pair_unpair(self, car_id):
        with self.lock:
            removed = self.pairs.pop(car_id, None) is not None
            self._save("pairs")
            return {"carId": car_id, "unpaired": removed}

    def phone_state(self, phone_id):
        """X-Phone-Id의 짝 상태(paired | pending | rejected | None)."""
        if not phone_id:
            return None
        with self.lock:
            for pair in self.pairs.values():
                if pair.get("phoneId") == phone_id:
                    return pair.get("state", "paired")
        return None

    def touch_phone(self, car_id, phone_id):
        if not phone_id:
            return
        with self.lock:
            pair = self.pairs.get(car_id)
            if pair and pair.get("phoneId") == phone_id:
                pair["lastSeenAt"] = now_ms()  # 파일에는 쓰지 않습니다(자주 바뀌는 값)

    # --- 기록 요청(차 -> 폰)
    def add_request(self, body):
        car_id = req_str(body, "carId")
        kind = body.get("kind")
        if kind not in REQUEST_KINDS:
            raise ApiError(400, "bad_kind", "kind는 %s 중 하나여야 합니다" % ", ".join(REQUEST_KINDS))
        text = opt_str(body, "text")
        if kind in REMINDER_KINDS and not text:
            text = "트렁크에 짐이 남아 있지 않나요?" if kind == "trunk_reminder" else "뒷좌석에 두고 내린 것이 없나요?"
        with self.lock:
            t = self.tick()
            rec = {"id": "req-" + secrets.token_hex(4), "carId": car_id, "kind": kind,
                   "recordId": opt_str(body, "recordId"), "partHint": opt_str(body, "partHint"),
                   "note": opt_str(body, "note"), "placement": opt_str(body, "placement"), "text": text,
                   "reminder": kind in REMINDER_KINDS,
                   "status": "pending", "createdAt": t, "doneAt": None, "captureId": None}
            self.requests.append(rec)
            self._save("requests")
            return dict(rec)

    def list_requests(self, car_id, pending, since):
        with self.lock:
            out = [dict(r) for r in self.requests
                   if (car_id is None or r["carId"] == car_id) and (not pending or r["status"] == "pending")
                   and r["createdAt"] > since]
            return {"requests": out, "now": self.clock or now_ms()}

    def finish_request(self, req_id, status, capture_id=None):
        with self.lock:
            rec = next((r for r in self.requests if r["id"] == req_id), None)
            if rec is None:
                raise ApiError(404, "request_not_found", "그런 요청이 없습니다")
            if rec["status"] == "pending":
                rec["status"] = status
                rec["doneAt"] = now_ms()
            if capture_id and not rec.get("captureId"):
                rec["captureId"] = capture_id
            self._save("requests")
            return dict(rec)

    # --- 폰 기록(폰 -> 차)
    def add_capture(self, body):
        car_id = req_str(body, "carId")
        kind = body.get("kind")
        if kind not in KINDS:
            raise ApiError(400, "bad_kind", "kind는 %s 중 하나여야 합니다" % ", ".join(KINDS))
        cap_id = "cap-" + secrets.token_hex(5)
        image = decode_b64(body.get("imageJpegBase64"), "imageJpegBase64", MAX_IMAGE)
        if image is not None and not image.startswith(b"\xff\xd8\xff"):
            raise ApiError(400, "bad_image", "imageJpegBase64는 JPEG이어야 합니다")
        audio = decode_b64(body.get("audioM4aBase64"), "audioM4aBase64", MAX_AUDIO)
        if audio is not None and audio[4:8] != b"ftyp":
            raise ApiError(400, "bad_audio", "audioM4aBase64는 M4A(MP4 컨테이너)여야 합니다")

        metrics, labels = {}, {}
        raw_metrics = body.get("metrics") or {}
        if not isinstance(raw_metrics, dict):
            raise ApiError(400, "bad_metrics", "metrics는 객체여야 합니다")
        for k, v in raw_metrics.items():
            if isinstance(v, bool):
                metrics[str(k)] = 1.0 if v else 0.0
            elif isinstance(v, (int, float)):
                metrics[str(k)] = float(v)
            elif v is not None:
                labels[str(k)] = v  # 글자 값은 labels로 옮깁니다(차 앱은 metrics를 숫자 지도로 읽음)
        if isinstance(body.get("labels"), dict):
            labels.update(body["labels"])

        rec = {
            "id": cap_id, "carId": car_id, "requestId": opt_str(body, "requestId"), "recordId": opt_str(body, "recordId"),
            "kind": kind, "startedAt": num(body.get("startedAt"), now_ms(), "startedAt", integer=True),
            "durationMs": num(body.get("durationMs"), 0, "durationMs", integer=True),
            "placement": opt_str(body, "placement"), "partHint": opt_str(body, "partHint"),
            "metrics": metrics, "labels": labels,
            # 차 앱은 spectrum 64칸, series 200점까지 그립니다. 더 길게 오면 칸을 묶어 줄입니다.
            "spectrum": shrink(num_list(body.get("spectrum"), "spectrum", 8192), SPECTRUM_MAX),
            "spectrumHz": shrink(num_list(body.get("spectrumHz"), "spectrumHz", 8192), SPECTRUM_MAX),
            "spectrumUnit": opt_str(body, "spectrumUnit"),
            "series": shrink(num_list(body.get("series"), "series", 20000), SERIES_MAX),
            "seriesStepMs": num(body.get("seriesStepMs"), None, "seriesStepMs"),
            "seriesUnit": opt_str(body, "seriesUnit"),
            "candidates": str_list(body.get("candidates"), "candidates", 40),
            "confirmed": opt_str(body, "confirmed"), "confirmedName": opt_str(body, "confirmedName"),
            "note": opt_str(body, "note"), "summary": opt_str(body, "summary"),
            "phoneName": opt_str(body, "phoneName"), "phoneId": opt_str(body, "phoneId"),
            "hasImage": image is not None, "hasAudio": audio is not None,
            "imageBytes": len(image) if image else 0, "audioBytes": len(audio) if audio else 0,
            "ackedAt": None, "ackRecordId": None, "ackBy": None,
        }
        # 규약 밖의 더한 필드(예: thirdOctave[], clockOffsetMs)는 작은 JSON 값이면 그대로 둡니다.
        for k, v in body.items():
            if k in rec or k in ("imageJpegBase64", "audioM4aBase64", "receivedAt", "id") or not isinstance(k, str):
                continue
            if len(json.dumps(v, ensure_ascii=False)) <= 32_000:
                rec[k] = v
        if image is not None:
            write_bytes(os.path.join(self.blob_dir, cap_id + ".jpg"), image)
        if audio is not None:
            write_bytes(os.path.join(self.blob_dir, cap_id + ".m4a"), audio)
        with self.lock:
            rec["receivedAt"] = self.tick()
            self.captures.append(rec)
            self._save("captures")
            if rec["requestId"]:
                try:
                    self.finish_request(rec["requestId"], "done", cap_id)
                except ApiError:
                    pass  # 없는 요청 id는 기록만 남깁니다
            return dict(rec)

    def _with_blobs(self, rec, lite):
        out = dict(rec)
        out["imageUrl"] = "/api/captures/%s/image" % rec["id"] if rec.get("hasImage") else None
        out["audioUrl"] = "/api/captures/%s/audio" % rec["id"] if rec.get("hasAudio") else None
        if not lite:
            out["imageJpegBase64"] = self._blob_b64(rec["id"], ".jpg") if rec.get("hasImage") else None
            out["audioM4aBase64"] = self._blob_b64(rec["id"], ".m4a") if rec.get("hasAudio") else None
        return out

    def _blob_b64(self, cap_id, ext):
        data = self.blob(cap_id, ext)
        return base64.b64encode(data).decode("ascii") if data is not None else None

    def blob(self, cap_id, ext):
        p = os.path.join(self.blob_dir, cap_id + ext)
        if not re.fullmatch(r"cap-[0-9a-f]+", cap_id) or not os.path.exists(p):
            return None
        with open(p, "rb") as f:
            return f.read()

    def list_captures(self, car_id, since, lite, kind):
        with self.lock:
            rows = [r for r in self.captures
                    if (car_id is None or r["carId"] == car_id) and r["receivedAt"] > since and (kind is None or r["kind"] == kind)]
            cursor = self.clock or now_ms()
        return {"captures": [self._with_blobs(r, lite) for r in rows], "now": cursor, "count": len(rows)}

    def get_capture(self, cap_id, lite):
        with self.lock:
            rec = next((r for r in self.captures if r["id"] == cap_id), None)
        if rec is None:
            raise ApiError(404, "capture_not_found", "그런 기록이 없습니다")
        return self._with_blobs(rec, lite)

    def ack_capture(self, cap_id, body):
        with self.lock:
            rec = next((r for r in self.captures if r["id"] == cap_id), None)
            if rec is None:
                raise ApiError(404, "capture_not_found", "그런 기록이 없습니다")
            if not rec.get("ackedAt"):
                rec["ackedAt"] = now_ms()
            if body:
                rec["ackRecordId"] = opt_str(body, "recordId") or rec.get("ackRecordId")
                rec["ackBy"] = opt_str(body, "by") or rec.get("ackBy")
            self._save("captures")
            return self._with_blobs(rec, True)

    # --- 정비소 · 후기
    def shop(self, shop_id):
        shop = next((s for s in SHOPS if s["id"] == shop_id), None)
        if shop is None:
            raise ApiError(404, "shop_not_found", "그런 정비소가 없습니다")
        return shop

    def reviews_of(self, shop_id):
        with self.lock:
            rows = [r for r in self.seed_reviews + self.reviews if r["shopId"] == shop_id]
        return sorted(rows, key=lambda r: r["createdAt"], reverse=True)

    def shop_summary(self, shop, detail=False):
        """별점 · 후기 수는 방문이 확인된 후기(verified)만 셉니다(조사 6.4절). 차 앱은 5개 미만이면 별점을 숨깁니다."""
        all_rows = self.reviews_of(shop["id"])
        rows = [r for r in all_rows if r.get("verified", True)]
        n = len(rows)
        tag_counts = {}
        for r in rows:
            for tag in r.get("tags", []):
                tag_counts[tag] = tag_counts.get(tag, 0) + 1
        tags_sorted = sorted(tag_counts.items(), key=lambda kv: (-kv[1], REVIEW_TAGS.index(kv[0]) if kv[0] in REVIEW_TAGS else 99))
        out = {
            "id": shop["id"], "name": shop["name"], "services": shop["services"], "hours": shop["hours"], "ev": shop["ev"],
            "priceNote": shop["priceNote"], "certs": shop["certs"],
            "rating": round(sum(r["rating"] for r in rows) / n, 1) if n else 0.0, "reviewCount": n,
            "unverifiedCount": len(all_rows) - n, "partner": bool(shop.get("partner")),
            "example": True, "label": SHOP_LABEL, "address": shop["address"], "tags": [t for t, _ in tags_sorted[:3]],
        }
        if detail:
            # 사실 질문마다 "예" 비율(0~1, 답한 후기 기준)과 답한 후기 수
            ratios, answered = {}, {}
            for key in DIM_KEYS:
                vals = [r["dims"][key] for r in rows if isinstance(r.get("dims"), dict) and key in r["dims"]]
                ratios[key] = round(sum(vals) / len(vals), 2) if vals else None
                answered[key] = len(vals)
            hist = {str(s): sum(1 for r in rows if r["rating"] == s) for s in range(5, 0, -1)}
            out.update(evDetail=shop["evDetail"], prices=shop["prices"], phoneLabel=shop["phoneLabel"],
                       dimAverages=ratios, dimAnswered=answered, dimKeys=list(DIM_KEYS), tagCounts=dict(tags_sorted),
                       ratingHistogram=hist, recentReviews=rows[:3], reviewTags=REVIEW_TAGS)
        return out

    def add_review(self, shop_id, body):
        self.shop(shop_id)
        author = (opt_str(body, "author") or "나").strip()[:30] or "나"
        rating = body.get("rating")
        if isinstance(rating, bool) or not isinstance(rating, (int, float)) or not 1 <= rating <= 5:
            raise ApiError(400, "bad_rating", "rating은 1~5 숫자여야 합니다")
        dims_in = body.get("dims") or {}
        if not isinstance(dims_in, dict):
            raise ApiError(400, "bad_dims", "dims는 객체여야 합니다")
        dims = {}
        for k, v in dims_in.items():
            raw = str(k).strip()
            key = raw if raw in DIM_KEYS else DIM_ALIASES.get(raw.replace(" ", "").lower(), raw)
            if key not in DIM_KEYS:
                raise ApiError(400, "bad_dims", "dims 열쇠는 %s 중 하나입니다" % " · ".join(DIM_KEYS))
            if v is None:
                continue  # 답하지 않음
            if isinstance(v, bool) or not isinstance(v, (int, float)) or v not in (0, 1):
                raise ApiError(400, "bad_dims", "dims 값은 1(예) 또는 0(아니요)입니다")
            dims[key] = int(v)
        text = (opt_str(body, "text") or "").strip()
        if len(text) > 500:
            raise ApiError(400, "text_too_long", "후기는 500자까지 씁니다")
        car_id = opt_str(body, "carId")
        visit_id = opt_str(body, "visitId")
        rec = {"id": "rv-" + secrets.token_hex(4), "shopId": shop_id, "author": author, "rating": int(round(rating)),
               "dims": dims, "tags": str_list(body.get("tags"), "tags", 10), "text": text,
               "visitId": visit_id, "carId": car_id, "example": False, "label": None}
        with self.lock:
            # 방문 확인 후기: 같은 정비소(그리고 carId를 주면 같은 차)의 방문 기록 id가 있어야 verified입니다.
            visit = next((v for v in self.visits if v["id"] == visit_id), None) if visit_id else None
            rec["verified"] = bool(visit and visit["shopId"] == shop_id and (car_id is None or visit["carId"] == car_id))
            rec["createdAt"] = self.tick()
            self.reviews.append(rec)
            self._save("reviews")
            if rec["verified"]:
                visit["reviewed"] = True
                visit["reviewId"] = rec["id"]
                self._save("visits")
        return rec

    # --- 방문 기록(더한 것)
    def add_visit(self, body):
        car_id = req_str(body, "carId")
        shop = self.shop(req_str(body, "shopId"))
        with self.lock:
            rec = {"id": "vs-" + secrets.token_hex(4), "carId": car_id, "shopId": shop["id"], "shopName": shop["name"],
                   "work": opt_str(body, "work") or "", "visitedAt": num(body.get("visitedAt"), now_ms(), "visitedAt", integer=True),
                   "costWon": num(body.get("costWon"), None, "costWon", integer=True),
                   "mileageKm": num(body.get("mileageKm"), None, "mileageKm", integer=True),
                   "note": opt_str(body, "note"), "reviewed": False, "reviewId": None, "createdAt": self.tick()}
            self.visits.append(rec)
            self._save("visits")
            return dict(rec)

    def list_visits(self, car_id):
        with self.lock:
            rows = [dict(v) for v in self.visits if car_id is None or v["carId"] == car_id]
        return {"visits": sorted(rows, key=lambda v: v["visitedAt"], reverse=True)}


# ---------------------------------------------------------------- 값 검사

def req_str(body, key):
    v = body.get(key) if isinstance(body, dict) else None
    if not isinstance(v, str) or not v.strip():
        raise ApiError(400, "missing_" + key, "%s(글자)가 필요합니다" % key)
    return v.strip()[:200]


def opt_str(body, key):
    v = body.get(key) if isinstance(body, dict) else None
    if v is None:
        return None
    if isinstance(v, (int, float)) and not isinstance(v, bool):
        return str(v)
    if not isinstance(v, str):
        raise ApiError(400, "bad_" + key, "%s는 글자여야 합니다" % key)
    v = v.strip()
    return v[:2000] if v else None


def num(v, default, key, integer=False):
    if v is None:
        return default
    if isinstance(v, bool) or not isinstance(v, (int, float)):
        raise ApiError(400, "bad_" + key, "%s는 숫자여야 합니다" % key)
    return int(v) if integer else float(v)


def num_list(v, key, limit):
    if v is None:
        return []
    if not isinstance(v, list) or len(v) > limit:
        raise ApiError(400, "bad_" + key, "%s는 숫자 %d개 이하의 배열이어야 합니다" % (key, limit))
    out = []
    for x in v:
        if isinstance(x, bool) or not isinstance(x, (int, float)):
            raise ApiError(400, "bad_" + key, "%s에는 숫자만 넣습니다" % key)
        out.append(x)
    return out


def shrink(values, limit):
    """배열을 [limit]칸 이하로 줄입니다(칸마다 평균). 이미 짧으면 그대로 둡니다."""
    n = len(values)
    if n <= limit:
        return values
    out = []
    for i in range(limit):
        a, b = i * n // limit, max((i + 1) * n // limit, i * n // limit + 1)
        chunk = values[a:b]
        out.append(round(sum(chunk) / len(chunk), 6))
    return out


def str_list(v, key, limit):
    if v is None:
        return []
    if not isinstance(v, list) or len(v) > limit:
        raise ApiError(400, "bad_" + key, "%s는 글자 %d개 이하의 배열이어야 합니다" % (key, limit))
    return [str(x)[:100] for x in v if isinstance(x, (str, int, float)) and not isinstance(x, bool)]


def decode_b64(v, key, limit):
    if v in (None, ""):
        return None
    if not isinstance(v, str):
        raise ApiError(400, "bad_" + key, "%s는 base64 글자여야 합니다" % key)
    if v.startswith("data:") and "," in v:
        v = v.split(",", 1)[1]
    try:
        data = base64.b64decode(v, validate=False)
    except (binascii.Error, ValueError):
        raise ApiError(400, "bad_" + key, "%s를 base64로 풀지 못했습니다" % key)
    if len(data) > limit:
        raise ApiError(413, "too_large", "%s가 너무 큽니다(%d바이트 넘음)" % (key, limit))
    return data


def write_bytes(path, data):
    tmp = path + ".tmp"
    with open(tmp, "wb") as f:
        f.write(data)
    os.replace(tmp, path)


def q_int(query, key, default=0):
    v = query.get(key)
    if v in (None, ""):
        return default
    try:
        return int(float(v))
    except ValueError:
        raise ApiError(400, "bad_" + key, "%s는 숫자여야 합니다" % key)


def q_flag(query, key):
    return str(query.get(key, "")).lower() in ("1", "true", "yes", "y")


# ---------------------------------------------------------------- 끝점

def h_index(store, q, body, headers):
    lines = ["플레오스 슈퍼 케어 모의 서버 %s (데모 · 예시 자료)" % VERSION, ""]
    lines += [("%-5s %s" % (m, p.pattern.replace("\\", ""))) for m, p, _ in ROUTES if p.pattern.startswith("/api")]
    return Raw(("\n".join(lines) + "\n").encode("utf-8"), "text/plain; charset=utf-8")


def h_health(store, q, body, headers):
    return {"ok": True, "name": "pleos-super-care-demo-server", "version": VERSION, "time": now_ms(),
            "startedAt": store.started, "counts": store.counts(), "label": "데모 모의 서버 · 실제 서비스 아님"}


def h_pair_start(store, q, body, headers):
    host = (headers.get("Host") or "10.0.2.2:%d" % DEFAULT_PORT).strip()
    return 201, store.pair_start(req_str(body, "carId"), "http://" + host)


def h_pair_claim(store, q, body, headers):
    code = re.sub(r"\D", "", str((body or {}).get("code", "")))
    if len(code) != 6:
        raise ApiError(400, "bad_code", "연결 번호는 숫자 6자리입니다")
    phone_name = (opt_str(body, "phoneName") or "폰").strip()[:60] or "폰"
    return store.pair_claim(code, phone_name)


def h_pair_status(store, q, body, headers):
    car_id = q.get("carId")
    if not car_id:
        raise ApiError(400, "missing_carId", "carId가 필요합니다")
    store.touch_phone(car_id, headers.get("X-Phone-Id"))
    return store.pair_status(car_id)


def h_pair_confirm(store, q, body, headers):
    return store.pair_decide(req_str(body, "carId"), True)


def h_pair_reject(store, q, body, headers):
    return store.pair_decide(req_str(body, "carId"), False)


def h_pair_unpair(store, q, body, headers):
    return store.pair_unpair(req_str(body, "carId"))


def h_requests_post(store, q, body, headers):
    return 201, store.add_request(body or {})


def h_requests_get(store, q, body, headers):
    car_id = q.get("carId") or None
    if car_id:
        store.touch_phone(car_id, headers.get("X-Phone-Id"))
    return store.list_requests(car_id, q_flag(q, "pending"), q_int(q, "since"))


def h_request_done(store, q, body, headers, req_id):
    return store.finish_request(req_id, "done", opt_str(body or {}, "captureId"))


def h_request_cancel(store, q, body, headers, req_id):
    return store.finish_request(req_id, "cancelled")


def h_captures_post(store, q, body, headers):
    phone_id = headers.get("X-Phone-Id")
    if store.phone_state(phone_id) in ("pending", "rejected"):
        raise ApiError(403, "pair_not_confirmed", "차 화면에서 연결을 확인한 뒤에 보낼 수 있습니다")
    if isinstance(body, dict) and not body.get("phoneId") and phone_id:
        body["phoneId"] = phone_id
    rec = store.add_capture(body or {})
    out = store._with_blobs(rec, True)
    return 201, out


def h_captures_get(store, q, body, headers):
    kind = q.get("kind") or None
    if kind is not None and kind not in KINDS:
        raise ApiError(400, "bad_kind", "kind는 %s 중 하나여야 합니다" % ", ".join(KINDS))
    return store.list_captures(q.get("carId") or None, q_int(q, "since"), q_flag(q, "lite"), kind)


def h_capture_get(store, q, body, headers, cap_id):
    return store.get_capture(cap_id, q_flag(q, "lite"))


def h_capture_image(store, q, body, headers, cap_id):
    data = store.blob(cap_id, ".jpg")
    if data is None:
        raise ApiError(404, "image_not_found", "사진이 없습니다")
    return Raw(data, "image/jpeg")


def h_capture_audio(store, q, body, headers, cap_id):
    data = store.blob(cap_id, ".m4a")
    if data is None:
        raise ApiError(404, "audio_not_found", "소리 조각이 없습니다")
    return Raw(data, "audio/mp4")


def h_capture_ack(store, q, body, headers, cap_id):
    return store.ack_capture(cap_id, body or {})


def h_shops(store, q, body, headers):
    return {"shops": [store.shop_summary(s) for s in SHOPS], "label": SHOP_LABEL}


def h_shop(store, q, body, headers, shop_id):
    return store.shop_summary(store.shop(shop_id), detail=True)


def h_shop_reviews(store, q, body, headers, shop_id):
    shop = store.shop(shop_id)
    summary = store.shop_summary(shop, detail=True)
    car_id = q.get("carId")  # 주면 그 차가 쓴 후기에 mine: true를 붙입니다
    rows = [dict(r, mine=bool(car_id) and r.get("carId") == car_id) for r in store.reviews_of(shop_id)]
    return {"shopId": shop_id, "reviews": rows,
            "summary": {k: summary[k] for k in ("rating", "reviewCount", "unverifiedCount", "partner", "dimAverages", "dimAnswered", "dimKeys",
                                                "tagCounts", "ratingHistogram")},
            "label": REVIEW_LABEL}


def h_shop_review_post(store, q, body, headers, shop_id):
    return 201, store.add_review(shop_id, body or {})


def h_recalls(store, q, body, headers):
    model = (q.get("model") or "").strip()
    year = q_int(q, "year", 0)
    matched_model = None
    if model:
        for canon, aliases in RECALL_MODELS.items():
            if model.upper() == canon or model.lower() in aliases:
                matched_model = canon
    else:
        matched_model = "AVANTE"
    ok = matched_model is not None and (year == 0 or year in RECALL_YEARS)
    rows = [dict(r, model="AVANTE", year=2026, example=True, source="가상 예시", label=RECALL_LABEL) for r in RECALLS] if ok else []
    return {"model": matched_model or model, "year": year or (2026 if ok else None), "recalls": rows, "label": RECALL_LABEL,
            "source": "모의 서버의 가상 예시. 실제 서비스는 공공 리콜 자료를 씁니다(조사 보고서 참고)"}


def h_visits_post(store, q, body, headers):
    return 201, store.add_visit(body or {})


def h_visits_get(store, q, body, headers):
    return store.list_visits(q.get("carId") or None)


ROUTES = [
    ("GET", re.compile(r"/"), h_index),
    ("GET", re.compile(r"/api/health"), h_health),
    ("POST", re.compile(r"/api/pair/start"), h_pair_start),
    ("POST", re.compile(r"/api/pair/claim"), h_pair_claim),
    ("GET", re.compile(r"/api/pair/status"), h_pair_status),
    ("POST", re.compile(r"/api/pair/confirm"), h_pair_confirm),
    ("POST", re.compile(r"/api/pair/reject"), h_pair_reject),
    ("POST", re.compile(r"/api/pair/unpair"), h_pair_unpair),
    ("POST", re.compile(r"/api/requests"), h_requests_post),
    ("GET", re.compile(r"/api/requests"), h_requests_get),
    ("POST", re.compile(r"/api/requests/([\w-]+)/done"), h_request_done),
    ("POST", re.compile(r"/api/requests/([\w-]+)/cancel"), h_request_cancel),
    ("POST", re.compile(r"/api/captures"), h_captures_post),
    ("GET", re.compile(r"/api/captures"), h_captures_get),
    ("GET", re.compile(r"/api/captures/([\w-]+)"), h_capture_get),
    ("GET", re.compile(r"/api/captures/([\w-]+)/image"), h_capture_image),
    ("GET", re.compile(r"/api/captures/([\w-]+)/audio"), h_capture_audio),
    ("POST", re.compile(r"/api/captures/([\w-]+)/ack"), h_capture_ack),
    ("GET", re.compile(r"/api/shops"), h_shops),
    ("GET", re.compile(r"/api/shops/([\w-]+)"), h_shop),
    ("GET", re.compile(r"/api/shops/([\w-]+)/reviews"), h_shop_reviews),
    ("POST", re.compile(r"/api/shops/([\w-]+)/reviews"), h_shop_review_post),
    ("GET", re.compile(r"/api/recalls"), h_recalls),
    ("POST", re.compile(r"/api/visits"), h_visits_post),
    ("GET", re.compile(r"/api/visits"), h_visits_get),
]


# ---------------------------------------------------------------- HTTP

class Handler(BaseHTTPRequestHandler):
    server_version = "PleosCareDemo/" + VERSION

    def log_message(self, fmt, *args):  # 기본 로그 대신 아래 한 줄 로그를 씁니다
        pass

    def do_GET(self):
        self._dispatch("GET")

    def do_POST(self):
        self._dispatch("POST")

    def do_PUT(self):
        self._send_json(405, {"error": "method_not_allowed", "message": "GET · POST만 씁니다"})

    do_DELETE = do_PUT
    do_PATCH = do_PUT

    def _read_body(self):
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            chunks, total = [], 0
            while True:
                line = self.rfile.readline().strip()
                size = int(line.split(b";")[0] or b"0", 16)
                if size == 0:
                    self.rfile.readline()
                    break
                total += size
                if total > MAX_BODY:
                    raise ApiError(413, "too_large", "본문이 너무 큽니다")
                chunks.append(self.rfile.read(size))
                self.rfile.readline()
            return b"".join(chunks)
        length = int(self.headers.get("Content-Length") or 0)
        if length > MAX_BODY:
            raise ApiError(413, "too_large", "본문이 너무 큽니다")
        return self.rfile.read(length) if length > 0 else b""

    def _dispatch(self, method):
        t0 = time.time()
        status, note = 500, ""
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/") or "/"
        try:
            query = {k: v[-1] for k, v in parse_qs(parsed.query).items()}
            body = None
            if method == "POST":
                raw = self._read_body()
                if raw.strip():
                    try:
                        body = json.loads(raw.decode("utf-8"))
                    except (UnicodeDecodeError, ValueError):
                        raise ApiError(400, "bad_json", "본문이 JSON이 아닙니다")
                    if not isinstance(body, dict):
                        raise ApiError(400, "bad_json", "본문은 JSON 객체여야 합니다")
            for m, pattern, fn in ROUTES:
                match = pattern.fullmatch(path)
                if match and m == method:
                    result = fn(self.server.store, query, body, self.headers, *match.groups())
                    break
            else:
                if any(p.fullmatch(path) for _, p, _ in ROUTES):
                    raise ApiError(405, "method_not_allowed", "이 주소는 다른 방식(GET/POST)으로 부릅니다")
                raise ApiError(404, "not_found", "없는 주소입니다: " + path)
            status = 200
            if isinstance(result, tuple):
                status, result = result
            if isinstance(result, Raw):
                self._send_raw(status, result.data, result.content_type)
            else:
                self._send_json(status, result)
            note = summarize(path, body, result)
            if query.get("carId") and "car=" not in note:
                note = ("car=%s %s" % (query["carId"], note)).strip()
        except ApiError as e:
            status, note = e.status, e.code
            self._send_json(e.status, {"error": e.code, "message": e.message})
        except (ConnectionError, BrokenPipeError):
            status, note = 499, "client closed"
        except Exception as e:  # 예상하지 못한 오류도 서버는 계속 돕니다
            status, note = 500, type(e).__name__
            traceback.print_exc()
            try:
                self._send_json(500, {"error": "server_error", "message": str(e)})
            except OSError:
                pass
        finally:
            ms = int((time.time() - t0) * 1000)
            print("%s %-4s %-34s %d %4dms %s" % (time.strftime("%H:%M:%S"), method, path[:34], status, ms, note), flush=True)

    def _send_json(self, status, payload):
        if isinstance(payload, dict) and "serverTime" not in payload:
            # 시각 맞추기: 기기는 offset = serverTime - (보낸 시각 + 받은 시각) / 2 로 시계 차이를 구합니다.
            payload = dict(payload, serverTime=now_ms())
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self._send_raw(status, data, "application/json; charset=utf-8")

    def _send_raw(self, status, data, content_type):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)


def summarize(path, body, result):
    """로그 한 줄 끝의 짧은 설명(사진 · 소리 base64는 찍지 않습니다)."""
    if not isinstance(result, dict):
        return ""
    if path == "/api/captures" and "kind" in result:
        bits = ["kind=%s" % result.get("kind"), "car=%s" % result.get("carId")]
        if result.get("requestId"):
            bits.append("req=%s" % result["requestId"])
        if result.get("hasImage"):
            bits.append("jpg=%dB" % result.get("imageBytes", 0))
        if result.get("hasAudio"):
            bits.append("m4a=%dB" % result.get("audioBytes", 0))
        return " ".join(bits)
    if "captures" in result:
        return "n=%d" % len(result["captures"])
    if "requests" in result:
        return "n=%d" % len(result["requests"])
    if path == "/api/pair/start":
        return "car=%s code=%s" % (result.get("carId"), result.get("code"))
    if path == "/api/pair/claim":
        return "car=%s phone=%s (차 확인 기다림)" % (result.get("carId"), result.get("phoneName"))
    if path in ("/api/pair/confirm", "/api/pair/reject"):
        return "car=%s paired=%s" % (result.get("carId"), result.get("paired"))
    if path == "/api/requests" and "kind" in result:
        return "kind=%s car=%s" % (result.get("kind"), result.get("carId"))
    return ""


def make_server(host, port, data_dir):
    server = ThreadingHTTPServer((host, port), Handler)
    server.daemon_threads = True
    server.store = Store(data_dir)
    return server


def main(argv=None):
    if hasattr(sys.stdout, "reconfigure"):
        # 콘솔은 그대로 두고, 파일로 돌린 로그는 UTF-8로 씁니다(Windows 기본 코드 페이지로 깨지지 않게).
        sys.stdout.reconfigure(errors="replace", **({} if sys.stdout.isatty() else {"encoding": "utf-8"}))
        sys.stderr.reconfigure(errors="replace")
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser(description="플레오스 슈퍼 케어 데모 모의 서버")
    ap.add_argument("--host", default=DEFAULT_HOST, help="기본 127.0.0.1(PC 안에서만). 같은 망의 다른 기기에 열 때만 0.0.0.0")
    ap.add_argument("--port", type=int, default=DEFAULT_PORT)
    ap.add_argument("--data", default=os.path.join(here, "data"), help="실행 중 자료 폴더(기본: server/data)")
    ap.add_argument("--reset", action="store_true", help="시작 전에 실행 중 자료(JSON · blobs)를 지웁니다")
    args = ap.parse_args(argv)
    if args.reset:
        for name in Store.FILES:
            p = os.path.join(args.data, name + ".json")
            if os.path.exists(p):
                os.remove(p)
        blob_dir = os.path.join(args.data, "blobs")
        if os.path.isdir(blob_dir):
            for f in os.listdir(blob_dir):
                os.remove(os.path.join(blob_dir, f))
    server = make_server(args.host, args.port, args.data)
    print("플레오스 슈퍼 케어 모의 서버 %s · http://%s:%d · 자료 폴더 %s" % (VERSION, args.host, args.port, os.path.relpath(server.store.dir)), flush=True)
    print("  에뮬레이터(차 · 폰): http://10.0.2.2:%d  /  실제 폰: adb reverse tcp:%d tcp:%d 뒤 http://127.0.0.1:%d" % (args.port, args.port, args.port, args.port), flush=True)
    print("  예시 자료(정비소 3곳 · 후기 %d건 · 가상 리콜 %d건)는 실제 업체 · 리콜이 아닙니다. 멈추려면 Ctrl+C" % (len(server.store.seed_reviews), len(RECALLS)), flush=True)
    try:
        server.serve_forever(poll_interval=0.5)
    except KeyboardInterrupt:
        print("멈춤", flush=True)
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
