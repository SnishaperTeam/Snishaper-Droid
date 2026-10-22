// Reload the page through CDP while collecting console messages, exceptions
// and failed requests. Prints everything the browser reported.
//
// Usage: node scripts/cdp-reload-diagnose.mjs
const port = process.env.CDP_PORT ?? '9222';

const list = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
const target = list.find((t) => t.type === 'page');
if (!target) {
  console.error('no page target found');
  process.exit(1);
}

const ws = new WebSocket(target.webSocketDebuggerUrl);
let id = 0;
const pending = new Map();
const events = [];

ws.addEventListener('message', (event) => {
  const msg = JSON.parse(event.data);
  if (msg.id && pending.has(msg.id)) {
    pending.get(msg.id)(msg);
    pending.delete(msg.id);
    return;
  }
  if (msg.method === 'Runtime.consoleAPICalled') {
    const args = (msg.params.args ?? [])
      .map((a) => a.value ?? a.description ?? a.type)
      .join(' ');
    events.push(`[console.${msg.params.type}] ${args}`);
  } else if (msg.method === 'Runtime.exceptionThrown') {
    const d = msg.params.exceptionDetails;
    events.push(
      `[exception] ${d.exception?.description ?? d.text} @ ${d.url ?? ''}:${d.lineNumber ?? ''}`,
    );
  } else if (msg.method === 'Log.entryAdded') {
    const e = msg.params.entry;
    events.push(`[log.${e.level}] ${e.text} ${e.url ?? ''}`);
  } else if (msg.method === 'Network.loadingFailed') {
    events.push(`[net.failed] ${msg.params.errorText} ${msg.params.type}`);
  } else if (msg.method === 'Network.responseReceived') {
    const r = msg.params.response;
    if (r.status >= 400) events.push(`[net.${r.status}] ${r.url}`);
  }
});

const send = (method, params) =>
  new Promise((resolve) => {
    const msgId = ++id;
    pending.set(msgId, resolve);
    ws.send(JSON.stringify({ id: msgId, method, params }));
  });

await new Promise((resolve) => ws.addEventListener('open', resolve, { once: true }));

await send('Runtime.enable', {});
await send('Log.enable', {});
await send('Network.enable', {});
await send('Page.enable', {});

await send('Page.reload', { ignoreCache: true });

await new Promise((r) => setTimeout(r, 7000));

// Report the DOM state too.
const dom = await send('Runtime.evaluate', {
  expression: `JSON.stringify({
    rootChildren: document.getElementById('root')?.children.length ?? -1,
    bodyText: (document.body.innerText || '').slice(0, 300),
    path: location.pathname
  })`,
  returnByValue: true,
});

ws.close();

console.log('--- browser events ---');
console.log(events.length ? events.join('\n') : '(none)');
console.log('--- dom ---');
console.log(dom.result?.result?.value ?? '(no dom result)');
