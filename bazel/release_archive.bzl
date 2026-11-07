"""Release static archives: merged, self-contained, and linkable with or without LTO (bazel/release_archive.sh)."""

load("@rules_cc//cc:find_cc_toolchain.bzl", "find_cc_toolchain", "use_cc_toolchain")

_EXTRA_RUSTC_FLAGS = "@rules_rust//rust/settings:extra_rustc_flags"
_COPT = "//command_line_option:copt"

def _bitcode_impl(settings, _attr):
    # Rust crates emit bitcode for the archive: under --@rules_rust//rust/settings:lto=thin rlibs already do, and
    # -Clinker-plugin-lto turns the staticlib's own crate from rustc-LTO machine code into bitcode too. C/C++ objects
    # are bitcode under --features=thin_lto on Linux only: the macOS toolchain configuration has no thin_lto feature,
    # so the release configuration compiles C/C++ with -flto=thin itself (repeating the feature's flag on Linux).
    flags = settings[_EXTRA_RUSTC_FLAGS]
    copts = settings[_COPT]
    return {
        _EXTRA_RUSTC_FLAGS: flags if "-Clinker-plugin-lto" in flags else flags + ["-Clinker-plugin-lto"],
        _COPT: copts if "-flto=thin" in copts else copts + ["-flto=thin"],
    }

_bitcode = transition(
    implementation = _bitcode_impl,
    inputs = [_EXTRA_RUSTC_FLAGS, _COPT],
    outputs = [_EXTRA_RUSTC_FLAGS, _COPT],
)

def _release_archive_impl(ctx):
    cc = find_cc_toolchain(ctx)
    output = ctx.actions.declare_file("lib%s.a" % ctx.label.name)
    manifest = ctx.actions.declare_file("lib%s.members.txt" % ctx.label.name)
    outputs = [output, manifest]
    bitcode = []
    bitcode_manifest = []
    if ctx.attr.format == "macho":
        # Mach-O has no fat objects: the ThinLTO bitcode ships as a second archive (lld and LLVM >= 23 consumers).
        bitcode = [ctx.actions.declare_file("lib%s-bitcode.a" % ctx.label.name)]
        bitcode_manifest = [ctx.actions.declare_file("lib%s-bitcode.members.txt" % ctx.label.name)]
        outputs += bitcode + bitcode_manifest
    archives = ctx.files.archives
    ctx.actions.run(
        executable = ctx.executable._script,
        arguments = [
            ctx.attr.format,
            output.path,
            manifest.path,
            bitcode[0].path if bitcode else "-",
            bitcode_manifest[0].path if bitcode_manifest else "-",
            cc.compiler_executable,
            cc.ar_executable.rpartition("/")[0],
        ] + [archive.path for archive in archives],
        inputs = archives,
        tools = cc.all_files,
        outputs = outputs,
        mnemonic = "ReleaseArchive",
        progress_message = "Building release archive %{output}",
    )
    return [
        DefaultInfo(files = depset([output])),
        OutputGroupInfo(
            manifest = depset([manifest]),
            bitcode = depset(bitcode),
            bitcode_manifest = depset(bitcode_manifest),
        ),
    ]

_release_archive = rule(
    implementation = _release_archive_impl,
    attrs = {
        "archives": attr.label_list(allow_files = [".a"], cfg = _bitcode, mandatory = True),
        "format": attr.string(values = ["elf", "macho"], mandatory = True),
        "_script": attr.label(default = "//bazel:release_archive.sh", executable = True, cfg = "exec", allow_single_file = True),
    },
    toolchains = use_cc_toolchain(),
    fragments = ["cpp"],
)

def release_archive(name, archives, **kwargs):
    """One self-contained static archive of every member of `archives`, in order.

    On ELF every member with bitcode is a fat object (machine code plus ThinLTO bitcode); on Mach-O, which has no
    fat object format, every member is machine code and the `bitcode` output group holds the same members as ThinLTO
    bitcode. The `manifest` and `bitcode_manifest` output groups list each member's kind.
    """
    _release_archive(
        name = name,
        archives = archives,
        format = select({
            "@platforms//os:macos": "macho",
            "//conditions:default": "elf",
        }),
        # Compiling the merged Rust module and std at -O3 needs more than the default executor.
        exec_properties = {
            "EstimatedCPU": "4",
            "EstimatedMemory": "8GB",
        },
        **kwargs
    )
