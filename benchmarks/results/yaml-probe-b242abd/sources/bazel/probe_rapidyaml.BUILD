# TEMPORARY (YAML probe): rapidyaml 0.16.0 with its bundled c4core and the extra integer event handler.
load("@rules_cc//cc:defs.bzl", "cc_library")

cc_library(
    name = "ryml",
    srcs = glob([
        "src/c4/yml/*.cpp",
        "ext/c4core.src/c4/*.cpp",
        "src_extra/c4/yml/extra/*.cpp",
    ]),
    hdrs = glob([
        "src/**/*.hpp",
        "ext/c4core.src/**/*.hpp",
        "ext/c4core.src/**/*.h",
        "src_extra/**/*.hpp",
    ]),
    copts = [
        "-std=c++17",
        "-O3",
    ],
    defines = [
        "RYML_DEFAULT_CALLBACK_USES_EXCEPTIONS",
        "RYML_WITH_TAB_TOKENS",
    ],
    includes = [
        "ext/c4core.src",
        "src",
        "src_extra",
    ],
    visibility = ["//visibility:public"],
)
