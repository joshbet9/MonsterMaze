# Monster Maze Solo VM deployment

This directory is for the private Ubuntu/Hyper-V solo server, not the player-facing Windows distribution.

## Design

The VM updater pulls the current 1.8 and 1.21 Solo release ZIPs from GitHub Releases. It updates server binaries, Monster Maze plugin binaries/configuration, ProtocolLib (1.21), and canonical `mm_*` maps.

The updater deliberately preserves runtime/user state:

- `server/world*`
- `server/plugins/MonsterMazeStandalone/solo-runs`
- submitter archives under `~/submitter/submitted`
- webhook configuration in `~/submit-config-18.ps1` and `~/submit-config-21.ps1`
- `server/server.properties`

The Linux submitter source is kept here so the VM copy can also be updated from Git. It reads the existing private webhook config files and archives each JSON only after Discord accepts it.

## VM layout

```text
/home/monstermaze/
├── servers/
│   ├── 1.8/
│   └── 1.21/
├── submitter/
└── backups/
```

Run `update.sh` from this directory after installing it on the VM. The script accepts `1.8`, `1.21`, or `all`.

A normal update creates a backup with the existing `~/backup-servers.sh` before replacing server files.
