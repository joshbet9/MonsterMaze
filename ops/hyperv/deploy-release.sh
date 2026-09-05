#!/usr/bin/env bash
set -euo pipefail

# Release promotion target for the local Hyper-V integration server.
# Runs on the Hyper-V Linux VM. It consumes the immutable GitHub Release
# server artifacts, preserves environment-owned state, and guarantees
# solo-mode:false for both hosted instances.

TAG="${1:-}"
[[ "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
  echo "Usage: $0 vX.Y.Z" >&2
  exit 2
}

REPO="joshbet9/MonsterMaze"
BASE_URL="https://github.com/${REPO}/releases/download/${TAG}"
ROOT="/home/monstermaze/servers"
TMP="$(mktemp -d /tmp/monstermaze-deploy.XXXXXX)"
trap 'rm -rf "$TMP"' EXIT

log() { printf '[Hyper-V deploy] %s\n' "$*"; }

for command in curl unzip rsync sha256sum systemctl; do
  command -v "$command" >/dev/null || { echo "Missing required command: $command" >&2; exit 1; }
done

log "Downloading immutable release checksums for $TAG"
curl -fsSL "${BASE_URL}/SHA256SUMS.txt" -o "$TMP/SHA256SUMS.txt"
for version in 1.8 1.21; do
  curl -fsSL "${BASE_URL}/MonsterMaze-Server-${version}.zip" -o "$TMP/MonsterMaze-Server-${version}.zip"
done

cd "$TMP"
grep -E "  MonsterMaze-Server-(1\.8|1\.21)\.zip$" SHA256SUMS.txt | sha256sum -c -

for version in 1.8 1.21; do
  mkdir -p "$TMP/extracted/$version"
  unzip -q "$TMP/MonsterMaze-Server-${version}.zip" -d "$TMP/extracted/$version"
  test -f "$TMP/extracted/$version/spigot-1.8.8.jar" -o -f "$TMP/extracted/$version/paper-1.21.11.jar"
  config="$TMP/extracted/$version/plugins/MonsterMazeStandalone/config.yml"
  grep -Eq '^solo-mode:[[:space:]]*false[[:space:]]*$' "$config"
done

# Ensure service ownership is explicit and repeatable. These units run the
# same server directories used by the existing Hyper-V installation.
install_service() {
  local version="$1" jar="$2" java="$3" port="$4"
  local service="/etc/systemd/system/monstermaze-${version}.service"
  cat > "$TMP/monstermaze-${version}.service" <<EOF
[Unit]
Description=Monster Maze ${version} integration server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=monstermaze
WorkingDirectory=${ROOT}/${version}
ExecStart=${java} -Xms2G -Xmx${version == "1.8" && "2G" || "4G"} -jar ${jar} nogui
Restart=on-failure
RestartSec=5
TimeoutStopSec=30

[Install]
WantedBy=multi-user.target
EOF
  install -m 0644 "$TMP/monstermaze-${version}.service" "$service"
}

# Stop the managed services first. Also terminate the legacy manually-started
# process if one is still present, so the new service cannot bind its port.
sudo systemctl stop monstermaze-1.8.service monstermaze-1.21.service 2>/dev/null || true
sudo pkill -f 'spigot-1\.8\.8\.jar' 2>/dev/null || true
sudo pkill -f 'paper-1\.21\.11\.jar' 2>/dev/null || true
sleep 2

# Preserve environment-owned state. In particular, do not replace worlds,
# logs, server.properties, or the environment's plugin config. If a plugin
# config is absent, the canonical release config is installed and must be false.
for version in 1.8 1.21; do
  target="$ROOT/$version"
  stage="$TMP/extracted/$version"
  sudo mkdir -p "$target"

  sudo rsync -a "$stage/" "$target/" \
    --exclude='world/' \
    --exclude='world_nether/' \
    --exclude='world_the_end/' \
    --exclude='logs/' \
    --exclude='plugins/MonsterMazeStandalone/solo-runs/' \
    --exclude='server.properties' \
    --exclude='plugins/MonsterMazeStandalone/config.yml'

  config="$target/plugins/MonsterMazeStandalone/config.yml"
  if ! sudo test -f "$config"; then
    sudo install -D -m 0644 "$stage/plugins/MonsterMazeStandalone/config.yml" "$config"
  fi

  # Hosted/integration instances are never Solo PB environments.
  sudo sed -i -E 's/^solo-mode:.*/solo-mode: false/' "$config"
  sudo grep -Eq '^solo-mode:[[:space:]]*false[[:space:]]*$' "$config"
done

install_service 1.8 spigot-1.8.8.jar /usr/bin/java 25565
install_service 1.21 paper-1.21.11.jar /usr/lib/jvm/java-21-openjdk-amd64/bin/java 25566
sudo systemctl daemon-reload
sudo systemctl enable monstermaze-1.8.service monstermaze-1.21.service
sudo systemctl start monstermaze-1.8.service
sudo systemctl start monstermaze-1.21.service

sleep 3
sudo systemctl is-active --quiet monstermaze-1.8.service
sudo systemctl is-active --quiet monstermaze-1.21.service

log "Hyper-V is now running release $TAG with solo-mode:false on MM18/MM21."
