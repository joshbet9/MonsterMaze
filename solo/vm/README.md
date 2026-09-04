# Monster Maze Solo VM deployment

This directory contains the Ubuntu/Hyper-V deployment tooling for Monster Maze Solo. It is separate from the player-facing Windows Solo distributions.

## VM design

The VM hosts both supported server versions:

```text
/home/monstermaze/
├── servers/
│   ├── 1.8/
│   └── 1.21/
├── submitter/
└── backups/
```

The updater pulls the latest published Solo release for each platform from GitHub Releases. It replaces server binaries, Monster Maze plugin binaries/configuration, ProtocolLib (1.21), and canonical `mm_*` maps.

The updater deliberately preserves runtime/user state:

- `server/world*`
- `server/plugins/MonsterMazeStandalone/solo-runs`
- submitter archives under `~/submitter/submitted`
- webhook configuration in `~/submit-config-18.ps1` and `~/submit-config-21.ps1`
- `server/server.properties`

Release ZIPs are SHA-256 checked when GitHub provides an asset digest. A server backup is created before replacement when `~/backup-servers.sh` is available.

## First-time VM bootstrap

Run `install.sh` on a fresh Ubuntu VM. It installs the updater and Linux submission service from the `main` branch and creates the required directories.

Then run:

```bash
~/monstermaze-update.sh all
```

The updater accepts `1.8`, `1.21`, or `all`.

## Discord submission setup

Discord webhooks are intentionally not stored in Git or inside a distributable VM image. On a destination VM, run:

```bash
~/setup-submitters.sh
```

Enter a Discord webhook URL for each platform that should submit runs. Leave either platform blank to keep submission disabled.

The submitter watches both platform `solo-runs` folders, posts completed runs, and archives a run only after Discord accepts the post.

## Preparing a VM for distribution

Do not sanitise the VM that contains your personal runtime history. Export or clone it first, then run the sanitiser inside the copy.

The sanitiser is:

```bash
sudo /path/to/prepare-shareable.sh --confirm
```

It removes distribution-sensitive or personal state:

- Solo run history and submission archives
- Discord webhook configuration
- shell history and temporary updater files
- generated server logs and local caches
- Linux machine-id
- SSH host keys

It retains server worlds and installed release files, because these are part of the tested VM baseline.

After sanitisation, shut the VM down and export it from Hyper-V. The exported VM should be boot-tested again before being shared.

A typical Windows export command is:

```powershell
Export-VM -Name "MonsterMaze-Test" -Path "D:\MonsterMaze\Exports"
```

For an actual public release, export the sanitised copy under a neutral name such as `MonsterMaze-Solo-VM`, then package the Hyper-V export directory into a ZIP or 7z archive.

## Distribution lifecycle

The intended workflow is:

```text
Build/test release
    ↓
Publish 1.0.x Solo release
    ↓
VM updater downloads release
    ↓
Existing worlds/settings/history remain intact
    ↓
Future release updates require no manual server replacement
```

A shareable VM should contain no personal webhook secrets or personal run history. Recipients can add their own webhook configuration after import when Discord submission is required.
