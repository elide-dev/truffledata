# Optional Myna/Buffa backend

The optional backend supports schemaless wire inspection and schema-directed binary,
protobuf text format, and ProtoJSON ingestion. Native parsing uses Buffa wire primitives
and its text tokenizer behind Myna-generated C/SVM and JVM FFM bindings. A compiled
`ProtoPlan` supplies a bounded schema program; both generated contracts and runtime plans
work without protobuf-java reflection during ingestion. Binary results use durable owning
slabs; text/ProtoJSON and schemaless inspection retain explicit scoped ownership.

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
explicit snapshot. ANY and ARRAY adapter-read contracts admit scoped arrays and enforce their element and
length constraints. Owned normalization still requires explicit detachment.

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

`JAVA` never loads native code. On the JVM, `AUTO` selects the Java producer; setting
`-Dtruffledata.buffa.jvmNative=true` opts into an explicitly supplied native library. SVM
`AUTO` uses `BuffaFeature` when its Rust archive is linked, otherwise Java. `NATIVE` requires native support
and fails if unavailable. `selected()` exposes the decision. No search of arbitrary
library paths, runtime downloads, or silent fallback after parse/contract errors occurs.
An ABI fingerprint mismatch is an error, never a fallback condition.

SVM is the primary native target. Its generated `@CLibrary(requireStatic = true)` and
`@CFunction` bindings call the linked archive directly. Normal VM transitions are retained:
parsing/allocating operations are not eligible for Myna's no-transition leaf contract.
FFM is a separate JVM adapter; it requires Java 25 and native access enabled. The SVM
fallback image is built without the Feature/archive to prove that Java-only use needs no
native symbols. The native-enabled image uses `BuffaFeature`, not runtime FFM registration.

### Producer and ownership matrix

| Entry point / environment | Producer | Result |
|---|---|---|
| `ProtoPlan.decodeSlab`, generated contract | Generated Java decoder bound once per plan | Durable owning heap slab; no native dependency |
| `ProtoPlan.decodeSlab`, runtime model | Table-driven Java decoder | Durable owning heap slab |
| Buffa `JAVA`, or JVM `AUTO` | Same Java producer selection | Durable owning heap slab |
| Buffa SVM `AUTO` with linked archive, or explicit `NATIVE` | Native compiled layout | Durable owning heap slab |
| Buffa SVM `AUTO` without archive | Generated Java for contracts, table Java for runtime models | Durable owning heap slab; native-free release gate |
| `compiled.project(selection).decode` | Native selected-field producer; generated Java for eligible lazy selections, table Java otherwise | Durable selected slab; immutable input retained only for external bytes/lazy children |
| `compiled.newSession(...).decode` | Native compiled layout into a reusable arena | Scoped borrow; explicit `retain()` produces a durable owner |
| Text / ProtoJSON | Native typed-index scratch plus eager Java values, or bounded Java parser | Scoped text result with an input lease |

Generated Java fast loops restart through the table producer for unknown fields, maps, groups
and singular-message merges. Generated lazy selections without byte borrowing use specialized
validation scans and lazy headers; unsupported shapes restart the table producer. Required
descendant closures remain eager, including when the required descendant is absent. Runtime
models and other selected Java decodes use the table producer.
The optional compiled-runtime-decoder experiment missed its joint JVM/SVM speed gate and is not
selected in production; runtime-created models keep the table fallback. The measured ruling is
in [the A4 evidence](../benchmarks/results/protobuf-compiled-decoder-dab2dbc5/README.md).

## Typed and text input

`BuffaWire.decodeTyped(input, plan)` reads the sealed input region directly through the
bounded Java codec. This works with allocated or adopted native memory on JVM and SVM,
and with Java-only input. It does **not** run a redundant native validation pass or copy
the entire wire buffer into a Java array. The result is an ordinary owned `ProtoRow`:
strings, bytes, and unknown fields are materialized; it can outlive the input and decoder.
The temporary lease is released on success and failure. Backend selection describes
schemaless `decode`, not a promise that `decodeTyped` runs inside Rust.

```java
try (var input = decoder.allocate(wire.length).put(wire)) {
  ProtoRow row = decoder.decodeTyped(input, plan);
}
ProtoRow text = plan.parseText("count: 150");
ProtoRow json = plan.parseJson("{\"count\":150}");
```

`ProtoPlan.decode(ByteBuffer)` also accepts heap, direct, sliced, and read-only buffers.
It consumes only the remaining region and preserves the caller's position and limit.
The caller must keep that region alive and immutable for the duration of the call.

Both text routes can use generated or runtime-compiled plans without retained descriptors
or temporary `DynamicMessage` objects. Text format uses protobuf-java's scalar escape
utility; ProtoJSON uses Gson's streaming token reader. Special JSON mappings for well-known
messages use schema-validated dispatch, including wrappers, Timestamp, Duration, FieldMask,
Struct, Value, ListValue and explicitly registered Any payloads. See [text contracts](CONTRACTS.md#descriptor-free-text-input)
for syntax, budgets, and compatibility limits.

For schema-directed ingestion, compile a reusable plan:

```java
try (var compiled = decoder.compile(plan, 64 << 10)) {
  SlabMessage binary = compiled.decode(wire, 1 << 20);
  SlabRow row = binary.row(); // DataLibrary and presence via row.has(ordinal)
  assert binary.snapshot() == binary;
  // binary and every nested row/array remain readable after this scope closes.
}
```

`decode(byte[], maxSlabBytes)` and `decode(WireInput, maxSlabBytes)` return a durable
`SlabMessage` from `protobuf-runtime`. It owns its payloads, allows concurrent reads,
and retains neither the caller's input nor the plan. Closing it is a no-op; snapshot
is identity. Child rows, arrays and strings materialize lazily and are cached.
String-length reads use validated UTF-16 metadata. Byte fields and repeated values
implement the durable `OwnedArray` contract; they are not necessarily `DataArray`.
`encode()` preserves unknown fields while normalizing known fields according to the
plan. The deprecated `decodeView` delegates to durable decode.

The SVM heap path pins input/output arrays only around a bounded native call. On the
JVM, explicit native heap decoding uses a bounded critical downcall. Both use a
64 KiB default bound for input length and output capacity; a `SlabLimits` argument
can reduce the bound, and zero disables it. Larger inputs/slabs use native scratch
and one output copy; native `WireInput` currently uses that scratch path as well.
Every native result is structurally validated before becoming a durable row.
The slab budget charges retained array capacity, including unused capacity:
`slabBytes()` reports its valid extent and `retainedSlabBytes()` reports storage.

`parseText` and `parseJson` continue to return scoped `TypedMessage` objects. Their
rows and arrays reject access after the message closes and are confined to the
creating thread; `snapshot()` detaches them into durable `ProtoRow` values. These
results share the `ProtoMessage` row/snapshot/encode interface with slabs. The native
text path uses the typed index; Java mode uses its bounded text parsers. Parse failures
never change the selected backend.

Native text supports ordinary proto2/proto3 messages, groups, enums, repeated fields,
maps and oneofs. Native ProtoJSON also specializes the well-known types listed above.
Timestamp/Duration use integer parsing; FieldMask writes converted paths into native payload
storage. Struct/Value/ListValue retain dynamic content beneath their fixed schema shapes.
Extensions and expanded Any syntax in protobuf text format remain unsupported. Input/depth/cell/string budgets apply before expansion. The native byte
budget covers index growth and conservative text scratch headroom; caller-owned schema,
input and decoded-payload regions have separate size bounds. Schema compilation has an
explicit byte limit and is intended to be reused across messages.

For Any JSON, explicitly allow payload plans at compilation:

```java
try (var compiled = decoder.compile(anyPlan, 64 << 10, List.of(timestampPlan))) {
  try (var message = compiled.parseJson(
      "{\"@type\":\"type.googleapis.com/google.protobuf.Timestamp\","
          + "\"value\":\"1970-01-01T00:00:01Z\"}", 1 << 20)) {
    ProtoRow owned = message.snapshot();
  }
}
```

Native Any parsing selects only registered schemas and produces a typed index. Java then
packs the validated payload with the bounded wire encoder. This packing allocates owned
bytes; it is not zero-copy. Binary Any byte fields still borrow their input. There is no
network lookup or automatic descriptor discovery. The Java-only equivalent is
`anyPlan.parseJson(json, List.of(timestampPlan))`.

Null wrapper fields are unset; Value null is a present null sentinel, including inside
lists and maps. A bare wrapper root cannot represent absence in a non-null row and rejects
JSON null; use an enclosing message when that distinction matters.

## Build and validation

The optional `//protobuf-buffa` target ships as `dev.elide:truffledata-protobuf-buffa:0.2.0`,
with generated bindings in `truffledata-protobuf-buffa-bindings`. The release includes Linux
x86-64 native attachments; see [publishing](PUBLISHING.md#buffa-native-distribution).
Builds use pinned Myna sources, Buffa crates, Rust toolchains and
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

The Linux amd64 JVM acceptance run measured 66,328 bytes of Java allocation per owned
typed 64 KiB payload (one payload copy plus row metadata), and about 1.4 KB per borrowed
64 KiB wire message, about 4.2 KB per native typed 64 KiB message, and 0.000064 bytes per compiled byte read, with a 17-node access AST.
These are allocation checks, not throughput claims. CI enforces a 16 KiB/message allocation
ceiling for both schemaless and native typed ingestion, an 80 KiB ceiling for owned typed ingestion of the same payload, 0.05 bytes/compiled read, a 192-node AST ceiling, and a 128-byte native index budget
for this single-payload case. JVM allocation counters are not used in SVM; SVM runs the same
semantic/lifetime corpus, compiled-read checks, native index budgets, and blocklist gates.

WKT acceptance compares generated and runtime contracts against protobuf-java, including
all wrappers, temporal limits, 200 seeded timestamps, nested Value/null cases and registered
Any payloads. Timestamp, Duration and Int64Value reads must execute compiled code within
the same allocation/AST budgets and reject access after close. On the Linux amd64 JVM,
measured Java allocations per native parsed message were 1,752 bytes for Timestamp versus
8,208 for descriptor-bound compatibility parsing, 1,624 versus 3,840 for Int64Value, and
2,352 versus 5,000 for FieldMask. CI caps each native case at 16 KiB. These figures exclude
native heap allocations and do not establish throughput gains.

Empty repeated-field views share one immutable view per decoded native message, including
its nested rows. This avoids a wrapper allocation for each empty field. Views from different
messages retain independent leases; closing a message invalidates all its empty views, while
detached snapshots remain readable.

Typed rows with at most 64 fields store protobuf presence in a primitive bit mask. Wider
schemas retain an indexed presence array. Explicit default values, oneof clearing, snapshots,
and bounds checks have the same semantics in both representations and in the Java fallback.

Native materialization reuses the plan validation of standard absent/default values instead
of validating them for every message. The shortcut requires exact equality with the standard
field contract; richer contracts retain value validation. Required-field, wire, UTF-8, depth,
and cumulative budget checks still run before a result is returned.

Repeated native fields materialize into privately owned arrays which transfer to the final
view after merges and validation. The decoder sizes the current contiguous run from the
native index; interleaved fields and later message merges grow storage geometrically, bounded
by index cardinality. Empty views still share storage only within their owning message.

## Development: fixed scalar native results

The unreleased ABI 4 adds a schema-directed path for one or two singular `int32`/`int64`
fields without oneofs or contract overrides, including binary Timestamp and Duration.
The native decoder reads borrowed input directly into a reusable 24-byte output slot.
It allocates no per-message native index and creates no Java tape wrapper. Presence,
required fields, declared defaults and unknown-field forwarding retain their usual semantics.
Unknown groups still obey the depth limit; recognized field occurrences consume the cell budget.

Both static SVM and JVM FFM use this path. The Java fallback remains available. The backend
owns the fixed output slot and Java scalar scratch until closure; each decoded message owns independent scalar values
and an input lease, so reusing or closing the decoder cannot invalidate a live message.
`nativeIndexBytes()` is zero for this path, and a zero per-message native-index budget is valid.
This excludes the fixed decoder/schema storage and caller-owned wire input. Rust tests instrument
the allocator to assert zero allocations during scalar decoding, and shared JVM/SVM acceptance
tests exercise lifetime, presence, unknown fields and zero-index-budget behavior.

ABI fingerprints prevent mixing these bindings with released native libraries. Publish the
bindings and native attachments together in the next release.

Typed messages now hold their own lease and invalidation assumption, avoiding a separate
lifetime-wrapper allocation. Construction completes once before any message or row is
exposed. Closing one message invalidates its rows but preserves sibling messages, including
messages borrowing the same input. Shared backend scalar scratch is copied into immutable
row fields before the next decode can reuse it.

## SIMD Unicode and Myna values foundation

Myna is pinned to the `0.18.0` release source archive (`myna-source-0.18.0.tar.gz`, SHA-256 `1085ddbc…8f3e`).
Its generator, Java value readers (`@myna//:values`) and C++ ABI headers
(`@myna//:values_headers`) are separate Bazel targets. The value ABI is v3; it is
independent of the Buffa seam ABI, now v7 with compact index entries
(v5 added Unicode conversion; v6 added reusable typed index storage).

An index entry is 24 bytes: field number/kind and payload length/subtree end are
32-bit, while the scalar value or source offset remains 64-bit. Input regions and
native allocation budgets are already limited to `Integer.MAX_VALUE`; therefore
lengths and entry counts remain Java-indexable. Native length conversion checks
this bound, and Java still checks nonnegative lengths and subtree extents. This
reduces entry storage by 25% without narrowing accepted inputs or scalar values.
Generated fingerprints reject libraries with the previous 32-byte layout.

Typed decoding reuses a backend-owned native index allocation between calls. Java
materialization completes before the index becomes reusable; returned messages retain
their own input leases and never borrow this scratch index. A warmed binary decode
whose index fits the retained capacity needs no index allocation. Text parsing can
still allocate token and escape scratch.

This trades fewer allocations for decoder-retained native memory. Each call enforces
its native budget, including text scratch headroom and overlapping index growth;
an incompatible retained capacity is discarded before parsing. Native parse failure,
generic decoding, and backend close release the index. Closing a typed message alone
does not release the backend cache. The `typedReuse` ABI consumes the previous result
even on failure and requires exclusive ownership of it; it is explicitly not a leaf
call. Fresh-output callers can continue using `typed`.

The native protobuf backend uses the same `simdutf` 0.7.0 Rust binding as Myna.
It validates text at 64 bytes and above with CPU dispatch and measures UTF-16
payload budgets with SIMD; shorter strings use Rust's UTF-8 validator. Malformed
UTF-8 is rejected, never repaired. SVM materializes strings of at least 256 UTF-8
bytes directly into a pinned Java `char[]` through the generated Myna C binding,
using an ordinary VM transition. Static consumers must link the C++ runtime
(`-lc++ -lc++abi` on Linux, `-lc++` on macOS, matching the pinned Elide LLVM toolchain).
The Bazel archive target propagates this through `CcInfo` to Native Image. Consumers
of the published raw archive must also supply those libraries, for example
`-H:NativeLinkerOption=-lc++ -H:NativeLinkerOption=-lc++abi` on Linux. The release
consumer test imports the published archive with that explicit link contract. No temporary UTF-8 Java array is needed for that
route. This conservative crossover matches Myna and is not a universal speedup
claim. JVM materialization and native-disabled images keep their JDK path.

Native string reads depend on the retained input lease, not a live decoder.
Borrowed views still invalidate on message close; snapshots own their contents.
Rust tests cover malformed text and caller-buffer extents; the shared JVM/SVM
acceptance corpus covers ASCII, NUL, Latin, CJK and supplementary characters around
both dispatch thresholds, eager/lazy reads, input closure and detachment.

The simdjson adapter is a separate general-JSON integration. Myna's value ABI is
its foundation; ProtoJSON mappings remain in the protobuf backend. This change
does not implement the general-JSON parser or the JEP 540 facade.

Scoped rows and arrays must be detached before entering `Batch`, `DataArray`,
`StaticLayout`, or an owned protobuf builder. `Values.validate` can check scoped
RECORD/ANY fields during guest execution, while `Values.normalize` and
`Values.requireInterop` reject ownership transfer. Use the enclosing message's
`snapshot()` to retain values independently of message closure. Shared JVM/SVM
tests cover both top-level and nested borrowed rows, rejection by owned containers,
and detached nested values surviving all source owners.

Protobuf text/ProtoJSON entry points reject unpaired Java UTF-16 surrogates before
UTF-8 encoding can replace them. They also reject negative native budgets and
check the exact UTF-8 expansion before allocating input/payload buffers. This is
separate from the future general-JSON facade's Java UTF-16 factory semantics.


### Myna 0.8.0 / binding ABI 2

Both protobuf and JSON regenerate their bindings with explicit native call policies.
Only the fingerprint-address functions are short bounded leaves. Decode, typed
parsing, scalar parsing, UTF conversion, and release explicitly retain normal VM
transitions (`native_leaf=false@explicit_contract`), including their allocation and
cleanup paths. Value-tape ABI v3 and the application protocol versions are unchanged;
the binding fingerprints change, so native attachments and Java bindings must be
rebuilt and distributed together.

Generated bindings compile against the pinned Graal SDK's `svm-shared.jar`,
`svm-guest-staging.jar`, and `svm-guest.jar` through a Bazel `neverlink` import. These
compiler-private jars are not runtime dependencies or bundled classes. Generated
`META-INF/native-image` metadata is packaged in each bindings JAR, preserving Myna's
builder exports and symbolic/build-time versus address/runtime initialization rules.
`//bazel:myna_contract` checks the generated policies and packaged metadata;
JVM acceptance checks that compiler-private classes are absent from the application
classpath. Actual static SVM, native-free fallback, and published-artifact acceptance
exercise the rebuilt consumers.

### Standard contract proofs

Typed native decoding establishes standard protobuf field types, widths, child
types, and repeated-element representations while consuming the schema-directed
index. Finalization reuses those facts when the schema field exactly matches its
FieldPlan contract, instead of walking completed arrays again. Richer contracts
retain the general validator. Required fields, duplicate rules, budgets and owner
checks are independent of this shortcut.

`-Dtruffledata.strict=true` revalidates these completed values, including resolving
deferred strings, to audit the native/Java cooperation. Strict mode may therefore
materialize strings earlier. The acceptance corpus runs with strict mode both on
and off; performance measurements use the shipping, non-strict path. For SVM, set
the property at image build time with the other strict-test options.

## Seam ABI 12: layout-compiled slabs

ABI 12 (over ABI 11) changes only the `SlabResult` structure: its reserved word becomes the
decoded slab's logical totals (`cells u32 · string_bytes u32`, `0xFFFFFFFF` when unknown; see
[SLAB.md](SLAB.md)). The native backends publish a slab their own decoder wrote into an array they
allocated and never exposed through `SlabMessage.produced`: a `CERTIFIED` owning slab whose totals
lie within the plan's limits skips the Java `SlabVerifier.verifyOwning` walk and the exact
accounting walk, keeping the O(1) header check; anything else takes the previous checked path with
unchanged messages. External, copied (`copyOf`, `copyRecordOf`, session `retain`), projected and
`takeOwnership` slabs keep the full verifier. The slab bytes and goldens are unchanged.

Binary `BuffaPlan.decode` uses the owning slab representation. `newSession` explicitly
selects a reusable native arena, and `project` binds selected fields and deferred children.
The deprecated Java `decodeView` spelling delegates to durable owning decode; it does not
retain a native field-directory view.

ABI 11 removes the earlier `View`/`Scalars` result structures, compiled-view imports,
scalar-only producer, and the binary format-3 typed-index path. The Java `NativeRow` and
`ScalarRow` readers and their retained directory/index allocations are gone. Ordinary
wire scanning and typed text/ProtoJSON decoding still use the bounded typed-index scratch;
those are separate APIs. Text results materialize `TypedRow.ArrayRow` values before releasing
the scratch, and their string/bytes views retain their input lease.

The `TDS1` slab and `TDP1` projection formats remain unchanged. `compileLayout`/
`releaseLayout` compile a schema program's `TDB3` tables and `TDL1` section once, recomputing
the layout and rejecting disagreement. `decodeSlab` writes one message into caller-provided
storage and reports its exact required length (`NEEDS_CAPACITY = 6`); `decodeSlabSmall` is
its bounded native leaf (input and capacity at most 64 KiB, no heap allocation or lock).
Bindings, native attachments and the Java JAR must be upgraded together because the seam
fingerprint changes with removed exports. See [SLAB.md](SLAB.md) for the wire layout and
projection contract.

Rust tests retain byte-exact golden slabs, an 8,000-input differential against the independent
typed-wire decoder, capacity retries, budgets, UTF-8 rejection offsets, merges, oneofs, maps,
unknown fields, depth, allocation and failure injection. `NativeTapeAcceptance` verifies that
durable slab rows survive typed-index scratch reuse and decoder closure. Historical view
measurements remain available in [the protobuf results](../benchmarks/results/protobuf-f26678a/README.md);
current comparison cells exercise slabs and sessions.

### Reusable native slab sessions

`BuffaPlan.newSession(maxSlabBytes)` creates an explicit, thread-confined native arena. `session.decode(input)` returns a `BorrowedSlabMessage`; decoding again or calling `reset()` invalidates all previous root, child and array borrows. A failed decode also invalidates the previous result. `retain()` copies the valid slab into a durable `SlabMessage`, and child/array `snapshot()` preserves unknown fields and ownership without reparsing wire bytes. Closing the source `BuffaPlan` does not release a session's separately compiled layout.

`message.pin()` returns an `AutoCloseable` guard. While pinned, decode, reset, message close and session close throw before changing the current result. Reads and pin release must occur on the session thread. Closing an already expired message cannot invalidate a newer result. Owning containers reject borrowed rows and arrays until explicitly retained or snapshotted.

Sessions require a native backend; Java-only consumers use the durable slab methods. The arena reuses the caller-owned slab output buffer introduced in ABI 10, rather than adding a second Rust session allocator or new ABI symbols. The ordinary producer writes owning payloads into that arena. `arenaBytes()` reports retained native capacity, which stays bounded by `maxSlabBytes`; `slabBytes()` reports the current valid extent. Projected sessions can retain immutable source input for lazy children or large bytes; `retainedInputBytes()` reports that capacity separately. Deferred child slabs share the session's remaining retained-memory and logical budgets.


### Selected fields and deferred children

Bind `compiled.project(Projection.builder(contract).select("id").build())` to decode
only the selected values. Presence and repeated-field counts remain available; omitted
value reads fail explicitly. `.lazyChildren()` allows strict decoding of an omitted child
on first access, and `.borrowBytes()` avoids copying selected bytes payloads of at least
4 KiB into the slab. Both preserve immutable source input. `.lax()` explicitly defers UTF-8
validation in omitted subtrees; malformed wire structure is always rejected.

Owning `decode` returns a durable projected `SlabMessage` on both Java and native backends.
A projected `newSession` returns scoped roots and children: first access to a lazy child
returns `BorrowedMaterializedRow`, whose `snapshot()` is durable. Lazy grandchildren and
arrays remain scoped too. Reset or the next decode invalidates them even if their bytes
were already materialized. Pinning and root/child retention follow the ordinary session
rules. Full format details and conservative eager fallbacks are in [SLAB.md](SLAB.md).

## Myna Truffle cells (`truffledata.buffa.cells`)

`-Dtruffledata.buffa.cells=true` routes every slab producer call (`td_buffa_decode_slab`, and
`td_buffa_decode_slab_small` on the JVM's bounded heap path) through Truffle cells that Myna
generates from this module's seam, instead of the hand-written `@CFunction`/FFM calls. It covers
`BuffaPlan.decodeSlab`/`decode` over heap and native input and the session/projection arena
writes. The route is **off by default** and is retained as support for Myna cells, not as a speed
option: on the measured x86-64 consumer cells it is slower today (see the WS12 results linked
below). Everything else — budgets, the learned-capacity retry, the critical-size scratch fallback,
extent checks, trimming, the trusted publication through `SlabMessage.produced`, error types,
messages and order, ownership and lifetimes — is the backend's own; the slabs are byte-identical.

- **Artifact.** The route lives in the separate `dev.elide:truffledata-protobuf-buffa-cells`
  (Bazel `//protobuf-buffa/cells`), which carries the generated cells and depends on
  `truffledata-cells`, where the `myna-truffle` runtime ships once for every cells route
  ([PUBLISHING.md](PUBLISHING.md#optional-truffle-cells-routes)). Nothing in `truffledata-protobuf-buffa` depends on it; with the flag off it need not be
  on the class path, and if it is, nothing in it is reachable.
- **Native Image.** The property is read **at image build time** by `BuffaFeature`, which registers
  the cells backend factory instead of the static one. Pass it to `native-image` (and put the cells
  artifact on the image class path). An image built without it contains no cells code; setting the
  property when running an image has no effect.
- **JVM.** The property is read once, when the backend selector initializes (a `static final`), and
  picks the backend when `BuffaWire.open` loads a library; no decode path tests it. The JVM cells
  use Myna's FFM cell backend, which binds through the class loader's lookup, so the cells backend
  also `System.load`s the (fingerprint-checked) library, and only one library path per class loader.
- **Seam and fingerprint.** The cells are generated from seams *derived* in the build from
  `native/buffa.seam` by `bazel/cells_seam.py`: the module options change (`truffle_cells=true`,
  `truffle_backend` `native`/`ffm`, `truffle_transitions=direct`, and the Java package/class),
  and `cells/ownership.seam` adds the Myna ownership declarations below. The production seam,
  bindings, ABI fingerprint and natives are unchanged. Myna folds the cells options and the
  ownership into the derived bindings' own fingerprint constant, which is never compared with the
  library; the backends keep checking the library against the production fingerprint of the ABI
  class they run on (`BuffaAbi`; Myna 0.14.0 gives Linux AArch64 its own, see
  [PUBLISHING.md](PUBLISHING.md#prebuilt-natives)), and `//bazel:myna_contract` checks that each
  derived descriptor equals the production descriptor apart from those options and the ownership
  delta `cells_seam.py` allows.
- **Shape.** The backends are host code, compiled ahead of time on Native Image and called below
  the decode entries' `@TruffleBoundary`, so they use each generated cell's static `invoke` entry
  (Myna 0.16.0): native carriers and raw `long` addresses, with no cell instance, argument array,
  box, `NativePointer` or `Borrow`. `invoke` coerces and borrows nothing, so the caller keeps every
  address valid for the call: heap arrays are pinned around it with plain `try/finally` (javac's
  try-with-resources reaches `Throwable.addSuppressed`, which the runtime-compilation blocklist
  rejects), and native inputs, arenas and the result slot stay held by their leases and the
  backend. On Native Image every entry calls `decodeSlab_Cell.invoke`, the same native entry the
  static backend calls (it never uses the leaf `decodeSlabSmall`), so the two routes differ only
  in which generated binding they call; in the symbol-bearing flag-on consumer image `invoke` is
  inlined into both `slab` overloads and `writeSlab`, which make the native call themselves and
  call no `execute` symbol (`//benchmarks/comparison:consumer_cells_call_shape`). On HotSpot the entries whose buffers are all native
  memory (native input, the session and projection arena writes) call `decodeSlab_Cell.invoke`
  after the region scope check below; the bounded heap-input entry keeps the concrete
  `decodeSlabSmall_Cell` and its typed `executeInt` (no result box, no
  `UnexpectedResultException`), because `invoke` takes only addresses and a HotSpot heap array has
  none: Myna's borrow (`HeapBytes`) copies it to scratch memory and the output back, where the FFM
  backend lends it in place. `//protobuf-buffa/cells/tests:call_shape` scans every
  `<format>/cells` package: Native Image routes must call `invoke` and no `execute` method, HotSpot
  routes may call `invoke` or a typed `execute*`, and a boxed cast or unboxing of a cell result or
  an `UnexpectedResultException` handler fails everywhere.
- **Per-call cost.** `CellsAcceptance` measures the Java bytes per `writeSlab` call of the static
  backend and the cells route in the Native Image and holds the difference to 0 B, with the same
  input and arena on every call and with both alternating. Measured through `invoke`: 0 B per call on both routes in both shapes (the static backend 0 B,
  the cells route 0 B).
  Through `executeInt(Object[])` on Myna 0.14.0 the route allocated 144 B per call (the three
  `Borrow` objects of the byte-pointer parameters, with a reused argument array and cached
  pointers) and 176 B when the buffers alternated (plus a `NativePointer` per new address). On
  HotSpot the shared acceptance's session decode measured 0 B/message on the cells route through
  `invoke` (0 B with the flag off as well), against 240 B through `executeInt` on Myna 0.14.0 and
  528 B on Myna 0.16.0; its flag-on ceiling is 0.1 B/message and the default route keeps its 48 B
  contract. These are allocation counts, not timings. Upstream: elide-dev/myna#85 (per-call
  allocation of `execute`), #86 (direct transitions not faster than one boundary call on
  sub-microsecond imports) and #87.
- **Region scope on HotSpot.** The FFM backend hands `MemorySegment`s to the downcall, which
  refuses one whose scope is closed or confined to another thread. `invoke` takes a raw address, so
  the cells backend makes that check itself before every call, with the downcall's exceptions in
  the downcall's order; without it a region closed behind an adopted input (`BuffaJvm.adopt`)
  reached the producer as a dangling pointer. One difference remains and Myna forces it: the
  downcall also keeps a shared scope open for the duration of the call, and `invoke`, which tracks
  no caller's scope, cannot, so a shared arena closed by another thread while the producer runs is not rejected on
  this route. Native Image inputs are raw addresses on both routes.
- **Ownership declarations (Myna 0.18.0).** `cells/ownership.seam` declares, for guest code
  calling the cells through `execute`, what Myna 0.18.0 can express of this seam, each contract
  checked against `native/` (the overlay's comments cite the code):
  - **`Layout`**, an opaque resource: `compileLayout` is its create, reporting status 0 and
    writing the handle into the result struct's `data` (`handle=result.data success=0`; any other
    status leaves the struct zeroed and nothing to release), and `releaseLayout` its destroy. The
    guest gets a `NativeResource` that closes once (a second close or destroy is a no-op, as Myna
    defines it) and a failing compile raises `NativeStatusException` with the native status. The
    production seam types the handle as `ptr<u8>` inside the `Result` struct that `decode` and
    `typed` share, so the cells seam types it on `LayoutResult`, a copy of `Result` with the same
    size, fields and offsets, and as `ptr<Layout>` on `releaseLayout` (declared non-null, which the
    owner never passes) and on `decodeSlab`/`decodeSlabSmall`'s `compiled`, so a guest passes the
    owner there and a closed one is refused before native code runs. Each is the same C pointer.
  - **`Result`**, initialized in place: `decode` fills the runtime's struct (status 0) and
    `release` frees its field index from the same address. `typed`/`typedReuse` fill the same
    struct but are not declared as creates: a struct is one resource and `release` can be the
    destroy of only one. Their `result` stays a pass-through pointer.
  - **Call borrows** on every byte buffer, whose length the cell derives (the guest-visible arity
    drops it): `compileLayout` `schema`; `decode` `data`; `typed`/`typedReuse` `schema`, `data`
    and `payload`; `utf8_to_utf16` `data`; `decodeSlab`/`decodeSlabSmall` `data` and the nullable
    `out` (absent means null with capacity 0, which the native accepts). All are `nocapture`: the
    decoders keep input offsets, never addresses, and `slab::Compiled` owns its tables.
  - **Not declared**: `utf8_to_utf16`'s `output` (a `ptr<u16>`, not a byte buffer) and
    `decodeSlab`'s `projection` (a TDP1 mask whose extent is in its own header, with no length
    parameter) stay pass-through pointers. Every import is `nounwind`: each is a Rust `extern "C"`
    function, so an unwind reaching the ABI aborts, and the bodies run under `catch_unwind`.

  The routes do not use any of this: they call `invoke` with raw addresses, which ownership leaves
  unchanged (Myna 0.18.0 gives a resource's destroy cell no `invoke`, and no route calls one), so
  layout and result-slot lifetimes stay with their existing owners: `BuffaPlan` and `SlabSession`
  release their layouts, and the result slot is busy-checked. The HotSpot heap entry's typed
  `decodeSlabSmall` call now passes eight arguments, its two lengths derived from the
  `HeapBytes`. `CellsRouteTest` exercises the declarations as a guest would.

Tests: `//protobuf-buffa/cells/tests:jvm` (the shared acceptance suite with the flag on, the route
check and a byte/error parity run of every slab entry against the FFM backend, including depth
limits and adopted regions whose scope is closed or owned by another thread, and the guest calls
of the ownership declarations: `Layout` and in-place `Result` owners closed once and refused after
close, borrows from heap bytes and `NativeBytes`, a closed scope refused, a failing create's
status, each result equal to the FFM backend's),
`:jvm_off` (artifact present, flag off), `:call_shape` (the call-shape scan),
`:native_test` (Native Image built with the flag: the shared native acceptance, the route check,
parity against the static backend and the per-call allocation ceilings, with the blocklist
checks), `//benchmarks/comparison:consumer_cells_call_shape` (the symbol-bearing flag-on consumer
image: the cells backend reaches the producer through `decodeSlab_Cell.invoke`, or has it inlined
and calls the native binding directly, and calls no `_Cell_execute*` symbol), and `//benchmarks/comparison:consumer_native_smoke_cells` (the 132-cell
consumer smoke matrix on the cells route).
