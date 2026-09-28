// Real Chromium hit-testing, with an isolated temporary profile. No browser extension required.
// Prerequisites: disposable backend :18080, frontend proxying to it at :15173.
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';

const profile = await mkdtemp(path.join(tmpdir(), 'atlas-recovery-browser-'));
const chromePath = process.env.ATLAS_CHROME ?? 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const chrome = spawn(chromePath, ['--headless=new', '--no-first-run', '--no-default-browser-check',
  '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'], { windowsHide: true, stdio: 'ignore' });
let socket;
async function until(check, timeout = 15000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    try { const result = await check(); if (result) return result; } catch { /* Process/page is still starting. */ }
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error('Browser assertion timed out');
}
try {
  const port = await until(async () => (await readFile(path.join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]);
  const page = await fetch(`http://127.0.0.1:${port}/json/new?http://127.0.0.1:15173`, { method: 'PUT' }).then(r => r.json());
  socket = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
  let nextId = 1; const pending = new Map();
  socket.onmessage = event => { const result = JSON.parse(event.data); const item = pending.get(result.id); if (item) { pending.delete(result.id); result.error ? item.reject(new Error(JSON.stringify(result.error))) : item.resolve(result.result); } };
  const call = (method, params = {}) => new Promise((resolve, reject) => { const id = nextId++; pending.set(id, { resolve, reject }); socket.send(JSON.stringify({ id, method, params })); });
  async function evaluate(expression) {
    const result = await call('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.text + ': ' + result.result.description);
    return result.result.value;
  }
  await call('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false });
  await until(() => evaluate('document.querySelector("#root") !== null'));

  const fixture = await evaluate(`(async () => {
    const credentials={email:'recovery-browser-'+crypto.randomUUID()+'@example.test',password:'Local-'+crypto.randomUUID()};
    window.http=async(p,body,method='POST')=>{const r=await fetch(p,{method,headers:{'Content-Type':'application/json',...(sessionStorage.getItem('atlas.session')?{Authorization:'Bearer '+sessionStorage.getItem('atlas.session')}:{}),'Idempotency-Key':crypto.randomUUID()},...(body===undefined?{}:{body:JSON.stringify(body)})});if(!r.ok)throw new Error(p+': '+r.status+' '+await r.text());return r.json()};
    await http('/api/auth/register',credentials);const auth=await http('/api/auth/login',credentials);sessionStorage.setItem('atlas.session',auth.token);
    await http('/users/me/working-hours',{timezone:'UTC',windows:Array.from({length:7},(_,i)=>[{dayOfWeek:i+1,startTime:'00:00',endTime:'12:00',kind:'working'},{dayOfWeek:i+1,startTime:'12:00',endTime:'00:00',kind:'working'}]).flat()},'PUT');
    const goal=await http('/goals',{title:'Review goal',targetDeadline:new Date(Date.now()-86400000).toISOString().slice(0,10)});
    const task=await http('/commitments',{title:'Browser recovery work',completionCriterion:'Read and summarize',importance:'medium',flexibilityTier:'flexible',goalId:goal.id});
    const past=new Date(Date.now()-3600000).toISOString();const end=new Date(Date.now()-1800000).toISOString();
    const plan=await http('/schedule/generate',{startTime:past,endTime:end,work:[{commitmentId:task.id,workMinutes:20}]});
    if(plan.placements.length!==1)throw new Error('Fixture placement failed');
    return {task:task.id,block:plan.placements[0].id,goal:goal.id};
  })()`);
  await call('Page.reload');
  await until(() => evaluate('document.querySelector(".page-heading h1")?.textContent === "Today"'));
  async function click(label) {
    await until(()=>evaluate(`!!Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()===${JSON.stringify(label)} && !b.disabled)`));
    await evaluate(`Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()===${JSON.stringify(label)}).click()`);
  }
  async function input(label,value) {
    await evaluate(`(()=>{const l=Array.from(document.querySelectorAll('label')).find(e=>e.textContent.startsWith(${JSON.stringify(label)}));const e=l?.querySelector('input,textarea,select') ?? document.querySelector('[aria-label='+JSON.stringify(${JSON.stringify(label)})+']');if(!e)throw new Error('Missing input');const proto=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:e.tagName==='SELECT'?HTMLSelectElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(proto,'value').set.call(e,${JSON.stringify(value)});e.dispatchEvent(new Event(e.tagName==='SELECT'?'change':'input',{bubbles:true}));})()`);
  }
  const workspace=()=>evaluate("fetch('/execution',{headers:{Authorization:'Bearer '+sessionStorage.getItem('atlas.session')}}).then(r=>r.json())");
  await click('Recovery and weekly review');await click('Check passed windows');
  await until(async()=> (await workspace()).blocks.find(b=>b.id===fixture.block)?.state==='unresolved');
  await input('Report','Half completed without the timer');await input('Current task completion %','50');await click('Save report');
  await until(async()=> (await workspace()).tasks.find(t=>t.id===fixture.task)?.completionPct===50).catch(async e=>{console.error(await evaluate("document.querySelector('[aria-label=Recovery]')?.textContent"));throw e;});
  assert.equal((await workspace()).history.length,0,'Retrospective report must not invent an ActualSession');
  await input('Total task estimate','60');await click('Find suitable time for selected work');
  const recovered=await until(async()=> (await workspace()).blocks.find(b=>b.commitmentId===fixture.task && b.state==='scheduled'));
  assert.equal(Date.parse(recovered.endTime)-Date.parse(recovered.startTime),1800000,'Remaining work is 50% of estimated effort');
  // Risk uses a zero-capacity past deadline, not invented contextual evidence.
  await evaluate(`(async()=>{const r=await fetch('/goals/${fixture.goal}/risk',{method:'POST',headers:{Authorization:'Bearer '+sessionStorage.getItem('atlas.session'),'Content-Type':'application/json','Idempotency-Key':crypto.randomUUID()},body:JSON.stringify({estimates:[{commitmentId:${fixture.task},totalWorkMinutes:60,localHour:9}]})});if(!r.ok)throw new Error(await r.text());return r.json()})()`);
  await call('Page.reload');await until(()=>evaluate('document.querySelector(".page-heading h1")?.textContent === "Today"'));await click('Recovery and weekly review');
  await until(()=>evaluate("document.body.textContent.includes('Goal at risk')"));
  assert.equal(await evaluate("Array.from(document.querySelectorAll('select')).find(e=>e.parentElement.textContent.includes('Choose a next step')).options.length"),5);
  const risk=await evaluate(`fetch('/goals/${fixture.goal}/risk',{headers:{Authorization:'Bearer '+sessionStorage.getItem('atlas.session')}}).then(r=>r.json())`);
  assert.equal(risk.awaitingResponse,true);assert.equal(risk.planningState,'active','Silence must not acknowledge the formal transition');
  // Real recurring generation and execution shares Focus/ActualSession infrastructure.
  const recurring=await evaluate(`(async()=>{const http=async(p,b)=>{const r=await fetch(p,{method:'POST',headers:{Authorization:'Bearer '+sessionStorage.getItem('atlas.session'),'Content-Type':'application/json','Idempotency-Key':crypto.randomUUID()},body:JSON.stringify(b)});if(!r.ok)throw new Error(await r.text());return r.json()};const r=await http('/recurring-intentions',{title:'Browser recurring practice',targetCountPerWeek:3,flexibilityTier:'flexible'});return r;})()`);
  await call('Page.reload');await until(()=>evaluate('document.querySelector(".page-heading h1")?.textContent === "Today"'));await click('Recovery and weekly review');
  await until(()=>evaluate("document.body.textContent.includes('Browser recurring practice')"));
  await input('Minutes per instance','20');await click("Schedule this week's remaining target");
  await until(async()=> (await workspace()).blocks.filter(b=>b.recurringIntentionId===recurring.id).length===3);
  console.log('PASS: Chromium missed detection, truthful retrospective partial report, progressive recovery, risk visibility/five choices/silence, and recurring generation through real HTTP.');
} finally {
  socket?.close(); chrome.kill();
  await new Promise(resolve => chrome.once('exit', resolve));
  const resolved = path.resolve(profile), parent = path.resolve(tmpdir());
  if (resolved.startsWith(parent + path.sep) && path.basename(resolved).startsWith('atlas-recovery-browser-'))
    await rm(resolved, { recursive: true, force: true, maxRetries: 5, retryDelay: 300 });
}
