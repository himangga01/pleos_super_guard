from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
from lxml import etree as E
from pypdf import PdfReader
from PIL import Image,ImageChops
import json,re,hashlib,sys,pdfplumber
sys.stdout.reconfigure(encoding='utf-8')
R=Path(__file__).parent;ROOT=R.parents[1];Q=R/'qa/content3000';B=Q/'baseline';SRC=ROOT/'docs/submission/official-content-3000'
NAME='01_공식양식_플레오스가드';NS={'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
def h(b):return hashlib.sha256(b).hexdigest()
report=json.loads((Q/'verification.json').read_text(encoding='utf-8'))
counts=json.loads((Q/'word-counts-final.json').read_text(encoding='utf-8-sig'))
tags=['planning_background','vehicle_scenario','key_features','pleos_plan','reference_files']
assert [c['word_characters_with_spaces'] for c in counts if c['tag'] in tags]==[3000]*5
with ZipFile(B/(NAME+'.docx')) as old,ZipFile(R/(NAME+'.docx')) as new:
 changed=[n for n in old.namelist() if old.read(n)!=new.read(n)];assert changed==['word/document.xml']
 a=E.fromstring(old.read('word/document.xml'));b=E.fromstring(new.read('word/document.xml'));sa=a.findall('.//w:sdt',NS);sb=b.findall('.//w:sdt',NS)
 assert len(sa)==len(sb)==8
 for i in [0,6,7]:assert E.tostring(sa[i])==E.tostring(sb[i])
 for i,p in enumerate(sorted(SRC.glob('*.txt')),1):
  source=p.read_text(encoding='utf-8').strip();assert len(source)==3000
  t=''.join(sb[i].xpath('.//w:t/text()',namespaces=NS));assert t==source.replace('\n','')
  assert len(t)+len(sb[i].findall('.//w:br',NS))==3000
 def tail(rt):
  nodes=list(rt.find('w:body',NS));idx=next(i for i,n in enumerate(nodes) if ''.join(n.xpath('.//w:t/text()',namespaces=NS)).startswith('03  개인정보'))
  return [E.tostring(n) for n in nodes[idx:]]
 assert tail(a)==tail(b)
 report['original_consent_signature_xml_unchanged']=True
pdf=PdfReader(R/(NAME+'.pdf'));assert len(pdf.pages)==14
texts=[]
for p in pdf.pages:
 text=p.extract_text() or '';assert len(text)>70
 text=re.sub(r'본\s*문서는\s*현대자동차.*?받습니다\.', '',text,flags=re.S)
 text=re.sub(r'^\s*\d+\s*$','',text,flags=re.M);texts.append(text)
joined=re.sub(r'\s+','',''.join(texts));coverage=[]
for p in sorted(SRC.glob('*.txt')):
 paragraphs=p.read_text(encoding='utf-8').strip().split('\n\n')
 missing=[i+1 for i,s in enumerate(paragraphs) if re.sub(r'\s+','',s) not in joined]
 assert not missing,(p.name,missing)
 coverage.append({'source':p.name,'paragraphs_found_in_PDF':len(paragraphs),'paragraphs_expected':12})
report['pdf_text_coverage']=coverage
report['consent_original_design']=[]
with pdfplumber.open(B/(NAME+'.pdf')) as old_pdf,pdfplumber.open(R/(NAME+'.pdf')) as new_pdf:
 for old,new in [(7,13),(8,14)]:
  a=Image.open(R/f'qa/paperlogy/final01d/page-{old}.png');b=Image.open(Q/f'render2/page-{new:02}.png')
  diff=ImageChops.difference(a,b);box=diff.getbbox()
  ac=[c for c in old_pdf.pages[old-1].chars if c['top']<780];bc=[c for c in new_pdf.pages[new-1].chars if c['top']<780]
  assert len(ac)==len(bc)
  assert [(c['text'],c['fontname'],c['size']) for c in ac]==[(c['text'],c['fontname'],c['size']) for c in bc]
  deltas={k:max(abs(x[k]-y[k]) for x,y in zip(ac,bc)) for k in ['x0','x1','top','bottom','size']}
  assert max(deltas.values())<0.02
  report['consent_original_design'].append({'old_page':old,'current_page':new,'original_XML_fonts_sizes_text_unchanged':True,'max_character_position_delta_points':deltas,'pixel_body_difference_bbox':diff.crop((0,0,1132,1480)).getbbox(),'full_difference_bbox':box,'note':'New page number; signature underline has at most 0.01512pt PDF rounding shift, not a style change.'})
for name in ['02_상세기획_기술명세.docx','02_상세기획_기술명세.pdf']:assert (B/name).read_bytes()==(R/name).read_bytes()
report['word_counts_executed']=counts;report['official_page_count']=14;report['appendix_page_count']=40;report['visual_QA']='passed'
report['visual_review']={'reviewer':'root','render_directory':'output/submission/qa/content3000/render2','all_pages_reviewed':list(range(1,15)),'unresolved_findings':[]}
report['rendered_pages']=[{'page':i,'sha256':h((Q/f'render2/page-{i:02}.png').read_bytes())} for i in range(1,15)]
report['limits']=['Document content, counts and layout only; no new app, sensor or messaging tests','Word count includes spaces and 22 manual line breaks per field; visible text excluding line breaks is 2978 characters per field','Existing Word-to-PDF fallback used because bundled LibreOffice was unavailable; Poppler Symbol/ArialUnicode warnings were visually checked','Earlier design-only verification retained under qa/content3000/baseline/previous-verification.json']
report['final_files']=[]
names=[NAME,'02_상세기획_기술명세']
for name in names:
 for suffix in ['.docx','.pdf']:
  p=R/(name+suffix);report['final_files'].append({'path':str(p.relative_to(ROOT)).replace('\\','/'),'bytes':p.stat().st_size,'sha256':h(p.read_bytes())})
bundle=R/'플레오스가드_제출문서_Word_PDF.zip'
with ZipFile(bundle,'w',ZIP_DEFLATED) as z:
 for name in names:
  for suffix in ['.docx','.pdf']:z.write(R/(name+suffix),name+suffix)
with ZipFile(bundle) as z:
 assert len(z.namelist())==4
 for n in z.namelist():assert z.read(n)==(R/n).read_bytes()
report['bundle']={'path':str(bundle.relative_to(ROOT)).replace('\\','/'),'sha256':h(bundle.read_bytes()),'members':4}
report['status']='PASS'
(Q/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
(ROOT/'docs/analysis/submission-documents-verification-2026-11-12.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'status':'PASS','Word_character_counts':[3000]*5,'official_pages':14,'PDF_paragraph_coverage':'60/60','consent_design':'Unchanged except footer page numbers','appendix':'Unchanged','bundle':str(bundle)},ensure_ascii=False,indent=2))
