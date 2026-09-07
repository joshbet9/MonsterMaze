#!/usr/bin/env bash
set -euo pipefail

TAG="${1:-}"
[[ "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+\.test$ ]] || {
  echo "Usage: $0 vX.Y.Z.test" >&2
  exit 2
}

REPO="joshbet9/MonsterMaze"
BASE_URL="https://github.com/${REPO}/releases/download/${TAG}"
ROOT="/home/monstermaze/servers/1.21"
TMP="$(mktemp -d /tmp/monstermaze-test-deploy.XXXXXX)"

log() { printf '[Hyper-V test deploy] %s\n' "$*"; }
cleanup() {
  local status=$?
  if [ "$status" -ne 0 ]; then
    log 'Deployment failed; attempting to restart the MM21 integration service.'
    sudo systemctl start monstermaze-21.service 2>/dev/null || true
  fi
  rm -rf "$TMP"
  exit "$status"
}
trap cleanup EXIT

for command in curl unzip rsync sha256sum systemctl; do
  command -v "$command" >/dev/null || { echo "Missing required command: $command" >&2; exit 1; }
done
sudo -n true 2>/dev/null || { echo 'Passwordless sudo is required.' >&2; exit 1; }

find_java21() {
  local candidate
  for candidate in \
    /usr/lib/jvm/java-21-openjdk-amd64/bin/java \
    /usr/lib/jvm/temurin-21-jdk-amd64/bin/java \
    /usr/lib/jvm/*21*/bin/java; do
    if [ -x "$candidate" ] && "$candidate" -version 2>&1 | grep -q 'version "21\.'; then
      printf '%s' "$candidate"
      return 0
    fi
  done
  return 1
}

JAVA21="$(find_java21)" || { echo 'Java 21 runtime not found on Hyper-V host.' >&2; exit 1; }

log "Downloading immutable test release $TAG"
curl -fsSL "${BASE_URL}/SHA256SUMS.txt" -o "$TMP/SHA256SUMS.txt"
curl -fsSL "${BASE_URL}/MonsterMaze-Test-Server-1.21.zip" -o "$TMP/MonsterMaze-Test-Server-1.21.zip"
cd "$TMP"
grep -E '  MonsterMaze-Test-Server-1\.21\.zip$' SHA256SUMS.txt | sha256sum -c -

mkdir -p "$TMP/extracted"
unzip -q "$TMP/MonsterMaze-Test-Server-1.21.zip" -d "$TMP/extracted"
test -f "$TMP/extracted/paper-1.21.11.jar"
config="$TMP/extracted/plugins/MonsterMazeStandalone/config.yml"
grep -Eq '^solo-mode:[[:space:]]*false[[:space:]]*$' "$config"

log 'Stopping MM21 integration service'
sudo systemctl stop monstermaze-21.service 2>/dev/null || true
sudo pkill -f 'paper-1\.21\.11\.jar' 2>/dev/null || true
sleep 2

sudo mkdir -p "$ROOT"
sudo rsync -a "$TMP/extracted/" "$ROOT/" \
  --exclude='world/' \
  --exclude='world_nether/' \
  --exclude='world_the_end/' \
  --exclude='logs/' \
  --exclude='plugins/MonsterMazeStandalone/solo-runs/' \
  --exclude='server.properties' \
  --exclude='plugins/MonsterMazeStandalone/config.yml'

config="$ROOT/plugins/MonsterMazeStandalone/config.yml"
if ! sudo test -f "$config"; then
  sudo install -D -m 0644 "$TMP/extracted/plugins/MonsterMazeStandalone/config.yml" "$config"
fi
sudo sed -i -E 's/^solo-mode:.*/solo-mode: false/' "$config"
sudo grep -Eq '^solo-mode:[[:space:]]*false[[:space:]]*$' "$config"
sudo chown -R monstermaze:monstermaze "$ROOT"

SERVICE="/etc/systemd/system/monstermaze-21.service"
cat > "$TMP/monstermaze-21.service" <<EOF
[Unit]
Description=Monster Maze 1.21 integration server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=monstermaze
WorkingDirectory=${ROOT}
ExecStart=${JAVA21} -Xms2G -Xmx4G -jar paper-1.21.11.jar nogui
Restart=on-failure
RestartSec=5
TimeoutStopSec=30

[Install]
WantedBy=multi-user.target
EOF
sudo install -m 0644 "$TMP/monstermaze-21.service" "$SERVICE"
sudo systemctl daemon-reload
sudo systemctl enable monstermaze-21.service
sudo systemctl start monstermaze-21.service
sleep 3
sudo systemctl is-active --quiet monstermaze-21.service

log "Hyper-V MM21 is now running test release $TAG with solo-mode:false."
