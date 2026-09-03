# Monster Maze - AI Agent Instructions

## Project Overview

Monster Maze is a recreation/reimplementation of the Mineplex Monster Maze minigame.

This repository contains TWO implementations of the same game:

* `1.8/MonsterMazeStandalone/`

  * Minecraft 1.8.8 / Spigot
  * Java 8
  * Existing, substantially playable implementation
  * Uses Spigot API and some version-specific NMS (`v1_8_R3`)

* `1.21/MonsterMazeStandalone/`

  * Minecraft 1.21.11 / Paper
  * Java 21
  * Current work-in-progress modern implementation
  * The immediate goal is to make this version fully playable and behaviourally accurate.

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

## Evidence-First Investigation

When asked to inspect, verify, compare, or determine whether something has been implemented:

* Do NOT make changes.
* Do NOT give a general summary of the project or class.
* Answer the specific question asked.
* Use repository/file tools to inspect the actual files.
* Search for the exact classes, methods, fields, strings, or behaviours relevant to the question.
* Report concrete evidence from the source.
* If something cannot be verified from the available files, say `UNCLEAR` rather than guessing.

For verification tasks, use this general workflow:

1. Identify the exact file(s) relevant to the question.
2. Search for the exact symbols/strings involved.
3. Read the surrounding implementation.
4. Compare the implementation against the requested behaviour.
5. Report each requested item individually.
6. Only run a build if the user asks for build verification or the task explicitly requires it.
7. Never modify files during a verification-only task.

A request to "inspect", "verify", "check", "determine whether", or "assess whether" does NOT authorize code changes unless the user explicitly asks for changes.

### Tool Use

When repository/file tools are available, actually use them to inspect the project.

Do not substitute a generic explanation for an inspection.

When a tool returns a result, use that result to answer the user's specific question before exploring unrelated files.

For example, if asked whether `gapHops` exists:

* Search for `gapHops`.
* Search for `spaceWasDown`.
* Inspect `tickJumpLock`.
* Inspect `resetPlayerState`.
* Report the findings.

Do not instead summarize `KitManager.java` as a whole.

## Task Discipline

Prefer completing the requested task over exploring the entire repository.

For focused tasks:

* Do not inspect every Java file unless necessary.
* Do not repeatedly rediscover the same information.
* Do not broaden a focused investigation into a general code review.
* If the requested information has been established, stop investigating and report it.
* If a tool operation fails, retry it once using a simpler approach before changing strategy.

## During Changes

Prefer small, focused changes.

Do not refactor unrelated code while implementing a gameplay feature unless there is a clear reason.

When modifying gameplay behaviour, first state the intended behaviour and identify the relevant implementation. Do not begin editing until you understand the existing implementation.

## After Changes

Build the affected project.

For 1.8:

```
cd 1.8/MonsterMazeStandalone
mvn package
```

For 1.21:

```
cd 1.21/MonsterMazeStandalone
mvn package
```

If both implementations are affected, build both.

Do not claim a change is complete until the affected project builds successfully, unless a build cannot reasonably be performed. If a build cannot be performed, state why.

## Build and Deployment

Monster Maze has two actively maintained implementations:

* Minecraft 1.8.8 / Spigot
* Minecraft 1.21.11 / Paper

These are separate implementations of the same game. They should maintain equivalent gameplay mechanics where practical, while respecting version-specific APIs and capabilities.

### Source directories

1.8:

`C:\Users\Josh\MonsterMaze\1.8\MonsterMazeStandalone`

1.21:

`C:\Users\Josh\MonsterMaze\1.21\MonsterMazeStandalone`

### Test server directories

1.8 server:

`C:\monstermaze_test`

1.8 plugin directory:

`C:\monstermaze_test\plugins`

1.21 server:

`C:\monstermaze21`

1.21 plugin directory:

`C:\monstermaze21\plugins`

### Deployment mapping

NEVER deploy a version to the wrong server.

| Source                       | Test server                   |
| ---------------------------- | ----------------------------- |
| `1.8/MonsterMazeStandalone`  | `C:\monstermaze_test\plugins` |
| `1.21/MonsterMazeStandalone` | `C:\monstermaze21\plugins`    |

### Build workflow

After making a code change:

1. Build the affected version with Maven.
2. Do not deploy if the build fails.
3. If the build succeeds, deploy the newly built MonsterMazeStandalone JAR to the corresponding test server's `plugins` directory.
4. Restart the corresponding test server.
5. Inspect the server console for startup errors.
6. Report build and startup status.
7. Do not claim gameplay has been tested unless the user has actually tested it in Minecraft.

### Automatic Test Server Deployment

The test servers are local development servers used to verify that the built plugin actually starts.

When the user asks to implement, fix, or test a change, the normal workflow should be:

1. Make the requested code changes.
2. Build the affected implementation with Maven.
3. If the build fails:

   * Do NOT deploy.
   * Report the build failure and relevant error.
4. If the build succeeds:

   * Copy the newly built `MonsterMazeStandalone.jar` to the correct test server's `plugins` directory.
   * Replace the previous MonsterMazeStandalone JAR only.
   * Do NOT modify or delete unrelated plugins.
5. Restart the corresponding test server.
6. Inspect the server console/log output.
7. Confirm whether the plugin loaded successfully.
8. Report:

   * Build result
   * Deployment result
   * Server startup result
   * Any startup errors
9. Actual gameplay behaviour must still be verified by the user unless the agent has an available way to observe the game directly.

Deployment commands should use these mappings:

1.8:

`1.8/MonsterMazeStandalone/target/MonsterMazeStandalone.jar`

->

`C:\monstermaze_test\plugins\MonsterMazeStandalone.jar`

1.21:

`1.21/MonsterMazeStandalone/target/MonsterMazeStandalone.jar`

->

`C:\monstermaze21\plugins\MonsterMazeStandalone.jar`

Do not assume that a successful Maven build means the server successfully loaded the plugin.

Do not claim gameplay was tested merely because the server started.

If the server is already running and cannot safely be restarted, do not kill it without first determining whether doing so is appropriate. Report the limitation instead.

### Cross-Version Changes

When implementing a gameplay/mechanics change in one version, consider whether the same mechanic should also exist in the other version.

Do not blindly copy implementation code between versions because the Bukkit/Spigot/Paper APIs and Minecraft internals differ.

Instead:

1. Identify the gameplay behaviour being changed.
2. Determine whether the behaviour should exist in both versions.
3. Implement the equivalent behaviour using the appropriate API for each version.
4. Update `docs/cross-version.md` if the behaviour differs intentionally.
5. Build both versions when practical.
6. Test the affected version(s).

### Safety

Never:

* Deploy 1.8 code to the 1.21 server.
* Deploy 1.21 code to the 1.8 server.
* Modify the other version merely because a similarly named class exists there.
* Claim a server test succeeded without checking the server startup/log output.
* Delete or overwrite unrelated plugins.

## Testing

Whenever possible, distinguish between:

* Source-level confidence
* Build verification
* Static/code analysis
* Actual in-game testing
* Behaviour confirmed against original Mineplex evidence

Do not describe untested behaviour as confirmed.

## Cross-Version Changes

When a mechanic is changed in one implementation, consider whether the corresponding implementation in the other version needs:

* The same change
* An adapted equivalent
* No change because the behaviour is version-specific

If the other implementation is intentionally left unchanged, document why when the reason is not obvious.

## Git

Do not reset, revert, checkout, or discard existing user changes without explicit permission.

Do not use destructive Git commands to "clean up" the repository.

Do not automatically commit major changes unless explicitly requested.

Before substantial work, inspect:

```
git status
git diff
```

After substantial work, report:

* Files changed
* Behaviour changed
* Build result
* Tests performed
* Whether the other Minecraft version may require corresponding work

## Multi-Agent Collaboration

Different AI agents may work on this repository at different times, including remote/cloud agents and local agents.

Git history and the filesystem are the shared source of truth between agents.

Do not assume that the previous agent's conversation, summary, or final response accurately describes the current state of the project.

Git history is also part of the project's development record. Substantial commits should make it possible for a later agent to understand:

* What changed
* Why it changed
* Which Minecraft version was affected
* Whether the equivalent behaviour was investigated in the other version
* Whether the project built successfully
* Whether deployment/startup was verified
* Whether actual gameplay testing remains outstanding

Before continuing substantial work, inspect recent history:

```
git log --oneline --decorate -10
```

Also inspect:

```
git status
git diff
```

When continuing work started by another agent:

1. Inspect the current working tree.
2. Read `AGENTS.md`.
3. Read relevant project documentation.
4. Inspect recent Git history.
5. Inspect existing diffs.
6. Continue from the actual current code rather than assuming the previous agent completed its claimed work.

Do not assume that a recent commit represents fully tested behaviour. Inspect the commit and current working tree before relying on it.

When making a substantial change, use a clear commit message describing the actual change rather than generic messages such as "updates" or "fixes".

Do not automatically commit changes unless explicitly requested by the user or unless the agent's configured workflow specifically requires a commit.

## Important Principle

The ultimate goal is not merely to make the plugin compile.

The goal is to recreate Monster Maze's gameplay and behaviour accurately across both supported Minecraft versions while preserving the ability to understand, test, and port mechanics between them.
