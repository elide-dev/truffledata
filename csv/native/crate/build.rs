//! Compiles the vendored TruffleData CSV C ABI (csv/native/api.cc) with the `cc` crate.
//!
//! The C++ standard library is the embedder's: `cc` selects and links it (and honors CXXSTDLIB);
//! nothing here forces `-stdlib`. Copts match csv/native/BUILD.bazel: `-std=c++17 -O3`, and each
//! instruction-set kernel compiles in its own translation unit with its own target flags;
//! dispatch.cc selects one at run time.

use std::env;
use std::fs;
use std::path::PathBuf;

const SEAM: &str = "vendor/csv/native/generated/seam.h";
const ABI_DEFINE: &str = "#define TRUFFLEDATA_CSV_SEAM_ABI \"";
const AVX2: &[&str] = &["-mavx2", "-mpclmul", "-mbmi", "-mbmi2", "-mpopcnt", "-mlzcnt"];

fn base() -> cc::Build {
    let mut build = cc::Build::new();
    build
        .cpp(true)
        .std("c++17")
        .opt_level(3)
        .warnings(false)
        .include("vendor")
        .include("vendor/simdutf");
    build
}

fn main() {
    let bundled = env::var_os("CARGO_FEATURE_BUNDLED_SIMDUTF").is_some();
    let external = env::var_os("CARGO_FEATURE_SIMDUTF_CRATE").is_some();
    if bundled && external {
        panic!(
            "truffledata-csv-native: features `bundled-simdutf` and `simdutf-crate` are exclusive; \
             set default-features = false to use the `simdutf` crate"
        );
    }
    let arch = env::var("CARGO_CFG_TARGET_ARCH").unwrap();

    // ISA kernels: separate archives so their target flags never leak into portable code.
    if arch == "x86_64" {
        let mut avx2 = base();
        avx2.file("vendor/csv/native/index_avx2.cc");
        for flag in AVX2 {
            avx2.flag(flag);
        }
        avx2.compile("truffledata_csv_avx2");

        let mut avx512 = base();
        avx512.file("vendor/csv/native/index_avx512.cc");
        for flag in AVX2.iter().chain(&["-mavx512f", "-mavx512bw"]) {
            avx512.flag(flag);
        }
        avx512.compile("truffledata_csv_avx512");
    }

    let mut build = base();
    build
        .file("vendor/csv/native/api.cc")
        .file("vendor/csv/native/dispatch.cc")
        .file("vendor/csv/native/index_swar.cc");
    if arch == "aarch64" {
        build.file("vendor/csv/native/index_neon.cc");
    }
    if bundled {
        build.file("vendor/simdutf/simdutf.cpp");
    }
    // Compiled last so it precedes the kernel archives on the link line.
    build.compile("truffledata_csv");

    let seam = fs::read_to_string(SEAM).expect("read vendored seam.h");
    let abi = seam
        .lines()
        .find_map(|line| line.strip_prefix(ABI_DEFINE)?.strip_suffix('"'))
        .expect("TRUFFLEDATA_CSV_SEAM_ABI in vendored seam.h");
    let out = PathBuf::from(env::var_os("OUT_DIR").unwrap());
    fs::write(out.join("seam_abi.rs"), format!("pub const SEAM_ABI: &str = {abi:?};\n")).unwrap();

    println!("cargo:rerun-if-changed=build.rs");
    println!("cargo:rerun-if-changed=vendor");
    let manifest = PathBuf::from(env::var_os("CARGO_MANIFEST_DIR").unwrap());
    println!("cargo:include={}", manifest.join("vendor").display());
    println!("cargo:abi={abi}");
}
