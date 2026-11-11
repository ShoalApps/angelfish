"""Prepare matching release notes for the APK and GitHub before building."""

import json
import re
import subprocess
import sys
from pathlib import Path


ASSET = Path("app/src/main/assets/whats-new.json")


def git(*args):
    return subprocess.check_output(["git", *args], text=True)


def release_entries(previous_tag):
    current = json.loads(ASSET.read_text(encoding="utf-8"))
    if not previous_tag:
        return current

    previous_files = git("ls-tree", "--name-only", previous_tag, "--", str(ASSET))
    previous = json.loads(git("show", f"{previous_tag}:{ASSET}")) if previous_files else []
    if current and current != previous:
        return current

    subjects = git("log", "--no-merges", "--format=%s", f"{previous_tag}..HEAD").splitlines()
    titles = [re.sub(r" \(#\d+\)$", "", subject) for subject in subjects]
    entries = [{"title": title, "body": ""} for title in dict.fromkeys(titles)]
    return entries or [{"title": "This build uses the same source as the previous release.", "body": ""}]


def main():
    previous_tag, body_path = sys.argv[1:]
    entries = release_entries(previous_tag)
    bullets = []
    for entry in entries:
        title, body = entry["title"], entry["body"]
        bullets.append(f"- **{title}** — {body}" if body else f"- {title}")

    markdown = "## What's new\n\n" + "\n".join(bullets)
    markdown += (
        "\n\nInstall `Angelfish.apk` below. Angelfish is free software under the GPL-3.0 "
        "and is not affiliated with Google or YouTube.\n"
    )
    Path(body_path).write_text(markdown, encoding="utf-8")
    ASSET.write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
