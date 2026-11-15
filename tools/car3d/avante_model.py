"""
플레오스 슈퍼 케어 데모용 '디 올 뉴 아반떼'(8세대, CN8) 3D 모델 생성기 (Blender 4.5 LTS, bpy).

공식 CAD나 스캔 자료가 아닙니다. 공개 사진(옆모습, 앞 사선, 뒤 사선)을 설계도처럼 보고 치수와 선의 위치만 읽어
코드로 새로 만든 형상입니다. 사진을 모델에 붙이거나 투영하지 않습니다. 차체의 재질은 모두 수치로만 정한 PBR
(기본색 · 금속성 · 거칠기 · 클리어코트 · 발광)이고, 그림 텍스처는 바닥 그림자 한 장뿐입니다.
전장 4,765 · 전폭 1,855 · 전고 1,425 · 축거 2,750 mm(공개 제원)에 맞췄습니다(앞 번호판 자리는 전장에 넣지 않았습니다).

6회(2026-11-15, 앞모습): 앞 범퍼 · 검은 마스크 · 전조등 · 모서리 통풍구 · 날개 · 가로 램프와 꺾인 끝 · 보닛 · 앞 펜더 코만 다시 만들었습니다.
공식 360° 그림 일곱 장과 공식 사진(ref/front_2026-11-15)에서 좌표를 읽었고, 문 · 뒤쪽 · 실내 · 바퀴는 5회와 같습니다.

만드는 방법
  1. 옆면 · 윗면 · 앞면 · 뒷면을 각각 촘촘한 격자 곡면으로 만듭니다(특징선: 문턱, 아래 꺾임선, 어깨선, 유리 아래 선, 지붕 옆 선).
  2. 곡면 위에 2차원 도형(부호 거리 함수)으로 경계를 정확히 잘라 문 · 유리 · 검은 띠 · 램프 자리를 나누고, 판 사이 틈은 얕은 V 홈으로 팝니다.
  3. 잘린 조각을 부위별 물체로 묶고, Blender에서 꼭짓점 합치기 · 매끈한 음영 · 꺾임선 · 넓이 가중 법선(Weighted Normal) · 모서리 깎기(Bevel)를
     적용한 뒤 glTF로 내보냅니다. 같은 입력이면 바이트까지 같은 파일이 나옵니다.

좌표계: Blender 기준 X = 앞, Y = 왼쪽, Z = 위 (ISO 8855 차량 좌표). 단위 m.
glTF로 내보내면 x = 앞, y = 위, z = 오른쪽입니다.

물체 이름 규칙(앱이 이름 앞부분으로 부위를 정합니다):
  Front_*  앞부분      Rear_*  뒤쪽       Wheel_F* / Well_F*  앞 타이어   Wheel_R* / Well_R*  뒤 타이어
  Door_* / DoorTrim_* / Mirror_*  문      Canopy / Int_* / Seat_* / Belt_* / Occupant_* / Airbag_*  실내
  CanopyGhost(실내 보기용 반투명 덮개), Shadow(바닥 그림자), Underbody(바닥)

실행(경로는 절대 경로로):
  blender -b --factory-startup -P avante_model.py -- <glb 출력 경로> [미리보기 폴더 또는 -] [앱 그림 폴더]
"""
import math
import os
import sys

import bmesh
import bpy
import numpy as np
from mathutils import Matrix, Vector

ARGS = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
HERE = os.path.dirname(os.path.abspath(__file__))
GLB_OUT = ARGS[0] if ARGS else os.path.join(HERE, "avante.glb")
PREVIEW_DIR = ARGS[1] if len(ARGS) > 1 and ARGS[1] != "-" else None
SNAP_DIR = ARGS[2] if len(ARGS) > 2 and ARGS[2] != "-" else None   # 앱의 미리 그린 그림(car3d_home.webp · car3d_interior.webp)
REF_DIR = os.path.join(HERE, "ref")                                  # 설계도로만 쓰는 사진 폴더(생성에는 읽지 않습니다)

# ------------------------------------------------------------------------------------------ 치수
XF, XR = 1.375, -1.375        # 앞뒤 차축 X (축거 2.75 m)
WZ = 0.327                    # 바퀴 중심 높이 = 타이어 반지름
TRACK = 0.800                 # 바퀴 중심 Y(반)
R_ARCH = 0.378                # 휠 아치 구멍 반지름
ZA = 0.335                    # 아치 중심 높이
X_NOSE, X_TAIL = 2.325, -2.440  # 차체 맨 앞과 맨 뒤(전장 4.765 m)
XS0, XS1 = 1.76, -2.24        # 옆면 격자의 앞 끝(보닛 앞 모서리)과 뒤 끝(꼬리 끝 모서리)
XA, XH, XRR, XD = 0.85, 0.27, -1.05, -1.98   # A 기둥 아래, 지붕 앞 모서리, 지붕 뒤 모서리, 뒷유리 아래 모서리
N_PT = (-2.012, 0.791, 0.924)  # 옆면 뒤 끝의 꺾인 점 = 세로 램프 위 바깥 모서리 (x, y, z)
T_PT = (-2.240, 0.630, 1.035)  # 오리 꼬리 끝 모서리 (x, y, z)


# ------------------------------------------------------------------------------------------ 곡선 보조
def pchip(xs, ys, x):
    """단조 3차 보간(PCHIP). xs 오름차순."""
    xs = np.asarray(xs, float); ys = np.asarray(ys, float); x = np.atleast_1d(np.asarray(x, float))
    h = np.diff(xs); d = np.diff(ys) / h
    n = len(xs); m = np.zeros(n)
    if n == 2:
        m[:] = d[0]
    else:
        for k in range(1, n - 1):
            if d[k - 1] * d[k] <= 0:
                m[k] = 0.0
            else:
                w1 = 2 * h[k] + h[k - 1]; w2 = h[k] + 2 * h[k - 1]
                m[k] = (w1 + w2) / (w1 / d[k - 1] + w2 / d[k])
        m[0] = d[0]; m[-1] = d[-1]
    i = np.clip(np.searchsorted(xs, x) - 1, 0, n - 2)
    t = np.clip((x - xs[i]) / h[i], -0.2, 1.2)
    h00 = 2 * t ** 3 - 3 * t ** 2 + 1; h10 = t ** 3 - 2 * t ** 2 + t
    h01 = -2 * t ** 3 + 3 * t ** 2; h11 = t ** 3 - t ** 2
    return h00 * ys[i] + h10 * h[i] * m[i] + h01 * ys[i + 1] + h11 * h[i] * m[i + 1]


class PC:
    """(x, 값) 점들을 지나는 단조 곡선. 호출하면 값을 돌려줍니다(스칼라 또는 배열)."""

    def __init__(self, pts, linear=False):
        pts = sorted(pts, key=lambda p: p[0])
        self.x = np.array([p[0] for p in pts], float); self.v = np.array([p[1] for p in pts], float)
        self.linear = linear

    def __call__(self, x):
        sc = np.isscalar(x)
        xx = np.clip(np.atleast_1d(np.asarray(x, float)), self.x[0], self.x[-1])
        r = np.interp(xx, self.x, self.v) if self.linear else pchip(self.x, self.v, xx)
        return float(r[0]) if sc else r


def smooth01(t):
    t = np.clip(t, 0.0, 1.0)
    return t * t * (3 - 2 * t)


def fillet_poly(P, R, counts, nf=2):
    """꺾은선 P(m, d)의 안쪽 꼭짓점을 반지름 R[i]로 둥글린 뒤 고정 개수로 표본합니다.
    counts[k] = k번째 직선 구간의 칸 수, 둥근 모서리는 nf칸. 반환 (점 배열, 각 점의 구간 번호: 직선은 k, 모서리는 -1-k)."""
    P = np.asarray(P, float); m = len(P)
    A = [P[i].copy() for i in range(m)]; B = [P[i].copy() for i in range(m)]
    for i in range(1, m - 1):
        if R[i - 1] <= 0:
            continue
        u1 = P[i] - P[i - 1]; l1 = float(np.linalg.norm(u1)); u2 = P[i + 1] - P[i]; l2 = float(np.linalg.norm(u2))
        if l1 < 1e-7 or l2 < 1e-7:
            continue
        u1 = u1 / l1; u2 = u2 / l2
        phi = math.acos(float(np.clip(np.dot(u1, u2), -1.0, 1.0)))
        t = R[i - 1] * math.tan(phi / 2)
        t = max(min(t, 0.45 * l1, 0.45 * l2), min(0.0015, 0.2 * l1, 0.2 * l2))
        A[i] = P[i] - u1 * t; B[i] = P[i] + u2 * t
    out = [P[0]]; seg = [0]
    for k in range(m - 1):
        s0 = B[k]; s1 = A[k + 1]
        for a in range(1, counts[k] + 1):
            out.append(s0 + (s1 - s0) * a / counts[k]); seg.append(k)
        if k + 1 < m - 1 and R[k] > 0:
            for a in range(1, nf + 1):
                tt = a / nf
                out.append((1 - tt) ** 2 * A[k + 1] + 2 * (1 - tt) * tt * P[k + 1] + tt ** 2 * B[k + 1]); seg.append(-1 - k)
    return np.array(out), np.array(seg)


def resample(P, n):
    """꺾은선을 호 길이 기준 n점으로."""
    P = np.asarray(P, float)
    s = np.concatenate([[0], np.cumsum(np.linalg.norm(np.diff(P, axis=0), axis=1))])
    t = np.linspace(0, s[-1], n)
    return np.stack([np.interp(t, s, P[:, k]) for k in range(P.shape[1])], axis=1)


def poly_curve(pts, n):
    """점들을 지나는 Catmull-Rom 곡선을 호 길이 기준 n점으로."""
    P = np.asarray([list(p) for p in pts], float)
    if len(P) == 2:
        return np.linspace(P[0], P[1], n)
    ext = np.vstack([2 * P[0] - P[1], P, 2 * P[-1] - P[-2]])
    dense = []
    for i in range(1, len(ext) - 2):
        p0, p1, p2, p3 = ext[i - 1], ext[i], ext[i + 1], ext[i + 2]
        for t in np.linspace(0, 1, 24, endpoint=False):
            t2, t3 = t * t, t * t * t
            dense.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3))
    dense.append(P[-1])
    out = resample(np.array(dense), n)
    out[0] = P[0]; out[-1] = P[-1]
    return out


# ------------------------------------------------------------------------------------------ 2차원 도형(부호 거리: 안쪽이 음수)
def sd_poly(Q, poly):
    """다각형까지의 부호 거리. Q (n,2), poly (m,2)."""
    Q = np.asarray(Q, float); poly = np.asarray(poly, float)
    d = np.full(len(Q), np.inf); inside = np.zeros(len(Q), bool)
    m = len(poly)
    for i in range(m):
        a = poly[i]; b = poly[(i + 1) % m]
        e = b - a; w = Q - a
        t = np.clip((w @ e) / max(float(e @ e), 1e-18), 0, 1)
        dd = np.linalg.norm(w - t[:, None] * e, axis=1)
        d = np.minimum(d, dd)
        c1 = (a[1] <= Q[:, 1]) & (b[1] > Q[:, 1]); c2 = (b[1] <= Q[:, 1]) & (a[1] > Q[:, 1])
        cr = e[0] * w[:, 1] - e[1] * w[:, 0]
        inside ^= (c1 & (cr > 0)) | (c2 & (cr < 0))
    return np.where(inside, -d, d)


def inset_poly(poly, r):
    """다각형을 안쪽으로 r만큼 줄입니다(단순 다각형, 모서리가 사라지지 않는 범위)."""
    P = np.asarray(poly, float); m = len(P)
    area = 0.5 * np.sum(P[:, 0] * np.roll(P[:, 1], -1) - np.roll(P[:, 0], -1) * P[:, 1])
    sgn = 1.0 if area > 0 else -1.0
    lines = []
    for i in range(m):
        a = P[i]; b = P[(i + 1) % m]; e = b - a; e = e / (np.linalg.norm(e) + 1e-18)
        nrm = np.array([-e[1], e[0]]) * sgn          # 안쪽 법선
        lines.append((a + nrm * r, e))
    out = []
    for i in range(m):
        p1, e1 = lines[i - 1]; p2, e2 = lines[i]
        den = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(den) < 1e-9:
            out.append(p2)
        else:
            t = ((p2[0] - p1[0]) * e2[1] - (p2[1] - p1[1]) * e2[0]) / den
            out.append(p1 + e1 * t)
    return np.array(out)


def sd_rpoly(Q, poly, r):
    """모서리를 반지름 r로 둥글린 다각형."""
    return sd_poly(Q, inset_poly(poly, r)) - r if r > 0 else sd_poly(Q, poly)


def sd_box(Q, c, half, r=0.0):
    Q = np.asarray(Q, float)
    q = np.abs(Q - np.asarray(c, float)) - (np.asarray(half, float) - r)
    return np.linalg.norm(np.maximum(q, 0), axis=1) + np.minimum(np.max(q, axis=1), 0) - r


def sd_polyline(Q, pts):
    """꺾은선까지의 거리(2차원 · 3차원 모두)."""
    Q = np.asarray(Q, float); pts = np.asarray(pts, float)
    d = np.full(len(Q), np.inf)
    for i in range(len(pts) - 1):
        a = pts[i]; e = pts[i + 1] - a; w = Q - a
        t = np.clip((w @ e) / max(float(e @ e), 1e-18), 0, 1)
        d = np.minimum(d, np.linalg.norm(w - t[:, None] * e, axis=1))
    return d


def XZ(V):
    return V[:, [0, 2]]


def YZ(V):
    return V[:, [1, 2]]


def XY(V):
    return V[:, [0, 1]]


# ------------------------------------------------------------------------------------------ 다각형 모음(자르기 · 밀어 넣기)
class Soup:
    """꼭짓점과 다각형(꼭짓점 번호 목록, 표지)을 모읍니다. 표지 = (물체 이름, 재질 이름).
    trim 은 부호 거리 함수가 0인 선을 따라 다각형을 정확히 잘라 안쪽 조각의 표지를 바꿉니다."""

    def __init__(self):
        self.V = []
        self.P = []

    def add_grid(self, S, label, label_fn=None, ref=(0.0, 0.0, 0.6)):
        """격자 (nu, nv, 3). 면 방향은 ref 점에서 멀어지는 쪽. label_fn(i, j) 가 있으면 칸마다 표지."""
        S = np.asarray(S, float); nu, nv = S.shape[:2]
        off = len(self.V)
        self.V.extend(S.reshape(-1, 3).tolist())
        # 넓은 칸 하나로 방향을 정합니다.
        best = None
        for i in range(nu - 1):
            for j in range(nv - 1):
                nrm = np.cross(S[i + 1, j] - S[i, j], S[i, j + 1] - S[i, j])
                a = float(np.linalg.norm(nrm))
                if best is None or a > best[0]:
                    best = (a, nrm, (S[i, j] + S[i + 1, j + 1]) / 2)
        flip = float(np.dot(best[1], best[2] - np.asarray(ref, float))) < 0
        for i in range(nu - 1):
            for j in range(nv - 1):
                lab = label if label_fn is None else label_fn(i, j)
                if lab is None:
                    continue
                a = off + i * nv + j; b = off + (i + 1) * nv + j; c = off + (i + 1) * nv + j + 1; d = off + i * nv + j + 1
                self.P.append([[a, d, c, b] if flip else [a, b, c, d], lab])
        return off

    def add_poly(self, pts, label):
        off = len(self.V)
        self.V.extend([list(map(float, p)) for p in pts])
        self.P.append([list(range(off, off + len(pts))), label])

    def weld(self, tol=2e-4):
        """같은 자리의 꼭짓점을 합치고 넓이가 없는 다각형을 버립니다."""
        V = np.asarray(self.V, float)
        keys = np.round(V / tol).astype(np.int64)
        _, first, inv = np.unique(keys, axis=0, return_index=True, return_inverse=True)
        inv = np.asarray(inv).ravel()
        self.V = V[first].tolist()
        newP = []
        for verts, lab in self.P:
            vv = []
            for v in verts:
                w = int(inv[v])
                if not vv or vv[-1] != w:
                    vv.append(w)
            if len(vv) > 1 and vv[0] == vv[-1]:
                vv.pop()
            if len(set(vv)) >= 3 and len(vv) == len(set(vv)):
                newP.append([vv, lab])
        self.P = newP

    def trim(self, sdf, relabel, only=None, iters=14, eps=2e-5):
        """sdf(V (n,3)) < 0 인 쪽을 relabel(옛 표지)로 바꿉니다. relabel 이 None 을 돌려주면 그 조각을 지웁니다.
        only(표지) 가 참인 다각형만 자릅니다. |sdf| < eps 인 꼭짓점은 경계 위의 점으로 봅니다."""
        V = np.asarray(self.V, float)
        g = sdf(V)
        sg = np.where(g < -eps, -1, np.where(g > eps, 1, 0))
        base = len(self.V)
        cache = {}; pend = []

        def cross(a, b):
            k = (a, b) if a < b else (b, a)
            i = cache.get(k)
            if i is None:
                i = base + len(pend); cache[k] = i; pend.append(k if g[k[0]] < 0 else (k[1], k[0]))
            return i

        newP = []; whole = []
        for verts, lab in self.P:
            if only is not None and not only(lab):
                whole.append([verts, lab]); continue
            ss = [int(sg[v]) for v in verts]
            if 1 not in ss:
                if -1 not in ss:
                    whole.append([verts, lab]); continue
                nl = relabel(lab)
                if nl is not None:
                    whole.append([verts, nl])
                continue
            if -1 not in ss:
                whole.append([verts, lab]); continue
            nz = [x for x in ss if x != 0]
            changes = sum(nz[i] != nz[(i + 1) % len(nz)] for i in range(len(nz)))
            n = len(verts)
            polys = [verts] if changes == 2 else [[verts[0], verts[i], verts[i + 1]] for i in range(1, n - 1)]
            nl = relabel(lab)
            for pv in polys:
                pin = []; pout = []
                m = len(pv)
                for i in range(m):
                    a = pv[i]; b = pv[(i + 1) % m]
                    sa = sg[a]; sb = sg[b]
                    if sa == 0:
                        pin.append(a); pout.append(a)
                    elif sa < 0:
                        pin.append(a)
                    else:
                        pout.append(a)
                    if sa * sb < 0:
                        c = cross(a, b); pin.append(c); pout.append(c)
                if len(pin) >= 3 and nl is not None and any(sg[v] < 0 for v in pv):
                    newP.append([pin, nl])
                if len(pout) >= 3 and any(sg[v] > 0 for v in pv):
                    newP.append([pout, lab])
        if pend:
            E = np.array(pend)
            A = V[E[:, 0]]; B = V[E[:, 1]]          # A 안쪽(g<0), B 바깥
            lo = np.zeros(len(E)); hi = np.ones(len(E))
            for _ in range(iters):
                tm = (lo + hi) / 2
                gm = sdf(A + (B - A) * tm[:, None])
                inside = gm < 0
                lo = np.where(inside, tm, lo); hi = np.where(inside, hi, tm)
            tm = (lo + hi) / 2
            self.V.extend((A + (B - A) * tm[:, None]).tolist())
            # 자르지 않은 이웃 다각형의 변 위에 새 점이 생겼으면 끼워 넣습니다(틈 방지).
            for item in whole:
                verts = item[0]; n = len(verts); out = []; hit = False
                for i in range(n):
                    a = verts[i]; b = verts[(i + 1) % n]
                    out.append(a)
                    c = cache.get((a, b) if a < b else (b, a))
                    if c is not None:
                        out.append(c); hit = True
                if hit:
                    item[0] = out
        self.P = whole + newP

    def presplit(self, proj, poly, only=None, where=None, radius=0.07):
        """다각형(proj 좌표)의 꼭짓점이 면 한가운데에 떨어지면 trim 이 그 모서리를 면 크기만큼 뭉갭니다.
        꼭짓점 둘레의 면을 두 변의 연장선으로 미리 나눠(표지는 그대로) 꼭짓점이 격자점이 되게 합니다. where(V) > 0 인 꼭짓점만."""
        poly = np.asarray(poly, float); m = len(poly)
        for k in range(m):
            c = poly[k]
            V = np.asarray(self.V, float)
            near = np.linalg.norm(proj(V) - c, axis=1) < radius
            if where is not None:
                near &= where(V) > 0
            hit = False
            for item in self.P:
                if (only is None or only(item[1])) and all(near[v] for v in item[0]):
                    item[1] = ("__near__",) + tuple(item[1]); hit = True
            if not hit:
                continue
            for other in (poly[k - 1], poly[(k + 1) % m]):
                e = other - c; nrm = np.array([-e[1], e[0]]) / (float(np.linalg.norm(e)) + 1e-18)
                self.trim(lambda V, c=c, nrm=nrm: (proj(V) - c) @ nrm, lambda l: l, lambda l: l[0] == "__near__")
            for item in self.P:
                if item[1][0] == "__near__":
                    item[1] = tuple(item[1][1:])

    def verts_of(self, pred):
        s = set()
        for verts, lab in self.P:
            if pred(lab):
                s.update(verts)
        return s

    def move(self, idx, vec):
        """idx 꼭짓점들을 vec(n,3 또는 3)만큼 옮깁니다."""
        idx = sorted(idx)
        if not idx:
            return
        V = np.asarray(self.V, float)
        V[idx] += np.asarray(vec, float)
        self.V = V.tolist()

    def positions(self, idx):
        return np.asarray(self.V, float)[sorted(idx)]

    def recess(self, sdf, wall, floor, width, depth, direction, only=None):
        """sdf<0 영역을 direction 쪽으로 depth 만큼 밀어 넣습니다(음수면 돋움). 가장자리 width 폭은 비스듬한 벽.
        wall · floor = 표지를 바꾸는 함수."""
        tmp = "__wall__"
        self.trim(sdf, lambda l: (l[0], tmp, l[1]) if wall(l) is not None else None, only)
        self.trim(lambda V: sdf(V) + width, lambda l: ("__floor__",) + tuple(l), lambda l: len(l) == 3 and l[1] == tmp)
        idx = self.verts_of(lambda l: l[0] == "__floor__")
        d = np.asarray(direction, float)
        self.move(idx, d * depth)
        for item in self.P:
            l = item[1]
            if l[0] == "__floor__":
                item[1] = floor((l[1], l[3]))
            elif len(l) == 3 and l[1] == tmp:
                item[1] = wall((l[0], l[2]))

    def groove(self, sdf, width, depth, direction, only=None, mat="Gap"):
        """sdf = 0 인 선을 따라 폭 width, 깊이 depth 의 V 홈을 팝니다(문 틈 · 판 사이 틈). 홈의 두 면은 양쪽 판에 나눠 붙습니다.
        가는 띠가 꼭짓점 사이로 빠져나가지 않도록 먼저 선(sdf = 0)에서 자르고, 양쪽으로 width/2 만큼씩 다시 자릅니다."""
        neg = "__neg__"; mark = "__g__"
        self.trim(sdf, lambda l: (l[0], neg, l[1]), only)
        self.trim(lambda V: -(sdf(V) + width / 2), lambda l: (l[0], mark + "in", l[2]), lambda l: len(l) == 3 and l[1] == neg)
        self.trim(lambda V: sdf(V) - width / 2, lambda l: (l[0], mark, l[1]), lambda l: len(l) == 2 and (only is None or only(l)))
        a = self.verts_of(lambda l: len(l) == 3 and l[1] == mark)
        b = self.verts_of(lambda l: len(l) == 3 and l[1] == mark + "in")
        mid = sorted(a & b)
        if mid:
            V = np.asarray(self.V, float)
            g = np.abs(sdf(V[mid]))
            mid = [i for i, gg in zip(mid, g) if gg < width * 0.2]
            self.move(mid, np.asarray(direction, float) * depth)
        for item in self.P:
            l = item[1]
            if len(l) == 3 and l[1] == neg:
                item[1] = (l[0], l[2])
            elif len(l) == 3 and l[1].startswith(mark):
                item[1] = (l[0], mat)

    def mirrored(self, obj_map=None):
        """왼쪽 반(y>=0)을 오른쪽으로 비춘 사본. obj_map(물체 이름) 으로 이름을 바꿉니다."""
        s = Soup()
        V = np.asarray(self.V, float).copy(); V[:, 1] *= -1
        s.V = V.tolist()
        for verts, lab in self.P:
            nl = (obj_map(lab[0]) if obj_map else lab[0],) + tuple(lab[1:])
            s.P.append([list(reversed(verts)), nl])
        return s

    def extend(self, other):
        off = len(self.V)
        self.V.extend(other.V)
        for verts, lab in other.P:
            self.P.append([[v + off for v in verts], lab])


# ------------------------------------------------------------------------------------------ Blender 보조
def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


MATS = {}


def mat(name, color, metal=0.0, rough=0.5, alpha=1.0, emit=None, emit_strength=1.0, coat=0.0, coat_rough=0.05,
        spec=0.5, double=False):
    """수치로만 정하는 PBR 재질(그림 텍스처 없음)."""
    if name in MATS:
        return MATS[name]
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1.0)
    b.inputs["Metallic"].default_value = metal
    b.inputs["Roughness"].default_value = rough
    b.inputs["Specular IOR Level"].default_value = spec
    if coat > 0:
        b.inputs["Coat Weight"].default_value = coat
        b.inputs["Coat Roughness"].default_value = coat_rough
    if emit is not None:
        b.inputs["Emission Color"].default_value = (*emit, 1.0)
        b.inputs["Emission Strength"].default_value = emit_strength
    if alpha < 1.0:
        b.inputs["Alpha"].default_value = alpha
        m.surface_render_method = "BLENDED"
    m.use_backface_culling = not double
    m.diffuse_color = (*color, alpha)
    MATS[name] = m
    return m


def mat_unlit_shadow(name, image):
    """KHR_materials_unlit 그림자: 검은색, 투명도는 그림의 알파. 차체가 아닌 바닥에만 쓰는 유일한 그림입니다."""
    m = bpy.data.materials.new(name); m.use_nodes = True
    nt = m.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    mix = nt.nodes.new("ShaderNodeMixShader")
    tr = nt.nodes.new("ShaderNodeBsdfTransparent")
    tex = nt.nodes.new("ShaderNodeTexImage"); tex.image = image
    nt.links.new(tex.outputs["Alpha"], mix.inputs["Fac"])
    nt.links.new(tr.outputs[0], mix.inputs[1])
    nt.links.new(tex.outputs["Color"], mix.inputs[2])
    nt.links.new(mix.outputs[0], out.inputs["Surface"])
    m.surface_render_method = "BLENDED"
    m.use_backface_culling = True
    MATS[name] = m
    return m


def link(o, parent=None):
    bpy.context.scene.collection.objects.link(o)
    if parent is not None:
        o.parent = parent
    return o


def obj_from(name, verts, faces, mats, face_mat=None, parent=None, smooth=True):
    me = bpy.data.meshes.new(name)
    me.from_pydata([tuple(map(float, v)) for v in verts], [], [tuple(f) for f in faces])
    for m in mats:
        me.materials.append(m)
    if face_mat is not None:
        for p, fm in zip(me.polygons, face_mat):
            p.material_index = fm
    me.validate(clean_customdata=False)
    for p in me.polygons:
        p.use_smooth = smooth
    o = bpy.data.objects.new(name, me)
    return link(o, parent)


def set_active(o):
    bpy.ops.object.select_all(action="DESELECT")
    bpy.context.view_layer.objects.active = o
    o.select_set(True)


def apply_mods(o):
    set_active(o)
    for m in list(o.modifiers):
        bpy.ops.object.modifier_apply(modifier=m.name)


def shade(o, angle=32.0, weighted=False):
    """매끈한 음영 + 각도가 큰 변은 꺾임선. weighted 면 넓은 면 쪽으로 법선을 맞춥니다(Weighted Normal)."""
    me = o.data
    for p in me.polygons:
        p.use_smooth = True
    me.set_sharp_from_angle(angle=math.radians(angle))
    if weighted:
        md = o.modifiers.new("wn", "WEIGHTED_NORMAL"); md.keep_sharp = True; md.weight = 60
        apply_mods(o)
    return o


def box(name, center, size, m, parent=None, bevel=0.0, segs=2, rot=None):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co = Vector((v.co.x * size[0], v.co.y * size[1], v.co.z * size[2]))
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    me.materials.append(m)
    o = link(bpy.data.objects.new(name, me))
    if bevel > 0:
        md = o.modifiers.new("bevel", "BEVEL"); md.width = bevel; md.segments = segs; md.limit_method = "NONE"
        apply_mods(o)
    for p in o.data.polygons:
        p.use_smooth = bevel > 0
    if rot is not None:
        o.rotation_euler = rot
    o.location = center
    if parent is not None:
        o.parent = parent
    return o


def sphere(name, center, scale, m, parent=None, rot=None, segs=(24, 14)):
    """위도 · 경도 구. 삼각형을 직접 만들어 내보낼 때마다 같은 결과가 나오게 합니다."""
    nu, nv = segs
    V = [(0.0, 0.0, 1.0)]
    for j in range(1, nv):
        th = math.pi * j / nv
        for i in range(nu):
            ph = 2 * math.pi * i / nu
            V.append((math.sin(th) * math.cos(ph), math.sin(th) * math.sin(ph), math.cos(th)))
    V.append((0.0, 0.0, -1.0))
    F = []
    ring = lambda j, i: 1 + (j - 1) * nu + (i % nu)
    for i in range(nu):
        F.append((0, ring(1, i), ring(1, i + 1)))
    for j in range(1, nv - 1):
        for i in range(nu):
            a, b_, c, d = ring(j, i), ring(j + 1, i), ring(j + 1, i + 1), ring(j, i + 1)
            F.append((a, b_, c)); F.append((a, c, d))
    last = len(V) - 1
    for i in range(nu):
        F.append((last, ring(nv - 1, i + 1), ring(nv - 1, i)))
    o = obj_from(name, V, F, [m], parent=parent, smooth=True)
    o.scale = scale; o.location = center
    if rot is not None:
        o.rotation_euler = rot
    return o


def fix_normals(o, inside_point=(0, 0, 0.62)):
    """면 방향을 inside_point에서 멀어지는 쪽으로 맞춥니다."""
    c = Vector(inside_point)
    mw = o.matrix_world.copy()
    bm = bmesh.new(); bm.from_mesh(o.data)
    bm.normal_update()
    for f in bm.faces:
        ctr = mw @ f.calc_center_median()
        nrm = mw.to_3x3() @ f.normal
        if nrm.dot(ctr - c) < 0:
            f.normal_flip()
    bm.to_mesh(o.data); bm.free()


def strip(name, pts, width, depth, m, normal=(1, 0, 0), parent=None, smooth=False, bevel=0.0):
    """점 열을 따라가는 납작한 띠. normal = 두께 방향(대략), width = 면 위에서의 폭."""
    P = np.asarray([list(p) for p in pts], float); n = len(P)
    nn = np.asarray(normal, float); nn /= np.linalg.norm(nn)
    V = []; F = []
    for i in range(n):
        t = P[min(i + 1, n - 1)] - P[max(i - 1, 0)]; t /= (np.linalg.norm(t) + 1e-12)
        nr = nn - np.dot(nn, t) * t; nr /= (np.linalg.norm(nr) + 1e-12)
        u = np.cross(nr, t)
        for su, sn in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            V.append(P[i] + u * su * width / 2 + nr * sn * depth / 2)
    for i in range(n - 1):
        for k in range(4):
            a = i * 4 + k; b = i * 4 + (k + 1) % 4; c = (i + 1) * 4 + (k + 1) % 4; d = (i + 1) * 4 + k
            F.append((a, b, c, d))
    F.append((3, 2, 1, 0)); F.append(((n - 1) * 4, (n - 1) * 4 + 1, (n - 1) * 4 + 2, (n - 1) * 4 + 3))
    o = obj_from(name, V, F, [m], parent=parent, smooth=smooth)
    fix_normals(o, inside_point=tuple(P.mean(axis=0)))
    if bevel > 0:
        md = o.modifiers.new("bevel", "BEVEL"); md.width = bevel; md.segments = 2; md.limit_method = "ANGLE"; md.angle_limit = math.radians(40)
        apply_mods(o)
        shade(o, 40)
    return o


def lathe(name, profile, m, segs=48, smooth=True):
    """(r, y) 단면을 Y축 둘레로 돌린 회전체(바퀴용). 원점 기준."""
    V = []; F = []
    n = len(profile)
    for s in range(segs):
        a = 2 * math.pi * s / segs
        for r, y in profile:
            V.append((r * math.cos(a), y, r * math.sin(a)))
    for s in range(segs):
        s2 = (s + 1) % segs
        for k in range(n - 1):
            F.append((s * n + k, s * n + k + 1, s2 * n + k + 1, s2 * n + k))
    return obj_from(name, V, F, [m], smooth=smooth)


def join(objs, name):
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.join()
    o = bpy.context.view_layer.objects.active
    o.name = name; o.data.name = name
    return o


def reparent_keep(child, parent):
    """child의 월드 위치를 유지하며 parent 아래로(메시를 옮겨 부모 역행렬 없이)."""
    bpy.context.view_layer.update()
    mw = child.matrix_world.copy()
    pinv = parent.matrix_world.inverted()
    local = pinv @ mw
    child.parent = parent
    child.matrix_parent_inverse = Matrix.Identity(4)
    child.matrix_basis = Matrix.Identity(4)
    child.data.transform(local)


def soup_objects(soup, M, root, angle=32.0, weighted=(), angles=None):
    """다각형 모음을 물체 이름별 메시로 만듭니다. 같은 자리의 꼭짓점을 합쳐 매끈한 법선이 이어지게 합니다."""
    V = np.asarray(soup.V, float)
    groups = {}
    for verts, lab in soup.P:
        groups.setdefault(lab[0], []).append((verts, lab[1]))
    out = {}
    for name, polys in groups.items():
        used = sorted({v for vs, _ in polys for v in vs}); remap = {v: i for i, v in enumerate(used)}
        mats = []; fm = []
        for _, mn in polys:
            if mn not in mats:
                mats.append(mn)
            fm.append(mats.index(mn))
        me = bpy.data.meshes.new(name)
        me.from_pydata(V[used].tolist(), [], [[remap[v] for v in vs] for vs, _ in polys])
        for mn in mats:
            me.materials.append(M[mn])
        for p, k in zip(me.polygons, fm):
            p.material_index = k
        me.validate(clean_customdata=False)
        o = link(bpy.data.objects.new(name, me), root)
        bm = bmesh.new(); bm.from_mesh(me)
        bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1.2e-4)
        bmesh.ops.dissolve_degenerate(bm, edges=bm.edges, dist=2e-5)
        bm.to_mesh(me); bm.free()
        shade(o, (angles or {}).get(name, angle), weighted=name in weighted)
        out[name] = o
    return out


# ------------------------------------------------------------------------------------------ 재질
def make_materials():
    paint = (0.240, 0.240, 0.232)       # 무광에 가까운 금속성 회색(사진의 회색 차, 선형값)
    return dict(
        Paint=mat("Paint", paint, metal=0.45, rough=0.52, coat=0.15, coat_rough=0.40, double=True),
        Black=mat("BlackGloss", (0.010, 0.011, 0.013), rough=0.10, coat=1.0, coat_rough=0.03, double=True),
        Trim=mat("TrimBlack", (0.017, 0.018, 0.020), rough=0.52, double=True),
        Grille=mat("Grille", (0.006, 0.006, 0.007), rough=0.80, spec=0.2, double=True),
        Gap=mat("Gap", (0.004, 0.004, 0.005), rough=0.9, spec=0.1, double=True),
        Chrome=mat("Chrome", (0.86, 0.87, 0.88), metal=1.0, rough=0.22),
        Glass=mat("Glass", (0.004, 0.005, 0.007), rough=0.04, alpha=0.95, spec=0.5),
        Ghost=mat("Ghost", (0.36, 0.55, 0.70), rough=0.2, alpha=0.14, spec=0.5),
        LampWhite=mat("LampWhite", (0.9, 0.95, 1.0), emit=(0.85, 0.93, 1.0), emit_strength=4.0),
        LampLens=mat("LampLens", (0.62, 0.68, 0.74), rough=0.25, emit=(0.80, 0.90, 1.0), emit_strength=1.6),
        LampHousing=mat("LampHousing", (0.016, 0.019, 0.024), rough=0.14, spec=0.5, coat=1.0, coat_rough=0.04, double=True),
        LampRed=mat("LampRed", (0.30, 0.0, 0.006), emit=(1.0, 0.012, 0.02), emit_strength=1.3),
        LampRedDim=mat("LampRedDim", (0.16, 0.0, 0.004), rough=0.2, emit=(1.0, 0.015, 0.03), emit_strength=0.5),
        Reflector=mat("Reflector", (0.38, 0.0, 0.02), rough=0.25),
        Tire=mat("Tire", (0.020, 0.020, 0.022), rough=0.88),
        RimSilver=mat("RimSilver", (0.86, 0.87, 0.88), metal=0.85, rough=0.26),
        RimDark=mat("RimDark", (0.016, 0.017, 0.019), metal=0.4, rough=0.30, double=True),
        BrakeDisc=mat("BrakeDisc", (0.20, 0.20, 0.21), metal=1.0, rough=0.45),
        Caliper=mat("Caliper", (0.10, 0.10, 0.11), metal=0.3, rough=0.4),
        Plate=mat("Plate", (0.78, 0.79, 0.78), rough=0.55),
        WheelWell=mat("WheelWell", (0.010, 0.010, 0.012), rough=0.92, double=True),
        Underbody=mat("Underbody", (0.018, 0.018, 0.020), rough=0.9, double=True),
        SeatFabric=mat("SeatFabric", (0.06, 0.063, 0.07), rough=0.82),
        DashSoft=mat("DashSoft", (0.035, 0.037, 0.042), rough=0.7),
        Carpet=mat("Carpet", (0.03, 0.03, 0.034), rough=0.95, double=True),
        DoorTrim=mat("DoorTrim", (0.07, 0.072, 0.08), rough=0.7, double=True),
        Belt=mat("Belt", (0.03, 0.03, 0.035), rough=0.6, double=True),
        Occupant=mat("Occupant", (0.47, 0.48, 0.95), rough=0.5),
        Airbag=mat("Airbag", (0.92, 0.92, 0.9), rough=0.6),
        Pedal=mat("Pedal", (0.25, 0.26, 0.27), metal=0.8, rough=0.35),
        Steering=mat("Steering", (0.03, 0.03, 0.033), rough=0.55),
        Display=mat("Display", (0.01, 0.02, 0.04), rough=0.2, emit=(0.03, 0.09, 0.16), emit_strength=1.4),
        DisplayUI=mat("DisplayUI", (0.2, 0.6, 0.6), rough=0.3, emit=(0.30, 0.85, 0.80), emit_strength=1.6),
    )


# ------------------------------------------------------------------------------------------ 차체 특징선(왼쪽, x의 함수)
# 값을 읽은 방법: 공개 사진 넉 장(진짜 옆모습, 앞 사선, 뒤 사선 두 장)에 카메라를 맞춘 뒤
#   · 옆모습에서 x · z 를 읽고(화소 → 차량 좌표),
#   · 두 사진에 함께 보이는 점(램프 모서리, 손잡이, 쪽창 꼭짓점, 번호판 모서리 등)을 두 시선의 교점으로 구해 폭(y)을 정했습니다.
# 카메라는 바퀴 허브 중심과 림 타원(지름 0.50 m), 번호판 폭(0.52 m)으로 검증했습니다. 사진은 치수를 읽는 데만 썼습니다.
YB = PC([(1.76, 0.906), (1.60, 0.904), (1.375, 0.898), (1.0, 0.892), (0.0, 0.893), (-1.0, 0.892), (-1.375, 0.895), (-1.7, 0.885),
         (-2.02, 0.862), (-2.126, 0.838), (-2.24, 0.820)])                                                  # 옆면 반폭(문 가운데 높이)
BULGE = PC([(1.76, 0.0), (-0.18, 0.002), (-0.6, 0.006), (-1.0, 0.012), (-1.2, 0.012), (-1.5, 0.0), (-2.24, 0.0)], linear=True)  # 뒷문 아래 볼록한 판
Z0 = PC([(1.76, 0.200), (1.70, 0.185), (1.0, 0.222), (-0.95, 0.228), (-1.76, 0.250), (-2.107, 0.327), (-2.24, 0.35)])   # 문턱 아래 끝
Z1 = PC([(1.76, 0.250), (1.0, 0.262), (0.935, 0.260), (-0.16, 0.285), (-0.85, 0.302), (-1.0, 0.31), (-1.76, 0.33), (-2.1, 0.40), (-2.24, 0.42)])  # 문 아래 선
Z3 = PC([(1.76, 0.56), (1.375, 0.56), (0.944, 0.46), (-0.18, 0.464), (-0.569, 0.552), (-0.842, 0.664), (-1.025, 0.743), (-1.2, 0.80),
         (-1.39, 0.817), (-1.55, 0.79), (-1.74, 0.66), (-2.0, 0.64), (-2.24, 0.66)])           # 아래 꺾임선(앞문은 수평, 뒷문에서 뒤 펜더로 올라감)
Z4 = PC([(1.76, 0.785), (1.72, 0.789), (1.575, 0.805), (1.40, 0.823), (1.18, 0.843), (0.94, 0.860), (0.80, 0.881), (0.649, 0.917), (0.3, 0.925),
         (-0.207, 0.932), (-1.03, 0.951), (-1.231, 0.936), (-1.551, 0.932), (-1.885, 0.928), (N_PT[0], N_PT[2]), (T_PT[0], T_PT[2])])   # 어깨선
Y4 = PC([(1.76, 0.863), (1.72, 0.863), (1.575, 0.870), (1.40, 0.876), (1.18, 0.878), (0.94, 0.876), (0.85, 0.872), (0.6, 0.876), (0.0, 0.880),
         (-1.04, 0.878), (-1.25, 0.862), (-1.44, 0.843), (-1.7, 0.830), (-1.915, 0.822), (N_PT[0], N_PT[1]), (T_PT[0], T_PT[1])])
Z5 = PC([(1.76, 0.749), (1.70, 0.785), (1.586, 0.824), (1.408, 0.851), (1.185, 0.882), (0.943, 0.910), (0.90, 0.925), (XA, 0.955), (0.536, 0.972),
         (0.151, 0.990), (-0.146, 1.007), (-0.585, 1.020), (-1.104, 1.037), (-1.157, 1.038), (-1.373, 1.050), (-1.72, 1.062), (XD, 1.060),
         (T_PT[0], T_PT[2])])                                                                   # 유리 아래 선 = 보닛 · 트렁크 옆 선
Y5 = PC([(1.76, 0.813), (1.6, 0.806), (1.2, 0.800), (0.95, 0.797), (XA, 0.795), (0.6, 0.794), (-0.235, 0.792), (-0.7, 0.772), (-1.156, 0.735),
         (-1.373, 0.715), (-1.72, 0.652), (XD, 0.637), (T_PT[0], T_PT[1])])
Y6 = PC([(XA, 0.795), (0.73, 0.760), (0.605, 0.720), (0.515, 0.690), (0.425, 0.660), (0.335, 0.630), (XH, 0.610), (0.14, 0.585), (0.0, 0.570),
         (-0.3, 0.552), (-0.7, 0.535), (-0.95, 0.526), (XRR, 0.525), (-1.21, 0.572), (-1.56, 0.612), (-1.85, 0.630), (XD, 0.637)])   # 지붕 옆 선(A 기둥 → 지붕 → 뒷유리 옆)
Z6 = PC([(XA, 0.955), (0.728, 1.081), (0.604, 1.160), (0.512, 1.223), (0.420, 1.274), (0.327, 1.318), (XH, 1.340), (0.14, 1.372), (0.0, 1.392),
         (-0.3, 1.408), (-0.7, 1.404), (-0.95, 1.384), (XRR, 1.362), (-1.184, 1.322), (-1.37, 1.258), (-1.559, 1.195), (-1.747, 1.136),
         (-1.842, 1.108), (XD, 1.060)])
# 가운데 선(y = 0)의 높이
# 보닛(6회): 공식 360° 그림의 옆모습 윤곽에서 다시 읽었습니다. 가운데 판이 5회보다 3~5 cm 높고, 앞 끝 선은 거의 수평입니다.
# 높이는 360° 그림(먼 카메라)과 공식 옆모습 사진(가까운 카메라)이 2 cm 쯤 다르게 읽혀, 360° 그림 값에서 1 cm 낮춘 가운데 값을 썼습니다.
ZC_HOOD = PC([(1.03, 0.992), (1.10, 0.986), (1.182, 0.978), (1.253, 0.972), (1.325, 0.965), (1.397, 0.955), (1.469, 0.947), (1.541, 0.936),
              (1.613, 0.924), (1.685, 0.912), (1.757, 0.899), (1.828, 0.883), (1.900, 0.868), (1.972, 0.850), (2.044, 0.830), (2.116, 0.805),
              (2.181, 0.777), (2.215, 0.762), (2.233, 0.748)])
X_COWL = 1.03                       # 보닛 뒤 끝(가운데). 그 뒤는 앞 유리 아래 선까지 내려가는 검은 카울 턱입니다
HOOD_CR = (0.489, 0.530)            # 보닛 꺾임선의 y (앞 끝, 뒤 끝): 가로 램프의 안쪽 끝에서 앞 유리 아래 양쪽으로
ZC_WS = PC([(0.30, 1.345), (0.399, 1.306), (0.498, 1.262), (0.597, 1.202), (0.696, 1.143), (0.845, 1.049), (1.00, 0.958)])
ZC_ROOF = PC([(-1.10, 1.372), (-0.898, 1.400), (-0.696, 1.418), (-0.318, 1.423), (-0.092, 1.414), (0.107, 1.394), (0.257, 1.362), (0.30, 1.345)])
ZC_BL = PC([(-2.08, 1.064), (-1.85, 1.128), (-1.56, 1.216), (-1.21, 1.338), (-1.10, 1.372)])
ZC_DECK = PC([(-2.335, 1.024), (-2.30, 1.026), (-2.20, 1.042), (-2.08, 1.064)])
XC_TOP = [2.233, 1.00, 0.30, -1.10, -2.08, -2.335]      # 구간 경계에서 가운데 선의 x (보닛 앞, 카울, 지붕 앞, 지붕 뒤, 뒷유리 아래, 꼬리 턱)
P_TOP = [2.4, 2.2, 2.0, 2.0, 2.0, 2.2]                   # 가로 단면의 볼록한 정도(지수)
Q_TOP = [2.2, 2.2, 2.0, 2.0, 2.0, 2.2]                   # 평면에서 가로 선이 휘는 정도(지수)
# 앞면 · 뒷면 가운데 단면 (z → x)
# 앞면(6회): 아래 검은 턱 → 차체색 날(0.262~0.298) → 검은 마스크(0.298~0.553) → 차체색 들보(~0.665). 그 위는 뒤로 누운 면과 검은 띠입니다.
ZF_BOT, ZF_BEAM = 0.215, 0.665
XCF = PC([(0.215, 2.195), (0.232, 2.278), (0.250, 2.320), (0.262, X_NOSE), (0.292, X_NOSE), (0.300, 2.318), (0.42, 2.316), (0.553, 2.316),
          (0.565, 2.321), (0.640, X_NOSE), (ZF_BEAM, 2.322)], linear=True)
XCR = PC([(0.29, -2.37), (0.33, -2.40), (0.40, -2.43), (0.46, X_TAIL), (0.58, X_TAIL), (0.62, -2.41), (0.65, -2.365), (0.66, -2.358), (0.757, -2.356),
          (0.83, -2.350), (0.853, -2.345), (0.915, -2.338), (0.966, -2.337), (1.024, -2.335)])
# 옆면 뒤 끝 모서리(세로 램프가 붙는 선): 높이 → x
XE_LOW = PC([(0.36, -2.180), (0.50, -2.165), (0.663, -2.126), (N_PT[2], N_PT[0])])
Y2R = PC([(0.29, 0.47), (0.60, 0.47), (0.68, 0.62), (1.03, 0.62)], linear=True)      # 뒷면에서 모서리 깎임이 시작하는 반폭
C2R = PC([(0.29, 0.012), (0.60, 0.012), (0.68, 0.100), (0.92, 0.118), (1.024, 0.085)], linear=True)   # 그 점이 가운데보다 앞으로 들어간 양
LAMP_IN_TOP = (-2.037, 0.745); LAMP_IN_BOT = (-2.149, 0.792)       # 세로 램프 안쪽 모서리 (x, y)
# ---- 앞모습(6회, 2026-11-15) ---------------------------------------------------------------------------------------
# 읽은 방법: 공식 360° 그림 일곱 장(0 · 12 · 24 · 36 · 48 · 60 · 90°)에 카메라 하나를 맞추고(바퀴 중심, 18인치 림 지름, 전고로 확인),
# 같은 점을 세 장 이상에서 읽어 시선이 만나는 곳을 (x, y, z) 로 삼았습니다(되비춤 오차 대부분 1.5화소 = 8 mm 이내).
# 사진은 좌표를 읽는 데만 썼습니다. 아래 값은 모두 차의 왼쪽(y > 0)입니다.
BAR_IN = (2.186, 0.489, 0.746)      # 보닛 앞 끝 선이 꺾이는 점 = 가로 램프의 안쪽 끝
HOOD_R = (1.868, 0.812, 0.764)      # 보닛 앞 끝 선이 모서리를 도는 점
KINK_TOP = (1.716, 0.853, 0.799)    # 꺾인 램프의 위 · 뒤 끝(어깨선 위)
# 앞면이 비스듬한 면으로 꺾이는 선 (높이, x, y): 날 → 날개 위 안쪽 → 전조등 아래 안쪽 → 전조등 위 안쪽
_C2 = [(0.215, 2.100, 0.600), (0.262, 2.232, 0.585), (0.300, 2.232, 0.585), (0.415, 2.122, 0.684), (0.560, 2.183, 0.619), (0.657, 2.216, 0.574)]
C2X = PC([(z, x) for z, x, _ in _C2], linear=True); C2Y = PC([(z, y) for z, _, y in _C2], linear=True)
# 모서리 능선(비스듬한 면과 펜더 코 옆면 사이) (높이, x, y): 날개 뒤 아래 → 날개 뒤 위 → 전조등 아래 바깥 → 전조등 위 바깥 → 꺾인 램프 아래 끝
_RG = [(0.215, 1.900, 0.845), (0.281, 1.927, 0.849), (0.417, 1.845, 0.869), (0.559, 1.941, 0.816), (0.661, 2.018, 0.778), (0.697, 1.983, 0.808)]
RGX = PC([(z, x) for z, x, _ in _RG], linear=True); RGY = PC([(z, y) for z, _, y in _RG], linear=True)
FR_N = (6, 14, 3, 18)               # 앞면 가로 줄의 칸 수: 펜더 코 옆면, 비스듬한 면, 마스크 덩어리의 옆벽, 앞면
MASK_EDGE = (2.232, 0.585)          # 검은 마스크 덩어리의 바깥 모서리(세로 선). 여기서 옆벽이 통풍구 쪽으로 물러납니다
LAMP_Q = [(2.216, 0.574, 0.655), (2.018, 0.778, 0.661), (1.941, 0.816, 0.559), (2.183, 0.619, 0.561)]   # 전조등: 위 안쪽, 위 바깥, 아래 바깥, 아래 안쪽
WING_Q = [(2.122, 0.684, 0.415), (2.232, 0.585, 0.300), (1.845, 0.869, 0.417), (1.927, 0.849, 0.281)]   # 날개: 위 안쪽, 아래 안쪽, 위 뒤, 아래 뒤
Z_MASK = (0.298, 0.553)             # 가운데 검은 마스크의 아래 · 위
Z_LIP = 0.275                       # 차체색 날의 아래 끝(그 아래는 검은 턱)
SLOT = ((0.0, 0.6335), (0.325, 0.0175))        # 들보의 가는 홈 (가운데 y · z, 반 크기)
PLATE = ((0.0, 0.513), (0.266, 0.060))         # 앞 번호판 자리(글자 없는 빈 판)
SENSOR_FN = (1.828, 0.881, 0.567)   # 펜더 코의 주차 센서
OB_A = math.radians(30.0)           # 앞 모서리 무늬를 자를 때 쓰는 비스듬한 방향(정면에서 왼쪽으로 30도)


def OB(V):
    """앞면 · 비스듬한 면 · 펜더 코를 한꺼번에 보는 방향(OB_A)의 평면 좌표 (가로, 높이). 세 면이 겹치지 않고 펼쳐집니다."""
    V = np.asarray(V, float).reshape(-1, 3)
    return np.stack([-math.sin(OB_A) * V[:, 0] + math.cos(OB_A) * V[:, 1], V[:, 2]], 1)


OB_IN = (-math.cos(OB_A), -math.sin(OB_A), 0.0)      # 그 방향으로 차 안쪽(밀어 넣어도 OB 좌표가 변하지 않습니다)

LOW_COUNTS = [2, 2, 7, 12, 6]; LOW_R = [0.012, 0.010, 0.035, 0.010]
NL = 1 + sum(LOW_COUNTS) + 2 * 4      # 아래 격자의 줄 수
JK1, JK3, JK4 = 6, 15, 30             # 문 아래 선, 아래 꺾임선, 어깨선(둥근 모서리의 가운데)의 줄 번호
JB_FRONT = 34                         # 앞면에서 검은 띠가 시작하는 줄(그 위가 띠)
NUP = 15
NV_TOP = 25


def lip_x(y):
    """꼬리 끝 턱(오리 꼬리)의 평면 모양: y → x."""
    return -2.335 + (T_PT[0] + 2.335) * (np.minimum(np.abs(y), T_PT[1]) / T_PT[1]) ** 2.2


_XT = np.linspace(T_PT[0], N_PT[0], 240); _ZT = Z4(_XT)       # 램프 위: 어깨선 위의 (z → x)


def xe_of_z(z):
    """옆면 뒤 끝 모서리의 x: 램프 위 끝 아래는 램프 선, 위는 어깨선을 따라 꼬리 끝으로."""
    z = np.asarray(z, float)
    up = np.interp(z, _ZT[::-1], _XT[::-1])
    return np.where(z <= N_PT[2], XE_LOW(np.minimum(z, N_PT[2])), up)


def stations():
    segs = [(XS0, XA, 31), (XA, XH, 20), (XH, XRR, 41), (XRR, XD, 31), (XD, XS1, 9)]
    xs = [XS0]; idx = [0]
    for a, b, n in segs:
        xs += list(np.linspace(a, b, n + 1)[1:]); idx.append(len(xs) - 1)
    return np.array(xs), idx          # idx = [0, iA, iH, iR, iD, 끝]


def low_section(x):
    """아래 차체의 단면(문턱 안쪽 → 유리 아래 선). (NL, 2) = (y, z)."""
    yb = YB(x); bl = BULGE(x); z0 = Z0(x)
    ctrl = [(yb - 0.14, z0 + 0.012), (yb + bl - 0.012, z0), (yb + bl, Z1(x)), (yb + 0.002, Z3(x)), (Y4(x), Z4(x)), (Y5(x), Z5(x))]
    P, seg = fillet_poly(ctrl, LOW_R, LOW_COUNTS)
    m = seg == 3
    t = (np.arange(m.sum()) + 1) / m.sum()
    P[m, 0] += 0.005 * np.sin(np.pi * t)            # 문 가운데가 살짝 볼록
    return P


def up_section(x):
    """유리 아래 선 → 유리 → 지붕 옆 선. (NUP, 2) = (y, z)."""
    a5 = np.array([Y5(x), Z5(x)]); k6 = np.array([Y6(x), Z6(x)])
    d = k6 - a5; L = float(np.linalg.norm(d))
    if L < 1e-6:
        return np.repeat(a5[None, :], NUP, 0)
    nrm = np.array([d[1], -d[0]]) / L
    t = np.linspace(0, 1, NUP)
    return a5[None, :] + d[None, :] * t[:, None] + nrm[None, :] * (0.012 * np.sin(np.pi * t) ** 0.8)[:, None]


class Body:
    """차체 곡면(왼쪽 반)을 만듭니다."""

    def __init__(self):
        self.xs, self.idx = stations()
        xs = self.xs; iA, iH, iR, iD, iE = self.idx[1:6]
        self.iA, self.iH, self.iR, self.iD, self.iE = iA, iH, iR, iD, iE
        low = np.array([low_section(x) for x in xs])
        self.S_low = np.stack([np.repeat(xs[:, None], NL, 1), low[..., 0], low[..., 1]], -1)
        self.S_low[-1, JK4:] = (T_PT[0], T_PT[1], T_PT[2])            # 꼬리 끝 모서리에서 어깨선과 트렁크 옆 선이 한 점으로 모입니다
        xu = xs[iA:iD + 1]
        up = np.array([up_section(x) for x in xu])
        self.S_up = np.stack([np.repeat(xu[:, None], NUP, 1), up[..., 0], up[..., 1]], -1)
        self.S_front = self.front_grid()
        self.S_top = self.top_grid()
        self.S_rear = self.rear_grid()

    # ---- 옆면에서 (x, z) → y
    def side_y(self, x, z):
        if x > XS0:
            # 앞 휠 아치 앞쪽(펜더 코 옆면): 옆면 맨 앞 줄에서 모서리 능선까지 곧게 안으로 좁아집니다.
            P = low_section(XS0)
            y0 = float(np.interp(z, P[1:JK4 + 1, 1], P[1:JK4 + 1, 0]))
            xr, yr = RGX(z), RGY(z)
            return y0 + (yr - y0) * min((float(x) - XS0) / max(xr - XS0, 1e-6), 1.0)
        P = low_section(float(x))
        return float(np.interp(z, P[1:JK4 + 1, 1], P[1:JK4 + 1, 0]))

    # ---- 앞면: 옆면 맨 앞 줄에서 가운데 선까지 감싸는 격자. (줄, 칸, 3) 아래 → 위
    def front_row(self, pJ, pR, p2, xc, zs, block=False):
        """앞면의 가로 줄 하나. 평면에서 이음 점 pJ → 모서리 능선 pR → 앞면이 꺾이는 점 p2 → (옆벽) → 가운데 (xc, 0).
        pJ–pR(펜더 코 옆면)과 pR–p2(비스듬한 면)는 곧은 선이라 각진 면이 되고, 앞면은 살짝 휩니다. zs = 네 점의 높이.
        block 이면 검은 마스크 높이의 줄입니다: 앞면이 MASK_EDGE 까지 나와 있고, 거기서 p2 까지는 곧은 옆벽입니다."""
        pJ = np.asarray(pJ, float); pR = np.asarray(pR, float); p2 = np.asarray(p2, float)
        a = np.linspace(pJ, pR, FR_N[0] + 1)
        b = np.linspace(pR, p2, FR_N[1] + 1)[1:]
        if block and float(np.hypot(MASK_EDGE[0] - p2[0], MASK_EDGE[1] - p2[1])) > 0.004:
            p3 = np.array(MASK_EDGE); ref = p3
        else:
            ref = p2; y3 = 0.86 * p2[1]
            p3 = np.array([xc - (xc - p2[0]) * (y3 / max(p2[1], 1e-6)) ** 2.2, y3])
        w = np.linspace(p2, p3, FR_N[2] + 1)[1:]
        ys = np.linspace(p3[1], 0.0, FR_N[3] + 1)[1:]
        c = np.stack([xc - (xc - ref[0]) * (ys / max(ref[1], 1e-6)) ** 2.2, ys], 1)
        P = np.vstack([a, b, w, c])
        i1, i2 = FR_N[0], FR_N[0] + FR_N[1]
        s = np.concatenate([[0], np.cumsum(np.linalg.norm(np.diff(P, axis=0), axis=1))])
        z = np.interp(s, [0.0, s[i1], s[i2], s[-1]], zs)
        return np.stack([P[:, 0], P[:, 1], z], 1)

    def front_grid(self):
        col = self.S_low[0]
        z_bot = col[1, 2]; zk4 = col[JK4, 2]
        rows = []
        for j in range(JK4 + 1):
            pJ = col[j]
            f = float(np.clip((pJ[2] - z_bot) / (zk4 - z_bot), 0, 1))
            zc = ZF_BOT + f * (ZF_BEAM - ZF_BOT)
            if j == 0:                                # 문턱 안쪽 줄: 바닥 밑으로 접혀 들어갑니다
                xc = XCF(zc) - 0.10
                rows.append(self.front_row((pJ[0], pJ[1]), (1.82, pJ[1] - 0.022), (xc - 0.10, 0.52), xc, [pJ[2], pJ[2], pJ[2], zc]))
                continue
            # 줄의 높이: 옆면에서 모서리 능선까지 내려오고(어깨선 줄은 꺾인 램프를 따라 0.785 → 0.697), 앞면에서는 거의 수평
            zr = pJ[2] + (zc - pJ[2]) * 0.72; z2 = zc + (pJ[2] - zc) * 0.07
            rows.append(self.front_row((pJ[0], pJ[1]), (RGX(zr), RGY(zr)), (C2X(z2), C2Y(z2)), XCF(zc), [pJ[2], zr, z2, zc],
                                       block=Z_MASK[0] + 0.004 < zc < Z_MASK[1] + 0.002))
        # 맨 위 줄 = 보닛 앞 끝 선(거의 수평, 모서리 쪽이 조금 높습니다)
        pJ = col[-1]
        zc0 = ZC_HOOD(XC_TOP[0])
        top = self.front_row((pJ[0], pJ[1]), HOOD_R[:2], BAR_IN[:2], XC_TOP[0], [pJ[2], HOOD_R[2], BAR_IN[2], zc0])
        # 검은 띠의 아래 선: 보닛 앞 끝 선 아래 2.1 cm, 바깥으로 1.6 cm(옆면 끝에서는 폭이 0)
        tan = np.gradient(top[:, :2], axis=0); tan /= (np.linalg.norm(tan, axis=1)[:, None] + 1e-12)
        s_top = np.concatenate([[0], np.cumsum(np.linalg.norm(np.diff(top, axis=0), axis=1))])
        g = smooth01(s_top / 0.10)
        band = top.copy()
        band[:, 0] += -tan[:, 1] * 0.016 * g; band[:, 1] += tan[:, 0] * 0.016 * g; band[:, 2] -= 0.021 * g
        k4 = rows[-1]
        fade = np.clip(1.0 - np.arange(k4.shape[0]) / float(FR_N[0]), 0.0, 1.0) ** 2      # 옆면 이음 줄에 맞추는 양(능선에서 0)
        nw = JB_FRONT - JK4                         # 어깨선 줄에서 띠 아래 선까지의 줄 수
        for j in range(JK4 + 1, NL):
            if j <= JB_FRONT:
                b = (j - JK4) / nw; r = (1 - b) * k4 + b * band
            else:
                b = (j - JB_FRONT) / (NL - 1 - JB_FRONT); r = (1 - b) * band + b * top
            r = r + (col[j] - r[0])[None, :] * fade[:, None]
            rows.append(r)
        return np.array(rows)

    # ---- 윗면(보닛 · 앞유리 · 지붕 · 뒷유리 · 트렁크): 가로 갈비 격자. (갈비, NV, 3)
    def top_grid(self):
        b = [0, self.iA, self.iH, self.iR, self.iD, self.iE]
        zfun = [ZC_HOOD, ZC_WS, ZC_ROOF, ZC_BL, ZC_DECK]
        t = np.linspace(0, 1, NV_TOP)
        nose = self.S_front[-1][::-1]                 # 가운데 → 모서리
        y_c = nose[-1, 1]
        k_front = (np.interp(t * y_c, nose[:, 1], nose[:, 0]) - nose[0, 0]) / (nose[-1, 0] - nose[0, 0])
        ribs = []; self.xc_rib = []
        for i in range(self.iE + 1):
            if i < self.iA:
                rib, xc = self.hood_rib(i, t, nose)
                ribs.append(rib); self.xc_rib.append(xc)
                continue
            zone = max(k for k in range(5) if i >= b[k])
            u = (i - b[zone]) / (b[zone + 1] - b[zone])
            xc = XC_TOP[zone] + (XC_TOP[zone + 1] - XC_TOP[zone]) * u
            zc = zfun[zone](xc)
            p = P_TOP[zone] + (P_TOP[zone + 1] - P_TOP[zone]) * u; q = Q_TOP[zone] + (Q_TOP[zone + 1] - Q_TOP[zone]) * u
            e = self.S_low[i, -1] if (i <= self.iA or i >= self.iD) else self.S_up[i - self.iA, -1]
            k = t ** q
            if i < 12:
                w = smooth01(i / 12.0); k = (1 - w) * k_front + w * k
            rib = np.stack([xc + (e[0] - xc) * k, e[1] * t, e[2] + (zc - e[2]) * (1 - t ** p)], 1)
            ribs.append(rib)
            self.xc_rib.append(xc)
        return np.array(ribs)

    def hood_rib(self, i, t, nose):
        """보닛의 가로 갈비 하나(가운데 → 옆 끝)와 가운데 x 를 돌려줍니다(6회).
        가운데 판은 거의 평평하고, 꺾임선(가로 램프 안쪽 끝에서 앞 유리 아래 양쪽으로 가는 두 줄) 바깥은 3 cm 폭의 턱을 지나
        높이를 거의 유지하다가 펜더 끝에서 내려갑니다(각진 어깨).
        맨 앞 갈비는 앞면의 맨 위 줄(보닛 앞 끝 선)과 같은 선입니다. 맨 뒤 갈비(i = iA - 1)는 카울 위에 떠 있는 보닛 뒤 끝입니다."""
        iA = self.iA
        e = self.S_low[i, -1]
        u = i / (iA - 1.0)
        xc = XC_TOP[0] + (X_COWL - XC_TOP[0]) * u
        zc = ZC_HOOD(xc)
        ycr = HOOD_CR[0] + (HOOD_CR[1] - HOOD_CR[0]) * u
        n = NV_TOP - 1
        ja = int(round(ycr / e[1] * n))
        w0 = 1.0 - smooth01(i / 8.0)                               # 앞 끝에서는 바깥 둘째 칸을 보닛 앞 끝 선의 모서리 점에 둡니다
        y_pen = e[1] * ((n - 1.0) / n + w0 * (HOOD_R[1] / nose[-1, 1] - (n - 1.0) / n))
        y = np.interp(t, [0.0, ja / n, (ja + 1.0) / n, (n - 1.0) / n, 1.0], [0.0, ycr, ycr + 0.03, y_pen, e[1]])
        tau = y / e[1]
        # 평면 모양: 앞 끝은 앞면의 맨 위 줄을 따르고, 뒤로 갈수록 곧은 가로 줄
        y_c = nose[-1, 1]
        k_front = (np.interp(tau * y_c, nose[:, 1], nose[:, 0]) - nose[0, 0]) / (nose[-1, 0] - nose[0, 0])
        k_front[-1] = 1.0
        q = Q_TOP[0] + (Q_TOP[1] - Q_TOP[0]) * u
        wk = smooth01(i / 12.0)
        k = (1 - wk) * k_front + wk * tau ** q
        # 높이: 가운데 판(가운데가 6 mm 높음) → 턱(3 cm 폭, 1.2 cm) → 펜더 쪽으로 볼록하게 내려감
        z_in = zc - 0.006 * (np.minimum(y, ycr) / ycr) ** 2
        z1 = zc - 0.006 - 0.012
        s_out = np.clip((y - ycr - 0.03) / np.maximum(e[1] - ycr - 0.03, 1e-6), 0, 1)
        z_plat = np.where(y <= ycr, z_in, np.where(y <= ycr + 0.03, zc - 0.006 - 0.012 * (y - ycr) / 0.03, z1 + (e[2] - z1) * s_out ** 3.0))
        z_edge = np.interp(tau * y_c, nose[:, 1], nose[:, 2]) + (zc - nose[0, 2]) * (1 - tau ** 2) + (e[2] - nose[-1, 2]) * tau ** 4
        z_edge[-1] = e[2]
        wz = smooth01(i / 7.0)
        z = (1 - wz) * z_edge + wz * z_plat
        return np.stack([xc + (e[0] - xc) * k, y, z], 1), xc

    # ---- 뒷면: 가운데 선에서 옆면 뒤 끝 모서리까지. (줄, 칸, 3), 아래 → 위
    def rear_grid(self):
        n_up = 8
        ze = list(np.linspace(0.36, 0.663, 19)) + list(np.linspace(0.663, N_PT[2], 17)[1:])     # 램프 아래 끝(0.663)과 위 끝에 줄을 맞춥니다
        zc = list(np.interp(ze, [0.36, 0.663, N_PT[2]], [0.29, 0.660, 0.915]))
        xe = [float(XE_LOW(z)) for z in ze]; ye = [self.side_y(x, z) for x, z in zip(xe, ze)]
        for k in range(1, n_up + 1):                  # 램프 위: 어깨선을 따라 꼬리 끝 모서리로
            u = k / n_up
            x = N_PT[0] + (T_PT[0] - N_PT[0]) * u
            xe.append(x); ye.append(float(Y4(x))); ze.append(float(Z4(x))); zc.append(0.915 + (1.024 - 0.915) * u)
        ye[-1] = T_PT[1]; ze[-1] = T_PT[2]
        na, nb, nc = 16, 5, 3
        rows = []
        for k, (x_e, y_e, z_e, z_c) in enumerate(zip(xe, ye, ze, zc)):
            xc = float(XCR(z_c)); y2 = float(Y2R(z_c)); c2 = float(C2R(z_c))
            if k == len(xe) - 1:                      # 맨 위 줄 = 꼬리 끝 턱
                ys = np.linspace(0, T_PT[1], na + nb + nc + 1)
                P = np.stack([lip_x(ys), ys], 1)
            else:
                ys = np.linspace(0, y2, na + 1)
                face = np.stack([xc + c2 * (ys / y2) ** 2, ys], 1)
                p2 = face[-1]; e = np.array([x_e, y_e])
                if 0.663 - 1e-9 <= z_e <= N_PT[2] + 1e-9:
                    u = (z_e - 0.663) / (N_PT[2] - 0.663)
                    p3 = np.array(LAMP_IN_BOT) + (np.array(LAMP_IN_TOP) - np.array(LAMP_IN_BOT)) * u
                else:
                    p3 = p2 + (e - p2) * 0.62
                P = np.vstack([face, np.linspace(p2, p3, nb + 1)[1:], np.linspace(p3, e, nc + 1)[1:]])
            z = z_c + (z_e - z_c) * np.clip(P[:, 1] / y_e, 0, 1) ** 2
            rows.append(np.stack([P[:, 0], P[:, 1], z], 1))
        self.rear_cols = (na, nb, nc)
        return np.array(rows)


# ---- 옆모습 (x, z) 에서 쓰는 선과 도형 --------------------------------------------------
DOOR_F_FRONT = [(0.944, 0.10), (0.944, 0.60), (0.900, 0.84), (0.852, 0.950), (0.840, 1.00)]     # 앞문 앞 끝(아래 → 위)
DOOR_MID = [(-0.180, 0.10), (-0.211, 0.936), (-0.238, 1.019), (-0.245, 1.06)]                      # 앞문과 뒷문 사이
DOOR_R_REAR = [(-0.930, 0.10), (-0.938, 0.39), (-0.971, 0.549), (-1.050, 0.652), (-1.187, 0.742), (-1.220, 0.844), (-1.207, 0.969), (-1.157, 1.040), (-1.14, 1.07)]  # 뒷문 뒤 끝
CLAD_TOP = PC([(0.944, 0.312), (0.728, 0.382), (-0.161, 0.405), (-0.727, 0.422), (-0.870, 0.366), (-0.985, 0.365)], linear=True)
DLO = [(0.74, 0.966), (0.604, 1.096), (0.513, 1.155), (0.420, 1.209), (0.327, 1.250), (0.141, 1.297), (-0.188, 1.321), (-0.613, 1.321), (-0.994, 1.317),
       (-1.061, 1.280), (-1.157, 1.040)]                                                               # 옆 창 테두리(검은 틀 포함), 아래 변은 유리 아래 선
PANE_F = [(0.545, 0.992), (0.52, 1.105), (0.43, 1.172), (0.33, 1.225), (0.141, 1.275), (-0.188, 1.299), (-0.222, 1.270), (-0.150, 1.008)]
PANE_R = [(-0.352, 1.012), (-0.384, 1.270), (-0.40, 1.299), (-0.613, 1.299), (-0.99, 1.295), (-1.028, 1.270), (-1.012, 1.036)]
QGLASS = [(-1.213, 1.253), (-1.720, 1.067), (-1.373, 1.058)]
FUEL = ((-1.438, 0.895), (0.123, 0.077))            # 주유구 뚜껑(가운데, 반 크기)
HANDLE_F = ((-0.036, 0.822), 11.1); HANDLE_R = ((-1.043, 0.857), 12.8)   # 손잡이 가운데와 기울기(도)
FLARE_F = (0.450, 0.490); FLARE_R = (0.487, 0.490)    # 휠 아치 테의 바깥 반지름(앞쪽 · 위, 뒤쪽)


def x_of_z(line):
    zz = np.array([p[1] for p in line]); xx = np.array([p[0] for p in line])
    return lambda z: np.interp(z, zz, xx)


def flare_rout(dx, dz, spec):
    """휠 아치 테의 바깥 반지름: 바퀴 중심에서 본 방향마다."""
    a = np.arctan2(dz, dx)                     # 0 = 앞쪽, pi/2 = 위, pi = 뒤쪽
    w = np.clip(a / np.pi, 0, 1)
    return spec[0] + (spec[1] - spec[0]) * smooth01((w - 0.45) / 0.55)


def sd_flare(V, xc, spec):
    dx = V[:, 0] - xc; dz = V[:, 2] - ZA
    return np.hypot(dx, dz) - flare_rout(dx, dz, spec)


# ------------------------------------------------------------------------------------------ 겉면 조립(자르기)
def to_obj(name):
    return lambda l: (name, l[1])


def to_mat(name):
    return lambda l: (l[0], name)


def is_obj(*names):
    return lambda l: l[0] in names


def is_lab(obj, mat):
    return lambda l: l[0] == obj and l[1] == mat


IN_L = (0.0, -1.0, 0.0)        # 왼쪽 옆면에서 차 안쪽 방향


def build_skin(B):
    """왼쪽 반의 겉면을 만들어 자른 뒤 좌우를 합친 Soup 을 돌려줍니다."""
    s = Soup()
    xs = B.xs; iA, iH, iR, iD, iE = B.iA, B.iH, B.iR, B.iD, B.iE
    na, nb, nc = B.rear_cols

    # ================= 아래 옆면 + 앞면 + 뒷면
    def low_label(i, j):
        if j <= JK1:
            return ("Body", "Rocker")
        return ("Shelf", "Paint") if j >= JK4 else ("Body", "Paint")

    def rear_label(i, j):
        z0 = B.S_rear[i, -1, 2]; z1 = B.S_rear[i + 1, -1, 2]
        if j >= na + nb and z0 > 0.663 - 1e-6 and z1 < N_PT[2] + 1e-6:
            return ("Rear_Body", "LampRed")
        return ("Rear_Body", "Paint")

    s.add_grid(B.S_low, None, label_fn=low_label)
    s.add_grid(B.S_front, None, label_fn=lambda i, j: ("Front_Body", "Black" if i >= JB_FRONT else "Paint"))   # 보닛 끝 아래 검은 띠
    s.add_grid(B.S_rear, None, label_fn=rear_label)
    s.weld()
    s.trim(lambda V: V[:, 0] - xe_of_z(V[:, 2]), lambda l: None, is_obj("Body"))            # 옆면 뒤 끝 모서리 뒤쪽을 버림
    for xc, spec in ((XF, FLARE_F), (XR, FLARE_R)):                                          # 휠 아치(테 자리까지 비움)
        s.trim(lambda V, xc=xc, spec=spec: sd_flare(V, xc, spec), lambda l: None, is_obj("Body", "Front_Body"))
    xf, xm, xr = x_of_z(DOOR_F_FRONT), x_of_z(DOOR_MID), x_of_z(DOOR_R_REAR)
    rocker = lambda l: l[1] == "Rocker"
    s.trim(lambda V: 1.0 - V[:, 0], lambda l: ("Front_Body", "Trim"), rocker)
    s.trim(lambda V: V[:, 0] + 1.0, lambda l: ("Rear_Body", "Trim"), rocker)
    for it in s.P:
        if it[1][1] == "Rocker":
            it[1] = ("Underbody", "Trim")
    side = is_obj("Body", "Shelf")
    s.trim(lambda V: xf(V[:, 2]) - V[:, 0], to_obj("Front_Body"), side)
    s.trim(lambda V: V[:, 0] - xr(V[:, 2]), to_obj("Rear_Body"), side)
    s.trim(lambda V: xm(V[:, 2]) - V[:, 0], to_obj("Door_FL"), side)
    for it in s.P:
        if it[1][0] in ("Body", "Shelf"):
            it[1] = ("Door_RL", it[1][1])
    doors = is_obj("Door_FL", "Door_RL")
    # 창 아래 검은 띠(문 위 끝): 유리 아래 선에서 2.6 cm
    belt = B.S_low[:, -1]
    belt = belt[(belt[:, 0] <= 0.80) & (belt[:, 0] >= -1.157)]
    s.trim(lambda V: sd_polyline(V, belt) - 0.026, to_mat("Black"), doors)
    s.trim(lambda V: V[:, 2] - CLAD_TOP(V[:, 0]), to_mat("Trim"), lambda l: doors(l) and l[1] == "Paint")     # 문 아래 검은 덧판
    zb = PC([(-1.60, 0.52), (-1.711, 0.549), (-2.017, 0.637), (-2.143, 0.658), (-2.30, 0.662)], linear=True)
    s.trim(lambda V: np.maximum(V[:, 2] - zb(V[:, 0]), V[:, 0] + 1.62), to_mat("Trim"), is_lab("Rear_Body", "Paint"))   # 뒤 범퍼 옆 검은 부분
    # 앞 범퍼 옆 아래 검은 덧판(펜더 코 옆면에서 휠 아치 앞까지). 앞 끝은 날개의 뒤 끝 선(모서리 능선)입니다.
    fb = np.array([(1.60, 0.10), (1.60, 0.435), (1.757, 0.452), (1.895, 0.478), WING_Q[2][::2], WING_Q[3][::2], (1.99, 0.10)])
    s.trim(lambda V: np.maximum(sd_poly(XZ(V), fb), 0.80 - V[:, 1]), to_mat("Trim"), is_lab("Front_Body", "Paint"))

    # ================= 앞면 무늬(6회): 검은 마스크 · 전조등 · 모서리 통풍구 · 날개 · 날 · 홈
    FWD = (-1.0, 0.0, 0.0)
    paintF = is_lab("Front_Body", "Paint")
    maskF = is_lab("Front_Body", "Grille")                    # 검은 마스크의 바닥(그 안에서 전조등 · 통풍구 · 가운데 구멍을 다시 팝니다)
    ahead = lambda V: (XS0 - 0.004) - V[:, 0]                 # 앞면 격자(옆면 맨 앞 줄보다 앞)만
    s.trim(lambda V: np.maximum(V[:, 2] - Z_LIP, ahead(V)), to_mat("Trim"), paintF)                 # 맨 아래 검은 턱(그 위 3.6 cm 는 차체색 날)
    # 검은 부분의 윤곽(OB 좌표): 마스크 위 선 → 전조등(안쪽 변, 위 변, 바깥 변 = 모서리 능선) → 능선을 따라 내려가 휠 아치 앞 →
    # 날개 위 선 → 날개 안쪽 변 → 마스크 아래 선. 가운데 쪽은 y < 0 까지 넉넉히 잡습니다(왼쪽 반만 만들고 비춥니다).
    ti, to_, bo, bi = LAMP_Q
    wti, wbi, wtr, wbr = WING_Q
    zm0, zm1 = Z_MASK
    blk = OB([(XCF(zm1), -0.25, zm1), (bi[0], bi[1], zm1 + 0.006), ti, to_, bo, (RGX(0.47), RGY(0.47), 0.47), (XS0 - 0.01, 0.925, 0.440),
              (XS0 - 0.01, 0.925, wtr[2]), wtr, wti, wbi, (XCF(zm0), -0.25, zm0)])
    s.presplit(OB, blk[1:-1], paintF, lambda V: -ahead(V))
    s.recess(lambda V: np.maximum(sd_poly(OB(V), blk), ahead(V)), to_mat("Trim"), to_mat("Grille"), 0.004, 0.004, OB_IN, paintF)
    # 모서리 통풍구(전조등 아래, 날개 위): 한 단 더 들어간 어두운 바닥. 가로 핀은 build_front_details 에서 붙입니다.
    vent = OB([bi, bo, (RGX(0.47), RGY(0.47), 0.47), (XS0 - 0.01, 0.925, 0.440), (XS0 - 0.01, 0.925, wtr[2]), wtr, wti])
    s.recess(lambda V: np.maximum(sd_poly(OB(V), vent) + 0.012, ahead(V)), to_mat("Trim"), to_mat("Grille"), 0.012, 0.030, OB_IN, maskF)
    # 전조등: 겉면과 거의 같은 높이의 어두운 유리 덮개(렌즈 두 줄은 build_front_details)
    lamp = OB(LAMP_Q)
    s.recess(lambda V: np.maximum(sd_poly(OB(V), lamp) + 0.006, ahead(V)), to_mat("Black"), to_mat("LampHousing"), 0.005, -0.002, OB_IN, maskF)
    # 번호판 아래의 가운데 공기 구멍(가로 살은 build_front_details)
    s.recess(lambda V: np.maximum(sd_box(YZ(V), (0.0, 0.372), (0.262, 0.060), 0.010), 2.1 - V[:, 0]), to_mat("Trim"), to_mat("Grille"), 0.010, 0.030, FWD, maskF)
    # 들보의 가는 홈
    s.recess(lambda V: np.maximum(sd_box(YZ(V), SLOT[0], SLOT[1], 0.008), 2.0 - V[:, 0]), to_mat("Trim"), to_mat("Grille"), 0.006, 0.030, FWD, paintF)

    # ================= 뒷면 무늬
    BACK = (1.0, 0.0, 0.0)
    paintR = is_lab("Rear_Body", "Paint")
    rearface = lambda V: V[:, 0] + 2.0                       # x < -2.0 인 곳만(뒷면)
    s.trim(lambda V: np.maximum(V[:, 2] - 0.660, rearface(V)), to_mat("Trim"), paintR)                  # 트렁크 아래는 검은 범퍼
    trimR = is_lab("Rear_Body", "Trim")
    diff = np.array([(0.345, 0.525), (0.705, 0.525), (0.660, 0.345), (0.375, 0.330)])
    s.recess(lambda V: np.maximum(sd_rpoly(YZ(V), diff, 0.012), rearface(V)), to_mat("Paint"), to_mat("Paint"), 0.012, -0.012, BACK, trimR)   # 아래 차체색 판(돋움)
    s.recess(lambda V: np.maximum(sd_box(YZ(V), (0.0, 0.495), (0.325, 0.085), 0.015), rearface(V)), to_mat("Trim"), to_mat("Grille"), 0.02, 0.055, BACK, trimR)   # 번호판 자리
    s.recess(lambda V: np.maximum(sd_box(YZ(V), (0.0, 0.854), (0.735, 0.012), 0.004), rearface(V)), to_mat("Black"), to_mat("Black"), 0.004, 0.006, BACK, paintR)  # 가는 가로 램프 띠

    # ================= 판 사이 틈(V 홈)
    G_W, G_D = 0.007, 0.007
    sideobj = is_obj("Front_Body", "Rear_Body", "Door_FL", "Door_RL")
    zcap = lambda V: np.maximum(V[:, 2] - 1.06, 0.20 - V[:, 2])
    for fn in (xf, xm, xr):
        s.groove(lambda V, fn=fn: np.where((np.abs(V[:, 0] - fn(V[:, 2])) < 0.2) & (V[:, 1] > 0.5), V[:, 0] - fn(V[:, 2]), 1.0), G_W, G_D, IN_L, sideobj)
    # 문 손잡이: 문과 같은 면의 손잡이 테두리(가는 틈)와 그 아래 어두운 손 넣는 자리
    for (c, ang), ob in ((HANDLE_F, "Door_FL"), (HANDLE_R, "Door_RL")):
        ca, sa = math.cos(math.radians(ang)), math.sin(math.radians(ang))

        def huv(V, c=c, ca=ca, sa=sa):
            dx = V[:, 0] - c[0]; dz = V[:, 2] - c[1]
            return np.stack([-(dx * ca - dz * sa), dx * sa + dz * ca], 1)      # u = 뒤쪽으로, v = 위로(손잡이 기울기에 맞춤)

        s.recess(lambda V, huv=huv: np.where(V[:, 1] > 0.5, sd_box(huv(V), (0.0, -0.010), (0.105, 0.013), 0.011), 1.0), to_mat("Black"), to_mat("Black"), 0.006, 0.014, IN_L, is_lab(ob, "Paint"))
        s.groove(lambda V, huv=huv: np.where(V[:, 1] > 0.5, sd_box(huv(V), (0.0, 0.0), (0.125, 0.027), 0.022), 1.0), 0.004, 0.004, IN_L, is_lab(ob, "Paint"))
    # 앞 범퍼와 펜더 사이
    bf = np.array([(1.722, 0.789), (1.578, 0.662)])
    s.groove(lambda V: np.where((V[:, 1] > 0.6) & (V[:, 2] > 0.655) & (V[:, 2] < 0.795), V[:, 0] - np.interp(V[:, 2], bf[::-1, 1], bf[::-1, 0]), 1.0), 0.005, 0.005, IN_L, is_obj("Front_Body"))
    # 뒤 범퍼와 옆판 사이(램프 앞 9 cm)
    rb = np.array([(-1.915, 0.925), (-2.017, 0.653)])
    s.groove(lambda V: np.where((V[:, 1] > 0.6) & (V[:, 2] > 0.645) & (V[:, 2] < 0.93), V[:, 0] - np.interp(V[:, 2], rb[::-1, 1], rb[::-1, 0]), 1.0), 0.005, 0.005, IN_L, is_lab("Rear_Body", "Paint"))
    # 트렁크 뚜껑 옆 선(뒷면)
    lid = np.array([(0.600, 1.06), (0.636, 0.925), (0.628, 0.85), (0.605, 0.72), (0.585, 0.685), (0.55, 0.665), (0.0, 0.665)])
    s.groove(lambda V: np.where(V[:, 0] < -2.0, sd_polyline(YZ(V), lid) * np.where(sd_poly(YZ(V), np.vstack([lid, [(0.0, 1.06)]])) < 0, -1.0, 1.0), 1.0),
             0.006, 0.006, BACK, paintR)

    # ================= 옆 창(유리 · 틀 · 기둥)
    g = Soup()
    g.add_grid(B.S_up, ("Canopy", "Paint"))
    xb = np.linspace(-1.157, 0.74, 9)
    dlo = np.array(DLO + [(x, Z5(x) - 0.02) for x in xb])
    g.trim(lambda V: sd_rpoly(XZ(V), dlo, 0.012), to_mat("Black"))
    g.trim(lambda V: sd_rpoly(XZ(V), np.array(PANE_F), 0.02), to_mat("Glass"))
    g.trim(lambda V: sd_rpoly(XZ(V), np.array(PANE_R), 0.02), to_mat("Glass"))
    g.trim(lambda V: sd_rpoly(XZ(V), np.array(QGLASS), 0.012), to_mat("Black"), lambda l: l[1] == "Paint")
    g.trim(lambda V: sd_rpoly(XZ(V), np.array(QGLASS), 0.012) + 0.013, to_mat("Glass"), lambda l: l[1] == "Black")
    s.extend(g)

    # ================= 윗면
    last = NV_TOP - 2
    xc_rib = B.xc_rib

    def top_label(i, j):
        if i < iA:
            return ("Front_Hood", "Trim" if i == iA - 1 else "Paint")       # 맨 뒤 한 칸은 보닛 뒤 끝에서 앞 유리 아래로 내려가는 검은 카울 턱
        if i < iH:
            if j >= last:
                return ("Canopy", "Paint")
            return ("Canopy", "Black") if (j == last - 1 or i - iA < 2 or iH - i <= 2) else ("Canopy", "Glass")
        if i < iR:
            if j >= last - 1:
                return ("Canopy", "Paint")
            return ("Canopy", "Black") if xc_rib[i] > -0.72 else ("Canopy", "Paint")
        if i < iD:
            if j >= last:
                return ("Canopy", "Paint")
            return ("Canopy", "Black") if (j == last - 1 or i - iR < 2 or iD - i <= 2) else ("Canopy", "Glass")
        return ("Rear_Trunk", "Paint")

    t = Soup()
    t.add_grid(B.S_top, None, label_fn=top_label)
    # 보닛 옆 선 · 트렁크 옆 선의 틈
    hood_edge = B.S_low[:iA + 1, -1]
    t.groove(lambda V: sd_polyline(V, hood_edge) - 0.004, 0.006, 0.006, (0.0, 0.0, -1.0), is_obj("Front_Hood"))
    s.extend(t)
    s.weld()
    full = Soup(); full.extend(s)
    full.extend(s.mirrored(lambda n: n[:-1] + "R" if n.startswith("Door_") else n))
    full.weld()
    # 주유구 뚜껑 테두리(왼쪽에만 있습니다)
    fuel = lambda V: np.where(V[:, 1] > 0.5, sd_box(XZ(V), FUEL[0], FUEL[1], 0.03), 1.0)
    full.groove(fuel, 0.005, 0.005, IN_L, is_obj("Rear_Body"))
    return full


def build_flares(B, M, root):
    """휠 아치 테: 차체에서 살짝 나온 납작한 띠. 구멍 가장자리에서 가장 많이 나오고 바깥 끝에서 차체와 만납니다."""
    out = []
    for nm, xc, spec in (("F", XF, FLARE_F), ("R", XR, FLARE_R)):
        nth = 64
        th = np.radians(np.linspace(-24, 204, nth))
        y_top = B.side_y(xc, ZA + R_ARCH)
        h_lip = 0.9275 - y_top
        fs = [-1.0, 0.0, 0.03, 0.30, 0.62, 0.90, 1.0]                # 띠 안에서의 위치(구멍 가장자리 0 → 바깥 끝 1, -1 = 안으로 접힌 턱)
        S = np.zeros((nth, len(fs), 3))
        for i, a in enumerate(th):
            ca, sa = math.cos(a), math.sin(a)
            ro = float(flare_rout(np.array([ca]), np.array([sa]), spec)[0])
            for j, f in enumerate(fs):
                r = R_ARCH + (ro - R_ARCH) * max(f, 0.0)
                x = xc + r * ca; z = ZA + r * sa
                z = max(z, Z0(x) + 0.004)
                yb = B.side_y(x, max(z, Z0(x) + 0.02))
                ymax = 0.9275 - (0.018 * smooth01((x - 1.50) / 0.28) if nm == "F" else 0.0)       # 앞 테는 앞쪽으로 갈수록 조금 좁아집니다
                hl = max(min(h_lip, ymax - yb), 0.006)
                if f < 0:
                    y = yb + hl - 0.045
                elif f >= 1.0:
                    y = yb
                else:
                    y = yb + hl * (1 - 0.72 * f)
                S[i, j] = (x, y, z)
        zf, zr = ((0.44, 0.30) if nm == "F" else (0.36, 0.56))           # 테 아래쪽이 검은 덧판으로 바뀌는 높이(앞쪽 끝, 뒤쪽 끝)
        for side, sgn in (("L", 1), ("R", -1)):
            T = S.copy(); T[..., 1] *= sgn
            sp = Soup()
            sp.add_grid(T, ("x", "Paint"), ref=(xc, 0.0, ZA))
            sp.weld()
            sp.trim(lambda V: V[:, 2] - np.where(V[:, 0] > xc, zf, zr), to_mat("Trim"))
            o = soup_objects(sp, M, root, angle=30)["x"]
            o.name = f"Well_{nm}{side}_Flare"; o.data.name = o.name
            out.append(o)
    return out


def build_wells(M, root):
    """바퀴 집(검은 안쪽 벽)."""
    for nm, x in (("F", XF), ("R", XR)):
        for left in (True, False):
            s = 1 if left else -1
            V = []; F = []
            segs = 32
            a0, a1 = math.radians(-24), math.radians(204)
            ys = (0.56 * s, 0.895 * s)
            r = R_ARCH + 0.004
            for i in range(segs + 1):
                a = a0 + (a1 - a0) * i / segs
                for yy in ys:
                    V.append((x + r * math.cos(a), yy, ZA + r * math.sin(a)))
            for i in range(segs):
                F.append((i * 2, i * 2 + 1, i * 2 + 3, i * 2 + 2))
            c = len(V); V.append((x, ys[0], ZA - 0.05))
            for i in range(segs + 1):
                a = a0 + (a1 - a0) * i / segs
                V.append((x + r * math.cos(a), ys[0], ZA + r * math.sin(a)))
            for i in range(segs):
                F.append((c, c + 1 + i, c + 2 + i))
            o = obj_from(f"Well_{nm}{'L' if left else 'R'}", V, F, [M["WheelWell"]], parent=root, smooth=True)
            shade(o, 40)


def build_under(M, root):
    V = [(-1.95, -0.76, 0.232), (1.95, -0.76, 0.205), (1.95, 0.76, 0.205), (-1.95, 0.76, 0.232)]
    return obj_from("Underbody_Plate", V, [(0, 1, 2, 3)], [M["Underbody"]], parent=None, smooth=False)


# ------------------------------------------------------------------------------------------ 바퀴
def _prism(pts_front, depth, m_front, m_side, name):
    """앞면 다각형(바깥쪽, +Y)을 -Y로 depth 만큼 내린 기둥. 앞면과 옆면의 재질이 다릅니다."""
    bm = bmesh.new()
    vf = [bm.verts.new(p) for p in pts_front]
    vb = [bm.verts.new((p[0], p[1] - depth, p[2])) for p in pts_front]
    f = bm.faces.new(vf); f.material_index = 0
    n = len(vf)
    for i in range(n):
        j = (i + 1) % n
        q = bm.faces.new([vf[i], vb[i], vb[j], vf[j]]); q.material_index = 1
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    me.materials.append(m_front); me.materials.append(m_side)
    o = link(bpy.data.objects.new(name, me))
    c = np.mean(np.asarray(pts_front, float), axis=0)
    fix_normals(o, inside_point=(c[0], c[1] - depth / 2, c[2]))
    return o


def build_wheel_mesh(M):
    """바퀴 하나(바깥쪽 = +Y, 원점 = 중심). 다섯 방향의 쌍 스포크: 밝게 깎은 살 옆에 검은 살이 나란히 갑니다."""
    w = 0.118
    tyre = [(0.2445, -w + 0.004), (0.258, -w), (0.288, -w - 0.001), (0.310, -w + 0.008), (0.322, -w + 0.030), (0.327, -0.062),
            (0.327, -0.036), (0.3205, -0.033), (0.3205, -0.025), (0.327, -0.022),
            (0.327, 0.022), (0.3205, 0.025), (0.3205, 0.033), (0.327, 0.036),
            (0.327, 0.062), (0.322, w - 0.030), (0.310, w - 0.008), (0.288, w + 0.001), (0.258, w), (0.2445, w - 0.004)]
    k = WZ / 0.327
    t = lathe("w_tyre", [(r * k if r > 0.25 else r, y) for r, y in tyre], M["Tire"], segs=72)
    fix_normals(t, inside_point=(0, 0, 0))
    lip = lathe("w_lip", [(0.2445, w - 0.004), (0.2480, w + 0.002), (0.2420, w + 0.004), (0.2330, w - 0.003), (0.2290, w - 0.016)], M["RimSilver"], segs=72)
    barrel = lathe("w_barrel", [(0.2290, w - 0.016), (0.2260, 0.0), (0.2260, -w + 0.02), (0.2445, -w + 0.004)], M["RimDark"], segs=48)
    disc = lathe("w_disc", [(0.075, 0.046), (0.170, 0.046), (0.170, 0.020), (0.075, 0.020), (0.075, 0.046)], M["BrakeDisc"], segs=40)
    hat = lathe("w_hat", [(0.0, 0.060), (0.070, 0.060), (0.075, 0.046)], M["BrakeDisc"], segs=32)
    cal = box("w_cal", (0, 0, 0), (0.075, 0.066, 0.150), M["Caliper"], bevel=0.012)
    cal.data.transform(Matrix.Rotation(math.radians(-30), 4, "Y") @ Matrix.Translation((0.150, 0.036, 0.0)))
    parts = [t, lip, barrel, disc, hat, cal]
    yh = w - 0.036                                  # 허브 쪽 살 앞면
    yr = w - 0.012                                  # 림 쪽 살 앞면
    r0, r1 = 0.058, 0.2305
    for kk in range(5):
        a = 2 * math.pi * kk / 5 + math.pi / 2

        def pt(r, deg, dy=0.0):
            ang = a + math.radians(deg)
            return (r * math.cos(ang), yh + (yr - yh) * (r - r0) / (r1 - r0) + dy, r * math.sin(ang))

        # 넓고 밝게 깎은 살(림 쪽으로 넓어지며 한쪽으로 휨)
        fa = [pt(r0, -13), pt(r1, 2), pt(r1, 27), pt(0.150, 17.5), pt(r0, 13)]
        parts.append(_prism(fa, 0.030, M["RimSilver"], M["RimDark"], "w_sa"))
        # 나란한 가는 밝은 살
        fb = [pt(0.078, -30), pt(r1, -11), pt(r1, -5), pt(0.078, -22)]
        parts.append(_prism(fb, 0.026, M["RimSilver"], M["RimDark"], "w_sb"))
        # 두 살 사이의 유광 검정 면(조금 안쪽)
        fw = [pt(0.078, -22, -0.010), pt(r1, -5, -0.010), pt(r1, 2, -0.010), pt(r0, -13, -0.010)]
        parts.append(_prism(fw, 0.016, M["Black"], M["RimDark"], "w_sw"))
        # 너트
        a2 = a + math.radians(40)
        c = np.array([math.cos(a2), 0.0, math.sin(a2)]) * 0.052
        nut = lathe("w_nut", [(0.0, yh + 0.004), (0.0095, yh + 0.004), (0.0105, yh - 0.002), (0.0105, yh - 0.018)], M["RimDark"], segs=10)
        nut.data.transform(Matrix.Translation(tuple(c)))
        parts.append(nut)
    hub = lathe("w_hub", [(0.033, yh + 0.004), (0.070, yh + 0.002), (0.082, yh - 0.010), (0.082, yh - 0.045)], M["RimDark"], segs=40)
    cap = lathe("w_cap", [(0.0, yh + 0.009), (0.028, yh + 0.009), (0.033, yh + 0.004)], M["Black"], segs=32)
    capring = lathe("w_capring", [(0.028, yh + 0.0095), (0.031, yh + 0.0095), (0.034, yh + 0.004)], M["RimSilver"], segs=32)
    parts.append(capring)
    parts += [hub, cap]
    whl = join(parts, "WheelMesh")
    shade(whl, 30)
    return whl


def build_wheels(M, root):
    base = build_wheel_mesh(M)
    me = base.data
    out = []
    for nm, x, left in (("Wheel_FL", XF, True), ("Wheel_FR", XF, False), ("Wheel_RL", XR, True), ("Wheel_RR", XR, False)):
        o = link(bpy.data.objects.new(nm, me), root)
        o.location = (x, TRACK if left else -TRACK, WZ)
        if not left:
            o.rotation_euler = (0, 0, math.pi)
        out.append(o)
    bpy.data.objects.remove(base)
    return out


# ------------------------------------------------------------------------------------------ 장식(거울 · 안테나 · 램프 · 글자)
def hull(name, pts, m, bevel=0.0, parent=None):
    """점들의 볼록 껍질로 만든 각진 덩어리."""
    bm = bmesh.new()
    vs = [bm.verts.new(p) for p in pts]
    bmesh.ops.convex_hull(bm, input=vs)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    me.materials.append(m)
    o = link(bpy.data.objects.new(name, me), parent)
    if bevel > 0:
        md = o.modifiers.new("bevel", "BEVEL"); md.width = bevel; md.segments = 2; md.limit_method = "ANGLE"; md.angle_limit = math.radians(12)
        apply_mods(o)
    shade(o, 22, weighted=True)
    return o


class RowSurf:
    """가로 줄마다 y 가 한쪽으로 변하는 격자 곡면(앞면 · 뒷면)에서 (y, z) → x 를 읽습니다. 줄 위에서 y 로, 줄 사이는 z 로 보간합니다."""

    def __init__(self, S):
        S = np.asarray(S, float)
        if S[0, 0, 1] > S[0, -1, 1]:
            S = S[:, ::-1]
        self.S = S.copy()
        self.S[..., 1] = np.maximum.accumulate(self.S[..., 1], axis=1) + np.arange(S.shape[1])[None, :] * 1e-9

    def __call__(self, y, z):
        xs = np.array([np.interp(y, r[:, 1], r[:, 0]) for r in self.S]); zs = np.array([np.interp(y, r[:, 1], r[:, 2]) for r in self.S])
        o = np.argsort(zs)
        return float(np.interp(z, zs[o], xs[o]))


def mirror_obj(o, name):
    """y = 0 평면에 비춘 사본."""
    c = o.copy(); c.data = o.data.copy(); c.name = name; c.data.name = name
    link(c, o.parent)
    c.data.transform(Matrix.Scale(-1, 4, (0, 1, 0)))
    c.data.flip_normals()
    return c


def ring(name, c, e1, e2, nrm, r1, r2, m, parent, width=0.010, depth=0.006):
    """엠블럼 자리 표시용 타원 고리(로고는 그리지 않습니다)."""
    t = np.linspace(0, 2 * math.pi, 49)
    pts = c + nrm * 0.004 + np.outer(r1 * np.cos(t), e1) + np.outer(r2 * np.sin(t), e2)
    return strip(name, pts, width, depth, m, normal=tuple(nrm), parent=parent, smooth=True)


def build_mirrors(M, root):
    """사이드 미러: 문 어깨에서 나온 가는 검은 팔 위의 각진 덮개(차체색), 아래는 검정."""
    c = np.array([0.668, 0.860, 1.036])
    cap = [(-0.105, 0.030, -0.045), (-0.105, 0.030, 0.056), (-0.100, 0.185, 0.046), (-0.096, 0.205, -0.020),
           (0.105, 0.030, 0.030), (0.105, 0.030, -0.045), (0.050, 0.120, 0.050), (-0.020, 0.205, 0.018), (0.040, 0.140, -0.045),
           (-0.095, 0.080, 0.064), (0.060, 0.050, 0.050)]
    chin = [(-0.103, 0.034, -0.045), (-0.094, 0.203, -0.020), (0.103, 0.034, -0.045), (0.040, 0.140, -0.045),
            (-0.098, 0.040, -0.070), (-0.090, 0.185, -0.050), (0.085, 0.040, -0.066), (0.030, 0.130, -0.062)]
    to_w = lambda pts: [tuple(c + np.array([p[0], p[1], p[2]])) for p in pts]
    o1 = hull("Mirror_L_cap", to_w(cap), M["Paint"], bevel=0.004)
    o2 = hull("Mirror_L_chin", to_w(chin), M["Black"], bevel=0.003)
    gl = [(-0.1065, 0.042, -0.034), (-0.1065, 0.042, 0.046), (-0.1015, 0.178, 0.038), (-0.0975, 0.193, -0.016)]
    o3 = obj_from("Mirror_L_glass", to_w(gl), [(0, 1, 2, 3)], [M["LampHousing"]], smooth=False)
    fix_normals(o3, inside_point=tuple(c))
    arm = [(0.060, -0.035, -0.085), (-0.040, -0.035, -0.085), (0.070, 0.045, -0.052), (-0.060, 0.045, -0.052),
           (0.050, -0.035, -0.105), (-0.030, -0.035, -0.105), (0.060, 0.045, -0.068), (-0.050, 0.045, -0.068)]
    o4 = hull("Mirror_L_arm", to_w(arm), M["Trim"], bevel=0.003)
    mr = join([o1, o2, o3, o4], "Mirror_L")
    mr.parent = root
    mirror_obj(mr, "Mirror_R")


def build_fin(M, root):
    """샤크핀 안테나(뒤가 높은 지느러미)."""
    xf = -1.16; zf = ZC_BL(xf)
    fin = box("Canopy_Fin", (0, 0, 0), (0.17, 0.050, 0.075), M["Black"], bevel=0.018, segs=3)
    for v in fin.data.vertices:
        if v.co.z > 0:
            k = (v.co.x + 0.085) / 0.17               # 0 = 뒤, 1 = 앞
            v.co.z *= 1.0 - 0.80 * k; v.co.x -= 0.02; v.co.y *= 0.55
    fin.location = (xf, 0, zf + 0.030)
    fin.parent = root


def build_front_details(B, M, root):
    """앞모습의 작은 부품(6회): 주간 주행등(가로 · 꺾인 끝), 전조등 렌즈, 통풍구 핀, 가운데 살, 빈 번호판, 주차 센서, 엠블럼 고리."""
    F = B.S_front
    hf = RowSurf(F[:JB_FRONT + 1])
    i1, i2 = FR_N[0], FR_N[0] + FR_N[1]                       # 가로 줄에서 모서리 능선, 앞면이 꺾이는 점의 칸 번호
    ob_in = np.array(OB_IN)
    ch_n = (math.cos(math.radians(44.0)), math.sin(math.radians(44.0)), 0.0)      # 비스듬한 면의 바깥 방향(대략)
    # 가로 주간 주행등: 검은 띠 가운데. 보닛 앞 끝이 꺾이는 점(안쪽 끝)에서 모서리 쪽으로만 켜집니다.
    mid = 0.5 * (F[JB_FRONT + 1] + F[JB_FRONT + 2])
    tan = np.gradient(mid[:, :2], axis=0); tan /= (np.linalg.norm(tan, axis=1)[:, None] + 1e-12)
    out = [mid[k] + np.array([-tan[k, 1], tan[k, 0], 0.0]) * 0.004 for k in range(i1 + 1, i2 + 1)]
    barL = strip("Front_LampBarL", out, 0.011, 0.008, M["LampWhite"], normal=ch_n, parent=root, smooth=True)
    mirror_obj(barL, "Front_LampBarR")
    # 꺾인 끝: 어깨선 위(KINK_TOP)에서 모서리 능선을 따라 앞 · 아래로 내려옵니다(검은 틀 + 켜진 띠).
    k4 = np.vstack([B.S_low[2:0:-1, JK4], F[JK4, 0:i1 + 1]])
    k4 = k4[k4[:, 0] >= KINK_TOP[0] - 1e-6]
    lift = np.clip((RGX(0.697) - k4[:, 0]) / (RGX(0.697) - KINK_TOP[0]), 0, 1)             # 위 · 뒤 끝 1, 아래 · 앞 끝 0
    arrow = [p + np.array([0.0, 0.004, -0.004 + 0.012 * w]) for p, w in zip(k4, lift)]
    hous = strip("Front_LampEndL_h", arrow, 0.025, 0.006, M["Black"], normal=(0.1, 1, 0.2), parent=None, smooth=True)
    lit = strip("Front_LampEndL_l", [p + np.array([0.0, 0.003, 0.002]) for p in arrow], 0.013, 0.006, M["LampWhite"], normal=(0.1, 1, 0.2), parent=None, smooth=True)
    endL = join([hous, lit], "Front_LampEndL"); endL.parent = root
    mirror_obj(endL, "Front_LampEndR")

    def chamfer_pts(z, t0, t1, n, inset):
        """높이 z 에서 비스듬한 면 위의 점들(앞면이 꺾이는 점 t = 0 → 모서리 능선 t = 1). inset 만큼 안으로."""
        a = np.array([C2X(z), C2Y(z)]); b = np.array([RGX(z), RGY(z)])
        return [(a[0] + (b[0] - a[0]) * t + ob_in[0] * inset, a[1] + (b[1] - a[1]) * t + ob_in[1] * inset, z) for t in np.linspace(t0, t1, n)]

    # 전조등 렌즈 두 줄(얇은 렌즈형): 전조등 바깥쪽 절반
    lens = [strip("lens", chamfer_pts(z, 0.63, 0.89, 4, 0.000), 0.013, 0.006, M["LampLens"], normal=ch_n, smooth=False) for z in (0.638, 0.605)]
    hl = join(lens, "Front_HeadlampL"); hl.parent = root
    mirror_obj(hl, "Front_HeadlampR")
    # 모서리 통풍구의 가로 핀 세 줄
    fins = [strip("fin", chamfer_pts(z, 0.42, 0.95, 6, 0.025), 0.010, 0.012, M["Trim"], normal=ch_n, smooth=False) for z in (0.455, 0.488, 0.521)]
    f = join(fins, "Front_VentFinsL"); f.parent = root
    mirror_obj(f, "Front_VentFinsR")
    # 가운데 공기 구멍의 가로 살
    slats = []
    for z in (0.330, 0.355, 0.380, 0.405):
        ys = np.linspace(-0.245, 0.245, 21)
        slats.append(strip("slat", [(hf(abs(y), z) - 0.024, y, z) for y in ys], 0.008, 0.014, M["Black"], normal=(1, 0, 0), smooth=False))
    g = join(slats, "Front_GrilleBars"); g.parent = root
    # 앞 번호판 자리: 글자 없는 빈 판과 검은 받침(공식 사진의 차는 모두 번호판을 달고 있습니다)
    (py, pz), (ph, pv) = PLATE
    box("Front_Plate", (X_NOSE + 0.004, py, pz), (0.036, 2 * ph, 2 * pv), M["Plate"], parent=root, bevel=0.004)
    # 펜더 코의 주차 센서(작은 고리)
    c = np.array(SENSOR_FN)
    d = np.array([RGX(c[2]) - XS0, RGY(c[2]) - B.side_y(XS0, c[2]), 0.0]); d /= np.linalg.norm(d)
    nrm = np.array([-d[1], d[0], 0.0])
    sen = ring("Front_SensorL", c - nrm * 0.003, d, np.array([0, 0, 1.0]), nrm, 0.009, 0.009, M["Gap"], root, width=0.002, depth=0.002)
    mirror_obj(sen, "Front_SensorR")
    # 엠블럼 자리(로고는 그리지 않습니다)
    hood = B.S_top; col = hood[:, 0]                                     # 가운데 선
    k = int(np.argmin(np.abs(col[:B.iA, 0] - 2.174)))
    c = col[k]; tan = col[k + 1] - col[max(k - 1, 0)]
    nrm = np.cross(tan, np.array([0, 1.0, 0])); nrm /= np.linalg.norm(nrm)
    if nrm[2] < 0:
        nrm = -nrm
    e1 = np.array([0, 1.0, 0])
    ring("Front_Emblem", c - nrm * 0.0025, e1, np.cross(nrm, e1), nrm, 0.068, 0.034, M["Chrome"], root, width=0.009, depth=0.004)


def build_rear_details(B, M, root):
    hf = RowSurf(B.S_rear)
    # 가는 가로 램프의 켜진 부분(트렁크 쪽 · 옆판 쪽)
    segs = []
    for y0, y1 in ((0.335, 0.598), (0.628, 0.726)):
        pts = [(hf(y, 0.854) + 0.002, y, 0.854) for y in np.linspace(y0, y1, 14)]
        segs.append(strip("lit", pts, 0.010, 0.006, M["LampRed"], normal=(-1, 0.35, 0), smooth=False))
    barL = join(segs, "Rear_LampBarL"); barL.parent = root
    mirror_obj(barL, "Rear_LampBarR")
    # 반사기
    pts = [(hf(y, 0.540) - 0.004, y, 0.540) for y in np.linspace(0.345, 0.690, 16)]
    rf = strip("Rear_ReflectorL", pts, 0.022, 0.008, M["Reflector"], normal=(-1, 0.35, 0), parent=root, smooth=False)
    mirror_obj(rf, "Rear_ReflectorR")
    # 번호판, 엠블럼 자리, 띄어 쓴 글자
    box("Rear_Plate", (hf(0.0, 0.4935) + 0.055 - 0.008, 0, 0.4935), (0.014, 0.52, 0.11), M["Plate"], parent=root, bevel=0.004)
    ring("Rear_Emblem", np.array([hf(0.0, 0.966), 0.0, 0.966]), np.array([0, 1.0, 0]), np.array([0, 0, 1.0]), np.array([-1.0, 0, 0]),
         0.060, 0.034, M["Chrome"], root)
    letters = []
    for ch, y in zip("AVANTE", (0.2375, 0.1425, 0.0475, -0.0475, -0.1425, -0.2375)):
        bpy.ops.object.text_add(location=(0, 0, 0))
        t = bpy.context.object
        t.data.body = ch; t.data.size = 0.040; t.data.extrude = 0.0025
        t.data.align_x = "CENTER"; t.data.align_y = "CENTER"
        bpy.ops.object.convert(target="MESH")
        t = bpy.context.object
        t.data.transform(Matrix.Translation((hf(abs(y), 0.757) - 0.0035, y, 0.757)) @ Matrix.Rotation(math.radians(-90), 4, "Z") @ Matrix.Rotation(math.radians(90), 4, "X"))
        t.data.materials.clear(); t.data.materials.append(M["Chrome"])
        letters.append(t)
    bd = join(letters, "Rear_Badge"); bd.parent = root
    for p in bd.data.polygons:
        p.use_smooth = False


def build_details(B, M, root):
    build_mirrors(M, root)
    build_fin(M, root)
    build_front_details(B, M, root)
    build_rear_details(B, M, root)


# ------------------------------------------------------------------------------------------ 실내
def build_interior(M, root):
    box("Int_Floor", (-0.30, 0, 0.235), (2.30, 1.58, 0.05), M["Carpet"], parent=root)
    box("Int_Firewall", (0.86, 0, 0.56), (0.06, 1.50, 0.60), M["Carpet"], parent=root)
    box("Int_DashTop", (0.77, 0, 0.862), (0.36, 1.44, 0.09), M["DashSoft"], parent=root, bevel=0.035, segs=3)
    box("Int_DashLow", (0.76, 0, 0.71), (0.26, 1.50, 0.34), M["DashSoft"], parent=root, bevel=0.04, segs=3)
    # 화면: 그림 없이 발광 판과 작은 발광 조각으로 그립니다.
    scr = box("Int_Display_p", (0, 0, 0), (0.018, 0.80, 0.115), M["Display"], bevel=0.006)
    bits = [scr]
    for yy, hh, ww in ((0.27, 0.060, 0.14), (0.08, 0.060, 0.14)):
        bits.append(box("Int_Display_t", (-0.0095, yy, 0.0), (0.002, ww, hh), M["DisplayUI"]))
    for yy, zz, ww in ((-0.20, 0.022, 0.24), (-0.14, -0.012, 0.10), (-0.27, -0.012, 0.08)):
        bits.append(box("Int_Display_l", (-0.0105, yy, zz), (0.002, ww, 0.008), M["DisplayUI"]))
    disp = join(bits, "Int_Display")
    disp.rotation_euler = (0, math.radians(-12), 0); disp.location = (0.638, 0.20, 0.972); disp.parent = root
    build_steering(M, root)
    box("Int_Console", (0.18, 0, 0.40), (0.86, 0.24, 0.30), M["DashSoft"], parent=root, bevel=0.03)
    box("Int_Armrest", (-0.12, 0, 0.585), (0.36, 0.22, 0.07), M["SeatFabric"], parent=root, bevel=0.025)
    box("Int_PedalBrake", (0.70, 0.31, 0.36), (0.03, 0.09, 0.07), M["Pedal"], parent=root, bevel=0.006, rot=(0, math.radians(-25), 0))
    box("Int_PedalAccel", (0.72, 0.45, 0.33), (0.02, 0.05, 0.13), M["Pedal"], parent=root, bevel=0.005, rot=(0, math.radians(-25), 0))
    box("Int_Shelf", (-1.66, 0, 0.99), (0.60, 1.30, 0.03), M["DashSoft"], parent=root)
    box("Int_RearWall", (-1.34, 0, 0.72), (0.06, 1.50, 0.56), M["Carpet"], parent=root)
    for key_, x, y, w, rear in (("FL", 0.0, 0.37, 0.52, False), ("FR", 0.0, -0.37, 0.52, False),
                                ("RL", -0.98, 0.42, 0.46, True), ("RC", -0.98, 0.0, 0.36, True), ("RR", -0.98, -0.42, 0.46, True)):
        build_seat(M, root, key_, x, y, w, rear)
    sphere("Airbag_FL", (0.27, 0.37, 0.86), (0.11, 0.21, 0.19), M["Airbag"], parent=root, rot=(0, math.radians(-25), 0))


def build_steering(M, root):
    bpy.ops.mesh.primitive_torus_add(major_radius=0.185, minor_radius=0.019, major_segments=48, minor_segments=12)
    rim = bpy.context.object; rim.data.materials.append(M["Steering"])
    hub = box("hub", (0, 0, 0.0), (0.13, 0.11, 0.05), M["Steering"], bevel=0.02, segs=3)
    sp1 = box("sp1", (0.10, 0, 0), (0.17, 0.035, 0.02), M["Steering"], bevel=0.008)
    sp2 = box("sp2", (-0.10, 0, 0), (0.17, 0.035, 0.02), M["Steering"], bevel=0.008)
    sp3 = box("sp3", (0, -0.10, 0), (0.035, 0.17, 0.02), M["Steering"], bevel=0.008)
    col = box("col", (0, 0, -0.16), (0.07, 0.07, 0.26), M["Steering"], bevel=0.02)
    for p in (rim, hub, sp1, sp2, sp3, col):
        for f in p.data.polygons:
            f.use_smooth = True
    sw = join([rim, hub, sp1, sp2, sp3, col], "Int_Steering")
    sw.rotation_euler = (0, math.radians(-65), 0)
    sw.location = (0.42, 0.37, 0.83)
    sw.parent = root


def build_seat(M, root, key_, x, y, w, rear):
    m = M["SeatFabric"]
    cus_len = 0.48 if not rear else 0.46
    cz = 0.43 if not rear else 0.42
    cushion = box(f"Seat_{key_}_c", (x + 0.12, y, cz), (cus_len, w, 0.13), m, bevel=0.04, segs=3, rot=(0, math.radians(-6), 0))
    tilt = math.radians(20 if not rear else 24)
    bh = 0.60 if not rear else 0.56
    bx0 = x + 0.12 - cus_len / 2 + 0.03
    hinge = Vector((bx0, y, cz + 0.05))
    R = Matrix.Rotation(-tilt, 4, "Y")
    back = box(f"Seat_{key_}_b", (0, 0, 0), (0.13, w, bh), m, bevel=0.045, segs=3)
    back.matrix_basis = Matrix.Translation(hinge) @ R @ Matrix.Translation((-0.065, 0, bh / 2))
    top = hinge + R.to_3x3() @ Vector((-0.065, 0, bh + 0.10))
    head = box(f"Seat_{key_}_h", (0, 0, 0), (0.10, min(0.26, w * 0.6), 0.17), m, bevel=0.035, segs=3)
    head.matrix_basis = Matrix.Translation(top) @ R
    seat = join([cushion, back, head], f"Seat_{key_}")
    seat.parent = root
    outer = 1 if y > 0.05 else (-1 if y < -0.05 else 1)
    sh = hinge + R.to_3x3() @ Vector((0.03, outer * w * 0.38, bh * 0.92))
    hip = Vector((x + 0.12 - cus_len / 2 + 0.11, y - outer * w * 0.40, cz + 0.11))
    lap_o = Vector((x + 0.12 - cus_len / 2 + 0.11, y + outer * w * 0.46, cz + 0.11))
    chest = (sh + hip) / 2 + R.to_3x3() @ Vector((0.17, 0, 0))
    b1 = strip(f"Belt_{key_}_s", poly_curve([sh, chest, hip], 12), 0.05, 0.008, M["Belt"], normal=tuple(R.to_3x3() @ Vector((1, 0, 0))))
    lap_mid = (lap_o + hip) / 2 + Vector((0.13, 0, 0.06))
    b2 = strip(f"Belt_{key_}_l", poly_curve([lap_o, lap_mid, hip], 10), 0.045, 0.008, M["Belt"], normal=(0.3, 0, 1))
    belt = join([b1, b2], f"Belt_{key_}")
    belt.parent = root
    torso = box(f"Occ_{key_}_t", (0, 0, 0), (0.20, w * 0.66, 0.46), M["Occupant"], bevel=0.07, segs=4)
    torso.matrix_basis = Matrix.Translation(hinge) @ R @ Matrix.Translation((0.11, 0, 0.27))
    hd = sphere(f"Occ_{key_}_h", hinge + R.to_3x3() @ Vector((0.10, 0, bh + 0.03)), (0.095, 0.085, 0.105), M["Occupant"])
    thighs = box(f"Occ_{key_}_l", (x + 0.17, y, cz + 0.135), (0.42, w * 0.62, 0.13), M["Occupant"], bevel=0.05, segs=3)
    occ = join([torso, hd, thighs], f"Occupant_{key_}")
    occ.parent = root


# ------------------------------------------------------------------------------------------ 바닥 그림자(차체가 아닌 바닥에만 쓰는 그림)
def make_shadow_image():
    W, H = 512, 256
    yy, xx = np.mgrid[0:H, 0:W]
    cx = ((xx + 0.5) / W * 2 - 1) * 3.0; cy = ((yy + 0.5) / H * 2 - 1) * 1.5
    d = np.sqrt((np.maximum(np.abs(cx) - 2.05, 0) / 0.70) ** 2 + (np.maximum(np.abs(cy) - 0.66, 0) / 0.42) ** 2)
    a = np.clip(1 - d, 0, 1) ** 1.7 * 0.78
    for wx in (XF, XR):
        for wy in (TRACK, -TRACK):
            r = np.sqrt(((cx - wx) / 0.24) ** 2 + ((cy - wy) / 0.14) ** 2)
            a = np.maximum(a, np.clip(1 - r, 0, 1) ** 1.2 * 0.92)
    img = bpy.data.images.new("ShadowTex", W, H, alpha=True)
    px = np.zeros((H, W, 4), np.float32); px[..., 3] = a
    img.pixels = px.ravel().tolist(); img.pack()
    return img


def build_shadow(root, img):
    m = mat_unlit_shadow("Shadow", img)
    V = [(-3.0, -1.5, 0.002), (3.0, -1.5, 0.002), (3.0, 1.5, 0.002), (-3.0, 1.5, 0.002)]
    o = obj_from("Shadow", V, [(0, 1, 2, 3)], [m], parent=root, smooth=False)
    uv = o.data.uv_layers.new(name="UVMap")
    for li, (uu, vv) in enumerate(((0, 0), (1, 0), (1, 1), (0, 1))):
        uv.data[li].uv = (uu, vv)
    return o


# ------------------------------------------------------------------------------------------ 문 경첩 · 안쪽 패널
HINGE_F, HINGE_R = 0.956, -0.168      # 앞문 · 뒷문 경첩의 X


def door_hinges(M, root):
    spans = {"FL": (0.944, -0.19, 1, HINGE_F), "RL": (-0.19, -0.95, 1, HINGE_R), "FR": (0.944, -0.19, -1, HINGE_F), "RR": (-0.19, -0.95, -1, HINGE_R)}
    for k, (x0, x1, s, hx) in spans.items():
        door = bpy.data.objects["Door_" + k]
        hinge = Vector((hx, 0.865 * s, 0.70))
        door.data.transform(Matrix.Translation(-hinge))
        door.location = hinge
        door.parent = root
        trim = box(f"DoorTrim_{k}_p", ((x0 + x1) / 2, 0.80 * s, 0.63), (abs(x0 - x1) - 0.06, 0.035, 0.66), M["DoorTrim"], bevel=0.015)
        arm = box(f"DoorTrim_{k}_a", ((x0 + x1) / 2 - 0.05, 0.765 * s, 0.665), (abs(x0 - x1) * 0.55, 0.06, 0.04), M["DashSoft"], bevel=0.012)
        t = join([trim, arm], f"DoorTrim_{k}")
        reparent_keep(t, door)
        for h in [o for o in bpy.data.objects if o.name.startswith(f"Door_{k}_")]:
            reparent_keep(h, door)


# ------------------------------------------------------------------------------------------ 미리보기
def setup_preview_scene():
    sc = bpy.context.scene
    sc.render.engine = "BLENDER_EEVEE_NEXT"
    sc.render.resolution_x = 1200; sc.render.resolution_y = 700
    sc.render.film_transparent = True
    sc.view_settings.view_transform = "AgX"
    world = bpy.data.worlds.new("World"); sc.world = world; world.use_nodes = True
    nt = world.node_tree; bg = nt.nodes["Background"]
    tc = nt.nodes.new("ShaderNodeTexCoord"); sep = nt.nodes.new("ShaderNodeSeparateXYZ"); ramp = nt.nodes.new("ShaderNodeValToRGB")
    nt.links.new(tc.outputs["Generated"], sep.inputs[0]); nt.links.new(sep.outputs["Z"], ramp.inputs[0])
    ramp.color_ramp.elements[0].position = 0.46; ramp.color_ramp.elements[0].color = (0.16, 0.17, 0.18, 1)
    ramp.color_ramp.elements[1].position = 0.60; ramp.color_ramp.elements[1].color = (0.80, 0.84, 0.90, 1)
    nt.links.new(ramp.outputs["Color"], bg.inputs["Color"])
    sun = bpy.data.lights.new("Sun", "SUN"); sun.energy = 3.0; sun.angle = math.radians(8)
    so = link(bpy.data.objects.new("Sun", sun)); so.rotation_euler = (math.radians(38), 0, math.radians(150))
    cam = bpy.data.cameras.new("Cam"); cam.lens = 50
    co = link(bpy.data.objects.new("Cam", cam)); sc.camera = co
    return co


def look(cam, eye, target=(0, 0, 0.62)):
    cam.location = eye
    cam.rotation_euler = (Vector(target) - Vector(eye)).to_track_quat("-Z", "Y").to_euler()


def render_previews(out_dir, views, cam=None):
    os.makedirs(out_dir, exist_ok=True)
    cam = cam or setup_preview_scene()
    for nm, (eye, target, lens) in views.items():
        cam.data.lens = lens
        look(cam, eye, target)
        bpy.context.scene.render.filepath = os.path.join(out_dir, f"{nm}.png")
        bpy.ops.render.render(write_still=True)
    return cam


# ------------------------------------------------------------------------------------------ 메인
def main():
    reset()
    M = make_materials()
    root = link(bpy.data.objects.new("Avante", None))
    B = Body()
    skin = build_skin(B)
    objs = soup_objects(skin, M, root, angle=34.0,
                        weighted=("Front_Body", "Rear_Body", "Front_Hood", "Rear_Trunk", "Canopy", "Door_FL", "Door_FR", "Door_RL", "Door_RR"),
                        angles={"Front_Body": 21.0, "Front_Hood": 15.0})        # 앞면의 각진 면과 보닛 꺾임선은 더 작은 각도에서도 꺾임선으로
    build_flares(B, M, root)
    build_wells(M, root)
    plate = build_under(M, root)
    ub = join([objs["Underbody"], plate], "Underbody"); ub.parent = root
    build_wheels(M, root)
    build_details(B, M, root)
    build_interior(M, root)
    door_hinges(M, root)
    build_shadow(root, make_shadow_image())
    canopy = bpy.data.objects["Canopy"]
    ghost = canopy.copy(); ghost.data = canopy.data.copy()
    ghost.name = "CanopyGhost"; ghost.data.name = "CanopyGhost"
    link(ghost)
    ghost.data.materials.clear(); ghost.data.materials.append(M["Ghost"])
    for p in ghost.data.polygons:
        p.material_index = 0
    for o in list(bpy.context.scene.collection.objects):
        if o is not root and o.parent is None and o.type == "MESH":
            o.parent = root
    os.makedirs(os.path.dirname(os.path.abspath(GLB_OUT)), exist_ok=True)
    bpy.ops.object.select_all(action="DESELECT")
    bpy.ops.export_scene.gltf(filepath=GLB_OUT, export_format="GLB", export_yup=True, export_apply=True,
                              export_texcoords=True, export_normals=True, export_materials="EXPORT",
                              export_cameras=False, export_lights=False, export_extras=False, use_selection=False)
    print("WROTE", GLB_OUT, os.path.getsize(GLB_OUT))
    tris = 0
    for o in bpy.data.objects:
        if o.type == "MESH":
            o.data.calc_loop_triangles(); tris += len(o.data.loop_triangles)
    print("TRIS", tris, "OBJECTS", len([o for o in bpy.data.objects if o.type == "MESH"]))
    if PREVIEW_DIR:
        for o in bpy.context.scene.objects:
            if o.name.startswith(("CanopyGhost", "Airbag", "Occupant", "Belt_")):
                o.hide_render = True
        T = (0, 0, 0.62)
        views = {"front34": ((6.6, 4.6, 2.9), T, 50), "side": ((0.0, 8.2, 1.0), T, 50), "rear34": ((-6.6, 4.4, 2.7), T, 50),
                 "front": ((8.0, 0.0, 1.0), T, 50), "rear": ((-8.0, 0.0, 1.3), T, 50), "top": ((0.01, 0.0, 14.5), (0, 0, 0), 50),
                 "front34low": ((5.6, 3.9, 1.25), T, 50), "rear34low": ((-5.6, 3.9, 1.35), T, 50),
                 "front34r": ((6.6, -4.6, 2.9), T, 50), "cu_nose": ((4.6, 0.9, 1.9), (2.0, 0.3, 0.6), 50),
                 "cu_front": ((3.9, 1.9, 1.05), (2.0, 0.35, 0.55), 50), "cu_rear": ((-4.0, 1.9, 1.15), (-2.1, 0.3, 0.72), 50),
                 "cu_wheel": ((1.2, 2.9, 0.55), (1.3, 0.9, 0.38), 50), "cu_side": ((-0.6, 3.4, 1.05), (-0.6, 0.9, 0.82), 50),
                 "cu_roof": ((-3.2, 2.4, 2.3), (-1.2, 0.3, 1.1), 50), "cu_cowl": ((1.9, 2.0, 1.7), (0.9, 0.75, 0.95), 60),
                 "cu_rflare": ((-2.9, 2.6, 0.9), (-1.6, 0.9, 0.5), 60)}
        cam = render_previews(PREVIEW_DIR, views)
        bpy.data.objects["Canopy"].hide_render = True
        bpy.data.objects["Canopy_Fin"].hide_render = True
        for o in bpy.context.scene.objects:
            if o.name.startswith(("Occupant_FL", "Belt_FL")):
                o.hide_render = False
        render_previews(PREVIEW_DIR, {"interior": ((3.6, 2.6, 4.2), (0, 0, 0.55), 42)}, cam)


# ------------------------------------------------------------------------------------------ 앱용 미리 그린 그림
# 앱(CarModel.kt)의 궤도 카메라와 같은 계산입니다. glTF 좌표(x 앞, y 위, z 오른쪽)로 계산한 뒤 Blender 좌표로 바꿉니다.
FOV_Y, NEAR, FAR, MARGIN = 28.0, 0.1, 60.0, 0.86
BOUNDS = [np.array([x, y, z]) for x in (-2.47, 2.37) for y in (0.0, 1.47) for z in (-1.0, 1.0)]


def _dir(yaw, pitch):
    y, p = math.radians(yaw), math.radians(pitch)
    return np.array([math.cos(p) * math.cos(y), math.sin(p), -math.cos(p) * math.sin(y)])


def _look(eye, target):
    f = target - eye; f /= np.linalg.norm(f)
    s_ = np.cross(f, [0.0, 1.0, 0.0]); s_ /= np.linalg.norm(s_)
    u = np.cross(s_, f)
    m = np.identity(4)
    m[0, :3] = s_; m[1, :3] = u; m[2, :3] = -f
    m[0, 3] = -s_.dot(eye); m[1, 3] = -u.dot(eye); m[2, 3] = f.dot(eye)
    return m


def _persp(aspect):
    f = 1.0 / math.tan(math.radians(FOV_Y) / 2)
    m = np.zeros((4, 4))
    m[0, 0] = f / aspect; m[1, 1] = f; m[2, 2] = (FAR + NEAR) / (NEAR - FAR); m[2, 3] = 2 * FAR * NEAR / (NEAR - FAR); m[3, 2] = -1
    return m


def _fit_one(yaw, pitch, aspect, target):
    lo, hi = 1.0, 40.0
    for _ in range(32):
        mid = (lo + hi) / 2
        m = _persp(aspect) @ _look(target + _dir(yaw, pitch) * mid, target)
        ok = True
        for p in BOUNDS:
            q = m @ np.append(p, 1.0)
            if q[3] <= 1e-6 or abs(q[0] / q[3]) > MARGIN or abs(q[1] / q[3]) > MARGIN:
                ok = False; break
        if ok:
            hi = mid
        else:
            lo = mid
    return hi


def orbit_eye(yaw, pitch, zoom, target, aspect):
    target = np.array(target, float)
    worst = max(_fit_one(k * 30.0, pitch, aspect, target) for k in range(12))
    d = max(worst, _fit_one(yaw, pitch, aspect, target)) * zoom
    return target + _dir(yaw, pitch) * d, target


def to_blender(v):
    return Vector((float(v[0]), float(-v[2]), float(v[1])))


def render_snaps(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    sc = bpy.context.scene
    cam = sc.camera if sc.camera is not None else setup_preview_scene()
    sc.render.resolution_x, sc.render.resolution_y = 1200, 960
    sc.render.image_settings.file_format = "WEBP"
    sc.render.image_settings.color_mode = "RGBA"
    sc.render.image_settings.quality = 88
    cam.data.sensor_fit = "VERTICAL"; cam.data.angle_y = math.radians(FOV_Y)
    cam.data.clip_start, cam.data.clip_end = NEAR, FAR
    aspect = 1200 / 960
    # CarModel.kt의 Orbit.HOME · Orbit.INTERIOR와 같은 값이어야 합니다(yaw, pitch, zoom, target).
    shots = {"car3d_home": (38.0, 16.0, 0.86, (-0.05, 0.62, 0.0), False),
             "car3d_interior": (46.0, 50.0, 0.74, (-0.25, 0.62, 0.0), True)}
    for nm, (yaw, pitch, zoom, target, interior) in shots.items():
        for o in sc.objects:
            if o.type != "MESH":
                continue
            hide = o.name.startswith(("Airbag", "Occupant", "Belt_"))
            if interior:
                hide = hide or o.name in ("Canopy", "Canopy_Fin")
            else:
                hide = hide or o.name == "CanopyGhost"
            o.hide_render = hide
        eye, tgt = orbit_eye(yaw, pitch, zoom, target, aspect)
        cam.location = to_blender(eye)
        cam.rotation_euler = (to_blender(tgt) - to_blender(eye)).to_track_quat("-Z", "Y").to_euler()
        sc.render.filepath = os.path.join(out_dir, nm + ".webp")
        bpy.ops.render.render(write_still=True)
        print("SNAP", sc.render.filepath, [round(float(c), 3) for c in eye])


main()
if SNAP_DIR:
    render_snaps(SNAP_DIR)
