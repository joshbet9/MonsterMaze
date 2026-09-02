from pathlib import Path
source = Path('.github/rework_body_slowball_v2.py').read_text()
old = "s=once(s,'                // Anti-bonk, ping-independent: lift the player ABOVE the maze floor before applying','                // Normal mob contact retains the original one-second bump cooldown.\\n                markBump(player);\\n\\n                // Anti-bonk, ping-independent: lift the player ABOVE the maze floor before applying',f'{p} normal mark')"
new = "if '1.21/' in str(p):\n        s=once(s,'                double floorY = game.getCenter().getY();','                markBump(player);\\n\\n                double floorY = game.getCenter().getY();',f'{p} normal mark')\n    else:\n        s=once(s,'                // Anti-bonk, ping-independent: lift the player ABOVE the maze floor before applying','                // Normal mob contact retains the original one-second bump cooldown.\\n                markBump(player);\\n\\n                // Anti-bonk, ping-independent: lift the player ABOVE the maze floor before applying',f'{p} normal mark')"
source = source.replace(old, new)
source = source.replace("'.github/workflows/run-body-slowball-rework-v2.yml']", "'.github/workflows/run-body-slowball-rework-v2.yml', '.github/rework_body_slowball_v3.py']")
exec(compile(source, '.github/rework_body_slowball_v3.py', 'exec'))
