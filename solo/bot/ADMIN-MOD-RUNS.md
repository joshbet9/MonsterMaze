# JNP Mod Community Run Admin Tool

`solo/bot/admin_mod_runs.py` is an Oracle-host-side CLI for reviewing, correcting, or deleting client-reported **Monster Maze Community** and **Halloween Havoc/HHC Community** records. It is not a public HTTP admin endpoint. It never modifies the authoritative Monster Maze server PB (`runs`) or server submissions tables.

## Where it reads data

The default database is `/home/ubuntu/monstermaze/data/leaderboard.db`, shared with `monster-bot.service`. The tool refuses to create a missing database and validates that the selected dataset's expected tables/columns exist before modifying anything.

Run it on Oracle as the normal `ubuntu` service account:

```bash
cd /home/ubuntu/monstermaze/current/solo/bot

# Monster Maze Community records (default dataset).
python3 admin_mod_runs.py list --limit 50
python3 admin_mod_runs.py list --name MazeRunner --context MPS
python3 admin_mod_runs.py show <submission-id>
python3 admin_mod_runs.py edit <submission-id> --name CorrectName --stage 28 --kit Jumper
python3 admin_mod_runs.py delete <submission-id>

# Halloween Havoc / Halloween Havoc Challenger uploads.
python3 admin_mod_runs.py list --dataset hh --context HH --limit 50
python3 admin_mod_runs.py list --dataset hh --context HHC --server-type MPS
python3 admin_mod_runs.py show <submission-id> --dataset hh
python3 admin_mod_runs.py edit <submission-id> --dataset hh --name CorrectName --time-ms 28500
python3 admin_mod_runs.py edit <submission-id> --dataset hh --successful false
python3 admin_mod_runs.py delete <submission-id> --dataset hh
```

Use the complete `submission_id` returned by `list` or `show`. Options such as `--db` and `--backup-dir` are global arguments and must be placed before the command if overriding the defaults.

For MM entries, editable fields include player identity/name, context, mode, pattern, kit, stage, and duration. For HH/HHC, editable fields include name, context, server type/node, duration/reported duration, success/status, and finish reason. HH/HHC edits that affect shared run metadata (name, context, server type, status) propagate to the associated objective-split rows so the full-run and split leaderboards remain consistent. Deleting an HH/HHC run also deletes its linked split rows.

## Safeguards

- Every confirmed edit or deletion creates a timestamped SQLite snapshot in `/home/ubuntu/monstermaze/backups/` (or the chosen `--backup-dir`) and validates it with `PRAGMA quick_check` before changing data.
- Every successful mutation records its action, operator, timestamp, and before/after JSON. MM audit entries use `community_mod_run_admin_audit`; HH/HHC entries use `community_hh_run_admin_audit`.
- Interactive confirmation is enabled by default. `--yes` can skip it only for deliberate scripted use.
- Submission IDs and immutable run identities are not editable. Validation prevents impossible HH/HHC status combinations; a run is successful exactly when its status is `COMPLETED`.
- All Community records remain unverified. The tool never promotes them into official Monster Maze PBs/MMR.
- Backups are retained for recovery; admin-created backups are not automatically pruned.

Run the focused tests before using it on production:

```bash
python3 -m unittest -v test_admin_mod_runs.py
```

The tests use a disposable SQLite database, validate backup/audit behavior, ensure HH splits follow edits/deletes, and verify official server PB rows remain unchanged.
