# Protobuf decode-ceiling and competitor probes at `fecb39f` (2026-11-07)

Raw output of three temporary probes used to write
`docs/superpowers/plans/2026-11-07-protobuf-performance.md`. Each ran once on a shared
BuildBuddy RBE worker (Firecracker VM reporting "AMD EPYC", generation not exposed; the three
probes landed on **three different workers**). **All timings are advisory:** one worker, one
run, best-of-7 rounds (best-of-10 × 3 warm passes on the JVM) of a tight loop, no confidence
intervals, no cross-host comparability. Compare rows within one file; across files, calibrate
with the `scan_flat` row that the Rust and C++ probes share (21.4 ns on the C++ worker against
28.1 ns on the Rust worker for the same 32 messages, so the C++ worker ran about 1.3× faster on
that kernel). These numbers support no speedup claim by themselves; the plan uses them as
ratios and as hypotheses for the comparison harness to test.

## Files

- `probe.rs`: the Rust probe, included from `protobuf-buffa/native/lib.rs` as a `#[cfg(test)]`
  module and run through the existing `//protobuf-buffa/native:test` target
  (`--test_arg=probe::probe --test_arg=--nocapture`); removed from the tree before commit.
  Variants: a structural scan with length-delimited fields opaque (`scan_flat`), a schema-aware
  structural scan recursing into every known message field (`scan_nested`), the production
  ABI 9 `typed::view` decoder with scratch reuse and per-message release (`view`), a
  hand-written schema-specialized decoder writing a flat `u64` slab with spans into the input
  (`hand`, with the production UTF-8/UTF-16 validator; `hand_ascii`, with an 8-bytes-at-a-time
  ASCII check before that validator; `hand_novf`, no validation), a table-driven generic decoder
  into the same slab (`table`, `table_novf`), the same with parse-time projection for the sparse
  consumer (`table_sparse`; `_noskipv` does not structurally validate skipped subtrees) and
  projecting nothing (`table_none`), slab and view sizes for message 1, packed varint decoding
  strategies over 4,096 values (byte loop, `buffa::encoding::decode_varint`, 8-byte load with
  shift-and-mask, 8-byte load with BMI2 `pext`, and continuation-bit counting), and per-string
  validation cost on fixture-sized names.
- `rust.txt`: its measurement lines; `rust-raw.log`: the full Bazel log. Invocation:
  <https://app.buildbuddy.io/invocation/182fa836-af4e-4229-aa91-05eedf4c9d64>.
- `proto_probe.cc`: the C++ probe (a temporary `cc_test` in `benchmarks/comparison` linking
  `@protobuf//:protobuf`, `@protobuf//:type_cc_proto` and a `upb_c_proto_library` over
  `@protobuf//:type_proto`; removed before commit). Variants: `scan_flat`, protobuf C++ 33.4
  generated classes with one reused message (`cpp_reuse`, `cpp_reuse_consume` walks every
  field like the consumer harness), fresh arena-allocated messages with the arena reset per 32
  messages (`cpp_arena`), and upb (`upb`, `upb_consume`) over `upb_Arena_Init` on a
  preallocated block. upb ran in its default configuration (`fasttable_enabled` off: the
  mini-table interpreter, not the tail-call fast table).
- `cpp.txt`, `cpp-raw.log` (trimmed to the invocation header and the test output; the 135,000 lines of protobuf C++ and upb compiler output are only in the invocation). Invocation:
  <https://app.buildbuddy.io/invocation/6ab1c651-894b-4154-b417-a7dae849b749>.
- `ProtoProbe.java`: the JVM probe (a temporary `java_test` with `use_testrunner = False`,
  removed before commit), on the pinned GraalVM JDK (`OpenJDK 64-Bit Server VM
  25.0.4.1.1+1-jvmci-25.5-b24`, the comparison harness's JVM). Variants: protobuf-java 4.36.1
  `Type.parseFrom(byte[])` (`stock_parse`, `stock_parse_consume`), a pure-Java schema-specialized
  decoder into a `long[]` slab with the same record layout as the Rust `hand` decoder
  (`java_slab` with ASCII-first UTF-8 validation, `java_slab_novf`, `java_slab_consume`), FFM
  downcall cost for the shipped Buffa library's bounded leaf `td_buffa_fingerprint` with a normal
  handle and with `Linker.Option.critical(false)`, and the cost of copying a 150 B heap array
  into a native segment. Each variant prints three warm passes; read the last two.
- `jvm.txt`, `jvm-raw.log`. Invocation:
  <https://app.buildbuddy.io/invocation/2a78e775-ce42-4b8c-8d51-dd329429c184>.

## Fixture

All three probes generate the consumer harness fixture (`ConsumerComparison.java`, 32
`google.protobuf.Type` messages) byte for byte: `name = "catalog-<i>-λ"`, `syntax = PROTO3`,
`source_context` when `i % 3 != 0`, oneofs `identity` (+ `contact-😀` for even `i`), and
`3 + i % 6` nested `Field` messages (`kind = TYPE_INT64`, `cardinality = REPEATED`, `number`,
`name = "column-<j>"`, `packed` when `(i + j) % 3 != 0`). `holdout` uses `12 + i % 13` fields with
`-λ😀` name suffixes; `large` uses `2048 + (i % 8) * 64` fields. Totals: small 4,797 B (149.9 B
average), holdout 16,321 B (510 B), large 1,658,313 B (51,822 B). The Java probe builds the
messages with protobuf-java and the other two with a 30-line encoder; the sizes match exactly.
Every variant consumes its result into a sink; checksums were not asserted across variants.

## Headline rows (ns per message; MB/s of wire input)

| Variant | small 150 B | holdout 510 B | large 52 KB | Worker |
|---|---:|---:|---:|---|
| `scan_flat` (structural floor) | 28.1 · 5,341 | 51.0 · 10,003 | 4,141 · 12,516 | Rust |
| production ABI 9 `view` | 634.0 · 236 | 1,878 · 272 | 204,721 · 253 | Rust |
| `hand` (flat slab, production validator) | 174.3 · 860 | 632 · 807 | 62,453 · 830 | Rust |
| `hand_ascii` | 114.7 · 1,307 | 689 · 740 | 26,351 · 1,967 | Rust |
| `hand_novf` | 80.5 · 1,863 | 211 · 2,422 | 23,126 · 2,241 | Rust |
| `table` (naive table-driven, validated) | 274.8 · 546 | 856 · 596 | 98,676 · 525 | Rust |
| `table_sparse` (parse-time projection) | 226.4 · 662 | 798 · 639 | 93,945 · 552 | Rust |
| `table_none` (validate, materialize nothing) | 80.8 · 1,855 | 266 · 1,917 | 21,924 · 2,364 | Rust |
| protobuf C++ `cpp_reuse` | 288.9 · 519 | 838.5 · 608 | 82,912 · 625 | C++ |
| protobuf C++ `cpp_arena` | 341.6 · 439 | 968.3 · 527 | 192,338 · 269 | C++ |
| upb (mini-table) | 366.2 · 409 | 1,076 · 474 | 124,907 · 415 | C++ |
| protobuf-java `stock_parse` (HotSpot/Graal JIT) | 191.3 · 784 | 801.3 · 637 | 49,740 · 1,042 | JVM |
| pure-Java `java_slab` (validated) | 88.7 · 1,689 | 245.1 · 2,081 | 26,461 · 1,959 | JVM |
| pure-Java `java_slab_novf` | 63.8 · 2,349 | 163.4 · 3,122 | 21,278 · 2,436 | JVM |

Other rows: packed varints (2.35 B average) decode at 0.751 ns/varint with a byte loop
(3,128 MB/s), 0.886 with `buffa::encoding::decode_varint`, 2.64 with an 8-byte shift-and-mask
load, 2.62 with `pext`, and count at 0.329 ns/varint (7,144 MB/s). The production string
validator (`std::str::from_utf8` + `encode_utf16().count()` below 64 bytes) costs 5.4 ns per
8–9-byte ASCII name against 1.0 ns for an ASCII word check, and 7.9 against 6.7 ns for the
12-byte `catalog-<i>-λ`. Sizes for message 1 (122 B): flat slab 488 B, ABI 9 view 892 B (28
entries, 55 directory words); large message 1 (48,087 B): slab 202,856 B, view 380,324 B. FFM:
7.9 ns per normal downcall, 4.5 ns per critical downcall, 4.3 ns to copy 150 B into a native
segment.

## Caveats specific to these probes

- The `hand`, `table` and `java_slab` decoders are standalone sketches of a flat result
  layout, not the production decoder; they show what the wire allows for this schema (presence
  bits, inline scalars, spans into the input, child records, repeated lists), with the
  production validator where marked. They preserve unknown fields only as skips and implement
  no merge, oneof, map or required-field semantics; a production decoder pays some of that.
- The flat slab uses 8-byte slots for every field; the plan's layout compiler uses 4-byte slots
  for 32-bit kinds and offsets, so production slabs will be smaller than the sizes printed here.
- `view` includes one exact allocation and one release per message (the production steady
  state) but no Java owner, lease or transition; the harness's SVM decode-only cell for the
  same code measured 690 ns, so the Java and ABI layer costs about 60 ns per message.
- The C++ and upb rows ran on a faster worker than the Rust rows (see `scan_flat`). upb's
  fast-table parser was not built; protobuf C++ ran with the module's default copts under the
  Elide LLVM toolchain and ThinLTO.
- The JVM probe's first warm pass is cold-JIT (for example `java_slab` 564 ns before
  compilation); its `stock_parse` runs in a tight loop without the consumer harness's context,
  checksum and rotation, which is why it is faster than the harness's 282 ns decode cell.
- The Rust probe's naive table decoder scans a pending list per repeated field at message
  end and indexes bounds-checked vectors; it bounds a table-driven design from above, not
  from below.
