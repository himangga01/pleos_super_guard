"""
플레오스 슈퍼 케어 로고 · 앱 아이콘 · 진입 화면 그림을 만듭니다(Pillow).

로고 뜻: 민트 고리(돌봄) 안에 차 앞모습과 위쪽 '+'(케어). 작은 크기(58dp)에서도 읽히게 굵은 선으로 그립니다.
진입 화면(2026-11-15부터): Codex가 만든 그림(공식 아반떼 사진 + PLEOS / SUPER / CARE 세 줄 큰 제목, 가운데 정렬, SUPER는 빨간색)을 install_launch()로 옮깁니다.
옛 진입 화면(3D 아반떼 그림, save_launch)은 --legacy-launch로만 만듭니다.
폰 앱 진입 그림: "플레오스 슈퍼 케어 모바일"(phone-app/app/src/main/res/drawable-nodpi/launch_mobile.png).

실행(저장소 루트): python demo-app/tools/brand/make_brand.py
3D 모델 그림이 바뀌면 다시 실행해 진입 화면을 맞춥니다.
"""
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.normpath(os.path.join(HERE, "..", "..", "app", "src", "main", "res"))
DRAW = os.path.join(APP, "drawable-nodpi")
FONT_BOLD = os.path.join(APP, "font", "pretendard_extrabold.ttf")

MINT = (94, 234, 212)
TEAL = (20, 160, 150)
BG = (9, 14, 22)
WHITE = (247, 250, 255)


def gradient(size, c0, c1):
    """왼쪽 위 → 오른쪽 아래 대각선 그라데이션."""
    w, h = size
    g = Image.new("RGB", size)
    px = g.load()
    for y in range(h):
        for x in range(w):
            t = (x + y) / (w + h - 2)
            px[x, y] = tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(3))
    return g


def symbol(px=1024):
    """로고 심볼(투명 배경). px×px, 그 뒤 원하는 크기로 줄입니다."""
    S = px
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    mask = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(mask)
    # 고리
    r_out, ring = int(S * 0.47), int(S * 0.075)
    cx = cy = S // 2
    d.ellipse([cx - r_out, cy - r_out, cx + r_out, cy + r_out], fill=255)
    d.ellipse([cx - r_out + ring, cy - r_out + ring, cx + r_out - ring, cy + r_out - ring], fill=0)
    # 차 앞모습(굵은 선): 지붕 · 앞유리 · 몸통 · 램프 · 바퀴
    k = S / 1024
    d.rounded_rectangle([250 * k, 520 * k, 774 * k, 700 * k], radius=int(60 * k), fill=255)           # 몸통
    d.polygon([(330 * k, 520 * k), (400 * k, 380 * k), (624 * k, 380 * k), (694 * k, 520 * k)], fill=255)  # 지붕
    d.polygon([(372 * k, 505 * k), (425 * k, 410 * k), (599 * k, 410 * k), (652 * k, 505 * k)], fill=0)  # 앞유리(구멍)
    d.rounded_rectangle([300 * k, 575 * k, 405 * k, 625 * k], radius=int(20 * k), fill=0)              # 왼쪽 램프
    d.rounded_rectangle([619 * k, 575 * k, 724 * k, 625 * k], radius=int(20 * k), fill=0)              # 오른쪽 램프
    d.rounded_rectangle([440 * k, 650 * k, 584 * k, 670 * k], radius=int(10 * k), fill=0)              # 그릴 줄
    d.rounded_rectangle([280 * k, 690 * k, 380 * k, 770 * k], radius=int(26 * k), fill=255)            # 바퀴
    d.rounded_rectangle([644 * k, 690 * k, 744 * k, 770 * k], radius=int(26 * k), fill=255)
    # 오른쪽 위 '+'(케어 배지). 차 지붕 위에 두면 택시 표시등처럼 보여 옆으로 뺐습니다.
    pc, pw, pl = (735 * k, 300 * k), 34 * k, 92 * k
    d.rounded_rectangle([pc[0] - pw, pc[1] - pl, pc[0] + pw, pc[1] + pl], radius=int(18 * k), fill=255)
    d.rounded_rectangle([pc[0] - pl, pc[1] - pw, pc[0] + pl, pc[1] + pw], radius=int(18 * k), fill=255)
    col = gradient((S, S), MINT, TEAL).convert("RGBA")
    img.paste(col, (0, 0), mask)
    return img


def save_logo():
    big = symbol(1024)
    big.resize((512, 512), Image.LANCZOS).save(os.path.join(DRAW, "logo_symbol.png"))
    # 앱 아이콘: 어두운 둥근 사각형 바탕 + 심볼
    S = 864
    icon = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bgm = Image.new("L", (S, S), 0)
    ImageDraw.Draw(bgm).rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.22), fill=255)
    icon.paste(Image.new("RGBA", (S, S), BG + (255,)), (0, 0), bgm)
    sym = big.resize((int(S * 0.78), int(S * 0.78)), Image.LANCZOS)
    icon.alpha_composite(sym, ((S - sym.width) // 2, (S - sym.height) // 2))
    icon.resize((432, 432), Image.LANCZOS).save(os.path.join(DRAW, "app_icon.png"))


def outline_text(draw, xy, text, font, stroke, fill_rgba):
    draw.text(xy, text, font=font, fill=(0, 0, 0, 0), stroke_width=stroke, stroke_fill=fill_rgba)


def car_image(width):
    """앱의 3D 아반떼 그림(car3d_home.webp)에서 투명 여백을 잘라 [width] 폭으로 줄입니다."""
    car_path = os.path.join(DRAW, "car3d_home.webp")
    if not os.path.exists(car_path):
        return None
    car = Image.open(car_path).convert("RGBA")
    bbox = car.getbbox()
    if bbox:
        car = car.crop(bbox)
    return car.resize((width, int(car.height * width / car.width)), Image.LANCZOS)


def centered(d, text, font, y, W, fill=None, outline=None, stroke=0):
    w = d.textlength(text, font=font)
    if outline is not None:
        d.text(((W - w) / 2, y), text, font=font, fill=(0, 0, 0, 0), stroke_width=stroke, stroke_fill=outline)
    else:
        d.text(((W - w) / 2, y), text, font=font, fill=fill)


def save_launch():
    """
    차 앱 진입 화면(1682×1210, 앱 영역). 2026-11-14 사용자 요청: 앱 이름은 "플레오스 슈퍼 케어", 진입 화면도 그 이름으로.
    위: 작은 외곽선 영문 PLEOS SUPER CARE, 가운데: 큰 한글 이름, 아래: 3D 아반떼(이름을 가리지 않게 이름 아래에 둡니다).
    """
    W, H = 1682, 1210
    img = Image.new("RGBA", (W, H), BG + (255,))
    glow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(glow).ellipse([W * 0.10, H * 0.52, W * 0.90, H * 1.10], fill=120)
    glow = glow.filter(ImageFilter.GaussianBlur(120))
    img.paste(Image.new("RGBA", (W, H), (30, 70, 80, 255)), (0, 0), glow)
    d = ImageDraw.Draw(img)
    # 로고 + 영문 외곽선
    logo = symbol(1024).resize((150, 150), Image.LANCZOS)
    img.alpha_composite(logo, ((W - 150) // 2, 70))
    centered(d, "PLEOS SUPER CARE", ImageFont.truetype(FONT_BOLD, 92), 236, W, outline=MINT + (235,), stroke=3)
    # 큰 한글 이름(주인공)
    centered(d, "플레오스 슈퍼 케어", ImageFont.truetype(FONT_BOLD, 168), 362, W, fill=WHITE + (255,))
    car = car_image(int(W * 0.56))
    if car is not None:
        top = 600
        if top + car.height > H - 40:
            car = car.resize((int(car.width * (H - 40 - top) / car.height), H - 40 - top), Image.LANCZOS)
        img.alpha_composite(car, ((W - car.width) // 2, top))
    img.convert("RGB").save(os.path.join(DRAW, "launch_outline.webp"), quality=90)


PHONE_RES = os.path.normpath(os.path.join(HERE, "..", "..", "phone-app", "app", "src", "main", "res"))


def save_phone_launch():
    """
    폰 앱 "플레오스 슈퍼 케어 모바일" 진입 화면 그림(720×900, 투명 바탕). 창 바탕(layer-list) 가운데에 놓고,
    앱 안에서도 처음 열 때 잠깐 보입니다. 폰 앱 폴더가 없으면 건너뜁니다.
    """
    out_dir = os.path.join(PHONE_RES, "drawable-nodpi")
    if not os.path.isdir(out_dir):
        return
    W, H = 720, 900
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    logo = symbol(1024).resize((300, 300), Image.LANCZOS)
    img.alpha_composite(logo, ((W - 300) // 2, 60))
    d = ImageDraw.Draw(img)
    centered(d, "플레오스 슈퍼 케어", ImageFont.truetype(FONT_BOLD, 76), 430, W, fill=WHITE + (255,))
    centered(d, "모바일", ImageFont.truetype(FONT_BOLD, 76), 530, W, fill=MINT + (255,))
    centered(d, "PLEOS SUPER CARE MOBILE", ImageFont.truetype(FONT_BOLD, 40), 660, W, fill=MINT + (230,))
    img.save(os.path.join(out_dir, "launch_mobile.png"))


CODEX_LAUNCH = os.path.normpath(os.path.join(HERE, "..", "..", "..", "output", "submission", "assets", "v5", "brand", "codex"))


def install_launch():
    """
    2026-11-15 사용자 요청: 진입(로딩) 화면을 처음 스타일(공식 아반떼 사진 + 겹치는 PLEOS 큰 글자 + 뒤에 앱 홈)로 되돌림.
    그림은 Codex CLI(gpt-6-astra 세션)가 배경을 생성하고 합성했습니다(output/submission/assets/v5/brand/codex/compose.py).
    여기서는 그 결과를 앱 자원으로 옮깁니다: 넓은 창 drawable-nodpi, 세로 창 drawable-port-nodpi(같은 이름 launch_outline).
    """
    pairs = [("launch_super_care_wide.png", DRAW), ("launch_super_care_portrait.png", os.path.join(APP, "drawable-port-nodpi"))]
    for name, folder in pairs:
        src = os.path.join(CODEX_LAUNCH, name)
        os.makedirs(folder, exist_ok=True)
        Image.open(src).convert("RGB").save(os.path.join(folder, "launch_outline.webp"), quality=90)


if __name__ == "__main__":
    import sys
    save_logo()
    if "--legacy-launch" in sys.argv:  # 2026-11-14의 3D 그림 진입 화면(지금은 쓰지 않음)
        save_launch()
    else:
        install_launch()
    save_phone_launch()
    print("brand images written to", DRAW, "and", PHONE_RES)
