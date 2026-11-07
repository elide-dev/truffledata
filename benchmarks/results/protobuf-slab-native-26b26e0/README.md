# Native slab decoder restructure (Task A2b), on top of `26b26e0` (2026-11-07)

Task A2b of `docs/superpowers/plans/2026-11-07-protobuf-sota.md` §6 (the restructure of Task A2's
decoder, `protobuf-buffa/native/slab.rs`): the production decoder measured against the SOTA
probe's `hand_slab` (a per-schema decoder writing the production format) and `fast2` (the
table-driven ceiling with upb's register discipline), and against the Task A2 decoder as committed
at `ce7399f` (`slab_prev`), all in one run on one worker.

**All timings are advisory.** One shared BuildBuddy RBE worker (Firecracker VM reporting
"AMD EPYC"), one run, best of 7 rounds of a tight loop, no confidence intervals. Compare rows
within `rust.txt`, not across directories.

## Files

- `probe.rs`: the probe as run: `benchmarks/results/protobuf-sota-probe-ce7399f/probe.rs` plus a
  `slab_prev` variant (the Task A2 `slab.rs` copied in as `slab_prev.rs`, after checking that both
  decoders write the same bytes for every fixture message). It was copied into
  `protobuf-buffa/native/` with `slab_prev.rs` temporarily (and `#![feature(explicit_tail_calls)]`
  in `lib.rs`, which the SOTA probe's `fast_tail` variant needs), run through
  `//protobuf-buffa/native:test` (`--test_arg=probe::probe --test_arg=--nocapture`) and removed
  before the commit.
- `rust.txt`: the measurement lines; `rust-raw.log`: the test log. Invocation:
  <https://app.buildbuddy.io/invocation/cd2e0d13-536d-4169-9057-214dafa8b88c>.
- `exploration.txt`: three earlier runs of the intermediate shapes (different workers; compare
  within a run only), with their invocations.

## Gate (plan §6 Task A2b; same worker; ns per message, MB/s of wire)

| Shape | `slab` (A2b) | `slab_prev` (A2) | `hand_slab` | `fast2` | `slab` / `hand_slab` | `slab` / `fast2` | Gate | Result |
|---|---:|---:|---:|---:|---:|---:|---|---|
| small | 182.4 | 230.6 | 104.5 | 134.9 | 1.75 × | 1.35 × | ≤ 1.5 × `hand_slab` | **miss** |
| holdout | 552.5 | 649.3 | 302.5 | 392.8 | 1.83 × | 1.41 × | ≤ 1.5 × `hand_slab` | **miss** |
| large | 1,052 MB/s | 736 MB/s | 1,765 MB/s | 1,263 MB/s | 0.60 × | 0.83 × | ≥ 1.2 GB/s | **miss** |

Stop rule (plan §6): after one measured iteration, `slab` is still above 1.25 × `fast2` on small
(1.35 ×, 182 > 175 ns), so the restructure ships as measured and does not escalate. Against Task
A2 on the same worker it is 0.79 × (small), 0.85 × (holdout) and 1.43 × the throughput (large).
`slab_small` (fixed scratch) measures like `slab`. Slab bytes are unchanged: message 1 is 448 B
(3.67 ×) as before. The SOTA probe's `hand_slab`/`fast*` variants write 440 B for message 1, 8 B
less than the production format, because they are sketches that differ in one detail of
placement; they do not change the comparison.

Against the original Task A2 hypotheses (`benchmarks/results/protobuf-slab-native-d3f778c/`):
small 2.03 × `hand_ascii` (89.7 ns; target ≤ 1.3 ×), 0.39 × `view` (466.5 ns; target ≤ 0.3 ×);
holdout 1.83 × `hand_slab` (the corrected baseline; A2's "3.92 × `hand_novf`" compared with a
decoder that validates nothing); large 1.05 GB/s (target ≥ 1.5 GB/s, superseded by ≥ 1.2 GB/s).

## What changed and what each step measured

The intermediate shapes, each measured against `slab_prev` and the hand variants in one run
(`exploration.txt`; arm64 local numbers in the ledger are indicative only):

| Step | small vs A2 | holdout vs A2 | large vs A2 | Note |
|---|---:|---:|---:|---|
| Explicit record stack, 16-byte `Entry`, one-byte tag table, inline scalar/string/message handlers, short-ASCII strings inline, cold paths out of line (run 1) | 0.81 × | 0.85 × | 1.30 × | |
| Pointer cursors and body end, no wire-order prediction (run 2, `slab_v1`) | 0.81 × | 0.89 × | 1.38 × | x86-64 kept spilling the position, end and presence word |
| Plain fields in a separate out-of-line loop (run 2, `slab`) | 0.92 × | 1.05 × | 1.25 × | slower on both architectures; dropped |
| `become` tail-called handlers (run 3, `slab`) | 0.86 × | 0.91 × | 1.28 × | 3 % slower than the pointer loop on the same worker; dropped (the SOTA probe measured 14 %) |
| 64-byte frame (u32 offsets and marks), the shipped shape (final run) | 0.79 × | 0.85 × | 1.43 × | |

Also measured: removing UTF-8 validation entirely (local ablation) takes holdout from 443 to
365 ns, and record zeroing costs nothing measurable; the remaining gap to `fast2` is per-record
work `fast2` does not do (error offsets, `NEEDS_CAPACITY` accounting with frames for records that
do not fit, merge payloads, unknown-field marks, map keys, implicit-presence bit clears) and
x86-64 register pressure in one loop that carries all of it. A generated-style prototype (run 1,
`e3_proto`, byte-identical output for the fixture, bailing out on anything rare) measured 0.65 ×
`slab` small on x86-64 but only 0.85 × on arm64; E3 is withdrawn by the plan and was not pursued.

## Caveats

- Same as `protobuf-probe-fecb39f/README.md` and `protobuf-sota-probe-ce7399f/README.md`: the
  hand, table and `fast*` decoders are sketches; `slab` is the production decoder with full
  semantics, owning payload copies, exact capacity accounting and error offsets.
- The `slab` loop reuses one output buffer and includes no Java owner, pinning or transition.
