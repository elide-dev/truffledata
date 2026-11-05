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

Run `bazel test //json/native:test`. Tests check malformed values, trailing input,
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
bazel test //json/native:test --copt=-fsanitize=address,undefined \
  --copt=-fno-sanitize-recover=all --linkopt=-fsanitize=address,undefined
```

Both the adapter and simdjson are instrumented by this invocation.

This is an internal foundation, not yet a published Java API. Remaining work includes
parser scratch accounting,
native input leases, a stable C ABI and generated Myna bindings, JVM/SVM scoped
views and snapshots, typed fixed slots, and compiled access/allocation gates.
Myna's current node/text/depth limits do not constitute a total native-memory budget.
