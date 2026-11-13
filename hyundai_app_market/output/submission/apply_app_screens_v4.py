"""제출 문서의 앱 화면 그림을 데모 앱 실제 화면으로 바꿉니다(2026-11-13, Windows 로컬).

build_v4.py(리눅스·LibreOffice)로 다시 만들지 않고, 이미 만든 v4.2 DOCX를 그대로 열어
(1) 그림 파일(word/media)만 같은 픽셀 크기의 새 그림으로 바꾸고 (2) 정해진 문장만 바꿉니다.
문서 구조, 서식, 03·04 원본 디자인, 내용 컨트롤은 건드리지 않습니다. PDF는 Word로 따로 만듭니다(export_pdf_word.ps1).

입력
  assets/app-demo/p-*.png          Pleos 에뮬레이터 2.1.0에서 찍은 데모 앱 화면(앱 창 841x1210을 840x1212로 맞춤)
  assets/v4/portrait/p-*.png       아직 앱에 없는 화면의 설계 시안(출발 전 점검, 재현 조건, 비상 절차 미리 익히기)
출력
  assets/app-demo/composite/*.png  합성 그림(세로 두 장, 간격 48px, build_composites_v4.py와 같은 배치)
  qa/<폴더>/01_공식양식_플레오스가드.docx, 02_상세기획_기술명세.docx
사용: python -I apply_app_screens_v4.py --out qa/v4-r4-app
"""
from __future__ import annotations

import argparse
import shutil
import sys
import zipfile
from pathlib import Path

from lxml import etree
from PIL import Image, ImageDraw

R = Path(__file__).resolve().parent
APP = R / 'assets/app-demo'
MOCK = R / 'assets/v4/portrait'
COMP = APP / 'composite'
W = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
GAP = 48

# 합성 그림: (왼쪽, 오른쪽). 'app:' = 데모 앱 실제 화면, 'mock:' = 설계 시안
PAIRS = {
    'form-portrait-1.png': ('app:p-home.png', 'app:p-warning-find.png'),
    'pair-home-warning.png': ('app:p-home.png', 'app:p-warning-find.png'),
    'form-portrait-2.png': ('app:p-emergency.png', 'app:p-countdown.png'),
    'form-portrait-3.png': ('mock:p-check.png', 'app:p-guardian.png'),
    'pair-result-check.png': ('app:p-warning-result.png', 'mock:p-check.png'),
    'pair-record-pattern.png': ('app:p-record.png', 'mock:p-pattern.png'),
    'pair-emergency-practice.png': ('app:p-emergency.png', 'mock:p-practice.png'),
    'pair-countdown-guardian.png': ('app:p-countdown.png', 'app:p-guardian.png'),
}

# 문서 안 그림 파일 → 새 그림(같은 픽셀 크기). 2026-11-13 docx_images 대조 결과
MEDIA = {
    '01_공식양식_플레오스가드.docx': {
        'word/media/care_image3.png': COMP / 'form-portrait-1.png',
        'word/media/care_image4.png': COMP / 'form-portrait-2.png',
        'word/media/care_image5.png': COMP / 'form-portrait-3.png',
    },
    '02_상세기획_기술명세.docx': {
        'word/media/image3.png': APP / 'p-home-driving.png',
        'word/media/image4.png': COMP / 'form-portrait-1.png',
        'word/media/image5.png': COMP / 'pair-result-check.png',
        'word/media/image6.png': COMP / 'pair-record-pattern.png',
        'word/media/image7.png': COMP / 'pair-emergency-practice.png',
        'word/media/image8.png': APP / 'p-confirm.png',
        'word/media/image9.png': COMP / 'pair-countdown-guardian.png',
    },
}

RUN = '데모 앱을 Pleos 에뮬레이터에서 실행한 화면'

# 문장 바꾸기: (옛 문장, 새 문장). 공식 양식 2~6번(각 3,000자) 안의 문장은 글자 수가 같아야 합니다(검사함).
TEXT = {
    '01_공식양식_플레오스가드.docx': [
        # 5번 개발 계획(3,000자) — 글자 수 같음
        ('주행 제한은 adb 명령으로 켜서', '주행 제한은 콘솔 기어 D로 켜서'),
        ('지금 끝낸 일은 기획, 화면 시안, API와 데이터 계약 설계이며 앱 코드와 실제 발송은 아직 하지 않았습니다.',
         '지금 끝낸 일은 기획, 계약 설계, 에뮬레이터에서 실행한 데모 앱이고 서버와 실제 발송은 아직 하지 않았습니다.'),
        # 7번 참고자료(글자 수 기준 없음)
        ('포함 자료: 세로·가로 화면 시안,', '포함 자료: 데모 앱 실행 화면과 세로·가로 화면 시안,'),
        ('화면은 기획 시안이며 실제 구동 화면이 아닙니다.',
         f'그림 3·4와 그림 5 오른쪽은 {RUN}이고, 나머지 화면은 기획 시안입니다.'),
        # 그림 설명(내용 컨트롤 밖)
        ('그림 3. 세로 한 창의 홈과 경고등 찾기.', f'그림 3. 세로 한 창의 홈과 경고등 찾기({RUN}).'),
        ('그림 4. 주행 중 긴급 안내와 보호자 발송 확인.', f'그림 4. 주행 중 긴급 안내와 보호자 발송 확인({RUN}).'),
        ('그림 5. 출발 전 점검 카드와 보호자 알림 상태.', '그림 5. 출발 전 점검 카드(설계 시안)와 보호자 알림 상태(데모 앱 실행 화면).'),
        ('그림 6. 가로 화면의 재현 조건 카드.', '그림 6. 가로 화면의 재현 조건 카드(설계 시안).'),
    ],
    '02_상세기획_기술명세.docx': [
        ('이 문서의 화면은 기획 시안이며 실제 구동 화면이 아닙니다.',
         f'그림 3·4·8·9와 그림 5~7의 왼쪽은 {RUN}이고, 나머지 화면은 기획 시안입니다.'),
        ('기획, 화면 시안, API·데이터 계약 설계를 마쳤습니다. 앱 코드, 실제 알림 발송, 실차 시험은 다음 단계입니다.',
         '기획, 화면 시안, API·데이터 계약 설계와 시연용 데모 앱을 마치고 에뮬레이터에서 실행했습니다. 서버, 실제 알림 발송, 실차 시험은 다음 단계입니다.'),
        ('그림 3. 주행 중 홈.', f'그림 3. 주행 중 홈({RUN}).'),
        ('그림 4. 세로 한 창의 홈(왼쪽)과 경고등 찾기(오른쪽).', f'그림 4. 세로 한 창의 홈(왼쪽)과 경고등 찾기(오른쪽). 둘 다 {RUN}입니다.'),
        ('그림 5. 경고등 설명(왼쪽)과 출발 전 점검(오른쪽).', '그림 5. 경고등 설명(왼쪽, 데모 앱 실행 화면)과 출발 전 점검(오른쪽, 설계 시안).'),
        ('그림 6. 지금 기록(왼쪽)과 재현 조건(오른쪽).', '그림 6. 지금 기록(왼쪽, 데모 앱 실행 화면)과 재현 조건(오른쪽, 설계 시안).'),
        ('그림 7. 원치 않는 가속 안내(왼쪽)와 비상 절차 미리 익히기(오른쪽).',
         '그림 7. 원치 않는 가속 안내(왼쪽, 데모 앱 실행 화면)와 비상 절차 미리 익히기(오른쪽, 설계 시안).'),
        ('그림 8. 확인 질문.', f'그림 8. 확인 질문({RUN}. 시험 입력 없이 Virtual Console 차속·브레이크 신호로 떴습니다).'),
        ('그림 9. 발송 확인 10초(왼쪽)와 보호자 알림 상태(오른쪽).', f'그림 9. 발송 확인 10초(왼쪽)와 보호자 알림 상태(오른쪽). 둘 다 {RUN}입니다.'),
        ('주행 제한에 따른 화면 축소 · adb driving_regulation on/off', '주행 제한에 따른 화면 축소 · Virtual Console 기어 D/P'),
        ('주행 제한은 adb shell cmd hmg_car_service --service drivingstate driving_regulation on으로 켜고 off로 끕니다.',
         '주행 제한은 Virtual Console에서 기어를 D로 바꿔 켜고 P로 끕니다(이 에뮬레이터 이미지에는 hmg_car_service 명령이 없음을 2026-11-13에 확인).'),
        ('화면 시안은 기획 시안이며 실제 구동 화면이 아닙니다.', '그림 3~9의 데모 앱 화면은 Pleos 에뮬레이터 실행 화면이고, 나머지 화면은 기획 시안입니다.'),
        # 위 문장을 먼저 바꾼 뒤 표 38의 칸(같은 문구)을 바꿉니다.
        ('기획 시안이며 실제 구동 화면이 아닙니다.', '그림 3~9의 데모 앱 화면은 에뮬레이터 실행 화면이고, 나머지는 기획 시안입니다.'),
        ('공개 문서와 SDK 패키지를 읽어 확인했습니다. 실제 호출 시험은 다음 단계입니다.',
         '공개 문서와 SDK 패키지를 읽어 확인했고, 에뮬레이터에서 브레이크·차속·주행 제한·음성 호출을 확인했습니다.'),
        ('접수 뒤 바로 시작합니다.', '차량 데모 앱은 에뮬레이터에서 실행했습니다. 휴대폰 앱과 서버는 접수 뒤 시작합니다.'),
        ('11월 16일부터 확인합니다.', '2026-11-13 확인(Vehicle 브레이크, AAOS 차속, CarUxRestrictions, 한국어 TTS).'),
    ],
}
# 표 38의 "다음 단계" 칸 두 개(앱 코드, 에뮬레이터 SDK 호출)를 바꿉니다: 앞 칸 글로 찾습니다.
TABLE_STATUS = {
    '02_상세기획_기술명세.docx': [('앱 코드(차량·휴대폰·서버)', '일부 끝냄'), ('에뮬레이터에서 SDK 실제 호출', '끝냄')],
}


def build_composites() -> None:
    COMP.mkdir(parents=True, exist_ok=True)
    for name, (a, b) in PAIRS.items():
        ims = []
        for src in (a, b):
            kind, fn = src.split(':')
            im = Image.open((APP if kind == 'app' else MOCK) / fn).convert('RGB')
            if im.size != (840, 1212):
                im = im.resize((840, 1212), Image.Resampling.LANCZOS)
            ims.append(im)
        canvas = Image.new('RGB', (840 * 2 + GAP, 1212), 'white')
        canvas.paste(ims[0], (0, 0)); canvas.paste(ims[1], (840 + GAP, 0))
        d = ImageDraw.Draw(canvas)
        for x0 in (0, 840 + GAP):
            d.rectangle((x0, 0, x0 + 839, 1211), outline='#CAD5E5', width=2)
        canvas.save(COMP / name)
        print('composite', name, canvas.size)


def replace_in_paragraphs(root, old: str, new: str) -> int:
    """문단 안 여러 w:t에 걸친 문장도 바꿉니다. 새 글은 첫 w:t에 넣고 나머지에서는 옛 글을 지웁니다."""
    count = 0
    for p in root.iter('{%s}p' % W):
        ts = [t for t in p.iter('{%s}t' % W)]
        full = ''.join(t.text or '' for t in ts)
        start = full.find(old)
        while start >= 0:
            end = start + len(old)
            pos, first = 0, True
            for t in ts:
                s = t.text or ''
                a, b = pos, pos + len(s)
                pos = b
                if b <= start or a >= end:
                    continue
                cut0, cut1 = max(start, a) - a, min(end, b) - a
                if first:
                    t.text = s[:cut0] + new + s[cut1:]
                    first = False
                else:
                    t.text = s[:cut0] + s[cut1:]
                if t.text and (t.text[0] == ' ' or t.text[-1] == ' '):
                    t.set('{http://www.w3.org/XML/1998/namespace}space', 'preserve')
            count += 1
            full = ''.join(t.text or '' for t in ts)
            start = full.find(old, start + len(new))
    return count


def set_status_cells(root, rows: list[tuple[str, str]]) -> int:
    n = 0
    for tr in root.iter('{%s}tr' % W):
        cells = tr.findall('{%s}tc' % W)
        texts = [''.join(t.text or '' for t in c.iter('{%s}t' % W)).strip() for c in cells]
        for key, status in rows:
            if len(cells) >= 2 and texts[0] == key:
                ts = list(cells[1].iter('{%s}t' % W))
                if ts:
                    ts[0].text = status
                    for t in ts[1:]:
                        t.text = ''
                    n += 1
    return n


def sdt_counts(root) -> dict[str, int]:
    out = {}
    for sdt in root.iter('{%s}sdt' % W):
        tg = sdt.find('{%s}sdtPr/{%s}tag' % (W, W))
        if tg is None:
            continue
        txt = ''.join(t.text or '' for t in sdt.iter('{%s}t' % W))
        out[tg.get('{%s}val' % W)] = len(txt) + sum(1 for _ in sdt.iter('{%s}br' % W))
    return out


def edit_docx(name: str, out_dir: Path) -> None:
    src = R / name
    dst = out_dir / name
    zin = zipfile.ZipFile(src)
    root = etree.fromstring(zin.read('word/document.xml'))
    before = sdt_counts(root)
    for old, new in TEXT.get(name, []):
        n = replace_in_paragraphs(root, old, new)
        if n != 1:
            raise SystemExit(f'{name}: "{old[:30]}…" {n}곳(1곳이어야 함)')
    if name in TABLE_STATUS:
        n = set_status_cells(root, TABLE_STATUS[name])
        if n != len(TABLE_STATUS[name]):
            raise SystemExit(f'{name}: 표 상태 칸 {n}개만 바뀜')
    after = sdt_counts(root)
    for tag in ('planning_background', 'vehicle_scenario', 'key_features', 'pleos_plan', 'reference_files'):
        if tag in before and before[tag] != after.get(tag):
            raise SystemExit(f'{name}: {tag} 글자 수 변함 {before[tag]} → {after.get(tag)}')
    xml = etree.tostring(root, xml_declaration=True, encoding='UTF-8', standalone=True)
    media = MEDIA.get(name, {})
    with zipfile.ZipFile(dst, 'w') as zout:
        for info in zin.infolist():
            if info.filename == 'word/document.xml':
                data = xml
            elif info.filename in media:
                new_png = media[info.filename]
                old_size = Image.open(zin.open(info.filename)).size
                if Image.open(new_png).size != old_size:
                    raise SystemExit(f'{name}: {info.filename} 크기 다름 {old_size} vs {Image.open(new_png).size}')
                data = new_png.read_bytes()
            else:
                data = zin.read(info.filename)
            zout.writestr(info, data)
    print('docx', dst.relative_to(R), 'counts', {k: v for k, v in after.items() if k in ('planning_background', 'vehicle_scenario', 'key_features', 'pleos_plan', 'reference_files')})


# 원고(build_v4.py 입력)도 같은 내용으로 고칩니다. 그래야 나중에 생성기로 다시 만들어도 되돌아가지 않습니다.
DOCS = R.parent.parent / 'docs/submission'
SOURCES = {
    DOCS / 'official-content-3000/05-development.txt': [
        ('주행 제한은 adb 명령으로 켜서', '주행 제한은 콘솔 기어 D로 켜서'),
        ('지금 끝낸 일은 기획, 화면 시안, API와 데이터 계약 설계이며 앱 코드와 실제 발송은 아직 하지 않았습니다.',
         '지금 끝낸 일은 기획, 계약 설계, 에뮬레이터에서 실행한 데모 앱이고 서버와 실제 발송은 아직 하지 않았습니다.'),
    ],
    DOCS / 'official-form-v4.json': [
        ('포함 자료: 세로·가로 화면 시안,', '포함 자료: 데모 앱 실행 화면과 세로·가로 화면 시안,'),
        ('화면은 기획 시안이며 실제 구동 화면이 아닙니다.', f'그림 3·4와 그림 5 오른쪽은 {RUN}이고, 나머지 화면은 기획 시안입니다.'),
        ('"세로 한 창의 홈과 경고등 찾기.', f'"세로 한 창의 홈과 경고등 찾기({RUN}).'),
        ('"주행 중 긴급 안내와 보호자 발송 확인.', f'"주행 중 긴급 안내와 보호자 발송 확인({RUN}).'),
        ('"출발 전 점검 카드와 보호자 알림 상태.', '"출발 전 점검 카드(설계 시안)와 보호자 알림 상태(데모 앱 실행 화면).'),
        ('"가로 화면의 재현 조건 카드.', '"가로 화면의 재현 조건 카드(설계 시안).'),
        ('assets/v4/composite/form-portrait-1.png', 'assets/app-demo/composite/form-portrait-1.png'),
        ('assets/v4/composite/form-portrait-2.png', 'assets/app-demo/composite/form-portrait-2.png'),
        ('assets/v4/composite/form-portrait-3.png', 'assets/app-demo/composite/form-portrait-3.png'),
    ],
    DOCS / 'annex-v4/annex.md': [
        ('이 문서의 화면은 기획 시안이며 실제 구동 화면이 아닙니다.',
         f'그림 3·4·8·9와 그림 5~7의 왼쪽은 {RUN}이고, 나머지 화면은 기획 시안입니다.'),
        ('기획, 화면 시안, API·데이터 계약 설계를 마쳤습니다. 앱 코드, 실제 알림 발송, 실차 시험은 다음 단계입니다.',
         '기획, 화면 시안, API·데이터 계약 설계와 시연용 데모 앱을 마치고 에뮬레이터에서 실행했습니다. 서버, 실제 알림 발송, 실차 시험은 다음 단계입니다.'),
        ('![주행 중 홈. ', f'![주행 중 홈({RUN}). '),
        ('![세로 한 창의 홈(왼쪽)과 경고등 찾기(오른쪽).', f'![세로 한 창의 홈(왼쪽)과 경고등 찾기(오른쪽). 둘 다 {RUN}입니다.'),
        ('![경고등 설명(왼쪽)과 출발 전 점검(오른쪽).', '![경고등 설명(왼쪽, 데모 앱 실행 화면)과 출발 전 점검(오른쪽, 설계 시안).'),
        ('![지금 기록(왼쪽)과 재현 조건(오른쪽).', '![지금 기록(왼쪽, 데모 앱 실행 화면)과 재현 조건(오른쪽, 설계 시안).'),
        ('![원치 않는 가속 안내(왼쪽)와 비상 절차 미리 익히기(오른쪽).',
         '![원치 않는 가속 안내(왼쪽, 데모 앱 실행 화면)와 비상 절차 미리 익히기(오른쪽, 설계 시안).'),
        ('![확인 질문. ', f'![확인 질문({RUN}. 시험 입력 없이 Virtual Console 차속·브레이크 신호로 떴습니다). '),
        ('![발송 확인 10초(왼쪽)와 보호자 알림 상태(오른쪽).', f'![발송 확인 10초(왼쪽)와 보호자 알림 상태(오른쪽). 둘 다 {RUN}입니다.'),
        ('assets/v4/portrait/p-home-driving.png', 'assets/app-demo/p-home-driving.png'),
        ('assets/v4/composite/pair-home-warning.png', 'assets/app-demo/composite/pair-home-warning.png'),
        ('assets/v4/composite/pair-result-check.png', 'assets/app-demo/composite/pair-result-check.png'),
        ('assets/v4/composite/pair-record-pattern.png', 'assets/app-demo/composite/pair-record-pattern.png'),
        ('assets/v4/composite/pair-emergency-practice.png', 'assets/app-demo/composite/pair-emergency-practice.png'),
        ('assets/v4/portrait/p-confirm.png', 'assets/app-demo/p-confirm.png'),
        ('assets/v4/composite/pair-countdown-guardian.png', 'assets/app-demo/composite/pair-countdown-guardian.png'),
        ('주행 제한에 따른 화면 축소 · adb `driving_regulation on/off`', '주행 제한에 따른 화면 축소 · Virtual Console 기어 D/P'),
        ('주행 제한은 `adb shell cmd hmg_car_service --service drivingstate driving_regulation on`으로 켜고 `off`로 끕니다.',
         '주행 제한은 Virtual Console에서 기어를 D로 바꿔 켜고 P로 끕니다(이 에뮬레이터 이미지에는 `hmg_car_service` 명령이 없음을 2026-11-13에 확인).'),
        ('화면 시안은 기획 시안이며 실제 구동 화면이 아닙니다.', '그림 3~9의 데모 앱 화면은 Pleos 에뮬레이터 실행 화면이고, 나머지 화면은 기획 시안입니다.'),
        ('| 끝냄 | 기획 시안이며 실제 구동 화면이 아닙니다. |', '| 끝냄 | 그림 3~9의 데모 앱 화면은 에뮬레이터 실행 화면이고, 나머지는 기획 시안입니다. |'),
        ('공개 문서와 SDK 패키지를 읽어 확인했습니다. 실제 호출 시험은 다음 단계입니다.',
         '공개 문서와 SDK 패키지를 읽어 확인했고, 에뮬레이터에서 브레이크·차속·주행 제한·음성 호출을 확인했습니다.'),
        ('| 앱 코드(차량·휴대폰·서버) | 다음 단계 | 접수 뒤 바로 시작합니다. |',
         '| 앱 코드(차량·휴대폰·서버) | 일부 끝냄 | 차량 데모 앱은 에뮬레이터에서 실행했습니다. 휴대폰 앱과 서버는 접수 뒤 시작합니다. |'),
        ('| 에뮬레이터에서 SDK 실제 호출 | 다음 단계 | 11월 16일부터 확인합니다. |',
         '| 에뮬레이터에서 SDK 실제 호출 | 끝냄 | 2026-11-13 확인(Vehicle 브레이크, AAOS 차속, CarUxRestrictions, 한국어 TTS). |'),
    ],
}


def update_sources() -> None:
    for path, pairs in SOURCES.items():
        text = path.read_text(encoding='utf-8')
        before = len(text)
        for old, new in pairs:
            if new in text and old not in text:
                continue  # 이미 바꿈
            n = text.count(old)
            if n != 1:
                raise SystemExit(f'{path.name}: "{old[:30]}…" {n}곳(1곳이어야 함)')
            text = text.replace(old, new)
        if path.name == '05-development.txt' and len(text) != before:
            raise SystemExit(f'05-development.txt 글자 수 변함 {before} → {len(text)}')
        path.write_text(text, encoding='utf-8')
        print('source', path.relative_to(DOCS.parent.parent), before, '→', len(text))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', default='qa/v4-r4-app')
    ap.add_argument('--sources', action='store_true', help='원고(05 원고, 공식 양식 설정, 별첨 원고)만 고칩니다')
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding='utf-8')
    if a.sources:
        build_composites(); update_sources(); return
    out_dir = R / a.out
    out_dir.mkdir(parents=True, exist_ok=True)
    build_composites()
    for name in MEDIA:
        edit_docx(name, out_dir)


if __name__ == '__main__':
    main()
