import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, extname } from 'node:path';

/**
 * Verify the built Android bundle:
 *  1. index.html references RELATIVE asset paths (./assets/...)
 *  2. no CA method names anywhere in the built JS
 *  3. no bare `/wails/` import that would fail over file://
 *  4. the wails runtime is bundled (android transport available)
 */
const root = 'D:/c++/Contributions/Snishaper-Droid/app/src/main/assets/frontend';

const walk = (dir, out = []) => {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, out);
    else out.push(p);
  }
  return out;
};

const files = walk(root);
const jsFiles = files.filter((f) => extname(f) === '.js');
const html = readFileSync(join(root, 'index.html'), 'utf8');

let failed = false;
const check = (label, ok, detail = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${detail ? ` — ${detail}` : ''}`);
  if (!ok) failed = true;
};

// 1. relative asset paths
const scriptSrc = [...html.matchAll(/(?:src|href)="([^"]+)"/g)].map((m) => m[1]);
const absolute = scriptSrc.filter((s) => s.startsWith('/'));
check('index.html uses relative asset paths', absolute.length === 0, `refs: ${scriptSrc.join(', ')}`);
check('index.html references ./assets/', scriptSrc.every((s) => s.startsWith('./assets/')));

// 2. CA names must be absent
const CA_NAMES = [
  'InstallCA',
  'ExportCert',
  'RegenerateCert',
  'GetCACertPEM',
  'UninstallCert',
  'GetInstalledCerts',
  'OpenCertDir',
  'OpenCAFile',
  'GetCAInstallStatus',
  'GetCACertPath',
];
const caHits = [];
for (const f of jsFiles) {
  const text = readFileSync(f, 'utf8');
  for (const name of CA_NAMES) {
    if (text.includes(name)) caHits.push(`${f.split('\\').pop()}: ${name}`);
  }
}
check('no CA method names in built JS', caHits.length === 0, caHits.join(', '));

// also check for CA-related i18n strings
const CA_I18N = ['cert_installed', 'cert_not_installed', 'ca_management', 'install_cert', 'cert_policy'];
const i18nHits = [];
for (const f of jsFiles) {
  const text = readFileSync(f, 'utf8');
  for (const name of CA_I18N) {
    if (text.includes(name)) i18nHits.push(`${f.split('\\').pop()}: ${name}`);
  }
}
check('no CA i18n strings in built JS', i18nHits.length === 0, i18nHits.join(', '));

// 3. no bare /wails/ import
const wailsImportHits = [];
for (const f of jsFiles) {
  const text = readFileSync(f, 'utf8');
  if (/["']\/wails\//.test(text)) wailsImportHits.push(f.split('\\').pop());
}
check('no bare /wails/ import in built JS', wailsImportHits.length === 0, wailsImportHits.join(', '));

// 4. wails runtime bundled + android bridge present
let hasAndroidBridge = false;
let hasInvokeAsync = false;
for (const f of jsFiles) {
  const text = readFileSync(f, 'utf8');
  if (text.includes('invokeAsync')) hasInvokeAsync = true;
  if (text.includes('_wailsAndroidCallback')) hasAndroidBridge = true;
}
check('wails runtime bundled (invokeAsync present)', hasInvokeAsync);
check('android callback bridge present (_wailsAndroidCallback)', hasAndroidBridge);

console.log(failed ? '\nRESULT: FAILED' : '\nRESULT: ALL CHECKS PASSED');
process.exit(failed ? 1 : 0);
