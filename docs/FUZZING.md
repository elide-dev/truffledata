# Untrusted-input testing

This is the first continuous fuzzing tranche. Passing it is evidence for the exercised
paths, not a claim that every parser, native library or SVM machine-code path is sanitized.
Production build settings and performance contracts are unchanged.

## Coverage

| Surface | Harness and oracle | Execution |
| --- | --- | --- |
| JSON native sessions | Arbitrary UTF-8/UTF-16/Latin-1 bytes; validation/directory modes; full/partial/repeated decode, retained ownership, reset and recovery; compact-tape structural validation | libFuzzer + ASan/UBSan |
| YAML native sessions | Arbitrary encoded input; schema, duplicate, merge and alias policies; full/partial/repeated decode, retention, reset and recovery; compact-tape structural validation | libFuzzer + ASan/UBSan |
| CSV native indexing | Arbitrary bytes and format, carry derived from preceding chunks, chunk boundaries; each host-supported kernel against scalar entries and carry | libFuzzer + ASan/UBSan |
| External serialized slabs | Arbitrary bytes and mutations of valid slabs for nine schemas, including a recursive message; reference, bitmap, identity and public-copy agreement including logical usage | Jazzer on JVM |
| Buffa Rust | Existing wire, typed, slab and ownership regression tests | Rust ASan plus instrumented native dependencies |

Every fuzz input describes a deterministic case; sessions are created within that case.
Unexpected internal errors, verifier disagreements and invalid produced tapes fail the harness.
Only documented malformed-input/resource-limit failures are accepted. Native pointers retain
valid provenance and alignment: these are untrusted-data tests, not deliberate violations of
unsafe C ABI pointer contracts. Input sizes, per-document budgets, per-input time and process
RSS are bounded. Timeout/OOM artifacts require triage rather than being silently discarded.

## Run

Use the normal repository Bazel wrapper and serialize local Bazel invocations across worktrees.
The security profile targets **GNU Linux amd64**; the normal RBE platform is suitable. The
upstream instrumented dependency add-on is currently published for that architecture only.

```sh
bazel test --config=asan //tools/fuzz:sanitizer_canary_test \
  //json/native:session_replay //yaml/native:session_replay //csv/native:index_replay
bazel test --config=asan-rust //protobuf-buffa/native:test
bazel test --config=fuzz --nocache_test_results \
  //json/native:session_fuzz //yaml/native:session_fuzz //csv/native:index_fuzz
bazel test --nocache_test_results //protobuf-runtime:slab_replay //protobuf-runtime:slab_fuzz
```

`*_replay` tests run without sanitizer flags as part of ordinary `//...` testing. Campaigns
are explicit/manual Bazel targets. Native campaign binaries require `--config=fuzz` so an
uninstrumented campaign cannot accidentally report success. `--config=fuzz` instruments native dependencies with
`fuzzer-no-link`, with libFuzzer's main linked only into the campaign executable. ASan/UBSan
canaries must emit their expected diagnostic and fail; a clean exit is a failing gate.

PR campaigns run for 30 seconds per harness; scheduled campaigns run for 900 seconds.
For a longer run, pass `--test_env=TD_FUZZ_SECONDS=900 --test_timeout=1200`.
The checked-in seeds are copied to a writable output corpus. New corpus entries, crash,
timeout and leak reproducers, and test logs are preserved in Bazel's test outputs and uploaded
by the workflow for 30 days. Fuzz results are not reused from the test cache.

To reproduce a native finding, build its `*_fuzzer` with `--config=fuzz`, then execute that
binary on the single saved artifact (`-runs=1 artifact`). Minimize with libFuzzer's
`-minimize_crash=1` and commit the minimized artifact in the harness's `corpus/` directory,
with a focused regression assertion when it explains the bug better. JSON/YAML corpus files
start with two control bytes; CSV starts with five. Slab files start with schema and mutation
mode bytes. Preserve these bytes when minimizing. The same files run in ordinary replay tests.
Do not publish a suspected exploitable reproducer before security triage.

## Upstream sanitizer libraries

The profile pins the same Elide toolchain version as production and overlays its checksum-pinned
ASan add-on in a separate Bazel repository. The add-on supplies instrumented libc++/libc++abi
and native components. The compiler wrapper selects that sysroot, headers and library directory;
the production toolchain repository is not modified. Native dependencies are linked statically
into each test executable, while the system C library remains dynamic for ASan. ASan/UBSan compilation also covers our
C++ sources, simdjson, rapidyaml and simdutf. Rust uses `-Zsanitizer=address` and
`-Zexternal-clangrt` so mixed Rust/C++ code shares Clang's sanitizer runtime. This does not
claim an instrumented Rust standard library or Rust UBSan coverage.

See [Elide toolchain sanitizer support](https://github.com/elide-dev/toolchain/blob/main/docs/reference.md#sanitizers).
The matching [Graal native-library add-ons](https://github.com/elide-dev/graal/blob/elide-25.5.x/substratevm/docs/native-sanitizer-variants.md)
are relevant to future JNI/native-resource integration campaigns. These standalone parser
harnesses do not link Graal native support libraries, so they do not download that add-on.
Graal's add-on covers its listed native support archives/resources; it does **not** instrument
Java machine code generated by Native Image, JDK native libraries or separate GC libraries.
Jazzer instrumentation is Java fuzz coverage, not ASan instrumentation of the JVM.

## Findings

- **YAML: input ending inside a flow sequence's implicit mapping** (first campaign, 6,660
  executions; reproducer `[?\u`, minimized to `[?` and `[:`). rapidyaml 0.16.0 ends the stream
  without an error in that state: its end-of-input check looks for an open flow sequence or
  mapping, but the implicit single-pair mapping state (`RSEQIMAP`) carries neither flag, so no end
  events arrive and no "missing terminating" error is raised. The decoder then published a tape
  whose containers were never closed (`container next out of range` from the tape validator), and
  `retain()` copied that tape. Not a memory-safety defect under ASan/UBSan; a malformed tape handed
  to consumers. Fixed in the decoder's `finish()`: an open frame at end of parse is a syntax error
  (`missing terminating ]`, classified as truncation by the stream decoder). Regressions:
  `decoder_test`, `stream_test`, and `yaml/native/corpus/session/{explicit-key-eof,empty-key-eof,explicit-key-eof-found}`.
  The upstream behaviour is worth reporting to rapidyaml; the decoder-side check stays regardless.

- **YAML: bare tag after a closed root collection** (first PR campaign after the fix above,
  3,137 executions; reproducer `a:(\xc8&a [wo]\n!bo\n\n`, whose failing step is the suffix
  `[wo]\n!bo\n`). rapidyaml 0.16.0 emits a second root node (`=VAL <!bo> :`) instead of a parse
  error. The decoder already refused a second root, but as `status_internal`, which the harness
  treats as unexpected. A document holds one root node, so this is now a syntax error.
  Regressions: `decoder_test` and `yaml/native/corpus/session/{root-then-tag,root-then-tag-found}`.

- **YAML: pending-annotation overflow in rapidyaml** (second PR campaign, 51,968 executions;
  UBSan `index 2 out of bounds for type 'Entry[2]'` at `parse_engine.def.hpp:4345`; reproducer
  `!b\n!b\n!bow\naeu`, minimized to `!a\n!b\n!c\nx`). rapidyaml 0.16.0 stores pending tags and
  anchors in a two-entry array bounded only by an assertion that production builds compile out, so
  a third consecutive annotation writes past the array inside the parse engine. This is the one
  memory-safety finding so far and it is upstream. Fixed by a pinned source patch
  (`bazel/patches/rapidyaml-annotation-bounds.patch`) that raises the parse error
  `too many annotations` in all three `_add_annotation` overloads; the yaml-test-suite conformance
  lane passes unchanged. Regressions: `decoder_test` and
  `yaml/native/corpus/session/{three-tags,three-tags-found}`. Report upstream with the patch.

Harness aborts print the reason (unexpected status and message, or the tape validator's
diagnostic) before `abort()`, so a libFuzzer "deadly signal" can be triaged from its log.

## Remaining coverage

- Coverage-guided Buffa wire/typed/projection harnesses (the Rust lane here is regression testing).
- JSON/YAML streaming, typed projections, C ABI output capacities and allocation-failure injection.
- Managed lifetime misuse sequences and replay of discovered semantic cases through production SVM.
- ARM SIMD campaign coverage and separate concurrency/TSan tests.
- Persisted/minimized cross-run corpus promotion; currently discoveries are retained as artifacts
  and promoted by review, rather than automatically used by the next scheduled run.
- Full independent semantic oracles for JSON/YAML, beyond structural tape validation and recovery.

The existing SVM acceptance, allocation and compiled-graph tests continue in normal CI. They
remain distinct from sanitizer coverage. Future MSan/JNI lanes must use the matching upstream
instrumented libraries throughout their native dependency closure.
