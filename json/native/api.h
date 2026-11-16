#ifndef TRUFFLEDATA_JSON_API_H
#define TRUFFLEDATA_JSON_API_H
#include "json/native/generated/seam.h"

// The integer protocol (status codes, flags, limits and plan code tables) is declared once, in
// json.seam, and generated into seam.h (TRUFFLEDATA_JSON_*) and JsonConstants. The TD_JSON_ names
// below are aliases of the generated values, never second copies of a number.

// ABI status codes (json.seam `enum Status`). No C++ exception crosses the generated C boundary.
#define TD_JSON_OK TRUFFLEDATA_JSON_STATUS_OK
#define TD_JSON_ARGUMENT TRUFFLEDATA_JSON_STATUS_ARGUMENT
#define TD_JSON_LIMIT TRUFFLEDATA_JSON_STATUS_LIMIT
#define TD_JSON_ALLOCATION TRUFFLEDATA_JSON_STATUS_ALLOCATION
#define TD_JSON_SYNTAX TRUFFLEDATA_JSON_STATUS_SYNTAX
#define TD_JSON_INTERNAL TRUFFLEDATA_JSON_STATUS_INTERNAL
#define TD_JSON_CONTRACT TRUFFLEDATA_JSON_STATUS_CONTRACT
#define TD_JSON_TEXT_TOO_SMALL TRUFFLEDATA_JSON_STATUS_TEXT_TOO_SMALL
// Streams (Task F1); the same values as TD_YAML_STREAM_EMPTY/TD_YAML_STREAM_TRUNCATED.
#define TD_JSON_STREAM_EMPTY TRUFFLEDATA_JSON_STATUS_STREAM_EMPTY
#define TD_JSON_STREAM_TRUNCATED TRUFFLEDATA_JSON_STATUS_STREAM_TRUNCATED

// session_decode flags (json.seam `flags DecodeFlags`).
// TD_JSON_WANT_TEXT (Task D2): also write the document's text arena into text_out (see below).
#define TD_JSON_WANT_TEXT TRUFFLEDATA_JSON_DECODE_FLAGS_WANT_TEXT
// Retained/member-read paths request lookup directories; sink-only borrows omit them.
#define TD_JSON_WANT_DIRECTORIES TRUFFLEDATA_JSON_DECODE_FLAGS_WANT_DIRECTORIES
// TD_JSON_UTF16_INPUT (Task D3): `data` is UTF-16LE (`length` bytes, even; `data` 2-byte aligned),
// validated and transcoded to UTF-8 natively; an unpaired surrogate is TD_JSON_SYNTAX. Limits,
// lexical spans and copied_input_bytes refer to the UTF-8 form.
#define TD_JSON_UTF16_INPUT TRUFFLEDATA_JSON_DECODE_FLAGS_UTF16_INPUT
// TD_JSON_LATIN1_INPUT (Task D3): `data` is ISO-8859-1, one code point per byte, transcoded to
// UTF-8. Exclusive with TD_JSON_UTF16_INPUT.
#define TD_JSON_LATIN1_INPUT TRUFFLEDATA_JSON_DECODE_FLAGS_LATIN1_INPUT
// TD_JSON_VALIDATE_ONLY (tape-gbps Task 2): run the decode walk and every check (grammar, UTF-8,
// numbers, duplicate keys after unescaping, limits) and keep nothing: on success result.view is
// null, the session holds no document (retain is TD_JSON_ARGUMENT) and only the accounting fields
// are set. Exclusive with TD_JSON_WANT_TEXT.
#define TD_JSON_VALIDATE_ONLY TRUFFLEDATA_JSON_DECODE_FLAGS_VALIDATE_ONLY

// The largest max_depth (decode, decode_adopted, JsonLimitsStruct) accepted.
#define TD_JSON_MAX_DEPTH TRUFFLEDATA_JSON_MAX_DEPTH

// ABI 10: every decoded result publishes td_tape_view (TapeView in json.seam).
// Spans and key flags are embedded in words; text_table has one entry per word,
// with zero entries for second words. JsonLimitsStruct.flags is reserved and must be zero.

// td_json_result_myna(result, output): converts the compact tape of `result` (borrowed or owned,
// still valid) into an owned Myna ABI v4 result in the zero-initialized `output` (view: a
// myna_value_view_v4 with directories on objects of at least two members; region 1 is the output's
// own copy of the input, so it outlives the source). Release it with
// td_json_release. A result whose view is not a compact tape (a Myna descriptor, a validate-only
// result) or a live `output` is TD_JSON_ARGUMENT.

// Sessions (td_json_session_*). A session is a thread-confined decode context: one simdjson
// stage1 structural parser, a padded input buffer and a block arena reused across documents. Callers
// serialize every call on one session. session_create copies JsonLimitsStruct (max_depth ≤ 128)
// and returns null on invalid limits or allocation failure.
// session_decode copies `data` into the session and writes a *borrowed* JsonResult: owner is
// null, and every pointer stays valid only until the next call on the session (decode, retain,
// reset or release). Never td_json_release a borrowed result (doing so is a harmless no-op). A
// failed decode zeroes the result, keeps nothing and leaves the session reusable. Unknown flags
// are TD_JSON_ARGUMENT. index_allocations counts upstream allocations made by this decode (0 in
// steady state).
// Text arenas (TD_JSON_WANT_TEXT, Task D2): text_out (2-byte aligned, text_capacity ≤ INT32_MAX)
// receives every key and string node's decoded text once, in tape order: Latin-1 when all its code
// points are ≤ U+00FF, else UTF-16LE at an even offset. result.text_table holds one u64 per node,
// offset << 32 | byte length << 1 | utf16 (0 for other nodes and empty strings), valid like the
// rest of the borrowed result; result.text_bytes is the bytes written. 2 × length + 16 bytes always
// suffice; a smaller capacity may fail with TD_JSON_TEXT_TOO_SMALL (nothing published, session
// reusable). text_out/text_capacity without the flag, or the flag without text_out, is
// TD_JSON_ARGUMENT.
// session_retain moves the live document's arena blocks and input into a zero-initialized
// `result` that owns them (release with td_json_release), without copying nodes; the session then
// holds no document. Retaining twice, with no document, or into a live result is TD_JSON_ARGUMENT.
// session_reset invalidates the borrowed document in O(1) without freeing (a native leaf call);
// storage is recycled by the next decode. session_release frees the session; retained results
// stay valid.
// session_decode_small is session_decode restricted to length ≤ TD_JSON_CRITICAL_LIMIT
// (TD_JSON_ARGUMENT above it), declared a native leaf for the JVM's bounded critical path:
// bounded input (≈ 30 µs at 150 MB/s), no locks, no exception escapes.
#define TD_JSON_CRITICAL_LIMIT TRUFFLEDATA_JSON_CRITICAL_LIMIT

// Streams (td_json_stream_*, Task F1; json/native/stream.h): whitespace-separated documents
// (NDJSON, JSON Lines, concatenated JSON) decoded one at a time into a session, with the carry-over
// contract of td_yaml_stream_*. stream_create(session, max_document_bytes, batch_bytes) borrows the
// session (which must outlive the stream) and returns null when max_document_bytes is 0 or above
// UINT32_MAX / 2, batch_bytes (the stage1 batch limit) is below max_document_bytes or above
// UINT32_MAX, or allocation fails. The carry-over buffer holds at most 2 × max_document_bytes.
// stream_feed copies up to `length` bytes and returns how many it accepted: fewer means the buffer
// is full or still being read; call stream_next until TD_JSON_STREAM_EMPTY, then feed the rest. A
// finished or failed stream accepts nothing. stream_finish marks the end of input. stream_next
// accepts only TD_JSON_WANT_DIRECTORIES: set it for borrowed by-key reads, omit it for sinks. It
// writes the next document as a borrowed result (as session_decode does: owner null, valid until
// the next stream_next/stream_skip or any call on the session; feeding does not invalidate it; no
// text arena) and returns TD_JSON_OK, TD_JSON_STREAM_EMPTY (no complete document buffered yet, or
// the finished stream is exhausted), TD_JSON_STREAM_TRUNCATED (the finished input ended inside a
// document) or the failing status; a failed stream stays failed. stream_skip resumes a stream
// failed by a document-level error (TD_JSON_SYNTAX, or TD_JSON_LIMIT for a complete document) after
// that document and returns TD_JSON_OK, else TD_JSON_ARGUMENT: stage-1 errors (invalid UTF-8, raw
// control bytes) are reported for a whole simdjson batch (documents of the batch before the error
// may not be delivered) and cannot be skipped, nor can allocation failures,
// truncation, or an incomplete document above max_document_bytes. stream_truncated_bytes is the
// number of buffered bytes past the last delivered document (after TD_JSON_STREAM_TRUNCATED: the
// incomplete document). A UTF-8 BOM is accepted only at the start of the stream. Session results
// can be retained with session_retain as usual.

// Typed plans hold at most this many records (each at most TRUFFLEDATA_JSON_MAX_RECORD_FIELDS).
#define TD_JSON_MAX_PLAN_RECORDS TRUFFLEDATA_JSON_MAX_PLAN_RECORDS
// Maximum record nesting of a typed plan; the root record alone has depth 1.
#define TD_JSON_MAX_PLAN_DEPTH TRUFFLEDATA_JSON_MAX_PLAN_DEPTH

// JsonScalarField code tables are json.seam enums and flag sets: kind (FieldKind: boolean, int64,
// uint64, float64, string, record), length_unit (LengthUnit: unconstrained, UTF-16 units, Unicode
// scalar values), float_bits (FloatWidth: unspecified and binary64 are binary64; binary32 requires
// an exactly representable binary32 value) and flags (FieldFlags: required, nullable, repeated;
// other bits must be zero). JsonScalarField.child is the record ordinal for FieldKind record and
// TRUFFLEDATA_JSON_NO_CHILD (UINT32_MAX) otherwise.
// plan_create takes JsonRecordPlan[record_count] (record 0 is the root) that
// partition the field table in order: record i's fields_offset equals the sum
// of the preceding field_counts, and the counts sum to field_count. Plans that
// are recursive or deeper than TD_JSON_MAX_PLAN_DEPTH are TD_JSON_ARGUMENT.
// A repeated field is a JSON array; scalar contracts apply to each element and
// elements are never null. Nullable applies to the field (the array) itself.
// Typed results publish one slot arena (slots/count) and one record table
// (records/record_count) of JsonRecordEntry {present, nulls, slots, plan}:
// entry 0 is the root and its slots start at index 0. A record's field i is
// slot[entry.slots + i]. A RECORD slot's bits hold its child entry index; a
// repeated slot's bits hold (first << 32) | count, where first indexes the
// slot arena (scalar elements) or the record table (record elements); an empty
// array is 0. Record and repeated slots carry no text or lexical span.
// Flat plans (no RECORD field in the root) publish the root entry from result
// control storage, so index accounting covers only the slot arena.
// Plans copy schema names. The caller keeps each plan alive during typed_decode;
// completed typed results retain no plan pointers. Names must be valid UTF-8.
// max_name_bytes bounds total name payload, not plan allocator overhead.
// JsonScalarSlot pointers and lexical bytes are read-only despite ABI pointer carriers.
// JsonPlan/JsonTypedResult follow the same zero-init, unique-owner, release rules.
// Missing slots are zeroed; null slots retain lexical text but no scalar payload.
// Inspect presence/null masks before reading scalar payloads.
// TD_JSON_CONTRACT covers required/null/type/range/duplicate/unknown-field rejection.
// decode requires readable data[length] and a zero-initialized JsonResult.
// Successful results uniquely own their handle: do not copy an owning result.
// Reusing a live result is rejected without altering it. Release zeros the result
// and is idempotent on that same storage. Views remain valid until release;
// the caller's input may be discarded immediately after decode returns.
// Native callers serialize access to each result. Java supplies scope/thread guards.
#endif
