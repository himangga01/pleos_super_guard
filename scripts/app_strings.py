"""앱 화면 문구(Kotlin 문자열)를 세종엔진에 넣을 글로 뽑고, 윤문 결과를 소스에 되돌려 넣습니다(2026-11-13).

  extract <출력.txt> <지도.json>                    한글이 든 화면 문구를 한 문단씩(빈 줄로 나눔) 적습니다.
  apply   <지도.json> <윤문.txt> <보고.md> [--dry] [--only 번호,번호…] [--skip 번호,번호…]
                                                    바뀐 문구를 뽑을 때 기록한 자리에만 넣습니다.

고르지 않는 것: 주석, 로그·예외 문구, 비교·검색에 쓰는 낱말(.contains, `in t`, 낱말 목록 줄, "a" to listOf(...)),
음성 판정(Rules.kt의 VoiceAnswerParser·VoiceRouter), 시험 명령. 문자열 템플릿(${...}, $이름)과 printf 자리(%s, %.1f)는
{1}, {2} 같은 자리표로 바꿔 보내고, 돌아온 글에 자리표가 그대로 있어야 받아들입니다. 문구 앞뒤 공백과 줄바꿈 구조는 지킵니다.
같은 문구가 비교에도 쓰이는지는 apply 보고서의 "다른 자리" 목록으로 사람이 확인합니다.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / 'app/src/main/java/app/pleosguard/demo'
HANGUL = re.compile('[가-힣]')
SKIP_FILES = {'ui/GuardIcons.kt', 'ui/LucidePaths.kt', 'ui/Theme.kt'}
SKIP_LINE = re.compile(
    r'\bLog\.[a-z]\(|\bthrow\b|\berror\(|\brequire\(|\bcheck\(|\.contains\(|\bin t\b|\bto listOf\(|getStringExtra|"cmd"'
    r'|^\s*listOf\((\s*"[^"]*"\s*,?)+\)\)?,?\s*$')
SKIP_BLOCKS = ('object VoiceAnswerParser', 'object VoiceRouter', 'fun parseSpoken')
PRINTF = re.compile(r'%[-#+ 0,(]*\d*(?:\.\d+)?[sdfx%]')


def lex(src: str):
    """Kotlin 소스에서 문자열 리터럴 위치와 조각(글·템플릿)을 찾습니다. 주석은 건너뜁니다."""
    i, n = 0, len(src)
    out = []
    while i < n:
        c = src[i]
        if src.startswith('//', i):
            i = src.find('\n', i)
            i = n if i < 0 else i
        elif src.startswith('/*', i):
            depth, i = 1, i + 2
            while i < n and depth:
                if src.startswith('/*', i):
                    depth, i = depth + 1, i + 2
                elif src.startswith('*/', i):
                    depth, i = depth - 1, i + 2
                else:
                    i += 1
        elif c == "'":
            j = i + 1
            while j < n and src[j] != "'":
                j += 2 if src[j] == '\\' else 1
            i = j + 1
        elif c == '"':
            raw = src.startswith('"""', i)
            start = i
            i, parts = string_body(src, i + (3 if raw else 1), raw)
            out.append((start, i, raw, parts))
        else:
            i += 1
    return out


def string_body(src: str, i: int, raw: bool):
    parts, buf = [], []
    n = len(src)
    while i < n:
        if raw and src.startswith('"""', i):
            i += 3
            break
        c = src[i]
        if not raw and c == '"':
            i += 1
            break
        if not raw and c == '\\':
            esc = src[i + 1]
            buf.append({'n': '\n', 't': '\t', '"': '"', '\\': '\\', '$': '$', "'": "'"}.get(esc, '\\' + esc))
            i += 2
            continue
        if c == '$' and i + 1 < n and src[i + 1] == '{':
            j, depth = i + 2, 1
            while j < n and depth:
                if src[j] == '{':
                    depth += 1
                elif src[j] == '}':
                    depth -= 1
                elif src[j] == '"':
                    j = string_body(src, j + 1, False)[0] - 1
                j += 1
            if buf:
                parts.append(('text', ''.join(buf))); buf = []
            parts.append(('tmpl', src[i:j]))
            i = j
            continue
        if c == '$' and i + 1 < n and (src[i + 1].isalpha() or src[i + 1] == '_'):
            j = i + 1
            while j < n and (src[j].isalnum() or src[j] == '_'):
                j += 1
            if buf:
                parts.append(('text', ''.join(buf))); buf = []
            parts.append(('tmpl', src[i:j]))
            i = j
            continue
        buf.append(c)
        i += 1
    if buf:
        parts.append(('text', ''.join(buf)))
    return i, parts


def to_engine(parts) -> tuple[str, list[list[str]]]:
    """조각을 엔진에 보낼 글로 바꿉니다. 템플릿과 printf 자리는 {1}, {2}… 자리표가 됩니다. tmpls 항목: [종류, 원문]."""
    text, tmpls = '', []
    for kind, s in parts:
        if kind == 'text':
            pos = 0
            for m in PRINTF.finditer(s):
                text += s[pos:m.start()]
                tmpls.append(['printf', m.group()])
                text += '{%d}' % len(tmpls)
                pos = m.end()
            text += s[pos:]
        else:
            tmpls.append(['code', s])
            text += '{%d}' % len(tmpls)
    return text, tmpls


def to_kotlin(text: str, tmpls: list[list[str]], raw: bool) -> str:
    out = ''
    for piece in re.split(r'(\{\d+\})', text):
        m = re.fullmatch(r'\{(\d+)\}', piece)
        if m:
            out += tmpls[int(m.group(1)) - 1][1]
        elif raw:
            out += piece
        else:
            out += piece.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n').replace('$', '\\$')
    return ('"""' + out + '"""') if raw else ('"' + out + '"')


def in_skip_block(src: str, start: int) -> bool:
    """함수·객체 본문(중괄호 짝) 안이면 true."""
    for head in SKIP_BLOCKS:
        k = src.rfind(head, 0, start)
        if k < 0:
            continue
        b = src.find('{', k)
        depth, j = 0, b
        while j < len(src):
            if src[j] == '{':
                depth += 1
            elif src[j] == '}':
                depth -= 1
                if depth == 0:
                    break
            j += 1
        if b <= start <= j:
            return True
    return False


def collect():
    items: dict[str, dict] = {}
    skipped: dict[str, list[str]] = {}
    for f in sorted(ROOT.rglob('*.kt')):
        rel = f.relative_to(ROOT).as_posix()
        if rel in SKIP_FILES:
            continue
        src = f.read_text(encoding='utf-8')
        for start, end, raw, parts in lex(src):
            text, tmpls = to_engine(parts)
            if not HANGUL.search(text):
                continue
            key = src[start:end]
            line_start = src.rfind('\n', 0, start) + 1
            line_end = src.find('\n', end)
            line = src[line_start:line_end if line_end >= 0 else len(src)]
            where = f'{rel}:{src.count(chr(10), 0, start) + 1}'
            if SKIP_LINE.search(line) or in_skip_block(src, start):
                skipped.setdefault(key, []).append(where)
                continue
            it = items.setdefault(key, {'literal': key, 'raw': raw, 'text': text, 'tmpls': tmpls, 'where': [], 'spans': []})
            it['where'].append(where)
            it['spans'].append([rel, start, end])
    return list(items.values()), skipped


def shape(text: str):
    """줄마다 (앞 공백, 본문, 뒤 공백). 빈 줄은 엔진에 보내지 않습니다."""
    out = []
    for line in text.split('\n'):
        core = line.strip()
        lead = line[:len(line) - len(line.lstrip())]
        trail = line[len(line.rstrip()):]
        out.append([lead, core, trail])
    return out


def cmd_extract(out_txt: Path, out_map: Path) -> None:
    items, skipped = collect()
    paras = []
    for it in items:
        it['lines'] = shape(it['text'])
        paras += [core for _, core, _ in it['lines'] if core]
    out_txt.write_text('\n\n'.join(paras), encoding='utf-8')
    out_map.write_text(json.dumps({'items': items, 'skipped': skipped}, ensure_ascii=False, indent=1), encoding='utf-8')
    print(f'{len(items)} literals, {len(paras)} paragraphs, {sum(map(len, paras))} chars, skipped literals {len(skipped)}')


def placeholders(s: str) -> list[str]:
    return sorted(re.findall(r'\{\d+\}', s))


def cmd_apply(map_json: Path, rewritten: Path, report: Path, dry: bool, only: set[int] | None, skip: set[int]) -> None:
    data = json.loads(map_json.read_text(encoding='utf-8'))
    items, skipped = data['items'], data['skipped']
    new_paras = rewritten.read_text(encoding='utf-8').strip('\n').split('\n\n')
    total = sum(1 for it in items for _, core, _ in it['lines'] if core)
    if len(new_paras) != total:
        raise SystemExit(f'문단 수가 다릅니다: 원문 {total}, 윤문 {len(new_paras)}')
    k, changes, refused = 0, [], []
    for n, it in enumerate(items):
        rebuilt = []
        for lead, core, trail in it['lines']:
            if core:
                rebuilt.append(lead + new_paras[k].replace('\n', ' ').strip() + trail)
                k += 1
            else:
                rebuilt.append(lead + trail)
        new = '\n'.join(rebuilt)
        if new == it['text']:
            continue
        if placeholders(new) != placeholders(it['text']):
            refused.append((n, it, new, '자리표가 바뀜'))
            continue
        changes.append((n, it, new))
    applied = [(n, it, new) for n, it, new in changes if (only is None or n in only) and n not in skip]
    edits: dict[str, list[tuple[int, int, str]]] = {}
    for n, it, new in applied:
        lit = to_kotlin(new, it['tmpls'], it['raw'])
        for rel, start, end in it['spans']:
            edits.setdefault(rel, []).append((start, end, lit))
    for rel, es in edits.items():
        f = ROOT / rel
        src = f.read_text(encoding='utf-8')
        for start, end, lit in sorted(es, reverse=True):
            src = src[:start] + lit + src[end:]
        if not dry:
            f.write_bytes(src.encode('utf-8'))
    lines = [f'# 앱 문구 세종엔진 반영 기록\n\n엔진이 바꾼 문구 {len(changes)}개, 적용 {len(applied)}개, 자리표 때문에 거절 {len(refused)}개\n']
    for n, it, new in changes:
        mark = '적용' if (n, it, new) in applied else '보류'
        other = skipped.get(it['literal'])
        lines.append(f'- [{n}] {mark} · {", ".join(it["where"])}' + (f' · 다른 자리(비교·검색): {", ".join(other)}' if other else ''))
        lines.append(f'  - 원문: {it["text"]!r}\n  - 결과: {new!r}')
    for n, it, new, why in refused:
        lines.append(f'- [{n}] 거절({why}) · {", ".join(it["where"])}\n  - 원문: {it["text"]!r}\n  - 결과: {new!r}')
    report.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(f'changed {len(changes)}, applied {len(applied)}, refused {len(refused)}{" (dry run)" if dry else ""}')


def ids(arg: str | None) -> set[int] | None:
    return None if arg is None else {int(x) for x in arg.split(',') if x}


def main() -> None:
    sys.stdout.reconfigure(encoding='utf-8')
    a = sys.argv[1:]
    if a and a[0] == 'extract':
        cmd_extract(Path(a[1]), Path(a[2]))
    elif a and a[0] == 'apply':
        only = ids(a[a.index('--only') + 1]) if '--only' in a else None
        skip = ids(a[a.index('--skip') + 1]) if '--skip' in a else set()
        cmd_apply(Path(a[1]), Path(a[2]), Path(a[3]), '--dry' in a, only, skip or set())
    else:
        raise SystemExit(__doc__)


if __name__ == '__main__':
    main()
