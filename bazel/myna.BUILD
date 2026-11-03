load("@rules_java//java:defs.bzl", "java_binary")

java_binary(
    name = "generator",
    srcs = glob(["src/main/java/**/*.java"]),
    main_class = "dev.elide.myna.cli.Main",
    visibility = ["//visibility:public"],
)
