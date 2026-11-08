# Native YAML decoder

rapidyaml 0.16.0 drives `yaml::handler`, a `ParseEngine` event handler that writes compact tape
words straight into the session arena through the shared presized `ctape_writer` (`//tape/native`).
There is no rapidyaml tree, event array or DOM in between. The plan is
`docs/superpowers/plans/2026-11-06-yaml.md`; rulings are in its ledger.

## Layout

| File | Role |
| --- | --- |
| `input.h` | BOM detection, UTF-16/UTF-32 transcoding, strict UTF-8 (simdutf) and c-printable validation in 64-byte SSE2/NEON blocks fused with the session copy, the LF bitmap |
| `scalars.h` | `resolve(text, style, schema, tag)`: core, JSON, failsafe and YAML 1.1 schemas, explicit core tags, key stringification |
| `tags.h` | Tag URIs and kinds; `!!binary` (simdutf base64) and `!!timestamp` validation |
| `decoder.h` | `yaml::handler`: compact words with embedded spans/directories and `yaml_meta` per word; anchors, aliases, tags, directives; duplicate keys, retroactive `[a: b]` mappings, the pairs representation |
| `merge.h` | Merge keys (`<<`), resolved when their mapping closes |
| `session.h` | Sessions with the JSON session's semantics: decode, borrow, retain, reset |
| `stream.h` | Multi-document streams: a line splitter over a carry-over buffer, one document decoded at a time |
| `errors.h` | `yaml::error` (status, line, column, fixed 256-byte message) |
| `api.h`, `api.cc` | The C ABI (`td_yaml_*`); no exception crosses it |
| `yaml.seam` | Myna seam (`abi=4`): compact `YamlResult`, options, session and typed-plan imports |
| `bench.cc` | Native reference rows: rapidyaml's integer handler and tree, libyaml, the session and stream APIs (`bazel test //yaml/native:bench --test_output=all`, manual, RBE) |
| `sampler.h` | Benchmark only: a SIGPROF frame-pointer sampler for RBE workers (`//yaml/native:bench_profile`) |
| `testing.h`, `reference_tape.h` | Test-only helpers; the probe's reference tape (rapidyaml's integer handler into `myna::value_builder`) |
| `tape_events.h` | Test-only: renders a document's tape and side tables back to yaml-test-suite events (`//yaml/conformance`, plan Task 8) |
| `ryml_events.h` | Task 1's test-only event renderer over rapidyaml itself |

## Build configuration

rapidyaml always builds with `RYML_USE_ASSERT=0`, `C4_USE_ASSERT=0`, `RYML_WITH_TAB_TOKENS` and
`RYML_DEFAULT_CALLBACK_USES_EXCEPTIONS`, propagated through `defines` so the decoder's own
`ParseEngine<yaml::handler>` instantiation inherits them. The handler always runs with
`resolve_tags(true).resolve_tags_all(true)`. `src_extra` (the integer handler) is linked only by the
reference tape and the benchmark (`@rapidyaml//:ryml_ints`, testonly).

## Tape and side tables

One `td_tape_view` per document, shared with JSON. Plain unfiltered strings reference the
retained input. Quoted/folded strings filtered in place use a YAML-only WIDE/non-OWNED word
pair: decoded input span plus independent raw span. Normalized strings, or filtered strings
with pristine-source retention, use owned text with embedded source spans.
Numbers and containers occupy two words; ordinary strings, booleans, nulls and references use one
unless an owned/raw-span payload needs a second word. The `TD_TAPE_YAML` flag identifies plain
string spans; `TD_TAPE_HAS_META` identifies the metadata table.

Scalar lexical spans, ASCII/KEY flags and object directory entries live in compact words. Plain
single-line scalar spans are always retained; quoted, multiline and block source spellings require
`TD_YAML_RETAIN_SOURCE`. Every word has one `yaml_meta` entry (payload entries are zero): style
(bits 0–2), anchored (3), alias (4), key kind (5–7), tag index or private alias bookkeeping (8–31).

Per-document tables hold interned tag URIs, anchors (word offset and name), directives and version.
Names and URIs index one `strings` table. Alias targets are carried by REF words and are not a
published side array. The producer retains a private target table for key aliases and rewrites.

Strict duplicate rejection announces each canonical key to the compact writer and checks equal
keys while producing its sorted directory. Merge, last-wins and pairs policies retain incremental
key checks and rebuild directories after rewrites. A duplicate under strict rejection is diagnosed
when its mapping closes; the location can therefore follow the second key.

`session_write_text` publishes a borrowed word-indexed table into a caller-owned Latin-1/UTF-16LE
byte buffer. A null-buffer query reports a checked capacity bound from decoded string lengths;
`TD_YAML_TEXT_TOO_SMALL` leaves the document live for a resize and retry. The initial Java buffer
estimate is not a universal bound: canonical numeric keys and repeated aliased keys can expand
beyond twice the input length. Native Image grows the buffer before any returned string shares it.
The text table counts against the native index budget; a decode/reset invalidates the table, while
strings already returned to Java retain their byte array.

## Anchors, aliases and merge keys

A value alias is a REF word naming its anchored node, so readers resolve it without a side table
and nothing is expanded natively: a billion-laughs document decodes in O(input) (`alias_graph_is_linear`: 10 levels of 10
aliases are 121 nodes). An alias to an anchor whose collection is still open is "recursive YAML
alias"; an unbound name is "undefined YAML alias"; a redefined anchor rebinds later aliases; the
number of aliases is bounded by `maxAliases`. An alias used as a key is a STRING key node with its
target's key text and the alias bit.

Merge keys (`<<`, with `mergeKeys` or the 1.1 schema) are resolved when the mapping closes: merged
entries are key copies plus alias-reference values placed at the position of `<<`; explicit keys
and earlier sources win; inline source mappings move into place. Rewrites (merges, last-wins
duplicates, the pairs conversion) relocate anchors and alias targets with their nodes and fail when
they would remove an anchored node.

## Tags and schemas

rapidyaml resolves `!!x`, `%TAG` handles and verbatim tags (`resolve_tags_all`). Core tags are
enforced (`!!int "12"` is 12, `!!int abc` fails); `!!binary` (base64 through simdutf, in chunks
without allocation) and `!!timestamp` are validated and stay strings; `!!set`, `!!omap` and
`!!pairs` keep their natural kinds and their shape is checked when they close; other tags are
preserved (or rejected with `UnknownTags.REJECT`); `!` forces a string. Under `YAML_1_1` the 1.1
forms resolve (`on` is true, `0755` is 493, `1:20` is 80, `2001-12-14` is a string with an implicit
`!!timestamp`) and merge keys are on; `VersionDirective.HONOR` switches a `%YAML 1.1` document to it.

## Streams

`td_yaml_stream_*` split multi-document input at c-forbidden lines (`---`/`...` at column 0
followed by a space, tab, line break or the end of input) without parsing, then decode each
document through the session. `%` directive lines belong to the next document only when no
document is open. The split is pinned against the whole-buffer stream at chunk sizes from 1 byte
to 1 MB (`stream_split_matches_whole_parse`) and, through the conformance target, on every
yaml-test-suite case at 1- and 7-byte chunks.

## Pairs

The plan defers complex keys. Internally (`TD_YAML_COMPLEX_KEYS_PAIRS`,
`TD_YAML_DUPLICATE_KEYS_PAIRS`; not in `YamlOptions` yet) a mapping with a collection key or a
duplicate key is stored as an ARRAY of 2-element `[key, value]` ARRAYs whose style is 7. The
yaml-test-suite target uses it to pass every case through this handler.

Keys are STRING nodes holding the resolved key's canonical text (`null` -> `""`, booleans, decimal
integers, shortest round-trip floats, `.inf`/`-.inf`/`.nan`), so duplicates are detected after
resolution: `{1: a, 0x1: b}`, `{1: a, "1": b}` and `{null: a, ~: b}` are rejected. The original kind
is in `yaml_meta`, and the source spelling is embedded in the scalar words.

Non-finite floats (`.inf`, `-.inf`, `.nan`) use IEEE F64 bits and `TD_TAPE_NONFINITE`;
`TD_YAML_DOC_NONFINITE` marks the document. Myna compatibility copies preserve
`MYNA_VALUE_ALLOW_NON_FINITE`. Double-quoted text is validated after rapidyaml filtering: paired
JSON surrogate escapes combine into one scalar; unpaired surrogates and values above U+10FFFF fail.

## Sessions and memory

`td_yaml_session_*` follow `td_json_session_*`: a thread-confined session reuses its parse engine,
input buffer and block arena; `session_decode` borrows; `session_retain` moves the arena blocks and
input into an owner (`td_yaml_release`); `session_reset` is an O(1) native leaf. rapidyaml's own
allocations (its parser stack and tag cache) go through `c4::yml::Callbacks` into the session's
counted `index_memory`, so they are charged to `maxIndexBytes` and fail like every other allocation.
After warm-up a session decodes without upstream allocation (pinned by `api_test`).

## Fast paths

Nearly every scalar is short, untagged and unanchored. Such a plain scalar of 1-16 bytes still in
the input (under CORE), or a quoted one (any schema, without `TD_YAML_RETAIN_SOURCE`), is loaded
once as two words: the session pads its input buffer with 64 zero bytes, so the 16-byte read is
always in bounds, and bytes past the scalar are masked. The words give the lexical span (a plain
scalar with no byte <= 0x20 is single-line, so its bytes are the source token), the class (first
byte and length: strings, the null and bool spellings, 1-8 digit integers and `d*.d*` decimals by
SWAR, the rest through `resolve_core_fast`), the ASCII bit and, for keys, a 256-slot FNV-1a memo.
Compact directories use all 32 hash bits and are rebuilt at mapping close after rewrites. Duplicate keys go through a
per-mapping `key_set` (16 inline keys behind a 64-bit hash filter, then `tape::key_table`), and
mappings that repeat the keys of one of the last two mappings at their level (records) follow that
key template: matching keys need neither the duplicate check nor the hash. Anything else takes the
general path. `//yaml/native:fast_path_test` decodes the probe fixtures, every yaml-test-suite
input and generated boundary and template corpora with the fast paths on and off
(`session::fast_paths`, a test hook) under seven policies and requires byte-identical tapes, side
tables and errors.

## Profiling on RBE

perf is not available on the RBE workers, so `//yaml/native:bench_profile` samples the same
benchmark binary with `sampler.h` (SIGPROF, frame-pointer stacks; 250 samples per second of CPU on
those kernels) and writes one line of load-relative addresses per sample into the test's undeclared
outputs. Symbolize them against the downloaded binary:

```sh
bazel test //yaml/native:bench_profile --test_output=all --copt=-gline-tables-only
bazel build //yaml/native:bench_profile --copt=-gline-tables-only --remote_download_outputs=toplevel
llvm-symbolizer --obj=bazel-out/rbe_linux_amd64-opt/bin/yaml/native/bench_profile --inlining \
  < <(tr ' ' '\n' < bazel-testlogs/yaml/native/bench_profile/test.outputs/profile-td-records-block.yaml.txt | sed 's/^/0x/')
```

`benchmarks/results/yaml-decoder-*/` keep the folded stacks and summaries.

## Verification

```sh
bazel test //yaml/native:all //tape/native:all //yaml/conformance:yaml_test_suite //json/native:all
bazel test //yaml/native:all //tape/native:all \
  --copt=-fsanitize=address,undefined --copt=-fno-sanitize-recover=all --linkopt=-fsanitize=address,undefined
bazel test //yaml/native:bench --test_output=all   # manual; RBE; three passes, best per variant
```

Borrowed native session and stream documents omit lookup directories unless decode or stream-next sets
`TD_YAML_WANT_DIRECTORIES` (4). All duplicate policies still apply, and generic key lookup scans
when no directory is present. Retain builds missing directories without moving tape words; a
failed build leaves the borrowed descriptor valid for retry. Java retained and callback reads
request directories before decode, while sink export and validation omit them.

ABI 5 gives `stream_next` per-document flags: by-key iterators request lookup directories,
while sequential sink traversal omits them. Both honor source retention. Reserved flags fail
before consuming buffered input.

Checked short-key templates own their inline bytes and survive stream document/input reuse.
Incomplete templates are invalidated on errors before reuse; cache misses still check every key.
