// Node >=18. Only creates draft confirmation cards; never calls confirmation endpoints.
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.EVAL_BASE_URL || 'http://127.0.0.1:18080';
const cases = JSON.parse(fs.readFileSync(path.join(__dirname,'evaluation-cases.json'),'utf8'));
async function request(route, token, body) {
  const response = await fetch(base+route,{method:body?'POST':'GET',headers:{'Content-Type':'application/json',...(token?{Authorization:token}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(180000)});
  if(!response.ok)throw new Error(`HTTP ${response.status}`);
  const payload=await response.json();if(payload.code!=='0')throw new Error(payload.message||'请求失败');return payload.data;
}
function dates() {
  const d=new Date();d.setUTCDate(d.getUTCDate()+14);while(d.getUTCDay()!==1)d.setUTCDate(d.getUTCDate()+1);
  const start=d.toISOString().slice(0,10);d.setUTCDate(d.getUTCDate()+1);return {START:start,END:d.toISOString().slice(0,10)};
}
async function main() {
  const login=await request('/api/auth/login',null,{username:process.env.EVAL_USERNAME||'employee',password:process.env.EVAL_PASSWORD||'Enterprise123!'});
  const token=login.token; if(!token)throw new Error('登录响应缺少 token');
  const meta=await request('/agent/v1/meta',token);
  const placeholders=dates();const results=[];
  for(const test of cases) {
    const started=Date.now();const events=[];let session;let error;
    try {
      for(const original of test.turns) {
        const prompt=original.replace(/\{\{(START|END)\}\}/g,(_,key)=>placeholders[key]);
        const query=new URLSearchParams({question:prompt,...(session?{conversationId:session}:{})});
        const response=await fetch(`${base}/agent/v1/chat?${query}`,{headers:{Authorization:token},signal:AbortSignal.timeout(180000)});
        if(!response.ok)throw new Error(`Chat HTTP ${response.status}`);
        const body=await response.text();let finished=false;
        for(const frame of body.replace(/\r\n/g,'\n').split('\n\n')) {
          const name=frame.split('\n').find(l=>l.startsWith('event:'))?.slice(6).trim();
          const raw=frame.split('\n').filter(l=>l.startsWith('data:')).map(l=>l.slice(5).trim()).join('\n');
          if(!name)continue;let data;try{data=JSON.parse(raw);}catch{data=raw;}
          events.push({name,data});if(name==='meta')session=data.conversationId;if(name==='done')finished=true;
          if(name==='error')throw new Error('Agent SSE error');
        }
        if(!finished)throw new Error('Agent stream did not complete');
      }
    } catch(e){error=e.message;}
    const called=[...new Set(events.filter(e=>e.name==='tool'&&e.data.status==='start').map(e=>e.data.name))];
    const missing=test.expectedTools.filter(t=>!called.includes(t));const forbidden=called.filter(t=>test.forbiddenTools.includes(t));
    results.push({id:test.id,category:test.category,session,elapsedMs:Date.now()-started,called,missing,forbidden,error,
      automaticCheck:!error&&!missing.length&&!forbidden.length?'PASS':'FAIL',semanticReview:'PENDING',reviewCriteria:test.reviewCriteria,events});
    console.log(`${test.id}: ${results.at(-1).automaticCheck}; semantic review pending`);
  }
  const passed=results.filter(r=>r.automaticCheck==='PASS').length;
  const report={timestamp:new Date().toISOString(),base,model:meta,automaticPassRate:passed/results.length,acceptancePassRate:null,
    note:'自动检查只覆盖工具路由及流完成。必须逐条审阅 reviewCriteria 并填写 semanticReview，才能计算实际目标成功率；PENDING 不计为成功。',results};
  const output=path.join(__dirname,'evaluation-results.local.json');fs.writeFileSync(output,JSON.stringify(report,null,2));
  console.log(`自动检查 ${passed}/${results.length}；语义验收待审阅。报告：${output}`);
  if(passed/results.length<0.9)process.exitCode=1;
}
main().catch(e=>{console.error(`评测未完成：${e.message}`);process.exitCode=1;});
