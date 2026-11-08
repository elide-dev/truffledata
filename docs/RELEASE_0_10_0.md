# TruffleData 0.10.0

This follow-up to the intermediate 0.9.0 release adds native YAML support and
completes the public protobuf slab and compact JSON reader migration. Java 25,
Myna 0.13.0 and Elide GraalVM 25.5.5-elide.27 remain the runtime baseline.
Upgrade native attachments and generated bindings together.

## Data APIs

- Protobuf durable messages own their slab bytes. Contract types use generated
  Java producers on the JVM; Native Image uses the native decoder with Java
  fallback. Reusable sessions and explicit projections support borrowed data and
  lazy children with checked lifetimes. Raw unknown-field presence and byte counts
  are available without copying on immutable, generated, owning and borrowed rows.
- Resolved protobuf Editions, extensions and MessageSet plans can bind supported
  host sources explicitly. `Source.ingestion()` identifies direct, normalized and
  unsupported paths before input is processed.
- JSON uses compact readers and a structural-index producer for ordinary and
  streamed documents. Sink documents omit member directories; retaining them
  constructs the directories needed for key lookup. Duplicate and Unicode checks
  remain mandatory.
- YAML exposes documents, metadata, directives, aliases, typed records, sessions,
  streams and text output, with JVM FFM and static Native Image backends.
- CSV accepts bounded byte-array windows, owned copies and caller-owned file-channel
  mappings. Schema name lookup uses an immutable ordinal index.

## Distribution

Twenty-one libraries and the BOM include source/Javadoc jars, Gradle module
metadata, POMs, signatures and checksums. The project is dual licensed under MIT
or Apache 2.0, at the consumer's option. The publisher validates a signed Central
bundle before publication; the Elide repository also supplies the GraalVM fork.

Buffa, JSON, YAML and CSV natives support Linux x86-64, Linux AArch64 and macOS
AArch64. CSV additionally supplies static musl archives for both Linux
architectures and SDK tarballs containing C headers, manifests, license notices,
link instructions and hashes. CSV implementation symbols are isolated from the
five-function public C ABI.

## Compatibility and measurement

`Accessor` adds an optional protected constructor for layouts identified by exact row class and
schema identity, plus protected `readLongBound`, `readDoubleBound`, `readBooleanBound`,
`lengthBound` and `assertAccepted` hooks. Existing subclasses retain checked defaults. Bound
reads skip only a repeated layout check; lifetime, projection, presence and type checks remain.
Public accessor methods stay checked. This is an additive API change.

Native protobuf ABI11 retires the old binary view and scalar entry points.
Public migration aliases delegate to owning slabs; protobuf text/JSON parsing
retains its separate typed support. Compiled runtime-schema decoders were
measured and were not adopted because the joint JVM/Native Image speed gate was
missed. Runtime schemas keep the table-driven Java fallback.

Performance results, remaining hypotheses and reproducible commands are recorded
in the JSON, YAML and protobuf plan ledgers and comparison results. Exploratory
single-fork timings are not release performance claims. Final release validation
requires green main, publication consumers and the full native platform matrix.
