// Performs a real, confirmed PERSONAL-leave demonstration on the three demo accounts.
const base=process.env.ENTERPRISE_BASE_URL||'http://127.0.0.1:18080';
async function call(route,token,body){const r=await fetch(base+route,{method:body?'POST':'GET',headers:{'Content-Type':'application/json',...(token?{Authorization:token}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(30000)});if(!r.ok)throw new Error(`HTTP ${r.status}`);const p=await r.json();if(p.code!=='0')throw new Error(p.message);return p.data;}
async function main(){
  const tokens={};for(const name of ['employee','manager','hr'])tokens[name]=(await call('/api/auth/login',null,{username:name,password:process.env.ENTERPRISE_DEMO_PASSWORD||'Enterprise123!'})).token;
  const existing=await call('/api/enterprise/leave-requests',tokens.employee);const date=new Date();date.setUTCDate(date.getUTCDate()+14);
  let start;for(let i=0;i<90;i++){const d=date.toISOString().slice(0,10);if(date.getUTCDay()>0&&date.getUTCDay()<6&&!existing.some(r=>['PENDING_MANAGER','PENDING_HR','APPROVED'].includes(r.status)&&r.startDate<=d&&r.endDate>=d)){start=d;break;}date.setUTCDate(date.getUTCDate()+1);}if(!start)throw new Error('未找到空闲演示日期');
  const common=()=>({conversationId:'smoke-workflow',requestKey:crypto.randomUUID()});
  const action=await call('/api/enterprise/leave-requests/prepare',tokens.employee,{...common(),leaveType:'PERSONAL',startDate:start,endDate:start,reason:'自动验收演示（事假，不消耗年假）'});
  await call(`/api/enterprise/actions/${action.id}/confirm`,tokens.employee,{});
  for(const actor of ['manager','hr']){const a=await call(`/api/enterprise/approval-tasks/${action.requestId}/decision/prepare`,tokens[actor],{...common(),decision:'APPROVE',comment:'自动验收演示'});await call(`/api/enterprise/actions/${a.id}/confirm`,tokens[actor],{});}
  const detail=await call(`/api/enterprise/leave-requests/${action.requestId}`,tokens.employee);if(detail.request.status!=='APPROVED')throw new Error('最终状态不正确');
  console.log(`PASS ${action.requestId} ${start}：员工提交 → 主管通过 → 人事通过；审计 ${detail.timeline.length} 条。`);
}
main().catch(e=>{console.error(`验收失败：${e.message}`);process.exitCode=1;});
