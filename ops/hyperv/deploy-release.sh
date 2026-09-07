#!/usr/bin/env bash
set -euo pipefail

# Release promotion target for the local Hyper-V integration server.
# Hyper-V is the persistent local test environment, so it intentionally runs
# with debug commands enabled while starting in competitive mode. The hosted
# release artifacts are production-safe; this deployment overlays the
# Hyper-V-specific debug configuration before starting the local services.

TAG="${1:-}"
ASSET_DIR="${2:-}"
[[ "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
  echo "Usage: $0 vX.Y.Z [validated-asset-dir]" >&2
  exit 2
}

REPO="joshbet9/MonsterMaze"
BASE_URL="https://github.com/${REPO}/releases/download/${TAG}"
SOURCE_BASE_URL="https://raw.githubusercontent.com/${REPO}/refs/tags/${TAG}"
ROOT="/home/monstermaze/servers"
TMP="$(mktemp -d /tmp/monstermaze-deploy.XXXXXX)"

log() { printf '[Hyper-V deploy] %s\n' "$*"; }

cleanup() {
  local status=$?
  if [ "$status" -ne 0 ]; then
    log "Deployment failed; attempting to restart both Hyper-V services so the integration servers are not left offline."
    sudo systemctl start monstermaze-18.service 2>/dev/null || true
    sudo systemctl start monstermaze-21.service 2>/dev/null || true
  fi
  rm -rf "$TMP"
  if [ -n "$ASSET_DIR" ]; then
    rm -rf "$ASSET_DIR"
  fi
  exit "$status"
}
trap cleanup EXIT

for command in curl unzip rsync sha256sum systemctl; do
  command -v "$command" >/dev/null || { echo "Missing required command: $command" >&2; exit 1; }
done

sudo -n true 2>/dev/null || {
  echo 'Passwordless sudo is required for automated Hyper-V deployment.' >&2
  exit 1
}

find_java8() {
  local candidate
  for candidate in \
    /usr/lib/jvm/java-8-openjdk-amd64/bin/java \
    /usr/lib/jvm/temurin-8-jdk-amd64/bin/java \
    /usr/lib/jvm/*8*/bin/java; do
    if [ -x "$candidate" ] && "$candidate" -version 2>&1 | grep -q 'version "1\.8\.'; then
      printf '%s' "$candidate"
      return 0
    fi
  done
  if /usr/bin/java -version 2>&1 | grep -q 'version "1\.8\.'; then
    printf '%s' /usr/bin/java
    return 0
  fi
  return 1
}

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

JAVA8="$(find_java8)" || { echo 'Java 8 runtime not found on Hyper-V host.' >&2; exit 1; }
JAVA21="$(find_java21)" || { echo 'Java 21 runtime not found on Hyper-V host.' >&2; exit 1; }

if [ -n "$ASSET_DIR" ]; then
  log "Using validated build artifacts from $ASSET_DIR for $TAG"
  test -f "$ASSET_DIR/SHA256SUMS.txt"
  for version in 1.8 1.21; do
    test -f "$ASSET_DIR/MonsterMaze-Server-${version}.zip"
  done
  cp "$ASSET_DIR/SHA256SUMS.txt" "$TMP/SHA256SUMS.txt"
  for version in 1.8 1.21; do
    cp "$ASSET_DIR/MonsterMaze-Server-${version}.zip" "$TMP/MonsterMaze-Server-${version}.zip"
  done
else
  log "Downloading immutable release assets for $TAG"
  curl -fsSL "${BASE_URL}/SHA256SUMS.txt" -o "$TMP/SHA256SUMS.txt"
  for version in 1.8 1.21; do
    curl -fsSL "${BASE_URL}/MonsterMaze-Server-${version}.zip" -o "$TMP/MonsterMaze-Server-${version}.zip"
  done
fi

cd "$TMP"
grep -E "  MonsterMaze-Server-(1\.8|1\.21)\.zip$" SHA256SUMS.txt | sha256sum -c -

for version in 1.8 1.21; do
  mkdir -p "$TMP/extracted/$version"
  unzip -q "$TMP/MonsterMaze-Server-${version}.zip" -d "$TMP/extracted/$version"
  if [ "$version" = "1.8" ]; then
    test -f "$TMP/extracted/$version/spigot-1.8.8.jar"
  else
    test -f "$TMP/extracted/$version/paper-1.21.11.jar"
  fi
  config="$TMP/extracted/$version/plugins/MonsterMazeStandalone/config.yml"
  grep -Eq '^solo-mode:[[:space:]]*false[[:space:]]*$' "$config"
  grep -Eq '^debug:[[:space:]]*false[[:space:]]*$' "$config"

  # Hyper-V's debug configuration is source-controlled separately from the
  # production-safe hosted artifact. It is pinned to the same release tag.
  curl -fsSL "${SOURCE_BASE_URL}/docker/deployments/hyperv/${version}/config.yml" \
    -o "$TMP/hyperv-config-${version}.yml"
  grep -Eq '^solo-mode:[[:space:]]*false[[:space:]]*$' "$TMP/hyperv-config-${version}.yml"
  grep -Eq '^debug:[[:space:]]*true[[:space:]]*$' "$TMP/hyperv-config-${version}.yml"
done

install_service() {
  local version="$1" jar="$2" java="$3" heap="$4"
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
ExecStart=${java} -Xms2G -Xmx${heap} -jar ${jar} nogui
Restart=on-failure
RestartSec=5
TimeoutStopSec=30

[Install]
WantedBy=multi-user.target
EOF
  sudo install -m 0644 "$TMP/monstermaze-${version}.service" "$service"
}

# Stop the managed services first. Also terminate legacy manually-started
# processes if one is still present, so the new services can bind their ports.
sudo systemctl stop monstermaze-18.service monstermaze-21.service 2>/dev/null || true
sudo pkill -f 'spigot-1\.8\.8\.jar' 2>/dev/null || true
sudo pkill -f 'paper-1\.21\.11\.jar' 2>/dev/null || true
sleep 2

# Preserve environment-owned state. In particular, do not replace worlds,
# logs, server.properties, or the environment's plugin config. If a plugin
# config is absent, install the Hyper-V overlay from the exact release tag.
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
  sudo install -D -m 0644 "$TMP/hyperv-config-${version}.yml" "$config"

  sudo grep -Eq '^solo-mode:[[:space:]]*false[[:space:]]*$' "$config"
  sudo grep -Eq '^debug:[[:space:]]*true[[:space:]]*$' "$config"
  sudo chown -R monstermaze:monstermaze "$target"
done

install_service 1.8 spigot-1.8.8.jar "$JAVA8" 2G
install_service 1.21 paper-1.21.11.jar "$JAVA21" 4G
sudo systemctl daemon-reload
sudo systemctl enable monstermaze-18.service monstermaze-21.service
sudo systemctl start monstermaze-18.service
sudo systemctl start monstermaze-21.service

sleep 3
sudo systemctl is-active --quiet monstermaze-18.service
sudo systemctl is-active --quiet monstermaze-21.service

log "Hyper-V is now running release $TAG with solo-mode:false and debug:true on MM18/MM21."
