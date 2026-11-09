#!/usr/bin/env python3
"""Check emitted ABI policy and the resources actually packaged with Java bindings."""
import hashlib
import json
import re
import sys
import zipfile

# Myna 0.14.0 target ABI classes (bazel/myna.bzl): the signed-char LP64 little-endian class shared by
# x86-64 and Apple arm64, and AAPCS64 Linux (aarch64-unknown-linux-gnu), whose char is unsigned.
CLASSES = {
    "LP64_LITTLE": {"dataModel": "LP64", "byteOrder": "little"},
    "LP64_LITTLE_AAPCS64": {"dataModel": "LP64", "byteOrder": "little", "charUnsigned": True},
}
HEX64 = re.compile(rb"(?<![0-9a-f])[0-9a-f]{64}(?![0-9a-f])")


def load(path):
    with open(path, encoding="utf-8") as source:
        return json.load(source)


def verify(descriptor, archive):
    module = load(descriptor)
    assert module["schemaVersion"] == 3
    assert module["targetAbi"] in CLASSES.values(), module["targetAbi"]
    assert "targetTriple" not in module
    assert module["bindingAbiVersion"] == 2
    expected = {
        "truffledata_json": {"fingerprint", "decode", "decode_adopted", "release", "plan_create", "plan_release", "typed_decode", "typed_release",
                             "session_create", "session_decode", "session_decode_small", "session_retain", "session_reset", "session_release",
                             "stream_create", "stream_feed", "stream_finish", "stream_next", "stream_skip",
                             "stream_truncated_bytes", "stream_release", "result_myna"},
        # ABI 12 (ABI 11 retired the view imports; 12 adds logical totals to SlabResult): layout-compiled slabs and typed text indexes.
        "truffledata_buffa": {"fingerprint", "decode", "release", "typed", "typedReuse", "utf8_to_utf16",
                              "compileLayout", "releaseLayout", "decodeSlab", "decodeSlabSmall"},
        "truffledata_yaml": {"fingerprint", "release", "session_create", "session_decode", "session_retain",
                             "session_write_text", "plan_create", "plan_release", "typed_decode", "typed_release",
                             "session_reset", "session_release", "stream_create", "stream_feed", "stream_finish",
                             "stream_next", "stream_release"},
        # csv/native/csv.seam: the structural index kernel (ABI 1).
        "truffledata_csv": {"fingerprint", "kernel", "index", "index_leaf", "validate_utf8"},
    }[module["name"]]
    functions = module["functions"]
    assert {f["logicalName"] for f in functions} == expected
    # Leaf calls need an explicit bound (json/native/api.h): the ABI query, the O(1) session
    # reset and the TD_JSON_CRITICAL_LIMIT-bounded small decode.
    leaves = {
        "truffledata_json": {"fingerprint", "session_reset", "session_decode_small"},
        # yaml/native/api.h: the ABI query and the O(1) session reset.
        "truffledata_yaml": {"fingerprint", "session_reset"},
        # protobuf-buffa/native/lib.rs: the ABI query and the 64 KiB-bounded, allocation-free
        # slab decode (input and capacity checked before any work).
        "truffledata_buffa": {"fingerprint", "decodeSlabSmall"},
        # csv/native/api.h: the ABI query, the kernel selection and the 64 KiB-bounded index leaf.
        "truffledata_csv": {"fingerprint", "kernel", "index_leaf"},
    }[module["name"]]
    for function in functions:
        assert function["direction"] == "IMPORT"
        policy = "INLINE_LEAF" if function["logicalName"] in leaves else "TRANSITION"
        assert function["nativeCallPolicy"] == policy, function
    name = module["java"]["package"] + "." + module["java"]["class"]
    if archive == "-":
        # Native descriptor-only validation; Java packaging is checked with its archive below.
        return name
    with zipfile.ZipFile(archive) as jar:
        resource = "META-INF/native-image/dev.elide.myna/" + name + "/native-image.properties"
        metadata = jar.read(resource).decode()
        assert "--add-exports=org.graalvm.nativeimage.guest.staging/" in metadata
        # Leaf bindings are initialized at run time; Myna lists them in name order, so the
        # fingerprint binding need not come first (Buffa's decodeSlabSmall sorts before it).
        runtime = [arg.split("=", 1)[1].split(",") for arg in metadata.split()
                   if arg.startswith("--initialize-at-run-time=")]
        assert len(runtime) == 1, metadata
        assert name + "$fingerprint_Binding" in runtime[0], metadata
        assert set(runtime[0]) <= {name + "$" + leaf + "_Binding" for leaf in leaves}, metadata
        assert name + "$MynaSymbols" in metadata
        assert not any(path.startswith("com/oracle/svm/") for path in jar.namelist())
    return name


def verify_classes(descriptor, archive, per_class):
    """The production bindings carry every ABI class's fingerprint (bazel/myna.bzl, bazel/myna_abi.py):
    `per_class` lists each class's seam.json and seam.abi; the descriptors must differ only in
    `targetAbi`, each seam.abi must be its seam.json's SHA-256, the configured target's descriptor must
    be one of them, and the jar's `<Class>Abi` constants must be exactly those fingerprints."""
    module = load(descriptor)
    fingerprints = {}
    canonical = None
    for cls, (class_descriptor, class_abi) in per_class.items():
        with open(class_descriptor, encoding="utf-8") as source:
            text = source.read()
        with open(class_abi, encoding="utf-8") as source:
            fingerprint = source.read().strip()
        assert hashlib.sha256(text.encode("utf-8")).hexdigest() == fingerprint, class_abi
        parsed = json.loads(text)
        assert parsed["targetAbi"] == CLASSES[cls], (cls, parsed["targetAbi"])
        rest = {key: value for key, value in parsed.items() if key != "targetAbi"}
        assert canonical is None or rest == canonical, "ABI classes differ beyond targetAbi: " + class_descriptor
        canonical = rest
        fingerprints[cls] = fingerprint
    assert len(set(fingerprints.values())) == len(fingerprints), fingerprints
    assert {key: value for key, value in module.items() if key != "targetAbi"} == canonical, descriptor
    assert module["targetAbi"] in CLASSES.values()
    name = module["java"]["package"] + "." + module["java"]["class"] + "Abi"
    if archive != "-":
        with zipfile.ZipFile(archive) as jar:
            constants = set(HEX64.findall(jar.read(name.replace(".", "/") + ".class")))
        assert constants == {f.encode() for f in fingerprints.values()}, (name, constants)
    return name + " " + ", ".join("%s=%s" % (cls, f[:12] + "...") for cls, f in fingerprints.items())


def verify_derived(production, derived):
    """A cells seam derived from a production seam (protobuf-buffa/cells/BUILD.bazel) may differ
    only in its module options: the Truffle cells block and the Java package/class. Every import,
    struct, policy and the ABI itself must be the production one, so cells call the library the
    production fingerprint check verified."""
    base = load(production)
    cells = load(derived)
    assert "truffle" not in base, "production seams do not enable Truffle cells"
    truffle = cells.get("truffle")
    assert truffle and truffle["cells"] is True and truffle["backend"] in {"NATIVE", "FFM"}, truffle
    assert truffle["coercion"] == "STRICT", truffle
    assert cells["java"]["package"] == base["java"]["package"].rsplit(".", 1)[0] + ".cells.generated", cells["java"]
    assert cells["java"]["class"] != base["java"]["class"], cells["java"]
    rest = {key: value for key, value in cells.items() if key not in ("truffle", "java")}
    assert rest == {key: value for key, value in base.items() if key != "java"}, derived
    return cells["java"]["package"] + "." + cells["java"]["class"] + " (" + truffle["backend"] + ")"


if __name__ == "__main__":
    # Entries of (descriptor, bindings jar or "-" for a descriptor-only check), each optionally
    # followed by --classes LP64_LITTLE.json LP64_LITTLE.abi LP64_LITTLE_AAPCS64.json LP64_LITTLE_AAPCS64.abi
    # for production seams, then optionally --derived PRODUCTION DERIVED... for seams derived from one.
    args = sys.argv[1:]
    derived = []
    if "--derived" in args:
        index = args.index("--derived")
        args, derived = args[:index], args[index + 1:]
        assert len(derived) >= 2, derived
    assert len(args) >= 2
    while args:
        descriptor, archive, args = args[0], args[1], args[2:]
        print("Verified binding ABI 2 policy and packaged metadata:", verify(descriptor, archive))
        if args and args[0] == "--classes":
            files, args = args[1:1 + 2 * len(CLASSES)], args[1 + 2 * len(CLASSES):]
            assert len(files) == 2 * len(CLASSES), files
            per_class = {cls: (files[2 * i], files[2 * i + 1]) for i, cls in enumerate(CLASSES)}
            print("Verified ABI class fingerprints:", verify_classes(descriptor, archive, per_class))
    for descriptor in derived[1:]:
        print("Verified derived cells descriptor:", verify_derived(derived[0], descriptor))
