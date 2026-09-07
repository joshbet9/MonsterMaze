# Monster Maze `/pause`

Implemented in both the 1.8 and 1.21 standalone implementations.

## Behaviour

- Solo mode: any player can use `/pause`.
- Non-solo mode: `/pause` requires `monstermaze.admin`, which defaults to OP.
- `/pause` toggles between the live round and a frozen round.
- Paused rounds stop the Monster Maze live game loop and custom mob movement.
- Alive players are prevented from moving, interacting, taking damage, changing sprint/sneak/flight, dropping/picking up items, or losing hunger while paused.
- Existing mob/player velocities are cleared when pausing.
- Resuming uses a 3-second countdown.
- The elapsed live timer is adjusted by the complete pause duration, including the resume countdown, so PB/run timing does not include paused time.

The current implementation uses the existing GameManager `STARTING` gate while paused so all existing live-loop and mob-loop checks naturally stop progressing. The pause controller restores `LIVE` on resume.
