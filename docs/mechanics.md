# Monster Maze — Mechanics Reference

This document records known Monster Maze gameplay behaviour independently from the implementation.

The purpose is to keep important gameplay discoveries and version differences in the repository rather
than inside development conversations.

For each mechanic, distinguish between:

- **Original behaviour** — behaviour reproduced from the original Mineplex implementation where known.
- **1.8 implementation** — how the Java 1.8 recreation currently implements it.
- **1.21 implementation** — how the Java 1.21 recreation currently implements it.
- **Verification** — whether the current implementation has been tested.

> Internal implementation groupings such as `QOL` are not gameplay categories. Where a mechanic differs
> by mode, this document describes the actual mode behaviour instead.

---

## Movement

### Speed and Jumping

**1.8:**

- Original uses the original movement/jump handling.
- Jumper has 5 charged jumps in Original and 3 in the other modes.
- A charged jump is a normal vanilla jump; it is not a double-jump or vertical boost.
- In non-Original modes, jumping while standing on a Safe Pad does not consume a charge and reaching a
  Safe Pad restores the Jumper to 3 charges.
- The 1.8 implementation uses the original jump-lock behaviour when no charges remain.

**1.21:**

- Jumper has 5 charged jumps in Original and 3 in Modern/Classic.
- The modern implementation uses the player's jump-strength attribute for jump locking because the old
  negative jump-effect technique is not suitable for modern Minecraft.
- Modern provides additional ground movement speed to players whose jumping is locked, reproducing the
  gameplay effect of the 1.8 movement handling as closely as the modern client allows.
- Classic keeps normal movement speed instead of that Modern speed boost.

**Verification:** Implemented in both versions; gameplay behaviour has been tested during parity work.

### Gap Crossing

Players can use their available movement and kit abilities to cross gaps between maze sections. Exact
movement edge cases remain implementation-dependent.

---

## Safe Pads

- Safe Pads are the progression checkpoints of Monster Maze.
- The first player to reach a Safe Pad receives the first-player reward.
- Other players reaching the same pad receive the normal pad heal.
- Body Builder gains 1 heart of maximum health when first to a Safe Pad, up to a maximum of 15 hearts.
- In non-Original modes, Jumper charges are restored when reaching a Safe Pad.
- In non-Original modes, Jumper jumps while standing on a Safe Pad are free.
- Maverick redirects monster knockback toward the relevant Safe Pad instead of using the normal random
  knockback direction.

---

## Monster Behaviour

### Movement

Monsters navigate through the maze toward players using the active monster movement implementation.

### Targeting

Monsters target alive players during the run. Exact target-selection edge cases are implementation-specific.

### Collision

A monster contacting a player normally causes the game's monster-hit response, including knockback and
associated damage where applicable.

Body Rush intercepts qualifying monster contacts before the normal player hit response.

### Knockback

Normal monster hits use the standard Monster Maze knockback implementation.

Maverick changes the result of a monster hit by directing the player toward the next Safe Pad rather than
allowing the normal random-direction knockback.

---

## Waves and Timing

### Starter Monsters

| Version | Mode | Starter monsters |
|---|---|---:|
| 1.8 | Original | 150 |
| 1.8 | Speed | 150 |
| 1.8 | Modern | 225 |
| 1.8 | Lagless | 500 fixed pool |
| 1.21 | Original | 150 |
| 1.21 | Modern | 225 |
| 1.21 | Classic | 225 |

### Timer

- Original pacing starts at 60 seconds and decreases by 2 seconds per stage until reaching 15 seconds.
- Modern pacing starts at 35 seconds and decreases toward 15 seconds over the first 10 stages.
- 1.8 Speed uses the Original timer/pacing while retaining the Modern gameplay changes.
- 1.8 Lagless uses the Modern timer.
- 1.21 Classic uses the Modern timer.

### Lagless

The 1.8 Lagless mode uses a fixed pool of 500 monsters created at the start of the run and does not add
another monster batch at each stage.

Instead, monster movement speed increases every 5 stages to maintain progression difficulty. Its timer
matches Modern and it retains the non-Original kit mechanics.

---

## Kits

Kit descriptions in the lobby should describe the abilities available in the currently selected mode.
They should not contain implementation labels such as `QOL`.

### Jumper

**Original:**

- 5 charged jumps.
- Charges are consumed by normal jumping when airborne.

**Non-Original modes:**

- 3 charged jumps.
- Safe Pads restore all charges.
- Jumping while standing on a Safe Pad does not consume a charge.

### Slowballer

**All modes:**

- Throws snowballs that slow other players for 2 seconds.
- Snowballs regenerate at 1 every 2 seconds, up to 16.

**Non-Original modes:**

- Cryo Blitz is available through the drop key.
- Cryo Blitz freezes monsters within 6 blocks for 3 seconds.
- Cryo Blitz has a 30-second cooldown.

### Body Builder

**All modes:**

- Being first to a Safe Pad increases maximum health by 1 heart, up to 15 hearts.
- Reaching a Safe Pad also provides the normal first-player healing reward.

**Non-Original modes:**

- Body Rush provides 2 activations per run.
- Each activation lasts 10 seconds.
- While active, qualifying monster contacts are completely deflected: the player takes no normal
  knockback or damage and the monster is launched away.
- Each monster contact removes 2 seconds from the remaining Body Rush duration.

### Repulsor

**All modes:**

- Has 3 charges per run.
- Activating Repulsor launches nearby monsters away from the player.
- The effective range is 6 blocks.
- Players are not Repulsor targets.
- Repulsed monsters are removed after the launch resolves rather than remaining as normal active threats.

### Maverick

**Non-Original modes:**

- Monster hits launch the player toward the next Safe Pad instead of using the normal random knockback
  direction.
- Maverick is unavailable in Original mode.

---

## Mode Summary

### 1.8

**Original**

- Original timer and monster spawning.
- 5 Jumper charges.
- Original kit set; no non-Original secondary abilities.

**Speed**

- Modern gameplay and kit mechanics.
- Original timer and monster spawning.
- 3 Jumper charges with Safe Pad restoration/free Safe Pad jumps.
- Maverick and non-Original secondary abilities are available.

**Modern**

- Faster timer: 35 seconds toward 15 seconds over the first 10 stages.
- 225 starter monsters instead of 150.
- Non-Original kit mechanics.

**Lagless**

- Fixed pool of 500 monsters.
- No per-stage monster spawning.
- Monster speed increases every 5 stages.
- Modern timer and non-Original kit mechanics.

### 1.21

**Original**

- Original timer and monster spawning.
- 5 Jumper charges.
- Original kit set; no non-Original secondary abilities.

**Modern**

- Faster timer and 225 starter monsters.
- 3 Jumper charges with Safe Pad restoration/free Safe Pad jumps.
- Maverick and non-Original secondary abilities.
- Modern movement handling provides the closest practical equivalent to 1.8's movement-speed behaviour.

**Classic**

- Modern timer, monster count and kit mechanics.
- No Modern speed boost for players whose jumping is locked.

---

## Scoring and Personal Bests

Run progression is measured by the stage reached. Kit personal bests are tracked against the selected
mode and maze pattern.

The competitive backend separately records lifetime personal-best information and seasonal competition
data. Seasonal competitive components reset at the start of a new season while lifetime MMR remains
persistent.

---

## Secondary Ability Details

### Body Rush

- Two apples provide two activations.
- Each activation lasts 10 seconds.
- Monster contact while active deflects the monster and prevents the normal player knockback/damage.
- Each real monster contact removes exactly 2 seconds from the remaining duration.
- The normal 1-second monster-hit cooldown is bypassed while Body Rush is active.

### Cryo Blitz

- Activated with the drop key while playing Slowballer in a non-Original mode.
- Freezes every monster within 6 blocks.
- Freeze duration: 3 seconds.
- Cooldown: 30 seconds.
- Frozen monsters do not move or perform normal bump behaviour during the freeze.

---

## Known Version Differences

The two implementations target different Minecraft generations and therefore do not always use the same
underlying mechanism even when the player-facing gameplay is intended to match.

The most significant current example is Jumper jump locking and the associated Modern movement-speed
behaviour. The 1.8 implementation can use legacy movement/effect mechanics; 1.21 uses modern player
attributes and event handling instead.
