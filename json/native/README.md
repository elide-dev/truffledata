# Native JSON decoder foundation

The initial C++ decoder traverses simdjson On-Demand directly into Myna ABI v3,
without an intermediate DOM. It accepts all root kinds, rejects decoded duplicate
keys, validates number grammar without restricting numeric range, and retains raw
string/key spellings alongside the index. Every field is visited before publication.

Each document currently copies input into a padded allocation and retains its own
parser. Long decoded strings borrow that parser's scratch; numeric tokens borrow
the retained input. The document cannot be copied or reparsed. This establishes
correct retained-region lifetimes before adding parser pooling or adopted inputs.
Returned descriptors and lexical views are valid only while the document lives.

Run `bazel test //json/native:test //json/native:api_test //json:bindings`. Tests check malformed values, trailing input,
escaped duplicate keys, exact huge exponents, decoded Unicode, raw lexical text,
independent documents, container offsets, and node/text/depth limits. Allocation
checks inject failure at every index allocation and verify cleanup, including
constructor failure and invalid input.

`storage_limits` caps logical input bytes before copying and index allocations
before requesting memory. Myna nodes, regions, lexical spans, traversal storage,
and duplicate-key sets share the index budget. Reallocation overlap contributes
to the peak. Statistics distinguish copied/padded input, retained/peak requested
index bytes, index allocation count, and the parser's public input capacity.
These counts exclude allocator metadata. The parser owns its scratch allocations;
its input capacity is explicitly not presented as scratch bytes.

Run the native memory checks under both sanitizers with:

```sh
bazel test //json/native:test //json/native:api_test --copt=-fsanitize=address,undefined \
  --copt=-fno-sanitize-recover=all --linkopt=-fsanitize=address,undefined
```

Both the adapter and simdjson are instrumented by this invocation.

This is an internal foundation, not yet a published Java API. Remaining work includes
parser scratch accounting,
native input leases, actual JVM/SVM consumer qualification, scoped
views and snapshots, typed fixed slots, and compiled access/allocation gates.
Myna's current node/text/depth limits do not constitute a total native-memory budget.


## Native interface

`json.seam` defines the versioned C layout and generates JVM FFM and direct SVM
bindings. `api.h` documents status codes and caller obligations. Decode publishes
an owning `JsonResult` only after validation, including a pointer to the immutable
Myna descriptor. Reads need no further native calls. The caller must validate the
binding fingerprint and Myna view version before reading their layouts.

Initialize results to zero, never copy their ownership, and release each result on
the same storage. Releasing that storage again is harmless. A live result cannot
be overwritten by another decode. Input can be discarded after decode returns;
the result retains its own padded copy, index, and parser scratch. Returned raw
views are invalid after release. Thread confinement and invalidation checks belong
to the forthcoming Java owner, not the raw C pointer interface.

The ABI currently accepts a maximum requested nesting depth of 128. Failures map
to argument, limit, allocation, syntax, or internal-error statuses; exceptions do
not cross C. Statistics additionally report the fixed control allocation separately
from input and index storage. Parser scratch remains excluded from byte totals.
Generated bindings compile, and native ownership/error tests pass under ASan/UBSan;
actual Java-to-native execution is the next qualification step.
