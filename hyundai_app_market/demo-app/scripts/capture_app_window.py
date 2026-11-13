"""에뮬레이터 화면을 찍어 데모 앱 창(세로 한 창)만 잘라 840x1212로 저장합니다(제출 문서 그림용).

2026-11-13 v4.3 제출 문서 그림(output/submission/assets/app-demo/)을 이 방법으로 만들었습니다.
- 앱 창을 세로 한 창으로 나눈 뒤 씁니다(앱 창 오른쪽 "⋮" 손잡이를 왼쪽으로 끌기). 넓은 창이면 멈춥니다.
- 창 위치는 `dumpsys window windows`의 frame에서 읽습니다(Pleos 홈이 앱을 TaskView에 넣어 uiautomator로는 앱 글자를 볼 수 없음).
- 841x1210 창을 시안 규격 840x1212로 맞춥니다(합성 그림·문서 그림 틀과 같은 크기).
사용: python -I scripts/capture_app_window.py <이름> [--out <폴더>]
"""
import argparse
import io
import re
import subprocess
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
DEFAULT_OUT = HERE.parent.parent / 'output' / 'submission' / 'assets' / 'app-demo'


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('name')
    ap.add_argument('--out', default=str(DEFAULT_OUT))
    ap.add_argument('--serial', default='emulator-5554')
    ap.add_argument('--adb', default='adb')
    a = ap.parse_args()
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8')
    dump = subprocess.run([a.adb, '-s', a.serial, 'shell', 'dumpsys', 'window', 'windows'],
                          capture_output=True, text=True, encoding='utf-8', errors='replace').stdout
    i = dump.find('app.pleosguard.demo/app.pleosguard.demo.MainActivity')
    m = re.search(r'frame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]', dump[i:]) if i >= 0 else None
    if not m:
        sys.exit('앱 창을 찾지 못했습니다')
    x1, y1, x2, y2 = map(int, m.groups())
    if x2 - x1 > 900:
        sys.exit(f'앱 창이 세로 한 창이 아닙니다: {(x1, y1, x2, y2)}')
    png = subprocess.run([a.adb, '-s', a.serial, 'exec-out', 'screencap', '-p'], capture_output=True).stdout
    app = Image.open(io.BytesIO(png)).convert('RGB').crop((x1, y1, x2, y2))
    out = Path(a.out); out.mkdir(parents=True, exist_ok=True)
    app.resize((840, 1212), Image.Resampling.LANCZOS).save(out / f'{a.name}.png')
    print(a.name, (x1, y1, x2, y2), '->', out / f'{a.name}.png')


if __name__ == '__main__':
    main()
