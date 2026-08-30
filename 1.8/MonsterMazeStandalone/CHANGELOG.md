# Monster Maze — gameplay changes vs the original Mineplex source

Everything below is a place where this version **differs from the original Mineplex game**. Anything
not listed (maze layout, Safe Pad building/decay, monster AI, bump physics, compass, exp-bar timer,
phase countdowns, first-to-pad heal/timer bonus, stats) plays the same as the original. No server,
lobby, or admin features are listed here.

---

## 1. Game modes (new)

The game now has three selectable modes instead of one fixed config:

- **Original** — 1:1 source behavior (150 starter monsters; 15 added per Safe Pad; 60s→15s timer; 5 Jumper leaps).
- **Speed** — the QOL fixes below, plus Jumper has 3 leaps that refill on every Safe Pad.
- **Modern** — QOL + difficulty tuning: **225 starter monsters**, **30 added per Safe Pad**, timer **35s → 15s** over the first 10 stages.

## 2. Kit changes

### Jumper
- **Leap refill** (Speed/Modern): leaps refill to 3 every time you reach a Safe Pad. (Original gives a one-time 5, like the source.)
- **Free jumps on the pad** (Speed/Modern): hopping around on the Safe Pad no longer spends a leap.
- **2s post-hit grace**: after a monster knocks you, your leaps aren't consumed while you're being flung and recovering (the original's check dropped off as soon as you topped out or started falling, so spam-jumping could eat a charge mid-knock).
- **Jump-block exploit closed**: non-Jumper players can no longer slip a jump through a small window while the "no jumping" effect refreshes.

### Slowballer
- **Slow-only snowballs**: a hit now applies just Slowness — it no longer deals damage or gives a knockback jolt (this version's snow that only slows).
- **Post-bump immunity** (Speed/Modern): a player who was *just* knocked by a monster (or who is mid-air) is immune to your slow for a moment, so a snowball can't double-punish someone a monster already got.

### Body Builder
- Unchanged — first-to-pad still grants +1 heart (max 15), same as the original.

### Repulsor
- Unchanged — 3-point coal repulse, 6-block radius, knock + firework, same as the original.

### Maverick (new kit, Speed/Modern only)
- A monster hit always knocks you **toward** the next Safe Pad — no more knocked into a random wall or off the maze.

## 3. Bug fixes vs. the OG source

- **Map repetition now actually works.** The original's `getRandomMap()` saved a "last selected" map but returned a fresh random anyway, so it could replay the same one. Now each game gets a genuinely different layout.
- **Exact monster counts.** The original spawn loops used `<=`, silently spawning one extra monster per wave (151, not 150). Counts are exact now.
- **No stale knock-lock between rounds.** The original never cleared its per-player "launched" state, so a knock from a previous game could still trap you in the next one. Resets cleanly.
- **Knocks near the floor actually launch you.** In the original, being hit while grounded/bouncing on the floor could have the client eat the knock via ground friction, so a bump sometimes just looked like a push. The launch now lifts you off the floor first, so every hit throws you (all modes).
- **Safe Pad hitbox** (Speed/Modern): the original's detection box is offset from the visible 5×5 pad, so standing on an edge block could still count as "off pad" and kill you at the timer. QOL modes use a symmetric box that exactly matches the pad you're standing on; **Original** intentionally keeps the source's box.

## 4. New in-game features

- **Countdown in the hotbar number**: the seconds remaining are shown as the level number above the exp bar, so the panic time is visible at a glance.
- **View-of-others toggle**: a hotbar dye lets you cycle **visible → hidden → see-through ghosts** for the other players (in the original you had no say over how you saw people).
- **Personal best on the scoreboard**: your best stage for the current mode + map layout is shown per game, so you always know the record to beat.

## 5. Removed compared to the original

- **Gem rewards** (2 per Safe Pad, 7.5 for first) — there's no economy in this version, so they're gone. The heal, timer bonus, and Body Builder growth all remain.

## 6. No gameplay effect

- Monsters spawn in staggered batches, move/AI runs at 10Hz, and HUD packets are throttled — the game feels the same but runs far lighter. (Full technical notes in `PERFORMANCE_CHANGES.md`.)