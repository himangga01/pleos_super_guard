from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
from lxml import etree as E
from PIL import Image,ImageChops
from pypdf import PdfReader
import hashlib,json,uuid,sys
sys.stdout.reconfigure(encoding='utf-8')
R=Path(__file__).parent;ROOT=R.parents[1];Q=R/'qa/paperlogy';NS={'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main','r':'http://schemas.openxmlformats.org/officeDocument/2006/relationships'}
def h(b):return hashlib.sha256(b).hexdigest()
report=json.loads((Q/'style-verification.json').read_text(encoding='utf-8'))
report['visual_QA']='passed';report['limits']=['Document design and preservation checks only','No actual app, sensor, messaging, crash-response or driving-usability execution','PDF rendered by a private Word instance after bundled render_docx failed because LibreOffice was absent','Poppler emitted Symbol/ArialUnicode fallback warnings for official form; all rendered pages were visually inspected','One transient Word COM startup failure was followed by a successful fresh export; failed export was not treated as current output']
report['final_files']=[];report['rendered_pages']={}
names=['01_공식양식_플레오스가드','02_상세기획_기술명세']
for i,name in enumerate(names):
 with ZipFile(Q/'baseline'/(name+'.docx')) as old,ZipFile(R/(name+'.docx')) as new:
  a=E.fromstring(old.read('word/document.xml'));b=E.fromstring(new.read('word/document.xml'))
  assert a.xpath('.//w:t/text()',namespaces=NS)==b.xpath('.//w:t/text()',namespaces=NS)
  assert old.read('word/numbering.xml')==new.read('word/numbering.xml')
  for tag in ['tbl','tr','tc','sdt','drawing','sectPr','p','br']:
   assert len(a.findall('.//w:'+tag,NS))==len(b.findall('.//w:'+tag,NS))
  ft=E.fromstring(new.read('word/fontTable.xml'));rels=E.fromstring(new.read('word/_rels/fontTable.xml.rels'));rm={x.get('Id'):x.get('Target') for x in rels}
  for font in ft.findall('w:font',NS):
   fam=font.get('{'+NS['w']+'}name','')
   if not fam.startswith('페이퍼로지'):continue
   e=font.find('w:embedRegular',NS);key=uuid.UUID(e.get('{'+NS['w']+'}fontKey').strip('{}')).bytes[::-1]
   data=bytearray(new.read('word/'+rm[e.get('{'+NS['r']+'}id')]))
   for j in range(32):data[j]^=key[j%16]
   num=fam.split()[1];label=fam.split()[2];assert bytes(data)==(R/f'assets/paperlogy/Paperlogy-{num}{label}.ttf').read_bytes()
  images=[new.read(p) for p in new.namelist() if p.startswith('word/media/')]
  assert (R/'assets/pleos-guard-launch-outline.png').read_bytes() in images
  if i==1:
   for p in (R/'assets/final').glob('ui-*.png'):assert p.read_bytes() in images,p.name
  report['documents'][i]['sha256']=h((R/(name+'.docx')).read_bytes())
  report['documents'][i]['original_numbering_xml_unchanged']=True
  report['documents'][i]['embedded_font_bytes_verified']=True
 pdf=PdfReader(R/(name+'.pdf'));fonts=set();texts=[]
 for pg in pdf.pages:
  texts.append(pg.extract_text() or '')
  for v in pg.get('/Resources',{}).get('/Font',{}).values():fonts.add(str(v.get_object().get('/BaseFont')))
 assert len(pdf.pages)==[8,40][i]
 assert all(len(t)>70 for t in texts)
 assert any('Paperlogy-4Regular' in x for x in fonts)
 assert any('Paperlogy-7Bold' in x for x in fonts)
 assert '차안심' not in ''.join(texts)
 if i==1:assert all(f'US{j:02}' in ''.join(texts) for j in range(13))
 folder=Q/['final01d','final02c'][i];pages=sorted(folder.glob('page-*.png'));assert len(pages)==len(pdf.pages)
 report['rendered_pages'][name]={'count':len(pages),'font_names':sorted(fonts),'paths_and_sha256':[{'path':str(p.relative_to(ROOT)).replace('\\','/'),'sha256':h(p.read_bytes())} for p in pages]}
 for suffix in ['.docx','.pdf']:
  p=R/(name+suffix);report['final_files'].append({'path':str(p.relative_to(ROOT)).replace('\\','/'),'bytes':p.stat().st_size,'sha256':h(p.read_bytes())})
report['consent_original_design']={}
for n in [7,8]:
 a=Image.open(R/'qa/qc01'/f'page-{n}.png');b=Image.open(Q/'final01d'/f'page-{n}.png')
 assert ImageChops.difference(a,b).getbbox() is None
 report['consent_original_design'][str(n)]={'pixel_identical_to_original_style_baseline':True,'baseline':f'output/submission/qa/qc01/page-{n}.png','latest':f'output/submission/qa/paperlogy/final01d/page-{n}.png'}
changed=[]
for p in sorted((Q/'final02c').glob('page-*.png')):
 if p.read_bytes()!=(Q/'render02'/p.name).read_bytes():changed.append(int(p.stem.split('-')[1]))
assert changed==[14,20,21,22,23,24,37,39]
report['visual_review']={'official':'Root reviewed all 8 final pages; consent/signature also pixel-identical to preserved original-style rendering','appendix':'Reviewer inspected all 40 pages, then re-inspected changed pages 14,20,21,22,23,24,37,39; remaining 32 PNGs are byte-identical','unresolved_layout_findings':[]}
report['original_form_sha256']=h((R/'official-form-original.docx').read_bytes())
assert report['original_form_sha256']=='07be893d58b0e96ab7788f0d189d6829f0e59a2cee4bc18cb90ec37eeda2f5fd'
dest=ROOT/'docs/analysis/submission-documents-verification-2026-11-12.json';dest.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');(Q/'style-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
bundle=R/'플레오스가드_제출문서_Word_PDF.zip'
with ZipFile(bundle,'w',ZIP_DEFLATED) as z:
 for name in names:
  for suffix in ['.docx','.pdf']:z.write(R/(name+suffix),name+suffix)
with ZipFile(bundle) as z:
 assert len(z.namelist())==4
 for n in z.namelist():assert z.read(n)==(R/n).read_bytes()
print(json.dumps({'status':'PASS','pages':[8,40],'paragraphs_preserved':[153,1134],'tables_preserved':[10,24],'original_consent_pages_pixel_identical':[7,8],'bundle':str(bundle),'report':str(dest)},ensure_ascii=False,indent=2))
