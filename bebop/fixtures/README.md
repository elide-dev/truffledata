# Bebop shared fixtures

Positive and negative vectors for any implementation of the indexed wire layout and the strict
profile. JSON is only the interchange format of the tests; it is not a Bebop encoding.

| File | Contents |
| --- | --- |
| `general.json` | Wire acceptance and resource cases under the general profile |
| `strict.json` | Wire acceptance under the strict profile, rules revision 0.11 |
| `host.json` | Host-side normalization and refusal by an encoder, and the duration adapter. Not wire acceptance |
| `fixtures.schema.json` | JSON Schema of the format |
| `gen.py` | Writes the three files: `python3 gen.py . [rules-identifier]` |

The expected bytes are computed by `gen.py`, an encoder written from the wire rules that shares no
code with the Java or Rust implementations. The Java suites run every case
(`BebopFixtureTest`), and the native validator runs every wire and resource case through the same
loader (`KernelDifferentialTest`).

## Format `bebop-fixtures/1`

A file is one object:

- `format`: `"bebop-fixtures/1"`.
- `wire`: the upstream revision, tag and commit: `bebop-2026.0.0-alpha.6@f73e40a1…`.
- `profile`: `{"name": "general"}`, or `{"name": "strict", "rules": …, "admit": "all", "nan16": …,
  "nanBrain16": …, "nan32": …, "nan64": …}` with NaN patterns as fixed-width hexadecimal.
  `rules` is the identifier a project gives the rules revision; the default here is a neutral
  placeholder.
- `definitions`: enums (`base`, `members` as `[name, decimal]`), structs (`fields` as `[name,
  type]`), messages (`fields` as `[tag, name, type]`) and unions (`branches` as `[discriminator,
  name, type]`).
- `cases`: each has a unique `name`, a `kind` and, except for the adapter, a `root` type.

**Types.** A primitive is its name (`"int32"`, `"float16"`, `"timestamp"`, …). `"utc-instant"` is
a timestamp refined to a zero offset. Compound types are `{"array": T}`, `{"set": T}`, `{"fixed":
[T, n]}`, `{"map": [K, V]}` and `{"ref": name}`. Sets and sequences are distinct types.

**Values.** Typed, never bare JSON numbers:

| Value | Meaning |
| --- | --- |
| `{"bool": true}` | A boolean |
| `{"int": "-5"}` | Any integer or enum value, in decimal |
| `{"bits": "7e00"}` | A float as its bit pattern, 4, 8 or 16 hexadecimal digits for the type's width |
| `{"bits32": "3dcccccd"}` | Host input only: a binary32 value offered to a reduced-width float type |
| `{"string": "…"}` | A string |
| `{"uuid": "…"}` | A UUID in canonical text |
| `{"timestamp": [s, n, o]}` | Seconds, nanoseconds and offset in milliseconds, in decimal strings |
| `{"duration": [s, n]}` | Seconds and nanoseconds, in decimal strings |
| `{"bytes": "…"}` | An array of `byte`, in hexadecimal |
| `{"seq": […]}`, `{"set": […]}` | A sequence (ordered) and a set (unordered) |
| `{"map": [[k, v], …]}` | Map entries; order carries no meaning |
| `{"record": […]}` | Struct or message fields in definition order; `null` is an absent message field |
| `{"union": [d, v]}` | A branch; `{"raw": "…"}` for the content of a branch the schema does not know |

**Case kinds.**

| Kind | Fields | Requirement |
| --- | --- | --- |
| `wire-accept` | `bytes`, `value`, optional `canonical: false` | The bytes decode to the value. Unless `canonical` is false, the value encodes to exactly these bytes |
| `wire-reject` | `bytes`, `error` | Decoding and validation fail with `error`: `malformed` |
| `host-normalize` | `host`, `bytes`, `value` | Encoding the host value gives the bytes, which decode to the value |
| `host-reject` | `host`, `error` | The encoder refuses the host value: `value` |
| `resource` | `bytes`, `operation`, `limits`, `error` | Under these limits, `operation` (`decode` or `validate`) ends with `limit`, or succeeds when `error` is `none` |
| `duration-adapter` | `host`, `value` or `error` | The optional normalization of a host duration |

A local limit is never reported as malformed bytes, and an allocation-free `validate` can accept
bytes an allocating `decode` refuses under the same limits; resource cases name the operation.
