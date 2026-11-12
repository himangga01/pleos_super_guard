from pathlib import Path
from copy import deepcopy
import zipfile,re,hashlib,json,textwrap
from lxml import etree as E
from docx import Document
from docx.shared import Cm,Pt,RGBColor
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT,WD_CELL_VERTICAL_ALIGNMENT

R=Path(__file__).parent; ROOT=R.parents[1]; A=R/'assets'
W='http://schemas.openxmlformats.org/wordprocessingml/2006/main'
REL='http://schemas.openxmlformats.org/package/2006/relationships'
NS={'w':W,'r':'http://schemas.openxmlformats.org/officeDocument/2006/relationships'}
OFFICIAL='https://hyundaimotorgroup.co.kr/ko/news/hyundai-motor-company-all-new-avante'
NAME='플레오스 가드'
FIGURES={}

def font(run,size=10.5,bold=False):
 run.font.name='맑은 고딕';run.font.size=Pt(size);run.font.bold=bold;run.font.color.rgb=RGBColor(0,0,0)
 rp=run._element.get_or_add_rPr();rf=rp.find(qn('w:rFonts'))
 if rf is None:rf=OxmlElement('w:rFonts');rp.append(rf)
 rf.set(qn('w:eastAsia'),'맑은 고딕')
def inline(p,s,size=10.5):
 prev=p._p.getprevious()
 if prev is not None and prev.tag==qn('w:tbl'):p.paragraph_format.space_before=Pt(6)
 s=s.replace('차량안내',NAME)
 # Show public source URLs; local project references are labels, not private workstation paths.
 s=re.sub(r'\[([^\]]+)\]\((?!https?://)[^)]+\)',r'\1',s)
 parts=re.split(r'(\*\*.*?\*\*|\[[^\]]+\]\(https?://[^)]+\)|`[^`]+`)',s)
 for x in parts:
  if not x:continue
  m=re.fullmatch(r'\[([^\]]+)\]\((https?://[^)]+)\)',x)
  if m:
   rid=p.part.relate_to(m[2],'http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink',is_external=True)
   h=OxmlElement('w:hyperlink');h.set(qn('r:id'),rid);rr=OxmlElement('w:r');pr=OxmlElement('w:rPr');c=OxmlElement('w:color');c.set(qn('w:val'),'234E70');pr.append(c);rr.append(pr);t=OxmlElement('w:t');t.text=m[1];rr.append(t);h.append(rr);p._p.append(h)
  else:
   bold=x.startswith('**');x=x[2:-2] if bold else x.strip('`');rr=p.add_run(x);font(rr,size,bold)
def setup(doc):
 FIGURES[id(doc)]=0
 sec=doc.sections[0];sec.page_width=Cm(21);sec.page_height=Cm(29.7);sec.top_margin=Cm(1.8);sec.bottom_margin=Cm(1.8);sec.left_margin=Cm(1.8);sec.right_margin=Cm(1.8)
 sec.header_distance=Cm(.8);sec.footer_distance=Cm(.8)
 for sn in ['Normal','Title','Subtitle','Heading 1','Heading 2','Heading 3','Heading 4','Caption']:
  st=doc.styles[sn];st.font.name='맑은 고딕';st.font.color.rgb=RGBColor(0,0,0);st.font.size=Pt(10.5)
  st.element.get_or_add_rPr().append(E.Element(qn('w:rFonts'),{qn('w:eastAsia'):'맑은 고딕'}))
 normal=doc.styles['Normal'];normal.paragraph_format.space_after=Pt(4);normal.paragraph_format.line_spacing=1.1
 for border in list(doc.styles.element.xpath('.//w:pBdr')):border.getparent().remove(border)
 for sn,size in [('Title',26),('Heading 1',18),('Heading 2',14),('Heading 3',12),('Heading 4',11)]:
  doc.styles[sn].font.size=Pt(size);doc.styles[sn].font.bold=True
  doc.styles[sn].paragraph_format.space_before=Pt(12);doc.styles[sn].paragraph_format.space_after=Pt(7);doc.styles[sn].paragraph_format.keep_with_next=True
 doc.styles['Caption'].font.size=Pt(9);doc.styles['Caption'].paragraph_format.space_after=Pt(10)
 p=sec.header.paragraphs[0];font(p.add_run(NAME+'  |  상세 기획 및 기술 명세'),9)
 p=sec.footer.paragraphs[0];p.alignment=WD_ALIGN_PARAGRAPH.RIGHT
 font(p.add_run('설계 문서  ·  '),9);fld=OxmlElement('w:fldSimple');fld.set(qn('w:instr'),'PAGE');p._p.append(fld)
 doc.core_properties.author='';doc.core_properties.last_modified_by='';doc.core_properties.title=NAME+' 상세 기획 및 기술 명세';doc.core_properties.subject='Pleos 차량용 앱 개발 공모전 별첨'
def picture(doc,path,caption,width=17.2):
 if path.name.startswith('ui-'):path=A/'final'/path.name
 p=doc.add_paragraph();p.paragraph_format.keep_with_next=True;p.alignment=WD_ALIGN_PARAGRAPH.CENTER;p.add_run().add_picture(str(path),width=Cm(width))
 FIGURES[id(doc)]=FIGURES.get(id(doc),0)+1
 caption=re.sub(r'^그림\s+\d+\.\s*','',caption)
 p=doc.add_paragraph(style='Caption');inline(p,f'그림 {FIGURES[id(doc)]}. '+caption,9)
def table(doc,rows):
 if len(rows)<2:return
 n=len(rows[0]);t=doc.add_table(rows=1,cols=n);t.alignment=WD_TABLE_ALIGNMENT.CENTER;t.autofit=False
 weights={2:[.28,.72],3:[.23,.38,.39],4:[.18,.29,.23,.30],5:[.13,.22,.22,.22,.21]}.get(n,[1/n]*n)
 for i,col in enumerate(t.columns):col.width=Cm(17.2*weights[i])
 for ri,row in enumerate(rows):
  cells=t.rows[0].cells if ri==0 else t.add_row().cells
  for ci,cell in enumerate(cells):
   cell.width=Cm(17.2*weights[ci]);cell.vertical_alignment=WD_CELL_VERTICAL_ALIGNMENT.CENTER
   pr=cell._tc.get_or_add_tcPr();sh=OxmlElement('w:shd');sh.set(qn('w:fill'),'263445' if ri==0 else ('F3F5F7' if ri%2==0 else 'FFFFFF'));pr.append(sh)
   mar=OxmlElement('w:tcMar')
   for k,v in [('top','90'),('bottom','90'),('left','100'),('right','100')]:el=OxmlElement('w:'+k);el.set(qn('w:w'),v);el.set(qn('w:type'),'dxa');mar.append(el)
   pr.append(mar);p=cell.paragraphs[0];p.paragraph_format.space_after=Pt(1);p.paragraph_format.line_spacing=1.05
   inline(p,row[ci] if ci<len(row) else '',9.0)
   if ri==0:
    for rr in p.runs:rr.font.color.rgb=RGBColor(255,255,255);rr.bold=True
  if ri==0: t.rows[0]._tr.get_or_add_trPr().append(OxmlElement('w:tblHeader'))
  t.rows[ri]._tr.get_or_add_trPr().append(OxmlElement('w:cantSplit'))
 pr=t._tbl.tblPr;b=OxmlElement('w:tblBorders')
 for k in ['top','left','bottom','right','insideH','insideV']:
  el=OxmlElement('w:'+k);el.set(qn('w:val'),'single');el.set(qn('w:sz'),'4');el.set(qn('w:color'),'D9D9D9');b.append(el)
 pr.append(b)
 return t

fields=[
'Pleos 차량의 경고등과 순간적인 이상 증상을 기록하고, 위험할 때 음성·화면으로 대처를 안내하며 등록한 보호자에게 위치와 상황을 전달하는 차량용 앱입니다.',
'경고등 이름을 모르면 무엇을 확인해야 하는지 찾기 어렵습니다. 소음이나 떨림이 잠깐 나타났다가 사라지면 정비소에서 당시 상황을 설명하기도 어렵습니다. 갑작스러운 가속이나 사고에서는 화면을 오래 조작하기 어렵고, 운전자가 응답하지 못하면 보호자가 위치를 알기 어렵습니다.\n차량안내는 이 문제를 하나의 사건 기록으로 연결합니다. 해당 Pleos 차량의 공식 안내, 허용된 차량 상태와 사용자가 느낀 증상을 함께 보관합니다. 차량 앱이 중심이고 안드로이드 휴대폰은 전원 차단 등에 대비한 백업을 맡습니다. 화면·차량 이미지의 기준은 공식 자료로 확인한 디 올 뉴 아반떼입니다.',
'① 전체 경고등·오류 찾기: 지원 차량의 공식 목록을 대상으로 모양·문구·사진으로 찾고, 뜻과 대처 순서 및 필요한 정비소 연결을 제공합니다.\n② 순간 증상 기록: 사용 가능한 속도·브레이크·진동·소리·영상을 발생 전후 구간으로 보관합니다. 자동 진동 감지는 실제 센서 지원·주기·보정 확인 후 적용합니다.\n③ 긴급 행동과 수동 선택: 센서값이 없거나 오래되면 9개 상황 카드로 직접 선택합니다. 급가속과 브레이크 입력 후 감속 실패를 나누어 음성과 같은 단계의 그림·설명 애니메이션을 제공합니다.\n④ 보호자 알림: 사건 정보는 먼저 서버에 보내고, 10초 확인·즉시 발송·취소를 제공합니다. 카카오톡 알림톡 우선, 실패 또는 전달 미확인 시 SMS/LMS로 전환합니다.\n차량 상태·안내·기록·보호자 통지를 같은 시간표에 묶는 것이 제안의 차별점입니다. 자동 제동이나 부상 진단 기능으로 설명하지 않습니다.',
'출발 전에는 차량·휴대폰 백업·보호자 연결과 수집 항목을 확인합니다.\n주행 중 낯선 소음이나 떨림이 느껴지면 지금 증상 기록을 실행합니다. 최근 30초와 이후 60초를 첫 시험 기준으로 삼되 실제로 확보한 자료와 빈 구간을 구별합니다.\n정보가 없으면 차체 떨림, 핸들 쏠림, 이상한 소음, 타는 냄새, 페달 이상, 타이어 손상, 가벼운 접촉, 단독 충돌, 출력 저하 중 현재 상황을 선택합니다.\n긴급 상황에서는 검토된 차량별 음성·화면 안내를 제공합니다. 사고 관련 신호가 들어오면 위치와 받은 사실부터 보관하고, 확인 시간 뒤 보호자에게 알립니다. 차량 앱이 꺼지면 준비된 휴대폰이 전달받은 사건과 가능한 기능을 이어갑니다.\n정차 후에는 사건 시간표와 원본 자료를 확인하여 정비사에게 필요한 구간을 보여 줍니다.',
'개발은 Pleos 기능 확인 → 전체 도감·화면 → 기록·판정 → 서버·알림 → 휴대폰·블랙박스 검증 순서로 진행합니다.\nVehicle SDK의 브레이크·에어백·허용된 차량 상태, AAOS의 공식 차량 속도 경로, FusedLocation·SpeechToText·TextToSpeech·NaviHelper를 목적에 맞게 사용합니다. IMU·휠 tick·RPM·DTC·원본 녹음·영상은 대상 차량의 실제 제공 범위를 먼저 확인합니다.\n차량 앱은 Kotlin 기반, 백엔드는 TypeScript API와 PostgreSQL·객체 저장소로 설계했습니다. API 23개 작업과 DB 23개 테이블, 수동 선택·발송 상태·분할 업로드·중복/취소 규칙을 별첨에 정리했습니다.\n현재는 기획·계약·화면 시안 단계입니다. 문서·색 대비·배치 계산과 합성 신호 검사는 수행했으나, 실제 앱·차량 센서·카카오톡 발송·사고 대응 성능은 시험하지 않았습니다. 공모전 시연에서는 실제 SDK 값과 시험 입력을 구별하겠습니다.',
'대상은 Pleos 탑재 차량 운전자와 사전 등록한 보호자입니다. 경고 의미를 찾는 과정과 정비소에 증상을 설명하는 과정을 줄이고, 사고 때 위치와 받은 자료를 빠르게 전달하는 경험을 목표로 합니다.\n초기 4주 시험 운영은 운전자 20명·정비 설명 수신자 5명을 모집 목표로 삼습니다. 초기 월간 활성 사용자 목표는 시험 운전자 20명이며 현재 이용 실적은 없습니다. 도감 탐색 성공률, 증상 설명 시간, 기록 재열람, D7·D28 재사용률과 오발송을 측정합니다.\n향후 발전은 Pleos 지원 차량 안에서 검토된 가이드·센서·블랙박스 범위를 늘리고, 기록 보관과 정비 전달의 실제 수요를 확인하는 방향입니다. 확인되지 않은 사고 예방률·수상 가능성·매출을 성과로 제시하지 않습니다.',
'별첨: 02_상세기획_기술명세.pdf 및 편집 가능한 DOCX.\n포함 자료: 메뉴 흐름도, 6개 주요 화면 설계안, 신형 아반떼 공식 사진 기반 AI 적용 개념도, 시스템 구성도, 기능별 표, 상태 머신, API JSON, 데이터 구조, 인수 시나리오.\n차량 사진 출처: 현대자동차그룹 2026-08-05 디 올 뉴 아반떼 공식 보도자료. 이미지와 화면은 기획 시안이며 실제 구동 캡처가 아닙니다.\n시연 영상·소스 저장소·APK 링크는 현재 미제출입니다.',
'미제출'
]

def build_official():
 ref=R/'official-form-original.docx';z=zipfile.ZipFile(ref);parts={n:z.read(n) for n in z.namelist()};rt=E.fromstring(parts['word/document.xml'])
 controls=rt.findall('.//w:sdt',NS)
 for s,body in zip(controls,fields):
  for highlight in list(s.findall('.//w:highlight',NS)):highlight.getparent().remove(highlight)
  body=body.replace('차량안내',NAME).replace('6개 주요 화면','10개 주요 화면')
  if s is controls[2]:body='차량 중심 화면: 신형 아반떼의 앞부분·실내·타이어·뒤쪽을 직접 선택하고 관련 증상을 아이콘으로 고릅니다. 부위를 모르면 소리·떨림·냄새에서 바로 시작합니다.\n'+body
  c=s.find('w:sdtContent',NS);ts=c.findall('.//w:t',NS)
  first=ts[0];first.text=body.split('\n')[0]
  for t in ts[1:]:t.text=''
  run=first.getparent()
  for line in body.split('\n')[1:]:E.SubElement(run,qn('w:br'));t=E.SubElement(run,qn('w:t'));t.text=line
 # Minimal edits to known application fields; keep identity and consent blank.
 for p in rt.findall('w:body/w:p',NS):
  ts=p.findall('.//w:t',NS);tx=''.join(t.text or '' for t in ts)
  new=None
  if tx.startswith('참가 단위:'):new=tx.replace('□ 개인','☑ 개인')
  elif tx.startswith('참가 인원:'):new='참가 인원: 1명'
  elif tx.startswith('앱명 / 프로젝트명:'):new='앱명 / 프로젝트명: '+NAME
  if new is not None and ts:
   ts[0].text=new
   for t in ts[1:]:t.text=''
 # Add graphical references after visible section 7, preserving all original controls.
 tmp=Document();setup(tmp)
 p=tmp.add_paragraph('메뉴 구성과 화면 설계');font(p.runs[0],12,True);p.paragraph_format.keep_with_next=True
 picture(tmp,A/'pleos-guard-launch-outline.png','앱 진입 화면. 아반떼와 겹치는 PLEOS·GUARD는 외곽선으로 표현합니다.')
 picture(tmp,A/'menu-flow.png','차량 부위 선택 또는 증상 선택에서 기록·안내·보호자 통지로 이어집니다.')
 tmp.add_page_break()
 picture(tmp,A/'ui-home.png','아반떼 모습에서 부위를 직접 선택하는 홈입니다. 글꼴은 Pretendard입니다.')
 picture(tmp,A/'ui-parts.png','앞 타이어 선택 뒤 관련 증상을 아이콘으로 고릅니다. 선택은 고장 진단과 구별됩니다.')
 tmp.add_page_break()
 picture(tmp,A/'ui-guidance.png','일반 메뉴와 구별되는 긴급 전용 화면과 상세 페달·발 움직임 설명 시안입니다.')
 picture(tmp,A/'ui-guardian.png','위치 지도와 보호자 채널 상태를 함께 보는 설계안입니다. 지도·수신 상태는 예시입니다.')
 p=tmp.add_paragraph();inline(p,'차량 원본 사진: [현대자동차그룹 디 올 뉴 아반떼 공식 자료]('+OFFICIAL+')',9)
 tmp_path=R/'qa/insert.docx';tmp.save(tmp_path)
 iz=zipfile.ZipFile(tmp_path);ir=E.fromstring(iz.read('word/document.xml'));rels=E.fromstring(parts['word/_rels/document.xml.rels']);irels=E.fromstring(iz.read('word/_rels/document.xml.rels'));remap={}
 for ri,rel in enumerate(irels):
  typ=rel.get('Type','')
  if not (typ.endswith('/image') or typ.endswith('/hyperlink')):continue
  new=f'rIdCare{ri+100}';remap[rel.get('Id')]=new;nr=deepcopy(rel);nr.set('Id',new)
  if typ.endswith('/image'):
   old=rel.get('Target');dest='media/care_'+Path(old).name;nr.set('Target',dest);parts['word/'+dest]=iz.read('word/'+old)
  rels.append(nr)
 anchor=controls[6]
 while anchor.tag!=qn('w:tbl'):anchor=anchor.getparent()
 last=anchor
 for i,node in enumerate(ir.find('w:body',NS)):
  if node.tag==qn('w:sectPr'):continue
  node=deepcopy(node)
  for el in node.iter():
   for k,v in list(el.attrib.items()):
    if v in remap and k.startswith('{'+NS['r']+'}'):el.set(k,remap[v])
   if el.tag.endswith('}docPr'):el.set('id',str(2000+i));el.set('name','Care figure '+str(i))
  last.addnext(node);last=node
 parts['word/document.xml']=E.tostring(rt,xml_declaration=True,encoding='UTF-8',standalone=True)
 parts['word/_rels/document.xml.rels']=E.tostring(rels,xml_declaration=True,encoding='UTF-8',standalone=True)
 ct=E.fromstring(parts['[Content_Types].xml']);nsct='http://schemas.openxmlformats.org/package/2006/content-types'
 if not any(e.get('Extension')=='png' for e in ct):E.SubElement(ct,'{'+nsct+'}Default',Extension='png',ContentType='image/png')
 parts['[Content_Types].xml']=E.tostring(ct,xml_declaration=True,encoding='UTF-8',standalone=True)
 cp=E.fromstring(parts['docProps/core.xml'])
 for e in cp:
  if e.tag.split('}')[-1] in ['creator','lastModifiedBy']:e.text=''
 parts['docProps/core.xml']=E.tostring(cp,xml_declaration=True,encoding='UTF-8',standalone=True)
 st=E.fromstring(parts['word/settings.xml']);uf=st.find('w:updateFields',NS)
 if uf is None:uf=E.SubElement(st,qn('w:updateFields'))
 uf.set(qn('w:val'),'true');parts['word/settings.xml']=E.tostring(st,xml_declaration=True,encoding='UTF-8',standalone=True)
 dest=R/'01_공식양식_플레오스가드.docx'
 with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED) as out:
  for n,b in parts.items():out.writestr(n,b)
 changed=[n for n in z.namelist() if parts[n]!=z.read(n)]
 print('official_changed_parts',changed)

def build_annex():
 doc=Document();setup(doc)
 doc.add_paragraph('플레오스 가드 상세 기획 및 기술 명세',style='Title')
 p=doc.add_paragraph();p.add_run().add_picture(str(A/'logo-symbol.png'),width=Cm(1.8));font(p.add_run('  플레오스 가드'),20,True)
 p=doc.add_paragraph('현대자동차그룹 차량용 앱 개발 공모전 별첨');font(p.runs[0],14)
 p=doc.add_paragraph('Pleos 차량 앱 중심  ·  안드로이드 휴대폰 백업  ·  2026년 11월 12일');font(p.runs[0],10)
 doc.add_paragraph('경고등 설명부터 순간 증상 기록, 긴급 안내와 보호자 통지까지 같은 사건에서 이어지는 앱을 제안합니다. 이 문서는 화면·상태·데이터·연동 계약을 정의하며, 구현과 실차 검증이 완료됐다는 의미는 아닙니다.')
 picture(doc,A/'pleos-guard-launch-outline.png','그림 1. 확정한 진입 시안. 차량과 겹치는 PLEOS·GUARD는 투명한 내부와 외곽선으로 표현합니다.')
 doc.add_paragraph('문서 읽는 순서',style='Heading 2')
 doc.add_paragraph('메뉴·화면 설계를 먼저 확인한 뒤, 서비스 개요와 시스템 구조, 네 가지 기능 명세, UI 규격, 데이터 구조, 예외·검증 계획 순서로 읽습니다. 실험 임계값·설계 수치·공식 제공 기능을 구분했습니다.')
 doc.add_page_break();doc.add_paragraph('대상 차량과 메뉴 구성',style='Heading 1')
 picture(doc,A/'avante-exterior.jpg','그림 2. 디 올 뉴 아반떼 공식 외관 사진. 출처: 현대자동차그룹, 2026년 8월 5일.')
 doc.add_paragraph('공식 자료에서 확인한 신형 아반떼의 Pleos 환경을 이미지 기준으로 삼았습니다. 실제 앱 영역은 시스템 Driving view·상태 표시줄·GNB를 제외한 창 크기에 맞춰 조정합니다. 자동차 외관이 바뀌었다는 이유로 센서 접근 권한이 확인된 것으로 간주하지 않습니다.')
 picture(doc,A/'menu-flow.png','그림 3. 홈 메뉴에서 사건 기록·긴급 안내·보호자 통지로 이어지는 흐름입니다.')
 for page,pair in enumerate([['home','parts'],['warning','record'],['manual','guidance'],['stopping','countdown'],['guardian','history']]):
  doc.add_page_break();doc.add_paragraph(['차량 중심 홈과 부위 선택','경고등 찾기와 순간 증상 기록','상황 선택과 긴급 행동 안내','감속 실패와 보호자 발송 확인','위치 확인과 정비용 사건 기록'][page],style='Heading 1')
  for n in pair:picture(doc,A/f'ui-{n}.png',{'home':'신형 아반떼를 중심에 둔 홈입니다. 부위 또는 증상에서 시작합니다.','parts':'앞 타이어 선택 뒤 아이콘으로 증상을 고릅니다. 선택 부위는 사용자 보고로 기록합니다.','warning':'도감·공식 대처·서비스 연결을 함께 보여 줍니다. 메뉴 아이콘은 제조사 경고등 원본과 구분됩니다.','record':'아반떼 사진·마이크·파형·시간표를 사용한 기록 화면입니다. 파형은 합성 예시입니다.','manual':'센서 결측 때 제공할 9개 보기입니다. 아이콘과 짧은 이름을 함께 사용합니다.','guidance':'위험 전용 배치와 상세 페달·발 움직임 설명 시안입니다. 실제 차량별 검토가 필요합니다.','stopping':'감속 실패는 별도 상태로 표시하고 검토된 차량별 절차로 연결합니다.','countdown':'10초 확인 화면이며 아직 보호자에게 발송하기 전입니다.','guardian':'지도와 채널별 전달 상태의 설계 예시입니다. 실제 위치·발송 결과는 아닙니다.','history':'같은 시각의 소리·진동·표시·안내를 묶어 정비소에서 확인하는 화면입니다.'}[n])
 doc.add_page_break();doc.add_paragraph('시스템 구성',style='Heading 1');picture(doc,A/'architecture.png','그림 10. 허용된 차량 입력부터 판정·UI·보관·외부 알림까지의 처리 구조입니다.')
 doc.add_paragraph('개발 상태와 자료 기준',style='Heading 2');doc.add_paragraph('현재는 기획·계약·화면 설계 단계입니다. 문서 구조, 색 대비·배치 계산, 합성 신호의 수치 검사는 수행했습니다. 실제 차량 센서·화면·알림톡·문자·블랙박스·휴대폰 인계는 시험하지 않았습니다. 이 문서의 화면과 AI 이미지는 공모전 제안을 설명하는 시안입니다.')
 p=doc.add_paragraph();inline(p,'차량 사진과 신형 모델 기준: [현대자동차그룹 공식 보도자료]('+OFFICIAL+')')
 doc.add_page_break();doc.add_paragraph('사용성 조사와 유저스토리',style='Heading 1')
 ux=(ROOT/'docs/analysis/ux-redesign-2026-11-12.md').read_text(encoding='utf-8')
 for line in ux.splitlines()[4:]:
  if not line.strip() or line.startswith('|'):continue
  if line.startswith('### '):doc.add_paragraph(line[4:],style='Heading 2')
  elif line.startswith('## '):doc.add_paragraph(line[3:],style='Heading 2')
  else:inline(doc.add_paragraph(),line)
 md=(ROOT/'docs/analysis/integrated-vehicle-care-2026-11-12.md').read_text(encoding='utf-8');md=md[md.index('## 1.'):];lines=md.splitlines();i=0
 while i<len(lines):
  s=lines[i]
  if not s.strip():i+=1;continue
  if s.startswith('```'):
   lang=s[3:];code=[];i+=1
   while i<len(lines) and not lines[i].startswith('```'):code.append(lines[i]);i+=1
   if lang=='mermaid':
    source='\n'.join(code)
    asset='architecture.png' if 'flowchart' in source else ('guidance-state.png' if 'OBSERVING' in source else 'notification-state.png')
    picture(doc,A/asset,'처리 흐름과 상태 전이 요약. 세부 조건과 예외는 본문 명세를 따릅니다.')
    i+=1;continue
   if lang=='text' and code and re.match(r'P[034]',code[0]):
    key='ui-home.png' if code[0].startswith('P0') else ('ui-manual.png' if code[0].startswith('P3') else 'ui-guidance.png')
    picture(doc,A/key,'화면 구획 시안. 정확한 좌표와 크기는 4.2절의 기준표를 따릅니다.')
    if code[0].startswith('P4'):picture(doc,A/'ui-countdown.png','발송 확인의 독립 화면입니다. 안내 단계와 전송 상태를 구분합니다.')
    i+=1;continue
   for line in code:
    for segment in (textwrap.wrap(line,width=106,replace_whitespace=False,drop_whitespace=False) or ['']):
     p=doc.add_paragraph();p.paragraph_format.space_after=Pt(0);p.paragraph_format.line_spacing=1.0
     rr=p.add_run(segment.replace('차량안내',NAME));font(rr,8);rr.font.name='Consolas'
   i+=1;continue
  if s.startswith('|'):
   rows=[]
   while i<len(lines) and lines[i].startswith('|'):
    cells=[x.strip() for x in lines[i].strip().strip('|').split('|')]
    if not all(re.fullmatch(r':?-+:?',c.replace(' ','')) for c in cells):rows.append(cells)
    i+=1
   table(doc,rows);continue
  h=re.match(r'^(#{2,4})\s+(.*)',s)
  if h:
   level=len(h[1])-1
   # Plain black headings. Preserve wording and number hierarchy.
   p=doc.add_paragraph(h[2].replace(':',' '),style=f'Heading {level}')
   if level==1:p.paragraph_format.page_break_before=True
   i+=1;continue
  p=doc.add_paragraph()
  if re.match(r'^[-*] ',s):s='• '+s[2:];p.paragraph_format.left_indent=Cm(.3);p.paragraph_format.first_line_indent=Cm(-.3)
  inline(p,s);i+=1
 # Keep a compact real-file index without exposing private absolute paths.
 p=doc.add_paragraph('개발 계약 파일 안내',style='Heading 1');p.paragraph_format.page_break_before=True
 table(doc,[['파일','내용'],['api.openapi.json','23개 API 작업의 요청·응답·권한·오류·분할 업로드 계약'],['database.sql','23개 테이블과 외래키·고유 제약·작업 인덱스'],['rules-lab.json','운영 검증 전 실험용 판정값과 발송 정책'],['ui-tokens.json','그리드·색·글자·터치 크기'],['acceptance-scenarios.json','구현 후 실행할 인수 시나리오 56개']])
 doc.add_paragraph('위 계약은 구현 시작용 설계입니다. 이 문서에 제시한 JSON과 표의 원본이며, 실제 API 서버나 데이터베이스가 이미 설치됐다는 뜻은 아닙니다.')
 doc.save(R/'02_상세기획_기술명세.docx')

if __name__=='__main__':build_official();build_annex();print('DOCX documents created')
