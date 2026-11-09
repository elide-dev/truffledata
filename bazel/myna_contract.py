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


if __name__ == "__main__":
    # Pairs of (descriptor, bindings jar or "-" for a descriptor-only check).
    assert len(sys.argv) >= 3 and len(sys.argv) % 2 == 1
    for start in range(1, len(sys.argv), 2):
        print("Verified binding ABI 2 policy and packaged metadata:", verify(*sys.argv[start:start + 2]))
