# libyaml 0.2.5 (MIT), benchmark only: the C reference row of //yaml/native:bench, built as the YAML
# probe built it (benchmarks/results/yaml-probe-b242abd/sources/bazel/probe_libyaml.BUILD).
load("@rules_cc//cc:defs.bzl", "cc_library")

cc_library(
    name = "libyaml",
    testonly = True,
    srcs = glob(["src/*.c"]) + ["src/yaml_private.h"],
    hdrs = ["include/yaml.h"],
    copts = ["-O3"],
    defines = ["YAML_DECLARE_STATIC"],
    includes = ["include"],
    local_defines = [
        "YAML_VERSION_MAJOR=0",
        "YAML_VERSION_MINOR=2",
        "YAML_VERSION_PATCH=5",
        "YAML_VERSION_STRING=\\\"0.2.5\\\"",
    ],
    visibility = ["//visibility:public"],
)
