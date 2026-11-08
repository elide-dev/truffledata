"""Shared safety checks for positive native images and the expected-failure control."""

TRUFFLE_JIT_CHECKS = [
    "-H:+TruffleCheckBlockListMethods",
    "-H:+TruffleCheckNeverPartOfCompilation",
]

# Semantic acceptance runs on both supported Linux SVM architectures. The benchmark images
# declare their own architecture explicitly; an x86 timing result is not an Arm timing claim.
LINUX_SVM_SDK = select({
    "//bazel/platforms:linux_arm64": "@graalvm_linux_aarch64//:gvm",
    "//conditions:default": "@graalvm_linux_amd64//:gvm",
})

LINUX_SVM_PLATFORMS = select({
    "//bazel/platforms:linux_amd64": ["@platforms//os:linux", "@platforms//cpu:x86_64"],
    "//bazel/platforms:linux_arm64": ["@platforms//os:linux", "@platforms//cpu:aarch64"],
    "//conditions:default": ["@platforms//:incompatible"],
})
