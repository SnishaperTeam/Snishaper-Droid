import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import path from 'path'

/**
 * SniShaper frontend build config — Android / WebView target.
 *
 * Output layout
 * -------------
 * The build project lives at `<repo>/frontend-android/` (a sibling of `app/`),
 * NOT under `app/src/main/assets/`, so that `node_modules` and the TypeScript
 * sources never end up inside the APK's assets (AGP packages everything under
 * `assets/` verbatim into the APK).
 *
 * `build.outDir` points straight at `app/src/main/assets/frontend/`, so
 * `vite build` emits the deployable bundle exactly where the Android WebView
 * expects it:
 *
 *   app/src/main/assets/frontend/index.html
 *   app/src/main/assets/frontend/assets/index-<hash>.js
 *   app/src/main/assets/frontend/assets/index-<hash>.css
 *
 * The WebView then loads `file:///android_asset/frontend/index.html`.
 *
 * `base: './'` is REQUIRED: the default `/` base would make the bundle request
 * `/assets/index-<hash>.js` from the filesystem root, which does not exist
 * under `file://`. With `./` every asset reference is relative to
 * `index.html`, so the page loads from the asset folder.
 *
 * `emptyOutDir: true` makes each build replace the previous output, so stale
 * hashed bundles never pile up inside the APK.
 *
 * Note on the `external` rule below: the desktop build marked `/wails/` as
 * external because a Wails desktop app serves the runtime from `/wails/`.
 * On Android the runtime is imported as the normal npm package
 * `@wailsio/runtime`, which bundles normally. The rule is kept only to avoid
 * an accidental absolute-path import; it never matches the npm specifier.
 */
export default defineConfig({
  // Relative asset URLs so the bundle works when loaded over file://
  base: './',
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    // Emit directly into the Android assets folder.
    outDir: path.resolve(__dirname, '../app/src/main/assets/frontend'),
    emptyOutDir: true,
    target: 'esnext',
    cssMinify: 'lightningcss',
    minify: 'oxc',
    reportCompressedSize: false,
    chunkSizeWarningLimit: 1000,
    rolldownOptions: {
      external: (id: string) => id.startsWith('/wails/'),
      onwarn(warning: any, warn: any) {
        if (warning.code === 'MODULE_LEVEL_DIRECTIVE' && warning.message.includes("'use client'")) return
        if (warning.code === 'CIRCULAR_DEPENDENCY') return
        warn(warning)
      },
    },
  },
  cacheDir: 'node_modules/.vite',
})
