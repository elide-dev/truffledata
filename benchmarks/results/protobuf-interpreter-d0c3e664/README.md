# B4 interpreter diagnosis before inline-reader prototype

Diagnostic invocation: <https://app.buildbuddy.io/invocation/d0c3e664-9f56-466e-8460-4443a1e231e9>.
The `consumer_interpreter_profile` image samples only after warmup. It is a monitoring-enabled
ordinary SVM build, with no profile-guided optimization. Its timings are diagnostic and do not
enter the three-fork comparison/gate reports. Full raw JFR, binary hash, compressed AOT symbols,
selected read-method sizes and the JFR summarizer output are retained here.

Each TruffleData cell has about 3,000 application CPU samples. `Accessor.bound` is the top
leaf (19.6% generated-owned, 19.5% slab); `ReadLongNode.accessed` accounts for another
9.4% / 9.7%, and cached array-element interop for 10.2% each. Generated accessor `readLong`
adds 8.7%. On slabs, value dispatch, `getInt` and cached child-row lookup together contribute
16.4%. These are sampling associations, not a precise attribution of each method's exclusive
wall time. Stack truncation/inlining can move a sample to its visible caller.

The ordinary cached `ReadLongNodeGen.execute` AOT body is 1,596 bytes, boolean 1,581 bytes,
and length 3,110 bytes; specialization routines add 2,539, 2,535 and 2,859 bytes respectively.
The guard helper survives as 81/132-byte symbols. The source path is generated-node cache
lookup → `Accessor.bound`/layout check → specialization helper → accessor/layout recheck →
representation getter or slot load. Repeated fields run five such cached readers inside
`FieldAudit`. The proposed next experiment keeps existing cached public APIs while permitting
DSL-inline storage/execution of those readers in their parent node, and checks unchanged
correctness, allocation, graph and AST requirements before comparing performance.

The stock host-interop profile is dominated by real reflection/host dispatch; it confirms that
this control measures its advertised host calls. It does not replace the independent stock
boundary used by the <=2.5× interpreter target. No B4 gate is marked passed by these profiles.
