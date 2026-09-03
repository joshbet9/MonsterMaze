#!/usr/bin/env bash
set -euo pipefail

PLATFORM="${1:-}"
case "$PLATFORM" in
  1.8)
    ROOT=/data/1.8
    TEMPLATE=/opt/mm18-template
    PORT=25565
    JAR=spigot-1.8.8.jar
    ;;
  1.21)
    ROOT=/data/1.21
    TEMPLATE=/opt/mm21-template
    PORT=25566
    JAR=paper-1.21.11.jar
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

init_server

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
exec java -Xms512M -Xmx1536M -jar "$JAR" nogui
