#!/usr/bin/env bash
set -euo pipefail

assets=$(cd "$(dirname "$0")" && pwd)
source_dir=${1:?Usage: bash switch/prepare.sh NEW_SOURCE_DIRECTORY}
upstream_commit=a54b009f6d046a8ea7585e86c3c7ed41c4a57984

mkdir "$source_dir"
git -C "$source_dir" init --quiet
git -C "$source_dir" remote add origin https://github.com/mirusu400/switch-newpipe.git
git -C "$source_dir" fetch --quiet --depth 1 origin "$upstream_commit"
git -C "$source_dir" checkout --quiet --detach FETCH_HEAD
git -C "$source_dir" submodule update --init --depth 1 vendor/quickjs
git -C "$source_dir" apply "$assets/angelfish.patch"

# Each install starts signed out. The upstream checkout includes a sample session file.
unlink "$source_dir/switch_newpipe_session.json"
cp "$assets/icon.jpg" "$source_dir/resources/icon/icon.jpg"
install -Dm644 "$assets/google-roots.pem" "$source_dir/resources/certs/google-roots.pem"
