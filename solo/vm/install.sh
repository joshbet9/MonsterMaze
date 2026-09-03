#!/usr/bin/env bash
# One-time bootstrap for the Monster Maze Solo Ubuntu VM.
set -euo pipefail

HOME_DIR="/home/monstermaze"
RAW="https://raw.githubusercontent.com/joshbet9/MonsterMaze/main/solo/vm"

mkdir -p "$HOME_DIR/submitter" "$HOME_DIR/submitter/submitted/1.8" "$HOME_DIR/submitter/submitted/1.21"

curl -fsSL --retry 3 --connect-timeout 15 --max-time 120 "$RAW/update.sh" -o "$HOME_DIR/monstermaze-update.sh"
chmod 0755 "$HOME_DIR/monstermaze-update.sh"

curl -fsSL --retry 3 --connect-timeout 15 --max-time 120 "$RAW/submit.py" -o "$HOME_DIR/submitter/submit.py.new"
python3 -m py_compile "$HOME_DIR/submitter/submit.py.new"
if [[ -f "$HOME_DIR/submitter/submit.py" ]]; then
  cp "$HOME_DIR/submitter/submit.py" "$HOME_DIR/submitter/submit.py.before-repo-bootstrap"
fi
install -m 0755 "$HOME_DIR/submitter/submit.py.new" "$HOME_DIR/submitter/submit.py"
rm -f "$HOME_DIR/submitter/submit.py.new"

curl -fsSL --retry 3 --connect-timeout 15 --max-time 120 "$RAW/monstermaze-submitter.service" -o "$HOME_DIR/monstermaze-submitter.service"
sudo install -m 0644 "$HOME_DIR/monstermaze-submitter.service" /etc/systemd/system/monstermaze-submitter.service
sudo systemctl daemon-reload
sudo systemctl enable monstermaze-submitter.service
sudo systemctl restart monstermaze-submitter.service

# The updater will create platform release markers after its first successful run.
mkdir -p "$HOME_DIR/servers/.versions"

echo
echo "Bootstrap complete."
echo "Updater:  $HOME_DIR/monstermaze-update.sh"
echo "Submitter: $HOME_DIR/submitter/submit.py"
echo
echo "Next: run '$HOME_DIR/monstermaze-update.sh all'"
