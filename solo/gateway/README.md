# Monster Maze Minecraft Wake Gateway

A small Python TCP gateway intended to run on the existing Oracle VM alongside
the Monster Maze Discord bot/API.

## Purpose

The gateway is a **wake/redirect endpoint, not a gameplay proxy**. It understands
the initial Java Edition handshake:

- **Status ping (`Next State = 1`)**: never starts a Fly Machine. If the backend
  is running, the gateway proxies only the server-list status request to it. If
  it is stopped, the gateway returns a local status response.
- **Login (`Next State = 2`)**: routes by protocol version. If the target Fly
  Machine is stopped, the gateway starts it through the Fly Machines API and
  disconnects the player with the direct Fly address to use after startup. If
  it is already running, the gateway also disconnects the player with the direct
  Fly address.

**The Oracle gateway never carries an active Minecraft gameplay connection.**
Players reconnect directly to the Fly public service, so their gameplay traffic
uses the normal path to the US Fly region rather than travelling through the
Australian Oracle VM.

The current targets are:

| Minecraft | Protocol | Fly process | Fly public port |
|---|---:|---|---:|
| 1.8.9 | 47 | mm18 | 25565 |
| 1.21.11 | 774 | mm21 | 25566 |

The protocol numbers are used only for wake routing; the backend receives no
player gameplay connection from the gateway.

## Why Fly still handles shutdown

The player reconnects directly to the Fly **public TCP service**, not a
`.internal` 6PN address. Fly Proxy therefore sees the real player connection
and can perform `auto_stop_machines = "stop"` when the Machine is idle.
`auto_start_machines` remains disabled so a random TCP connection cannot wake
the server. The gateway explicitly starts a Machine only after seeing a
Minecraft login handshake.

## Oracle installation

The gateway is designed to live beside `/home/ubuntu/monster-bot` on the same
Oracle VM. It does not share the bot process or Python virtual environment.

Recommended layout:

```text
/home/ubuntu/monster-bot/       # existing Discord bot + API
/home/ubuntu/monster-gateway/   # this gateway + local status API
/etc/monstermaze-gateway.env    # Fly token + gateway configuration
```

Install:

```bash
sudo mkdir -p /home/ubuntu/monster-gateway
sudo chown ubuntu:ubuntu /home/ubuntu/monster-gateway
cd /home/ubuntu/monster-gateway
curl -fsSL https://raw.githubusercontent.com/joshbet9/MonsterMaze/main/solo/gateway/monstermaze_gateway.py -o monstermaze_gateway.py
curl -fsSL https://raw.githubusercontent.com/joshbet9/MonsterMaze/main/solo/gateway/status_api.py -o status_api.py
```

Create `/etc/monstermaze-gateway.env` from the committed example and put the
real Fly token there. **Do not commit the real token.**

Install the services:

```bash
sudo cp /home/ubuntu/monster-gateway/monstermaze-gateway.service /etc/systemd/system/monstermaze-gateway.service
sudo cp /home/ubuntu/monster-gateway/monstermaze-status.service /etc/systemd/system/monstermaze-status.service
sudo systemctl daemon-reload
sudo systemctl enable --now monstermaze-gateway.service monstermaze-status.service
sudo systemctl status monstermaze-gateway.service monstermaze-status.service
```

## Discord server status

The local status API is intentionally bound to `127.0.0.1:8765`; it is not
internet-facing. It is consumed by the Discord bot using:

```text
http://127.0.0.1:8765/status
```

For each server, it first checks the Fly Machine state. A stopped/suspended
Machine is reported offline and **no Minecraft connection is attempted**. A
starting/restarting Machine is reported as starting. Only when Fly reports the
Machine as started does the API perform a normal Minecraft server-list status
request to obtain the live player count. If that status request is not yet
available, the server remains yellow/starting rather than falsely reporting
online.

The bot keeps one persistent message in the configured `server-status`
channel and edits it rather than posting repeatedly:

```text
🟢 MM18     3 players
🟡 MM21     Starting...
```

Configure these optional bot settings in `solo/bot/config.json`:

```json
"server_status_channel": "server-status",
"server_status_url": "http://127.0.0.1:8765/status",
"server_status_interval": 15
```

The bot stores the message ID in its existing `boards` table, so restarting
the bot does not create a second status message.

## Fly token

Use an app-scoped deploy token for the `monstermaze` app. Fly documents these
as the narrow-scope option intended for programmatic access:

```bash
fly tokens create deploy --name "Monster Maze Oracle Gateway"
```

The resulting token belongs in `/etc/monstermaze-gateway.env` as
`FLY_API_TOKEN=...`.

## DNS / Caddy

The Minecraft wake gateway needs TCP port **25565** on the Oracle VM. The same
hostname can continue to serve the HTTPS API through Caddy on 443; Caddy does
not handle this raw Minecraft TCP listener.

Players initially use:

```text
monstermaze.duckdns.org:25565
```

When a login arrives, the gateway starts the correct Machine and tells the
player to reconnect directly to the corresponding Fly service:

```text
1.8.9   -> monstermaze.fly.dev:25565
1.21.11 -> monstermaze.fly.dev:25566
```

The direct Fly address is important: **do not point the Minecraft gameplay
connection back at `monstermaze.duckdns.org:25565`**, because that would send
the gameplay traffic through the Australian gateway again.

## Testing order

1. Stop both Fly Machines.
2. Confirm the gateway and status API are active on Oracle.
3. Query `curl -fsS http://127.0.0.1:8765/status`; both stopped Machines must be
   reported offline.
4. Repeat the status query several times while both Machines are stopped. The
   Machine states must remain stopped; the status API must never wake them.
5. Wake MM18 using a real Minecraft login through the gateway. The Discord
   status should progress from offline to starting to online.
6. Join/leave MM18 and confirm the player count changes without creating a new
   Discord message.
7. Repeat with MM21.
8. Stop each Machine and confirm Discord returns to offline.
9. Restart the bot and confirm it edits the existing status message rather than
   creating a duplicate.
10. Refresh the Minecraft server list while a Machine is stopped; the existing
    gateway safety guarantee still applies and the Machine must not wake.

## Security notes

- The gateway exposes only the Minecraft TCP wake listener and the Fly API
  token is read from a root-readable environment file.
- The Discord status API binds to localhost only and exposes no Fly credentials.
- Unknown Minecraft protocol versions are rejected before reaching a backend.
- The Fly token should be app-scoped and rotated if it is ever exposed.
- The gateway never accepts a player connection to a backend. It starts the
  Machine when necessary and closes the client connection with the direct Fly
  service address.
