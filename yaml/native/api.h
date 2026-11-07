#ifndef TRUFFLEDATA_YAML_API_H
#define TRUFFLEDATA_YAML_API_H
#include "yaml/native/generated/seam.h"

// ABI status codes, as JSON's plus the YAML ones. No C++ exception (and no rapidyaml exception)
// crosses the generated C boundary.
enum td_yaml_status {
  TD_YAML_OK = 0,
  TD_YAML_ARGUMENT = 1,
  TD_YAML_LIMIT = 2,
  TD_YAML_ALLOCATION = 3,
  TD_YAML_SYNTAX = 4,
  TD_YAML_INTERNAL = 5,
  TD_YAML_CONTRACT = 6,
  TD_YAML_MULTIPLE_DOCUMENTS = 7,
  TD_YAML_STREAM_EMPTY = 8,
  TD_YAML_STREAM_TRUNCATED = 9
};

// session_decode flags. TD_YAML_WANT_UTF16_TEXT is reserved for the text arena (plan Task 5 with
// JSON performance D2): until then it, any other bit, and a non-null text_out or nonzero
// text_capacity are TD_YAML_ARGUMENT.
#define TD_YAML_WANT_UTF16_TEXT 1u
#define TD_YAML_RETAIN_SOURCE 2u

// YamlOptionsStruct enumerations (YamlOptions on the Java side).
#define TD_YAML_SCHEMA_CORE 0u
#define TD_YAML_SCHEMA_JSON 1u
#define TD_YAML_SCHEMA_FAILSAFE 2u
#define TD_YAML_SCHEMA_YAML_1_1 3u
#define TD_YAML_DUPLICATE_KEYS_REJECT 0u
#define TD_YAML_DUPLICATE_KEYS_LAST_WINS 1u
#define TD_YAML_ALIASES_ALLOW 0u
#define TD_YAML_ALIASES_REJECT 1u
#define TD_YAML_UNKNOWN_TAGS_PRESERVE 0u
#define TD_YAML_UNKNOWN_TAGS_REJECT 1u
#define TD_YAML_VERSION_DIRECTIVE_IGNORE 0u
#define TD_YAML_VERSION_DIRECTIVE_HONOR 1u

// YamlResult.document_flags.
#define TD_YAML_DOC_EXPLICIT_START 1u     // the document began with `---`
#define TD_YAML_DOC_EXPLICIT_END 2u       // the document ended with `...`
#define TD_YAML_DOC_NONFINITE 4u          // non-finite F64 nodes (.inf/.nan; elide-dev/myna#52)
#define TD_YAML_DOC_SOURCE_RETAINED 8u    // `source` is a pristine copy (TD_YAML_RETAIN_SOURCE)
#define TD_YAML_DOC_PRESENT 16u           // the input held a document (otherwise the root is null)

// yaml_meta bits (one uint32_t per node): 0-2 style (0 plain, 1 single, 2 double, 3 literal,
// 4 folded, 5 block collection, 6 flow collection), 3 anchored, 4 alias, 5-7 key kind (0 string,
// 1 null, 2 bool, 3 int, 4 float), 8-31 tag index (1-based into `tags`; 0 none) or, on aliases, the
// alias ordinal into `alias_targets`.
//
// Sessions (td_yaml_session_*) follow td_json_session_* exactly. A session is a thread-confined
// decode context: one rapidyaml parse engine and event handler, an input buffer and a block arena,
// reused across documents; callers serialize every call on one session. session_create copies
// YamlOptionsStruct (max_depth <= 128, enumerations as above, reserved 0) and returns null on
// invalid options or allocation failure.
//
// session_decode accepts zero or one document (zero yields a root null) and writes a *borrowed*
// YamlResult: owner is null, and every pointer stays valid only until the next call on the session.
// Input is UTF-8 (a BOM is skipped) or UTF-16/UTF-32 with a BOM; it is validated as strict UTF-8 and
// c-printable before rapidyaml sees it. A second document is TD_YAML_MULTIPLE_DOCUMENTS. A failed
// decode publishes nothing but error_line, error_column (1-based; 0 unknown) and error_message
// (error_message_length bytes, at most 255, NUL-terminated, at offsetof(YamlResult, error_message)),
// and leaves the session reusable. `lexical` spans index `source`: plain single-line scalars always;
// quoted, multi-line and block scalars only with TD_YAML_RETAIN_SOURCE (else 0).
//
// session_retain moves the live document's arena blocks and input into a zero-initialized result
// that owns them (release with td_yaml_release). session_reset invalidates the borrowed document in
// O(1) without freeing (a native leaf). session_release frees the session; retained results stay
// valid.
#endif
