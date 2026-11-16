# Bazel and BuildBuddy

The workspace follows Komodo's Bazel/BuildBuddy configuration, with an independent `elide-dev/truffledata/v1` cache namespace. It does not share Komodo's application cache namespace or invent a repository hosting URL.

## Execution and caches

`bazel build` and `bazel test` default to `--config=remote`: Linux amd64 targets and execution platform, BuildBuddy RBE, Firecracker isolation, a digest-pinned executor image, and no network inside build actions. All compilation/test toolchains are declared Bazel inputs. Remote execution failure does not silently fall back to a local compiler.

BES links, BLAKE3 digests, cache compression/chunking, the remote downloader with direct-download fallback, path mapping, top-level output downloads, and test-report retrieval follow Komodo. Local results are not uploaded to the shared cache. `--config=untrusted` isolates PR action-cache entries from main; it is not an authorization boundary or a replacement for scoped credentials.

The remote downloader affects repository fetching, which necessarily happens before remote build actions. Native helper toolchains and JDK archives must still be staged locally. Leave several GiB available for initial setup. On macOS, `tools/bazel` uses the same case-sensitive sparsebundle convention as Komodo, preserving case-distinct Linux sysroot filenames. It intentionally reuses the existing `komodo-bazel-volume` cache when present. Bazel's workspace hash separates output bases.

The pinned GraalVM fork version is shared by the SDK toolchains and Maven artifacts. This prevents the compiler/runtime mismatch that can occur when a developer's `JAVA_HOME` points to a different GraalVM release. The pin is in `MODULE.bazel`.

## Native optimization

The Elide toolchain is pinned to v2026.11.6. Rust target compilation, host Cargo tooling, rustfmt, and rust-analyzer use its `rustversion` recommendation, `nightly-2026-10-09` (Rust LLVM 23.1.3). `rust-toolchain.toml` gives local tools the same dated pin; Bazel downloads have explicit SHA256 checksums.

Default builds use `--compilation_mode=opt`, including transitive Rust crates. An adapter's explicit `-Copt-level=3` does not optimize its dependencies in `fastbuild`.

`--@rules_rust//rust/settings:lto=thin` preserves Rust dependency bitcode and performs cross-crate ThinLTO when producing static/shared libraries. `--features=thin_lto` enables C/C++ ThinLTO indexing and backend actions with the pinned Elide LLVM toolchain. Rust host tools and procedural macros follow rules_rust's separate execution-configuration policy.

The Linux amd64 JVM shared-library and SVM static-library paths are tested with these defaults. Native Image consumes the native archives through the pinned Elide clang/lld toolchain; this is not Java-to-Rust LTO.

Under these defaults C/C++ objects and Rust rlibs are bitcode, so the intermediate `cc_static_library` archives only link with an LLVM 23 LTO linker. The published static archives are the `release_archive` targets in `//tools/release` (`bazel/release_archive.bzl`). They build their inputs with Rust `-Clinker-plugin-lto` and recompile every bitcode member. On ELF each member becomes a fat object: machine code plus a ThinLTO `.llvm.lto` section, which ld.lld uses under `-flto=thin` and every linker ignores otherwise. On Mach-O, which has no fat object format, each member becomes machine code. Only `compiler_builtins` and the compiler-rt objects it bundles stay machine code on ELF, because rustc never LTOs them. Each archive's `manifest` output group lists its members with their kinds. The rulings and evidence are in `docs/superpowers/plans/2026-11-07-release-matrix-ledger.md`.

Release natives are built per platform, one invocation each: `bazel build //tools/release:natives` (linux-x86_64), `bazel build --config=remote_arm64 //tools/release:natives` (linux-aarch64 on BuildBuddy's arm64 executors, `//bazel/platforms:rbe_linux_arm64`), and `bazel build --config=local //tools/release:natives` on an Apple silicon Mac (macos-aarch64, shared libraries as `.dylib` with install name `@rpath/libtruffledata_<name>.dylib`, static archives as machine code plus a ThinLTO bitcode twin). `//tools/release:native_consumers` links and loads them on each platform, and `tools/release/publish.sh` builds, collects and gates all three ([Prebuilt natives](PUBLISHING.md#prebuilt-natives)).

The Linux sysroot fat-bitcode final-link integration is tracked in [toolchain#13](https://github.com/elide-dev/toolchain/issues/13). The current ThinLTO action checks establish Rust cross-crate and C++ target LTO; they do not prove every shipped sysroot archive participates in cross-module optimization.

For an explicit diagnostic comparison, disable both LTO paths with `--@rules_rust//rust/settings:lto=off --features=-thin_lto`. Keep `-c opt` when measuring production performance. Compiler-action evidence and the optimized baseline measurements live under `benchmarks/results/buffa-native-views/`.

## Credentials

Use your existing `~/.bazelrc`, or an untracked `user.bazelrc`, with BuildBuddy's `x-buildbuddy-api-key` remote header. No credential is checked in or copied from another project. BuildBuddy workflow activation and repository secrets are external setup; `buildbuddy.yaml` defines main and isolated PR builds but does not register the repository with BuildBuddy.

## Commands

```sh
bazel test //...
bazel test //tests:compiled --nocache_test_results
bazel run --config=local //:gazelle
bazel run --config=local //bazel/gazelle:check
bazel run --config=local //benchmarks:jmh -- -prof gc
```

Use the local profile when running a host binary on macOS. A remote build produces a Linux executable; `bazel run` does not itself turn the invocation of that executable into a remote action. BuildBuddy workflow runners are Linux and can use the default remote configuration for Gazelle's `run` target.

Gazelle owns the `data_java_library` declarations for core, JDBC, Protobuf, tests, and benchmarks. It discovers sorted Java source membership (including package annotations) and uses explicit module dependencies and processor settings from `bazel/gazelle/sources.json`. Test runners and toolchain/platform definitions stay handwritten. A generator unit test checks sorting, ownership, and dependency preservation.

`//tests:test` covers behavior and source policies; `//tests:allocation` measures allocation; `//tests:compiled` additionally requires actual Truffle compilation and applies the compiler budgets. `//tools:nullness_probe` proves nullness rejection. `//benchmarks:jmh` is an opt-in diagnostic benchmark, not a database-throughput claim.

## GitHub CI and Codecov

`.github/workflows/ci.yml` calls the reusable `check.bazel.yml` workflow on pushes to main, pull requests and manual dispatch. Jobs use Elide's SHA-pinned harden-runner, checkout, artifact and Codecov actions; checkout credentials are not persisted. BuildBuddy uses the inherited `BUILDBUDDY_API_KEY` (or organization-wide `BUILDBUDDY_APIKEY`) in a temporary mode-0600 `user.bazelrc`, removed on every exit path. Fork PRs and runs without that key explicitly use local execution on the hosted Linux runner, without accessing the shared cache.

The workflow runs Gazelle drift checks and `bazel test //...`, preserves JUnit XML, then runs `bazel coverage` over `//tests:test`, the Buffa JVM lanes, `//json/tests:jvm` and `//csv/tests:jvm` with `--config=codecov`. Allocation/JIT gates run without instrumentation; only production core/JDBC/Protobuf/Buffa/JSON/CSV sources contribute to LCOV (not generated bindings). `.github/scripts/coverage_contract.py` then requires every method of the listed access surfaces to be covered, except the exact SVM-only methods it names; the static SVM paths and compiled gates run in `check.buffa.yml` (`//json/tests:native_test`, `//csv/tests:native_test`, `//benchmarks/comparison:json_native_smoke`, `//tools/release:json_native_test`). Reporting validates real source paths and covered lines in every module, and preserves testcase failure details in a combined JUnit report. Both reports go to Codecov using the inherited global `CODECOV_TOKEN`, with the current repository slug and `jvm` flag. Upload errors do not fail CI; the tests and `coverage_contract.py` are the gates. Fork PRs receive no secrets and retain their reports as Actions artifacts without uploading to Codecov.

Local reproduction:

```sh
bazel test //...
python3 .github/scripts/reports.py junit
bazel coverage //tests:test //protobuf-buffa/tests:jvm //protobuf-buffa/tests:jvm_non_strict //protobuf-buffa/tests:jvm_views //protobuf-buffa/tests:jvm_views_strict //protobuf-buffa/tests:jvm_no_native_access //json/tests:jvm //csv/tests:jvm --config=codecov
python3 .github/scripts/reports.py coverage
python3 .github/scripts/coverage_contract.py
```

JUnit is copied before the coverage invocation updates Bazel testlog symlinks. Reports are retained in the `bazel-reports` Actions artifact for 14 days, including logs on failure. The legacy BuildBuddy workflow remains usable, while GitHub Actions owns Codecov reporting.
