# Native slab decoder probe (Task A2), on top of `d3f778c` (2026-11-07)

Task A2 Step 3 of `docs/superpowers/plans/2026-11-07-protobuf-performance.md`: the production
ABI 10 slab decoder (`protobuf-buffa/native/slab.rs`) measured against the retained decode probe
of `benchmarks/results/protobuf-probe-fecb39f/`, in the same run and on the same worker as the
probe's `view` and `hand` variants. The probe was copied back into `protobuf-buffa/native/`
temporarily, given a `slab` variant, run through `//protobuf-buffa/native:test`
(`--test_arg=probe::probe --test_arg=--nocapture`) and removed before the commit.

**All timings are advisory.** One shared BuildBuddy RBE worker (Firecracker VM reporting
"AMD EPYC"), one run, best of 7 rounds of a tight loop, no confidence intervals. This is not the
worker of the `fecb39f` probe, so compare rows within `rust.txt`, not across directories. The
ratios below support no speedup claim by themselves; they test the Task A2 hypotheses.

## Files

- `probe.rs`: the probe as run. Additions over `protobuf-probe-fecb39f/probe.rs`: `slab_program()`
  (the probe's `google.protobuf.Type` schema program plus its `TDL1` layout section),
  `slab` (`slab::decode`, the normal entry point, into one reused 16 MiB buffer; scratch may
  spill to the heap) and `slab_small` (`slab::decode_with` with fixed scratch and no spill, the
  small entry point's configuration, capacity 64 KiB), and `slab_size` (slab bytes for message 1
  and for all 32 messages). Everything else is unchanged.
- `rust.txt`: the measurement lines; `rust-raw.log`: the test log. Invocation:
  <https://app.buildbuddy.io/invocation/6e96eed9-d02e-4811-82b3-61df47fc5805>.

The measured tree is the Task A2 working tree on top of `d3f778c` (the slab decoder as
committed, with the probe module added).

## Hypotheses (same worker; ns per message, MB/s of wire)

| Hypothesis (plan Task A2 Step 3) | Measured | Ratio | Target | Result |
|---|---:|---:|---:|---|
| small: `slab` ≤ 1.3 × `hand_ascii` | 229.5 vs 89.2 ns | 2.57 × | ≤ 1.3 × | **miss** |
| small: `slab` ≤ 0.3 × `view` | 229.5 vs 470.4 ns | 0.49 × | ≤ 0.3 × | **miss** |
| holdout: `slab` ≤ 1.5 × `hand_novf` | 670.0 vs 171.0 ns | 3.92 × | ≤ 1.5 × | **miss** |
| large: ≥ 1.5 GB/s | 740 MB/s | 0.49 × target | ≥ 1,500 MB/s | **miss** |
| slab bytes for message 1 ≤ 4 × input | 448 B for 122 B | 3.67 × | ≤ 4 × | pass |

Other sizes: all 32 small messages 3.65 × input (max 3.86 ×), holdout 3.21 × (max 3.34 ×),
large 3.33 × (message 1: 160,608 B for 48,087 B, against 202,856 B for the probe's 8-byte-slot
flat slab and 380,324 B for the ABI 9 view). `slab_small` (fixed scratch) measures like `slab`
(227.6 and 659.5 ns): spilling is not on the path for these messages.

The size gate passes because of the Task A2 format change (ledger ruling): before it, message 1
was 536 B (4.39 ×).

## Reading

- The decoder is 2.0–2.4 × faster than the ABI 9 view on every shape (small 0.49 ×, holdout
  0.44 ×, large 0.42 × of `view`'s time) and writes a smaller result, but it is 2.6–3.9 × the
  hand-written decoders and below the probe's naive `table` decoder (178 / 546 ns /
  819 MB/s), which writes 8-byte words with spans into the input and implements none of the slab
  semantics (no payload copies, string headers, record zeroing, handles, presence rules,
  oneofs, merges, unknown spans or maps).
- Profiles (Instruments time profiler on an Apple M-series host, the same code locally:
  small 204 ns, large 863 MB/s after the last change) put 60–70 % of the time in the record
  body loop at roughly 100 instructions per field on the fast path, against about 15 in the
  hand decoder: one generic loop for every type keeps about fifteen values live across the calls
  for nested records and strings, so the predicted-tag check, the per-field descriptor loads and
  the slot/presence writes run with spilled operands. Per record, opening (bump allocation,
  zeroing, handle) and closing cost another 15–20 %. Unicode validation is not the holdout
  bottleneck: per-record cost is the same 33–36 ns on all three shapes.
- Changes that measurably helped (local, small/large): readers that return the position by
  value instead of through `&mut` (a store-forwarding chain per field), presence word 0 kept
  in a register, record zeroing and alignment padding without libc calls, lists built in one
  counting pass instead of one scan per list field, the string path out of line
  (268 → 204 ns, 580 → 863 MB/s). Inlining the record open/close into the message path and
  moving tag validation behind the wire-order prediction did not.
- Closing the remaining gap needs per-schema specialization (generated decoders, Task E3, or
  Task A3's generated Java decoder on the JVM) or an iterative decoder with an explicit record
  stack. These are recorded as open, not attempted here.

## Caveats

- Same as `protobuf-probe-fecb39f/README.md`: the hand and table decoders are sketches with
  spans into the input; the slab decoder is the production decoder with full semantics and
  owning payload copies.
- The `slab` loop reuses one output buffer and includes no Java owner, pinning or transition.
