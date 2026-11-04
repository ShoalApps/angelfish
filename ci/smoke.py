#!/usr/bin/env python3
"""
Walks through Angelfish on an Android emulator, taking screenshots and
collecting crashes, so the app can be checked without a phone.
Usage: python3 ci/smoke.py Angelfish.apk
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "io.github.shoalapps.swordfish"
OUT = "shots"
os.makedirs(OUT, exist_ok=True)
log_lines = []
step = 0


def sh(cmd, timeout=60):
    try:
        r = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return r.stdout + r.stderr
    except subprocess.TimeoutExpired:
        return "TIMEOUT"


def adb(c, timeout=60):
    return sh(f"adb {c}", timeout)


def note(msg):
    print(msg, flush=True)
    log_lines.append(msg)


def size():
    m = re.search(r"(\d+)x(\d+)", adb("shell wm size"))
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


W, H = 1080, 2400


def shot(name):
    global step
    step += 1
    path = f"{OUT}/{step:02d}_{name}.png"
    sh(f"adb exec-out screencap -p > {path}")
    note(f"[shot] {path} alive={alive()}")


def alive():
    return bool(adb(f"shell pidof {PKG}").strip())


def dump():
    """Returns a list of (text, desc, cx, cy) for every node on screen."""
    for _ in range(3):
        out = adb("shell uiautomator dump /sdcard/ui.xml", timeout=30)
        if "dumped" in out:
            break
        time.sleep(2)
    adb("pull /sdcard/ui.xml /tmp/ui.xml")
    nodes = []
    try:
        root = ET.parse("/tmp/ui.xml").getroot()
    except Exception:
        return nodes
    for n in root.iter("node"):
        b = re.findall(r"\d+", n.get("bounds", ""))
        if len(b) != 4:
            continue
        x1, y1, x2, y2 = map(int, b)
        nodes.append((n.get("text", ""), n.get("content-desc", ""), (x1 + x2) // 2, (y1 + y2) // 2))
    return nodes


def screen_text():
    return " | ".join(t or d for t, d, _, _ in dump() if (t or d))


def tap_xy(x, y):
    adb(f"shell input tap {x} {y}")


def tap(label, exact=False, wait=2.5, bottom=False):
    nodes = dump()
    if bottom:
        nodes = sorted(nodes, key=lambda n: -n[3])
    for t, d, x, y in nodes:
        for v in (t, d):
            if v and ((v == label) if exact else (label.lower() in v.lower())):
                tap_xy(x, y)
                note(f"[tap] '{label}' at {x},{y}")
                time.sleep(wait)
                return True
    note(f"[miss] could not find '{label}'")
    return False


def swipe_up(n=1, dur=300):
    for _ in range(n):
        adb(f"shell input swipe {W // 2} {int(H * 0.75)} {W // 2} {int(H * 0.3)} {dur}")
        time.sleep(0.8)


def swipe_down(n=1, dur=300):
    for _ in range(n):
        adb(f"shell input swipe {W // 2} {int(H * 0.3)} {W // 2} {int(H * 0.75)} {dur}")
        time.sleep(0.8)


def back(wait=1.5):
    adb("shell input keyevent 4")
    time.sleep(wait)


def check_text(label, *needles):
    txt = screen_text()
    found = [n for n in needles if n.lower() in txt.lower()]
    note(f"[check] {label}: found={found} text={txt[:600]}")
    return found


def main():
    global W, H
    apk = sys.argv[1]
    note(adb(f"install -r -g {apk}", timeout=180).strip())
    adb(f"shell pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    adb("shell settings put system screen_off_timeout 1800000")
    adb("shell svc power stayon true")
    adb("logcat -c")
    W, H = size()
    note(f"screen {W}x{H}")
    for _ in range(30):
        if "1 received" in adb("shell ping -c 1 -W 2 www.youtube.com", timeout=10):
            note("network ready")
            break
        time.sleep(2)

    # 1. Home (light)
    adb("shell cmd uimode night no")
    adb(f"shell am start -n {PKG}/.MainActivity")
    time.sleep(20)
    tap("Let's dive in", exact=True)
    shot("home_light")
    check_text("home", "Angelfish", "Home", "Shorts", "Subscriptions", "You")

    # 2. Scroll down and back up: thumbnails should still be there
    swipe_up(5)
    time.sleep(3)
    shot("home_scrolled")
    swipe_down(4, dur=250)
    time.sleep(0.3)
    shot("home_back_up_immediate")
    swipe_down(1, dur=600)
    time.sleep(1)

    # 3. Open the first video
    tap_xy(W // 2, int(H * 0.30))
    time.sleep(12)
    shot("player_opened")
    time.sleep(12)
    shot("player_after_24s")
    check_text("player", "not a bot", "Playback error", "Try again", "Comments", "Share")
    swipe_up(1)
    time.sleep(1)
    shot("watch_page_scrolled")

    # 4. Shrink to the mini player
    back(2)
    shot("mini_player")

    # 4b. The reported crash: search while a video is minimised, scroll, go back, reopen the video
    if tap("Search", exact=True, wait=3):
        adb("shell input text music")
        adb("shell input keyevent 66")
        time.sleep(10)
        swipe_up(8)
        time.sleep(4)
        swipe_up(6)
        time.sleep(4)
        shot("search_scrolled")
        note(f"[crash-check] after search scroll alive={alive()}")
        back(2)
        back(2)
        shot("back_from_search")
    mini_y = int(H * 0.842)
    tap_xy(W // 2, mini_y)
    time.sleep(4)
    shot("mini_reopened")
    note(f"[crash-check] after reopening alive={alive()}")
    back(2)
    shot("collapsed_again")
    # Leave the app and come back
    adb("shell input keyevent 3")
    time.sleep(4)
    adb(f"shell am start -n {PKG}/.MainActivity")
    time.sleep(5)
    shot("returned_to_app")
    note(f"[crash-check] after leaving and returning alive={alive()}")

    # 5. Shorts
    if tap("Shorts", exact=True, wait=18, bottom=True):
        shot("shorts_first")
        swipe_up(1)
        time.sleep(6)
        shot("shorts_second")
        swipe_up(1)
        time.sleep(4)
        shot("shorts_third")
    else:
        tap_xy(int(W * 0.375), H - 120)
        time.sleep(15)
        shot("shorts_fallback")

    # 6. Subscriptions, You
    tap("Subscriptions", exact=True, wait=6)
    shot("subscriptions")
    tap("You", exact=True, wait=4)
    shot("you")

    # 7. About + unlock developer settings
    if tap("About Angelfish", wait=5):
        shot("about")
        time.sleep(2)
        shot("about_morph_2s_later")
        for _ in range(7):
            tap("Version", wait=0.4)
        shot("about_after_taps")
        back()

    # 8. Settings + developer settings
    tap("Settings", exact=True, wait=3)
    shot("settings_top")
    swipe_up(3)
    shot("settings_middle")
    swipe_up(6)
    shot("settings_bottom")
    if tap("Developer settings", wait=3):
        shot("developer_top")
        swipe_up(4)
        shot("developer_bottom")
        back()
    back()

    # 9. Feed switcher: Learning feed
    tap("Home", exact=True, wait=3)
    if tap("Choose feed", wait=3):
        shot("feed_sheet")
        tap("Learning", exact=True, wait=2)
        tap_xy(W // 2, int(H * 0.12))  # tap above the sheet to close it
        time.sleep(15)
        shot("learning_feed")
        if tap("Choose feed", wait=3):
            tap("Recommended", exact=True, wait=2)
            tap_xy(W // 2, int(H * 0.12))
            time.sleep(12)
            shot("recommended_again")

    # 9b. Chart chips
    if tap("Music", exact=True, wait=10):
        shot("chip_music")
        tap("All", exact=True, wait=8)

    # 10. Search
    if tap("Search", exact=True, wait=3):
        adb("shell input text android")
        adb("shell input keyevent 66")
        time.sleep(10)
        shot("search_results")
        back()
        back()

    # 11. Dark theme
    adb("shell cmd uimode night yes")
    time.sleep(6)
    shot("home_dark")
    tap("Shorts", exact=True, wait=12, bottom=True)
    shot("shorts_dark")
    tap("Home", exact=True, wait=3)
    adb("shell cmd uimode night no")

    # Swipe the mini player away
    adb(f"shell input swipe {int(W * 0.3)} {int(H * 0.842)} {int(W * 0.98)} {int(H * 0.842)} 200")
    time.sleep(2)
    shot("mini_swiped_away")

    # Launcher shortcut
    adb(f"shell am start -a android.intent.action.VIEW -d materialtube://search -n {PKG}/.MainActivity")
    time.sleep(3)
    shot("shortcut_search")

    # Logs
    logcat = adb("logcat -d -v time", timeout=120)
    with open(f"{OUT}/logcat_full.txt", "w") as f:
        f.write(logcat)
    crashes = [l for l in logcat.splitlines() if "FATAL EXCEPTION" in l or "AndroidRuntime" in l]
    app_lines = [l for l in logcat.splitlines() if PKG in l or "materialtube" in l.lower() or " E/" in l]
    with open(f"{OUT}/logcat_app.txt", "w") as f:
        f.write("\n".join(app_lines[-3000:]))
    with open(f"{OUT}/crashes.txt", "w") as f:
        # include the stack trace lines that follow each AndroidRuntime line
        f.write("\n".join(crashes) if crashes else "no crashes")
    note(f"crash lines: {len(crashes)}")
    with open(f"{OUT}/summary.txt", "w") as f:
        f.write("\n".join(log_lines))


if __name__ == "__main__":
    try:
        main()
    finally:
        with open(f"{OUT}/summary.txt", "w") as f:
            f.write("\n".join(log_lines))
