# SDD ledger — plan: docs/superpowers/plans/2026-11-07-protobuf-performance.md

Decisions (user, 2026-11-07): every proposed default of §10 (recorded at the end of the plan).
Ordering ruling (coordinator): Task B2's core `Accessor` lands before B1 with implementations for the existing backends; `SlabRow` adopts it in B1. Shared with the tape plan: `TapeSink` events, typed slot kinds, owner liveness, `td_tape_view`, `TapeKey` hashing (tape plan §8).

