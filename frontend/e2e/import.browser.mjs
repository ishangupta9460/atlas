// Real Chromium hit-testing, with an isolated temporary profile. No browser extension required.
// Prerequisites: disposable backend :18080, frontend proxying to it at :15175.
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';

const profile = await mkdtemp(path.join(tmpdir(), 'atlas-import-browser-'));
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
  const page = await fetch(`http://127.0.0.1:${port}/json/new?http://127.0.0.1:15175`, { method: 'PUT' }).then(r => r.json());
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

  await evaluate(`(async()=>{
    const credentials={email:'import-browser-'+crypto.randomUUID()+'@example.test',password:'Local-'+crypto.randomUUID()};
    window.http=async(p,body,method='POST')=>{const r=await fetch(p,{method,headers:{'Content-Type':'application/json',...(sessionStorage.getItem('atlas.session')?{Authorization:'Bearer '+sessionStorage.getItem('atlas.session')}:{}),'Idempotency-Key':crypto.randomUUID()},...(body===undefined?{}:{body:JSON.stringify(body)})});if(!r.ok)throw new Error(p+': '+r.status+' '+await r.text());return r.json()};
    await http('/api/auth/register',credentials);const auth=await http('/api/auth/login',credentials);sessionStorage.setItem('atlas.session',auth.token);
    window.fixtureGoal=await http('/goals',{title:'Browser import goal'});
  })()`);
  await call('Page.reload');
  async function click(label) {
    await until(()=>evaluate(`!!Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()===${JSON.stringify(label)} && !b.disabled)`));
    await evaluate(`Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()===${JSON.stringify(label)}).click()`);
  }
  async function input(label,value) {
    await evaluate(`(()=>{const l=Array.from(document.querySelectorAll('label')).find(e=>e.textContent.startsWith(${JSON.stringify(label)}));const e=l?.querySelector('input,textarea,select');if(!e)throw new Error('Missing input '+${JSON.stringify(label)});const proto=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:e.tagName==='SELECT'?HTMLSelectElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(proto,'value').set.call(e,${JSON.stringify(value)});e.dispatchEvent(new Event(e.tagName==='SELECT'?'change':'input',{bubbles:true}));})()`);
  }
  await click('Import');
  await until(()=>evaluate('document.querySelector("input[type=file]") !== null'));
  await evaluate(`(()=>{const dt=new DataTransfer();dt.items.add(new File(['# Phase\\n- Build browser feature\\n  - resource: Guide https://example.com'],'plan.md',{type:'text/markdown'}));const e=document.querySelector('input[type=file]');e.files=dt.files;e.dispatchEvent(new Event('change',{bubbles:true}));})()`);
  await click('Upload for review');
  await until(()=>evaluate("document.body.textContent.includes('Draft / Review')"));
  assert.equal(await evaluate(`fetch('/execution',{headers:{Authorization:'Bearer '+sessionStorage.getItem('atlas.session')}}).then(r=>r.json()).then(w=>w.tasks.length)`),0);
  await input('Completion criterion for #2','Browser flow works');await click('Save review edits');
  await until(()=>evaluate("!Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='Save review edits')?.disabled === false"));
  const goal=await evaluate(`fetch('/goals',{headers:{Authorization:'Bearer '+sessionStorage.getItem('atlas.session')}}).then(r=>r.json()).then(r=>r.goals[0].id)`);
  await input('Goal for this roadmap',String(goal));await input('Task importance','medium');await input('Task flexibility','flexible');await click('Approve reviewed import');
  await until(()=>evaluate("document.body.textContent.includes('Approved / Imported')"));
  const imported=await evaluate(`fetch('/roadmaps/import',{headers:{Authorization:'Bearer '+sessionStorage.getItem('atlas.session')}}).then(r=>r.json()).then(r=>r[0])`);
  assert.equal(imported.result.commitmentIds.length,1);assert.equal(imported.scheduledCount,0);
  await click('Open imported task #'+imported.result.commitmentIds[0]+' · schedule next');
  await until(()=>evaluate("document.querySelector('[aria-label=\"Task brief\"]')?.textContent.includes('Guide')"));
  await click('Import');await input('Import type','fixed');
  await until(()=>evaluate("document.querySelector('input[type=file]')?.accept.includes('png')"));
  await evaluate(`(async()=>{const canvas=document.createElement('canvas');canvas.width=20;canvas.height=20;const blob=await new Promise(r=>canvas.toBlob(r,'image/png'));const dt=new DataTransfer();dt.items.add(new File([blob],'schedule.png',{type:'image/png'}));const e=document.querySelector('input[type=file]');e.files=dt.files;e.dispatchEvent(new Event('change',{bubbles:true}));})()`);
  await input('Optional schedule transcript','Class | 2026-10-01T09:00:00Z | 2026-10-01T10:00:00Z');await click('Upload for review');
  await until(()=>evaluate("document.body.textContent.includes('schedule.png · Draft / Review')"));
  await click('Approve reviewed import');await until(()=>evaluate("document.body.textContent.includes('Approved fixed commitments now reserve calendar time.')"));
  console.log('PASS: real Chromium document upload, no preapproval domain work, review edit, approval, attached resource and screenshot review/approval through real HTTP.');
} finally {
  socket?.close(); chrome.kill();
  await new Promise(resolve => chrome.once('exit', resolve));
  const resolved = path.resolve(profile), parent = path.resolve(tmpdir());
  if (resolved.startsWith(parent + path.sep) && path.basename(resolved).startsWith('atlas-import-browser-'))
    await rm(resolved, { recursive: true, force: true, maxRetries: 5, retryDelay: 300 });
}
