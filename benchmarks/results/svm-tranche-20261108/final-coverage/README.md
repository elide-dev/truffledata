# Final combined JVM coverage — 2026-11-08

Production revision: `7fca64bed6bf995f6b79f4c352aca145cadd7b79` (all admitted
CSV, JSON and protobuf production changes). Parent-owned documentation/evidence
edits were present and left untouched; `source.json` records the tested tree,
working-tree state, source hashes and pinned SDK archive hash.

[Invocation b436036f](https://app.buildbuddy.io/invocation/b436036f-46bf-4662-b8ff-c9d65a474d7e)
passed all nine coverage targets, exit 0, elapsed 105.760 seconds. Their XML reports
contain 455 tests with zero failures, errors or skips. Eight targets executed;
`//tape/tests:jvm` used its valid cached result. `tests.json` preserves per-target
counts. Every target's original log and XML is retained under `testlogs/`.

Both post-processors exited 0:

- `reports.py coverage` validated 201 production source files across 12 modules.
- `coverage_contract.py` passed the access-method contract with its existing
  explicit native-only/unreachable exemptions. No exemption or gate was changed.

The existing `codecov` lane excludes allocation/JIT-tagged tests because coverage
instrumentation changes their budgets. Native acceptance and graph/allocation
qualification are separate gates. This report does not claim complete branch
coverage, Arm qualification or release completion.

```sh
bzl coverage //tests:test //protobuf-runtime:test //protobuf-buffa/tests:jvm \
  //protobuf-buffa/tests:jvm_non_strict //protobuf-buffa/tests:jvm_no_native_access \
  //json/tests:jvm //yaml/tests:jvm //tape/tests:jvm //csv/tests:jvm \
  --config=codecov --test_output=errors
python3 .github/scripts/reports.py coverage
python3 .github/scripts/coverage_contract.py
```

`coverage.log.gz` contains the full invocation output; validator/contract logs,
combined `lcov.info.gz` and `gaps.json.gz` preserve the complete coverage evidence.
All gzip archives use a zero timestamp. `artifacts-sha256.json` hashes every other
file in this directory. Artifacts were copied before releasing the shared Bazel
slot, so a subsequent architecture switch cannot replace their source symlinks.
The already-passing 14 reporting-tool and four paired-runner unit tests are
archived separately by the integration owner in `../report-tests/`.
