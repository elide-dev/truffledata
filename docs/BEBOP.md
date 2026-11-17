# Bebop backend

The `//bebop` module encodes and decodes [Bebop](https://github.com/6over3/bebop-next) values in
the 2026 **indexed** wire layout (`docs/WIRE.md` at `2026.0.0-alpha.6`), and reads validated bytes
in place from Truffle guests. It is pure Java: one jar serves the JVM and Native Image. An optional
native validator (`//bebop/native`, Rust) is opt-in.

Status: experimental. Nothing here is a frozen wire profile.

```java
BebopSchema schema =
    BebopSchema.builder()
        .struct("Row", new Field("id", UINT32), new Field("score", FLOAT64))
        .message("Table", new TaggedField(1, "rows", BebopType.array(BebopType.ref("Row"))))
        .build();
BebopCodec codec =
    BebopCodec.compile(schema, BebopType.ref("Table"), BebopProfile.GENERAL, BebopLimits.DEFAULT);

byte[] bytes = codec.encode(new BebopRecord("Table", List.of(new BebopRecord("Row", 7L, 0.5))));
Object value = codec.decode(bytes);            // host values
BebopDocument doc = codec.open(bytes);         // validate once, then read in place
BebopRow table = doc.row();                    // guest row: interop, DataLibrary, accessors
BebopSpan scores = doc.span("rows", "score");  // bounded column view, no object per element
```

## Parts

| Part | Role |
| --- | --- |
| `BebopSchema`, `BebopType` | Immutable definitions (enums, structs, messages, unions) and type expressions, including refinements. `fingerprint()` covers the definitions; the identity of what a codec reads also binds its root type ([identity](#identity)) |
| `BebopDescriptors` | Loads the `DescriptorSet` that upstream's `bebopc` compiles from `.bop` files into a `BebopSchema` |
| `BebopProfile` | `GENERAL`, the strict profile of rules revision 0.11 (`rules011`), or a custom strict profile whose every choice is an explicit input |
| `BebopLimits` | Byte, count, depth, work and allocation limits |
| `BebopCodec` | Encoder, decoder, allocation-free validator and the strict reference validator for one root type |
| `BebopDocument` | Validated immutable bytes; the owner of everything read in place |
| `BebopRow`, `BebopArray` | Guest views: structs, messages, unions; arrays, sets, maps |
| `BebopSpan`, `BebopOffsets` | Bulk views: fixed-width columns and validated string ranges |
| `BebopValidator` | The validator interface; `JAVA` is the reference |
| `//bebop/native`, `//bebop/kernel` | The native validator and its JVM (FFM) and Native Image backends |

Failures are `BebopException` with one of four kinds that are never folded together:
`UNSUPPORTED` (schema or type outside the codec or profile), `MALFORMED` (bad or non-canonical
bytes), `LIMIT` (a local resource limit; not a verdict on the bytes) and `VALUE` (a host value
outside the schema's value domain, from the encoder).

## Loading schemas from `bebopc`

```java
BebopSchema schema = BebopDescriptors.load(descriptorSetBytes);
BebopCodec codec =
    BebopCodec.compile(schema, BebopType.ref("pkg.Outer.Inner"), profile, BebopLimits.DEFAULT);
```

A descriptor set is a Bebop value of upstream's `bebop/descriptor.bop`, so the loader decodes it
with `BebopCodec` under the general profile (`BebopDescriptors.SCHEMA` is that schema, field for
field) and converts the result. Verified against `bebopc` at `2026.0.0-alpha.6` (commit
`f73e40a1`): descriptor sets use the indexed message layout, the same layout as the C runtime's
encoder; `bebopc` passes byte-identical schemas to its plugins.

- Definitions are named by fully-qualified name (`pkg.Outer.Inner`; a bare name when the file
  declares no package), nested and inline union-branch definitions included, across every schema
  in the set. Order in the set does not matter (upstream keeps source order, not dependency order).
- Inline branches are named by the last component of their definition, as upstream's accessor does.
- Services, constants and decorator definitions are not data types and are left out.
- `@flags` enums load as flags enums (`EnumDef.flags()`, part of the fingerprint). The general
  profile reads them like any enum; a strict profile refuses them as `UNSUPPORTED`, because no strict
  rule for combinations of flags is decided.
- Sets are an explicit input. Every array is a sequence unless `Options.withSetDecorator(name)`
  names a decorator; a field carrying it must be a dynamic array. `bebopc` records a usage by its
  spelling (`@set` as `set`, `@pkg.set` as `pkg.set`), so the name is compared with that spelling.
- Bytes that are not a descriptor set fail as the codec reports them (`MALFORMED`, `LIMIT` under the
  given limits); a self-contradicting descriptor (missing names or types, duplicates, dangling or
  non-type references, members outside their base) is `MALFORMED`; anything the codec cannot
  represent (unknown kinds, another edition, non-integer enum bases, unkeyable map keys, a set
  decorator on a non-array) is `UNSUPPORTED`. A loaded schema always compiles under the general
  profile.

Golden descriptor sets, their `.bop` sources, and values encoded by upstream's C runtime are in
`bebop/tests/descriptors` (`gen/` holds the programs that wrote them, linked against upstream's
`libbebop` and `bebopc-gen-c` output). `bebop/descriptor.bop` and `bebop/decorators.bop` there are
upstream's files, unmodified, used to check that `BebopDescriptors.SCHEMA` matches them.

## Both profiles

These hold under the general profile too. Where upstream is silent or more permissive, this
module is deliberately stricter.

- **Message index.** Fields in ascending tag order; the directory is the canonical choice
  (smallest mask that fits the highest tag, blocks above tag 32, a tiny directory when at most
  three fields make it *strictly* smaller); the boundary width is the smallest that represents the
  payload size, also when there is no boundary; reserved control bits are zero; ranks, counts,
  masks and boundaries are checked. Any other index is rejected.
- **Exact consumption.** Every field fills its indexed range, every length-delimited region is
  consumed exactly, and trailing bytes are rejected. A zero-byte range is valid only for a type
  with a zero-byte encoding, which follows from exact consumption.
- **Strings.** Valid UTF-8 (no overlong forms, surrogates or code points above U+10FFFF), the
  declared length, embedded NULs kept, and a NUL terminator. No normalization. The encoder rejects
  unpaired surrogates.
- **Maps.** Duplicate keys are rejected. The encoder rejects distinct host keys with one encoding
  (`Integer 1` and `Long 1`).
- **Limits.** A count that cannot fit the remaining bytes is malformed whatever the limits are;
  collections of zero-byte elements are bounded by the count and work limits.

The general profile otherwise follows upstream: any nonzero byte is a true boolean, unknown
message tags are skipped, unknown union branches keep their raw content, unknown enum values pass
through, map and array order is the wire order, and every float bit pattern is preserved.

## Strict profile

A strict profile admits an explicit set of primitive types and accepts exactly one encoding per
value. A schema using a type that is not admitted is `UNSUPPORTED`. There are two ways to get one:

- `BebopProfile.rules011(identifier)` is rules revision 0.11 with every choice fixed: all
  primitive types admitted; canonical NaNs `0x7E00` (float16), `0x7FC0` (bfloat16), `0x7FC00000`
  (float32) and `0x7FF8000000000000` (float64); acceptance and equality as in the table below.
  The caller supplies the identifier its project gives that revision.
- `BebopProfile.strict(name)` builds a custom profile from explicit inputs. It has no defaults
  and can never carry a rules revision's identity.

Both are experimental and say so in their identity. Nothing here is a frozen profile.

```text
decode(encode(value)) == value
if decode(bytes) succeeds: encode(decoded) == bytes
```

Equality is equality in the value domain below, not host equality. `BebopValues.equal` compares
decoded values of one type under one profile; it is not an equality over arbitrary host inputs
(it does not treat a set as unordered or an `Integer` and a `Long` as one integer). The tests
judge value preservation with a separate reference that is driven by the schema and never calls
the codec (`Abstract` in `bebop/tests`).

| Type | Value domain and equality | Canonical encoding | Decoder rejects | Encoder |
| --- | --- | --- | --- | --- |
| `bool` | true, false | `01`, `00` | any other byte | — |
| integers, 8 to 128 bits | the integer | fixed width, little-endian | nothing (every pattern is a value) | rejects out-of-range values |
| `float32`, `float64` | every non-NaN bit pattern is its own value, so `+0` and `-0` differ; all NaNs are one value | the bits; NaN as the profile's pattern for that type | any other NaN pattern | writes the profile's NaN for any host NaN |
| `float16`, `bfloat16` | as above, at 16 bits | as above, with `canonicalNaN16` / `canonicalNaNBrain16` | any other NaN pattern (signaling included) | as above; rejects values not exactly representable |
| `string` | the sequence of Unicode scalar values, unnormalized | UTF-8, length prefix, NUL terminator | invalid UTF-8, wrong terminator | rejects unpaired surrogates |
| `uuid` | the 16 bytes; nil and non-RFC variants are values like any other | the 16 bytes in text order | nothing | — |
| `timestamp` | the triple (seconds, nanoseconds, offset); the same instant at two offsets is two values | the three fields | nanoseconds outside `[0, 999999999]`; offset outside `[-86400000, 86400000]` ms | rejects the same |
| `duration` | the signed span | seconds and nanoseconds with `abs(nanos) < 10^9` and never opposite in sign to the seconds | any other form | rejects non-canonical forms; it does not renormalize |
| enum | a declared member | the base integer | undeclared values | rejects undeclared values |
| struct | its fields | fields in order | — | — |
| message | present fields; absent and present-zero differ | the indexed layout | unknown tags | writes every present field |
| union | a declared branch and its value | discriminator and content | unknown discriminators | rejects unknown discriminators |
| array (sequence) | the elements in order | count and elements | — | keeps order and duplicates |
| array (set) | the set of elements | distinct elements ascending by their encoded bytes | unsorted or repeated elements | sorts and removes duplicates |
| map | the key-to-value function | entries ascending by encoded key bytes | unsorted or repeated keys | sorts; rejects keys that collapse |

Encoded-byte order is not numeric or lexical order: little-endian integers and length prefixes
lead the comparison (`256` sorts before `1`; `"b"` before `"aa"`). A set of zero-byte elements has
at most one member. The canonical NaN patterns have no default; a float type admitted without one
is refused when the profile is built.

Rulings applied from review: UUIDs keep all 128-bit patterns with no variant or version check;
timestamps have no calendar or whole-minute restriction; durations with zero seconds may carry
nanoseconds of either sign.

**Integers and hosts.** Every integer box of the right value is the same value (`Integer 7`,
`Long 7`, `BigInteger 7`). `uint64` decodes to a `long` holding the bits; the encoder also takes a
non-negative `BigInteger`. Conversions are exact and out-of-range values are refused.

**UTC instants.** `BebopType.utcInstant()` refines a timestamp at the schema level: the offset
must be zero. The 16 bytes are unchanged. A nonzero offset is rejected on decode (`MALFORMED`) and
on encode (`VALUE`) under either profile; it is never cleared or subtracted. The plain timestamp
keeps its offset, and the same instant at two offsets stays two values. A refinement the codec
cannot validate is `UNSUPPORTED`, never silently reduced to its base type.

**Duration adapter.** `BebopDuration.normalized(seconds, nanos)` is an explicit host helper: the
exact total, split with quotient and remainder truncated toward zero, so `(-1, +500000000)` gives
`(0, -500000000)`; a result outside the domain is a `VALUE` error. The codec never applies it: the
strict encoder refuses a non-canonical pair and the decoder never repairs one.

### Identity

| Identity | Binds |
| --- | --- |
| `BebopProfile.WIRE_REVISION` | `bebop-2026.0.0-alpha.6@f73e40a1b118aff6d29d91a00ed3fdb7b4fe5e8c` |
| `BebopProfile.identity()` | `general;wire=…`, or `strict-experimental;rules=<identifier>;wire=…;admit=…;nan16=…;nanbrain16=…;nan32=…;nan64=…`. A custom profile has `custom=<name>` in place of `rules=` |
| `BebopSchema.fingerprint()` | Every definition, including flags enums and each field's set/sequence choice and refinements |
| `BebopCodec.schemaIdentity()` | The wire revision, the schema fingerprint and the root type with its nested set/sequence choices and refinements. Two roots over one schema differ |
| `BebopCodec.identity()` | The schema identity and the profile identity: everything that decides acceptance |

Identities are SHA-256 over canonical text. The profile name is not written into any encoding.

### Three validators, one answer

- `decode` checks everything directly while building the value.
- `validate` makes the same checks and builds nothing.
- `decodeReference` is the reference for strict profiles: decode without the canonical-form
  checks, re-encode, and accept only if the bytes are reproduced.

With sufficient budgets they accept exactly the same inputs, and `decode` and `validate` report
the same reason. Budgets are per operation: only decoding charges for what it retains, so the
allocation-free `validate` can accept bytes an allocating `decode` refuses under the same limits.
A local limit is reported as a limit and never as malformed bytes. The reference decodes leniently
first, so on bytes that are both non-canonical and over a limit it may report the limit. The
native validator is held to `validate`'s exact answer.

## Reading in place

`codec.open(bytes)` validates the whole value and keeps a private copy; `adopt(bytes)` keeps the
array itself (the caller gives up ownership). Everything read from a document keeps it alive, so
backing storage lives exactly as long as its views. Loads are bounded and assume no alignment and
no readable padding.

**Bulk views.** `span(path...)` names message and struct fields down to an array, then optionally
struct fields inside its fixed-size elements, giving one column of an array of structs: element
type and width, row count, stride and byte bounds, bound to the document's schema identity.
`strings(path...)` gives validated byte ranges of an array of strings. A structural pass locates
columns behind variable-width fields without decoding them. An absent message field yields
`null`; there are no sentinels and no derived bitmap. Dictionary and derived-offset schemas are
not defined yet, so they have no views.

**Guest rows.** `BebopRow` exports `DataLibrary` and the shared row interop; `BebopArray` is an
owned array. Guest values: `Boolean`; `Long` for integers to 63 bits and `int64`; `ExactNumber`
for `uint64` and 128-bit integers; `Double` for every float type; `String` for strings and UUIDs;
rows for structs, messages, unions (a `discriminator` and one optional member per branch),
timestamps (`seconds`, `nanos`, `offset_ms`) and durations; arrays for arrays, sets and maps (rows
with `key` and `value`). A message field read is the control byte, the directory, a population
count and one boundary load, with no allocation. Each codec type has one layout and one schema, so
read nodes bind a layout-bound accessor guarded by a class and a field compare.

## Native validator

`//bebop/native` is a small Rust crate with one job: `td_bebop_validate(program, data)` checks a
whole encoded value in one call and returns a reason code. It is called once per document, never
per field. UTF-8 validation uses simdutf. The schema program (format `BBP1`, documented in
`BebopProgram`) is compiled by the Java codec from the same nodes it interprets, and carries the
profile and limits.

It is opt-in: `codec.open(bytes, validator)` with `FfmValidator.load(path)` on the JVM
(`//bebop/kernel:jvm`) or `new SvmValidator()` in a native image (`//bebop/kernel:svm`, with the
static archive `//bebop/native:truffledata_bebop`). `//bebop` neither bundles nor loads it. The
Java validator is the reference and the default. No paired SVM measurement of the two has been
taken, so there is no performance claim and no change of default.

## Tests

| Target | What it checks |
| --- | --- |
| `//bebop/tests:jvm` | Descriptor sets from `bebopc` loaded and compared with hand-written schemas; values from upstream's C runtime decoded and re-encoded byte for byte (every directory kind and boundary width, timestamp and duration extremes); specification vectors; canonical directories and widths; strict rules for every type; round trips; the three-way validator differential on mutated inputs; rows, arrays and spans against the decoded value for every type; limits; the shared fixtures against an independent abstract-value reference; all 65,536 patterns of each 16-bit float; temporal layout and identity |
| `//bebop/tests/svm:native_test` | Native image: codec and strict smoke checks, views, guest reads, and compiled-read gates (compiled, AST budget, at most 0.05 B allocated per read) |
| `//bebop/kernel/tests:jvm` | The native validator through FFM against the Java reference: same acceptance and same reason, every type, both profiles, tight limits |
| `//bebop/kernel/tests/svm:native_test` | The statically linked validator in a native image against the Java reference |
| `//bebop/native:test` | Rust unit tests |
| `//bebop/fuzz:replay`, `//bebop/fuzz:campaign` | The untrusted-input harness over generated seeds and the corpus, and the Jazzer campaign ([fuzzing](FUZZING.md)) |

Coverage: `bazelisk coverage //bebop/tests:jvm //bebop/kernel/tests:jvm --config=codecov`.

## Shared fixtures

`bebop/fixtures/` holds versioned JSON vectors (format `bebop-fixtures/1`, described in its
`README.md` and checked by `fixtures.schema.json`): `general.json` and `strict.json` for wire
acceptance, and `host.json` for encoder-side normalization and refusal, kept apart. Each file
records the wire revision, the profile identity inputs, the definitions, and per case the root
type with refinements, the bytes in hexadecimal and a typed expected value or failure; resource
cases name the operation and its budgets. Wide integers are decimal strings and floats are
fixed-width bit patterns. The expected bytes come from `gen.py`, an encoder that shares no code
with the implementations. `BebopFixtureTest` runs every case; `KernelDifferentialTest` runs every
wire and resource case through the native validator.

## Not yet done

- A `.bop` parser; schemas come from `bebopc` descriptor sets or are built programmatically.
- A decided convention for sets in `.bop` sources, and a strict rule for `@flags` enums (a strict
  profile refuses them as `UNSUPPORTED`).
- The shared fixtures have not been run through the upstream C runtime. The cross-check with
  upstream runs the other way: values its C runtime encoded (temporal types, every directory kind
  and boundary width) are decoded and re-encoded byte for byte. Its map encoder writes hash order,
  which the strict profile rejects by design.
- A fixture runner outside Java. The Rust validator consumes the fixtures through the Java loader
  because it takes a compiled schema program, not a schema.
- Randomized differential testing against the upstream C runtime (it is driven only to write the
  checked-in values, on macOS, outside the build).
- Scan kernels over spans, and SIMD validation of anything but UTF-8.
- Registration in the root source-policy and JIT-matrix tests.
- SVM timings of any kind. No performance claim is made for any path, native validator included.
- Anything above the codec: object schemas, identity and trust integration, query semantics.
