# Local integration qualification — 2026-11-08

Production merge `151c1d7` contains the measured slab allocation and JSON changes,
plus B4 tests/evidence with its regressing production prototype restored. The
additional test identified in `source.json` covers ASCII metadata on input-backed
and pooled strings through both lazy and preloaded text cursors.

The first combined nine-suite coverage run (`6d4a1ca2`) passed every test but
identified one uncovered access method, `TapeReader.isAscii`. The test-only fix
closes that gap. Final invocation
[018a698d](https://app.buildbuddy.io/invocation/018a698d-5e75-404e-83d0-15bfe6bd8e9b)
passes all nine suites, LCOV validation for 201 production source files across
12 modules, and the access-method coverage contract with its existing explicit
native-only exemptions. All 14 reporting-tool unit tests also pass.

Compressed logs, combined LCOV and method/branch gap details accompany this file.
Passing the method contract does not claim complete branch coverage or release
qualification. The native and performance evidence lives in the sibling
`protobuf-slab-allocation-20261108`, `protobuf-b4-213a289b`, and
`json-codspeed-20261108` directories.

```sh
bzl coverage //tests:test //protobuf-runtime:test //protobuf-buffa/tests:jvm \
  //protobuf-buffa/tests:jvm_non_strict //protobuf-buffa/tests:jvm_no_native_access \
  //json/tests:jvm //yaml/tests:jvm //tape/tests:jvm //csv/tests:jvm --config=codecov
python3 .github/scripts/reports.py coverage
python3 .github/scripts/coverage_contract.py
python3 -m unittest discover -s .github/scripts -p 'test_*.py'
```

All work stays local. The user requested a performance summary and pause after
landing; the remaining performance and release gates are recorded in the resume
ledger rather than treated as complete.
