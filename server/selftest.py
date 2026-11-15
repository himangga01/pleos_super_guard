#!/usr/bin/env python3
"""모의 서버 자체 시험: 짝 맺기 · 기록 요청 · 폰 기록 · 정비소 후기 · 리콜을 차례로 불러 봅니다.

기본: 임시 폴더를 자료 폴더로 쓰는 서버를 이 프로세스 안에서 띄워 시험합니다(실제 data/는 건드리지 않음).
  python demo-app/server/selftest.py
이미 돌고 있는 서버를 시험하려면(시험 자료가 그 서버에 남습니다):
  python demo-app/server/selftest.py --url http://127.0.0.1:8766
"""

import argparse
import base64
import json
import os
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

PASS, FAIL = [], []


def call(base, method, path, body=None, headers=None, raw=False):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(base + path, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json; charset=utf-8")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            payload = r.read()
            return r.status, (payload if raw else json.loads(payload.decode("utf-8")))
    except urllib.error.HTTPError as e:
        payload = e.read()
        try:
            return e.code, json.loads(payload.decode("utf-8"))
        except ValueError:
            return e.code, payload


def check(name, cond, detail=""):
    (PASS if cond else FAIL).append(name)
    print("%s  %s%s" % ("PASS" if cond else "FAIL", name, ("  -- " + str(detail)) if (detail and not cond) else ""), flush=True)


def run(base):
    car = "selftest-car-%d" % int(time.time() * 1000)

    s, r = call(base, "GET", "/api/health")
    check("health", s == 200 and r.get("ok") is True, r)
    check("serverTime on responses", abs(r.get("serverTime", 0) - time.time() * 1000) < 60_000, r.get("serverTime"))

    # --- 짝 맺기(차 화면 확인 포함)
    s, r = call(base, "POST", "/api/pair/start", {"carId": car})
    code = r.get("code", "") if isinstance(r, dict) else ""
    ttl = r.get("expiresAt", 0) - r.get("createdAt", 0)
    check("pair/start -> code, expiresAt (5 min)", s == 201 and len(code) == 6 and code.isdigit() and ttl == 300_000, r)
    check("pair/start qrText has code and server", code in r.get("qrText", "") and "server=http://" in r.get("qrText", ""), r.get("qrText"))
    s, r = call(base, "GET", "/api/pair/status?carId=" + car)
    check("pair/status before claim", s == 200 and r.get("paired") is False and r.get("pending") is False and r.get("pendingCode") == code, r)
    s, r = call(base, "POST", "/api/pair/claim", {"code": "000000", "phoneName": "시험 폰"})
    check("pair/claim wrong code -> 404", s == 404 and r.get("error") == "code_not_found", r)
    s, r = call(base, "POST", "/api/pair/claim", {"code": "12345", "phoneName": "시험 폰"})
    check("pair/claim 5 digits -> 400", s == 400, r)
    s, r = call(base, "POST", "/api/pair/claim", {"code": code, "phoneName": "시험 폰"})
    phone_id = r.get("phoneId")
    check("pair/claim -> carId, pending", s == 200 and r.get("carId") == car and phone_id and r.get("pending") is True, r)
    s, r = call(base, "POST", "/api/pair/claim", {"code": code, "phoneName": "시험 폰"})
    check("pair/claim same code twice -> 404", s == 404, r)
    s, r = call(base, "GET", "/api/pair/status?carId=" + car)
    check("pair/status pending until car confirms", r.get("paired") is False and r.get("pending") is True and r.get("phoneName") == "시험 폰", r)
    s, r = call(base, "POST", "/api/captures", {"carId": car, "kind": "noise"}, headers={"X-Phone-Id": phone_id})
    check("captures from unconfirmed phone -> 403", s == 403 and r.get("error") == "pair_not_confirmed", r)
    s, r = call(base, "POST", "/api/pair/confirm", {"carId": car})
    check("pair/confirm -> paired", s == 200 and r.get("paired") is True and r.get("pending") is False, r)
    s, r = call(base, "POST", "/api/pair/confirm", {"carId": car})
    check("pair/confirm again -> 409", s == 409, r)
    s, r = call(base, "GET", "/api/pair/status?carId=" + car)
    check("pair/status paired", s == 200 and r.get("paired") is True and r.get("phoneName") == "시험 폰", r)

    # 거절 흐름(다른 차)
    car2 = car + "-b"
    s, r = call(base, "POST", "/api/pair/start", {"carId": car2})
    s, r = call(base, "POST", "/api/pair/claim", {"code": r.get("code"), "phoneName": "다른 폰"})
    s, r = call(base, "POST", "/api/pair/reject", {"carId": car2})
    check("pair/reject -> rejected", s == 200 and r.get("paired") is False and r.get("rejected") is True, r)

    # 번호 5번 틀리면 잠금(살아 있는 번호 지움)
    car3 = car + "-c"
    s, r = call(base, "POST", "/api/pair/start", {"carId": car3})
    code3 = r.get("code")
    statuses = [call(base, "POST", "/api/pair/claim", {"code": "000000", "phoneName": "x"})[0] for _ in range(5)]
    check("5 wrong codes -> 429 lock", statuses[-1] == 429 and statuses[:-1] == [404] * 4, statuses)
    s, r = call(base, "POST", "/api/pair/claim", {"code": code3, "phoneName": "x"})
    check("after lock old code is gone", s == 404, r)

    # --- 기록 요청(차 -> 폰)
    s, r = call(base, "POST", "/api/requests", {"carId": car, "kind": "noise", "recordId": "rec-1", "partHint": "앞 타이어"})
    req_id = r.get("id")
    check("requests POST noise", s == 201 and r.get("status") == "pending" and r.get("partHint") == "앞 타이어", r)
    s, r = call(base, "POST", "/api/requests", {"carId": car, "kind": "trunk_reminder"})
    rem_id = r.get("id")
    check("requests POST trunk_reminder gets default text", s == 201 and "트렁크" in (r.get("text") or "") and r.get("reminder") is True, r)
    s, r = call(base, "POST", "/api/requests", {"carId": car, "kind": "rear_seat_reminder", "text": "뒷좌석을 확인하세요"})
    check("requests POST rear_seat_reminder keeps text", s == 201 and r.get("text") == "뒷좌석을 확인하세요", r)
    s, r = call(base, "POST", "/api/requests", {"carId": car, "kind": "dance"})
    check("requests POST bad kind -> 400", s == 400 and r.get("error") == "bad_kind", r)
    s, r = call(base, "GET", "/api/requests?carId=%s&pending=1" % car, headers={"X-Phone-Id": phone_id})
    ids = [x["id"] for x in r.get("requests", [])]
    check("requests GET pending", s == 200 and req_id in ids and rem_id in ids and "now" in r, r)
    s, r = call(base, "GET", "/api/pair/status?carId=" + car)
    check("pair/status phoneOnline after phone poll", r.get("phoneOnline") is True, r)

    # --- 폰 기록(폰 -> 차)
    t0 = int(time.time() * 1000)
    noise = {"carId": car, "requestId": req_id, "recordId": "rec-1", "kind": "noise", "startedAt": t0, "durationMs": 10000,
             "placement": "소리 나는 곳 가까이", "partHint": "앞 타이어", "metrics": {"avgDb": -38.2, "peakDb": -12.5, "dominantHz": 180.0,
             "spectrumMaxHz": 8000, "source": "UNPROCESSED"}, "spectrum": [-60 + i * 0.5 for i in range(64)],
             "series": [-40.0] * 200, "candidates": [], "note": "시험 소리", "thirdOctave": [-50.0] * 30, "clockOffsetMs": 12}
    s, r = call(base, "POST", "/api/captures", noise, headers={"X-Phone-Id": phone_id})
    noise_id = r.get("id")
    check("captures POST noise", s == 201 and noise_id and r.get("receivedAt"), r)
    check("captures metrics numbers only (text -> labels)", r.get("metrics", {}).get("avgDb") == -38.2 and "source" not in r.get("metrics", {})
          and r.get("labels", {}).get("source") == "UNPROCESSED", r.get("metrics"))
    check("captures keep extra fields (thirdOctave, clockOffsetMs)", len(r.get("thirdOctave", [])) == 30 and r.get("clockOffsetMs") == 12, list(r.keys()))
    s, r = call(base, "GET", "/api/requests?carId=%s&pending=1" % car)
    check("request auto-done by capture with requestId", req_id not in [x["id"] for x in r.get("requests", [])], r)

    vib = {"carId": car, "kind": "vibration", "startedAt": t0 + 1, "durationMs": 15000, "placement": "바닥", "partHint": "실내",
           "metrics": {"rmsG": 0.031, "peakG": 0.12, "dominantHz": 12.4, "spectrumMaxHz": 100}, "spectrum": [0.001] * 128,
           "series": [0.01 * (i % 7) for i in range(300)], "candidates": [], "note": ""}
    s, r = call(base, "POST", "/api/captures", vib)
    vib_id = r.get("id")
    check("captures POST vibration (128/300 -> 64/200)", s == 201 and len(r.get("spectrum", [])) == 64 and len(r.get("series", [])) == 200, r)

    jpeg = b"\xff\xd8\xff\xe0" + b"\x00" * 64 + b"\xff\xd9"
    m4a = b"\x00\x00\x00\x18ftypM4A " + b"\x00" * 40
    photo = {"carId": car, "kind": "warning_photo", "startedAt": t0 + 2, "durationMs": 0, "placement": "계기판", "partHint": None,
             "metrics": {"confidence": 0.81}, "spectrum": [], "series": [], "candidates": ["engine", "engine-blink", "tpms-low"],
             "confirmed": "engine", "imageJpegBase64": base64.b64encode(jpeg).decode(), "audioM4aBase64": base64.b64encode(m4a).decode(),
             "note": "주황 엔진 기호"}
    s, r = call(base, "POST", "/api/captures", photo)
    photo_id = r.get("id")
    check("captures POST warning_photo with image + audio", s == 201 and r.get("hasImage") and r.get("hasAudio") and r.get("confirmed") == "engine", r)
    s, raw = call(base, "GET", "/api/captures/%s/image" % photo_id, raw=True)
    check("captures image bytes round-trip", s == 200 and raw == jpeg, s)
    s, raw = call(base, "GET", "/api/captures/%s/audio" % photo_id, raw=True)
    check("captures audio bytes round-trip", s == 200 and raw == m4a, s)

    bad = dict(photo, imageJpegBase64=base64.b64encode(b"not a jpeg").decode(), audioM4aBase64=None)
    s, r = call(base, "POST", "/api/captures", bad)
    check("captures POST non-JPEG image -> 400", s == 400 and r.get("error") == "bad_image", r)
    s, r = call(base, "POST", "/api/captures", dict(noise, kind="smell"))
    check("captures POST bad kind -> 400", s == 400, r)

    s, r = call(base, "GET", "/api/captures?carId=%s&since=0" % car)
    caps = r.get("captures", [])
    order = [c["id"] for c in caps]
    check("captures GET since=0 in receivedAt order", s == 200 and order == [noise_id, vib_id, photo_id], order)
    full = next((c for c in caps if c["id"] == photo_id), {})
    check("captures GET full has base64", base64.b64decode(full.get("imageJpegBase64") or "") == jpeg and full.get("audioM4aBase64"), list(full.keys()))
    needed = ("id", "carId", "requestId", "recordId", "kind", "startedAt", "durationMs", "placement", "partHint", "note", "receivedAt",
              "metrics", "spectrum", "series", "candidates")
    check("captures objects carry all car fields", all(all(k in c for k in needed) for c in caps), [k for k in needed if k not in caps[0]] if caps else "none")
    s, r2 = call(base, "GET", "/api/captures?carId=%s&since=%d" % (car, r.get("now", 0)))
    check("captures GET since=now -> empty", s == 200 and r2.get("captures") == [], r2)
    s, r = call(base, "GET", "/api/captures?carId=%s&since=%d" % (car, caps[0]["receivedAt"]))
    check("captures GET since=first -> 2 newer", [c["id"] for c in r.get("captures", [])] == [vib_id, photo_id], r)
    s, r = call(base, "POST", "/api/captures/%s/ack" % noise_id, {"recordId": "rec-1", "by": "car"})
    check("captures ack", s == 200 and r.get("ackedAt") and r.get("ackRecordId") == "rec-1", r)
    s, r = call(base, "GET", "/api/captures?carId=%s&lite=1" % car)
    lite = r.get("captures", [])
    check("captures GET lite strips base64", all("imageJpegBase64" not in c for c in lite) and any(c.get("ackedAt") for c in lite), lite[:1])

    s, r = call(base, "POST", "/api/requests/%s/done" % rem_id, {})
    check("requests done (reminder confirmed)", s == 200 and r.get("status") == "done", r)
    s, r = call(base, "POST", "/api/requests/req-nope/done", {})
    check("requests done unknown -> 404", s == 404, r)

    # --- 정비소 · 후기
    s, r = call(base, "GET", "/api/shops")
    shops = {x["id"]: x for x in r.get("shops", [])}
    check("shops: 3 example shops", s == 200 and [shops[k]["name"] for k in sorted(shops)] == ["정비소 A (예시)", "정비소 B (예시)", "정비소 C (예시)"], list(shops))
    fields = ("id", "name", "services", "hours", "ev", "priceNote", "certs", "rating", "reviewCount", "example", "partner")
    check("shops carry ShopInfo fields (+partner)", all(all(f in x for f in fields) and x["example"] is True for x in shops.values()), shops.get("shop-a"))
    check("partner: shop-a only", [k for k in sorted(shops) if shops[k]["partner"]] == ["shop-a"], {k: v["partner"] for k, v in shops.items()})
    counts = {k: v["reviewCount"] for k, v in shops.items()}
    check("seed review counts 6-10 per shop", all(6 <= c <= 10 for c in counts.values()), counts)
    facts = ["견적대로 결제", "설명을 들음", "약속 시간에 끝남", "증상 해결"]
    s, r = call(base, "GET", "/api/shops/shop-a")
    ratios = r.get("dimAverages", {})
    check("shop detail: yes-ratios (0-1) per fact question", s == 200 and list(ratios) == facts and all(v is None or 0 <= v <= 1 for v in ratios.values()), ratios)
    s, r = call(base, "GET", "/api/shops/shop-x")
    check("shop unknown -> 404", s == 404, r)
    s, r = call(base, "GET", "/api/shops/shop-b/reviews")
    before = r.get("summary", {}).get("reviewCount")
    rv = r.get("reviews", [{}])[0]
    rv_fields = ("id", "shopId", "author", "rating", "dims", "tags", "text", "createdAt", "example", "verified")
    check("reviews carry Review fields, example authors", all(f in rv for f in rv_fields) and rv.get("author", "").startswith("예시 사용자"), rv)
    check("seed dims use only fact keys with 0/1", all(set(x["dims"]) <= set(facts) and set(x["dims"].values()) <= {0, 1} for x in r.get("reviews", [])), rv.get("dims"))

    # --- 방문 기록 -> 방문 확인 후기
    s, r = call(base, "POST", "/api/visits", {"carId": car, "shopId": "shop-b", "work": "타이어 위치 교환", "costWon": 22000})
    visit_id = r.get("id")
    check("visits POST", s == 201 and r.get("shopName") == "정비소 B (예시)", r)
    s, r = call(base, "POST", "/api/shops/shop-b/reviews", {"author": "시험 운전자", "rating": 5, "carId": car, "visitId": visit_id,
                                                            "dims": {"견적대로 결제": 1, "설명을 들음": 1, "증상 해결": 0},
                                                            "tags": ["빠름"], "text": "시험 후기입니다."})
    check("reviews POST with visitId -> verified, unanswered key absent", s == 201 and r.get("verified") is True and r.get("example") is False
          and r.get("dims") == {"견적대로 결제": 1, "설명을 들음": 1, "증상 해결": 0}, r)
    s, r = call(base, "GET", "/api/shops/shop-b/reviews?carId=" + car)
    mine = [x for x in r.get("reviews", []) if x.get("mine")]
    check("reviewCount +1 and mine flag", r["summary"]["reviewCount"] == before + 1 and len(mine) == 1, r.get("summary"))
    s, r = call(base, "GET", "/api/visits?carId=" + car)
    check("visit marked reviewed", s == 200 and r.get("visits", [{}])[0].get("reviewed") is True, r)
    s, r = call(base, "POST", "/api/shops/shop-b/reviews", {"author": "방문 없음", "rating": 1, "dims": {}, "tags": [], "text": ""})
    check("review without visit -> verified false", s == 201 and r.get("verified") is False, r)
    s, r = call(base, "GET", "/api/shops/shop-b")
    check("unverified review not counted", r.get("reviewCount") == before + 1 and r.get("unverifiedCount") == 1, {k: r.get(k) for k in ("reviewCount", "unverifiedCount")})
    s, r = call(base, "POST", "/api/shops/shop-b/reviews", {"author": "x", "rating": 4, "dims": {"증상 해결": 3}, "tags": [], "text": ""})
    check("reviews POST fact value 3 -> 400", s == 400 and r.get("error") == "bad_dims", r)
    s, r = call(base, "POST", "/api/shops/shop-b/reviews", {"author": "x", "rating": 4, "dims": {"친절": 1}, "tags": [], "text": ""})
    check("reviews POST old dim key -> 400", s == 400 and r.get("error") == "bad_dims", r)
    s, r = call(base, "POST", "/api/shops/shop-b/reviews", {"author": "x", "rating": 9, "dims": {}, "tags": [], "text": ""})
    check("reviews POST bad rating -> 400", s == 400 and r.get("error") == "bad_rating", r)

    # --- 리콜
    s, r = call(base, "GET", "/api/recalls?model=AVANTE&year=2026")
    recs = r.get("recalls", [])
    rc_fields = ("id", "title", "part", "risk", "action", "period", "done", "example", "source", "level", "fix", "gen", "produced", "ota", "checked")
    check("recalls AVANTE 2026: 가상-001, 가상-002 labeled", s == 200 and [x["id"] for x in recs] == ["가상-001", "가상-002"]
          and all(x.get("label") == "가상 예시 · 실제 리콜 아님" and x.get("example") is True for x in recs), r)
    check("recalls carry Recall fields (level, fix, gen, produced, ota, checked)", all(all(f in x for f in rc_fields) for x in recs)
          and recs[0]["ota"] is True and recs[1]["level"] == "실외 주차" and recs[0]["gen"] == "CN8", recs[:1])
    s, r = call(base, "GET", "/api/recalls?model=SONATA&year=2026")
    check("recalls other model -> empty", s == 200 and r.get("recalls") == [], r)
    s, r = call(base, "GET", "/api/recalls?model=avante&year=2019")
    check("recalls other year -> empty", s == 200 and r.get("recalls") == [], r)

    # --- 그 밖
    s, r = call(base, "GET", "/api/nothing")
    check("unknown path -> 404", s == 404, r)
    s, r = call(base, "POST", "/api/pair/unpair", {"carId": car})
    check("pair/unpair", s == 200 and r.get("unpaired") is True, r)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", help="이미 돌고 있는 서버 주소(예: http://127.0.0.1:8766). 없으면 임시 서버를 띄웁니다")
    args = ap.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(errors="replace")
    if args.url:
        run(args.url.rstrip("/"))
    else:
        import care_server
        with tempfile.TemporaryDirectory(prefix="care-selftest-") as tmp:
            server = care_server.make_server("127.0.0.1", 0, tmp)
            th = threading.Thread(target=server.serve_forever, kwargs={"poll_interval": 0.2}, daemon=True)
            th.start()
            try:
                run("http://127.0.0.1:%d" % server.server_address[1])
            finally:
                server.shutdown()
                server.server_close()
    print("\n%d passed, %d failed" % (len(PASS), len(FAIL)), flush=True)
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
