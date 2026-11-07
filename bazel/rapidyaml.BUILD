# rapidyaml 0.16.0 with its bundled c4core (MIT). Configuration per
# docs/superpowers/plans/2026-11-06-yaml.md (D1, Global Constraints):
#
#   - RYML_USE_ASSERT=0 / C4_USE_ASSERT=0: assertions off whatever NDEBUG says. With assertions on,
#     every plain scalar in a flow mapping rescans the rest of its line, so one-line flow documents
#     parse in quadratic time (biojppm/rapidyaml#694).
#   - RYML_WITH_TAB_TOKENS: tabs separate tokens, as YAML 1.2.2 requires (9 yaml-test-suite cases).
#   - RYML_DEFAULT_CALLBACK_USES_EXCEPTIONS: the default error callbacks throw; the C ABI catches.
#
# The switches are `defines`, not `copts`: they must reach every translation unit that instantiates
# ParseEngine<Handler>, including TruffleData's own handlers. src_extra (the integer event handler)
# stays out of these targets. No patches are applied.
load("@rules_cc//cc:defs.bzl", "cc_library")

_SRCS = glob([
    "src/c4/yml/*.cpp",
    "ext/c4core.src/c4/*.cpp",
])

_HDRS = glob([
    "src/**/*.hpp",
    "ext/c4core.src/**/*.hpp",
    "ext/c4core.src/**/*.h",
])

_COPTS = [
    "-std=c++17",
    "-O3",
]

_INCLUDES = [
    "ext/c4core.src",
    "src",
]

_CONFIG = [
    "RYML_WITH_TAB_TOKENS",
    "RYML_DEFAULT_CALLBACK_USES_EXCEPTIONS",
]

cc_library(
    name = "ryml",
    srcs = _SRCS,
    hdrs = _HDRS,
    copts = _COPTS,
    defines = _CONFIG + [
        "RYML_USE_ASSERT=0",
        "C4_USE_ASSERT=0",
    ],
    includes = _INCLUDES,
    visibility = ["//visibility:public"],
)

# The assertion lane: the only build with rapidyaml's assertions on. Test-only; never link it
# together with :ryml (the two differ in noexcept specifications and inline definitions).
cc_library(
    name = "ryml_assert",
    testonly = True,
    srcs = _SRCS,
    hdrs = _HDRS,
    copts = _COPTS,
    defines = _CONFIG + [
        "RYML_USE_ASSERT=1",
        "C4_USE_ASSERT=1",
    ],
    includes = _INCLUDES,
    visibility = ["//visibility:public"],
)

# Test and benchmark only: rapidyaml's integer event handler (src_extra). It is the YAML probe's
# reference for tape parity (//yaml/native:decoder_test) and the `ryml-ints(scan)` benchmark row
# (//yaml/native:bench). Never a production dependency.
cc_library(
    name = "ryml_ints",
    testonly = True,
    srcs = ["src_extra/c4/yml/extra/event_handler_ints.cpp"],
    hdrs = glob(["src_extra/c4/yml/extra/*.hpp"]),
    copts = _COPTS,
    includes = ["src_extra"],
    visibility = ["//visibility:public"],
    deps = [":ryml"],
)
