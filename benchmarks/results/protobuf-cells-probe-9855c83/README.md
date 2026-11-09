# Myna Truffle cells for the guest-facing slab decode — WS11 gate probe (2026-11-09)

Plan: `docs/superpowers/plans/2026-11-08-beat-stock-svm.md` §4 WS11 (gate: "a `benchmarks/cells`-style
probe calling one TD decode entry from a force-compiled root, cell vs current boundary, three forks;
adopt per entry point only with a measured gain"). Rulings in `2026-11-08-beat-stock-svm-ledger.md`
under "WS11 (Myna cells worker)".

**Source.** Production code is `3999a6a` (the `perf/beat-stock-svm` tip after WS5); `9855c83` adds
only benchmark code (`benchmarks/comparison/cells/`, `probes/CellProbe.java`, the
`cells_probe_measure` target, `@myna//:truffle`); `64a5450` changes only the runner's report and
GC logging. Probe image sha256 `25bbb2c9…6684` (`run*/binary.sha256`). Linux x86-64 RBE, SDK
`25.5.5-elide.27`, Myna 0.13.0, `-O2`, `-march=compatibility`, serial GC, no PGO, the consumer
images' flags plus `TRUFFLE_JIT_CHECKS`. The shipped `Buffa` bindings, seam fingerprint and natives
are unchanged: the probe seam (`cells/cells.seam`) is the production seam with `truffle_cells=true
truffle_backend=native truffle_transitions=direct`, generated into a benchmark-only package.

**Toolchain facts (build, not timing).**

- Myna 0.13.0 generates cells with direct transitions from this repo's seam: `BuffaCells.java` is
  byte-identical to the shipped `Buffa.java` except the ABI fingerprint (`5bd597bd…a01e52` →
  `4f3e7fec…d26516`; the `truffle` block in `seam.json` is the only change), plus
  `BuffaCellsTruffle.java` (one `NativeCell` per import, `BytesCoercion` for every `ptr<u8>`,
  `PointerCoercion` for `ptr<void>`, `IntegerCoercion` for integers) and a `native-image.properties`
  that initializes the cell classes at build time. "Direct" transitions emit no wrapper: the cell
  calls the ordinary `@CFunction` binding, and the pinned fork (graal#196) compiles that call from
  runtime-compiled code as a direct AOT-stub call. The image build's blocklist check
  (`-H:+TruffleCheckBlockListMethods`) accepts the transition and `PinnedObject.create/close` from
  compiled code.
- The first image build failed with exactly one blocklist violation: `Throwable.addSuppressed`,
  which javac emits for try-with-resources (`cac278cc`). The production `SvmBackend.slab` pins with
  try-with-resources, so making it partial-evaluation visible would need the same plain
  try/finally rewrite.

**Run.** One RBE worker per run (AMD EPYC, pinned to CPU 6, load 0.0 before), five routes in one
binary, one route per process, three fresh forks per route in a seeded interleaved order
(`cells_probe.py`): `boundary` (today's `BuffaPlan.decodeSlab(byte[])` behind its
`@TruffleBoundary`), `boundary-body` (the probe's copy of that body behind a boundary), `direct`
(the body partial-evaluation visible, `Buffa.decodeSlab` as a direct transition), `cell` (the same
body calling the generated `decodeSlab_Cell` adopted as a child node), `cell-interop` (the cell's
`NativeFunction` through a dispatched `InteropLibrary`). Fixture: the consumer harness's full
fixture (32 `google.protobuf.Type` messages, 149.9 B wire, 547.3 B slab, learned capacity 728 B),
heap `byte[]` input as a guest hands it over; every route's slabs equal the production slabs byte
for byte (checksum `3764254882730299103`), every sample observed compiled code, graph budget 10,000
and `TreatPerformanceWarningsAsErrors=all` unchanged. ns per decoded message (= per row, 32 per
operation including publication), median of fork medians (fork range); Java bytes per message.

## Run 1 (`a5fccacc`, `run1/`)

| Route | ns/row | fork range | vs boundary | Java B/row |
|---|---:|---|---:|---:|
| `boundary` | 855.4 | 850.3–869.1 | 1.000× | 832.2 |
| `boundary-body` | 861.0 | 851.9–866.8 | 1.007× | 832.2 |
| `direct` | 835.1 | 829.2–11,474.1 | 0.976× | 832.2 |
| `cell` | 849.4 | 849.0–867.8 | 0.993× | 1,000.2 |
| `cell-interop` | 867.5 | 861.3–868.5 | 1.014× | 1,000.2 |

## Run 2 (`d61cdde7`, `run2/`, fresh worker, same image)

| Route | ns/row | fork range | vs boundary | Java B/row |
|---|---:|---|---:|---:|
| `boundary` | 937.3 | 900.4–967.5 | 1.000× | 832.2 |
| `boundary-body` | 914.6 | 891.3–984.1 | 0.976× | 832.2 |
| `direct` | 889.0 | 887.6–5,341.6 | 0.948× | 832.2 |
| `cell` | 916.8 | 908.6–1,133.1 | 0.978× | 1,000.2 |
| `cell-interop` | 1,019.0 | 965.3–1,077.7 | 1.087× | 1,000.2 |

Run 2's worker is noisier than run 1's (the unchanged `boundary` route moves 855 → 937 ns, its own
fork range widens from 19 to 67 ns, and `boundary-body`, identical code behind the same boundary,
spans 891–984). Compare routes within one run only.

## Run 3 (`run3/`, fresh worker, same image, runner `64a5450` with `-XX:+PrintGC`)

| Route | ns/row | fork range | vs boundary | Java B/row |
|---|---:|---|---:|---:|
| `boundary` | 874.3 | 862.7–901.6 | 1.000× | 832.2 |
| `boundary-body` | 863.8 | 862.9–867.5 | 0.988× | 832.2 |
| `direct` | 862.8 | 853.4–873.7 | 0.987× | 832.2 |
| `cell` | 872.6 | 872.4–896.6 | 0.998× | 1,000.2 |
| `cell-interop` | 877.0 | 872.5–882.2 | 1.003× | 1,000.2 |

No `direct` fork stalled in run 3. Every process logs 29–42 incremental young collections of
2–6 ms (`run3/logs/*.stderr`, ~48 MB young generation per ~50 ms of decoding, the same for every
route), so GC is route-neutral here; the two earlier stalls ran without GC logging and remain
unexplained worker-side stalls of the first or second process of a run (the seeded order puts
`direct` fork 0 there every run).

## Graph of the calling root (same in every fork; `engine.CompilationStatisticDetails`, `run*/logs/*.stderr`)

| Route | AST nodes | Graal nodes after PE / final | code B | final `InvokeWithException` | final `ForeignCall` | final `Deoptimize` |
|---|---:|---|---:|---:|---:|---:|
| `boundary` | 2 | 52 / 1,467 | 5,129 | 32 | 3 | 32 |
| `boundary-body` | 2 | 51 / 1,466 | 5,001 | 32 | 3 | 32 |
| `direct` | 2 | 387 / 1,138 | 5,139 | 12 | 16 | 7 (+2 dynamic) |
| `cell` | 14 | 702 / 2,413 | 11,240 | 25 | 45 | 17 (+2 dynamic) |
| `cell-interop` | 7 | 715 / 2,820 | 12,600 | 32 | 51 | 26 (+3 dynamic) |

The boundary routes' 32 invokes are the fully unrolled 32-message loop (one boundary call per
message); after PE they are one invoke. `direct` partially evaluates the body (387 nodes after PE:
budget check, busy flag, allocation, two pins, the transition, extent checks, trim rule) and keeps
12 invokes and 16 foreign calls (the `takeOwnership` boundary, pin/unpin, allocation stubs and the
slow paths). The cell routes nearly double the PE graph and the code size (coercions, `Borrow`
objects, the `Object[]` argument, the wrapper's call target for `cell-interop`); the +168 B/row they
allocate is the argument array, the boxed integers and the pointer/borrow objects that are not
scalar-replaced across the borrow `finally` and the exception edges (Myna's own cells baseline
records 48 B per borrowing call for the same reason).

## Verdict

- **No measurable gain for a Myna cell on the guest-called decode entry.** `cell` is within
  `boundary`'s fork range in all three runs (0.993× / 0.978× / 0.998×, overlapping), allocates
  168 B/row more and doubles the compiled graph; `cell-interop` is never faster (1.014× / 1.087× /
  1.003×). The plan's kill rule applies to the cells themselves.
- **The boundary is not the cost.** `direct`, the mechanism a cell would carry (the publication body
  partial-evaluation visible, the transition compiled as a direct stub call), moves the cell by
  −2.4% / −5.2% / −1.3% at the medians with fork ranges that overlap `boundary` in every run (in
  runs 1 and 2 one of its forks stalls at 11.5 µs and 5.3 µs per row: all five samples of that
  process, no recompilation, no invalidation, no GC; in run 3 no fork stalls and the medians are
  11 ns apart inside a 39 ns `boundary` range). No gain is claimed. The cell's cost is the Java walk
  behind the `takeOwnership` boundary (P0.1: the verifier is ≈62% of the cell; WS2 ruling
  pending), which no cell or boundary removal touches; P0.1's ~6 ns transition and ~33 ns
  `BuffaPlan` plumbing bound what `direct` could ever recover on this cell (≈4%).
- The second probe entry the brief names (`JsonSession`/`JsonPlan` decode) was not probed: the gate
  says "then, if the probe pays", and it did not.
