import { readFileSync } from 'node:fs';

/**
 * Cross-check the frontend's pass-through method names against the Kotlin
 * bridge's dispatch list. Any method the frontend sends but the bridge does
 * not implement would throw "unsupported Android bridge method".
 */
const bindings = readFileSync('src/api/bindings.ts', 'utf8');
const bridge = readFileSync(
  '../app/src/main/java/com/snishaper/android/webview/EngineBridge.kt',
  'utf8'
);

// Methods the frontend forwards to the host (appCall('X')).
const forwarded = [...bindings.matchAll(/appCall\('([A-Za-z0-9_]+)'/g)].map((m) => m[1]);

// Methods the Kotlin bridge implements.
const implemented = new Set(
  [...bridge.matchAll(/^\s*"([A-Za-z0-9_]+)"\s*->/gm)].map((m) => m[1])
);

const missing = [...new Set(forwarded)].filter((m) => !implemented.has(m));

console.log(`frontend forwards ${new Set(forwarded).size} distinct method(s)`);
console.log(`bridge implements ${implemented.size} method(s)`);
if (missing.length) {
  console.log(`\nMISSING in bridge (${missing.length}):`);
  for (const m of missing) console.log(`  - ${m}`);
} else {
  console.log('\nOK: every forwarded method is implemented by the bridge');
}
