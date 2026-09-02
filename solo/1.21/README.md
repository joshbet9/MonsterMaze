# Monster Maze Solo — Minecraft 1.21

This is the 1.21 Solo distribution modelled on the working 1.8 Solo package.

It is **not a second Minecraft plugin**. The distribution runs the normal `MonsterMazeStandalone` plugin on a local Paper 1.21.11 server and ships the canonical `mm_*` arena worlds as application assets.

## Player flow

1. Extract the release ZIP.
2. Double-click `launcher/play.bat`.
3. Open Minecraft 1.21.11 and join `localhost`.
4. Run `/mm start`.
5. Choose a kit and play.
6. Double-click `submitter/submit.bat` when finished to submit saved Solo PBs.

A bundled Java 21 runtime is preferred for the public release so players do not need to install Java themselves. Paper 1.20 through 1.21.11 requires Java 21.

## Map assets

The canonical 1.21 source maps live in `solo/1.21/maps/mm_*`. The pack process copies them into the player server as `server/mm_*`. These are already native 1.21 world folders; no conversion is performed when the player starts the release.

The seven shipped physical map worlds are:

- `mm_colombia`
- `mm_sandycoast`
- `mm_siberian`
- `mm_swampland`
- `mm_tesorohundido`
- `mm_volcano`
- `mm_void`

`eyeofender` uses the shipped `mm_void` world. It is not generated on demand.
