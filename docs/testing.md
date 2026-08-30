# Monster Maze — Testing

## Build Commands

### Minecraft 1.8

    cd 1.8/MonsterMazeStandalone
    mvn package

### Minecraft 1.21

    cd 1.21/MonsterMazeStandalone
    mvn package

---

## Testing Philosophy

A successful compilation does not prove gameplay correctness.

When practical, mechanics should be validated through:

1. Original Mineplex source/reference
2. Recorded gameplay/log evidence
3. Source-level analysis
4. Build verification
5. Actual in-game testing

Record important discrepancies and discoveries in `docs/mechanics.md` or `docs/cross-version.md`.

---

## Test Sessions

### Template

**Date:**

**Version:**

**Build/Commit:**

**Map:**

**Mechanic Tested:**

**Expected:**

**Observed:**

**Result:**

**Evidence:**

**Follow-up:**
