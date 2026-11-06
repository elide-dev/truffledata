#ifndef TRUFFLEDATA_JSON_API_H
#define TRUFFLEDATA_JSON_API_H
#include "json/native/generated/seam.h"

// ABI status codes. No C++ exception crosses the generated C boundary.
enum td_json_status {
  TD_JSON_OK = 0,
  TD_JSON_ARGUMENT = 1,
  TD_JSON_LIMIT = 2,
  TD_JSON_ALLOCATION = 3,
  TD_JSON_SYNTAX = 4,
  TD_JSON_INTERNAL = 5,
  TD_JSON_CONTRACT = 6
};

// Typed plans hold at most this many records (each at most 64 fields).
#define TD_JSON_MAX_PLAN_RECORDS 65536u
// Maximum record nesting of a typed plan; the root record alone has depth 1.
#define TD_JSON_MAX_PLAN_DEPTH 32u

// Field kinds: 0 boolean, 1 int64, 2 uint64, 3 float64, 4 string, 5 record.
// Length units: 0 unconstrained, 1 UTF-16 units, 2 Unicode scalar values.
// Floating widths: 0/64 binary64; 32 requires an exactly representable binary32 value.
// Field flags: bit 0 required, bit 1 nullable, bit 2 repeated; other bits must be zero.
// JsonScalarField.child is the record ordinal for kind 5 and UINT32_MAX otherwise.
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
