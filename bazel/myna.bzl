"""Myna bindings generation for the three release targets, across Myna 0.14.0's two ABI classes.

Myna 0.14.0 makes C `char` signedness part of the target ABI class: x86_64-unknown-linux-gnu and
aarch64-apple-darwin share the LP64 little-endian signed-char class (`LP64_LITTLE`),
aarch64-unknown-linux-gnu (AAPCS64) is its own class (`LP64_LITTLE_AAPCS64`) with its own fingerprint.
`myna_bindings` validates each class across its release targets, generates the bindings for the
configured target (so a static image folds its own class's constant), and, through
//bazel:myna_abi.py, adds the `<Class>Abi` constants of both classes to the bindings, folds both
fingerprints into one `seam.h` selected by `__CHAR_UNSIGNED__`, and keeps each class's `seam.abi` and
`seam.json` under `<out>/abi/<class>/` for the contract check and tests.
"""

load("@rules_cc//cc:defs.bzl", "cc_binary")

LP64_LITTLE_TARGETS = [
    "x86_64-unknown-linux-gnu",
    "aarch64-apple-darwin",
]

LP64_LITTLE_AAPCS64_TARGETS = ["aarch64-unknown-linux-gnu"]

# Each class's per-class output directory (under <out>/abi) and the target that generates it.
ABI_CLASSES = {
    "lp64_little": LP64_LITTLE_TARGETS[0],
    "lp64_little_aapcs64": LP64_LITTLE_AAPCS64_TARGETS[0],
}

_GENERATOR = "@myna//:generator"
_FOLD = "//bazel:myna_abi.py"

def _target_select():
    return select({
        "@platforms//os:macos": "aarch64-apple-darwin",
        "//bazel/platforms:linux_arm64": "aarch64-unknown-linux-gnu",
        "//conditions:default": "x86_64-unknown-linux-gnu",
    })

def myna_bindings(name, seam, java_class, java_package, out = "generated", abi = True, truffle = False, visibility = None):
    """Generates `seam`'s bindings into `out` for the configured target.

    Args:
      name: the genrule name.
      seam: the `.seam` label.
      java_class: the seam's `java_class` (names the generated Java classes).
      java_package: the seam's `java_package` (names the Native Image metadata directory).
      out: the output directory, relative to the package.
      abi: emit `<java_class>Abi.java`, the class-selected `seam.h` and `abi/<class>/` (production seams).
        Derived cells seams leave it off: their fingerprints are never compared with a library.
      truffle: the seam enables Truffle cells (adds `<java_class>Truffle.java`).
      visibility: the genrule's visibility.
    """
    outs = [
        java_class + ".java",
        java_class + "FFM.java",
        java_class + "ForeignFeature.java",
        "seam.rs",
        "seam.h",
        "seam.json",
        "seam.abi",
        "seam.ll",
        "META-INF/native-image/dev.elide.myna/%s.%s/native-image.properties" % (java_package, java_class),
    ] + ([java_class + "Truffle.java"] if truffle else [])
    if abi:
        outs.append(java_class + "Abi.java")
        for cls in ABI_CLASSES:
            outs += ["abi/%s/seam.abi" % cls, "abi/%s/seam.json" % cls]
    generator = "$(location %s)" % _GENERATOR
    source = "$(location %s)" % seam
    out_dir = "$(RULEDIR)/" + out

    # Each ABI class must share one fingerprint across its release targets (Myna's own check); the classes differ.
    cmd = "%s validate %s --targets %s && %s validate %s --targets %s && " % (
        generator,
        source,
        ",".join(LP64_LITTLE_TARGETS),
        generator,
        source,
        ",".join(LP64_LITTLE_AAPCS64_TARGETS),
    )
    cmd += "%s generate %s --out %s --target " % (generator, source, out_dir)
    cmd = cmd + _target_select()
    if abi:
        cmd += " && tmp=$$(mktemp -d) && trap 'rm -rf \"$$tmp\"' EXIT"
        for cls, target in ABI_CLASSES.items():
            cmd += " && %s generate %s --out $$tmp/%s --target %s" % (generator, source, cls, target)
        cmd += " && python3 $(location %s) --out %s %s" % (_FOLD, out_dir, " ".join(["$$tmp/" + cls for cls in ABI_CLASSES]))
    native.genrule(
        name = name,
        srcs = [seam],
        outs = ["%s/%s" % (out, file) for file in outs],
        cmd = cmd,
        tools = [_GENERATOR] + ([_FOLD] if abi else []),
        visibility = visibility,
    )

def myna_other_class_stub(name, abi, symbol, **kwargs):
    """A test-only shared library `<name>.so` whose `symbol` reports the fingerprint of the ABI class this host is *not*.

    `abi` is the bindings genrule's output directory label prefix (for example `:generated/abi`); the stub takes
    each class's `seam.abi` from it and selects the other class with `__CHAR_UNSIGNED__`, so on every host a
    consumer that checks the fingerprint of its own class must reject it with the same clarity as any wrong ABI.
    """
    source = name + ".c"
    native.genrule(
        name = name + "_source",
        testonly = True,
        srcs = [
            abi + "/lp64_little/seam.abi",
            abi + "/lp64_little_aapcs64/seam.abi",
        ],
        outs = [source],
        cmd = ("printf '%%s\\n' " +
               "'/* Generated: the other ABI class'\"'\"'s fingerprint, which the host'\"'\"'s bindings must reject. */' " +
               "'#ifdef __CHAR_UNSIGNED__' " +
               "\"const char *%s(void) { return \\\"$$(cat $(location %s/lp64_little/seam.abi))\\\"; }\" " +
               "'#else' " +
               "\"const char *%s(void) { return \\\"$$(cat $(location %s/lp64_little_aapcs64/seam.abi))\\\"; }\" " +
               "'#endif' > $@") % (symbol, abi, symbol, abi),
    )
    cc_binary(
        name = name + ".so",
        testonly = True,
        srcs = [source],
        linkshared = True,
        **kwargs
    )
    native.filegroup(
        name = name + "_fingerprints",
        testonly = True,
        srcs = [
            abi + "/lp64_little/seam.abi",
            abi + "/lp64_little_aapcs64/seam.abi",
        ],
        **kwargs
    )
