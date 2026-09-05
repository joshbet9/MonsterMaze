# Architecture Decisions

This directory records decisions that should remain stable unless there is new evidence or an explicit architectural change.

These are intentionally short. The purpose is to preserve context, not to replace the implementation documentation.

## Decision format

Each decision should record:

- **Status** — accepted, superseded, or rejected.
- **Context** — the problem that prompted the decision.
- **Decision** — what the project chose to do.
- **Consequences** — important trade-offs.
- **Evidence** — tests, measurements, references or observations supporting it.

## Current decisions

### 001 — Separate Minecraft server Machines

**Status:** Accepted

The public 1.8 and 1.21 servers run as separate Fly Machines. They should not be casually recombined for convenience or cost reduction; the architecture exists to isolate the two server workloads and preserve performance.

### 002 — Fly compute is ephemeral

**Status:** Accepted

Fly server filesystems are deployment/runtime state, not the authoritative persistent store. Persistent competitive/backend data belongs outside the ephemeral Minecraft Machines.

### 003 — Git history is a recovery mechanism

**Status:** Accepted

Changes should be additive and revertible. Avoid destructive history rewriting, force-pushes and broad cleanup commits. Before risky structural work, establish a recoverable commit/branch point.

### 004 — Behavioural parity, not source parity

**Status:** Accepted

Minecraft 1.8.8 and 1.21.x are separate codebases. Shared gameplay behaviour should be equivalent where practical, but version-specific APIs and internals should remain version-specific.

### 005 — Evidence before gameplay changes

**Status:** Accepted

Recovered Mineplex source, recorded gameplay, logs and reproducible observations take precedence over assumptions. Uncertain behaviour should remain explicitly uncertain until evidence establishes it.

### 006 — Release artifacts are generated outputs

**Status:** Accepted

Player packages should be produced from canonical source and packaging scripts. Generated distribution files are not an independent source of truth.

### 007 — Discord webhook configuration is an intentional project choice

**Status:** Accepted

The project intentionally keeps its current Discord webhook configuration approach. It is not treated as a release blocker or an accidental secret-management failure under the project's current threat model. This decision should only be revisited if the webhook's role or access scope changes.
