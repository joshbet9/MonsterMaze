#!/usr/bin/env python3
"""Safely inspect and administer client-submitted JNP Mod community runs.

This tool intentionally edits only community_mod_runs. It never changes the
server-authoritative runs/submissions tables and is intended to run on the
Oracle host, not as an unauthenticated HTTP endpoint.
"""
from __future__ import annotations

import argparse
import getpass
import json
import os
import re
import sqlite3
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

DEFAULT_DB = Path(os.environ.get("MM_MOD_ADMIN_DB", "/home/ubuntu/monstermaze/data/leaderboard.db"))
DEFAULT_BACKUP_DIR = Path(os.environ.get("MM_MOD_ADMIN_BACKUP_DIR", "/home/ubuntu/monstermaze/backups"))
NAME_RE = re.compile(r"^[A-Za-z0-9_]{1,16}$")
ALLOWED_CONTEXTS = {"MPS", "MIN"}
ALLOWED_MODES = {"unknown", "original", "modern", "speed", "classic"}
ALLOWED_KITS = {"Unknown", "Jumper", "Slowball", "Body Builder", "Repulsor", "Maverick"}
REQUIRED_COLUMNS = {
    "submission_id", "uuid", "name", "context", "game", "mode", "pattern",
    "kit", "stage", "time_ms", "submitted_at",
}
EDITABLE_FIELDS = {
    "uuid": "uuid", "name": "name", "context": "context", "mode": "mode",
    "pattern": "pattern", "kit": "kit", "stage": "stage", "time_ms": "time_ms",
}


class AdminError(Exception):
    """Expected validation or operational error shown to the CLI user."""


def canonical_uuid(raw: Any, field: str) -> str:
    try:
        return str(uuid.UUID(str(raw))).lower()
    except (ValueError, TypeError, AttributeError) as exc:
        raise AdminError(f"{field} must be a valid UUID") from exc


def canonical_submission_id(raw: Any) -> str:
    return canonical_uuid(raw, "submission ID")


def connect_db(db_path: str | Path) -> sqlite3.Connection:
    path = Path(db_path)
    if not path.is_file():
        raise AdminError(f"Database file does not exist: {path}; refusing to create a new database")
    conn = sqlite3.connect(str(path), timeout=30)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA busy_timeout=30000")
    verify_schema(conn)
    return conn


def verify_schema(conn: sqlite3.Connection) -> None:
    try:
        columns = {row["name"] for row in conn.execute("PRAGMA table_info(community_mod_runs)")}
    except sqlite3.Error as exc:
        raise AdminError(f"Could not inspect community leaderboard schema: {exc}") from exc
    missing = REQUIRED_COLUMNS - columns
    if missing:
        raise AdminError(
            "community_mod_runs table is missing expected columns: " + ", ".join(sorted(missing))
        )


def row_dict(row: sqlite3.Row | dict[str, Any] | None) -> dict[str, Any] | None:
    if row is None:
        return None
    return {key: row[key] for key in row.keys()}


def find_entry(db_path: str | Path, submission_id: str) -> dict[str, Any] | None:
    sid = canonical_submission_id(submission_id)
    conn = connect_db(db_path)
    try:
        return row_dict(conn.execute(
            "SELECT submission_id,uuid,name,context,game,mode,pattern,kit,stage,time_ms,submitted_at "
            "FROM community_mod_runs WHERE lower(submission_id)=?", (sid,)
        ).fetchone())
    finally:
        conn.close()


def list_entries(
    db_path: str | Path,
    filters: dict[str, Any] | None = None,
    limit: int = 50,
    offset: int = 0,
) -> list[dict[str, Any]]:
    if not 1 <= int(limit) <= 500:
        raise AdminError("limit must be between 1 and 500")
    if int(offset) < 0:
        raise AdminError("offset must be zero or greater")
    filters = filters or {}
    clauses: list[str] = []
    args: list[Any] = []

    if filters.get("submission_id"):
        clauses.append("lower(submission_id)=?")
        args.append(canonical_submission_id(filters["submission_id"]))
    if filters.get("uuid"):
        clauses.append("lower(uuid)=?")
        args.append(canonical_uuid(filters["uuid"], "player UUID"))
    if filters.get("context"):
        context = str(filters["context"]).strip().upper()
        if context not in ALLOWED_CONTEXTS:
            raise AdminError("context must be MPS or MIN")
        clauses.append("context=?")
        args.append(context)
    if filters.get("mode"):
        mode = str(filters["mode"]).strip().lower()
        if mode not in ALLOWED_MODES:
            raise AdminError("mode must be unknown, original, modern, speed, or classic")
        clauses.append("lower(mode)=?")
        args.append(mode)
    if filters.get("name"):
        name = str(filters["name"]).strip()
        if not name:
            raise AdminError("name filter cannot be blank")
        escaped = name.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        clauses.append("name LIKE ? ESCAPE '\\'")
        args.append("%" + escaped + "%")

    where = (" WHERE " + " AND ".join(clauses)) if clauses else ""
    query = (
        "SELECT submission_id,uuid,name,context,game,mode,pattern,kit,stage,time_ms,submitted_at "
        "FROM community_mod_runs" + where +
        " ORDER BY submitted_at DESC, name COLLATE NOCASE ASC, submission_id ASC LIMIT ? OFFSET ?"
    )
    args.extend((int(limit), int(offset)))
    conn = connect_db(db_path)
    try:
        return [row_dict(row) for row in conn.execute(query, args).fetchall()]
    finally:
        conn.close()


def normalize_changes(changes: dict[str, Any]) -> dict[str, Any]:
    if not changes:
        raise AdminError("Specify at least one field to change")
    unknown = set(changes) - set(EDITABLE_FIELDS)
    if unknown:
        raise AdminError("Fields cannot be changed by this tool: " + ", ".join(sorted(unknown)))
    normalized: dict[str, Any] = {}
    for field, raw in changes.items():
        if field == "uuid":
            normalized[field] = canonical_uuid(raw, "player UUID")
        elif field == "name":
            name = str(raw).strip()
            if not NAME_RE.fullmatch(name):
                raise AdminError("name must be a Minecraft username containing 1-16 letters, digits, or underscores")
            normalized[field] = name
        elif field == "context":
            context = str(raw).strip().upper()
            if context not in ALLOWED_CONTEXTS:
                raise AdminError("context must be MPS or MIN")
            normalized[field] = context
        elif field == "mode":
            mode = str(raw).strip().lower()
            if mode not in ALLOWED_MODES:
                raise AdminError("mode must be unknown, original, modern, speed, or classic")
            normalized[field] = mode
        elif field == "pattern":
            try:
                pattern = int(raw)
            except (TypeError, ValueError) as exc:
                raise AdminError("pattern must be -1, 0, 1, or 2") from exc
            if pattern not in (-1, 0, 1, 2):
                raise AdminError("pattern must be -1, 0, 1, or 2")
            normalized[field] = pattern
        elif field == "kit":
            kit = str(raw).strip()
            if kit.lower() == "slowballer":
                kit = "Slowball"
            if kit not in ALLOWED_KITS:
                raise AdminError("kit must be Unknown, Jumper, Slowball, Body Builder, Repulsor, or Maverick")
            normalized[field] = kit
        elif field == "stage":
            try:
                stage = int(raw)
            except (TypeError, ValueError) as exc:
                raise AdminError("stage must be an integer between 1 and 500") from exc
            if not 1 <= stage <= 500:
                raise AdminError("stage must be between 1 and 500")
            normalized[field] = stage
        elif field == "time_ms":
            try:
                time_ms = int(raw)
            except (TypeError, ValueError) as exc:
                raise AdminError("time_ms must be an integer between 0 and 86400000") from exc
            if not 0 <= time_ms <= 86_400_000:
                raise AdminError("time_ms must be between 0 and 86400000")
            normalized[field] = time_ms
    return normalized


def make_backup(db_path: str | Path, backup_dir: str | Path, action: str) -> Path:
    source_path = Path(db_path)
    destination_dir = Path(backup_dir)
    destination_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    destination = destination_dir / f"leaderboard-before-community-{action}-{stamp}.db"
    source = sqlite3.connect(str(source_path), timeout=30)
    backup = sqlite3.connect(str(destination), timeout=30)
    try:
        source.backup(backup)
        check = backup.execute("PRAGMA quick_check").fetchone()
        if not check or check[0] != "ok":
            raise AdminError(f"Database backup failed integrity check: {check[0] if check else 'no result'}")
    except Exception:
        backup.close()
        source.close()
        try:
            destination.unlink(missing_ok=True)
        except OSError:
            pass
        raise
    else:
        backup.close()
        source.close()
    try:
        destination.chmod(0o640)
    except OSError:
        pass
    return destination


def ensure_audit_schema(conn: sqlite3.Connection) -> None:
    conn.execute("""CREATE TABLE IF NOT EXISTS community_mod_run_admin_audit (
        audit_id INTEGER PRIMARY KEY AUTOINCREMENT,
        action TEXT NOT NULL,
        submission_id TEXT NOT NULL,
        actor TEXT NOT NULL,
        occurred_at INTEGER NOT NULL,
        before_json TEXT NOT NULL,
        after_json TEXT
    )""")


def audit_action(
    conn: sqlite3.Connection,
    action: str,
    submission_id: str,
    actor: str,
    before: dict[str, Any],
    after: dict[str, Any] | None,
) -> None:
    ensure_audit_schema(conn)
    conn.execute(
        "INSERT INTO community_mod_run_admin_audit "
        "(action,submission_id,actor,occurred_at,before_json,after_json) VALUES(?,?,?,?,?,?)",
        (action, submission_id, actor, int(datetime.now(timezone.utc).timestamp() * 1000),
         json.dumps(before, sort_keys=True), json.dumps(after, sort_keys=True) if after is not None else None),
    )


def edit_entry(
    db_path: str | Path,
    backup_dir: str | Path,
    submission_id: str,
    changes: dict[str, Any],
    *,
    confirmed: bool = False,
    actor: str | None = None,
) -> tuple[dict[str, Any], dict[str, Any], Path]:
    if not confirmed:
        raise AdminError("Edit cancelled: explicit confirmation is required")
    sid = canonical_submission_id(submission_id)
    normalized = normalize_changes(changes)
    conn = connect_db(db_path)
    try:
        conn.execute("BEGIN IMMEDIATE")
        before = row_dict(conn.execute(
            "SELECT submission_id,uuid,name,context,game,mode,pattern,kit,stage,time_ms,submitted_at "
            "FROM community_mod_runs WHERE lower(submission_id)=?", (sid,)
        ).fetchone())
        if before is None:
            raise AdminError(f"No community run found with submission ID {sid}")
        backup_path = make_backup(db_path, backup_dir, "edit")
        assignments = ", ".join(f"{field}=?" for field in normalized)
        values = list(normalized.values()) + [sid]
        conn.execute(f"UPDATE community_mod_runs SET {assignments} WHERE lower(submission_id)=?", values)
        after = row_dict(conn.execute(
            "SELECT submission_id,uuid,name,context,game,mode,pattern,kit,stage,time_ms,submitted_at "
            "FROM community_mod_runs WHERE lower(submission_id)=?", (sid,)
        ).fetchone())
        audit_action(conn, "edit", sid, actor or getpass.getuser(), before, after)
        conn.commit()
        return before, after, backup_path
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def delete_entry(
    db_path: str | Path,
    backup_dir: str | Path,
    submission_id: str,
    *,
    confirmed: bool = False,
    actor: str | None = None,
) -> tuple[dict[str, Any], Path]:
    if not confirmed:
        raise AdminError("Delete cancelled: explicit confirmation is required")
    sid = canonical_submission_id(submission_id)
    conn = connect_db(db_path)
    try:
        conn.execute("BEGIN IMMEDIATE")
        before = row_dict(conn.execute(
            "SELECT submission_id,uuid,name,context,game,mode,pattern,kit,stage,time_ms,submitted_at "
            "FROM community_mod_runs WHERE lower(submission_id)=?", (sid,)
        ).fetchone())
        if before is None:
            raise AdminError(f"No community run found with submission ID {sid}")
        backup_path = make_backup(db_path, backup_dir, "delete")
        conn.execute("DELETE FROM community_mod_runs WHERE lower(submission_id)=?", (sid,))
        audit_action(conn, "delete", sid, actor or getpass.getuser(), before, None)
        conn.commit()
        return before, backup_path
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def format_time_ms(value: int) -> str:
    minutes, rem = divmod(int(value), 60_000)
    seconds, millis = divmod(rem, 1000)
    return f"{minutes}:{seconds:02d}.{millis:03d}"


def format_submitted_at(value: int) -> str:
    return datetime.fromtimestamp(int(value) / 1000, tz=timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")


def print_entry(entry: dict[str, Any]) -> None:
    print(json.dumps(entry, indent=2, sort_keys=True))


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Administer client-submitted JNP Mod community runs only.")
    parser.add_argument("--db", type=Path, default=DEFAULT_DB, help=f"SQLite database (default: {DEFAULT_DB})")
    parser.add_argument("--backup-dir", type=Path, default=DEFAULT_BACKUP_DIR,
                        help=f"directory for pre-change database backups (default: {DEFAULT_BACKUP_DIR})")
    sub = parser.add_subparsers(dest="command", required=True)

    list_cmd = sub.add_parser("list", help="list community runs")
    list_cmd.add_argument("--name", help="case-insensitive name substring")
    list_cmd.add_argument("--uuid", help="player UUID")
    list_cmd.add_argument("--submission-id", help="exact submission ID")
    list_cmd.add_argument("--context", choices=["MPS", "MIN", "mps", "min"])
    list_cmd.add_argument("--mode", choices=sorted(ALLOWED_MODES))
    list_cmd.add_argument("--limit", type=int, default=50)
    list_cmd.add_argument("--offset", type=int, default=0)
    list_cmd.add_argument("--json", action="store_true", help="emit JSON")

    show_cmd = sub.add_parser("show", help="show one community run by submission ID")
    show_cmd.add_argument("submission_id")

    edit_cmd = sub.add_parser("edit", help="edit a community run; creates a verified backup and audit record")
    edit_cmd.add_argument("submission_id")
    edit_cmd.add_argument("--uuid")
    edit_cmd.add_argument("--name")
    edit_cmd.add_argument("--context", choices=["MPS", "MIN", "mps", "min"])
    edit_cmd.add_argument("--mode", choices=sorted(ALLOWED_MODES))
    edit_cmd.add_argument("--pattern", type=int, choices=[-1, 0, 1, 2])
    edit_cmd.add_argument("--kit", choices=sorted(ALLOWED_KITS))
    edit_cmd.add_argument("--stage", type=int)
    edit_cmd.add_argument("--time-ms", type=int, dest="time_ms")
    edit_cmd.add_argument("--yes", action="store_true", help="skip interactive confirmation")

    delete_cmd = sub.add_parser("delete", help="delete a community run; creates a verified backup and audit record")
    delete_cmd.add_argument("submission_id")
    delete_cmd.add_argument("--yes", action="store_true", help="skip interactive confirmation")
    return parser


def run_cli(args: argparse.Namespace) -> int:
    db_path = args.db
    if args.command == "list":
        filters = {key: getattr(args, key) for key in ("name", "uuid", "submission_id", "context", "mode")}
        entries = list_entries(db_path, filters, args.limit, args.offset)
        if args.json:
            print(json.dumps(entries, indent=2))
        elif not entries:
            print("No community runs matched.")
        else:
            print(f"{'SUBMISSION ID':36} {'NAME':16} {'CTX':3} {'MODE':8} {'PAT':3} {'KIT':14} {'STAGE':5} {'TIME':10} SUBMITTED (UTC)")
            for entry in entries:
                print(f"{entry['submission_id']:36} {entry['name'][:16]:16} {entry['context']:3} "
                      f"{entry['mode'][:8]:8} {entry['pattern']:3} {entry['kit'][:14]:14} "
                      f"{entry['stage']:5} {format_time_ms(entry['time_ms']):10} {format_submitted_at(entry['submitted_at'])}")
            print(f"Showing {len(entries)} run(s); use --offset to page through more.")
        return 0

    if args.command == "show":
        entry = find_entry(db_path, args.submission_id)
        if entry is None:
            raise AdminError(f"No community run found with submission ID {args.submission_id}")
        print_entry(entry)
        return 0

    if args.command == "edit":
        changes = {field: getattr(args, field) for field in EDITABLE_FIELDS if getattr(args, field, None) is not None}
        normalized = normalize_changes(changes)
        before = find_entry(db_path, args.submission_id)
        if before is None:
            raise AdminError(f"No community run found with submission ID {args.submission_id}")
        preview = dict(before)
        preview.update(normalized)
        print("Current entry:")
        print_entry(before)
        print("Proposed entry:")
        print_entry(preview)
        if not args.yes:
            answer = input("Apply this edit? Type YES to continue: ").strip()
            if answer != "YES":
                print("Cancelled; no changes made.")
                return 0
        _, after, backup = edit_entry(db_path, args.backup_dir, args.submission_id,
                                      normalized, confirmed=True)
        print(f"Updated community run. Verified pre-change backup: {backup}")
        print("Updated entry:")
        print_entry(after)
        return 0

    if args.command == "delete":
        before = find_entry(db_path, args.submission_id)
        if before is None:
            raise AdminError(f"No community run found with submission ID {args.submission_id}")
        print("Entry selected for deletion:")
        print_entry(before)
        if not args.yes:
            answer = input("Permanently delete this community run? Type DELETE to continue: ").strip()
            if answer != "DELETE":
                print("Cancelled; no changes made.")
                return 0
        deleted, backup = delete_entry(db_path, args.backup_dir, args.submission_id, confirmed=True)
        print(f"Deleted {deleted['submission_id']} ({deleted['name']}). Verified pre-change backup: {backup}")
        return 0

    raise AdminError("unknown command")


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return run_cli(args)
    except (AdminError, sqlite3.Error, OSError, ValueError) as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
