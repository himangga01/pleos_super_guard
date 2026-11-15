#!/usr/bin/env python3
r"""경고등 찍기 시험용 합성 계기판 그림을 만듭니다(시험 입력, 실제 차 사진 아님).

어두운 판 위에 주황 엔진 점검 기호와 빨강 브레이크 기호를 그립니다. 기호 좌표는 앱 아이콘(ui/Icons.kt, 100×100)과 같습니다.
쓰는 법: python -I demo-app/phone-app/tools/make_warning_test_image.py <저장할 파일.jpg|.png>
그다음 .\scripts\phone-emulator.ps1 push-image -Image <파일> 로 폰 사진함에 넣고 앱의 "사진 불러오기"로 고릅니다.
Pillow가 필요합니다.
"""
import sys

from PIL import Image, ImageDraw

W, H = 1280, 960
AMBER = (255, 176, 0)
RED = (255, 40, 30)


def engine(d, cx, cy, s, col, w):
    pts = [(16, 42), (24, 42), (24, 34), (34, 34), (34, 26), (64, 26), (64, 34), (72, 34), (80, 42), (88, 42),
           (88, 68), (80, 68), (72, 76), (30, 76), (24, 68), (16, 68), (16, 42)]
    d.line([(cx + (x - 50) * s, cy + (y - 50) * s) for x, y in pts], fill=col, width=w, joint="curve")
    for a, b in [((8, 46), (8, 64)), ((8, 55), (16, 55)), ((42, 18), (56, 18)), ((49, 18), (49, 26))]:
        d.line([(cx + (a[0] - 50) * s, cy + (a[1] - 50) * s), (cx + (b[0] - 50) * s, cy + (b[1] - 50) * s)], fill=col, width=w)


def brake(d, cx, cy, s, col, w):
    r = 22 * s
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=col, width=w)
    d.arc([cx - 42 * s, cy - 30 * s, cx - 6 * s, cy + 30 * s], 125, 235, fill=col, width=w)
    d.arc([cx + 6 * s, cy - 30 * s, cx + 42 * s, cy + 30 * s], -55, 55, fill=col, width=w)
    d.line([(cx, cy - 12 * s), (cx, cy + 2 * s)], fill=col, width=w)
    d.ellipse([cx - 4 * s, cy + 8 * s, cx + 4 * s, cy + 16 * s], fill=col)


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    img = Image.new("RGB", (W, H), (12, 15, 20))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([60, 120, 1220, 840], radius=80, fill=(20, 24, 30), outline=(40, 46, 56), width=6)
    engine(d, 420, 480, 3.2, AMBER, 22)
    brake(d, 860, 480, 3.2, RED, 22)
    img.save(sys.argv[1], quality=92)
    print("saved", sys.argv[1])


if __name__ == "__main__":
    main()
