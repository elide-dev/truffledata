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

// Scalar field kinds: 0 boolean, 1 int64, 2 uint64, 3 float64, 4 string.
// Field flags: bit 0 required, bit 1 nullable; all other bits must be zero.
// Plans copy schema names. The caller keeps each plan alive during typed_decode;
// completed typed results retain no plan pointers. Names must be valid UTF-8.
// max_name_bytes bounds total name payload, not plan allocator overhead (<=64 fields).
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
