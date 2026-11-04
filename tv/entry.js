// Angelfish's TV mode: VacuumTube's preload modules (MIT, see vacuumtube/LICENSE), run inside
// Android's WebView on YouTube's own TV app. Mirrors vacuumtube/preload/index.js, with the module
// list fixed at build time instead of read from disk.
require('./trusted-types')
require('./gamepad')

if (location.host === 'www.youtube.com' && location.pathname === '/tv') {
    const xhrModifiers = require('./vacuumtube/preload/util/xhrModifiers')
    xhrModifiers.block()

    for (const module of require('vt:modules')) {
        try {
            module()
        } catch (err) {
            console.error('a module experienced failure while loading', err)
        }
    }

    xhrModifiers.unblock()
}
