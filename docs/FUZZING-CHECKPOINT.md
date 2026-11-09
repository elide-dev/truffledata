# Fuzzing checkpoint — 2026-11-08

Paused at Sam's request. This is a draft PR, not completed security qualification.
No production parser code changed. Resume only on instruction.
The checkpoint commit skips automatic CI to avoid starting new campaigns during the pause.

## Implemented

Four coverage-guided harnesses: JSON/YAML sessions and CSV kernels with libFuzzer,
and external slabs with Jazzer. Ordinary corpus replay tests; pinned Elide toolchain
ASan dependency add-on; C++ ASan/UBSan and Rust ASan profiles; instrumentation canaries;
PR/nightly workflow with saved corpora and reproducers. See [coverage and gaps](FUZZING.md).
Graal's sanitizer native-resource add-ons were inspected; these standalone harnesses do
not link those resources and do not claim sanitizer instrumentation of generated SVM Java code.

## Evidence

- [Native replay and Rust ASan](https://app.buildbuddy.io/invocation/9cb53046-d895-4a54-9c58-22d78d7fc54e): all four selected tests passed. Rust was tested before the final static-dependency/run-wrapper configuration changes; recheck that exact final profile on resume.
- [Sanitized parser regressions](https://app.buildbuddy.io/invocation/9558a858-b989-420f-9d0a-af215234e4f3): canaries, CSV API/index, JSON decoder and million-case shape test, YAML decoder, compact tape, and corpus replays passed. This invocation also contains three initial harness failures, subsequently corrected: Jazzer argument forwarding, impossible CSV initial carry, and a YAML recovery fixture invalid under JSON schema.
- [Latest campaigns](https://app.buildbuddy.io/invocation/60abb37f-93e2-46f1-b934-ea78162e7bec): six of seven targets passed. JSON: 252,566 executions; CSV: 1,362,753; slabs: 3,896,398, each in 61 seconds. CSV/YAML/slab corpus replays passed. YAML fuzzing failed after 6,660 executions.
- ShellCheck, actionlint and git diff whitespace checks passed. Normal full CI has not run for this branch.

## Unresolved YAML finding

Preserved [input](../tools/fuzz/findings/yaml-7d9b50ccd332b5cd8bbdd1ee1cd680f3b87dfead.bin)
and [raw log (gzip)](../tools/fuzz/findings/yaml-session.log.gz). The six bytes are
`40 20 5b 3f 5c 75` (base64 `QCBbP1x1`). The first two are harness controls;
the document bytes are `[?\u`. The control selects core schema and retained source.

libFuzzer reports an abort in the decode lambda, called by the first full-document decode.
The trace does not establish a memory-safety defect. The harness currently aborts on either
an unexpected internal status or invalid produced tape; add diagnostic text and reproduce
to determine which. Do not suppress the finding or broaden accepted errors without diagnosis.
The finding is deliberately outside the ordinary replay corpus until triaged.

On Linux amd64, build `//yaml/native:session_fuzzer` with `--config=fuzz`, then run the
resulting executable with `-runs=1` and the saved input path. A copy of the exact failing
executable is downloaded at `bazel-bin/yaml/native/session_fuzzer` in this worktree.

## Resume

1. Diagnose/minimize the YAML finding, fix the harness or parser as warranted, and add a regression.
2. Re-run the affected YAML checks and the final Rust sanitizer profile; verify ordinary unsanitized corpus replay and Gazelle consistency.
3. Review the staged diff, improve any remaining harness/build integration issues, and run normal PR CI without the checkpoint's skip marker.
4. Consider enriching slab raw-byte seeds with existing golden slabs and an explicitly deep recursive fixture; the current harness uses deterministic generated seeds plus a recursive schema.

Worktree: `/Users/sam/workspace/graalvm/td-security-fuzz`.
Branch: `test/untrusted-input-fuzzing`. Original performance checkout remains untouched.
Local detailed build logs: `/tmp/td-security-*.log`; additional triage logs:
`/tmp/td-security-evidence/`. No further campaign or optimization work is active.
