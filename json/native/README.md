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
independent documents, container offsets, and node/text/depth limits.

This is an internal foundation, not yet a published Java API. Remaining work includes
bounded allocation accounting (including parser scratch and duplicate-key storage),
native input leases, a stable C ABI and generated Myna bindings, JVM/SVM scoped
views and snapshots, typed fixed slots, and compiled access/allocation gates.
Myna's current node/text/depth limits do not constitute a total native-memory budget.
