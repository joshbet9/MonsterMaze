#!/usr/bin/env bash
set -euo pipefail

PLATFORM="${1:-}"
case "$PLATFORM" in
  1.8)
    ROOT=/data/1.8
    TEMPLATE=/opt/mm18-template
    DEPLOYMENT_CONFIG=/opt/mm18-deployment-config.yml
    PORT=25565
    JAR=spigot-1.8.8.jar
    JAVA_BIN=/opt/java8/bin/java
    ;;
  1.21)
    ROOT=/data/1.21
    TEMPLATE=/opt/mm21-template
    DEPLOYMENT_CONFIG=/opt/mm21-deployment-config.yml
    PORT=25566
    JAR=paper-1.21.11.jar
    JAVA_BIN=/usr/bin/java
    ;;
  *)
    echo "Usage: $0 <1.8|1.21>" >&2
    exit 2
    ;;
esac

init_server() {
  if [ ! -f "$ROOT/.monstermaze-initialized" ]; then
    mkdir -p "$ROOT"
    cp -a "$TEMPLATE/." "$ROOT/"
    touch "$ROOT/.monstermaze-initialized"
  fi

  # The public Fly TCP service is responsible for the external port. Keep the
  # Minecraft server listening on all interfaces inside the Machine.
  sed -i "s/^server-port=.*/server-port=$PORT/" "$ROOT/server.properties"
  if grep -q '^server-ip=' "$ROOT/server.properties"; then
    sed -i 's/^server-ip=.*/server-ip=/' "$ROOT/server.properties"
  else
    printf '\nserver-ip=\n' >> "$ROOT/server.properties"
  fi
  mkdir -p "$ROOT/logs" "$ROOT/plugins/MonsterMazeStandalone/solo-runs"
}

apply_deployment_config() {
  local plugin_config="$ROOT/plugins/MonsterMazeStandalone/config.yml"
  local solo_mode

  if [ ! -f "$DEPLOYMENT_CONFIG" ]; then
    echo "Deployment configuration missing: $DEPLOYMENT_CONFIG" >&2
    exit 1
  fi

  solo_mode="$(awk -F': *' '$1 == "solo-mode" { print $2; exit }' "$DEPLOYMENT_CONFIG")"
  case "$solo_mode" in
    true|false) ;;
    *)
      echo "Invalid solo-mode in deployment configuration: '$solo_mode'" >&2
      exit 1
      ;;
  esac

  if grep -q '^solo-mode:' "$plugin_config"; then
    sed -i "s/^solo-mode:.*/solo-mode: $solo_mode/" "$plugin_config"
  else
    printf '\nsolo-mode: %s\n' "$solo_mode" >> "$plugin_config"
  fi

  echo "Deployment configuration: solo-mode=$solo_mode"
}

init_server
apply_deployment_config

# The 1.8 Solo template contains the Linux-safe Netty setting verified during
# production testing. Reassert it at startup in case an older template is used.
if [ "$PLATFORM" = "1.8" ]; then
  if grep -q '^use-native-transport=' "$ROOT/server.properties"; then
    sed -i 's/^use-native-transport=.*/use-native-transport=false/' "$ROOT/server.properties"
  else
    printf '\nuse-native-transport=false\n' >> "$ROOT/server.properties"
  fi
fi

# Rotate logs inside the Machine so a noisy server cannot fill its root filesystem.
cat >/etc/monstermaze-logrotate-run <<'EOF'
#!/bin/sh
/usr/sbin/logrotate /etc/logrotate.d/monstermaze >/dev/null 2>&1 || true
EOF
chmod +x /etc/monstermaze-logrotate-run
(
  while true; do
    sleep 300
    /etc/monstermaze-logrotate-run
  done
) &
ROTATE_PID=$!

cleanup() {
  kill "$ROTATE_PID" 2>/dev/null || true
}
trap cleanup TERM INT EXIT

cd "$ROOT"
exec "$JAVA_BIN" -Xms512M -Xmx1536M -jar "$JAR" nogui
