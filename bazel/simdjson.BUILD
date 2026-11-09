load("@rules_cc//cc:defs.bzl", "cc_library")

cc_library(
    name = "simdjson",
    srcs = ["singleheader/simdjson.cpp"],
    hdrs = ["singleheader/simdjson.h"],
    copts = [
        "-std=c++17",
        "-O3",
    ],
    includes = ["singleheader"],
    visibility = ["//visibility:public"],
)

# simdjson's generic number parser, unmodified from the pinned release, for the JSON number
# kernels (//json/native:number_kernel) that instantiate it under the westmere/haswell target
# regions. Included as "simdjson_generic/<file>.h": only with SIMDJSON_CONDITIONAL_INCLUDE defined,
# after the amalgamated simdjson.h, so the non-amalgamated include tree is never reached.
cc_library(
    name = "generic_numberparsing",
    hdrs = [
        "include/simdjson/generic/atomparsing.h",
        "include/simdjson/generic/base.h",
        "include/simdjson/generic/jsoncharutils.h",
        "include/simdjson/generic/numberparsing.h",
    ],
    include_prefix = "simdjson_generic",
    strip_include_prefix = "include/simdjson/generic",
    visibility = ["//visibility:public"],
    deps = [":simdjson"],
)
