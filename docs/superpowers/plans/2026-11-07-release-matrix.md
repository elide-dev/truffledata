# Release Matrix: Prebuilt Natives for Three Platforms, Static and Shared, Fat LTO Objects

> **For agentic workers:** record rulings in `docs/superpowers/plans/2026-11-07-release-matrix-ledger.md` (create it at Task R1).

**Goal (user, 2026-11-07):** before the next release, every published native ships prebuilt for `linux-x86_64`, `linux-aarch64` and `macos-aarch64`, as both a static archive and a shared library, and every object in every static archive is a *fat* object carrying both machine code and ThinLTO bitcode, so consumers can link our natives with or without LTO and with no build of their own. Protobuf keeps a pure-Java path for consumers who cannot use Buffa.

**Baseline:** `tools/release/BUILD.bazel` attaches `-linux-x86_64.a` and `-linux-x86_64.so` for `truffledata-protobuf-buffa` (Rust), `truffledata-json` (simdjson + Myna bridge, merged archive) and `truffledata-csv-native`. The Elide LLVM 23 toolchain and GraalVM are already registered for `linux_aarch64` and `macos_aarch64` (`MODULE.bazel`); `bazel/platforms` has `config_setting`s for all three; RBE has only `rbe_linux_amd64`.

## Requirements

1. **Platforms:** classifiers `linux-x86_64`, `linux-aarch64`, `macos-aarch64` (attachment suffixes `-<classifier>.a`, `.so` on Linux, `.dylib` on macOS; the header attachment once). YAML's native joins the matrix when it publishes.
2. **Static and shared** for every native module on every platform. A static archive is self-contained (merged dependencies, as `json_archive` today).
3. **Fat LTO objects:** every member of every static archive contains machine code *and* LLVM bitcode usable by the linker's LTO (ELF: clang `-ffat-lto-objects` with `-flto=thin`, `.llvm.lto` section). Mach-O and Rust need an investigated approach (Task R1); if no format a real linker consumes for LTO exists for one of them, stop and report rather than ship a section no linker reads.
4. **Release gate:** a checker (`tools/release/check_natives.py` or similar, using the toolchain's `llvm-objdump`/`llvm-readobj`/`llvm-nm`) fails publication if any archive member lacks machine code or bitcode, if any platform's attachment is missing, or if a shared library has undefined symbols outside the platform's allowlist (libc, libm, libpthread, libdl, libSystem).
5. **Consumers:** `native_consumer.sh` and the release consumers link each platform's static archive with LTO off and with ThinLTO, and load the shared library, on that platform (RBE for Linux, local for macOS).
6. **Publication:** `publish.sh` builds the three platforms (Linux amd64 and arm64 remote, macOS local, or a documented CI matrix whose artifacts the publish step collects) and stages one Maven repository with every attachment; the gate list in `publish.sh` runs on each platform it can.
7. **Pure-Java protobuf:** the native-free path stays a release gate: `//protobuf-buffa/tests:fallback_test` and `//tools/release:buffa_consumer`'s fallback mode. After the slab redesign the Java slab producer (protobuf plan Task A3) is required, not optional, and the fallback consumer reads slabs it produced.

## Tasks

### Task R1: Investigation and toolchain flags
- [x] Done (ledger, Task R1).
Determine and prove the fat-object recipe for each (language × object format): C/C++ ELF, C/C++ Mach-O, Rust ELF, Rust Mach-O, on nightly-2026-10-09 and LLVM 23.1.3. Show with the toolchain's tools that the objects carry both, and that a test link uses the bitcode under LTO (e.g. `-Wl,--lto-...` save-temps or a cross-module inlining witness) and the machine code without it. Determine how linux-aarch64 builds: BuildBuddy arm64 executors (`Arch: arm64`) or a cross-build with the toolchain sysroot. Ledger every ruling. Commit only probes or docs that the later tasks need.

### Task R2: Build wiring
- [x] Done: linux-x86_64, linux-aarch64 (BuildBuddy arm64, Firecracker) and macos-aarch64 (local, with the `-bitcode.a` twin) build (ledger).
Platforms (`rbe_linux_arm64` if used), per-platform archive/shared targets, fat-object features scoped to the release archives (do not slow ordinary test builds unless free), the attachment map per classifier, `.dylib` naming on macOS.

### Task R3: Release gate and consumers
- [x] Done: `check_natives.py`, `//tools/release:native_consumers`, three-platform `publish.sh` staging, docs (ledger).
The checker, consumer links per platform (LTO on and off), `publish.sh` multi-platform staging, `docs/PUBLISHING.md`, README platform table.

### Task R4: Verification
- [x] Staged and checked on all three platforms with Myna 0.13.0 and GraalVM 25.5.5-elide.27; every archive, shared-library and bindings fingerprint check passes (ledger, 0.9.0 follow-up).
Stage a full repository with every platform; run every consumer; record the checker output in the ledger.
