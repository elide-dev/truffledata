#!/usr/bin/env python3
"""Check emitted ABI policy and the resources actually packaged with Java bindings."""
import json
import sys
import zipfile


def verify(descriptor, archive):
    with open(descriptor, encoding="utf-8") as source:
        module = json.load(source)
    assert module["schemaVersion"] == 3
    assert module["targetAbi"] == {"dataModel": "LP64", "byteOrder": "little"}
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


def verify_derived(production, derived):
    """A cells seam derived from a production seam (protobuf-buffa/cells/BUILD.bazel) may differ
    only in its module options: the Truffle cells block and the Java package/class. Every import,
    struct, policy and the ABI itself must be the production one, so cells call the library the
    production fingerprint check verified."""
    with open(production, encoding="utf-8") as source:
        base = json.load(source)
    with open(derived, encoding="utf-8") as source:
        cells = json.load(source)
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
    # Pairs of (descriptor, bindings jar or "-" for a descriptor-only check), then optionally
    # --derived PRODUCTION DERIVED... for seams derived from a production seam.
    args = sys.argv[1:]
    derived = []
    if "--derived" in args:
        index = args.index("--derived")
        args, derived = args[:index], args[index + 1:]
        assert len(derived) >= 2, derived
    assert len(args) >= 2 and len(args) % 2 == 0
    for start in range(0, len(args), 2):
        print("Verified binding ABI 2 policy and packaged metadata:", verify(*args[start:start + 2]))
    for descriptor in derived[1:]:
        print("Verified derived cells descriptor:", verify_derived(derived[0], descriptor))
