"""C consumers of the release natives on the target platform: the static archive linked with LTO off and with ThinLTO,
and the shared library loaded with dlopen (tools/release/native_consumer.c)."""

load("@rules_cc//cc:defs.bzl", "cc_binary", "cc_import")
load("@rules_cc//cc:find_cc_toolchain.bzl", "find_cc_toolchain", "use_cc_toolchain")
load("@rules_shell//shell:sh_test.bzl", "sh_test")

def _lto_witness_impl(ctx):
    # The linker used the archive's bitcode exactly when the fingerprint call is gone from main: under ThinLTO it is
    # inlined across the C/C++ or Rust boundary, without LTO main calls the archive's machine code.
    cc = find_cc_toolchain(ctx)
    binary = ctx.file.binary
    output = ctx.actions.declare_file(ctx.label.name + ".txt")
    ctx.actions.run_shell(
        command = """
set -euo pipefail
bin=$1 binary=$2 symbol=$3 lto=$4 output=$5
main=main
case $("$bin/llvm-readobj" --file-headers "$binary" | sed -n 's/^ *Format: //p') in Mach-O*) main=_main symbol=_$symbol ;; esac
"$bin/llvm-objdump" -d --no-show-raw-insn --disassemble-symbols="$main" "$binary" > "$output"
grep -q "<$main>:" "$output" || { echo "lto_witness: no $main in $binary" >&2; exit 1; }
if grep -q "<$symbol>" "$output"; then called=1; else called=0; fi
if [[ $lto == 1 && $called == 1 ]]; then
  echo "lto_witness: $main still calls $symbol under ThinLTO: the archive's bitcode was not used" >&2; cat "$output" >&2; exit 1
fi
if [[ $lto == 0 && $called == 0 ]]; then
  echo "lto_witness: $main does not call $symbol without LTO: expected the archive's machine code" >&2; cat "$output" >&2; exit 1
fi
""",
        arguments = [
            cc.ar_executable.rpartition("/")[0],
            binary.path,
            ctx.attr.symbol,
            "1" if ctx.attr.lto else "0",
            output.path,
        ],
        inputs = [binary],
        tools = cc.all_files,
        outputs = [output],
        mnemonic = "LtoWitness",
        progress_message = "Checking the LTO witness of %{label}",
    )
    return [DefaultInfo(files = depset([output]))]

lto_witness = rule(
    implementation = _lto_witness_impl,
    attrs = {
        "binary": attr.label(allow_single_file = True, mandatory = True),
        "lto": attr.bool(mandatory = True),
        "symbol": attr.string(mandatory = True),
    },
    toolchains = use_cc_toolchain(),
    fragments = ["cpp"],
)

def native_consumers(name, archive, shared, fingerprint, probe = None):
    """`<name>_static_test` (LTO off) and `<name>_thinlto_test` (ThinLTO): link `archive` into native_consumer.c, check
    the LTO witness, run it, and load `shared` with dlopen.

    On ELF both link the same fat archive; on macOS ThinLTO links the `<archive>_bitcode` twin with ld64.lld.
    """
    defines = ["FINGERPRINT=" + fingerprint] + (["PROBE=" + probe] if probe else [])
    linkopts = select({
        "@platforms//os:macos": [],
        "//conditions:default": [
            "-ldl",
            "-lpthread",
            "-lm",
        ],
    })
    cc_import(
        name = name + "_machine_code",
        static_library = archive,
        testonly = True,
    )
    cc_import(
        name = name + "_lto_input",
        static_library = select({
            "@platforms//os:macos": archive + "_bitcode",
            "//conditions:default": archive,
        }),
        testonly = True,
    )
    for variant, lto in [("static", False), ("thinlto", True)]:
        binary = "%s_%s" % (name, variant)
        cc_binary(
            name = binary,
            srcs = ["native_consumer.c"],
            # The consumer's own ThinLTO (in-process, by the linker), not Bazel's distributed ThinLTO backends.
            copts = ["-flto=thin"] if lto else [],
            features = ["-thin_lto"],
            linkopts = (["-flto=thin"] if lto else []) + linkopts,
            local_defines = defines,
            testonly = True,
            deps = [":%s_%s" % (name, "lto_input" if lto else "machine_code")],
        )
        lto_witness(
            name = binary + "_witness",
            binary = ":" + binary,
            lto = lto,
            symbol = fingerprint,
            testonly = True,
        )
        sh_test(
            name = binary + "_test",
            srcs = ["native_consumer.sh"],
            args = [
                "$(rootpath :%s)" % binary,
                "$(rootpath %s)" % shared,
            ],
            data = [
                ":" + binary,
                ":%s_witness" % binary,
                shared,
            ],
            tags = ["native-consumer"],
        )
