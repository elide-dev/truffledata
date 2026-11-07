#!/usr/bin/env python3
"""Check emitted ABI policy and the resources actually packaged with Java bindings."""
import json
import sys
import zipfile


def verify(descriptor, archive):
    with open(descriptor, encoding="utf-8") as source:
        module = json.load(source)
    assert module["schemaVersion"] == 2
    assert module["bindingAbiVersion"] == 2
    expected = {
        "truffledata_json": {"fingerprint", "decode", "decode_adopted", "release", "plan_create", "plan_release", "typed_decode", "typed_release",
                             "session_create", "session_decode", "session_decode_small", "session_retain", "session_reset", "session_release"},
        "truffledata_buffa": {"fingerprint", "decode", "release", "typed", "typedReuse", "scalars", "utf8_to_utf16"},
        "truffledata_yaml": {"fingerprint", "release", "session_create", "session_decode", "session_retain",
                             "session_reset", "session_release", "stream_create", "stream_feed", "stream_finish",
                             "stream_next", "stream_release"},
    }[module["name"]]
    functions = module["functions"]
    assert {f["logicalName"] for f in functions} == expected
    for function in functions:
        assert function["direction"] == "IMPORT"
        # Leaf calls need an explicit bound (json/native/api.h): the ABI query, the O(1) session
        # reset and the TD_JSON_CRITICAL_LIMIT-bounded small decode.
        leaves = {
            "truffledata_json": {"fingerprint", "session_reset", "session_decode_small"},
            # yaml/native/api.h: the ABI query and the O(1) session reset.
            "truffledata_yaml": {"fingerprint", "session_reset"},
        }.get(module["name"], {"fingerprint"})
        policy = "INLINE_LEAF" if function["logicalName"] in leaves else "TRANSITION"
        assert function["nativeCallPolicy"] == policy, function
    name = module["java"]["package"] + "." + module["java"]["class"]
    if archive == "-":
        # Native-only module (YAML until its Java bindings library lands, plan Task 5).
        return name
    with zipfile.ZipFile(archive) as jar:
        resource = "META-INF/native-image/dev.elide.myna/" + name + "/native-image.properties"
        metadata = jar.read(resource).decode()
        assert "--add-exports=org.graalvm.nativeimage.guest.staging/" in metadata
        assert "--initialize-at-run-time=" + name + "$fingerprint_Binding" in metadata
        assert name + "$MynaSymbols" in metadata
        assert not any(path.startswith("com/oracle/svm/") for path in jar.namelist())
    return name


if __name__ == "__main__":
    # Pairs of (descriptor, bindings jar or "-" for a descriptor-only check).
    assert len(sys.argv) >= 3 and len(sys.argv) % 2 == 1
    for start in range(1, len(sys.argv), 2):
        print("Verified binding ABI 2 policy and packaged metadata:", verify(*sys.argv[start:start + 2]))
