# Bazel and BuildBuddy

The workspace follows Komodo's Bazel/BuildBuddy configuration, with an independent `elide-dev/truffledata/v1` cache namespace. It does not share Komodo's application cache namespace or invent a repository hosting URL.

## Execution and caches

`bazel build` and `bazel test` default to `--config=remote`: Linux amd64 targets and execution platform, BuildBuddy RBE, Firecracker isolation, a digest-pinned executor image, and no network inside build actions. All compilation/test toolchains are declared Bazel inputs. Remote execution failure does not silently fall back to a local compiler.

BES links, BLAKE3 digests, cache compression/chunking, the remote downloader with direct-download fallback, path mapping, top-level output downloads, and test-report retrieval follow Komodo. Local results are not uploaded to the shared cache. `--config=untrusted` isolates PR action-cache entries from main; it is not an authorization boundary or a replacement for scoped credentials.

The remote downloader affects repository fetching, which necessarily happens before remote build actions. Native helper toolchains and JDK archives must still be staged locally. Leave several GiB available for initial setup. On macOS, `tools/bazel` uses the same case-sensitive sparsebundle convention as Komodo, preserving case-distinct Linux sysroot filenames. It intentionally reuses the existing `komodo-bazel-volume` cache when present. Bazel's workspace hash separates output bases.

The pinned GraalVM fork version is shared by the SDK toolchains and Maven artifacts. This prevents the compiler/runtime mismatch that can occur when a developer's `JAVA_HOME` points to a different GraalVM release. The pin is in `MODULE.bazel`.

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

`.github/workflows/ci.yml` calls the reusable `check.bazel.yml` workflow on pushes to main, pull requests and manual dispatch. Jobs use Elide's SHA-pinned harden-runner, checkout, artifact and Codecov actions; checkout credentials are not persisted. BuildBuddy uses the inherited `BUILDBUDDY_API_KEY` in a temporary mode-0600 `user.bazelrc`, removed on every exit path. Fork PRs and runs without that key explicitly use local execution on the hosted Linux runner, without accessing the shared cache.

The workflow runs Gazelle drift checks and `bazel test //...`, preserves JUnit XML, then runs `bazel coverage //tests:test --config=codecov`. Allocation/JIT gates run without instrumentation; only production core/JDBC/Protobuf sources contribute to LCOV. Reporting validates real source paths and covered lines in every module, and preserves testcase failure details in a combined JUnit report. Both reports go to Codecov using the inherited global `CODECOV_TOKEN`, with the current repository slug and `jvm` flag. Upload errors fail CI. Fork PRs receive no secrets and retain their reports as Actions artifacts without uploading to Codecov.

Local reproduction:

```sh
bazel test //...
python3 .github/scripts/reports.py junit
bazel coverage //tests:test --config=codecov
python3 .github/scripts/reports.py coverage
```

JUnit is copied before the coverage invocation updates Bazel testlog symlinks. Reports are retained in the `bazel-reports` Actions artifact for 14 days, including logs on failure. The legacy BuildBuddy workflow remains usable, while GitHub Actions owns Codecov reporting.
