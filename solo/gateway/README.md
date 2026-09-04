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
/home/ubuntu/monster-gateway/   # this gateway
/etc/monstermaze-gateway.env    # Fly token + gateway configuration
```

Install:

```bash
sudo mkdir -p /home/ubuntu/monster-gateway
sudo chown ubuntu:ubuntu /home/ubuntu/monster-gateway
cd /home/ubuntu/monster-gateway
curl -fsSL https://raw.githubusercontent.com/joshbet9/MonsterMaze/main/solo/gateway/monstermaze_gateway.py -o monstermaze_gateway.py
```

Create `/etc/monstermaze-gateway.env` from the committed example and put the
real Fly token there. **Do not commit the real token.**

Install the service:

```bash
sudo cp /home/ubuntu/monster-gateway/monstermaze-gateway.service /etc/systemd/system/monstermaze-gateway.service
sudo systemctl daemon-reload
sudo systemctl enable --now monstermaze-gateway.service
sudo systemctl status monstermaze-gateway.service
```

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
2. Confirm the gateway is listening on Oracle port 25565.
3. Refresh the Minecraft server list. This must **not** start either Machine.
4. Connect using Minecraft 1.8.9. The gateway should start mm18 and show the
   direct Fly address plus the reconnect message.
5. Reconnect to `monstermaze.fly.dev:25565`; gameplay must connect directly to
   the Fly service and must not appear in the gateway's proxy/login logs.
6. Repeat with Minecraft 1.21.11 and verify mm21 starts, then reconnect to
   `monstermaze.fly.dev:25566`.
7. Leave the server empty and confirm Fly eventually stops the Machine.
8. Refresh the server list again while stopped; the gateway should answer the
   ping without waking the Machine.
9. While a Machine is already running, connecting to the gateway should only
   return the direct Fly address; it must never proxy gameplay.

## Security notes

- The gateway exposes only the Minecraft TCP wake listener and the Fly API
  token is read from a root-readable environment file.
- Unknown Minecraft protocol versions are rejected before reaching a backend.
- The Fly token should be app-scoped and rotated if it is ever exposed.
- The gateway never accepts a player connection to a backend. It starts the
  Machine when necessary and closes the client connection with the direct Fly
  service address.
