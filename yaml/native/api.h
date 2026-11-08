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
  TD_YAML_STREAM_TRUNCATED = 9,
  TD_YAML_TEXT_TOO_SMALL = 10
};

// session_decode flags. TD_YAML_WANT_UTF16_TEXT and its inline output arguments remain reserved;
// use session_write_text after decode. YAML must size text from decoded strings, because aliases
// used as mapping keys can expand their text beyond any input-sized allocation estimate.
#define TD_YAML_WANT_UTF16_TEXT 1u
#define TD_YAML_RETAIN_SOURCE 2u
// Borrowed scan/export documents omit directories by default; key lookup still scans keys.
// Set this for random access. Retain always publishes directories, building them if needed.
#define TD_YAML_WANT_DIRECTORIES 4u

// YamlOptionsStruct enumerations (YamlOptions on the Java side).
#define TD_YAML_SCHEMA_CORE 0u
#define TD_YAML_SCHEMA_JSON 1u
#define TD_YAML_SCHEMA_FAILSAFE 2u
#define TD_YAML_SCHEMA_YAML_1_1 3u
#define TD_YAML_DUPLICATE_KEYS_REJECT 0u
#define TD_YAML_DUPLICATE_KEYS_LAST_WINS 1u
// Not in YamlOptions yet (the plan's deferred ComplexKeys.PAIRS shape): a mapping with a duplicate
// key, or with a collection key under TD_YAML_COMPLEX_KEYS_PAIRS, is stored as an ARRAY of
// 2-element [key, value] ARRAYs whose yaml_meta style is 7. The yaml-test-suite target uses both.
#define TD_YAML_DUPLICATE_KEYS_PAIRS 2u
#define TD_YAML_COMPLEX_KEYS_REJECT 0u
#define TD_YAML_COMPLEX_KEYS_PAIRS 1u
#define TD_YAML_ALIASES_ALLOW 0u
#define TD_YAML_ALIASES_REJECT 1u
#define TD_YAML_UNKNOWN_TAGS_PRESERVE 0u
#define TD_YAML_UNKNOWN_TAGS_REJECT 1u
#define TD_YAML_VERSION_DIRECTIVE_IGNORE 0u
#define TD_YAML_VERSION_DIRECTIVE_HONOR 1u

// YamlResult.document_flags.
#define TD_YAML_DOC_EXPLICIT_START 1u     // the document began with `---`
#define TD_YAML_DOC_EXPLICIT_END 2u       // the document ended with `...`
#define TD_YAML_DOC_NONFINITE 4u          // non-finite F64 nodes (.inf/.nan); the descriptor sets MYNA_VALUE_ALLOW_NON_FINITE
#define TD_YAML_DOC_SOURCE_RETAINED 8u    // `source` is a pristine copy (TD_YAML_RETAIN_SOURCE)
#define TD_YAML_DOC_PRESENT 16u           // the input held a document (otherwise the root is null)
#define TD_YAML_DOC_SCHEMA_1_1 32u        // resolved with the YAML 1.1 schema (option or HONOR)

// yaml_meta bits (one uint32_t per compact word, payload entries zero): 0-2 style (0 plain, 1 single, 2 double, 3 literal,
// 4 folded, 5 block collection, 6 flow collection, 7 pair), 3 anchored, 4 alias, 5-7 key kind
// (0 string, 1 null, 2 bool, 3 int, 4 float), 8-31 tag index (1-based into `tags`; 0 none) or, on
// aliases, private producer bookkeeping (consumers resolve the REF word).
//
// Per-document tables (all borrowed like the tape): `tags` (YamlSpan: resolved tag URIs such as
// `tag:yaml.org,2002:str` or `!local`, without brackets), `anchors` (YamlAnchor: node and name, in
// definition order, so an anchor's id is its index), `directives` (YamlTagDirective: %TAG handle and
// prefix). Every name and URI is (offset, length) into `strings`. An alias is a compact REF word with the
// alias bit; an alias used as a key is a STRING key node holding its target's key text with the
// alias bit. Readers resolve aliases through REF words; nothing is expanded natively.
//
// Streams (td_yaml_stream_*): stream_create(session, max_document_bytes, buffer_bytes) splits
// multi-document input at c-forbidden `---`/`...` lines and decodes one document at a time into
// the session (the stream borrows the session, which must outlive it). stream_feed copies up to
// `length` bytes into the carry-over buffer and returns how many it accepted (fewer: backpressure;
// call stream_next, then feed the rest). stream_finish marks the end of input. stream_next writes the
// next document as a borrowed result (as session_decode does) and returns TD_YAML_OK,
// TD_YAML_STREAM_EMPTY (no complete document buffered yet, or the finished stream is exhausted),
// TD_YAML_STREAM_TRUNCATED (the finished input ended inside the final document) or the failing
// document's status; a failed stream stays failed. documents(byte[]) is a stream fed the whole
// buffer and finished.
//
// Sessions (td_yaml_session_*) follow td_json_session_* exactly. A session is a thread-confined
// decode context: one rapidyaml parse engine and event handler, an input buffer and a block arena,
// reused across documents; callers serialize every call on one session. session_create copies
// YamlOptionsStruct (max_depth <= 128, enumerations as above) and returns null on
// invalid options or allocation failure.
//
// session_decode accepts zero or one document (zero yields a root null) and writes a *borrowed*
// YamlResult: owner is null, and every pointer stays valid only until the next call on the session.
// Input is UTF-8 (a BOM is skipped) or UTF-16/UTF-32 with a BOM; it is validated as strict UTF-8 and
// c-printable before rapidyaml sees it. A second document is TD_YAML_MULTIPLE_DOCUMENTS. A failed
// decode publishes nothing but error_line, error_column (1-based; 0 unknown) and error_message
// (error_message_length bytes, at most 255, NUL-terminated, at offsetof(YamlResult, error_message)),
// and leaves the session reusable. Compact scalar spans index `source`: plain single-line scalars always;
// quoted, multi-line and block scalars only with TD_YAML_RETAIN_SOURCE (else 0).
//
// session_retain builds missing lookup directories, then moves arena blocks and input into a zero-initialized result
// that owns them (release with td_yaml_release). session_reset invalidates the borrowed document in
// O(1) without freeing (a native leaf). session_release frees the session; retained results stay
// valid.
// session_write_text accepts a live session and a zeroed YamlTextResult. With null output and
// zero capacity it reports required_capacity (a checked upper bound from decoded strings). With
// an aligned output buffer it writes Latin-1/UTF-16LE bytes and publishes one entry per compact
// word in table. TD_YAML_TEXT_TOO_SMALL reports the required capacity without invalidating the
// document, so the caller can grow and retry. A successful table is borrowed until reset/decode;
// the caller owns the byte buffer. No partial table is published on failure.
//
// ABI4 publishes td_tape_view through view. Scalar lexical spans and directory key hashes are
// embedded in compact words; no parallel lexical or key-hash arrays are allocated or published.
#endif
