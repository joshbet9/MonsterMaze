# Monster Maze Solo — Minecraft 1.21

This is the 1.21 Solo distribution modelled on the working 1.8 Solo package.

It is **not a second Minecraft plugin**. The distribution runs the normal
`MonsterMazeStandalone` plugin on a local Paper 1.21.11 server and ships the
canonical `mm_*` arena worlds as application assets.

## Player flow

1. Extract the release ZIP.
2. Double-click `launcher\play.bat`.
3. Open Minecraft 1.21.11 and join `localhost`.
4. Run `/mm start`.
5. Choose a kit and play.
6. Double-click `submitter\submit.bat` when finished to submit saved Solo PBs.

A bundled Java 21 runtime is preferred for the public release so players do
not need to install Java themselves. Paper 1.20 through 1.21.11 requires Java
21.

## Map assets

The canonical source maps remain in `solo/maps/mm_*`. The 1.21 pack process
copies them into the player server as `server/mm_*`. Paper upgrades the legacy
world data to the 1.21 world format during the conversion step; the resulting
1.21 map assets are then packaged with the release.

The physical maps currently required by the 1.21 map definitions are:

- `mm_colombia`
- `mm_sandycoast`
- `mm_siberian`
- `mm_swampland`
- `mm_tesorohundido`
- `mm_volcano`

`eyeofender` uses the generated `mm_void` world and does not require a shipped
physical world.
