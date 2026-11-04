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
