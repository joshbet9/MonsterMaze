#!/usr/bin/env bash
# Configure optional Discord submission webhooks on a Monster Maze Solo VM.
set -euo pipefail

HOME_DIR="/home/monstermaze"
validate_url() {
  [[ "$1" =~ ^https://discord\.com/api/webhooks/ ]] || {
    echo "Invalid Discord webhook URL. Expected https://discord.com/api/webhooks/..." >&2
    exit 1
  }
}

write_config() {
  local platform="$1"
  local path="$2"
  local url="$3"
  cat > "$path" <<EOF
# Monster Maze Solo submission webhook for platform $platform
# Keep this file private; it is intentionally excluded from Git and VM releases.
\$DEFAULT_WEBHOOK = '$url'
EOF
  chmod 0600 "$path"
  chown monstermaze:monstermaze "$path"
}

echo "Monster Maze Solo Discord submitter setup"
echo "Leave a platform blank to disable submissions for that platform."
echo

read -r -p "1.8 Discord webhook (optional): " webhook18
if [[ -n "$webhook18" ]]; then
  validate_url "$webhook18"
  write_config "1.8" "$HOME_DIR/submit-config-18.ps1" "$webhook18"
  echo "1.8 webhook configured."
else
  rm -f "$HOME_DIR/submit-config-18.ps1"
  echo "1.8 submissions disabled."
fi

read -r -p "1.21 Discord webhook (optional): " webhook21
if [[ -n "$webhook21" ]]; then
  validate_url "$webhook21"
  write_config "1.21" "$HOME_DIR/submit-config-21.ps1" "$webhook21"
  echo "1.21 webhook configured."
else
  rm -f "$HOME_DIR/submit-config-21.ps1"
  echo "1.21 submissions disabled."
fi

if systemctl list-unit-files | grep -q '^monstermaze-submitter\.service'; then
  sudo systemctl restart monstermaze-submitter.service
fi

echo
echo "Submitter configuration updated."
