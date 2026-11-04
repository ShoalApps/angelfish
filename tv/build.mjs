// Bundles VacuumTube's preload modules into one script for the app's TV mode:
//   cd tv && npm ci && node build.mjs   ->   app/src/main/assets/tv/vacuumtube.js
import * as esbuild from 'esbuild'
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'

const here = path.dirname(new URL(import.meta.url).pathname)
const vt = path.join(here, 'vacuumtube')
const preload = path.join(vt, 'preload')
const out = path.join(here, '..', 'app', 'src', 'main', 'assets', 'tv', 'vacuumtube.js')

// VacuumTube's code expects to live at <root>/src/preload, <root>/locale and <root>/assets.
const virtualOf = (file) => {
    const rel = path.relative(vt, file)
    return '/vt/' + (rel.startsWith('preload') ? 'src/' + rel : rel)
}

function walk(dir) {
    return fs.readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(path.join(dir, e.name)) : [path.join(dir, e.name)])
}

// Every non-JS file VacuumTube reads from disk.
const files = {}
for (const file of [...walk(preload), ...walk(path.join(vt, 'locale')), path.join(vt, 'assets', 'icon.png')]) {
    if (file.endsWith('.js')) continue
    files[virtualOf(file)] = file.endsWith('.png') ? { base64: fs.readFileSync(file).toString('base64') } : { text: fs.readFileSync(file, 'utf8') }
}

// The module list preload/index.js builds with readdirSync, fixed here.
const modulesDir = path.join(preload, 'modules')
const modules = fs.readdirSync(modulesDir, { withFileTypes: true })
    .filter(e => e.isDirectory() || e.name.endsWith('.js'))
    .map(e => e.name === 'touch-support.js' ? path.join(here, 'touch.js') : path.join(modulesDir, e.isDirectory() ? e.name + '/index.js' : e.name))
    .sort()

// Config defaults from VacuumTube's main process, with Android-specific changes.
const hostConfig = fs.readFileSync(path.join(vt, 'host-config.js'), 'utf8')
const defaultsSource = hostConfig.match(/const defaults = (\{[\s\S]*?\n\})/)[1]
const defaults = vm.runInNewContext('(' + defaultsSource + ')', { crypto: { randomUUID: () => 'UUID' } })
Object.assign(defaults, {
    device_discoverability: false, // casting server needs raw sockets
    auto_update: false, // the app updates itself
    hardware_decoding: true,
    touch_overlay: false,
})

const version = JSON.parse(fs.readFileSync(path.join(here, 'vacuumtube-version.json'), 'utf8')).version

const virtual = {
    name: 'virtual',
    setup(build) {
        build.onResolve({ filter: /(?:^|\/)util\/css$/ }, a => a.importer.includes('vacuumtube') || a.importer.endsWith('/touch.js') ? {path:path.join(here,'shims/css.js')} : undefined)
        build.onResolve({ filter: /^vt:/ }, a => ({ path: a.path, namespace: 'vt' }))
        // VacuumTube's DIAL code reads its own package.json for the version.
        build.onResolve({ filter: /package\.json$/ }, a => a.importer.includes('vacuumtube') ? { path: 'vt:package', namespace: 'vt' } : undefined)
        build.onLoad({ filter: /.*/, namespace: 'vt' }, a => {
            if (a.path === 'vt:package') return { contents: 'module.exports = ' + JSON.stringify({ version }), loader: 'js' }
            if (a.path === 'vt:files') return { contents: 'module.exports = ' + JSON.stringify(files), loader: 'js' }
            if (a.path === 'vt:modules') return { contents: 'module.exports = [' + modules.map(m => `require(${JSON.stringify(m)})`).join(',\n') + ']', loader: 'js', resolveDir: here }
            if (a.path === 'vt:config-defaults') {
                const src = JSON.stringify(defaults).replace('"UUID"', 'crypto.randomUUID()')
                return { contents: 'module.exports = ' + src, loader: 'js' }
            }
        })
        // Give each VacuumTube file the __dirname it would have had on disk.
        build.onLoad({ filter: /vacuumtube[\/\\]preload[\/\\].*\.js$/ }, a => {
            const src = fs.readFileSync(a.path, 'utf8')
            const dir = path.dirname(virtualOf(a.path))
            return { contents: `var __dirname = ${JSON.stringify(dir)}, __filename = ${JSON.stringify(virtualOf(a.path))};\n` + src, loader: 'js' }
        })
    },
}

const shim = (name) => path.join(here, 'shims', name)
await esbuild.build({
    absWorkingDir: path.join(here, ".."),
    entryPoints: [path.join(here, 'entry.js')],
    bundle: true,
    format: 'iife',
    platform: 'browser',
    target: 'chrome100',
    outfile: out,
    legalComments: 'inline',
    banner: { js: `/* Angelfish TV mode. Includes VacuumTube ${version} (https://github.com/shy1132/VacuumTube), MIT License, Copyright (c) 2025-2026 shy. */` },
    define: { VT_VERSION: JSON.stringify(version), 'process.platform': '"android"', 'process.arch': '"arm64"', 'process.env.NODE_ENV': '"production"' },
    alias: {
        electron: shim('electron.js'),
        fs: shim('fs.js'),
        path: shim('path.js'),
        os: shim('os.js'),
        dgram: shim('unavailable.js'),
        http: shim('unavailable.js'),
        crypto: shim('unavailable.js'),
    },
    plugins: [virtual],
    logLevel: 'warning',
})
console.log('wrote', path.relative(path.join(here, '..'), out), (fs.statSync(out).size / 1024).toFixed(0) + ' KiB,', modules.length, 'modules')
