"""플레오스 가드 제출 문서 v4 생성기 (Linux에서 실행 가능, 기존 산출물을 덮어쓰지 않음).

입력
  docs/submission/official-content-3000/0[2-6]-*.txt   공식 양식 2~6번 원고(각 3,000자)
  docs/submission/official-form-v4.json                공식 양식 1·7·8번 문구와 '메뉴 구성과 화면 설계' 그림 목록
  docs/submission/annex-v4/annex.md                    상세 별첨 원고(Markdown)
  output/submission/official-form-original.docx        공모전 원본 양식(바이트 그대로 보존)
출력
  --out 폴더 아래 01·02 DOCX, PDF, 쪽 렌더, verification.json, 제출 ZIP
  --install 을 주면 검증을 통과한 결과만 output/submission/ 의 정식 파일로 복사합니다.

처리 순서
  공식 양식: 원본 양식 + 1~8번 작성란 + 그림 삽입 → restyle_documents.restyle(official=True)
            (페이퍼로지 적용, 03·04 동의서·서명 원본 디자인 복원) → 2~6번 3,000자 원고 반영과 쪽 나눔 허용
  별첨:     annex.md → python-docx 문서 → restyle_documents.restyle() → 링크 색 복원
  PDF:      LibreOffice 변환. 공식 양식의 03·04 쪽은 원본 디자인 보존을 위해, 쪽 번호와 내용이 같을 때
            기존 Word 변환본의 해당 쪽으로 교체합니다(--keep-word-consent 기본값).

주의: build_documents.py · restyle_documents.py · expand_official_content.py 를 직접 실행하지 마세요.
     최신 원고와 디자인이 이전 상태로 돌아갈 수 있습니다. 이 스크립트는 두 모듈의 함수만 빌려 씁니다.
"""
from __future__ import annotations
import argparse, hashlib, json, re, shutil, subprocess, sys, tempfile, unicodedata, zipfile
from copy import deepcopy
from pathlib import Path
from lxml import etree as E

R = Path(__file__).resolve().parent
ROOT = R.parents[1]
A = R / 'assets'
sys.path.insert(0, str(R))
import build_documents as bd  # noqa: E402  (함수만 사용, 모듈 실행 없음)
import restyle_documents as rd  # noqa: E402  (Linux에서 C:/ 글꼴 복사는 건너뜀)
from docx import Document  # noqa: E402
from docx.shared import Cm, Pt  # noqa: E402
from docx.oxml import OxmlElement  # noqa: E402
from docx.oxml.ns import qn  # noqa: E402
from docx.enum.text import WD_ALIGN_PARAGRAPH  # noqa: E402

def _picture(doc, path, caption, width=17.2):
    """build_documents.picture 와 같지만 'ui-' 이름을 assets/final/ 로 바꾸지 않습니다(v4 그림을 그대로 사용)."""
    path = Path(path)
    assert path.exists(), f'그림 없음: {path}'
    p = doc.add_paragraph()
    p.paragraph_format.keep_with_next = True
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.add_run().add_picture(str(path), width=Cm(width))
    bd.FIGURES[id(doc)] = bd.FIGURES.get(id(doc), 0) + 1
    caption = re.sub(r'^그림\s+\d+\.\s*', '', caption)
    cp = doc.add_paragraph(style='Caption')
    bd.inline(cp, f'그림 {bd.FIGURES[id(doc)]}. ' + caption, 9)


bd.picture = _picture

W = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
NS = {'w': W, 'r': 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'}
OFFICIAL_NAME = '01_공식양식_플레오스가드'
ANNEX_NAME = '02_상세기획_기술명세'
CONTENT = ROOT / 'docs/submission/official-content-3000'
FORM_CFG = ROOT / 'docs/submission/official-form-v4.json'
ANNEX_MD = ROOT / 'docs/submission/annex-v4/annex.md'
FILES = ['02-background.txt', '03-features.txt', '04-scenarios.txt', '05-development.txt', '06-impact.txt']
FORBIDDEN = ['차안심', '차량안내', 'C:/Users', 'C:\\Users', 'AGENTS.md', '사용자가 지시', '사용자 지시', 'SHA-256', 'sha256']


def sha(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


def read_field(path: Path) -> str:
    raw = path.read_text(encoding='utf-8').replace('\r\n', '\n').strip()
    assert unicodedata.is_normalized('NFC', raw), f'{path.name}: NFC가 아닙니다'
    return raw


def word_count(s: str) -> int:
    """Word ComputeStatistics(5)와 같은 방식: 글자·공백 + 수동 줄바꿈 1개당 1자."""
    return len(s)


# ---------------------------------------------------------------- 공식 양식
def build_official_prestyle(work: Path, fields: list[str], insert: dict) -> Path:
    """build_documents.build_official 과 같은 처리를 입력·출력 경로를 지정해 수행합니다."""
    ref = R / 'official-form-original.docx'
    z = zipfile.ZipFile(ref)
    parts = {n: z.read(n) for n in z.namelist()}
    rt = E.fromstring(parts['word/document.xml'])
    controls = rt.findall('.//w:sdt', NS)
    assert len(controls) == 8
    for s, body in zip(controls, fields):
        for hl in list(s.findall('.//w:highlight', NS)):
            hl.getparent().remove(hl)
        c = s.find('w:sdtContent', NS)
        ts = c.findall('.//w:t', NS)
        first = ts[0]
        first.text = body.split('\n')[0]
        first.set('{http://www.w3.org/XML/1998/namespace}space', 'preserve')
        for t in ts[1:]:
            t.text = ''
        run = first.getparent()
        for line in body.split('\n')[1:]:
            E.SubElement(run, qn('w:br'))
            t = E.SubElement(run, qn('w:t'))
            t.set('{http://www.w3.org/XML/1998/namespace}space', 'preserve')
            t.text = line
    for p in rt.findall('w:body/w:p', NS):
        ts = p.findall('.//w:t', NS)
        tx = ''.join(t.text or '' for t in ts)
        new = None
        if tx.startswith('참가 단위:'):
            new = tx.replace('□ 개인', '☑ 개인')
        elif tx.startswith('참가 인원:'):
            new = '참가 인원: 1명'
        elif tx.startswith('앱명 / 프로젝트명:'):
            new = '앱명 / 프로젝트명: ' + bd.NAME
        if new is not None and ts:
            ts[0].text = new
            for t in ts[1:]:
                t.text = ''
    # 7번 작성란 표 바로 뒤에 그림 설명 묶음을 넣습니다.
    tmp = Document()
    bd.setup(tmp)
    p = tmp.add_paragraph(insert['heading'])
    bd.font(p.runs[0], 12, True)
    p.paragraph_format.keep_with_next = True
    for i, fig in enumerate(insert['figures']):
        if fig.get('page_break_before') and i:
            tmp.add_page_break()
        bd.picture(tmp, R / fig['image'], fig['caption'], fig.get('width_cm', 17.2))
    for line in insert.get('notes', []):
        bd.inline(tmp.add_paragraph(), line, 9)
    (work / 'qa').mkdir(exist_ok=True)
    tmp_path = work / 'qa' / 'insert.docx'
    tmp.save(tmp_path)
    iz = zipfile.ZipFile(tmp_path)
    ir = E.fromstring(iz.read('word/document.xml'))
    rels = E.fromstring(parts['word/_rels/document.xml.rels'])
    irels = E.fromstring(iz.read('word/_rels/document.xml.rels'))
    remap = {}
    for ri, rel in enumerate(irels):
        typ = rel.get('Type', '')
        if not (typ.endswith('/image') or typ.endswith('/hyperlink')):
            continue
        new = f'rIdCare{ri + 100}'
        remap[rel.get('Id')] = new
        nr = deepcopy(rel)
        nr.set('Id', new)
        if typ.endswith('/image'):
            old = rel.get('Target')
            dest = 'media/care_' + Path(old).name
            nr.set('Target', dest)
            parts['word/' + dest] = iz.read('word/' + old)
        rels.append(nr)
    anchor = controls[6]
    while anchor.tag != qn('w:tbl'):
        anchor = anchor.getparent()
    last = anchor
    for i, node in enumerate(ir.find('w:body', NS)):
        if node.tag == qn('w:sectPr'):
            continue
        node = deepcopy(node)
        for el in node.iter():
            for k, v in list(el.attrib.items()):
                if v in remap and k.startswith('{' + NS['r'] + '}'):
                    el.set(k, remap[v])
            if el.tag.endswith('}docPr'):
                el.set('id', str(2000 + i))
                el.set('name', 'Care figure ' + str(i))
        last.addnext(node)
        last = node
    parts['word/document.xml'] = E.tostring(rt, xml_declaration=True, encoding='UTF-8', standalone=True)
    parts['word/_rels/document.xml.rels'] = E.tostring(rels, xml_declaration=True, encoding='UTF-8', standalone=True)
    ct = E.fromstring(parts['[Content_Types].xml'])
    nsct = 'http://schemas.openxmlformats.org/package/2006/content-types'
    for ext, typ in [('png', 'image/png'), ('jpg', 'image/jpeg'), ('jpeg', 'image/jpeg')]:
        if not any(e.get('Extension') == ext for e in ct):
            E.SubElement(ct, '{' + nsct + '}Default', Extension=ext, ContentType=typ)
    parts['[Content_Types].xml'] = E.tostring(ct, xml_declaration=True, encoding='UTF-8', standalone=True)
    cp = E.fromstring(parts['docProps/core.xml'])
    for e in cp:
        if e.tag.split('}')[-1] in ['creator', 'lastModifiedBy']:
            e.text = ''
    parts['docProps/core.xml'] = E.tostring(cp, xml_declaration=True, encoding='UTF-8', standalone=True)
    out = work / (OFFICIAL_NAME + '.docx')
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zo:
        for n, b in parts.items():
            zo.writestr(n, b)
    return out


def run_restyle(prestyle: Path, out_dir: Path, name: str, official: bool) -> dict:
    """restyle_documents.restyle 을 임시 경로에서 실행합니다(기준본 자동 복사 방지)."""
    base = out_dir / '_restyle_base'
    base.mkdir(parents=True, exist_ok=True)
    shutil.copy2(prestyle, base / (name + '.docx'))
    shutil.copy2(prestyle, out_dir / (name + '.docx'))
    old_r, old_base = rd.R, rd.BASE
    rd.R, rd.BASE = out_dir, base
    try:
        result = rd.restyle(name, official)
    finally:
        rd.R, rd.BASE = old_r, old_base
    return result


def apply_long_fields(docx: Path, texts: list[str]) -> list[dict]:
    """2~6번 작성란에 원고를 넣고 긴 작성란이 쪽을 넘어갈 수 있게 합니다(expand_official_content와 같은 규칙)."""
    with zipfile.ZipFile(docx) as z:
        parts = {n: z.read(n) for n in z.namelist()}
    root = E.fromstring(parts['word/document.xml'])
    controls = root.findall('.//w:sdt', NS)
    assert len(controls) == 8
    counts = []
    for index, raw in enumerate(texts, 1):
        s = controls[index]
        content = s.find('w:sdtContent', NS)
        p = content.find('w:p', NS)
        if p is None:
            p = s.xpath('ancestor::w:p', namespaces=NS)[-1]
        first_run = content.find('.//w:r', NS)
        for c in list(first_run):
            if c.tag != qn('w:rPr'):
                first_run.remove(c)
        for line_no, line in enumerate(raw.split('\n')):
            if line_no:
                E.SubElement(first_run, qn('w:br'))
            t = E.SubElement(first_run, qn('w:t'))
            t.set('{http://www.w3.org/XML/1998/namespace}space', 'preserve')
            t.text = line
        for r in content.findall('.//w:r', NS):
            if r is not first_run:
                for c in list(r):
                    if c.tag != qn('w:rPr'):
                        r.remove(c)
        pp = p.find('w:pPr', NS)
        if pp is None:
            pp = E.Element(qn('w:pPr'))
            p.insert(0, pp)
        for kind in ['keepLines', 'keepNext']:
            for el in list(pp.findall(qn('w:' + kind))):
                pp.remove(el)
            E.SubElement(pp, qn('w:' + kind), {qn('w:val'): '0'})
        for rowprop in s.xpath('ancestor::w:tr/w:trPr', namespaces=NS):
            for el in list(rowprop.findall('w:cantSplit', NS)):
                rowprop.remove(el)
        ml = s.find('w:sdtPr/w:text', NS)
        if ml is not None:
            ml.set(qn('w:multiLine'), '1')
        text_len = len(''.join(s.xpath('.//w:t/text()', namespaces=NS)))
        brs = len(s.findall('.//w:br', NS))
        counts.append({'item': index + 1, 'xml_count_text_plus_breaks': text_len + brs,
                       'text_characters': text_len, 'manual_line_breaks': brs,
                       'paragraphs': len(raw.split('\n\n'))})
    parts['word/document.xml'] = E.tostring(root, xml_declaration=True, encoding='UTF-8', standalone=True)
    with zipfile.ZipFile(docx, 'w', zipfile.ZIP_DEFLATED) as z:
        for n, b in parts.items():
            z.writestr(n, b)
    return counts


def fix_settings(docx: Path):
    """Word에서 열 때 필드 갱신 확인 창이 뜨지 않도록 updateFields를 지웁니다. 링크 색을 복원합니다."""
    with zipfile.ZipFile(docx) as z:
        parts = {n: z.read(n) for n in z.namelist()}
    st = E.fromstring(parts['word/settings.xml'])
    for uf in list(st.findall('w:updateFields', NS)):
        st.remove(uf)
    parts['word/settings.xml'] = E.tostring(st, xml_declaration=True, encoding='UTF-8', standalone=True)
    rt = E.fromstring(parts['word/document.xml'])
    # restyle 이 캡션의 '다음 문단과 함께'를 지우므로 표 제목(표 N.)에는 다시 넣습니다.
    for p in rt.findall('.//w:body/w:p', NS):
        t = ''.join(p.xpath('.//w:t/text()', namespaces=NS))
        if re.match(r'^표 \d+\. ', t):
            pp = p.find('w:pPr', NS)
            if pp is None:
                pp = E.Element(qn('w:pPr'))
                p.insert(0, pp)
            if pp.find('w:keepNext', NS) is None:
                E.SubElement(pp, qn('w:keepNext'))
    for r in rt.findall('.//w:hyperlink//w:r', NS):
        rp = r.find('w:rPr', NS)
        if rp is None:
            rp = E.SubElement(r, qn('w:rPr'))
            r.insert(0, rp)
        col = rp.find('w:color', NS)
        if col is None:
            col = E.SubElement(rp, qn('w:color'))
        col.set(qn('w:val'), '234E70')
        if rp.find('w:u', NS) is None:
            E.SubElement(rp, qn('w:u'), {qn('w:val'): 'single'})
    parts['word/document.xml'] = E.tostring(rt, xml_declaration=True, encoding='UTF-8', standalone=True)
    with zipfile.ZipFile(docx, 'w', zipfile.ZIP_DEFLATED) as z:
        for n, b in parts.items():
            z.writestr(n, b)


# ---------------------------------------------------------------- 별첨
def build_annex_prestyle(work: Path, md_path: Path) -> Path:
    """annex.md 를 python-docx 문서로 만듭니다.

    문법: '% 제목'(표지 제목) · '%% 문장'(표지 부제) · '## '(1단계 제목, 새 쪽) · '### ' · '#### ' ·
          '![캡션](경로){w=8}'(그림, 경로는 output/submission 기준) · '!![캡션](경로1|경로2){w=8}'(두 그림 나란히) ·
          '표: 캡션'(표 제목) · '|' 표 · '- ' 목록 · '<!-- pagebreak -->' · '> ' 참고 문단(작은 글씨)
    """
    doc = Document()
    bd.setup(doc)
    lines = md_path.read_text(encoding='utf-8').replace('\r\n', '\n').split('\n')
    tables = 0
    first_h1 = True
    i = 0
    while i < len(lines):
        s = lines[i]
        if not s.strip() or s.startswith('<!-- ') and 'pagebreak' not in s:
            i += 1
            continue
        if s.strip() == '<!-- pagebreak -->':
            doc.add_page_break()
            i += 1
            continue
        if s.startswith('% '):
            doc.add_paragraph(s[2:].strip(), style='Title')
            i += 1
            continue
        if s.startswith('%% '):
            p = doc.add_paragraph()
            bd.font(p.add_run(s[3:].strip()), 12)
            i += 1
            continue
        m = re.match(r'^!\[(.*)\]\(([^)]+)\)(\{w=([\d.]+)\})?\s*$', s)
        if m:
            bd.picture(doc, R / m[2], m[1], float(m[4]) if m[4] else 17.2)
            i += 1
            continue
        m = re.match(r'^!!\[(.*)\]\(([^)]+)\)(\{w=([\d.]+)\})?\s*$', s)
        if m:
            paths = m[2].split('|')
            width = float(m[4]) if m[4] else 8.2
            t = doc.add_table(rows=1, cols=len(paths))
            for cell, path in zip(t.rows[0].cells, paths):
                cp = cell.paragraphs[0]
                cp.alignment = WD_ALIGN_PARAGRAPH.CENTER
                cp.add_run().add_picture(str(R / path), width=Cm(width))
            bd.FIGURES[id(doc)] = bd.FIGURES.get(id(doc), 0) + 1
            cap = doc.add_paragraph(style='Caption')
            bd.inline(cap, f'그림 {bd.FIGURES[id(doc)]}. ' + re.sub(r'^그림\s+\d+\.\s*', '', m[1]), 9)
            i += 1
            continue
        if s.startswith('표: '):
            tables += 1
            p = doc.add_paragraph(style='Caption')
            bd.inline(p, f'표 {tables}. ' + s[3:].strip(), 9)
            p.paragraph_format.keep_with_next = True
            i += 1
            continue
        if s.startswith('|'):
            rows = []
            while i < len(lines) and lines[i].startswith('|'):
                cells = [x.strip() for x in lines[i].strip().strip('|').split('|')]
                if not all(re.fullmatch(r':?-+:?', c.replace(' ', '')) for c in cells):
                    rows.append(cells)
                i += 1
            bd.table(doc, rows)
            continue
        h = re.match(r'^(#{2,4})\s+(.*)', s)
        if h:
            level = len(h[1]) - 1
            title = h[2]
            nobreak = title.endswith('{nobreak}')
            title = title.replace('{nobreak}', '').strip()
            p = doc.add_paragraph(title, style=f'Heading {level}')
            if level == 1 and not nobreak and not first_h1:
                p.paragraph_format.page_break_before = True
            if level == 1:
                first_h1 = False
            i += 1
            continue
        if s.startswith('```'):
            code = []
            i += 1
            while i < len(lines) and not lines[i].startswith('```'):
                code.append(lines[i])
                i += 1
            for line in code:
                p = doc.add_paragraph()
                p.paragraph_format.space_after = Pt(0)
                rr = p.add_run(line)
                bd.font(rr, 8)
                rr.font.name = 'Consolas'
            i += 1
            continue
        p = doc.add_paragraph()
        if re.match(r'^[-*] ', s):
            s = '• ' + s[2:]
        if s.startswith('> '):
            bd.inline(p, s[2:], 9)
        else:
            bd.inline(p, s)
        i += 1
    p = doc.add_paragraph()
    out = work / (ANNEX_NAME + '.docx')
    doc.save(out)
    return out


# ---------------------------------------------------------------- PDF와 검증
def to_pdf(docx: Path, out_dir: Path) -> Path:
    subprocess.run(['soffice', '--headless', '--convert-to', 'pdf', '--outdir', str(out_dir), str(docx)],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=600)
    return out_dir / (docx.stem + '.pdf')


def pdf_pages(pdf: Path) -> int:
    from pypdf import PdfReader
    return len(PdfReader(str(pdf)).pages)


def page_text(pdf: Path, n: int) -> str:
    return subprocess.run(['pdftotext', '-f', str(n), '-l', str(n), '-layout', str(pdf), '-'],
                          capture_output=True, text=True).stdout


def keep_word_consent(lo_pdf: Path, word_pdf: Path, out_pdf: Path) -> dict:
    """03·04 영역 쪽을 기존 Word 변환본의 같은 쪽으로 바꿉니다. 쪽 번호·내용이 같을 때만 수행합니다."""
    from pypdf import PdfReader, PdfWriter
    lo, wd = PdfReader(str(lo_pdf)), PdfReader(str(word_pdf))
    def consent_pages(path, reader):
        start = None
        for k in range(len(reader.pages)):
            t = page_text(path, k + 1)
            if re.search(r'03\s+개인정보 수집', t):
                start = k
                break
        return start
    ls, ws = consent_pages(lo_pdf, lo), consent_pages(word_pdf, wd)
    info = {'lo_pages': len(lo.pages), 'word_pages': len(wd.pages), 'lo_consent_start': ls, 'word_consent_start': ws}
    norm = lambda t: re.sub(r'\s+', '', t)
    ok = ls is not None and ws is not None and len(lo.pages) - ls == len(wd.pages) - ws and ls == ws
    if ok:
        for k in range(ls, len(lo.pages)):
            if norm(page_text(lo_pdf, k + 1)) != norm(page_text(word_pdf, k + 1)):
                ok = False
                info['text_mismatch_page'] = k + 1
                break
    info['replaced'] = bool(ok)
    w = PdfWriter()
    for k in range(len(lo.pages)):
        w.add_page(wd.pages[k] if ok and k >= ls else lo.pages[k])
    with open(out_pdf, 'wb') as f:
        w.write(f)
    return info


def render(pdf: Path, dest: Path, dpi: int = 80):
    dest.mkdir(parents=True, exist_ok=True)
    subprocess.run(['pdftoppm', '-r', str(dpi), '-png', str(pdf), str(dest / 'page')], check=True)


def docx_text(docx: Path) -> str:
    with zipfile.ZipFile(docx) as z:
        rt = E.fromstring(z.read('word/document.xml'))
    return '\n'.join(''.join(p.xpath('.//w:t/text()', namespaces=NS)) for p in rt.findall('.//w:p', NS))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', required=True)
    ap.add_argument('--stage', default='all', choices=['all', 'official', 'annex'])
    ap.add_argument('--install', action='store_true')
    ap.add_argument('--form-config', default=str(FORM_CFG))
    ap.add_argument('--annex-md', default=str(ANNEX_MD))
    ap.add_argument('--content-dir', default=str(CONTENT))
    args = ap.parse_args()
    out = Path(args.out).resolve()
    out.mkdir(parents=True, exist_ok=True)
    work = out / '_work'
    work.mkdir(exist_ok=True)
    report = {'generator': 'output/submission/build_v4.py', 'converter': 'LibreOffice (soffice --headless)',
              'counting': 'XML 계산값(작성란 w:t 글자 수 + w:br 개수). Word ComputeStatistics(5) 실측이 아님',
              'documents': {}}
    # 2026-11-12 Microsoft Word로 변환한 공식 양식 PDF(03·04 원본 디자인 기준본). 덮어쓰지 않습니다.
    word_pdf_ref = R / 'qa/word-reference/01_공식양식_플레오스가드-word-2026-11-12.pdf'
    if args.stage in ('all', 'official'):
        cfg = json.loads(Path(args.form_config).read_text(encoding='utf-8'))
        texts = [read_field(Path(args.content_dir) / f) for f in FILES]
        for f, t in zip(FILES, texts):
            assert word_count(t) == 3000, (f, word_count(t))
            assert len(t.split('\n\n')) == 12, (f, len(t.split('\n\n')))
            assert not any(x in t for x in FORBIDDEN), f
        fields = [cfg['item1']] + texts + [cfg['item7'], cfg['item8']]
        pre = build_official_prestyle(work, fields, cfg['insert'])
        styled_dir = work / 'official'
        styled_dir.mkdir(exist_ok=True)
        res = run_restyle(pre, styled_dir, OFFICIAL_NAME, True)
        docx = styled_dir / (OFFICIAL_NAME + '.docx')
        counts = apply_long_fields(docx, texts)
        fix_settings(docx)
        for c in counts:
            assert c['xml_count_text_plus_breaks'] == 3000, c
        final = out / (OFFICIAL_NAME + '.docx')
        shutil.copy2(docx, final)
        lo_dir = work / 'lo'
        lo_dir.mkdir(exist_ok=True)
        lo_pdf = to_pdf(final, lo_dir)
        pdf = out / (OFFICIAL_NAME + '.pdf')
        consent = keep_word_consent(lo_pdf, word_pdf_ref, pdf)
        render(pdf, out / 'render' / '01')
        text = docx_text(final)
        report['documents']['official'] = {
            'docx': final.name, 'docx_sha256': sha(final.read_bytes()), 'pdf_sha256': sha(pdf.read_bytes()),
            'pdf_pages': pdf_pages(pdf), 'field_counts_2_6': counts,
            'item1_chars': len(cfg['item1']), 'item7_xml_count': len(cfg['item7']),
            'consent_pages_from_word_pdf': consent, 'restyle_paragraphs': res['paragraphs'],
            'forbidden_strings_found': [x for x in FORBIDDEN if x in text]}
    if args.stage in ('all', 'annex'):
        pre = build_annex_prestyle(work, Path(args.annex_md))
        styled_dir = work / 'annex'
        styled_dir.mkdir(exist_ok=True)
        res = run_restyle(pre, styled_dir, ANNEX_NAME, False)
        docx = styled_dir / (ANNEX_NAME + '.docx')
        fix_settings(docx)
        final = out / (ANNEX_NAME + '.docx')
        shutil.copy2(docx, final)
        pdf = to_pdf(final, out)
        render(pdf, out / 'render' / '02')
        text = docx_text(final)
        report['documents']['annex'] = {
            'docx': final.name, 'docx_sha256': sha(final.read_bytes()), 'pdf_sha256': sha(pdf.read_bytes()),
            'pdf_pages': pdf_pages(pdf), 'restyle_paragraphs': res['paragraphs'],
            'forbidden_strings_found': [x for x in FORBIDDEN if x in text]}
    if args.stage == 'all':
        zpath = out / '플레오스가드_제출문서_Word_PDF.zip'
        with zipfile.ZipFile(zpath, 'w', zipfile.ZIP_DEFLATED) as z:
            for n in [OFFICIAL_NAME + '.docx', OFFICIAL_NAME + '.pdf', ANNEX_NAME + '.docx', ANNEX_NAME + '.pdf']:
                z.write(out / n, n)
        report['zip_sha256'] = sha(zpath.read_bytes())
    (out / 'verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if args.install:
        bad = [k for k, v in report['documents'].items() if v['forbidden_strings_found']]
        assert not bad, f'금지 문자열 발견: {bad}'
        for n in [OFFICIAL_NAME + '.docx', OFFICIAL_NAME + '.pdf', ANNEX_NAME + '.docx', ANNEX_NAME + '.pdf',
                  '플레오스가드_제출문서_Word_PDF.zip']:
            if (out / n).exists():
                shutil.copy2(out / n, R / n)


if __name__ == '__main__':
    main()
