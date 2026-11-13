#!/usr/bin/env python3
"""Writes the shared Bebop fixture files (format bebop-fixtures/1).

An independent encoder: every expected byte string here is computed from the wire rules directly,
never by the Java or Rust implementations the fixtures test.
"""
import json, struct, sys, uuid

WIRE = "bebop-2026.0.0-alpha.6@f73e40a1b118aff6d29d91a00ed3fdb7b4fe5e8c"
RULES = sys.argv[2] if len(sys.argv) > 2 else "strict-rules-0.11"
STRICT = {"name": "strict", "rules": RULES, "admit": "all", "nan16": "7e00", "nanBrain16": "7fc0",
          "nan32": "7fc00000", "nan64": "7ff8000000000000"}
GENERAL = {"name": "general"}

DEFS = [
    {"enum": "Status", "base": "byte", "members": [["ACTIVE", "1"], ["GONE", "3"]]},
    {"struct": "Empty", "fields": []},
    {"struct": "Point", "fields": [["x", "float32"], ["y", "float32"]]},
    {"struct": "Circle", "fields": [["radius", "float32"]]},
    {"struct": "Rectangle", "fields": [["width", "float32"], ["height", "float32"]]},
    {"struct": "Stamped", "fields": [["at", "timestamp"], ["sentinel", "uint32"]]},
    {"struct": "Spanned", "fields": [["span", "duration"], ["sentinel", "uint32"]]},
    {"message": "UserRequest", "fields": [[1, "user_id", "int32"], [2, "include_profile", "bool"]]},
    {"message": "Times", "fields": [[1, "at", "timestamp"], [2, "span", "duration"], [3, "tail", "byte"]]},
    {"message": "Instant", "fields": [[1, "at", "utc-instant"], [2, "any", "timestamp"]]},
    {"message": "Wide", "fields": [[1, "a", "byte"], [2, "b", "byte"], [3, "c", "byte"], [9, "d", "byte"],
                                    [17, "e", "byte"], [33, "f", "byte"], [255, "g", "byte"]]},
    {"union": "Shape", "branches": [[1, "Circle", {"ref": "Circle"}], [2, "Rectangle", {"ref": "Rectangle"}]]},
]

def u8(v): return struct.pack("<B", v & 0xFF)
def u16(v): return struct.pack("<H", v & 0xFFFF)
def u32(v): return struct.pack("<I", v & 0xFFFFFFFF)
def i32(v): return struct.pack("<i", v)
def i64(v): return struct.pack("<q", v)
def u64(v): return struct.pack("<Q", v & 0xFFFFFFFFFFFFFFFF)
def string(s):
    b = s.encode("utf-8"); return u32(len(b)) + b + b"\0"
def timestamp(s, n, o): return i64(s) + i32(n) + i32(o)
def duration(s, n): return i64(s) + i32(n)
def i128(v): return u64(v) + u64(v >> 64)
def array(items): return u32(len(items)) + b"".join(items)

def message(fields):
    """fields: list of (tag, bytes) in ascending tag order."""
    payload = b"".join(b for _, b in fields)
    n = len(fields)
    code = 0 if len(payload) <= 0xFF else 1 if len(payload) <= 0xFFFF else 2
    width = [u8, u16, u32][code]
    starts, at = [], 0
    for _, b in fields:
        starts.append(at); at += len(b)
    boundaries = b"".join(width(s) for s in starts[1:])
    tags = [t for t, _ in fields]
    top = tags[-1] if tags else 0
    if n == 0:
        kind, directory = 0, b""
    else:
        kind, size = (4, 1) if top <= 8 else (5, 2) if top <= 16 else (6, 4) if top <= 32 else (7, 6)
        if n <= 3 and n < size:
            kind, directory = n, bytes(tags)
        elif kind == 7:
            directory, blocks, rank = b"", 0, 0
            for block in range(8):
                inside = [t for t in tags if (t - 1) // 32 == block]
                if not inside: continue
                mask = 0
                for t in inside: mask |= 1 << ((t - 1) % 32)
                directory += u8(rank) + u32(mask); blocks |= 1 << block; rank += len(inside)
            directory += u8(blocks)
        else:
            mask = 0
            for t in tags: mask |= 1 << (t - 1)
            directory = [u8, u16, u32][kind - 4](mask)
    content = payload + boundaries + directory + u8(kind << 2 | code)
    return u32(len(content)) + content

def union(disc, content): return u32(1 + len(content)) + u8(disc) + content

def hx(b): return b.hex()
def I(v): return {"int": str(v)}
def F(bits, digits): return {"bits": format(bits, "0%dx" % digits)}
def TS(s, n, o): return {"timestamp": [str(s), str(n), str(o)]}
def DU(s, n): return {"duration": [str(s), str(n)]}
def REC(*v): return {"record": list(v)}

def accept(name, root, data, value, canonical=True):
    c = {"name": name, "kind": "wire-accept", "root": root, "bytes": hx(data), "value": value}
    if not canonical: c["canonical"] = False
    return c
def reject(name, root, data, error="malformed"):
    return {"name": name, "kind": "wire-reject", "root": root, "bytes": hx(data), "error": error}
def normalize(name, root, host, data, value):
    return {"name": name, "kind": "host-normalize", "root": root, "host": host, "bytes": hx(data), "value": value}
def host_reject(name, root, host):
    return {"name": name, "kind": "host-reject", "root": root, "host": host, "error": "value"}
def resource(name, root, data, operation, limits, error):
    return {"name": name, "kind": "resource", "root": root, "bytes": hx(data), "operation": operation,
            "limits": limits, "error": error}

MIN64, MAX64 = -(1 << 63), (1 << 63) - 1
DAY = 86_400_000

# ---------------------------------------------------------------------------------------------
general = [
    accept("spec string hello", "string", string("hello"), {"string": "hello"}),
    accept("spec enum", {"ref": "Status"}, u8(1), I(1)),
    accept("spec int32 array", {"array": "int32"}, array([i32(1), i32(2), i32(3)]), {"seq": [I(1), I(2), I(3)]}),
    accept("spec fixed bytes", {"fixed": ["byte", 4]}, bytes.fromhex("deadbeef"), {"bytes": "deadbeef"}),
    accept("spec map", {"map": ["byte", "int32"]}, u32(2) + u8(1) + i32(100) + u8(2) + i32(200),
           {"map": [[I(1), I(100)], [I(2), I(200)]]}),
    accept("spec struct", {"ref": "Point"}, bytes.fromhex("0000803f00000040"), REC(F(0x3F800000, 8), F(0x40000000, 8))),
    accept("spec message both fields", {"ref": "UserRequest"}, bytes.fromhex("080000002a00000001040310"),
           REC(I(42), {"bool": True})),
    accept("spec message one field", {"ref": "UserRequest"}, bytes.fromhex("060000002a0000000110"), REC(I(42), None)),
    accept("spec message empty", {"ref": "UserRequest"}, bytes.fromhex("0100000000"), REC(None, None)),
    accept("spec union circle", {"ref": "Shape"}, bytes.fromhex("05000000010000a040"),
           {"union": [1, REC(F(0x40A00000, 8))]}),
    accept("spec array of arrays", {"array": {"array": "int32"}},
           array([array([i32(1), i32(2)]), array([i32(3)])]), {"seq": [{"seq": [I(1), I(2)]}, {"seq": [I(3)]}]}),
    accept("spec array of strings", {"array": "string"}, array([string("ab"), string("cde")]),
           {"seq": [{"string": "ab"}, {"string": "cde"}]}),
    accept("nonzero boolean is true", "bool", u8(2), {"bool": True}, canonical=False),
    accept("unknown tag is skipped", {"ref": "UserRequest"}, bytes.fromhex("090000002a000000ffff040510"),
           REC(I(42), None), canonical=False),
    accept("unknown branch keeps its content", {"ref": "Shape"}, bytes.fromhex("0300000009aabb"),
           {"union": [9, {"raw": "aabb"}]}),
    accept("NaN payload is preserved", "float32", bytes.fromhex("0100f87f"), F(0x7FF80001, 8)),
    accept("timestamp with negative nanoseconds", "timestamp", timestamp(0, -1, 0), TS(0, -1, 0)),
    accept("embedded NUL", "string", string("a\0b"), {"string": "a\0b"}),
    reject("tiny directory where mask 8 ties", {"ref": "UserRequest"}, bytes.fromhex("060000002a0000000104")),
    reject("mask 16 where mask 8 fits", {"ref": "UserRequest"}, bytes.fromhex("070000002a000000010014")),
    reject("boundary width not minimal", {"ref": "UserRequest"}, bytes.fromhex("060000002a0000000111")),
    reject("reserved control bit", {"ref": "UserRequest"}, bytes.fromhex("060000002a0000000130")),
    reject("payload with empty directory", {"ref": "UserRequest"}, bytes.fromhex("050000002a00000000")),
    reject("trailing byte", {"ref": "UserRequest"}, bytes.fromhex("060000002a000000011000")),
    reject("field shorter than its range", {"ref": "UserRequest"}, bytes.fromhex("080000002a00000001030310")),
    reject("string terminator not NUL", "string", bytes.fromhex("02000000616201")),
    reject("string overlong UTF-8", "string", bytes.fromhex("02000000c08000")),
    reject("string surrogate", "string", bytes.fromhex("03000000eda08000")),
    reject("duplicate map key", {"map": ["int32", "int32"]}, u32(2) + i32(1) + i32(5) + i32(1) + i32(6)),
    reject("count exceeds remaining bytes", {"array": "int32"}, u32(0xFFFFFFFF) + i32(1)),
    reject("timestamp truncated to 12 bytes", "timestamp", timestamp(1, 2, 3)[:12]),
    resource("zero-byte elements, huge count", {"array": {"ref": "Empty"}}, u32(0xFFFFFFFF), "decode",
             {"maxBytes": 1024, "maxCount": 1024, "maxDepth": 8, "maxWork": "4096", "maxAllocation": "65536"}, "limit"),
    resource("work budget", {"array": "int16"}, array([u16(i) for i in range(6)]), "validate",
             {"maxBytes": 1024, "maxCount": 1024, "maxDepth": 8, "maxWork": "5", "maxAllocation": "65536"}, "limit"),
    resource("allocation budget refuses decode", {"array": "string"}, array([string("0123456789" * 4)]), "decode",
             {"maxBytes": 1024, "maxCount": 1024, "maxDepth": 8, "maxWork": "4096", "maxAllocation": "64"}, "limit"),
    resource("allocation-free validation accepts the same bytes", {"array": "string"}, array([string("0123456789" * 4)]),
             "validate", {"maxBytes": 1024, "maxCount": 1024, "maxDepth": 8, "maxWork": "4096", "maxAllocation": "64"},
             "none"),
    resource("input exceeds byte budget", {"array": "byte"}, array([u8(0)] * 20), "validate",
             {"maxBytes": 16, "maxCount": 1024, "maxDepth": 8, "maxWork": "4096", "maxAllocation": "65536"}, "limit"),
]

# ---------------------------------------------------------------------------------------------
strict = [
    accept("false", "bool", u8(0), {"bool": False}),
    accept("true", "bool", u8(1), {"bool": True}),
    reject("boolean 02", "bool", u8(2)),
    reject("boolean ff", "bool", u8(0xFF)),
    # NaNs: exactly one pattern per type.
    accept("float16 canonical NaN", "float16", u16(0x7E00), F(0x7E00, 4)),
    reject("float16 NaN payload", "float16", u16(0x7E01)),
    reject("float16 NaN sign", "float16", u16(0xFE00)),
    reject("float16 signaling NaN", "float16", u16(0x7C01)),
    accept("bfloat16 canonical NaN", "bfloat16", u16(0x7FC0), F(0x7FC0, 4)),
    reject("bfloat16 NaN payload", "bfloat16", u16(0x7FC1)),
    reject("bfloat16 NaN sign", "bfloat16", u16(0xFFC0)),
    reject("bfloat16 signaling NaN", "bfloat16", u16(0x7F81)),
    accept("float32 canonical NaN", "float32", u32(0x7FC00000), F(0x7FC00000, 8)),
    reject("float32 NaN payload", "float32", u32(0x7FC00001)),
    reject("float32 NaN sign", "float32", u32(0xFFC00000)),
    reject("float32 signaling NaN", "float32", u32(0x7F800001)),
    accept("float64 canonical NaN", "float64", u64(0x7FF8000000000000), F(0x7FF8000000000000, 16)),
    reject("float64 NaN payload", "float64", u64(0x7FF8000000000001)),
    reject("float64 NaN sign", "float64", u64(0xFFF8000000000000)),
    # Signed zeros, infinities and subnormal boundaries are ordinary values.
    accept("float16 +0", "float16", u16(0x0000), F(0x0000, 4)),
    accept("float16 -0", "float16", u16(0x8000), F(0x8000, 4)),
    accept("float16 smallest subnormal", "float16", u16(0x0001), F(0x0001, 4)),
    accept("float16 largest subnormal", "float16", u16(0x03FF), F(0x03FF, 4)),
    accept("float16 smallest normal", "float16", u16(0x0400), F(0x0400, 4)),
    accept("float16 largest finite", "float16", u16(0x7BFF), F(0x7BFF, 4)),
    accept("float16 +inf", "float16", u16(0x7C00), F(0x7C00, 4)),
    accept("float16 -inf", "float16", u16(0xFC00), F(0xFC00, 4)),
    accept("bfloat16 -0", "bfloat16", u16(0x8000), F(0x8000, 4)),
    accept("bfloat16 smallest subnormal", "bfloat16", u16(0x0001), F(0x0001, 4)),
    accept("bfloat16 largest subnormal", "bfloat16", u16(0x007F), F(0x007F, 4)),
    accept("bfloat16 smallest normal", "bfloat16", u16(0x0080), F(0x0080, 4)),
    accept("bfloat16 +inf", "bfloat16", u16(0x7F80), F(0x7F80, 4)),
    accept("float32 -0", "float32", u32(0x80000000), F(0x80000000, 8)),
    accept("float32 smallest subnormal", "float32", u32(1), F(1, 8)),
    accept("float64 +0", "float64", u64(0), F(0, 16)),
    accept("float64 -0", "float64", u64(0x8000000000000000), F(0x8000000000000000, 16)),
    accept("float64 smallest subnormal", "float64", u64(1), F(1, 16)),
    # Integers: every pattern is a value.
    accept("int128 minimum", "int128", i128(-(1 << 127)), I(-(1 << 127))),
    accept("int128 minus two", "int128", i128(-2), I(-2)),
    accept("uint128 maximum", "uint128", i128((1 << 128) - 1), I((1 << 128) - 1)),
    accept("uint64 maximum", "uint64", u64((1 << 64) - 1), I((1 << 64) - 1)),
    accept("int64 minimum", "int64", i64(MIN64), I(MIN64)),
    # UUIDs: text byte order, every pattern.
    accept("uuid nil", "uuid", bytes(16), {"uuid": "00000000-0000-0000-0000-000000000000"}),
    accept("uuid max", "uuid", b"\xff" * 16, {"uuid": "ffffffff-ffff-ffff-ffff-ffffffffffff"}),
    accept("uuid byte order", "uuid", uuid.UUID("00112233-4455-6677-8899-aabbccddeeff").bytes,
           {"uuid": "00112233-4455-6677-8899-aabbccddeeff"}),
    accept("uuid unfamiliar variant and version", "uuid", uuid.UUID("ffeeddcc-bbaa-f988-f766-554433221100").bytes,
           {"uuid": "ffeeddcc-bbaa-f988-f766-554433221100"}),
    # Timestamps.
    accept("timestamp seconds minimum", "timestamp", timestamp(MIN64, 0, 0), TS(MIN64, 0, 0)),
    accept("timestamp seconds maximum", "timestamp", timestamp(MAX64, 999_999_999, 0), TS(MAX64, 999_999_999, 0)),
    accept("timestamp offset +1 day", "timestamp", timestamp(-1, 999_999_999, DAY), TS(-1, 999_999_999, DAY)),
    accept("timestamp offset -1 day", "timestamp", timestamp(0, 0, -DAY), TS(0, 0, -DAY)),
    accept("timestamp millisecond offset", "timestamp", timestamp(5, 1, 1), TS(5, 1, 1)),
    accept("timestamp odd negative offset", "timestamp", timestamp(5, 1, -12_345_677), TS(5, 1, -12_345_677)),
    accept("same instant, offset zero", "timestamp", timestamp(5, 0, 0), TS(5, 0, 0)),
    accept("same instant, offset +1h is another value", "timestamp", timestamp(5, 0, 3_600_000), TS(5, 0, 3_600_000)),
    reject("timestamp nanoseconds 10^9", "timestamp", timestamp(0, 1_000_000_000, 0)),
    reject("timestamp nanoseconds -1", "timestamp", timestamp(0, -1, 0)),
    reject("timestamp offset past +1 day", "timestamp", timestamp(0, 0, DAY + 1)),
    reject("timestamp offset past -1 day", "timestamp", timestamp(0, 0, -DAY - 1)),
    reject("timestamp truncated to 12 bytes", "timestamp", timestamp(1, 2, 3)[:12]),
    reject("timestamp truncated to 13 bytes", "timestamp", timestamp(1, 2, 3)[:13]),
    reject("timestamp truncated to 14 bytes", "timestamp", timestamp(1, 2, 3)[:14]),
    reject("timestamp truncated to 15 bytes", "timestamp", timestamp(1, 2, 3)[:15]),
    reject("timestamp with a 17th byte", "timestamp", timestamp(1, 2, 3) + b"\0"),
    # UTC instants: the same 16 bytes, offset zero required by the schema.
    accept("utc instant", "utc-instant", timestamp(5, 7, 0), TS(5, 7, 0)),
    reject("utc instant with an offset", "utc-instant", timestamp(5, 7, 3_600_000)),
    reject("utc instant with offset -1 ms", "utc-instant", timestamp(5, 7, -1)),
    accept("utc instant field beside a general timestamp", {"ref": "Instant"},
           message([(1, timestamp(5, 0, 0)), (2, timestamp(5, 0, 3_600_000))]), REC(TS(5, 0, 0), TS(5, 0, 3_600_000))),
    reject("utc instant field with an offset", {"ref": "Instant"},
           message([(1, timestamp(5, 0, 60_000)), (2, timestamp(5, 0, 0))])),
    # Durations.
    accept("duration zero", "duration", duration(0, 0), DU(0, 0)),
    accept("duration maximum", "duration", duration(MAX64, 999_999_999), DU(MAX64, 999_999_999)),
    accept("duration minimum", "duration", duration(MIN64, -999_999_999), DU(MIN64, -999_999_999)),
    accept("duration zero seconds, positive nanos", "duration", duration(0, 5), DU(0, 5)),
    accept("duration zero seconds, negative nanos", "duration", duration(0, -5), DU(0, -5)),
    accept("duration negative", "duration", duration(-1, -999_999_999), DU(-1, -999_999_999)),
    reject("duration +1 s, -1 ns", "duration", duration(1, -1)),
    reject("duration -1 s, +1 ns", "duration", duration(-1, 1)),
    reject("duration nanos 10^9", "duration", duration(0, 1_000_000_000)),
    reject("duration nanos -10^9", "duration", duration(0, -1_000_000_000)),
    reject("duration truncated to 11 bytes", "duration", duration(1, 2)[:11]),
    # Temporal values inside containers: packed, unaligned, exact lengths.
    accept("timestamps in an array", {"array": "timestamp"}, array([timestamp(1, 2, 3), timestamp(-4, 5, -6)]),
           {"seq": [TS(1, 2, 3), TS(-4, 5, -6)]}),
    accept("timestamps in a fixed array", {"fixed": ["timestamp", 2]}, timestamp(1, 2, 3) + timestamp(7, 8, 9),
           {"seq": [TS(1, 2, 3), TS(7, 8, 9)]}),
    accept("timestamp then a sentinel in a struct", {"ref": "Stamped"}, timestamp(1, 2, 3) + u32(0xDEADBEEF),
           REC(TS(1, 2, 3), I(0xDEADBEEF))),
    accept("duration then a sentinel in a struct", {"ref": "Spanned"}, duration(-1, -2) + u32(0xDEADBEEF),
           REC(DU(-1, -2), I(0xDEADBEEF))),
    accept("structs with timestamps in an array", {"array": {"ref": "Stamped"}},
           array([timestamp(1, 2, 3) + u32(10), timestamp(4, 5, 6) + u32(11)]),
           {"seq": [REC(TS(1, 2, 3), I(10)), REC(TS(4, 5, 6), I(11))]}),
    accept("temporal message fields", {"ref": "Times"},
           message([(1, timestamp(1, 2, 3)), (2, duration(4, 5)), (3, u8(9))]), REC(TS(1, 2, 3), DU(4, 5), I(9))),
    reject("timestamp field with a 12-byte range", {"ref": "Times"},
           message([(1, timestamp(1, 2, 3)[:12]), (3, u8(9))])),
    reject("timestamp array short by one byte", {"array": "timestamp"}, array([timestamp(1, 2, 3)])[:-1]),
    # Presence.
    accept("absent", {"ref": "UserRequest"}, message([]), REC(None, None)),
    accept("present zero", {"ref": "UserRequest"}, message([(1, i32(0)), (2, u8(0))]), REC(I(0), {"bool": False})),
    accept("one present zero", {"ref": "UserRequest"}, message([(1, i32(0))]), REC(I(0), None)),
    reject("unknown tag", {"ref": "UserRequest"}, bytes.fromhex("090000002a000000ffff040510")),
    reject("zero-byte range for an int32", {"ref": "UserRequest"}, bytes.fromhex("070000002a000000040310")),
    reject("unknown enum value", {"ref": "Status"}, u8(2)),
    accept("declared enum value", {"ref": "Status"}, u8(3), I(3)),
    reject("unknown union branch", {"ref": "Shape"}, bytes.fromhex("0300000009aabb")),
    # Directories and boundary widths.
    accept("mask 8", {"ref": "Wide"}, message([(1, u8(1)), (2, u8(2)), (3, u8(3))]),
           REC(I(1), I(2), I(3), None, None, None, None)),
    accept("tiny 1 above tag 8", {"ref": "Wide"}, message([(9, u8(4))]), REC(None, None, None, I(4), None, None, None)),
    accept("mask 16", {"ref": "Wide"}, message([(1, u8(1)), (9, u8(4))]), REC(I(1), None, None, I(4), None, None, None)),
    accept("tiny 2 over mask 32", {"ref": "Wide"}, message([(1, u8(1)), (17, u8(5))]),
           REC(I(1), None, None, None, I(5), None, None)),
    accept("mask 32", {"ref": "Wide"}, message([(1, u8(1)), (2, u8(2)), (3, u8(3)), (17, u8(5))]),
           REC(I(1), I(2), I(3), None, I(5), None, None)),
    accept("blocks", {"ref": "Wide"}, message([(1, u8(1)), (2, u8(2)), (3, u8(3)), (33, u8(6)), (255, u8(7))]),
           REC(I(1), I(2), I(3), None, None, I(6), I(7))),
    # Maps and sets: order is over encoded bytes.
    accept("map sorted by little-endian key bytes", {"map": ["int32", "bool"]},
           u32(3) + i32(256) + u8(1) + i32(1) + u8(0) + i32(-1) + u8(1),
           {"map": [[I(256), {"bool": True}], [I(1), {"bool": False}], [I(-1), {"bool": True}]]}),
    reject("map unsorted", {"map": ["int32", "bool"]}, u32(2) + i32(1) + u8(0) + i32(256) + u8(1)),
    reject("map duplicate", {"map": ["int32", "bool"]}, u32(2) + i32(1) + u8(0) + i32(1) + u8(1)),
    accept("string keys: length prefix leads", {"map": ["string", "bool"]},
           u32(2) + string("b") + u8(1) + string("aa") + u8(1),
           {"map": [[{"string": "b"}, {"bool": True}], [{"string": "aa"}, {"bool": True}]]}),
    accept("set sorted", {"set": "uint16"}, array([u16(256), u16(1), u16(2)]), {"set": [I(256), I(1), I(2)]}),
    reject("set unsorted", {"set": "uint16"}, array([u16(1), u16(256)])),
    reject("set duplicate", {"set": "uint16"}, array([u16(1), u16(1)])),
    accept("sequence keeps order and duplicates", {"array": "uint16"}, array([u16(2), u16(1), u16(2)]),
           {"seq": [I(2), I(1), I(2)]}),
    accept("set of sequences", {"set": {"array": "byte"}}, array([array([u8(2)]), array([u8(1), u8(1)])]),
           {"set": [{"bytes": "02"}, {"bytes": "0101"}]}),
    accept("sequence of sets", {"array": {"set": "uint16"}}, array([array([u16(2)]), array([u16(1), u16(2)]), array([u16(2)])]),
           {"seq": [{"set": [I(2)]}, {"set": [I(1), I(2)]}, {"set": [I(2)]}]}),
    reject("sequence of sets with an unsorted inner set", {"array": {"set": "uint16"}}, array([array([u16(2), u16(1)])])),
    accept("the same bytes as a sequence of sequences", {"array": {"array": "uint16"}}, array([array([u16(2), u16(1)])]),
           {"seq": [{"seq": [I(2), I(1)]}]}),
    accept("set of zero-byte elements has one member", {"set": {"ref": "Empty"}}, u32(1), {"set": [REC()]}),
    reject("set of two zero-byte elements", {"set": {"ref": "Empty"}}, u32(2)),
    accept("uuid set in byte order", {"set": "uuid"},
           array([uuid.UUID(int=1).bytes, uuid.UUID("ff000000-0000-0000-0000-000000000000").bytes]),
           {"set": [{"uuid": "00000000-0000-0000-0000-000000000001"}, {"uuid": "ff000000-0000-0000-0000-000000000000"}]}),
    reject("trailing byte", "bool", u8(1) + u8(0)),
    reject("invalid UTF-8", "string", bytes.fromhex("02000000c32800")),
    accept("string with embedded NUL", "string", string("a\0b"), {"string": "a\0b"}),
]

# ---------------------------------------------------------------------------------------------
host = [
    normalize("float32 NaN payload normalizes", "float32", F(0xFFC12345, 8), u32(0x7FC00000), F(0x7FC00000, 8)),
    normalize("float64 NaN payload normalizes", "float64", F(0xFFF8000000000123, 16), u64(0x7FF8000000000000),
              F(0x7FF8000000000000, 16)),
    normalize("float16 NaN from a float32 host NaN", "float16", {"bits32": "ffc12345"}, u16(0x7E00), F(0x7E00, 4)),
    normalize("bfloat16 NaN from a float32 host NaN", "bfloat16", {"bits32": "7f812345"}, u16(0x7FC0), F(0x7FC0, 4)),
    normalize("set members are sorted and deduplicated", {"set": "uint16"}, {"set": [I(2), I(1), I(256), I(1), I(2)]},
              array([u16(256), u16(1), u16(2)]), {"set": [I(256), I(1), I(2)]}),
    normalize("map entries are sorted", {"map": ["string", "bool"]},
              {"map": [[{"string": "aa"}, {"bool": True}], [{"string": "b"}, {"bool": False}]]},
              u32(2) + string("b") + u8(0) + string("aa") + u8(1),
              {"map": [[{"string": "b"}, {"bool": False}], [{"string": "aa"}, {"bool": True}]]}),
    normalize("nested sets normalize, the sequence around them does not", {"array": {"set": "uint16"}},
              {"seq": [{"set": [I(2), I(1), I(2)]}, {"set": [I(1)]}]}, array([array([u16(1), u16(2)]), array([u16(1)])]),
              {"seq": [{"set": [I(1), I(2)]}, {"set": [I(1)]}]}),
    normalize("signed zeros stay distinct in a set", {"set": "float64"}, {"set": [F(0x8000000000000000, 16), F(0, 16)]},
              array([u64(0), u64(0x8000000000000000)]), {"set": [F(0, 16), F(0x8000000000000000, 16)]}),
    host_reject("float16 does not round: 0.1f", "float16", {"bits32": "3dcccccd"}),
    host_reject("float16 does not round: just above the largest finite", "float16", {"bits32": "477fe100"}),
    host_reject("float16 does not flush: below the smallest subnormal", "float16", {"bits32": "33000000"}),
    host_reject("bfloat16 does not round: 0.1f", "bfloat16", {"bits32": "3dcccccd"}),
    host_reject("timestamp nanoseconds 10^9", "timestamp", TS(0, 1_000_000_000, 0)),
    host_reject("timestamp offset past a day", "timestamp", TS(0, 0, DAY + 1)),
    host_reject("utc instant with an offset is not repaired", "utc-instant", TS(5, 0, 3_600_000)),
    host_reject("duration with inconsistent signs is not repaired", "duration", DU(1, -1)),
    host_reject("duration nanos 10^9", "duration", DU(0, 1_000_000_000)),
    host_reject("undeclared enum value", {"ref": "Status"}, I(2)),
    host_reject("unknown union branch", {"ref": "Shape"}, {"union": [9, {"raw": "aabb"}]}),
    host_reject("int32 out of range", "int32", I(1 << 31)),
    host_reject("uint128 negative", "uint128", I(-1)),
    # The optional duration adapter: exact arithmetic, truncation toward zero.
    {"name": "adapter: -1 s +0.5 s", "kind": "duration-adapter", "host": DU(-1, 500_000_000), "value": DU(0, -500_000_000)},
    {"name": "adapter: +1 s -1 ns", "kind": "duration-adapter", "host": DU(1, -1), "value": DU(0, 999_999_999)},
    {"name": "adapter: carries whole seconds", "kind": "duration-adapter", "host": DU(1, 2_000_000_001), "value": DU(3, 1)},
    {"name": "adapter: canonical input is unchanged", "kind": "duration-adapter", "host": DU(-3, -7), "value": DU(-3, -7)},
    {"name": "adapter: most negative nanos", "kind": "duration-adapter", "host": DU(0, -(1 << 31)), "value": DU(-2, -147_483_648)},
    {"name": "adapter: maximum stays in range", "kind": "duration-adapter", "host": DU(MAX64, 999_999_999), "value": DU(MAX64, 999_999_999)},
    {"name": "adapter: overflow above", "kind": "duration-adapter", "host": DU(MAX64, 1_000_000_000), "error": "value"},
    {"name": "adapter: overflow below", "kind": "duration-adapter", "host": DU(MIN64, -1_000_000_000), "error": "value"},
    {"name": "adapter: brought back into range", "kind": "duration-adapter", "host": DU(MIN64, 1), "value": DU(MIN64 + 1, -999_999_999)},
]

def write(name, profile, cases, note):
    doc = {"format": "bebop-fixtures/1", "note": note, "wire": WIRE, "profile": profile, "definitions": DEFS, "cases": cases}
    names = [c["name"] for c in cases]
    assert len(names) == len(set(names)), [n for n in names if names.count(n) > 1]
    with open(sys.argv[1] + "/" + name, "w") as f:
        json.dump(doc, f, indent=1, ensure_ascii=True); f.write("\n")
    print(name, len(cases))

write("general.json", GENERAL, general, "Wire acceptance under the general profile.")
write("strict.json", STRICT, strict, "Wire acceptance under the strict profile.")
write("host.json", STRICT, host, "Host-side normalization and refusal under the strict profile; not wire acceptance.")
