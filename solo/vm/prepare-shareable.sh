#!/usr/bin/env bash
# Prepare the current Monster Maze Solo Ubuntu VM for Hyper-V export.
#
# This is intentionally destructive: run it only on a VM snapshot/copy that is
# no longer needed as a personal development environment.
set -euo pipefail

HOME_DIR="/home/monstermaze"
SERVER_ROOT="$HOME_DIR/servers"
SERVICE_DIR="/etc/systemd/system"

if [[ "${1:-}" != "--confirm" ]]; then
  echo "This will sanitise the current VM for distribution."
  echo "It removes personal Solo run history, submission archives, webhook config,"
  echo "logs, shell history, temporary updater state, machine identity and SSH host keys."
  echo
  echo "It does NOT delete server worlds or release-installed server files."
  echo
  echo "To continue, run:"
  echo "  sudo $0 --confirm"
  exit 2
fi

[[ $EUID -eq 0 ]] || { echo "Run as root (use sudo)."; exit 1; }

backup_root="$HOME_DIR/backups"
mkdir -p "$backup_root/shareable-prep"
STAMP="$(date +%Y%m%d-%H%M%S)"

# Stop everything that could write new runtime data while sanitising.
sudo systemctl stop monstermaze-18.service 2>/dev/null || true
sudo systemctl stop monstermaze-21.service 2>/dev/null || true
sudo systemctl stop monstermaze-submitter.service 2>/dev/null || true

# Keep the installed release/version state but remove user-specific run data.
rm -rf "$SERVER_ROOT/1.8/plugins/MonsterMazeStandalone/solo-runs"
rm -rf "$SERVER_ROOT/1.21/plugins/MonsterMazeStandalone/solo-runs"
rm -rf "$HOME_DIR/submitter/submitted"
mkdir -p "$HOME_DIR/submitter/submitted/1.8" "$HOME_DIR/submitter/submitted/1.21"

# Remove private webhook configuration files. The submitter remains installed and
# will simply wait for configuration to be added on the destination VM.
rm -f "$HOME_DIR/submit-config-18.ps1" "$HOME_DIR/submit-config-21.ps1"

# Remove personal shell/session history and temporary updater state.
rm -f "$HOME_DIR/.bash_history" "$HOME_DIR/.zsh_history" "$HOME_DIR/.lesshst"
rm -rf "$HOME_DIR/.mm-update"

# Remove generated logs/caches from the VM itself. Do not touch server worlds.
find "$SERVER_ROOT" -type f \( -name '*.log' -o -name '*.log.gz' \) -delete 2>/dev/null || true
rm -rf /var/log/journal/*
rm -rf "$HOME_DIR/.cache" 2>/dev/null || true

# Remove machine-specific identity so a cloned VM does not share a machine-id.
truncate -s 0 /etc/machine-id
rm -f /var/lib/dbus/machine-id

# Remove SSH host keys so the destination VM can generate unique keys on first boot.
rm -f /etc/ssh/ssh_host_*_key /etc/ssh/ssh_host_*_key.pub

# Clear common temporary state and package caches to reduce the exported image size.
rm -rf /tmp/* /var/tmp/*
apt-get clean

# Make sure the submitter is enabled for boot but cannot accidentally submit anything
# until the recipient deliberately installs webhook configuration.
systemctl daemon-reload
systemctl enable monstermaze-submitter.service >/dev/null 2>&1 || true

# Create a marker that lets the first-boot helper show that this image was sanitised.
printf '%s\n' "$STAMP" > "$HOME_DIR/.monstermaze-shareable-image"
chmod 0644 "$HOME_DIR/.monstermaze-shareable-image"

# Recreate empty runtime directories expected by the submitter and plugins.
mkdir -p \
  "$SERVER_ROOT/1.8/plugins/MonsterMazeStandalone/solo-runs" \
  "$SERVER_ROOT/1.21/plugins/MonsterMazeStandalone/solo-runs"
chown -R monstermaze:monstermaze "$HOME_DIR/submitter" "$HOME_DIR/.monstermaze-shareable-image" \
  "$SERVER_ROOT/1.8/plugins/MonsterMazeStandalone/solo-runs" \
  "$SERVER_ROOT/1.21/plugins/MonsterMazeStandalone/solo-runs"

# Reset the shell history once more after the commands above.
history -c 2>/dev/null || true

cat <<EOF

Monster Maze Solo VM sanitised: $STAMP

Safe to shut down and export from Hyper-V.

Destination VM notes:
  - Add your own submit-config-18.ps1 / submit-config-21.ps1 if Discord submission is desired.
  - The updater remains at $HOME_DIR/monstermaze-update.sh.
  - Server worlds and installed release files were retained.
  - Machine-id and SSH host keys were removed so the clone can get unique identities.

Before distribution, boot-test the exported VM once and verify both servers start.
EOF
