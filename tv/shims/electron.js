// Stands in for Electron inside Android's WebView: the "main process" is Angelfish, reached
// through the PufferfishTV bridge (TvBridge.kt). Only what VacuumTube's preload modules use.
const defaults = require('vt:config-defaults')

const bridge = window.PufferfishTV
const listeners = {}

function emit(channel, ...args) {
    for (const fn of listeners[channel] || []) {
        try { fn({}, ...args) } catch (err) { console.error('[pufferfish] listener failed', channel, err) }
    }
}
window.__pufferfishEmit = emit // the app calls this for focus/blur

function readConfig() {
    let stored = {}
    try { stored = JSON.parse(bridge.getConfig() || '{}') } catch {}
    let app = {}
    try { app = JSON.parse(bridge.getAppDefaults() || '{}') } catch {}
    return { ...defaults, ...app, ...stored, touch_overlay: false }
}

function open(url) { bridge.openUrl(url) }

const about = () => ({
    status: 'idle',
    currentVersion: VT_VERSION,
    latestVersion: '',
    platform: 'android',
    arch: 'arm64',
    installationType: 'swordfish',
    canAutoUpdate: false,
    progress: null,
})

const handlers = {
    'reload': () => location.reload(),
    'set-fullscreen': () => {},
    'set-on-top': () => {},
    'is-steam': () => false,
    'get-userstyles': () => [],
    'get-deeplink': () => bridge.getDeepLink() || null,
    'get-about-info': about,
    'check-for-updates': about,
    'install-update': () => {},
    'open-update-download': () => open('https://github.com/ShoalApps/angelfish/releases/latest'),
    'open-project-page': () => open('https://github.com/shy1132/VacuumTube'),
    'open-release-page': () => open('https://github.com/ShoalApps/angelfish/releases/latest'),
    'open-userstyles-folder': () => {},
    'request-microphone-permission': () => false,
}

const ipcRenderer = {
    invoke(channel, ...args) {
        try { return Promise.resolve(handlers[channel] ? handlers[channel](...args) : undefined) } catch (err) { return Promise.reject(err) }
    },
    on(channel, fn) { (listeners[channel] = listeners[channel] || []).push(fn); return ipcRenderer },
    removeListener(channel, fn) { listeners[channel] = (listeners[channel] || []).filter(f => f !== fn); return ipcRenderer },
    send() {},
    sendSync(channel, arg) {
        switch (channel) {
            case 'get-config': return readConfig()
            case 'set-config': {
                const merged = { ...readConfig(), ...arg }
                bridge.setConfig(JSON.stringify(merged))
                setTimeout(() => emit('config-update', merged))
                return merged
            }
            case 'can-auto-update': return false
            default: return undefined
        }
    },
}

module.exports = { ipcRenderer, shell: { openExternal: open } }
