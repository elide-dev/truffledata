# TruffleData 0.13.0

This release adds experimental Bebop support. Every other library and its natives are built from
the same sources as 0.12.0, so defaults, flags, APIs and native ABIs are unchanged.

## Bebop (experimental)

`dev.elide:truffledata-bebop` encodes and decodes [Bebop](https://github.com/6over3/bebop-next)
values in the 2026 indexed wire layout, pinned to upstream `2026.0.0-alpha.6`. It is pure Java, one
jar for the JVM and Native Image, with no native attachments. It is in the BOM.

**Experimental means:** upstream is an alpha, the strict profile is not frozen, and the API, the
identities and the fixtures can change in any release. No performance has been measured, on any
runtime. Read [the Bebop backend](BEBOP.md) before depending on it.

What it does:

- **Every type.** All primitives (including 128-bit integers, `float16`, `bfloat16`, UUID,
  timestamp and duration), enums, structs, messages, unions, dynamic and fixed arrays, sets and
  maps.
- **Two profiles.** The general profile reads and writes the wire format. A strict profile accepts
  exactly one encoding per value: canonical NaNs, sorted maps and sets, exact schemas, canonical
  timestamps and durations. Rules revision 0.11 is a preset; a custom profile takes every choice as
  an explicit input. Identities bind the wire revision, the rules, the schema, the root type and
  refinements such as UTC-only timestamps.
- **Validation without decoding.** An allocation-free validator accepts exactly what the decoder
  accepts and reports the same reason.
- **Reading in place.** A validated document gives guest rows and arrays through `DataLibrary` and
  interop with layout-bound accessors, and bounded column views over arrays of fixed-width values.
- **Schemas from `bebopc`.** `BebopDescriptors.load` turns a descriptor set into a schema.

Where it is stricter than upstream, under both profiles: non-canonical message indexes,
non-minimal boundary widths, invalid UTF-8 and duplicate map keys are rejected.

What it was checked against: values encoded by upstream's C runtime (temporal types, every
directory kind, each boundary width) decode and re-encode byte for byte, and descriptor sets from
`bebopc` load and re-encode to the same bytes. Upstream writes map entries in hash-table order,
which the strict profile rejects by design.

Known limits:

- No `.bop` parser; schemas come from `bebopc` descriptor sets or are built in Java.
- `@flags` enums work under the general profile and are refused by a strict profile.
- Marking an array as a set when loading a descriptor needs an explicit option; there is no
  convention for it in `.bop` sources yet.
- The optional native validator (Rust) and its JVM and Native Image backends are in the repository
  but are not published.

## Build and CI

- The sanitizer toolchain add-on is pinned to the published `v2026.11.6` archive (#101); the old
  pin predated that release and failed uncached fetches.
- The security workflow runs the Bebop fuzz harness and the Rust address sanitizer on the Bebop
  validator.

## Performance

No performance work is in this release and no new benchmark capture was taken. The README cards
remain those of earlier releases.

## Publication

Published on Elide Maven and GitHub. Maven Central remains deferred under
[#28](https://github.com/elide-dev/truffledata/issues/28).
