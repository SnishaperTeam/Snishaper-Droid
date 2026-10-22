// Evaluate a JavaScript expression in the running WebView via the Chrome
// DevTools Protocol. Used to inspect the bridge state inside the page.
//
// Usage: node scripts/cdp-eval.mjs "<expression>"
// Requires: adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>

const expression = process.argv[2] ?? '1+1';
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

ws.addEventListener('message', (event) => {
  const msg = JSON.parse(event.data);
  if (msg.id && pending.has(msg.id)) {
    pending.get(msg.id)(msg);
    pending.delete(msg.id);
  }
});

const send = (method, params) =>
  new Promise((resolve) => {
    const msgId = ++id;
    pending.set(msgId, resolve);
    ws.send(JSON.stringify({ id: msgId, method, params }));
  });

await new Promise((resolve) => ws.addEventListener('open', resolve, { once: true }));

const result = await send('Runtime.evaluate', {
  expression,
  returnByValue: true,
  awaitPromise: true,
});

ws.close();

if (result.result?.exceptionDetails) {
  console.error('EXCEPTION:', JSON.stringify(result.result.exceptionDetails, null, 2));
  process.exit(2);
}
console.log(JSON.stringify(result.result?.result?.value ?? result.result, null, 2));
