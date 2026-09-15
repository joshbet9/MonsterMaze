"""Discord commands for managing the MonsterMaze IGN denylist."""
import json
import os
from pathlib import Path

DENYLIST_FILE = Path(os.getenv("MM_IGN_BLACKLIST_FILE", "/home/ubuntu/monstermaze/data/ign_blacklist.json"))


def _load():
    try:
        with DENYLIST_FILE.open("r", encoding="utf-8") as handle:
            data = json.load(handle)
        if isinstance(data, list):
            return {str(item).strip().casefold() for item in data if str(item).strip()}
    except (FileNotFoundError, OSError, json.JSONDecodeError):
        pass
    seed = os.getenv("MM_IGN_BLACKLIST", "")
    return {item.strip().casefold() for item in seed.split(",") if item.strip()}


def _save(entries):
    DENYLIST_FILE.parent.mkdir(parents=True, exist_ok=True)
    temp = DENYLIST_FILE.with_name(DENYLIST_FILE.name + ".tmp")
    with temp.open("w", encoding="utf-8") as handle:
        json.dump(sorted(entries), handle, indent=2)
        handle.write("\n")
    os.replace(temp, DENYLIST_FILE)


async def handle_denylist(bot, message, args):
    if not args or args[0].lower() in ("help", "?"):
        await message.channel.send("`!blacklist add <IGN>` • `!blacklist remove <IGN>` • `!blacklist list` • `!blacklist check <IGN>` (staff)")
        return True
    if not message.guild or not message.author.guild_permissions.manage_guild:
        await message.channel.send("❌ Manage Server permission required.")
        return True

    sub = args[0].lower()
    entries = _load()

    if sub == "list":
        if not entries:
            await message.channel.send("✅ IGN blacklist is empty.")
        else:
            names = "\n".join(f"• `{name}`" for name in sorted(entries))
            await message.channel.send(f"🚫 **IGN blacklist**\n{names}")
        return True

    if sub in ("add", "remove", "check"):
        if len(args) != 2 or not args[1].strip():
            await message.channel.send(f"Usage: `!blacklist {sub} <IGN>`")
            return True
        ign = args[1].strip()
        key = ign.casefold()
        if len(ign) > 16:
            await message.channel.send("❌ Minecraft IGN must be 16 characters or fewer.")
            return True

        if sub == "check":
            await message.channel.send(f"{'🚫 Blacklisted' if key in entries else '✅ Not blacklisted'}: `{ign}`")
            return True

        if sub == "add":
            if key in entries:
                await message.channel.send(f"ℹ️ `{ign}` is already blacklisted.")
                return True
            entries.add(key)
            _save(entries)
            await message.channel.send(f"🚫 Added `{ign}` to the IGN blacklist.")
            return True

        if key not in entries:
            await message.channel.send(f"ℹ️ `{ign}` is not on the IGN blacklist.")
            return True
        entries.remove(key)
        _save(entries)
        await message.channel.send(f"✅ Removed `{ign}` from the IGN blacklist.")
        return True

    await message.channel.send("Unknown blacklist command. Use `!blacklist help`.")
    return True
