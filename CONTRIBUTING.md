# Contributing to Monster Maze

Monster Maze is an active reverse-engineering and recreation project. The repository is designed to support rapid iteration without losing the ability to understand, reproduce, or recover earlier work.

## Before changing code

1. Read `AGENTS.md`.
2. Inspect the current implementation rather than relying on a previous agent's description.
3. Inspect recent Git history and the working tree when working locally.
4. Identify the authoritative source file(s) and any generated outputs.
5. Check the corresponding implementation in the other Minecraft version when the behaviour is shared.
6. Check recovered Mineplex references or recorded evidence when they can answer the question.

Do not turn an investigation into a code change unless the task explicitly asks for changes.

## Change categories

Use these prefixes for commits:

- `feat:` — new functionality
- `fix:` — correction of existing incorrect behaviour
- `perf:` — performance improvement without intended gameplay change
- `refactor:` — structural/code-quality change without intended behaviour change
- `test:` — tests or test infrastructure
- `docs:` — documentation only
- `ops:` — deployment, hosting or operational tooling
- `build:` — packaging, dependency or build-system changes
- `chore:` — maintenance that does not fit the categories above

Prefer one logical change per commit. Related files can and should share a commit when they form one coherent change.

## Verification language

Be precise about what has been established:

- **Source verified** — the implementation was inspected and the behaviour is established from code.
- **Build verified** — the affected project compiled successfully.
- **Test verified** — automated tests/checks passed.
- **Server startup verified** — the plugin started successfully on the mapped test server.
- **Gameplay verified** — behaviour was actually observed in Minecraft.
- **Mineplex parity verified** — behaviour is supported by recovered original/reference evidence.
- **UNCLEAR** — the available evidence is insufficient.

Do not call something a bug merely because it is unexpected. Establish the cause or clearly label it as a hypothesis/risk.

## Cross-version discipline

Minecraft 1.8.8 and 1.21.x have different APIs and server internals. Never force source-level symmetry between them.

For shared gameplay changes:

1. Define the gameplay behaviour.
2. Inspect both implementations.
3. Determine whether both should change.
4. Implement an appropriate version-specific solution.
5. Build both versions when practical.
6. Document intentional differences when they are not obvious.

## Generated files

Do not manually edit generated build/release output when the source or packaging script is the authoritative input.

Examples include Maven `target/` output and generated Solo distribution staging directories.

If a generated artifact is wrong, fix the source or generator and regenerate it.

## Safe Git workflow

The repository's history is part of its recovery mechanism.

- Never force-push unless explicitly requested.
- Never reset or discard user work without explicit permission.
- Never rewrite published history merely to make it look cleaner.
- Prefer short-lived branches for substantial changes.
- Keep `main` as the canonical branch.
- Commit completed, verified work rather than half-finished experiments.
- Before a risky structural change, create a recovery point first.
- Never combine unrelated cleanup with a gameplay change.

A change should be easy to revert by reverting its commit, without requiring a history rewrite.

## Testing expectations

For 1.8:

```text
cd 1.8/MonsterMazeStandalone
mvn package
```

For 1.21:

```text
cd 1.21/MonsterMazeStandalone
mvn package
```

Do not claim gameplay verification from a successful compilation. Do not claim server startup verification without checking the server.

Packaging scripts should validate the files they publish and should not silently ignore failures.

## Deployment boundaries

Never deploy the wrong Minecraft implementation to a test or production server.

| Implementation | Local test server |
| --- | --- |
| `1.8/MonsterMazeStandalone` | `C:\monstermaze_test` |
| `1.21/MonsterMazeStandalone` | `C:\monstermaze21` |

Only replace the intended MonsterMaze plugin when deploying locally. Preserve unrelated plugins and runtime state.

## Documentation

When a change affects architecture, release procedures, cross-version behaviour, or a decision that future agents might reasonably question, update the relevant documentation in the same logical change.
