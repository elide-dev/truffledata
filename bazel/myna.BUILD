load("@rules_cc//cc:defs.bzl", "cc_library")
load("@rules_java//java:defs.bzl", "java_binary", "java_library")

java_binary(
    name = "generator",
    srcs = glob(["src/main/java/**/*.java"]),
    main_class = "dev.elide.myna.cli.Main",
    visibility = ["//visibility:public"],
)

# Myna's producer-neutral value ABI is separate from the code generator.
filegroup(
    name = "values_sources",
    srcs = glob(["src/values/java/**/*.java"]),
    visibility = ["//visibility:public"],
)

java_library(
    name = "values",
    srcs = [":values_sources"],
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:org_graalvm_sdk_nativeimage",
        "@maven//:org_graalvm_sdk_word",
    ],
)

# myna-truffle: the language-neutral runtime of Myna's Truffle native cells (NativeCell, coercions,
# pointers, borrows). Benchmark-only consumer today (//benchmarks/comparison WS11 probe); no shipped
# artifact depends on it. Needs the Truffle DSL processor for its interop exports.
java_library(
    name = "truffle",
    srcs = glob(["src/truffle/java/**/*.java"]),
    resource_strip_prefix = "src/truffle/resources",
    resources = glob(["src/truffle/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "@//bazel:truffle_processor",
        "@maven//:org_graalvm_sdk_nativeimage",
        "@maven//:org_graalvm_sdk_word",
        "@maven//:org_graalvm_truffle_truffle_api",
    ],
)

cc_library(
    name = "values_headers",
    hdrs = [
        "include/myna/values.h",
        "include/myna/values.hpp",
    ],
    includes = ["include"],
    visibility = ["//visibility:public"],
)
