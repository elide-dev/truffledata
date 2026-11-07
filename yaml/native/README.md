# Native YAML decoder

rapidyaml 0.16.0 drives `yaml::handler`, a `ParseEngine` event handler that writes Myna ABI v3
nodes straight into the session arena through the shared presized `tape_writer` (`//tape/native`).
There is no rapidyaml tree, event array or DOM in between. The plan is
`docs/superpowers/plans/2026-11-06-yaml.md`; rulings are in its ledger.

## Layout

| File | Role |
| --- | --- |
| `input.h` | BOM detection, UTF-16/UTF-32 transcoding, strict UTF-8 (simdutf) and c-printable validation in 64-byte SSE2/NEON blocks fused with the session copy, the LF bitmap |
| `scalars.h` | `resolve(text, style, schema, tag)`: core, JSON, failsafe and YAML 1.1 schemas, explicit core tags, key stringification |
| `tags.h` | Tag URIs and kinds; `!!binary` (simdutf base64) and `!!timestamp` validation |
| `decoder.h` | `yaml::handler`: nodes, `lexical` spans, `key_hashes` and `yaml_meta` per node; anchors, aliases, tags, directives; duplicate keys, retroactive `[a: b]` mappings, the pairs representation |
| `merge.h` | Merge keys (`<<`), resolved when their mapping closes |
| `session.h` | Sessions with the JSON session's semantics: decode, borrow, retain, reset |
| `stream.h` | Multi-document streams: a line splitter over a carry-over buffer, one document decoded at a time |
| `errors.h` | `yaml::error` (status, line, column, fixed 256-byte message) |
| `api.h`, `api.cc` | The C ABI (`td_yaml_*`); no exception crosses it |
| `yaml.seam` | Myna seam (`abi=1`): `YamlResult`, `YamlOptionsStruct` and the session imports |
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

One tape per document, exactly as JSON. Scalars are inline up to 16 bytes, otherwise a region-1
reference into the session input buffer that rapidyaml filtered in place; the few scalars that grow
while filtering (rapidyaml moves them into its arena) are copied into the tape's owned text.

Every node has three side-table entries:

- `lexical` (`uint64`, shared with JSON): `offset << 32 | length` into `source`. Plain single-line
  scalars always; quoted, multi-line plain and block scalars only with `TD_YAML_RETAIN_SOURCE`
  (which keeps a pristine copy, because rapidyaml filters in place); 0 otherwise. Multi-line plain
  scalars are told apart from single-line ones by the LF bitmap recorded during validation.
- `key_hashes` (`uint32`, shared with JSON): the ASCII bit, and on keys the low 15 bits of the
  FNV-1a hash and the key's offset from its object, so JSON's position-hinted reads work unchanged.
- `yaml_meta` (`uint32`, YAML only): style (bits 0-2; 7 marks a pair, below), anchored (3),
  alias (4), key kind (5-7), tag index or alias ordinal (8-31).

Per-document tables: `tags` (interned URIs without brackets, `tag:yaml.org,2002:str`, `!local`,
`!`), `anchors` (node and name, in definition order, so an anchor's id is its index),
`alias_targets` (the anchored node per alias ordinal), `directives` (`%TAG` handle and prefix) and
the `%YAML` version; names and URIs index one `strings` byte table.

## Anchors, aliases and merge keys

An alias is a NULL node with the alias bit; its ordinal indexes `alias_targets`, which always
names the anchored node itself, so readers resolve an alias with one load and nothing is expanded
natively: a billion-laughs document decodes in O(input) (`alias_graph_is_linear`: 10 levels of 10
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
is in `yaml_meta`, and the source spelling in `lexical`.

Non-finite floats (`.inf`, `-.inf`, `.nan`) are written as raw IEEE F64 bits through
`tape_writer::nonfinite`, the single entry point; `TD_YAML_DOC_NONFINITE` marks such documents. Myna
descriptor of such a document sets Myna 0.10.0's `MYNA_VALUE_ALLOW_NON_FINITE` (flag bit 0,
elide-dev/myna#52), which value ABI v3 builders and Myna's Java readers honour; finite documents
keep flags 0, exactly like JSON tapes.

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
SWAR, the rest through `resolve_core_fast`), the ASCII bit, the inline node
(`tape_writer::string_words`) and, for keys, a 256-slot FNV-1a memo. Duplicate keys go through a
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
