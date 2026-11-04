/* Angelfish TV mode. Includes VacuumTube 2.0.1 (https://github.com/shy1132/VacuumTube), MIT License, Copyright (c) 2025-2026 shy. */
(() => {
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __commonJS = (cb, mod) => function __require() {
    return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
  };

  // tv/trusted-types.js
  var require_trusted_types = __commonJS({
    "tv/trusted-types.js"() {
      if (window.trustedTypes && window.trustedTypes.createPolicy && !window.trustedTypes.defaultPolicy) {
        try {
          window.trustedTypes.createPolicy("default", { createHTML: (s) => s, createScript: (s) => s, createScriptURL: (s) => s });
        } catch (err) {
          console.error("[pufferfish] could not create the default Trusted Types policy", err);
        }
      }
    }
  });

  // tv/gamepad.js
  var require_gamepad = __commonJS({
    "tv/gamepad.js"() {
      var pad = {
        id: "Shoal controller (STANDARD GAMEPAD)",
        index: 0,
        connected: false,
        mapping: "standard",
        timestamp: 0,
        axes: [0, 0, 0, 0],
        buttons: Array.from({ length: 17 }, () => ({ pressed: false, touched: false, value: 0 }))
      };
      var values = new Array(17).fill(0);
      var unread = new Array(17).fill(false);
      function publish() {
        for (let i = 0; i < pad.buttons.length; i++) {
          const v = unread[i] ? Math.max(values[i], 1) : values[i];
          pad.buttons[i] = { pressed: v > 0.5, touched: v > 0, value: v };
        }
      }
      window.__pufferfishPad = (buttons, axes) => {
        pad.connected = true;
        pad.timestamp = performance.now();
        for (let i = 0; i < values.length; i++) {
          const v = buttons[i] || 0;
          if (v > 0.5 && values[i] <= 0.5) unread[i] = true;
          values[i] = v;
        }
        pad.axes = axes.slice(0, 4);
        publish();
      };
      window.__pufferfishPadGone = () => {
        pad.connected = false;
      };
      navigator.getGamepads = () => {
        if (!pad.connected) return [null, null, null, null];
        const snapshot = { ...pad, buttons: pad.buttons.slice(), axes: pad.axes.slice() };
        if (unread.some(Boolean)) {
          unread.fill(false);
          publish();
        }
        return [snapshot, null, null, null];
      };
    }
  });

  // tv/vacuumtube/preload/util/functions.js
  var require_functions = __commonJS({
    "tv/vacuumtube/preload/util/functions.js"(exports, module) {
      function el(tag, attrs = {}, children = []) {
        const element = document.createElement(tag);
        for (const [key, value] of Object.entries(attrs)) {
          if (key === "className") {
            element.className = value;
          } else if (key === "textContent") {
            element.textContent = value;
          } else if (key === "style" && typeof value === "object") {
            Object.assign(element.style, value);
          } else if (key.startsWith("data")) {
            element.setAttribute(key.replace(/([A-Z])/g, "-$1").toLowerCase(), value);
          } else {
            element.setAttribute(key, value);
          }
        }
        for (const child of children) {
          if (child) element.appendChild(child);
        }
        return element;
      }
      async function waitForSelector(selector) {
        return new Promise((resolve) => {
          let observer = new MutationObserver(() => {
            let el3 = document.querySelector(selector);
            if (el3) {
              resolve(el3);
              observer.disconnect();
            }
          });
          observer.observe(document.documentElement, {
            childList: true,
            subtree: true
          });
          let el2 = document.querySelector(selector);
          if (el2) {
            resolve(el2);
            observer.disconnect();
          }
        });
      }
      async function waitForCondition(func) {
        return await new Promise((resolve) => {
          if (func()) return resolve();
          let interval = setInterval(() => {
            if (!func()) return;
            clearInterval(interval);
            resolve();
          }, 10);
        });
      }
      function deepMerge(current, updates) {
        for (let key of Object.keys(updates)) {
          if (!current.hasOwnProperty(key) || typeof updates[key] !== "object") {
            if (updates[key] === "__DELETE__") {
              delete current[key];
            } else {
              current[key] = updates[key];
            }
          } else {
            deepMerge(current[key], updates[key]);
          }
        }
        return current;
      }
      module.exports = {
        el,
        waitForSelector,
        waitForCondition,
        deepMerge
      };
    }
  });

  // tv/vacuumtube/preload/util/xhrModifiers.js
  var require_xhrModifiers = __commonJS({
    "tv/vacuumtube/preload/util/xhrModifiers.js"(exports, module) {
      var functions = require_functions();
      var responseModifiers = [];
      var requestModifiers = [];
      var OriginalXMLHttpRequest = window.XMLHttpRequest;
      var nativeResponseText = Object.getOwnPropertyDescriptor(OriginalXMLHttpRequest.prototype, "responseText").get;
      var nativeResponse = Object.getOwnPropertyDescriptor(OriginalXMLHttpRequest.prototype, "response").get;
      var responseEvents = ["readystatechange", "load", "loadend"];
      var blocked = false;
      function XMLHttpRequest() {
        const xhr = new OriginalXMLHttpRequest();
        const originalOpen = xhr.open;
        const originalSend = xhr.send;
        const originalAddEventListener = xhr.addEventListener;
        const originalRemoveEventListener = xhr.removeEventListener;
        let url = null;
        let modification = null;
        let modifiedText = null;
        let request = 0;
        function modifyResponse() {
          if (!modification) {
            let current = request;
            modification = (async () => {
              if (xhr.responseType !== "" && xhr.responseType !== "text") return;
              if (xhr.status === 0) return;
              let text = nativeResponseText.call(xhr);
              for (let modifier of responseModifiers) {
                try {
                  let modified = await modifier(url, text);
                  if (modified === void 0) continue;
                  text = modified;
                } catch (err) {
                  console.error("an xhr response modifier failed", err);
                }
              }
              if (current === request) modifiedText = text;
            })();
          }
          return modification;
        }
        function createWrapper(listener) {
          return async function(event) {
            if (xhr.readyState === 4) {
              let current = request;
              await modifyResponse();
              if (current !== request) return;
            }
            if (typeof listener === "function") {
              listener.call(xhr, event);
            } else {
              listener.handleEvent(event);
            }
          };
        }
        const wrappers = Object.fromEntries(responseEvents.map((type) => [type, /* @__PURE__ */ new WeakMap()]));
        function wrap(type, listener) {
          if (!wrappers[type].has(listener)) wrappers[type].set(listener, createWrapper(listener));
          return wrappers[type].get(listener);
        }
        xhr.addEventListener = function(type, listener, options) {
          if (responseEvents.includes(type) && listener) {
            return originalAddEventListener.call(this, type, wrap(type, listener), options);
          }
          return originalAddEventListener.apply(this, arguments);
        };
        xhr.removeEventListener = function(type, listener, options) {
          if (responseEvents.includes(type) && listener) {
            return originalRemoveEventListener.call(this, type, wrappers[type].get(listener) ?? listener, options);
          }
          return originalRemoveEventListener.apply(this, arguments);
        };
        for (let type of responseEvents) {
          let handler = null;
          let wrapper = null;
          Object.defineProperty(xhr, `on${type}`, {
            configurable: true,
            get() {
              return handler;
            },
            set(value) {
              if (wrapper) originalRemoveEventListener.call(xhr, type, wrapper);
              handler = typeof value === "function" ? value : null;
              wrapper = handler ? createWrapper(handler) : null;
              if (wrapper) originalAddEventListener.call(xhr, type, wrapper);
            }
          });
        }
        for (let [property, nativeGetter] of [["responseText", nativeResponseText], ["response", nativeResponse]]) {
          Object.defineProperty(xhr, property, {
            configurable: true,
            get() {
              return modifiedText ?? nativeGetter.call(xhr);
            }
          });
        }
        xhr.open = function(method, requestUrl) {
          url = String(requestUrl);
          modification = null;
          modifiedText = null;
          request++;
          return originalOpen.apply(this, arguments);
        };
        xhr.send = function(body) {
          for (let modifier of requestModifiers) {
            try {
              body = modifier(url, body);
            } catch (err) {
              console.error("an xhr request modifier failed", err);
            }
          }
          if (!blocked) return originalSend.call(this, body);
          functions.waitForCondition(() => !blocked).then(() => originalSend.call(xhr, body)).catch((err) => {
            console.error("failed to send xhr request", err);
          });
        };
        return xhr;
      }
      XMLHttpRequest.prototype = OriginalXMLHttpRequest.prototype;
      for (let key of ["UNSENT", "OPENED", "HEADERS_RECEIVED", "LOADING", "DONE"]) {
        XMLHttpRequest[key] = OriginalXMLHttpRequest[key];
      }
      window.XMLHttpRequest = XMLHttpRequest;
      function addResponseModifier(func) {
        responseModifiers.push(func);
      }
      function addRequestModifier(func) {
        requestModifiers.push(func);
      }
      function block() {
        blocked = true;
      }
      function unblock() {
        blocked = false;
      }
      module.exports = {
        addResponseModifier,
        addRequestModifier,
        block,
        unblock
      };
    }
  });

  // tv/vacuumtube/preload/util/configOverrides.js
  var require_configOverrides = __commonJS({
    "tv/vacuumtube/preload/util/configOverrides.js"(exports, module) {
      var functions = require_functions();
      var ytcfgOverrides = [];
      var environmentOverrides = [];
      function overrideEnv(key, value) {
        let params = new URLSearchParams(window.location.search);
        key = String(key);
        value = String(value);
        let existing = params.has(key);
        if (existing) {
          if (value === existing) return;
          params.delete(key);
        }
        params.set(key, value);
        let newUrl = window.location.pathname + "?" + params.toString();
        history.replaceState(null, "", newUrl);
      }
      function applyOverrides(target, overrides) {
        for (let override of overrides) {
          try {
            functions.deepMerge(target, override);
          } catch (err) {
            console.error("a config override failed", err);
          }
        }
      }
      var environment;
      Object.defineProperty(window, "environment", {
        configurable: true,
        get() {
          return environment;
        },
        set(value) {
          applyOverrides(value, environmentOverrides);
          environment = value;
        }
      });
      var ytcfg;
      Object.defineProperty(window, "ytcfg", {
        configurable: true,
        get() {
          return ytcfg;
        },
        set(value) {
          ytcfg = value;
          let set = value?.set;
          if (typeof set !== "function") return;
          value.set = function(...args) {
            let result = set.apply(this, args);
            if (value.data_) applyOverrides(value.data_, ytcfgOverrides);
            return result;
          };
        }
      });
      module.exports = {
        overrideEnv,
        ytcfgOverrides,
        environmentOverrides
      };
    }
  });

  // tv/shims/css.js
  var require_css = __commonJS({
    "tv/shims/css.js"(exports, module) {
      var sheets = /* @__PURE__ */ new Map();
      function apply() {
        if ("adoptedStyleSheets" in document && typeof CSSStyleSheet.prototype.replaceSync === "function") {
          const ours = [...sheets.values()].map((x) => x.sheet).filter(Boolean);
          document.adoptedStyleSheets = [...document.adoptedStyleSheets.filter((x) => !x.__shoal), ...ours];
        }
      }
      async function inject(id, text) {
        remove(id);
        if ("adoptedStyleSheets" in document && typeof CSSStyleSheet.prototype.replaceSync === "function") {
          const sheet = new CSSStyleSheet();
          sheet.__shoal = true;
          sheet.replaceSync(text);
          sheets.set(id, { sheet });
          apply();
        } else {
          if (!document.head) await new Promise((resolve) => document.addEventListener("DOMContentLoaded", resolve, { once: true }));
          const node = document.createElement("style");
          node.id = `vt-${id}`;
          const nonce = document.querySelector("style[nonce],script[nonce]")?.nonce;
          if (nonce) node.nonce = nonce;
          node.textContent = text;
          document.head.append(node);
          sheets.set(id, { node });
        }
      }
      function remove(id) {
        const old = sheets.get(id);
        old?.node?.remove();
        sheets.delete(id);
        apply();
      }
      module.exports = { inject, delete: remove };
    }
  });

  // tv/touch.js
  var require_touch = __commonJS({
    "tv/touch.js"(exports, module) {
      var overrides = require_configOverrides();
      var css = require_css();
      function scrollTarget(node) {
        for (let el = node; el && el !== document.body; el = el.parentElement) {
          const style = getComputedStyle(el);
          if (el.scrollHeight > el.clientHeight + 2 && /auto|scroll/.test(style.overflowY) || el.scrollWidth > el.clientWidth + 2 && /auto|scroll/.test(style.overflowX)) return el;
        }
        return null;
      }
      function press(code) {
        for (const type of ["keydown", "keyup"]) {
          const event = new Event(type, { bubbles: true });
          Object.defineProperty(event, "keyCode", { value: code });
          Object.defineProperty(event, "key", { value: { 32780: "ArrowUp", 32781: "ArrowDown", 32782: "ArrowLeft", 32783: "ArrowRight" }[code] });
          document.dispatchEvent(event);
        }
      }
      module.exports = () => {
        overrides.environmentOverrides.push({ flags: { enable_touch_support: true } });
        css.inject("android-touch", "yt-virtual-list,.ogHbMc{touch-action:none!important} #vt-settings-overlay-root.vt-settings-hidden{display:none!important}");
        let gesture = null, suppressClickUntil = 0;
        window.addEventListener("pointerdown", (e) => {
          if (e.pointerType !== "touch" || !e.isPrimary) return;
          if (e.target.closest("#vt-settings-overlay-root")) return;
          document.documentElement.classList.add("vt-touch-input");
          gesture = { id: e.pointerId, target: e.target, scroll: scrollTarget(e.target), x: e.clientX, y: e.clientY, startX: e.clientX, startY: e.clientY, moved: false, remainder: 0 };
          e.preventDefault();
          e.stopImmediatePropagation();
        }, { capture: true, passive: false });
        window.addEventListener("pointermove", (e) => {
          if (!gesture || e.pointerId !== gesture.id) return;
          const g = gesture, dx = e.clientX - g.x, dy = e.clientY - g.y;
          g.x = e.clientX;
          g.y = e.clientY;
          if (Math.hypot(e.clientX - g.startX, e.clientY - g.startY) > 8) g.moved = true;
          if (g.moved) {
            if (g.scroll) g.scroll.scrollBy(-dx, -dy);
            else {
              const vertical = Math.abs(dy) >= Math.abs(dx);
              g.remainder += vertical ? dy : dx;
              while (Math.abs(g.remainder) >= 48) {
                press(vertical ? g.remainder < 0 ? 32781 : 32780 : g.remainder < 0 ? 32783 : 32782);
                g.remainder += g.remainder < 0 ? 48 : -48;
              }
            }
          }
          e.preventDefault();
          e.stopImmediatePropagation();
        }, { capture: true, passive: false });
        const finish = (e) => {
          if (!gesture || e.pointerId !== gesture.id) return;
          const g = gesture;
          gesture = null;
          suppressClickUntil = Date.now() + 400;
          e.preventDefault();
          e.stopImmediatePropagation();
          if (e.type === "pointercancel" || g.moved) return;
          const hit = document.elementFromPoint(e.clientX, e.clientY);
          if (!hit || !(g.target === hit || g.target.contains(hit) || hit.contains(g.target))) return;
          const node = hit.closest('.vt-focusable,a,button,[role="button"],[tabindex],.yt-focusable') || hit;
          node.focus?.({ preventScroll: true });
          for (const type of ["mouseover", "mousemove", "mousedown", "mouseup", "click"]) node.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true, view: window, clientX: e.clientX, clientY: e.clientY, button: 0 }));
        };
        window.addEventListener("pointerup", finish, { capture: true, passive: false });
        window.addEventListener("pointercancel", finish, { capture: true, passive: false });
        window.addEventListener("click", (e) => {
          if (e.isTrusted && Date.now() < suppressClickUntil) {
            e.preventDefault();
            e.stopImmediatePropagation();
          }
        }, true);
        window.addEventListener("blur", () => {
          gesture = null;
        });
      };
    }
  });

  // tv/vacuumtube/preload/util/jsonModifiers.js
  var require_jsonModifiers = __commonJS({
    "tv/vacuumtube/preload/util/jsonModifiers.js"(exports, module) {
      var modifiers = [];
      var jsonParse = JSON.parse;
      JSON.parse = function(...args) {
        let json = jsonParse(...args);
        if (typeof json !== "object" || json === null) return json;
        try {
          for (let modifier of modifiers) {
            json = modifier(json);
          }
        } catch (err) {
          console.error("a json modifier failed", err);
        }
        return json;
      };
      function addModifier(func) {
        modifiers.push(func);
      }
      module.exports = {
        addModifier
      };
    }
  });

  // vt:vt:config-defaults
  var require_vt_config_defaults = __commonJS({
    "vt:vt:config-defaults"(exports, module) {
      module.exports = { "volume": 100, "adblock": true, "sponsorblock": false, "sponsorblock_uuid": crypto.randomUUID(), "sponsorblock_skip_sponsor": true, "sponsorblock_skip_selfpromo": false, "sponsorblock_skip_interaction": false, "sponsorblock_skip_intro": false, "sponsorblock_skip_outro": false, "sponsorblock_skip_preview": false, "sponsorblock_skip_hook": false, "sponsorblock_skip_filler": false, "dearrow": false, "dislikes": false, "remove_super_resolution": false, "hide_shorts": false, "block_continue_watching": false, "h264ify": false, "h264ify_disable_webm": true, "h264ify_disable_vp8": true, "h264ify_disable_vp9": true, "h264ify_disable_av1": true, "unlock_resolution": false, "hardware_decoding": true, "wayland_hdr": false, "low_memory_mode": false, "fullscreen": false, "features_enabled": false, "music_mode_feature": false, "music_mode": false, "ultrawide_feature": false, "remaining_time_feature": false, "no_window_decorations": false, "keep_on_top": false, "pause_on_blur": false, "userstyles": false, "disabled_userstyles": [], "touch_overlay": false, "controller_support": true, "device_discoverability": false, "auto_update": false };
    }
  });

  // tv/shims/electron.js
  var require_electron = __commonJS({
    "tv/shims/electron.js"(exports, module) {
      var defaults = require_vt_config_defaults();
      var bridge = window.PufferfishTV;
      var listeners = {};
      function emit(channel, ...args) {
        for (const fn of listeners[channel] || []) {
          try {
            fn({}, ...args);
          } catch (err) {
            console.error("[pufferfish] listener failed", channel, err);
          }
        }
      }
      window.__pufferfishEmit = emit;
      function readConfig() {
        let stored = {};
        try {
          stored = JSON.parse(bridge.getConfig() || "{}");
        } catch {
        }
        let app = {};
        try {
          app = JSON.parse(bridge.getAppDefaults() || "{}");
        } catch {
        }
        return { ...defaults, ...app, ...stored, touch_overlay: false };
      }
      function open(url) {
        bridge.openUrl(url);
      }
      var about = () => ({
        status: "idle",
        currentVersion: "2.0.1",
        latestVersion: "",
        platform: "android",
        arch: "arm64",
        installationType: "swordfish",
        canAutoUpdate: false,
        progress: null
      });
      var handlers = {
        "reload": () => location.reload(),
        "set-fullscreen": () => {
        },
        "set-on-top": () => {
        },
        "is-steam": () => false,
        "get-userstyles": () => [],
        "get-deeplink": () => bridge.getDeepLink() || null,
        "get-about-info": about,
        "check-for-updates": about,
        "install-update": () => {
        },
        "open-update-download": () => open("https://github.com/ShoalApps/angelfish/releases/latest"),
        "open-project-page": () => open("https://github.com/shy1132/VacuumTube"),
        "open-release-page": () => open("https://github.com/ShoalApps/angelfish/releases/latest"),
        "open-userstyles-folder": () => {
        },
        "request-microphone-permission": () => false
      };
      var ipcRenderer = {
        invoke(channel, ...args) {
          try {
            return Promise.resolve(handlers[channel] ? handlers[channel](...args) : void 0);
          } catch (err) {
            return Promise.reject(err);
          }
        },
        on(channel, fn) {
          (listeners[channel] = listeners[channel] || []).push(fn);
          return ipcRenderer;
        },
        removeListener(channel, fn) {
          listeners[channel] = (listeners[channel] || []).filter((f) => f !== fn);
          return ipcRenderer;
        },
        send() {
        },
        sendSync(channel, arg) {
          switch (channel) {
            case "get-config":
              return readConfig();
            case "set-config": {
              const merged = { ...readConfig(), ...arg };
              bridge.setConfig(JSON.stringify(merged));
              setTimeout(() => emit("config-update", merged));
              return merged;
            }
            case "can-auto-update":
              return false;
            default:
              return void 0;
          }
        }
      };
      module.exports = { ipcRenderer, shell: { openExternal: open } };
    }
  });

  // tv/vacuumtube/preload/config.js
  var require_config = __commonJS({
    "tv/vacuumtube/preload/config.js"(exports, module) {
      var { ipcRenderer } = require_electron();
      var config = ipcRenderer.sendSync("get-config");
      var sharedConfig = { ...config };
      ipcRenderer.on("config-update", (event, newConfig) => {
        for (let key in sharedConfig) delete sharedConfig[key];
        for (let key in newConfig) sharedConfig[key] = newConfig[key];
      });
      function get() {
        return sharedConfig;
      }
      function set(newConfig) {
        let updated = ipcRenderer.sendSync("set-config", newConfig);
        for (let key in sharedConfig) delete sharedConfig[key];
        for (let key in updated) sharedConfig[key] = updated[key];
      }
      module.exports = {
        get,
        set
      };
    }
  });

  // tv/vacuumtube/preload/modules/adblock.js
  var require_adblock = __commonJS({
    "tv/vacuumtube/preload/modules/adblock.js"(exports, module) {
      var jsonMod = require_jsonModifiers();
      var xhrModifiers = require_xhrModifiers();
      var configManager = require_config();
      var config = configManager.get();
      module.exports = () => {
        xhrModifiers.addResponseModifier((url, text) => {
          if (!config.adblock) return;
          if (!url.startsWith("/youtubei/v1/browse") && !url.startsWith("/youtubei/v1/search")) {
            return;
          }
          let json = JSON.parse(text);
          if (url.startsWith("/youtubei/v1/browse")) {
            let homeFeed = json.contents?.tvBrowseRenderer?.content?.tvSurfaceContentRenderer?.content?.sectionListRenderer;
            if (!homeFeed || !homeFeed.contents) return;
            homeFeed.contents = homeFeed.contents.filter(
              (r) => !r.adSlotRenderer && !r.promoShelfRenderer && !r.shelfRenderer?.tvhtml5Metadata?.hideLogo
              /*only appears on premium upsells*/
            );
            for (let feed of homeFeed.contents) {
              let horizontal = feed?.shelfRenderer?.content?.horizontalListRenderer;
              if (!horizontal?.items) continue;
              horizontal.items = horizontal.items.filter((i) => !i.adSlotRenderer);
            }
          } else if (url.startsWith("/youtubei/v1/search")) {
            let searchFeed = json.contents?.sectionListRenderer;
            if (!searchFeed || !searchFeed.contents) return;
            for (let feed of searchFeed.contents) {
              let horizontal = feed?.shelfRenderer?.content?.horizontalListRenderer;
              if (!horizontal?.items) continue;
              horizontal.items = horizontal.items.filter((i) => !i.adSlotRenderer);
            }
          }
          return JSON.stringify(json);
        });
        jsonMod.addModifier((json) => {
          if (!config.adblock) return json;
          if (json?.adPlacements) {
            json.adPlacements = [];
          }
          if (json?.adSlots) {
            json.adSlots = [];
          }
          return json;
        });
        jsonMod.addModifier((json) => {
          if (!config.adblock) return json;
          if (json?.entries && Array.isArray(json.entries)) {
            json.entries = json.entries.filter((e) => !e?.command?.reelWatchEndpoint?.adClientParams?.isAd);
          }
          return json;
        });
      };
    }
  });

  // tv/vacuumtube/preload/modules/block-continue-watching.js
  var require_block_continue_watching = __commonJS({
    "tv/vacuumtube/preload/modules/block-continue-watching.js"(exports, module) {
      var jsonMod = require_jsonModifiers();
      var configManager = require_config();
      var config = configManager.get();
      module.exports = () => {
        jsonMod.addModifier((json) => {
          if (!config.block_continue_watching) return json;
          if ("playabilityStatus" in json && "messages" in json) {
            json.messages = json.messages.filter((m) => !m?.youThereRenderer);
            if (json.messages.length === 0) delete json.messages;
            return json;
          } else {
            return json;
          }
        });
      };
    }
  });

  // tv/vacuumtube/preload/util/resolveCommandModifiers.js
  var require_resolveCommandModifiers = __commonJS({
    "tv/vacuumtube/preload/util/resolveCommandModifiers.js"(exports, module) {
      var inputModifiers = [];
      var outputModifiers = [];
      var globalResolveCommand;
      var interval = setInterval(() => {
        for (let key in window._yttv) {
          if (window._yttv[key]?.instance?.resolveCommand) {
            let resolveCommand = window._yttv[key].instance.resolveCommand;
            globalResolveCommand = (command) => {
              return window._yttv[key].instance.resolveCommand(command);
            };
            window._yttv[key].instance.resolveCommand = function(command) {
              for (let modifier of inputModifiers) {
                command = modifier(command);
                if (command === false) return true;
              }
              let output = resolveCommand.apply(this, [command]);
              for (let modifier of outputModifiers) {
                output = modifier(output);
              }
              return output;
            };
            clearInterval(interval);
            return;
          }
        }
      }, 100);
      function addInputModifier(func) {
        inputModifiers.push(func);
      }
      function addOutputModifier(func) {
        outputModifiers.push(func);
      }
      module.exports = {
        resolveCommand: (command) => {
          if (globalResolveCommand) {
            return globalResolveCommand(command);
          } else {
            throw new Error("resolveCommand doesn't exist yet, probably called too early");
          }
        },
        addInputModifier,
        addOutputModifier
      };
    }
  });

  // tv/vacuumtube/preload/modules/block-sign-in-popup.js
  var require_block_sign_in_popup = __commonJS({
    "tv/vacuumtube/preload/modules/block-sign-in-popup.js"(exports, module) {
      var rcMod = require_resolveCommandModifiers();
      module.exports = () => {
        rcMod.addInputModifier((c) => {
          if (c.openPopupAction?.uniqueId === "playback-cap") return false;
          return c;
        });
      };
    }
  });

  // tv/shims/os.js
  var require_os = __commonJS({
    "tv/shims/os.js"(exports, module) {
      module.exports = { hostname: () => "Shoal", platform: () => "android", release: () => "", arch: () => "arm64", networkInterfaces: () => ({}), type: () => "Linux" };
    }
  });

  // tv/vacuumtube/preload/modules/cast-label.js
  var require_cast_label = __commonJS({
    "tv/vacuumtube/preload/modules/cast-label.js"(exports, module) {
      var os = require_os();
      var configOverrides = require_configOverrides();
      var hostname = os.hostname();
      if (false) {
        hostname = hostname.slice(0, -6);
      }
      module.exports = () => {
        configOverrides.environmentOverrides.push({
          feature_switches: {
            mdx_device_label: `${hostname} (VacuumTube)`
            //label that displays on cast. only mentions VacuumTube incase the user doesn't know their hostname
          }
        });
      };
    }
  });

  // tv/node_modules/events/events.js
  var require_events = __commonJS({
    "tv/node_modules/events/events.js"(exports, module) {
      "use strict";
      var R = typeof Reflect === "object" ? Reflect : null;
      var ReflectApply = R && typeof R.apply === "function" ? R.apply : function ReflectApply2(target, receiver, args) {
        return Function.prototype.apply.call(target, receiver, args);
      };
      var ReflectOwnKeys;
      if (R && typeof R.ownKeys === "function") {
        ReflectOwnKeys = R.ownKeys;
      } else if (Object.getOwnPropertySymbols) {
        ReflectOwnKeys = function ReflectOwnKeys2(target) {
          return Object.getOwnPropertyNames(target).concat(Object.getOwnPropertySymbols(target));
        };
      } else {
        ReflectOwnKeys = function ReflectOwnKeys2(target) {
          return Object.getOwnPropertyNames(target);
        };
      }
      function ProcessEmitWarning(warning) {
        if (console && console.warn) console.warn(warning);
      }
      var NumberIsNaN = Number.isNaN || function NumberIsNaN2(value) {
        return value !== value;
      };
      function EventEmitter() {
        EventEmitter.init.call(this);
      }
      module.exports = EventEmitter;
      module.exports.once = once;
      EventEmitter.EventEmitter = EventEmitter;
      EventEmitter.prototype._events = void 0;
      EventEmitter.prototype._eventsCount = 0;
      EventEmitter.prototype._maxListeners = void 0;
      var defaultMaxListeners = 10;
      function checkListener(listener) {
        if (typeof listener !== "function") {
          throw new TypeError('The "listener" argument must be of type Function. Received type ' + typeof listener);
        }
      }
      Object.defineProperty(EventEmitter, "defaultMaxListeners", {
        enumerable: true,
        get: function() {
          return defaultMaxListeners;
        },
        set: function(arg) {
          if (typeof arg !== "number" || arg < 0 || NumberIsNaN(arg)) {
            throw new RangeError('The value of "defaultMaxListeners" is out of range. It must be a non-negative number. Received ' + arg + ".");
          }
          defaultMaxListeners = arg;
        }
      });
      EventEmitter.init = function() {
        if (this._events === void 0 || this._events === Object.getPrototypeOf(this)._events) {
          this._events = /* @__PURE__ */ Object.create(null);
          this._eventsCount = 0;
        }
        this._maxListeners = this._maxListeners || void 0;
      };
      EventEmitter.prototype.setMaxListeners = function setMaxListeners(n) {
        if (typeof n !== "number" || n < 0 || NumberIsNaN(n)) {
          throw new RangeError('The value of "n" is out of range. It must be a non-negative number. Received ' + n + ".");
        }
        this._maxListeners = n;
        return this;
      };
      function _getMaxListeners(that) {
        if (that._maxListeners === void 0)
          return EventEmitter.defaultMaxListeners;
        return that._maxListeners;
      }
      EventEmitter.prototype.getMaxListeners = function getMaxListeners() {
        return _getMaxListeners(this);
      };
      EventEmitter.prototype.emit = function emit(type) {
        var args = [];
        for (var i = 1; i < arguments.length; i++) args.push(arguments[i]);
        var doError = type === "error";
        var events = this._events;
        if (events !== void 0)
          doError = doError && events.error === void 0;
        else if (!doError)
          return false;
        if (doError) {
          var er;
          if (args.length > 0)
            er = args[0];
          if (er instanceof Error) {
            throw er;
          }
          var err = new Error("Unhandled error." + (er ? " (" + er.message + ")" : ""));
          err.context = er;
          throw err;
        }
        var handler = events[type];
        if (handler === void 0)
          return false;
        if (typeof handler === "function") {
          ReflectApply(handler, this, args);
        } else {
          var len = handler.length;
          var listeners = arrayClone(handler, len);
          for (var i = 0; i < len; ++i)
            ReflectApply(listeners[i], this, args);
        }
        return true;
      };
      function _addListener(target, type, listener, prepend) {
        var m;
        var events;
        var existing;
        checkListener(listener);
        events = target._events;
        if (events === void 0) {
          events = target._events = /* @__PURE__ */ Object.create(null);
          target._eventsCount = 0;
        } else {
          if (events.newListener !== void 0) {
            target.emit(
              "newListener",
              type,
              listener.listener ? listener.listener : listener
            );
            events = target._events;
          }
          existing = events[type];
        }
        if (existing === void 0) {
          existing = events[type] = listener;
          ++target._eventsCount;
        } else {
          if (typeof existing === "function") {
            existing = events[type] = prepend ? [listener, existing] : [existing, listener];
          } else if (prepend) {
            existing.unshift(listener);
          } else {
            existing.push(listener);
          }
          m = _getMaxListeners(target);
          if (m > 0 && existing.length > m && !existing.warned) {
            existing.warned = true;
            var w = new Error("Possible EventEmitter memory leak detected. " + existing.length + " " + String(type) + " listeners added. Use emitter.setMaxListeners() to increase limit");
            w.name = "MaxListenersExceededWarning";
            w.emitter = target;
            w.type = type;
            w.count = existing.length;
            ProcessEmitWarning(w);
          }
        }
        return target;
      }
      EventEmitter.prototype.addListener = function addListener(type, listener) {
        return _addListener(this, type, listener, false);
      };
      EventEmitter.prototype.on = EventEmitter.prototype.addListener;
      EventEmitter.prototype.prependListener = function prependListener(type, listener) {
        return _addListener(this, type, listener, true);
      };
      function onceWrapper() {
        if (!this.fired) {
          this.target.removeListener(this.type, this.wrapFn);
          this.fired = true;
          if (arguments.length === 0)
            return this.listener.call(this.target);
          return this.listener.apply(this.target, arguments);
        }
      }
      function _onceWrap(target, type, listener) {
        var state = { fired: false, wrapFn: void 0, target, type, listener };
        var wrapped = onceWrapper.bind(state);
        wrapped.listener = listener;
        state.wrapFn = wrapped;
        return wrapped;
      }
      EventEmitter.prototype.once = function once2(type, listener) {
        checkListener(listener);
        this.on(type, _onceWrap(this, type, listener));
        return this;
      };
      EventEmitter.prototype.prependOnceListener = function prependOnceListener(type, listener) {
        checkListener(listener);
        this.prependListener(type, _onceWrap(this, type, listener));
        return this;
      };
      EventEmitter.prototype.removeListener = function removeListener(type, listener) {
        var list, events, position, i, originalListener;
        checkListener(listener);
        events = this._events;
        if (events === void 0)
          return this;
        list = events[type];
        if (list === void 0)
          return this;
        if (list === listener || list.listener === listener) {
          if (--this._eventsCount === 0)
            this._events = /* @__PURE__ */ Object.create(null);
          else {
            delete events[type];
            if (events.removeListener)
              this.emit("removeListener", type, list.listener || listener);
          }
        } else if (typeof list !== "function") {
          position = -1;
          for (i = list.length - 1; i >= 0; i--) {
            if (list[i] === listener || list[i].listener === listener) {
              originalListener = list[i].listener;
              position = i;
              break;
            }
          }
          if (position < 0)
            return this;
          if (position === 0)
            list.shift();
          else {
            spliceOne(list, position);
          }
          if (list.length === 1)
            events[type] = list[0];
          if (events.removeListener !== void 0)
            this.emit("removeListener", type, originalListener || listener);
        }
        return this;
      };
      EventEmitter.prototype.off = EventEmitter.prototype.removeListener;
      EventEmitter.prototype.removeAllListeners = function removeAllListeners(type) {
        var listeners, events, i;
        events = this._events;
        if (events === void 0)
          return this;
        if (events.removeListener === void 0) {
          if (arguments.length === 0) {
            this._events = /* @__PURE__ */ Object.create(null);
            this._eventsCount = 0;
          } else if (events[type] !== void 0) {
            if (--this._eventsCount === 0)
              this._events = /* @__PURE__ */ Object.create(null);
            else
              delete events[type];
          }
          return this;
        }
        if (arguments.length === 0) {
          var keys = Object.keys(events);
          var key;
          for (i = 0; i < keys.length; ++i) {
            key = keys[i];
            if (key === "removeListener") continue;
            this.removeAllListeners(key);
          }
          this.removeAllListeners("removeListener");
          this._events = /* @__PURE__ */ Object.create(null);
          this._eventsCount = 0;
          return this;
        }
        listeners = events[type];
        if (typeof listeners === "function") {
          this.removeListener(type, listeners);
        } else if (listeners !== void 0) {
          for (i = listeners.length - 1; i >= 0; i--) {
            this.removeListener(type, listeners[i]);
          }
        }
        return this;
      };
      function _listeners(target, type, unwrap) {
        var events = target._events;
        if (events === void 0)
          return [];
        var evlistener = events[type];
        if (evlistener === void 0)
          return [];
        if (typeof evlistener === "function")
          return unwrap ? [evlistener.listener || evlistener] : [evlistener];
        return unwrap ? unwrapListeners(evlistener) : arrayClone(evlistener, evlistener.length);
      }
      EventEmitter.prototype.listeners = function listeners(type) {
        return _listeners(this, type, true);
      };
      EventEmitter.prototype.rawListeners = function rawListeners(type) {
        return _listeners(this, type, false);
      };
      EventEmitter.listenerCount = function(emitter, type) {
        if (typeof emitter.listenerCount === "function") {
          return emitter.listenerCount(type);
        } else {
          return listenerCount.call(emitter, type);
        }
      };
      EventEmitter.prototype.listenerCount = listenerCount;
      function listenerCount(type) {
        var events = this._events;
        if (events !== void 0) {
          var evlistener = events[type];
          if (typeof evlistener === "function") {
            return 1;
          } else if (evlistener !== void 0) {
            return evlistener.length;
          }
        }
        return 0;
      }
      EventEmitter.prototype.eventNames = function eventNames() {
        return this._eventsCount > 0 ? ReflectOwnKeys(this._events) : [];
      };
      function arrayClone(arr, n) {
        var copy = new Array(n);
        for (var i = 0; i < n; ++i)
          copy[i] = arr[i];
        return copy;
      }
      function spliceOne(list, index) {
        for (; index + 1 < list.length; index++)
          list[index] = list[index + 1];
        list.pop();
      }
      function unwrapListeners(arr) {
        var ret = new Array(arr.length);
        for (var i = 0; i < ret.length; ++i) {
          ret[i] = arr[i].listener || arr[i];
        }
        return ret;
      }
      function once(emitter, name) {
        return new Promise(function(resolve, reject) {
          function errorListener(err) {
            emitter.removeListener(name, resolver);
            reject(err);
          }
          function resolver() {
            if (typeof emitter.removeListener === "function") {
              emitter.removeListener("error", errorListener);
            }
            resolve([].slice.call(arguments));
          }
          ;
          eventTargetAgnosticAddListener(emitter, name, resolver, { once: true });
          if (name !== "error") {
            addErrorHandlerIfEventEmitter(emitter, errorListener, { once: true });
          }
        });
      }
      function addErrorHandlerIfEventEmitter(emitter, handler, flags) {
        if (typeof emitter.on === "function") {
          eventTargetAgnosticAddListener(emitter, "error", handler, flags);
        }
      }
      function eventTargetAgnosticAddListener(emitter, name, listener, flags) {
        if (typeof emitter.on === "function") {
          if (flags.once) {
            emitter.once(name, listener);
          } else {
            emitter.on(name, listener);
          }
        } else if (typeof emitter.addEventListener === "function") {
          emitter.addEventListener(name, function wrapListener(arg) {
            if (flags.once) {
              emitter.removeEventListener(name, wrapListener);
            }
            listener(arg);
          });
        } else {
          throw new TypeError('The "emitter" argument must be of type EventEmitter. Received type ' + typeof emitter);
        }
      }
    }
  });

  // tv/vacuumtube/preload/util/controller.js
  var require_controller = __commonJS({
    "tv/vacuumtube/preload/util/controller.js"(exports, module) {
      var { ipcRenderer } = require_electron();
      var { EventEmitter } = require_events();
      var emitter = new EventEmitter();
      var buttonOffset = 32768;
      var axisThreshold = 0.5;
      var buttonRepeatInterval = 100;
      var buttonRepeatDelay = 500;
      var axisButtons = [
        [19, 20],
        [17, 18],
        [23, 24],
        [21, 22]
      ];
      var pressedButtons = {};
      var buttonRepeatTimeout;
      var focused = true;
      ipcRenderer.on("focus", () => {
        focused = true;
      });
      ipcRenderer.on("blur", () => {
        focused = false;
      });
      requestAnimationFrame(pollGamepads);
      function pollGamepads() {
        const gamepads = navigator.getGamepads();
        for (let index in pressedButtons) {
          if (!gamepads[index] && pressedButtons[index]) {
            releaseAll(pressedButtons[index]);
            pressedButtons[index] = null;
          }
        }
        const steamInput = gamepads.find((g) => g && g.id.endsWith("(STANDARD GAMEPAD Vendor: 28de Product: 11ff)"));
        if (steamInput) {
          handleGamepad(steamInput);
        } else {
          for (let gamepad of gamepads) {
            if (gamepad && gamepad.connected) handleGamepad(gamepad);
          }
        }
        requestAnimationFrame(pollGamepads);
      }
      function handleGamepad(gamepad) {
        const index = gamepad.index;
        if (!pressedButtons[index]) {
          pressedButtons[index] = { buttons: {}, axes: {} };
        }
        const state = pressedButtons[index];
        for (let i = 0; i < gamepad.buttons.length; i++) {
          let code = buttonOffset + i;
          let isPressed = gamepad.buttons[i].pressed;
          if (isPressed && !state.buttons[code]) {
            state.buttons[code] = true;
            press(code);
          } else if (!isPressed && state.buttons[code]) {
            state.buttons[code] = false;
            release(code);
          }
        }
        for (let i = 0; i < axisButtons.length && i < gamepad.axes.length; i++) {
          let value = gamepad.axes[i];
          let code = null;
          if (value < -axisThreshold) {
            code = buttonOffset + axisButtons[i][0];
          } else if (value > axisThreshold) {
            code = buttonOffset + axisButtons[i][1];
          }
          let heldCode = state.axes[i];
          if (code === heldCode) continue;
          if (heldCode) {
            release(heldCode);
          }
          state.axes[i] = code;
          if (code) {
            press(code);
          }
        }
      }
      function press(code) {
        buttonDown(code);
        stopButtonRepeat();
        buttonRepeatTimeout = setTimeout(() => startButtonRepeat(code), buttonRepeatDelay);
      }
      function release(code) {
        buttonUp(code);
        stopButtonRepeat();
      }
      function releaseAll(state) {
        for (let code in state.buttons) {
          if (state.buttons[code]) {
            release(Number(code));
          }
        }
        for (let code of Object.values(state.axes)) {
          if (code) {
            release(code);
          }
        }
      }
      function buttonDown(code) {
        if (!focused) return;
        emitter.emit("down", { code });
      }
      function buttonUp(code) {
        if (!focused) return;
        emitter.emit("up", { code });
      }
      function startButtonRepeat(code) {
        stopButtonRepeat();
        buttonRepeatTimeout = setInterval(() => buttonDown(code), buttonRepeatInterval);
      }
      function stopButtonRepeat() {
        clearTimeout(buttonRepeatTimeout);
        clearInterval(buttonRepeatTimeout);
      }
      module.exports = emitter;
    }
  });

  // tv/vacuumtube/preload/util/ui.js
  var require_ui = __commonJS({
    "tv/vacuumtube/preload/util/ui.js"(exports, module) {
      var rcMod = require_resolveCommandModifiers();
      function toast(title, subtitle) {
        let toastCommand = {
          openPopupAction: {
            popupType: "TOAST",
            popup: {
              overlayToastRenderer: {
                title: {
                  simpleText: title
                },
                subtitle: {
                  simpleText: subtitle
                }
              }
            }
          }
        };
        rcMod.resolveCommand(toastCommand);
      }
      function popupMenu(options) {
        return {
          openPopupAction: {
            popup: {
              overlaySectionRenderer: {
                dismissalCommand: {
                  signalAction: {
                    signal: "POPUP_BACK"
                  }
                },
                overlay: {
                  overlayTwoPanelRenderer: {
                    actionPanel: {
                      overlayPanelRenderer: {
                        header: {
                          overlayPanelHeaderRenderer: {
                            title: {
                              simpleText: options.title
                            }
                          }
                        },
                        content: {
                          overlayPanelItemListRenderer: {
                            selectedIndex: options.selectedIndex || 0,
                            items: options.items
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        };
      }
      function link(options) {
        return {
          compactLinkRenderer: {
            title: {
              simpleText: options.title
            },
            secondaryIcon: options.icon ? { iconType: options.icon } : void 0,
            serviceEndpoint: {
              commandExecutorCommand: {
                get commands() {
                  return [
                    options.closeMenu ? {
                      signalAction: {
                        signal: "POPUP_BACK"
                      }
                    } : void 0,
                    options.callback ? {
                      signalAction: {
                        get signal() {
                          options.callback();
                          return "UNKNOWN";
                        }
                      }
                    } : void 0,
                    options.createSubMenu ? options.createSubMenu() : void 0
                  ].filter(Boolean);
                }
              }
            }
          }
        };
      }
      module.exports = {
        toast,
        popupMenu,
        link
      };
    }
  });

  // vt:vt:files
  var require_vt_files = __commonJS({
    "vt:vt:files"(exports, module) {
      module.exports = { "/vt/src/preload/modules/settings/style.css": { "text": "/*\nsettings overlay\nstyles the frame and the generic row types from render.js only\n*/\n\n#vt-settings-overlay-root {\n    --vt-bg: #212121;\n    --vt-card: #2b2b2b;\n    --vt-card-hover: #333;\n    --vt-card-focus: #3a3a3a;\n    --vt-line: #383838;\n    --vt-text: #fff;\n    --vt-muted: #aaa;\n    --vt-faint: #808080;\n    --vt-focus: #fff;\n    --vt-warning-bg: #332b18;\n    --vt-warning-line: #6f5a25;\n    --vt-radius: 12px;\n\n    position: fixed;\n    inset: 0;\n    z-index: 99999;\n    display: flex;\n    align-items: center;\n    justify-content: center;\n    transition: opacity 0.2s ease;\n\n    /*own every inheritable base value instead of inheriting youtube's styling on <body>*/\n    font-family: 'YouTube Sans', Roboto, Arial, sans-serif;\n    font-size: 15px;\n    font-weight: 400;\n    font-style: normal;\n    line-height: normal;\n    letter-spacing: normal;\n    text-align: left;\n    text-transform: none;\n    white-space: normal;\n    color: var(--vt-text);\n    cursor: default;\n    user-select: none;\n}\n\n#vt-settings-overlay-root,\n#vt-settings-overlay-root * {\n    box-sizing: border-box;\n}\n\n#vt-settings-overlay-root.vt-settings-hidden {\n    display: none !important;\n    opacity: 0;\n    pointer-events: none;\n}\n\n.vt-settings-backdrop {\n    position: absolute;\n    inset: 0;\n    background: rgba(0, 0, 0, 0.85);\n}\n\n.vt-settings-container {\n    position: relative;\n    width: min(1100px, 90vw);\n    height: min(760px, 86vh);\n    background: var(--vt-bg);\n    border-radius: 18px;\n    display: flex;\n    flex-direction: column;\n    overflow: hidden;\n    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);\n}\n\n/*header*/\n\n.vt-settings-header {\n    display: flex;\n    align-items: center;\n    gap: 24px;\n    padding: 24px 32px 18px;\n}\n\n.vt-settings-title {\n    font-size: 28px;\n    font-weight: 700;\n}\n\n.vt-settings-hint {\n    margin-left: auto;\n    font-size: 14px;\n    color: var(--vt-muted);\n}\n\n.vt-settings-icon-button {\n    width: 40px;\n    height: 40px;\n    display: flex;\n    align-items: center;\n    justify-content: center;\n    flex-shrink: 0;\n    border-radius: 50%;\n    font-size: 20px;\n    color: var(--vt-muted);\n    cursor: pointer;\n}\n\n.vt-settings-icon-button + .vt-settings-icon-button {\n    margin-left: -16px;\n}\n\n.vt-settings-icon-button:hover,\n.vt-settings-icon-button.vt-focused {\n    background: var(--vt-card-hover);\n    color: var(--vt-text);\n}\n\n/*category tabs*/\n\n.vt-tabbar {\n    display: flex;\n    gap: 8px;\n    padding: 12px 28px;\n    border-top: 1px solid var(--vt-line);\n    border-bottom: 1px solid var(--vt-line);\n    overflow: hidden;\n}\n\n.vt-tab {\n    padding: 10px 16px;\n    border-radius: 10px;\n    font-size: 16px;\n    font-weight: 500;\n    white-space: nowrap;\n    cursor: pointer;\n}\n\n.vt-tab:hover {\n    background: var(--vt-card-hover);\n}\n\n.vt-tab.vt-tab-selected {\n    background: var(--vt-text);\n    color: var(--vt-bg);\n}\n\n/*tabs keep their own background when focused and get an outer ring instead*/\n.vt-tabbar .vt-tab.vt-focused {\n    background: var(--vt-card-hover);\n    box-shadow: 0 0 0 2px var(--vt-bg), 0 0 0 4px var(--vt-focus);\n}\n\n.vt-tabbar .vt-tab.vt-tab-selected.vt-focused {\n    background: var(--vt-text);\n}\n\n/*content*/\n\n.vt-content-viewport {\n    position: relative;\n    flex: 1;\n    min-height: 0;\n    overflow: hidden;\n}\n\n.vt-content-list {\n    padding: 24px 28px 16px;\n    transition: transform 0.15s ease-out;\n}\n\n.vt-view-header {\n    display: flex;\n    align-items: flex-end;\n    gap: 16px;\n    margin-bottom: 16px;\n}\n\n.vt-view-title {\n    margin: 0;\n    font-size: 24px;\n    font-weight: 700;\n}\n\n.vt-view-description {\n    margin: 6px 0 0;\n    font-size: 15px;\n    color: var(--vt-muted);\n}\n\n.vt-view-count {\n    margin-left: auto;\n    font-size: 13px;\n    color: var(--vt-faint);\n    white-space: nowrap;\n}\n\n.vt-view-icon {\n    width: 64px;\n    height: 64px;\n    flex-shrink: 0;\n    align-self: center;\n    border-radius: 14px;\n}\n\n.vt-view-badge {\n    align-self: center;\n    margin-left: auto;\n    padding: 6px 12px;\n    border-radius: 8px;\n    background: var(--vt-card-hover);\n    font-size: 14px;\n    white-space: nowrap;\n}\n\n.vt-view-page > .vt-section + .vt-section {\n    margin-top: 16px;\n}\n\n.vt-section-grid {\n    display: grid;\n    grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));\n    gap: 24px 20px;\n    align-items: start;\n}\n\n.vt-section-title {\n    margin: 0 0 10px 2px;\n    font-size: 12px;\n    font-weight: 600;\n    letter-spacing: 0.08em;\n    text-transform: uppercase;\n    color: var(--vt-muted);\n}\n\n.vt-card {\n    display: flex;\n    flex-direction: column;\n    background: var(--vt-card);\n    border-radius: var(--vt-radius);\n}\n\n.vt-view-page .vt-card {\n    max-width: 760px;\n}\n\n/*rows*/\n\n.vt-row {\n    display: flex;\n    align-items: center;\n    gap: 16px;\n    min-height: 60px;\n    padding: 12px 16px;\n    cursor: pointer;\n}\n\n.vt-card > * + * {\n    border-top: 1px solid var(--vt-line);\n}\n\n.vt-card > :first-child {\n    border-top-left-radius: var(--vt-radius);\n    border-top-right-radius: var(--vt-radius);\n}\n\n.vt-card > :last-child {\n    border-bottom-left-radius: var(--vt-radius);\n    border-bottom-right-radius: var(--vt-radius);\n}\n\n.vt-row.vt-focused {\n    border-radius: var(--vt-radius);\n}\n\n.vt-row:hover {\n    background: var(--vt-card-hover);\n}\n\n.vt-row.vt-row-static {\n    min-height: 48px;\n    cursor: default;\n}\n\n.vt-row.vt-row-static:hover {\n    background: none;\n}\n\n.vt-row-static .vt-row-value {\n    color: var(--vt-text);\n}\n\n.vt-focusable.vt-focused {\n    background: var(--vt-card-focus);\n    box-shadow: inset 0 0 0 2px var(--vt-focus);\n    border-color: transparent;\n}\n\n.vt-focused + * {\n    border-color: transparent;\n}\n\n/*a toggle's page link, tucked under it while the toggle is on*/\n.vt-card > .vt-row-sub {\n    min-height: 44px;\n    padding: 4px 16px 12px 32px;\n    border-top: none;\n}\n\n.vt-row-sub .vt-row-title {\n    font-size: 14px;\n    color: var(--vt-muted);\n}\n\n.vt-row-sub.vt-focused .vt-row-title {\n    color: var(--vt-text);\n}\n\n.vt-row-info {\n    display: flex;\n    flex-direction: column;\n    gap: 3px;\n    flex: 1;\n    min-width: 0;\n}\n\n.vt-row-title {\n    font-size: 16px;\n    font-weight: 500;\n}\n\n.vt-row-description {\n    font-size: 13px;\n    line-height: 1.35;\n    color: var(--vt-muted);\n}\n\n.vt-row-value {\n    font-size: 14px;\n    color: var(--vt-muted);\n    white-space: nowrap;\n}\n\n.vt-chevron {\n    font-size: 24px;\n    line-height: 1;\n    color: var(--vt-muted);\n}\n\n.vt-inactive {\n    opacity: 0.4;\n    cursor: default;\n}\n\n.vt-inactive:hover {\n    background: none;\n}\n\n/*toggle*/\n\n.vt-toggle {\n    position: relative;\n    flex-shrink: 0;\n    width: 50px;\n    height: 28px;\n    border-radius: 14px;\n    background: #555;\n    transition: background 0.15s ease;\n}\n\n.vt-toggle-thumb {\n    position: absolute;\n    top: 3px;\n    left: 3px;\n    width: 22px;\n    height: 22px;\n    border-radius: 50%;\n    background: var(--vt-text);\n    transition: transform 0.15s ease, background 0.15s ease;\n}\n\n.vt-toggle.vt-toggle-on {\n    background: var(--vt-text);\n}\n\n.vt-toggle.vt-toggle-on .vt-toggle-thumb {\n    transform: translateX(22px);\n    background: var(--vt-bg);\n}\n\n/*notice and text*/\n\n.vt-notice {\n    margin: 8px;\n    padding: 12px 14px;\n    border: 1px solid var(--vt-line);\n    border-radius: 10px;\n}\n\n.vt-card > .vt-notice + * {\n    border-top: none;\n}\n\n.vt-notice-warning {\n    background: var(--vt-warning-bg);\n    border-color: var(--vt-warning-line);\n}\n\n.vt-notice-warning .vt-row-description {\n    color: #e0d2b0;\n}\n\n.vt-notice-success {\n    background: #173524;\n    border-color: #2b6a45;\n}\n\n.vt-notice-success .vt-row-description {\n    color: #b5dcc3;\n}\n\n.vt-notice-error {\n    background: #3a1c1c;\n    border-color: #7a3434;\n}\n\n.vt-notice-error .vt-row-description {\n    color: #f0c0c0;\n}\n\n.vt-text {\n    padding: 10px 16px;\n    font-size: 14px;\n    color: var(--vt-muted);\n}\n\n/*translation prompt under a category*/\n\n.vt-translate {\n    display: flex;\n    align-items: center;\n    gap: 6px;\n    width: fit-content;\n    margin-top: 16px;\n    padding: 6px 14px;\n    border-radius: 999px;\n    font-size: 14px;\n    color: var(--vt-muted);\n    cursor: pointer;\n}\n\n.vt-translate:hover,\n.vt-translate.vt-focused {\n    color: var(--vt-text);\n}\n\n/*back button on pages*/\n\n.vt-back {\n    display: inline-flex;\n    align-items: center;\n    gap: 8px;\n    margin-bottom: 14px;\n    padding: 6px 14px 6px 10px;\n    border-radius: 999px;\n    font-size: 14px;\n    color: var(--vt-muted);\n    cursor: pointer;\n}\n\n.vt-back:hover {\n    background: var(--vt-card-hover);\n}\n\n.vt-back.vt-focused {\n    color: var(--vt-text);\n}\n\n/*scrollbar*/\n\n.vt-scrollbar {\n    position: absolute;\n    top: 0;\n    bottom: 0;\n    right: 6px;\n    width: 4px;\n    display: none;\n}\n\n.vt-scrollbar.vt-scrollbar-visible {\n    display: block;\n}\n\n.vt-scrollbar-thumb {\n    width: 100%;\n    border-radius: 2px;\n    background: #666;\n    transition: transform 0.15s ease-out;\n}" }, "/vt/src/preload/modules/speed-control/style.css": { "text": "#vt-speed-indicator {\n    position: fixed;\n    bottom: 80px;\n    left: 50%;\n    transform: translateX(-50%);\n    background-color: rgba(0, 0, 0, 0.5);\n    color: white;\n    padding: 12px 24px;\n    border-radius: 1.5rem;\n    display: flex;\n    align-items: center;\n    z-index: 999999;\n    opacity: 0;\n    transition: opacity 0.3s ease-in-out;\n    pointer-events: none;\n}\n\n#vt-speed-indicator.visible {\n    opacity: 1;\n}\n\n.vt-speed-icon {\n    width: 24px;\n    height: 24px;\n    margin-right: 16px;\n    font-size: 24px;\n    font-family: 'YouTube Icons Outlined';\n}\n\n.vt-speed-icon::before {\n    content: '\\edf6';\n}\n\n.vt-speed-text {\n    font-size: 18px;\n    font-weight: 500;\n    min-width: 30px;\n    text-align: right;\n}" }, "/vt/src/preload/modules/volume-control/style.css": { "text": `div[idomkey="lazy-mdx-volume"] { /*hide ugly legacy volume indicator that appears when setVolume is called on player*/
    display: none;
}

#vt-volume-indicator {
    position: fixed;
    bottom: 80px;
    left: 50%;
    transform: translateX(-50%);
    background-color: rgba(0, 0, 0, 0.5);
    color: white;
    padding: 12px 24px;
    border-radius: 1.5rem;
    display: flex;
    align-items: center;
    z-index: 999999;
    opacity: 0;
    transition: opacity 0.3s ease-in-out;
    pointer-events: none;
}

#vt-volume-indicator.visible {
    opacity: 1;
}

.vt-volume-icon {
    width: 24px;
    height: 24px;
    margin-right: 16px;
    font-size: 24px;
    font-family: 'YouTube Icons Outlined';
}

.vt-volume-icon.vt-volume-high::before {
    content: '\\ed89';
}

.vt-volume-icon.vt-volume-low::before {
    content: '\\ed8a';
}

.vt-volume-icon.vt-volume-muted::before {
    content: '\\e04e';
}

.vt-volume-bar-container {
    width: 200px;
    height: 8px;
    background-color: rgba(255, 255, 255, 0.3);
    border-radius: 4px;
    overflow: hidden;
}

.vt-volume-bar {
    height: 100%;
    width: 50%;
    background-color: white;
    border-radius: 4px;
    transition: width 0.1s linear;
}

.vt-volume-text {
    font-size: 18px;
    font-weight: 500;
    min-width: 55px;
    text-align: right;
    margin-left: 16px;
}` }, "/vt/src/preload/style.css": { "text": "/*css patches*/\n\n::-webkit-scrollbar { /*hide native scrollbars from enableTouchSupport in touch-support.js*/\n    display: none;\n}\n\nbody {\n    user-select: none; /*without this, you can accidentally ctrl+a and select everything, have to refresh to fix*/\n}" }, "/vt/locale/README.md": { "text": "# Localizing VacuumTube\n\nVacuumTube has a good amount of strings of its own, and since it integrates with YouTube directly, it makes sense to localize them.\n\nTo get started on your own translation, you may need to have some JSON experience. If you're inexperienced, you can likely still do it.\n\nFirst, follow these steps:\n\n1. Open VacuumTube on a device with a keyboard.\n2. Change your language to the one you intend to translate, and let YouTube reload.\n3. Press **Ctrl+Shift+I** to open up developer tools.\n4. Navigate to the **Console** tab.\n5. Type `ytcfg.data_.HL` into the console and press enter.\n\nThis will provide you with the tag of the language you want to translate. If you want to translate a language by itself, without any regional dialects, you can remove everything after the `-`. This would turn `en-GB` to `en`, for example.\n\nThen, fork VacuumTube and open the `locale` folder. If you don't know anything about GitHub or what forking is, you can just download the source code as a zip file and do it there, then create an issue. Otherwise, you should create a PR with your fork.\n\nIf there's already a file for your desired language, but not everything is translated, you can bring it up to date. You will need JSON experience for this though, since you'll be manually copy pasting objects.\n\nOtherwise, copy the `en.json` file and paste it in the same directory. Rename it to the language tag you acquired previously, and make sure to keep the extension at the end.\n\nAfter that, just go through every string and translate it to the desired language. Make sure it's properly formatted and gramatically correct, as every other string in the regular YouTube app is.\n\nI'd recommend doing this in a more sophisticated editor that does syntax highlighting, especially if you're inexperienced with JSON since you may not know what parts to translate. Only translate the text between quotes, and make sure not to translate things like \"YouTube\", \"VacuumTube\", and \"SponsorBlock\", since those are names, and don't translate variables like `{path}`.\n\nOnce you're done translating, either create a PR with your fork, or if you didn't know how to fork, open an issue with the translated JSON file." }, "/vt/locale/bs.json": { "text": `{
    "general": {
        "video_copied": "Link videa kopiran u me\u0111uspremnik",
        "dislikes": "Palac dole"
    },
    "settings": {
        "adblock": {
            "title": "Bloker Reklama",
            "description": "Besprijekorno blokira video i feed oglase, ne podlije\u017Ee YouTubeovim metodama za sprje\u010Davanje blokatora"
        },
        "dearrow": {
            "title": "DeArrow",
            "description": "Zamjenjuje naslove i sli\u010Dice ta\u010Dnijim, manje senzacionalisti\u010Dkim verzijama iz javne baze podataka prikupljenim sa strane korisnika. Usporava u\u010Ditavanje, jer mora preuzeti informacije sa servera"
        },
        "dislikes": {
            "title": "Vratite donji palac",
            "description": "Koristi YouTube API za vra\u0107anje palca prema dolje (returnyoutubedislike.com) za ponovno dodavanje oznake 'nesvi\u0111a mi se' na YouTube. Usporava u\u010Ditavanje videa jer mora preuzeti informacije sa servera"
        },
        "hardware_decoding": {
            "title": "Hardversko Dekodiranje",
            "description": "Koristi va\u0161u grafi\u010Dku karticu za dekodiranje videa kada je to mogu\u0107e. Onemogu\u0107avanje ove opcije mo\u017Ee rije\u0161iti probleme s reprodukcijom, ali mo\u017Ee uzrokovati ka\u0161njenje ovisno o va\u0161em procesoru. Ponovo pokrenite nakon prebacivanja."
        },
        "low_memory_mode": {
            "title": "Re\u017Eim za Nisku Memoriju",
            "description": "Uputuje YouTube-u da omogu\u0107i na\u010Din rada s malo memorije, \u0161to mo\u017Ee pobolj\u0161ati pokretanje na sporijim ure\u0111ajima, ali na \u0161tetu nekih izglednih efekata. Ponovo pokrenite nakon prebacivanja."
        },
        "fullscreen": {
            "title": "Cijeli Ekran",
            "description": "Omogu\u0107ava cijeli ekran i \u010Dini da se VacuumTube uvijek pokrene u punom ekranu."
        },
        "keep_on_top": {
            "title": "Ostani na vrhu",
            "description": "Omogu\u0107ava opciju \\"Ostani na vrhu\\" i pokre\u0107e VacuumTube s prozorom zaka\u010Denim preko svakog drugog prozora."
        },
        "userstyles": {
            "title": "Prilago\u0111eni CSS (korisni\u010Dki stilovi)",
            "description": "Omogu\u0107ava ubrizgavanje prilago\u0111enih CSS stilova. Postavite svoje .css datoteke u mapu 'korisni\u010Dki stilovi'."
        }
    }
}
` }, "/vt/locale/de.json": { "text": '{\n    "general": {\n        "video_copied": "Videolink in die Zwischenablage kopiert",\n        "dislikes": "Abneigungskn\xF6pfe"\n    },\n    "settings": {\n        "adblock": {\n            "title": "Anzeigenblocker",\n            "description": "Blockiert nahtlos Video- und Feed-Werbung und unterliegt nicht den Methoden von YouTube zur Verhinderung von Blockern"\n        },\n        "dearrow": {\n            "title": "DeArrow",\n            "description": "Ersetzt Titel und Vorschaubilder durch genauere, weniger sensationsl\xFCsterne Versionen aus einer \xF6ffentlichen Crowdsourced-Datenbank. Verlangsamt das Laden, da es Informationen vom Server abrufen muss"\n        },\n        "dislikes": {\n            "title": "Abneigungen zur\xFCckgeben",\n            "description": "Verwendet die YouTube Abneigungen zur\xFCckgeben API (returnyoutubedislike.com), um Abneigungskn\xF6pfe wieder zu YouTube hinzuzuf\xFCgen. Verlangsamt das Laden von Videos, da Informationen vom Server abgerufen werden m\xFCssen"\n        },\n        "hardware_decoding": {\n            "title": "Hardware-Dekodierung",\n            "description": "Verwendet Ihren Grafikprozessor zur Dekodierung von Videos, wenn m\xF6glich. Die Deaktivierung dieser Funktion kann Wiedergabeprobleme beheben, kann aber je nach CPU zu Verz\xF6gerungen f\xFChren. Starten Sie nach dem Umschalten neu."\n        },\n        "low_memory_mode": {\n            "title": "Wenigspeichermodus",\n            "description": "Weist YouTube an, den Modus f\xFCr geringen Speicherbedarf zu aktivieren, was die Leistung auf langsameren Ger\xE4ten auf Kosten einiger visueller Effekte verbessern kann. Starten Sie nach dem Umschalten neu."\n        },\n        "fullscreen": {\n            "title": "Vollbildschirm",\n            "description": "Aktiviert den Vollbildmodus und l\xE4sst VacuumTube immer im Vollbildmodus starten."\n        },\n        "keep_on_top": {\n            "title": "Oben halten",\n            "description": "Aktiviert \u201EOben halten\u201C und sorgt daf\xFCr, dass VacuumTube so gestartet wird, dass das Fenster \xFCber allen anderen Fenstern angeheftet ist."\n        },\n        "userstyles": {\n            "title": "Benutzerdefinierte CSS (Benutzerstile)",\n            "description": "Erm\xF6glicht das Einf\xFCgen von benutzerdefinierten CSS-Stilen. Legen Sie Ihre .css-Dateien im Ordner \u201EBenutzerstile\u201C ab."\n        },\n        "features": {\n            "settings": {\n                "remaining_time": {\n                    "title": "Verbleibende Zeit anzeigen",\n                    "description": "Zeigt die verbleibende Abspielzeit eines Videos anstatt der Gesamtl\xE4nge. Ber\xFCcksichtigt die aktuelle Wiedergabegeschwindigkeit."\n                }\n            }\n        }\n    }\n}\n' }, "/vt/locale/en.json": { "text": `{
    "general": {
        "video_copied": "Video link copied to clipboard",
        "dislikes": "Dislikes",
        "steam_controller_notice": "External controller isn't detected? Try the steamdeck-input-disabler plugin from Decky.",
        "encryption_error": {
            "title": "Something went wrong",
            "text": "This video couldn't be decoded. Movies aren't supported in VacuumTube due to encryption. If this happens on other videos, try logging in or switching accounts.",
            "switch_accounts": "Switch Accounts",
            "okay": "Okay"
        }
    },
    "settings": {
        "generic": {
            "title": "Settings",
            "description": "Opens the VacuumTube settings overlay. You can access this overlay anywhere by pressing Ctrl+O on your keyboard",
            "button_label": "Open VacuumTube Settings",
            "hint": "Use arrow keys to navigate",
            "option_count": "{count} options",
            "translate": "Help translate VacuumTube to {language}",
            "ratio": "{on} of {total}"
        },
        "categories": {
            "general": {
                "title": "General",
                "description": "How the VacuumTube window behaves."
            },
            "playback": {
                "title": "Playback",
                "description": "Control what plays and how videos are presented."
            },
            "appearance": {
                "title": "Appearance",
                "description": "Change what the interface shows and how it looks."
            },
            "controls": {
                "title": "Controls & Devices",
                "description": "Input methods and connected devices."
            },
            "advanced": {
                "title": "Advanced",
                "description": "Performance tweaks and optional features."
            }
        },
        "sections": {
            "window": "Window",
            "behavior": "Behavior",
            "updates": "Updates",
            "content": "Content",
            "quality": "Quality & Compatibility",
            "interface": "Interface",
            "userstyles": "Custom CSS",
            "input": "Input",
            "devices": "Devices",
            "performance": "Performance",
            "experimental": "Experimental"
        },
        "adblock": {
            "title": "Block Ads",
            "description": "Seamlessly blocks video and feed ads, not subject to YouTube's methods of preventing blockers"
        },
        "sponsorblock": {
            "title": "SponsorBlock",
            "description": "Skips sponsor segments in YouTube videos using a crowdsourced database",
            "categories_link": "Segments to skip",
            "categories": {
                "sponsor": {
                    "name": "Sponsor",
                    "description": "Paid promotion, paid referrals and direct advertisements. Not for self-promotion or free shoutouts to causes/creators/websites/products they like."
                },
                "selfpromo": {
                    "name": "Unpaid/Self Promotion",
                    "description": "Similar to 'sponsor' except for unpaid or self promotion. This includes sections about merchandise, donations, or information about who they collaborated with."
                },
                "interaction": {
                    "name": "Interaction Reminder (Subscribe)",
                    "description": "When there is a short reminder to like, subscribe or follow them in the middle of content. If it is long or about something specific, it should be under self promotion instead."
                },
                "intro": {
                    "name": "Intermission/Intro Animation",
                    "description": "An interval without actual content. Could be a pause, static frame, repeating animation. This should not be used for transitions containing information."
                },
                "outro": {
                    "name": "Endcards/Credits",
                    "description": "Credits or when the YouTube endcards appear. Not for conclusions with information."
                },
                "preview": {
                    "name": "Preview/Recap",
                    "description": "Collection of clips that show what is coming up in in this video or other videos in a series where all information is repeated later in the video."
                },
                "hook": {
                    "name": "Hook/Greetings",
                    "description": "Narrated trailers for the upcoming video, greetings and goodbyes. This should not skip conclusions with information."
                },
                "filler": {
                    "name": "Tangents/Jokes",
                    "description": "Tangential scenes or jokes that are not required to understand the main content of the video. This should not include segments providing context or background details. This is a very aggressive category meant for when you aren't in the mood for 'fun'."
                }
            }
        },
        "dearrow": {
            "title": "DeArrow",
            "description": "Replaces titles and thumbnails with more accurate, less sensationalized versions from a public crowdsourced database. Slows down loading, as it has to fetch info from the server"
        },
        "dislikes": {
            "title": "Return Dislikes",
            "description": "Uses community data from the Return YouTube Dislike API (returnyoutubedislike.com) to show rough dislike counts. Slows down video loading, as it has to fetch info from the server"
        },
        "remove_super_resolution": {
            "title": "Remove Super Resolution",
            "description": "Remove \\"Super resolution\\" (AI upscaled) qualities from low quality videos"
        },
        "hide_shorts": {
            "title": "Hide Shorts",
            "description": "Hides YouTube Shorts from the homepage"
        },
        "guide_tabs": {
            "title": "Guide Tabs",
            "description": "Choose which sidebar guide tabs are shown. Turn a tab off to hide it. Relaunch after changing",
            "tabs": {
                "search": "Search",
                "shorts": "Shorts",
                "subscriptions": "Subscriptions",
                "library": "Library",
                "gaming": "Gaming",
                "music": "Music",
                "news": "News",
                "podcasts": "Podcasts",
                "movies_and_tv": "Movies & TV",
                "live": "Live",
                "sports": "Sports"
            }
        },
        "unlock_resolution": {
            "title": "Unlock Resolution",
            "description": "Removes monitor resolution cap and allows you to watch videos at any resolution. Relaunch after toggling"
        },
        "h264ify": {
            "title": "Filter Video Codecs",
            "description": "Codec filtering options. Relaunch after toggling any of the options.",
            "enable_title": "Enable codec filter (h264ify)",
            "enable_description": "Enables the codec filter. This can help with performance and battery life on slower devices, but may reduce the available resolutions.",
            "disable_codec_title": "Disable {codec}",
            "disable_codec_description": "Block {codec} streams.",
            "codecs_link": "Blocked codecs"
        },
        "block_continue_watching": {
            "title": "Block Continue Watching",
            "description": "Blocks the \\"Video paused. Continue watching?\\" prompt that appears after being idle for a while"
        },
        "hardware_decoding": {
            "title": "Hardware Decoding",
            "description": "Uses your GPU to decode videos when possible. Disabling this may fix playback issues, but can cause lag depending on your CPU. Relaunch after toggling"
        },
        "wayland_hdr": {
            "title": "Wayland HDR",
            "description": "Enables HDR when using Wayland, but can cause desaturated colors on unsupported platforms. Relaunch after toggling"
        },
        "low_memory_mode": {
            "title": "Low Memory Mode",
            "description": "Tells YouTube to enable low memory mode, which may improve performance on slower devices at the cost of some visual effects. Relaunch after toggling"
        },
        "fullscreen": {
            "title": "Fullscreen",
            "description": "Enables fullscreen, and makes VacuumTube always launch in fullscreen"
        },
        "no_window_decorations": {
            "title": "No Window Decorations",
            "description": "Disables window decorations, including the title bar and window border. Relaunch after toggling"
        },
        "keep_on_top": {
            "title": "Keep on Top",
            "description": "Enables Keep on Top, and makes VacuumTube launch with the window pinned on top of every other window"
        },
        "auto_update": {
            "title": "Automatic Updates",
            "description": "Download new versions in the background and install them when VacuumTube closes. When off, you can still check for updates from the About page"
        },
        "pause_on_blur": {
            "title": "Pause on Blur",
            "description": "Pause current video when VacuumTube loses focus (e.g. tabbing out or minimizing the window)"
        },
        "features": {
            "title": "Features",
            "notice_title": "Not part of the default experience",
            "notice_description": "These optional features extend VacuumTube beyond the standard TV YouTube experience. Enable them below to acknowledge that they rely on internal YouTube behavior and may change or stop working without notice.",
            "enable_title": "Enable optional features",
            "enable_description": "I understand that these features are outside VacuumTube's default experience and may change or break.",
            "settings": {
                "music_mode": {
                    "title": "Music Mode",
                    "description": "Adds a Music Mode toggle to the video settings menu for audio-only playback using YouTube's native music view."
                },
                "remaining_time": {
                    "title": "Show Remaining Time",
                    "description": "Displays the remaining video runtime in the player overlay instead of the elapsed time. Takes current playback speed into account. Press R to toggle it for the current video."
                },
                "ultrawide": {
                    "title": "Ultrawide Support",
                    "description": "Expands the interface to fill ultrawide displays when in fullscreen. May look buggy and weird in some ways."
                }
            }
        },
        "userstyles": {
            "title": "Custom CSS (Userstyles)",
            "description": "Place .css files in the userstyles folder to customize the interface. Changes are applied automatically",
            "warn_empty": "No userstyles found",
            "open_folder": "Open Userstyles folder",
            "enable": "Enable Userstyles",
            "failed_to_load": "Failed to load userstyles",
            "manage_link": "Manage userstyles"
        },
        "touch_overlay": {
            "title": "Touch Overlay",
            "description": "Enables on-screen touch controls for easier navigation when touch input is detected"
        },
        "controller_support": {
            "title": "Controller Support",
            "description": "Enables support for game controllers, including navigation and video playback controls. Requires an external controller, such as an Xbox controller"
        },
        "device_discoverability": {
            "title": "Device Discoverability",
            "description": "Allows VacuumTube to be discovered by the YouTube mobile app on devices within the same local network. Relaunch after toggling"
        },
        "mac_permissions": {
            "title": "Permissions",
            "description": "Manage macOS permissions used by VacuumTube.",
            "microphone_title": "Microphone",
            "microphone_description": "Used only when you start YouTube voice search.",
            "status_label": "Status",
            "status_loading": "Checking microphone permission...",
            "request_microphone": "Request Microphone Access",
            "open_microphone_settings": "Open macOS Microphone Settings",
            "reset_microphone": "Troubleshooting: Reset Permission Prompt",
            "relaunch_app": "Relaunch VacuumTube",
            "restart_required": "Changes made in macOS Settings require relaunching VacuumTube.",
            "request_granted": "Microphone access is allowed. No permission prompt is needed.",
            "request_not_determined": "macOS still has not shown a permission prompt. Use Reset Permission Prompt, then try again.",
            "request_denied_help": "macOS will not show the permission prompt again while access is denied. Turn it on in macOS Settings, then relaunch VacuumTube.",
            "request_failed": "VacuumTube could not request microphone access. Open macOS Settings and relaunch the app after changing access.",
            "resetting_microphone": "Resetting the saved macOS microphone decision...",
            "statuses": {
                "not-determined": "Not Requested",
                "granted": "Allowed",
                "denied": "Denied",
                "restricted": "Restricted",
                "unknown": "Unknown",
                "unsupported": "Unsupported"
            }
        },
        "about": {
            "title": "About",
            "description": "YouTube for TV, on your PC",
            "version": "Version {version}",
            "platform_label": "Platform",
            "installation_label": "Installation",
            "update_method_label": "Update method",
            "view_github": "View on GitHub",
            "latest_releases": "Latest releases",
            "platforms": {
                "darwin": "macOS",
                "win32": "Windows",
                "linux": "Linux"
            },
            "architectures": {
                "arm64": "arm64",
                "x64": "x64",
                "ia32": "x86"
            },
            "installations": {
                "application": "Application",
                "installer": "Installed application",
                "portable": "Portable application",
                "flatpak": "Flatpak",
                "appimage": "AppImage",
                "package": "System package",
                "development": "Development build",
                "unknown": "Unknown"
            },
            "update_methods": {
                "automatic": "Automatic installer",
                "manual": "Manual download",
                "flatpak": "Flatpak"
            },
            "updates": {
                "idle_title": "Updates",
                "idle_detail": "Check whether a newer version of VacuumTube is available.",
                "check_now": "Check for updates",
                "checking_title": "Checking for updates\u2026",
                "checking_detail": "Looking for the newest VacuumTube release.",
                "checking_button": "Checking\u2026",
                "current_title": "You\u2019re up to date",
                "current_detail": "VacuumTube {version} is the newest version available.",
                "check_again": "Check again",
                "available_title": "VacuumTube {version} is available",
                "available_automatic_detail": "Download the update now, then restart VacuumTube when it is ready.",
                "available_manual_detail": "Download the latest build and replace your current copy.",
                "download_update": "Download update",
                "download_latest": "Download latest version",
                "downloading_title": "Downloading update\u2026",
                "downloading_detail": "The update is {percent}% downloaded.",
                "downloading_button": "Downloading\u2026 {percent}%",
                "ready_title": "Update ready to install",
                "ready_detail": "Restart VacuumTube to install version {version}.",
                "install_update": "Restart and install",
                "error_title": "Couldn\u2019t check for updates",
                "error_detail": "Check your connection and try again, or open the releases page.",
                "try_again": "Try again",
                "development_title": "Development build",
                "development_detail": "Automatic update checks are available in packaged builds.",
                "unsupported_title": "Update check unavailable",
                "unsupported_detail": "Use the update method shown below to check for a newer version.",
                "view_update_source": "View update source",
                "view_releases": "View latest releases"
            }
        }
    },
    "sponsorblock": {
        "sponsor_skipped": "Sponsor skipped",
        "selfpromo_skipped": "Self Promo skipped",
        "interaction_skipped": "Interaction skipped",
        "intro_skipped": "Intro skipped",
        "outro_skipped": "Outro skipped",
        "preview_skipped": "Preview skipped",
        "hook_skipped": "Hook/Greetings skipped",
        "filler_skipped": "Tangents/Jokes skipped"
    },
    "donate_unused": {
        "setting": {
            "title": "Donate",
            "description": "Support VacuumTube's development by making a donation! You can donate by visiting shy.rocks/donate, or by pressing the button below to open the donation page in your browser",
            "button_label": "Donate"
        }
    }
}
` }, "/vt/locale/fa.json": { "text": '{\n    "general": {\n        "video_copied": "\u067E\u06CC\u0648\u0646\u062F \u0648\u06CC\u062F\u06CC\u0648 \u062F\u0631 \u0628\u0631\u06CC\u062F\u0647\u200C\u062F\u0627\u0646 \u06A9\u067E\u06CC \u0634\u062F",\n        "dislikes": "\u062F\u06CC\u0633\u200C\u0644\u0627\u06CC\u06A9\u200C\u0647\u0627",\n        "steam_controller_notice": "\u062F\u0633\u062A\u0647 \u062E\u0627\u0631\u062C\u06CC \u0634\u0646\u0627\u0633\u0627\u06CC\u06CC \u0646\u0645\u06CC\u200C\u0634\u0648\u062F\u061F \u0627\u0641\u0632\u0648\u0646\u0647 steamdeck-input-disabler \u0627\u0632 Decky \u0631\u0627 \u0627\u0645\u062A\u062D\u0627\u0646 \u06A9\u0646\u06CC\u062F.",\n        "encryption_error": {\n            "title": "\u0645\u0634\u06A9\u0644\u06CC \u067E\u06CC\u0634 \u0622\u0645\u062F",\n            "text": "\u0631\u0645\u0632\u06AF\u0634\u0627\u06CC\u06CC \u0627\u06CC\u0646 \u0648\u06CC\u062F\u06CC\u0648 \u0645\u0645\u06A9\u0646 \u0646\u0634\u062F. \u0628\u0647\u200C\u062F\u0644\u06CC\u0644 \u0631\u0645\u0632\u06AF\u0630\u0627\u0631\u06CC\u060C \u0641\u06CC\u0644\u0645\u200C\u0647\u0627 \u062F\u0631 VacuumTube \u067E\u0634\u062A\u06CC\u0628\u0627\u0646\u06CC \u0646\u0645\u06CC\u200C\u0634\u0648\u0646\u062F. \u0627\u06AF\u0631 \u0627\u06CC\u0646 \u0645\u0634\u06A9\u0644 \u062F\u0631 \u0633\u0627\u06CC\u0631 \u0648\u06CC\u062F\u06CC\u0648\u0647\u0627 \u0646\u06CC\u0632 \u0631\u062E \u062F\u0627\u062F\u060C \u0648\u0631\u0648\u062F \u0628\u0647 \u062D\u0633\u0627\u0628 \u06CC\u0627 \u062A\u063A\u06CC\u06CC\u0631 \u062D\u0633\u0627\u0628 \u0631\u0627 \u0627\u0645\u062A\u062D\u0627\u0646 \u06A9\u0646\u06CC\u062F.",\n            "switch_accounts": "\u062A\u063A\u06CC\u06CC\u0631 \u062D\u0633\u0627\u0628",\n            "okay": "\u062A\u0623\u06CC\u06CC\u062F"\n        }\n    },\n    "settings": {\n        "generic": {\n            "title": "\u062A\u0646\u0638\u06CC\u0645\u0627\u062A",\n            "description": "\u0646\u0645\u0627\u06CC \u062A\u0646\u0638\u06CC\u0645\u0627\u062A VacuumTube \u0631\u0627 \u0628\u0627\u0632 \u0645\u06CC\u200C\u06A9\u0646\u062F. \u0628\u0627 \u0641\u0634\u0631\u062F\u0646 Ctrl+O \u0631\u0648\u06CC \u0635\u0641\u062D\u0647\u200C\u06A9\u0644\u06CC\u062F \u0645\u06CC\u200C\u062A\u0648\u0627\u0646\u06CC\u062F \u062F\u0631 \u0647\u0631 \u062C\u0627\u06CC\u06CC \u0628\u0647 \u0627\u06CC\u0646 \u0646\u0645\u0627 \u062F\u0633\u062A\u0631\u0633\u06CC \u067E\u06CC\u062F\u0627 \u06A9\u0646\u06CC\u062F",\n            "button_label": "\u0628\u0627\u0632 \u06A9\u0631\u062F\u0646 \u062A\u0646\u0638\u06CC\u0645\u0627\u062A VacuumTube",\n            "hint": "\u0628\u0631\u0627\u06CC \u067E\u06CC\u0645\u0627\u06CC\u0634 \u0627\u0632 \u06A9\u0644\u06CC\u062F\u0647\u0627\u06CC \u062C\u0647\u062A\u200C\u0646\u0645\u0627 \u0627\u0633\u062A\u0641\u0627\u062F\u0647 \u06A9\u0646\u06CC\u062F",\n            "option_count": "{count} \u06AF\u0632\u06CC\u0646\u0647",\n            "translate": "\u06A9\u0645\u06A9 \u0628\u0647 \u062A\u0631\u062C\u0645\u0647 VacuumTube \u0628\u0647 {language}",\n            "ratio": "{on} \u0627\u0632 {total}"\n        },\n        "categories": {\n            "general": {\n                "title": "\u0639\u0645\u0648\u0645\u06CC",\n                "description": "\u0646\u062D\u0648\u0647 \u0631\u0641\u062A\u0627\u0631 \u067E\u0646\u062C\u0631\u0647 VacuumTube."\n            },\n            "playback": {\n                "title": "\u067E\u062E\u0634",\n                "description": "\u06A9\u0646\u062A\u0631\u0644 \u0645\u062D\u062A\u0648\u0627\u06CC \u062F\u0631 \u062D\u0627\u0644 \u067E\u062E\u0634 \u0648 \u0646\u062D\u0648\u0647 \u0646\u0645\u0627\u06CC\u0634 \u0648\u06CC\u062F\u06CC\u0648\u0647\u0627."\n            },\n            "appearance": {\n                "title": "\u0638\u0627\u0647\u0631",\n                "description": "\u062A\u063A\u06CC\u06CC\u0631 \u0645\u062D\u062A\u0648\u0627\u06CC \u0631\u0627\u0628\u0637 \u06A9\u0627\u0631\u0628\u0631\u06CC \u0648 \u0646\u062D\u0648\u0647 \u0646\u0645\u0627\u06CC\u0634 \u0622\u0646."\n            },\n            "controls": {\n                "title": "\u06A9\u0646\u062A\u0631\u0644\u200C\u0647\u0627 \u0648 \u062F\u0633\u062A\u06AF\u0627\u0647\u200C\u0647\u0627",\n                "description": "\u0631\u0648\u0634\u200C\u0647\u0627\u06CC \u0648\u0631\u0648\u062F\u06CC \u0648 \u062F\u0633\u062A\u06AF\u0627\u0647\u200C\u0647\u0627\u06CC \u0645\u062A\u0635\u0644."\n            },\n            "advanced": {\n                "title": "\u067E\u06CC\u0634\u0631\u0641\u062A\u0647",\n                "description": "\u0628\u0647\u06CC\u0646\u0647\u200C\u0633\u0627\u0632\u06CC \u0639\u0645\u0644\u06A9\u0631\u062F \u0648 \u0642\u0627\u0628\u0644\u06CC\u062A\u200C\u0647\u0627\u06CC \u0627\u062E\u062A\u06CC\u0627\u0631\u06CC."\n            }\n        },\n        "sections": {\n            "window": "\u067E\u0646\u062C\u0631\u0647",\n            "behavior": "\u0631\u0641\u062A\u0627\u0631",\n            "updates": "\u0628\u0647\u200C\u0631\u0648\u0632\u0631\u0633\u0627\u0646\u06CC\u200C\u0647\u0627",\n            "content": "\u0645\u062D\u062A\u0648\u0627",\n            "quality": "\u06A9\u06CC\u0641\u06CC\u062A \u0648 \u0633\u0627\u0632\u06AF\u0627\u0631\u06CC",\n            "interface": "\u0631\u0627\u0628\u0637 \u06A9\u0627\u0631\u0628\u0631\u06CC",\n            "userstyles": "\u200FCSS \u0633\u0641\u0627\u0631\u0634\u06CC",\n            "input": "\u0648\u0631\u0648\u062F\u06CC",\n            "devices": "\u062F\u0633\u062A\u06AF\u0627\u0647\u200C\u0647\u0627",\n            "performance": "\u0639\u0645\u0644\u06A9\u0631\u062F",\n            "experimental": "\u0622\u0632\u0645\u0627\u06CC\u0634\u06CC"\n        },\n        "adblock": {\n            "title": "\u0645\u0633\u062F\u0648\u062F\u06A9\u0646\u0646\u062F\u0647 \u062A\u0628\u0644\u06CC\u063A\u0627\u062A",\n            "description": "\u062A\u0628\u0644\u06CC\u063A\u0627\u062A \u0648\u06CC\u062F\u06CC\u0648 \u0648 \u0641\u06CC\u062F \u0631\u0627 \u0628\u0647\u200C\u0637\u0648\u0631 \u06CC\u06A9\u067E\u0627\u0631\u0686\u0647 \u0645\u0633\u062F\u0648\u062F \u0645\u06CC\u200C\u06A9\u0646\u062F \u0648 \u062A\u062D\u062A \u062A\u0623\u062B\u06CC\u0631 \u0631\u0648\u0634\u200C\u0647\u0627\u06CC YouTube \u0628\u0631\u0627\u06CC \u0645\u0642\u0627\u0628\u0644\u0647 \u0628\u0627 \u0645\u0633\u062F\u0648\u062F\u06A9\u0646\u0646\u062F\u0647\u200C\u0647\u0627 \u0642\u0631\u0627\u0631 \u0646\u0645\u06CC\u200C\u06AF\u06CC\u0631\u062F"\n        },\n        "sponsorblock": {\n            "title": "SponsorBlock",\n            "description": "\u0628\u062E\u0634\u200C\u0647\u0627\u06CC \u062D\u0627\u0645\u06CC \u0645\u0627\u0644\u06CC \u062F\u0631 \u0648\u06CC\u062F\u06CC\u0648\u0647\u0627\u06CC YouTube \u0631\u0627 \u0628\u0627 \u0627\u0633\u062A\u0641\u0627\u062F\u0647 \u0627\u0632 \u06CC\u06A9 \u067E\u0627\u06CC\u06AF\u0627\u0647\u200C\u062F\u0627\u062F\u0647 \u062C\u0645\u0639\u200C\u0633\u067E\u0627\u0631\u06CC\u200C\u0634\u062F\u0647 \u0631\u062F \u0645\u06CC\u200C\u06A9\u0646\u062F",\n            "categories_link": "\u0628\u062E\u0634\u200C\u0647\u0627\u06CC \u0642\u0627\u0628\u0644 \u0631\u062F \u06A9\u0631\u062F\u0646",\n            "categories": {\n                "sponsor": {\n                    "name": "\u062D\u0627\u0645\u06CC \u0645\u0627\u0644\u06CC",\n                    "description": "\u062A\u0628\u0644\u06CC\u063A\u0627\u062A \u067E\u0648\u0644\u06CC\u060C \u0627\u0631\u062C\u0627\u0639\u0627\u062A \u067E\u0648\u0644\u06CC \u0648 \u062A\u0628\u0644\u06CC\u063A\u0627\u062A \u0645\u0633\u062A\u0642\u06CC\u0645. \u0634\u0627\u0645\u0644 \u062A\u0628\u0644\u06CC\u063A\u0627\u062A \u0634\u062E\u0635\u06CC \u06CC\u0627 \u0645\u0639\u0631\u0641\u06CC \u0631\u0627\u06CC\u06AF\u0627\u0646 \u0627\u0647\u062F\u0627\u0641\u060C \u0633\u0627\u0632\u0646\u062F\u06AF\u0627\u0646\u060C \u0648\u0628\u200C\u0633\u0627\u06CC\u062A\u200C\u0647\u0627 \u06CC\u0627 \u0645\u062D\u0635\u0648\u0644\u0627\u062A \u0645\u0648\u0631\u062F \u0639\u0644\u0627\u0642\u0647\u200C\u0634\u0627\u0646 \u0646\u0645\u06CC\u200C\u0634\u0648\u062F."\n                },\n                "selfpromo": {\n                    "name": "\u062A\u0628\u0644\u06CC\u063A\u0627\u062A \u0634\u062E\u0635\u06CC/\u063A\u06CC\u0631\u067E\u0648\u0644\u06CC",\n                    "description": "\u0645\u0634\u0627\u0628\u0647 \xAB\u062D\u0627\u0645\u06CC \u0645\u0627\u0644\u06CC\xBB\u060C \u0627\u0645\u0627 \u0645\u062E\u062A\u0635 \u062A\u0628\u0644\u06CC\u063A\u0627\u062A \u0634\u062E\u0635\u06CC \u06CC\u0627 \u063A\u06CC\u0631\u067E\u0648\u0644\u06CC. \u0634\u0627\u0645\u0644 \u0628\u062E\u0634\u200C\u0647\u0627\u06CC \u0645\u0631\u0628\u0648\u0637 \u0628\u0647 \u06A9\u0627\u0644\u0627\u0647\u0627\u060C \u06A9\u0645\u06A9\u200C\u0647\u0627\u06CC \u0645\u0627\u0644\u06CC \u06CC\u0627 \u0627\u0637\u0644\u0627\u0639\u0627\u062A \u0645\u0631\u0628\u0648\u0637 \u0628\u0647 \u0647\u0645\u06A9\u0627\u0631\u0627\u0646 \u0645\u06CC\u200C\u0634\u0648\u062F."\n                },\n                "interaction": {\n                    "name": "\u06CC\u0627\u062F\u0622\u0648\u0631\u06CC \u062A\u0639\u0627\u0645\u0644 (\u0627\u0634\u062A\u0631\u0627\u06A9)",\n                    "description": "\u06CC\u0627\u062F\u0622\u0648\u0631\u06CC \u06A9\u0648\u062A\u0627\u0647\u06CC \u0628\u0631\u0627\u06CC \u067E\u0633\u0646\u062F\u06CC\u062F\u0646\u060C \u0627\u0634\u062A\u0631\u0627\u06A9 \u06CC\u0627 \u062F\u0646\u0628\u0627\u0644 \u06A9\u0631\u062F\u0646 \u062F\u0631 \u0645\u06CC\u0627\u0646\u0647 \u0645\u062D\u062A\u0648\u0627. \u0627\u06AF\u0631 \u0637\u0648\u0644\u0627\u0646\u06CC \u0628\u0627\u0634\u062F \u06CC\u0627 \u062F\u0631\u0628\u0627\u0631\u0647 \u0645\u0648\u0636\u0648\u0639\u06CC \u062E\u0627\u0635\u060C \u0628\u0627\u06CC\u062F \u062F\u0631 \u062F\u0633\u062A\u0647 \u062A\u0628\u0644\u06CC\u063A\u0627\u062A \u0634\u062E\u0635\u06CC \u0642\u0631\u0627\u0631 \u06AF\u06CC\u0631\u062F."\n                },\n                "intro": {\n                    "name": "\u0645\u06CC\u0627\u0646\u200C\u067E\u0631\u062F\u0647/\u0627\u0646\u06CC\u0645\u06CC\u0634\u0646 \u0622\u063A\u0627\u0632\u06CC\u0646",\n                    "description": "\u0641\u0627\u0635\u0644\u0647\u200C\u0627\u06CC \u0628\u062F\u0648\u0646 \u0645\u062D\u062A\u0648\u0627\u06CC \u0627\u0635\u0644\u06CC\u061B \u0645\u0627\u0646\u0646\u062F \u0645\u06A9\u062B\u060C \u062A\u0635\u0648\u06CC\u0631 \u062B\u0627\u0628\u062A \u06CC\u0627 \u0627\u0646\u06CC\u0645\u06CC\u0634\u0646 \u062A\u06A9\u0631\u0627\u0631\u0634\u0648\u0646\u062F\u0647. \u0646\u0628\u0627\u06CC\u062F \u0628\u0631\u0627\u06CC \u0628\u062E\u0634\u200C\u0647\u0627\u06CC \u0627\u0646\u062A\u0642\u0627\u0644\u06CC \u062D\u0627\u0648\u06CC \u0627\u0637\u0644\u0627\u0639\u0627\u062A \u0627\u0633\u062A\u0641\u0627\u062F\u0647 \u0634\u0648\u062F."\n                },\n                "outro": {\n                    "name": "\u06A9\u0627\u0631\u062A\u200C\u0647\u0627\u06CC \u067E\u0627\u06CC\u0627\u0646\u06CC/\u062A\u06CC\u062A\u0631\u0627\u0698",\n                    "description": "\u062A\u06CC\u062A\u0631\u0627\u0698 \u06CC\u0627 \u0632\u0645\u0627\u0646 \u0646\u0645\u0627\u06CC\u0634 \u06A9\u0627\u0631\u062A\u200C\u0647\u0627\u06CC \u067E\u0627\u06CC\u0627\u0646\u06CC YouTube. \u0628\u0631\u0627\u06CC \u0646\u062A\u06CC\u062C\u0647\u200C\u06AF\u06CC\u0631\u06CC\u200C\u0647\u0627\u06CC \u062D\u0627\u0648\u06CC \u0627\u0637\u0644\u0627\u0639\u0627\u062A \u06A9\u0627\u0631\u0628\u0631\u062F \u0646\u062F\u0627\u0631\u062F."\n                },\n                "preview": {\n                    "name": "\u067E\u06CC\u0634\u200C\u0646\u0645\u0627\u06CC\u0634/\u0645\u0631\u0648\u0631 \u062E\u0644\u0627\u0635\u0647",\n                    "description": "\u0645\u062C\u0645\u0648\u0639\u0647\u200C\u0627\u06CC \u0627\u0632 \u06A9\u0644\u06CC\u067E\u200C\u0647\u0627 \u06A9\u0647 \u0631\u0648\u06CC\u062F\u0627\u062F\u0647\u0627\u06CC \u067E\u06CC\u0634\u200C\u0631\u0648 \u062F\u0631 \u0627\u06CC\u0646 \u0648\u06CC\u062F\u06CC\u0648 \u06CC\u0627 \u0633\u0627\u06CC\u0631 \u0648\u06CC\u062F\u06CC\u0648\u0647\u0627\u06CC \u0645\u062C\u0645\u0648\u0639\u0647 \u0631\u0627 \u0646\u0634\u0627\u0646 \u0645\u06CC\u200C\u062F\u0647\u062F \u0648 \u062A\u0645\u0627\u0645 \u0627\u0637\u0644\u0627\u0639\u0627\u062A \u0622\u0646 \u0628\u0639\u062F\u0627\u064B \u062F\u0631 \u0648\u06CC\u062F\u06CC\u0648 \u062A\u06A9\u0631\u0627\u0631 \u0645\u06CC\u200C\u0634\u0648\u062F."\n                },\n                "hook": {\n                    "name": "\u0642\u0644\u0627\u0628/\u0627\u062D\u0648\u0627\u0644\u200C\u067E\u0631\u0633\u06CC",\n                    "description": "\u067E\u06CC\u0634\u200C\u0646\u0645\u0627\u06CC\u0634\u200C\u0647\u0627\u06CC \u06AF\u0641\u062A\u0627\u0631\u06CC \u0628\u0631\u0627\u06CC \u0648\u06CC\u062F\u06CC\u0648\u06CC \u067E\u06CC\u0634\u200C\u0631\u0648\u060C \u0633\u0644\u0627\u0645 \u0648 \u0627\u062D\u0648\u0627\u0644\u200C\u067E\u0631\u0633\u06CC \u0648 \u062E\u062F\u0627\u062D\u0627\u0641\u0638\u06CC. \u0646\u0628\u0627\u06CC\u062F \u0646\u062A\u06CC\u062C\u0647\u200C\u06AF\u06CC\u0631\u06CC\u200C\u0647\u0627\u06CC \u062D\u0627\u0648\u06CC \u0627\u0637\u0644\u0627\u0639\u0627\u062A \u0631\u0627 \u0631\u062F \u06A9\u0646\u062F."\n                },\n                "filler": {\n                    "name": "\u0645\u0637\u0627\u0644\u0628 \u062D\u0627\u0634\u06CC\u0647\u200C\u0627\u06CC/\u0634\u0648\u062E\u06CC\u200C\u0647\u0627",\n                    "description": "\u0635\u062D\u0646\u0647\u200C\u0647\u0627\u06CC \u062D\u0627\u0634\u06CC\u0647\u200C\u0627\u06CC \u06CC\u0627 \u0634\u0648\u062E\u06CC\u200C\u0647\u0627\u06CC\u06CC \u06A9\u0647 \u0628\u0631\u0627\u06CC \u062F\u0631\u06A9 \u0645\u062D\u062A\u0648\u0627\u06CC \u0627\u0635\u0644\u06CC \u0648\u06CC\u062F\u06CC\u0648 \u0636\u0631\u0648\u0631\u06CC \u0646\u06CC\u0633\u062A\u0646\u062F. \u0646\u0628\u0627\u06CC\u062F \u0634\u0627\u0645\u0644 \u0628\u062E\u0634\u200C\u0647\u0627\u06CC \u0632\u0645\u06CC\u0646\u0647\u200C\u0633\u0627\u0632 \u06CC\u0627 \u062C\u0632\u0626\u06CC\u0627\u062A \u067E\u0633\u200C\u0632\u0645\u06CC\u0646\u0647 \u0628\u0627\u0634\u062F. \u0627\u06CC\u0646 \u06CC\u06A9 \u062F\u0633\u062A\u0647\u200C\u0628\u0646\u062F\u06CC \u0628\u0633\u06CC\u0627\u0631 \u0633\u062E\u062A\u200C\u06AF\u06CC\u0631\u0627\u0646\u0647 \u0627\u0633\u062A \u0648 \u0628\u0631\u0627\u06CC \u0632\u0645\u0627\u0646\u06CC \u0637\u0631\u0627\u062D\u06CC \u0634\u062F\u0647 \u06A9\u0647 \u062D\u0648\u0635\u0644\u0647 \xAB\u0633\u0631\u06AF\u0631\u0645\u06CC\xBB \u0631\u0627 \u0646\u062F\u0627\u0631\u06CC\u062F."\n                }\n            }\n        },\n        "dearrow": {\n            "title": "DeArrow",\n            "description": "\u0639\u0646\u0648\u0627\u0646\u200C\u0647\u0627 \u0648 \u062A\u0635\u0648\u06CC\u0631\u0647\u0627\u06CC \u0628\u0646\u062F\u0627\u0646\u06AF\u0634\u062A\u06CC \u0631\u0627 \u0628\u0627 \u0646\u0633\u062E\u0647\u200C\u0647\u0627\u06CC \u062F\u0642\u06CC\u0642\u200C\u062A\u0631 \u0648 \u06A9\u0645\u062A\u0631 \u0627\u063A\u0631\u0627\u0642\u200C\u0622\u0645\u06CC\u0632 \u0627\u0632 \u06CC\u06A9 \u067E\u0627\u06CC\u06AF\u0627\u0647\u200C\u062F\u0627\u062F\u0647 \u062C\u0645\u0639\u200C\u0633\u067E\u0627\u0631\u06CC \u0639\u0645\u0648\u0645\u06CC \u062C\u0627\u06CC\u06AF\u0632\u06CC\u0646 \u0645\u06CC\u200C\u06A9\u0646\u062F. \u0633\u0631\u0639\u062A \u0628\u0627\u0631\u06AF\u06CC\u0631\u06CC \u0631\u0627 \u06A9\u0627\u0647\u0634 \u0645\u06CC\u200C\u062F\u0647\u062F \u0632\u06CC\u0631\u0627 \u0628\u0627\u06CC\u062F \u0627\u0637\u0644\u0627\u0639\u0627\u062A \u0631\u0627 \u0627\u0632 \u0633\u0631\u0648\u0631 \u062F\u0631\u06CC\u0627\u0641\u062A \u06A9\u0646\u062F"\n        },\n        "dislikes": {\n            "title": "\u0628\u0627\u0632\u06AF\u0631\u062F\u0627\u0646\u062F\u0646 \u062F\u06CC\u0633\u200C\u0644\u0627\u06CC\u06A9\u200C\u0647\u0627",\n            "description": "\u0627\u0632 \u062F\u0627\u062F\u0647\u200C\u0647\u0627\u06CC \u0639\u0645\u0648\u0645\u06CC Return YouTube Dislike API (returnyoutubedislike.com) \u0628\u0631\u0627\u06CC \u0646\u0645\u0627\u06CC\u0634 \u062A\u0639\u062F\u0627\u062F \u062A\u0642\u0631\u06CC\u0628\u06CC \u062F\u06CC\u0633\u200C\u0644\u0627\u06CC\u06A9\u200C\u0647\u0627 \u0627\u0633\u062A\u0641\u0627\u062F\u0647 \u0645\u06CC\u200C\u06A9\u0646\u062F. \u0633\u0631\u0639\u062A \u0628\u0627\u0631\u06AF\u06CC\u0631\u06CC \u0648\u06CC\u062F\u06CC\u0648 \u0631\u0627 \u06A9\u0627\u0647\u0634 \u0645\u06CC\u200C\u062F\u0647\u062F \u0632\u06CC\u0631\u0627 \u0628\u0627\u06CC\u062F \u0627\u0637\u0644\u0627\u0639\u0627\u062A \u0631\u0627 \u0627\u0632 \u0633\u0631\u0648\u0631 \u062F\u0631\u06CC\u0627\u0641\u062A \u06A9\u0646\u062F"\n        },\n        "remove_super_resolution": {\n            "title": "\u062D\u0630\u0641 \u0648\u0636\u0648\u062D \u0641\u0648\u0642\u200C\u0627\u0644\u0639\u0627\u062F\u0647",\n            "description": "\u062D\u0630\u0641 \u06A9\u06CC\u0641\u06CC\u062A\u200C\u0647\u0627\u06CC \xAB\u0648\u0636\u0648\u062D \u0641\u0648\u0642\u200C\u0627\u0644\u0639\u0627\u062F\u0647\xBB (\u0627\u0631\u062A\u0642\u0627\u06CC\u0627\u0641\u062A\u0647 \u0628\u0627 \u0647\u0648\u0634 \u0645\u0635\u0646\u0648\u0639\u06CC) \u0627\u0632 \u0648\u06CC\u062F\u06CC\u0648\u0647\u0627\u06CC \u0628\u0627 \u06A9\u06CC\u0641\u06CC\u062A \u067E\u0627\u06CC\u06CC\u0646"\n        },\n        "hide_shorts": {\n            "title": "\u067E\u0646\u0647\u0627\u0646 \u06A9\u0631\u062F\u0646 Shorts",\n            "description": "\u0627\u0632 \u0635\u0641\u062D\u0647 \u0627\u0635\u0644\u06CC\u060C YouTube Shorts \u0631\u0627 \u067E\u0646\u0647\u0627\u0646 \u0645\u06CC\u200C\u06A9\u0646\u062F"\n        },\n        "guide_tabs": {\n            "title": "\u0628\u0631\u06AF\u0647\u200C\u0647\u0627\u06CC \u0631\u0627\u0647\u0646\u0645\u0627",\n            "description": "\u0627\u0646\u062A\u062E\u0627\u0628 \u06A9\u0646\u06CC\u062F \u06A9\u062F\u0627\u0645 \u0628\u0631\u06AF\u0647\u200C\u0647\u0627\u06CC \u0631\u0627\u0647\u0646\u0645\u0627\u06CC \u0646\u0648\u0627\u0631 \u06A9\u0646\u0627\u0631\u06CC \u0646\u0645\u0627\u06CC\u0634 \u062F\u0627\u062F\u0647 \u0634\u0648\u0646\u062F. \u0628\u0631\u0627\u06CC \u067E\u0646\u0647\u0627\u0646 \u06A9\u0631\u062F\u0646 \u0647\u0631 \u0628\u0631\u06AF\u0647\u060C \u0622\u0646 \u0631\u0627 \u062E\u0627\u0645\u0648\u0634 \u06A9\u0646\u06CC\u062F. \u067E\u0633 \u0627\u0632 \u0627\u0639\u0645\u0627\u0644 \u062A\u063A\u06CC\u06CC\u0631\u0627\u062A\u060C \u0628\u0631\u0646\u0627\u0645\u0647 \u0631\u0627 \u0645\u062C\u062F\u062F\u0627\u064B \u0631\u0627\u0647\u200C\u0627\u0646\u062F\u0627\u0632\u06CC \u06A9\u0646\u06CC\u062F",\n            "tabs": {\n                "search": "\u062C\u0633\u062A\u062C\u0648",\n                "shorts": "Shorts",\n                "subscriptions": "\u0627\u0634\u062A\u0631\u0627\u06A9\u200C\u0647\u0627",\n                "library": "\u06A9\u062A\u0627\u0628\u062E\u0627\u0646\u0647",\n                "gaming": "\u0628\u0627\u0632\u06CC",\n                "music": "\u0645\u0648\u0633\u06CC\u0642\u06CC",\n                "news": "\u0627\u062E\u0628\u0627\u0631",\n                "podcasts": "\u067E\u0627\u062F\u06A9\u0633\u062A\u200C\u0647\u0627",\n                "movies_and_tv": "\u0641\u06CC\u0644\u0645 \u0648 \u062A\u0644\u0648\u06CC\u0632\u06CC\u0648\u0646",\n                "live": "\u067E\u062E\u0634 \u0632\u0646\u062F\u0647",\n                "sports": "\u0648\u0631\u0632\u0634"\n            }\n        },\n        "unlock_resolution": {\n            "title": "\u0628\u0627\u0632 \u06A9\u0631\u062F\u0646 \u0642\u0641\u0644 \u0648\u0636\u0648\u062D",\n            "description": "\u0645\u062D\u062F\u0648\u062F\u06CC\u062A \u0648\u0636\u0648\u062D \u0646\u0645\u0627\u06CC\u0634\u06AF\u0631 \u0631\u0627 \u062D\u0630\u0641 \u0645\u06CC\u200C\u06A9\u0646\u062F \u0648 \u0628\u0647 \u0634\u0645\u0627 \u0627\u0645\u06A9\u0627\u0646 \u0645\u06CC\u200C\u062F\u0647\u062F \u0648\u06CC\u062F\u06CC\u0648\u0647\u0627 \u0631\u0627 \u0628\u0627 \u0647\u0631 \u0648\u0636\u0648\u062D\u06CC \u062A\u0645\u0627\u0634\u0627 \u06A9\u0646\u06CC\u062F. \u067E\u0633 \u0627\u0632 \u062A\u063A\u06CC\u06CC\u0631 \u0648\u0636\u0639\u06CC\u062A\u060C \u0628\u0631\u0646\u0627\u0645\u0647 \u0631\u0627 \u0645\u062C\u062F\u062F\u0627\u064B \u0631\u0627\u0647\u200C\u0627\u0646\u062F\u0627\u0632\u06CC \u06A9\u0646\u06CC\u062F"\n        },\n        "h264ify": {\n            "title": "\u0641\u06CC\u0644\u062A\u0631 \u06A9\u0631\u062F\u0646 \u06A9\u062F\u06A9\u200C\u0647\u0627\u06CC \u0648\u06CC\u062F\u06CC\u0648",\n            "description": "\u06AF\u0632\u06CC\u0646\u0647\u200C\u0647\u0627\u06CC \u0641\u06CC\u0644\u062A\u0631 \u06A9\u062F\u06A9. \u067E\u0633 \u0627\u0632 \u062A\u063A\u06CC\u06CC\u0631 \u0648\u0636\u0639\u06CC\u062A \u0647\u0631 \u06CC\u06A9 \u0627\u0632 \u06AF\u0632\u06CC\u0646\u0647\u200C\u0647\u0627\u060C \u0628\u0631\u0646\u0627\u0645\u0647 \u0631\u0627 \u0645\u062C\u062F\u062F\u0627\u064B \u0631\u0627\u0647\u200C\u0627\u0646\u062F\u0627\u0632\u06CC \u06A9\u0646\u06CC\u062F.",\n            "enable_title": "\u0641\u0639\u0627\u0644\u200C\u0633\u0627\u0632\u06CC \u0641\u06CC\u0644\u062A\u0631 \u06A9\u062F\u06A9 (h264ify)",\n            "enable_description": "\u0641\u06CC\u0644\u062A\u0631 \u06A9\u062F\u06A9 \u0631\u0627 \u0641\u0639\u0627\u0644 \u0645\u06CC\u200C\u06A9\u0646\u062F. \u0627\u06CC\u0646 \u06A9\u0627\u0631 \u0645\u06CC\u200C\u062A\u0648\u0627\u0646\u062F \u0628\u0647 \u0628\u0647\u0628\u0648\u062F \u0639\u0645\u0644\u06A9\u0631\u062F \u0648 \u0637\u0648\u0644 \u0639\u0645\u0631 \u0628\u0627\u062A\u0631\u06CC \u062F\u0631 \u062F\u0633\u062A\u06AF\u0627\u0647\u200C\u0647\u0627\u06CC \u0636\u0639\u06CC\u0641\u200C\u062A\u0631 \u06A9\u0645\u06A9 \u06A9\u0646\u062F\u060C \u0627\u0645\u0627 \u0645\u0645\u06A9\u0646 \u0627\u0633\u062A \u0648\u0636\u0648\u062D\u200C\u0647\u0627\u06CC \u062F\u0631\u062F\u0633\u062A\u0631\u0633 \u0631\u0627 \u06A9\u0627\u0647\u0634 \u062F\u0647\u062F.",\n            "disable_codec_title": "\u063A\u06CC\u0631\u0641\u0639\u0627\u0644 \u06A9\u0631\u062F\u0646 {codec}",\n            "disable_codec_description": "\u0645\u0633\u062F\u0648\u062F \u06A9\u0631\u062F\u0646 \u062C\u0631\u06CC\u0627\u0646\u200C\u0647\u0627\u06CC {codec}.",\n            "codecs_link": "\u06A9\u062F\u06A9\u200C\u0647\u0627\u06CC \u0645\u0633\u062F\u0648\u062F\u0634\u062F\u0647"\n        },\n        "block_continue_watching": {\n            "title": "\u0645\u0633\u062F\u0648\u062F \u06A9\u0631\u062F\u0646 \u0627\u062F\u0627\u0645\u0647 \u062A\u0645\u0627\u0634\u0627",\n            "description": "\u067E\u06CC\u0627\u0645 \xAB\u0648\u06CC\u062F\u06CC\u0648 \u0645\u062A\u0648\u0642\u0641 \u0634\u062F. \u0645\u0627\u06CC\u0644 \u0628\u0647 \u0627\u062F\u0627\u0645\u0647 \u062A\u0645\u0627\u0634\u0627 \u0647\u0633\u062A\u06CC\u062F\u061F\xBB \u0631\u0627 \u06A9\u0647 \u067E\u0633 \u0627\u0632 \u0645\u062F\u062A\u06CC \u0639\u062F\u0645 \u0641\u0639\u0627\u0644\u06CC\u062A \u0638\u0627\u0647\u0631 \u0645\u06CC\u200C\u0634\u0648\u062F \u0645\u0633\u062F\u0648\u062F \u0645\u06CC\u200C\u06A9\u0646\u062F"\n        },\n        "hardware_decoding": {\n            "title": "\u0631\u0645\u0632\u06AF\u0634\u0627\u06CC\u06CC \u0633\u062E\u062A\u200C\u0627\u0641\u0632\u0627\u0631\u06CC",\n            "description": "\u062F\u0631 \u0635\u0648\u0631\u062A \u0627\u0645\u06A9\u0627\u0646\u060C \u0627\u0632 GPU \u0634\u0645\u0627 \u0628\u0631\u0627\u06CC \u0631\u0645\u0632\u06AF\u0634\u0627\u06CC\u06CC \u0648\u06CC\u062F\u06CC\u0648\u0647\u0627 \u0627\u0633\u062A\u0641\u0627\u062F\u0647 \u0645\u06CC\u200C\u06A9\u0646\u062F. \u063A\u06CC\u0631\u0641\u0639\u0627\u0644 \u06A9\u0631\u062F\u0646 \u0627\u06CC\u0646 \u06AF\u0632\u06CC\u0646\u0647 \u0645\u0645\u06A9\u0646 \u0627\u0633\u062A \u0645\u0634\u06A9\u0644\u0627\u062A \u067E\u062E\u0634 \u0631\u0627 \u0628\u0631\u0637\u0631\u0641 \u06A9\u0646\u062F\u060C \u0627\u0645\u0627 \u0628\u0633\u062A\u0647 \u0628\u0647 CPU \u0645\u06CC\u200C\u062A\u0648\u0627\u0646\u062F \u0628\u0627\u0639\u062B \u06A9\u0646\u062F\u06CC \u0634\u0648\u062F. \u067E\u0633 \u0627\u0632 \u062A\u063A\u06CC\u06CC\u0631 \u0648\u0636\u0639\u06CC\u062A\u060C \u0628\u0631\u0646\u0627\u0645\u0647 \u0631\u0627 \u0645\u062C\u062F\u062F\u0627\u064B \u0631\u0627\u0647\u200C\u0627\u0646\u062F\u0627\u0632\u06CC \u06A9\u0646\u06CC\u062F"\n        },\n        "wayland_hdr": {\n            "title": "Wayland HDR",\n            "description": "\u0642\u0627\u0628\u0644\u06CC\u062A HDR \u0631\u0627 \u0647\u0646\u06AF\u0627\u0645 \u0627\u0633\u062A\u0641\u0627\u062F\u0647 \u0627\u0632 Wayland \u0641\u0639\u0627\u0644 \u0645\u06CC\u200C\u06A9\u0646\u062F\u060C \u0627\u0645\u0627 \u0645\u06CC\u200C\u062A\u0648\u0627\u0646\u062F \u062F\u0631 \u067E\u0644\u062A\u0641\u0631\u0645\u200C\u0647\u0627\u06CC \u067E\u0634\u062A\u06CC\u0628\u0627\u0646\u06CC\u200C\u0646\u0634\u062F\u0647 \u0628\u0627\u0639\u062B \u06A9\u0645\u200C\u0631\u0646\u06AF \u0634\u062F\u0646 \u0631\u0646\u06AF\u200C\u0647\u0627 \u0634\u0648\u062F. \u067E\u0633 \u0627\u0632 \u062A\u063A\u06CC\u06CC\u0631 \u0648\u0636\u0639\u06CC\u062A\u060C \u0628\u0631\u0646\u0627\u0645\u0647 \u0631\u0627 \u0645\u062C\u062F\u062F\u0627\u064B \u0631\u0627\u0647\u200C\u0627\u0646\u062F\u0627\u0632\u06CC \u06A9\u0646\u06CC\u062F"\n        },\n        "low_memory_mode": {\n            "title": "\u062D\u0627\u0644\u062A \u062D\u0627\u0641\u0638\u0647 \u06A9\u0645",\n            "description": "\u0628\u0647 YouTube \u0627\u0639\u0644\u0627\u0645 \u0645\u06CC\u200C\u06A9\u0646\u062F \u062D\u0627\u0644\u062A \u062D\u0627\u0641\u0638\u0647 \u06A9\u0645 \u0631\u0627 \u0641\u0639\u0627\u0644 \u06A9\u0646\u062F\u061B \u0627\u06CC\u0646 \u06A9\u0627\u0631 \u0645\u0645\u06A9\u0646 \u0627\u0633\u062A \u0628\u0627 \u06A9\u0627\u0647\u0634 \u0628\u0631\u062E\u06CC \u062C\u0644\u0648\u0647\u200C\u0647\u0627\u06CC \u0628\u0635\u0631\u06CC\u060C \u0639\u0645\u0644\u06A9\u0631\u062F \u0631\u0627 \u062F\u0631 \u062F\u0633\u062A\u06AF\u0627\u0647\u200C\u0647\u0627\u06CC \u0636\u0639\u06CC\u0641\u200C\u062A\u0631 \u0628\u0647\u0628\u0648\u062F \u0628\u062E\u0634\u062F. \u067E\u0633 \u0627\u0632 \u062A\u063A\u06CC\u06CC\u0631 \u0648\u0636\u0639\u06CC\u062A\u060C \u0628\u0631\u0646\u0627\u0645\u0647 \u0631\u0627 \u0645\u062C\u062F\u062F\u0627\u064B \u0631\u0627\u0647\u200C\u0627\u0646\u062F\u0627\u0632\u06CC \u06A9\u0646\u06CC\u062F"\n        },\n        "fullscreen": {\n            "title": "\u062A\u0645\u0627\u0645\u200C\u0635\u0641\u062D\u0647",\n            "description": "\u062D\u0627\u0644\u062A \u062A\u0645\u0627\u0645\u200C\u0635\u0641\u062D\u0647 \u0631\u0627 \u0641\u0639\u0627\u0644 \u0645\u06CC\u200C\u06A9\u0646\u062F \u0648 \u0628\u0627\u0639\u062B \u0645\u06CC\u200C\u0634\u0648\u062F VacuumTube \u0647\u0645\u06CC\u0634\u0647 \u0628\u0647\u200C\u0635\u0648\u0631\u062A \u062A\u0645\u0627\u0645\u200C\u0635\u0641\u062D\u0647 \u0627\u062C\u0631\u0627 \u0634\u0648\u062F"\n        },\n        "no_window_decorations": {\n            "title": "\u0628\u062F\u0648\u0646 \u062A\u0632\u0626\u06CC\u0646\u0627\u062A \u067E\u0646\u062C\u0631\u0647",\n            "description": "\u062A\u0632\u0626\u06CC\u0646\u0627\u062A \u067E\u0646\u062C\u0631\u0647 \u0634\u0627\u0645\u0644 \u0646\u0648\u0627\u0631 \u0639\u0646\u0648\u0627\u0646 \u0648 \u062D\u0627\u0634\u06CC\u0647 \u067E\u0646\u062C\u0631\u0647 \u0631\u0627 \u063A\u06CC\u0631\u0641\u0639\u0627\u0644 \u0645\u06CC\u200C\u06A9\u0646\u062F. \u067E\u0633 \u0627\u0632 \u062A\u063A\u06CC\u06CC\u0631 \u0648\u0636\u0639\u06CC\u062A\u060C \u0628\u0631\u0646\u0627\u0645\u0647 \u0631\u0627 \u0645\u062C\u062F\u062F\u0627\u064B \u0631\u0627\u0647\u200C\u0627\u0646\u062F\u0627\u0632\u06CC \u06A9\u0646\u06CC\u062F"\n        },\n        "keep_on_top": {\n            "title": "\u0647\u0645\u06CC\u0634\u0647 \u062F\u0631 \u0628\u0627\u0644\u0627",\n            "description": "\u0642\u0627\u0628\u0644\u06CC\u062A \u0647\u0645\u06CC\u0634\u0647 \u062F\u0631 \u0628\u0627\u0644\u0627 \u0631\u0627 \u0641\u0639\u0627\u0644 \u0645\u06CC\u200C\u06A9\u0646\u062F \u0648 \u0628\u0627\u0639\u062B \u0645\u06CC\u200C\u0634\u0648\u062F \u067E\u0646\u062C\u0631\u0647 VacuumTube \u0628\u0627\u0644\u0627\u062A\u0631 \u0627\u0632 \u0647\u0645\u0647 \u067E\u0646\u062C\u0631\u0647\u200C\u0647\u0627\u06CC \u062F\u06CC\u06AF\u0631 \u0646\u0645\u0627\u06CC\u0634 \u062F\u0627\u062F\u0647 \u0634\u0648\u062F"\n        },\n        "auto_update": {\n            "title": "\u0628\u0647\u200C\u0631\u0648\u0632\u0631\u0633\u0627\u0646\u06CC\u200C\u0647\u0627\u06CC \u062E\u0648\u062F\u06A9\u0627\u0631",\n            "description": "\u0646\u0633\u062E\u0647\u200C\u0647\u0627\u06CC \u062C\u062F\u06CC\u062F \u0631\u0627 \u062F\u0631 \u067E\u0633\u200C\u0632\u0645\u06CC\u0646\u0647 \u062F\u0627\u0646\u0644\u0648\u062F \u0645\u06CC\u200C\u06A9\u0646\u062F \u0648 \u0647\u0646\u06AF\u0627\u0645 \u0628\u0633\u062A\u0647 \u0634\u062F\u0646 VacuumTube \u0622\u0646\u200C\u0647\u0627 \u0631\u0627 \u0646\u0635\u0628 \u0645\u06CC\u200C\u0646\u0645\u0627\u06CC\u062F. \u062F\u0631 \u0635\u0648\u0631\u062A \u062E\u0627\u0645\u0648\u0634 \u0628\u0648\u062F\u0646\u060C \u0647\u0645\u0686\u0646\u0627\u0646 \u0645\u06CC\u200C\u062A\u0648\u0627\u0646\u06CC\u062F \u0627\u0632 \u0635\u0641\u062D\u0647 \xAB\u062F\u0631\u0628\u0627\u0631\u0647\xBB\u060C \u0628\u0647\u200C\u0631\u0648\u0632\u0631\u0633\u0627\u0646\u06CC\u200C\u0647\u0627 \u0631\u0627 \u0628\u0631\u0631\u0633\u06CC \u06A9\u0646\u06CC\u062F"\n        },\n        "pause_on_blur": {\n            "title": "\u062A\u0648\u0642\u0641 \u0647\u0646\u06AF\u0627\u0645 \u062E\u0631\u0648\u062C \u0627\u0632 \u067E\u0646\u062C\u0631\u0647",\n            "description": "\u0647\u0646\u06AF\u0627\u0645\u06CC \u06A9\u0647 \u062A\u0645\u0631\u06A9\u0632 \u0627\u0632 \u067E\u0646\u062C\u0631\u0647 VacuumTube \u0628\u0631\u062F\u0627\u0634\u062A\u0647 \u0645\u06CC\u200C\u0634\u0648\u062F (\u0645\u0627\u0646\u0646\u062F \u062C\u0627\u0628\u0647\u200C\u062C\u0627\u06CC\u06CC \u0645\u06CC\u0627\u0646 \u0628\u0631\u0646\u0627\u0645\u0647\u200C\u0647\u0627 \u06CC\u0627 \u06A9\u0645\u06CC\u0646\u0647\u200C\u06A9\u0631\u062F\u0646 \u067E\u0646\u062C\u0631\u0647)\u060C \u067E\u062E\u0634 \u0648\u06CC\u062F\u06CC\u0648\u06CC \u0641\u0639\u0644\u06CC \u0645\u062A\u0648\u0642\u0641 \u0645\u06CC\u200C\u0634\u0648\u062F"\n        },\n        "features": {\n            "title": "\u0642\u0627\u0628\u0644\u06CC\u062A\u200C\u0647\u0627",\n            "notice_title": "\u0628\u062E\u0634\u06CC \u0627\u0632 \u062A\u062C\u0631\u0628\u0647 \u067E\u06CC\u0634\u200C\u0641\u0631\u0636 \u0646\u06CC\u0633\u062A",\n            "notice_description": "\u0627\u06CC\u0646 \u0642\u0627\u0628\u0644\u06CC\u062A\u200C\u0647\u0627\u06CC \u0627\u062E\u062A\u06CC\u0627\u0631\u06CC\u060C VacuumTube \u0631\u0627 \u0641\u0631\u0627\u062A\u0631 \u0627\u0632 \u062A\u062C\u0631\u0628\u0647 \u0627\u0633\u062A\u0627\u0646\u062F\u0627\u0631\u062F YouTube \u062F\u0631 \u062A\u0644\u0648\u06CC\u0632\u06CC\u0648\u0646 \u06AF\u0633\u062A\u0631\u0634 \u0645\u06CC\u200C\u062F\u0647\u0646\u062F. \u0628\u0627 \u0641\u0639\u0627\u0644\u200C\u0633\u0627\u0632\u06CC \u0622\u0646\u200C\u0647\u0627 \u062F\u0631 \u0632\u06CC\u0631\u060C \u0645\u06CC\u200C\u067E\u0630\u06CC\u0631\u06CC\u062F \u06A9\u0647 \u0627\u06CC\u0646 \u0642\u0627\u0628\u0644\u06CC\u062A\u200C\u0647\u0627 \u0628\u0647 \u0631\u0641\u062A\u0627\u0631 \u062F\u0627\u062E\u0644\u06CC YouTube \u0648\u0627\u0628\u0633\u062A\u0647 \u0647\u0633\u062A\u0646\u062F \u0648 \u0645\u0645\u06A9\u0646 \u0627\u0633\u062A \u0628\u062F\u0648\u0646 \u0627\u0637\u0644\u0627\u0639 \u0642\u0628\u0644\u06CC \u062A\u063A\u06CC\u06CC\u0631 \u06A9\u0646\u0646\u062F \u06CC\u0627 \u0627\u0632 \u06A9\u0627\u0631 \u0628\u06CC\u0641\u062A\u0646\u062F.",\n            "enable_title": "\u0641\u0639\u0627\u0644\u200C\u0633\u0627\u0632\u06CC \u0642\u0627\u0628\u0644\u06CC\u062A\u200C\u0647\u0627\u06CC \u0627\u062E\u062A\u06CC\u0627\u0631\u06CC",\n            "enable_description": "\u0645\u062A\u0648\u062C\u0647 \u0647\u0633\u062A\u0645 \u06A9\u0647 \u0627\u06CC\u0646 \u0642\u0627\u0628\u0644\u06CC\u062A\u200C\u0647\u0627 \u062E\u0627\u0631\u062C \u0627\u0632 \u062A\u062C\u0631\u0628\u0647 \u067E\u06CC\u0634\u200C\u0641\u0631\u0636 VacuumTube \u0647\u0633\u062A\u0646\u062F \u0648 \u0645\u0645\u06A9\u0646 \u0627\u0633\u062A \u062A\u063A\u06CC\u06CC\u0631 \u06A9\u0646\u0646\u062F \u06CC\u0627 \u0627\u0632 \u06A9\u0627\u0631 \u0628\u06CC\u0641\u062A\u0646\u062F.",\n            "settings": {\n                "music_mode": {\n                    "title": "\u062D\u0627\u0644\u062A \u0645\u0648\u0633\u06CC\u0642\u06CC",\n                    "description": "\u06A9\u0644\u06CC\u062F \u062A\u063A\u06CC\u06CC\u0631 \xAB\u062D\u0627\u0644\u062A \u0645\u0648\u0633\u06CC\u0642\u06CC\xBB \u0631\u0627 \u0628\u0631\u0627\u06CC \u067E\u062E\u0634 \u0641\u0642\u0637\u200C\u0635\u0648\u062A\u06CC \u0628\u0627 \u0627\u0633\u062A\u0641\u0627\u062F\u0647 \u0627\u0632 \u0646\u0645\u0627\u06CC \u0645\u0648\u0633\u06CC\u0642\u06CC \u0627\u062E\u062A\u0635\u0627\u0635\u06CC YouTube \u0628\u0647 \u0645\u0646\u0648\u06CC \u062A\u0646\u0638\u06CC\u0645\u0627\u062A \u0648\u06CC\u062F\u06CC\u0648 \u0627\u0636\u0627\u0641\u0647 \u0645\u06CC\u200C\u06A9\u0646\u062F."\n                },\n                "remaining_time": {\n                    "title": "\u0646\u0645\u0627\u06CC\u0634 \u0632\u0645\u0627\u0646 \u0628\u0627\u0642\u06CC\u200C\u0645\u0627\u0646\u062F\u0647",\n                    "description": "\u0628\u0647\u200C\u062C\u0627\u06CC \u0632\u0645\u0627\u0646 \u0633\u067E\u0631\u06CC\u200C\u0634\u062F\u0647\u060C \u0632\u0645\u0627\u0646 \u0628\u0627\u0642\u06CC\u200C\u0645\u0627\u0646\u062F\u0647 \u0648\u06CC\u062F\u06CC\u0648 \u0631\u0627 \u062F\u0631 \u0646\u0645\u0627\u06CC \u067E\u062E\u0634\u200C\u06A9\u0646\u0646\u062F\u0647 \u0646\u0645\u0627\u06CC\u0634 \u0645\u06CC\u200C\u062F\u0647\u062F. \u0633\u0631\u0639\u062A \u067E\u062E\u0634 \u0641\u0639\u0644\u06CC \u0631\u0627 \u0646\u06CC\u0632 \u0644\u062D\u0627\u0638 \u0645\u06CC\u200C\u06A9\u0646\u062F. \u0628\u0631\u0627\u06CC \u062A\u063A\u06CC\u06CC\u0631 \u0648\u0636\u0639\u06CC\u062A \u062F\u0631 \u0648\u06CC\u062F\u06CC\u0648\u06CC \u0641\u0639\u0644\u06CC\u060C \u06A9\u0644\u06CC\u062F R \u0631\u0627 \u0641\u0634\u0627\u0631 \u062F\u0647\u06CC\u062F."\n                },\n                "ultrawide": {\n                    "title": "\u067E\u0634\u062A\u06CC\u0628\u0627\u0646\u06CC \u0627\u0632 \u0641\u0648\u0642\u200C\u0639\u0631\u06CC\u0636",\n                    "description": "\u062F\u0631 \u062D\u0627\u0644\u062A \u062A\u0645\u0627\u0645\u200C\u0635\u0641\u062D\u0647\u060C \u0631\u0627\u0628\u0637 \u06A9\u0627\u0631\u0628\u0631\u06CC \u0631\u0627 \u0628\u0631\u0627\u06CC \u067E\u0631 \u06A9\u0631\u062F\u0646 \u0646\u0645\u0627\u06CC\u0634\u06AF\u0631\u0647\u0627\u06CC \u0641\u0648\u0642\u200C\u0639\u0631\u06CC\u0636 \u06AF\u0633\u062A\u0631\u0634 \u0645\u06CC\u200C\u062F\u0647\u062F. \u0645\u0645\u06A9\u0646 \u0627\u0633\u062A \u062F\u0631 \u0628\u0631\u062E\u06CC \u0645\u0648\u0627\u0631\u062F \u0628\u0627 \u0627\u0634\u06A9\u0627\u0644 \u0648 \u0628\u0647 \u0634\u06A9\u0644 \u063A\u06CC\u0631\u0639\u0627\u062F\u06CC \u062F\u06CC\u062F\u0647 \u0634\u0648\u062F."\n                }\n            }\n        },\n        "userstyles": {\n            "title": "\u200FCSS \u0633\u0641\u0627\u0631\u0634\u06CC (Userstyles)",\n            "description": "\u0628\u0631\u0627\u06CC \u0634\u062E\u0635\u06CC\u200C\u0633\u0627\u0632\u06CC \u0631\u0627\u0628\u0637 \u06A9\u0627\u0631\u0628\u0631\u06CC\u060C \u0641\u0627\u06CC\u0644\u200C\u0647\u0627\u06CC .css \u0631\u0627 \u062F\u0631 \u067E\u0648\u0634\u0647 userstyles \u0642\u0631\u0627\u0631 \u062F\u0647\u06CC\u062F. \u062A\u063A\u06CC\u06CC\u0631\u0627\u062A \u0628\u0647\u200C\u0637\u0648\u0631 \u062E\u0648\u062F\u06A9\u0627\u0631 \u0627\u0639\u0645\u0627\u0644 \u0645\u06CC\u200C\u0634\u0648\u0646\u062F",\n            "warn_empty": "\u0647\u06CC\u0686 \u0627\u0633\u062A\u0627\u06CC\u0644 \u06A9\u0627\u0631\u0628\u0631\u06CC \u06CC\u0627\u0641\u062A \u0646\u0634\u062F",\n            "open_folder": "\u0628\u0627\u0632 \u06A9\u0631\u062F\u0646 \u067E\u0648\u0634\u0647 Userstyles",\n            "enable": "\u0641\u0639\u0627\u0644\u200C\u0633\u0627\u0632\u06CC Userstyles",\n            "failed_to_load": "\u0628\u0627\u0631\u06AF\u06CC\u0631\u06CC \u0627\u0633\u062A\u0627\u06CC\u0644\u200C\u0647\u0627\u06CC \u06A9\u0627\u0631\u0628\u0631\u06CC \u0646\u0627\u0645\u0648\u0641\u0642 \u0628\u0648\u062F",\n            "manage_link": "\u0645\u062F\u06CC\u0631\u06CC\u062A \u0627\u0633\u062A\u0627\u06CC\u0644\u200C\u0647\u0627\u06CC \u06A9\u0627\u0631\u0628\u0631\u06CC"\n        },\n        "touch_overlay": {\n            "title": "\u06A9\u0646\u062A\u0631\u0644\u200C\u0647\u0627\u06CC \u0644\u0645\u0633\u06CC \u0631\u0648\u06CC \u0635\u0641\u062D\u0647",\n            "description": "\u062F\u0631 \u0635\u0648\u0631\u062A \u0634\u0646\u0627\u0633\u0627\u06CC\u06CC \u0648\u0631\u0648\u062F\u06CC \u0644\u0645\u0633\u06CC\u060C \u06A9\u0646\u062A\u0631\u0644\u200C\u0647\u0627\u06CC \u0644\u0645\u0633\u06CC \u0631\u0648\u06CC \u0635\u0641\u062D\u0647 \u0631\u0627 \u0628\u0631\u0627\u06CC \u067E\u06CC\u0645\u0627\u06CC\u0634 \u0622\u0633\u0627\u0646\u200C\u062A\u0631 \u0641\u0639\u0627\u0644 \u0645\u06CC\u200C\u06A9\u0646\u062F"\n        },\n        "controller_support": {\n            "title": "\u067E\u0634\u062A\u06CC\u0628\u0627\u0646\u06CC \u0627\u0632 \u062F\u0633\u062A\u0647",\n            "description": "\u067E\u0634\u062A\u06CC\u0628\u0627\u0646\u06CC \u0627\u0632 \u062F\u0633\u062A\u0647\u200C\u0647\u0627\u06CC \u0628\u0627\u0632\u06CC\u060C \u0634\u0627\u0645\u0644 \u06A9\u0646\u062A\u0631\u0644\u200C\u0647\u0627\u06CC \u067E\u06CC\u0645\u0627\u06CC\u0634 \u0648 \u067E\u062E\u0634 \u0648\u06CC\u062F\u06CC\u0648 \u0631\u0627 \u0641\u0639\u0627\u0644 \u0645\u06CC\u200C\u06A9\u0646\u062F. \u0628\u0647 \u06CC\u06A9 \u062F\u0633\u062A\u0647 \u062E\u0627\u0631\u062C\u06CC\u060C \u0645\u0627\u0646\u0646\u062F \u062F\u0633\u062A\u0647 Xbox \u0646\u06CC\u0627\u0632 \u062F\u0627\u0631\u062F"\n        },\n        "device_discoverability": {\n            "title": "\u0642\u0627\u0628\u0644\u06CC\u062A \u0634\u0646\u0627\u0633\u0627\u06CC\u06CC \u062F\u0633\u062A\u06AF\u0627\u0647",\n            "description": "\u0627\u0645\u06A9\u0627\u0646 \u0634\u0646\u0627\u0633\u0627\u06CC\u06CC VacuumTube \u0631\u0627 \u062A\u0648\u0633\u0637 \u0628\u0631\u0646\u0627\u0645\u0647 \u062A\u0644\u0641\u0646 \u0647\u0645\u0631\u0627\u0647 YouTube \u062F\u0631 \u062F\u0633\u062A\u06AF\u0627\u0647\u200C\u0647\u0627\u06CC \u0645\u062A\u0635\u0644 \u0628\u0647 \u0647\u0645\u0627\u0646 \u0634\u0628\u06A9\u0647 \u0645\u062D\u0644\u06CC \u0641\u0631\u0627\u0647\u0645 \u0645\u06CC\u200C\u06A9\u0646\u062F. \u067E\u0633 \u0627\u0632 \u062A\u063A\u06CC\u06CC\u0631 \u0648\u0636\u0639\u06CC\u062A\u060C \u0628\u0631\u0646\u0627\u0645\u0647 \u0631\u0627 \u0645\u062C\u062F\u062F\u0627\u064B \u0631\u0627\u0647\u200C\u0627\u0646\u062F\u0627\u0632\u06CC \u06A9\u0646\u06CC\u062F"\n        },\n        "mac_permissions": {\n            "title": "\u0645\u062C\u0648\u0632\u0647\u0627",\n            "description": "\u0645\u062F\u06CC\u0631\u06CC\u062A \u0645\u062C\u0648\u0632\u0647\u0627\u06CC macOS \u0645\u0648\u0631\u062F \u0627\u0633\u062A\u0641\u0627\u062F\u0647 VacuumTube.",\n            "microphone_title": "\u0645\u06CC\u06A9\u0631\u0648\u0641\u0648\u0646",\n            "microphone_description": "\u0641\u0642\u0637 \u0647\u0646\u06AF\u0627\u0645 \u0634\u0631\u0648\u0639 \u062C\u0633\u062A\u062C\u0648\u06CC \u0635\u0648\u062A\u06CC YouTube \u0627\u0633\u062A\u0641\u0627\u062F\u0647 \u0645\u06CC\u200C\u0634\u0648\u062F.",\n            "status_label": "\u0648\u0636\u0639\u06CC\u062A",\n            "status_loading": "\u062F\u0631 \u062D\u0627\u0644 \u0628\u0631\u0631\u0633\u06CC \u0645\u062C\u0648\u0632 \u0645\u06CC\u06A9\u0631\u0648\u0641\u0648\u0646...",\n            "request_microphone": "\u062F\u0631\u062E\u0648\u0627\u0633\u062A \u062F\u0633\u062A\u0631\u0633\u06CC \u0628\u0647 \u0645\u06CC\u06A9\u0631\u0648\u0641\u0648\u0646",\n            "open_microphone_settings": "\u0628\u0627\u0632 \u06A9\u0631\u062F\u0646 \u062A\u0646\u0638\u06CC\u0645\u0627\u062A \u0645\u06CC\u06A9\u0631\u0648\u0641\u0648\u0646 \u062F\u0631 macOS",\n            "reset_microphone": "\u0639\u06CC\u0628\u200C\u06CC\u0627\u0628\u06CC: \u0628\u0627\u0632\u0646\u0634\u0627\u0646\u06CC \u0627\u0639\u0644\u0627\u0646 \u0645\u062C\u0648\u0632",\n            "relaunch_app": "\u0631\u0627\u0647\u200C\u0627\u0646\u062F\u0627\u0632\u06CC \u0645\u062C\u062F\u062F VacuumTube",\n            "restart_required": "\u062A\u063A\u06CC\u06CC\u0631\u0627\u062A \u0627\u06CC\u062C\u0627\u062F\u0634\u062F\u0647 \u062F\u0631 macOS Settings \u0646\u06CC\u0627\u0632\u0645\u0646\u062F \u0631\u0627\u0647\u200C\u0627\u0646\u062F\u0627\u0632\u06CC \u0645\u062C\u062F\u062F VacuumTube \u0627\u0633\u062A.",\n            "request_granted": "\u062F\u0633\u062A\u0631\u0633\u06CC \u0628\u0647 \u0645\u06CC\u06A9\u0631\u0648\u0641\u0648\u0646 \u0645\u062C\u0627\u0632 \u0627\u0633\u062A. \u0646\u06CC\u0627\u0632\u06CC \u0628\u0647 \u0627\u0639\u0644\u0627\u0646 \u0645\u062C\u0648\u0632 \u0646\u06CC\u0633\u062A.",\n            "request_not_determined": "\u0647\u0646\u0648\u0632 macOS \u0627\u0639\u0644\u0627\u0646 \u0645\u062C\u0648\u0632\u06CC \u0646\u0634\u0627\u0646 \u0646\u062F\u0627\u062F\u0647 \u0627\u0633\u062A. \u0627\u0632 \xAB\u0628\u0627\u0632\u0646\u0634\u0627\u0646\u06CC \u0627\u0639\u0644\u0627\u0646 \u0645\u062C\u0648\u0632\xBB \u0627\u0633\u062A\u0641\u0627\u062F\u0647 \u06A9\u0646\u06CC\u062F \u0648 \u0633\u067E\u0633 \u062F\u0648\u0628\u0627\u0631\u0647 \u0627\u0645\u062A\u062D\u0627\u0646 \u0646\u0645\u0627\u06CC\u06CC\u062F.",\n            "request_denied_help": "\u062A\u0627 \u0632\u0645\u0627\u0646\u06CC \u06A9\u0647 \u062F\u0633\u062A\u0631\u0633\u06CC \u0631\u062F \u0634\u062F\u0647 \u0628\u0627\u0634\u062F\u060C macOS \u0627\u0639\u0644\u0627\u0646 \u0645\u062C\u0648\u0632 \u0631\u0627 \u062F\u0648\u0628\u0627\u0631\u0647 \u0646\u0634\u0627\u0646 \u0646\u062E\u0648\u0627\u0647\u062F \u062F\u0627\u062F. \u0622\u0646 \u0631\u0627 \u062F\u0631 macOS Settings \u0641\u0639\u0627\u0644 \u06A9\u0646\u06CC\u062F \u0648 \u0633\u067E\u0633 VacuumTube \u0631\u0627 \u0645\u062C\u062F\u062F\u0627\u064B \u0631\u0627\u0647\u200C\u0627\u0646\u062F\u0627\u0632\u06CC \u0646\u0645\u0627\u06CC\u06CC\u062F.",\n            "request_failed": "\u062F\u0633\u062A\u0631\u0633\u06CC \u0628\u0647 \u0645\u06CC\u06A9\u0631\u0648\u0641\u0648\u0646 \u0631\u0627 VacuumTube \u0646\u062A\u0648\u0627\u0646\u0633\u062A \u062F\u0631\u062E\u0648\u0627\u0633\u062A \u06A9\u0646\u062F. macOS Settings \u0631\u0627 \u0628\u0627\u0632 \u06A9\u0646\u06CC\u062F \u0648 \u067E\u0633 \u0627\u0632 \u062A\u063A\u06CC\u06CC\u0631 \u062F\u0633\u062A\u0631\u0633\u06CC\u060C \u0628\u0631\u0646\u0627\u0645\u0647 \u0631\u0627 \u0645\u062C\u062F\u062F\u0627\u064B \u0631\u0627\u0647\u200C\u0627\u0646\u062F\u0627\u0632\u06CC \u0646\u0645\u0627\u06CC\u06CC\u062F.",\n            "resetting_microphone": "\u062F\u0631 \u062D\u0627\u0644 \u0628\u0627\u0632\u0646\u0634\u0627\u0646\u06CC \u062A\u0635\u0645\u06CC\u0645 \u0630\u062E\u06CC\u0631\u0647\u200C\u0634\u062F\u0647 \u0645\u06CC\u06A9\u0631\u0648\u0641\u0648\u0646 \u062F\u0631 macOS...",\n            "statuses": {\n                "not-determined": "\u062F\u0631\u062E\u0648\u0627\u0633\u062A\u200C\u0646\u0634\u062F\u0647",\n                "granted": "\u0645\u062C\u0627\u0632",\n                "denied": "\u0631\u062F \u0634\u062F\u0647",\n                "restricted": "\u0645\u062D\u062F\u0648\u062F \u0634\u062F\u0647",\n                "unknown": "\u0646\u0627\u0634\u0646\u0627\u062E\u062A\u0647",\n                "unsupported": "\u067E\u0634\u062A\u06CC\u0628\u0627\u0646\u06CC\u200C\u0646\u0634\u062F\u0647"\n            }\n        },\n        "about": {\n            "title": "\u062F\u0631\u0628\u0627\u0631\u0647",\n            "description": "\u200FYouTube \u0628\u0631\u0627\u06CC \u062A\u0644\u0648\u06CC\u0632\u06CC\u0648\u0646\u060C \u062F\u0631 \u0631\u0627\u06CC\u0627\u0646\u0647 \u0634\u0645\u0627",\n            "version": "\u0646\u0633\u062E\u0647 {version}",\n            "platform_label": "\u067E\u0644\u062A\u0641\u0631\u0645",\n            "installation_label": "\u0646\u0648\u0639 \u0646\u0635\u0628",\n            "update_method_label": "\u0631\u0648\u0634 \u0628\u0647\u200C\u0631\u0648\u0632\u0631\u0633\u0627\u0646\u06CC",\n            "view_github": "\u0645\u0634\u0627\u0647\u062F\u0647 \u062F\u0631 GitHub",\n            "latest_releases": "\u0622\u062E\u0631\u06CC\u0646 \u0646\u0633\u062E\u0647\u200C\u0647\u0627",\n            "platforms": {\n                "darwin": "macOS",\n                "win32": "Windows",\n                "linux": "Linux"\n            },\n            "architectures": {\n                "arm64": "arm64",\n                "x64": "x64",\n                "ia32": "x86"\n            },\n            "installations": {\n                "application": "\u0628\u0631\u0646\u0627\u0645\u0647",\n                "installer": "\u0628\u0631\u0646\u0627\u0645\u0647 \u0646\u0635\u0628\u200C\u0634\u062F\u0647",\n                "portable": "\u0628\u0631\u0646\u0627\u0645\u0647 \u0642\u0627\u0628\u0644\u200C\u062D\u0645\u0644",\n                "flatpak": "Flatpak",\n                "appimage": "AppImage",\n                "package": "\u0628\u0633\u062A\u0647 \u0633\u06CC\u0633\u062A\u0645\u06CC",\n                "development": "\u0646\u0633\u062E\u0647 \u062A\u0648\u0633\u0639\u0647",\n                "unknown": "\u0646\u0627\u0634\u0646\u0627\u062E\u062A\u0647"\n            },\n            "update_methods": {\n                "automatic": "\u0646\u0635\u0628\u200C\u06A9\u0646\u0646\u062F\u0647 \u062E\u0648\u062F\u06A9\u0627\u0631",\n                "manual": "\u062F\u0627\u0646\u0644\u0648\u062F \u062F\u0633\u062A\u06CC",\n                "flatpak": "Flatpak"\n            },\n            "updates": {\n                "idle_title": "\u0628\u0647\u200C\u0631\u0648\u0632\u0631\u0633\u0627\u0646\u06CC\u200C\u0647\u0627",\n                "idle_detail": "\u0628\u0631\u0631\u0633\u06CC \u06A9\u0646\u06CC\u062F \u0622\u06CC\u0627 \u0646\u0633\u062E\u0647 \u062C\u062F\u06CC\u062F\u062A\u0631\u06CC \u0627\u0632 VacuumTube \u0645\u0648\u062C\u0648\u062F \u0627\u0633\u062A \u06CC\u0627 \u062E\u06CC\u0631.",\n                "check_now": "\u0628\u0631\u0631\u0633\u06CC \u0628\u0647\u200C\u0631\u0648\u0632\u0631\u0633\u0627\u0646\u06CC\u200C\u0647\u0627",\n                "checking_title": "\u062F\u0631 \u062D\u0627\u0644 \u0628\u0631\u0631\u0633\u06CC \u0628\u0647\u200C\u0631\u0648\u0632\u0631\u0633\u0627\u0646\u06CC\u200C\u0647\u0627\u2026",\n                "checking_detail": "\u062F\u0631 \u062D\u0627\u0644 \u062C\u0633\u062A\u062C\u0648 \u0628\u0631\u0627\u06CC \u062C\u062F\u06CC\u062F\u062A\u0631\u06CC\u0646 \u0646\u0633\u062E\u0647 VacuumTube.",\n                "checking_button": "\u062F\u0631 \u062D\u0627\u0644 \u0628\u0631\u0631\u0633\u06CC\u2026",\n                "current_title": "\u0628\u0631\u0646\u0627\u0645\u0647 \u0628\u0647\u200C\u0631\u0648\u0632 \u0627\u0633\u062A",\n                "current_detail": "\u0646\u0633\u062E\u0647 {version} \u0628\u0631\u0646\u0627\u0645\u0647 VacuumTube \u062C\u062F\u06CC\u062F\u062A\u0631\u06CC\u0646 \u0646\u0633\u062E\u0647 \u0645\u0648\u062C\u0648\u062F \u0627\u0633\u062A.",\n                "check_again": "\u0628\u0631\u0631\u0633\u06CC \u0645\u062C\u062F\u062F",\n                "available_title": "\u200FVacuumTube {version} \u0645\u0648\u062C\u0648\u062F \u0627\u0633\u062A",\n                "available_automatic_detail": "\u0628\u0647\u200C\u0631\u0648\u0632\u0631\u0633\u0627\u0646\u06CC \u0631\u0627 \u0627\u06A9\u0646\u0648\u0646 \u062F\u0627\u0646\u0644\u0648\u062F \u06A9\u0646\u06CC\u062F\u060C \u0633\u067E\u0633 \u067E\u0633 \u0627\u0632 \u0622\u0645\u0627\u062F\u0647 \u0634\u062F\u0646\u060C VacuumTube \u0631\u0627 \u0645\u062C\u062F\u062F\u0627\u064B \u0631\u0627\u0647\u200C\u0627\u0646\u062F\u0627\u0632\u06CC \u0646\u0645\u0627\u06CC\u06CC\u062F.",\n                "available_manual_detail": "\u062C\u062F\u06CC\u062F\u062A\u0631\u06CC\u0646 \u0646\u0633\u062E\u0647 \u0631\u0627 \u062F\u0627\u0646\u0644\u0648\u062F \u06A9\u0631\u062F\u0647 \u0648 \u062C\u0627\u06CC\u06AF\u0632\u06CC\u0646 \u0646\u0633\u062E\u0647 \u0641\u0639\u0644\u06CC \u062E\u0648\u062F \u06A9\u0646\u06CC\u062F.",\n                "download_update": "\u062F\u0627\u0646\u0644\u0648\u062F \u0628\u0647\u200C\u0631\u0648\u0632\u0631\u0633\u0627\u0646\u06CC",\n                "download_latest": "\u062F\u0627\u0646\u0644\u0648\u062F \u062C\u062F\u06CC\u062F\u062A\u0631\u06CC\u0646 \u0646\u0633\u062E\u0647",\n                "downloading_title": "\u062F\u0631 \u062D\u0627\u0644 \u062F\u0627\u0646\u0644\u0648\u062F \u0628\u0647\u200C\u0631\u0648\u0632\u0631\u0633\u0627\u0646\u06CC\u2026",\n                "downloading_detail": "{percent}% \u0627\u0632 \u0628\u0647\u200C\u0631\u0648\u0632\u0631\u0633\u0627\u0646\u06CC \u062F\u0627\u0646\u0644\u0648\u062F \u0634\u062F\u0647 \u0627\u0633\u062A.",\n                "downloading_button": "\u062F\u0631 \u062D\u0627\u0644 \u062F\u0627\u0646\u0644\u0648\u062F\u2026 {percent}%",\n                "ready_title": "\u0628\u0647\u200C\u0631\u0648\u0632\u0631\u0633\u0627\u0646\u06CC \u0622\u0645\u0627\u062F\u0647 \u0646\u0635\u0628 \u0627\u0633\u062A",\n                "ready_detail": "\u0628\u0631\u0627\u06CC \u0646\u0635\u0628 \u0646\u0633\u062E\u0647 {version}\u060C \u0628\u0631\u0646\u0627\u0645\u0647 VacuumTube \u0631\u0627 \u0645\u062C\u062F\u062F\u0627\u064B \u0631\u0627\u0647\u200C\u0627\u0646\u062F\u0627\u0632\u06CC \u06A9\u0646\u06CC\u062F.",\n                "install_update": "\u0631\u0627\u0647\u200C\u0627\u0646\u062F\u0627\u0632\u06CC \u0645\u062C\u062F\u062F \u0648 \u0646\u0635\u0628",\n                "error_title": "\u0628\u0631\u0631\u0633\u06CC \u0628\u0647\u200C\u0631\u0648\u0632\u0631\u0633\u0627\u0646\u06CC\u200C\u0647\u0627 \u0627\u0646\u062C\u0627\u0645 \u0646\u0634\u062F",\n                "error_detail": "\u0627\u062A\u0635\u0627\u0644 \u0627\u06CC\u0646\u062A\u0631\u0646\u062A \u062E\u0648\u062F \u0631\u0627 \u0628\u0631\u0631\u0633\u06CC \u06A9\u0631\u062F\u0647 \u0648 \u062F\u0648\u0628\u0627\u0631\u0647 \u0627\u0645\u062A\u062D\u0627\u0646 \u06A9\u0646\u06CC\u062F\u060C \u06CC\u0627 \u0635\u0641\u062D\u0647 \u0627\u0646\u062A\u0634\u0627\u0631 \u0646\u0633\u062E\u0647\u200C\u0647\u0627 \u0631\u0627 \u0628\u0627\u0632 \u0646\u0645\u0627\u06CC\u06CC\u062F.",\n                "try_again": "\u062A\u0644\u0627\u0634 \u0645\u062C\u062F\u062F",\n                "development_title": "\u0646\u0633\u062E\u0647 \u062A\u0648\u0633\u0639\u0647",\n                "development_detail": "\u0628\u0631\u0631\u0633\u06CC \u062E\u0648\u062F\u06A9\u0627\u0631 \u0628\u0647\u200C\u0631\u0648\u0632\u0631\u0633\u0627\u0646\u06CC \u062F\u0631 \u0646\u0633\u062E\u0647\u200C\u0647\u0627\u06CC \u0628\u0633\u062A\u0647\u200C\u0628\u0646\u062F\u06CC\u200C\u0634\u062F\u0647 \u062F\u0631 \u062F\u0633\u062A\u0631\u0633 \u0627\u0633\u062A.",\n                "unsupported_title": "\u0628\u0631\u0631\u0633\u06CC \u0628\u0647\u200C\u0631\u0648\u0632\u0631\u0633\u0627\u0646\u06CC \u062F\u0631\u062F\u0633\u062A\u0631\u0633 \u0646\u06CC\u0633\u062A",\n                "unsupported_detail": "\u0628\u0631\u0627\u06CC \u0628\u0631\u0631\u0633\u06CC \u0646\u0633\u062E\u0647 \u062C\u062F\u06CC\u062F\u062A\u0631\u060C \u0627\u0632 \u0631\u0648\u0634 \u0628\u0647\u200C\u0631\u0648\u0632\u0631\u0633\u0627\u0646\u06CC \u0646\u0634\u0627\u0646\u200C\u062F\u0627\u062F\u0647\u200C\u0634\u062F\u0647 \u062F\u0631 \u0632\u06CC\u0631 \u0627\u0633\u062A\u0641\u0627\u062F\u0647 \u06A9\u0646\u06CC\u062F.",\n                "view_update_source": "\u0645\u0634\u0627\u0647\u062F\u0647 \u0645\u0646\u0628\u0639 \u0628\u0647\u200C\u0631\u0648\u0632\u0631\u0633\u0627\u0646\u06CC",\n                "view_releases": "\u0645\u0634\u0627\u0647\u062F\u0647 \u062C\u062F\u06CC\u062F\u062A\u0631\u06CC\u0646 \u0646\u0633\u062E\u0647\u200C\u0647\u0627"\n            }\n        }\n    },\n    "sponsorblock": {\n        "sponsor_skipped": "\u0628\u062E\u0634 \u062D\u0627\u0645\u06CC \u0645\u0627\u0644\u06CC \u0631\u062F \u0634\u062F",\n        "selfpromo_skipped": "\u062A\u0628\u0644\u06CC\u063A \u0634\u062E\u0635\u06CC \u0631\u062F \u0634\u062F",\n        "interaction_skipped": "\u06CC\u0627\u062F\u0622\u0648\u0631\u06CC \u062A\u0639\u0627\u0645\u0644 \u0631\u062F \u0634\u062F",\n        "intro_skipped": "\u0627\u0646\u06CC\u0645\u06CC\u0634\u0646 \u0622\u063A\u0627\u0632\u06CC\u0646 \u0631\u062F \u0634\u062F",\n        "outro_skipped": "\u062A\u06CC\u062A\u0631\u0627\u0698 \u067E\u0627\u06CC\u0627\u0646\u06CC \u0631\u062F \u0634\u062F",\n        "preview_skipped": "\u067E\u06CC\u0634\u200C\u0646\u0645\u0627\u06CC\u0634 \u0631\u062F \u0634\u062F",\n        "hook_skipped": "\u0642\u0644\u0627\u0628/\u0627\u062D\u0648\u0627\u0644\u200C\u067E\u0631\u0633\u06CC \u0631\u062F \u0634\u062F",\n        "filler_skipped": "\u0645\u0637\u0627\u0644\u0628 \u062D\u0627\u0634\u06CC\u0647\u200C\u0627\u06CC/\u0634\u0648\u062E\u06CC\u200C\u0647\u0627 \u0631\u062F \u0634\u062F"\n    },\n    "donate_unused": {\n        "setting": {\n            "title": "\u062D\u0645\u0627\u06CC\u062A \u0645\u0627\u0644\u06CC",\n            "description": "\u0628\u0627 \u06A9\u0645\u06A9 \u0645\u0627\u0644\u06CC\u060C \u0627\u0632 \u062A\u0648\u0633\u0639\u0647 VacuumTube \u062D\u0645\u0627\u06CC\u062A \u06A9\u0646\u06CC\u062F! \u0645\u06CC\u200C\u062A\u0648\u0627\u0646\u06CC\u062F \u0628\u0627 \u0645\u0631\u0627\u062C\u0639\u0647 \u0628\u0647 shy.rocks/donate \u06CC\u0627 \u0628\u0627 \u0641\u0634\u0631\u062F\u0646 \u062F\u06A9\u0645\u0647 \u0632\u06CC\u0631 \u0628\u0631\u0627\u06CC \u0628\u0627\u0632 \u06A9\u0631\u062F\u0646 \u0635\u0641\u062D\u0647 \u06A9\u0645\u06A9 \u0645\u0627\u0644\u06CC \u062F\u0631 \u0645\u0631\u0648\u0631\u06AF\u0631 \u062E\u0648\u062F\u060C \u062D\u0645\u0627\u06CC\u062A \u0645\u0627\u0644\u06CC \u0627\u0646\u062C\u0627\u0645 \u062F\u0647\u06CC\u062F",\n            "button_label": "\u062D\u0645\u0627\u06CC\u062A \u0645\u0627\u0644\u06CC"\n        }\n    }\n}\n' }, "/vt/locale/hr.json": { "text": `{
    "general": {
        "video_copied": "Link videa kopiran u me\u0111uspremnik",
        "dislikes": "Palac dolje"
    },
    "settings": {
        "adblock": {
            "title": "Bloker Reklama",
            "description": "Besprijekorno blokira video i feed oglase, ne podlije\u017Ee YouTubeovim metodama protiv blokiranja"
        },
        "dearrow": {
            "title": "DeArrow",
            "description": "Zamjenjuje naslove i sli\u010Dice to\u010Dnijim, manje senzacionalisti\u010Dkim verzijama iz javne baze podataka koju prikupljaju korisnici. Usporava u\u010Ditavanje jer mora dohva\u0107ati informacije s poslu\u017Eitelja"
        },
        "dislikes": {
            "title": "Vratite donji palac",
            "description": "Koristi YouTube API za vra\u0107anje palca prema dolje (returnyoutubedislike.com) za ponovno dodavanje oznake 'nesvi\u0111a mi se' na YouTube. Usporava u\u010Ditavanje videozapisa jer mora dohva\u0107ati informacije s poslu\u017Eitelja"
        },
        "hardware_decoding": {
            "title": "Hardversko Dekodiranje",
            "description": "Koristi va\u0161u grafi\u010Dku karticu za dekodiranje videa kada je to mogu\u0107e. Onemogu\u0107avanje ove opcije mo\u017Ee rije\u0161iti probleme s reprodukcijom, ali mo\u017Ee uzrokovati ka\u0161njenje ovisno o va\u0161em procesoru. Ponovno pokrenite nakon prebacivanja."
        },
        "low_memory_mode": {
            "title": "Re\u017Eim za Nisku Memoriju",
            "description": "Uputuje YouTube-u da omogu\u0107i na\u010Din rada s malo memorije, \u0161to mo\u017Ee pobolj\u0161ati pokretanje na sporijim ure\u0111ajima, ali na \u0161tetu nekih vizualnih efekata. Ponovno pokrenite nakon prebacivanja."
        },
        "fullscreen": {
            "title": "Cijeli Zaslon",
            "description": "Omogu\u0107ava cijeli zaslon i \u010Dini da se VacuumTube uvijek pokrene u punom zaslonu."
        },
        "keep_on_top": {
            "title": "Ostani na vrhu",
            "description": "Omogu\u0107uje opciju \\"Ostani na vrhu\\" i pokre\u0107e VacuumTube s prozorom usidrenim preko svakog drugog prozora."
        },
        "userstyles": {
            "title": "Prilago\u0111eni CSS (korisni\u010Dki stilovi)",
            "description": "Omogu\u0107uje ubrizgavanje prilago\u0111enih CSS stilova. Stavite svoje .css datoteke u mapu 'korisni\u010Dki stilovi'."
        }
    }
}
` }, "/vt/locale/id.json": { "text": `{
    "general": {
        "video_copied": "Tautan video disalin ke papan klip",
        "dislikes": "Tidak Suka",
        "steam_controller_notice": "Pengontrol eksternal tidak terdeteksi? Coba plugin steamdeck-input-disabler dari Decky."
    },
    "settings": {
        "generic": {
            "title": "Pengaturan",
            "description": "Buka menu pengaturan VacuumTube. Akses menu ini dengan menekan Ctrl+O pada papan ketik.",
            "button_label": "Buka Pengaturan VacuumTube",
            "hint": "Gunakan tombol panah untuk menjelajah"
        },
        "adblock": {
            "title": "Blokir Iklan",
            "description": "Blokir iklan beranda dan video, tanpa perlu khawatir terdeteksi YouTube"
        },
        "sponsorblock": {
            "title": "SponsorBlock",
            "description": "Lewati segmen sponsor pada video YouTube berdasarkan basis data publik. Saat ini hanya mendukung fitur lewati sponsor."
        },
        "dearrow": {
            "title": "DeArrow",
            "description": "Mengganti judul dan gambar pratinjau video yang lebih akurat dan tidak sensasional dari basis data publik yang dihimpun bersama. Proses pengambilan informasi akan memperlambat pemuatan aplikasi VacuumTube."
        },
        "dislikes": {
            "title": "Kembalikan 'Tidak Suka'",
            "description": "Gunakan API Return YouTube Dislike (returnyoutubedislike.com) untuk mengembalikan fitur info jumlah 'Tidak Suka' pada video YouTube. Proses pengambilan info ini akan memperlambat pemuatan video."
        },
        "remove_super_resolution": {
            "title": "Hapus Resolusi Super",
            "description": "Hapus pilihan kualitas \\"Resolusi super\\" (pembesaran AI) dari video beresolusi rendah"
        },
        "hide_shorts": {
            "title": "Sembunyikan Shorts",
            "description": "Sembunyikan Shorts YouTube dari halaman utama"
        },
        "hardware_decoding": {
            "title": "Optimasi Grafis",
            "description": "Gunakan kartu grafis (GPU) untuk mengurai pemutaran video jika sanggup. Mematikan opsi ini mungkin dapat menyelesaikan sebagian masalah pemutaran, namun dapat menghambat video jika prosesor tidak memadai. Luncurkan ulang setelah mengubah."
        },
        "low_memory_mode": {
            "title": "Optimasi Memori",
            "description": "Lakukan optimasi untuk perangkat bermemori rendah dengan mengurangi beberapa efek visual. Luncurkan ulang setelah mengubah."
        },
        "fullscreen": {
            "title": "Mode Layar Penuh",
            "description": "Memastikan VacuumTube selalu masuk dalam mode layar penuh setelah diluncurkan."
        },
        "keep_on_top": {
            "title": "Tetap di Atas",
            "description": "Memastikan jendela VacuumTube selalu berada di atas jendela aplikasi lain."
        },
        "userstyles": {
            "title": "Kustomisasi CSS (Userstyles)",
            "description": "Letakkan file .css kustom pada direktori userstyles untuk mengubah tampilan VacuumTube. Perubahan akan dimuat otomatis.",
            "warn_empty": "CSS tidak ditemukan",
            "open_folder": "Buka Direktori Userstyles",
            "enable": "Aktifkan Userstyles",
            "failed_to_load": "Gagal memuat CSS"
        },
        "controller_support": {
            "title": "Dukungan Pengontrol",
            "description": "Mengaktifkan dukungan untuk pengontrol konsol eksternal, termasuk kontrol navigasi halaman dan pemutaran video."
        }
    },
    "sponsorblock": {
        "sponsor_skipped": "Sponsor dilewati"
    },
    "donate_unused": {
        "setting": {
            "title": "Mari Berdonasi",
            "description": "Dukung pengembangan VacuumTube dengan berdonasi ke shy.rocks/donate. Tekan tombol berikut untuk membuka situs donasi di peramban Anda.",
            "button_label": "Beri Donasi"
        }
    }
}
` }, "/vt/locale/nl.json": { "text": `{
    "general": {
        "video_copied": "Videolink naar klembord gekopieerd",
        "dislikes": "Afkeerknoppen"
    },
    "settings": {
        "adblock": {
            "title": "Ad-blokker",
            "description": "Blokkeert naadloos video- en feed-advertenties en is niet onderhevig aan de blokkeermethoden van YouTube"
        },
        "dearrow": {
            "title": "DeArrow",
            "description": "Vervangt titels en miniaturen door nauwkeurigere, minder sensationele versies uit een openbare, crowdsourcedatabase. Vertraagt het laden, omdat het info moet ophalen van de server"
        },
        "dislikes": {
            "title": "Afkeren teruggeven",
            "description": "Gebruikt de Return YouTube Dislike API (returnyoutubedislike.com) om afkeerknoppen opnieuw toe te voegen aan YouTube. Vertraagt het laden van video's, omdat het informatie van de server moet ophalen"
        },
        "hardware_decoding": {
            "title": "Hardwaredecodering",
            "description": "Gebruikt waar mogelijk uw GPU om video's te decoderen. Als u dit uitschakelt, kunnen afspeelproblemen worden opgelost, maar kan er vertraging optreden, afhankelijk van uw CPU. Herstart na het omschakelen."
        },
        "low_memory_mode": {
            "title": "Laaggeheugenmodus",
            "description": "Vertelt YouTube om de modus met laag geheugen in te schakelen, wat de prestaties op langzamere apparaten kan verbeteren ten koste van sommige visuele effecten. Herstart na het omschakelen."
        },
        "fullscreen": {
            "title": "Vollescherm",
            "description": "Schakelt volledig scherm in en zorgt ervoor dat VacuumTube altijd opstart in volledig scherm."
        },
        "keep_on_top": {
            "title": "Bovenaan houden",
            "description": "Schakelt \\"Bovenaan houden\\" in en zorgt ervoor dat VacuumTube wordt gestart met het venster boven elk ander venster vastgepind."
        },
        "userstyles": {
            "title": "Aangepaste CSS (gebruikersstijlen)",
            "description": "Maakt injectie van aangepaste CSS-stijlen mogelijk. Plaats je .css-bestanden in de gebruikersstijlen map."
        }
    }
}
` }, "/vt/locale/ru.json": { "text": '{\n    "general": {\n        "video_copied": "\u0421\u0441\u044B\u043B\u043A\u0430 \u043D\u0430 \u0432\u0438\u0434\u0435\u043E \u0441\u043A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u043D\u0430",\n        "dislikes": "\u041D\u0435 \u043D\u0440\u0430\u0432\u0438\u0442\u0441\u044F",\n        "steam_controller_notice": "\u0412\u043D\u0435\u0448\u043D\u0438\u0439 \u043A\u043E\u043D\u0442\u0440\u043E\u043B\u043B\u0435\u0440 \u043D\u0435 \u043E\u0431\u043D\u0430\u0440\u0443\u0436\u0435\u043D? \u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u043F\u043B\u0430\u0433\u0438\u043D steamdeck-input-disabler \u0434\u043B\u044F Decky.",\n        "encryption_error": {\n            "title": "\u0427\u0442\u043E-\u0442\u043E \u043F\u043E\u0448\u043B\u043E \u043D\u0435 \u0442\u0430\u043A",\n            "text": "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0434\u0435\u043A\u043E\u0434\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u0432\u0438\u0434\u0435\u043E. \u0424\u0438\u043B\u044C\u043C\u044B \u043D\u0435 \u043F\u043E\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u044E\u0442\u0441\u044F \u0432 VacuumTube \u0438\u0437-\u0437\u0430 \u0448\u0438\u0444\u0440\u043E\u0432\u0430\u043D\u0438\u044F. \u0415\u0441\u043B\u0438 \u043E\u0448\u0438\u0431\u043A\u0430 \u0432\u043E\u0437\u043D\u0438\u043A\u0430\u0435\u0442 \u0438 \u0441 \u0434\u0440\u0443\u0433\u0438\u043C\u0438 \u0432\u0438\u0434\u0435\u043E, \u043F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0432\u043E\u0439\u0442\u0438 \u0432 \u0430\u043A\u043A\u0430\u0443\u043D\u0442 \u0438\u043B\u0438 \u043F\u0435\u0440\u0435\u043A\u043B\u044E\u0447\u0438\u0442\u044C\u0441\u044F \u043D\u0430 \u0434\u0440\u0443\u0433\u043E\u0439.",\n            "switch_accounts": "\u0421\u043C\u0435\u043D\u0438\u0442\u044C \u0430\u043A\u043A\u0430\u0443\u043D\u0442",\n            "okay": "\u041E\u041A"\n        }\n    },\n    "settings": {\n        "generic": {\n            "title": "\u041D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0438",\n            "description": "\u041E\u0442\u043A\u0440\u044B\u0432\u0430\u0435\u0442 \u043D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0438 VacuumTube. \u041E\u0432\u0435\u0440\u043B\u0435\u0439 \u043C\u043E\u0436\u043D\u043E \u0432\u044B\u0437\u0432\u0430\u0442\u044C \u0432 \u043B\u044E\u0431\u043E\u043C \u043C\u0435\u0441\u0442\u0435 \u0441\u043E\u0447\u0435\u0442\u0430\u043D\u0438\u0435\u043C \u043A\u043B\u0430\u0432\u0438\u0448 Ctrl+O.",\n            "button_label": "\u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0438 VacuumTube",\n            "hint": "\u0418\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0439\u0442\u0435 \u043A\u043B\u0430\u0432\u0438\u0448\u0438 \u0441\u043E \u0441\u0442\u0440\u0435\u043B\u043A\u0430\u043C\u0438 \u0434\u043B\u044F \u043D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u0438",\n            "option_count": "\u0412\u0430\u0440\u0438\u0430\u043D\u0442\u043E\u0432: {count}",\n            "translate": "\u041F\u043E\u043C\u043E\u0433\u0438\u0442\u0435 \u043F\u0435\u0440\u0435\u0432\u0435\u0441\u0442\u0438 VacuumTube \u043D\u0430 {language}",\n            "ratio": "{on} \u0438\u0437 {total}"\n        },\n        "categories": {\n            "general": {\n                "title": "\u041E\u0431\u0449\u0438\u0435",\n                "description": "\u041D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0438 \u043F\u043E\u0432\u0435\u0434\u0435\u043D\u0438\u044F \u043E\u043A\u043D\u0430 VacuumTube."\n            },\n            "playback": {\n                "title": "\u0412\u043E\u0441\u043F\u0440\u043E\u0438\u0437\u0432\u0435\u0434\u0435\u043D\u0438\u0435",\n                "description": "\u041D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0438 \u0432\u043E\u0441\u043F\u0440\u043E\u0438\u0437\u0432\u0435\u0434\u0435\u043D\u0438\u044F \u0438 \u043F\u0435\u0440\u0441\u043E\u043D\u0430\u043B\u0438\u0437\u0430\u0446\u0438\u0438 \u043F\u043B\u0435\u0435\u0440\u0430."\n            },\n            "appearance": {\n                "title": "\u0412\u043D\u0435\u0448\u043D\u0438\u0439 \u0432\u0438\u0434",\n                "description": "\u041D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0438 \u0432\u043D\u0435\u0448\u043D\u0435\u0433\u043E \u0432\u0438\u0434\u0430 \u0438\u043D\u0442\u0435\u0440\u0444\u0435\u0439\u0441\u0430."\n            },\n            "controls": {\n                "title": "\u0423\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u0435",\n                "description": "\u0421\u043F\u043E\u0441\u043E\u0431\u044B \u0432\u0432\u043E\u0434\u0430 \u0438 \u0443\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u0435 \u0443\u0441\u0442\u0440\u043E\u0439\u0441\u0442\u0432\u0430\u043C\u0438."\n            },\n            "advanced": {\n                "title": "\u0420\u0430\u0441\u0448\u0438\u0440\u0435\u043D\u043D\u044B\u0435",\n                "description": "\u041E\u043F\u0442\u0438\u043C\u0438\u0437\u0430\u0446\u0438\u0438 \u0438 \u044D\u043A\u0441\u043F\u0435\u0440\u0438\u043C\u0435\u043D\u0442\u0430\u043B\u044C\u043D\u044B\u0435 \u043D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0438."\n            }\n        },\n        "sections": {\n            "window": "\u041E\u043A\u043D\u043E",\n            "behavior": "\u041F\u043E\u0432\u0435\u0434\u0435\u043D\u0438\u0435",\n            "updates": "\u041E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u044F",\n            "content": "\u041A\u043E\u043D\u0442\u0435\u043D\u0442",\n            "quality": "\u041A\u0430\u0447\u0435\u0441\u0442\u0432\u043E \u0438 \u0441\u043E\u0432\u043C\u0435\u0441\u0442\u0438\u043C\u043E\u0441\u0442\u044C",\n            "interface": "\u0418\u043D\u0442\u0435\u0440\u0444\u0435\u0439\u0441",\n            "userstyles": "\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044C\u0441\u043A\u0438\u0439 CSS",\n            "input": "\u0412\u0432\u043E\u0434",\n            "devices": "\u0423\u0441\u0442\u0440\u043E\u0439\u0441\u0442\u0432\u0430",\n            "performance": "\u041F\u0440\u043E\u0438\u0437\u0432\u043E\u0434\u0438\u0442\u0435\u043B\u044C\u043D\u043E\u0441\u0442\u044C",\n            "experimental": "\u042D\u043A\u0441\u043F\u0435\u0440\u0438\u043C\u0435\u043D\u0442\u0430\u043B\u044C\u043D\u043E\u0435"\n        },\n        "adblock": {\n            "title": "Ad Block",\n            "description": "\u0411\u043B\u043E\u043A\u0438\u0440\u0443\u0435\u0442 \u0440\u0435\u043A\u043B\u0430\u043C\u0443 \u0432 \u0432\u0438\u0434\u0435\u043E \u0438 \u043D\u0430 \u0433\u043B\u0430\u0432\u043D\u043E\u0439 \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0435, \u043E\u0441\u0442\u0430\u0432\u0430\u044F\u0441\u044C \u043D\u0435\u0437\u0430\u043C\u0435\u0447\u0435\u043D\u043D\u044B\u043C \u0434\u043B\u044F \u043C\u0435\u0442\u043E\u0434\u043E\u0432 YouTube \u043F\u043E \u0431\u043E\u0440\u044C\u0431\u0435 \u0441 \u0431\u043B\u043E\u043A\u0438\u0440\u043E\u0432\u0449\u0438\u043A\u0430\u043C\u0438."\n        },\n        "sponsorblock": {\n            "title": "SponsorBlock",\n            "description": "\u041F\u0440\u043E\u043F\u0443\u0441\u043A\u0430\u0435\u0442 \u0441\u043F\u043E\u043D\u0441\u043E\u0440\u0441\u043A\u0438\u0435 \u0441\u0435\u0433\u043C\u0435\u043D\u0442\u044B \u0432 \u0432\u0438\u0434\u0435\u043E, \u0438\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u044F \u043E\u0431\u0449\u0435\u0434\u043E\u0441\u0442\u0443\u043F\u043D\u0443\u044E \u0431\u0430\u0437\u0443 \u0434\u0430\u043D\u043D\u044B\u0445.",\n            "categories_link": "\u041F\u0440\u043E\u043F\u0443\u0441\u043A\u0430\u0435\u043C\u044B\u0435 \u0441\u0435\u0433\u043C\u0435\u043D\u0442\u044B",\n            "categories": {\n                "sponsor": {\n                    "name": "\u0421\u043F\u043E\u043D\u0441\u043E\u0440\u0441\u0442\u0432\u043E",\n                    "description": "\u041F\u043B\u0430\u0442\u043D\u043E\u0435 \u043F\u0440\u043E\u0434\u0432\u0438\u0436\u0435\u043D\u0438\u0435, \u043F\u043B\u0430\u0442\u043D\u044B\u0435 \u0440\u0435\u0444\u0435\u0440\u0430\u043B\u044C\u043D\u044B\u0435 \u043F\u0440\u043E\u0433\u0440\u0430\u043C\u043C\u044B \u0438 \u043F\u0440\u044F\u043C\u0430\u044F \u0440\u0435\u043A\u043B\u0430\u043C\u0430. \u041D\u0435 \u0432\u043A\u043B\u044E\u0447\u0430\u0435\u0442 \u0441\u0430\u043C\u043E\u0440\u0435\u043A\u043B\u0430\u043C\u0443 \u0438 \u0431\u0435\u0441\u043F\u043B\u0430\u0442\u043D\u044B\u0435 \u0443\u043F\u043E\u043C\u0438\u043D\u0430\u043D\u0438\u044F \u043F\u0440\u043E\u0435\u043A\u0442\u043E\u0432, \u0430\u0432\u0442\u043E\u0440\u043E\u0432, \u0441\u0430\u0439\u0442\u043E\u0432 \u0438\u043B\u0438 \u043F\u0440\u043E\u0434\u0443\u043A\u0442\u043E\u0432, \u043A\u043E\u0442\u043E\u0440\u044B\u0435 \u043D\u0440\u0430\u0432\u044F\u0442\u0441\u044F \u0430\u0432\u0442\u043E\u0440\u0443 \u0432\u0438\u0434\u0435\u043E."\n                },\n                "selfpromo": {\n                    "name": "\u0421\u0430\u043C\u043E\u0440\u0435\u043A\u043B\u0430\u043C\u0430",\n                    "description": "\u0410\u043D\u0430\u043B\u043E\u0433\u0438\u0447\u043D\u043E \u043A\u0430\u0442\u0435\u0433\u043E\u0440\u0438\u0438 \xAB\u0421\u043F\u043E\u043D\u0441\u043E\u0440\u0441\u0442\u0432\u043E\xBB, \u043D\u043E \u0432\u043A\u043B\u044E\u0447\u0430\u0435\u0442 \u0431\u0435\u0441\u043F\u043B\u0430\u0442\u043D\u043E\u0435 \u043F\u0440\u043E\u0434\u0432\u0438\u0436\u0435\u043D\u0438\u0435 \u0438 \u0441\u0430\u043C\u043E\u0440\u0435\u043A\u043B\u0430\u043C\u0443. \u0421\u044E\u0434\u0430 \u043E\u0442\u043D\u043E\u0441\u044F\u0442\u0441\u044F \u0441\u0435\u0433\u043C\u0435\u043D\u0442\u044B \u043E \u0444\u0438\u0440\u043C\u0435\u043D\u043D\u043E\u0439 \u043F\u0440\u043E\u0434\u0443\u043A\u0446\u0438\u0438, \u043F\u043E\u0436\u0435\u0440\u0442\u0432\u043E\u0432\u0430\u043D\u0438\u044F\u0445 \u0438 \u043F\u0430\u0440\u0442\u043D\u0451\u0440\u0430\u0445."\n                },\n                "interaction": {\n                    "name": "\u041F\u0440\u0438\u0437\u044B\u0432 \u043A \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u044E",\n                    "description": "\u041A\u0440\u0430\u0442\u043A\u0438\u0435 \u043D\u0430\u043F\u043E\u043C\u0438\u043D\u0430\u043D\u0438\u044F \u043F\u043E\u0441\u0442\u0430\u0432\u0438\u0442\u044C \u043E\u0442\u043C\u0435\u0442\u043A\u0443 \xAB\u041D\u0440\u0430\u0432\u0438\u0442\u0441\u044F\xBB \u0438\u043B\u0438 \u043F\u043E\u0434\u043F\u0438\u0441\u0430\u0442\u044C\u0441\u044F, \u043E\u0431\u044B\u0447\u043D\u043E \u0432 \u0441\u0435\u0440\u0435\u0434\u0438\u043D\u0435 \u0432\u0438\u0434\u0435\u043E."\n                },\n                "intro": {\n                    "name": "\u041D\u0430\u0447\u0430\u043B\u043E",\n                    "description": "\u0423\u0447\u0430\u0441\u0442\u043E\u043A \u0431\u0435\u0437 \u0441\u043E\u0434\u0435\u0440\u0436\u0430\u0442\u0435\u043B\u044C\u043D\u043E\u0439 \u0447\u0430\u0441\u0442\u0438: \u043F\u0430\u0443\u0437\u0430, \u0441\u0442\u0430\u0442\u0438\u0447\u043D\u044B\u0439 \u043A\u0430\u0434\u0440 \u0438\u043B\u0438 \u0437\u0430\u0446\u0438\u043A\u043B\u0435\u043D\u043D\u0430\u044F \u0430\u043D\u0438\u043C\u0430\u0446\u0438\u044F."\n                },\n                "outro": {\n                    "name": "\u041E\u043A\u043E\u043D\u0447\u0430\u043D\u0438\u0435",\n                    "description": "\u0422\u0438\u0442\u0440\u044B \u0438\u043B\u0438 \u043A\u043E\u043D\u0435\u0447\u043D\u044B\u0435 \u0437\u0430\u0441\u0442\u0430\u0432\u043A\u0438 YouTube."\n                },\n                "preview": {\n                    "name": "\u041F\u0440\u0435\u0434\u043F\u0440\u043E\u0441\u043C\u043E\u0442\u0440 / \u0420\u0435\u043A\u0430\u043F",\n                    "description": "\u041F\u043E\u0434\u0431\u043E\u0440\u043A\u0430 \u0444\u0440\u0430\u0433\u043C\u0435\u043D\u0442\u043E\u0432, \u043A\u0440\u0430\u0442\u043A\u043E \u043F\u043E\u043A\u0430\u0437\u044B\u0432\u0430\u044E\u0449\u0438\u0445 \u0441\u043E\u0434\u0435\u0440\u0436\u0430\u043D\u0438\u0435 \u0432\u0438\u0434\u0435\u043E, \u043B\u0438\u0431\u043E \u043F\u043E\u0432\u0442\u043E\u0440\u044F\u044E\u0449\u0430\u044F \u0438\u043D\u0444\u043E\u0440\u043C\u0430\u0446\u0438\u044E, \u043A\u043E\u0442\u043E\u0440\u0430\u044F \u043F\u043E\u0437\u0434\u043D\u0435\u0435 \u0440\u0430\u0441\u043A\u0440\u044B\u0432\u0430\u0435\u0442\u0441\u044F \u0432 \u044D\u0442\u043E\u043C \u0438\u043B\u0438 \u0434\u0440\u0443\u0433\u0438\u0445 \u0440\u043E\u043B\u0438\u043A\u0430\u0445 \u0441\u0435\u0440\u0438\u0438."\n                },\n                "hook": {\n                    "name": "\u041F\u0440\u0438\u0432\u0435\u0442\u0441\u0442\u0432\u0438\u044F",\n                    "description": "\u0423\u0441\u0442\u043D\u044B\u0435 \u0430\u043D\u043E\u043D\u0441\u044B \u043F\u0440\u0435\u0434\u0441\u0442\u043E\u044F\u0449\u0435\u0433\u043E \u0432\u0438\u0434\u0435\u043E, \u043F\u0440\u0438\u0432\u0435\u0442\u0441\u0442\u0432\u0438\u044F \u0438 \u043F\u0440\u043E\u0449\u0430\u043D\u0438\u044F."\n                },\n                "filler": {\n                    "name": "\u041E\u0442\u0441\u0442\u0443\u043F\u043B\u0435\u043D\u0438\u044F / \u0448\u0443\u0442\u043A\u0438",\n                    "description": "\u041E\u0442\u0441\u0442\u0443\u043F\u043B\u0435\u043D\u0438\u044F \u0438 \u0448\u0443\u0442\u043A\u0438, \u043D\u0435 \u043D\u0443\u0436\u043D\u044B\u0435 \u0434\u043B\u044F \u043F\u043E\u043D\u0438\u043C\u0430\u043D\u0438\u044F \u043E\u0441\u043D\u043E\u0432\u043D\u043E\u0433\u043E \u0441\u043E\u0434\u0435\u0440\u0436\u0430\u043D\u0438\u044F \u0432\u0438\u0434\u0435\u043E."\n                }\n            }\n        },\n        "dearrow": {\n            "title": "DeArrow",\n            "description": "\u0417\u0430\u043C\u0435\u043D\u044F\u0435\u0442 \u0437\u0430\u0433\u043E\u043B\u043E\u0432\u043A\u0438 \u0438 \u043C\u0438\u043D\u0438\u0430\u0442\u044E\u0440\u044B \u0431\u043E\u043B\u0435\u0435 \u0442\u043E\u0447\u043D\u044B\u043C\u0438 \u0438 \u043C\u0435\u043D\u0435\u0435 \u0441\u0435\u043D\u0441\u0430\u0446\u0438\u043E\u043D\u043D\u044B\u043C\u0438 \u0432\u0430\u0440\u0438\u0430\u043D\u0442\u0430\u043C\u0438 \u0438\u0437 \u043E\u0431\u0449\u0435\u0434\u043E\u0441\u0442\u0443\u043F\u043D\u043E\u0439 \u0431\u0430\u0437\u044B \u0434\u0430\u043D\u043D\u044B\u0445. \u041C\u043E\u0436\u0435\u0442 \u0437\u0430\u043C\u0435\u0434\u043B\u044F\u0442\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u043A\u0443, \u043F\u043E\u0441\u043A\u043E\u043B\u044C\u043A\u0443 \u043F\u043E\u043B\u0443\u0447\u0430\u0435\u0442 \u0434\u0430\u043D\u043D\u044B\u0435 \u0441 \u0441\u0435\u0440\u0432\u0435\u0440\u0430."\n        },\n        "dislikes": {\n            "title": "Return Dislikes",\n            "description": "\u0418\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u0442 \u0434\u0430\u043D\u043D\u044B\u0435 \u0441\u043E\u043E\u0431\u0449\u0435\u0441\u0442\u0432\u0430 \u0438\u0437 API Return YouTube Dislike (returnyoutubedislike.com), \u0447\u0442\u043E\u0431\u044B \u043F\u043E\u043A\u0430\u0437\u044B\u0432\u0430\u0442\u044C \u043F\u0440\u0438\u0431\u043B\u0438\u0437\u0438\u0442\u0435\u043B\u044C\u043D\u043E\u0435 \u043A\u043E\u043B\u0438\u0447\u0435\u0441\u0442\u0432\u043E \u043E\u0442\u043C\u0435\u0442\u043E\u043A \xAB\u041D\u0435 \u043D\u0440\u0430\u0432\u0438\u0442\u0441\u044F\xBB. \u041C\u043E\u0436\u0435\u0442 \u0437\u0430\u043C\u0435\u0434\u043B\u044F\u0442\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u043A\u0443 \u0432\u0438\u0434\u0435\u043E, \u043F\u043E\u0441\u043A\u043E\u043B\u044C\u043A\u0443 \u043F\u043E\u043B\u0443\u0447\u0430\u0435\u0442 \u0434\u0430\u043D\u043D\u044B\u0435 \u0441 \u0441\u0435\u0440\u0432\u0435\u0440\u0430."\n        },\n        "remove_super_resolution": {\n            "title": "\u0421\u043A\u0440\u044B\u0442\u044C \u0441\u0443\u043F\u0435\u0440\u0440\u0430\u0437\u0440\u0435\u0448\u0435\u043D\u0438\u0435",\n            "description": "\u0421\u043A\u0440\u044B\u0432\u0430\u0435\u0442 \u0432\u0430\u0440\u0438\u0430\u043D\u0442\u044B \u043A\u0430\u0447\u0435\u0441\u0442\u0432\u0430 \u0441 \u0418\u0418-\u0430\u043F\u0441\u043A\u0435\u0439\u043B\u0438\u043D\u0433\u043E\u043C (\xAB\u0421\u0443\u043F\u0435\u0440\u0440\u0430\u0437\u0440\u0435\u0448\u0435\u043D\u0438\u0435\xBB) \u0434\u043B\u044F \u0432\u0438\u0434\u0435\u043E, \u0438\u0441\u0445\u043E\u0434\u043D\u043E\u0435 \u043A\u0430\u0447\u0435\u0441\u0442\u0432\u043E \u043A\u043E\u0442\u043E\u0440\u044B\u0445 \u043D\u0438\u0437\u043A\u043E\u0435."\n        },\n        "hide_shorts": {\n            "title": "\u0421\u043F\u0440\u044F\u0442\u0430\u0442\u044C Shorts",\n            "description": "\u0421\u043A\u0440\u044B\u0432\u0430\u0435\u0442 YouTube Shorts \u043D\u0430 \u0433\u043B\u0430\u0432\u043D\u043E\u0439 \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0435."\n        },\n        "guide_tabs": {\n            "title": "\u0412\u043A\u043B\u0430\u0434\u043A\u0438 \u0431\u043E\u043A\u043E\u0432\u043E\u0439 \u043F\u0430\u043D\u0435\u043B\u0438",\n            "description": "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435, \u043A\u0430\u043A\u0438\u0435 \u044D\u043B\u0435\u043C\u0435\u043D\u0442\u044B \u043F\u043E\u043A\u0430\u0437\u044B\u0432\u0430\u0442\u044C \u0432 \u0431\u043E\u043A\u043E\u0432\u043E\u043C \u043C\u0435\u043D\u044E. \u041E\u0442\u043A\u043B\u044E\u0447\u0438\u0442\u0435 \u043F\u0443\u043D\u043A\u0442, \u0447\u0442\u043E\u0431\u044B \u0441\u043A\u0440\u044B\u0442\u044C \u0435\u0433\u043E. \u041F\u043E\u0441\u043B\u0435 \u0438\u0437\u043C\u0435\u043D\u0435\u043D\u0438\u044F \u0442\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044F \u043F\u0435\u0440\u0435\u0437\u0430\u043F\u0443\u0441\u043A.",\n            "tabs": {\n                "search": "\u041F\u043E\u0438\u0441\u043A",\n                "shorts": "Shorts",\n                "subscriptions": "\u041F\u043E\u0434\u043F\u0438\u0441\u043A\u0438",\n                "library": "\u0411\u0438\u0431\u043B\u0438\u043E\u0442\u0435\u043A\u0430",\n                "gaming": "\u0418\u0433\u0440\u044B",\n                "music": "\u041C\u0443\u0437\u044B\u043A\u0430",\n                "news": "\u041D\u043E\u0432\u043E\u0441\u0442\u0438",\n                "podcasts": "\u041F\u043E\u0434\u043A\u0430\u0441\u0442\u044B",\n                "movies_and_tv": "\u0424\u0438\u043B\u044C\u043C\u044B \u0438 \u0422\u0412",\n                "live": "\u0422\u0440\u0430\u043D\u0441\u043B\u044F\u0446\u0438\u0438",\n                "sports": "\u0421\u043F\u043E\u0440\u0442"\n            }\n        },\n        "unlock_resolution": {\n            "title": "\u0420\u0430\u0437\u0431\u043B\u043E\u043A\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u0440\u0430\u0437\u0440\u0435\u0448\u0435\u043D\u0438\u044F",\n            "description": "\u0421\u043D\u0438\u043C\u0430\u0435\u0442 \u043E\u0433\u0440\u0430\u043D\u0438\u0447\u0435\u043D\u0438\u044F \u043D\u0430 \u0434\u043E\u0441\u0442\u0443\u043F\u043D\u044B\u0435 \u0440\u0430\u0437\u0440\u0435\u0448\u0435\u043D\u0438\u044F \u0438 \u043F\u043E\u0437\u0432\u043E\u043B\u044F\u0435\u0442 \u0441\u043C\u043E\u0442\u0440\u0435\u0442\u044C \u0432\u0438\u0434\u0435\u043E \u0432 \u043B\u044E\u0431\u043E\u043C \u043A\u0430\u0447\u0435\u0441\u0442\u0432\u0435. \u0422\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044F \u043F\u0435\u0440\u0435\u0437\u0430\u043F\u0443\u0441\u043A."\n        },\n        "h264ify": {\n            "title": "\u0424\u0438\u043B\u044C\u0442\u0440 \u043A\u043E\u0434\u0435\u043A\u043E\u0432",\n            "description": "\u041D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0438 \u0444\u0438\u043B\u044C\u0442\u0440\u0430. \u041F\u043E\u0441\u043B\u0435 \u043B\u044E\u0431\u043E\u0433\u043E \u0438\u0437\u043C\u0435\u043D\u0435\u043D\u0438\u044F \u0442\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044F \u043F\u0435\u0440\u0435\u0437\u0430\u043F\u0443\u0441\u043A.",\n            "enable_title": "\u0424\u0438\u043B\u044C\u0442\u0440 \u043A\u043E\u0434\u0435\u043A\u043E\u0432 (h264ify)",\n            "enable_description": "\u0412\u043A\u043B\u044E\u0447\u0430\u0435\u0442 \u0444\u0438\u043B\u044C\u0442\u0440\u0430\u0446\u0438\u044E \u043A\u043E\u0434\u0435\u043A\u043E\u0432. \u041C\u043E\u0436\u0435\u0442 \u043F\u043E\u0432\u044B\u0441\u0438\u0442\u044C \u043F\u0440\u043E\u0438\u0437\u0432\u043E\u0434\u0438\u0442\u0435\u043B\u044C\u043D\u043E\u0441\u0442\u044C \u0438 \u044D\u043D\u0435\u0440\u0433\u043E\u044D\u0444\u0444\u0435\u043A\u0442\u0438\u0432\u043D\u043E\u0441\u0442\u044C \u043D\u0430 \u0441\u043B\u0430\u0431\u044B\u0445 \u0443\u0441\u0442\u0440\u043E\u0439\u0441\u0442\u0432\u0430\u0445, \u043D\u043E \u0443\u043C\u0435\u043D\u044C\u0448\u0438\u0442\u044C \u043A\u043E\u043B\u0438\u0447\u0435\u0441\u0442\u0432\u043E \u0434\u043E\u0441\u0442\u0443\u043F\u043D\u044B\u0445 \u0440\u0430\u0437\u0440\u0435\u0448\u0435\u043D\u0438\u0439.",\n            "disable_codec_title": "\u041E\u0442\u043A\u043B\u044E\u0447\u0438\u0442\u044C {codec}",\n            "disable_codec_description": "\u0411\u043B\u043E\u043A\u0438\u0440\u0443\u0435\u0442 \u043A\u043E\u0434\u0435\u043A {codec}.",\n            "codecs_link": "\u0417\u0430\u0431\u043B\u043E\u043A\u0438\u0440\u043E\u0432\u0430\u043D\u043D\u044B\u0435 \u043A\u043E\u0434\u0435\u043A\u0438"\n        },\n        "block_continue_watching": {\n            "title": "\u0411\u043B\u043E\u043A\u0438\u0440\u043E\u0432\u0430\u0442\u044C \\"\u041F\u0440\u043E\u0434\u043E\u043B\u0436\u0438\u0442\u044C?\\"",\n            "description": "\u0411\u043B\u043E\u043A\u0438\u0440\u0443\u0435\u0442 \u0437\u0430\u043F\u0440\u043E\u0441 \xAB\u0412\u0438\u0434\u0435\u043E \u043F\u0440\u0438\u043E\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D\u043E. \u041F\u0440\u043E\u0434\u043E\u043B\u0436\u0438\u0442\u044C?\xBB \u043F\u043E\u0441\u043B\u0435 \u043F\u0435\u0440\u0438\u043E\u0434\u0430 \u0431\u0435\u0437\u0434\u0435\u0439\u0441\u0442\u0432\u0438\u044F."\n        },\n        "hardware_decoding": {\n            "title": "\u0410\u043F\u043F\u0430\u0440\u0430\u0442\u043D\u043E\u0435 \u0434\u0435\u043A\u043E\u0434\u0438\u0440\u043E\u0432\u0430\u043D\u0438\u0435",\n            "description": "\u0418\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u0442 \u0433\u0440\u0430\u0444\u0438\u0447\u0435\u0441\u043A\u0438\u0439 \u043F\u0440\u043E\u0446\u0435\u0441\u0441\u043E\u0440 \u0434\u043B\u044F \u0434\u0435\u043A\u043E\u0434\u0438\u0440\u043E\u0432\u0430\u043D\u0438\u044F \u0432\u0438\u0434\u0435\u043E. \u041E\u0442\u043A\u043B\u044E\u0447\u0435\u043D\u0438\u0435 \u043C\u043E\u0436\u0435\u0442 \u0443\u0441\u0442\u0440\u0430\u043D\u0438\u0442\u044C \u043F\u0440\u043E\u0431\u043B\u0435\u043C\u044B \u0441 \u0432\u043E\u0441\u043F\u0440\u043E\u0438\u0437\u0432\u0435\u0434\u0435\u043D\u0438\u0435\u043C, \u043D\u043E \u0432 \u0437\u0430\u0432\u0438\u0441\u0438\u043C\u043E\u0441\u0442\u0438 \u043E\u0442 \u043F\u0440\u043E\u0446\u0435\u0441\u0441\u043E\u0440\u0430 \u043C\u043E\u0436\u0435\u0442 \u0432\u044B\u0437\u044B\u0432\u0430\u0442\u044C \u0437\u0430\u0432\u0438\u0441\u0430\u043D\u0438\u044F. \u0422\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044F \u043F\u0435\u0440\u0435\u0437\u0430\u043F\u0443\u0441\u043A."\n        },\n        "wayland_hdr": {\n            "title": "Wayland HDR",\n            "description": "\u0412\u043A\u043B\u044E\u0447\u0430\u0435\u0442 HDR \u043F\u0440\u0438 \u0438\u0441\u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u043D\u0438\u0438 Wayland. \u041D\u0430 \u043D\u0435\u043F\u043E\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u0435\u043C\u044B\u0445 \u043F\u043B\u0430\u0442\u0444\u043E\u0440\u043C\u0430\u0445 \u0446\u0432\u0435\u0442\u0430 \u043C\u043E\u0433\u0443\u0442 \u0432\u044B\u0433\u043B\u044F\u0434\u0435\u0442\u044C \u0431\u043B\u0435\u043A\u043B\u044B\u043C\u0438. \u0422\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044F \u043F\u0435\u0440\u0435\u0437\u0430\u043F\u0443\u0441\u043A."\n        },\n        "low_memory_mode": {\n            "title": "\u0420\u0435\u0436\u0438\u043C \u044D\u043A\u043E\u043D\u043E\u043C\u0438\u0438 \u043F\u0430\u043C\u044F\u0442\u0438",\n            "description": "\u0412\u043A\u043B\u044E\u0447\u0430\u0435\u0442 \u0440\u0435\u0436\u0438\u043C \u044D\u043A\u043E\u043D\u043E\u043C\u0438\u0438 \u043E\u043F\u0435\u0440\u0430\u0442\u0438\u0432\u043D\u043E\u0439 \u043F\u0430\u043C\u044F\u0442\u0438. \u041C\u043E\u0436\u0435\u0442 \u043F\u043E\u0432\u044B\u0441\u0438\u0442\u044C \u043F\u0440\u043E\u0438\u0437\u0432\u043E\u0434\u0438\u0442\u0435\u043B\u044C\u043D\u043E\u0441\u0442\u044C \u0437\u0430 \u0441\u0447\u0451\u0442 \u043E\u0442\u043A\u043B\u044E\u0447\u0435\u043D\u0438\u044F \u043D\u0435\u043A\u043E\u0442\u043E\u0440\u044B\u0445 \u0432\u0438\u0437\u0443\u0430\u043B\u044C\u043D\u044B\u0445 \u044D\u0444\u0444\u0435\u043A\u0442\u043E\u0432. \u0422\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044F \u043F\u0435\u0440\u0435\u0437\u0430\u043F\u0443\u0441\u043A."\n        },\n        "fullscreen": {\n            "title": "\u041F\u043E\u043B\u043D\u043E\u044D\u043A\u0440\u0430\u043D\u043D\u044B\u0439 \u0440\u0435\u0436\u0438\u043C",\n            "description": "\u0412\u043A\u043B\u044E\u0447\u0430\u0435\u0442 \u043F\u043E\u043B\u043D\u043E\u044D\u043A\u0440\u0430\u043D\u043D\u044B\u0439 \u0440\u0435\u0436\u0438\u043C. \u041F\u0440\u0438 \u043F\u043E\u0441\u043B\u0435\u0434\u0443\u044E\u0449\u0438\u0445 \u0437\u0430\u043F\u0443\u0441\u043A\u0430\u0445 VacuumTube \u0431\u0443\u0434\u0435\u0442 \u043E\u0442\u043A\u0440\u044B\u0432\u0430\u0442\u044C\u0441\u044F \u043D\u0430 \u0432\u0435\u0441\u044C \u044D\u043A\u0440\u0430\u043D."\n        },\n        "no_window_decorations": {\n            "title": "\u041E\u0442\u043A\u043B\u044E\u0447\u0438\u0442\u044C \u043E\u0444\u043E\u0440\u043C\u043B\u0435\u043D\u0438\u0435 \u043E\u043A\u043D\u0430",\n            "description": "\u041E\u0442\u043A\u043B\u044E\u0447\u0430\u0435\u0442 \u043E\u0444\u043E\u0440\u043C\u043B\u0435\u043D\u0438\u0435 \u043E\u043A\u043D\u0430, \u0432\u043A\u043B\u044E\u0447\u0430\u044F \u0441\u0442\u0440\u043E\u043A\u0443 \u0437\u0430\u0433\u043E\u043B\u043E\u0432\u043A\u0430 \u0438 \u0433\u0440\u0430\u043D\u0438\u0446\u044B. \u0422\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044F \u043F\u0435\u0440\u0435\u0437\u0430\u043F\u0443\u0441\u043A."\n        },\n        "keep_on_top": {\n            "title": "\u0412\u0441\u0435\u0433\u0434\u0430 \u043F\u043E\u0432\u0435\u0440\u0445 \u043E\u043A\u043E\u043D",\n            "description": "\u041E\u043A\u043D\u043E \u0432\u0441\u0435\u0433\u0434\u0430 \u0431\u0443\u0434\u0435\u0442 \u043F\u043E\u0432\u0435\u0440\u0445 \u043E\u0441\u0442\u0430\u043B\u044C\u043D\u044B\u0445 \u043E\u043A\u043E\u043D. \u041D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0430 \u0441\u043E\u0445\u0440\u0430\u043D\u044F\u0435\u0442\u0441\u044F \u043F\u043E\u0441\u043B\u0435 \u0437\u0430\u043A\u0440\u044B\u0442\u0438\u044F VacuumTube."\n        },\n        "auto_update": {\n            "title": "\u0410\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u043E\u0435 \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u0435",\n            "description": "\u0417\u0430\u0433\u0440\u0443\u0436\u0430\u0435\u0442 \u043D\u043E\u0432\u044B\u0435 \u0432\u0435\u0440\u0441\u0438\u0438 \u0432 \u0444\u043E\u043D\u043E\u0432\u043E\u043C \u0440\u0435\u0436\u0438\u043C\u0435 \u0438 \u0443\u0441\u0442\u0430\u043D\u0430\u0432\u043B\u0438\u0432\u0430\u0435\u0442 \u0438\u0445 \u043F\u043E\u0441\u043B\u0435 \u0437\u0430\u043A\u0440\u044B\u0442\u0438\u044F VacuumTube. \u0415\u0441\u043B\u0438 \u0444\u0443\u043D\u043A\u0446\u0438\u044F \u043E\u0442\u043A\u043B\u044E\u0447\u0435\u043D\u0430, \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u044F \u043C\u043E\u0436\u043D\u043E \u043F\u0440\u043E\u0432\u0435\u0440\u0438\u0442\u044C \u043D\u0430 \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0435 \xAB\u041E \u043D\u0430\u0441\xBB."\n        },\n        "pause_on_blur": {\n            "title": "\u041F\u0430\u0443\u0437\u0430 \u043F\u0440\u0438 \u043F\u043E\u0442\u0435\u0440\u0435 \u0444\u043E\u043A\u0443\u0441\u0430",\n            "description": "\u041F\u0440\u0438\u043E\u0441\u0442\u0430\u043D\u0430\u0432\u043B\u0438\u0432\u0430\u0435\u0442 \u0432\u0438\u0434\u0435\u043E, \u043A\u043E\u0433\u0434\u0430 VacuumTube \u0442\u0435\u0440\u044F\u0435\u0442 \u0444\u043E\u043A\u0443\u0441, \u043D\u0430\u043F\u0440\u0438\u043C\u0435\u0440 \u043F\u0440\u0438 \u043F\u0435\u0440\u0435\u043A\u043B\u044E\u0447\u0435\u043D\u0438\u0438 \u043D\u0430 \u0434\u0440\u0443\u0433\u043E\u0435 \u043E\u043A\u043D\u043E \u0438\u043B\u0438 \u0441\u0432\u043E\u0440\u0430\u0447\u0438\u0432\u0430\u043D\u0438\u0438 \u043F\u0440\u0438\u043B\u043E\u0436\u0435\u043D\u0438\u044F."\n        },\n        "features": {\n            "title": "\u041E\u043F\u0446\u0438\u0438",\n            "notice_title": "\u041D\u0435 \u0432\u0445\u043E\u0434\u0438\u0442 \u0432 \u0441\u0442\u0430\u043D\u0434\u0430\u0440\u0442\u043D\u044B\u0439 \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044C\u0441\u043A\u0438\u0439 \u0441\u0446\u0435\u043D\u0430\u0440\u0438\u0439",\n            "notice_description": "\u042D\u0442\u0438 \u0444\u0443\u043D\u043A\u0446\u0438\u0438 \u0440\u0430\u0441\u0448\u0438\u0440\u044F\u044E\u0442 \u0432\u043E\u0437\u043C\u043E\u0436\u043D\u043E\u0441\u0442\u0438 VacuumTube \u0437\u0430 \u043F\u0440\u0435\u0434\u0435\u043B\u044B \u0441\u0442\u0430\u043D\u0434\u0430\u0440\u0442\u043D\u043E\u0433\u043E \u0438\u043D\u0442\u0435\u0440\u0444\u0435\u0439\u0441\u0430 YouTube \u0434\u043B\u044F \u0422\u0412. \u041E\u043D\u0438 \u0437\u0430\u0432\u0438\u0441\u044F\u0442 \u043E\u0442 \u0432\u043D\u0443\u0442\u0440\u0435\u043D\u043D\u0435\u0433\u043E \u0443\u0441\u0442\u0440\u043E\u0439\u0441\u0442\u0432\u0430 YouTube, \u043F\u043E\u044D\u0442\u043E\u043C\u0443 \u043C\u043E\u0433\u0443\u0442 \u0438\u0437\u043C\u0435\u043D\u0438\u0442\u044C\u0441\u044F \u0438\u043B\u0438 \u043F\u0435\u0440\u0435\u0441\u0442\u0430\u0442\u044C \u0440\u0430\u0431\u043E\u0442\u0430\u0442\u044C.",\n            "enable_title": "\u0412\u043A\u043B\u044E\u0447\u0438\u0442\u044C \u0440\u0430\u0441\u0448\u0438\u0440\u0435\u043D\u043D\u044B\u0435 \u043E\u043F\u0446\u0438\u0438",\n            "enable_description": "\u042F \u043F\u043E\u043D\u0438\u043C\u0430\u044E, \u0447\u0442\u043E \u044D\u0442\u0438 \u0444\u0443\u043D\u043A\u0446\u0438\u0438 \u0432\u044B\u0445\u043E\u0434\u044F\u0442 \u0437\u0430 \u0440\u0430\u043C\u043A\u0438 \u0441\u0442\u0430\u043D\u0434\u0430\u0440\u0442\u043D\u043E\u0433\u043E \u0438\u043D\u0442\u0435\u0440\u0444\u0435\u0439\u0441\u0430 VacuumTube \u0438 \u043C\u043E\u0433\u0443\u0442 \u0438\u0437\u043C\u0435\u043D\u0438\u0442\u044C\u0441\u044F \u0438\u043B\u0438 \u043F\u0435\u0440\u0435\u0441\u0442\u0430\u0442\u044C \u0440\u0430\u0431\u043E\u0442\u0430\u0442\u044C.",\n            "settings": {\n                "music_mode": {\n                    "title": "\u041C\u0443\u0437\u044B\u043A\u0430\u043B\u044C\u043D\u044B\u0439 \u0440\u0435\u0436\u0438\u043C",\n                    "description": "\u0414\u043E\u0431\u0430\u0432\u043B\u044F\u0435\u0442 \u0432 \u043C\u0435\u043D\u044E \u043D\u0430\u0441\u0442\u0440\u043E\u0435\u043A \u0432\u0438\u0434\u0435\u043E \u043F\u0435\u0440\u0435\u043A\u043B\u044E\u0447\u0430\u0442\u0435\u043B\u044C \u043C\u0443\u0437\u044B\u043A\u0430\u043B\u044C\u043D\u043E\u0433\u043E \u0440\u0435\u0436\u0438\u043C\u0430. \u041E\u043D \u043F\u043E\u0437\u0432\u043E\u043B\u044F\u0435\u0442 \u0432\u043E\u0441\u043F\u0440\u043E\u0438\u0437\u0432\u043E\u0434\u0438\u0442\u044C \u0442\u043E\u043B\u044C\u043A\u043E \u0437\u0432\u0443\u043A, \u0438\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u044F \u0432\u0441\u0442\u0440\u043E\u0435\u043D\u043D\u044B\u0439 \u043C\u0443\u0437\u044B\u043A\u0430\u043B\u044C\u043D\u044B\u0439 \u0438\u043D\u0442\u0435\u0440\u0444\u0435\u0439\u0441 YouTube."\n                },\n                "remaining_time": {\n                    "title": "\u041F\u043E\u043A\u0430\u0437\u044B\u0432\u0430\u0442\u044C \u043E\u0441\u0442\u0430\u0432\u0448\u0435\u0435\u0441\u044F \u0432\u0440\u0435\u043C\u044F",\n                    "description": "\u041F\u043E\u043A\u0430\u0437\u044B\u0432\u0430\u0435\u0442 \u0432 \u043E\u0432\u0435\u0440\u043B\u0435\u0435 \u043F\u043B\u0435\u0435\u0440\u0430 \u043E\u0441\u0442\u0430\u0432\u0448\u0435\u0435\u0441\u044F \u0432\u0440\u0435\u043C\u044F \u0432\u0438\u0434\u0435\u043E \u0432\u043C\u0435\u0441\u0442\u043E \u043F\u0440\u043E\u0448\u0435\u0434\u0448\u0435\u0433\u043E. \u0423\u0447\u0438\u0442\u044B\u0432\u0430\u0435\u0442 \u0441\u043A\u043E\u0440\u043E\u0441\u0442\u044C \u0432\u043E\u0441\u043F\u0440\u043E\u0438\u0437\u0432\u0435\u0434\u0435\u043D\u0438\u044F. \u041D\u0430\u0436\u043C\u0438\u0442\u0435 R, \u0447\u0442\u043E\u0431\u044B \u043F\u0435\u0440\u0435\u043A\u043B\u044E\u0447\u0438\u0442\u044C \u0440\u0435\u0436\u0438\u043C \u0434\u043B\u044F \u0442\u0435\u043A\u0443\u0449\u0435\u0433\u043E \u0432\u0438\u0434\u0435\u043E."\n                },\n                "ultrawide": {\n                    "title": "\u041F\u043E\u0434\u0434\u0435\u0440\u0436\u043A\u0430 \u0441\u0432\u0435\u0440\u0445\u0448\u0438\u0440\u043E\u043A\u0438\u0445 \u043C\u043E\u043D\u0438\u0442\u043E\u0440\u043E\u0432",\n                    "description": "\u0420\u0430\u0441\u0442\u044F\u0433\u0438\u0432\u0430\u0435\u0442 \u0438\u043D\u0442\u0435\u0440\u0444\u0435\u0439\u0441 \u043D\u0430 \u0432\u0435\u0441\u044C \u044D\u043A\u0440\u0430\u043D \u0441\u0432\u0435\u0440\u0445\u0448\u0438\u0440\u043E\u043A\u043E\u0433\u043E \u043C\u043E\u043D\u0438\u0442\u043E\u0440\u0430 \u0432 \u043F\u043E\u043B\u043D\u043E\u044D\u043A\u0440\u0430\u043D\u043D\u043E\u043C \u0440\u0435\u0436\u0438\u043C\u0435. \u0420\u0435\u0437\u0443\u043B\u044C\u0442\u0430\u0442 \u043C\u043E\u0436\u0435\u0442 \u0432\u044B\u0433\u043B\u044F\u0434\u0435\u0442\u044C \u043D\u0435\u043F\u0440\u0438\u0432\u044B\u0447\u043D\u043E."\n                }\n            }\n        },\n        "userstyles": {\n            "title": "\u041F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044C\u0441\u043A\u0438\u0439 CSS",\n            "description": "\u041F\u043E\u043C\u0435\u0441\u0442\u0438\u0442\u0435 CSS-\u0444\u0430\u0439\u043B\u044B \u0432 \u043F\u0430\u043F\u043A\u0443 userstyles, \u0447\u0442\u043E\u0431\u044B \u043D\u0430\u0441\u0442\u0440\u043E\u0438\u0442\u044C \u0438\u043D\u0442\u0435\u0440\u0444\u0435\u0439\u0441. \u0418\u0437\u043C\u0435\u043D\u0435\u043D\u0438\u044F \u043F\u0440\u0438\u043C\u0435\u043D\u044F\u044E\u0442\u0441\u044F \u0430\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u0438.",\n            "warn_empty": "\u0421\u0442\u0438\u043B\u0438 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u044B",\n            "open_folder": "\u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043F\u0430\u043F\u043A\u0443 \u0441\u0442\u0438\u043B\u0435\u0439",\n            "enable": "\u0418\u0441\u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u044C \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044C\u0441\u043A\u0438\u0435 \u0441\u0442\u0438\u043B\u0438",\n            "failed_to_load": "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u0441\u0442\u0438\u043B\u044C",\n            "manage_link": "\u0423\u043F\u0440\u0430\u0432\u043B\u044F\u0442\u044C \u0441\u0442\u0438\u043B\u044F\u043C\u0438"\n        },\n        "touch_overlay": {\n            "title": "\u0421\u0435\u043D\u0441\u043E\u0440\u043D\u044B\u0439 \u0432\u0432\u043E\u0434",\n            "description": "\u0412\u043A\u043B\u044E\u0447\u0430\u0435\u0442 \u0441\u0435\u043D\u0441\u043E\u0440\u043D\u043E\u0435 \u0443\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u0435 \u0434\u043B\u044F \u0443\u0434\u043E\u0431\u043D\u043E\u0439 \u043D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u0438 \u043F\u0440\u0438 \u043A\u0430\u0441\u0430\u043D\u0438\u0438 \u044D\u043A\u0440\u0430\u043D\u0430."\n        },\n        "controller_support": {\n            "title": "\u041F\u043E\u0434\u0434\u0435\u0440\u0436\u043A\u0430 \u043A\u043E\u043D\u0442\u0440\u043E\u043B\u043B\u0435\u0440\u0430",\n            "description": "\u0412\u043A\u043B\u044E\u0447\u0430\u0435\u0442 \u043F\u043E\u0434\u0434\u0435\u0440\u0436\u043A\u0443 \u043A\u043E\u043D\u0442\u0440\u043E\u043B\u043B\u0435\u0440\u043E\u0432 \u0434\u043B\u044F \u043D\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u0438 \u0438 \u0443\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u044F \u0432\u0438\u0434\u0435\u043E. \u0422\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044F \u0432\u043D\u0435\u0448\u043D\u0438\u0439 \u043A\u043E\u043D\u0442\u0440\u043E\u043B\u043B\u0435\u0440, \u043D\u0430\u043F\u0440\u0438\u043C\u0435\u0440 Xbox Controller."\n        },\n        "device_discoverability": {\n            "title": "\u041E\u0431\u043D\u0430\u0440\u0443\u0436\u0435\u043D\u0438\u0435 \u0443\u0441\u0442\u0440\u043E\u0439\u0441\u0442\u0432\u0430",\n            "description": "\u041F\u043E\u0437\u0432\u043E\u043B\u044F\u0435\u0442 \u043C\u043E\u0431\u0438\u043B\u044C\u043D\u043E\u043C\u0443 \u043F\u0440\u0438\u043B\u043E\u0436\u0435\u043D\u0438\u044E YouTube \u043E\u0431\u043D\u0430\u0440\u0443\u0436\u0438\u0432\u0430\u0442\u044C VacuumTube \u043D\u0430 \u0443\u0441\u0442\u0440\u043E\u0439\u0441\u0442\u0432\u0430\u0445 \u0432 \u043B\u043E\u043A\u0430\u043B\u044C\u043D\u043E\u0439 \u0441\u0435\u0442\u0438. \u0422\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044F \u043F\u0435\u0440\u0435\u0437\u0430\u043F\u0443\u0441\u043A."\n        },\n        "mac_permissions": {\n            "title": "\u0420\u0430\u0437\u0440\u0435\u0448\u0435\u043D\u0438\u044F",\n            "description": "\u0423\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u0435 \u0440\u0430\u0437\u0440\u0435\u0448\u0435\u043D\u0438\u044F\u043C\u0438 macOS, \u0438\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u043C\u044B\u043C\u0438 VacuumTube.",\n            "microphone_title": "\u041C\u0438\u043A\u0440\u043E\u0444\u043E\u043D",\n            "microphone_description": "\u0418\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u0442\u0441\u044F \u0434\u043B\u044F \u0433\u043E\u043B\u043E\u0441\u043E\u0432\u043E\u0433\u043E \u043F\u043E\u0438\u0441\u043A\u0430 \u0432 YouTube.",\n            "status_label": "\u0421\u0442\u0430\u0442\u0443\u0441: ",\n            "status_loading": "\u041F\u0440\u043E\u0432\u0435\u0440\u043A\u0430 \u0434\u043E\u0441\u0442\u0443\u043F\u0430 \u043A \u043C\u0438\u043A\u0440\u043E\u0444\u043E\u043D\u0443...",\n            "request_microphone": "\u0417\u0430\u043F\u0440\u043E\u0441\u0438\u0442\u044C \u0434\u043E\u0441\u0442\u0443\u043F \u043A \u043C\u0438\u043A\u0440\u043E\u0444\u043E\u043D\u0443",\n            "open_microphone_settings": "\u041E\u0442\u043A\u0440\u044B\u0442\u044C \u043D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0438 \u043C\u0438\u043A\u0440\u043E\u0444\u043E\u043D\u0430 macOS",\n            "reset_microphone": "\u0423\u0441\u0442\u0440\u0430\u043D\u0435\u043D\u0438\u0435 \u043D\u0435\u043F\u043E\u043B\u0430\u0434\u043E\u043A: \u0441\u0431\u0440\u043E\u0441 \u0437\u0430\u043F\u0440\u043E\u0441\u0430 \u0440\u0430\u0437\u0440\u0435\u0448\u0435\u043D\u0438\u044F",\n            "relaunch_app": "\u041F\u0435\u0440\u0435\u0437\u0430\u043F\u0443\u0441\u0442\u0438\u0442\u044C VacuumTube",\n            "restart_required": "\u0418\u0437\u043C\u0435\u043D\u0435\u043D\u0438\u044F \u0432 \u043D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0430\u0445 macOS \u0442\u0440\u0435\u0431\u0443\u044E\u0442 \u043F\u0435\u0440\u0435\u0437\u0430\u043F\u0443\u0441\u043A\u0430 VacuumTube.",\n            "request_granted": "\u0414\u043E\u0441\u0442\u0443\u043F \u043A \u043C\u0438\u043A\u0440\u043E\u0444\u043E\u043D\u0443 \u043F\u0440\u0435\u0434\u043E\u0441\u0442\u0430\u0432\u043B\u0435\u043D. \u0417\u0430\u043F\u0440\u043E\u0441 \u043D\u0435 \u0442\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044F.",\n            "request_not_determined": "macOS \u0432\u0441\u0451 \u0435\u0449\u0451 \u043D\u0435 \u043F\u043E\u043A\u0430\u0437\u0430\u043B\u0430 \u0437\u0430\u043F\u0440\u043E\u0441 \u0440\u0430\u0437\u0440\u0435\u0448\u0435\u043D\u0438\u044F. \u0418\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0439\u0442\u0435 \u0441\u0431\u0440\u043E\u0441 \u0437\u0430\u043F\u0440\u043E\u0441\u0430 \u0440\u0430\u0437\u0440\u0435\u0448\u0435\u043D\u0438\u0439 \u0438 \u043F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0441\u043D\u043E\u0432\u0430.",\n            "request_denied_help": "macOS \u043D\u0435 \u043F\u043E\u043A\u0430\u0436\u0435\u0442 \u0437\u0430\u043F\u0440\u043E\u0441 \u043D\u0430 \u0440\u0430\u0437\u0440\u0435\u0448\u0435\u043D\u0438\u0435 \u043F\u043E\u0441\u043B\u0435 \u043E\u0442\u043A\u0430\u0437\u0430 \u0434\u043E\u0441\u0442\u0443\u043F\u0430. \u0412\u043A\u043B\u044E\u0447\u0438\u0442\u0435 \u0435\u0433\u043E \u0432 \u043D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0430\u0445 macOS, \u0437\u0430\u0442\u0435\u043C \u043F\u0435\u0440\u0435\u0437\u0430\u043F\u0443\u0441\u0442\u0438\u0442\u0435 VacuumTube.",\n            "request_failed": "VacuumTube \u043D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u043F\u0440\u043E\u0441\u0438\u0442\u044C \u0434\u043E\u0441\u0442\u0443\u043F. \u041E\u0442\u043A\u0440\u043E\u0439\u0442\u0435 \u043D\u0430\u0441\u0442\u0440\u043E\u0439\u043A\u0438 macOS \u0438 \u043F\u0435\u0440\u0435\u0437\u0430\u043F\u0443\u0441\u0442\u0438\u0442\u0435 VacuumTube \u043F\u043E\u0441\u043B\u0435 \u043F\u0440\u0435\u0434\u043E\u0441\u0442\u0430\u0432\u043B\u0435\u043D\u0438\u044F \u0434\u043E\u0441\u0442\u0443\u043F\u0430.",\n            "resetting_microphone": "\u0421\u0431\u0440\u043E\u0441 \u0441\u043E\u0445\u0440\u0430\u043D\u0451\u043D\u043D\u043E\u0433\u043E \u0440\u0435\u0448\u0435\u043D\u0438\u044F macOS \u043E \u0434\u043E\u0441\u0442\u0443\u043F\u0435 \u043A \u043C\u0438\u043A\u0440\u043E\u0444\u043E\u043D\u0443",\n            "statuses": {\n                "not-determined": "\u043D\u0435 \u0437\u0430\u043F\u0440\u043E\u0448\u0435\u043D",\n                "granted": "\u0440\u0430\u0437\u0440\u0435\u0448\u0451\u043D",\n                "denied": "\u043E\u0442\u043A\u043B\u043E\u043D\u0451\u043D",\n                "restricted": "\u043E\u0433\u0440\u0430\u043D\u0438\u0447\u0435\u043D",\n                "unknown": "\u043D\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u0435\u043D",\n                "unsupported": "\u043D\u0435 \u043F\u043E\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u0435\u0442\u0441\u044F"\n            }\n        },\n        "about": {\n            "title": "\u041E \u043D\u0430\u0441",\n            "description": "YouTube \u0434\u043B\u044F \u0422\u0412 \u043D\u0430 \u0432\u0430\u0448\u0435\u043C \u041F\u041A",\n            "version": "\u0412\u0435\u0440\u0441\u0438\u044F {version}",\n            "platform_label": "\u041F\u043B\u0430\u0442\u0444\u043E\u0440\u043C\u0430",\n            "installation_label": "\u041C\u0435\u0442\u043E\u0434 \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043A\u0438",\n            "update_method_label": "\u041C\u0435\u0442\u043E\u0434 \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u044F",\n            "view_github": "\u041F\u043E\u0441\u043C\u043E\u0442\u0440\u0435\u0442\u044C \u0432 GitHub",\n            "latest_releases": "\u041F\u043E\u0441\u043B\u0435\u0434\u043D\u0438\u0435 \u0432\u044B\u043F\u0443\u0441\u043A\u0438",\n            "platforms": {\n                "darwin": "macOS",\n                "win32": "Windows",\n                "linux": "Linux"\n            },\n            "architectures": {\n                "arm64": "arm64",\n                "x64": "x64",\n                "ia32": "x86"\n            },\n            "installations": {\n                "application": "\u041F\u0440\u0438\u043B\u043E\u0436\u0435\u043D\u0438\u0435",\n                "installer": "\u0423\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D\u043D\u043E\u0435 \u043F\u0440\u0438\u043B\u043E\u0436\u0435\u043D\u0438\u0435",\n                "portable": "\u041F\u043E\u0440\u0442\u0430\u0442\u0438\u0432\u043D\u043E\u0435 \u043F\u0440\u0438\u043B\u043E\u0436\u0435\u043D\u0438\u0435",\n                "flatpak": "Flatpak",\n                "appimage": "AppImage",\n                "package": "\u0421\u0438\u0441\u0442\u0435\u043C\u043D\u044B\u0439 \u043F\u0430\u043A\u0435\u0442",\n                "development": "\u0421\u0431\u043E\u0440\u043A\u0430 \u0434\u043B\u044F \u0440\u0430\u0437\u0440\u0430\u0431\u043E\u0442\u043A\u0438",\n                "unknown": "\u041D\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043D\u043E"\n            },\n            "update_methods": {\n                "automatic": "\u0410\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u0438\u0439 \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u0449\u0438\u043A",\n                "manual": "\u0420\u0443\u0447\u043D\u0430\u044F \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043A\u0430",\n                "flatpak": "Flatpak"\n            },\n            "updates": {\n                "idle_title": "\u041E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u044F",\n                "idle_detail": "\u041F\u0440\u043E\u0432\u0435\u0440\u0438\u0442\u044C \u043D\u0430\u043B\u0438\u0447\u0438\u0435 \u043D\u043E\u0432\u043E\u0439 \u0432\u0435\u0440\u0441\u0438\u0438 VacuumTube.",\n                "check_now": "\u041F\u0440\u043E\u0432\u0435\u0440\u0438\u0442\u044C \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u044F",\n                "checking_title": "\u041F\u043E\u0438\u0441\u043A \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u0439\u2026",\n                "checking_detail": "\u041F\u043E\u0438\u0441\u043A \u0441\u0430\u043C\u043E\u0433\u043E \u0441\u0432\u0435\u0436\u0435\u0433\u043E \u0432\u044B\u043F\u0443\u0441\u043A\u0430 VacuumTube.",\n                "checking_button": "\u041F\u0440\u043E\u0432\u0435\u0440\u043A\u0430\u2026",\n                "current_title": "\u0423 \u0432\u0430\u0441 \u043F\u043E\u0441\u043B\u0435\u0434\u043D\u044F\u044F \u0432\u0435\u0440\u0441\u0438\u044F",\n                "current_detail": "\u0423\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D\u0430 \u043F\u043E\u0441\u043B\u0435\u0434\u043D\u044F\u044F \u0432\u0435\u0440\u0441\u0438\u044F VacuumTube: {version}.",\n                "check_again": "\u041F\u0440\u043E\u0432\u0435\u0440\u0438\u0442\u044C \u0441\u043D\u043E\u0432\u0430",\n                "available_title": "\u0414\u043E\u0441\u0442\u0443\u043F\u0435\u043D VacuumTube {version}",\n                "available_automatic_detail": "\u0417\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u0435 \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u0435 \u0438 \u043F\u0435\u0440\u0435\u0437\u0430\u043F\u0443\u0441\u0442\u0438\u0442\u0435 VacuumTube \u0434\u043B\u044F \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043A\u0438.",\n                "available_manual_detail": "\u0417\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u0435 \u043F\u043E\u0441\u043B\u0435\u0434\u043D\u044E\u044E \u0441\u0431\u043E\u0440\u043A\u0443 \u0438 \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u0435 \u0432\u0440\u0443\u0447\u043D\u0443\u044E.",\n                "download_update": "\u0417\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u0435",\n                "download_latest": "\u0417\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C \u043F\u043E\u0441\u043B\u0435\u0434\u043D\u044E\u044E \u0432\u0435\u0440\u0441\u0438\u044E",\n                "downloading_title": "\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430 \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u044F\u2026",\n                "downloading_detail": "\u041E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u0435 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043D\u043E \u043D\u0430 {percent}%",\n                "downloading_button": "\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430\u2026 {percent}%",\n                "ready_title": "\u041E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u0435 \u0433\u043E\u0442\u043E\u0432\u043E \u043A \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043A\u0435",\n                "ready_detail": "\u041F\u0435\u0440\u0435\u0437\u0430\u043F\u0443\u0441\u0442\u0438\u0442\u0435 \u043F\u0440\u0438\u043B\u043E\u0436\u0435\u043D\u0438\u0435, \u0447\u0442\u043E\u0431\u044B \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C VacuumTube {version}.",\n                "install_update": "\u041F\u0435\u0440\u0435\u0437\u0430\u043F\u0443\u0441\u0442\u0438\u0442\u044C \u0438 \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u0438\u0442\u044C",\n                "error_title": "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043F\u0440\u043E\u0432\u0435\u0440\u0438\u0442\u044C \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u044F",\n                "error_detail": "\u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u0441\u043E\u0435\u0434\u0438\u043D\u0435\u043D\u0438\u0435 \u0438\u043B\u0438 \u043E\u0442\u043A\u0440\u043E\u0439\u0442\u0435 \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0443 \u0437\u0430\u0433\u0440\u0443\u0437\u043E\u043A",\n                "try_again": "\u041F\u043E\u043F\u0440\u043E\u0431\u043E\u0432\u0430\u0442\u044C \u0441\u043D\u043E\u0432\u0430",\n                "development_title": "\u0421\u0431\u043E\u0440\u043A\u0430 \u0434\u043B\u044F \u0440\u0430\u0437\u0440\u0430\u0431\u043E\u0442\u043A\u0438",\n                "development_detail": "\u0410\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u0438\u0435 \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u044F \u0434\u043E\u0441\u0442\u0443\u043F\u043D\u044B \u0434\u043B\u044F \u0443\u043F\u0430\u043A\u043E\u0432\u0430\u043D\u043D\u044B\u0445 \u0441\u0431\u043E\u0440\u043E\u043A.",\n                "unsupported_title": "\u041F\u0440\u043E\u0432\u0435\u0440\u043A\u0430 \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u0439 \u043D\u0435\u0434\u043E\u0441\u0442\u0443\u043F\u043D\u0430",\n                "unsupported_detail": "\u0418\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0439\u0442\u0435 \u043C\u0435\u0442\u043E\u0434\u044B \u0441\u043D\u0438\u0437\u0443 \u0434\u043B\u044F \u043F\u0440\u043E\u0432\u0435\u0440\u043A\u0438 \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u0439.",\n                "view_update_source": "\u041F\u0440\u043E\u0441\u043C\u043E\u0442\u0440\u0435\u0442\u044C \u0438\u0441\u0445\u043E\u0434\u043D\u0438\u043A\u0438 \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u044F",\n                "view_releases": "\u041F\u043E\u0441\u043C\u043E\u0442\u0440\u0435\u0442\u044C \u043F\u043E\u0441\u043B\u0435\u0434\u043D\u0438\u0435 \u0432\u044B\u043F\u0443\u0441\u043A\u0438"\n            }\n        }\n    },\n    "sponsorblock": {\n        "sponsor_skipped": "\u0421\u043F\u043E\u043D\u0441\u043E\u0440\u0441\u043A\u0438\u0439 \u0441\u0435\u0433\u043C\u0435\u043D\u0442 \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D",\n        "selfpromo_skipped": "\u0421\u0430\u043C\u043E\u0440\u0435\u043A\u043B\u0430\u043C\u0430 \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u0430",\n        "interaction_skipped": "\u041F\u0440\u0438\u0437\u044B\u0432 \u043A \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u044E \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D",\n        "intro_skipped": "\u0418\u043D\u0442\u0440\u043E \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043E",\n        "outro_skipped": "\u041E\u043A\u043E\u043D\u0447\u0430\u043D\u0438\u0435 \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043E",\n        "preview_skipped": "\u041F\u0440\u0435\u0434\u0432\u0430\u0440\u0438\u0442\u0435\u043B\u044C\u043D\u044B\u0439 \u043E\u0431\u0437\u043E\u0440 \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D",\n        "hook_skipped": "\u041F\u0440\u0438\u0432\u0435\u0442\u0441\u0442\u0432\u0438\u0435 \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u043E",\n        "filler_skipped": "\u041E\u0442\u0441\u0442\u0443\u043F\u043B\u0435\u043D\u0438\u0435 \u0438\u043B\u0438 \u0448\u0443\u0442\u043A\u0430 \u043F\u0440\u043E\u043F\u0443\u0449\u0435\u043D\u044B"\n    },\n    "donate_unused": {\n        "setting": {\n            "title": "\u041F\u043E\u0436\u0435\u0440\u0442\u0432\u043E\u0432\u0430\u0442\u044C",\n            "description": "\u041F\u043E\u0434\u0434\u0435\u0440\u0436\u0438\u0442\u0435 \u0440\u0430\u0437\u0440\u0430\u0431\u043E\u0442\u043A\u0443 VacuumTube \u043F\u043E\u0436\u0435\u0440\u0442\u0432\u043E\u0432\u0430\u043D\u0438\u0435\u043C. \u041F\u0435\u0440\u0435\u0439\u0434\u0438\u0442\u0435 \u043D\u0430 shy.rocks/donate \u0438\u043B\u0438 \u043D\u0430\u0436\u043C\u0438\u0442\u0435 \u043A\u043D\u043E\u043F\u043A\u0443 \u043D\u0438\u0436\u0435, \u0447\u0442\u043E\u0431\u044B \u043E\u0442\u043A\u0440\u044B\u0442\u044C \u0441\u0442\u0440\u0430\u043D\u0438\u0446\u0443 \u043F\u043E\u0436\u0435\u0440\u0442\u0432\u043E\u0432\u0430\u043D\u0438\u0439 \u0432 \u0431\u0440\u0430\u0443\u0437\u0435\u0440\u0435.",\n            "button_label": "\u041F\u043E\u0436\u0435\u0440\u0442\u0432\u043E\u0432\u0430\u0442\u044C"\n        }\n    }\n}\n' }, "/vt/locale/sr-Latn.json": { "text": `{
    "general": {
        "video_copied": "Link videa kopiran u me\u0111uspremnik",
        "dislikes": "Palac dole"
    },
    "settings": {
        "adblock": {
            "title": "Blokator Oglasa",
            "description": "Besprekorno blokira video i fid oglase, nije podlo\u017Ean YouTube metodama protiv blokiranja"
        },
        "dearrow": {
            "title": "DeArrow",
            "description": "Zamenjuje naslove i sli\u010Dice ta\u010Dnijim, manje senzacionalisti\u010Dkim verzijama iz javne baze podataka koju su prikupili korisnici. Usporava u\u010Ditavanje, jer mora da preuzme informacije sa servera"
        },
        "dislikes": {
            "title": "Vratite donji palac",
            "description": "Koristi YouTube API za vra\u0107anje palca prema dole (returnyoutubedislike.com) za ponovno dodavanje oznake 'nesvi\u0111a mi se' na YouTube. Usporava u\u010Ditavanje videa, jer mora da preuzme informacije sa servera"
        },
        "hardware_decoding": {
            "title": "Hardversko Dekodiranje",
            "description": "Koristi va\u0161u grafi\u010Dku karticu za dekodiranje videa kada je to moguc\u0301e. Onemoguc\u0301avanje ove opcije mo\u017Ee re\u0161iti probleme sa reprodukcijom, ali mo\u017Ee izazvati ka\u0161njenje u zavisnosti od va\u0161eg procesora. Ponovo pokrenite sistem nakon prebacivanja."
        },
        "low_memory_mode": {
            "title": "Re\u017Eim za Nisku Memoriju",
            "description": "Nala\u017Ee YouTube-u da omoguc\u0301i re\u017Eim sa malo memorije, \u0161to mo\u017Ee pobolj\u0161ati reprodukciju na sporijim ure\u0111ajima, ali na \u0161tetu nekih vizuelnih efekata. Ponovo pokrenite sistem nakon prebacivanja."
        },
        "fullscreen": {
            "title": "Ceo Ekran",
            "description": "Omoguc\u0301ava ceo ekran i \u010Dini da VacuumTube uvek radi u re\u017Eimu celog ekrana."
        },
        "keep_on_top": {
            "title": "Ostani na vrhu",
            "description": "Omoguc\u0301ava opciju \u201EOstani na vrhu\u201C i pokrec\u0301e VacuumTube sa prozorom pri\u010Dvr\u0161c\u0301enim preko svakog drugog prozora."
        },
        "userstyles": {
            "title": "Prilago\u0111eni CSS (korisni\u010Dki stilovi)",
            "description": "Omogu\u0107ava ubrizgavanje prilago\u0111enih CSS stilova. Postavite svoje .css datoteke u mapu 'korisni\u010Dki stilovi'."
        }
    }
}
` }, "/vt/locale/sv.json": { "text": '{\n    "general": {\n        "video_copied": "Videol\xE4nk kopierad till urklipp",\n        "dislikes": "Ogillat"\n    },\n    "settings": {\n        "adblock": {\n            "title": "Reklamblockering",\n            "description": "Blockerar s\xF6ml\xF6st reklam i videor och fl\xF6det. P\xE5verkas ej av YouTube:s metoder f\xF6r att f\xF6rhindra reklamblockering"\n        },\n        "dearrow": {\n            "title": "DeArrow",\n            "description": "Ers\xE4tter titlar och indexbilder med mer korrekta och mindre sensationella varianter fr\xE5n en publikt crowdsourcad databas. Saktar ner sidladdning, eftersom att DeArrow m\xE5ste h\xE4mta informationen fr\xE5n servern"\n        },\n        "dislikes": {\n            "title": "\xC5terinf\xF6r Ogillningar",\n            "description": "Anv\xE4nder Return YouTube Dislike API (returnyoutubedislike.com) f\xF6r att \xE5terinf\xF6ra ogillningar till YouTube. Saktar ner videoladdning, eftersom information m\xE5ste h\xE4mtas fr\xE5n servern"\n        },\n        "hardware_decoding": {\n            "title": "H\xE5rdvaruavkodning",\n            "description": "Anv\xE4nder din enhets grafikkort f\xF6r att avkoda videor n\xE4r det \xE4r m\xF6jligt. Att avaktivera den h\xE4r funktionen kan hj\xE4lpa med uppspelningsproblem, men kan orsaka lagg beroende p\xE5 din processor. Starta om appen efter du har \xE4ndrat inst\xE4llningen."\n        },\n        "low_memory_mode": {\n            "title": "Begr\xE4nsat minne",\n            "description": "S\xE4ger till YouTube att aktivera l\xE4get f\xF6r begr\xE4nsat minne, vilket kan f\xF6rb\xE4ttra prestanda p\xE5 l\xE4ngsammare enheter i utbyte mot borttagning av vissa visuella effekter. Starta om appen efter du har \xE4ndrat inst\xE4llningen."\n        },\n        "fullscreen": {\n            "title": "Helsk\xE4rm",\n            "description": "Aktiverar helsk\xE4rm och ser till att VacuumTube alltid startar i helsk\xE4rmsl\xE4ge."\n        },\n        "keep_on_top": {\n            "title": "F\xE4st h\xF6gst upp",\n            "description": "Aktiverar \\"f\xE4st h\xF6gst upp\\", och startar VacuumTube med sitt f\xF6nster f\xE4st ovanf\xF6r alla andra f\xF6nster."\n        },\n        "userstyles": {\n            "title": "Egen CSS (Stilmallar)",\n            "description": "Aktiverar egna stilmallar i CSS. L\xE4gg dina .css-files i mappen userstyles."\n        }\n    }\n}' }, "/vt/locale/tr.json": { "text": '{\n    "general": {\n        "video_copied": "Video ba\u011Flant\u0131s\u0131 panoya kopyaland\u0131",\n		"dislikes": "Be\u011Fenmeyenler"\n    },\n    "settings": {\n        "adblock": {\n            "title": "Reklam Engelleyici",\n            "description": "Video ve yay\u0131nc\u0131 reklamlar\u0131n\u0131 engeller, YouTube engelleyicileri engelleme y\xF6ntemlerine tabi de\u011Fildir"\n        },\n		"dearrow": {\n            "title": "DeArrow",\n            "description": "Ba\u015Fl\u0131klar\u0131 ve k\xFC\xE7\xFCk resimleri, herkese a\xE7\u0131k veritaban\u0131ndan al\u0131nan daha do\u011Fru ve daha az sansasyonel s\xFCr\xFCmlerle de\u011Fi\u015Ftirir. Sunucudan bilgi almas\u0131 gerekti\u011Finden y\xFCklemeyi yava\u015Flat\u0131r"\n        },\n		"dislikes": {\n            "title": "Be\u011Fenmemeleri G\xF6ster",\n            "description": "YouTube be\u011Fenmeyenleri g\xF6stermek i\xE7in YouTube Be\u011Fenmeme API (returnyoutubedislike.com) kullan\u0131r. Sunucudan bilgi almas\u0131 gerekti\u011Finden video y\xFCklemesini yava\u015Flat\u0131r"\n        },\n        "hardware_decoding": {\n            "title": "Donan\u0131msal Kod \xC7\xF6z\xFCc\xFC",\n            "description": "M\xFCmk\xFCnse videolar\u0131 \xE7\xF6zmek i\xE7in GPU kullan\u0131r. Bu \xF6zelli\u011Fi devre d\u0131\u015F\u0131 b\u0131rakmak oynatma sorunlar\u0131n\u0131 \xE7\xF6zebilir, ancak CPU \xFCst\xFCnde gecikmeye neden olabilir. De\u011Fi\u015Ftirdikten sonra yeniden ba\u015Flat\u0131n."\n        },\n        "low_memory_mode": {\n            "title": "D\xFC\u015F\xFCk Bellek Modu",\n            "description": "YouTube d\xFC\u015F\xFCk bellek modu etkinle\u015Ftirmesini sa\u011Flar. Baz\u0131 g\xF6rsel efektlerden \xF6d\xFCn vererek daha yava\u015F cihazlarda performans\u0131 art\u0131rabilir. De\u011Fi\u015Ftirdikten sonra yeniden ba\u015Flat\u0131n."\n        },\n        "fullscreen": {\n            "title": "Tam Ekran",\n            "description": "Tam ekran \xF6zelli\u011Fini etkinle\u015Ftirir ve VacuumTube her zaman tam ekranda ba\u015Flat\u0131l\u0131r."\n        },\n        "keep_on_top": {\n            "title": "\xDCstte Tut",\n            "description": "\xDCstte Tutmay\u0131 etkinle\u015Ftirir ve VacuumTube t\xFCm pencerelerin \xFCst\xFCne sabitlenerek ba\u015Flat\u0131l\u0131r."\n        },\n        "userstyles": {\n            "title": "\xD6zel CSS (Kullan\u0131c\u0131 Stilleri)",\n            "description": "\xD6zel CSS stillerinin eklenmesini sa\u011Flar. .css dosyalar\u0131n\u0131z\u0131 userstyles klas\xF6r\xFCne yerle\u015Ftirin."\n        }\n    }\n}\n' }, "/vt/assets/icon.png": { "base64": "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" } };
    }
  });

  // tv/shims/path.js
  var require_path = __commonJS({
    "tv/shims/path.js"(exports, module) {
      function normalize(p) {
        const abs = p.startsWith("/");
        const out = [];
        for (const part of p.split("/")) {
          if (!part || part === ".") continue;
          if (part === "..") {
            if (out.length && out[out.length - 1] !== "..") out.pop();
            else if (!abs) out.push("..");
          } else out.push(part);
        }
        return (abs ? "/" : "") + out.join("/");
      }
      var join = (...parts) => normalize(parts.filter(Boolean).join("/"));
      var dirname = (p) => {
        const n = normalize(p);
        const i = n.lastIndexOf("/");
        return i <= 0 ? n.startsWith("/") ? "/" : "." : n.slice(0, i);
      };
      var basename = (p, ext) => {
        let b = normalize(p).split("/").pop() || "";
        if (ext && b.endsWith(ext)) b = b.slice(0, -ext.length);
        return b;
      };
      var extname = (p) => {
        const b = basename(p);
        const i = b.lastIndexOf(".");
        return i > 0 ? b.slice(i) : "";
      };
      module.exports = { normalize, join, resolve: join, dirname, basename, extname, sep: "/" };
    }
  });

  // tv/shims/fs.js
  var require_fs = __commonJS({
    "tv/shims/fs.js"(exports, module) {
      var files = require_vt_files();
      function norm(p) {
        return require_path().normalize(p);
      }
      function file(p) {
        const key = norm(p);
        if (!(key in files)) {
          const err = new Error(`ENOENT: ${key}`);
          err.code = "ENOENT";
          throw err;
        }
        const entry = files[key];
        return {
          text: entry.text,
          base64: entry.base64
        };
      }
      function readFileSync(p, encoding) {
        const f = file(p);
        if (f.text !== void 0) return encoding ? f.text : { toString: (enc) => enc === "base64" ? btoa(unescape(encodeURIComponent(f.text))) : f.text };
        return encoding ? atob(f.base64) : { toString: (enc) => enc === "base64" ? f.base64 : atob(f.base64) };
      }
      function readdirSync(dir, opts) {
        const prefix = norm(dir).replace(/\/$/, "") + "/";
        const names = /* @__PURE__ */ new Set();
        const dirs = /* @__PURE__ */ new Set();
        for (const key of Object.keys(files)) {
          if (!key.startsWith(prefix)) continue;
          const rest = key.slice(prefix.length);
          const name = rest.split("/")[0];
          names.add(name);
          if (rest.includes("/")) dirs.add(name);
        }
        const list = [...names].sort();
        if (opts && opts.withFileTypes) return list.map((name) => ({ name, isDirectory: () => dirs.has(name), isFile: () => !dirs.has(name) }));
        return list;
      }
      module.exports = {
        readFileSync,
        readdirSync,
        existsSync: (p) => norm(p) in files,
        promises: {
          readFile: async (p, enc) => readFileSync(p, enc),
          readdir: async (p, opts) => readdirSync(p, opts)
        }
      };
    }
  });

  // tv/vacuumtube/preload/util/localeProvider.js
  var require_localeProvider = __commonJS({
    "tv/vacuumtube/preload/util/localeProvider.js"(exports, module) {
      var __dirname = "/vt/src/preload/util";
      var fs = require_fs();
      var path = require_path();
      var functions = require_functions();
      var localeFolder = path.join(__dirname, "../", "../", "../", "locale");
      var locale;
      var translation = { language: "en", name: "English", missing: 0, complete: true };
      function countMissing(base, partial) {
        let missing = 0;
        for (const [key, value] of Object.entries(base)) {
          if (key.endsWith("_unused")) continue;
          const translated = partial?.[key];
          if (value && typeof value === "object") {
            missing += countMissing(value, translated || {});
          } else if (translated === void 0) {
            missing++;
          }
        }
        return missing;
      }
      function languageName(code) {
        try {
          return new Intl.DisplayNames([code], { type: "language" }).of(code) || code;
        } catch {
          return code;
        }
      }
      functions.waitForCondition(() => !!window.ytcfg).then(async () => {
        const lang = window.ytcfg.data_.HL;
        const broadLang = lang.split("-")[0];
        const localeFiles = await fs.promises.readdir(localeFolder);
        const baseLocaleStr = await fs.promises.readFile(path.join(localeFolder, "en.json"), "utf-8");
        const baseLocale = JSON.parse(baseLocaleStr);
        let langFile = null;
        if (localeFiles.includes(`${lang}.json`)) {
          langFile = `${lang}.json`;
        } else if (localeFiles.includes(`${broadLang}.json`)) {
          langFile = `${broadLang}.json`;
        }
        const str = await fs.promises.readFile(path.join(localeFolder, langFile || "en.json"), "utf-8");
        const partialLocale = JSON.parse(str);
        const language = langFile ? langFile.replace(".json", "") : broadLang;
        const missing = broadLang === "en" ? 0 : countMissing(baseLocale, langFile ? partialLocale : {});
        translation = {
          language,
          name: languageName(language),
          missing,
          complete: missing === 0
        };
        locale = functions.deepMerge(baseLocale, partialLocale);
      });
      async function waitUntilAvailable() {
        await functions.waitForCondition(() => !!locale);
      }
      function getLocale() {
        return locale;
      }
      function getTranslation() {
        return translation;
      }
      module.exports = {
        waitUntilAvailable,
        getLocale,
        getTranslation
      };
    }
  });

  // tv/vacuumtube/preload/modules/controller-support.js
  var require_controller_support = __commonJS({
    "tv/vacuumtube/preload/modules/controller-support.js"(exports, module) {
      var { ipcRenderer } = require_electron();
      var controller = require_controller();
      var ui = require_ui();
      var localeProvider = require_localeProvider();
      var configManager = require_config();
      var config = configManager.get();
      module.exports = async () => {
        const customKeyCodes = {
          //custom VacuumTube mappings
          32776: 16001,
          //select -> vt volume down
          32777: 16002,
          //start  -> vt volume up
          32778: 16e3
          //l3     -> vt mute
        };
        let hasPressedAnyButton = false;
        const runningOnSteam = await ipcRenderer.invoke("is-steam");
        if (runningOnSteam) {
          setTimeout(async () => {
            if (!hasPressedAnyButton) {
              await localeProvider.waitUntilAvailable();
              const locale = localeProvider.getLocale();
              ui.toast("VacuumTube", locale.general.steam_controller_notice);
            }
          }, 15e3);
        }
        controller.on("down", (e) => {
          hasPressedAnyButton = true;
          simulateKeyDown(customKeyCodes[e.code] ?? e.code);
        });
        controller.on("up", (e) => {
          simulateKeyUp(customKeyCodes[e.code] ?? e.code);
        });
        function simulateKeyDown(keyCode) {
          if (!config.controller_support) return;
          let event = new Event("keydown");
          event.keyCode = keyCode;
          document.dispatchEvent(event);
        }
        function simulateKeyUp(keyCode) {
          if (!config.controller_support) return;
          let event = new Event("keyup");
          event.keyCode = keyCode;
          document.dispatchEvent(event);
        }
      };
    }
  });

  // tv/vacuumtube/preload/modules/css.js
  var require_css2 = __commonJS({
    "tv/vacuumtube/preload/modules/css.js"(exports, module) {
      var __dirname = "/vt/src/preload/modules";
      var fs = require_fs();
      var path = require_path();
      var css = require_css();
      var cssPath = path.join(__dirname, "../", "style.css");
      var text = fs.readFileSync(cssPath, "utf-8");
      module.exports = () => {
        css.inject("patches", text);
      };
    }
  });

  // tv/vacuumtube/preload/modules/dearrow.js
  var require_dearrow = __commonJS({
    "tv/vacuumtube/preload/modules/dearrow.js"(exports, module) {
      var xhrModifiers = require_xhrModifiers();
      var configManager = require_config();
      var config = configManager.get();
      var cache = {};
      var timeout = 2e3;
      async function getBranding(id) {
        if (id in cache) {
          return cache[id];
        }
        let res = await fetch(`https://sponsor.ajay.app/api/branding?videoID=${id}`, { signal: AbortSignal.timeout(timeout) });
        if (res.status === 404) return null;
        if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
        let data = await res.json();
        cache[id] = data;
        return data;
      }
      function getThumbnail(id) {
        return `https://dearrow-thumb.ajay.app/api/v1/getThumbnail?videoID=${id}`;
      }
      module.exports = () => {
        xhrModifiers.addResponseModifier(async (url, text) => {
          if (!config.dearrow) return;
          if (!url.startsWith("/youtubei/v1/browse") && !url.startsWith("/youtubei/v1/search") && !url.startsWith("/youtubei/v1/next")) {
            return;
          }
          let json = JSON.parse(text);
          let items = [];
          if (json.continuationContents?.horizontalListContinuation || json.continuationContents?.gridContinuation) {
            if (json.continuationContents.horizontalListContinuation?.items) {
              items = json.continuationContents.horizontalListContinuation.items;
            } else if (json.continuationContents.gridContinuation?.items) {
              items = json.continuationContents.gridContinuation.items;
            }
            if (!items) return;
          } else {
            let contents = [];
            if (url.startsWith("/youtubei/v1/browse")) {
              if (json.contents?.tvBrowseRenderer?.content?.tvSecondaryNavRenderer?.sections) {
                let tvSecondaryNavRenderer = json.contents.tvBrowseRenderer.content.tvSecondaryNavRenderer;
                for (let section of tvSecondaryNavRenderer.sections) {
                  if (!section.tvSecondaryNavSectionRenderer?.tabs) continue;
                  let tab = section.tvSecondaryNavSectionRenderer.tabs[0];
                  contents = tab.tabRenderer?.content?.tvSurfaceContentRenderer?.content?.sectionListRenderer?.contents;
                }
              } else if (json.contents?.tvBrowseRenderer?.content?.tvSurfaceContentRenderer?.content?.sectionListRenderer?.contents) {
                contents = json.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer.content.sectionListRenderer.contents;
              } else if (json.contents?.tvBrowseRenderer?.content?.tvSurfaceContentRenderer?.content?.gridRenderer) {
                contents = [json.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer.content];
              } else if (json.continuationContents?.tvSurfaceContentContinuation?.content?.sectionListRenderer?.contents) {
                contents = json.continuationContents.tvSurfaceContentContinuation.content.sectionListRenderer.contents;
              }
            } else if (url.startsWith("/youtubei/v1/search")) {
              contents = json.contents?.sectionListRenderer?.contents;
            } else if (url.startsWith("/youtubei/v1/next")) {
              contents = json.contents?.singleColumnWatchNextResults?.pivot?.sectionListRenderer?.contents;
            }
            if (!contents) return;
            for (let content of contents) {
              let someItems;
              if (content.shelfRenderer) {
                someItems = content.shelfRenderer.content.horizontalListRenderer?.items;
              } else if (content.gridRenderer) {
                someItems = content.gridRenderer.items;
              }
              if (!someItems) continue;
              items = [...items, ...someItems];
            }
          }
          let promises = [];
          for (let item of items) {
            if (!item.tileRenderer) continue;
            if (item.tileRenderer.contentType !== "TILE_CONTENT_TYPE_VIDEO") continue;
            let id = item.tileRenderer.contentId;
            promises.push((async () => {
              try {
                if (!item.tileRenderer.metadata) return;
                let branding = await getBranding(id);
                if (!branding) return;
                let duration = branding.videoDuration;
                if (!duration) return;
                let goodTitle = branding.titles.find((t) => t.locked || t.votes >= 0);
                if (goodTitle) {
                  let words = goodTitle.title.split(" ");
                  words = words.map((w) => {
                    if (w.startsWith(">")) {
                      w = w.slice(1);
                    }
                    return w;
                  });
                  let title = words.join(" ");
                  item.tileRenderer.metadata.tileMetadataRenderer.title.simpleText = title;
                }
                let newThumbnail = getThumbnail(id);
                item.tileRenderer.header.tileHeaderRenderer.thumbnail.thumbnails[0].url = newThumbnail;
              } catch (err) {
                console.error("getting and applying dearrow branding failed", err);
              }
            })());
          }
          if (promises.length > 0) {
            await Promise.all(promises);
          }
          return JSON.stringify(json);
        });
      };
    }
  });

  // tv/vacuumtube/preload/modules/disable-direct-sign-in.js
  var require_disable_direct_sign_in = __commonJS({
    "tv/vacuumtube/preload/modules/disable-direct-sign-in.js"(exports, module) {
      var configOverrides = require_configOverrides();
      module.exports = async () => {
        configOverrides.environmentOverrides.push({
          flags: {
            enable_direct_sign_in: false
          }
        });
      };
    }
  });

  // tv/vacuumtube/preload/modules/encryption-notice.js
  var require_encryption_notice = __commonJS({
    "tv/vacuumtube/preload/modules/encryption-notice.js"(exports, module) {
      var rcMod = require_resolveCommandModifiers();
      var localeProvider = require_localeProvider();
      module.exports = async () => {
        await localeProvider.waitUntilAvailable();
        let locale = localeProvider.getLocale();
        rcMod.addInputModifier((c) => {
          if (c.openPopupAction?.uniqueId === "unknown-player-error") {
            return {
              openPopupAction: {
                popupType: "FULLSCREEN_OVERLAY",
                uniqueId: "vt-player-error",
                popup: {
                  overlaySectionRenderer: {
                    overlay: {
                      overlayTwoPanelRenderer: {
                        actionPanel: {
                          overlayPanelRenderer: {
                            header: {
                              overlayPanelHeaderRenderer: {
                                title: {
                                  simpleText: locale.general.encryption_error.title
                                },
                                subtitle: {
                                  simpleText: locale.general.encryption_error.text
                                }
                              }
                            },
                            footer: {
                              overlayPanelItemListRenderer: {
                                items: [
                                  {
                                    compactLinkRenderer: {
                                      title: {
                                        simpleText: locale.general.encryption_error.switch_accounts
                                      },
                                      serviceEndpoint: {
                                        commandExecutorCommand: {
                                          commands: [
                                            {
                                              clientActionEndpoint: {
                                                action: { actionType: "OPEN_SIGN_IN_PROMPT" }
                                              }
                                            }
                                          ]
                                        }
                                      }
                                    }
                                  },
                                  {
                                    compactLinkRenderer: {
                                      title: {
                                        simpleText: locale.general.encryption_error.okay
                                      },
                                      serviceEndpoint: {
                                        commandExecutorCommand: {
                                          commands: [
                                            { signalAction: { signal: "HISTORY_BACK" } },
                                            { signalAction: { signal: "CLOSE_POPUP" } }
                                          ]
                                        }
                                      }
                                    }
                                  }
                                ]
                              }
                            }
                          }
                        },
                        backButton: {
                          buttonRenderer: {
                            icon: { iconType: "DISMISSAL" },
                            command: {
                              commandExecutorCommand: {
                                commands: [
                                  { signalAction: { signal: "HISTORY_BACK" } },
                                  { signalAction: { signal: "CLOSE_POPUP" } }
                                ]
                              }
                            }
                          }
                        }
                      }
                    },
                    dismissalCommand: {
                      commandExecutorCommand: {
                        commands: [
                          { signalAction: { signal: "HISTORY_BACK" } },
                          { signalAction: { signal: "CLOSE_POPUP" } }
                        ]
                      }
                    }
                  }
                }
              }
            };
          }
          return c;
        });
      };
    }
  });

  // tv/vacuumtube/preload/modules/fix-exit.js
  var require_fix_exit = __commonJS({
    "tv/vacuumtube/preload/modules/fix-exit.js"(exports, module) {
      var rcMod = require_resolveCommandModifiers();
      module.exports = () => {
        rcMod.addInputModifier((command) => {
          if (!command.commandExecutorCommand || !command.commandExecutorCommand.commands) return command;
          let exitCommand = command.commandExecutorCommand.commands.find((c) => c.signalAction?.signal === "EXIT_APP");
          if (!exitCommand) return command;
          window.close();
          return false;
        });
      };
    }
  });

  // tv/vacuumtube/preload/modules/fix-reloads.js
  var require_fix_reloads = __commonJS({
    "tv/vacuumtube/preload/modules/fix-reloads.js"(exports, module) {
      var { ipcRenderer } = require_electron();
      var rcMod = require_resolveCommandModifiers();
      module.exports = () => {
        rcMod.addInputModifier((command) => {
          if (!command.signalAction || !command.signalAction.signal || command.signalAction.signal !== "RELOAD_PAGE") return command;
          ipcRenderer.invoke("reload");
          return false;
        });
      };
    }
  });

  // tv/vacuumtube/preload/modules/fix-voice.js
  var require_fix_voice = __commonJS({
    "tv/vacuumtube/preload/modules/fix-voice.js"(exports, module) {
      var { ipcRenderer } = require_electron();
      var configOverrides = require_configOverrides();
      var functions = require_functions();
      var pendingAudioCapture = null;
      function hasAudioConstraint(constraints) {
        if (!constraints || typeof constraints !== "object") return false;
        if (!Object.prototype.hasOwnProperty.call(constraints, "audio")) return false;
        return constraints.audio !== false;
      }
      function createNotAllowedError(message) {
        if (typeof DOMException === "function") {
          return new DOMException(message, "NotAllowedError");
        }
        const error = new Error(message);
        error.name = "NotAllowedError";
        return error;
      }
      async function getNativeMicrophoneStatus() {
        try {
          return await ipcRenderer.invoke("request-microphone-permission");
        } catch (err) {
          console.error("[Voice] Failed to request microphone permission:", err);
          return "unknown";
        }
      }
      function guardGetUserMedia() {
        if (true) return;
        if (!navigator.mediaDevices?.getUserMedia) return;
        if (navigator.mediaDevices.getUserMedia.vtMicrophoneGuarded) return;
        const originalGetUserMedia = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
        const guardedGetUserMedia = (constraints) => {
          if (!hasAudioConstraint(constraints)) return originalGetUserMedia(constraints);
          if (pendingAudioCapture) return pendingAudioCapture;
          pendingAudioCapture = (async () => {
            const status = await getNativeMicrophoneStatus();
            if (status !== "granted") {
              throw createNotAllowedError(`Microphone permission is ${status}`);
            }
            return originalGetUserMedia(constraints);
          })().finally(() => {
            pendingAudioCapture = null;
          });
          return pendingAudioCapture;
        };
        guardedGetUserMedia.vtMicrophoneGuarded = true;
        try {
          navigator.mediaDevices.getUserMedia = guardedGetUserMedia;
        } catch {
        }
        if (navigator.mediaDevices.getUserMedia !== guardedGetUserMedia) {
          try {
            Object.defineProperty(navigator.mediaDevices, "getUserMedia", {
              configurable: true,
              writable: true,
              value: guardedGetUserMedia
            });
          } catch (err) {
            console.error("[Voice] Failed to install getUserMedia guard:", err);
          }
        }
      }
      module.exports = () => {
        configOverrides.overrideEnv("env_enableMediaStreams", true);
        guardGetUserMedia();
        if (false) {
          functions.waitForCondition(() => !!navigator.mediaDevices?.getUserMedia).then(guardGetUserMedia);
        }
      };
    }
  });

  // tv/vacuumtube/preload/modules/guide-tabs.js
  var require_guide_tabs = __commonJS({
    "tv/vacuumtube/preload/modules/guide-tabs.js"(exports, module) {
      var xhrModifiers = require_xhrModifiers();
      var configManager = require_config();
      var config = configManager.get();
      var map = {
        "SEARCH": "search",
        //'WHAT_TO_WATCH': 'home', //disabling this breaks stuff because it tries to default to the home tab
        "YOUTUBE_SHORTS_FILL_24": "shorts",
        "SUBSCRIPTIONS": "subscriptions",
        "TAB_LIBRARY": "library",
        "GAMING": "gaming",
        "YOUTUBE_MUSIC": "music",
        "NEWS": "news",
        "BROADCAST": "podcasts",
        "CLAPPERBOARD": "movies_and_tv",
        "LIVE": "live",
        "TROPHY": "sports"
      };
      module.exports = () => {
        const disabledTabs = new Set(config.disabled_tabs);
        if (disabledTabs.size === 0) return;
        xhrModifiers.addResponseModifier(async (url, text) => {
          if (!url.startsWith("/youtubei/v1/guide")) return;
          let json = JSON.parse(text);
          let section = json.items[json.items.length - 1];
          if (section.guideSectionRenderer?.items) {
            section.guideSectionRenderer.items = section.guideSectionRenderer.items.filter((i) => !disabledTabs.has(map[i.guideEntryRenderer?.icon?.iconType]));
          }
          return JSON.stringify(json);
        });
      };
    }
  });

  // tv/vacuumtube/preload/modules/h264ify.js
  var require_h264ify = __commonJS({
    "tv/vacuumtube/preload/modules/h264ify.js"(exports, module) {
      var configManager = require_config();
      var config = configManager.get();
      module.exports = () => {
        if (!config.h264ify) return;
        let video = document.createElement("video");
        let canPlayType = video.__proto__.canPlayType;
        video.__proto__.canPlayType = function(type) {
          return isBlocked(type) ? "" : canPlayType.call(this, type);
        };
        let mse = window.MediaSource;
        let isTypeSupported = mse.isTypeSupported;
        mse.isTypeSupported = function(type) {
          return isBlocked(type) ? false : isTypeSupported.call(mse, type);
        };
        function isBlocked(type) {
          type = String(type);
          return config.h264ify_disable_webm && type.includes("webm") || config.h264ify_disable_vp8 && type.includes("vp8") || config.h264ify_disable_vp9 && (type.includes("vp9") || type.includes("vp09")) || config.h264ify_disable_av1 && type.includes("av01");
        }
      };
    }
  });

  // tv/shims/unavailable.js
  var require_unavailable = __commonJS({
    "tv/shims/unavailable.js"(exports, module) {
      function inert() {
        const obj = {};
        for (const name of ["on", "once", "off", "bind", "listen", "close", "send", "setBroadcast", "setMulticastTTL", "addMembership", "dropMembership", "address", "removeAllListeners", "end", "write"]) {
          obj[name] = () => obj;
        }
        return obj;
      }
      module.exports = {
        createSocket: inert,
        createServer: inert,
        request: inert,
        get: inert,
        randomUUID: () => crypto.randomUUID()
      };
    }
  });

  // tv/vacuumtube/preload/modules/h5vcc/dial/net.js
  var require_net = __commonJS({
    "tv/vacuumtube/preload/modules/h5vcc/dial/net.js"(exports, module) {
      var os = require_os();
      var dgram = require_unavailable();
      function toInt(address) {
        return address.split(".").reduce((acc, part) => (acc << 8) + Number(part), 0) >>> 0;
      }
      function isLocalAddress(address) {
        if (!address || !/^\d+\.\d+\.\d+\.\d+$/.test(address)) return false;
        let [a, b] = address.split(".").map(Number);
        return a === 10 || a === 127 || a === 172 && b >= 16 && b <= 31 || a === 192 && b === 168 || a === 169 && b === 254;
      }
      function getInterfaces() {
        return Object.values(os.networkInterfaces()).flat().filter((iface) => iface.family === "IPv4" && !iface.internal);
      }
      function getInterfaceAddressFor(remoteAddress) {
        if (!/^\d+\.\d+\.\d+\.\d+$/.test(remoteAddress)) return null;
        let remote = toInt(remoteAddress);
        for (let iface of getInterfaces()) {
          let mask = toInt(iface.netmask);
          if ((toInt(iface.address) & mask) === (remote & mask)) return iface.address;
        }
        return null;
      }
      function getDefaultAddress() {
        return new Promise((resolve) => {
          let sock = dgram.createSocket("udp4");
          let timeout = setTimeout(() => finish(null), 1e3);
          let finish = (address) => {
            clearTimeout(timeout);
            try {
              sock.close();
            } catch {
            }
            resolve(address);
          };
          sock.on("error", () => finish(null));
          sock.connect(80, "224.0.0.0", (err) => {
            finish(err ? null : sock.address().address);
          });
        });
      }
      module.exports = {
        isLocalAddress,
        getInterfaces,
        getInterfaceAddressFor,
        getDefaultAddress
      };
    }
  });

  // vt:vt:package
  var require_vt_package = __commonJS({
    "vt:vt:package"(exports, module) {
      module.exports = { "version": "2.0.1" };
    }
  });

  // tv/vacuumtube/preload/modules/h5vcc/dial/constants.js
  var require_constants = __commonJS({
    "tv/vacuumtube/preload/modules/h5vcc/dial/constants.js"(exports, module) {
      var os = require_os();
      var crypto2 = require_unavailable();
      var package2 = require_vt_package();
      var fallbackUUID = crypto2.randomUUID();
      var hostname = os.hostname();
      if (false) {
        hostname = hostname.slice(0, -6);
      }
      module.exports = {
        address: "239.255.255.250",
        port: 1900,
        osAgent: `${os.platform()}/${os.release()}`,
        appAgent: `VacuumTube/${package2.version}`,
        hostname,
        uuid: () => {
          try {
            let json = localStorage.getItem("yt.leanback.default::mdx-device-id");
            let obj = JSON.parse(json);
            let uuid = obj?.data;
            if (!uuid || typeof uuid !== "string") throw new Error(`Bad device ID: ${json}`);
            return uuid;
          } catch (err) {
            console.warn("[h5vcc] DIAL: Failed to get device ID", err);
            return fallbackUUID;
          }
        }
      };
    }
  });

  // tv/vacuumtube/preload/modules/h5vcc/dial/http.js
  var require_http = __commonJS({
    "tv/vacuumtube/preload/modules/h5vcc/dial/http.js"(exports, module) {
      var http = require_unavailable();
      var net = require_net();
      var constants = require_constants();
      var handlers = [];
      var server = http.createServer((req, res) => {
        if (!net.isLocalAddress(req.socket.remoteAddress) || req.headers.host !== `${req.socket.localAddress}:${req.socket.localPort}`) {
          res.writeHead(403);
          res.end();
          return;
        }
        res.setHeader("Server", constants.appAgent);
        for (let [method, path, handler] of handlers) {
          if (req.method === method && req.url === path) {
            handler(req, res);
            return;
          }
        }
        res.writeHead(404);
        res.end();
      });
      server.on("error", (err) => {
        console.error("[h5vcc] DIAL: HTTP server error", err);
      });
      function route(method, path, handler) {
        handlers.push([method, path, handler]);
      }
      function baseFor(req) {
        return `http://${req.socket.localAddress}:${req.socket.localPort}`;
      }
      function listen() {
        return new Promise((resolve, reject) => {
          server.once("error", reject);
          server.listen(0, "0.0.0.0", () => {
            server.off("error", reject);
            module.exports.port = server.address().port;
            resolve();
          });
        });
      }
      module.exports = {
        server,
        route,
        listen,
        baseFor,
        port: null
      };
    }
  });

  // tv/vacuumtube/preload/modules/h5vcc/dial/server.js
  var require_server = __commonJS({
    "tv/vacuumtube/preload/modules/h5vcc/dial/server.js"(exports, module) {
      var http = require_http();
      var constants = require_constants();
      var package2 = require_vt_package();
      var doc = new DOMParser().parseFromString("<root/>", "text/xml");
      function el(tag, children, attrs) {
        let node = doc.createElement(tag);
        if (attrs) {
          for (let [key, value] of Object.entries(attrs)) {
            node.setAttribute(key, value);
          }
        }
        if (typeof children === "string") {
          node.textContent = children;
        } else if (children) {
          for (let child of children) {
            node.appendChild(child);
          }
        }
        return node;
      }
      function buildDeviceDesc(base) {
        doc.documentElement.replaceWith(el("root", [
          el("specVersion", [
            el("major", "1"),
            el("minor", "0")
          ]),
          el("URLBase", base),
          el("device", [
            el("deviceType", "urn:dial-multiscreen-org:device:dial:1"),
            el("friendlyName", `${constants.hostname} (VacuumTube)`),
            el("manufacturer", "VacuumTube"),
            el("modelName", package2.version),
            el("UDN", `uuid:${constants.uuid()}`)
          ])
        ], { xmlns: "urn:schemas-upnp-org:device-1-0" }));
        return '<?xml version="1.0"?>' + new XMLSerializer().serializeToString(doc);
      }
      http.route("GET", "/", (req, res) => {
        let base = http.baseFor(req);
        res.statusCode = 200;
        res.setHeader("Content-Type", 'text/xml; charset="utf-8"');
        res.setHeader("Application-URL", `${base}/apps`);
        res.end(buildDeviceDesc(base));
      });
      async function handle(basePath, callback, req, res) {
        let body = "";
        if (req.method === "POST" || req.method === "DELETE") {
          body = await readBody(req);
        }
        let headers = new Headers();
        let data = {
          addHeader: (key, value) => headers.append(key, value)
        };
        let cb = callback({ host: `${req.socket.localAddress}:${req.socket.localPort}`, path: basePath, body }, data);
        if (!cb) {
          res.statusCode = 400;
          res.end();
          return;
        }
        if (data.mimeType) headers.append("Content-Type", data.mimeType);
        res.statusCode = data.responseCode ?? 200;
        res.setHeaders(headers);
        if (data.body) {
          res.end(data.body);
        } else {
          res.end();
        }
      }
      async function readBody(req, max = 102400) {
        return await new Promise((resolve, reject) => {
          if (!req.headers["content-length"] && req.headers["transfer-encoding"] !== "chunked") {
            resolve("");
            return;
          }
          let chunks = [];
          let size = 0;
          req.on("data", (chunk) => {
            size += chunk.length;
            if (size > max) {
              req.destroy();
              reject(new Error("Body too large"));
              return;
            }
            chunks.push(chunk);
          });
          req.on("end", () => resolve(Buffer.concat(chunks).toString()));
          req.on("error", reject);
        });
      }
      module.exports = class {
        constructor(appName) {
          this.appName = appName;
          this.basePath = `/apps/${appName}`;
        }
        #fullPath(path) {
          return (this.basePath + path).replace(/\/+$/, "") || "/";
        }
        #route(method, path, callback) {
          http.route(method, this.#fullPath(path), (req, res) => {
            handle(this.basePath, callback, req, res).catch((err) => {
              console.error("[h5vcc] DIAL: Failed to handle request", req.method, req.url, err);
              if (!res.headersSent) res.statusCode = 500;
              res.end();
            });
          });
        }
        onGet(path, callback) {
          this.#route("GET", path, callback);
        }
        onPost(path, callback) {
          this.#route("POST", path, callback);
        }
        onDelete(path, callback) {
          this.#route("DELETE", path, callback);
        }
      };
    }
  });

  // tv/vacuumtube/preload/modules/h5vcc/dial/discover.js
  var require_discover = __commonJS({
    "tv/vacuumtube/preload/modules/h5vcc/dial/discover.js"(exports, module) {
      var dgram = require_unavailable();
      var http = require_http();
      var net = require_net();
      var constants = require_constants();
      var socket = dgram.createSocket({ type: "udp4", reuseAddr: true });
      var joinedInterfaces = /* @__PURE__ */ new Set();
      var failedInterfaces = /* @__PURE__ */ new Set();
      socket.on("error", (err) => {
        console.error("[h5vcc] DIAL: SSDP socket error", err);
      });
      function joinGroup() {
        let addresses = net.getInterfaces().map((iface) => iface.address);
        for (let address of joinedInterfaces) {
          if (!addresses.includes(address)) {
            joinedInterfaces.delete(address);
          }
        }
        for (let address of addresses) {
          if (joinedInterfaces.has(address)) continue;
          try {
            socket.addMembership(constants.address, address);
            joinedInterfaces.add(address);
          } catch (err) {
            if (err.code === "EADDRINUSE") {
              joinedInterfaces.add(address);
              continue;
            }
            if (!failedInterfaces.has(address)) {
              console.warn(`[h5vcc] DIAL: Failed to listen for SSDP on ${address}`, err);
              failedInterfaces.add(address);
            }
          }
        }
      }
      socket.on("message", async (msg, rinfo) => {
        if (msg.length === 0) return;
        try {
          let ssdp = parseSSDP(msg);
          if (ssdp.method !== "M-SEARCH" || ssdp.path !== "*") return;
          let address = net.getInterfaceAddressFor(rinfo.address) ?? await net.getDefaultAddress();
          if (!address) return;
          let response = createSSDP({
            status: 200,
            statusText: "OK",
            headers: {
              "CACHE-CONTROL": "max-age=1800",
              "DATE": `${(/* @__PURE__ */ new Date()).toGMTString()}`,
              "EXT": "",
              "LOCATION": `http://${address}:${http.port}/`,
              "SERVER": `${constants.osAgent} UPnP/1.0 ${constants.appAgent}`,
              "ST": "urn:dial-multiscreen-org:service:dial:1",
              "USN": `uuid:${constants.uuid()}::urn:dial-multiscreen-org:service:dial:1`
            }
          });
          socket.send(response, rinfo.port, rinfo.address, (err) => {
            if (err) {
              console.error(`[h5vcc] DIAL: Failed to respond to SSDP discovery from ${rinfo.address}`, err);
            }
          });
        } catch (err) {
          console.error("[h5vcc] DIAL: Failed to handle SSDP discovery", err);
        }
      });
      function start() {
        socket.bind(constants.port, () => {
          joinGroup();
          setInterval(joinGroup, 3e4);
        });
      }
      function parseSSDP(buf) {
        let str = buf.toString("utf-8");
        let lines = str.split(/\r?\n/);
        let head = lines[0];
        let parts = head.split(" ");
        let method = parts[0];
        let path = parts[1];
        let protocol = parts[2];
        let headers = {};
        for (let line of lines.slice(1)) {
          if (!line?.trim()) continue;
          let i = line.indexOf(": ");
          let key = line.substring(0, i);
          let value = line.substring(i + 2);
          headers[key] = value;
        }
        return {
          protocol,
          method,
          path,
          headers
        };
      }
      function createSSDP(options) {
        let head = `HTTP/1.1 ${options.status} ${options.statusText}`;
        let headersText = "";
        for (let [key, value] of Object.entries(options.headers || {})) {
          headersText += `${key}: ${value}\r
`;
        }
        return `${head}\r
${headersText}\r
`;
      }
      module.exports = {
        start
      };
    }
  });

  // tv/vacuumtube/preload/modules/h5vcc/index.js
  var require_h5vcc = __commonJS({
    "tv/vacuumtube/preload/modules/h5vcc/index.js"(exports, module) {
      var { ipcRenderer } = require_electron();
      var http = require_http();
      var DialServer = require_server();
      var discover = require_discover();
      var configManager = require_config();
      var config = configManager.get();
      function getMaxResolution() {
        let resolutions = ["256x144", "426x240", "640x360", "854x480", "1280x720", "1920x1080", "2560x1440", "3840x2160", "7680x4320"];
        let screenWidth = Math.max(window.screen.width, window.screen.height);
        let screenHeight = Math.min(window.screen.width, window.screen.height);
        for (let i = 0; i < resolutions.length; i++) {
          let [width, height] = resolutions[i].split("x").map(Number);
          if (screenWidth <= width && screenHeight <= height) return resolutions[i];
        }
        return `${window.screen.width}x${window.screen.height}`;
      }
      async function waitForDeviceId() {
        let start = Date.now();
        while (Date.now() - start < 1e4) {
          try {
            let json = localStorage.getItem("yt.leanback.default::mdx-device-id");
            let id = JSON.parse(json)?.data;
            if (id && typeof id === "string") return;
          } catch {
          }
          await new Promise((resolve) => setTimeout(resolve, 50));
        }
        console.warn("[h5vcc] DIAL: Device ID not available after waiting, using fallback UUID");
      }
      async function startDial() {
        if (!config.device_discoverability) return;
        try {
          await waitForDeviceId();
          await http.listen();
          discover.start();
          console.log("[h5vcc] DIAL: Server started");
        } catch (err) {
          console.error("[h5vcc] DIAL: Failed to start server", err);
        }
      }
      function createOnDeepLink() {
        let listeners = [];
        let pending = [];
        function callListener(listener, deeplink) {
          try {
            listener(deeplink);
          } catch (err) {
            console.error("[h5vcc] Deeplink listener failed", err);
          }
        }
        ipcRenderer.on("deeplink", (event, deeplink) => {
          if (listeners.length === 0) {
            pending.push(deeplink);
            return;
          }
          for (let listener of listeners) {
            callListener(listener, deeplink);
          }
        });
        return {
          addListener: (listener) => {
            listeners.push(listener);
            while (pending.length > 0) {
              callListener(listener, pending.shift());
            }
          }
        };
      }
      module.exports = async () => {
        const initialDeepLink = await ipcRenderer.invoke("get-deeplink");
        const maxResolution = getMaxResolution();
        window.h5vcc = {
          dial: { DialServer },
          runtime: {
            initialDeepLink,
            onDeepLink: createOnDeepLink()
          },
          system: {
            getVideoContainerSizeOverride: () => {
              return config.unlock_resolution ? "7680x4320" : maxResolution;
            }
          }
        };
        startDial();
      };
    }
  });

  // tv/vacuumtube/preload/modules/hide-shorts.js
  var require_hide_shorts = __commonJS({
    "tv/vacuumtube/preload/modules/hide-shorts.js"(exports, module) {
      var xhrModifiers = require_xhrModifiers();
      var configManager = require_config();
      var config = configManager.get();
      module.exports = () => {
        xhrModifiers.addResponseModifier(async (url, text) => {
          if (!config.hide_shorts) return;
          if (!url.startsWith("/youtubei/v1/browse")) {
            return;
          }
          let json = JSON.parse(text);
          let sectionList = json.continuationContents?.sectionListContinuation || json.contents?.tvBrowseRenderer?.content?.tvSurfaceContentRenderer?.content?.sectionListRenderer;
          if (!sectionList) return;
          sectionList.contents = sectionList.contents.filter((i) => i?.shelfRenderer?.headerRenderer?.shelfHeaderRenderer?.avatarLockup?.avatarLockupRenderer?.title?.runs?.[0]?.text !== "Shorts");
          return JSON.stringify(json);
        });
      };
    }
  });

  // tv/vacuumtube/preload/util/patchFunction.js
  var require_patchFunction = __commonJS({
    "tv/vacuumtube/preload/util/patchFunction.js"(exports, module) {
      function patchFunction(obj, func, modifier) {
        let originalFunc = obj[func];
        let patched = function(...args) {
          return modifier.call(this, originalFunc, ...args);
        };
        obj[func] = patched;
      }
      module.exports = patchFunction;
    }
  });

  // tv/vacuumtube/preload/util/shortcuts.js
  var require_shortcuts = __commonJS({
    "tv/vacuumtube/preload/util/shortcuts.js"(exports, module) {
      function isSettingsOpen() {
        let root = document.getElementById("vt-settings-overlay-root");
        return !!root && !root.classList.contains("vt-settings-hidden");
      }
      function isShortcutKey(e) {
        if (e.ctrlKey || e.altKey || e.metaKey) return false;
        return !isSettingsOpen();
      }
      module.exports = {
        isShortcutKey,
        isSettingsOpen
      };
    }
  });

  // tv/vacuumtube/preload/modules/keybinds.js
  var require_keybinds = __commonJS({
    "tv/vacuumtube/preload/modules/keybinds.js"(exports, module) {
      var ui = require_ui();
      var rcMod = require_resolveCommandModifiers();
      var patchFunction = require_patchFunction();
      var localeProvider = require_localeProvider();
      var shortcuts = require_shortcuts();
      module.exports = async () => {
        await localeProvider.waitUntilAvailable();
        let locale = localeProvider.getLocale();
        let shiftHeld = false;
        let enterHeld = false;
        let shiftEnterHeld = false;
        document.addEventListener("keydown", (e) => {
          if (e.key === "Shift") shiftHeld = true;
          if (e.key === "Enter") enterHeld = true;
          shiftEnterHeld = shiftHeld && enterHeld;
        }, true);
        document.addEventListener("keyup", (e) => {
          if (e.key === "Shift") shiftHeld = false;
          if (e.key === "Enter") enterHeld = false;
          shiftEnterHeld = shiftHeld && enterHeld;
        }, true);
        patchFunction(window, "setTimeout", function(setTimeout2, callback, delay, ...args) {
          if (shiftEnterHeld && typeof callback === "function" && /^\(\)=>\{\([\w$]+=[\w$]+\.call\([^)]*\)\)&&[\w$]+\(\);[\w$]+\(\)\}$/.test(callback.toString())) {
            delay = 0;
          }
          return setTimeout2(callback, delay, ...args);
        });
        let lastShortId = null;
        rcMod.addInputModifier((c) => {
          if (c.reelWatchEndpoint) {
            lastShortId = c.reelWatchEndpoint.videoId;
          }
          return c;
        });
        document.addEventListener("keydown", (e) => {
          if (e.ctrlKey && e.shiftKey && e.key?.toLowerCase() === "c") {
            let url;
            let isShort = !!document.querySelector("ytlr-shorts-page")?.classList?.contains("zylon-focus");
            if (isShort && lastShortId) {
              url = `https://youtube.com/shorts/${lastShortId}`;
            } else {
              let baseUri = window.yt?.player?.utils?.videoElement_?.baseURI;
              if (!baseUri || !baseUri.includes("/watch?v=")) return;
              let id = baseUri.split("/watch?v=")[1]?.slice(0, 11);
              if (!id) return;
              url = `https://youtu.be/${id}`;
            }
            e.preventDefault();
            e.stopImmediatePropagation();
            navigator.clipboard.writeText(url);
            ui.toast("VacuumTube", locale.general.video_copied);
          }
        }, true);
        let captions = false;
        let captionSettings = { useDefaultTrack: true };
        rcMod.addInputModifier((c) => {
          if (c.selectSubtitlesTrackCommand) {
            if (Object.keys(c.selectSubtitlesTrackCommand).length === 0) {
              captions = false;
            } else {
              captions = true;
              captionSettings = c.selectSubtitlesTrackCommand;
            }
          }
          return c;
        });
        function toggleCaptions() {
          if (captions) {
            rcMod.resolveCommand({
              commandMetadata: {
                webCommandMetadata: {
                  clientAction: true
                }
              },
              selectSubtitlesTrackCommand: {}
              //off
            });
          } else {
            rcMod.resolveCommand({
              commandMetadata: {
                webCommandMetadata: {
                  clientAction: true
                }
              },
              selectSubtitlesTrackCommand: captionSettings
              //last known caption settings or the default
            });
          }
        }
        document.addEventListener("keydown", (e) => {
          if (!document.body.classList.contains("WEB_PAGE_TYPE_WATCH") && !document.body.classList.contains("WEB_PAGE_TYPE_SHORTS")) return;
          if (!e.shiftKey && shortcuts.isShortcutKey(e) && e.key?.toLowerCase() === "c") {
            e.stopImmediatePropagation();
            e.stopPropagation();
            toggleCaptions();
          }
        }, true);
      };
    }
  });

  // tv/vacuumtube/preload/modules/leanback-settings.js
  var require_leanback_settings = __commonJS({
    "tv/vacuumtube/preload/modules/leanback-settings.js"(exports, module) {
      var { shell } = require_electron();
      var configManager = require_config();
      var jsonMod = require_jsonModifiers();
      var rcMod = require_resolveCommandModifiers();
      var localeProvider = require_localeProvider();
      var functions = require_functions();
      var config = configManager.get();
      function createSettingButtonRenderer(title, summary, button, callback) {
        return {
          settingActionRenderer: {
            title: {
              runs: [{ text: title }]
            },
            summary: {
              runs: [{ text: summary }]
            },
            actionButton: {
              buttonRenderer: {
                text: {
                  runs: [{ text: button }]
                },
                navigationEndpoint: {
                  vtConfigOption: "vt-button",
                  vtConfigValue: callback
                }
              }
            }
          }
        };
      }
      module.exports = async () => {
        await localeProvider.waitUntilAvailable();
        await functions.waitForCondition(() => !!window.ytcfg);
        let isKids = window.ytcfg.data_.INNERTUBE_CLIENT_NAME === "TVHTML5_FOR_KIDS";
        let locale = localeProvider.getLocale();
        rcMod.addInputModifier((input) => {
          if (input.vtConfigOption) {
            if (input.vtConfigOption === "vt-button") {
              input.vtConfigValue();
              return false;
            }
            let newConfig = {};
            newConfig[input.vtConfigOption] = input.vtConfigValue;
            configManager.set(newConfig);
            config = configManager.get();
            for (let key of Object.keys(configOptions)) {
              configOptions[key].settingBooleanRenderer.enabled = config[key];
            }
            if (input.dynamicFunction) {
              input.dynamicFunction(input.vtConfigValue);
            }
            return false;
          }
          return input;
        });
        jsonMod.addModifier((json) => {
          if (json?.items?.[0]?.settingCategoryCollectionRenderer) {
            for (let item of json.items) {
              let category = item.settingCategoryCollectionRenderer;
              category.items = category.items.filter((c) => c.settingReadOnlyItemRenderer?.itemId !== "ABOUT_OPEN_SOURCE_LICENSES");
            }
            if (isKids) return json;
            json.items[0].settingCategoryCollectionRenderer.title = {
              //doesn't have a label by default
              runs: [
                { text: "YouTube" }
              ]
            };
            json.items.unshift(
              {
                settingCategoryCollectionRenderer: {
                  categoryId: "SETTINGS_CAT_VACUUMTUBE_OVERLAY",
                  focused: false,
                  items: [
                    createSettingButtonRenderer(
                      locale.settings.generic.title,
                      locale.settings.generic.description,
                      locale.settings.generic.button_label,
                      () => {
                        if (window.vtOpenSettingsOverlay) {
                          window.vtOpenSettingsOverlay();
                        }
                      }
                    )
                    /*
                    //don't yet have a way to receive donations
                    createSettingButtonRenderer(
                        locale.donate.setting.title,
                        locale.donate.setting.description,
                        locale.donate.setting.button_label,
                        () => {
                            shell.openExternal('https://shy.rocks/donate')
                        }
                    )
                    */
                  ],
                  title: {
                    runs: [
                      { text: "VacuumTube" }
                    ]
                  }
                }
              }
            );
          }
          return json;
        });
      };
    }
  });

  // tv/vacuumtube/preload/modules/low-memory-mode.js
  var require_low_memory_mode = __commonJS({
    "tv/vacuumtube/preload/modules/low-memory-mode.js"(exports, module) {
      var configManager = require_config();
      var configOverrides = require_configOverrides();
      module.exports = () => {
        let config = configManager.get();
        if (config.low_memory_mode) {
          configOverrides.environmentOverrides.push({
            feature_switches: {
              enable_memory_saving_mode: true
            }
          });
        }
      };
    }
  });

  // tv/vacuumtube/preload/modules/media-capabilities.js
  var require_media_capabilities = __commonJS({
    "tv/vacuumtube/preload/modules/media-capabilities.js"(exports, module) {
      var checks = {
        width: (value) => Number(value) > 0 && Number(value) <= 7680,
        //8k width
        height: (value) => Number(value) > 0 && Number(value) <= 4320,
        //8k height
        framerate: (value) => Number(value) > 0 && Number(value) <= 60,
        //60fps
        bitrate: (value) => Number(value) > 0 && Number(value) <= 2e8,
        //almost double avg bitrate of 8k video
        eotf: (value) => value === "bt709" || (value === "smpte2084" || value === "arib-std-b67") && isHdrDisplay(),
        channels: (value) => Number(value) > 0 && Number(value) <= 8,
        //audio channels
        cryptoblockformat: (value) => value === "subsample",
        "decode-to-texture": (value) => value === "true" || value === "false"
      };
      function isHdrDisplay() {
        return window.matchMedia("(dynamic-range: high)").matches;
      }
      function isHdrCodec(codec) {
        let parts = codec.split(".");
        let transfer = parts[0] === "vp09" ? parts[6] : parts[0] === "av01" ? parts[7] : void 0;
        return transfer === "16" || transfer === "18";
      }
      module.exports = () => {
        const isTypeSupported = MediaSource.isTypeSupported.bind(MediaSource);
        MediaSource.isTypeSupported = function(type) {
          let [mimeType, ...params] = String(type).split(";").map((p) => p.trim());
          let kept = [mimeType];
          for (let param of params) {
            let [key, ...rest] = param.split("=");
            key = key.trim().toLowerCase();
            let value = rest.join("=").trim().replace(/^"|"$/g, "");
            if (key === "codecs") {
              if (value.split(",").some((c) => isHdrCodec(c.trim())) && !isHdrDisplay()) return false;
              kept.push(param);
            } else if (checks[key]) {
              if (!checks[key](value)) return false;
            } else {
              kept.push(param);
            }
          }
          return isTypeSupported(kept.join("; "));
        };
      };
    }
  });

  // tv/vacuumtube/preload/modules/mouse.js
  var require_mouse = __commonJS({
    "tv/vacuumtube/preload/modules/mouse.js"(exports, module) {
      var shortcuts = require_shortcuts();
      module.exports = () => {
        const BACK = 32769;
        const UP = 32780;
        const DOWN = 32781;
        const LEFT = 32782;
        const RIGHT = 32783;
        const wheelThrottle = 100;
        let visible = true;
        let lastUse = 0;
        const showCursor = () => {
          document.documentElement.style.cursor = "default";
          visible = true;
        };
        const hideCursor = () => {
          document.documentElement.style.cursor = "none";
          visible = false;
        };
        const mouseUsed = () => {
          lastUse = Date.now();
          showCursor();
        };
        const simulateKeyDown = (keyCode) => {
          let event = new Event("keydown");
          event.keyCode = keyCode;
          document.dispatchEvent(event);
        };
        const simulateKeyUp = (keyCode) => {
          let event = new Event("keyup");
          event.keyCode = keyCode;
          document.dispatchEvent(event);
        };
        const pressKey = (keyCode) => {
          simulateKeyDown(keyCode);
          simulateKeyUp(keyCode);
        };
        let wheelThrottled = false;
        window.addEventListener("wheel", (e) => {
          e.preventDefault();
          e.stopPropagation();
          if (shortcuts.isSettingsOpen() || wheelThrottled) return;
          let keyCode;
          if (e.deltaY) {
            keyCode = e.deltaY < 0 ? UP : DOWN;
          } else if (e.deltaX) {
            keyCode = e.deltaX < 0 ? LEFT : RIGHT;
          } else {
            return;
          }
          pressKey(keyCode);
          wheelThrottled = true;
          setTimeout(() => wheelThrottled = false, wheelThrottle);
        }, { passive: false, capture: true });
        window.addEventListener("mousedown", (e) => {
          if (e.button === 2) {
            simulateKeyDown(BACK);
            setTimeout(() => simulateKeyUp(BACK), 50);
          }
        });
        setInterval(() => {
          if (!visible) return;
          if (Date.now() - lastUse >= 3e3) {
            hideCursor();
          }
        }, 20);
        window.addEventListener("pointermove", mouseUsed, true);
        window.addEventListener("pointerdown", mouseUsed, true);
        window.addEventListener("pointerup", mouseUsed, true);
      };
    }
  });

  // tv/vacuumtube/preload/modules/music-mode/player-response.js
  var require_player_response = __commonJS({
    "tv/vacuumtube/preload/modules/music-mode/player-response.js"(exports, module) {
      function isObject(value) {
        return value !== null && typeof value === "object" && !Array.isArray(value);
      }
      function isPlayerResponse(value) {
        return isObject(value) && isObject(value.streamingData) && isObject(value.videoDetails);
      }
      function getThumbnail(playerResponse) {
        const thumbnails = playerResponse.videoDetails?.thumbnail?.thumbnails;
        if (Array.isArray(thumbnails)) {
          const largest = [...thumbnails].filter((thumbnail) => typeof thumbnail?.url === "string" && thumbnail.url.length > 0).sort((a, b) => (b.width || 0) * (b.height || 0) - (a.width || 0) * (a.height || 0))[0];
          if (largest) return largest;
        }
        const videoId = playerResponse.videoDetails?.videoId;
        if (typeof videoId === "string" && videoId.length > 0) {
          return {
            url: `https://i.ytimg.com/vi/${encodeURIComponent(videoId)}/hqdefault.jpg`,
            width: 480,
            height: 360
          };
        }
        return null;
      }
      function enableAudioOnly(playerResponse) {
        if (!isPlayerResponse(playerResponse)) return null;
        if (!isObject(playerResponse.playerConfig)) {
          playerResponse.playerConfig = {};
        }
        if (!isObject(playerResponse.playerConfig.audioConfig)) {
          playerResponse.playerConfig.audioConfig = {};
        }
        playerResponse.playerConfig.audioConfig.playAudioOnly = true;
        playerResponse.videoDetails.musicVideoType = "MUSIC_VIDEO_TYPE_ATV";
        return getThumbnail(playerResponse);
      }
      function getWatchMetadataItem(json) {
        const contents = json.contents?.singleColumnWatchNextResults?.results?.results?.contents;
        if (!Array.isArray(contents)) return null;
        for (const result of contents) {
          const items = result?.itemSectionRenderer?.contents;
          if (!Array.isArray(items)) continue;
          const item = items.find((candidate) => candidate?.videoMetadataRenderer);
          if (item) return item;
        }
        return null;
      }
      function asThumbnailModel(currentVideoThumbnail, fallbackThumbnail) {
        if (Array.isArray(currentVideoThumbnail?.thumbnails) && currentVideoThumbnail.thumbnails.length > 0) {
          return { thumbnails: currentVideoThumbnail.thumbnails };
        }
        if (typeof fallbackThumbnail?.url === "string") {
          return { thumbnails: [fallbackThumbnail] };
        }
        return null;
      }
      function enableNativeMusicRenderer(json, fallbackThumbnail = null) {
        if (!isObject(json)) return false;
        const metadataItem = getWatchMetadataItem(json);
        const videoMetadata = metadataItem?.videoMetadataRenderer;
        if (!videoMetadata) return false;
        const currentVideoThumbnail = isObject(json.currentVideoThumbnail) ? json.currentVideoThumbnail : null;
        const thumbnailModel = asThumbnailModel(currentVideoThumbnail, fallbackThumbnail);
        const musicMetadata = {
          title: videoMetadata.title,
          byline: videoMetadata.owner?.videoOwnerRenderer?.title,
          secondaryTitle: videoMetadata.title,
          viewCountText: videoMetadata.viewCount?.videoViewCountRenderer?.shortViewCount,
          mayTruncateChannelName: true,
          trackingParams: videoMetadata.trackingParams
        };
        if (thumbnailModel) {
          musicMetadata.blurredBackgroundThumbnail = thumbnailModel;
          if (!currentVideoThumbnail) {
            json.currentVideoThumbnail = thumbnailModel;
          }
        }
        if (isObject(currentVideoThumbnail?.darkColorPalette)) {
          musicMetadata.darkColorPalette = currentVideoThumbnail.darkColorPalette;
        }
        for (const key of Object.keys(musicMetadata)) {
          if (musicMetadata[key] === void 0) delete musicMetadata[key];
        }
        delete metadataItem.videoMetadataRenderer;
        metadataItem.musicWatchMetadataRenderer = musicMetadata;
        return true;
      }
      module.exports = {
        enableAudioOnly,
        enableNativeMusicRenderer,
        getThumbnail,
        isPlayerResponse
      };
    }
  });

  // tv/vacuumtube/preload/modules/music-mode/index.js
  var require_music_mode = __commonJS({
    "tv/vacuumtube/preload/modules/music-mode/index.js"(exports, module) {
      var { ipcRenderer } = require_electron();
      var configManager = require_config();
      var jsonModifiers = require_jsonModifiers();
      var resolveCommandModifiers = require_resolveCommandModifiers();
      var localeProvider = require_localeProvider();
      var { enableAudioOnly, enableNativeMusicRenderer, isPlayerResponse } = require_player_response();
      var featureEnabled = false;
      var enabled = false;
      var lastThumbnail = null;
      var labels = {
        title: "Music Mode"
      };
      function isFeatureEnabled(config) {
        return config.features_enabled === true && config.music_mode_feature === true;
      }
      function getPlayerResponses(json) {
        const candidates = [json, json?.playerResponse];
        return candidates.filter(
          (candidate, index) => isPlayerResponse(candidate) && candidates.indexOf(candidate) === index
        );
      }
      function walkCommands(command) {
        const commands = [command];
        for (let i = 0; i < commands.length; i++) {
          let nested = commands[i]?.commandExecutorCommand?.commands;
          if (Array.isArray(nested)) commands.push(...nested);
        }
        return commands.filter(Boolean);
      }
      function buildMusicModeItem() {
        return {
          compactLinkRenderer: {
            icon: { iconType: "MUSIC" },
            secondaryIcon: {
              iconType: enabled ? "CHECK_BOX" : "CHECK_BOX_OUTLINE_BLANK"
            },
            serviceEndpoint: {
              signalAction: { signal: "VT_MUSIC_MODE_TOGGLE" }
            },
            title: {
              runs: [{ text: labels.title }]
            }
          }
        };
      }
      function injectMusicModeItem(items) {
        const existingIndex = items.findIndex(
          (item2) => item2?.compactLinkRenderer?.serviceEndpoint?.signalAction?.signal === "VT_MUSIC_MODE_TOGGLE"
        );
        const item = buildMusicModeItem();
        if (existingIndex === -1) {
          const speedIndex = items.findIndex(
            (existing) => existing?.compactLinkRenderer?.serviceEndpoint?.openClientOverlayAction?.type === "CLIENT_OVERLAY_TYPE_VIDEO_PLAYBACK_SPEED"
          );
          const insertAt = speedIndex === -1 ? items.length : speedIndex + 1;
          items.splice(insertAt, 0, item);
        } else {
          items[existingIndex] = item;
        }
      }
      function toggleMusicMode() {
        if (!featureEnabled) return;
        configManager.set({ music_mode: !enabled });
      }
      function closePlaybackSettingsPopup() {
        try {
          resolveCommandModifiers.resolveCommand({
            signalAction: { signal: "POPUP_BACK" }
          });
        } catch (err) {
          console.error("[Music Mode] Failed to close the playback settings popup", err);
        }
      }
      function reloadCurrentVideo() {
        const player = document.querySelector(".html5-video-player");
        if (!player?.loadVideoById) return false;
        const videoData = player.getVideoData?.() || {};
        const videoId = videoData.video_id || videoData.videoId;
        if (!videoId) return false;
        const currentTime = Number(player.getCurrentTime?.()) || 0;
        const wasPaused = player.getPlayerState?.() === 2;
        try {
          player.loadVideoById(videoId, Math.max(0, currentTime));
          if (wasPaused) setTimeout(() => player.pauseVideo?.(), 100);
          return true;
        } catch (err) {
          console.error("[Music Mode] Failed to reload the current video", err);
          return false;
        }
      }
      module.exports = () => {
        const config = configManager.get();
        featureEnabled = isFeatureEnabled(config);
        enabled = featureEnabled && config.music_mode === true;
        if (!featureEnabled && config.music_mode === true) {
          configManager.set({ music_mode: false });
          enabled = false;
        }
        jsonModifiers.addModifier((json) => {
          if (!featureEnabled || !enabled) return json;
          for (let playerResponse of getPlayerResponses(json)) {
            lastThumbnail = enableAudioOnly(playerResponse) || lastThumbnail;
          }
          enableNativeMusicRenderer(json, lastThumbnail);
          return json;
        });
        resolveCommandModifiers.addInputModifier((command) => {
          let has = walkCommands(command).some((c) => c?.signalAction?.signal === "VT_MUSIC_MODE_TOGGLE");
          if (has) {
            toggleMusicMode();
            setTimeout(closePlaybackSettingsPopup, 0);
            return false;
          }
          if (featureEnabled && command?.openPopupAction?.uniqueId === "playback-settings") {
            const items = command?.openPopupAction?.popup?.overlaySectionRenderer?.overlay?.overlayTwoPanelRenderer?.actionPanel?.overlayPanelRenderer?.content?.overlayPanelItemListRenderer?.items;
            if (Array.isArray(items)) injectMusicModeItem(items);
          }
          return command;
        });
        localeProvider.waitUntilAvailable().then(() => {
          const locale = localeProvider.getLocale();
          labels = {
            title: locale.settings?.features?.settings?.music_mode?.title || labels.title
          };
        });
        ipcRenderer.on("config-update", (event, nextConfig) => {
          const wasEnabled = enabled;
          featureEnabled = isFeatureEnabled(nextConfig);
          enabled = featureEnabled && nextConfig.music_mode === true;
          if (!featureEnabled && nextConfig.music_mode === true) {
            enabled = false;
            configManager.set({ music_mode: false });
          }
          if (wasEnabled !== enabled) {
            if (!enabled) lastThumbnail = null;
            setTimeout(reloadCurrentVideo, 0);
          }
        });
      };
    }
  });

  // tv/vacuumtube/preload/modules/no-f11.js
  var require_no_f11 = __commonJS({
    "tv/vacuumtube/preload/modules/no-f11.js"(exports, module) {
      module.exports = () => {
        document.addEventListener("keydown", (e) => {
          if (e.key === "F11") {
            e.stopImmediatePropagation();
          }
        }, true);
      };
    }
  });

  // tv/vacuumtube/preload/modules/pause-on-blur.js
  var require_pause_on_blur = __commonJS({
    "tv/vacuumtube/preload/modules/pause-on-blur.js"(exports, module) {
      var { ipcRenderer } = require_electron();
      var configManager = require_config();
      var rcMod = require_resolveCommandModifiers();
      module.exports = () => {
        let config = configManager.get();
        document.addEventListener("visibilitychange", (e) => {
          e.stopImmediatePropagation();
        });
        document.addEventListener("webkitvisibilitychange", (e) => {
          e.stopImmediatePropagation();
        });
        ipcRenderer.on("blur", () => {
          if (!config.pause_on_blur) return;
          rcMod.resolveCommand({
            commandMetadata: {
              webCommandMetadata: {
                clientAction: true
              }
            },
            playerControlAction: {
              playerControlType: "PLAYER_CONTROL_ACTION_TYPE_PAUSE",
              userInitiated: true
            }
          });
        });
      };
    }
  });

  // tv/vacuumtube/preload/modules/remaining-time.js
  var require_remaining_time = __commonJS({
    "tv/vacuumtube/preload/modules/remaining-time.js"(exports, module) {
      var configManager = require_config();
      var shortcuts = require_shortcuts();
      function isEnabled(config) {
        return config.features_enabled === true && config.remaining_time_feature === true;
      }
      function secondsToTime(totalSeconds) {
        let hours = Math.floor(totalSeconds / 3600);
        let minutes = Math.floor(totalSeconds % 3600 / 60);
        let seconds = totalSeconds % 60;
        if (hours > 0) {
          return `${hours}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
        } else {
          return `${minutes}:${String(seconds).padStart(2, "0")}`;
        }
      }
      function getRemainingTime(player) {
        let duration = Math.floor(player.getDuration());
        let elapsed = Math.floor(player.getCurrentTime());
        let remaining = Math.round(Math.max(0, (duration - elapsed) / player.getPlaybackRate()));
        return `-${secondsToTime(remaining)}`;
      }
      function isWatchPage() {
        return new URL(location.hash.substring(1), location.href).pathname === "/watch";
      }
      module.exports = () => {
        const config = configManager.get();
        let timeLabel = null;
        let player = null;
        let videoId = null;
        let showRemaining = true;
        let showing = false;
        function getElapsedTextNode() {
          let node = timeLabel?.querySelector('[idomkey="elapsedTime"]')?.firstChild;
          return node?.nodeType === Node.TEXT_NODE ? node : null;
        }
        function isActive() {
          return isEnabled(config) && showRemaining && isWatchPage() && !!player && !player.getVideoData?.()?.isLive;
        }
        function render() {
          let node = getElapsedTextNode();
          if (!node) return;
          if (!isActive()) {
            if (showing && player) {
              node.data = secondsToTime(Math.floor(player.getCurrentTime()));
            }
            showing = false;
            return;
          }
          let remaining = getRemainingTime(player);
          if (node.data === remaining) return;
          node.data = remaining;
          showing = true;
        }
        const observer = new MutationObserver(render);
        setInterval(() => {
          if (!isEnabled(config) && !showing) return;
          let label = document.querySelector('[idomkey="time-label"] > [idomkey="elapsedTime"]')?.parentElement ?? null;
          if (label !== timeLabel) {
            observer.disconnect();
            timeLabel = label;
            showing = false;
            if (label) {
              observer.observe(label, { characterData: true, childList: true, subtree: true });
            }
          }
          player = document.querySelector(".html5-video-player");
          let id = player?.getVideoData?.()?.video_id ?? null;
          if (id !== videoId) {
            videoId = id;
            showRemaining = true;
          }
          render();
        }, 250);
        document.addEventListener("keydown", (e) => {
          if (e.key !== "r" && e.key !== "R") return;
          if (!shortcuts.isShortcutKey(e)) return;
          if (!isEnabled(config) || !isWatchPage()) return;
          showRemaining = !showRemaining;
          render();
          e.preventDefault();
          e.stopPropagation();
          e.stopImmediatePropagation();
        }, true);
      };
    }
  });

  // tv/vacuumtube/preload/modules/remove-super-resolution.js
  var require_remove_super_resolution = __commonJS({
    "tv/vacuumtube/preload/modules/remove-super-resolution.js"(exports, module) {
      var jsonMod = require_jsonModifiers();
      var configManager = require_config();
      var config = configManager.get();
      module.exports = () => {
        jsonMod.addModifier((json) => {
          if (!config.remove_super_resolution) return json;
          if (!json?.streamingData?.adaptiveFormats) return json;
          json.streamingData.adaptiveFormats = json.streamingData.adaptiveFormats.filter((f) => f.xtags !== "CgcKAnNyEgEx");
          return json;
        });
      };
    }
  });

  // tv/vacuumtube/preload/modules/return-youtube-dislike.js
  var require_return_youtube_dislike = __commonJS({
    "tv/vacuumtube/preload/modules/return-youtube-dislike.js"(exports, module) {
      var xhrModifiers = require_xhrModifiers();
      var localeProvider = require_localeProvider();
      var configManager = require_config();
      var config = configManager.get();
      var timeout = 2e3;
      async function fetchDislikes(videoId) {
        let res = await fetch(`https://returnyoutubedislikeapi.com/Votes?videoId=${videoId}`, { signal: AbortSignal.timeout(timeout) });
        if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
        let data = await res.json();
        return data;
      }
      module.exports = async () => {
        await localeProvider.waitUntilAvailable();
        let locale = localeProvider.getLocale();
        xhrModifiers.addResponseModifier(async (url, text) => {
          if (!config.dislikes) return;
          if (!url.startsWith("/youtubei/v1/next")) {
            return;
          }
          let json = JSON.parse(text);
          let videoId = json.currentVideoEndpoint.watchEndpoint.videoId;
          let panel = json.engagementPanels.find((p) => p.engagementPanelSectionListRenderer?.panelIdentifier === "video-description-ep-identifier");
          if (!panel) return;
          let engagementActions = json.transportControls?.transportControlsRenderer?.engagementActions;
          let likesEngagement = engagementActions?.find((a) => a.type === "TRANSPORT_CONTROLS_BUTTON_TYPE_LIKE_BUTTON");
          let votes;
          try {
            votes = await fetchDislikes(videoId);
          } catch (err) {
            console.error(`fetching dislikes of ${videoId} failed`, err);
            return;
          }
          let dislikes = votes.dislikes;
          let abbreviatedDislikes = Intl.NumberFormat(void 0, {
            notation: "compact",
            maximumFractionDigits: 1
          }).format(dislikes);
          panel.engagementPanelSectionListRenderer.content.structuredDescriptionContentRenderer.items[0].videoDescriptionHeaderRenderer.factoid.push({
            factoidRenderer: {
              value: {
                simpleText: abbreviatedDislikes
              },
              label: {
                simpleText: locale.general.dislikes
              }
            }
          });
          if (likesEngagement?.button?.likeButtonRenderer) {
            likesEngagement.button.likeButtonRenderer.dislikeCountText.simpleText = abbreviatedDislikes;
            likesEngagement.button.likeButtonRenderer.dislikeCountWithUndislikeText.simpleText = abbreviatedDislikes;
          }
          return JSON.stringify(json);
        });
      };
    }
  });

  // tv/vacuumtube/preload/modules/settings/render.js
  var require_render = __commonJS({
    "tv/vacuumtube/preload/modules/settings/render.js"(exports, module) {
      var configManager = require_config();
      var { el } = require_functions();
      function rowId(row) {
        return row.id || row.key || row.page && `link-${row.page}` || row.title;
      }
      function resolve(value, ...args) {
        return typeof value === "function" ? value(...args) : value;
      }
      function focusable(navId, className, children, activate) {
        const node = el("div", { className: `vt-focusable ${className}`, dataNavId: navId }, children);
        node.vtActivate = activate;
        return node;
      }
      function info(title, description) {
        return el("div", { className: "vt-row-info" }, [
          el("span", { className: "vt-row-title", textContent: title || "" }),
          description ? el("span", { className: "vt-row-description", textContent: description }) : null
        ]);
      }
      var rowTypes = {
        toggle(row, context) {
          const on = row.get ? !!row.get(context.config) : !!context.config[row.key];
          const strings = context.locale[row.key];
          return focusable(`row:${rowId(row)}`, "vt-row", [
            info(row.title ?? strings?.title, row.description ?? strings?.description),
            el("div", { className: `vt-toggle${on ? " vt-toggle-on" : ""}` }, [
              el("div", { className: "vt-toggle-thumb" })
            ])
          ], () => {
            if (row.set) {
              row.set(!on, context.config);
            } else {
              configManager.set({ [row.key]: !on });
            }
            row.onChange?.(!on);
          });
        },
        link(row, context) {
          return focusable(`row:${rowId(row)}`, "vt-row vt-row-link", [
            info(row.title, row.description),
            el("span", { className: "vt-row-value", textContent: resolve(row.summary, context.config) || "" }),
            el("span", { className: "vt-chevron", textContent: "\u203A" })
          ], () => context.openPage(row.page));
        },
        button(row) {
          return focusable(`row:${rowId(row)}`, "vt-row vt-row-button", [
            info(row.title, row.description)
          ], () => row.run());
        },
        notice(row, context) {
          return el("div", { className: `vt-notice vt-notice-${row.tone || "info"}` }, [
            info(row.title, resolve(row.text, context.config))
          ]);
        },
        info(row, context) {
          return el("div", { className: "vt-row vt-row-static" }, [
            info(row.title, row.description),
            el("span", { className: "vt-row-value", textContent: resolve(row.value, context.config) ?? "" })
          ]);
        },
        text(row, context) {
          return el("div", { className: "vt-text", textContent: resolve(row.text, context.config) });
        }
      };
      function subLink(row, context) {
        const page = context.pages[row.page];
        const node = rowTypes.link({ page: row.page, title: resolve(page.title, context.locale), summary: row.summary }, context);
        node.classList.add("vt-row-sub");
        return node;
      }
      function renderRow(row, context) {
        const node = rowTypes[row.type](row, context);
        const active = (!row.dependsOn || context.config[row.dependsOn]) && !row.disabled;
        if (!active) {
          node.classList.add("vt-inactive");
          node.vtActivate = null;
        }
        if (row.type === "toggle" && row.page && active && context.config[row.key]) {
          return [node, subLink(row, context)];
        }
        return [node];
      }
      function visibleRows(rows) {
        return (rows || []).filter((row) => row && !row.hide);
      }
      function card(rows, context) {
        return el("div", { className: "vt-card" }, visibleRows(rows).flatMap((row) => renderRow(row, context)));
      }
      function viewHeader({ title, description, count, badge, icon }) {
        return el("div", { className: "vt-view-header" }, [
          icon ? el("img", { className: "vt-view-icon", src: icon, alt: "" }) : null,
          el("div", { className: "vt-view-heading" }, [
            el("h2", { className: "vt-view-title", textContent: title || "" }),
            description ? el("p", { className: "vt-view-description", textContent: description }) : null
          ]),
          count ? el("span", { className: "vt-view-count", textContent: count }) : null,
          badge ? el("span", { className: "vt-view-badge", textContent: badge }) : null
        ]);
      }
      function countFocusable(rows) {
        return visibleRows(rows).filter((row) => ["toggle", "link", "button"].includes(row.type)).length;
      }
      function translateLink(context) {
        const label = context.locale.generic.translate.replace("{language}", context.translation.name);
        return focusable("translate", "vt-translate", [
          el("span", { textContent: label }),
          el("span", { className: "vt-chevron", textContent: "\u203A" })
        ], context.openTranslation);
      }
      function renderCategory(category, context) {
        const sections = category.sections.filter((section) => !section.hide && visibleRows(section.rows).length);
        const count = sections.reduce((n, section) => n + countFocusable(section.rows), 0);
        const meta = context.locale.categories[category.id] || {};
        return el("div", { className: "vt-view" }, [
          viewHeader({
            title: meta.title,
            description: meta.description,
            count: context.locale.generic.option_count.replace("{count}", count)
          }),
          el("div", { className: "vt-section-grid" }, sections.map(
            (section) => el("section", { className: "vt-section" }, [
              el("h3", { className: "vt-section-title", textContent: context.locale.sections[section.id] || "" }),
              card(section.rows, context)
            ])
          )),
          context.translation.complete ? null : translateLink(context)
        ]);
      }
      function renderPage(page, sections, parentTitle, context) {
        const title = resolve(page.title, context.locale);
        return el("div", { className: "vt-view vt-view-page" }, [
          focusable("back", "vt-back", [
            el("span", { className: "vt-chevron", textContent: "\u2039" }),
            el("span", { textContent: `${parentTitle} / ${resolve(page.crumb, context.locale) || title}` })
          ], () => context.goBack()),
          viewHeader({
            title,
            description: resolve(page.description, context.locale),
            badge: resolve(page.badge, context.locale),
            icon: page.icon
          }),
          ...sections.filter((section) => section && visibleRows(section.rows).length).map(
            (section) => el("section", { className: "vt-section" }, [
              section.title ? el("h3", { className: "vt-section-title", textContent: section.title }) : null,
              card(section.rows, context)
            ])
          )
        ]);
      }
      module.exports = {
        renderCategory,
        renderPage
      };
    }
  });

  // tv/vacuumtube/preload/modules/settings/overlay.js
  var require_overlay = __commonJS({
    "tv/vacuumtube/preload/modules/settings/overlay.js"(exports, module) {
      var { el } = require_functions();
      function create(categories, locale, actions) {
        const tabs = categories.map((category, index) => {
          const tab = el("div", {
            className: "vt-focusable vt-tab",
            dataNavId: `tab:${category.id}`,
            dataIndex: String(index)
          }, [
            el("span", { textContent: locale.categories[category.id]?.title || category.id })
          ]);
          tab.vtActivate = () => actions.openCategory(index);
          return tab;
        });
        const about = el("div", {
          className: "vt-focusable vt-settings-icon-button",
          dataNavId: "about",
          title: locale.about.title
        }, [el("span", { textContent: "?" })]);
        const close = el("div", {
          className: "vt-focusable vt-settings-icon-button",
          dataNavId: "close"
        }, [el("span", { textContent: "\u2715" })]);
        about.vtActivate = actions.openAbout;
        close.vtActivate = actions.close;
        return el("div", { id: "vt-settings-overlay-root", className: "vt-settings-hidden", tabindex: "-1" }, [
          el("div", { className: "vt-settings-backdrop" }),
          el("div", { className: "vt-settings-container" }, [
            //nav groups limit arrow key movement to one area at a time, see nav.js
            el("div", { className: "vt-settings-header", dataNavGroup: "header" }, [
              el("span", { className: "vt-settings-title", textContent: locale.generic.title }),
              el("span", { className: "vt-settings-hint", textContent: locale.generic.hint }),
              about,
              close
            ]),
            el("div", { className: "vt-tabbar", dataNavGroup: "tabs" }, tabs),
            el("div", { className: "vt-content-viewport", dataNavGroup: "content" }, [
              el("div", { className: "vt-content-list", id: "vt-content-list" }),
              el("div", { className: "vt-scrollbar" }, [
                el("div", { className: "vt-scrollbar-thumb", id: "vt-content-scrollbar-thumb" })
              ])
            ])
          ])
        ]);
      }
      function root() {
        return document.getElementById("vt-settings-overlay-root");
      }
      function list() {
        return document.getElementById("vt-content-list");
      }
      function show() {
        root().classList.remove("vt-settings-hidden");
        root().focus();
      }
      function hide() {
        root().classList.add("vt-settings-hidden");
        root().blur();
      }
      function markSelectedTab(index) {
        root().querySelectorAll(".vt-tab").forEach((tab) => {
          tab.classList.toggle("vt-tab-selected", Number(tab.dataset.index) === index);
        });
      }
      module.exports = {
        create,
        root,
        list,
        show,
        hide,
        markSelectedTab
      };
    }
  });

  // tv/vacuumtube/preload/modules/settings/scroll.js
  var require_scroll = __commonJS({
    "tv/vacuumtube/preload/modules/settings/scroll.js"(exports, module) {
      var MARGIN = 16;
      var viewport = null;
      var list = null;
      var thumb = null;
      var offset = 0;
      function maxOffset() {
        return Math.max(0, list.scrollHeight - viewport.clientHeight);
      }
      function apply(value) {
        if (!list) return;
        offset = Math.max(0, Math.min(maxOffset(), value));
        list.style.transform = `translateY(-${offset}px)`;
        const viewportHeight = viewport.clientHeight;
        const listHeight = list.scrollHeight;
        const scrollbar = thumb.parentElement;
        if (listHeight <= viewportHeight) {
          scrollbar.classList.remove("vt-scrollbar-visible");
          return;
        }
        scrollbar.classList.add("vt-scrollbar-visible");
        const thumbHeight = Math.max(30, viewportHeight / listHeight * viewportHeight);
        const thumbTop = offset / maxOffset() * (viewportHeight - thumbHeight);
        thumb.style.height = `${thumbHeight}px`;
        thumb.style.transform = `translateY(${thumbTop}px)`;
      }
      function scrollTo(element) {
        if (!list || !list.contains(element)) return;
        const top = element.getBoundingClientRect().top - list.getBoundingClientRect().top;
        const bottom = top + element.offsetHeight;
        if (list.querySelector(".vt-focusable") === element) {
          apply(0);
        } else if (bottom - offset > viewport.clientHeight - MARGIN) {
          apply(bottom - viewport.clientHeight + MARGIN);
        } else if (top < offset + MARGIN) {
          apply(top - MARGIN);
        } else {
          apply(offset);
        }
      }
      function reset() {
        apply(0);
      }
      function attach(viewportElement, listElement, thumbElement) {
        viewport = viewportElement;
        list = listElement;
        thumb = thumbElement;
        let touchStartY = 0;
        let startOffset = 0;
        let dragging = false;
        viewport.addEventListener("touchstart", (e) => {
          if (e.touches.length !== 1) return;
          touchStartY = e.touches[0].clientY;
          startOffset = offset;
          dragging = true;
          list.style.transition = "none";
        }, { passive: true });
        viewport.addEventListener("touchmove", (e) => {
          if (!dragging || e.touches.length !== 1) return;
          apply(startOffset + touchStartY - e.touches[0].clientY);
        }, { passive: true });
        const endDrag = () => {
          dragging = false;
          list.style.transition = "";
        };
        viewport.addEventListener("touchend", endDrag, { passive: true });
        viewport.addEventListener("touchcancel", endDrag, { passive: true });
        window.addEventListener("wheel", (e) => {
          if (!viewport.contains(e.target)) return;
          e.preventDefault();
          apply(offset + e.deltaY);
        }, { passive: false, capture: true });
      }
      module.exports = {
        attach,
        scrollTo,
        reset,
        refresh: () => apply(offset)
      };
    }
  });

  // tv/vacuumtube/preload/modules/settings/nav.js
  var require_nav = __commonJS({
    "tv/vacuumtube/preload/modules/settings/nav.js"(exports, module) {
      function gap(a1, a2, b1, b2) {
        return Math.max(0, b1 - a2, a1 - b2);
      }
      function measure(from, to, direction) {
        switch (direction) {
          case "up":
            return [from.top - to.bottom, gap(from.left, from.right, to.left, to.right)];
          case "down":
            return [to.top - from.bottom, gap(from.left, from.right, to.left, to.right)];
          case "left":
            return [from.left - to.right, gap(from.top, from.bottom, to.top, to.bottom)];
          case "right":
            return [to.left - from.right, gap(from.top, from.bottom, to.top, to.bottom)];
        }
      }
      function nearest(origin, direction, nodes, exclude) {
        let best = null;
        let bestScore = Infinity;
        let bestAligned = false;
        for (const node of nodes) {
          if (node === exclude) continue;
          const rect = node.getBoundingClientRect();
          if (!rect.width && !rect.height) continue;
          const [primary, cross] = measure(origin, rect, direction);
          if (primary < -2) continue;
          const aligned = cross === 0;
          const score = primary + cross * 3;
          if (aligned && !bestAligned || aligned === bestAligned && score < bestScore) {
            best = node;
            bestScore = score;
            bestAligned = aligned;
          }
        }
        return best;
      }
      function nextGroup(group, direction, root) {
        const origin = group.getBoundingClientRect();
        let best = null;
        let bestDistance = Infinity;
        for (const other of root.querySelectorAll("[data-nav-group]")) {
          if (other === group) continue;
          const [primary] = measure(origin, other.getBoundingClientRect(), direction);
          if (primary < -2 || primary >= bestDistance) continue;
          best = other;
          bestDistance = primary;
        }
        return best;
      }
      function findNext(from, direction, root) {
        const origin = from.getBoundingClientRect();
        const group = from.closest("[data-nav-group]") || root;
        const inside = nearest(origin, direction, group.querySelectorAll(".vt-focusable"), from);
        if (inside) return inside;
        for (let next = nextGroup(group, direction, root); next; next = nextGroup(next, direction, root)) {
          const nodes = next.querySelectorAll(".vt-focusable");
          if (nodes.length) return nearest(origin, direction, nodes) || nodes[0];
        }
        return null;
      }
      module.exports = { findNext };
    }
  });

  // tv/vacuumtube/preload/modules/settings/focus.js
  var require_focus = __commonJS({
    "tv/vacuumtube/preload/modules/settings/focus.js"(exports, module) {
      var overlay = require_overlay();
      var scroll = require_scroll();
      var { findNext } = require_nav();
      var focusId = null;
      function current() {
        return overlay.root()?.querySelector(".vt-focused") || null;
      }
      function id() {
        return focusId;
      }
      function set(node) {
        overlay.root().querySelectorAll(".vt-focused").forEach((node2) => node2.classList.remove("vt-focused"));
        node.classList.add("vt-focused");
        focusId = node.dataset.navId;
        scroll.scrollTo(node);
      }
      function want(navId = null) {
        focusId = navId;
      }
      function first() {
        const list = overlay.list();
        const node = list.querySelector(".vt-row.vt-focusable") || list.querySelector(".vt-focusable");
        if (node) set(node);
      }
      function restore(wanted = focusId) {
        const node = wanted && overlay.root().querySelector(`[data-nav-id="${CSS.escape(wanted)}"]`);
        if (node) {
          set(node);
        } else {
          first();
        }
      }
      function isTab(node) {
        return node.classList.contains("vt-tab");
      }
      function move(direction, onCategoryChange) {
        const from = current();
        if (!from) return first();
        let next = findNext(from, direction, overlay.root());
        if (!next) return;
        if (isTab(next) && !isTab(from)) {
          next = overlay.root().querySelector(".vt-tab.vt-tab-selected") || next;
        }
        set(next);
        if (isTab(next) && isTab(from)) {
          onCategoryChange(Number(next.dataset.index));
        }
      }
      module.exports = {
        current,
        id,
        set,
        want,
        first,
        restore,
        move
      };
    }
  });

  // tv/vacuumtube/preload/modules/settings/input.js
  var require_input = __commonJS({
    "tv/vacuumtube/preload/modules/settings/input.js"(exports, module) {
      var controller = require_controller();
      var directions = {
        "ArrowUp": "up",
        "ArrowDown": "down",
        "ArrowLeft": "left",
        "ArrowRight": "right"
      };
      var gamepadKeys = {
        32768: "Enter",
        //a
        32769: "Escape",
        //b
        32780: "ArrowUp",
        //dpad up
        32781: "ArrowDown",
        //dpad down
        32782: "ArrowLeft",
        //dpad left
        32783: "ArrowRight",
        //dpad right
        32785: "ArrowUp",
        //left stick up
        32786: "ArrowDown",
        //left stick down
        32787: "ArrowLeft",
        //left stick left
        32788: "ArrowRight"
        //left stick right
      };
      function listen(actions) {
        let openedAt = 0;
        const justOpened = () => Date.now() - openedAt < 100;
        const handleKey = (key) => {
          if (key === "Escape" || key === "Backspace") {
            actions.back();
          } else if (directions[key]) {
            actions.move(directions[key]);
          } else if (key === "Enter" || key === " ") {
            actions.activate();
          }
        };
        document.addEventListener("keydown", (e) => {
          if (e.ctrlKey && e.key.toLowerCase() === "o") {
            e.preventDefault();
            e.stopPropagation();
            actions.toggle();
          }
        }, true);
        for (const type of ["keydown", "keyup"]) {
          document.addEventListener(type, (e) => {
            if (!actions.isOpen()) return;
            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();
            if (type === "keydown" && !justOpened()) handleKey(e.key);
          }, true);
        }
        document.addEventListener("click", (e) => {
          if (!actions.isOpen() || justOpened()) return;
          if (e.target.classList.contains("vt-settings-backdrop")) {
            actions.close();
            return;
          }
          const node = e.target.closest("#vt-settings-overlay-root .vt-focusable");
          if (node) actions.activate(node);
        }, true);
        controller.on("down", (e) => {
          if (!actions.isOpen() || justOpened()) return;
          const key = gamepadKeys[e.code];
          if (key) handleKey(key);
        });
        return { markOpened: () => {
          openedAt = Date.now();
        } };
      }
      module.exports = { listen };
    }
  });

  // tv/vacuumtube/preload/modules/settings/pages/about.js
  var require_about = __commonJS({
    "tv/vacuumtube/preload/modules/settings/pages/about.js"(exports, module) {
      var __dirname = "/vt/src/preload/modules/settings/pages";
      var fs = require_fs();
      var path = require_path();
      var { ipcRenderer } = require_electron();
      var iconPath = path.join(__dirname, "../../../../../assets/icon.png");
      var iconDataUrl = `data:image/png;base64,${fs.readFileSync(iconPath).toString("base64")}`;
      var refresh = null;
      var state = {
        status: "idle",
        currentVersion: "",
        latestVersion: "",
        platform: "android",
        arch: "arm64",
        installationType: "unknown",
        canAutoUpdate: false,
        progress: null
      };
      ipcRenderer.on("update-state-changed", (event, next) => {
        state = next;
        refresh?.();
      });
      function fill(template, values) {
        return template.replace(/\{(\w+)\}/g, (match, key) => values[key] ?? match);
      }
      function platformLabel(about) {
        const platform = about.platforms[state.platform] || state.platform;
        return `${platform} \xB7 ${about.architectures[state.arch] || state.arch}`;
      }
      function updateMethodLabel(about) {
        if (state.canAutoUpdate) return about.update_methods.automatic;
        if (state.installationType === "flatpak") return about.update_methods.flatpak;
        return about.update_methods.manual;
      }
      function updateStatus(strings) {
        const values = {
          version: state.latestVersion || state.currentVersion,
          percent: state.progress ?? 0
        };
        switch (state.status) {
          case "checking":
            return { tone: "info", title: strings.checking_title, text: strings.checking_detail, button: strings.checking_button };
          case "current":
            return { tone: "success", title: strings.current_title, text: fill(strings.current_detail, { version: state.currentVersion }), button: strings.check_again };
          case "available":
            return {
              tone: "warning",
              title: fill(strings.available_title, values),
              text: state.canAutoUpdate ? strings.available_automatic_detail : strings.available_manual_detail,
              button: state.canAutoUpdate ? strings.download_update : strings.download_latest
            };
          case "downloading":
            return { tone: "info", title: strings.downloading_title, text: fill(strings.downloading_detail, values), button: fill(strings.downloading_button, values) };
          case "ready":
            return { tone: "warning", title: strings.ready_title, text: fill(strings.ready_detail, values), button: strings.install_update };
          case "error":
            return { tone: "error", title: strings.error_title, text: strings.error_detail, button: strings.try_again };
          case "development":
            return { tone: "info", title: strings.development_title, text: strings.development_detail, button: strings.view_releases };
          case "unsupported":
            return { tone: "info", title: strings.unsupported_title, text: strings.unsupported_detail, button: strings.view_update_source };
          default:
            return { tone: "info", title: strings.idle_title, text: strings.idle_detail, button: strings.check_now };
        }
      }
      async function runUpdateAction() {
        switch (state.status) {
          case "available":
            return ipcRenderer.invoke(state.canAutoUpdate ? "download-update" : "open-update-download");
          case "ready":
            return ipcRenderer.invoke("install-update");
          case "development":
            return ipcRenderer.invoke("open-release-page");
          case "unsupported":
            return ipcRenderer.invoke("open-update-download");
          default:
            state = await ipcRenderer.invoke("check-for-updates");
        }
      }
      module.exports = {
        id: "about",
        icon: iconDataUrl,
        title: "VacuumTube",
        crumb: (locale) => locale.about.title,
        description: (locale) => locale.about.description,
        badge: (locale) => state.currentVersion && fill(locale.about.version, { version: state.currentVersion }),
        async onOpen(ctx) {
          refresh = ctx.refresh;
          try {
            state = await ipcRenderer.invoke("get-about-info");
          } catch (err) {
            console.error("[About] Failed to load application information:", err);
            state = { ...state, status: "error" };
          }
        },
        sections: (config, locale) => {
          const about = locale.about;
          const update = updateStatus(about.updates);
          const busy = state.status === "checking" || state.status === "downloading";
          return [
            {
              rows: [
                { type: "notice", tone: update.tone, title: update.title, text: update.text },
                { type: "button", id: "update-action", title: update.button, disabled: busy, run: runUpdateAction }
              ]
            },
            {
              rows: [
                { type: "info", title: about.platform_label, value: platformLabel(about) },
                { type: "info", title: about.installation_label, value: about.installations[state.installationType] || about.installations.unknown },
                { type: "info", title: about.update_method_label, value: updateMethodLabel(about) }
              ]
            },
            {
              rows: [
                { type: "button", title: about.view_github, run: () => ipcRenderer.invoke("open-project-page") },
                { type: "button", title: about.latest_releases, run: () => ipcRenderer.invoke("open-release-page") }
              ]
            }
          ];
        }
      };
    }
  });

  // tv/vacuumtube/preload/modules/settings/pages/sponsorblock.js
  var require_sponsorblock = __commonJS({
    "tv/vacuumtube/preload/modules/settings/pages/sponsorblock.js"(exports, module) {
      var CATEGORIES = ["sponsor", "selfpromo", "interaction", "intro", "outro", "preview", "hook", "filler"];
      var SPONSORBLOCK_KEYS = CATEGORIES.map((name) => `sponsorblock_skip_${name}`);
      module.exports = {
        id: "sponsorblock",
        SPONSORBLOCK_KEYS,
        title: (locale) => locale.sponsorblock.categories_link,
        rows: (config, locale) => CATEGORIES.map((name) => ({
          type: "toggle",
          key: `sponsorblock_skip_${name}`,
          title: locale.sponsorblock.categories[name].name,
          description: locale.sponsorblock.categories[name].description,
          dependsOn: "sponsorblock"
        }))
      };
    }
  });

  // tv/vacuumtube/preload/modules/settings/pages/h264ify.js
  var require_h264ify2 = __commonJS({
    "tv/vacuumtube/preload/modules/settings/pages/h264ify.js"(exports, module) {
      var CODECS = { webm: "WebM", vp8: "VP8", vp9: "VP9", av1: "AV1" };
      var CODEC_KEYS = Object.keys(CODECS).map((id) => `h264ify_disable_${id}`);
      module.exports = {
        id: "h264ify",
        CODEC_KEYS,
        title: (locale) => locale.h264ify.codecs_link,
        description: (locale) => locale.h264ify.description,
        rows: (config, locale) => Object.entries(CODECS).map(([id, name]) => ({
          type: "toggle",
          key: `h264ify_disable_${id}`,
          title: locale.h264ify.disable_codec_title.replaceAll("{codec}", name),
          description: locale.h264ify.disable_codec_description.replaceAll("{codec}", name),
          dependsOn: "h264ify"
        }))
      };
    }
  });

  // tv/vacuumtube/preload/modules/settings/pages/guide-tabs.js
  var require_guide_tabs2 = __commonJS({
    "tv/vacuumtube/preload/modules/settings/pages/guide-tabs.js"(exports, module) {
      var configManager = require_config();
      var GUIDE_TABS = ["search", "shorts", "subscriptions", "library", "gaming", "music", "news", "podcasts", "movies_and_tv", "live", "sports"];
      module.exports = {
        id: "guide_tabs",
        GUIDE_TABS,
        title: (locale) => locale.guide_tabs.title,
        description: (locale) => locale.guide_tabs.description,
        rows: (config, locale) => GUIDE_TABS.map((name) => ({
          type: "toggle",
          id: `guide-tab-${name}`,
          title: locale.guide_tabs.tabs?.[name] || name,
          get: (config2) => !(config2.disabled_tabs || []).includes(name),
          set: (on, config2) => {
            const disabled = (config2.disabled_tabs || []).filter((tab) => tab !== name);
            configManager.set({ disabled_tabs: on ? disabled : [...disabled, name] });
          }
        }))
      };
    }
  });

  // tv/vacuumtube/preload/modules/settings/pages/userstyles.js
  var require_userstyles = __commonJS({
    "tv/vacuumtube/preload/modules/settings/pages/userstyles.js"(exports, module) {
      var { ipcRenderer } = require_electron();
      var configManager = require_config();
      async function getStyles() {
        try {
          return await ipcRenderer.invoke("get-userstyles");
        } catch (err) {
          console.error("[Settings Overlay] Failed to load userstyles:", err);
          return null;
        }
      }
      module.exports = {
        id: "userstyles",
        title: (locale) => locale.userstyles.manage_link,
        description: (locale) => locale.userstyles.description,
        rows: async (config, locale) => {
          const styles = await getStyles();
          return [
            styles === null && { type: "notice", tone: "warning", text: locale.userstyles.failed_to_load },
            styles?.length === 0 && { type: "notice", text: locale.userstyles.warn_empty },
            ...(styles || []).map(({ filename }) => ({
              type: "toggle",
              id: `userstyle-${filename}`,
              title: filename,
              dependsOn: "userstyles",
              get: (config2) => !(config2.disabled_userstyles || []).includes(filename),
              set: (on, config2) => {
                const disabled = (config2.disabled_userstyles || []).filter((f) => f !== filename);
                configManager.set({ disabled_userstyles: on ? disabled : [...disabled, filename] });
                window.dispatchEvent(new CustomEvent("vt-userstyle-toggle", {
                  detail: { filename, enabled: on }
                }));
              }
            })),
            { type: "button", title: locale.userstyles.open_folder, run: () => ipcRenderer.invoke("open-userstyles-folder") }
          ];
        }
      };
    }
  });

  // tv/vacuumtube/preload/modules/settings/pages/features.js
  var require_features = __commonJS({
    "tv/vacuumtube/preload/modules/settings/pages/features.js"(exports, module) {
      module.exports = {
        id: "features",
        title: (locale) => locale.features.title,
        rows: (config, locale) => [
          {
            type: "toggle",
            key: "music_mode_feature",
            title: locale.features.settings.music_mode.title,
            description: locale.features.settings.music_mode.description,
            dependsOn: "features_enabled"
          },
          {
            type: "toggle",
            key: "remaining_time_feature",
            title: locale.features.settings.remaining_time.title,
            description: locale.features.settings.remaining_time.description,
            dependsOn: "features_enabled"
          },
          {
            type: "toggle",
            key: "ultrawide_feature",
            title: locale.features.settings.ultrawide.title,
            description: locale.features.settings.ultrawide.description,
            dependsOn: "features_enabled"
          }
        ]
      };
    }
  });

  // tv/vacuumtube/preload/modules/settings/pages/mac-permissions.js
  var require_mac_permissions = __commonJS({
    "tv/vacuumtube/preload/modules/settings/pages/mac-permissions.js"(exports, module) {
      var { ipcRenderer } = require_electron();
      var status = null;
      var message = "";
      async function invoke(channel) {
        try {
          return await ipcRenderer.invoke(channel);
        } catch (err) {
          console.error(`[Settings Overlay] ${channel} failed:`, err);
          return "unknown";
        }
      }
      function requestMessage(locale, result) {
        const strings = locale.mac_permissions;
        if (result === "granted") return strings.request_granted;
        if (result === "not-determined") return strings.request_not_determined;
        if (result === "denied" || result === "restricted") return strings.request_denied_help;
        return strings.request_failed;
      }
      module.exports = {
        id: "mac_permissions",
        title: (locale) => locale.mac_permissions.title,
        description: (locale) => locale.mac_permissions.description,
        async onOpen() {
          status = null;
          message = "";
          status = await invoke("get-microphone-permission-status");
        },
        rows: (config, locale) => {
          const strings = locale.mac_permissions;
          const statusText = status === null ? strings.status_loading : strings.statuses[status] || strings.statuses.unknown;
          return [
            { type: "notice", title: strings.microphone_title, text: strings.microphone_description },
            { type: "info", title: strings.status_label, value: statusText },
            message && { type: "text", text: message },
            { type: "text", text: strings.restart_required },
            {
              type: "button",
              title: strings.request_microphone,
              run: async () => {
                status = null;
                message = "";
                status = await invoke("request-microphone-permission");
                message = requestMessage(locale, status);
              }
            },
            { type: "button", title: strings.open_microphone_settings, run: () => invoke("open-microphone-privacy-settings") },
            {
              type: "button",
              title: strings.reset_microphone,
              run: async () => {
                status = null;
                message = strings.resetting_microphone;
                await invoke("reset-microphone-permission");
                status = await invoke("request-microphone-permission");
                message = requestMessage(locale, status);
              }
            },
            { type: "button", title: strings.relaunch_app, run: () => invoke("relaunch-app") }
          ];
        }
      };
    }
  });

  // tv/vacuumtube/preload/modules/settings/pages/index.js
  var require_pages = __commonJS({
    "tv/vacuumtube/preload/modules/settings/pages/index.js"(exports, module) {
      var pages = [
        require_about(),
        require_sponsorblock(),
        require_h264ify2(),
        require_guide_tabs2(),
        require_userstyles(),
        require_features(),
        require_mac_permissions()
      ];
      module.exports = Object.fromEntries(pages.map((page) => [page.id, page]));
    }
  });

  // tv/vacuumtube/preload/modules/settings/schema.js
  var require_schema = __commonJS({
    "tv/vacuumtube/preload/modules/settings/schema.js"(exports, module) {
      var { ipcRenderer } = require_electron();
      var { SPONSORBLOCK_KEYS } = require_sponsorblock();
      var { CODEC_KEYS } = require_h264ify2();
      var { GUIDE_TABS } = require_guide_tabs2();
      function ratio(locale, on, total) {
        return locale.generic.ratio.replace("{on}", on).replace("{total}", total);
      }
      function countOn(config, keys) {
        return keys.filter((key) => config[key]).length;
      }
      module.exports = (locale) => [
        {
          id: "general",
          sections: [
            {
              id: "window",
              rows: [
                { type: "toggle", key: "fullscreen", onChange: (value) => ipcRenderer.invoke("set-fullscreen", value) },
                { type: "toggle", key: "no_window_decorations" },
                { type: "toggle", key: "keep_on_top", onChange: (value) => ipcRenderer.invoke("set-on-top", value) }
              ]
            },
            {
              id: "behavior",
              rows: [
                { type: "toggle", key: "pause_on_blur" }
              ]
            },
            {
              id: "updates",
              rows: [
                { type: "toggle", key: "auto_update", hide: !ipcRenderer.sendSync("can-auto-update") }
                //hidden if installation can't auto-update to begin with
              ]
            }
          ]
        },
        {
          id: "playback",
          sections: [
            {
              id: "content",
              rows: [
                { type: "toggle", key: "adblock" },
                {
                  type: "toggle",
                  key: "sponsorblock",
                  page: "sponsorblock",
                  summary: (config) => ratio(locale, countOn(config, SPONSORBLOCK_KEYS), SPONSORBLOCK_KEYS.length)
                },
                { type: "toggle", key: "block_continue_watching" },
                { type: "toggle", key: "dearrow" },
                { type: "toggle", key: "dislikes" }
              ]
            },
            {
              id: "quality",
              rows: [
                { type: "toggle", key: "unlock_resolution" },
                { type: "toggle", key: "remove_super_resolution" },
                { type: "toggle", key: "hardware_decoding" },
                { type: "toggle", key: "wayland_hdr", hide: true },
                {
                  type: "toggle",
                  key: "h264ify",
                  page: "h264ify",
                  title: locale.h264ify.enable_title,
                  description: locale.h264ify.enable_description,
                  summary: (config) => ratio(locale, countOn(config, CODEC_KEYS), CODEC_KEYS.length)
                }
              ]
            }
          ]
        },
        {
          id: "appearance",
          sections: [
            {
              id: "interface",
              rows: [
                { type: "toggle", key: "hide_shorts" },
                {
                  type: "link",
                  page: "guide_tabs",
                  title: locale.guide_tabs.title,
                  summary: (config) => ratio(locale, GUIDE_TABS.filter((tab) => !(config.disabled_tabs || []).includes(tab)).length, GUIDE_TABS.length)
                }
              ]
            },
            {
              id: "userstyles",
              rows: [
                { type: "toggle", key: "userstyles", page: "userstyles", title: locale.userstyles.enable, description: locale.userstyles.description }
              ]
            }
          ]
        },
        {
          id: "controls",
          sections: [
            {
              id: "input",
              rows: [
                { type: "toggle", key: "controller_support" }
              ]
            },
            {
              id: "devices",
              rows: [
                { type: "toggle", key: "device_discoverability" },
                { type: "link", page: "mac_permissions", title: locale.mac_permissions.title, hide: true }
              ]
            }
          ]
        },
        {
          id: "advanced",
          sections: [
            {
              id: "performance",
              rows: [
                { type: "toggle", key: "low_memory_mode" }
              ]
            },
            {
              id: "experimental",
              rows: [
                { type: "notice", tone: "warning", title: locale.features.notice_title, text: locale.features.notice_description },
                { type: "toggle", key: "features_enabled", page: "features", title: locale.features.enable_title, description: locale.features.enable_description }
              ]
            }
          ]
        }
      ];
    }
  });

  // tv/vacuumtube/preload/modules/settings/index.js
  var require_settings = __commonJS({
    "tv/vacuumtube/preload/modules/settings/index.js"(exports, module) {
      var __dirname = "/vt/src/preload/modules/settings";
      var fs = require_fs();
      var path = require_path();
      var { ipcRenderer, shell } = require_electron();
      var configManager = require_config();
      var css = require_css();
      var localeProvider = require_localeProvider();
      var functions = require_functions();
      var { renderCategory, renderPage } = require_render();
      var overlay = require_overlay();
      var focus = require_focus();
      var input = require_input();
      var scroll = require_scroll();
      var pages = require_pages();
      var schema = require_schema();
      var locale = null;
      var categories = [];
      var markOpened = () => {
      };
      var state = {
        open: false,
        category: 0,
        page: null
        //{ id, fromHeader, returnFocus } while a page is open
      };
      var renderCount = 0;
      var renderedView = null;
      function currentCategory() {
        return categories[state.category];
      }
      function categoryTabIndex() {
        return state.page?.fromHeader ? -1 : state.category;
      }
      async function render() {
        if (!state.open) return;
        const renderId = ++renderCount;
        const config = configManager.get();
        const context = {
          config,
          locale,
          pages,
          openPage,
          goBack,
          translation: localeProvider.getTranslation(),
          openTranslation: () => shell.openExternal("https://github.com/shy1132/VacuumTube/blob/main/locale/README.md")
        };
        let view;
        if (state.page) {
          const page = pages[state.page.id];
          const sections = page.sections ? await page.sections(config, locale) : [{ rows: await page.rows(config, locale) }];
          if (renderId !== renderCount) return;
          const parent = state.page.fromHeader ? locale.generic.title : locale.categories[currentCategory().id]?.title;
          view = renderPage(page, sections, parent, context);
        } else {
          view = renderCategory(currentCategory(), context);
        }
        overlay.list().replaceChildren(view);
        overlay.markSelectedTab(categoryTabIndex());
        const viewId = `${currentCategory().id}/${state.page?.id}`;
        if (viewId !== renderedView) {
          renderedView = viewId;
          scroll.reset();
        }
        focus.restore();
      }
      function openCategory(index) {
        state.category = index;
        state.page = null;
        return render();
      }
      async function openPage(id, { fromHeader = false } = {}) {
        const page = pages[id];
        state.page = { id, fromHeader, returnFocus: focus.id() };
        focus.want();
        await render();
        if (page.onOpen) {
          const refresh = () => state.open && state.page?.id === id ? render() : Promise.resolve();
          await page.onOpen({ refresh });
          await render();
        }
      }
      function goBack() {
        if (!state.page) return close();
        const returnFocus = state.page.returnFocus;
        state.page = null;
        render().then(() => focus.restore(returnFocus));
      }
      async function activate(node = focus.current()) {
        if (!node?.vtActivate) return;
        focus.set(node);
        try {
          const running = node.vtActivate();
          if (running instanceof Promise) {
            await render();
            await running;
          }
        } catch (err) {
          console.error("[Settings Overlay] Action failed:", err);
        }
        render();
      }
      function open() {
        if (window.ytcfg.data_.INNERTUBE_CLIENT_NAME === "TVHTML5_FOR_KIDS") return;
        state.open = true;
        state.page = null;
        state.category = 0;
        focus.want(`tab:${currentCategory().id}`);
        markOpened();
        overlay.show();
        render();
      }
      function close() {
        state.open = false;
        overlay.hide();
      }
      function toggle() {
        if (state.open) {
          close();
        } else {
          open();
        }
      }
      module.exports = async () => {
        await localeProvider.waitUntilAvailable();
        await functions.waitForCondition(() => !!document.body);
        locale = localeProvider.getLocale().settings;
        categories = schema(locale).filter((category) => !category.hide);
        await css.inject("settings", fs.readFileSync(path.join(__dirname, "style.css"), "utf-8"));
        document.body.appendChild(overlay.create(categories, locale, {
          //activating a tab moves focus into the settings, arrow keys across the tabs do not
          openCategory: async (index) => {
            await openCategory(index);
            focus.first();
          },
          openAbout: () => openPage("about", { fromHeader: true }),
          close
        }));
        scroll.attach(
          document.querySelector(".vt-content-viewport"),
          overlay.list(),
          document.getElementById("vt-content-scrollbar-thumb")
        );
        markOpened = input.listen({
          isOpen: () => state.open,
          toggle,
          close,
          back: goBack,
          move: (direction) => focus.move(direction, openCategory),
          activate
        }).markOpened;
        ipcRenderer.on("config-update", render);
        window.vtOpenSettingsOverlay = open;
        window.vtToggleSettingsOverlay = toggle;
      };
      module.exports.openSettingsOverlay = open;
    }
  });

  // tv/vacuumtube/preload/modules/speed-control/index.js
  var require_speed_control = __commonJS({
    "tv/vacuumtube/preload/modules/speed-control/index.js"(exports, module) {
      var __dirname = "/vt/src/preload/modules/speed-control";
      var fs = require_fs();
      var path = require_path();
      var rcMod = require_resolveCommandModifiers();
      var css = require_css();
      var functions = require_functions();
      var configManager = require_config();
      var shortcuts = require_shortcuts();
      var config = configManager.get();
      module.exports = async () => {
        const el = functions.el;
        await functions.waitForCondition(() => !!document.body);
        const cssPath = path.join(__dirname, "style.css");
        const text = fs.readFileSync(cssPath, "utf-8");
        css.inject("speed-control", text);
        let lastCustomPlaybackRate = 2;
        let currentPlacbackRate = 1;
        let speedTimeout;
        function createSpeedIndicator() {
          return el("div", { id: "vt-speed-indicator" }, [
            el("div", { id: "vt-speed-icon", className: "vt-speed-icon" }),
            el("span", { id: "vt-speed-text", className: "vt-speed-text" })
          ]);
        }
        const speedIndicatorElement = createSpeedIndicator();
        document.body.appendChild(speedIndicatorElement);
        function showSpeedIndicator() {
          const indicator = document.getElementById("vt-speed-indicator");
          const text2 = document.getElementById("vt-speed-text");
          if (!indicator || !text2) return;
          text2.textContent = `${currentPlacbackRate}x`;
          indicator.classList.add("visible");
          clearTimeout(speedTimeout);
          speedTimeout = setTimeout(() => {
            indicator.classList.remove("visible");
          }, 1500);
        }
        function setPlaybackRate(step) {
          let players = document.querySelectorAll(".html5-video-player");
          for (let player of players) {
            if (!player?.setPlaybackRate) continue;
            currentPlacbackRate = player.getPlaybackRate();
            if (step == 0) {
              if (currentPlacbackRate != 1) {
                lastCustomPlaybackRate = currentPlacbackRate;
                currentPlacbackRate = 1;
              } else {
                currentPlacbackRate = lastCustomPlaybackRate;
              }
            } else {
              currentPlacbackRate = Math.max(0.25, Math.min(currentPlacbackRate + step, 2));
            }
            player.setPlaybackRate(currentPlacbackRate);
          }
        }
        function isWatching() {
          let isShort = !!document.querySelector("ytlr-shorts-page")?.classList?.contains("zylon-focus");
          if (isShort) {
            return true;
          } else {
            let baseUri = window.yt?.player?.utils?.videoElement_?.baseURI;
            if (!baseUri || !baseUri.includes("/watch?v=")) return false;
            let id = baseUri.split("/watch?v=")[1]?.slice(0, 11);
            if (!id) return false;
            return true;
          }
        }
        document.addEventListener("keydown", (e) => {
          const key = e.key || e.keyCode;
          if (!key || !isWatching() || !shortcuts.isShortcutKey(e))
            return;
          const speedStep = 0.25;
          if (key === "s" || key === "S") {
            setPlaybackRate(0);
          } else if (key === "a" || key === "A") {
            setPlaybackRate(-speedStep);
          } else if (key === "d" || key === "D") {
            setPlaybackRate(speedStep);
          } else {
            return;
          }
          e.preventDefault();
          e.stopPropagation();
          e.stopImmediatePropagation();
          showSpeedIndicator();
        }, true);
      };
    }
  });

  // tv/vacuumtube/preload/modules/sponsorblock.js
  var require_sponsorblock2 = __commonJS({
    "tv/vacuumtube/preload/modules/sponsorblock.js"(exports, module) {
      var { ipcRenderer } = require_electron();
      var ui = require_ui();
      var css = require_css();
      var localeProvider = require_localeProvider();
      var configManager = require_config();
      var config = configManager.get();
      var SPONSORBLOCK_CATEGORIES = ["sponsor", "selfpromo", "interaction", "intro", "outro", "preview", "hook", "filler"];
      var SEGMENT_COLORS = {
        //same as sponsorblock
        "sponsor": "#00d400",
        "selfpromo": "#ffff00",
        "interaction": "#cc00ff",
        "intro": "#00ffff",
        "outro": "#0202ed",
        "preview": "#008fd6",
        "hook": "#395699",
        "filler": "#7300ff"
      };
      function isEnabled(segment) {
        return config.sponsorblock && config[`sponsorblock_skip_${segment.category}`];
      }
      async function getSegments(videoId, categories) {
        let params = new URLSearchParams({ videoID: videoId, service: "YouTube", categories: JSON.stringify(categories) });
        let res = await fetch(`https://sponsor.ajay.app/api/skipSegments?${params}`);
        if (res.status === 404) return [];
        if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
        let data = await res.json();
        return data.map(({ segment, category }) => ({ startTime: segment[0], endTime: segment[1], category }));
      }
      function getLayers(segments, from, to) {
        let layers = [];
        for (let segment of segments) {
          if (segment.endTime <= from || segment.startTime >= to) continue;
          let color = SEGMENT_COLORS[segment.category] || "#ffffff";
          let start = Math.max(0, (segment.startTime - from) / (to - from) * 100);
          let end = Math.min(100, (segment.endTime - from) / (to - from) * 100);
          layers.push(`linear-gradient(to right, transparent ${start}%, ${color} ${start}%, ${color} ${end}%, transparent ${end}%)`);
        }
        return layers.join(", ") || "none";
      }
      function getChapters(duration) {
        let pieces = document.querySelectorAll('ytlr-multi-markers-player-bar-renderer [idomkey="progress-bar"] > [idomkey^="chapter-"]');
        if (pieces.length === 0 || !(duration > 0)) return null;
        let total = 0;
        for (let piece of pieces) {
          total += parseFloat(piece.style.width);
        }
        if (!(total > 0)) return null;
        let chapters = [];
        let start = 0;
        for (let piece of pieces) {
          let width = parseFloat(piece.style.width);
          chapters.push({ key: piece.getAttribute("idomkey"), from: start / total * duration, to: (start + width) / total * duration });
          start += width;
        }
        return chapters;
      }
      function showSegments(segments, duration, chapters) {
        if (segments.length === 0 || !(duration > 0)) {
          css.delete("sponsorblock-segments");
          return;
        }
        segments = [...segments].sort((a, b) => a.endTime - a.startTime - (b.endTime - b.startTime));
        let style = `ytlr-progress-bar [idomkey="slider"]::after { background: ${getLayers(segments, 0, duration)}; }`;
        if (chapters) {
          for (let chapter of chapters) {
            style += `ytlr-multi-markers-player-bar-renderer [idomkey="${chapter.key}"]::after { background: ${getLayers(segments, chapter.from, chapter.to)}; }`;
          }
        }
        css.inject("sponsorblock-segments", style);
      }
      module.exports = async () => {
        await localeProvider.waitUntilAvailable();
        let locale = localeProvider.getLocale();
        css.inject("sponsorblock-bar", `
        ytlr-progress-bar [idomkey="slider"]::after,
        ytlr-multi-markers-player-bar-renderer [idomkey^="chapter-"]::after {
            content: '';
            position: absolute;
            inset: 0;
            pointer-events: none;
            opacity: 0.7;
        }
    `);
        let sponsorBlockSegments = [];
        let chapters = null;
        const getDuration = () => document.querySelector(".html5-video-player")?.getDuration?.();
        const updateSegments = () => showSegments(sponsorBlockSegments.filter(isEnabled), getDuration(), chapters);
        ipcRenderer.on("config-update", updateSegments);
        setInterval(() => {
          if (sponsorBlockSegments.length === 0) return;
          let found = getChapters(getDuration());
          if (!found || JSON.stringify(found) === JSON.stringify(chapters)) return;
          chapters = found;
          updateSegments();
        }, 1e3);
        let activeVideoId = 0;
        let attachVideoTimeout = null;
        let activeVideo = null;
        const attachToVideo = function() {
          clearTimeout(attachVideoTimeout);
          attachVideoTimeout = null;
          activeVideo = document.querySelector("video");
          if (!activeVideo) {
            attachVideoTimeout = setTimeout(attachToVideo, 100);
            return;
          }
          console.log("[SponsorBlock] Attached to video ID", activeVideoId);
          activeVideo.addEventListener("timeupdate", checkForSponsorSkip);
          activeVideo.addEventListener("durationchange", updateSegments);
          updateSegments();
        };
        const checkForSponsorSkip = function() {
          if (!config.sponsorblock || !activeVideo || sponsorBlockSegments.length === 0) return;
          if (activeVideo.paused) return;
          let matchingSegment = sponsorBlockSegments.filter((v) => {
            return isEnabled(v) && activeVideo.currentTime > v.startTime && activeVideo.currentTime < v.startTime + 2 && activeVideo.currentTime < v.endTime;
          }).sort((x, y) => x.startTime - y.startTime);
          if (matchingSegment.length === 0) return;
          console.log("[SponsorBlock] Skipping sponsor segment");
          activeVideo.currentTime = matchingSegment[0].endTime;
          ui.toast("VacuumTube", locale.sponsorblock[`${matchingSegment[0].category}_skipped`]);
        };
        const onNavigate = () => {
          const pageUrl = new URL(location.hash.substring(1), location.href);
          if (pageUrl.pathname === "/watch") {
            const videoId = pageUrl.searchParams.get("v");
            if (videoId === activeVideoId) return;
            sponsorBlockSegments = [];
            chapters = null;
            activeVideoId = videoId;
            updateSegments();
            if (!config.sponsorblock) return;
            getSegments(videoId, SPONSORBLOCK_CATEGORIES).then((segments) => {
              if (activeVideoId !== videoId) return;
              sponsorBlockSegments = segments;
              attachToVideo();
            }).catch((err) => {
              console.error("[SponsorBlock] Failed to get segments for", videoId, err);
            });
          } else {
            activeVideo = null;
            activeVideoId = 0;
            sponsorBlockSegments = [];
            chapters = null;
            updateSegments();
            if (attachVideoTimeout != null) {
              clearTimeout(attachVideoTimeout);
              attachVideoTimeout = null;
            }
          }
        };
        window.addEventListener("hashchange", onNavigate);
        onNavigate();
      };
    }
  });

  // tv/vacuumtube/preload/modules/support-webp.js
  var require_support_webp = __commonJS({
    "tv/vacuumtube/preload/modules/support-webp.js"(exports, module) {
      var configOverrides = require_configOverrides();
      module.exports = () => {
        configOverrides.ytcfgOverrides.push({
          INNERTUBE_CONTEXT: {
            client: {
              webpSupport: true
            }
          }
        });
      };
    }
  });

  // tv/vacuumtube/preload/modules/ultrawide.js
  var require_ultrawide = __commonJS({
    "tv/vacuumtube/preload/modules/ultrawide.js"(exports, module) {
      var { ipcRenderer } = require_electron();
      var configManager = require_config();
      var css = require_css();
      var ultrawideCSS = `
div#container,
yt-virtual-list,
ytlr-section-list-renderer,
ytlr-horizontal-list-renderer,
ytlr-tv-surface-content-renderer,
div[idomkey="shadow"],
ytlr-two-column-renderer,
yt-route,
ytlr-welcome-immersive-value-prop
{
    width: 100vw !important;
    max-width: 100vw !important;
    box-sizing: border-box !important;
    min-width: 100vw !important;
}
`;
      function isEnabled(config) {
        return config.features_enabled === true && config.ultrawide_feature === true;
      }
      function update(enabled) {
        if (enabled) {
          css.inject("ultrawide", ultrawideCSS);
        } else {
          css.delete("ultrawide");
        }
      }
      module.exports = () => {
        let enabled = isEnabled(configManager.get());
        if (enabled) {
          update(true);
        }
        ipcRenderer.on("config-update", (event, config) => {
          if (isEnabled(config) === enabled) return;
          enabled = isEnabled(config);
          update(enabled);
        });
      };
    }
  });

  // tv/vacuumtube/preload/modules/userstyles.js
  var require_userstyles2 = __commonJS({
    "tv/vacuumtube/preload/modules/userstyles.js"(exports, module) {
      var { ipcRenderer } = require_electron();
      var configManager = require_config();
      var functions = require_functions();
      var css = require_css();
      var config = configManager.get();
      var injected = /* @__PURE__ */ new Set();
      function injectCSS(filename, text) {
        const id = getId(filename);
        injected.add(id);
        css.inject(id, text);
        console.log(`[Userstyles] Injected: ${id}`);
      }
      function removeCSS(identifier, isFileName) {
        let id = identifier;
        if (isFileName) {
          id = getId(identifier);
        }
        injected.delete(id);
        css.delete(id);
        console.log(`[Userstyles] Removed: ${id}`);
      }
      async function loadUserstyles() {
        if (!config.userstyles) {
          console.log("[Userstyles] Disabled in config");
          return;
        }
        try {
          const styles = await ipcRenderer.invoke("get-userstyles");
          let loaded = 0;
          styles.forEach(({ filename, css: css2 }) => {
            if (!config.disabled_userstyles.includes(filename)) {
              injectCSS(filename, css2);
              loaded++;
            } else {
              console.log(`[Userstyles] Skipping disabled: ${filename}`);
            }
          });
          console.log(`[Userstyles] Loaded ${loaded} of ${styles.length} stylesheets`);
        } catch (err) {
          console.error("[Userstyles] Failed to load styles:", err);
        }
      }
      function getId(filename) {
        let filenameNoExt = filename.slice(0, -".css".length);
        let cleanFilename = filenameNoExt.replace(/[^a-zA-Z0-9]/g, "-");
        return `userstyle-${cleanFilename}`;
      }
      module.exports = async () => {
        await functions.waitForCondition(() => !!document.head);
        console.log("[Userstyles] Initializing...");
        await loadUserstyles();
        ipcRenderer.on("config-update", (event, newConfig) => {
          const wasEnabled = config.userstyles;
          config = newConfig;
          if (config.userstyles && !wasEnabled) {
            loadUserstyles();
          } else if (!config.userstyles && wasEnabled) {
            injected.forEach((id) => {
              removeCSS(id);
            });
          }
        });
        ipcRenderer.on("userstyle-updated", (event, { filename, css: css2 }) => {
          if (config.userstyles && !config.disabled_userstyles.includes(filename)) {
            injectCSS(filename, css2);
          }
        });
        ipcRenderer.on("userstyle-removed", (event, { filename }) => {
          removeCSS(filename, true);
        });
        window.addEventListener("vt-userstyle-toggle", async (event) => {
          const { filename, enabled } = event.detail;
          if (!config.userstyles) return;
          console.log(`[Userstyles] Toggle ${filename}: ${enabled ? "enabled" : "disabled"}`);
          if (enabled) {
            try {
              const styles = await ipcRenderer.invoke("get-userstyles");
              const style = styles.find((s) => s.filename === filename);
              if (style) {
                injectCSS(style.filename, style.css);
              }
            } catch (err) {
              console.error(`[Userstyles] Failed to load ${filename}:`, err);
            }
          } else {
            removeCSS(filename, true);
          }
        });
        console.log("[Userstyles] Initialized");
      };
    }
  });

  // tv/vacuumtube/preload/modules/voice-privacy-notice.js
  var require_voice_privacy_notice = __commonJS({
    "tv/vacuumtube/preload/modules/voice-privacy-notice.js"(exports, module) {
      var configOverrides = require_configOverrides();
      module.exports = () => {
        if (false) return;
        configOverrides.environmentOverrides.push({
          feature_switches: {
            has_samsung_voice_privacy_notice: true
          }
        });
      };
    }
  });

  // tv/vacuumtube/preload/modules/volume-control/index.js
  var require_volume_control = __commonJS({
    "tv/vacuumtube/preload/modules/volume-control/index.js"(exports, module) {
      var __dirname = "/vt/src/preload/modules/volume-control";
      var fs = require_fs();
      var path = require_path();
      var rcMod = require_resolveCommandModifiers();
      var css = require_css();
      var functions = require_functions();
      var configManager = require_config();
      var shortcuts = require_shortcuts();
      var config = configManager.get();
      module.exports = async () => {
        const el = functions.el;
        await functions.waitForCondition(() => !!document.body);
        const cssPath = path.join(__dirname, "style.css");
        const text = fs.readFileSync(cssPath, "utf-8");
        css.inject("volume-control", text);
        let volume = config.volume ?? 100;
        let muted = false;
        let volumeTimeout;
        function createVolumeIndicator() {
          return el("div", { id: "vt-volume-indicator" }, [
            el("div", { id: "vt-volume-icon", className: "vt-volume-icon" }),
            el("div", { className: "vt-volume-bar-container" }, [
              el("div", { id: "vt-volume-bar", className: "vt-volume-bar" })
            ]),
            el("span", { id: "vt-volume-text", className: "vt-volume-text" })
          ]);
        }
        const volumeIndicatorElement = createVolumeIndicator();
        document.body.appendChild(volumeIndicatorElement);
        function showVolumeIndicator() {
          const indicator = document.getElementById("vt-volume-indicator");
          const bar = document.getElementById("vt-volume-bar");
          const text2 = document.getElementById("vt-volume-text");
          const icon = document.getElementById("vt-volume-icon");
          if (!indicator || !bar || !text2 || !icon) return;
          let userFacingVolume = muted ? 0 : volume;
          bar.style.width = `${userFacingVolume}%`;
          text2.textContent = `${userFacingVolume}%`;
          if (volume === 0 || muted) {
            icon.className = "vt-volume-icon vt-volume-muted";
          } else if (volume <= 50) {
            icon.className = "vt-volume-icon vt-volume-low";
          } else {
            icon.className = "vt-volume-icon vt-volume-high";
          }
          indicator.classList.add("visible");
          clearTimeout(volumeTimeout);
          volumeTimeout = setTimeout(() => {
            indicator.classList.remove("visible");
          }, 1500);
        }
        function setVolume(interval) {
          if (interval && config.volume !== volume) {
            configManager.set({ volume });
          }
          let players = document.querySelectorAll(".html5-video-player");
          for (let player of players) {
            if (!player?.setVolume) continue;
            if (muted) {
              player.setVolume(0);
            } else {
              player.setVolume(volume);
            }
          }
        }
        function isWatching() {
          let isShort = !!document.querySelector("ytlr-shorts-page")?.classList?.contains("zylon-focus");
          if (isShort) {
            return true;
          } else {
            let baseUri = window.yt?.player?.utils?.videoElement_?.baseURI;
            if (!baseUri || !baseUri.includes("/watch?v=")) return false;
            let id = baseUri.split("/watch?v=")[1]?.slice(0, 11);
            if (!id) return false;
            return true;
          }
        }
        document.addEventListener("keydown", (e) => {
          const key = e.key || e.keyCode;
          if (!key || !isWatching() || !shortcuts.isShortcutKey(e)) return;
          const volumeStep = 5;
          if (key === "+" || key === "=" || key === 187 || key === 16002) {
            volume = Math.min(100, volume + volumeStep);
          } else if (key === "-" || key === 189 || key === 16001) {
            volume = Math.max(0, volume - volumeStep);
          } else if (key === "m" || key === "M" || key === 77 || key === 16e3) {
            muted = !muted;
          } else {
            return;
          }
          e.preventDefault();
          e.stopPropagation();
          e.stopImmediatePropagation();
          showVolumeIndicator();
          setVolume();
        }, true);
        rcMod.addInputModifier((c) => {
          if (!c.volumeControlAction) return c;
          let action = c.volumeControlAction;
          let type = action.volumeControlType;
          if (type === "VOLUME_CONTROL_ACTION_TYPE_MUTE") {
            muted = true;
          } else if (type === "VOLUME_CONTROL_ACTION_TYPE_UNMUTE") {
            muted = false;
          } else if (type === "VOLUME_CONTROL_ACTION_TYPE_SET_ABSOLUTE") {
            volume = action.volumeControlValue;
          }
          showVolumeIndicator();
          setVolume();
          return false;
        });
        setInterval(() => {
          setVolume(true);
        }, 100);
      };
    }
  });

  // vt:vt:modules
  var require_vt_modules = __commonJS({
    "vt:vt:modules"(exports, module) {
      module.exports = [
        require_touch(),
        require_adblock(),
        require_block_continue_watching(),
        require_block_sign_in_popup(),
        require_cast_label(),
        require_controller_support(),
        require_css2(),
        require_dearrow(),
        require_disable_direct_sign_in(),
        require_encryption_notice(),
        require_fix_exit(),
        require_fix_reloads(),
        require_fix_voice(),
        require_guide_tabs(),
        require_h264ify(),
        require_h5vcc(),
        require_hide_shorts(),
        require_keybinds(),
        require_leanback_settings(),
        require_low_memory_mode(),
        require_media_capabilities(),
        require_mouse(),
        require_music_mode(),
        require_no_f11(),
        require_pause_on_blur(),
        require_remaining_time(),
        require_remove_super_resolution(),
        require_return_youtube_dislike(),
        require_settings(),
        require_speed_control(),
        require_sponsorblock2(),
        require_support_webp(),
        require_ultrawide(),
        require_userstyles2(),
        require_voice_privacy_notice(),
        require_volume_control()
      ];
    }
  });

  // tv/entry.js
  require_trusted_types();
  require_gamepad();
  if (location.host === "www.youtube.com" && location.pathname === "/tv") {
    const xhrModifiers = require_xhrModifiers();
    xhrModifiers.block();
    for (const module of require_vt_modules()) {
      try {
        module();
      } catch (err) {
        console.error("a module experienced failure while loading", err);
      }
    }
    xhrModifiers.unblock();
  }
})();
