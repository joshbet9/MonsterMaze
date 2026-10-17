#!/usr/bin/env bash
set -euo pipefail

RELEASE_TAG="${1:-}"
EXPECTED_COMMIT="${2:-}"
if [[ ! "$RELEASE_TAG" =~ ^(v|oracle-v)[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Usage: $0 vX.Y.Z or oracle-vX.Y.Z [expected-commit-sha]" >&2
  exit 2
fi
if [[ -n "$EXPECTED_COMMIT" && ! "$EXPECTED_COMMIT" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Expected commit must be a full 40-character lowercase Git SHA." >&2
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

cleanup() { rm -rf "$TMP"; if [[ -n "${BACKUP_TMP:-}" ]]; then rm -f -- "$BACKUP_TMP"; fi; }
trap cleanup EXIT

mkdir -p "$RELEASES" "$DATA"
test -f "$DATA/config.json" || { echo "Missing $DATA/config.json" >&2; exit 1; }
test -f "$DATA/leaderboard.db" || { echo "Missing $DATA/leaderboard.db" >&2; exit 1; }
test -x "$VENV/bin/python" || { echo "Missing bot virtualenv at $VENV" >&2; exit 1; }

if [[ -e "$TARGET" ]]; then
  echo "Release $RELEASE_TAG already exists; verifying immutable checkout."
  test -d "$TARGET/.git" || { echo "Existing release path is not a Git checkout." >&2; exit 1; }
  ACTUAL_COMMIT="$(git -C "$TARGET" rev-parse HEAD)"
  test "$(git -C "$TARGET" describe --exact-match --tags HEAD 2>/dev/null)" = "$RELEASE_TAG" \
    || { echo "Existing release tag verification failed." >&2; exit 1; }
  if [[ -n "$EXPECTED_COMMIT" && "$ACTUAL_COMMIT" != "$EXPECTED_COMMIT" ]]; then
    echo "Existing release commit $ACTUAL_COMMIT does not match expected $EXPECTED_COMMIT." >&2
    exit 1
  fi
else
  git clone --quiet --branch "$RELEASE_TAG" --single-branch "$REPO" "$TMP"
  ACTUAL_COMMIT="$(git -C "$TMP" rev-parse HEAD)"
  test "$(git -C "$TMP" describe --exact-match --tags HEAD 2>/dev/null)" = "$RELEASE_TAG" \
    || { echo "Release tag verification failed." >&2; exit 1; }
  if [[ -n "$EXPECTED_COMMIT" && "$ACTUAL_COMMIT" != "$EXPECTED_COMMIT" ]]; then
    echo "Release tag points to $ACTUAL_COMMIT, expected $EXPECTED_COMMIT." >&2
    exit 1
  fi
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

# Take a consistent SQLite backup before switching/restarting application code.
# The API change is additive, but a recoverable data snapshot is still required.
BACKUP_DIR="$BASE/backups"
mkdir -p "$BACKUP_DIR"
BACKUP_FILE="$BACKUP_DIR/leaderboard-before-${RELEASE_TAG}-$(date -u +%Y%m%dT%H%M%SZ).db"
BACKUP_TMP="$BACKUP_FILE.tmp"
python3 - "$DATA/leaderboard.db" "$BACKUP_TMP" <<'PY'
import os, sqlite3, sys
source_path, backup_path = map(os.path.abspath, sys.argv[1:3])
source = sqlite3.connect(source_path, timeout=30)
backup = sqlite3.connect(backup_path, timeout=30)
try:
    source.backup(backup)
    result = backup.execute("PRAGMA quick_check").fetchone()
    if not result or result[0] != "ok":
        raise SystemExit("SQLite backup failed integrity check: %r" % (result,))
finally:
    backup.close()
    source.close()
PY
test -s "$BACKUP_TMP" || { echo "Database backup is empty." >&2; exit 1; }
chmod 0640 "$BACKUP_TMP"
mv "$BACKUP_TMP" "$BACKUP_FILE"
echo "Created verified database backup: $BACKUP_FILE"
# Keep the ten newest release backups to avoid unbounded disk growth.
mapfile -t OLD_BACKUPS < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'leaderboard-before-*.db' -printf '%T@ %p\n' | sort -nr | tail -n +11 | cut -d' ' -f2-)
for old_backup in "${OLD_BACKUPS[@]}"; do rm -f -- "$old_backup"; done

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

# The public JNP Mod API must be present after a release. Community submissions
# stay in a separate unverified table and never enter official PB/MMR records.
if ! curl -fsS --max-time 10 'http://127.0.0.1:8090/api/v1/mod/leaderboard?source=community&context=ALL&mode=all&kind=overall&filter=all' \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("ok") is True and d.get("source") == "community" and d.get("verified") is False and isinstance(d.get("rows"), list)'; then
  echo "Public JNP Mod community leaderboard health check failed." >&2
  rollback
  exit 1
fi
if ! curl -fsS --max-time 10 'http://127.0.0.1:8090/api/v1/mod/leaderboard?source=server&platform=1.8&mode=original&kind=overall&filter=all' \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("ok") is True and d.get("source") == "server" and d.get("verified") is True and isinstance(d.get("rows"), list)'; then
  echo "Public JNP Mod server-PB leaderboard health check failed." >&2
  rollback
  exit 1
fi
# Halloween Havoc has its own unverified dataset; check both independent boards
# after restart so a release cannot appear healthy when the new routes are absent.
if ! curl -fsS --max-time 10 'http://127.0.0.1:8090/api/v1/mod/halloween-havoc/leaderboard?context=HH&kind=runs' \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("ok") is True and d.get("source") == "community" and d.get("verified") is False and d.get("kind") == "runs" and isinstance(d.get("rows"), list)'; then
  echo "Public Halloween Havoc full-run health check failed." >&2
  rollback
  exit 1
fi
if ! curl -fsS --max-time 10 'http://127.0.0.1:8090/api/v1/mod/halloween-havoc/leaderboard?context=HH&kind=splits' \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("ok") is True and d.get("source") == "community" and d.get("verified") is False and d.get("kind") == "splits" and isinstance(d.get("rows"), list)'; then
  echo "Public Halloween Havoc objective-split health check failed." >&2
  rollback
  exit 1
fi

# HHI is an independent public community context. Confirm its full runs, splits,
# and the shared canonical-checkpoint BKS route are available and scoped only to HHI.
if ! curl -fsS --max-time 10 'http://127.0.0.1:8090/api/v1/mod/halloween-havoc/leaderboard?context=HHI&kind=runs' \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("ok") is True and d.get("source") == "community" and d.get("verified") is False and d.get("context") == "HHI" and d.get("kind") == "runs" and isinstance(d.get("rows"), list)'; then
  echo "Public Halloween Havoc Insanity full-run health check failed." >&2
  rollback
  exit 1
fi
if ! curl -fsS --max-time 10 'http://127.0.0.1:8090/api/v1/mod/halloween-havoc/leaderboard?context=HHI&kind=splits' \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("ok") is True and d.get("source") == "community" and d.get("verified") is False and d.get("context") == "HHI" and d.get("kind") == "splits" and isinstance(d.get("rows"), list)'; then
  echo "Public Halloween Havoc Insanity objective-split health check failed." >&2
  rollback
  exit 1
fi
if ! curl -fsS --max-time 10 'http://127.0.0.1:8090/api/v1/mod/halloween-havoc/leaderboard?context=HHI&serverType=ALL&kind=bks' \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("ok") is True and d.get("source") == "community" and d.get("verified") is False and d.get("context") == "HHI" and d.get("kind") == "bks" and isinstance(d.get("benchmarks"), list) and all(b.get("context") == "HHI" for b in d["benchmarks"])'; then
  echo "Public Halloween Havoc Insanity BKS health check failed." >&2
  rollback
  exit 1
fi

# Validate the chronological games-history route introduced for JNP Mod.
if ! curl -fsS --max-time 10 'http://127.0.0.1:8090/api/v1/mod/history?context=ALL&limit=1&offset=0' \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("ok") is True and d.get("source") == "community" and d.get("verified") is False and d.get("context") == "ALL" and d.get("chronological") == "oldest-first" and isinstance(d.get("rows"), list) and isinstance(d.get("total"), int) and isinstance(d.get("hasMore"), bool)'; then
  echo "Public JNP Mod game-history health check failed." >&2
  rollback
  exit 1
fi
# Validate that any available completed WR has schema-v3 canonical checkpoint rows.
# Partial runs may omit boundaries, so gaps are allowed and are surfaced by the API.
if ! curl -fsS --max-time 10 'http://127.0.0.1:8090/api/v1/mod/halloween-havoc/leaderboard?context=ALL&serverType=ALL&kind=bks' \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); ids=("GIANT","GENDO","CHALLENGES_COMPLETE","MINECART_LAVA","MAZE_EXIT","BOTTOM_DECK_PORTAL","PUMPKIN_KING_MINIONS","PUMPKIN_KING"); assert d.get("ok") is True and d.get("source") == "community" and d.get("verified") is False and d.get("kind") == "bks" and isinstance(d.get("benchmarks"), list); assert all(b.get("worldRecordTimeMs") is None or (isinstance(b.get("worldRecordSplits"), list) and len(b["worldRecordSplits"]) > 0 and all(s.get("checkpointId") in ids and int(s.get("schemaVersion", 0)) >= 3 and isinstance(s.get("missingCheckpointIds"), list) for s in b["worldRecordSplits"])) for b in d["benchmarks"])'; then
  echo "Public Halloween Havoc canonical-checkpoint health check failed." >&2
  rollback
  exit 1
fi

echo "Oracle bot deployment successful: $RELEASE_TAG"
echo "Current release: $(readlink -f "$CURRENT")"
