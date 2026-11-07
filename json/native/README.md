# Native JSON decoder

The C++ decoder traverses simdjson 5.1.1 On-Demand directly into a Myna value ABI
v4 index (Myna 0.11.0), without an intermediate DOM. Typed plans write fixed scalar
slots instead. Both sit behind one versioned C ABI, `json.seam` (application ABI 7),
which generates the direct SVM `@CFunction` bindings and JVM FFM handles. The Java
owners, guest interop, export and snapshot contracts are described in
[docs/JSON.md](../../docs/JSON.md).

## Compact tape (ABI 7, tape-gbps plan Task 2)

`decoder.h` now walks On-Demand into the compact tape of `//tape/native:ctape.h` (plan
`docs/superpowers/plans/2026-11-07-tape-gbps.md` §3): one 64-bit word per value, a second
word for numbers, containers and escaped (pool-owned) text; number and string words carry
their exact token or body span in the retained input, keys carry the `KEY` flag and strings
the `ASCII` flag, so there are no lexical-span or key-meta side tables. Every object of at
least two members gets a directory run of `key_offset << 32 | FNV-1a` entries, and the
duplicate-key check happens while that run is sorted (equal hashes are byte-compared); one-member
objects have none. `tape_decoder<null_writer>` is the validate-only walk
(`TD_JSON_VALIDATE_ONLY`): the same limits and the same sorted-run duplicate check over a
scratch key stack, nothing written. A session created with `TD_JSON_SESSION_COMPACT_TAPE`
(`JsonLimitsStruct.flags`) publishes `td_tape_view` descriptors (`json.seam` `TapeView`, 88
bytes) for decodes, retains and stream documents, with a text arena indexed by word;
`td_json_result_myna` converts a compact result into an owned Myna ABI v4 result (its own input
copy) for interchange. Until the Java readers move to the compact tape (plan Task 3), sessions
without the flag and `td_json_decode`/`_adopted` still publish the 0.8.0 representation below,
from `legacy_decoder.h`; `JsonResult` keeps its `lexical`, `lexical_count` and `key_hashes`
fields (null for compact results) until then.

## Generic documents (0.8.0 representation, until Task 3)

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

`JsonResult.view` points at a complete 88-byte `myna_value_view_v4` (ABI 6; ABI 5
published the 56-byte v3 descriptor). Every object with at least one member has a
Myna directory (`directory`/`directory_count`): one `(hash, key_offset)` entry per
member, `hash` being `myna_value_hash` (32-bit FNV-1a over the decoded key bytes)
and `key_offset` the key node's distance from its object, sorted by unsigned hash
and then offset. The object's payload is its first entry plus one; empty objects
keep payload 0. The duplicate-key check computes the same hash, and the writer takes
it from the decoder rather than re-hashing key bytes when the object closes. JSON
tapes carry no v4 metadata table (`metadata_count` 0) and no REF nodes. Java readers
look members up through the directory and remember a member's rank in it.

`JsonResult.key_hashes` publishes a side table with the node count: one `uint32_t`
per node. Bit 31 marks string and key nodes whose decoded bytes are all ASCII (the
text arena and export cursors use it); key nodes also carry the low 15 bits of their
hash in bits 16–30 (export cursors' `hash()`, the sinks' key caches) and their
offset from their object in bits 0–15, a layout shared with YAML's decoder. Member
lookup no longer reads it.

### The lean walk

- Numbers call `get_number()` first. Its success is simdjson's full grammar
  validation; the RFC 8259 check runs only for tokens it does not type (big
  integers, overflowing doubles) or rejects, before a DECIMAL reference is written.
  `-0` still reads the raw token's sign. Rejected spellings (`01`, `1.`, `.5`, `1e`,
  `-`) and the I64/U64/F64/DECIMAL classification are unchanged.
- String and key bodies with no backslash and no byte below 0x20 are referenced
  in the input (or inlined) without `get_string()`/`unescaped_key()`. Stage 1 has
  validated UTF-8 for the whole buffer before the walk, and rejects raw control
  bytes; bodies failing the scan still go through simdjson's unescaping.
- Duplicate keys are checked per object after escape decoding: a linear
  `(hash, length, bytes)` compare over up to 16 keys on the stack, then an
  open-addressing table in counted index memory.

### Arena storage and the tape writer

Nodes, owned text, the region table, lexical spans and key hashes are written by
`tape_writer.h` into a `block_arena` (`arena.h`) rather than through Myna's
`value_builder`. The decoder presizes every array from the input length (one node
per four input bytes, never more than the Myna node limit or a fixed share of the
index budget), so a typical document makes one upstream allocation: a single arena
block. Arrays above 32 KiB are allocated upstream directly and freed on growth,
so big tapes do not strand arena space. The writer enforces the builder's node,
text, depth and container limits and produces byte-identical nodes, bytes and
regions, and the same v4 directories as Myna's builder with `enable_v4`;
`//json/native:test` runs both writers over a 200-document corpus. Myna 0.11.0's
builder can reserve storage (elide-dev/myna#46), and the parity writer uses it, but
the raw writer stays the production path: the builder still wraps each write in a
poisoning try/catch, keeps a slot stack and re-hashes every key when its object
closes (`benchmarks/results/json-v4-*` measures both decoders).

## Sessions

`session.h` keeps one On-Demand parser, a padded input buffer and a block arena per
thread-confined session, so a small document costs no native allocation in steady
state (`//json/native:api_test` decodes 10,000 documents with zero index
allocations after warm-up). `td_json_session_decode` copies the input into the
session and writes a *borrowed* `JsonResult` (null owner) that stays valid until
the next call on the session. `td_json_session_retain` moves the document's arena
blocks, large allocations and input buffer into a new owner in O(blocks): node
pointers are unchanged and nothing is copied. `td_json_session_reset` is an O(1)
native leaf that only invalidates the borrow; the next decode recycles storage,
keeps at most four arena blocks, and drops parsers and input buffers that grew past
1 MiB. `td_json_session_decode_small` is the same decode bounded by
`TD_JSON_CRITICAL_LIMIT` (4 KiB) and declared a leaf for the JVM's critical path.
A failed decode publishes nothing and leaves the session reusable. `api.h`
documents the contract; `td_json_decode` does not use a session (no hidden
per-thread state).

### Text arenas

With `TD_JSON_WANT_TEXT`, `session_decode` also writes every key and string once into the
caller's buffer (`tape/native/text_arena.h`, shared with YAML; a post-pass over the finished
tape): Latin-1 when all code points are at most U+00FF, UTF-16LE (even offset) otherwise, with
one `offset << 32 | bytes << 1 | utf16` entry per node in `JsonResult.text_table` (session arena, valid like the borrowed result).
`2 × length + 16` bytes always suffice; `TD_JSON_TEXT_TOO_SMALL` reports a smaller buffer and
leaves the session reusable. Java's `TruffleSink` builds `TruffleString`s over that array without
copying.

### Streams

`stream.h` (`td_json_stream_*`) decodes whitespace-separated documents (NDJSON, JSON Lines,
concatenated JSON) one at a time into a session, with the carry-over contract of YAML's
`td_yaml_stream_*`. `stream_feed` copies into a carry-over buffer of at most
`2 × max_document_bytes` and returns the bytes it accepted (fewer: drain with `stream_next`, then
feed the rest). Each region of buffered bytes runs through the stream's own simdjson parser with
`iterate_many` (batch at least the maximum document); when its documents are delivered, its
`truncated_bytes()` tail moves to the front of the buffer. Until `stream_finish`, a region never ends
inside a UTF-8 sequence and a root number or literal touching the end is held back. Each document's
bytes are copied into the session's input buffer, which becomes its region, so a borrowed document
survives later feeds and `session_retain` works as for `session_decode`. Only whitespace separates
documents (simdjson's comma format accepts doubled and missing commas); a BOM is skipped only at the
start of the stream. Document-level errors can be skipped with `stream_skip`; stage-1 errors
(invalid UTF-8, raw control bytes) fail a whole batch and cannot.

## Limits and accounting

`storage_limits` caps logical input bytes before copying, and index allocations
before requesting memory. The budget is charged per arena block and per large
allocation, not per node: Myna nodes, regions, lexical spans, key hashes, owned text
and duplicate-key tables share it, unused block tails count, and the overlap of a
large array and its grown replacement counts toward the peak. Presizing depends
only on the input length and the configured limits, so a document's allocation
sequence (and its exact peak) is reproducible. Myna's node, text and depth limits apply separately; requested depth is
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
fingerprint query, the O(1) session reset and the 4 KiB-bounded small session
decode (`bazel/myna_contract.py` pins this set). Thread confinement and invalidation belong to the Java owners,
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

The Java side is `JsonPlan`, `TypedRecord`, `TypedArray` and `TypedAccess`, on SVM
through the static bindings and on the JVM through FFM (`JsonDecoder.compile`).

Character-length contracts use the shared `//bazel:unicode` simdutf library, also
used by Rust Buffa conversion. Counting decoded UTF-8 needs no intermediate UTF-16
buffer or Java string.

## Nested and repeated typed plans

A `typed_plan` is a table of closed records (record 0 is the root). A field of
kind 5 (`record`) links to another record by `JsonScalarField.child`; flag bit 2
marks a repeated field, decoded from a JSON array whose elements each satisfy the
field's scalar contract (elements are never null; `nullable` applies to the array
itself). Plans that are recursive or nest deeper than 32 records are rejected at
creation, so decoding recursion is bounded by the plan, not the input. Field
descriptors are 88 bytes; `td_json_plan_create` takes `JsonRecordPlan` entries
that partition the field table in order.

`typed_document` writes one slot arena and one record table of
`JsonRecordEntry {present, nulls, slots, plan}`, both charged to `index_memory`.
Root slots start at 0. A record slot holds its child entry index; a repeated slot
holds `(first << 32) | count`, indexing the slot arena for scalar elements or the
record table for record elements, whose entries are reserved contiguously before
any element decodes. Unknown, duplicate, missing, null, type-mismatched and
trailing input are rejected at every level. Plans without a record field publish
the root entry from result control storage, so flat results still make exactly one
index allocation. `//json/native:typed_test` injects a failure at every arena
growth and checks that each unwinds with no retained bytes under ASan/UBSan.

## Tests

```sh
bazel test //json/native:test //json/native:legacy_test //json/native:api_test //json/native:stream_test //json/native:typed_test //json/native:unicode_test
bazel test //json/tests:jvm          # JVM FFM backend
bazel test //json/tests:native_test  # SVM, manual, Linux x86-64 via RBE
```

Native tests cover malformed values, trailing input, escaped duplicate keys, exact
huge exponents, number classification, decoded Unicode, raw lexical text, adopted
and copied input, independent documents, container offsets, node/text/depth
limits, integer and length boundaries, all 64 presence bits, and result/plan
release order. Allocation checks inject failure at every index allocation,
including the adopted path, constructor failure and invalid input; `stream_test` checks that
10,000 documents fed in chunks of 1, 7, 64, 4,096 and 1,000,000 bytes (and with batches smaller
than the buffer) decode to exactly the tapes of per-document decodes, and injects failure at every
stream and session allocation. Run them under
both sanitizers, which instrument the adapter and simdjson:

```sh
bazel test //json/native:all //tape/native:all \
  --copt=-fsanitize=address,undefined --copt=-fno-sanitize-recover=all \
  --linkopt=-fsanitize=address,undefined
```

The SVM target links `//json/native:svm` statically: the C ABI plus `bridge/`, a thin
Rust static library over Myna's `myna-values` 0.9.1 whose `myna_utf8_to_utf16_v2` (simdutf)
`SvmValueTape` calls to materialize non-ASCII strings of 256 bytes or more
(`td_json_bridge_present` is its link probe). It adds compiled-read gates: typed
primitive reads, generic member reads, interop reads and export loops each require
observed compiled execution with performance warnings as errors, Graal graphs of at
most 10,000 nodes, ASTs of at most 192 nodes, and at most 0.05 Java bytes per
iteration. The typed and generic gates also require compiled reads to fail after
close while a sibling stays valid. These measure retained reads, not parse-time
allocation, native memory, interpreter throughput or parity with a stock parser.
