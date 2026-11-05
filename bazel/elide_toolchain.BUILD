# An elide-dev/toolchain bundle (strip_prefix leaves bin/, lib/, include/ and sysroot/ at the root) as a C/C++ toolchain.
# `cc` (written by MODULE.bazel's patch_cmds) runs bin/clang, which loads the host triple's cfg: target, sysroot,
# compiler-rt, libunwind, libc++ and lld. The toolchain() registrations live in //bazel/toolchains, so a bundle is
# fetched only when its platform is selected.
load("@rules_cc//cc:defs.bzl", "cc_toolchain")
load("@rules_cc//cc/private/toolchain:unix_cc_toolchain_config.bzl", "cc_toolchain_config")

package(default_visibility = ["//visibility:public"])

TRIPLES = [f[len("bin/"):-len(".cfg")] for f in glob(["bin/*.cfg"])]

DARWIN = "arm64-apple-darwin" in TRIPLES

# The host triple: gnu on Linux (musl serves --libc=musl images), darwin on macOS.
TRIPLE = "arm64-apple-darwin" if DARWIN else [t for t in TRIPLES if t.endswith("-linux-gnu")][0]

filegroup(
    name = "all_files",
    srcs = glob(
        ["**"],
        exclude = ["**/* *"],
    ),
)

# `cc` (bin/clang behind the macOS SDK lookup) for host commands: Cargo's linker, CC for build scripts.
filegroup(
    name = "cc_wrapper",
    srcs = ["cc"],
    data = [":all_files"],
)

filegroup(
    name = "bin",
    srcs = glob(["bin/**"]),
)

cc_toolchain_config(
    name = "config",
    abi_libc_version = "darwin" if DARWIN else "glibc_2.17",
    abi_version = "clang",
    compile_flags = [
        "-fstack-protector",
        "-Wall",
        "-fno-omit-frame-pointer",
    ],
    compiler = "clang",
    cpu = "darwin_arm64" if DARWIN else "k8" if TRIPLE.startswith("x86_64") else "aarch64",
    # The bundle's own headers; on macOS, the SDK (located by `cc` through xcrun when SDKROOT is unset).
    cxx_builtin_include_directories = [
        "%package(@@" + repository_name().lstrip("@") + "//)%/lib/clang",
        "%package(@@" + repository_name().lstrip("@") + "//)%/include",
        "%package(@@" + repository_name().lstrip("@") + "//)%/sysroot",
    ] + ([
        "/Library/Developer/CommandLineTools/SDKs",
        "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs",
    ] if DARWIN else []),
    dbg_compile_flags = ["-g"],
    host_system_name = TRIPLE,
    link_flags = [],
    # Links run through the C driver: C++ programs need libc++ named (static archives: pulled in only when used).
    link_libs = ["-lc++"] if DARWIN else [
        "-lc++",
        "-lc++abi",
        "-lm",
    ],
    opt_compile_flags = [
        "-g0",
        "-O2",
        "-D_FORTIFY_SOURCE=1",
        "-DNDEBUG",
        "-ffunction-sections",
        "-fdata-sections",
    ],
    opt_link_flags = [] if DARWIN else ["-Wl,--gc-sections"],
    supports_start_end_lib = not DARWIN,
    target_libc = "macosx" if DARWIN else "glibc_2.17",
    target_system_name = TRIPLE,
    tool_paths = {
        "ar": "bin/llvm-ar",
        "cpp": "bin/clang-cpp",
        "dwp": "bin/llvm-dwp",
        "gcc": "cc",
        "gcov": "bin/llvm-cov",
        "ld": "bin/ld.lld",
        "libtool": "bin/llvm-libtool-darwin",
        "llvm-cov": "bin/llvm-cov",
        "llvm-profdata": "bin/llvm-profdata",
        "nm": "bin/llvm-nm",
        "objcopy": "bin/llvm-objcopy",
        "objdump": "bin/llvm-objdump",
        "strip": "bin/llvm-strip",
    },
    toolchain_identifier = "elide-toolchain-" + TRIPLE,
    unfiltered_compile_flags = [
        "-no-canonical-prefixes",
        "-Wno-builtin-macro-redefined",
        "-D__DATE__=\"redacted\"",
        "-D__TIMESTAMP__=\"redacted\"",
        "-D__TIME__=\"redacted\"",
    ],
)

cc_toolchain(
    name = "cc_toolchain",
    all_files = ":all_files",
    ar_files = ":bin",
    as_files = ":all_files",
    compiler_files = ":all_files",
    dwp_files = ":bin",
    linker_files = ":all_files",
    objcopy_files = ":bin",
    strip_files = ":bin",
    toolchain_config = ":config",
)
