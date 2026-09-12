// Imports the demonstration policy through the existing ingestion API. Node >=20.
const fs=require('node:fs');const path=require('node:path');
const base=process.env.ENTERPRISE_BASE_URL||'http://127.0.0.1:18080';
async function main(){
  async function call(route,options={}){const r=await fetch(base+route,{...options,signal:AbortSignal.timeout(120000)});if(!r.ok)throw new Error(`HTTP ${r.status}`);const p=await r.json();if(p.code!=='0')throw new Error(p.message);return p.data;}
  const login=await call('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:process.env.ENTERPRISE_ADMIN||'admin',password:process.env.ENTERPRISE_ADMIN_PASSWORD||'admin'})});
  const headers={Authorization:login.token,'Content-Type':'application/json'};
  const statePath=path.join(__dirname,'seed-state.local.json');
  let state=fs.existsSync(statePath)?JSON.parse(fs.readFileSync(statePath,'utf8')):{};
  if(state.base&&state.base!==base)throw new Error('种子状态属于其他服务，请使用独立目录或核对状态文件');
  const save=()=>fs.writeFileSync(statePath,JSON.stringify({...state,base},null,2));
  if(!state.kbId){state.kbId=await call('/knowledge-base',{method:'POST',headers,body:JSON.stringify({name:'企业请假制度（演示）',embeddingModel:process.env.ENTERPRISE_EMBEDDING_MODEL||'BAAI/bge-m3',collectionName:'enterprise_leave_demo'})});save();}
  await call(`/api/enterprise/admin/knowledge/${state.kbId}/grants`,{method:'PUT',headers,body:JSON.stringify([{audience:'ALL',subject:'*'}])});
  if(!state.docId){const form=new FormData();form.set('sourceType','file');form.set('processMode','chunk');form.set('file',new Blob([fs.readFileSync(path.join(__dirname,'请假制度-v1.0.md'))],{type:'text/markdown'}),'请假制度-v1.0.md');const doc=await call(`/knowledge-base/${state.kbId}/docs/upload`,{method:'POST',headers:{Authorization:login.token},body:form});state.docId=doc.id;save();}
  await call(`/api/enterprise/admin/documents/${state.docId}/policy`,{method:'PUT',headers,body:JSON.stringify({version:'v1.0',effectiveDate:'2026-09-01'})});
  if(!state.chunkRequested){await call(`/knowledge-base/docs/${state.docId}/chunk`,{method:'POST',headers});state.chunkRequested=true;save();}
  console.log(`演示知识库 ${state.kbId}，文档 ${state.docId} 已提交分块任务。请在管理后台确认入库完成，再运行模型评测。`);
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
