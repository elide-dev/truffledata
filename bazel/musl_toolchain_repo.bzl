"""Reuse a pinned LLVM bundle without copying its multi-gigabyte sysroots."""

def _impl(ctx):
    root = ctx.path(ctx.attr.base).dirname
    for entry in root.readdir():
        if entry.basename not in ["BUILD.bazel", "BUILD", "cc"]:
            ctx.symlink(entry, entry.basename)
    ctx.file("cc", ctx.read(root.get_child("cc")).replace("-unknown-linux-gnu", "-unknown-linux-musl"), executable = True)
    ctx.file("BUILD.bazel", ctx.read(ctx.attr.build_file))

musl_toolchain_repo = repository_rule(
    implementation = _impl,
    attrs = {
        "base": attr.label(mandatory = True, allow_single_file = True),
        "build_file": attr.label(mandatory = True, allow_single_file = True),
    },
)
