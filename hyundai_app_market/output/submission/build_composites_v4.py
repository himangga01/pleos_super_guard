"""세로 화면 시안 두 장을 나란히 붙인 합성 이미지를 만듭니다(별첨·공식 양식 그림용).

입력: assets/v4/portrait/p-*.png (build_ui_v4.py 결과)
출력: assets/v4/composite/*.png
"""
from pathlib import Path
from PIL import Image, ImageDraw

R = Path(__file__).resolve().parent
P = R / 'assets/v4/portrait'
OUT = R / 'assets/v4/composite'
PAIRS = {
    'pair-home-warning.png': ('p-home.png', 'p-warning-find.png'),
    'pair-result-check.png': ('p-warning-result.png', 'p-check.png'),
    'pair-record-pattern.png': ('p-record.png', 'p-pattern.png'),
    'pair-emergency-practice.png': ('p-emergency.png', 'p-practice.png'),
    'pair-countdown-guardian.png': ('p-countdown.png', 'p-guardian.png'),
    'form-portrait-1.png': ('p-home.png', 'p-warning-find.png'),
    'form-portrait-2.png': ('p-emergency.png', 'p-countdown.png'),
    'form-portrait-3.png': ('p-check.png', 'p-guardian.png'),
}
GAP = 48


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (a, b) in PAIRS.items():
        ia, ib = Image.open(P / a).convert('RGB'), Image.open(P / b).convert('RGB')
        h = max(ia.height, ib.height)
        canvas = Image.new('RGB', (ia.width + ib.width + GAP, h), 'white')
        canvas.paste(ia, (0, 0))
        canvas.paste(ib, (ia.width + GAP, 0))
        d = ImageDraw.Draw(canvas)
        for x0, im in [(0, ia), (ia.width + GAP, ib)]:
            d.rectangle((x0, 0, x0 + im.width - 1, im.height - 1), outline='#CAD5E5', width=2)
        canvas.save(OUT / name)
        print(name, canvas.size)


if __name__ == '__main__':
    main()
