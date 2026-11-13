"""플레오스 가드 데모: Pleos Virtual Console 주행 시나리오 재생기 (표준 라이브러리만 씁니다).

급발진(원치 않는 가속) 조사에서 정한 조건을 시간표로 만들어 Virtual Console(127.0.0.1:8088)에 보냅니다.
Virtual Console이 받은 값은 에뮬레이터 차량 신호(AAOS PERF_VEHICLE_SPEED, Vehicle SDK 브레이크 등)로 나가므로
앱은 시험 입력이 아닌 "실제 신호 경로"로 값을 받습니다. 시나리오 정의: ../scenarios/sua-scenarios.json

쓰는 법 (먼저: adb forward tcp:8088 tcp:8088, 브라우저의 Virtual Console 페이지는 닫기)
  python scripts/vc_scenario.py --list
  python scripts/vc_scenario.py --dry-run sua-brake-overpowered      # 보내지 않고 시간표만 출력
  python scripts/vc_scenario.py sua-brake-overpowered                 # 재생
  python scripts/vc_scenario.py --export traces sua-brake-overpowered # CSV로 저장

주의
- Virtual Console 웹 페이지가 열려 있으면 페이지의 물리 계산이 값을 덮어씁니다. 재생 중에는 페이지를 닫습니다.
  페이지가 닫혀 있으면 콘솔은 하트비트가 없어 값을 받지 않으므로(503 emulator_inactive) 이 스크립트가 직접 하트비트를 보냅니다.
- 차속 계산은 Virtual Console 페이지(physicsTick)와 같은 식입니다. 실제 차량 동역학이 아닙니다.
  가속 페달 100% = +19 km/h/s(약 5.3 m/s²), 브레이크 100% = -42 km/h/s, 관성 감속 = 1.2 + 0.035 × 속도(km/h) km/h/s.
- 로컬 에뮬레이터 전용입니다. 127.0.0.1 밖으로는 보내지 않습니다.
"""
from __future__ import annotations

import argparse
import csv
import json
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
SCENARIOS = HERE.parent / "scenarios" / "sua-scenarios.json"
DT = 0.1


def load() -> dict:
    return json.loads(SCENARIOS.read_text(encoding="utf-8"))


def keyframe_speed(keys: list[tuple[float, float]], t: float) -> float:
    """replay 모드: speed_kph가 적힌 단계 사이를 직선으로 잇습니다. 마지막 값 뒤로는 그대로 둡니다."""
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            return v0 + (v1 - v0) * (t - t0) / (t1 - t0) if t1 > t0 else v1
    return keys[-1][1]


def simulate(sc: dict) -> list[dict]:
    """시나리오 단계(t초마다 바뀌는 기어·페달·속도)를 0.1초 간격 시간표로 바꿉니다.

    mode "physics"(기본): 페달 값으로 Virtual Console 페이지와 같은 식을 적분해 차속을 만듭니다.
    mode "replay": 사례 기록(EDR 요약값 등)에서 정한 속도점(speed_kph)을 직선으로 이어 차속을 만들고,
                   페달·기어 값은 단계마다 그대로 보냅니다. 기어 P에서는 0입니다.
    """
    steps = sorted(sc["steps"], key=lambda s: s["t"])
    end = sc.get("duration_s", steps[-1]["t"] + 3.0)
    replay = sc.get("mode", "physics") == "replay"
    keys = [(float(s["t"]), float(s["speed_kph"])) for s in steps if "speed_kph" in s]
    gear, acc, brk, v = "Park", 0.0, 0.0, 0.0
    out, i, k = [], 0, 0
    while k * DT <= end + 1e-9:
        t = round(k * DT, 1)
        while i < len(steps) and steps[i]["t"] <= t + 1e-9:
            s = steps[i]
            gear = s.get("gear", gear)
            acc = float(s.get("accelerator", acc))
            brk = float(s.get("brake", brk))
            if "speed_kph" in s and not replay:
                v = float(s["speed_kph"])
            i += 1
        if gear == "Park":
            v = 0.0
        elif replay:
            v = keyframe_speed(keys, t)
        else:
            drive = gear in ("Drive", "Reverse")
            factor = 0.45 if gear == "Reverse" else 1.0
            drive_accel = (acc / 100.0) * 19.0 * factor if drive else 0.0
            brake_decel = (brk / 100.0) * 42.0
            coast = 1.2 + v * 0.035 if v > 0 else 0.0
            v = max(0.0, v + (drive_accel - brake_decel - coast) * DT)
            if v < 0.35 and acc == 0:
                v = 0.0
        out.append({"t": t, "gear": gear, "accelerator": round(acc), "brake": round(brk), "speed_kph": v})
        k += 1
    return out


class Console:
    def __init__(self, base: str):
        if not (base.startswith("http://127.0.0.1") or base.startswith("http://localhost")):
            raise SystemExit("로컬 Virtual Console(127.0.0.1)만 씁니다")
        self.base = base.rstrip("/")
        self.client = "pleos-guard-scenario-" + uuid.uuid4().hex[:8]
        self.last_hb = 0.0

    def _call(self, method: str, path: str) -> int:
        req = urllib.request.Request(self.base + path, method=method, data=b"" if method == "POST" else None)
        try:
            with urllib.request.urlopen(req, timeout=3) as r:
                return r.status
        except urllib.error.HTTPError as e:
            return e.code

    def heartbeat(self, closing: bool = False) -> None:
        self._call("POST", f"/browser/session?client={self.client}" + ("&closing=1" if closing else ""))
        self.last_hb = time.monotonic()

    def gear(self, g: str) -> int:
        return self._call("PUT", f"/vehicle/gear?gear={g}")

    def controls(self, speed: float, acc: float, brk: float) -> int:
        return self._call("PUT", f"/vehicle/controls?speed={round(speed)}&accelerator={round(acc)}&brake={round(brk)}&steering=0")


def play(sc: dict, trace: list[dict], base: str) -> None:
    con = Console(base)
    con.heartbeat()
    print(f"재생: {sc['id']} · {sc['title']} ({len(trace) * DT:.1f}초)")
    gear = None
    start = time.monotonic()
    try:
        for k, row in enumerate(trace):
            target = start + k * DT
            delay = target - time.monotonic()
            if delay > 0:
                time.sleep(delay)
            if time.monotonic() - con.last_hb >= 1.0:
                con.heartbeat()
            if row["gear"] != gear:
                code = con.gear(row["gear"])
                gear = row["gear"]
                print(f"  t={row['t']:5.1f}s 기어 {gear} ({code})")
            code = con.controls(row["speed_kph"], row["accelerator"], row["brake"])
            if code != 200:
                print(f"  t={row['t']:5.1f}s 값 보내기 실패 {code}. Virtual Console 페이지가 열려 있거나 콘솔이 꺼져 있는지 보세요")
            if k % 10 == 0:
                print(f"  t={row['t']:5.1f}s 가속 {row['accelerator']:3d}% 브레이크 {row['brake']:3d}% 차속 {row['speed_kph']:6.1f} km/h")
    finally:
        if sc.get("park_at_end", True):
            con.controls(0, 0, 100)
            con.gear("Park")
            con.controls(0, 0, 0)
        con.heartbeat(closing=True)
        print("끝: 기어 P, 차속 0" if sc.get("park_at_end", True) else "끝")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("scenario", nargs="?")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--export", metavar="DIR")
    ap.add_argument("--base", default="http://127.0.0.1:8088")
    a = ap.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    data = load()
    if a.list or not a.scenario:
        for sc in data["scenarios"]:
            print(f"{sc['id']:32s} {sc['title']}")
        return
    sc = next((s for s in data["scenarios"] if s["id"] == a.scenario), None)
    if sc is None:
        raise SystemExit(f"시나리오 없음: {a.scenario}")
    trace = simulate(sc)
    if a.export:
        d = Path(a.export); d.mkdir(parents=True, exist_ok=True)
        with open(d / f"{sc['id']}.csv", "w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=list(trace[0].keys())); w.writeheader(); w.writerows(trace)
        print(d / f"{sc['id']}.csv")
    if a.dry_run:
        for row in trace[::5]:
            print(f"t={row['t']:5.1f}s {row['gear']:7s} 가속 {row['accelerator']:3d}% 브레이크 {row['brake']:3d}% 차속 {row['speed_kph']:6.1f} km/h")
        return
    if not a.export:
        play(sc, trace, a.base)


if __name__ == "__main__":
    main()
