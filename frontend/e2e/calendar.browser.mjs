// Real Chromium hit-testing, with an isolated temporary profile. No browser extension required.
// Prerequisites: disposable backend :18080, frontend proxying to it at :15173.
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';

const profile = await mkdtemp(path.join(tmpdir(), 'atlas-calendar-browser-'));
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
  // Seed only this disposable local account through real public APIs.
  const fixture = await evaluate(`(async () => {
    const credentials={email:'browser-'+crypto.randomUUID()+'@example.test',password:'Local-'+crypto.randomUUID()};
    const http=async(p,body,method='POST',token)=>{const r=await fetch(p,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{}),'Idempotency-Key':crypto.randomUUID()},...(body===undefined?{}:{body:JSON.stringify(body)})});if(!r.ok)throw new Error(p+': '+r.status);return r.json()};
    await http('/api/auth/register',credentials);const auth=await http('/api/auth/login',credentials);
    sessionStorage.setItem('atlas.session',auth.token);
    await http('/users/me/working-hours',{timezone:'UTC',windows:Array.from({length:7},(_,i)=>[{dayOfWeek:i+1,startTime:'00:00',endTime:'12:00',kind:'working'},{dayOfWeek:i+1,startTime:'12:00',endTime:'00:00',kind:'working'}]).flat()},'PUT',auth.token);
    const task=await http('/commitments',{title:'Browser calendar verification',completionCriterion:'Move persists and valid targets receive pointer input',importance:'medium',flexibilityTier:'flexible'},'POST',auth.token);
    const day=new Date(Date.now()+86400000).toISOString().slice(0,10);
    await http('/schedule/generate',{startTime:day+'T09:00:00Z',endTime:day+'T17:00:00Z',work:[{commitmentId:task.id,workMinutes:30}]},'POST',auth.token);
    return {day,nextWeek:new Date().getUTCDay()===0};
  })()`);
  await call('Page.reload');
  await until(() => evaluate('document.querySelector(".page-heading h1")?.textContent === "Today"'));
  const clickButton = async label => {
    const selector = `Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()===${JSON.stringify(label)})`;
    const position = await evaluate(`(() => {const e=${selector}; if(!e || e.disabled)throw new Error('Button unavailable');e.scrollIntoView({block:'center'});const r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()`);
    await call('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, ...position });
    await call('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, ...position });
  };
  await clickButton('Schedule');
  await until(() => evaluate("!!document.querySelector('[aria-label=\"Next week\"]')"));
  if (fixture.nextWeek) await evaluate("document.querySelector('[aria-label=\"Next week\"]').click()");
  await until(() => evaluate("!!document.querySelector('[aria-label=\"Week calendar\"] article')"));
  await clickButton('Move Browser calendar verification');
  const destination = async hour => evaluate(`(() => {
    const day=document.querySelector('[aria-label="Week calendar"] section[aria-label="${fixture.day}"]');
    const slot=day.querySelectorAll('.week-slot')[${hour * 2}];slot.scrollIntoView({block:'center',inline:'center'});
    const r=slot.getBoundingClientRect(),x=r.x+r.width*.7,y=r.y+r.height/2;
    return {x,y,receivesPointer:document.elementFromPoint(x,y)===slot};
  })()`);
  const target = await destination(10);
  assert.equal(target.receivesPointer, true, 'Expanded block must not cover the valid 10:00 target');
  await call('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, x: target.x, y: target.y });
  await call('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, x: target.x, y: target.y });
  const workspace = () => evaluate("fetch('/execution',{headers:{Authorization:'Bearer '+sessionStorage.getItem('atlas.session')}}).then(r=>r.json())");
  const moved = await until(async () => (await workspace()).blocks.find(b => b.state === 'scheduled' && b.startTime === fixture.day + 'T10:00:00Z'));
  assert.equal(moved.userMovedFlag, true);
  await until(() => evaluate("!Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='Refresh')?.disabled"));
  // CDP dispatches drag events through Chromium's actual coordinate hit-testing.
  await evaluate(`(() => {const e=document.querySelector('[aria-label="Week calendar"] article');const d=new DataTransfer();d.setData('text/plain','${moved.id}');e.dispatchEvent(new DragEvent('dragstart',{bubbles:true,dataTransfer:d}));})()`);
  const dragTarget = await destination(11); assert.equal(dragTarget.receivesPointer, true);
  const data = { items: [{ mimeType: 'text/plain', data: String(moved.id) }], dragOperationsMask: 1 };
  for (const type of ['dragEnter', 'dragOver', 'drop']) await call('Input.dispatchDragEvent', { type, x: dragTarget.x, y: dragTarget.y, data });
  await evaluate("document.querySelector('[aria-label=\"Week calendar\"] article')?.dispatchEvent(new DragEvent('dragend',{bubbles:true}))");
  const dragged = await until(async () => (await workspace()).blocks.find(b => b.state === 'scheduled' && b.startTime === fixture.day + 'T11:00:00Z'));
  assert.equal(dragged.userMovedFlag, true);
  await call('Page.reload');
  await until(() => evaluate('document.querySelector(".page-heading h1")?.textContent === "Today"'));
  assert.equal((await workspace()).blocks.find(b => b.id === dragged.id).startTime, fixture.day + 'T11:00:00Z');
  await clickButton('Schedule');
  await until(() => evaluate("!!document.querySelector('[aria-label=\"Next week\"]')"));
  if (fixture.nextWeek) await evaluate("document.querySelector('[aria-label=\"Next week\"]').click()");
  if (process.env.ATLAS_BROWSER_SCREENSHOT) {
    await destination(11);
    const screenshot = await call('Page.captureScreenshot', { format: 'png' });
    await writeFile(process.env.ATLAS_BROWSER_SCREENSHOT, Buffer.from(screenshot.data, 'base64'));
  }
  console.log('PASS: real Chromium pointer hit-testing, keyboard move, coordinate drag/drop, sticky flag and reload.');
} finally {
  socket?.close(); chrome.kill();
  await new Promise(resolve => chrome.once('exit', resolve));
  const resolved = path.resolve(profile), parent = path.resolve(tmpdir());
  if (resolved.startsWith(parent + path.sep) && path.basename(resolved).startsWith('atlas-calendar-browser-'))
    await rm(resolved, { recursive: true, force: true, maxRetries: 5, retryDelay: 300 });
}
