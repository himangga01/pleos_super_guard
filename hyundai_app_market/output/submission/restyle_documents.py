"""Apply the user-selected Paperlogy document design without changing prose or structure."""
from pathlib import Path
from copy import deepcopy
import zipfile, hashlib, json, re, shutil, struct, uuid, math
from lxml import etree as E
from PIL import Image, ImageDraw, ImageFont

R=Path(__file__).parent; A=R/'assets'; QA=R/'qa'/'paperlogy'; QA.mkdir(parents=True,exist_ok=True)
BASE=QA/'baseline'; BASE.mkdir(exist_ok=True)
REF=Path('C:/2026_project/사업계획서/output/두유스토어_사업계획서_서식수정본.docx')
FONTS=A/'paperlogy'; FONTS.mkdir(exist_ok=True)
for f in Path('C:/2026_project/사업계획서/assets/fonts').glob('Paperlogy-*.ttf'):
 shutil.copy2(f,FONTS/f.name)
W='http://schemas.openxmlformats.org/wordprocessingml/2006/main'; RR='http://schemas.openxmlformats.org/officeDocument/2006/relationships'
PK='http://schemas.openxmlformats.org/package/2006/relationships'; CT='http://schemas.openxmlformats.org/package/2006/content-types'
NS={'w':W,'r':RR,'wp':'http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing','a':'http://schemas.openxmlformats.org/drawingml/2006/main','pic':'http://schemas.openxmlformats.org/drawingml/2006/picture'}
def q(s): a,b=s.split(':'); return '{'+NS[a]+'}'+b
def sub(el,name,**attrs):
 child=el.find(q(name))
 if child is None: child=E.SubElement(el,q(name))
 for k,v in attrs.items(): child.set(q('w:'+k),str(v))
 return child
def remove(el,*names):
 for name in names:
  for x in list(el.findall(q(name))):el.remove(x)
def pr(el,name):
 x=el.find(q(name))
 if x is None:x=E.Element(q(name));el.insert(0,x)
 return x
def xml(el):return E.tostring(el,xml_declaration=True,encoding='UTF-8',standalone=True)
def sha(b):return hashlib.sha256(b).hexdigest()
def txt(el):return ''.join(el.xpath('.//w:t/text()',namespaces=NS))
def para_texts(rt):return [txt(p) for p in rt.findall('.//w:p',NS)]
def run_font(rp,face='페이퍼로지 4 Regular',size=None,color='000000'):
 rf=sub(rp,'w:rFonts')
 rf.attrib.clear()
 for k in ['ascii','hAnsi','eastAsia','cs']:rf.set(q('w:'+k),face)
 if size is not None:
  sub(rp,'w:sz',val=round(size*2));sub(rp,'w:szCs',val=round(size*2))
 sub(rp,'w:color',val=color)
 remove(rp,'w:b','w:bCs')
def pformat(pp,align='both',line=360,before=60,after=200,left=0,first=0):
 sub(pp,'w:jc',val=align);sp=sub(pp,'w:spacing',before=before,after=after,line=line,lineRule='exact')
 remove(pp,'w:ind');ind=sub(pp,'w:ind',left=left,right=0)
 if first>=0:ind.set(q('w:firstLine'),str(first))
 else:ind.set(q('w:hanging'),str(-first))
 sub(pp,'w:widowControl',val=1)
def set_runs(p,size=None,face=None,color='000000'):
 for run in p.findall('.//w:r',NS):
  rp=pr(run,'w:rPr');old=rp.find('w:b',NS)
  bold=(old is not None and old.get(q('w:val'),'1') not in ['0','false']) or any('7 Bold' in v for rf in rp.findall('w:rFonts',NS) for v in rf.attrib.values())
  run_font(rp,face or ('페이퍼로지 7 Bold' if bold else '페이퍼로지 4 Regular'),size,color)

def embed_fonts(parts):
 ft=E.fromstring(parts['word/fontTable.xml']);relpath='word/_rels/fontTable.xml.rels'
 rels=E.fromstring(parts[relpath]) if relpath in parts else E.Element('{'+PK+'}Relationships',nsmap={None:PK})
 ct=E.fromstring(parts['[Content_Types].xml']); manifest=[]
 if not any(x.get('Extension')=='odttf' for x in ct):E.SubElement(ct,'{'+CT+'}Default',Extension='odttf',ContentType='application/vnd.openxmlformats-officedocument.obfuscatedFont')
 for num,label in [(4,'Regular'),(5,'Medium'),(7,'Bold'),(8,'ExtraBold')]:
  b=(FONTS/f'Paperlogy-{num}{label}.ttf').read_bytes();n=struct.unpack_from('>H',b,4)[0]
  tabs={b[12+16*i:16+16*i].decode():struct.unpack_from('>II',b,20+16*i) for i in range(n)};os=tabs['OS/2'][0]
  fs=struct.unpack_from('>H',b,os+8)[0];assert fs==0
  family=f'페이퍼로지 {num} {label}'
  for old in list(ft):
   if old.get(q('w:name'))==family:ft.remove(old)
  fn=E.SubElement(ft,q('w:font'),{q('w:name'):family});sub(fn,'w:altName',val=f'Paperlogy {num} {label}')
  sub(fn,'w:family',val='swiss');sub(fn,'w:pitch',val='variable');sub(fn,'w:charset',val='81')
  key=uuid.uuid5(uuid.NAMESPACE_URL,'pleos-guard/'+family);bb=bytearray(b);kb=key.bytes[::-1]
  for i in range(32):bb[i]^=kb[i%16]
  filename=f'paperlogy-{num}.odttf';rid=f'rIdPaperlogy{num}'
  parts['word/fonts/'+filename]=bytes(bb)
  for old in list(rels):
   if old.get('Id')==rid:rels.remove(old)
  E.SubElement(rels,'{'+PK+'}Relationship',Id=rid,Type=RR+'/font',Target='fonts/'+filename)
  E.SubElement(fn,q('w:embedRegular'),{q('r:id'):rid,q('w:fontKey'):'{'+str(key).upper()+'}'})
  manifest.append({'family':family,'sha256':sha(b),'fsType':fs,'full_font_embedded':True})
 parts['word/fontTable.xml']=xml(ft);parts[relpath]=xml(rels);parts['[Content_Types].xml']=xml(ct)
 st=E.fromstring(parts['word/settings.xml']);sub(st,'w:embedTrueTypeFonts');sub(st,'w:saveSubsetFonts',val=0);parts['word/settings.xml']=xml(st)
 return manifest

def numbering(parts):
 np='word/numbering.xml';rt=E.fromstring(parts[np]) if np in parts else E.Element(q('w:numbering'),nsmap={'w':W})
 absid=max([int(e.get(q('w:abstractNumId'))) for e in rt.findall('w:abstractNum',NS)]+[0])+1
 nid=max([int(e.get(q('w:numId'))) for e in rt.findall('w:num',NS)]+[0])+1
 an=E.Element(q('w:abstractNum'),{q('w:abstractNumId'):str(absid)});sub(an,'w:multiLevelType',val='multilevel')
 for level,(glyph,sz,spacing,pos) in enumerate([('■',46,-47,-3),('●',16,100,3),('○',11,150,4)]):
  lv=E.SubElement(an,q('w:lvl'),{q('w:ilvl'):str(level)});sub(lv,'w:start',val=1);sub(lv,'w:numFmt',val='bullet');sub(lv,'w:lvlText',val=glyph);sub(lv,'w:suff',val='space');sub(lv,'w:lvlJc',val='left')
  pp=sub(lv,'w:pPr');sub(pp,'w:ind',left=306+level*210,hanging=306)
  rp=sub(lv,'w:rPr');run_font(rp,'페이퍼로지 4 Regular',sz/2);sub(rp,'w:spacing',val=spacing);sub(rp,'w:position',val=pos)
 # abstract definitions must precede num instances
 first=rt.find('w:num',NS)
 if first is not None:first.addprevious(an)
 else:rt.append(an)
 nu=E.SubElement(rt,q('w:num'),{q('w:numId'):str(nid)});sub(nu,'w:abstractNumId',val=absid);parts[np]=xml(rt)
 return nid

def restore_consent_design(rt,parts,orig):
 """Keep sections 03 and 04 in the original contest form's actual styles."""
 original=E.fromstring(orig['word/document.xml']);oldbody=original.find('w:body',NS);body=rt.find('w:body',NS)
 def is_consent(n):return n.tag==q('w:p') and txt(n).startswith('03') and '동의서' in txt(n)
 oi=next(i for i,n in enumerate(oldbody) if is_consent(n));ni=next(i for i,n in enumerate(body) if is_consent(n))
 oldstyles=E.fromstring(orig['word/styles.xml']);styles=E.fromstring(parts['word/styles.xml'])
 ids={s.get(q('w:styleId')):'OriginalForm_'+s.get(q('w:styleId')) for s in oldstyles.findall('w:style',NS)}
 default_id=next(s.get(q('w:styleId')) for s in oldstyles.findall('w:style',NS) if s.get(q('w:type'))=='paragraph' and s.get(q('w:default'))=='1')
 default_r=oldstyles.find('w:docDefaults/w:rPrDefault/w:rPr',NS);default_p=oldstyles.find('w:docDefaults/w:pPrDefault/w:pPr',NS)
 # Character-unit indents in the official consent page use document/default-style metrics.
 # Keep those original metrics; proposal runs already have their new direct formatting.
 current_defaults=styles.find('w:docDefaults',NS)
 if current_defaults is not None:styles.remove(current_defaults)
 if oldstyles.find('w:docDefaults',NS) is not None:styles.insert(0,deepcopy(oldstyles.find('w:docDefaults',NS)))
 for s in list(styles.findall('w:style',NS)):
  if s.get(q('w:styleId'))==default_id:styles.remove(s)
 styles.append(deepcopy(next(s for s in oldstyles.findall('w:style',NS) if s.get(q('w:styleId'))==default_id)))
 def merge_defaults(target,defaults):
  if defaults is None:return
  for el in defaults:
   found=target.find(el.tag)
   if found is None:target.append(deepcopy(el))
   else:
    for k,v in el.attrib.items():
     if k not in found.attrib:found.set(k,v)
 for s in oldstyles.findall('w:style',NS):
  clone=deepcopy(s);clone.set(q('w:styleId'),ids[s.get(q('w:styleId'))]);clone.attrib.pop(q('w:default'),None)
  for child in clone:
   if E.QName(child).localname in ['basedOn','next','link'] and child.get(q('w:val')) in ids:child.set(q('w:val'),ids[child.get(q('w:val'))])
  # Document defaults changed for the proposal, so pin source defaults at each original style root.
  if clone.find('w:basedOn',NS) is None:
   merge_defaults(sub(clone,'w:rPr'),default_r);merge_defaults(sub(clone,'w:pPr'),default_p)
  nm=clone.find('w:name',NS)
  if nm is not None:nm.set(q('w:val'),'Original contest form '+nm.get(q('w:val'),''))
  styles.append(clone)
 def original_styles(node):
  for e in node.iter():
   if e.tag in [q('w:pStyle'),q('w:rStyle'),q('w:tblStyle')] and e.get(q('w:val')) in ids:e.set(q('w:val'),ids[e.get(q('w:val'))])
  for p in ([node] if node.tag==q('w:p') else [])+node.findall('.//w:p',NS):
   pp=pr(p,'w:pPr')
   if pp.find('w:pStyle',NS) is None:sub(pp,'w:pStyle',val=ids[default_id])
 for n in list(body)[ni:]:body.remove(n)
 for n in list(oldbody)[oi:]:
  clone=deepcopy(n);original_styles(clone);body.append(clone)
 # The original legal footer is retained throughout the official form.
 for path in orig:
  if re.fullmatch(r'word/(header|footer)\d+\.xml',path):
   r=E.fromstring(orig[path]);original_styles(r);parts[path]=xml(r)
 parts['word/styles.xml']=xml(styles)
 parts['word/document.xml']=xml(rt)

def restyle(name,official=False):
 source=R/(name+'.docx');baseline=BASE/source.name
 if not baseline.exists():shutil.copy2(source,baseline)
 if (R/(name+'.pdf')).exists() and not (BASE/(name+'.pdf')).exists():shutil.copy2(R/(name+'.pdf'),BASE/(name+'.pdf'))
 with zipfile.ZipFile(baseline) as z:orig={n:z.read(n) for n in z.namelist()}
 parts=dict(orig);rt=E.fromstring(parts['word/document.xml']);before=para_texts(rt)
 st=E.fromstring(parts['word/styles.xml'])
 for rp in st.findall('.//w:rPr',NS):run_font(rp)
 styles={e.get(q('w:styleId')):e for e in st.findall('w:style',NS)}
 for sid in ['Normal','a','0']:
  if sid in styles:
   run_font(sub(styles[sid],'w:rPr'),size=10.5);pformat(sub(styles[sid],'w:pPr'))
 for sid,sz in [('Title',26),('Heading1',14),('Heading2',11.5),('Heading3',10.5),('1',14),('21',11.5)]:
  if sid in styles:run_font(sub(styles[sid],'w:rPr'),'페이퍼로지 7 Bold',sz)
 for x in list(st.findall('.//w:pBdr',NS)):x.getparent().remove(x)
 parts['word/styles.xml']=xml(st)
 heading_level=0;in_cover=not official
 for p in rt.findall('.//w:p',NS):
  text=txt(p); pp=pr(p,'w:pPr');ps=pp.find('w:pStyle',NS);sid=ps.get(q('w:val'),'') if ps is not None else ''
  in_table=bool(p.xpath('ancestor::w:tc',namespaces=NS));is_picture=bool(p.findall('.//w:drawing',NS) or p.findall('.//w:pict',NS));is_code=any(x.get(q('w:ascii'))=='Consolas' for x in p.findall('.//w:rFonts',NS))
  set_runs(p)
  remove(pp,'w:pBdr')
  if is_picture:
   pformat(pp,'center',line=240,before=60,after=40);sub(pp,'w:spacing').set(q('w:lineRule'),'auto');sub(pp,'w:keepNext',val=1)
   continue
  if not text:
   # Existing page breaks and empty form cells remain in place.
   if not in_table:pformat(pp,'left',line=20,before=0,after=0)
   continue
  if in_table:continue
  if official:
   if sid=='1':level=0
   elif sid=='21':level=1
   else:level=None
  else:level=int(sid[-1])-1 if re.fullmatch('Heading[1-3]',sid) else None
  if sid=='Title':
   pformat(pp,'center',line=660,before=160,after=260);set_runs(p,26,'페이퍼로지 7 Bold');sub(pp,'w:keepNext',val=1)
  elif level is not None:
   heading_level=level;in_cover=False
   # Keep the Hyundai template's existing numbering/hierarchy; never import reference bullets.
   pformat(pp,'left',line=400 if level==0 else 340,before=240 if level==0 else 180,after=100,left=0,first=0)
   set_runs(p,[14,11.5,10.5][level],'페이퍼로지 7 Bold');sub(pp,'w:keepNext',val=1)
  elif sid=='Caption' or text.startswith('그림 '):
   pformat(pp,'right',line=220,before=60,after=80);set_runs(p,9,'페이퍼로지 4 Regular','505B6A');remove(pp,'w:keepNext')
  elif is_code:
   pformat(pp,'left',line=220,before=0,after=0,left=0)
   if text=='항상 보이는 긴급 액션':sub(pp,'w:keepNext',val=1)
   for rr in p.findall('.//w:r',NS):
    rp=pr(rr,'w:rPr');run_font(rp,'Consolas',8);sub(rp,'w:rFonts').set(q('w:eastAsia'),'페이퍼로지 4 Regular')
   sub(pp,'w:shd',fill='F3F5F8')
  elif official:
   # The form's own indents and line rhythm protect blank fields and consent layout.
   set_runs(p,10 if len(text)<170 else 9.5)
   if re.match(r'^[1-8]\. ',text):
    pformat(pp,'left',line=280,before=120,after=60);sub(pp,'w:keepNext',val=1)
    for rr in p.findall('.//w:r',NS):
     old=rr.find('w:rPr/w:sz',NS)
   elif text=='메뉴 구성과 화면 설계':
    pformat(pp,'left',line=340,before=180,after=100);set_runs(p,11.5,'페이퍼로지 7 Bold')
  else:
   left=0
   if in_cover:
    pformat(pp,'center' if len(text)<90 else 'both',line=360,before=60,after=180)
    set_runs(p,10.5)
   else:
    pformat(pp,'both',line=360,before=60,after=200,left=left,first=210)
    set_runs(p,10.5)
    if text.startswith('• '):pformat(pp,'left',line=360,before=40,after=100,left=left+210,first=-210)
   # Link source paragraphs use the reference's compact gray source style.
   if p.find('w:hyperlink',NS) is not None and len(text)<180:
    pformat(pp,'left',line=240,before=0,after=80);set_runs(p,9,'페이퍼로지 4 Regular')
   if re.search(r'[0-9a-fA-F]{64}',text):
    sub(pp,'w:jc',val='left')
 for table in rt.findall('.//w:tbl',NS):
  tp=pr(table,'w:tblPr');sub(tp,'w:jc',val='center');remove(tp,'w:tblBorders');b=sub(tp,'w:tblBorders')
  for edge in ['top','left','bottom','right','insideH','insideV']:sub(b,'w:'+edge,val='single',sz=4,color='AAB7C8')
  rows=table.findall('w:tr',NS);formbox=official and bool(table.findall('.//w:sdt',NS))
  if not official:
   # Scale to the reference's 17 cm text width without changing columns.
   cols=table.findall('w:tblGrid/w:gridCol',NS);total=sum(int(c.get(q('w:w'),'0')) for c in cols);target=9638
   if total:
    for c in cols:c.set(q('w:w'),str(round(int(c.get(q('w:w')))*target/total)))
    for tcw in table.findall('.//w:tcW',NS):
     if tcw.get(q('w:type'))=='dxa':tcw.set(q('w:w'),str(round(int(tcw.get(q('w:w'),'0'))*target/total)))
   sub(tp,'w:tblW',w=target,type='dxa')
  for i,row in enumerate(rows):
   trp=pr(row,'w:trPr');sub(trp,'w:cantSplit')
   if i==0 and not formbox:sub(trp,'w:tblHeader')
   for cell in row.findall('w:tc',NS):
    tcp=pr(cell,'w:tcPr');sub(tcp,'w:vAlign',val='center');sub(tcp,'w:shd',fill='FFFFFF' if formbox else ('E8EDF3' if i==0 else ('F7F8FA' if i%2==0 else 'FFFFFF')))
    remove(tcp,'w:tcMar');mar=sub(tcp,'w:tcMar')
    for k,v in [('top',55),('bottom',55),('left',85),('right',85)]:sub(mar,'w:'+k,w=v,type='dxa')
    for p in cell.findall('.//w:p',NS):
     pp=pr(p,'w:pPr');pformat(pp,'both' if formbox else 'center',line=340 if formbox else 262,before=0,after=0)
     set_runs(p,10 if formbox else 9.5,'페이퍼로지 4 Regular' if formbox or i else '페이퍼로지 7 Bold')
     if i==0 and not formbox:sub(pp,'w:keepNext',val=1)
 for extent in rt.findall('.//wp:extent',NS):
  cx=int(extent.get('cx'));cy=int(extent.get('cy'));limit=6120000
  if cx>limit:
   extent.set('cx',str(limit));extent.set('cy',str(round(cy*limit/cx)))
   parent=extent.getparent()
   for ext in parent.findall('.//a:xfrm/a:ext',NS):ext.set('cx',str(limit));ext.set('cy',str(round(cy*limit/cx)))
 if not official:
  for sec in rt.findall('.//w:sectPr',NS):sub(sec,'w:pgMar',top=1077,bottom=1020,left=1134,right=1134,header=397,footer=397,gutter=0)
 parts['word/document.xml']=xml(rt)
 # Preserve all original header/footer text, only match reference typography/alignment.
 for path in list(parts):
  if re.fullmatch(r'word/(header|footer)\d+\.xml',path):
   r=E.fromstring(parts[path]);isfooter='footer' in path
   for p in r.findall('.//w:p',NS):
    pp=pr(p,'w:pPr');pformat(pp,'center' if isfooter else 'left',line=200,before=0,after=0)
    set_runs(p,10.5 if isfooter and not official else 8,'페이퍼로지 4 Regular','000000' if isfooter else '505B6A')
   parts[path]=xml(r)
 if official:restore_consent_design(rt,parts,orig)
 manifest=embed_fonts(parts)
 if official:
  settings=E.fromstring(parts['word/settings.xml']);sub(sub(settings,'w:compat'),'w:doNotExpandShiftReturn')
  parts['word/settings.xml']=xml(settings)
 # Match known diagram bytes, leaving car, logo, splash and UI screenshots unchanged.
 media_changes=[]
 for path,data in list(parts.items()):
  if path.startswith('word/media/'):
   for fn in ['menu-flow.png','architecture.png','guidance-state.png','notification-state.png']:
    if data==(A/fn).read_bytes():parts[path]=(A/'paperlogy-diagrams'/fn).read_bytes();media_changes.append({'part':path,'asset':fn})
 after=para_texts(E.fromstring(parts['word/document.xml']));assert before==after,'Paragraph content/order changed'
 # All written words, links, control IDs, original field instructions, and table topology are invariant.
 for part in ['word/document.xml']+[p for p in orig if re.fullmatch(r'word/(header|footer)\d+\.xml',p)]:
  old=E.fromstring(orig[part]);new=E.fromstring(parts[part])
  for xp in ['.//w:t/text()','.//w:instrText/text()','.//w:sdtPr/w:tag/@w:val']:
   assert old.xpath(xp,namespaces=NS)==new.xpath(xp,namespaces=NS),(part,xp)
 for tag in ['p','tbl','tr','tc','sdt','drawing','sectPr','br']:
  assert len(E.fromstring(orig['word/document.xml']).findall('.//w:'+tag,NS))==len(rt.findall('.//w:'+tag,NS)),tag
 with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED) as z:
  for n,b in parts.items():z.writestr(n,b)
 return {'file':source.name,'baseline_sha256':sha(baseline.read_bytes()),'sha256':sha(source.read_bytes()),'exact_paragraph_text_and_order':True,'paragraphs':len(before),'table_count':len(rt.findall('.//w:tbl',NS)),'control_count':len(rt.findall('.//w:sdt',NS)),'text_fields_links_and_structure_preserved':True,'fonts':manifest,'diagram_media_changes':media_changes,'changed_parts':[n for n in orig if parts[n]!=orig[n]]}

def f(size,bold=False):return ImageFont.truetype(str(FONTS/('Paperlogy-7Bold.ttf' if bold else 'Paperlogy-4Regular.ttf')),size)
BLUE='#2663FF';TEXT='#202C40';MUTED='#5C6B80';LINE='#CAD5E5';FILL='#F3F6FA'
def arrow(d,a,b,color=BLUE):
 d.line([a,b],fill=color,width=4);t=math.atan2(b[1]-a[1],b[0]-a[0]);d.polygon([b,(b[0]-13*math.cos(t-.5),b[1]-13*math.sin(t-.5)),(b[0]-13*math.cos(t+.5),b[1]-13*math.sin(t+.5))],fill=color)
def label(d,xy,s,size=25,bold=False,fill=TEXT,anchor='mm'):
 d.text(xy,s,font=f(size,bold),fill=fill,anchor=anchor)
def diagrams():
 dest=A/'paperlogy-diagrams';dest.mkdir(exist_ok=True)
 for name,rows in [('menu-flow.png',[('홈','기능 선택\n연결 상태'),('이상 찾기','도감 · 사진\n상황 직접 선택'),('기록과 안내','자료 구간 보관\n음성 · 화면 설명'),('발송 확인','10초 확인\n즉시 발송 · 취소'),('보호자 확인','카카오톡 · 문자\n위치 · 받은 자료')]),('architecture.png',[('차량 입력','허용된 SDK\n사용자 상황 선택'),('품질과 판정','출처 · 시각 확인\n검토된 가이드'),('Pleos 앱','안내 · 기록\n휴대폰 백업'),('서버 처리','사건 · 발송 정책\n보관 · 권한'),('외부 전달','카카오톡 → 문자\n보호자 위치 화면')])]:
  im=Image.new('RGB',(1500,420),'white');d=ImageDraw.Draw(im)
  for i,(title,lines) in enumerate(rows):
   x=30+i*300;d.rounded_rectangle((x,48,x+260,360),12,fill=FILL,outline=LINE,width=2)
   label(d,(x+130,95),f'{i+1:02}',25,True,BLUE);label(d,(x+130,171),title,30,True)
   for j,ss in enumerate(lines.split('\n')):label(d,(x+130,242+j*42),ss,24,fill=MUTED)
   if i<4:arrow(d,(x+262,205),(x+298,205))
  im.save(dest/name)
 specs=[('guidance-state.png',{'O':(0,0,'상태 관측'),'A':(1,0,'급가속 후보'),'P':(2,0,'페달 안내'),'B':(3,0,'브레이크 감시'),'M':(1,1,'수동 상황 선택'),'S':(3,1,'비상 제동 안내'),'H':(2,2,'도움 요청 안내'),'E':(3,2,'정차 후 확인')},[('O','A',''),('A','P','조건 확인'),('P','B','입력 확인'),('O','M','자료 무효'),('B','S','감속 미관측'),('M','S','정지 실패'),('M','H','일반 도움'),('S','E','정차'),('H','E','')]),('notification-state.png',{'E':(0,0,'사건 생성'),'C':(1,0,'10초 확인'),'R':(2,0,'발송 해제'),'D':(3,0,'발송사 요청'),'O':(0,1,'기록만 보관'),'X':(1,1,'발송 취소'),'F':(2,1,'문자 대체'),'P':(3,1,'전달 결과 확인'),'N':(2,2,'전달 실패'),'S':(3,2,'전달 확인')},[('E','C','L2 · L3'),('E','O','L1'),('C','R','만료 · 즉시'),('C','X','괜찮습니다'),('R','D',''),('D','P',''),('P','F','실패 · 미확인'),('P','S','성공'),('F','N','실패'),('F','S','성공')])]
 for name,nodes,edges in specs:
  im=Image.new('RGB',(1640,900),'white');d=ImageDraw.Draw(im);pos={k:(30+c*410,80+r*300) for k,(c,r,_) in nodes.items()}
  labels=[]
  for src,dst,s in edges:
   x,y=pos[src];xx,yy=pos[dst];a=(x+140,y+110);b=(xx+140,yy)
   if y==yy:a=(x+280,y+55) if xx>x else (x,y+55);b=(xx,y+55) if xx>x else (xx+280,y+55)
   arrow(d,a,b)
   if s:labels.append(((a[0]+b[0])/2, y-23 if y==yy else (a[1]+b[1])/2,s))
  for k,(_,_,s) in nodes.items():
   x,y=pos[k];d.rounded_rectangle((x,y,x+280,y+110),12,fill=FILL,outline=LINE,width=2);label(d,(x+140,y+55),s,28,True)
  for x,y,s in labels:
   bb=d.textbbox((x,y),s,font=f(24),anchor='mm');d.rectangle((bb[0]-6,bb[1]-5,bb[2]+6,bb[3]+5),fill='white');label(d,(x,y),s,24,fill=MUTED)
  im.save(dest/name)

if __name__=='__main__':
 diagrams()
 results=[restyle('01_공식양식_플레오스가드',True),restyle('02_상세기획_기술명세')]
 report={'date':'2026-11-12','reference_thread':'01a1b3d8-8797-7880-83dd-b2f3b81029e8','reference_docx_sha256':sha(REF.read_bytes()),'scope':'Formatting only; all original text, controls, order, tables and approved app visuals preserved','documents':results,'visual_QA':'pending'}
 (QA/'style-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
 print(json.dumps([{'file':x['file'],'paragraphs_preserved':x['paragraphs'],'tables_preserved':x['table_count'],'embedded_fonts':len(x['fonts'])} for x in results],ensure_ascii=False))
