# Monster Maze Minecraft Wake Gateway

A small Python TCP gateway intended to run on the existing Oracle VM alongside
the Monster Maze Discord bot/API.

## Purpose

The gateway is the public Minecraft endpoint. It understands only the initial
Java Edition handshake:

- **Status ping (`Next State = 1`)**: never starts a Fly Machine. If the backend
  is running, the gateway proxies the status request to it. If it is stopped,
  the gateway returns a local status response.
- **Login (`Next State = 2`)**: routes by protocol version. If the target Fly
  Machine is stopped, the gateway starts it through the Fly Machines API and
  tells the player to reconnect in about 60 seconds. If it is already running,
  the gateway transparently proxies the connection.

The current targets are:

| Minecraft | Protocol | Fly process | Fly public port |
|---|---:|---|---:|
| 1.8.9 | 47 | mm18 | 25565 |
| 1.21.11 | 774 | mm21 | 25566 |

The protocol numbers are used only for routing; the backend receives the
original handshake unchanged.

## Why Fly still handles shutdown

The gateway connects to the Fly **public TCP services**, not a `.internal`
6PN address. This is deliberate. Fly Proxy therefore still sees the player TCP
connection and can perform `auto_stop_machines = "stop"` when the Machine is
idle. `auto_start_machines` remains disabled so a random TCP connection cannot
wake the server. The gateway explicitly starts a Machine only after seeing a
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
sudo cp /home/ubuntu/monster-gateway/monstermaze_gateway.py /home/ubuntu/monster-gateway/monstermaze_gateway.py
```

Create `/etc/monstermaze-gateway.env` from the committed example and put the
real Fly token there. Do **not** commit the real token.

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

The Minecraft gateway needs TCP port **25565** on the Oracle VM. The same
hostname can continue to serve the HTTPS API through Caddy on 443; Caddy does
not handle this raw Minecraft TCP listener.

For example, if `monstermaze.duckdns.org` already points at the Oracle VM, the
Minecraft client can use:

```text
monstermaze.duckdns.org:25565
```

No Minecraft TCP configuration is required in Caddy.

## Testing order

1. Stop both Fly Machines.
2. Confirm the gateway is listening on Oracle port 25565.
3. Refresh the Minecraft server list. This must **not** start either Machine.
4. Connect using Minecraft 1.8.9. The gateway should start mm18 and show the
   reconnect message.
5. Reconnect after the server is ready; the connection should proxy normally.
6. Repeat with Minecraft 1.21.11 and verify mm21 starts instead.
7. Leave the server empty and confirm Fly eventually stops the Machine.
8. Refresh the server list again while stopped; the gateway should answer the
   ping without waking the Machine.

## Security notes

- The gateway exposes only the Minecraft TCP listener and the Fly API token is
  read from a root-readable environment file.
- Unknown Minecraft protocol versions are rejected before reaching a backend.
- The Fly token should be app-scoped and rotated if it is ever exposed.
- The gateway never accepts a player connection to a stopped backend; it starts
  the Machine and closes the client connection with a reconnect message.
