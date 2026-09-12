// Browser-only UI contract test using mocked HTTP responses; not a live backend acceptance test.
const { chromium }=require(process.env.PLAYWRIGHT_MODULE_PATH||'playwright');
const path=require('node:path');
async function main(){
  const browser=await chromium.launch({headless:true,channel:process.env.PLAYWRIGHT_CHANNEL||'msedge'});
  try{
    const page=await browser.newPage({viewport:{width:1280,height:900}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.addInitScript(()=>{localStorage.setItem('myagent_token','ui-test-only');localStorage.setItem('myagent_user',JSON.stringify({userId:'e',username:'employee',nickname:'演示员工',role:'user',token:'ui-test-only'}));});
    let action;let confirms=0;const request={id:'r1',userId:'e',leaveType:'ANNUAL',startDate:'2030-01-07',endDate:'2030-01-08',days:2,reason:'家庭安排',status:'DRAFT',managerId:'主管王明',hrId:'人事李华',createdAt:'2026-09-11T10:00:00'};
    await page.route('**/api/**',async route=>{
      const url=new URL(route.request().url());const p=url.pathname;let data;
      if(p==='/api/user/me')data={userId:'e',username:'employee',nickname:'演示员工',role:'user'};
      else if(p.endsWith('/me'))data={userId:'e',displayName:'演示员工',department:'研发部',businessRole:'EMPLOYEE',active:true};
      else if(p.endsWith('/leave-balances'))data={available:10,frozen:0,used:0};
      else if(p.endsWith('/leave-requests/prepare')){action={id:'a1',kind:'SUBMIT',requestId:'r1',payload:JSON.stringify({summary:request}),status:'PENDING',expiresAt:'2030-01-01T12:00:00'};data=action;}
      else if(p.endsWith('/actions/a1/confirm')){confirms++;request.status='PENDING_MANAGER';action.status='DONE';data=action;}
      else if(p.endsWith('/actions/a1'))data=action;
      else if(p.endsWith('/actions'))data=action?[action]:[];
      else if(p.endsWith('/approval-tasks'))data=[];
      else if(p.endsWith('/leave-requests/r1'))data={request,tasks:[],timeline:[{id:'audit1',event:request.status,comment:'用户确认提交',createdAt:'2026-09-11T10:00:00'}]};
      else if(p.endsWith('/leave-requests'))data=action?[request]:[];
      else {await route.fulfill({status:404,contentType:'application/json',body:JSON.stringify({code:'NOT_FOUND',message:p})});return;}
      await route.fulfill({contentType:'application/json',body:JSON.stringify({code:'0',data})});
    });
    await page.goto((process.env.FRONTEND_URL||'http://127.0.0.1:5175')+'/enterprise');
    await page.getByText('演示员工 · 研发部 · 员工').waitFor();
    await page.getByLabel('开始日期').fill('2030-01-07');await page.getByLabel('结束日期').fill('2030-01-08');await page.getByLabel('申请原因').fill('家庭安排');
    await page.getByRole('button',{name:'生成确认卡'}).click();await page.getByRole('button',{name:'确认执行',exact:true}).waitFor();
    if(confirms!==0)throw new Error('准备卡片意外执行提交');
    await page.screenshot({path:path.join(__dirname,'workbench.local.png'),fullPage:true});
    await page.getByRole('button',{name:'确认执行',exact:true}).click();await page.getByText('操作已完成，可在申请详情查看最新进度。').waitFor();
    if(confirms!==1)throw new Error('确认调用次数不正确');
    await page.reload();await page.getByText('操作已完成，可在申请详情查看最新进度。').waitFor();
    await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(__dirname,'workbench-mobile.local.png'),fullPage:true});
    if(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth+1))throw new Error('移动端页面水平溢出');
    if(errors.length)throw new Error(errors.join('\n'));
    console.log('PASS UI: prepare without submit, explicit confirm once, reload restoration, mobile layout, no page errors. HTTP is mocked.');
  }finally{await browser.close();}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
