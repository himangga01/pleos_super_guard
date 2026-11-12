"""플레오스 가드 화면 시안 v4 생성기.

- build_ui_final.py 의 함수 형식(txt·box·icon·asset·btn)을 복사해 넓혔습니다. 기존 생성기와 기존 이미지는 바꾸지 않습니다.
- 출력
  assets/v4/portrait/p-*.png    세로 한 창 840×1212 (Pleos 마켓 스크린샷 세로 규격 비율)
  assets/v4/landscape/ui-*.png  가로 1280×720 (기존 승인 시안 구조 유지, 규정 충돌만 수정)
  assets/v4/contrast-check.json 글자·아이콘마다 잰 대비, 글자 크기, 겹침 검사 결과
- 그릴 때마다 검사합니다: 글자 22px 이상, 3줄 이하, 화면 밖으로 나가지 않음, 정해진 폭을 넘지 않음,
  글자·아이콘과 바로 아래 배경(그 자리에서 가장 많은 색)의 대비 4.5:1 이상, 주요 버튼 높이 88px 이상.
- 이모지·실제 번호판·실명·전화번호를 쓰지 않습니다. 경고등 기호는 일반 기호를 단순한 선으로 새로 그렸습니다.
실행: python3 build_ui_v4.py   (Pillow, assets/fonts 의 Pretendard 필요)
"""
from __future__ import annotations
import json, math, sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

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


SYM = dict(engine=_engine, oil=_oil, brake=_brake, battery=_battery, tire=_tire, belt=_belt, airbag=_airbag,
           coolant=_coolant, home=_home, users=_users, lifebuoy=_lifebuoy, rec=_rec, qr=_qr, steering=_steering,
           pedal=_pedal, turn=_turn, search=_search, flag=_flag, lock=_lock, door=_door, washer=_washer,
           trend=_trend, up=_up, road=_road, crash=_crash, seat=_seat, park=_park, steady=_steady, blink=_blink, next=_next, close=_close)

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

    def btn(s, rect, label, ic, fill, fg, sub=None, stroke=None, n=28):
        x0, y0, x1, y1 = rect
        assert y1 - y0 >= MIN_BTN, (s.name, label, '버튼 높이', y1 - y0)
        s.buttons.append(dict(label=label, rect=list(rect), h=y1 - y0))
        s.box(rect, fill, 26, stroke, 2); cy = (y0 + y1) / 2; isz = 40 if y1 - y0 > 110 else 36
        s.icon(ic, x0 + 26, cy - isz / 2, isz, fg); tx = x0 + 26 + isz + 18; maxw = x1 - tx - 16
        if sub:
            bm, bs = F(n, True).getbbox(label), F(22, True).getbbox(sub); gap = 12
            hm, hs = bm[3] - bm[1], bs[3] - bs[1]; top = cy - (hm + gap + hs) / 2
            s.txt(tx, top - bm[1], label, n, fg, True, maxw=maxw)
            s.txt(tx, top + hm + gap - bs[1], sub, 22, fg, True, maxw=maxw)
        else:
            s.txt_vc(tx, cy, label, n, fg, True, maxw=maxw)

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
CHECKS = [('oil', '엔진오일', '정상', MINT, True), ('brake', '브레이크액', '정상', MINT, True),
          ('washer', '워셔액', '부족 · 보충 필요', AMBER, True), ('trend', '출력 저하', '표시 없음', MINT, True),
          ('belt', '안전벨트', '운전석 착용', MINT, True), ('door', '도어', '모두 닫힘', MINT, False)]
SYMPTOMS = [('activity', '떨림'), ('audio-lines', '소음'), ('steering', '쏠림'), ('flame', '냄새'), ('engine', '경고등'), ('circle-help', '기타')]


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


def timeline(s, rect, label_w=220, row_h=72, t0=-20, t1=10, badge_on_speed=True):
    """차량 상태 스냅샷 시간표: 차속 막대·브레이크 구간·조향각 점·방향지시등 막대. 움직이지 않는 그림입니다."""
    x0, y0, x1, y1 = rect; px0 = x0 + label_w; px1 = x1 - 8
    X = lambda t: px0 + (t - t0) / (t1 - t0) * (px1 - px0)
    rows = [('circle-gauge', '차속', '72km/h', MINT), ('pedal', '브레이크', '기록 전 밟음', AMBER),
            ('steering', '조향각', '오른쪽 12도', PURPLE), ('turn', '방향지시등', '꺼짐', FG)]
    speeds = [66, 67, 68, 68, 69, 70, 70, 71, 72, 72, 73, 73, 72, 72, 71, 71, 72, 73, 74, 74, 72, 71, 70, 70, 69, 69, 68, 68, 67, 67]
    steer = [0, 0, 1, 0, -1, 0, 0, 1, 1, 2, 2, 3, 5, 8, 10, 12, 12, 12, 11, 9, 12, 10, 8, 6, 4, 2, 1, 0, 0, 0]
    for i, (ic, name, val, col) in enumerate(rows):
        ry = y0 + i * row_h
        s.d.rounded_rectangle((px0, ry + 6, px1, ry + row_h - 6), radius=10, fill='#16233A')
        s.icon(ic, x0, ry + 10, 30, col)
        s.txt(x0 + 40, ry + 2, name, 24, FG, True)
        s.txt(x0 + 40, ry + 40, val, 22, MUT)
        if i == 0 and badge_on_speed:
            s.badge(x0 + 40 + s.tw(name, 24, True) + 12, ry)
        cy = ry + row_h / 2; top, bot = ry + 14, ry + row_h - 14
        if i == 0:
            for k, v in enumerate(speeds):
                t = t0 + k; hh = (v - 55) / 25 * (bot - top)
                s.d.rectangle((X(t) + 3, bot - hh, X(t + 1) - 3, bot), fill='#3FA59A')
        elif i == 1:
            s.d.rounded_rectangle((X(-8), cy - 12, X(-5), cy + 12), radius=8, fill=AMBER)
            s.log('graphic', '브레이크 구간', AMBER, '#16233A')
        elif i == 2:
            s.d.line((px0 + 6, cy, px1 - 6, cy), fill='#33476A', width=2)
            for k, v in enumerate(steer):
                t = t0 + k + .5; yy = cy - v / 12 * (row_h / 2 - 16)
                s.d.ellipse((X(t) - 5, yy - 5, X(t) + 5, yy + 5), fill=PURPLE)
            s.log('graphic', '조향각 점', PURPLE, '#16233A')
        else:
            for t in (-15, -14, -13, -12):
                s.d.rounded_rectangle((X(t) + 3, cy - 10, X(t + .55), cy + 10), radius=5, fill=FG)
    s.d.line((X(0), y0, X(0), y0 + 4 * row_h), fill=MINT, width=4)
    s.d.polygon([(X(0) - 9, y0 - 2), (X(0) + 9, y0 - 2), (X(0), y0 + 10)], fill=MINT)
    ay = y0 + 4 * row_h + 4
    s.txt(px0, ay, f'{t0}초', 22, MUT); s.txt_c(X(-10), ay + 14, '-10초', 22, MUT)
    s.txt_c(X(0), ay + 14, '기록 시점', 22, MINT, True); s.txt_r(px1, ay, f'+{t1}초', 22, MUT)


def road_scene(w, h, hazard=False):
    """주행 중 긴급 화면의 정지 그림: 도로와 차 뒷모습만, 손·발 동작 화살표 없음."""
    Z = 3; W, H = w * Z, h * Z; im = Image.new('RGB', (W, H), '#1A0F15'); d = ImageDraw.Draw(im)
    hz = int(H * 0.32)
    for y in range(hz):
        d.line([(0, y), (W, y)], fill=mix('#120A10', '#2C1620', y / hz))
    d.rectangle((0, hz, W, H), fill='#180E14'); vx = W / 2
    d.polygon([(vx - W * .05, hz), (vx + W * .05, hz), (W * 1.02, H), (-W * .02, H)], fill='#2A2C35')
    for sx, ex in ((vx - W * .045, W * .06), (vx + W * .045, W * .94)):
        d.line([(sx, hz), (ex, H)], fill='#C9CED8', width=4 * Z)
    for lane in (-1, 1):
        for k in range(9):
            t0 = (k / 9) ** 1.6; t1 = ((k + .5) / 9) ** 1.6
            p = lambda t: (vx + lane * (W * .016 + t * W * .30), hz + t * (H - hz))
            d.line([p(t0), p(t1)], fill='#8A8F9C', width=max(2, int((2 + 6 * t1) * Z)))
    cw, ch = W * .42, H * .30; cx, cy = W * .5, H * .79
    d.ellipse((cx - cw * .58, cy + ch * .32, cx + cw * .58, cy + ch * .58), fill='#0E080C')
    d.rounded_rectangle((cx - cw * .46, cy + ch * .25, cx - cw * .30, cy + ch * .50), radius=8 * Z, fill='#101114')
    d.rounded_rectangle((cx + cw * .30, cy + ch * .25, cx + cw * .46, cy + ch * .50), radius=8 * Z, fill='#101114')
    d.polygon([(cx - cw * .36, cy - ch * .40), (cx + cw * .36, cy - ch * .40), (cx + cw * .27, cy - ch * .95), (cx - cw * .27, cy - ch * .95)], fill='#6E7884')
    d.polygon([(cx - cw * .30, cy - ch * .46), (cx + cw * .30, cy - ch * .46), (cx + cw * .23, cy - ch * .88), (cx - cw * .23, cy - ch * .88)], fill='#232B36')
    d.rounded_rectangle((cx - cw * .5, cy - ch * .46, cx + cw * .5, cy + ch * .34), radius=26 * Z, fill='#97A1AD')
    d.rounded_rectangle((cx - cw * .47, cy - ch * .26, cx + cw * .47, cy - ch * .17), radius=6 * Z, fill='#FF5B6B')
    d.rounded_rectangle((cx - cw * .16, cy - ch * .06, cx + cw * .16, cy + ch * .08), radius=4 * Z, fill='#5E6874')
    d.rounded_rectangle((cx - cw * .44, cy + ch * .16, cx + cw * .44, cy + ch * .32), radius=10 * Z, fill='#3B424C')
    if hazard:
        for sx in (-1, 1):
            x = cx + sx * cw * .43
            d.rounded_rectangle((x - cw * .06, cy - ch * .31, x + cw * .06, cy - ch * .12), radius=6 * Z, fill='#FFB020')
    return im.resize((w, h), LZ)


def schematic_map(w, h, kind):
    """실제 지도를 복제하지 않은 단순 도식."""
    Z = 2; W, H = w * Z, h * Z; im = Image.new('RGB', (W, H), '#112335'); d = ImageDraw.Draw(im)
    for j in range(7):
        y = H * (j + .5) / 7; d.line([(0, y + 18 * Z), (W, y - 22 * Z)], fill='#1E3850', width=12 * Z)
    for j in range(6):
        x = W * (j + .5) / 6; d.line([(x - 20 * Z, 0), (x + 30 * Z, H)], fill='#1E3850', width=10 * Z)
    return im.resize((w, h), LZ)


def seat_diagram(s, rect):
    """사고 순간 차량 스냅샷: 위에서 본 좌석(앞이 위쪽). 운전석 왼쪽 앞."""
    def fn(d, Z, w, h):
        k = Z; bw = w * k; bh = h * k
        d.rounded_rectangle((bw * .14, 0, bw * .86, bh - 1), radius=bw * .22, outline=MUT, width=3 * k)
        d.line((bw * .22, bh * .22, bw * .78, bh * .22), fill=MUT, width=3 * k)
        seats = [(.24, .30, True), (.53, .30, True), (.24, .62, False), (.53, .62, False)]
        for sx, sy, occ in seats:
            box = (bw * sx, bh * sy, bw * (sx + .23), bh * (sy + .22))
            if occ:
                d.rounded_rectangle(box, radius=8 * k, fill='#2F3366', outline=PURPLE, width=3 * k)
                d.line((box[2] - 6 * k, box[1] + 6 * k, box[0] + 6 * k, box[3] - 6 * k), fill=FG, width=3 * k)
            else:
                d.rounded_rectangle(box, radius=8 * k, outline=DIM, width=3 * k)
        d.ellipse((bw * .27, bh * .12, bw * .27 + bw * .17, bh * .12 + bw * .17), fill=AMBER)
        d.line((bw * .14, bh * .32, bw * .02, bh * .44), fill=AMBER, width=5 * k)
    s.aa(rect, fn)
    s.log('graphic', '좌석 외곽선', MUT, s.sample((rect[0], rect[1], rect[0] + 6, rect[1] + 6)))


def speed_band(s, x0, y, x1, lo=60, hi=80, vmax=120):
    s.d.rounded_rectangle((x0, y - 5, x1, y + 5), radius=5, fill='#2E3F57')
    X = lambda v: x0 + v / vmax * (x1 - x0)
    s.d.rounded_rectangle((X(lo), y - 11, X(hi), y + 11), radius=8, fill=MINT)
    s.log('graphic', '속도 구간 막대', MINT, SURF2)


def dots3(s, xr, cy, n_on=3, col=MINT):
    for k in range(3):
        x = xr - (3 - k) * 26
        if k < n_on:
            s.d.ellipse((x, cy - 9, x + 18, cy + 9), fill=col)
        else:
            s.d.ellipse((x, cy - 9, x + 18, cy + 9), outline=DIM, width=2)


def ring(s, cx, cy, r, width=18, frac=1.0):
    def fn(d, Z, w, h):
        d.ellipse((Z * 2, Z * 2, w * Z - Z * 2, h * Z - Z * 2), outline='#3A1C26', width=width * Z)
        d.arc((Z * 2, Z * 2, w * Z - Z * 2, h * Z - Z * 2), -90, -90 + 360 * frac, fill=RED, width=width * Z)
    s.aa((cx - r, cy - r, cx + r, cy + r), fn); s.log('graphic', '시간 고리', RED, EBG)


# ---------------------------------------------------------------- 세로 틀
PW, PH, PM = 840, 1212, 24
TABS = [('warning', '경고등 찾기', 'engine', AMBER), ('record', '증상 기록', 'history', MINT),
        ('guardian', '보호자 알림', 'users', PURPLE), ('service', '정비소', 'wrench', AMBER)]
PB_L, PB_R = (24, 1092, 408, 1188), (432, 1092, 816, 1188)


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


# ---------------------------------------------------------------- 세로 화면
def p_home(selected=False):
    s = p_frame('p-parts' if selected else 'p-home')
    x = 24
    for ic, t, col in (('park', '정차 중', MINT), ('bluetooth', '휴대폰 연결됨', MINT), ('users', '보호자 2명 준비', PURPLE)):
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
        s.txt(116, 1006, '출발 전 점검에서 자세히 볼 수 있어요', 22, MUT)
    else:
        s.box((24, 896, 816, 1072)); s.icon('tire', 48, 912, 36, AMBER); s.txt(96, 914, '앞 타이어 · 어떤 증상인가요', 24, b=True)
        w = (744 - 36) / 4
        for j, (ic, lab) in enumerate((('activity', '떨림'), ('tire', '공기압\n경고등'), ('triangle-alert', '파손\n의심'), ('circle-help', '모르겠음'))):
            x = 48 + j * (w + 12); on = j == 0
            s.box((x, 960, x + w, 1056), ON[AMBER] if on else SURF2, 18, AMBER if on else None, 3 if on else 2)
            s.icon(ic, x + 18, 990, 36, AMBER if on else FG); s.txt_vc(x + 64, 1008, lab, 22, AMBER if on else FG, True, maxw=w - 70)
    s.btn(PB_L, '지금 기록', 'rec', MINT, BG); s.btn(PB_R, '도움이 필요해요', 'lifebuoy', HELP, FG)
    s.save(PO)


def p_warning_find():
    s = p_frame('p-warning-find', 'warning', '경고등 찾기')
    s.box((24, 296, 816, 448)); s.num(48, 316, '1'); s.txt_vc(96, 334, '무슨 색인가요', 26, b=True)
    s.txt_r(792, 320, '색 → 모양 → 켜짐', 22, MUT); color_chips(s, 48, 368, 792)
    s.box((24, 464, 816, 872)); s.num(48, 484, '2'); s.txt_vc(96, 502, '어떤 모양인가요', 26, b=True)
    s.txt_r(792, 488, '계기판과 같은 모양', 22, MUT); warn_tiles(s, 48, 536, 792, 852)
    s.box((24, 888, 816, 1072)); s.num(48, 908, '3'); s.txt_vc(96, 926, '켜져 있나요, 깜박이나요', 26, b=True)
    state_chips(s, 48, 968, 792, h=80)
    s.btn(PB_L, '말로 찾기', 'mic', AMBER, BG); s.btn(PB_R, '문구로 찾기', 'search', SURF2, FG, stroke=LINE)
    s.save(PO)


def p_warning_result():
    s = p_frame('p-warning-result', 'warning', '후보 2개를 찾았어요')
    x = 24
    for t in ('주황', '타이어 공기압 모양', '계속 켜짐'):
        x += s.chip(x, 296, t, None, h=48, fill=SURF2, stroke=None) + 10
    s.txt_r(816, 306, '다시 고르기', 22, MINT, True)
    for j, (name, st, note, on) in enumerate((('타이어 공기압\n낮음', '계속 켜짐', '고른 조건과 같아요', True),
                                               ('공기압 장치\n점검', '깜박인 뒤 켜짐', '켜짐 방식이 달라요', False))):
        x0 = 24 + j * 408; r = (x0, 364, x0 + 384, 556)
        s.box(r, ON[AMBER] if on else SURF, 22, AMBER if on else LINE, 3 if on else 2)
        s.icon('tire', x0 + 20, 384, 64, AMBER)
        s.txt(x0 + 100, 380, name, 24, FG, True, maxw=230)
        if on:
            s.icon('circle-check', x0 + 332, 380, 32, AMBER)
        s.txt(x0 + 20, 470, st, 22, AMBER if on else MUT, True); s.txt(x0 + 20, 508, note, 22, MINT if on else MUT)
    s.box((24, 572, 816, 1072))
    s.txt(48, 592, '타이어 공기압 낮음', 30, b=True); s.badge(792, 594, '안내 예시 · 검토 전', right=True)
    s.txt(48, 646, '공식 설명 요약', 22, AMBER, True)
    s.txt(48, 682, '타이어 공기압이 많이 낮을 때 켜집니다.\n안전한 곳에 세운 뒤 타이어 상태를 확인하세요.\n공기압을 맞춘 뒤에도 켜져 있으면 점검을 받으세요.', 24, FG, maxw=744)
    s.txt(48, 806, '지금 계속 가도 되나요', 24, b=True)
    w = (744 - 24) / 3
    for j, lab in enumerate(('바로 세우기', '정비소까지', '다음 점검')):
        x0 = 48 + j * (w + 12); on = j == 1
        s.box((x0, 846, x0 + w, 914), AMBER if on else SURF2, 18, None)
        s.txt_c(x0 + w / 2, 880, lab, 24, BG if on else MUT, on)
    s.txt(48, 932, '속도를 줄이고 가까운 정비소에서 확인하세요', 22, FG)
    s.txt(48, 980, '출처: 아반떼 취급설명서 해당 절 (확인 전)\n차량 계기판 안내와 다르면 차량 안내를 따르세요', 22, MUT)
    s.btn(PB_L, '정비소 들르기', 'wrench', AMBER, BG); s.btn(PB_R, '정차 후 자세히', 'park', SURF2, FG, stroke=LINE)
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
    s.box((24, 296, 816, 784)); s.txt(48, 314, '차가 알려 준 점검 항목', 26, b=True)
    s.txt(48, 354, '경고등이 아니라 차가 보내는 점검 신호입니다', 22, MUT)
    check_grid(s, 48, 400, 792)
    s.box((24, 800, 816, 940)); s.txt(48, 818, '정비 일정 · 주행거리 기준', 24, b=True); s.txt_r(792, 818, '32,140km', 24, FG, True)
    s.d.rounded_rectangle((48, 866, 792, 882), radius=8, fill='#2E3F57'); s.d.rounded_rectangle((48, 866, 48 + 744 * .714, 882), radius=8, fill=MINT)
    s.log('graphic', '정비 일정 막대', MINT, SURF)
    s.txt(48, 896, '엔진오일 교환까지 2,860km 남았어요', 22, FG)
    s.box((24, 956, 816, 1072))
    for j, (ic, t, sub, col) in enumerate((('bluetooth', '휴대폰 연결됨', '11:58 확인', MINT), ('users', '보호자 2명 수신 확인', '시험 알림 받음', PURPLE))):
        x = 48 + j * 384; s.icon(ic, x, 980, 40, col); s.txt(x + 54, 974, t, 24, FG, True); s.txt(x + 54, 1014, sub, 22, MUT)
    s.btn(PB_L, '확인했어요', 'circle-check', MINT, BG); s.btn(PB_R, '정비소 들르기', 'wrench', SURF2, FG, stroke=LINE)
    s.save(PO)


def symptom_chips(s, x0, y0, x1, cols=3, h=68, gap=12, sel='떨림'):
    w = (x1 - x0 - gap * (cols - 1)) / cols
    for i, (ic, lab) in enumerate(SYMPTOMS):
        x = x0 + i % cols * (w + gap); y = y0 + i // cols * (h + gap)
        sel_chip(s, (x, y, x + w, y + h), lab, lab == sel, MINT, ic=ic)


def p_record():
    s = p_frame('p-record', 'record', '증상을 기록했어요')
    s.box((24, 296, 816, 744)); s.txt(48, 314, '차량 상태 스냅샷 시간표', 26, b=True)
    s.txt(48, 354, '21:00:12 기록 · 앞 20초와 뒤 10초', 22, MUT)
    timeline(s, (48, 404, 792, 700), label_w=230, row_h=70)
    s.box((24, 760, 816, 984)); s.txt(48, 776, '어떤 증상이었나요', 24, b=True); symptom_chips(s, 48, 820, 792)
    s.box((24, 1000, 816, 1072), SURF, 20, LINE); s.icon('lock', 48, 1020, 32, DIM)
    s.txt_vc(96, 1036, '원본 녹음 · 영상', 24, DIM, True); s.chip(792, 1014, '향후 지원', None, c=DIM, fill=SURF, stroke=DIM, h=44, right=True)
    s.btn(PB_L, '기록 끝내기', 'circle-check', MINT, BG); s.btn(PB_R, '말로 메모', 'mic', SURF2, FG, stroke=LINE)
    s.save(PO)


COND = [('circle-gauge', '속도', '시속 60~80km'), ('steering', '조향', '핸들을 돌리는 중'),
        ('pedal', '브레이크', '밟지 않음'), ('road', '주행거리', '32,000km 넘은 뒤부터')]


def cond_rows(s, x0, y0, x1, row_h=88, gap=12, band=(300, 520)):
    for i, (ic, lab, val) in enumerate(COND):
        y = y0 + i * (row_h + gap); s.box((x0, y, x1, y + row_h), SURF2, 18)
        s.icon(ic, x0 + 18, y + (row_h - 40) / 2, 40, MINT)
        s.txt(x0 + 74, y + 10, lab, 22, MUT); s.txt(x0 + 74, y + row_h - 44, val, 26, FG, True)
        if i == 0:
            speed_band(s, x0 + band[0], y + row_h / 2 - 8, x0 + band[1])
            s.txt_c(x0 + band[0] + 8, y + row_h / 2 + 22, '0', 22, MUT); s.txt_c(x0 + band[1] - 16, y + row_h / 2 + 22, '120', 22, MUT)
        s.txt_r(x1 - 18, y + 10, '3번 중 3번', 22, MUT); dots3(s, x1 - 12, y + row_h - 26)


def p_pattern():
    s = p_frame('p-pattern', 'record', '재현 조건')
    s.box((24, 296, 816, 500)); s.car(40, 316, 300)
    s.txt(364, 320, '앞 타이어 · 떨림', 30, b=True); s.txt(364, 372, '같은 증상 3번', 26, MINT, True)
    s.txt(364, 418, '11월 2일 · 7일 · 12일', 22, MUT)
    s.box((24, 516, 816, 1000)); s.txt(48, 534, '3번 모두 같았던 조건', 26, b=True)
    cond_rows(s, 48, 584, 792, row_h=92, gap  =  12)
    s.txt(24, 1024, '원본 녹음이나 진동 분석이 아니라 기록 순간의 차량 신호입니다', 22, MUT, maxw=792)
    s.btn(PB_L, '정비소에 보여 주기', 'qr', MINT, BG); s.btn(PB_R, '기록 목록', 'history', SURF2, FG, stroke=LINE)
    s.save(PO)


def emergency_portrait(name, line2, big, subtitle, left, right, hazard=False):
    s = Scr(name, (PW, PH), EBG)
    s.box((16, 16, 824, 128), EBAND, 22); s.icon('octagon-alert', 40, 44, 56, FG)
    s.txt(116, 30, '긴급 안내', 34, FG, True); s.txt(116, 80, line2, 26, FG, True); s.draft(800, 52, EDRAFT, EDRAFT)
    x = 24
    for ic, t in (('car-front', '주행 중'), ('volume-2', '음성 안내 중')):
        x += s.chip(x, 148, t, ic, EPINK, FG, ECARD, None) + 12
    s.txt(24, 226, big, 46, FG, True, maxw=792)
    s.paste_round(road_scene(792, 432, hazard), (24, 360, 816, 792), 24)
    s.box((24, 812, 816, 1016), ECARD, 22); s.icon('volume-2', 48, 834, 36, EPINK); s.txt_vc(96, 852, '음성 자막', 22, EPINK, True)
    s.txt(48, 892, subtitle, 30, FG, True, w='SemiBold', maxw=744)
    s.txt(48, 966, '출처: 아반떼 취급설명서 비상 절차 · 문장 확인 전 예시', 22, MUT, maxw=744)
    s.btn((24, 1040, 408, 1188), left[0], left[1], RED, BG, left[2], n=30)
    s.btn((432, 1040, 816, 1188), right[0], right[1], ENEU, FG, right[2], n=30)
    s.save(PO)


def p_countdown():
    s = Scr('p-countdown', (PW, PH), EBG)
    s.box((16, 16, 824, 128), EBAND, 22); s.icon('octagon-alert', 40, 44, 56, FG)
    s.txt(116, 30, '보호자 알림', 34, FG, True); s.txt(116, 80, '보내기 전 10초 확인', 26, FG, True); s.draft(800, 52, EDRAFT, EDRAFT)
    bb = s.txt(24, 150, '에어백 전개 신호를 받았습니다', 34, FG, True); s.badge(bb[2] + 14, 156)
    ring(s, 420, 362, 132); s.txt_c(420, 346, '10', 112, FG, True); s.txt_c(420, 424, '초 뒤 보냅니다', 26, MUT, True)
    s.box((24, 520, 816, 744), ECARD, 22); s.txt(48, 538, '받는 사람', 24, FG, True)
    for j, t in enumerate(('보호자 1', '보호자 2')):
        y = 584 + j * 76; s.box((40, y, 800, y + 68), '#3A1C26', 16)
        s.icon('users', 58, y + 14, 40, PURPLE); s.txt_vc(114, y + 34, t, 28, FG, True); s.txt_r(780, y + 20, '알림톡, 안 되면 문자', 22, MUT)
    s.box((24, 760, 816, 972), ECARD, 22); s.txt(48, 776, '보낼 내용', 24, FG, True)
    for j, (k, v) in enumerate((('사건', '에어백 전개 신호 · 21:00:03'), ('위치', '측정 시각 21:00:01'), ('차량', '탑승 2명 · 안전벨트 2명 · 문 닫힘'))):
        y = 822 + j * 48; s.txt(48, y, k, 22, MUT, True); s.txt(130, y - 2, v, 24, FG, maxw=660)
    s.txt(24, 990, '119에 자동 신고하지 않습니다. 필요하면 119에 직접 신고하세요', 22, MUT, maxw=792)
    s.btn((24, 1040, 408, 1188), '지금 보내기', 'send', RED, BG, '기다리지 않고 바로', n=30)
    s.btn((432, 1040, 816, 1188), '괜찮아요', 'circle-check', ENEU, FG, '보내지 않음', n=30)
    s.save(PO)


GUARD = [('보호자 1', 'check-check', '열람함 · 21:01', MINT), ('보호자 2', 'message-circle', '문자로 다시 보냄 · 21:00:43', AMBER)]
SNAP = [('seat', '탑승: 운전석 · 조수석', False), ('belt', '안전벨트: 2명 착용', True),
        ('airbag', '에어백: 운전석 전개', True), ('door', '도어: 운전석 열림 21:00:14', False)]


def guardian_rows(s, x0, y0, x1, h=80, gap=12, n=28):
    for j, (who, ic, st, col) in enumerate(GUARD):
        y = y0 + j * (h + gap); s.box((x0, y, x1, y + h), SURF2, 18)
        s.icon('users', x0 + 16, y + (h - 36) / 2, 36, PURPLE); s.txt_vc(x0 + 64, y + h / 2, who, n, FG, True)
        w = s.tw(st, 22, True); s.icon(ic, x1 - 16 - w - 40, y + (h - 30) / 2, 30, col); s.txt_vc(x1 - 16 - w, y + h / 2, st, 22, col, True)


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
    s.txt(48, 548, '상태는 접수됨 → 전달됨 → 열람함 순서로 바뀝니다', 22, MUT)
    s.box((24, 608, 816, 832)); s.paste_round(schematic_map(300, 192, 'g'), (40, 624, 340, 816), 16)
    s.d.line([(70, 790), (150, 740), (220, 752), (280, 690)], fill=PURPLE, width=6); s.icon('map-pin', 262, 646, 40, FG)
    s.box((52, 768, 166, 806), '#0B1828', 12); s.txt_c(109, 787, '지도 예시', 22, MUT)
    s.txt(364, 628, '위치 측정 시각', 22, MUT); s.txt(364, 662, '21:00:01', 34, FG, True)
    s.txt(364, 718, '차량 위치 · 휴대폰 위치는 따로 표시', 22, MUT)
    s.txt(364, 762, '보호자 2명에게 위치를 보내는 중', 22, PURPLE, True)
    s.box((24, 848, 816, 1072)); s.txt(48, 864, '사고 순간 차량 스냅샷', 24, b=True)
    seat_diagram(s, (52, 904, 172, 1060)); snap_rows(s, 204, 904, 42)
    s.btn(PB_L, '기록 보기', 'history', PURPLE, BG); s.btn(PB_R, '공유 중지', 'pin-off', SURF2, FG, stroke=LINE)
    s.save(PO)


def p_practice():
    s = p_frame('p-practice', None, '비상 절차 미리 익히기', ('lock', '정차 중에만 열립니다'))
    s.box((24, 296, 816, 404)); s.txt(48, 312, '단계 1/3', 24, MINT, True); s.txt_r(792, 314, '브레이크 → 비상등 → 비상 제동 듣기', 22, MUT)
    w = (744 - 24) / 3
    for j in range(3):
        x = 48 + j * (w + 12); s.d.rounded_rectangle((x, 362, x + w, 378), radius=8, fill=MINT if j == 0 else '#2E3F57')
    s.box((24, 420, 816, 866), SURF); s.asset(A / 'pedal-detail.png', (24, 420, 792, 446))
    s.box((24, 882, 816, 1072)); s.txt(48, 898, '브레이크를 밟아 보세요', 40, FG, True)
    s.icon('circle-check', 48, 962, 36, MINT); s.txt_vc(96, 980, '브레이크 신호를 받았어요', 26, MINT, True)
    s.txt(48, 1022, '그림: AI 설명 그림 · 문장: 취급설명서 확인 전 예시', 22, MUT, maxw=744)
    s.btn(PB_L, '다음 단계', 'next', MINT, BG); s.btn(PB_R, '그만하기', 'close', SURF2, FG, stroke=LINE)
    s.save(PO)


SHOPS = [('A', '정비소 A', '+6분', '도착 21:42'), ('B', '정비소 B', '+11분', '도착 21:47'), ('C', '정비소 C', '+15분', '도착 21:51')]


def service_map(s, rect, pins):
    x0, y0, x1, y1 = rect; w, h = x1 - x0, y1 - y0
    s.paste_round(schematic_map(w, h, 's'), rect, 22)
    P = lambda fx, fy: (x0 + fx * w, y0 + fy * h)
    route = [P(.12, .80), P(.30, .68), P(.52, .62), P(.70, .40), P(.86, .18)]
    s.d.line(route, fill=MINT, width=8, joint='curve'); s.log('graphic', '경로', MINT, '#112335')
    for (fx, fy), (rx, ry) in zip(pins, ((.30, .68), (.52, .62), (.70, .40))):
        a, b = P(fx, fy), P(rx, ry)
        for t in range(0, 10, 2):
            s.d.line([mix_pt(a, b, t / 10), mix_pt(a, b, (t + 1) / 10)], fill=AMBER, width=4)
    sx, sy = P(.12, .80); s.d.ellipse((sx - 14, sy - 14, sx + 14, sy + 14), fill=FG, outline=MINT, width=5)
    fx, fy = P(.86, .18); s.icon('flag', fx - 6, fy - 40, 40, MINT, check=False)
    for (px, py), (L, *_r) in zip(pins, SHOPS):
        x, y = P(px, py); on = L == 'A'
        s.d.ellipse((x - 22, y - 22, x + 22, y + 22), fill=AMBER if on else '#3A3220', outline=AMBER, width=3)
        s.txt_c(x, y, L, 24, BG if on else AMBER, True)
    lab = [('지금 위치', P(.12, .80), (18, 18)), ('목적지', P(.86, .18), (-150, -34))]
    for t, (x, y), (dx, dy) in lab:
        w2 = s.tw(t, 22, True) + 24; s.box((x + dx, y + dy, x + dx + w2, y + dy + 38), '#0B1828', 12); s.txt_c(x + dx + w2 / 2, y + dy + 19, t, 22, FG, True)
    s.box((x1 - 140, y1 - 54, x1 - 16, y1 - 16), '#0B1828', 12); s.txt_c(x1 - 78, y1 - 35, '지도 예시', 22, MUT)


def mix_pt(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def shop_rows(s, x0, y0, x1, h=72, gap=12, n=26):
    for j, (L, name, plus, eta) in enumerate(SHOPS):
        y = y0 + j * (h + gap); on = j == 0
        s.box((x0, y, x1, y + h), ON[AMBER] if on else SURF2, 18, AMBER if on else None, 3 if on else 2)
        s.d.ellipse((x0 + 16, y + h / 2 - 18, x0 + 52, y + h / 2 + 18), fill=AMBER if on else '#3A3220', outline=AMBER, width=3)
        s.txt_c(x0 + 34, y + h / 2, L, 22, BG if on else AMBER, True)
        s.txt_vc(x0 + 68, y + h / 2, name, n, FG, True)
        s.txt_vc(x0 + 68 + s.tw(name, n, True) + 18, y + h / 2, plus, 24, AMBER, True)
        bb = s.bbox(0, 0, eta, 22); s.txt_vc(x1 - 18 - (bb[2] - bb[0]), y + h / 2, eta, 22, MUT)


def p_service():
    s = p_frame('p-service', 'service', '가는 길에 정비소 들르기')
    service_map(s, (24, 296, 816, 712), [(.24, .48), (.62, .84), (.52, .26)])
    s.box((24, 728, 816, 812)); s.icon('flag', 48, 750, 40, MINT); s.txt_vc(100, 770, '목적지는 그대로 둡니다', 26, FG, True)
    s.txt_r(792, 756, '도착 21:36', 24, MUT)
    shop_rows(s, 24, 828, 816, 72)
    s.btn(PB_L, '경유지로 추가', 'map-pin', AMBER, BG); s.btn(PB_R, '취소', 'close', SURF2, FG, stroke=LINE)
    s.save(PO)


def p_manual():
    s = p_frame('p-manual', None, '상황 선택', ('circle-check', '2개 선택 · 최대 3개'))
    s.txt(24, 296, '급한 상황은 바로 긴급 안내로 갑니다', 22, MUT)
    w = (792 - 24) / 3
    for j, (ic, t) in enumerate((('octagon-alert', '안 멈춰요'), ('up', '저절로\n빨라져요'), ('crash', '사고가\n났어요'))):
        x = 24 + j * (w + 12); s.box((x, 336, x + w, 476), ECARD, 22, RED, 3)
        s.icon(ic, x + 20, 354, 44, RED); s.txt(x + 20, 406, t, 26, FG, True, maxw=w - 30)
    s.txt(24, 500, '차가 이상해요 · 기록만 하고 알림은 묻습니다', 22, MUT)
    items = [('activity', '차체 떨림', True), ('audio-lines', '이상한 소음', False), ('steering', '핸들 쏠림', False),
             ('flame', '타는 냄새', False), ('trend', '힘이 없어요', False), ('tire', '타이어 이상', True)]
    w2 = (792 - 16) / 2
    for i, (ic, t, on) in enumerate(items):
        x = 24 + i % 2 * (w2 + 16); y = 540 + i // 2 * (160 + 16)
        s.box((x, y, x + w2, y + 160), ON[AMBER] if on else SURF, 22, AMBER if on else None, 3 if on else 2)
        s.icon(ic, x + 28, y + 52, 56, AMBER if on else FG); s.txt_vc(x + 108, y + 80, t, 28, AMBER if on else FG, True)
    s.btn(PB_L, '고른 증상 기록', 'rec', AMBER, BG); s.btn(PB_R, '지금 도움 요청', 'lifebuoy', HELP, FG)
    s.save(PO)


def p_home_driving():
    """주행 중 홈(D13·C4-I02): 차 그림·탭·점검 카드 숨김, 상태 한 줄과 큰 버튼 3개만."""
    s = Scr('p-home-driving', (PW, PH)); s.logo(24, 16, 64); s.txt(104, 28, '플레오스 가드', 30, b=True); s.draft(816, 30)
    w = s.chip(24, 112, '주행 중', 'car-front', MINT); s.txt_vc(24 + w + 16, 138, '자세한 화면은 차를 세운 뒤 열립니다', 22, MUT)
    x = 24
    for ic, t, col in (('bluetooth', '휴대폰 연결됨', MINT), ('users', '보호자 2명 준비', PURPLE)):
        x += s.chip(x, 184, t, ic, col) + 12
    s.btn((24, 276, 816, 516), '지금 기록', 'rec', MINT, BG, '누른 순간의 차량 상태를 남겨요', n=36)
    s.btn((24, 540, 816, 780), '말로 기록', 'mic', SURF2, FG, '버튼을 누른 뒤 말해 주세요', stroke=LINE, n=36)
    s.btn((24, 804, 816, 1044), '도움이 필요해요', 'lifebuoy', HELP, FG, '상황을 고르거나 보호자에게 알려요', n=36)
    s.box((24, 1068, 816, 1148), SURF, 20); s.icon('lock', 48, 1092, 32, DIM)
    s.txt_vc(96, 1108, '경고등 설명 · 점검 항목 · 설정은 정차 후에 열립니다', 22, DIM, True)
    s.save(PO)


# ---------------------------------------------------------------- 가로 틀
LW, LH = 1280, 720
DOCK = [('home', '홈', 'home', MINT), ('warning', '경고등', 'engine', AMBER), ('record', '기록', 'history', MINT),
        ('guardian', '보호자', 'users', PURPLE), ('service', '정비소', 'wrench', AMBER)]
LB_L, LB_R = (160, 610, 696, 698), (720, 610, 1256, 698)


def l_frame(name, title, active='home', chip=('park', '정차 중')):
    s = Scr(name, (LW, LH)); s.logo(20, 16, 72); s.txt(108, 24, '플레오스 가드', 30, b=True); s.txt(370, 26, title, 28, b=True)
    dw = s.draft(1256, 30)
    if chip:
        s.chip(1256 - dw - 14, 24, chip[1], chip[0], MINT, right=True)
    s.box((16, 116, 136, 600), SURF, 26)
    for j, (key, lab, ic, col) in enumerate(DOCK):
        y = 132 + j * 91; on = key == active
        if on:
            s.box((24, y, 128, y + 82), ON[col], 18, col, 3)
        s.icon(ic, 59, y + 8, 34, col if on else MUT); s.txt_c(76, y + 60, lab, 22, col if on else MUT, on)
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
        s.icon('tire', 976, 148, 40, AMBER); s.txt_vc(1028, 168, '앞 타이어', 28, FG, True); s.txt(976, 206, '어떤 증상인가요', 22, MUT)
        for j, (ic, lab) in enumerate((('activity', '떨림'), ('tire', '공기압\n경고등'), ('triangle-alert', '파손\n의심'), ('circle-help', '모르겠음'))):
            xx = 972 + j % 2 * 144; yy = 248 + j // 2 * 164; on = j == 0
            s.box((xx, yy, xx + 120, yy + 148), ON[AMBER] if on else SURF2, 18, AMBER if on else None, 3 if on else 2)
            s.icon(ic, xx + 36, yy + 14, 48, AMBER if on else FG); s.txt_c(xx + 60, yy + 104, lab, 22, AMBER if on else FG, True)
    else:
        s.txt(976, 148, '지금 상태', 26, b=True)
        for j, (ic, t, col) in enumerate((('park', '정차 중', MINT), ('bluetooth', '휴대폰 연결됨', MINT), ('users', '보호자 2명 준비', PURPLE))):
            y = 200 + j * 58; s.icon(ic, 976, y, 32, col); s.txt_vc(1022, y + 16, t, 24, FG)
        s.d.line((976, 380, 1232, 380), fill=LINE, width=2)
        s.icon('washer', 976, 398, 32, AMBER); s.txt_vc(1022, 414, '워셔액 부족', 24, AMBER, True)
        s.badge(1022, 446); s.txt(976, 520, '출발 전 점검 보기 →', 22, MINT, True)
    s.btn(LB_L, '지금 기록', 'rec', MINT, BG); s.btn(LB_R, '도움이 필요해요', 'lifebuoy', HELP, FG)
    s.save(LA)


def l_warning():
    s = l_frame('ui-warning', '경고등 찾기', 'warning', None)
    s.chip(560, 24, '문구로 찾기', 'search', FG, h=56); s.chip(752, 24, '말로 찾기', 'mic', FG, h=56)
    s.box((160, 128, 820, 584))
    s.num(180, 162, '1'); s.txt_vc(224, 180, '색', 24, FG, True); color_chips(s, 284, 148, 796, h=64, gap=12)
    s.num(180, 232, '2'); s.txt_vc(224, 250, '모양', 24, FG, True); s.txt_r(796, 236, '계기판과 같은 모양을 고르세요', 22, MUT)
    warn_tiles(s, 180, 278, 796, 494, cols=4, gap=12, sym=48)
    s.num(180, 520, '3'); s.txt_vc(224, 538, '상태', 24, FG, True); state_chips(s, 284, 506, 796, h=64, gap=12)
    s.box((844, 128, 1256, 584)); s.txt(868, 144, '후보 2개', 24, b=True); s.badge(1232, 142, '안내 예시 · 검토 전', right=True)
    for j, (name, st, on) in enumerate((('타이어 공기압 낮음', '계속 켜짐', True), ('공기압 장치 점검', '깜박인 뒤 켜짐', False))):
        y = 186 + j * 84; s.box((860, y, 1240, y + 74), ON[AMBER] if on else SURF2, 16, AMBER if on else None, 3 if on else 2)
        s.icon('tire', 874, y + 14, 46, AMBER); s.txt(932, y + 6, name, 24, FG, True); s.txt(932, y + 40, st, 22, AMBER if on else MUT, on)
        if on:
            s.icon('circle-check', 1200, y + 22, 28, AMBER)
    s.txt(868, 358, '공기압이 많이 낮을 때 켜집니다.\n안전한 곳에 세운 뒤 확인하세요.\n맞춘 뒤에도 켜지면 점검받으세요.', 22, FG, maxw=364)
    s.txt(868, 470, '지금 계속 가도 되나요', 22, MUT, True)
    w = (364 - 16) / 3
    for j, lab in enumerate(('바로 세우기', '정비소까지', '다음 점검')):
        x0 = 868 + j * (w + 8); on = j == 1
        s.box((x0, 516, x0 + w, 562), AMBER if on else SURF2, 14); s.txt_c(x0 + w / 2, 539, lab, 22, BG if on else MUT, on)
    s.btn(LB_L, '정비소 들르기', 'wrench', AMBER, BG); s.btn(LB_R, '정차 후 자세히', 'park', SURF2, FG, stroke=LINE)
    s.save(LA)


def l_record():
    s = l_frame('ui-record', '증상을 기록했어요', 'record')
    s.box((160, 128, 880, 584)); s.txt(184, 144, '차량 상태 스냅샷 시간표', 24, b=True); s.txt_r(856, 148, '21:00:12 · 앞 20초와 뒤 10초', 22, MUT)
    timeline(s, (184, 202, 856, 530), label_w=230, row_h=74)
    s.box((904, 128, 1256, 584)); s.txt(928, 144, '어떤 증상이었나요', 24, b=True); symptom_chips(s, 920, 188, 1240, cols=2, h=62, gap=10)
    s.box((920, 410, 1240, 474), SURF, 16, LINE); s.icon('lock', 934, 428, 28, DIM); s.txt_vc(970, 442, '원본 녹음 · 영상', 22, DIM, True)
    s.chip(1228, 423, '향후 지원', None, c=DIM, fill=SURF, stroke=DIM, h=38, right=True)
    s.icon('mic', 928, 494, 28, MINT); s.txt(968, 488, '말로 메모', 22, MINT, True)
    s.txt(928, 526, '60km 넘으면 핸들이 떨려요', 22, FG, maxw=312)
    s.btn(LB_L, '기록 끝내기', 'circle-check', MINT, BG); s.btn(LB_R, '말로 메모', 'mic', SURF2, FG, stroke=LINE)
    s.save(LA)


def l_history():
    s = l_frame('ui-history', '재현 조건', 'record')
    s.box((160, 128, 536, 584)); s.car(184, 150, 328)
    s.txt(184, 356, '앞 타이어 · 떨림', 28, b=True); s.txt(184, 402, '같은 증상 3번', 24, MINT, True)
    s.txt(184, 444, '11월 2일 · 7일 · 12일', 22, MUT); s.txt(184, 496, '기록 순간의 차량 신호를\n묶은 자료입니다', 22, MUT)
    s.box((560, 128, 1256, 584)); s.txt(584, 144, '3번 모두 같았던 조건', 26, b=True)
    cond_rows(s, 576, 192, 1240, row_h=88, gap=8, band=(340, 500))
    s.btn(LB_L, '정비소에 보여 주기', 'qr', MINT, BG); s.btn(LB_R, '기록 목록', 'history', SURF2, FG, stroke=LINE)
    s.save(LA)


def emergency_landscape(name, title, big, subtitle, left, right, hazard=False):
    s = Scr(name, (LW, LH), EBG)
    s.box((16, 16, 1264, 104), EBAND, 20); s.icon('octagon-alert', 40, 32, 56, FG); s.txt_vc(116, 60, title, 34, FG, True)
    dw = s.draft(1240, 40, EDRAFT, EDRAFT); s.icon('volume-2', 1240 - dw - 190, 44, 32, FG); s.txt_vc(1240 - dw - 150, 60, '음성 안내 중', 22, FG, True)
    s.chip(40, 128, '주행 중', 'car-front', EPINK, FG, ECARD, None, h=48)
    s.txt(40, 196, big, 44, FG, True, maxw=600)
    s.box((40, 340, 628, 584), ECARD, 22); s.icon('volume-2', 64, 360, 32, EPINK); s.txt_vc(106, 376, '음성 자막', 22, EPINK, True)
    s.txt(64, 414, subtitle, 28, FG, True, w='SemiBold', maxw=540)
    s.txt(64, 530, '출처: 아반떼 취급설명서 · 확인 전 예시', 22, MUT, maxw=540)
    s.paste_round(road_scene(588, 456, hazard), (652, 128, 1240, 584), 24)
    s.btn((24, 608, 632, 708), left[0], left[1], RED, BG, left[2])
    s.btn((656, 608, 1256, 708), right[0], right[1], ENEU, FG, right[2])
    s.save(LA)


def l_countdown():
    s = Scr('ui-countdown', (LW, LH), EBG)
    s.box((16, 16, 1264, 104), EBAND, 20); s.icon('octagon-alert', 40, 32, 56, FG); s.txt_vc(116, 60, '보호자 알림 · 보내기 전 10초 확인', 34, FG, True)
    s.draft(1240, 40, EDRAFT, EDRAFT)
    s.txt(40, 128, '에어백 전개 신호를', 30, FG, True); bb = s.txt(40, 170, '받았습니다', 30, FG, True); s.badge(bb[2] + 14, 172)
    ring(s, 228, 372, 112, 16); s.txt_c(228, 356, '10', 96, FG, True); s.txt_c(228, 420, '초 뒤 보냅니다', 22, MUT, True)
    s.txt(40, 538, '119에 자동 신고하지 않습니다', 22, MUT)
    s.box((448, 128, 1256, 584), ECARD, 22); s.txt(472, 144, '받는 사람', 24, FG, True)
    for j, t in enumerate(('보호자 1', '보호자 2')):
        y = 184 + j * 76; s.box((464, y, 1240, y + 66), '#3A1C26', 16)
        s.icon('users', 480, y + 13, 40, PURPLE); s.txt_vc(534, y + 33, t, 26, FG, True); s.txt_r(1222, y + 20, '알림톡, 안 되면 문자', 22, MUT)
    s.txt(472, 350, '보낼 내용', 24, FG, True)
    for j, (k, v) in enumerate((('사건', '에어백 전개 신호 · 21:00:03'), ('위치', '측정 시각 21:00:01'), ('차량', '탑승 2명 · 안전벨트 2명 · 문 닫힘'))):
        y = 396 + j * 52; s.txt(472, y, k, 22, MUT, True); s.txt(552, y - 2, v, 24, FG)
    s.btn((24, 608, 632, 708), '지금 보내기', 'send', RED, BG, '기다리지 않고 바로')
    s.btn((656, 608, 1256, 708), '괜찮아요', 'circle-check', ENEU, FG, '보내지 않음')
    s.save(LA)


def l_guardian():
    s = l_frame('ui-guardian', '보호자 알림 상태', 'guardian')
    s.box((160, 128, 640, 584)); s.paste_round(schematic_map(448, 248, 'g'), (176, 144, 624, 392), 16)
    s.d.line([(220, 360), (330, 300), (430, 316), (540, 220)], fill=PURPLE, width=6); s.icon('map-pin', 522, 176, 40, FG)
    s.box((188, 340, 302, 378), '#0B1828', 12); s.txt_c(245, 359, '지도 예시', 22, MUT)
    s.txt(184, 408, '위치 측정 시각', 22, MUT); s.txt(184, 440, '21:00:01', 30, FG, True)
    s.txt(184, 494, '차량 위치 · 휴대폰 위치는 따로 표시', 22, MUT); s.txt(184, 534, '보호자 2명에게 위치를 보내는 중', 22, PURPLE, True)
    s.box((664, 128, 1256, 584)); s.txt(688, 144, '보호자별 상태', 24, b=True); s.txt_r(1232, 146, '21:00:13 보냄', 22, MUT)
    guardian_rows(s, 680, 184, 1240, 68, 10, 26)
    s.txt(688, 340, '사고 순간 차량 스냅샷', 24, b=True)
    seat_diagram(s, (692, 386, 782, 568)); snap_rows(s, 812, 390, 44)
    s.btn(LB_L, '기록 보기', 'history', PURPLE, BG); s.btn(LB_R, '공유 중지', 'pin-off', SURF2, FG, stroke=LINE)
    s.save(LA)


def l_manual():
    s = l_frame('ui-manual', '상황 선택', None, ('circle-check', '2개 선택 · 최대 3개'))
    w = (1096 - 48) / 3
    for j, (ic, t) in enumerate((('octagon-alert', '안 멈춰요'), ('up', '저절로 빨라져요'), ('crash', '사고가 났어요'))):
        x = 160 + j * (w + 24); s.box((x, 128, x + w, 248), ECARD, 22, RED, 3)
        s.icon(ic, x + 24, 160, 56, RED); s.txt_vc(x + 100, 188, t, 28, FG, True, maxw=w - 110)
    items = [('activity', '차체 떨림', True), ('audio-lines', '이상한 소음', False), ('steering', '핸들 쏠림', False),
             ('flame', '타는 냄새', False), ('trend', '힘이 없어요', False), ('tire', '타이어 이상', True)]
    for i, (ic, t, on) in enumerate(items):
        x = 160 + i % 3 * (w + 24); y = 268 + i // 3 * 136
        s.box((x, y, x + w, y + 120), ON[AMBER] if on else SURF, 22, AMBER if on else None, 3 if on else 2)
        s.icon(ic, x + 24, y + 32, 56, AMBER if on else FG); s.txt_vc(x + 100, y + 60, t, 28, AMBER if on else FG, True)
    s.btn(LB_L, '고른 증상 기록', 'rec', AMBER, BG); s.btn(LB_R, '지금 도움 요청', 'lifebuoy', HELP, FG)
    s.save(LA)


def l_check():
    s = l_frame('ui-check', '출발 전 점검', 'warning', ('lock', '정차 중에만'))
    s.box((160, 128, 820, 584)); s.txt(184, 144, '차가 알려 준 점검 항목', 24, b=True); s.txt(184, 182, '경고등이 아니라 차가 보내는 점검 신호입니다', 22, MUT)
    check_grid(s, 176, 226, 804, rows_h=108, gap=11, isz=36, n=24, ipad=14, igap=12)
    s.box((844, 128, 1256, 584)); s.txt(868, 144, '정비 일정', 24, b=True); s.txt_r(1232, 146, '32,140km', 24, FG, True)
    s.d.rounded_rectangle((868, 196, 1232, 210), radius=7, fill='#2E3F57'); s.d.rounded_rectangle((868, 196, 868 + 364 * .714, 210), radius=7, fill=MINT)
    s.log('graphic', '정비 일정 막대', MINT, SURF)
    s.txt(868, 226, '엔진오일 교환까지\n2,860km 남았어요', 22, FG)
    s.d.line((868, 316, 1232, 316), fill=LINE, width=2); s.txt(868, 334, '준비 상태', 24, b=True)
    for j, (ic, t, sub, col) in enumerate((('bluetooth', '휴대폰 연결됨', '11:58 확인', MINT), ('users', '보호자 2명 수신 확인', '시험 알림 받음', PURPLE))):
        y = 384 + j * 92; s.icon(ic, 868, y + 4, 36, col); s.txt(916, y, t, 24, FG, True); s.txt(916, y + 38, sub, 22, MUT)
    s.btn(LB_L, '확인했어요', 'circle-check', MINT, BG); s.btn(LB_R, '정비소 들르기', 'wrench', SURF2, FG, stroke=LINE)
    s.save(LA)


def l_practice():
    s = l_frame('ui-practice', '비상 절차 미리 익히기', None, ('lock', '정차 중에만 열립니다'))
    s.box((160, 128, 800, 584)); s.asset(A / 'pedal-detail.png', (168, 136, 624, 352))
    s.txt(184, 504, '단계 1/3', 24, MINT, True); s.txt_r(784, 506, '브레이크 → 비상등 → 비상 제동 듣기', 22, MUT)
    w = (600 - 24) / 3
    for j in range(3):
        x = 184 + j * (w + 12); s.d.rounded_rectangle((x, 548, x + w, 562), radius=7, fill=MINT if j == 0 else '#2E3F57')
    s.box((824, 128, 1256, 584)); s.txt(848, 150, '브레이크를\n밟아 보세요', 36, FG, True)
    s.icon('circle-check', 848, 268, 36, MINT); s.txt_vc(896, 286, '브레이크 신호를 받았어요', 24, MINT, True)
    s.txt(848, 340, '차가 멈춰 있을 때만 연습합니다.\n주행 중에는 음성과 정지 그림만\n보여 줍니다.', 22, FG, maxw=392)
    s.txt(848, 500, '그림: AI 설명 그림\n문장: 취급설명서 확인 전 예시', 22, MUT)
    s.btn(LB_L, '다음 단계', 'next', MINT, BG); s.btn(LB_R, '그만하기', 'close', SURF2, FG, stroke=LINE)
    s.save(LA)


def l_service():
    s = l_frame('ui-service', '가는 길에 정비소 들르기', 'service')
    service_map(s, (160, 128, 760, 584), [(.24, .48), (.62, .84), (.50, .26)])
    s.box((784, 128, 1256, 584)); s.icon('flag', 804, 146, 36, MINT); s.txt_vc(852, 164, '목적지는 그대로', 24, FG, True); s.txt_r(1236, 152, '도착 21:36', 22, MUT)
    shop_rows(s, 800, 204, 1240, 88, 12, 24)
    s.txt(808, 512, '정비소 목록은 앱 안 자료입니다 (예시)', 22, MUT)
    s.btn(LB_L, '경유지로 추가', 'map-pin', AMBER, BG); s.btn(LB_R, '취소', 'close', SURF2, FG, stroke=LINE)
    s.save(LA)


def l_home_driving():
    s = Scr('ui-home-driving', (LW, LH)); s.logo(20, 16, 72); s.txt(108, 24, '플레오스 가드', 30, b=True); s.draft(1256, 30)
    w = s.chip(24, 120, '주행 중', 'car-front', MINT); s.txt_vc(24 + w + 16, 146, '자세한 화면은 차를 세운 뒤 열립니다', 22, MUT)
    x = 1256
    for ic, t, col in (('users', '보호자 2명 준비', PURPLE), ('bluetooth', '휴대폰 연결됨', MINT)):
        x -= s.chip(x, 120, t, ic, col, right=True) + 12
    s.btn((24, 196, 416, 580), '지금 기록', 'rec', MINT, BG, '그 순간 차량 상태 저장', n=32)
    s.btn((440, 196, 832, 580), '말로 기록', 'mic', SURF2, FG, '누른 뒤 말하기', stroke=LINE, n=32)
    s.btn((856, 196, 1256, 580), '도움이 필요해요', 'lifebuoy', HELP, FG, '상황 선택 · 보호자 알림', n=32)
    s.box((24, 604, 1256, 676), SURF, 20); s.icon('lock', 48, 624, 32, DIM)
    s.txt_vc(96, 640, '경고등 설명 · 점검 항목 · 설정은 정차 후에 열립니다', 22, DIM, True)
    s.save(LA)


# ---------------------------------------------------------------- 실행
def main():
    PO.mkdir(parents=True, exist_ok=True); LA.mkdir(parents=True, exist_ok=True)
    p_home(); p_home(True); p_home_driving(); p_warning_find(); p_warning_result(); p_check(); p_record(); p_pattern()
    emergency_portrait('p-emergency', '원하지 않는 가속', '앞을 보세요\n음성 안내를 따라 주세요',
                       '취급설명서 비상 절차를 음성으로 안내합니다',
                       ('멈추지 않아요', 'octagon-alert', '감속 실패 대처'), ('괜찮아요', 'circle-check', '안내 끝내기'))
    emergency_portrait('p-stopping', '감속 실패 대처', '차를 세우는 방법을\n음성으로 안내합니다',
                       '취급설명서의 비상 제동 절차를 읽습니다',
                       ('도움 요청', 'send', '보호자에게 알리기'), ('차가 멈췄어요', 'circle-check', '안내 끝내기'), hazard=True)
    p_countdown(); p_guardian(); p_practice(); p_service(); p_manual()
    l_home(); l_home(True); l_home_driving(); l_warning(); l_record(); l_history()
    emergency_landscape('ui-guidance', '긴급 안내 · 원하지 않는 가속', '앞을 보세요\n음성 안내를 따라 주세요',
                        '취급설명서 비상 절차를\n음성으로 안내합니다',
                        ('멈추지 않아요', 'octagon-alert', '감속 실패 대처'), ('괜찮아요', 'circle-check', '안내 끝내기'))
    emergency_landscape('ui-stopping', '긴급 안내 · 감속 실패 대처', '차를 세우는 방법을\n음성으로 안내합니다',
                        '취급설명서의 비상 제동 절차를\n음성으로 읽어 드립니다',
                        ('도움 요청', 'send', '보호자에게 알리기'), ('차가 멈췄어요', 'circle-check', '안내 끝내기'), hazard=True)
    l_countdown(); l_guardian(); l_manual(); l_check(); l_practice(); l_service()
    pairs = {}
    for scr, r in REPORT.items():
        for c in r['checks']:
            k = (c['fg'], c['bg']); p = pairs.setdefault(k, dict(fg=c['fg'], bg=c['bg'], ratio=c['ratio'], count=0, screens=set()))
            p['count'] += 1; p['screens'].add(scr)
    summary = dict(rule=dict(min_text_px=MIN_FONT, min_contrast=MIN_CONTRAST, min_button_px=MIN_BTN, max_lines=3),
                   screens={k: {kk: vv for kk, vv in v.items() if kk != 'checks'} for k, v in REPORT.items()},
                   min_ratio=min(v['min_ratio'] for v in REPORT.values()),
                   min_text_px=min(v['min_text_px'] for v in REPORT.values()),
                   pairs=sorted([dict(p, screens=sorted(p['screens'])) for p in pairs.values()], key=lambda p: p['ratio']),
                   details={k: v['checks'] for k, v in REPORT.items()})
    (OUT / 'contrast-check.json').write_text(json.dumps(summary, ensure_ascii=False, indent=1), encoding='utf-8')
    print(f'화면 {len(REPORT)}장, 최소 대비 {summary["min_ratio"]}, 최소 글자 {summary["min_text_px"]}px')


if __name__ == '__main__':
    main()
