# CPU Opponent Configuration v2

## Configuration shape

The server configuration should describe a CPU opponent as a player, not as a machine-learning model.

Example:

    cpu-opponents:
      enabled: true
      model: current
      defaults:
        difficulty: 0.50
        attributes:
          speed: 0.60
          agility: 0.50
          handling: 0.50
          reactions: 0.50
          planning: 0.50
          recovery: 0.50
          ability: 0.50
        tendencies:
          risk: 0.35
          aggression: 0.50
          monster-contact: 0.25
          ability-conservation: 0.50
          jump: 0.50
          route: 0.50
          pad-contest: 0.50
          strafe: 0.25
          variance: 0.00

      opponents:
        - id: maze-bot-1
          name: MazeBot
          kit: JUMPER
          difficulty: 0.75
          seed: 1001

        - id: rush-bot
          name: Rush
          kit: REPULSOR
          difficulty: 1.00
          seed: 1002
          tendencies:
            risk: 0.90
            aggression: 0.85
            route: 0.90
            pad-contest: 0.90

## Configuration semantics

Difficulty is a convenient preset/input for the policy.

Attributes are independent capability controls.

Tendencies are independent behavioural preferences.

Explicit per-opponent values override defaults.

A profile value always remains in [0,1].

The profile must never alter:
- gravity;
- movement physics;
- monster damage;
- knockback;
- kit mechanics;
- pad geometry;
- timers;
- stage progression.

## Why model is separate

The server should not expose neural-network details in its gameplay configuration.

A server operator should be able to select the promoted model version, but should not need to know layer sizes, feature order or training framework.

The policy artifact carries its own schema and compatibility metadata.

## Deterministic seeds

Every CPU receives an explicit seed.

The seed controls intentional behavioural variance and stochastic policy choices.

The seed does not alter game mechanics.

Two CPUs with identical:
- model version;
- game state;
- profile;
- seed

must make the same policy decision.

## Difficulty presets

Presets are named convenience configurations.

Recommended initial presets:

- EASY
- NORMAL
- HARD
- EXPERT

The exact numeric values should be tuned from measured gameplay, not invented as arbitrary handicaps.

Presets should expand to the same underlying attributes/tendencies schema.

## Kit selection

The kit remains an authoritative Monster Maze gameplay property.

The AI receives the current kit as part of its observation/context and can therefore learn kit-specific behaviour using the same policy.

No separate neural model is required for each kit.

## Human-player parity

CPU configuration should use the same kit IDs and game mode IDs as normal players.

The CPU should enter the normal game state machine rather than having a second CPU-specific version of pad progression, damage or stage handling.

## Account/backend separation

CPU identifiers are gameplay identities only.

They must not be sent to player PB, leaderboard or personal-account systems as though they were human accounts.

A future CPU leaderboard can explicitly opt into a separate namespace.
