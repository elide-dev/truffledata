# Myna Truffle cells behind a zero-overhead flag — WS12, `d5c0f39` (2026-11-09)

Plan ledger: `docs/superpowers/plans/2026-11-08-beat-stock-svm-ledger.md`, Sam's ruling on the WS11
re-probe ("retain cells support behind a flag … zero overhead when off") and the entries under
"WS12 (Myna cells flag worker)". Mechanism and usage: `docs/BUFFA.md`, "Myna Truffle cells".
Earlier cell evidence: `../protobuf-cells-probe-9855c83/`, `../protobuf-cells-probe-reprobe-2b88aa1/`.

**Change.** `-Dtruffledata.buffa.cells=true` routes every Buffa slab producer call through Myna
cells generated from seams derived (in the build) from the production `buffa.seam`. Native Image:
the property is read at image build time by `BuffaFeature`, which registers the cells backend
factory instead of the static one; JVM: a `static final` read once selects the backend in
`BuffaWire.open`. The route lives in a separate artifact (`//protobuf-buffa/cells`,
`truffledata-protobuf-buffa-cells`); the production seam, bindings, fingerprint and natives are
unchanged. The decode path (`BuffaPlan` → `Backend.slab`) is textually unchanged; the static
backend gained only package-private hooks the cells backend calls.

**Images** (Linux x86-64 RBE, SDK `25.5.5-elide.27`, `-O2`, `-march=compatibility`, serial GC, no
PGO; `binary.sha256`). Before: `75cb0c0` (the worktree base; invocation `e0d71981`, remote-cache
hit; consumer `37259773…a900`, the WS2 after-image; preserved under ignored
`build/before-75cb0c0/`). After `d5c0f39` (gates invocation
[`8ead94b8`](https://app.buildbuddy.io/invocation/8ead94b8-0207-4bb7-b9e3-1ce02c685e7a), images
materialized in `2de445d8`):

| Image | Configuration | sha256 | Bytes |
|---|---|---|---:|
| `truffledata-consumer` | before (`75cb0c0`) | `37259773…a900` | 44,699,760 |
| `truffledata-consumer` | after, flag off (production configuration) | `2cca807c…87fd` | 44,699,760 |
| `truffledata-consumer-cells-off` | after, cells artifact on the class path, flag off | `f61b0dac…0799` | 44,699,760 |
| `truffledata-consumer-cells` | after, flag on | `7955a06c…fa2e` | 44,765,296 |

Full hashes (and the profile images') are in `binary.sha256`.

## Zero overhead when off: code shape (`aot/`)

`tools/bench/aot_symbols.py` over the four symbol-bearing profile images (same local llvm tooling):

- **Every budgeted symbol is identical** (instructions, calls, frame, stack loads/stores, size and
  call targets) in all four images, including the flag-on image: `aot/budgeted-comparison.md`; the
  budget run passes on each (`aot/*.md`), and `consumer_aot_symbols` passed in `8ead94b8`.
- **The decode path is identical with the flag off.** `BuffaPlan_decode` 289 instructions / 26
  calls, `BuffaPlan_decodeSlab` 36/2 and 200/19, `SvmBackend_slab` 461/26 and 444/22,
  `SvmBackend_writeSlab` 351/23, `BuffaWire_open` 87/3 — the same in the before, flag-off and
  artifact-present flag-off images. No `Cells*`, `NativeCell`, `Borrow`, `NativePointer` or
  coercion symbol exists in any flag-off image.
- **The whole flag-off symbol table is unchanged.** Before vs after (production configuration):
  47,236 defined symbols, every name and size identical except 12 JDK hidden-class names whose
  address-derived suffix differs (`TypeSwitch0x…`; same names otherwise and same sizes,
  `aot/ts-*.txt`); the image is the same size. The binaries differ only in such metadata (line and
  bytecode-index tables of the edited classes, hidden-class names).
- **Artifact present, flag off:** also 44,699,760 B with no cells code; four JDK
  `ImmutableCollections` iterator/`toArray` bodies differ in size (−32 to +28 B,
  `aot/cells-off-artifact-vs-off.nm.diff`), consistent with the jar's Native Image metadata
  changing which collection shapes the build-time heap holds; none is on a Buffa path.
- **Flag on:** `CellsSvmBackend_slab` 611/38 and 502/29, `CellsSvmBackend_writeSlab` 356/26 replace
  the `SvmBackend` bodies; `BuffaPlan_decode` 282/27; +65,536 B image.

## Timing pairs: not run

The paired `consumer_measure` runs (before vs flag-off, before vs flag-on) were not run; the
tranche was settled for release first (lead, 2026-11-09). With the flag off the change makes no
timing claim and needs none: the production image has the same size, the same symbol table and
identical bodies for every Buffa symbol (above). The flag-on route is opt-in, ships as a separate
artifact, and makes **no performance claim**. Its earlier probes
(`../protobuf-cells-probe-*`) and the 368 B/message JVM allocation (elide-dev/myna#85) indicate a
cost, not a gain.

## Correctness gates (`gates/`)

All pass in `8ead94b8` (flag off, unchanged targets): `//protobuf-runtime:test`, `:slab_replay`,
`//protobuf-buffa/native:test`, `//protobuf-buffa/tests:jvm`, `:jvm_non_strict`,
`:jvm_no_native_access`, `:slab_producers`, `//tests:test`, `//tests:allocation`,
`consumer_smoke`, `consumer_native_smoke` (132 cells), `native_smoke`, `consumer_aot_symbols`,
`//bazel:myna_contract` (with the new derived-descriptor check), `//tools/release:test` (the new
publication's POM/Gradle metadata and contents). Cells route:
`//protobuf-buffa/cells/tests:jvm` (flag on: the shared `AcceptanceTest`, 13/13, route check, 88,716
byte/error parity outcomes against the FFM backend), `:jvm_off` (artifact present, flag off: route
stays FFM, same parity), `:native_test` (Native Image built with the flag and
`-H:+TruffleCheckBlockListMethods`/`NeverPartOfCompilation`: route check, the shared native
acceptance, 88,716 parity outcomes against the static backend it wraps),
`consumer_native_smoke_cells` (the 132-cell smoke matrix on the cells route; its checksums are
validated against the before image in the flag-on pair below).
