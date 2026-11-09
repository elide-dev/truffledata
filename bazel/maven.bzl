"""Thin Maven publications from the same JavaInfo used by Bazel consumers."""

load("@rules_java//java/common:java_common.bzl", "java_common")
load("@rules_java//java/common:java_info.bzl", "JavaInfo")

VersionInfo = provider(fields = ["value"])

def gradle_metadata(value):
    # Gradle's streaming parser requires formatVersion first; json.encode_indent sorts keys.
    return '{"formatVersion":"1.1",' + json.encode_indent({k: v for k, v in value.items() if k != "formatVersion"})[1:]

def _version_impl(ctx):
    return [VersionInfo(value = ctx.build_setting_value)]

release_version = rule(implementation = _version_impl, build_setting = config.string(flag = True))

# Attachment classifiers per target platform: (classifier, shared library extension, bitcode twin). The static archive
# is -<classifier>.a, the shared library -<classifier>.so on Linux and -<classifier>.dylib (install name
# @rpath/libtruffledata_<name>.dylib) on macOS, where -<classifier>-bitcode.a carries the static archive's members as
# ThinLTO bitcode, since Mach-O has no fat objects. Keep in sync with tools/release/maven_repo.py.
CLASSIFIERS = {
    "//bazel/platforms:linux_amd64": ("linux-x86_64", ".so", False),
    "//bazel/platforms:linux_arm64": ("linux-aarch64", ".so", False),
    "//bazel/platforms:macos_arm64": ("macos-aarch64", ".dylib", True),
}

def classifier_attachments(static, shared, neutral = {}, manifests = False):
    """A select() of the target platform's attachments for one native module.

    Args:
      static: a release_archive label (its `<label>_bitcode`, `<label>_manifest` and `<label>_bitcode_manifest`
        filegroups must exist).
      shared: the shared library label.
      neutral: platform-independent attachments, label to suffix.
      manifests: also attach the archives' member manifests (release gate input, never published).
    """
    return select({
        setting: {
            static: "-%s.a" % classifier,
            shared: "-%s%s" % (classifier, extension),
        } | ({
            static + "_bitcode": "-%s-bitcode.a" % classifier,
        } if bitcode else {}) | ({
            static + "_manifest": "-%s.members.txt" % classifier,
        } if manifests else {}) | ({
            static + "_bitcode_manifest": "-%s-bitcode.members.txt" % classifier,
        } if manifests and bitcode else {}) | neutral
        for setting, (classifier, extension, bitcode) in CLASSIFIERS.items()
    })

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
<!-- do_not_remove: published-with-gradle-metadata -->
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>dev.elide</groupId><artifactId>{artifact}</artifactId><version>{version}</version>
  <name>TruffleData {module}</name>
  <description>Query-aware Truffle data access: {module}</description>
  <properties><maven.compiler.release>25</maven.compiler.release></properties>
  <url>https://github.com/elide-dev/truffledata</url>
  <licenses>
    <license><name>MIT License</name><url>https://opensource.org/license/mit</url><distribution>repo</distribution><comments>MIT OR Apache-2.0, at your option.</comments></license>
    <license><name>Apache License, Version 2.0</name><url>https://www.apache.org/licenses/LICENSE-2.0.txt</url><distribution>repo</distribution><comments>MIT OR Apache-2.0, at your option.</comments></license>
  </licenses>
  <developers><developer><id>elide-dev</id><name>The TruffleData Authors</name><url>https://github.com/elide-dev/truffledata/graphs/contributors</url><organization>Elide</organization><organizationUrl>https://elide.dev</organizationUrl></developer></developers>
  <scm><connection>scm:git:https://github.com/elide-dev/truffledata.git</connection><developerConnection>scm:git:ssh://git@github.com/elide-dev/truffledata.git</developerConnection><url>https://github.com/elide-dev/truffledata</url><tag>v{version}</tag></scm>
  <repositories><repository><id>elide-graalvm</id><url>https://maven.elide.dev/gvm-elide/{graalvm_version}</url></repository></repositories>
  <dependencies>{dependencies}</dependencies>
</project>
""".format(graalvm_version = graalvm_version, artifact = artifact, version = version, module = ctx.attr.module, dependencies = "\n    ".join(dependencies)))
    info = ctx.attr.library[JavaInfo]
    outputs = [pom]
    runtime = ctx.attr._java_runtime[java_common.JavaRuntimeInfo]
    licenses = ctx.files._licenses
    for suffix, source in [(".jar", info.runtime_output_jars[0]), ("-sources.jar", info.source_jars[0]), ("-javadoc.jar", info.source_jars[0])]:
        output = ctx.actions.declare_file(prefix + suffix)
        ctx.actions.run(
            executable = ctx.executable._publication,
            arguments = [
                "javadoc" if suffix == "-javadoc.jar" else "jar",
                output.path,
                runtime.java_home,
                source.path,
                ":".join([f.path for f in info.transitive_compile_time_jars.to_list()]),
            ] + [f.path for f in licenses],
            inputs = depset([source] + licenses, transitive = [info.transitive_compile_time_jars]),
            tools = runtime.files,
            outputs = [output],
            mnemonic = "PublicationJar",
        )
        outputs.append(output)
    metadata = ctx.actions.declare_file(prefix + ".module")
    dependencies = []
    for coordinate in ctx.attr.dependencies:
        group, name, dep_version = coordinate.split(":")
        dependencies.append({"group": group, "module": name, "version": {"requires": dep_version.replace("{version}", version)}})
    variants = [{
        "name": usage,
        "attributes": {
            "org.gradle.category": "library",
            "org.gradle.usage": usage,
            "org.gradle.libraryelements": "jar",
            "org.gradle.dependency.bundling": "external",
            "org.gradle.jvm.version": 25,
        },
        "dependencies": dependencies,
        "files": [{"name": artifact + "-" + version + ".jar", "url": artifact + "-" + version + ".jar"}],
    } for usage in ["java-api", "java-runtime"]]
    variants.append({
        "name": "sourcesElements",
        "attributes": {"org.gradle.category": "documentation", "org.gradle.docstype": "sources", "org.gradle.usage": "java-runtime", "org.gradle.dependency.bundling": "external"},
        "files": [{"name": artifact + "-" + version + "-sources.jar", "url": artifact + "-" + version + "-sources.jar"}],
    })
    variants.append({
        "name": "javadocElements",
        "attributes": {"org.gradle.category": "documentation", "org.gradle.docstype": "javadoc", "org.gradle.usage": "java-runtime", "org.gradle.dependency.bundling": "external"},
        "files": [{"name": artifact + "-" + version + "-javadoc.jar", "url": artifact + "-" + version + "-javadoc.jar"}],
    })
    ctx.actions.write(metadata, gradle_metadata({
        "formatVersion": "1.1",
        "component": {"group": "dev.elide", "module": artifact, "version": version, "attributes": {"org.gradle.status": "release"}},
        "variants": variants,
    }) + "\n")
    outputs.append(metadata)
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
        if suffix.endswith(".a") and not suffix.endswith("-bitcode.a"):
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
        "_publication": attr.label(default = "//bazel:publication.sh", executable = True, cfg = "exec", allow_single_file = True),
        "_java_runtime": attr.label(default = "@bazel_tools//tools/jdk:current_java_runtime", cfg = "exec"),
        "_licenses": attr.label_list(default = ["//:LICENSE", "//:LICENSE-MIT", "//:LICENSE-APACHE"], allow_files = True),
        "module": attr.string(mandatory = True),
        "dependencies": attr.string_list(),
        "attachments": attr.label_keyed_string_dict(),
        "_version": attr.label(default = "//tools/release:version"),
    },
)
