# Shape-cache qualification

RBE same-process probe, invocation [a7505c96](https://app.buildbuddy.io/invocation/a7505c96-3cd6-4664-9f55-7e54ac622409), before stage-2 replacement. Baseline is the pinned pre-cutover On-Demand visitor and validate-only writer; the current compact writer runs with its cache disabled for the retained baseline. The candidate uses the same walk with a session-persistent cache. Five rounds, minimum nanoseconds per document; same padded input, parser and arena reuse.

| Input | Retained ratio | Validate ratio |
|---|---:|---:|
| 4,330 bytes | 1.062 | 1.180 |
| 228,485 bytes | 1.068 | 1.176 |
| 4,850,894 bytes | 1.064 | 1.168 |

Task 1’s RBE gates pass: retained ≥1.05× and validate ≥1.15× on big/huge. The small validate baseline was anomalously slow (1,901ns), so that ratio is not evidence. Native Image/allocation, sanitizers and repository-wide gates are separate. These results do not satisfy the later stage-2 or unclemax throughput gates.

The cache is 43,144 bytes (`sizeof(key_shape_cache)`) on the measured 64-bit builds, including active frames. The plan’s 12KB estimate is too small: the requested 32 depths ×32 keys ×32 bytes alone requires32KiB before lengths, hashes, ranks and frame state. Sessions retain one cache; one-shot documents use a temporary stack cache during construction and retain only the compact tape.

On local Apple Silicon, the same SIMD-comparison candidate measured retained1.043×/1.055× and validate1.034×/1.032× on big/huge. Those are diagnostics, not substitutes for the RBE or unclemax gates.

Reproduction: compile `shape_probe.cc` with the repository’s pinned C++ toolchain, repository root, Myna include and simdjson singleheader include directories; link simdjson.cpp. For the RBE lane, use a temporary cc_test with these two probe sources and dependencies `//json/native:decoder` and `//tape/native:testing`, then remove that target after collecting its test log.
