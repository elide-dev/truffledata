# Optional Myna/Buffa backend (0.2.0 development)

This first integration decodes **schemaless protobuf wire input** using Buffa's borrowed
slice primitives, behind Myna-generated C/SVM and Java FFM bindings. It returns the same
ordered `(number, wireType, value)` rows as `ProtoWire`, preserving duplicates, groups,
unsigned bits, and opaque length-delimited payloads. It does not guess strings, embedded
messages, or packed arrays. Schema-aware `ProtoPlan`, MessageOrBuilder, ProtoJSON, and
text-format ingestion retain their Java implementations. Generated Buffa message views
and native schema/text parsing are subsequent work, not implicit capabilities of this API.

## Ownership and copies

`BuffaWire.allocate(size)` allocates unmanaged input for native backends. Fill it using
`put`, then decode; the first decode seals the input against further writes. One native
parse call produces a bounded field index. Payloads are slices of the input, never copied
or expanded to per-byte objects by Rust. Java materializes field rows, then releases the
native index in one cleanup call; guest reads make no native calls.

A `WireMessage` retains the input allocation independently of the `WireInput` handle.
Close the producer's handle as soon as it is no longer needed; memory remains live until
the last message closes. A native payload is a `BorrowedBinary`/`BorrowedBytes` interop array.
Its reads check the message lifetime and reject closed or cross-thread access. A message
can live for an entire request, context, or longer when its owner keeps it open. There is
no implicit GC finalizer or Cleaner: close handles explicitly. These owners are confined
to their creating thread; cross-thread/async ownership transfer is not implemented.

`snapshot()` detaches the message into ordinary immutable rows with `DataArray` binary
payloads. Each payload is copied once during detachment. `Values.normalize` rejects a
`BorrowedBinary`, so placing a borrowed payload into an owned batch/array requires an
explicit snapshot. ANY adapter-read contracts admit the borrowed representation; normal
ARRAY contracts still use owned `DataArray` values.

For messages that already originate off-heap, transfer a region with
`BuffaJvm.adopt(MemorySegment, releaseCallback)` or
`BuffaSvm.adopt(address, length, releaseCallback)`. Neither copies input. The callback
runs on the owning thread after the input handle and all its message views close.
The producer must not mutate, recycle, or independently release that region before the
callback. JVM foreign-memory scope checks remain active. The SVM pointer API is explicitly
unsafe: address validity, length, and exclusive ownership are caller obligations.

```java
try (var decoder = BuffaWire.open(BuffaWire.Mode.AUTO, libraryPath);
     var input = decoder.allocate(wire.length).put(wire);
     var message = decoder.decode(input, Limits.DEFAULT, 32, 8 << 20, 1 << 20)) {
  // Keep message open while a guest consumes message.fields().
  // message.snapshot() can outlive both handles.
}
```

A heap byte array must be copied once into the native input allocation in this API.
Adopted producer memory avoids that copy. Java fallback currently uses ordinary snapshots
and therefore copies input/payloads. Do not describe the whole library as universally
zero-copy. Compare ingestion, retained reads, and detachment separately.

## Runtime selection

`JAVA` never loads native code. `AUTO` selects native only when explicitly available:
JVM callers provide a library path; SVM builds opt into `BuffaFeature` and link the Rust
archive statically. With neither, it uses the Java parser. `NATIVE` requires native support
and fails if unavailable. `selected()` exposes the decision. No search of arbitrary
library paths, runtime downloads, or silent fallback after parse/contract errors occurs.
An ABI fingerprint mismatch is an error, never a fallback condition.

SVM is the primary native target. Its generated `@CLibrary(requireStatic = true)` and
`@CFunction` bindings call the linked archive directly. Normal VM transitions are retained:
parsing/allocating operations are not eligible for Myna's no-transition leaf contract.
FFM is a separate JVM adapter; it requires Java 25 and native access enabled. The SVM
fallback image is built without the Feature/archive to prove that Java-only use needs no
native symbols. The native-enabled image uses `BuffaFeature`, not runtime FFM registration.

## Build and validation

The optional `//protobuf-buffa` target is not part of the 0.1.0 Maven artifacts. This PR
does not publish 0.2.0. Builds use pinned Myna sources, Buffa crates, Rust toolchains and
Cargo/Bazel lockfiles. Cargo records dependencies; Bazel compiles and links everything
under the existing BuildBuddy remote execution/cache configuration.

```sh
bazel test //protobuf-buffa/native:test //protobuf-buffa/tests:jvm
bazel test //protobuf-buffa/tests:native_test //protobuf-buffa/tests:fallback_test
```

Rust tests cover wire parsing, malformed inputs, budgets, borrowing offsets and ABI
ownership. Shared JVM/SVM acceptance covers parity with Java, producer-memory adoption,
multiple leases, close order, snapshots, confinement, malformed-input propagation, and
allocation bounds. Compiled interop reads must execute compiled code within AST/allocation
budgets; closing a message invalidates those reads. Native images enable the existing
Truffle blocklist/never-part-of-compilation checks with assertions and strict contracts enabled.
The parity corpus includes 1,000 seeded arbitrary wire inputs, 200 payload cases, and explicit
unsigned/fixed/group and malformed cases. Layouts and call signatures are Myna-generated and
fingerprinted; Rust checks their layout at compile time.

Wire bytes, structural cells (four per field plus payload bytes), group depth, and native
index allocation have explicit limits. Native allocation errors/panics translate to a
host error and do not unwind into Java. Fingerprints check generated ABI agreement; they
do not make an arbitrary untrusted native library safe to load. Allocation budgets exclude
allocator metadata and the caller's independently owned input region. Index-growth budgets
also allow for the old and new buffers coexisting during relocation.

The Linux amd64 JVM acceptance run measured about 1.4 KB of Java allocation per borrowed
64 KiB message and 0.000064 bytes per compiled byte read, with a 10-node access AST.
These are allocation checks, not throughput claims. CI enforces a 16 KiB/message allocation
ceiling, 0.05 bytes/compiled read, a 192-node AST ceiling, and a 128-byte native index budget
for this single-payload case. JVM allocation counters are not used in SVM; SVM runs the same
semantic/lifetime corpus, compiled-read checks, native index budgets, and blocklist gates.
