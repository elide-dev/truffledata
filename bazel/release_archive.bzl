"""Release static archives: merged, self-contained, and linkable with or without LTO (bazel/release_archive.sh)."""

load("@rules_cc//cc:find_cc_toolchain.bzl", "find_cc_toolchain", "use_cc_toolchain")

_EXTRA_RUSTC_FLAGS = "@rules_rust//rust/settings:extra_rustc_flags"

def _bitcode_impl(settings, _attr):
    # Rust crates emit bitcode for the archive: under --@rules_rust//rust/settings:lto=thin rlibs already do, and
    # -Clinker-plugin-lto turns the staticlib's own crate from rustc-LTO machine code into bitcode too. C/C++ objects
    # are bitcode under the default --features=thin_lto.
    flags = settings[_EXTRA_RUSTC_FLAGS]
    if "-Clinker-plugin-lto" in flags:
        return {_EXTRA_RUSTC_FLAGS: flags}
    return {_EXTRA_RUSTC_FLAGS: flags + ["-Clinker-plugin-lto"]}

_bitcode = transition(
    implementation = _bitcode_impl,
    inputs = [_EXTRA_RUSTC_FLAGS],
    outputs = [_EXTRA_RUSTC_FLAGS],
)

def _release_archive_impl(ctx):
    cc = find_cc_toolchain(ctx)
    output = ctx.actions.declare_file("lib%s.a" % ctx.label.name)
    manifest = ctx.actions.declare_file("lib%s.members.txt" % ctx.label.name)
    archives = ctx.files.archives
    ctx.actions.run(
        executable = ctx.executable._script,
        arguments = [
            ctx.attr.format,
            output.path,
            manifest.path,
            cc.compiler_executable,
            cc.ar_executable.rpartition("/")[0],
        ] + [archive.path for archive in archives],
        inputs = archives,
        tools = cc.all_files,
        outputs = [output, manifest],
        mnemonic = "ReleaseArchive",
        progress_message = "Building release archive %{output}",
    )
    return [
        DefaultInfo(files = depset([output])),
        OutputGroupInfo(manifest = depset([manifest])),
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
    fat object format, every member is machine code. The `manifest` output group lists each member's kind.
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
