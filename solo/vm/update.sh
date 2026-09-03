#!/usr/bin/env bash
# Monster Maze Solo VM updater
# Updates the private Ubuntu solo VM from published GitHub Solo releases.
# Runtime worlds, run history, server.properties and webhook secrets are never replaced.
set -euo pipefail

REPO="joshbet9/MonsterMaze"
RAW="https://raw.githubusercontent.com/${REPO}/main/solo/vm"
HOME_DIR="/home/monstermaze"
SERVER_ROOT="${HOME_DIR}/servers"
TMP_ROOT="${HOME_DIR}/.mm-update"
VERSIONS="${SERVER_ROOT}/.versions"
SUBMITTER="${HOME_DIR}/submitter"

usage() { echo "Usage: $0 {1.8|1.21|all}"; exit 2; }
[[ $# -eq 1 ]] || usage
TARGET="$1"
case "$TARGET" in 1.8|1.21|all) ;; *) usage ;; esac

mkdir -p "$TMP_ROOT" "$VERSIONS" "$SUBMITTER" "$SUBMITTER/submitted/1.8" "$SUBMITTER/submitted/1.21"

service_name() {
  case "$1" in
    1.8) echo "monstermaze-18.service" ;;
    1.21) echo "monstermaze-21.service" ;;
    *) return 1 ;;
  esac
}

latest_release_json() {
  local platform="$1"
  python3 - "$platform" <<'PY'
import json, sys, urllib.request
platform = sys.argv[1]
req = urllib.request.Request(
    "https://api.github.com/repos/joshbet9/MonsterMaze/releases?per_page=100",
    headers={"Accept": "application/vnd.github+json", "User-Agent": "MonsterMaze-VM-Updater"},
)
with urllib.request.urlopen(req, timeout=30) as r:
    releases = json.load(r)
if platform == "1.8":
    prefix, asset_name = "v", "MonsterMaze-Solo.zip"
else:
    prefix, asset_name = "1.21-", "1.21-MonsterMaze-Solo.zip"
candidates = [
    r for r in releases
    if not r.get("draft") and not r.get("prerelease")
    and r.get("tag_name", "").startswith(prefix)
    and any(a.get("name") == asset_name for a in r.get("assets", []))
]
if not candidates:
    raise SystemExit(f"No published {platform} Solo release with {asset_name} found")
candidates.sort(key=lambda r: r.get("published_at") or r.get("created_at") or "", reverse=True)
rel = candidates[0]
asset = next(a for a in rel["assets"] if a.get("name") == asset_name)
print(json.dumps({"tag": rel["tag_name"], "url": asset["browser_download_url"], "digest": asset.get("digest", "")}))
PY
}

json_field() { python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }

stop_service() {
  local platform="$1"
  sudo systemctl stop "$(service_name "$platform")" 2>/dev/null || true
}

start_service() {
  local platform="$1"
  sudo systemctl start "$(service_name "$platform")" 2>/dev/null || true
}

backup_before_update() {
  local platform="$1"
  if [[ -x "$HOME_DIR/backup-servers.sh" ]]; then
    "$HOME_DIR/backup-servers.sh" "$platform"
  else
    echo "WARNING: $HOME_DIR/backup-servers.sh not found; continuing without automatic backup."
  fi
}

install_submitter() {
  local tmp="$TMP_ROOT/submit.py.tmp"
  echo "Updating Linux submitter..."
  curl -fsSL --retry 3 --connect-timeout 15 --max-time 120 "$RAW/submit.py" -o "$tmp"
  python3 -m py_compile "$tmp"
  install -m 0755 "$tmp" "$SUBMITTER/submit.py"
  rm -f "$tmp"
  if systemctl list-unit-files | grep -q '^monstermaze-submitter\.service'; then
    sudo systemctl daemon-reload
    sudo systemctl restart monstermaze-submitter.service
  fi
}

update_one() {
  local platform="$1"
  local live="$SERVER_ROOT/$platform"
  local meta release_tag asset_url asset_digest zip extract_root source expected actual map name

  echo
  echo "=== Monster Maze $platform VM update ==="
  meta="$(latest_release_json "$platform")"
  release_tag="$(printf '%s' "$meta" | json_field tag)"
  asset_url="$(printf '%s' "$meta" | json_field url)"
  asset_digest="$(printf '%s' "$meta" | json_field digest)"
  echo "Latest release: $release_tag"

  if [[ -f "$VERSIONS/$platform" ]] && [[ "$(cat "$VERSIONS/$platform")" == "$release_tag" ]]; then
    echo "Already on $release_tag."
    return 0
  fi

  zip="$TMP_ROOT/$platform-$release_tag.zip"
  extract_root="$TMP_ROOT/$platform-$release_tag"
  rm -rf "$extract_root" "$zip"

  echo "Downloading release asset..."
  curl -fL --retry 3 --connect-timeout 15 --max-time 600 "$asset_url" -o "$zip"
  if [[ -n "$asset_digest" && "$asset_digest" == sha256:* ]]; then
    expected="${asset_digest#sha256:}"
    actual="$(sha256sum "$zip" | awk '{print $1}')"
    [[ "$actual" == "$expected" ]] || { echo "Release ZIP hash mismatch"; exit 1; }
    echo "Release ZIP SHA-256 verified."
  fi

  mkdir -p "$extract_root"
  unzip -q "$zip" -d "$extract_root"
  source="$extract_root/server"
  [[ -d "$source" ]] || { echo "Release ZIP does not contain server/"; exit 1; }

  stop_service "$platform"
  backup_before_update "$platform"

  if [[ "$platform" == "1.8" ]]; then
    install -m 0644 "$source/spigot-1.8.8.jar" "$live/spigot-1.8.8.jar"
    install -m 0644 "$source/plugins/MonsterMazeStandalone.jar" "$live/plugins/MonsterMazeStandalone.jar"
    mkdir -p "$live/plugins/MonsterMazeStandalone"
    install -m 0644 "$source/plugins/MonsterMazeStandalone/config.yml" "$live/plugins/MonsterMazeStandalone/config.yml"
    [[ ! -f "$source/bukkit.yml" ]] || install -m 0644 "$source/bukkit.yml" "$live/bukkit.yml"
    [[ ! -f "$source/spigot.yml" ]] || install -m 0644 "$source/spigot.yml" "$live/spigot.yml"
  else
    install -m 0644 "$source/paper-1.21.11.jar" "$live/paper-1.21.11.jar"
    install -m 0644 "$source/plugins/ProtocolLib.jar" "$live/plugins/ProtocolLib.jar"
    install -m 0644 "$source/plugins/MonsterMazeStandalone.jar" "$live/plugins/MonsterMazeStandalone.jar"
    mkdir -p "$live/plugins/MonsterMazeStandalone"
    install -m 0644 "$source/plugins/MonsterMazeStandalone/config.yml" "$live/plugins/MonsterMazeStandalone/config.yml"
  fi

  # Canonical maps are release assets. Runtime world files remain untouched.
  shopt -s nullglob
  for map in "$source"/mm_*; do
    [[ -d "$map" ]] || continue
    name="$(basename "$map")"
    rm -rf "$live/$name.new"
    mkdir -p "$live/$name.new"
    rsync -a --delete "$map/" "$live/$name.new/"
    rm -rf "$live/$name.old"
    if [[ -d "$live/$name" ]]; then mv "$live/$name" "$live/$name.old"; fi
    mv "$live/$name.new" "$live/$name"
    rm -rf "$live/$name.old"
    echo "Updated map $name"
  done
  shopt -u nullglob

  printf '%s\n' "$release_tag" > "$VERSIONS/$platform"
  echo "Updated $platform to $release_tag."
}

cleanup() { rm -rf "$TMP_ROOT"; }
trap cleanup EXIT

install_submitter

case "$TARGET" in
  1.8) update_one 1.8; start_service 1.8 ;;
  1.21) update_one 1.21; start_service 1.21 ;;
  all) update_one 1.8; update_one 1.21; start_service 1.8; start_service 1.21 ;;
esac

echo
echo "VM update complete."
