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
  TD_JSON_INTERNAL = 5
};

// decode requires readable data[length] and a zero-initialized JsonResult.
// Successful results uniquely own their handle: do not copy an owning result.
// Reusing a live result is rejected without altering it. Release zeros the result
// and is idempotent on that same storage. Views remain valid until release;
// the caller's input may be discarded immediately after decode returns.
// Native callers serialize access to each result. Java supplies scope/thread guards.
#endif
