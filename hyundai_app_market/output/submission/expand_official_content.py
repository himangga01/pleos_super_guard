"""Expand only visible official-form fields 2-6 to exactly 3,000 characters."""
from pathlib import Path
from copy import deepcopy
from zipfile import ZipFile,ZIP_DEFLATED
from lxml import etree as E
import json,hashlib,shutil,sys
sys.stdout.reconfigure(encoding='utf-8')
R=Path(__file__).parent;ROOT=R.parents[1];Q=R/'qa/content3000';Q.mkdir(parents=True,exist_ok=True);B=Q/'baseline';B.mkdir(exist_ok=True)
SOURCE=ROOT/'docs/submission/official-content-3000';NAME='01_공식양식_플레오스가드'
W='http://schemas.openxmlformats.org/wordprocessingml/2006/main';NS={'w':W};qn=lambda s:'{'+W+'}'+s
def digest(b):return hashlib.sha256(b).hexdigest()
def text(s):return ''.join(s.xpath('.//w:t/text()',namespaces=NS))
for name in [NAME+'.docx',NAME+'.pdf','02_상세기획_기술명세.docx','02_상세기획_기술명세.pdf']:
 if not (B/name).exists():shutil.copy2(R/name,B/name)
record=ROOT/'docs/analysis/submission-documents-verification-2026-11-12.json'
if record.exists() and not (B/'previous-verification.json').exists():shutil.copy2(record,B/'previous-verification.json')
with ZipFile(B/(NAME+'.docx')) as z:parts={n:z.read(n) for n in z.namelist()}
original=E.fromstring(parts['word/document.xml']);root=deepcopy(original);controls=root.findall('.//w:sdt',NS);old=original.findall('.//w:sdt',NS)
assert len(controls)==8
files=['02-background.txt','03-features.txt','04-scenarios.txt','05-development.txt','06-impact.txt']
titles=['기획 배경','앱 주요 기능 및 차량 특화 요소','주요 사용 시나리오','개발 계획','기대효과 및 발전 가능성'];counts=[]
for index,(filename,title) in enumerate(zip(files,titles),1):
 raw=(SOURCE/filename).read_text(encoding='utf-8').strip().replace('\r\n','\n')
 paragraphs=raw.split('\n\n');assert len(paragraphs)==12
 count=len(raw);assert count==3000,(filename,count)
 s=controls[index];content=s.find('w:sdtContent',NS);p=content.find('w:p',NS)
 if p is None:p=s.xpath('ancestor::w:p',namespaces=NS)[-1]
 # Retain the original paragraph/run properties and plain-text content-control IDs.
 first_run=content.find('.//w:r',NS);assert first_run is not None
 for c in list(first_run):
  if c.tag!=qn('rPr'):first_run.remove(c)
 for line_no,line in enumerate(raw.split('\n')):
  if line_no:E.SubElement(first_run,qn('br'))
  t=E.SubElement(first_run,qn('t'));t.set('{http://www.w3.org/XML/1998/namespace}space','preserve');t.text=line
 for r in content.findall('.//w:r',NS):
  if r is not first_run:
   for c in list(r):
    if c.tag!=qn('rPr'):r.remove(c)
 pp=p.find('w:pPr',NS)
 if pp is None:pp=E.Element(qn('pPr'));p.insert(0,pp)
 # The long answer must continue naturally across pages, keeping the source box design.
 for kind in ['keepLines','keepNext']:
  for el in list(pp.findall(qn(kind))):pp.remove(el)
  E.SubElement(pp,qn(kind),{qn('val'):'0'})
 for rowprop in s.xpath('ancestor::w:tr/w:trPr',namespaces=NS):
  for el in list(rowprop.findall('w:cantSplit',NS)):rowprop.remove(el)
 multiline=s.find('w:sdtPr/w:text',NS)
 if multiline is not None:multiline.set(qn('multiLine'),'1')
 assert len(text(s))+len(s.findall('.//w:br',NS))==3000
 counts.append({'item':index+1,'title':title,'word_characters_including_spaces_and_manual_breaks':count,'text_characters_excluding_manual_breaks':len(text(s)),'manual_line_breaks':len(s.findall('.//w:br',NS)),'paragraphs':len(paragraphs),'source':str((SOURCE/filename).relative_to(ROOT)).replace('\\','/'),'sha256':digest(raw.encode())})
# Protect all unrequested content and metadata.
for index in [0,6,7]:assert E.tostring(controls[index])==E.tostring(old[index])
def without_targets(rt):
 copy=deepcopy(rt)
 for s in copy.findall('.//w:sdt',NS)[1:6]:
  for pp in s.xpath('ancestor::w:p/w:pPr',namespaces=NS):
   for kind in ['keepLines','keepNext']:
    for el in list(pp.findall(qn(kind))):pp.remove(el)
  s.getparent().remove(s)
 # Only pagination settings on the five edited rows are allowed to change.
 for e in list(copy.findall('.//w:cantSplit',NS)):e.getparent().remove(e)
 return E.tostring(copy)
assert without_targets(root)==without_targets(original),'Content outside the requested fields changed'
parts['word/document.xml']=E.tostring(root,xml_declaration=True,encoding='UTF-8',standalone=True)
out=R/(NAME+'.docx')
with ZipFile(out,'w',ZIP_DEFLATED) as z:
 for n,b in parts.items():z.writestr(n,b)
with ZipFile(B/(NAME+'.docx')) as a,ZipFile(out) as b:
 assert [n for n in a.namelist() if a.read(n)!=b.read(n)]==['word/document.xml']
for name in ['02_상세기획_기술명세.docx','02_상세기획_기술명세.pdf']:assert (R/name).read_bytes()==(B/name).read_bytes()
report={'date':'2026-11-12','request':'Visible fields 2-6 each 3,000 characters, including spaces; one-line summary unchanged','counting':'Microsoft Word wdStatisticCharactersWithSpaces, including manual line breaks; field labels and outside content are not counted','baseline_sha256':digest((B/(NAME+'.docx')).read_bytes()),'items':counts,'total_authored_characters':sum(x['word_characters_including_spaces_and_manual_breaks'] for x in counts),'one_line_summary_unchanged':True,'reference_and_shortform_fields_unchanged':True,'identity_consent_signature_images_styles_numbering_unchanged':True,'appendix_unchanged':True,'changed_package_parts':['word/document.xml'],'docx_sha256':digest(out.read_bytes()),'visual_QA':'pending'}
(Q/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
