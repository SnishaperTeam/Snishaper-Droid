import { readFileSync, writeFileSync } from 'node:fs';

/**
 * Make the fixed-width control containers in Settings.tsx responsive.
 *
 * On a phone the SettingRowInline control now drops onto its own line
 * (width: 100%), so the inner boxes must not keep a hard pixel width or they
 * overflow the viewport. Replace `width: N, flexShrink: 0` with
 * `width: { xs: '100%', sm: N }, flexShrink: 0`.
 */
const file = 'src/pages/Settings.tsx';
let text = readFileSync(file, 'utf8');

const before = text;
text = text.replace(
  /width: (220|240|320), flexShrink: 0/g,
  "width: { xs: '100%', sm: $1 }, flexShrink: 0"
);

if (text === before) {
  console.log('no changes applied');
} else {
  const count = (before.match(/width: (220|240|320), flexShrink: 0/g) || []).length;
  writeFileSync(file, text, 'utf8');
  console.log(`Settings.tsx: made ${count} fixed-width control(s) responsive`);
}
