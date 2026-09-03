#!/usr/bin/env bash
set -euo pipefail

DATA_ROOT=/data
MM18="$DATA_ROOT/1.8"
MM21="$DATA_ROOT/1.21"
T18=/opt/mm18-template
T21=/opt/mm21-template

init_server() {
  local template="$1"
  local target="$2"
  local port="$3"

  if [ ! -f "$target/.monstermaze-initialized" ]; then
    mkdir -p "$target"
    cp -a "$template/." "$target/"

    # The two external Fly TCP services are 25565 (1.8) and 25566 (1.21).
    # Keep the server bound to all interfaces; Fly handles the public endpoint.
    sed -i "s/^server-port=.*/server-port=$port/" "$target/server.properties"
    sed -i "s/^server-ip=.*/server-ip=/" "$target/server.properties"
    touch "$target/.monstermaze-initialized"
  fi

  mkdir -p "$target/logs" "$target/plugins/MonsterMazeStandalone/solo-runs"
}

init_server "$T18" "$MM18" 25565
init_server "$T21" "$MM21" 25566

# The 1.8 Solo template already carries the Netty workaround that was verified on
# the Linux VM.  Re-assert it here in case an older template is ever deployed.
if grep -q '^use-native-transport=' "$MM18/server.properties"; then
  sed -i 's/^use-native-transport=.*/use-native-transport=false/' "$MM18/server.properties"
else
  printf '\nuse-native-transport=false\n' >> "$MM18/server.properties"
fi

# logrotate runs from the host on the VM; inside Fly we run it ourselves so a log
# storm cannot consume the persistent volume.
cat >/etc/monstermaze-logrotate-cron <<'EOF'
#!/bin/sh
/usr/sbin/logrotate /etc/logrotate.d/monstermaze >/dev/null 2>&1 || true
EOF
chmod +x /etc/monstermaze-logrotate-cron
(
  while true; do
    sleep 300
    /etc/monstermaze-logrotate-cron
  done
) &
ROTATE_PID=$!

cleanup() {
  trap - TERM INT EXIT
  kill "$ROTATE_PID" "$SUBMITTER_PID" "$PID18" "$PID21" 2>/dev/null || true
  wait || true
}
trap cleanup TERM INT EXIT

python3 /opt/monstermaze-submitter.py \
  --root18 "$MM18" \
  --root21 "$MM21" \
  --interval "${MM_SUBMIT_INTERVAL:-10}" &
SUBMITTER_PID=$!

cd "$MM18"
java -Xms512M -Xmx1536M -jar spigot-1.8.8.jar nogui &
PID18=$!

cd "$MM21"
java -Xms512M -Xmx1536M -jar paper-1.21.11.jar nogui &
PID21=$!

# Keep the Fly Machine alive while both game servers are alive. If either exits,
# terminate the other process so the machine is restarted cleanly by Fly.
while kill -0 "$PID18" 2>/dev/null && kill -0 "$PID21" 2>/dev/null; do
  sleep 5
done

status=0
if ! kill -0 "$PID18" 2>/dev/null; then
  wait "$PID18" || status=$?
fi
if ! kill -0 "$PID21" 2>/dev/null; then
  local_status=0
  wait "$PID21" || local_status=$?
  if [ "$status" -eq 0 ]; then status=$local_status; fi
fi
exit "$status"
