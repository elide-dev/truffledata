load("@rules_cc//cc:defs.bzl", "cc_library")
load("@rules_java//java:defs.bzl", "java_binary", "java_library")

java_binary(
    name = "generator",
    srcs = glob(["src/main/java/**/*.java"]),
    main_class = "dev.elide.myna.cli.Main",
    visibility = ["//visibility:public"],
)

# Myna's producer-neutral value ABI is separate from the code generator.
java_library(
    name = "values",
    srcs = glob(["src/values/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:org_graalvm_sdk_nativeimage",
        "@maven//:org_graalvm_sdk_word",
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
