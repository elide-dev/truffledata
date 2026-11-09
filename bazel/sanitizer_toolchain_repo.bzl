"""Pinned ASan add-on over the matching Elide toolchain; production stays unchanged."""

def _impl(ctx):
    ctx.download_and_extract(url = ctx.attr.base_urls, sha256 = ctx.attr.base_sha256, stripPrefix = "elide-toolchain")
    ctx.download_and_extract(url = ctx.attr.addon_urls, sha256 = ctx.attr.addon_sha256, stripPrefix = "elide-toolchain")
    wrapper = ctx.read(ctx.attr.base_wrapper)
    wrapper = wrapper.replace('exec "$here/bin/clang"', 'set -- "$@" --sysroot="$here/sysroot/x86_64-unknown-linux-gnu+asan" -isystem "$here/include/x86_64-unknown-linux-gnu/asan/c++/v1" -L"$here/lib/x86_64-unknown-linux-gnu/asan"\nexec "$here/bin/clang"')
    ctx.file("cc", wrapper, executable = True)
    ctx.file("BUILD.bazel", ctx.read(ctx.attr.build_file) + '\ntoolchain(name = "asan", toolchain = ":cc_toolchain", toolchain_type = "@bazel_tools//tools/cpp:toolchain_type", exec_compatible_with = ["@platforms//os:linux", "@platforms//cpu:x86_64"], target_compatible_with = ["@platforms//os:linux", "@platforms//cpu:x86_64"])\n')

sanitizer_toolchain_repo = repository_rule(
    implementation = _impl,
    attrs = {
        "base_urls": attr.string_list(mandatory = True),
        "base_sha256": attr.string(mandatory = True),
        "addon_urls": attr.string_list(mandatory = True),
        "addon_sha256": attr.string(mandatory = True),
        "base_wrapper": attr.label(mandatory = True, allow_single_file = True),
        "build_file": attr.label(mandatory = True, allow_single_file = True),
    },
)
