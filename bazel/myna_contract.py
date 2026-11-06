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
        "truffledata_json": {"fingerprint", "decode", "decode_adopted", "release", "plan_create", "plan_release", "typed_decode", "typed_release"},
        "truffledata_buffa": {"fingerprint", "decode", "release", "typed", "typedReuse", "scalars", "utf8_to_utf16"},
    }[module["name"]]
    functions = module["functions"]
    assert {f["logicalName"] for f in functions} == expected
    for function in functions:
        assert function["direction"] == "IMPORT"
        policy = "INLINE_LEAF" if function["logicalName"] == "fingerprint" else "TRANSITION"
        assert function["nativeCallPolicy"] == policy, function
    name = module["java"]["package"] + "." + module["java"]["class"]
    with zipfile.ZipFile(archive) as jar:
        resource = "META-INF/native-image/dev.elide.myna/" + name + "/native-image.properties"
        metadata = jar.read(resource).decode()
        assert "--add-exports=org.graalvm.nativeimage.guest.staging/" in metadata
        assert "--initialize-at-run-time=" + name + "$fingerprint_Binding" in metadata
        assert name + "$MynaSymbols" in metadata
        assert not any(path.startswith("com/oracle/svm/") for path in jar.namelist())
    return name


if __name__ == "__main__":
    assert len(sys.argv) == 5
    for start in (1, 3):
        print("Verified binding ABI 2 policy and packaged metadata:", verify(*sys.argv[start:start + 2]))
