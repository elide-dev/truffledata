# YAML comparison

The comparison uses the pinned GraalVM 25.5.5-elide.27 APIs and native compiler, Myna 0.13.0 bindings, SnakeYAML Engine 3.2, SnakeYAML 2.7 and Jackson YAML 2.22.4. Competitors are benchmark dependencies only. `yaml_jvm` runs our FFM implementation and all four JVM competitors. `yaml_svm` statically links our YAML and JSON implementations, without JVM competitor libraries.

## Fixtures and work

`YamlFixture` derives every record from `JsonFixture.document(i)`. Each record contains nine members, a nested object, two arrays, short and long text, escaped Unicode, integer and floating-point numbers, a boolean and null. Odd records reverse member order. The same ten reads and checksum apply to all tree, interop, typed and snapshot cells.

| Workload | Input | Records per operation |
|---|---|---:|
| `yaml-config` | 3,800 UTF-8 bytes, block YAML with a trailing comment | 8 |
| `yaml-records` | Block record sequence | 1,000 |
| `yaml-flow` | Equivalent JSON-compatible flow sequence | 1,000 |
| `yaml-stream` | Explicit `---` document boundaries, one block record per document | 1,000 |

The three single-document fixtures put their sequence in a `rows` mapping member because the typed API requires a root record. Every engine receives exactly the same bytes, including that wrapper and the config comment. This is a recorded change from the proposed bare sequence fixture. The typed stream cell is absent: `YamlPlan` has no typed stream operation. Pre-splitting input would measure a different operation. Native C++ reference rows use the separate checked-in native fixtures and must not be substituted for these byte counts.

Our Java/Truffle tree cells use one session and key cache per measuring process. They parse directly through the corresponding sink. The interop cell decodes a retained owner, reads the records and closes it. The snapshot cell copies the tape into a durable heap snapshot before reading. Typed decode includes its independent result ownership and close. Streaming consumes the whole input buffer and all returned documents per operation. Setup, plan compilation and fixture generation occur outside timed regions.

SnakeYAML Engine load uses YAML 1.2 core resolution and rejects duplicate keys. SnakeYAML uses its safe YAML 1.1 loader. These fixtures avoid schema-dependent scalar spellings. Jackson builds trees. Engine events counts every stream/document/container/key/value event against an independently calculated event count; it does not build a tree or perform the ten member reads, and is a scanner reference rather than an equivalent materialization workload.

`truffledata-json-interop` runs only on the flow fixture in the same static image, with identical bytes and reads, to test the proposed YAML overhead of at most 20% relative to JSON.

## Measurement

Run `bazel test //benchmarks/comparison:yaml_measure`. The runner randomizes the order of three fresh-process forks per cell, fixes the Java heap and serial collector, warms each operation for three seconds, and records three samples of at least 200 milliseconds per fork. It checks every result and calibrates the allocation counter. The report uses the median of fork medians and retains raw samples, fixture sizes, checksums, runtime, binary/library hashes and machine details in Bazel's undeclared outputs. `YAML_FORKS=1` is available for a qualification pass and is labeled in the report; it does not replace the three-fork comparison.

Times are per whole operation, with input MB/s and Java bytes per logical record also reported. Java allocation excludes native arenas and native result storage. JVM and Native Image results are separate rows; an across-runtime ratio is not evidence about parser code alone. Concurrent shared-worker load can distort samples. Do not present smoke timings as performance results.

The planned hypotheses remain: native decode at least 0.8 times the paired rapidyaml integer scanner; YAML JavaSink ingestion at least five times Engine load on the records fixture; YAML flow interop at most 1.2 times JSON flow interop. The native compact regression floor is separately enforced by `//yaml/native:compact_bench`. A hypothesis miss must retain its samples and diagnosis.

## Compiled smoke gates

`bazel test //benchmarks/comparison:yaml_native_smoke` checks all 20 ingestion cells (including the JSON reference), retained reads, export and closure invalidation. The 32 retained documents use the same logical fixtures as the JSON graph gate.

| Root | Maximum graph nodes | Loops | Unresolved calls | Graph allocations |
|---|---:|---:|---:|---:|
| `GenericReads[truffledata-yaml-interop]` | 2,000 | 1 | 0 | 0 |
| `GenericReads[truffledata-yaml-export]` | 1,000 | 2 | 0 | 0 |

Each root must execute compiled guest code, stay within 192 AST nodes and allocate at most 1.0 Java byte per document, including call-result boxing. Closing one owner must make its read fail while a sibling remains valid. The dedicated borrowed TruffleSink cell has a predeclared 2,400 Java bytes/document budget on the same 32 records, covering fresh host collections and the shared text arena after warmup. Larger wrapper and stream workloads report their allocations separately. The YAML API tests additionally gate individual compiled reads at 0.05 bytes/read, including typed, alias and array paths.

JVM FFM ingestion is measured; the retained graph qualification is a Native Image gate. A direct JVM FFM attempt retains VarHandle segment calls and is not represented as passing the static-image graph contract.

## Qualification status

The first direct JVM check matched all 35 ingestion checksums across our five applicable modes and four competitors on all four workloads. Full Native Image smoke, three-fork measurements, hypothesis ratios and CodSpeed simulation qualification are pending. No throughput claim is made from that smoke run.
