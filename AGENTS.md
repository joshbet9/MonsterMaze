# Monster Maze — AI Agent Instructions

## Project Overview

Monster Maze is a recreation/reimplementation of the Mineplex Monster Maze minigame.

This repository contains TWO implementations of the same game:

- `1.8/MonsterMazeStandalone/`
  - Minecraft 1.8.8 / Spigot
  - Java 8
  - Existing, substantially playable implementation
  - Uses Spigot API and some version-specific NMS (`v1_8_R3`)

- `1.21/MonsterMazeStandalone/`
  - Minecraft 1.21.11 / Paper
  - Java 21
  - Current work-in-progress modern implementation
  - The immediate goal is to make this version fully playable and behaviourally accurate.

These are separate codebases because the Minecraft APIs and server internals differ substantially.

## Critical Cross-Version Rule

The two implementations represent the SAME GAME.

Gameplay behaviour, mechanics, timing, movement, mob behaviour, wave behaviour, abilities, scoring, maps, and other game rules should be considered shared concepts even when their code implementations differ.

A change discovered or implemented in one version may need to be investigated and/or ported to the other version.

DO NOT assume that a change only matters to the version currently being edited.

However, DO NOT blindly copy code between versions. Instead:

1. Identify the underlying gameplay behaviour.
2. Check how the other implementation handles that behaviour.
3. Check the original Mineplex reference material when available.
4. Implement the equivalent behaviour using the APIs appropriate to that Minecraft version.
5. Build and test the affected implementation(s).

## Version-Specific Code

Never replace working version-specific code merely to make the two implementations look structurally similar.

The goal is behavioural equivalence, not identical source code.

Minecraft 1.8 and Minecraft 1.21 have fundamentally different APIs and server internals. Use the appropriate implementation for each version.

## Original Mineplex Reference

The repository contains original/recovered Mineplex Monster Maze source/reference material.

Treat this as the primary reference when determining how the original game behaved.

Do not invent behaviour when the original source, recorded gameplay, logs, or other evidence can answer the question.

When evidence is uncertain, explicitly state the uncertainty.

## Before Making Changes

Always:

1. Inspect the relevant existing implementation.
2. Check `git status`.
3. Inspect existing uncommitted changes.
4. Determine whether the mechanic has a corresponding implementation in the other Minecraft version.
5. Avoid overwriting unrelated work.

Existing user/agent changes are valuable and must be preserved unless explicitly asked to replace them.

## During Changes

Prefer small, focused changes.

Do not refactor unrelated code while implementing a gameplay feature unless there is a clear reason.

When modifying gameplay behaviour, explain what behaviour is being changed and why.

## After Changes

Build the affected project.

For 1.8:

    cd 1.8/MonsterMazeStandalone
    mvn package

For 1.21:

    cd 1.21/MonsterMazeStandalone
    mvn package

If both implementations are affected, build both.

Do not claim a change is complete until the affected project builds successfully, unless a build cannot reasonably be performed. If a build cannot be performed, state why.

## Testing

Whenever possible, distinguish between:

- Source-level confidence
- Build verification
- Static/code analysis
- Actual in-game testing
- Behaviour confirmed against original Mineplex evidence

Do not describe untested behaviour as confirmed.

## Cross-Version Changes

When a mechanic is changed in one implementation, consider whether the corresponding implementation in the other version needs:

- The same change
- An adapted equivalent
- No change because the behaviour is version-specific

If the other implementation is intentionally left unchanged, document why when the reason is not obvious.

## Git

Do not reset, revert, checkout, or discard existing user changes without explicit permission.

Do not use destructive Git commands to "clean up" the repository.

Do not automatically commit major changes unless explicitly requested.

Before substantial work, inspect:

    git status
    git diff

After substantial work, report:

- Files changed
- Behaviour changed
- Build result
- Tests performed
- Whether the other Minecraft version may require corresponding work

## Agent Handoffs

Different AI agents may work on this repository at different times.

The filesystem and Git history are the shared source of truth, not the previous AI's conversation.

When continuing work started by another agent:

1. Inspect the current working tree.
2. Read this file.
3. Read relevant project documentation.
4. Inspect recent Git history.
5. Inspect existing diffs.
6. Continue from the actual current code rather than assuming the previous agent completed its claimed work.

Never assume that a previous agent's final message accurately describes the current code state.

## Important Principle

The ultimate goal is not merely to make the plugin compile.

The goal is to recreate Monster Maze's gameplay and behaviour accurately across both supported Minecraft versions while preserving the ability to understand, test, and port mechanics between them.
