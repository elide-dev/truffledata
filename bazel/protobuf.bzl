"""Build-time Java contracts from .proto sources, without runtime descriptor initialization."""

load("@rules_java//java:defs.bzl", "java_library")
load("//bazel:java.bzl", "NULLAWAY_OPTS")

def _generate(ctx):
    relative = ctx.attr.java_package.replace(".", "/") + "/" + ctx.attr.class_name + ".java"
    output = ctx.actions.declare_file(ctx.label.name + "/" + relative)
    root = output.path[:-len(relative) - 1]
    source_root = ctx.label.workspace_root
    if ctx.attr.proto_path:
        source_root = source_root + "/" + ctx.attr.proto_path if source_root else ctx.attr.proto_path
    args = ctx.actions.args()
    args.add("--proto_path=" + (source_root or "."))
    args.add("--plugin=protoc-gen-truffledata=" + ctx.executable._plugin.path)
    args.add("--truffledata_out=package=" + ctx.attr.java_package + ",class=" + ctx.attr.class_name + ":" + root)
    args.add_all(ctx.files.srcs)
    ctx.actions.run_shell(
        outputs = [output],
        inputs = ctx.files.srcs,
        tools = [ctx.attr._protoc[DefaultInfo].files_to_run, ctx.attr._plugin[DefaultInfo].files_to_run],
        command = 'mkdir -p "$1" && shift && "$@"',
        arguments = [root, ctx.executable._protoc.path, args],
        mnemonic = "ProtoContracts",
    )
    return [DefaultInfo(files = depset([output]))]

_proto_contracts = rule(
    implementation = _generate,
    attrs = {
        "srcs": attr.label_list(allow_files = [".proto"], mandatory = True),
        "proto_path": attr.string(mandatory = True),
        "java_package": attr.string(mandatory = True),
        "class_name": attr.string(mandatory = True),
        "_protoc": attr.label(default = Label("@protobuf//:protoc"), executable = True, cfg = "exec"),
        "_plugin": attr.label(default = Label("//protobuf:protoc-gen-truffledata"), executable = True, cfg = "exec"),
    },
)

def proto_contract_library(name, srcs, proto_path, java_package = "dev.elide.truffledata.generated", class_name = "TruffleDataContracts", testonly = False, visibility = None):
    """Generate a registry; proto_path is relative to the calling repository's root."""
    _proto_contracts(
        name = name + "_generate",
        srcs = srcs,
        proto_path = proto_path,
        java_package = java_package,
        class_name = class_name,
        testonly = testonly,
        visibility = visibility,
    )
    java_library(
        name = name,
        srcs = [":" + name + "_generate"],
        deps = [
            Label("//core"),
            Label("//protobuf"),
            Label("@maven//:org_graalvm_truffle_truffle_api"),
            Label("@maven//:org_jspecify_jspecify"),
            Label("//bazel:truffle_processor"),
        ],
        plugins = [Label("//bazel:nullaway")],
        javacopts = NULLAWAY_OPTS,
        testonly = testonly,
        visibility = visibility,
    )
