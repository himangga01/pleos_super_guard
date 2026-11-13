"""v4 도표 생성기: 페이퍼로지 문서 디자인과 같은 색·글꼴로 별첨·공식 양식용 도표 4장을 만듭니다.

출력: assets/v4/diagrams/{menu-flow,architecture,guidance-state,notification-state}-v4.png
기존 assets/paperlogy-diagrams/ 는 덮어쓰지 않습니다. restyle_documents.py 의 그리기 함수만 빌려 씁니다.
"""
from pathlib import Path
import sys
from PIL import Image, ImageDraw

R = Path(__file__).resolve().parent
sys.path.insert(0, str(R))
import restyle_documents as rd  # noqa: E402

DEST = R / 'assets/v4/diagrams'
TEXT, MUTED, BLUE, LINE, FILL = rd.TEXT, rd.MUTED, rd.BLUE, rd.LINE, rd.FILL
RED_FILL, RED_LINE = '#FBEDEF', '#E3A9B3'


def flow(name, rows, height=440):
    im = Image.new('RGB', (1500, height), 'white')
    d = ImageDraw.Draw(im)
    for i, (title, lines) in enumerate(rows):
        x = 30 + i * 300
        d.rounded_rectangle((x, 48, x + 260, height - 50), 12, fill=FILL, outline=LINE, width=2)
        rd.label(d, (x + 130, 95), f'{i + 1:02}', 25, True, BLUE)
        rd.label(d, (x + 130, 165), title, 29, True)
        for j, s in enumerate(lines.split('\n')):
            rd.label(d, (x + 130, 232 + j * 40), s, 23, fill=MUTED)
        if i < len(rows) - 1:
            rd.arrow(d, (x + 262, height // 2), (x + 298, height // 2))
    im.save(DEST / name)


def states(name, nodes, edges, size=(1640, 900), danger=()):
    im = Image.new('RGB', size, 'white')
    d = ImageDraw.Draw(im)
    pos = {k: (30 + c * 410, 80 + r * 300) for k, (c, r, _) in nodes.items()}
    labels = []
    for src, dst, s in edges:
        x, y = pos[src]
        xx, yy = pos[dst]
        a = (x + 140, y + 110)
        b = (xx + 140, yy)
        if y == yy:
            a = (x + 280, y + 55) if xx > x else (x, y + 55)
            b = (xx, y + 55) if xx > x else (xx + 280, y + 55)
        elif yy < y:
            a = (x + 140, y)
            b = (xx + 140, yy + 110)
        rd.arrow(d, a, b)
        if s:
            labels.append(((a[0] + b[0]) / 2, y - 23 if y == yy else (a[1] + b[1]) / 2, s))
    for k, (_, _, s) in nodes.items():
        x, y = pos[k]
        fill, line = (RED_FILL, RED_LINE) if k in danger else (FILL, LINE)
        d.rounded_rectangle((x, y, x + 280, y + 110), 12, fill=fill, outline=line, width=2)
        rd.label(d, (x + 140, y + 55), s, 27, True)
    for x, y, s in labels:
        bb = d.textbbox((x, y), s, font=rd.f(23), anchor='mm')
        d.rectangle((bb[0] - 6, bb[1] - 5, bb[2] + 6, bb[3] + 5), fill='white')
        rd.label(d, (x, y), s, 23, fill=MUTED)
    im.save(DEST / name)


def main():
    DEST.mkdir(parents=True, exist_ok=True)
    flow('menu-flow-v4.png', [
        ('경고등 찾기', '색 · 모양 · 말로 찾기\n차가 알려 준 점검 항목'),
        ('증상 기록', '지금 기록 · 차량 상태\n재현 조건 카드'),
        ('긴급 안내', '운전자 직접 알림\n정지 그림 + 짧은 음성'),
        ('보호자 알림', '10초 확인 · 지금 도움 요청\n먼저 할 일 · 빠른 답'),
        ('정비소 연결', '가는 길에 경유지 추가\nQR 24시간 요약'),
    ])
    flow('architecture-v4.png', [
        ('차량 입력', '공개 SDK 5종\nAAOS 차속 · 운전자 선택'),
        ('판단과 기록', '출처 · 받은 시각 · 품질\n주행 제한 상태'),
        ('Pleos 앱 화면', '세로 한 창 · 가로 화면\n음성 안내 · 음성 입력'),
        ('서버', '기록 · 동의 · 위치 제공 기록\n알림톡 → 문자 전환'),
        ('받는 사람', '보호자 알림 화면\n정비사 요약 · 휴대폰 앱'),
    ])
    states('guidance-state-v4.png', {
        'O': (0, 0, '상태 관찰'), 'Q': (1, 0, '확인 질문(화면·말)'), 'X': (2, 0, '안내 끝내기'),
        'R': (0, 1, '운전자 직접 알림'), 'A': (1, 1, '원치 않는 가속 안내'), 'S': (2, 1, '감속 실패 대처'),
        'E': (3, 1, '정차 후 확인'), 'Y': (1, 2, '안내 끝내기'),
        'M': (0, 3, '상황 직접 선택'), 'H': (2, 3, '보호자 발송 확인'),
    }, [
        ('O', 'Q', '센서 감지'), ('Q', 'X', '괜찮아요'), ('Q', 'S', '안 멈춰요'), ('R', 'A', '저절로 빨라져요'),
        ('A', 'S', '안 멈춰요'), ('S', 'E', '차가 멈췄어요'), ('A', 'Y', '괜찮아요'), ('M', 'H', '사고가 났어요 · 지금 도움 요청'),
        ('H', 'E', ''),
    ], size=(1640, 1200), danger=('A', 'S'))
    states('notification-state-v4.png', {
        'L': (0, 0, '기록 저장'), 'C': (1, 0, '10초 확인'), 'E': (2, 0, '발송 시작'), 'D': (3, 0, '발송 중'),
        'R': (0, 1, '기록 · 선택 시 알림'), 'X': (1, 1, '보내지 않음'), 'B': (2, 1, '받는 사람 없음'), 'S': (3, 1, '발송 종료'),
        'T': (3, 2, '정정 알림'),
    }, [
        ('L', 'C', 'L3 자동'), ('L', 'R', 'L1 · L2'), ('C', 'E', '만료 · 지금 보내기'), ('C', 'X', '괜찮아요'),
        ('E', 'D', ''), ('E', 'B', '보호자 · 동의 없음'), ('D', 'S', '전달 확인'), ('S', 'T', '발송 뒤 괜찮아요'),
    ], danger=('C', 'D'))
    print('diagrams ->', DEST)


if __name__ == '__main__':
    main()
