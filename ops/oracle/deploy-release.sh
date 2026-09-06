#!/usr/bin/env bash
set -euo pipefail

RELEASE_TAG="${1:-}"
if [[ ! "$RELEASE_TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Usage: $0 vX.Y.Z" >&2
  exit 2
fi

BASE=/home/ubuntu/monstermaze
RELEASES="$BASE/releases"
DATA="$BASE/data"
TARGET="$RELEASES/$RELEASE_TAG"
CURRENT="$BASE/current"
TMP="$RELEASES/.${RELEASE_TAG}.tmp.$$"
SERVICE=monster-bot.service
REPO=https://github.com/joshbet9/MonsterMaze.git
VENV=/home/ubuntu/monster-bot/venv

cleanup() { rm -rf "$TMP"; }
trap cleanup EXIT

mkdir -p "$RELEASES" "$DATA"
test -f "$DATA/config.json" || { echo "Missing $DATA/config.json" >&2; exit 1; }
test -f "$DATA/leaderboard.db" || { echo "Missing $DATA/leaderboard.db" >&2; exit 1; }
test -x "$VENV/bin/python" || { echo "Missing bot virtualenv at $VENV" >&2; exit 1; }

if [[ -e "$TARGET" ]]; then
  echo "Release $RELEASE_TAG already exists; reusing immutable checkout."
else
  git clone --quiet --branch "$RELEASE_TAG" --single-branch "$REPO" "$TMP"
  test "$(git -C "$TMP" describe --exact-match --tags HEAD 2>/dev/null)" = "$RELEASE_TAG" \
    || { echo "Release tag verification failed." >&2; exit 1; }
  mv "$TMP" "$TARGET"
fi

BOT="$TARGET/solo/bot"
test -f "$BOT/monster_bot.py" || { echo "Release missing bot entrypoint." >&2; exit 1; }
test -f "$BOT/api_server.py" || { echo "Release missing API server." >&2; exit 1; }
grep -q 'get_mmr_target' "$BOT/competitive.py" || { echo "Release missing MMR implementation." >&2; exit 1; }
grep -q 'mmr.*next' "$BOT/api_server.py" || { echo "Release missing MMR API route." >&2; exit 1; }

ln -sfn "$DATA/config.json" "$BOT/config.json"
ln -sfn "$DATA/leaderboard.db" "$BOT/leaderboard.db"
ln -sfn "$VENV" "$BOT/venv"

PREVIOUS=""
if [[ -L "$CURRENT" ]]; then
  PREVIOUS="$(readlink -f "$CURRENT")"
elif [[ -e "$CURRENT" ]]; then
  echo "$CURRENT exists and is not a symlink; refusing deployment." >&2
  exit 1
fi

ln -sfn "$TARGET" "$CURRENT"

rollback() {
  if [[ -n "$PREVIOUS" ]]; then
    echo "Rolling back to $PREVIOUS" >&2
    ln -sfn "$PREVIOUS" "$CURRENT"
    sudo systemctl restart "$SERVICE" || true
  fi
}

if ! sudo systemctl restart "$SERVICE"; then
  echo "Service restart failed." >&2
  rollback
  exit 1
fi

sleep 2
if ! sudo systemctl is-active --quiet "$SERVICE"; then
  echo "Service is not active." >&2
  rollback
  exit 1
fi

if ! sudo bash -c 'source /etc/monstermaze-api.env && curl -fsS --max-time 10 -H "Authorization: Bearer $MM_API_TOKEN" http://127.0.0.1:8090/api/v1/mmr/player/00000000-0000-0000-0000-000000000000/next/1.8 >/dev/null'; then
  echo "Authenticated MMR API health check failed." >&2
  rollback
  exit 1
fi

echo "Oracle bot deployment successful: $RELEASE_TAG"
echo "Current release: $(readlink -f "$CURRENT")"
