#!/usr/bin/env python3
"""폰 앱의 "시험 입력 재생" 파일을 만듭니다(합성 자료, 실측 아님).

폰 센서 조사(docs/analysis/phone-sensing-research-2026-11-14.md) 7.5절의 예:
14Hz 사인파(0.3m/s²)에 잡음(축마다 0.1m/s² RMS)을 더한 30초, 400Hz. 폰을 화면이 위로 가게 바닥에 둔 자세(중력은 z축).
실행: python -I demo-app/phone-app/tools/make_vibration_sample.py
"""
import math
import os
import random

RATE = 400
SECONDS = 30
FREQ = 14.0
AMP = 0.3  # m/s2, z축 사인 진폭
NOISE = 0.1  # m/s2 RMS, 축마다
G = 9.80665

here = os.path.dirname(os.path.abspath(__file__))
out = os.path.join(here, "..", "app", "src", "main", "assets", "samples", "vibration_test_14hz.csv")
rnd = random.Random(20261114)
lines = [
    "# desc=시험 입력: 14Hz 사인파(0.3m/s²) + 잡음(축마다 0.1m/s² RMS), 400Hz, 30초 합성 자료(실측 아님)",
    "# rate=%d" % RATE,
    "# columns=x,y,z (m/s², 중력 포함, 폰 화면이 위)",
]
for i in range(RATE * SECONDS):
    t = i / RATE
    x = rnd.gauss(0, NOISE)
    y = rnd.gauss(0, NOISE)
    z = G + AMP * math.sin(2 * math.pi * FREQ * t) + rnd.gauss(0, NOISE)
    lines.append("%.3f,%.3f,%.3f" % (x, y, z))
with open(out, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines) + "\n")
print("wrote", os.path.relpath(out), len(lines) - 3, "samples")
