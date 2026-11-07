"""Dependency management for the tested TruffleData and upstream driver combination."""

load("//bazel:maven.bzl", "VersionInfo", "gradle_metadata")

def _bom_impl(ctx):
    version = ctx.attr._version[VersionInfo].value
    if not version or any([c not in "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-" for c in version.elems()]):
        fail("Invalid Maven version")
    dependencies = []
    graalvm_version = None
    for coordinate in ctx.attr.managed:
        group, artifact, dep_version = coordinate.split(":")
        if group == "org.graalvm.truffle" and artifact == "truffle-api":
            graalvm_version = dep_version
        dependencies.append("<dependency><groupId>%s</groupId><artifactId>%s</artifactId><version>%s</version><scope>compile</scope></dependency>" % (group, artifact, dep_version.replace("{version}", version)))
    if not graalvm_version:
        fail("BOM requires the tested Truffle API version")
    pom = ctx.actions.declare_file("%s/dev/elide/truffledata-bom/%s/truffledata-bom-%s.pom" % (ctx.label.name, version, version))
    ctx.actions.write(pom, """<?xml version="1.0" encoding="UTF-8"?>
<!-- do_not_remove: published-with-gradle-metadata -->
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>dev.elide</groupId><artifactId>truffledata-bom</artifactId><version>{version}</version>
  <packaging>pom</packaging><name>TruffleData BOM</name>
  <description>Tested TruffleData library, runtime and driver versions; consumers may override managed versions.</description>
  <url>https://github.com/elide-dev/truffledata</url>
  <licenses>
    <license><name>MIT License</name><url>https://opensource.org/license/mit</url><distribution>repo</distribution><comments>MIT OR Apache-2.0, at your option.</comments></license>
    <license><name>Apache License, Version 2.0</name><url>https://www.apache.org/licenses/LICENSE-2.0.txt</url><distribution>repo</distribution><comments>MIT OR Apache-2.0, at your option.</comments></license>
  </licenses>
  <developers><developer><id>elide-dev</id><name>The TruffleData Authors</name><url>https://github.com/elide-dev/truffledata/graphs/contributors</url><organization>Elide</organization><organizationUrl>https://elide.dev</organizationUrl></developer></developers>
  <scm><connection>scm:git:https://github.com/elide-dev/truffledata.git</connection><developerConnection>scm:git:ssh://git@github.com/elide-dev/truffledata.git</developerConnection><url>https://github.com/elide-dev/truffledata</url><tag>v{version}</tag></scm>
  <repositories><repository><id>elide-graalvm</id><url>https://maven.elide.dev/gvm-elide/{graalvm_version}</url></repository></repositories>
  <dependencyManagement><dependencies>{dependencies}</dependencies></dependencyManagement>
</project>
""".format(version = version, graalvm_version = graalvm_version, dependencies = "\n    ".join(dependencies)))
    metadata = ctx.actions.declare_file("%s/dev/elide/truffledata-bom/%s/truffledata-bom-%s.module" % (ctx.label.name, version, version))
    constraints = []
    for coordinate in ctx.attr.managed:
        group, artifact, dep_version = coordinate.split(":")
        constraints.append({"group": group, "module": artifact, "version": {"requires": dep_version.replace("{version}", version)}})
    ctx.actions.write(metadata, gradle_metadata({
        "formatVersion": "1.1",
        "component": {"group": "dev.elide", "module": "truffledata-bom", "version": version, "attributes": {"org.gradle.status": "release"}},
        "variants": [{
            "name": usage,
            "attributes": {"org.gradle.category": "platform", "org.gradle.usage": usage},
            "dependencyConstraints": constraints,
        } for usage in ["java-api", "java-runtime"]],
    }) + "\n")
    return [DefaultInfo(files = depset([pom, metadata]))]

maven_bom = rule(
    implementation = _bom_impl,
    attrs = {
        "managed": attr.string_list(mandatory = True),
        "_version": attr.label(default = "//tools/release:version"),
    },
)
