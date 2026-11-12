from pathlib import Path
from PIL import Image,ImageDraw,ImageFont,ImageOps
import math
R=Path(__file__).parent;A=R/'assets';I=A/'icons'; V=A/'final';V.mkdir(exist_ok=True)
BG='#090E16';SURF='#121C2B';LINE='#26364D';FG='#F7FAFF';MUT='#B8C5D8';MINT='#5EEAD4';AMBER='#FFC857';RED='#FF5B6B';PURPLE='#9FA4FF'
def f(n,b=False):return ImageFont.truetype(str(A/'fonts'/('Pretendard-Bold.ttf' if b and n>=40 else ('Pretendard-SemiBold.ttf' if b else 'Pretendard-Regular.ttf'))),n)
def txt(d,x,y,t,n=26,c=FG,b=False):d.text((x,y),t,font=f(n,b),fill=c,spacing=12)
def box(d,r,c=SURF,rad=24,stroke=None):d.rounded_rectangle(r,radius=rad,fill=c,outline=stroke,width=2)
def icon(im,n,x,y,s=40,c=FG):
 p=I/(n+'-'+c.lstrip('#').upper()+'.png');p=p if p.exists() else I/(n+'-FFFFFF.png');z=Image.open(p).convert('RGBA').resize((s,s),Image.Resampling.LANCZOS);im.paste(z,(x,y),z)
def asset(im,p,rect):
 x,y,w,h=rect;z=Image.open(p).convert('RGBA');z.thumbnail((w,h),Image.Resampling.LANCZOS);im.paste(z,(x+(w-z.width)//2,y+(h-z.height)//2),z)
def header(title,active='home',color=MINT):
 im=Image.new('RGB',(1280,720),BG);d=ImageDraw.Draw(im)
 asset(im,A/'logo-symbol.png',(16,12,80,80));txt(d,108,21,'플레오스 가드',30,b=True);txt(d,370,25,title,28,b=True)
 icon(im,'car-front',1008,27,36,MUT);txt(d,1054,32,'아반떼',25,c=MUT)
 box(d,(16,116,136,600),SURF,26)
 for j,(key,n,l) in enumerate([('home','shield-check','홈'),('warning','triangle-alert','경고등'),('record','mic','기록'),('manual','circle-help','상황선택'),('guardian','map-pin','보호자')]):
  y=132+j*91
  if key==active:box(d,(24,y,128,y+82),'#203D43',18, color)
  icon(im,n,59,y+7,34,color if key==active else MUT);txt(d,35 if len(l)>2 else 51,y+48,l,20,c=color if key==active else MUT,b=key==active)
 return im,d
def btn(im,rect,label,n,color,light=False):
 d=ImageDraw.Draw(im);box(d,rect,color,25);x,y,x2,y2=rect;icon(im,n,x+24,y+24,36,BG if light else FG);txt(d,x+76,y+22,label,28,c=BG if light else FG,b=True)
im,d=header('오늘도 안심하고 출발하세요')
cards=[('warning','triangle-alert',AMBER,'경고등 찾기','모양 · 사진으로 확인'),('record','audio-lines',MINT,'이상 증상 기록','사라지기 전에 보관'),('manual','octagon-alert',RED,'긴급 상황 안내','지금 해야 할 행동'),('guardian','map-pin',PURPLE,'보호자 연결','위치와 상황 전달')]
for j,(key,n,c,title,sub) in enumerate(cards):
 x=160+j%2*560;y=128+j//2*228;box(d,(x,y,x+536,y+204),SURF,28);box(d,(x+24,y+28,x+124,y+128),'#1B2B3F',24);icon(im,n,x+46,y+50,56,c);txt(d,x+148,y+42,title,34,b=True);txt(d,x+148,y+100,sub,25,c=MUT);d.line((x+25,y+172,x+511,y+172),fill=c,width=4)
btn(im,(160,610,696,698),'지금 증상 기록','mic',MINT,True);btn(im,(720,610,1256,698),'도움이 필요해요','phone-call','#622230');im.save(V/'ui-home.png')
# Catalog: warning pictograms and dedicated official guide panel.
im,d=header('어떤 경고등인가요','warning',AMBER)
box(d,(160,128,680,584),SURF);txt(d,186,150,'같은 표시를 선택하세요',29,b=True)
for j,(n,l) in enumerate([('wrench','엔진 · 동력'),('circle-gauge','타이어'),('circle-stop','브레이크'),('power','전동화'),('triangle-alert','통합 경고'),('activity','기타 표시')]):
 x=184+j%3*162;y=220+j//3*166;box(d,(x,y,x+144,y+142),'#19283D',20,AMBER if j==1 else None);icon(im,n,x+42,y+20,60,AMBER);txt(d,x+18,y+98,l,21,b=True)
box(d,(704,128,1256,584),'#262317',24);icon(im,'circle-gauge',736,156,68,AMBER);txt(d,822,160,'타이어 상태 확인',32,b=True);txt(d,736,252,'공식 안내',24,c=AMBER,b=True)
for j,t in enumerate(['1  표시 상태와 차량 사양 확인','2  정차 후 타이어 상태 확인','3  필요한 점검과 정비 연결']):txt(d,736,302+j*64,t,25)
btn(im,(160,610,680,698),'사진으로 찾기','camera',AMBER,True);btn(im,(704,610,1256,698),'공식 설명과 정비소','wrench',SURF);im.save(V/'ui-warning.png')
# Recorder: waveform and actual official Avante photo, not a generic car.
im,d=header('지금 나타난 증상을 기록합니다','record',MINT)
box(d,(160,128,800,584),SURF);icon(im,'mic',188,154,52,RED);txt(d,258,157,'기록 중',34,b=True);box(d,(610,153,768,201),'#24384B',18);txt(d,634,163,'00:18',24,c=MINT,b=True)
for k in range(94):
 x=188+k*6;h=(.3+abs(math.sin(k*.39))*abs(math.sin(k*.095)))*90;d.rounded_rectangle((x,348-h,x+3,348+h),radius=1,fill=MINT)
d.line((188,468,772,468),fill=LINE,width=2);d.ellipse((438,456,462,480),fill=RED);txt(d,188,500,'이전 30초',23,c=MUT);txt(d,420,500,'증상 표시',23,c=RED);txt(d,630,500,'이후 60초',23,c=MUT)
box(d,(824,128,1256,584),SURF);asset(im,A/'avante-exterior.jpg',(844,155,392,232));txt(d,848,412,'디 올 뉴 아반떼',28,b=True)
for j,(n,t) in enumerate([('audio-lines','소리 · 지원 상태 확인'),('activity','진동 · 측정 가능 시'),('history','원본과 시각 함께 보관')]):icon(im,n,848,464+j*32,25,MINT);txt(d,888,462+j*32,t,20,c=MUT)
btn(im,(160,610,800,698),'이 구간 보관','circle-stop',MINT,True);btn(im,(824,610,1256,698),'증상 추가','mic',SURF);im.save(V/'ui-record.png')
# Manual: meaningful category icons, no large numbering wall.
im,d=header('현재 느끼는 상황을 선택하세요','manual',AMBER)
items=[('activity','차체 떨림'),('navigation','핸들 쏠림'),('audio-lines','이상한 소음'),('flame','타는 냄새'),('circle-stop','페달 이상'),('circle-gauge','타이어 손상'),('car-front','가벼운 접촉'),('triangle-alert','단독 충돌'),('power','출력 저하')]
for j,(n,t) in enumerate(items):
 x=160+j%3*373;y=128+j//3*156;c=AMBER if j in [0,5] else FG;box(d,(x,y,x+349,y+132),'#30291C' if j in[0,5] else SURF,24,AMBER if j in[0,5] else None);icon(im,n,x+25,y+36,56,c);txt(d,x+105,y+44,t,30,c=c,b=True)
btn(im,(160,610,696,698),'선택한 상황 안내','circle-help',AMBER,True);btn(im,(720,610,1256,698),'지금 도움 요청','phone-call','#622230');im.save(V/'ui-manual.png')
# Emergency changes the entire app hierarchy; no ordinary menu dock.
im=Image.new('RGB',(1280,720),'#1B0B12');d=ImageDraw.Draw(im);box(d,(16,16,1264,104),'#702132',20);icon(im,'octagon-alert',40,31,56,FG);txt(d,116,34,'긴급  ·  원하지 않는 가속',35,b=True);icon(im,'volume-2',1096,40,36,FG);txt(d,1143,45,'음성',23)
txt(d,40,146,'전방을 보며',42,b=True);txt(d,40,208,'브레이크를 확인하세요',42,c='#FFCAD1',b=True)
for j,t in enumerate(['발을 가속 페달에서 옮기기','브레이크 입력 확인','감속 여부 확인']):
 y=326+j*75;box(d,(40,y,97,y+52),RED if j==0 else '#36232B',15);txt(d,57,y+8,str(j+1),26,c=BG if j==0 else MUT,b=True);txt(d,115,y+8,t,25,c=FG if j==0 else MUT)
asset(im,A/'pedal-detail.png',(610,140,630,415));txt(d,680,565,'강조된 페달이 브레이크입니다',25,c='#FFCAD1',b=True)
btn(im,(24,620,632,704),'밟아도 멈추지 않아요','circle-stop',RED,True);btn(im,(656,620,1256,704),'보호자에게 알림','send','#4D2632');im.save(V/'ui-guidance.png')
# Separate no-deceleration story.
im=Image.new('RGB',(1280,720),'#1B0B12');d=ImageDraw.Draw(im);box(d,(16,16,1264,104),'#702132',20);icon(im,'triangle-alert',40,32,56,FG);txt(d,116,34,'긴급  ·  감속이 확인되지 않습니다',35,b=True)
txt(d,40,154,'차량의 비상 제동',46,b=True);txt(d,40,218,'안내를 진행합니다',46,c='#FFCAD1',b=True)
asset(im,A/'avante-interior.jpg',(696,145,540,350));icon(im,'volume-2',42,345,64,RED);txt(d,128,355,'음성 안내를 듣고 전방을 주시하세요',27)
for j,t in enumerate(['브레이크 입력 확인','해당 차량의 검토된 절차','정차 여부 확인']):icon(im,'circle-check' if j==0 else 'clock-3',44,454+j*42,28,RED if j==0 else MUT);txt(d,92,452+j*42,t,24,c=FG if j==0 else MUT)
txt(d,720,535,'차량별 장치 그림과 절차를 적용합니다',23,c=MUT)
btn(im,(24,620,632,704),'지금 도움 요청','phone-call',RED,True);btn(im,(656,620,1256,704),'차가 멈췄어요','circle-check','#4D2632');im.save(V/'ui-stopping.png')
# Countdown with clear warning hierarchy and guardian icons.
im=Image.new('RGB',(1280,720),'#150E19');d=ImageDraw.Draw(im);box(d,(16,16,1264,104),'#702132',20);icon(im,'triangle-alert',40,31,56,FG);txt(d,116,34,'사고 관련 신호  ·  보호자에게 알립니다',34,b=True)
d.arc((48,154,392,498),-90,230,fill=RED,width=16);txt(d,144,210,'10',112,b=True);txt(d,167,357,'초 후 발송',28,c=MUT)
txt(d,48,536,'즉시 발송하거나 취소할 수 있습니다',23,c=MUT)
box(d,(448,144,1256,584),SURF);icon(im,'map-pin',476,173,36,PURPLE);txt(d,526,174,'위치와 관측 정보를 전달합니다',29,b=True)
for j,t in enumerate(['보호자 1','보호자 2','보호자 3']):
 y=256+j*91;d.ellipse((478,y,538,y+60),fill='#354963');icon(im,'shield-check',492,y+14,32,PURPLE);txt(d,562,y+12,t,28,b=True);icon(im,'message-circle',918,y+16,32,PURPLE);txt(d,970,y+18,'발송 전',25,c=MUT)
btn(im,(24,620,632,704),'지금 즉시 발송','send',RED,True);btn(im,(656,620,1256,704),'괜찮습니다','circle-check','#303044');im.save(V/'ui-countdown.png')
# Guardian location view, map is deliberately an illustrative UI map.
im,d=header('위치와 전달 상태','guardian',PURPLE);box(d,(160,128,856,584),'#112335',24)
for j in range(8):
 pts=[(180,160+j*52),(380+j*12,170+j*43),(600-j*20,220+j*33),(832,150+j*55)];d.line(pts,fill='#29445D',width=14)
for j in range(6):d.line([(220+j*100,145),(250+j*90,560)],fill='#29445D',width=11)
d.line([(220,472),(386,395),(534,418),(644,320)],fill=PURPLE,width=7);d.ellipse((598,274,690,366),outline=PURPLE,width=3);icon(im,'map-pin',620,286,48,FG)
box(d,(184,504,832,564),'#142C40',16);txt(d,208,519,'최근 위치 21:00:03  ·  지도 예시',24,c=MUT)
box(d,(880,128,1256,584),SURF);txt(d,906,158,'보호자 전달',31,b=True)
for j,(n,t,c) in enumerate([('check-check','카카오톡 전달됨',MINT),('message-circle','문자 대체 준비',AMBER),('clock-3','열람 확인 전',MUT)]):
 y=245+j*102;icon(im,n,908,y,40,c);txt(d,968,y+5,t,22,c=c,b=True)
btn(im,(160,610,856,698),'사건 기록 보기','history',PURPLE,True);btn(im,(880,610,1256,698),'보호자 연락','phone-call',SURF);im.save(V/'ui-guardian.png')
print('Eight redesigned screens generated')

# Final vehicle-first surfaces. The selected region is a user report, not a diagnosis.
def vehicle_scene(selected=False):
 im,d=header('어디에서 이상을 느끼셨나요','home' if not selected else 'manual',AMBER if selected else MINT)
 box(d,(160,128,928,584),SURF);txt(d,184,151,'내 차의 부위를 직접 선택하세요',27,b=True)
 asset(im,A/'avante-cutout.png',(170,200,740,365))
 spots=[(410,403,'앞부분'),(617,296,'실내'),(677,505,'앞 타이어'),(827,376,'뒤쪽')]
 for x,y,label in spots:
  c=AMBER if selected and label=='앞 타이어' else MINT
  d.ellipse((x-22,y-22,x+22,y+22),fill=BG,outline=c,width=4);d.ellipse((x-7,y-7,x+7,y+7),fill=c)
  ly=y-66 if label!='앞 타이어' else y-80
  width=d.textbbox((0,0),label,font=f(23,True))[2]+30;box(d,(x-width/2,ly,x+width/2,ly+40),'#182D3A',12);txt(d,x-width/2+15,ly+6,label,23,c=c,b=True)
 box(d,(952,128,1256,584),SURF)
 if selected:
  icon(im,'circle-gauge',976,150,42,AMBER);txt(d,1031,153,'앞 타이어',27,b=True);txt(d,976,202,'어떤 증상인가요',23,c=MUT)
  for j,(ic,label) in enumerate([('activity','떨림'),('circle-gauge','공기압\n표시'),('triangle-alert','파손\n의심'),('circle-help','모르겠음')]):
   xx=972+j%2*144;yy=244+j//2*164;box(d,(xx,yy,xx+120,yy+140),'#242B34',18);icon(im,ic,xx+36,yy+15,48,AMBER)
   for row,s in enumerate(label.split('\n')):
    tw=d.textbbox((0,0),s,font=f(22,True))[2];txt(d,xx+(120-tw)/2,yy+78+row*27,s,22,b=True)
 else:
  txt(d,976,154,'차량에서 바로',26,b=True);txt(d,976,194,'찾아보세요',26,b=True)
  for j,(ic,label) in enumerate([('car-front','외부 부위 선택'),('circle-gauge','타이어 상태'),('triangle-alert','경고등 찾기'),('mic','지금 증상 기록')]):
   yy=266+j*72;icon(im,ic,980,yy,34,AMBER if j==2 else MINT);txt(d,1032,yy+3,label,22)
 btn(im,(160,610,696,698),'부위 모름 · 증상 선택','circle-help',MINT,True)
 btn(im,(720,610,1256,698),'지금 도움 요청','phone-call','#622230')
 return im
vehicle_scene().save(V/'ui-home.png');vehicle_scene(True).save(V/'ui-parts.png')

im,d=header('정비소에 보여 줄 기록','record',MINT)
box(d,(160,128,536,584),SURF);asset(im,A/'avante-cutout.png',(174,145,348,214));txt(d,184,390,'앞 타이어 · 떨림',28,b=True);txt(d,184,442,'사용자가 선택한 부위',22,c=MUT);txt(d,184,504,'기록 시각  21:00',24,c=MINT)
box(d,(560,128,1256,584),SURF);txt(d,586,150,'같은 시각의 자료를 함께 확인',28,b=True)
for j,(ic,t,sub,c) in enumerate([('audio-lines','소리 구간','원본 녹음이 있는 구간만',MINT),('activity','진동 관측','측정값과 빈 구간 구분',AMBER),('triangle-alert','경고 표시','공식 설명과 상태 대조',AMBER),('volume-2','앱의 안내','어떤 안내가 나왔는지',PURPLE)]):
 yy=226+j*82;icon(im,ic,588,yy,40,c);txt(d,650,yy,t,26,b=True);txt(d,850,yy+4,sub,21,c=MUT)
btn(im,(160,610,696,698),'선택 구간 다시 보기','history',MINT,True);btn(im,(720,610,1256,698),'정비용 자료 공유','send',SURF);im.save(V/'ui-history.png')

print('Final vehicle-first home, region selection and history added; Pretendard applied')
