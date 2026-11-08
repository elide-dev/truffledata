# Generated eager projections

A local diagnostic of the generated non-lazy projection extension, following the
JVM selected-small miss (0.86384 versus the required 0.85 ceiling) in invocation
4065dd6f's full qualification. This is not the required resident performance gate.

The generated loop now validates omitted child wire records without producing
records or element slots. Omitted repeated messages retain count-only headers,
matching the table producer byte for byte. Singular presence, required-descendant
fallbacks, unknown-field/complex-schema fallback, string validation and wire/depth/
slab budgets remain checked. Eager projection cache misses start with a bounded
256-byte buffer; lazy and ordinary producer sizing are unchanged.

The diagnostic covers 29,440 exact byte/status pairs across 96 Type fixtures,
eager/lazy/strict/lax/borrowed projections, offset windows, exact and one-byte-below
budgets, mutations and truncations. ProtoNativeTest extends its recursive generated
fixture corpus to eager projections and transitive-required fallback cases; its
Bazel run remains pending at this checkpoint.

| Input | Table ns/op | Generated ns/op | Table B/op | Generated B/op |
| --- | ---: | ---: | ---: | ---: |
| small | 292.29 | 120.54 | 982.25 | 307.75 |
| holdout | 665.48 | 361.80 | 982.25 | 324.75 |
| large | 55695.35 | 27613.09 | 982.25 | 375.75 |

These are five post-warmup sample medians from one local macOS JVM process,
GraalVM 25.5.5-elide.27, not paired fresh Linux resident measurements. Timing
order is table then generated. Raw samples and both small diagnostic sources are
included; the generator writes the Type contract to /tmp/td-generated-projection.
The workload comes from benchmarks/comparison/SlabWriterProbe.fixture, and the
selected fields match the resident benchmark. Final E1 qualification remains
required. No acceptance threshold changes.
