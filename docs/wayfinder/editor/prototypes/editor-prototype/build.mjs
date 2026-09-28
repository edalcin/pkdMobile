// PROTOTYPE build script. Bundles the PKD's real TipTap extensions (imported straight from
// S:/git/pkd, never edited) into a single self-contained JS file for a WebView under
// file:///android_asset/. Run: `node build.mjs` (from this directory).
//
// Reuses the esbuild already installed in pkd/frontend/node_modules instead of npm-installing
// a duplicate copy here — this whole tree is throwaway.
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import fs from 'node:fs'
import zlib from 'node:zlib'

const require = createRequire(import.meta.url)
const here = path.dirname(fileURLToPath(import.meta.url))
const pkdFrontend = 'S:/git/pkd/frontend'
const esbuild = require(path.join(pkdFrontend, 'node_modules/esbuild/lib/main.js'))

const outDir = path.join(here, 'dist')
fs.mkdirSync(outDir, { recursive: true })

// Redirect the two PWA-coupled imports (docs/research/bundle-editor.md §1) to prototype stubs,
// without touching pkd source.
const stubPlugin = {
  name: 'pkd-prototype-stubs',
  setup(build) {
    build.onResolve({ filter: /^mermaid$/ }, () => ({
      path: path.join(here, 'src/stub-mermaid.js'),
    }))
    build.onResolve({ filter: /\.\.\/api\.js$/ }, (args) => {
      if (args.importer.replace(/\\/g, '/').endsWith('lib/editor/link-suggestion.js')) {
        return { path: path.join(here, 'src/stub-api.js') }
      }
      return null
    })
  },
}

const result = await esbuild.build({
  entryPoints: [path.join(here, 'src/entry.js')],
  bundle: true,
  outfile: path.join(outDir, 'editor-prototype.js'),
  format: 'iife',
  target: 'chrome100', // Android WebView (System WebView on API 31+) is Chromium-based
  minify: true,
  sourcemap: false,
  nodePaths: [path.join(pkdFrontend, 'node_modules')],
  plugins: [stubPlugin],
  logLevel: 'info',
  metafile: true,
})

fs.copyFileSync(path.join(here, 'src/editor-prototype.css'), path.join(outDir, 'editor-prototype.css'))
fs.copyFileSync(path.join(here, 'editor.html'), path.join(outDir, 'editor.html'))

const jsBytes = fs.statSync(path.join(outDir, 'editor-prototype.js')).size
const cssBytes = fs.statSync(path.join(outDir, 'editor-prototype.css')).size
const jsGzip = zlib.gzipSync(fs.readFileSync(path.join(outDir, 'editor-prototype.js'))).length
const cssGzip = zlib.gzipSync(fs.readFileSync(path.join(outDir, 'editor-prototype.css'))).length

console.log(`\neditor-prototype.js  : ${(jsBytes / 1024).toFixed(1)} KB raw / ${(jsGzip / 1024).toFixed(1)} KB gzip`)
console.log(`editor-prototype.css : ${(cssBytes / 1024).toFixed(1)} KB raw / ${(cssGzip / 1024).toFixed(1)} KB gzip`)
console.log(`Output: ${outDir}`)

esbuild.stop()
