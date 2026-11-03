load("@rules_java//java:defs.bzl", "java_library")

NULLAWAY_OPTS = [
    "-Werror",
    "-XepDisableAllChecks",
    "-Xep:NullAway:ERROR",
    "-Xep:RequireExplicitNullMarking:ERROR",
    "-Xep:JSpecifyUnrecognizedAnnotationLocation:ERROR",
    "-XepOpt:NullAway:OnlyNullMarked=true",
    "-XepOpt:NullAway:JSpecifyMode=true",
    "-XepOpt:NullAway:TreatGeneratedAsUnannotated=true",
    "-XepExcludedPaths:.*/_javac/.*",
]

def data_java_library(name, deps = [], plugins = [], **kwargs):
    java_library(
        name = name,
        deps = deps + ["@maven//:org_jspecify_jspecify", "//bazel:truffle_processor"],
        plugins = plugins + ["//bazel:nullaway"],
        javacopts = NULLAWAY_OPTS,
        **kwargs
    )
