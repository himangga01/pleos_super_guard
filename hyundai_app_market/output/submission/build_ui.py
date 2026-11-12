from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import math
R=Path(__file__).parent; A=R/'assets'; A.mkdir(exist_ok=True)
FONT='C:/Windows/Fonts/malgun.ttf'; BOLD='C:/Windows/Fonts/malgunbd.ttf'
def f(n,b=False):return ImageFont.truetype(BOLD if b else FONT,n)
BG='#0A0A0C'; CARD='#1C1C20'; TXT='#FFFFFF'; MUT='#C7CBD4'; PINK='#FF1F8F'; AMBER='#FFC04D'
def text(d,xy,t,n=26,c=TXT,b=False):d.text(xy,t,font=f(n,b),fill=c,spacing=10)
def box(d,rect,fill=CARD,r=20,outline=None):d.rounded_rectangle(rect,radius=r,fill=fill,outline=outline,width=2)
def base(title,active='홈',state='정차 중'):
 im=Image.new('RGB',(1280,720),BG);d=ImageDraw.Draw(im)
 text(d,(24,24),'차량안내',32,b=True);text(d,(336,24),title,32,b=True)
 text(d,(960,32),'화면 설계안',24,c=MUT)
 box(d,(24,112,320,584));text(d,(48,138),'디 올 뉴 아반떼',26,b=True)
 text(d,(48,182),state,24,c='#75E6AA' if state=='정차 중' else AMBER)
 for i,s in enumerate(['홈','경고등 찾기','증상 기록','상황 직접 선택','기록 보기']):
  y=238+i*66
  if s==active:box(d,(40,y-2,304,y+62),PINK,16);color=BG
  else:color=MUT
  text(d,(58,y+10),s,24,c=color,b=s==active)
 return im,d
def actions(d,left,right):
 box(d,(24,608,632,696),PINK,28);box(d,(648,608,1256,696),CARD,28)
 text(d,(58,630),left,30,c=BG,b=True);text(d,(680,630),right,30,b=True)
im,d=base('필요한 도움을 선택하세요')
for i,(title,desc) in enumerate([('경고등 찾기','모양 · 문구 · 사진\n내 차의 공식 안내'),('지금 증상 기록','잠깐 나타난 이상\n발생 전후 구간 보관'),('상황 직접 선택','센서 정보가 없어도\n직접 상황을 선택'),('기록 보기','기록을 다시 보고\n정비소에 설명')]):
 x=336+(i%2)*312;y=112+(i//2)*244;box(d,(x,y,x+296,y+228));text(d,(x+24,y+32),title,28,b=True);text(d,(x+24,y+92),desc,21,c=MUT)
box(d,(960,112,1256,584));text(d,(984,140),'보호자와 백업',27,b=True)
for i,s in enumerate(['보호자 1   등록됨','보호자 2   등록됨','휴대폰     연결됨']):text(d,(984,216+i*84),s,23,c=MUT)
text(d,(984,490),'연결 상태는 예시입니다',18,c=MUT);actions(d,'지금 증상 기록','상황 직접 선택');im.save(A/'ui-home.png')
im,d=base('현재 상황을 선택하세요','상황 직접 선택','차량 정보 확인 불가')
labels=['차체 떨림','핸들 쏠림','이상한 소음','타는 냄새','페달 이상','타이어 손상','가벼운 접촉','단독 충돌','출력 저하']
for i,t in enumerate(labels):
 x=336+i%3*312;y=112+i//3*160;box(d,(x,y,x+296,y+144),PINK if i==0 else CARD)
 text(d,(x+22,y+24),f'{i+1:02}',21,c=BG if i==0 else MUT);text(d,(x+22,y+74),t,28,c=BG if i==0 else TXT,b=True)
actions(d,'이 상황 안내','지금 도움 요청');im.save(A/'ui-manual.png')
im,d=base('경고등과 공식 안내','경고등 찾기')
box(d,(336,112,944,584));text(d,(360,140),'타이어 공기압 표시',30,b=True)
text(d,(360,202),'공식 설명',24,c=PINK,b=True);text(d,(360,248),'차량 사양과 표시 상태에 맞는 내용을 확인합니다.',21)
text(d,(360,308),'대처 순서',24,c=PINK,b=True)
for i,s in enumerate(['1  안전한 곳에서 차량 상태 확인','2  표시 조건과 공식 안내 대조','3  필요한 점검과 정비소 연결']):text(d,(360,354+i*57),s,23)
box(d,(960,112,1256,584));text(d,(984,142),'찾는 방법',27,b=True)
for i,s in enumerate(['모양으로 찾기','문구로 찾기','사진으로 찾기']):text(d,(984,226+i*76),s,24)
text(d,(984,502),'정차 후 사진 입력',21,c=MUT);actions(d,'공식 설명서 보기','가까운 정비소');im.save(A/'ui-warning.png')
im,d=base('순간 증상 기록','증상 기록')
box(d,(336,112,1256,584));text(d,(360,138),'최근 구간을 보관하고 있습니다',30,b=True)
text(d,(360,194),'소리 · 진동 · 차량 상태는 사용 가능한 항목만 기록합니다',23,c=MUT)
for j,col in enumerate([PINK,'#75E6AA']):
 pts=[]
 for x in range(375,1215):
  t=(x-375)/840;env=.25+1.2*math.exp(-((t-.58)/.10)**2);y=310+j*110+math.sin(t*75)*env*26;pts.append((x,y))
 d.line(pts,fill=col,width=3)
text(d,(360,470),'사건 전 30초  |  발생 시점  |  사건 후 60초',24,b=True)
text(d,(360,524),'파형은 합성 예시이며 실제 센서 측정값이 아닙니다',19,c=MUT);actions(d,'이 구간 보관','증상 설명 추가');im.save(A/'ui-record.png')
im,d=base('긴급 안내','상황 직접 선택','정지 요청 확인')
box(d,(336,112,944,584));text(d,(360,136),'페달 위치를 확인하세요',30,b=True)
for x,w,h,label in [(480,140,130,'브레이크'),(720,80,190,'가속 페달')]:
 box(d,(x,238,x+w,238+h),'#5F6571',18,TXT);text(d,(x-4,450),label,24)
d.line([(780,410),(600,410)],fill=PINK,width=9);d.polygon([(600,410),(630,390),(630,430)],fill=PINK)
text(d,(360,522),'음성과 같은 단계의 설명 그림',22,c=MUT)
box(d,(960,112,1256,584));text(d,(984,142),'현재 단계',27,b=True)
for i,s in enumerate(['1  페달 확인','2  입력 상태 확인','3  감속 여부 확인']):text(d,(984,236+i*76),s,23,c=PINK if i==0 else MUT)
actions(d,'브레이크를 밟아도 안 멈춤','지금 도움 요청');im.save(A/'ui-guidance.png')
im,d=base('보호자에게 알릴까요','상황 직접 선택','사고 관련 신호 수신')
box(d,(336,112,944,584));text(d,(480,150),'10초',80,b=True);text(d,(374,272),'응답이 없으면 등록 보호자에게 알립니다',25)
text(d,(374,340),'관측 신호와 위치를 먼저 보냅니다',25,c=MUT);text(d,(374,406),'심각도는 추정이며 부상 정도는 미확인입니다',21,c=MUT)
text(d,(374,500),'카카오톡 우선  →  전달 미확인 시 문자',24,b=True)
box(d,(960,112,1256,584));text(d,(984,142),'알림 대상',27,b=True)
for i,s in enumerate(['보호자 1','보호자 2','보호자 3']):
 y=230+i*90;d.ellipse((984,y,1036,y+52),fill='#5F6571');text(d,(1050,y+4),s,23);text(d,(1050,y+36),'발송 전',18,c=MUT)
actions(d,'지금 즉시 발송','괜찮습니다');im.save(A/'ui-countdown.png')
# Deterministic diagrams, not AI-generated technical labels.
def flow(name,rows):
 im=Image.new('RGB',(1500,420),'white');d=ImageDraw.Draw(im)
 for i,(a,b) in enumerate(rows):
  x=30+i*300;box(d,(x,40,x+270,360),'#F2F4F7',18,'#D9D9D9');text(d,(x+24,68),f'{i+1:02}',28,c='#374151',b=True);text(d,(x+24,146),a,31,c='#111111',b=True)
  for j,s in enumerate(b.split('\n')):text(d,(x+24,220+j*42),s,23,c='#374151')
  if i<len(rows)-1:d.line((x+272,200,x+296,200),fill='#BD176B',width=5);d.polygon([(x+296,200),(x+284,191),(x+284,209)],fill='#BD176B')
 im.save(A/name)
flow('menu-flow.png',[('홈','기능 선택\n연결 상태'),('이상 찾기','도감 · 사진\n상황 직접 선택'),('기록과 안내','자료 구간 보관\n음성 · 화면 설명'),('발송 확인','10초 확인\n즉시 발송 · 취소'),('보호자 확인','카카오톡 · 문자\n위치 · 받은 자료')])
flow('architecture.png',[('차량 입력','허용된 SDK\n사용자 상황 선택'),('품질과 판정','출처 · 시각 확인\n검토된 가이드'),('Pleos 앱','안내 · 기록\n휴대폰 백업'),('서버 처리','사건 · 발송 정책\n보관 · 권한'),('외부 전달','카카오톡 → 문자\n보호자 위치 화면')])
def state_diagram(name,nodes,edges):
 im=Image.new('RGB',(1640,900),'white');d=ImageDraw.Draw(im)
 positions={k:(30+c*400,45+r*300) for k,(c,r,label) in nodes.items()}
 for src,dst,label in edges:
  x,y=positions[src];xx,yy=positions[dst];a=(x+150,y+110);b=(xx+150,yy)
  if y==yy:
   a=(x+300,y+55) if xx>x else (x,y+55)
   b=(xx,y+55) if xx>x else (xx+300,y+55)
  d.line([a,b],fill='#8793A4',width=4)
  theta=math.atan2(b[1]-a[1],b[0]-a[0]);d.polygon([b,(b[0]-14*math.cos(theta-.5),b[1]-14*math.sin(theta-.5)),(b[0]-14*math.cos(theta+.5),b[1]-14*math.sin(theta+.5))],fill='#8793A4')
  if label:
   cx=(a[0]+b[0])/2;cy=(a[1]+b[1])/2;bb=d.textbbox((0,0),label,font=f(22));w=bb[2];d.rectangle((cx-w/2-6,cy-14,cx+w/2+6,cy+20),fill='white');text(d,(cx-w/2,cy-14),label,22,c='#374151')
 for k,(c,r,label) in nodes.items():
  x,y=positions[k];box(d,(x,y,x+300,y+110),'#F2F4F7',18,'#D9D9D9');text(d,(x+20,y+22),label,31,c='#111111',b=True)
 im.save(A/name)
state_diagram('guidance-state.png',{'O':(0,0,'상태 관측'),'A':(1,0,'급가속 후보'),'P':(2,0,'페달 안내'),'B':(3,0,'브레이크 감시'),'M':(1,1,'수동 상황 선택'),'S':(3,1,'비상 제동 안내'),'H':(2,2,'도움 요청 안내'),'E':(3,2,'정차 후 확인')},[('O','A',''),('A','P','조건 확인'),('P','B','입력 확인'),('O','M','자료 무효'),('B','S','감속 미관측'),('M','S','정지 실패'),('M','H','일반 도움'),('S','E','정차'),('H','E','')])
state_diagram('notification-state.png',{'E':(0,0,'사건 생성'),'C':(1,0,'10초 확인'),'R':(2,0,'발송 해제'),'D':(3,0,'발송사 요청'),'O':(0,1,'기록만 보관'),'X':(1,1,'발송 취소'),'F':(2,1,'문자 대체'),'P':(3,1,'전달 결과 확인'),'N':(2,2,'전달 실패'),'S':(3,2,'전달 확인')},[('E','C','L2 · L3'),('E','O','L1'),('C','R','만료 · 즉시'),('C','X','괜찮습니다'),('R','D',''),('D','P',''),('P','F','실패 · 미확인'),('P','S','성공'),('F','N','실패'),('F','S','성공')])
print('created',len(list(A.glob('ui-*.png'))),'screens and two diagrams')
