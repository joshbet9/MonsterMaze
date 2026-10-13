# JNP Mod Community Run Admin Tool

solo/bot/admin_mod_runs.py is an Oracle-host-side CLI for reviewing and correcting client-submitted **Community Mod runs**. It does not edit the authoritative server PB (runs) or server submission (submissions) tables, and it adds no public admin API endpoints.

## Where it reads data

The default database is /home/ubuntu/monstermaze/data/leaderboard.db, the same persistent SQLite database used by monster-bot.service. It refuses to create a missing database or proceed if the community_mod_runs schema is absent.

Run it on Oracle as the normal ubuntu service account. From the active release:

    cd /home/ubuntu/monstermaze/current/solo/bot

    # List newest community entries.
    python3 admin_mod_runs.py list --limit 50

    # Narrow by Minecraft name, context, mode, or player UUID.
    python3 admin_mod_runs.py list --name MazeRunner --context MPS
    python3 admin_mod_runs.py list --mode modern --limit 100 --offset 100

    # Inspect one entry before changing or deleting it.
    python3 admin_mod_runs.py show <submission-id>

    # Correct an entry. The tool displays a before/after preview and asks for YES.
    python3 admin_mod_runs.py edit <submission-id> --name CorrectName --stage 28 --kit Jumper

    # Remove an entry. The tool displays the selected record and asks for DELETE.
    python3 admin_mod_runs.py delete <submission-id>

Use the full submission_id value shown by list or show. Global options such as --db and --backup-dir must appear before the subcommand if you need to override the production defaults.

## Safeguards

- Every confirmed edit or deletion creates a timestamped SQLite backup in /home/ubuntu/monstermaze/backups/ (or the chosen --backup-dir) and validates it using PRAGMA quick_check before changing data.
- Every successful mutation records the action, operator, timestamp, and before/after JSON in community_mod_run_admin_audit.
- Interactive confirmation is on by default; --yes can skip it for deliberate, scripted operations.
- The submission_id and game fields are immutable through this tool. Player UUID/name, context, mode, pattern, kit, stage, and run time may be corrected with validation.
- Changes are limited to community_mod_runs. Community records remain unverified and never become official server PBs.
- A backup is kept for recovery; the tool does not automatically prune admin backups.

Before using it on production, run python3 -m unittest -v test_admin_mod_runs.py in the solo/bot directory. The focused tests use a temporary database and include a check that official server PB data is unchanged.
