"""Build-time Java contracts from .proto sources, without runtime descriptor initialization."""

load("@protobuf//bazel/common:proto_info.bzl", "ProtoInfo")
load("@rules_java//java:defs.bzl", "java_library")
load("//bazel:java.bzl", "NULLAWAY_OPTS")

def _generate(ctx):
    relative = ctx.attr.java_package.replace(".", "/") + "/" + ctx.attr.class_name + ".java"
    output = ctx.actions.declare_file(ctx.label.name + "/" + relative)
    root = output.path[:-len(relative) - 1]
    source_root = ctx.label.workspace_root
    infos = [dep[ProtoInfo] for dep in ctx.attr.proto_deps]
    sources = depset(direct = ctx.files.srcs, transitive = [info.transitive_sources for info in infos]).to_list()
    if not sources:
        fail("Contract generation requires srcs or proto_deps")
    if ctx.attr.proto_path:
        source_root = source_root + "/" + ctx.attr.proto_path if source_root else ctx.attr.proto_path
    args = ctx.actions.args()
    args.add("--proto_path=" + (source_root or "."))
    proto_roots = depset(transitive = [info.transitive_proto_path for info in infos]).to_list()
    for path in proto_roots:
        args.add("--proto_path=" + path)
    args.add("--plugin=protoc-gen-truffledata=" + ctx.executable._plugin.path)
    args.add("--truffledata_out=package=" + ctx.attr.java_package + ",class=" + ctx.attr.class_name + ":" + root)
    for source in sources:
        name = source.path
        for path in proto_roots:
            if name.startswith(path + "/"):
                name = name[len(path) + 1:]
                break
        args.add(name)
    ctx.actions.run_shell(
        outputs = [output],
        inputs = sources,
        tools = [ctx.attr._protoc[DefaultInfo].files_to_run, ctx.attr._plugin[DefaultInfo].files_to_run],
        command = 'mkdir -p "$1" && shift && "$@"',
        arguments = [root, ctx.executable._protoc.path, args],
        mnemonic = "ProtoContracts",
    )
    return [DefaultInfo(files = depset([output]))]

_proto_contracts = rule(
    implementation = _generate,
    attrs = {
        "srcs": attr.label_list(allow_files = [".proto"]),
        "proto_deps": attr.label_list(providers = [ProtoInfo]),
        "proto_path": attr.string(),
        "java_package": attr.string(mandatory = True),
        "class_name": attr.string(mandatory = True),
        "_protoc": attr.label(default = Label("@protobuf//:protoc"), executable = True, cfg = "exec"),
        "_plugin": attr.label(default = Label("//protobuf:protoc-gen-truffledata"), executable = True, cfg = "exec"),
    },
)

def proto_contract_library(name, srcs = [], proto_path = "", java_package = "dev.elide.truffledata.generated", class_name = "TruffleDataContracts", testonly = False, visibility = None, proto_deps = []):
    """Generate a registry; proto_path is relative to the calling repository's root."""
    _proto_contracts(
        name = name + "_generate",
        srcs = srcs,
        proto_deps = proto_deps,
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
            Label("//protobuf-runtime"),
            Label("@maven//:org_graalvm_truffle_truffle_api"),
            Label("@maven//:org_jspecify_jspecify"),
            Label("//bazel:truffle_processor"),
        ],
        plugins = [Label("//bazel:nullaway")],
        javacopts = NULLAWAY_OPTS,
        testonly = testonly,
        visibility = visibility,
    )
