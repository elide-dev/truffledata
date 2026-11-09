"""The same harness runs as a normal corpus regression and under libFuzzer."""

load("@rules_cc//cc:defs.bzl", "cc_binary")
load("@rules_shell//shell:sh_test.bzl", "sh_test")

def native_fuzzer(name, src, deps):
    seeds = native.glob(["corpus/" + name + "/*"])
    cc_binary(
        name = name + "_replay_bin",
        testonly = True,
        srcs = [src],
        copts = ["-std=c++17"],
        deps = deps + ["//tools/fuzz:replay"],
    )
    sh_test(
        name = name + "_replay",
        srcs = ["//tools/fuzz:run.sh"],
        args = ["$(rootpath :" + name + "_replay_bin)"] + ["$(rootpath " + s + ")" for s in seeds],
        data = [":" + name + "_replay_bin"] + seeds,
    )
    cc_binary(
        name = name + "_fuzzer",
        testonly = True,
        srcs = [src],
        copts = ["-std=c++17"],
        linkopts = ["-fsanitize=fuzzer"],
        target_compatible_with = select({
            "//tools/fuzz:enabled": [],
            "//conditions:default": ["@platforms//:incompatible"],
        }),
        tags = ["manual"],
        deps = deps,
    )
    sh_test(
        name = name + "_fuzz",
        size = "medium",
        srcs = ["//tools/fuzz:campaign.sh"],
        args = ["$(rootpath :" + name + "_fuzzer)"] + ["$(rootpath " + s + ")" for s in seeds],
        data = [":" + name + "_fuzzer"] + seeds,
        tags = ["manual", "fuzz"],
    )
