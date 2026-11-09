# truffledata-json-native

Cargo build of the TruffleData JSON decoder C ABI (`json/native/api.cc`). Embedders that build
their natives from source depend on this crate so their image links one C++ standard library,
one allocator and one simdutf (elide-dev/truffledata#37).

```toml
[dependencies]
truffledata-json-native = { git = "https://github.com/elide-dev/truffledata", tag = "v0.9.0" }
```

The crate exposes the raw `extern "C"` seam (`td_json_*`), `SEAM_ABI` and
`BINDING_ABI_VERSION`. It sets `links = "truffledata_json"`, so a graph holds one copy.

The crate is a standalone package with an empty `[workspace]` table. It is not a member of the
repository root workspace, whose Cargo.lock feeds Bazel crate_universe. Build and test it from
this directory.

## What it compiles

Everything is vendored under `vendor/`, so a git checkout at a tag builds without Bazel:

| Path | Source |
| --- | --- |
| `vendor/json/native` | `json/native/api.cc` and the decoder headers |
| `vendor/json/native/generated/seam.h` | Myna generator output for `json/native/json.seam`, with `TRUFFLEDATA_JSON_SEAM_ABI` selected per ABI class by `__CHAR_UNSIGNED__` (`bazel/myna_abi.py`) |
| `vendor/tape/native` | `tape/native/*.h` and `text.cc` |
| `vendor/myna/include` | Myna 0.14.0 value headers |
| `vendor/simdjson` | simdjson 5.1.1 single header |
| `vendor/simdutf` | simdutf from the `simdutf` 0.7.0 crate's bundled C++ |

Copts match `json/native/BUILD.bazel`: `-std=c++17 -O3`, in every Cargo profile.

## simdutf

| Feature | Effect |
| --- | --- |
| `bundled-simdutf` (default) | Compiles `vendor/simdutf/simdutf.cpp` into this crate. |
| `simdutf-crate` | Compiles only against `vendor/simdutf/simdutf.h` and links the C++ simdutf symbols from the `simdutf = "=0.7.0"` crate. |

The two are exclusive; the build fails if both are on. An embedder that already has the
`simdutf` crate in its graph (for example through `truffledata-buffa`) should use:

```toml
truffledata-json-native = { git = "...", tag = "...", default-features = false, features = ["simdutf-crate"] }
```

Keeping `bundled-simdutf` in such a graph still links on the tested toolchains, but it carries a
second copy of simdutf, which is what this crate exists to avoid. Disabling both features leaves
the simdutf symbols undefined for the embedder to supply.

## C++ standard library

The `cc` crate picks and links the target's default C++ standard library and honors
`CXXSTDLIB` (and `CXX`, `CXXFLAGS`). Nothing here forces `-stdlib`. The published Bazel binaries
use libc++; set `CXXSTDLIB=c++` (with a libc++-capable `CXX`) on Linux to match them.

The crate sets no `#[global_allocator]`, panic strategy or profile.

## Keeping the vendored copy in sync

`vendor/json/native/generated/seam.h` must be byte-identical to `bazel-bin/json/native/generated/seam.h`
for the current `json.seam`: the Myna generator output whose `TRUFFLEDATA_JSON_SEAM_ABI` define
`bazel/myna_abi.py` selects per Myna 0.14.0 target ABI class with `__CHAR_UNSIGNED__` (AAPCS64 Linux,
where C `char` is unsigned, has its own fingerprint; x86-64 and Apple arm64 share the other). The
fingerprint `td_json_fingerprint()` returns is compiled from it, so a Cargo build for any target reports its
class's fingerprint, and the published bindings jars compare it at load time against the class they
run on. `build.rs` mirrors the compiler's choice from the Cargo target (`aarch64` Linux is the
unsigned-char class) for `SEAM_ABI`; `tests/link.rs` checks the linked fingerprint against both.

To refresh every vendored file after changing the decoder, the seam or a pinned dependency:

```sh
bazel build //json/native:bindings
EXTERNAL="$(bazel info output_base)/external" json/native/crate/vendor.sh
(cd json/native/crate && cargo test)
```
