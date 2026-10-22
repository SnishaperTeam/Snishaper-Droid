/**
 * One-off helper: remove the CA-related i18n keys from the Android locale
 * files. Run with:  node scripts/purge-ca-i18n.mjs
 *
 * The locale files are JSON with 2-space indentation, except the
 * `tutorial.s*` entries which are written as compact one-line objects. This
 * script deletes the keys via a JSON round-trip and then restores the compact
 * one-line form for the tutorial entries, so the resulting diff only contains
 * the removed keys.
 *
 * Keys removed (all verified to have ZERO remaining references in src/):
 *   dashboard.cert_status, dashboard.cert_installed, dashboard.cert_not_installed,
 *   dashboard.path_pending, dashboard.install_cert.*
 *   dns.cert_policy, dns.verify_mode, dns.allow_unknown, dns.allow_names,
 *   dns.allow_suffixes, dns.modes.*, dns.mode_descs.*
 *   rules.form.cert_verify_warn, rules.form.allow_spki_list,
 *   rules.form.placeholder_domains, rules.form.placeholder_suffixes,
 *   rules.form.placeholder_spki
 *   rules.cert_policy.*
 *   settings.ca_management.*, settings.certs_count, settings.tabs.security
 *   tutorial.s4
 *
 * `rules.form.placeholder_domains` was only used by the removed cert-verify
 * "allowed names" textarea, so it goes too.
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const localesDir = resolve(here, '../src/i18n/locales');

/** Whole objects (with all children) to delete. */
const DELETE_BLOCKS = [
  'dashboard.install_cert',
  'dns.modes',
  'dns.mode_descs',
  'rules.cert_policy',
  'settings.ca_management',
];

/** Leaf keys to delete. */
const DELETE_LEAVES = [
  'dashboard.cert_status',
  'dashboard.cert_installed',
  'dashboard.cert_not_installed',
  'dashboard.path_pending',
  'dns.cert_policy',
  'dns.verify_mode',
  'dns.allow_unknown',
  'dns.allow_names',
  'dns.allow_suffixes',
  'rules.form.cert_verify_warn',
  'rules.form.allow_spki_list',
  'rules.form.placeholder_domains',
  'rules.form.placeholder_suffixes',
  'rules.form.placeholder_spki',
  'settings.certs_count',
  'settings.tabs.security',
  'tutorial.s4',
];

const deletePath = (obj, path) => {
  const parts = path.split('.');
  let node = obj;
  for (let i = 0; i < parts.length - 1; i += 1) {
    if (!node || typeof node !== 'object') return false;
    node = node[parts[i]];
  }
  if (!node || typeof node !== 'object') return false;
  const last = parts[parts.length - 1];
  if (!(last in node)) return false;
  delete node[last];
  return true;
};

/**
 * Re-compact the `tutorial.s*` entries onto one line, matching the original
 * hand-written style of the locale files.
 */
const compactTutorial = (text) =>
  text.replace(
    /^(\s*)"s(\d)": \{\n\s*"title": (".*?"),\n\s*"text": (".*?")\n\s*\}(,?)$/gm,
    '$1"s$2": { "title": $3, "text": $4 }$5'
  );

for (const lang of ['zh', 'en', 'ru']) {
  const file = resolve(localesDir, `${lang}.json`);
  const original = readFileSync(file, 'utf8');
  const data = JSON.parse(original);

  const removed = [];
  for (const path of DELETE_BLOCKS) if (deletePath(data, path)) removed.push(path);
  for (const path of DELETE_LEAVES) if (deletePath(data, path)) removed.push(path);

  const next = compactTutorial(`${JSON.stringify(data, null, 2)}\n`);
  writeFileSync(file, next, 'utf8');

  console.log(`${lang}.json: removed ${removed.length} key(s)`);
  for (const p of removed) console.log(`   - ${p}`);
}
