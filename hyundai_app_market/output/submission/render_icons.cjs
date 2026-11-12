const fs=require('fs'),path=require('path');
const sharp=require('C:/Program Files/WindowsApps/OpenAI.CodexPrimaryRuntime.v26-1104-2876-0_26.1104.2876.0_x64__3k8sg7r9htsxt/dependencies/node/node_modules/sharp');
const dir=path.join(__dirname,'assets','icons');
const extra={
 'triangle-alert':'<path d="M10.3 3.9 1.8 18.5A2 2 0 0 0 3.5 21h17a2 2 0 0 0 1.7-2.5L13.7 3.9a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4m0 4h.01"/>',
 'circle-help':'<circle cx="12" cy="12" r="10"/><path d="M9.1 9a3 3 0 0 1 5.8 1c0 2-3 2-3 4m.1 3h.01"/>',
 'history':'<path d="M3 11a9 9 0 1 1 2.8 7M3 4v7h7m2-4v6l4 2"/>',
 'octagon-alert':'<path d="m8 2-6 6v8l6 6h8l6-6V8l-6-6Z"/><path d="M12 7v6m0 4h.01"/>'
};
for(const [n,s] of Object.entries(extra))fs.writeFileSync(path.join(dir,n+'.svg'),`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${s}</svg>`);
(async()=>{for(const name of fs.readdirSync(dir).filter(n=>n.endsWith('.svg'))){let s=fs.readFileSync(path.join(dir,name),'utf8');for(const c of ['FFFFFF','5EEAD4','FFC857','FF5B6B','9FA4FF','B8C5D8','090E16'])await sharp(Buffer.from(s.replaceAll('currentColor','#'+c))).resize(144,144).png().toFile(path.join(dir,name.replace('.svg','-'+c+'.png')));}console.log('icons rendered');})();
