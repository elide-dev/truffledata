# Native YAML decoder

rapidyaml 0.16.0 drives `yaml::handler`, a `ParseEngine` event handler that writes Myna ABI v3
nodes straight into the session arena through the shared presized `tape_writer` (`//tape/native`).
There is no rapidyaml tree, event array or DOM in between. The plan is
`docs/superpowers/plans/2026-11-06-yaml.md`; rulings are in its ledger.

## Layout

| File | Role |
| --- | --- |
| `input.h` | BOM detection, UTF-16/UTF-32 transcoding, strict UTF-8 (simdutf) and c-printable validation, the LF bitmap |
| `scalars.h` | `resolve(text, style, schema, tag)`: core, JSON and failsafe schemas, explicit core tags, key stringification |
| `decoder.h` | `yaml::handler`: nodes, `lexical` spans, `key_hashes` and `yaml_meta` per node; duplicate keys, retroactive `[a: b]` mappings |
| `session.h` | Sessions with the JSON session's semantics: decode, borrow, retain, reset |
| `errors.h` | `yaml::error` (status, line, column, fixed 256-byte message) |
| `api.h`, `api.cc` | The C ABI (`td_yaml_*`); no exception crosses it |
| `yaml.seam` | Myna seam (`abi=1`): `YamlResult`, `YamlOptionsStruct` and the session imports |
| `bench.cc` | Native reference rows (`bazel test //yaml/native:bench --test_output=all`, manual, RBE) |
| `testing.h`, `reference_tape.h` | Test-only helpers; the probe's reference tape (rapidyaml's integer handler into `myna::value_builder`) |
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
- `yaml_meta` (`uint32`, YAML only): style (bits 0-2), anchored (3), alias (4), key kind (5-7),
  tag index or alias ordinal (8-31).

Keys are STRING nodes holding the resolved key's canonical text (`null` -> `""`, booleans, decimal
integers, shortest round-trip floats, `.inf`/`-.inf`/`.nan`), so duplicates are detected after
resolution: `{1: a, 0x1: b}`, `{1: a, "1": b}` and `{null: a, ~: b}` are rejected. The original kind
is in `yaml_meta`, and the source spelling in `lexical`.

Non-finite floats (`.inf`, `-.inf`, `.nan`) are written as raw IEEE F64 bits through
`tape_writer::nonfinite`, the single entry point; `TD_YAML_DOC_NONFINITE` marks such documents. Myna
ABI v3 builders and `ValueTape.validate()` reject them until elide-dev/myna#52 adds a descriptor
flag; then `tape_writer::nonfinite_flag` becomes that flag and nothing else changes.

## Sessions and memory

`td_yaml_session_*` follow `td_json_session_*`: a thread-confined session reuses its parse engine,
input buffer and block arena; `session_decode` borrows; `session_retain` moves the arena blocks and
input into an owner (`td_yaml_release`); `session_reset` is an O(1) native leaf. rapidyaml's own
allocations (its parser stack and tag cache) go through `c4::yml::Callbacks` into the session's
counted `index_memory`, so they are charged to `maxIndexBytes` and fail like every other allocation.
After warm-up a session decodes without upstream allocation (pinned by `api_test`).

## Verification

```sh
bazel test //yaml/native:all //tape/native:all
bazel test //yaml/native:all //tape/native:all \
  --copt=-fsanitize=address,undefined --copt=-fno-sanitize-recover=all --linkopt=-fsanitize=address,undefined
bazel test //yaml/native:bench --test_output=all   # manual; RBE
```
