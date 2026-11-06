# Native JSON decoder

The C++ decoder traverses simdjson 5.1.1 On-Demand directly into a Myna ABI v3
value index, without an intermediate DOM. Typed plans write fixed scalar slots
instead. Both sit behind one versioned C ABI, `json.seam` (application ABI 4),
which generates the direct SVM `@CFunction` bindings and JVM FFM handles. The Java
owners, guest interop, export and snapshot contracts are described in
[docs/JSON.md](../../docs/JSON.md).

## Generic documents

The decoder accepts every root kind, rejects decoded duplicate keys and trailing
input, and validates number grammar without restricting numeric range. Every value
is visited before a result is published.

Each document retains its input. `td_json_decode` copies it once into padded
storage. `td_json_decode_adopted` parses caller memory in place when `capacity`
leaves `SIMDJSON_PADDING` readable bytes past `length`; otherwise it copies once
and `copied_input_bytes` equals `length`. The library never frees adopted memory;
the caller keeps it valid until `td_json_release` returns.

Parsers are leased from a bounded process-wide pool (at most eight idle) and
returned before decode completes. `get_string()` output is treated as parser
scratch: unescaped strings longer than 16 bytes reference the retained input
region, escaped strings are copied into message-owned bytes, and short strings are
inline. No retained document depends on parser scratch, so parser reuse cannot
overwrite a live document. A document cannot be copied or reparsed.

Numbers are classified once:

| Token | Node |
| --- | --- |
| Integer within `int64` | I64 |
| `-0` | F64 `-0.0` (simdjson folds it into integer zero) |
| Integer above `int64` within `uint64` | U64 |
| Other token with a finite `get_double()` | F64, simdjson's nearest binary64 |
| Anything else, such as `1E400` | DECIMAL, referencing the token in the input |

Every node has a packed lexical span, `offset << 32 | length`, into region 1, the
retained input. Strings and keys keep their quotes and escapes, numbers keep their
exact spelling, and containers, booleans and null carry zero.
`JsonResult.lexical`/`lexical_count` publish that array, one entry per node.

## Limits and accounting

`storage_limits` caps logical input bytes before copying, and index allocations
before requesting memory. Myna nodes, regions, lexical spans, traversal storage and
duplicate-key sets share the index budget, and reallocation overlap counts toward
the peak. Myna's node, text and depth limits apply separately; requested depth is
at most 128.

`JsonResult` reports copied and padded input bytes, retained and peak requested
index bytes, the index allocation count, the fixed control allocation, and the
parser's public input capacity. Counts exclude allocator metadata. The parser owns
its scratch allocations; they are not counted, and its input capacity is not
presented as scratch. These limits are not a total native-memory budget.

## Native interface

`api.h` documents status codes and caller obligations. Decode publishes an owning
`JsonResult` only after validation, including a pointer to the immutable Myna
descriptor; reads need no further native calls. Callers must check the binding
fingerprint and Myna view version before reading those layouts.

Initialize results to zero, never copy their ownership, and release each result on
the same storage; releasing it again is harmless. A live result cannot be
overwritten by another decode. With `td_json_decode`, the input can be discarded
when decode returns. Returned views are invalid after release. Failures map to
argument, limit, allocation, syntax or internal-error statuses; exceptions do not
cross C. Every import uses normal VM transitions (`native_leaf=false`) except the
fingerprint query. Thread confinement and invalidation belong to the Java owners,
not to this pointer interface.

## Typed scalar plans

`typed.h` compiles reusable closed-record plans and populates slots directly,
without a generic Myna tape. Plans copy their field names under a name-byte limit
and support up to 64 fields with separate presence and null masks,
required/nullable contracts, signed integer ranges, unsigned maxima, decoded UTF-8
byte limits, UTF-16/code-point length bounds, and exact binary32 representability.
Integer kinds require integer JSON syntax; float64 uses checked finite binary64
conversion. Arbitrary-precision numbers belong on the generic path.

Every field is consumed and validated. Escaped duplicates, unknown fields and
trailing input are rejected. Typed strings retain their raw spelling. Results
retain their own input and outlive their plans; a plan must stay alive only during
decoding. Index accounting covers the single slot buffer; parser scratch and plan
construction are separate. The ABI exposes plan creation/release and typed
decode/release, with 80-byte field descriptors and 40-byte slots, published
without copying a second slot array.

The Java side is `JsonPlan`, `JsonRecord` and `JsonAccess` on SVM. Plans are
scalar-only today; nested and repeated records are in progress.

Character-length contracts use the shared `//bazel:unicode` simdutf library, also
used by Rust Buffa conversion. Counting decoded UTF-8 needs no intermediate UTF-16
buffer or Java string.

## Tests

```sh
bazel test //json/native:test //json/native:api_test //json/native:typed_test //json/native:unicode_test
bazel test //json/tests:jvm          # JVM FFM backend
bazel test //json/tests:native_test  # SVM, manual, Linux x86-64 via RBE
```

Native tests cover malformed values, trailing input, escaped duplicate keys, exact
huge exponents, number classification, decoded Unicode, raw lexical text, adopted
and copied input, independent documents, container offsets, node/text/depth
limits, integer and length boundaries, all 64 presence bits, and result/plan
release order. Allocation checks inject failure at every index allocation,
including the adopted path, constructor failure and invalid input. Run them under
both sanitizers, which instrument the adapter and simdjson:

```sh
bazel test //json/native:test //json/native:api_test //json/native:typed_test //json/native:unicode_test \
  --copt=-fsanitize=address,undefined --copt=-fno-sanitize-recover=all \
  --linkopt=-fsanitize=address,undefined
```

The SVM target links the parser statically and adds compiled-read gates: typed
primitive reads, generic member reads, interop reads and export loops each require
observed compiled execution with performance warnings as errors, Graal graphs of at
most 10,000 nodes, ASTs of at most 192 nodes, and at most 0.05 Java bytes per
iteration. The typed and generic gates also require compiled reads to fail after
close while a sibling stays valid. These measure retained reads, not parse-time
allocation, native memory, interpreter throughput or parity with a stock parser.
