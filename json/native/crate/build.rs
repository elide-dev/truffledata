//! Compiles the vendored TruffleData JSON C ABI (json/native/api.cc) with the `cc` crate.
//!
//! The C++ standard library is the embedder's: `cc` selects and links it (and honors CXXSTDLIB);
//! nothing here forces `-stdlib`. Release-equivalent copts: `-std=c++17 -O3`.

use std::env;
use std::fs;
use std::path::PathBuf;

const SEAM: &str = "vendor/json/native/generated/seam.h";
const ABI_DEFINE: &str = "#define TRUFFLEDATA_JSON_SEAM_ABI \"";

/// The fingerprint of the target's Myna ABI class from the vendored seam.h, whose `TRUFFLEDATA_JSON_SEAM_ABI`
/// define is selected by `__CHAR_UNSIGNED__` (Myna 0.14.0: AAPCS64 Linux, where C `char` is unsigned,
/// is its own class). The compiled C++ takes the branch the compiler decides; this mirrors it for the
/// Myna targets: aarch64 Linux is the unsigned-char class, every other supported target the signed one.
/// `tests/link.rs` checks the two agree.
fn seam_abi(seam: &str, target_os: &str, target_arch: &str) -> String {
    let defines: Vec<&str> = seam
        .lines()
        .filter_map(|line| line.strip_prefix(ABI_DEFINE)?.strip_suffix('"'))
        .collect();
    let char_unsigned = target_os == "linux" && target_arch == "aarch64";
    match defines.as_slice() {
        // `#ifdef __CHAR_UNSIGNED__` branch first, then `#else`.
        [unsigned, signed] => (if char_unsigned { unsigned } else { signed }).to_string(),
        [only] => only.to_string(),
        _ => panic!("expected one TRUFFLEDATA_JSON_SEAM_ABI define per ABI class in vendored seam.h"),
    }
}

fn main() {
    let bundled = env::var_os("CARGO_FEATURE_BUNDLED_SIMDUTF").is_some();
    let external = env::var_os("CARGO_FEATURE_SIMDUTF_CRATE").is_some();
    if bundled && external {
        panic!(
            "truffledata-json-native: features `bundled-simdutf` and `simdutf-crate` are exclusive; \
             set default-features = false to use the `simdutf` crate"
        );
    }

    let mut build = cc::Build::new();
    build
        .cpp(true)
        .std("c++17")
        .opt_level(3)
        .warnings(false)
        .include("vendor")
        .include("vendor/myna/include")
        .include("vendor/simdjson")
        .include("vendor/simdutf")
        .file("vendor/json/native/api.cc")
        .file("vendor/tape/native/text.cc")
        .file("vendor/simdjson/simdjson.cpp");
    if bundled {
        build.file("vendor/simdutf/simdutf.cpp");
    }
    build.compile("truffledata_json");

    // The fingerprint compiled into api.cc, exported to Rust from the same vendored header.
    let seam = fs::read_to_string(SEAM).expect("read vendored seam.h");
    let abi = seam_abi(&seam, &env::var("CARGO_CFG_TARGET_OS").unwrap(), &env::var("CARGO_CFG_TARGET_ARCH").unwrap());
    let out = PathBuf::from(env::var_os("OUT_DIR").unwrap());
    fs::write(out.join("seam_abi.rs"), format!("pub const SEAM_ABI: &str = {abi:?};\n")).unwrap();

    println!("cargo:rerun-if-changed=build.rs");
    println!("cargo:rerun-if-changed=vendor");
    // Dependents can find the C header layout via DEP_TRUFFLEDATA_JSON_INCLUDE.
    let manifest = PathBuf::from(env::var_os("CARGO_MANIFEST_DIR").unwrap());
    println!("cargo:include={}", manifest.join("vendor").display());
    println!("cargo:abi={abi}");
}
