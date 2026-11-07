# TEMPORARY (YAML probe): libyaml 0.2.5.
load("@rules_cc//cc:defs.bzl", "cc_library")

cc_library(
    name = "libyaml",
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
