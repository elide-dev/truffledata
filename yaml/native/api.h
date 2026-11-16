#ifndef TRUFFLEDATA_YAML_API_H
#define TRUFFLEDATA_YAML_API_H
#include "yaml/native/generated/seam.h"

// The integer protocol (status codes, decode and document flags, option enumerations, yaml_meta
// layout, typed-plan codes and limits) is declared in yaml.seam and defined by the generated
// seam.h (TRUFFLEDATA_YAML_*). The TD_YAML_* names below are aliases of those definitions, kept for
// existing C callers; they hold no second copy of any number.

// ABI status codes (enum Status), as JSON's plus the YAML ones. No C++ exception (and no rapidyaml
// exception) crosses the generated C boundary.
#define TD_YAML_OK TRUFFLEDATA_YAML_STATUS_OK
#define TD_YAML_ARGUMENT TRUFFLEDATA_YAML_STATUS_ARGUMENT
#define TD_YAML_LIMIT TRUFFLEDATA_YAML_STATUS_LIMIT
#define TD_YAML_ALLOCATION TRUFFLEDATA_YAML_STATUS_ALLOCATION
#define TD_YAML_SYNTAX TRUFFLEDATA_YAML_STATUS_SYNTAX
#define TD_YAML_INTERNAL TRUFFLEDATA_YAML_STATUS_INTERNAL
#define TD_YAML_CONTRACT TRUFFLEDATA_YAML_STATUS_CONTRACT
#define TD_YAML_MULTIPLE_DOCUMENTS TRUFFLEDATA_YAML_STATUS_MULTIPLE_DOCUMENTS
#define TD_YAML_STREAM_EMPTY TRUFFLEDATA_YAML_STATUS_STREAM_EMPTY
#define TD_YAML_STREAM_TRUNCATED TRUFFLEDATA_YAML_STATUS_STREAM_TRUNCATED
#define TD_YAML_TEXT_TOO_SMALL TRUFFLEDATA_YAML_STATUS_TEXT_TOO_SMALL

// session_decode and stream_next flags (flags DecodeFlags). TD_YAML_WANT_UTF16_TEXT and its inline
// output arguments remain reserved; use session_write_text after decode. YAML must size text from
// decoded strings, because aliases used as mapping keys can expand their text beyond any input-sized
// allocation estimate.
#define TD_YAML_WANT_UTF16_TEXT TRUFFLEDATA_YAML_DECODE_FLAGS_WANT_UTF16_TEXT
#define TD_YAML_RETAIN_SOURCE TRUFFLEDATA_YAML_DECODE_FLAGS_RETAIN_SOURCE
// Borrowed scan/export documents omit directories by default; key lookup still scans keys.
// Set this for random access. Retain always publishes directories, building them if needed.
#define TD_YAML_WANT_DIRECTORIES TRUFFLEDATA_YAML_DECODE_FLAGS_WANT_DIRECTORIES

// YamlOptionsStruct enumerations (YamlOptions on the Java side; enums Schema, DuplicateKeys,
// ComplexKeys, Aliases, UnknownTags and VersionDirective). max_depth is at most
// TRUFFLEDATA_YAML_MAX_DEPTH.
#define TD_YAML_SCHEMA_CORE TRUFFLEDATA_YAML_SCHEMA_CORE
#define TD_YAML_SCHEMA_JSON TRUFFLEDATA_YAML_SCHEMA_JSON
#define TD_YAML_SCHEMA_FAILSAFE TRUFFLEDATA_YAML_SCHEMA_FAILSAFE
#define TD_YAML_SCHEMA_YAML_1_1 TRUFFLEDATA_YAML_SCHEMA_YAML_1_1
#define TD_YAML_DUPLICATE_KEYS_REJECT TRUFFLEDATA_YAML_DUPLICATE_KEYS_REJECT
#define TD_YAML_DUPLICATE_KEYS_LAST_WINS TRUFFLEDATA_YAML_DUPLICATE_KEYS_LAST_WINS
// Not in YamlOptions yet (the plan's deferred ComplexKeys.PAIRS shape): a mapping with a duplicate
// key, or with a collection key under TD_YAML_COMPLEX_KEYS_PAIRS, is stored as an ARRAY of
// 2-element [key, value] ARRAYs whose yaml_meta style is 7. The yaml-test-suite target uses both.
#define TD_YAML_DUPLICATE_KEYS_PAIRS TRUFFLEDATA_YAML_DUPLICATE_KEYS_PAIRS
#define TD_YAML_COMPLEX_KEYS_REJECT TRUFFLEDATA_YAML_COMPLEX_KEYS_REJECT
#define TD_YAML_COMPLEX_KEYS_PAIRS TRUFFLEDATA_YAML_COMPLEX_KEYS_PAIRS
#define TD_YAML_ALIASES_ALLOW TRUFFLEDATA_YAML_ALIASES_ALLOW
#define TD_YAML_ALIASES_REJECT TRUFFLEDATA_YAML_ALIASES_REJECT
#define TD_YAML_UNKNOWN_TAGS_PRESERVE TRUFFLEDATA_YAML_UNKNOWN_TAGS_PRESERVE
#define TD_YAML_UNKNOWN_TAGS_REJECT TRUFFLEDATA_YAML_UNKNOWN_TAGS_REJECT
#define TD_YAML_VERSION_DIRECTIVE_IGNORE TRUFFLEDATA_YAML_VERSION_DIRECTIVE_IGNORE
#define TD_YAML_VERSION_DIRECTIVE_HONOR TRUFFLEDATA_YAML_VERSION_DIRECTIVE_HONOR

// YamlResult.document_flags (flags DocumentFlags).
#define TD_YAML_DOC_EXPLICIT_START TRUFFLEDATA_YAML_DOCUMENT_FLAGS_EXPLICIT_START  // `---`
#define TD_YAML_DOC_EXPLICIT_END TRUFFLEDATA_YAML_DOCUMENT_FLAGS_EXPLICIT_END      // `...`
// non-finite F64 nodes (.inf/.nan); the descriptor sets MYNA_VALUE_ALLOW_NON_FINITE
#define TD_YAML_DOC_NONFINITE TRUFFLEDATA_YAML_DOCUMENT_FLAGS_NONFINITE
// `source` is a pristine copy (TD_YAML_RETAIN_SOURCE)
#define TD_YAML_DOC_SOURCE_RETAINED TRUFFLEDATA_YAML_DOCUMENT_FLAGS_SOURCE_RETAINED
// the input held a document (otherwise the root is null)
#define TD_YAML_DOC_PRESENT TRUFFLEDATA_YAML_DOCUMENT_FLAGS_PRESENT
// resolved with the YAML 1.1 schema (option or HONOR)
#define TD_YAML_DOC_SCHEMA_1_1 TRUFFLEDATA_YAML_DOCUMENT_FLAGS_SCHEMA_1_1

// yaml_meta (flags Meta, one uint32_t per compact word, payload entries zero): the style range
// (enum Style), anchored, alias, the key_kind range (enum KeyKind) and the tag range: the tag index
// (1-based into `tags`; 0 none) or, on aliases, private producer bookkeeping (consumers resolve the
// REF word). seam.h has the masks, shifts and range getters (truffledata_yaml_meta_style, ...).
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
// next document as a borrowed result (as session_decode does). Its flags accept TD_YAML_RETAIN_SOURCE
// and TD_YAML_WANT_DIRECTORIES independently for each document. It returns TD_YAML_OK,
// TD_YAML_STREAM_EMPTY (no complete document buffered yet, or the finished stream is exhausted),
// TD_YAML_STREAM_TRUNCATED (the finished input ended inside the final document) or the failing
// document's status; a failed stream stays failed. documents(byte[]) is a stream fed the whole
// buffer and finished.
//
// Sessions (td_yaml_session_*) follow td_json_session_* exactly. A session is a thread-confined
// decode context: one rapidyaml parse engine and event handler, an input buffer and a block arena,
// reused across documents; callers serialize every call on one session. session_create copies
// YamlOptionsStruct (max_depth <= TRUFFLEDATA_YAML_MAX_DEPTH, enumerations as above) and returns null on
// invalid options or allocation failure.
//
// session_decode accepts zero or one document (zero yields a root null) and writes a *borrowed*
// YamlResult: owner is null, and every pointer stays valid only until the next call on the session.
// Input is UTF-8 (a BOM is skipped) or UTF-16/UTF-32 with a BOM; it is validated as strict UTF-8 and
// c-printable before rapidyaml sees it. A second document is TD_YAML_MULTIPLE_DOCUMENTS. A failed
// decode publishes nothing but error_line, error_column (1-based; 0 unknown) and error_message
// (error_message_length bytes, at most TRUFFLEDATA_YAML_ERROR_MESSAGE_CAPACITY - 1, NUL-terminated, at offsetof(YamlResult, error_message)),
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
