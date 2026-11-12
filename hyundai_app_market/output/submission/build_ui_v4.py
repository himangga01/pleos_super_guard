"""플레오스 가드 화면 시안 v4.1 생성기 (2차 비판 결정 DECISIONS-r2 반영).

- build_ui_final.py 의 함수 형식(txt·box·icon·asset·btn)을 복사해 넓혔습니다. 기존 생성기와 기존 이미지는 바꾸지 않습니다.
- 출력
  assets/v4/portrait/p-*.png    세로 한 창 840×1212 (Pleos 마켓 스크린샷 세로 규격 비율)
  assets/v4/landscape/ui-*.png  가로 1280×720 (기존 승인 시안 구조 유지, 규정 충돌만 수정)
  assets/v4/contrast-check.json 글자·아이콘마다 잰 대비, 글자 크기, 겹침 검사 결과
- 그릴 때마다 검사합니다: 글자 22px 이상, 3줄 이하, 화면 밖으로 나가지 않음, 정해진 폭을 넘지 않음,
  글자·아이콘과 바로 아래 배경(그 자리에서 가장 많은 색)의 대비 4.5:1 이상, 주요 버튼 높이 88px 이상.
- v4.1 규칙
  버튼 자리(F1): 왼쪽 = 끝내기·취소·기록(중립색), 오른쪽 = 도움·보내기·감속 실패(빨강).
  오른쪽이 빨강이 아닌 화면은 오른쪽을 그 화면의 주 행동 색으로 둡니다. 버튼 이름은 G 화면 문구표 그대로입니다.
  값은 A 대표 사례 한 벌만 씁니다. 앱 안 지도를 그리지 않습니다(B11). 주행 중 긴급 화면에는 출처 줄을 두지 않고,
  안내 문장 자리는 "검토 후 확정 문장"으로만 표시합니다(F5).
- 이모지·실제 번호판·실명·전화번호·실제 주소를 쓰지 않습니다. 경고등 기호는 일반 기호를 단순한 선으로 새로 그렸습니다.
실행: python3 build_ui_v4.py   (Pillow, assets/fonts 의 Pretendard 필요)
"""
from __future__ import annotations
import json, math, sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

R = Path(__file__).resolve().parent
A = R / 'assets'; I = A / 'icons'
OUT = A / 'v4'; PO = OUT / 'portrait'; LA = OUT / 'landscape'
LZ = Image.Resampling.LANCZOS

# 기존 승인 시안 색(ux-v3 4.3)을 그대로 쓰고, 긴급 띠 위 코럴 글자(3.56:1)만 흰 글자로 바꿉니다.
BG = '#090E16'; SURF = '#121C2B'; SURF2 = '#19283D'; LINE = '#26364D'
FG = '#F7FAFF'; MUT = '#B8C5D8'; DIM = '#8C99AD'
MINT = '#5EEAD4'; AMBER = '#FFC857'; RED = '#FF5B6B'; PURPLE = '#9FA4FF'
EBG = '#1B0B12'; EBAND = '#702132'; ECARD = '#2A1219'; ENEU = '#3A3346'; EPINK = '#FFCAD1'; EDRAFT = '#F7D9DF'
HELP = '#622230'
ON = {AMBER: '#30291C', MINT: '#1E3340', PURPLE: '#262A4D', RED: '#3A1520'}
MIN_FONT, MIN_CONTRAST, MIN_BTN = 22, 4.5, 88


# ---------------------------------------------------------------- 색·대비
def _rgb(c):
    if isinstance(c, tuple):
        return c[:3]
    c = c.lstrip('#'); return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))


def hexc(c):
    return '#%02X%02X%02X' % _rgb(c)


def lum(c):
    def ch(v):
        v /= 255; return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = _rgb(c); return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


def contrast(a, b):
    la, lb = lum(a), lum(b); return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


def mix(a, b, t):
    a, b = _rgb(a), _rgb(b); return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ---------------------------------------------------------------- 글꼴
_fc = {}


def F(n, b=False, w=None):
    name = w or ('Bold' if b and n >= 40 else 'SemiBold' if b else 'Regular')
    if (name, n) not in _fc:
        _fc[(name, n)] = ImageFont.truetype(str(A / 'fonts' / f'Pretendard-{name}.ttf'), n)
    return _fc[(name, n)]


# ---------------------------------------------------------------- 직접 그린 기호 (100×100 좌표, 4배로 그려 줄임)
class G:
    def __init__(g, d, k, W, c):
        g.d, g.k, g.W, g.c = d, k, W, c

    def P(g, pts):
        return [(x * g.k, y * g.k) for x, y in pts]

    def L(g, pts, w=None, close=False):
        w = round((w or 7.5) * g.k); pts = list(pts)
        if close:
            pts = pts + [pts[0], pts[1]]
        g.d.line(g.P(pts), fill=g.c, width=w, joint='curve')
        if not close:
            for x, y in (pts[0], pts[-1]):
                r = w / 2; g.d.ellipse((x * g.k - r, y * g.k - r, x * g.k + r, y * g.k + r), fill=g.c)

    def E(g, bb, fill=False, w=None):
        bb = tuple(v * g.k for v in bb)
        if fill:
            g.d.ellipse(bb, fill=g.c)
        else:
            g.d.ellipse(bb, outline=g.c, width=round((w or 7.5) * g.k))

    def A(g, bb, a0, a1, w=None):
        g.d.arc(tuple(v * g.k for v in bb), a0, a1, fill=g.c, width=round((w or 7.5) * g.k))

    def RR(g, bb, r, fill=False, w=None):
        bb = tuple(v * g.k for v in bb)
        if fill:
            g.d.rounded_rectangle(bb, radius=r * g.k, fill=g.c)
        else:
            g.d.rounded_rectangle(bb, radius=r * g.k, outline=g.c, width=round((w or 7.5) * g.k))

    def R(g, bb):
        g.d.rectangle(tuple(v * g.k for v in bb), fill=g.c)

    def DOT(g, x, y, r):
        g.d.ellipse(((x - r) * g.k, (y - r) * g.k, (x + r) * g.k, (y + r) * g.k), fill=g.c)

    def T(g, x, y, t, sz):
        g.d.text((x * g.k, y * g.k), t, font=F(max(8, round(sz * g.k)), w='Bold'), fill=g.c, anchor='mm')


def _engine(g):
    g.L([(16, 42), (24, 42), (24, 34), (34, 34), (34, 26), (64, 26), (64, 34), (72, 34), (80, 42), (88, 42),
         (88, 68), (80, 68), (72, 76), (30, 76), (24, 68), (16, 68)], close=True)
    g.L([(8, 46), (8, 64)]); g.L([(8, 55), (16, 55)]); g.L([(42, 18), (56, 18)]); g.L([(49, 18), (49, 26)])


def _oil(g):
    g.L([(22, 48), (56, 48), (84, 36), (84, 42), (62, 72), (22, 72)], close=True)
    g.L([(22, 54), (10, 50), (10, 62), (22, 66)]); g.L([(30, 40), (48, 40)]); g.L([(39, 40), (39, 48)])
    g.DOT(88, 60, 5); g.d.polygon(g.P([(88, 50), (83.6, 59), (92.4, 59)]), fill=g.c)


def _brake(g):
    g.E((28, 28, 72, 72)); g.A((8, 20, 44, 80), 125, 235); g.A((56, 20, 92, 80), -55, 55)
    g.L([(50, 38), (50, 52)]); g.DOT(50, 61, 4.6)


def _battery(g):
    g.RR((12, 32, 88, 78), 6); g.R((24, 22, 38, 32)); g.R((62, 22, 76, 32))
    g.L([(24, 55), (40, 55)]); g.L([(60, 55), (76, 55)]); g.L([(68, 47), (68, 63)])


def _tire(g):
    g.L([(28, 12), (19, 28), (15, 45), (18, 61), (27, 74), (73, 74), (82, 61), (85, 45), (81, 28), (72, 12)])
    for x in (30, 43, 57, 70):
        g.L([(x, 84), (x, 92)], w=6.5)
    g.L([(50, 26), (50, 46)]); g.DOT(50, 58, 4.6)


def _person(g, x=0):
    g.E((30 + x, 8, 48 + x, 26), fill=True)
    g.L([(36 + x, 34), (52 + x, 34), (58 + x, 62), (40 + x, 62)], close=True)
    g.L([(48 + x, 62), (74 + x, 62), (80 + x, 88)])


def _belt(g):
    _person(g); g.L([(64, 30), (34, 62)], w=6.5)


def _airbag(g):
    _person(g, -14); g.E((56, 8, 92, 44))


def _coolant(g):
    g.L([(44, 10), (44, 54)]); g.E((35, 50, 53, 68), fill=True)
    for y in (20, 32, 44):
        g.L([(52, y), (64, y)], w=6)
    for yy in (80, 92):
        g.L([(10 + i * 4, yy + 3 * math.sin(i * 0.9)) for i in range(21)], w=6)


def _home(g):
    g.L([(12, 50), (50, 16), (88, 50)]); g.L([(22, 42), (22, 86), (78, 86), (78, 42)]); g.L([(42, 86), (42, 62), (58, 62), (58, 86)])


def _users(g):
    g.E((22, 14, 48, 40)); g.L([(8, 86), (8, 74), (18, 60), (52, 60), (62, 74), (62, 86)])
    g.A((56, 12, 80, 36), -110, 110); g.L([(70, 58), (84, 58), (92, 70), (92, 86)])


def _lifebuoy(g):
    g.E((8, 8, 92, 92)); g.E((32, 32, 68, 68))
    for a in (45, 135, 225, 315):
        c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
        g.L([(50 + 20 * c, 50 + 20 * s), (50 + 40 * c, 50 + 40 * s)])


def _rec(g):
    g.E((10, 10, 90, 90)); g.E((32, 32, 68, 68), fill=True)


def _qr(g):
    for x, y in ((10, 10), (62, 10), (10, 62)):
        g.RR((x, y, x + 28, y + 28), 4, w=6.5); g.R((x + 9, y + 9, x + 19, y + 19))
    for bb in ((62, 62, 72, 72), (80, 64, 90, 74), (64, 80, 74, 90), (80, 80, 90, 90), (46, 12, 54, 20), (46, 30, 54, 38), (12, 46, 20, 54), (30, 46, 38, 54)):
        g.R(bb)


def _steering(g):
    g.E((8, 8, 92, 92)); g.E((40, 40, 60, 60)); g.L([(10, 50), (40, 50)]); g.L([(60, 50), (90, 50)]); g.L([(50, 60), (50, 90)])


def _pedal(g):
    g.RR((26, 8, 74, 74), 10)
    for y in (24, 40, 56):
        g.L([(38, y), (62, y)], w=6.5)
    g.L([(50, 74), (50, 92)])


def _turn(g):
    g.L([(6, 50), (26, 30), (26, 42), (44, 42), (44, 58), (26, 58), (26, 70)], close=True, w=6.5)
    g.L([(94, 50), (74, 30), (74, 42), (56, 42), (56, 58), (74, 58), (74, 70)], close=True, w=6.5)


def _search(g):
    g.E((12, 12, 64, 64)); g.L([(57, 57), (88, 88)])


def _flag(g):
    g.L([(22, 10), (22, 92)]); g.L([(22, 14), (80, 14), (66, 32), (80, 50), (22, 50)])


def _lock(g):
    g.A((30, 10, 70, 50), 180, 360); g.L([(30, 30), (30, 44)]); g.L([(70, 30), (70, 44)])
    g.RR((18, 44, 82, 90), 8); g.DOT(50, 64, 5); g.L([(50, 64), (50, 76)])


def _door(g):
    g.L([(18, 46), (42, 12), (84, 12), (84, 88), (18, 88)], close=True); g.L([(18, 46), (84, 46)], w=6); g.L([(62, 60), (74, 60)])


def _washer(g):
    g.L([(8, 14), (92, 14), (82, 58), (18, 58)], close=True); g.L([(48, 54), (28, 26)], w=6)
    g.d.polygon(g.P([(76, 62), (67.5, 80), (84.5, 80)]), fill=g.c); g.DOT(76, 82, 8.5)


def _trend(g):
    g.L([(8, 28), (38, 58), (54, 42), (88, 76)]); g.L([(64, 76), (88, 76), (88, 52)])


def _park(g):
    g.RR((8, 8, 92, 92), 18); g.T(51, 52, 'P', 60)


def _steady(g):
    g.E((26, 26, 74, 74), fill=True); g.E((8, 8, 92, 92), w=5)


def _blink(g):
    g.E((32, 32, 68, 68), fill=True)
    for a in range(0, 360, 45):
        c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
        g.L([(50 + 34 * c, 50 + 34 * s), (50 + 46 * c, 50 + 46 * s)], w=6)


def _up(g):
    g.L([(8, 76), (38, 46), (54, 62), (88, 28)]); g.L([(64, 28), (88, 28), (88, 52)])


def _road(g):
    g.L([(22, 92), (40, 8)]); g.L([(78, 92), (60, 8)])
    for a, b in ((80, 68), (54, 44), (30, 22)):
        g.L([(50, a), (50, b)], w=6)


def _crash(g):
    pts = []
    for i in range(16):
        r = 44 if i % 2 == 0 else 22; a = math.radians(i * 22.5 - 90)
        pts.append((50 + r * math.cos(a), 50 + r * math.sin(a)))
    g.L(pts, close=True, w=6.5)


def _seat(g):
    g.L([(28, 10), (42, 10), (48, 58), (34, 58)], close=True, w=6.5)
    g.L([(34, 58), (80, 58), (80, 72), (36, 72)], close=True, w=6.5)
    g.L([(60, 72), (60, 90)]); g.L([(44, 90), (76, 90)])


def _next(g):
    g.L([(12, 50), (86, 50)]); g.L([(58, 22), (88, 50), (58, 78)])


def _close(g):
    g.L([(20, 20), (80, 80)]); g.L([(80, 20), (20, 80)])


def _wheel(g):
    """타이어 옆모습(증상·부위용). 타이어 공기압 경고등 기호와 다른 일반 사물 그림입니다."""
    g.E((8, 8, 92, 92), w=10); g.E((24, 24, 76, 76), w=5); g.DOT(50, 50, 6)
    for a in (-90, -18, 54, 126, 198):
        c, s_ = math.cos(math.radians(a)), math.sin(math.radians(a))
        g.L([(50 + 8 * c, 50 + 8 * s_), (50 + 24 * c, 50 + 24 * s_)], w=5)


def _fan(g):
    """공조 바람(에어컨)."""
    g.DOT(50, 50, 8)
    for a0 in (0, 120, 240):
        pts = []
        for i in range(9):
            t = i / 8; r = 12 + 30 * t; a = math.radians(a0 + 70 * t)
            pts.append((50 + r * math.cos(a), 50 + r * math.sin(a)))
        g.L(pts, w=8)


def _window(g):
    """차 옆 창문."""
    g.L([(10, 84), (10, 44), (34, 16), (90, 16), (90, 84)], close=True, w=7); g.L([(14, 56), (86, 56)], w=6)


def _wiper(g):
    """앞유리와 와이퍼."""
    g.A((6, 24, 94, 112), 200, 340, w=7); g.L([(50, 90), (22, 44)], w=7); g.DOT(50, 90, 7)


SYM = dict(engine=_engine, oil=_oil, brake=_brake, battery=_battery, tire=_tire, belt=_belt, airbag=_airbag,
           coolant=_coolant, home=_home, users=_users, lifebuoy=_lifebuoy, rec=_rec, qr=_qr, steering=_steering,
           pedal=_pedal, turn=_turn, search=_search, flag=_flag, lock=_lock, door=_door, washer=_washer,
           trend=_trend, up=_up, road=_road, crash=_crash, seat=_seat, park=_park, steady=_steady, blink=_blink, next=_next, close=_close,
           wheel=_wheel, fan=_fan, window=_window, wiper=_wiper)

_icache = {}


def icon_img(n, size, c):
    key = (n, size, hexc(c))
    if key in _icache:
        return _icache[key]
    c = hexc(c)
    if n in SYM:
        Z = 4; S = size * Z; im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        SYM[n](G(ImageDraw.Draw(im), S / 100, None, c)); img = im.resize((size, size), LZ)
    else:
        base_name = 'map-pin' if n == 'pin-off' else n
        base = Image.open(I / f'{base_name}-FFFFFF.png').convert('RGBA')
        img = Image.new('RGBA', base.size, c); img.putalpha(base.getchannel('A'))
        if n == 'pin-off':  # 위치 공유 중지: 핀 위에 사선
            d = ImageDraw.Draw(img); d.line((20, 20, 124, 124), fill=(0, 0, 0, 0), width=30); d.line((22, 22, 122, 122), fill=c, width=12)
        img = img.resize((size, size), LZ)
    _icache[key] = img
    return img


# ---------------------------------------------------------------- 자산
_car = None; _logo = None


def car_img():
    global _car
    if _car is None:
        im = Image.open(A / 'avante-cutout.png').convert('RGBA'); _car = im.crop(im.getchannel('A').getbbox())
    return _car


# 잘라낸 차량 그림(1600×899) 위 부위 점 (승인 가로 시안의 위치와 같음)
SPOTS = [((467, 496), '앞부분'), ((1001, 220), '실내'), ((1155, 759), '앞 타이어'), ((1542, 427), '뒤쪽')]


def logo_img(size):
    global _logo
    if _logo is None:
        src = Image.open(A / 'logo-symbol.png').convert('RGB'); bg = (6, 11, 24)
        al = Image.new('L', src.size); px = src.load(); ap = al.load()
        for y in range(src.height):
            for x in range(src.width):
                r, g_, b = px[x, y]; dist = abs(r - bg[0]) + abs(g_ - bg[1]) + abs(b - bg[2])
                ap[x, y] = max(0, min(255, (dist - 10) * 6))
        _logo = src.convert('RGBA'); _logo.putalpha(al); _logo = _logo.crop(al.getbbox())
    z = _logo.copy(); z.thumbnail((size, size), LZ); return z


# ---------------------------------------------------------------- 화면
REPORT = {}


class Scr:
    def __init__(s, name, size, bg=BG):
        s.name = name; s.W, s.H = size; s.im = Image.new('RGB', size, bg); s.d = ImageDraw.Draw(s.im)
        s.items = []; s.checks = []; s.buttons = []

    # 검사용 ----------------------------------------------------------
    def sample(s, bb):
        x0, y0, x1, y1 = [int(round(v)) for v in bb]
        x0 = max(0, x0 - 2); y0 = max(0, y0 - 2); x1 = min(s.W, x1 + 2); y1 = min(s.H, y1 + 2)
        reg = s.im.crop((x0, y0, max(x1, x0 + 1), max(y1, y0 + 1)))
        return max(reg.getcolors(reg.width * reg.height))[1]

    def log(s, kind, label, fg, bg, size=None):
        r = contrast(fg, bg)
        s.checks.append(dict(kind=kind, label=label, size=size, fg=hexc(fg), bg=hexc(bg), ratio=round(r, 2)))
        if r < MIN_CONTRAST:
            raise ValueError(f'{s.name}: 대비 부족 {label!r} {hexc(fg)} on {hexc(bg)} = {r:.2f}')

    # 글자 ------------------------------------------------------------
    def bbox(s, x, y, t, n, b=False, w=None, align='left'):
        return s.d.multiline_textbbox((x, y), t, font=F(n, b, w), spacing=int(n * 0.32), align=align)

    def tw(s, t, n, b=False, w=None):
        bb = s.bbox(0, 0, t, n, b, w); return bb[2] - bb[0]

    def txt(s, x, y, t, n=26, c=FG, b=False, w=None, maxw=None, align='left'):
        assert n >= MIN_FONT, (s.name, t, n)
        assert t.count('\n') <= 2, (s.name, '3줄 초과', t)
        bb = s.bbox(x, y, t, n, b, w, align)
        if maxw is not None and bb[2] - bb[0] > maxw + 0.5:
            raise ValueError(f'{s.name}: 글자 넘침 {t!r} {bb[2] - bb[0]:.0f}>{maxw:.0f}')
        if not (bb[0] >= 0 and bb[1] >= 0 and bb[2] <= s.W and bb[3] <= s.H):
            raise ValueError(f'{s.name}: 화면 밖 {t!r} {bb}')
        s.log('text', t.replace('\n', ' / '), c, s.sample(bb), n)
        s.items.append(('text', t, bb))
        s.d.multiline_text((x, y), t, font=F(n, b, w), fill=c, spacing=int(n * 0.32), align=align)
        return bb

    def txt_c(s, cx, cy, t, n=26, c=FG, b=False, w=None, maxw=None):
        bb = s.bbox(0, 0, t, n, b, w, 'center')
        return s.txt(cx - (bb[0] + bb[2]) / 2, cy - (bb[1] + bb[3]) / 2, t, n, c, b, w, maxw, 'center')

    def txt_vc(s, x, cy, t, n=26, c=FG, b=False, w=None, maxw=None):
        bb = s.bbox(0, 0, t, n, b, w)
        return s.txt(x - bb[0], cy - (bb[1] + bb[3]) / 2, t, n, c, b, w, maxw)

    def txt_r(s, xr, y, t, n=26, c=FG, b=False, w=None):
        bb = s.bbox(0, 0, t, n, b, w); return s.txt(xr - bb[2], y, t, n, c, b, w)

    # 도형·아이콘 -----------------------------------------------------
    def box(s, r, c=SURF, rad=24, stroke=None, width=2):
        s.d.rounded_rectangle(r, radius=rad, fill=c, outline=stroke, width=width)

    def icon(s, n, x, y, size=40, c=FG, check=True):
        x, y = int(round(x)), int(round(y)); img = icon_img(n, size, c)
        if check:
            s.log('icon', n, c, s.sample((x + size * .2, y + size * .2, x + size * .8, y + size * .8)))
        s.items.append(('icon', n, (x, y, x + size, y + size)))
        s.im.paste(img, (x, y), img)

    def aa(s, rect, fn, Z=4):
        """4배로 그린 뒤 줄여 붙입니다(선·원 가장자리 부드럽게). fn(draw, k, w, h)"""
        x0, y0, x1, y1 = [int(v) for v in rect]; w, h = x1 - x0, y1 - y0
        lay = Image.new('RGBA', (w * Z, h * Z), (0, 0, 0, 0)); fn(ImageDraw.Draw(lay), Z, w, h)
        lay = lay.resize((w, h), LZ); s.im.paste(lay, (x0, y0), lay)

    def paste_round(s, img, rect, rad=24):
        x0, y0, x1, y1 = [int(v) for v in rect]; w, h = x1 - x0, y1 - y0
        img = img.convert('RGB').resize((w, h), LZ)
        m = Image.new('L', (w * 4, h * 4), 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, w * 4 - 1, h * 4 - 1), radius=rad * 4, fill=255)
        s.im.paste(img, (x0, y0), m.resize((w, h), LZ))

    def asset(s, p, rect):
        x, y, w, h = rect; z = Image.open(p).convert('RGBA'); z.thumbnail((w, h), LZ)
        s.im.paste(z, (int(x + (w - z.width) / 2), int(y + (h - z.height) / 2)), z); return z.size

    def logo(s, x, y, size):
        z = logo_img(size); s.im.paste(z, (x, y + (size - z.height) // 2), z)

    def car(s, x, y, w):
        c = car_img(); h = round(c.height * w / c.width); z = c.resize((w, h), LZ); s.im.paste(z, (x, y), z)
        return w / c.width

    # 부품 ------------------------------------------------------------
    def draft(s, xr, y, c=MUT, stroke=LINE):
        t = '설계 시안'; w = s.tw(t, 22, True) + 28
        s.box((xr - w, y, xr, y + 40), None, 20, stroke, 2); s.txt_c(xr - w / 2, y + 20, t, 22, c, True); return w

    def badge(s, x, y, t='시험 입력', c=AMBER, fill='#2B2410', right=False):
        w = s.tw(t, 22, True) + 26
        if right:
            x = x - w
        s.box((x, y, x + w, y + 36), fill, 18, c, 2); s.txt_c(x + w / 2, y + 18, t, 22, c, True); return w

    def chip(s, x, y, t, ic=None, icol=MINT, c=FG, fill=SURF, stroke=LINE, h=52, right=False, n=22, b=True):
        w = s.tw(t, n, b) + 32 + (36 if ic else 0)
        if right:
            x = x - w
        s.box((x, y, x + w, y + h), fill, h // 2, stroke, 2)
        tx = x + 16
        if ic:
            s.icon(ic, x + 14, y + (h - 28) / 2, 28, icol); tx = x + 50
        s.txt_vc(tx, y + h / 2, t, n, c, b); return w

    def btn(s, rect, label, ic, fill, fg, sub=None, stroke=None, n=28, ic_col=None, side=None):
        x0, y0, x1, y1 = rect
        assert y1 - y0 >= MIN_BTN, (s.name, label, '버튼 높이', y1 - y0)
        s.buttons.append(dict(label=label, sub=sub, side=side, color=hexc(fill), rect=list(rect), h=y1 - y0))
        s.box(rect, fill, 26, stroke, 2); cy = (y0 + y1) / 2; isz = 40 if y1 - y0 > 110 else 36
        s.icon(ic, x0 + 26, cy - isz / 2, isz, ic_col or fg); tx = x0 + 26 + isz + 18; maxw = x1 - tx - 16
        if sub:
            bm, bs = F(n, True).getbbox(label), F(22, True).getbbox(sub); gap = 12
            hm, hs = bm[3] - bm[1], bs[3] - bs[1]; top = cy - (hm + gap + hs) / 2
            s.txt(tx, top - bm[1], label, n, fg, True, maxw=maxw)
            s.txt(tx, top + hm + gap - bs[1], sub, 22, fg, True, maxw=maxw)
        else:
            s.txt_vc(tx, cy, label, n, fg, True, maxw=maxw)

    def vbtn(s, rect, label, ic, fill, fg, sub=None, stroke=None, n=36, ic_col=None, isz=80, sub_c=None, side=None, top_off=None):
        """세로로 긴 큰 버튼(주행 중 홈): 아이콘 위, 이름·설명 가운데."""
        x0, y0, x1, y1 = rect
        assert y1 - y0 >= MIN_BTN, (s.name, label, '버튼 높이', y1 - y0)
        s.buttons.append(dict(label=label.replace('\n', ' '), sub=sub and sub.replace('\n', ' '), side=side,
                              color=hexc(fill), rect=[round(v) for v in rect], h=y1 - y0))
        s.box(rect, fill, 28, stroke, 2); cx = (x0 + x1) / 2
        lb = s.bbox(0, 0, label, n, True, align='center'); lh = lb[3] - lb[1]
        sh = 0
        if sub:
            sb = s.bbox(0, 0, sub, 22, True, align='center'); sh = sb[3] - sb[1] + 22
        top = (y0 + y1) / 2 - (isz + 30 + lh + sh) / 2 if top_off is None else y0 + top_off
        s.icon(ic, cx - isz / 2, top, isz, ic_col or fg)
        s.txt_c(cx, top + isz + 30 + lh / 2, label, n, fg, True, maxw=x1 - x0 - 24)
        if sub:
            s.txt_c(cx, top + isz + 30 + lh + 22 + (sb[3] - sb[1]) / 2, sub, 22, sub_c or fg, True, maxw=x1 - x0 - 24)

    def dashed(s, rect, c, bg, dash=14, gap=10, width=3):
        """안내 문장 '자리' 표시용 점선 테두리."""
        x0, y0, x1, y1 = rect
        for horiz, a, b, fixed in ((True, x0, x1, y0), (True, x0, x1, y1), (False, y0, y1, x0), (False, y0, y1, x1)):
            t = a
            while t < b:
                e = min(t + dash, b)
                s.d.line((t, fixed, e, fixed) if horiz else (fixed, t, fixed, e), fill=c, width=width); t = e + gap
        s.log('graphic', '문장 자리 점선', c, bg)

    def num(s, x, y, t, c=AMBER):
        s.d.ellipse((x, y, x + 36, y + 36), fill=c); s.txt_c(x + 18, y + 18, t, 22, BG, True)

    def save(s, folder):
        # 겹침 검사: 글자·아이콘 상자가 서로 겹치면 실패
        over = []
        for i in range(len(s.items)):
            for j in range(i + 1, len(s.items)):
                a, b = s.items[i][2], s.items[j][2]
                if a[0] < b[2] - 1 and b[0] < a[2] - 1 and a[1] < b[3] - 1 and b[1] < a[3] - 1:
                    over.append((s.items[i][1], s.items[j][1]))
        if over:
            raise ValueError(f'{s.name}: 겹침 {over}')
        p = folder / f'{s.name}.png'; s.im.save(p, optimize=True)
        tx = [c for c in s.checks if c['kind'] == 'text']
        REPORT[s.name] = dict(file=str(p.relative_to(R)), size=[s.W, s.H], min_text_px=min(c['size'] for c in tx),
                              min_ratio=min(c['ratio'] for c in s.checks), text_items=len(tx),
                              icon_items=len(s.checks) - len(tx), buttons=s.buttons, overlaps=0, checks=s.checks)
        print(f'{p.relative_to(R)}  글자 {len(tx)}개 최소 {REPORT[s.name]["min_text_px"]}px  최소 대비 {REPORT[s.name]["min_ratio"]}')


# ---------------------------------------------------------------- 공통 그림 부품
WARN = [('engine', '엔진 점검'), ('oil', '오일 압력'), ('brake', '브레이크'), ('battery', '배터리'),
        ('tire', '타이어 공기압'), ('belt', '안전벨트'), ('coolant', '냉각수 온도'), ('airbag', '에어백')]
COLORS = [('빨강', '#FF4D4D'), ('주황', '#FFA928'), ('초록', '#3DDC84'), ('파랑', '#4C9BFF')]
# 출발 전 점검(B10): 시스템 출발 전 안내와 겹치는 안전벨트·문 칸을 뺀 점검 항목 4개. 모두 시험 입력.
CHECKS = [('oil', '엔진오일', '정상', MINT, True), ('brake', '브레이크액', '정상', MINT, True),
          ('washer', '워셔액', '부족 · 보충 필요', AMBER, True), ('trend', '출력 저하', '표시 없음', MINT, True)]
# 지금 기록 화면의 증상 칩: PRD 증상 이름(떨림·소음·쏠림·냄새·타이어·잘 모르겠음). 대표 사례는 소음(끼익).
SYMPTOMS = [('activity', '떨림'), ('audio-lines', '소음'), ('steering', '쏠림'), ('flame', '냄새'), ('wheel', '타이어'),
            ('circle-help', '잘 모르겠음')]
# 함께 남긴 상태(H3 소리 용의자 지우기): 공조·창문·와이퍼
CABIN = [('fan', '에어컨 켜짐'), ('window', '창문 닫힘'), ('wiper', '와이퍼 꺼짐')]


def nbtn(s, rect, label, ic, sub=None, n=28, ic_col=None, emergency=False):
    """왼쪽 중립 버튼(끝내기·취소·기록)."""
    if emergency:
        s.btn(rect, label, ic, ENEU, FG, sub, n=n, ic_col=ic_col, side='left-neutral')
    else:
        s.btn(rect, label, ic, SURF2, FG, sub, stroke=LINE, n=n, ic_col=ic_col, side='left-neutral')


def rbtn(s, rect, label, ic, sub=None, n=28, kind='help'):
    """오른쪽 버튼. help = 빨강(일반 화면의 진한 빨강), alert = 빨강(긴급 화면의 밝은 빨강), 그 밖은 주 행동 색."""
    if kind == 'help':
        s.btn(rect, label, ic, HELP, FG, sub, n=n, side='right-red')
    elif kind == 'alert':
        s.btn(rect, label, ic, RED, BG, sub, n=n, side='right-red')
    else:
        s.btn(rect, label, ic, kind, BG, sub, n=n, side='right-primary')


def sel_chip(s, rect, label, on, col=AMBER, dot=None, ic=None, n=22):
    x0, y0, x1, y1 = rect
    s.box(rect, ON[col] if on else SURF2, 18, col if on else None, 3 if on else 2)
    lw = s.tw(label, n, on); extra = 0
    if dot or ic:
        extra = (24 if dot else 30) + 10
    x = (x0 + x1) / 2 - (lw + extra) / 2; cy = (y0 + y1) / 2
    if dot:
        s.d.ellipse((x, cy - 12, x + 24, cy + 12), fill=dot); s.log('graphic', f'색 {label}', dot, ON[col] if on else SURF2)
    if ic:
        s.icon(ic, x, cy - 15, 30, col if on else MUT)
    s.txt_vc(x + extra, cy, label, n, col if on else FG, on, maxw=x1 - x0 - 12)


def color_chips(s, x0, y0, x1, h=64, gap=16, sel='주황'):
    w = (x1 - x0 - gap * 3) / 4
    for j, (lab, col) in enumerate(COLORS):
        x = x0 + j * (w + gap); sel_chip(s, (x, y0, x + w, y0 + h), lab, lab == sel, AMBER, dot=col)


def state_chips(s, x0, y0, x1, h=72, gap=16, labels=('계속 켜져 있어요', '깜박여요')):
    w = (x1 - x0 - gap) / 2
    for j, lab in enumerate(labels):
        x = x0 + j * (w + gap); sel_chip(s, (x, y0, x + w, y0 + h), lab, j == 0, AMBER, ic='steady' if j == 0 else 'blink')


def warn_tiles(s, x0, y0, x1, y1, cols=4, gap=16, sel='tire', sym=64):
    rows = math.ceil(len(WARN) / cols); w = (x1 - x0 - gap * (cols - 1)) / cols; h = (y1 - y0 - gap * (rows - 1)) / rows
    for i, (k, lab) in enumerate(WARN):
        x = x0 + i % cols * (w + gap); y = y0 + i // cols * (h + gap); on = k == sel
        s.box((x, y, x + w, y + h), ON[AMBER] if on else SURF2, 18, AMBER if on else None, 3 if on else 2)
        top = y + (h - sym - 10 - 26) / 2
        s.icon(k, x + w / 2 - sym / 2, top, sym, AMBER if on else FG)
        s.txt_c(x + w / 2, top + sym + 10 + 13, lab, 22, AMBER if on else FG, on, maxw=w - 10)


# 대표 사례 소리 장면(A): 앞 타이어 끼익 소리. 시속 20km 아래에서 브레이크를 밟는 중에 기록. 값은 시안 예시입니다.
TL_SPEED = [34, 34, 33, 33, 32, 31, 30, 28, 26, 24, 22, 20, 19, 18, 17, 17, 16, 16, 15, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 6]
TL_STEER = [0, 0, 1, 0, -1, 0, 0, 1, 0, 0, -1, 0, 0, 1, 0, 0, 0, -1, 0, 0, 0, 1, 0, 0, 0, 0, -1, 0, 0, 0]


def timeline(s, rect, label_w=220, row_h=72, t0=-20, t1=10):
    """차량 상태 시간표: 차속 막대·브레이크 구간·바퀴 방향 점·방향지시등. 움직이지 않는 그림입니다."""
    x0, y0, x1, y1 = rect; px0 = x0 + label_w; px1 = x1 - 8
    X = lambda t: px0 + (t - t0) / (t1 - t0) * (px1 - px0)
    rows = [('circle-gauge', '차속', '시속 14km', MINT), ('pedal', '브레이크', '기록 순간 밟음', AMBER),
            ('steering', '바퀴 방향', '거의 직진', PURPLE), ('turn', '방향지시등', '꺼짐', FG)]
    for i, (ic, name, val, col) in enumerate(rows):
        ry = y0 + i * row_h
        s.d.rounded_rectangle((px0, ry + 6, px1, ry + row_h - 6), radius=10, fill='#16233A')
        s.icon(ic, x0, ry + 10, 30, col)
        s.txt(x0 + 40, ry + 2, name, 24, FG, True)
        s.txt(x0 + 40, ry + row_h - 32, val, 22, MUT)
        cy = ry + row_h / 2; top, bot = ry + 14, ry + row_h - 14
        if i == 0:
            for k, v in enumerate(TL_SPEED):
                t = t0 + k; hh = v / 40 * (bot - top)
                s.d.rectangle((X(t) + 3, bot - hh, X(t + 1) - 3, bot), fill='#3FA59A')
        elif i == 1:
            s.d.rounded_rectangle((X(-6), cy - 12, X(4), cy + 12), radius=8, fill=AMBER)
            s.log('graphic', '브레이크 구간', AMBER, '#16233A')
        elif i == 2:
            s.d.line((px0 + 6, cy, px1 - 6, cy), fill='#33476A', width=2)
            for k, v in enumerate(TL_STEER):
                t = t0 + k + .5; yy = cy - v / 12 * (row_h / 2 - 16)
                s.d.ellipse((X(t) - 5, yy - 5, X(t) + 5, yy + 5), fill=PURPLE)
            s.log('graphic', '바퀴 방향 점', PURPLE, '#16233A')
    s.d.line((X(0), y0, X(0), y0 + 4 * row_h), fill=MINT, width=4)
    s.d.polygon([(X(0) - 9, y0 - 2), (X(0) + 9, y0 - 2), (X(0), y0 + 10)], fill=MINT)
    ay = y0 + 4 * row_h + 4
    s.txt(px0, ay, f'{t0}초', 22, MUT); s.txt_c(X(-10), ay + 14, '-10초', 22, MUT)
    s.txt_c(X(0), ay + 14, '기록 시점', 22, MINT, True); s.txt_r(px1, ay, f'+{t1}초', 22, MUT)


def cabin_row(s, x0, cy, x1, n=22):
    """함께 남긴 공조·창문·와이퍼 상태(H3) 한 줄."""
    w = (x1 - x0) / 3
    for j, (ic, t) in enumerate(CABIN):
        x = x0 + j * w; s.icon(ic, x, cy - 15, 30, MINT); s.txt_vc(x + 40, cy, t, n, FG, maxw=w - 44)


_rear = None


def road_scene(w, h, hazard=False):
    """주행 중 긴급 화면의 정지 그림: 디 올 뉴 아반떼 실사 뒷모습 1장(현대자동차 보도자료 2026.8.5 공식 사진).

    손·발 동작 화살표, 비상등 같은 덧그림은 넣지 않습니다(앱이 차를 제어하는 것처럼 보이지 않게).
    hazard 인자는 이전 호출과의 호환용이며 그림을 바꾸지 않습니다.
    """
    global _rear
    if _rear is None:
        _rear = Image.open(A / 'avante-rear.jpg').convert('RGB')
    src = _rear; cx, cy = 1107, 690                      # 원본 1920×1080에서 차 중심
    cw = 1150 if w / h > 1.5 else 1050; ch = cw * h / w
    x0 = cx - cw / 2; y0 = max(0, min(src.height - ch, cy - ch / 2))
    im = src.crop((int(x0), int(y0), int(x0 + cw), int(y0 + ch))).resize((w * 2, h * 2), LZ)
    im = ImageEnhance.Brightness(im).enhance(0.74)       # 어두운 긴급 화면에서 눈부시지 않게
    W, H = im.size; m = Image.new('L', im.size, 0)
    ImageDraw.Draw(m).ellipse((-W * .15, -H * .25, W * 1.15, H * 1.25), fill=255)
    im = Image.composite(im, Image.new('RGB', im.size, '#1A0F15'), m.filter(ImageFilter.GaussianBlur(W * .08)))
    return im.resize((w, h), LZ)


def seat_diagram(s, rect):
    """사고 순간 차 상태: 위에서 본 좌석(앞이 위쪽). 대표 사례는 운전석(왼쪽 앞) 1명 탑승."""
    def fn(d, Z, w, h):
        k = Z; bw = w * k; bh = h * k
        d.rounded_rectangle((bw * .14, 0, bw * .86, bh - 1), radius=bw * .22, outline=MUT, width=3 * k)
        d.line((bw * .22, bh * .22, bw * .78, bh * .22), fill=MUT, width=3 * k)
        seats = [(.24, .30, True), (.53, .30, False), (.24, .62, False), (.53, .62, False)]
        for sx, sy, occ in seats:
            box = (bw * sx, bh * sy, bw * (sx + .23), bh * (sy + .22))
            if occ:
                d.rounded_rectangle(box, radius=8 * k, fill='#2F3366', outline=PURPLE, width=3 * k)
                d.line((box[2] - 6 * k, box[1] + 6 * k, box[0] + 6 * k, box[3] - 6 * k), fill=FG, width=3 * k)
            else:
                d.rounded_rectangle(box, radius=8 * k, outline=DIM, width=3 * k)
        d.ellipse((bw * .27, bh * .12, bw * .27 + bw * .17, bh * .12 + bw * .17), fill=AMBER)
    s.aa(rect, fn)
    s.log('graphic', '좌석 외곽선', MUT, s.sample((rect[0], rect[1], rect[0] + 6, rect[1] + 6)))


# ---------------------------------------------------------------- 세로 틀
PW, PH, PM = 840, 1212, 24
TABS = [('warning', '경고등 찾기', 'engine', AMBER), ('record', '증상 기록', 'history', MINT),
        ('guardian', '보호자 알림', 'users', PURPLE), ('service', '정비소', 'wrench', AMBER)]
PB_L, PB_R = (24, 1092, 408, 1188), (432, 1092, 816, 1188)
PE_L, PE_R = (24, 1040, 408, 1188), (432, 1040, 816, 1188)   # 긴급·확인 질문 화면의 큰 버튼


def p_frame(name, active=None, title=None, chip=('park', '정차 중')):
    s = Scr(name, (PW, PH)); s.logo(24, 16, 64); s.txt(104, 28, '플레오스 가드', 30, b=True); s.draft(816, 30)
    tw = (792 - 36) / 4
    for j, (key, lab, ic, col) in enumerate(TABS):
        x = 24 + j * (tw + 12); on = key == active
        s.box((x, 108, x + tw, 204), ON[col] if on else SURF, 20, col if on else None, 3 if on else 2)
        s.icon(ic, x + tw / 2 - 18, 120, 36, col if on else MUT); s.txt_c(x + tw / 2, 178, lab, 22, col if on else MUT, on)
    if title:
        cw = 0
        if chip:
            cw = s.chip(816, 224, chip[1], chip[0], MINT, right=True)
        s.txt_vc(24, 250, title, 30, FG, True, maxw=792 - cw - 16)
    return s


def p_drive_head(name, bg=BG):
    """주행 중 화면 머리줄: 탭 없음."""
    s = Scr(name, (PW, PH), bg); s.logo(24, 16, 64); s.txt(104, 28, '플레오스 가드', 30, b=True); s.draft(816, 30)
    return s


# ---------------------------------------------------------------- 세로 화면
def p_home(selected=False):
    s = p_frame('p-parts' if selected else 'p-home')
    x = 24
    for ic, t, col in (('park', '정차 중', MINT), ('bluetooth', '휴대폰 연결됨', MINT), ('users', '보호자 2명 준비됨', PURPLE)):
        x += s.chip(x, 224, t, ic, col) + 12
    s.icon('car-front', 816 - s.tw('아반떼', 22) - 40, 236, 30, MUT); s.txt_r(816, 236, '아반떼', 22, MUT)
    s.box((24, 296, 816, 880)); s.txt(48, 316, '어디에서 이상을 느끼셨나요', 28, b=True); s.txt(48, 358, '점이나 이름을 누르세요', 22, c=MUT)
    k = s.car(50, 420, 740)
    for (sx, sy), lab in SPOTS:
        x, y = 50 + sx * k, 420 + sy * k; on = selected and lab == '앞 타이어'; c = AMBER if on else MINT
        s.d.ellipse((x - 22, y - 22, x + 22, y + 22), fill=BG, outline=c, width=4); s.d.ellipse((x - 7, y - 7, x + 7, y + 7), fill=c)
        s.log('graphic', f'부위 점 {lab}', c, BG)
        lw = s.tw(lab, 24, True) + 30; ly = y - 72 if lab != '앞 타이어' else y - 84
        lx = min(max(x - lw / 2, 40), 800 - lw)
        s.box((lx, ly, lx + lw, ly + 42), '#182D3A', 12, c if on else None); s.txt_c(lx + lw / 2, ly + 21, lab, 24, c, True)
    if not selected:
        s.box((24, 896, 816, 1072)); s.icon('washer', 48, 922, 48, AMBER)
        s.txt(116, 916, '차가 알려 준 점검 항목', 24, b=True); s.badge(792, 914, right=True)
        s.txt(116, 958, '워셔액이 부족합니다', 26, AMBER, True)
        s.txt(116, 1006, '출발 전 점검에서 자세히 볼 수 있습니다', 22, MUT)
    else:
        s.box((24, 896, 816, 1072)); s.icon('wheel', 48, 912, 36, AMBER); s.txt(96, 914, '앞 타이어 · 어떤 증상인가요', 24, b=True)
        w = (744 - 36) / 4
        for j, (ic, lab) in enumerate((('audio-lines', '소음'), ('activity', '떨림'), ('steering', '쏠림'), ('circle-help', '잘 모르겠음'))):
            x = 48 + j * (w + 12); on = j == 0
            s.box((x, 960, x + w, 1056), ON[AMBER] if on else SURF2, 18, AMBER if on else None, 3 if on else 2)
            s.icon(ic, x + w / 2 - 16, 972, 32, AMBER if on else FG); s.txt_c(x + w / 2, 1030, lab, 22, AMBER if on else FG, True, maxw=w - 12)
    nbtn(s, PB_L, '지금 기록', 'rec', ic_col=MINT); rbtn(s, PB_R, '도움이 필요해요', 'lifebuoy')
    s.save(PO)


def p_home_driving():
    """주행 중 홈(B9): 차 그림·탭 숨김, 큰 버튼 3개를 가로로 나란히 두어 빨간 버튼이 오른쪽에 오게 합니다(F1)."""
    s = p_drive_head('p-home-driving')
    x = 24
    for ic, t, col in (('car-front', '주행 중', MINT), ('bluetooth', '휴대폰 연결됨', MINT), ('users', '보호자 2명 준비됨', PURPLE)):
        x += s.chip(x, 112, t, ic, col) + 12
    w = (792 - 32) / 3; top, bot = 192, 1072
    cols = [('지금 기록', 'rec', SURF2, LINE, MINT, '누른 순간의\n차 상태를 남깁니다', 'left-neutral'),
            ('말로 하기', 'mic', SURF2, LINE, FG, '누른 뒤 8초 동안\n말을 듣습니다', 'middle-neutral'),
            ('도움이\n필요해요', 'lifebuoy', HELP, None, FG, '급한 상황을\n고릅니다', 'right-red')]
    for j, (lab, ic, fill, stroke, icc, sub, side) in enumerate(cols):
        x0 = 24 + j * (w + 16)
        s.vbtn((x0, top, x0 + w, bot), lab, ic, fill, FG, sub, stroke, n=38, ic_col=icc, isz=88,
               sub_c=MUT if fill == SURF2 else FG, side=side, top_off=296)
    s.box((24, 1096, 816, 1176), SURF, 20); s.icon('lock', 48, 1120, 32, DIM)
    s.txt_vc(96, 1136, '경고등 자세한 설명 · 점검 · 설정은 정차 후에 열립니다', 22, DIM, True)
    s.save(PO)


def p_warning_find():
    s = p_frame('p-warning-find', 'warning', '경고등 찾기')
    s.box((24, 296, 816, 448)); s.num(48, 316, '1'); s.txt_vc(96, 334, '무슨 색인가요', 26, b=True)
    s.txt_r(792, 320, '색 → 모양 → 켜짐', 22, MUT); color_chips(s, 48, 368, 792)
    s.box((24, 464, 816, 872)); s.num(48, 484, '2'); s.txt_vc(96, 502, '어떤 모양인가요', 26, b=True)
    s.txt_r(792, 488, '계기판과 같은 모양', 22, MUT); warn_tiles(s, 48, 536, 792, 852)
    s.box((24, 888, 816, 1072)); s.num(48, 908, '3'); s.txt_vc(96, 926, '켜져 있나요, 깜박이나요', 26, b=True)
    state_chips(s, 48, 968, 792, h=80)
    nbtn(s, PB_L, '문구로 찾기', 'search'); rbtn(s, PB_R, '말로 찾기', 'mic', kind=AMBER)
    s.save(PO)


def judgment_band(s, rect, n=28, note=True):
    """운행 판단 한 줄(누를 수 없는 표시). 대표 사례: 가까운 정비소에 들르세요."""
    x0, y0, x1, y1 = rect
    s.box(rect, ON[AMBER], 18); cy = (y0 + y1) / 2
    s.icon('wrench', x0 + 18, cy - 18, 36, AMBER); bb = s.txt_vc(x0 + 66, cy, '가까운 정비소에 들르세요', n, AMBER, True)
    if note:
        s.txt_vc(x1 - 16 - s.tw('운행 판단', 22, True), cy, '운행 판단', 22, MUT, True)
    return bb


def p_warning_result():
    s = p_frame('p-warning-result', 'warning', '후보 2개를 찾았습니다', None)
    x = 24
    for t in ('주황', '타이어 공기압 모양', '계속 켜짐'):
        x += s.chip(x, 296, t, None, h=48, fill=SURF2, stroke=None) + 10
    s.txt_r(816, 306, '다시 고르기', 22, MINT, True)
    for j, (name, st, note, on) in enumerate((('타이어 공기압\n낮음', '계속 켜짐', '고른 조건과 같습니다', True),
                                               ('공기압 장치\n점검', '깜박인 뒤 켜짐', '켜짐 방식이 다릅니다', False))):
        x0 = 24 + j * 408; r = (x0, 364, x0 + 384, 556)
        s.box(r, ON[AMBER] if on else SURF, 22, AMBER if on else LINE, 3 if on else 2)
        s.icon('tire', x0 + 20, 384, 64, AMBER)
        s.txt(x0 + 100, 380, name, 24, FG, True, maxw=230)
        if on:
            s.icon('circle-check', x0 + 332, 380, 32, AMBER)
        s.txt(x0 + 20, 470, st, 22, AMBER if on else MUT, True); s.txt(x0 + 20, 508, note, 22, MINT if on else MUT)
    s.box((24, 572, 816, 1072))
    judgment_band(s, (40, 588, 800, 668), n=30)
    s.txt(48, 692, '타이어 공기압 낮음', 30, b=True); s.badge(792, 694, '안내 예시 · 검토 전', right=True)
    s.txt(48, 746, '설명 요약(설명서 기준)', 22, AMBER, True)
    s.txt(48, 782, '타이어 공기압이 많이 낮을 때 켜집니다.\n안전한 곳에 세운 뒤 타이어 상태를 확인하세요.\n공기압을 맞춘 뒤에도 켜져 있으면 점검을 받으세요.', 24, FG, maxw=744)
    s.txt(48, 924, '주행 중에는 위의 운행 판단 한 줄만 보입니다', 22, MUT, maxw=744)
    s.txt(48, 990, '출처: 아반떼 취급설명서 해당 절 (확인 전)\n차량 계기판 안내와 다르면 차량 안내를 따르세요', 22, MUT)
    nbtn(s, PB_L, '정차 후 자세히', 'park'); rbtn(s, PB_R, '정비소 들르기', 'wrench', kind=AMBER)
    s.save(PO)


def check_grid(s, x0, y0, x1, rows_h=112, gap=16, isz=48, n=26, ipad=18, igap=16):
    w = (x1 - x0 - gap) / 2
    for i, (ic, name, st, col, test) in enumerate(CHECKS):
        x = x0 + i % 2 * (w + gap); y = y0 + i // 2 * (rows_h + gap)
        s.box((x, y, x + w, y + rows_h), SURF2, 18); s.icon(ic, x + ipad, y + (rows_h - isz) / 2, isz, col)
        tx = x + ipad + isz + igap; bw = 0
        if test:
            bw = s.badge(x + w - 14, y + 14, right=True)
        s.txt(tx, y + 14, name, n, FG, True, maxw=w - (tx - x) - bw - 24)
        s.txt(tx, y + rows_h - 46, st, 22, col, True, maxw=w - (tx - x) - 10)


def p_check():
    s = p_frame('p-check', 'warning', '출발 전 점검', ('lock', '정차 중에만'))
    s.box((24, 296, 816, 656)); s.txt(48, 314, '차가 알려 준 점검 항목', 26, b=True)
    s.txt(48, 354, '경고등이 아니라 차가 보내는 점검 신호입니다', 22, MUT)
    check_grid(s, 48, 400, 792)
    s.box((24, 672, 816, 776)); s.icon('history', 48, 700, 48, MINT)
    s.txt(116, 690, '끝나지 않은 기록 1건', 26, FG, True); s.txt(116, 732, '앞 타이어 끼익 소리 3번 · 정비 전', 22, MUT)
    s.txt_r(792, 712, '기록 보기 →', 22, MINT, True)
    s.box((24, 792, 816, 928)); s.txt(48, 808, '정비 일정 · 주행거리 기준', 24, b=True); s.txt_r(792, 808, '32,140km', 24, FG, True)
    s.d.rounded_rectangle((48, 854, 792, 870), radius=8, fill='#2E3F57'); s.d.rounded_rectangle((48, 854, 48 + 744 * .714, 870), radius=8, fill=MINT)
    s.log('graphic', '정비 일정 막대', MINT, SURF)
    s.txt(48, 884, '정기 점검까지 2,860km 남았습니다', 22, FG)
    s.box((24, 944, 816, 1072))
    for j, (ic, t, sub, col) in enumerate((('bluetooth', '휴대폰 연결됨', '11:58 확인', MINT), ('users', '보호자 2명 준비됨', '연습 알림 받음', PURPLE))):
        x = 48 + j * 384; s.icon(ic, x, 972, 40, col); s.txt(x + 54, 964, t, 24, FG, True); s.txt(x + 54, 1004, sub, 22, MUT)
    nbtn(s, PB_L, '확인했어요', 'circle-check'); rbtn(s, PB_R, '정비소 들르기', 'wrench', kind=AMBER)
    s.save(PO)


def symptom_chips(s, x0, y0, x1, cols=3, h=68, gap=12, sel='소음'):
    w = (x1 - x0 - gap * (cols - 1)) / cols
    for i, (ic, lab) in enumerate(SYMPTOMS):
        x = x0 + i % cols * (w + gap); y = y0 + i // cols * (h + gap)
        sel_chip(s, (x, y, x + w, y + h), lab, lab == sel, MINT, ic=ic)


def p_record():
    s = p_frame('p-record', 'record', '증상을 기록했습니다')
    s.box((24, 296, 816, 696)); s.txt(48, 312, '차량 상태 시간표', 26, b=True); s.badge(792, 312, '차속 · 시험 입력', right=True)
    s.txt(48, 350, '08:42:15 기록 · 앞 20초와 뒤 10초', 22, MUT)
    timeline(s, (48, 396, 792, 644), label_w=230, row_h=62)
    s.box((24, 712, 816, 812)); s.txt(48, 726, '함께 남긴 상태 · 공조 · 창문 · 와이퍼', 22, MUT, True); s.badge(792, 722, right=True)
    cabin_row(s, 48, 784, 792)
    s.box((24, 828, 816, 1072)); s.txt(48, 844, '어떤 증상이었나요', 24, b=True)
    s.icon('mic', 300, 846, 28, MINT); s.txt(336, 846, '말로 메모: 앞 타이어 쪽 끼익 소리', 22, MINT, True, maxw=456)
    symptom_chips(s, 48, 890, 792, h=76, gap=12)
    nbtn(s, PB_L, '기록 끝내기', 'circle-check'); rbtn(s, PB_R, '말로 메모', 'mic', kind=MINT)
    s.save(PO)


# 재현 조건(B12): 평소 주행과 견줘 이 증상 때 유난히 겹친 조건만. 비율은 시안 예시 값입니다.
COND = [('circle-gauge', '속도', '시속 20km 아래', .18, True), ('pedal', '브레이크', '밟는 중', .12, False)]
SUSPECTS = [('fan', '에어컨 · 1번만 켜짐'), ('window', '창문 · 평소처럼 닫힘'), ('wiper', '와이퍼 · 평소처럼 꺼짐')]


def cond_rows(s, x0, y0, x1, row_h=108, gap=12, split=340, vn=26):
    for i, (ic, lab, val, usual, test) in enumerate(COND):
        y = y0 + i * (row_h + gap); s.box((x0, y, x1, y + row_h), SURF2, 18)
        s.icon(ic, x0 + 18, y + (row_h - 40) / 2, 40, MINT)
        bb = s.txt(x0 + 74, y + 12, lab, 22, MUT)
        if test:
            s.badge(bb[2] + 12, y + 8)
        s.txt(x0 + 74, y + row_h - 46, val, vn, FG, True)
        bx0 = x0 + split; bx1 = x1 - 18; lw = 132
        for k, (name, frac, col) in enumerate(((f'평소 {round(usual * 100)}%', usual, DIM), ('3번 중 3번', 1.0, MINT))):
            cy = y + row_h * (.3 if k == 0 else .7)
            s.txt_vc(bx0, cy, name, 22, MUT if k == 0 else MINT, k == 1)
            tx0 = bx0 + lw
            s.d.rounded_rectangle((tx0, cy - 7, bx1, cy + 7), radius=7, fill='#2E3F57')
            s.d.rounded_rectangle((tx0, cy - 7, tx0 + max(14, (bx1 - tx0) * frac), cy + 7), radius=7, fill=col)
            s.log('graphic', f'비교 막대 {lab} {name}', col, SURF2)


def p_pattern():
    s = p_frame('p-pattern', 'record', '재현 조건')
    s.box((24, 296, 816, 476)); s.car(40, 314, 270)
    s.txt(334, 314, '앞 타이어 · 끼익 소리', 30, b=True); s.txt(334, 362, '같은 증상 3번', 26, MINT, True)
    s.txt(334, 406, '11월 6일 · 9일 · 12일 · 메모 3건', 22, MUT)
    s.box((24, 492, 816, 800)); s.txt(48, 508, '평소 주행보다 자주 겹친 조건', 26, b=True)
    cond_rows(s, 48, 556, 792, row_h=108, gap=12, split=340)
    s.box((24, 816, 816, 1000)); s.txt(48, 832, '소리 원인 후보에서 뺀 것', 24, b=True)
    for j, (ic, t) in enumerate(SUSPECTS):
        y = 876 + j * 40; s.icon(ic, 48, y, 28, MINT); s.txt_vc(88, y + 14, t, 22, FG)
    s.txt(24, 1024, '기록 순간의 차 신호를 평소 주행과 견준 자료입니다', 22, MUT, maxw=792)
    nbtn(s, PB_L, '기록 목록', 'history'); rbtn(s, PB_R, '정비소에 보여 주기', 'qr', kind=MINT)
    s.save(PO)


def slot_card(s, rect, slot, n=30):
    """안내 문장 자리(F5). 취급설명서에서 검토한 문장만 들어갑니다. 지어낸 조작 문장을 넣지 않습니다."""
    x0, y0, x1, y1 = rect
    s.box(rect, ECARD, 22); s.dashed((x0 + 12, y0 + 12, x1 - 12, y1 - 12), EPINK, ECARD)
    s.icon('volume-2', x0 + 32, y0 + 32, 32, EPINK); s.txt_vc(x0 + 76, y0 + 48, '지금 나오는 음성 · 화면 문장', 22, EPINK, True)
    s.badge(x1 - 32, y0 + 30, '문장 자리', EPINK, ECARD, right=True)
    s.txt(x0 + 32, y0 + 88, slot, n, FG, True, w='SemiBold', maxw=x1 - x0 - 64)


def emergency_portrait(name, line2, slot, left, right, hazard=False):
    """주행 중 긴급 화면: 띠 · 행동 한 문장 · 안내 문장 자리 · 정지 그림 최대 1장 · 버튼 2개. 출처 줄은 숨김(F5)."""
    s = Scr(name, (PW, PH), EBG)
    s.box((16, 16, 824, 128), EBAND, 22); s.icon('octagon-alert', 40, 44, 56, FG)
    s.txt(116, 30, '긴급 안내', 34, FG, True); s.txt(116, 80, line2, 26, FG, True); s.draft(800, 52, EDRAFT, EDRAFT)
    x = 24
    for ic, t in (('car-front', '주행 중'), ('volume-2', '음성 안내 중')):
        x += s.chip(x, 148, t, ic, EPINK, FG, ECARD, None) + 12
    s.txt(24, 222, '앞을 보세요\n음성 안내를 따라 주세요', 46, FG, True, maxw=792)
    slot_card(s, (24, 362, 816, 552), slot)
    s.paste_round(road_scene(792, 456, hazard), (24, 568, 816, 1024), 24)
    nbtn(s, PE_L, left[0], left[1], left[2], n=30, emergency=True)
    rbtn(s, PE_R, right[0], right[1], right[2], n=30, kind='alert')
    s.save(PO)


def p_confirm():
    """확인 질문(F2·D8): 센서 조합은 이 질문만 띄웁니다. 답을 들은 뒤에만 안내를 시작합니다."""
    s = p_drive_head('p-confirm')
    x = 24
    for ic, t, col in (('car-front', '주행 중', MINT), ('volume-2', '음성으로도 묻습니다', FG)):
        x += s.chip(x, 112, t, ic, col) + 12
    s.box((24, 192, 816, 872), ECARD, 28, RED, 3)
    s.icon('circle-help', 56, 228, 48, RED); s.txt_vc(120, 252, '확인 질문', 26, EPINK, True)
    s.txt(56, 316, '차가 멈추지\n않나요?', 76, FG, True)
    s.d.line((56, 556, 784, 556), fill='#4A2430', width=2)
    s.icon('pedal', 56, 590, 40, AMBER)
    bb = s.txt(112, 584, '브레이크를 밟은 1초 동안\n속도가 줄지 않았습니다', 28, FG, True)
    s.badge(112, bb[3] + 14, '차속 · 시험 입력')
    s.icon('mic', 56, 772, 40, EPINK); s.txt_vc(112, 792, '8초 동안 말로 하는 답도 듣습니다', 26, FG)
    s.txt(24, 904, '앱은 운전자의 답을 들은 뒤에만 긴급 안내를 시작합니다', 22, MUT, maxw=792)
    nbtn(s, PE_L, '괜찮아요', 'circle-check', '질문 닫기', n=30)
    rbtn(s, PE_R, '안 멈춰요', 'octagon-alert', '감속 실패 대처', n=30, kind='alert')
    s.save(PO)


def p_countdown():
    s = Scr('p-countdown', (PW, PH), EBG)
    s.box((16, 16, 824, 128), EBAND, 22); s.icon('octagon-alert', 40, 44, 56, FG)
    s.txt(116, 30, '보호자 알림', 34, FG, True); s.txt(116, 80, '발송 확인(10초)', 26, FG, True); s.draft(800, 52, EDRAFT, EDRAFT)
    bb = s.txt(24, 150, '에어백 전개 신호를 받았습니다', 34, FG, True); s.badge(bb[2] + 14, 156)
    # 남은 초: 숫자만(줄어드는 고리 없음, PRD 3.4.3·ui-tokens countdown_ring_while_driving=false)
    s.box((24, 220, 816, 404), ECARD, 22)
    s.txt_c(200, 312, '10', 120, FG, True); s.txt_vc(330, 318, '초 뒤 보냅니다', 40, FG, True)
    s.box((24, 424, 816, 648), ECARD, 22); s.txt(48, 440, '받는 사람', 24, FG, True)
    for j, t in enumerate(('보호자 1', '보호자 2')):
        y = 486 + j * 76; s.box((40, y, 800, y + 68), '#3A1C26', 16)
        s.icon('users', 58, y + 14, 40, PURPLE); s.txt_vc(114, y + 34, t, 28, FG, True); s.txt_r(780, y + 20, '알림톡, 안 되면 문자', 22, MUT)
    s.box((24, 668, 816, 920), ECARD, 22); s.txt(48, 684, '보낼 내용', 24, FG, True)
    rows = (('감지', '에어백 전개 신호(운전석) · 21:00:03', True), ('위치', '측정 시각 21:00:01', False),
            ('차', '탑승 1명(운전석) · 안전벨트 착용 · 문 닫힘', True))
    for j, (k, v, test) in enumerate(rows):
        y = 732 + j * 56; s.txt(48, y, k, 22, MUT, True); vb = s.txt(118, y - 2, v, 24, FG, maxw=560)
        if test:
            s.badge(792, y - 6, right=True)
    s.txt(24, 944, '119에 자동 신고하지 않습니다. 필요하면 119에 직접 신고하세요', 22, MUT, maxw=792)
    nbtn(s, PE_L, '괜찮아요', 'circle-check', '보내지 않음', n=30, emergency=True)
    rbtn(s, PE_R, '지금 보내기', 'send', '기다리지 않고 바로', n=30, kind='alert')
    s.save(PO)


# 보호자 알림 상태(A): 보호자 1 알림톡 전달·21:01 열람·"전화할게요" 답, 보호자 2 21:00:23 문자로 다시 보냄
GUARD = [('보호자 1', 'check-check', '전달 · 열람 21:01 · 전화할게요', MINT), ('보호자 2', 'message-circle', '문자로 다시 보냄 21:00:23', AMBER)]
SNAP = [('seat', '탑승: 운전석 1명', False), ('belt', '안전벨트: 운전석 착용', True),
        ('airbag', '에어백: 운전석 전개', True), ('door', '문: 모두 닫힘', False)]


def guardian_rows(s, x0, y0, x1, h=80, gap=12, n=28, rows=GUARD):
    for j, (who, ic, st, col) in enumerate(rows):
        y = y0 + j * (h + gap); s.box((x0, y, x1, y + h), SURF2, 18)
        s.icon('users', x0 + 16, y + (h - 36) / 2, 36, PURPLE); s.txt_vc(x0 + 64, y + h / 2, who, n, FG, True)
        w = s.tw(st, 22, True); s.icon(ic, x1 - 16 - w - 40, y + (h - 30) / 2, 30, col)
        bb = s.bbox(0, 0, st, 22, True); s.txt(x1 - 16 - w - bb[0], y + h / 2 - (bb[1] + bb[3]) / 2, st, 22, col, True)


def snap_rows(s, x0, y0, step=40, n=22):
    for j, (ic, t, test) in enumerate(SNAP):
        y = y0 + j * step; s.icon(ic, x0, y + 2, 28, PURPLE if ic != 'airbag' else AMBER)
        bb = s.txt_vc(x0 + 38, y + 16, t, n, FG)
        if test:
            s.badge(bb[2] + 10, y - 2)


def p_guardian():
    s = p_frame('p-guardian', 'guardian', '보호자 알림 상태')
    s.box((24, 296, 816, 592)); s.txt(48, 314, '보호자별 상태', 26, b=True); s.txt_r(792, 320, '21:00:13 보냄', 22, MUT)
    guardian_rows(s, 40, 362, 800, 84)
    s.txt(48, 548, '상태는 접수 → 전달 → 열람 순서로 바뀝니다', 22, MUT)
    s.box((24, 608, 816, 832)); s.icon('map-pin', 48, 632, 44, PURPLE)
    s.txt(108, 624, '위치 측정 시각', 22, MUT); s.txt(108, 656, '21:00:01', 36, FG, True)
    s.txt(330, 668, '차량 위치 · 휴대폰 위치는 따로 표시', 22, MUT)
    s.txt(48, 716, '주소: ○○시 ○○구 ○○대로 부근 (예시)', 24, FG)
    s.txt(48, 756, '지도는 앱에 없고 보호자 휴대폰 페이지에서 봅니다', 22, MUT)
    s.txt(48, 792, '보호자 2명에게 위치를 보내는 중', 22, PURPLE, True)
    s.box((24, 848, 816, 1072)); s.txt(48, 864, '사고 순간 차 상태', 24, b=True)
    seat_diagram(s, (52, 904, 172, 1060)); snap_rows(s, 204, 904, 42)
    nbtn(s, PB_L, '기록 보기', 'history'); rbtn(s, PB_R, '공유 중지', 'pin-off', kind=PURPLE)
    s.save(PO)


def p_practice():
    s = p_frame('p-practice', None, '비상 절차 미리 익히기', ('lock', '정차 중에만 열립니다'))
    s.box((24, 296, 816, 404)); s.txt(48, 312, '단계 1/3', 24, MINT, True); s.txt_r(792, 314, '브레이크 → 비상등 → 비상 제동 듣기', 22, MUT)
    w = (744 - 24) / 3
    for j in range(3):
        x = 48 + j * (w + 12); s.d.rounded_rectangle((x, 362, x + w, 378), radius=8, fill=MINT if j == 0 else '#2E3F57')
    s.box((24, 420, 816, 866), SURF); s.asset(A / 'pedal-detail.png', (24, 420, 792, 446))
    s.box((24, 882, 816, 1072)); s.txt(48, 898, '브레이크를 밟아 보세요', 40, FG, True)
    s.icon('circle-check', 48, 962, 36, MINT); s.txt_vc(96, 980, '브레이크 신호를 받았습니다', 26, MINT, True)
    s.txt(48, 1022, '그림: AI 설명 그림 · 문장: 취급설명서 확인 전 예시', 22, MUT, maxw=744)
    nbtn(s, PB_L, '그만하기', 'close'); rbtn(s, PB_R, '다음 단계', 'next', kind=MINT)
    s.save(PO)


# 정비소 들르기(A·B11): 후보 3곳, 후보까지 거리·시간과 도착 예상 시각(requestTrip). 우회 +분 표기 없음. 값은 시안 예시.
SHOPS = [('A', '정비소 A', '4.1km · 12분', '21:19'), ('B', '정비소 B', '6.8km · 17분', '21:24'), ('C', '정비소 C', '9.5km · 21분', '21:28')]


def shop_rows(s, x0, y0, x1, h=132, gap=14, n=28, tn=36):
    for j, (L, name, dist, eta) in enumerate(SHOPS):
        y = y0 + j * (h + gap); on = j == 0
        s.box((x0, y, x1, y + h), ON[AMBER] if on else SURF2, 18, AMBER if on else None, 3 if on else 2)
        cy = y + h / 2
        s.d.ellipse((x0 + 18, cy - 24, x0 + 66, cy + 24), fill=AMBER if on else '#3A3220', outline=AMBER, width=3)
        s.txt_c(x0 + 42, cy, L, 24, BG if on else AMBER, True)
        s.txt(x0 + 88, y + h * .22, name, n, FG, True); s.txt(x0 + 88, y + h * .22 + n + 14, dist, 22, MUT)
        s.txt_r(x1 - 20, y + h * .18, '도착 예상', 22, MUT); s.txt_r(x1 - 20, y + h * .18 + 32, eta, tn, AMBER if on else FG, True)


def p_service():
    s = p_frame('p-service', 'service', '가는 길에 정비소 들르기')
    s.box((24, 296, 816, 408)); s.icon('flag', 48, 320, 40, MINT); s.txt(104, 314, '목적지는 그대로 둡니다', 26, FG, True)
    s.txt(104, 358, '고른 정비소를 지금 경로에 경유지로 넣습니다', 22, MUT)
    s.txt(24, 428, '후보 3곳 · 도착 예상 시각은 내비가 알려 준 값입니다', 22, MUT, maxw=792)
    shop_rows(s, 24, 468, 816)
    s.box((24, 918, 816, 1072)); s.icon('navigation', 48, 940, 36, MINT)
    s.txt(100, 936, '지도는 옆 화면의 내비에서 봅니다', 24, FG, True)
    s.txt(100, 978, '내비에서 보기를 누르면 고른 정비소를 내비에 띄웁니다', 22, MUT, maxw=700)
    s.txt(100, 1018, '정비소 목록은 앱 안 자료입니다 (예시)', 22, MUT)
    nbtn(s, PB_L, '내비에서 보기', 'navigation'); rbtn(s, PB_R, '경유지로 추가', 'map-pin', kind=AMBER)
    s.save(PO)


EMERG = [('octagon-alert', '안 멈춰요', '안 멈춰요'), ('up', '저절로\n빨라져요', '저절로 빨라져요'), ('crash', '사고가\n났어요', '사고가 났어요')]
# 정차 판 12칸 = 긴급 3 + 아래 9칸(증상 8 + 경고등 찾기로 가는 칸). 사고는 긴급 줄에만 둡니다.
PARKED = [('activity', '차체 떨림'), ('steering', '핸들 쏠림'), ('audio-lines', '이상한 소음'), ('flame', '타는 냄새'),
          ('pedal', '페달 이상'), ('wheel', '타이어 이상'), ('trend', '힘이 없어요'), ('engine', '경고등이 켜졌어요'),
          ('circle-help', '잘 모르겠음')]
PARKED_ON = ('이상한 소음', '타이어 이상')


def p_manual():
    """주행 중 상황 선택(B9): 긴급 3 + 증상 2 = 큰 카드 5개. 탭·여러 개 고르기 숨김."""
    s = p_drive_head('p-manual')
    s.chip(816, 112, '주행 중', 'car-front', MINT, right=True); s.txt_vc(24, 138, '상황 선택', 34, FG, True)
    s.txt(24, 194, '급한 상황 · 누르면 바로 안내합니다', 22, MUT)
    w = (792 - 32) / 3
    for j, (ic, t, _) in enumerate(EMERG):
        x = 24 + j * (w + 16); s.box((x, 236, x + w, 556), ECARD, 24, RED, 3)
        s.icon(ic, x + w / 2 - 36, 276, 72, RED); s.txt_c(x + w / 2, 446, t, 34, FG, True, maxw=w - 24)
    s.txt(24, 582, '증상 · 기록만 하고 보호자 알림은 묻습니다', 22, MUT)
    w2 = (792 - 16) / 2
    for j, (ic, t) in enumerate((('activity', '차체 떨림'), ('flame', '타는 냄새'))):
        x = 24 + j * (w2 + 16); s.box((x, 624, x + w2, 904), SURF, 24, LINE, 2)
        s.icon(ic, x + w2 / 2 - 36, 672, 72, AMBER); s.txt_c(x + w2 / 2, 820, t, 34, FG, True)
    s.box((24, 928, 816, 1008), SURF, 20); s.icon('lock', 48, 952, 32, DIM)
    s.txt_vc(96, 968, '차를 세우면 12가지 상황을 모두 고를 수 있습니다', 22, DIM, True)
    nbtn(s, PB_L, '고른 증상 기록', 'rec', ic_col=MINT); rbtn(s, PB_R, '지금 도움 요청', 'send')
    s.save(PO)


def p_manual_parked():
    """정차 후 상황 선택: 긴급 3 + 9칸 = 12칸, 최대 3개 고르기."""
    s = p_frame('p-manual-parked', None, '상황 선택', ('circle-check', '2개 선택 · 최대 3개'))
    s.txt(24, 294, '급한 상황 · 누르면 바로 안내합니다', 22, MUT)
    w = (792 - 24) / 3
    for j, (ic, _, t) in enumerate(EMERG):
        x = 24 + j * (w + 12); s.box((x, 334, x + w, 454), ECARD, 22, RED, 3)
        s.icon(ic, x + 20, 374, 40, RED); s.txt_vc(x + 72, 394, t.replace(' ', '\n', 1) if len(t) > 6 else t, 26, FG, True, maxw=w - 84)
    s.txt(24, 472, '증상 · 기록만 하고 보호자 알림은 묻습니다', 22, MUT)
    h = (1072 - 512 - 24) / 3
    for i, (ic, t) in enumerate(PARKED):
        x = 24 + i % 3 * (w + 12); y = 512 + i // 3 * (h + 12); on = t in PARKED_ON
        s.box((x, y, x + w, y + h), ON[AMBER] if on else SURF, 22, AMBER if on else None, 3 if on else 2)
        s.icon(ic, x + w / 2 - 26, y + 26, 52, AMBER if on else FG)
        s.txt_c(x + w / 2, y + h - 40, t, 24, AMBER if on else FG, True, maxw=w - 16)
    nbtn(s, PB_L, '고른 증상 기록', 'rec', ic_col=MINT); rbtn(s, PB_R, '지금 도움 요청', 'send')
    s.save(PO)


# ---------------------------------------------------------------- 가로 틀
LW, LH = 1280, 720
DOCK = [('home', '홈', 'home', MINT), ('warning', '경고등\n찾기', 'engine', AMBER), ('record', '증상\n기록', 'history', MINT),
        ('guardian', '보호자\n알림', 'users', PURPLE), ('service', '정비소', 'wrench', AMBER)]
LB_L, LB_R = (160, 610, 696, 698), (720, 610, 1256, 698)
LD_L, LD_R = (24, 608, 628, 704), (652, 608, 1256, 704)      # 도크 없는 주행 중 화면
LE_L, LE_R = (24, 608, 632, 708), (656, 608, 1256, 708)      # 긴급 화면


def l_frame(name, title, active='home', chip=('park', '정차 중')):
    s = Scr(name, (LW, LH)); s.logo(20, 16, 72); s.txt(108, 24, '플레오스 가드', 30, b=True); s.txt(370, 26, title, 28, b=True)
    dw = s.draft(1256, 30)
    if chip:
        s.chip(1256 - dw - 14, 24, chip[1], chip[0], MINT, right=True)
    s.box((16, 116, 136, 600), SURF, 26)
    for j, (key, lab, ic, col) in enumerate(DOCK):
        y = 122 + j * 95; on = key == active
        if on:
            s.box((22, y, 130, y + 90), ON[col], 18, col, 3)
        s.icon(ic, 62, y + 6, 28, col if on else MUT); s.txt_c(76, y + 63, lab, 22, col if on else MUT, on)
    return s


def l_drive_head(name, title, chips=(('car-front', '주행 중', MINT),), bg=BG):
    s = Scr(name, (LW, LH), bg); s.logo(20, 16, 72); s.txt(108, 24, '플레오스 가드', 30, b=True)
    if title:
        s.txt(370, 26, title, 28, b=True)
    x = 1256 - s.draft(1256, 30) - 14
    for ic, t, col in chips:
        x -= s.chip(x, 24, t, ic, col, right=True) + 12
    return s


# ---------------------------------------------------------------- 가로 화면
def l_home(selected=False):
    s = l_frame('ui-parts' if selected else 'ui-home', '어디에서 이상을 느끼셨나요', 'home', None)
    s.box((160, 128, 928, 584)); s.txt(184, 148, '내 차의 부위를 직접 선택하세요', 26, b=True)
    k = s.car(194, 186, 700)
    for (sx, sy), lab in SPOTS:
        x, y = 194 + sx * k, 186 + sy * k; on = selected and lab == '앞 타이어'; c = AMBER if on else MINT
        s.d.ellipse((x - 22, y - 22, x + 22, y + 22), fill=BG, outline=c, width=4); s.d.ellipse((x - 7, y - 7, x + 7, y + 7), fill=c)
        lw = s.tw(lab, 24, True) + 30; ly = y - 70 if lab != '앞 타이어' else y - 82; lx = min(max(x - lw / 2, 176), 912 - lw)
        s.box((lx, ly, lx + lw, ly + 42), '#182D3A', 12, c if on else None); s.txt_c(lx + lw / 2, ly + 21, lab, 24, c, True)
    s.box((952, 128, 1256, 584))
    if selected:
        s.icon('wheel', 976, 148, 40, AMBER); s.txt_vc(1028, 168, '앞 타이어', 28, FG, True); s.txt(976, 206, '어떤 증상인가요', 22, MUT)
        for j, (ic, lab) in enumerate((('audio-lines', '소음'), ('activity', '떨림'), ('steering', '쏠림'), ('circle-help', '잘\n모르겠음'))):
            xx = 972 + j % 2 * 144; yy = 248 + j // 2 * 164; on = j == 0
            s.box((xx, yy, xx + 120, yy + 148), ON[AMBER] if on else SURF2, 18, AMBER if on else None, 3 if on else 2)
            s.icon(ic, xx + 36, yy + 14, 48, AMBER if on else FG); s.txt_c(xx + 60, yy + 104, lab, 22, AMBER if on else FG, True)
    else:
        s.txt(976, 148, '지금 상태', 26, b=True)
        for j, (ic, t, col) in enumerate((('park', '정차 중', MINT), ('bluetooth', '휴대폰 연결됨', MINT), ('users', '보호자 2명 준비됨', PURPLE))):
            y = 200 + j * 58; s.icon(ic, 976, y, 32, col); s.txt_vc(1022, y + 16, t, 24, FG)
        s.d.line((976, 380, 1232, 380), fill=LINE, width=2)
        s.icon('washer', 976, 398, 32, AMBER); s.txt_vc(1022, 414, '워셔액 부족', 24, AMBER, True)
        s.badge(1022, 446); s.txt(976, 520, '출발 전 점검 보기 →', 22, MINT, True)
    nbtn(s, LB_L, '지금 기록', 'rec', ic_col=MINT); rbtn(s, LB_R, '도움이 필요해요', 'lifebuoy')
    s.save(LA)


def l_warning():
    s = l_frame('ui-warning', '경고등 찾기', 'warning', None)
    s.chip(560, 24, '문구로 찾기', 'search', FG, h=56); s.chip(752, 24, '말로 찾기', 'mic', AMBER, h=56)
    s.box((160, 128, 820, 584))
    s.num(180, 162, '1'); s.txt_vc(224, 180, '색', 24, FG, True); color_chips(s, 284, 148, 796, h=64, gap=12)
    s.num(180, 232, '2'); s.txt_vc(224, 250, '모양', 24, FG, True); s.txt_r(796, 236, '계기판과 같은 모양을 고르세요', 22, MUT)
    warn_tiles(s, 180, 278, 796, 494, cols=4, gap=12, sym=48)
    s.num(180, 520, '3'); s.txt_vc(224, 538, '상태', 24, FG, True); state_chips(s, 284, 506, 796, h=64, gap=12)
    s.box((844, 128, 1256, 584)); s.txt(868, 144, '후보 2개', 24, b=True); s.badge(1232, 142, '안내 예시 · 검토 전', right=True)
    for j, (name, st, on) in enumerate((('타이어 공기압 낮음', '계속 켜짐', True), ('공기압 장치 점검', '깜박인 뒤 켜짐', False))):
        y = 184 + j * 82; s.box((860, y, 1240, y + 76), ON[AMBER] if on else SURF2, 16, AMBER if on else None, 3 if on else 2)
        s.icon('tire', 874, y + 15, 46, AMBER); s.txt(932, y + 6, name, 24, FG, True); s.txt(932, y + 40, st, 22, AMBER if on else MUT, on)
        if on:
            s.icon('circle-check', 1200, y + 22, 28, AMBER)
    judgment_band(s, (860, 354, 1240, 406), n=24, note=False)
    s.txt(868, 418, '공기압이 많이 낮을 때 켜집니다.\n안전한 곳에 세운 뒤 확인하세요.\n맞춘 뒤에도 켜지면 점검받으세요.', 22, FG, maxw=364)
    s.txt(868, 512, '계기판 안내와 다르면\n차량 안내를 따르세요', 22, MUT, maxw=364)
    nbtn(s, LB_L, '정차 후 자세히', 'park'); rbtn(s, LB_R, '정비소 들르기', 'wrench', kind=AMBER)
    s.save(LA)


def l_record():
    s = l_frame('ui-record', '증상을 기록했습니다', 'record')
    s.box((160, 128, 880, 584)); s.txt(184, 144, '차량 상태 시간표', 24, b=True); s.badge(856, 142, '차속 · 시험 입력', right=True)
    s.txt(184, 182, '08:42:15 기록 · 앞 20초와 뒤 10초', 22, MUT)
    timeline(s, (184, 230, 856, 518), label_w=230, row_h=68)
    s.box((904, 128, 1256, 584)); s.txt(928, 144, '어떤 증상이었나요', 24, b=True)
    symptom_chips(s, 920, 186, 1240, cols=2, h=52, gap=8)
    s.txt(928, 372, '함께 남긴 상태', 22, MUT, True); s.badge(1232, 368, right=True)
    for j, (ic, t) in enumerate(CABIN):
        x = 928 + j % 2 * 156; y = 410 + j // 2 * 36; s.icon(ic, x, y, 26, MINT); s.txt_vc(x + 34, y + 13, t, 22, FG, maxw=118)
    s.icon('mic', 928, 508, 26, MINT); s.txt(964, 496, '말로 메모\n앞 타이어 쪽 끼익 소리', 22, MINT, True, maxw=280)
    nbtn(s, LB_L, '기록 끝내기', 'circle-check'); rbtn(s, LB_R, '말로 메모', 'mic', kind=MINT)
    s.save(LA)


def l_history():
    s = l_frame('ui-history', '재현 조건', 'record')
    s.box((160, 128, 536, 584)); s.car(184, 150, 328)
    s.txt(184, 352, '앞 타이어 · 끼익 소리', 28, b=True); s.txt(184, 398, '같은 증상 3번', 24, MINT, True)
    s.txt(184, 440, '11월 6일 · 9일 · 12일', 22, MUT); s.txt(184, 496, '기록 순간의 차 신호를\n평소 주행과 견준 자료입니다', 22, MUT)
    s.box((560, 128, 1256, 584)); s.txt(584, 144, '평소 주행보다 자주 겹친 조건', 26, b=True)
    cond_rows(s, 576, 190, 1240, row_h=100, gap=10, split=300, vn=24)
    s.txt(584, 414, '소리 원인 후보에서 뺀 것', 22, FG, True)
    for j, (ic, t) in enumerate(SUSPECTS):
        x = 584 + j % 2 * 330; y = 452 + j // 2 * 44
        s.icon(ic, x, y, 26, MINT); s.txt_vc(x + 36, y + 13, t, 22, FG, maxw=288)
    nbtn(s, LB_L, '기록 목록', 'history'); rbtn(s, LB_R, '정비소에 보여 주기', 'qr', kind=MINT)
    s.save(LA)


def emergency_landscape(name, title, slot, left, right, hazard=False):
    s = Scr(name, (LW, LH), EBG)
    s.box((16, 16, 1264, 104), EBAND, 20); s.icon('octagon-alert', 40, 32, 56, FG); s.txt_vc(116, 60, title, 34, FG, True)
    dw = s.draft(1240, 40, EDRAFT, EDRAFT); s.icon('volume-2', 1240 - dw - 190, 44, 32, FG); s.txt_vc(1240 - dw - 150, 60, '음성 안내 중', 22, FG, True)
    s.chip(40, 124, '주행 중', 'car-front', EPINK, FG, ECARD, None, h=48)
    s.txt(40, 190, '앞을 보세요\n음성 안내를 따라 주세요', 44, FG, True, maxw=600)
    slot_card(s, (40, 340, 628, 584), slot, n=28)
    s.paste_round(road_scene(588, 456, hazard), (652, 128, 1240, 584), 24)
    nbtn(s, LE_L, left[0], left[1], left[2], emergency=True)
    rbtn(s, LE_R, right[0], right[1], right[2], kind='alert')
    s.save(LA)


def l_confirm():
    s = l_drive_head('ui-confirm', '확인 질문', (('volume-2', '음성으로도 묻습니다', FG), ('car-front', '주행 중', MINT)))
    s.box((24, 120, 820, 584), ECARD, 26, RED, 3)
    s.icon('circle-help', 52, 148, 44, RED); s.txt_vc(110, 170, '확인 질문', 24, EPINK, True)
    s.txt(52, 222, '차가 멈추지 않나요?', 68, FG, True)
    s.d.line((52, 340, 792, 340), fill='#4A2430', width=2)
    s.icon('pedal', 52, 372, 36, AMBER)
    bb = s.txt(104, 364, '브레이크를 밟은 1초 동안\n속도가 줄지 않았습니다', 26, FG, True)
    s.badge(104, bb[3] + 14, '차속 · 시험 입력')
    s.box((844, 120, 1256, 584), SURF, 26)
    s.icon('mic', 868, 148, 36, EPINK); s.txt(916, 144, '말로 하는 답도\n8초 동안 듣습니다', 24, FG, True)
    s.txt(868, 270, '앱은 운전자의 답을\n들은 뒤에만 긴급 안내를\n시작합니다', 22, MUT, maxw=364)
    nbtn(s, LD_L, '괜찮아요', 'circle-check', '질문 닫기')
    rbtn(s, LD_R, '안 멈춰요', 'octagon-alert', '감속 실패 대처', kind='alert')
    s.save(LA)


def l_countdown():
    s = Scr('ui-countdown', (LW, LH), EBG)
    s.box((16, 16, 1264, 104), EBAND, 20); s.icon('octagon-alert', 40, 32, 56, FG); s.txt_vc(116, 60, '보호자 알림 · 발송 확인(10초)', 34, FG, True)
    s.draft(1240, 40, EDRAFT, EDRAFT)
    s.txt(40, 124, '에어백 전개 신호를', 30, FG, True); bb = s.txt(40, 166, '받았습니다', 30, FG, True); s.badge(bb[2] + 14, 168)
    s.box((40, 228, 424, 460), ECARD, 22)
    s.txt_c(232, 316, '10', 104, FG, True); s.txt_c(232, 410, '초 뒤 보냅니다', 26, FG, True)
    s.txt(40, 486, '119에 자동 신고하지 않습니다.\n필요하면 119에 직접 신고하세요', 22, MUT, maxw=384)
    s.box((448, 128, 1256, 584), ECARD, 22); s.txt(472, 144, '받는 사람', 24, FG, True)
    for j, t in enumerate(('보호자 1', '보호자 2')):
        y = 184 + j * 76; s.box((464, y, 1240, y + 66), '#3A1C26', 16)
        s.icon('users', 480, y + 13, 40, PURPLE); s.txt_vc(534, y + 33, t, 26, FG, True); s.txt_r(1222, y + 20, '알림톡, 안 되면 문자', 22, MUT)
    s.txt(472, 350, '보낼 내용', 24, FG, True)
    rows = (('감지', '에어백 전개 신호(운전석) · 21:00:03', True), ('위치', '측정 시각 21:00:01', False),
            ('차', '탑승 1명(운전석) · 안전벨트 착용 · 문 닫힘', True))
    for j, (k, v, test) in enumerate(rows):
        y = 396 + j * 56; s.txt(472, y, k, 22, MUT, True); s.txt(540, y - 2, v, 24, FG)
        if test:
            s.badge(1232, y - 6, right=True)
    nbtn(s, LE_L, '괜찮아요', 'circle-check', '보내지 않음', emergency=True)
    rbtn(s, LE_R, '지금 보내기', 'send', '기다리지 않고 바로', kind='alert')
    s.save(LA)


def l_guardian():
    s = l_frame('ui-guardian', '보호자 알림 상태', 'guardian')
    s.box((160, 128, 780, 584)); s.txt(184, 144, '보호자별 상태', 24, b=True); s.txt_r(756, 146, '21:00:13 보냄', 22, MUT)
    rows = [('보호자 1', 'check-check', '전달 · 열람 21:01 · 전화할게요', MINT), ('보호자 2', 'message-circle', '문자로 다시 보냄 21:00:23', AMBER)]
    guardian_rows(s, 176, 184, 764, 68, 10, 24, rows)
    s.d.line((184, 348, 756, 348), fill=LINE, width=2)
    s.icon('map-pin', 184, 366, 36, PURPLE); s.txt(232, 360, '위치 측정 시각', 22, MUT); s.txt(232, 390, '21:00:01', 30, FG, True)
    s.txt(420, 398, '차량 · 휴대폰 위치 따로 표시', 22, MUT)
    s.txt(184, 446, '주소: ○○시 ○○구 ○○대로 부근 (예시)', 22, FG)
    s.txt(184, 482, '지도는 보호자 휴대폰 페이지에서 봅니다', 22, MUT)
    s.txt(184, 518, '보호자 2명에게 위치를 보내는 중', 22, PURPLE, True)
    s.box((804, 128, 1256, 584)); s.txt(828, 144, '사고 순간 차 상태', 24, b=True)
    seat_diagram(s, (832, 188, 920, 356)); s.txt(948, 196, '21:00:03 기준', 22, MUT); s.txt(948, 232, '운전석 1명', 24, FG, True)
    snap_rows(s, 828, 374, 50)
    nbtn(s, LB_L, '기록 보기', 'history'); rbtn(s, LB_R, '공유 중지', 'pin-off', kind=PURPLE)
    s.save(LA)


def l_manual():
    """주행 중 상황 선택: 큰 카드 5개, 도크 숨김."""
    s = l_drive_head('ui-manual', '상황 선택')
    s.txt(24, 108, '급한 상황 · 누르면 바로 안내합니다', 22, MUT)
    w = (1232 - 32) / 3
    for j, (ic, _, t) in enumerate(EMERG):
        x = 24 + j * (w + 16); s.box((x, 144, x + w, 330), ECARD, 24, RED, 3)
        s.icon(ic, x + w / 2 - 32, 170, 64, RED); s.txt_c(x + w / 2, 290, t, 32, FG, True, maxw=w - 24)
    s.txt(24, 346, '증상 · 기록만 하고 보호자 알림은 묻습니다', 22, MUT)
    s.txt_r(1256, 346, '차를 세우면 12가지를 모두 고를 수 있습니다', 22, DIM)
    w2 = (1232 - 16) / 2
    for j, (ic, t) in enumerate((('activity', '차체 떨림'), ('flame', '타는 냄새'))):
        x = 24 + j * (w2 + 16); s.box((x, 384, x + w2, 584), SURF, 24, LINE, 2)
        s.icon(ic, x + 60, 452, 64, AMBER); s.txt_vc(x + 150, 484, t, 34, FG, True)
    nbtn(s, LD_L, '고른 증상 기록', 'rec', ic_col=MINT); rbtn(s, LD_R, '지금 도움 요청', 'send')
    s.save(LA)


def l_manual_parked():
    s = l_frame('ui-manual-parked', '상황 선택', None, ('circle-check', '2개 선택 · 최대 3개'))
    w = (1096 - 24) / 3
    for j, (ic, _, t) in enumerate(EMERG):
        x = 160 + j * (w + 12); s.box((x, 128, x + w, 208), ECARD, 20, RED, 3)
        s.icon(ic, x + 20, 148, 40, RED); s.txt_vc(x + 74, 168, t, 26, FG, True, maxw=w - 84)
    s.txt(160, 220, '빨간 칸은 바로 안내 · 아래 증상은 기록만 하고 보호자 알림은 묻습니다', 22, MUT, maxw=1096)
    h = (584 - 260 - 16) / 3
    for i, (ic, t) in enumerate(PARKED):
        x = 160 + i % 3 * (w + 12); y = 260 + i // 3 * (h + 8); on = t in PARKED_ON
        s.box((x, y, x + w, y + h), ON[AMBER] if on else SURF, 20, AMBER if on else None, 3 if on else 2)
        s.icon(ic, x + 20, y + h / 2 - 22, 44, AMBER if on else FG); s.txt_vc(x + 80, y + h / 2, t, 26, AMBER if on else FG, True, maxw=w - 92)
    nbtn(s, LB_L, '고른 증상 기록', 'rec', ic_col=MINT); rbtn(s, LB_R, '지금 도움 요청', 'send')
    s.save(LA)


def l_check():
    s = l_frame('ui-check', '출발 전 점검', 'warning', ('lock', '정차 중에만'))
    s.box((160, 128, 820, 584)); s.txt(184, 144, '차가 알려 준 점검 항목', 24, b=True); s.txt(184, 182, '경고등이 아니라 차가 보내는 점검 신호입니다', 22, MUT)
    check_grid(s, 176, 222, 804, rows_h=104, gap=12, isz=36, n=24, ipad=14, igap=12)
    s.d.line((184, 462, 796, 462), fill=LINE, width=2)
    s.icon('history', 184, 488, 40, MINT); s.txt(238, 478, '끝나지 않은 기록 1건', 24, FG, True)
    s.txt(238, 516, '앞 타이어 끼익 소리 3번 · 정비 전', 22, MUT); s.txt_r(796, 498, '기록 보기 →', 22, MINT, True)
    s.box((844, 128, 1256, 584)); s.txt(868, 144, '정비 일정', 24, b=True); s.txt_r(1232, 146, '32,140km', 24, FG, True)
    s.d.rounded_rectangle((868, 196, 1232, 210), radius=7, fill='#2E3F57'); s.d.rounded_rectangle((868, 196, 868 + 364 * .714, 210), radius=7, fill=MINT)
    s.log('graphic', '정비 일정 막대', MINT, SURF)
    s.txt(868, 226, '정기 점검까지\n2,860km 남았습니다', 22, FG)
    s.d.line((868, 316, 1232, 316), fill=LINE, width=2); s.txt(868, 334, '준비 상태', 24, b=True)
    for j, (ic, t, sub, col) in enumerate((('bluetooth', '휴대폰 연결됨', '11:58 확인', MINT), ('users', '보호자 2명 준비됨', '연습 알림 받음', PURPLE))):
        y = 384 + j * 92; s.icon(ic, 868, y + 4, 36, col); s.txt(916, y, t, 24, FG, True); s.txt(916, y + 38, sub, 22, MUT)
    nbtn(s, LB_L, '확인했어요', 'circle-check'); rbtn(s, LB_R, '정비소 들르기', 'wrench', kind=AMBER)
    s.save(LA)


def l_practice():
    s = l_frame('ui-practice', '비상 절차 미리 익히기', None, ('lock', '정차 중에만 열립니다'))
    s.box((160, 128, 800, 584)); s.asset(A / 'pedal-detail.png', (168, 136, 624, 352))
    s.txt(184, 504, '단계 1/3', 24, MINT, True); s.txt_r(784, 506, '브레이크 → 비상등 → 비상 제동 듣기', 22, MUT)
    w = (600 - 24) / 3
    for j in range(3):
        x = 184 + j * (w + 12); s.d.rounded_rectangle((x, 548, x + w, 562), radius=7, fill=MINT if j == 0 else '#2E3F57')
    s.box((824, 128, 1256, 584)); s.txt(848, 150, '브레이크를\n밟아 보세요', 36, FG, True)
    s.icon('circle-check', 848, 268, 36, MINT); s.txt_vc(896, 286, '브레이크 신호를 받았습니다', 24, MINT, True)
    s.txt(848, 340, '차가 멈춰 있을 때만 연습합니다.\n주행 중에는 음성과 정지 그림만\n보여 줍니다.', 22, FG, maxw=392)
    s.txt(848, 500, '그림: AI 설명 그림\n문장: 취급설명서 확인 전 예시', 22, MUT)
    nbtn(s, LB_L, '그만하기', 'close'); rbtn(s, LB_R, '다음 단계', 'next', kind=MINT)
    s.save(LA)


def l_service():
    s = l_frame('ui-service', '가는 길에 정비소 들르기', 'service')
    s.box((160, 128, 820, 584)); s.icon('flag', 184, 148, 36, MINT); s.txt_vc(232, 166, '목적지는 그대로 둡니다', 24, FG, True)
    s.txt(184, 196, '고른 정비소를 지금 경로에 경유지로 넣습니다', 22, MUT)
    shop_rows(s, 176, 238, 804, h=104, gap=10, n=26, tn=30)
    s.box((844, 128, 1256, 584)); s.icon('navigation', 868, 150, 36, MINT); s.txt(916, 146, '지도는 옆 화면의\n내비에서 봅니다', 24, FG, True)
    s.txt(868, 248, '내비에서 보기를 누르면\n고른 정비소를 내비에\n띄웁니다', 22, MUT, maxw=364)
    s.txt(868, 370, '도착 예상 시각은\n내비가 알려 준 값입니다', 22, MUT, maxw=364)
    s.txt(868, 460, '정비소 목록은 앱 안\n자료입니다 (예시)', 22, MUT, maxw=364)
    nbtn(s, LB_L, '내비에서 보기', 'navigation'); rbtn(s, LB_R, '경유지로 추가', 'map-pin', kind=AMBER)
    s.save(LA)


def l_home_driving():
    s = l_drive_head('ui-home-driving', None, (('users', '보호자 2명 준비됨', PURPLE), ('bluetooth', '휴대폰 연결됨', MINT), ('car-front', '주행 중', MINT)))
    w = (1232 - 32) / 3
    cols = [('지금 기록', 'rec', SURF2, LINE, MINT, '누른 순간의 차 상태를 남깁니다', 'left-neutral'),
            ('말로 하기', 'mic', SURF2, LINE, FG, '누른 뒤 8초 동안 말을 듣습니다', 'middle-neutral'),
            ('도움이 필요해요', 'lifebuoy', HELP, None, FG, '급한 상황을 고릅니다', 'right-red')]
    for j, (lab, ic, fill, stroke, icc, sub, side) in enumerate(cols):
        x0 = 24 + j * (w + 16)
        s.vbtn((x0, 120, x0 + w, 580), lab, ic, fill, FG, sub, stroke, n=34, ic_col=icc, isz=72,
               sub_c=MUT if fill == SURF2 else FG, side=side)
    s.box((24, 604, 1256, 676), SURF, 20); s.icon('lock', 48, 624, 32, DIM)
    s.txt_vc(96, 640, '경고등 자세한 설명 · 점검 · 설정은 정차 후에 열립니다', 22, DIM, True)
    s.save(LA)


# ---------------------------------------------------------------- 실행
def main():
    PO.mkdir(parents=True, exist_ok=True); LA.mkdir(parents=True, exist_ok=True)
    p_home(); p_home(True); p_home_driving(); p_warning_find(); p_warning_result(); p_check(); p_record(); p_pattern()
    p_manual(); p_manual_parked(); p_confirm()
    emergency_portrait('p-emergency', '원치 않는 가속', '취급설명서 비상 절차\n(검토 후 확정 문장)',
                       ('괜찮아요', 'circle-check', '안내 끝내기'), ('안 멈춰요', 'octagon-alert', '감속 실패 대처'))
    emergency_portrait('p-stopping', '감속 실패 대처', '취급설명서 비상 제동 절차\n(검토 후 확정 문장)',
                       ('차가 멈췄어요', 'circle-check', '안내 끝내기'), ('도움 요청', 'send', '받는 사람 확인'), hazard=True)
    p_countdown(); p_guardian(); p_practice(); p_service()
    l_home(); l_home(True); l_home_driving(); l_warning(); l_record(); l_history()
    l_manual(); l_manual_parked(); l_confirm()
    emergency_landscape('ui-guidance', '긴급 안내 · 원치 않는 가속', '취급설명서 비상 절차\n(검토 후 확정 문장)',
                        ('괜찮아요', 'circle-check', '안내 끝내기'), ('안 멈춰요', 'octagon-alert', '감속 실패 대처'))
    emergency_landscape('ui-stopping', '긴급 안내 · 감속 실패 대처', '취급설명서 비상 제동 절차\n(검토 후 확정 문장)',
                        ('차가 멈췄어요', 'circle-check', '안내 끝내기'), ('도움 요청', 'send', '받는 사람 확인'), hazard=True)
    l_countdown(); l_guardian(); l_check(); l_practice(); l_service()
    pairs = {}
    for scr, r in REPORT.items():
        for c in r['checks']:
            k = (c['fg'], c['bg']); p = pairs.setdefault(k, dict(fg=c['fg'], bg=c['bg'], ratio=c['ratio'], count=0, screens=set()))
            p['count'] += 1; p['screens'].add(scr)
    summary = dict(version='v4.1', rule=dict(min_text_px=MIN_FONT, min_contrast=MIN_CONTRAST, min_button_px=MIN_BTN, max_lines=3,
                                             button_sides='왼쪽 = 끝내기·취소·기록(중립색), 오른쪽 = 도움·보내기·감속 실패(빨강) 또는 주 행동 색'),
                   screens={k: {kk: vv for kk, vv in v.items() if kk != 'checks'} for k, v in REPORT.items()},
                   min_ratio=min(v['min_ratio'] for v in REPORT.values()),
                   min_text_px=min(v['min_text_px'] for v in REPORT.values()),
                   pairs=sorted([dict(p, screens=sorted(p['screens'])) for p in pairs.values()], key=lambda p: p['ratio']),
                   details={k: v['checks'] for k, v in REPORT.items()})
    (OUT / 'contrast-check.json').write_text(json.dumps(summary, ensure_ascii=False, indent=1), encoding='utf-8')
    print(f'화면 {len(REPORT)}장, 최소 대비 {summary["min_ratio"]}, 최소 글자 {summary["min_text_px"]}px')


if __name__ == '__main__':
    main()
