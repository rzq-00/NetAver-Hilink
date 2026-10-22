import http from 'node:http';
import fs from 'node:fs';

const getJson = (path) => new Promise((res, rej) => {
  http.get({ host: 'localhost', port: 9223, path }, (r) => {
    let d = ''; r.on('data', (c) => d += c); r.on('end', () => res(JSON.parse(d)));
  }).on('error', rej);
});

const targets = await getJson('/json/list');
const page = targets.find((t) => t.type === 'page');
if (!page) { console.error('no page target'); process.exit(1); }

const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });

let id = 0;
const pending = new Map();
const loadWaiters = [];
ws.onmessage = (ev) => {
  const m = JSON.parse(ev.data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
  if (m.method === 'Page.loadEventFired') loadWaiters.splice(0).forEach((f) => f());
};
const send = (method, params = {}) => new Promise((res) => {
  const mid = ++id; pending.set(mid, res);
  ws.send(JSON.stringify({ id: mid, method, params }));
});
const waitLoad = () => new Promise((res) => { loadWaiters.push(res); setTimeout(res, 6000); });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

await send('Page.enable');
await send('Emulation.setDeviceMetricsOverride', { width: 360, height: 760, deviceScaleFactor: 2, mobile: true });

const base = 'file:///home/hatch/workspace/netaver-hilink/mockups-v3-settings/';
for (const name of ['wifi', 'dhcp', 'ussd', 'apn']) {
  const lp = waitLoad();
  await send('Page.navigate', { url: base + name + '.html' });
  await lp; await sleep(500);
  const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true, clip: { x: 0, y: 0, width: 360, height: 760, scale: 1 } });
  const data = shot.result?.data;
  if (!data) { console.error(name, 'NO DATA', JSON.stringify(shot).slice(0, 200)); continue; }
  fs.writeFileSync('png/' + name + '.png', Buffer.from(data, 'base64'));
  console.log(name, 'OK');
}
ws.close();
process.exit(0);
