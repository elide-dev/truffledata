"""Merges static archives into one with the C++ toolchain's archiver (llvm-ar MRI script)."""

load("@rules_cc//cc:find_cc_toolchain.bzl", "find_cc_toolchain", "use_cc_toolchain")

def _merge_archives_impl(ctx):
    cc = find_cc_toolchain(ctx)
    output = ctx.actions.declare_file("lib%s.a" % ctx.label.name)
    archives = ctx.files.archives
    script = ctx.actions.declare_file(ctx.label.name + ".mri")
    ctx.actions.write(
        script,
        "create %s\n" % output.path + "".join(["addlib %s\n" % archive.path for archive in archives]) + "save\nend\n",
    )
    ctx.actions.run_shell(
        inputs = archives + [script],
        tools = cc.all_files,
        outputs = [output],
        command = '"$1" -M < "$2"',
        arguments = [cc.ar_executable, script.path],
        mnemonic = "MergeArchives",
        progress_message = "Merging static archives into %{output}",
    )
    return [DefaultInfo(files = depset([output]))]

merge_archives = rule(
    implementation = _merge_archives_impl,
    doc = "One static archive holding every member of `archives`, in order.",
    attrs = {
        "archives": attr.label_list(allow_files = [".a"], mandatory = True),
    },
    toolchains = use_cc_toolchain(),
    fragments = ["cpp"],
)
