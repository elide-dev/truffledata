"""Thin Maven publications from the same JavaInfo used by Bazel consumers."""

load("@rules_java//java/common:java_info.bzl", "JavaInfo")

VersionInfo = provider(fields = ["value"])

def _version_impl(ctx):
    return [VersionInfo(value = ctx.build_setting_value)]

release_version = rule(implementation = _version_impl, build_setting = config.string(flag = True))

def _publication_impl(ctx):
    version = ctx.attr._version[VersionInfo].value
    if not version or any([c not in "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-" for c in version.elems()]):
        fail("Invalid Maven version")
    artifact = "truffledata-" + ctx.attr.module
    prefix = "%s/dev/elide/%s/%s/%s-%s" % (ctx.label.name, artifact, version, artifact, version)
    dependencies = []
    graalvm_version = None
    for coordinate in ctx.attr.dependencies:
        group, name, dep_version = coordinate.split(":")
        if group == "org.graalvm.truffle" and name == "truffle-api":
            graalvm_version = dep_version
        dependencies.append("<dependency><groupId>%s</groupId><artifactId>%s</artifactId><version>%s</version><scope>compile</scope></dependency>" % (group, name, dep_version.replace("{version}", version)))
    if not graalvm_version:
        fail("Publication requires an explicit Truffle API dependency")
    pom = ctx.actions.declare_file(prefix + ".pom")
    ctx.actions.write(pom, """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>dev.elide</groupId><artifactId>{artifact}</artifactId><version>{version}</version>
  <name>TruffleData {module}</name>
  <description>Query-aware Truffle data access: {module}</description>
  <properties><maven.compiler.release>25</maven.compiler.release></properties>
  <repositories><repository><id>elide-graalvm</id><url>https://maven.elide.dev/gvm-elide/{graalvm_version}</url></repository></repositories>
  <dependencies>{dependencies}</dependencies>
</project>
""".format(graalvm_version = graalvm_version, artifact = artifact, version = version, module = ctx.attr.module, dependencies = "\n    ".join(dependencies)))
    info = ctx.attr.library[JavaInfo]
    outputs = [pom]
    for suffix, source in [(".jar", info.runtime_output_jars[0]), ("-sources.jar", info.source_jars[0])]:
        output = ctx.actions.declare_file(prefix + suffix)
        ctx.actions.symlink(output = output, target_file = source)
        outputs.append(output)
    native_static, native_shared, other = _attachments(ctx, prefix)
    outputs += native_static + native_shared + other
    return [DefaultInfo(files = depset(outputs)), OutputGroupInfo(binary = depset([outputs[1]]), native_static = depset(native_static), native_shared = depset(native_shared))]

def _prefix(ctx):
    version = ctx.attr._version[VersionInfo].value
    if not version or any([c not in "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-" for c in version.elems()]):
        fail("Invalid Maven version")
    artifact = "truffledata-" + ctx.attr.module
    return "%s/dev/elide/%s/%s/%s-%s" % (ctx.label.name, artifact, version, artifact, version)

def _attachments(ctx, prefix):
    """Symlinks each attachment as <prefix><suffix>: (static archives, shared libraries, other files such as headers)."""
    native_static = []
    native_shared = []
    other = []
    for target, suffix in ctx.attr.attachments.items():
        files = target[DefaultInfo].files.to_list()
        if len(files) != 1:
            fail("Publication attachment must contain exactly one file")
        output = ctx.actions.declare_file(prefix + suffix)
        ctx.actions.symlink(output = output, target_file = files[0])
        if suffix.endswith(".a"):
            native_static.append(output)
        elif suffix.endswith(".so") or suffix.endswith(".dylib"):
            native_shared.append(output)
        else:
            other.append(output)
    return native_static, native_shared, other

def _native_attachments_impl(ctx):
    native_static, native_shared, other = _attachments(ctx, _prefix(ctx))
    return [DefaultInfo(files = depset(native_static + native_shared + other)), OutputGroupInfo(native_static = depset(native_static), native_shared = depset(native_shared))]

native_attachments = rule(
    implementation = _native_attachments_impl,
    doc = "A module's native attachments for the target platform, in its maven_publication layout (no jars or POM).",
    attrs = {
        "module": attr.string(mandatory = True),
        "attachments": attr.label_keyed_string_dict(),
        "_version": attr.label(default = "//tools/release:version"),
    },
)

maven_publication = rule(
    implementation = _publication_impl,
    attrs = {
        "library": attr.label(providers = [JavaInfo], mandatory = True),
        "module": attr.string(mandatory = True),
        "dependencies": attr.string_list(),
        "attachments": attr.label_keyed_string_dict(),
        "_version": attr.label(default = "//tools/release:version"),
    },
)
