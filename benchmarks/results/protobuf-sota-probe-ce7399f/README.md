# Protobuf state-of-the-art probes at `ce7399f` (2026-11-07)

Evidence for `docs/superpowers/plans/2026-11-07-protobuf-sota.md`: which decoder *shape* beats
stock protobuf-java by far on each platform, measured over the consumer harness fixture
(32 `google.protobuf.Type` messages, byte-identical to `ConsumerComparison`'s generator; `small`
150 B, `holdout` 510 B with supplementary-Unicode names, `large` 52 KB). Every probe is temporary
code, retained here as run and removed from the tree before the commit.

**All timings are advisory.** Shared BuildBuddy RBE workers (Firecracker VMs reporting
"AMD EPYC"), one run each, best of 7 rounds of a tight loop (Rust) or best of 10 × three warm
passes (Java). The four files below ran on **four different workers**; compare rows within one
file. The Rust file carries the production decoder and the earlier probe's variants in the same
run, so its ratios are same-worker. No confidence intervals; these numbers support hypotheses for
the comparison harness, not speedup claims.

## Files

- `probe.rs`: the retained `protobuf-slab-native-d3f778c/probe.rs` plus four variants, included
  from `lib.rs` as a `#[cfg(test)]` module (the crate root temporarily carried
  `#![cfg_attr(test, feature(explicit_tail_calls))]`) and run through
  `//protobuf-buffa/native:test --test_arg=probe::probe --test_arg=--nocapture`:
  - `hand_slab`: a hand-written per-schema decoder (the `hand_*` shape) that writes the
    **production `TDSLAB1` bytes**: 8-byte-aligned zeroed records with handle and presence
    words and 4-byte slots, 8/12-byte string headers with copied payloads and UTF-16 counts,
    `count · 0 · elements` lists, header words. Validation is the production
    `unicode::validate_utf16_units` (ASCII words, then the scalar DFA).
  - `fast`: table-driven over 16-byte packed field entries (`tag, slot, child, kind, ordinal,
    next, flags`) with a predicted next entry and a 32-entry number lookup, an **explicit record
    stack** (no recursion), slice-offset state, lists flushed from a pending scratch at close.
  - `fast2`: `fast` with upb's state discipline: `*const u8` cursor and limit, record base
    pointer, hasbits in a local until close, one `Ctx` pointer for everything cold, `put_string`
    returning one word, frames in a fixed array.
  - `fast_tail`: `fast2`'s handlers as separate functions over the upb parameter set
    `(ctx, ptr, limit, base, hasbits, table, entry)` chained with Rust's `become` (guaranteed
    tail calls; a 10M-deep chain ran in constant stack on the pinned nightly).
  - All four write byte-identical slabs (`sota_size` lines); they skip unknown fields rather
    than preserving them and implement no merges, oneofs or maps (none occur in the fixture).
  `rust.txt` is the measurement lines; `rust-raw.log` the trimmed Bazel log. Invocation:
  <https://app.buildbuddy.io/invocation/c4215a0d-b751-4abe-a9fb-4d6be9fc2102>.
- `SotaProbe.java`: pure-Java decoders writing the same slab bytes into a `byte[]` through
  little-endian `VarHandle`s, next to `Type.parseFrom`: `java_gen` (generated shape: constant
  tags, slots and presence bits, one method per type) and `java_table` (table-driven with an
  explicit record stack, the runtime-plan `SlabCodec` shape). Run three ways:
  - `jvm-local-m-series.txt`: this host (Apple M-series, HotSpot 25.0.4.1.1 with the Graal JIT).
  - `jvm-rbe-shard1.txt`: a temporary `java_test` on an RBE worker without a CPU reservation,
    invocation <https://app.buildbuddy.io/invocation/34c9e3f1-d64d-4ebf-8e42-1c8e4f0bb38f>
    (`stock_parse` 1.9× slower than the `fecb39f` RBE JVM probe: a loaded worker).
  - `jvm-rbe-shard2.txt`: the same with `EstimatedCPU=4` and `-Xmx1g`, invocation
    <https://app.buildbuddy.io/invocation/b962e632-ef7e-430d-81b7-f96fb60145a6>; `stock_parse`
    at 221 ns is within 16% of the `fecb39f` shard, so this one is the x86 reference.
  - `svm.txt`: the same main as a `native_image` (`-O2`, `-march=compatibility`, serial GC, no
    profiles, no Truffle), run on an RBE worker, invocation
    <https://app.buildbuddy.io/invocation/7d2908b6-805e-491a-bc9d-4b97f677b883>.
- `upb.txt`: the retained `protobuf-probe-fecb39f/proto_probe.cc` rebuilt as a
  `cc_test` with `--@protobuf//upb:fasttable_enabled=true` (plan Q8). See "upb fast table" below.

## Headline rows (ns per message; MB/s of wire input)

Native, one worker (`rust.txt`):

| Variant | small | holdout | large | small ÷ `hand_slab` |
|---|---:|---:|---:|---:|
| `scan_flat` | 14.8 · 10,095 | 42.7 · 11,952 | 3,323 · 15,595 | 0.15 |
| ABI 9 `view` | 514.1 · 292 | 1,530.5 · 333 | 166,772 · 311 | 5.24 |
| `slab` (production A2) | 226.0 · 663 | 636.2 · 802 | 67,821 · 764 | 2.30 |
| `fast` | 177.3 · 846 | 509.8 · 1,000 | 53,206 · 974 | 1.81 |
| `fast_tail` | 159.4 · 940 | 451.0 · 1,131 | 42,759 · 1,212 | 1.62 |
| `fast2` | 140.3 · 1,069 | 391.7 · 1,302 | 38,910 · 1,332 | 1.43 |
| `hand_slab` | 98.1 · 1,529 | 281.4 · 1,813 | 26,969 · 1,922 | 1.00 |
| `hand_ascii` (spans, old validator) | 91.9 · 1,631 | 550.1 · 927 | 21,211 · 2,443 | 0.94 |
| `hand_novf` | 66.0 · 2,271 | 175.0 · 2,915 | 19,325 · 2,682 | 0.67 |

Java (ratios are within each file):

| Run | `stock_parse` | `java_gen` | `java_table` | gen ÷ stock | table ÷ gen |
|---|---:|---:|---:|---:|---:|
| JVM, local M-series, small | 187–201 ns | 94 | 166–172 | 0.49 | 1.8 |
| JVM, local M-series, large | 1,169–1,195 MB/s | 1,675–1,746 | 731–757 | 0.70 (time) | 2.3 |
| SVM, RBE worker, small | 743 ns | 397 | 586 | 0.53 | 1.47 |
| SVM, RBE worker, holdout | 2,336 ns | 1,118 | 1,477 | 0.48 | 1.32 |
| SVM, RBE worker, large | 272 MB/s | 423 | 311 | 0.64 (time) | 1.36 |
| JVM, RBE shard 1 (no CPU reservation), small | 360–369 ns | 289–296 | 490–508 | 0.80 | 1.7 |
| JVM, RBE shard 2 (4 vCPUs reserved), small | 221 ns | 205 | 330 | 0.93 | 1.6 |
| JVM, RBE shard 2, holdout | 916 ns | 602 | 1,028 | 0.66 | 1.7 |
| JVM, RBE shard 2, large | 864 MB/s | 736 | 442 | 1.17 (time) | 1.7 |

## Reading

- **The slab format is not the cost.** `hand_slab` is 7% slower than `hand_ascii` on small
  messages and 27% on large ones (payload copies and record zeroing: 6.4 GB/s of writes at
  1.9 GB/s of input). The production decoder's 2.3× gap to `hand_slab` is interpretation.
- **Table-driven interpretation floors at about 1.4× the per-schema decoder** on this schema
  (`fast2`), with the tail-call form 14% slower than the loop and the offset-based loop 26%
  slower. Changing only the state representation (`fast` → `fast2`) removed 37 ns of 177: this
  is the A2 profile's register-pressure finding, measured. The restructure in flight should land
  near `fast2` (≈ 0.62× the production time on every shape) if it adopts that discipline fully;
  it cannot reach 1.25× of the hand decoder, and no interpreter can on 2-byte fields.
- **The holdout was mis-baselined in A2.** Against `hand_slab`, which uses the production
  validator, the holdout ratio is 2.26× (same as small), not 3.92×; the production scalar DFA
  validator is itself 2× faster than the `fecb39f` probe's `utf16_length` on Unicode names
  (281 vs 550 ns).
- **Generated Java's lead on the JIT is host-dependent and the slab writer is the suspect.**
  On the M-series host it is 0.49× stock small and 0.70× large writing production bytes; on two
  x86-64 RBE shards it is 0.80–0.93× small, 0.66–0.71× holdout and 1.13–1.17× large. The
  `fecb39f` x86 shard measured the span form (`long[]`, no copies, no `VarHandle`) at 0.47×, so
  on x86 the slab-writing form is ≈ 2.3× the span form where Rust's format tax is 7%: the
  writer (`VarHandle` stores, `arraycopy` per short string, `Arrays.fill` per record) needs an
  x86 diagnosis (plan Task A3 Step 0). Table-driven Java pays a 1.6–1.8× interpretation tax on
  every host.
- **AOT without profiles costs the generated Java decoder 4.2×** (397 vs 94 ns); stock loses
  3.7–3.9×. Generated Java on SVM is 0.53× stock but 2× the expected native fast-table cell
  (≈ 140 + 60 ns), so Rust stays the SVM producer (plan Q2) and generated Java is the no-native
  fallback there. The pure-Java path on SVM needs runtime compilation (a Truffle decoder root,
  plan Task A4) to reach the JIT figure.

## upb fast table

`upb.txt` (invocation <https://app.buildbuddy.io/invocation/8bd59295-a2c0-4e88-97a9-03f69c16f3e2>)
is the `fecb39f` C++ probe (`proto_probe.cc`, copied here as run) rebuilt as a `cc_test` with
`--@protobuf//upb:fasttable_enabled=true`, which Bazel accepted:

| Variant | small | holdout | large |
|---|---:|---:|---:|
| `scan_flat` | 12.9 ns · 11,595 MB/s | 38.6 · 13,210 | 3,221 · 16,091 |
| protobuf C++ generated, reused message (`cpp_reuse`) | 165.7 · 905 | 475.9 · 1,072 | 43,562 · 1,190 |
| protobuf C++ arena (`cpp_arena`) | 238.9 · 628 | 702.6 · 726 | 119,047 · 435 |
| upb with `fasttable_enabled` (`upb`) | 208.9 · 718 | 609.0 · 838 | 63,277 · 819 |

This worker ran `scan_flat` 1.66 × faster than the `fecb39f` C++ worker (12.9 vs 21.4 ns) and
1.15 × faster than this cycle's Rust worker (14.8 ns). Against `fecb39f`, `cpp_reuse` improved
1.74 × (289 → 166 ns) and `upb` 1.75 × (366 → 209 ns): the upb-to-C++ ratio is unchanged
(1.26 × vs 1.27 ×), so **either the fast table did not engage in this build (the generated
mini-tables must carry fast-table entries and the decoder must be compiled with `UPB_FASTTABLE`;
neither was verified) or it makes no difference on this fixture**. Plan Q8 is recorded as
inconclusive; the design input (register discipline) was taken from the source, not from this
number. Calibrated through `scan_flat` to the Rust worker, upb ≈ 240 ns ≈ the production A2
decoder (226 ns), C++ generated ≈ 190 ns, against `fast2` 140 ns and `hand_slab` 98 ns.

## Caveats

- `hand_slab`, `fast*`, `java_gen` and `java_table` are sketches with full hot-path semantics
  but no unknown-field preservation, merges, oneofs or maps; the production decoder keeps those
  off the hot path (A2 profile: 60–70% in the field loop, 15–20% in record open/close), so the
  ratios bound it from below by a few percent.
- The JVM figures disagree across hosts (above); the x86 shard 2 is the reference for the
  slab-writing form and the `fecb39f` shard for the span form, and the gap between them is the
  open item, not noise (shards 1 and 2 agree on the ratios).
- `-march=compatibility` on the SVM image and no `-C target-cpu` on the Rust test: both are the
  repository's shipping configuration, not a tuned one.
- No cross-file comparison is valid except through ratios to a row present in both files.
