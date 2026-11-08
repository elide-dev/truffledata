# JSON producer and image qualification

The complete fresh-process runs compare the exact `ce7399f` compatibility image with the followup candidate compatibility and x86-64-v3 images. All jobs retain the existing checksum/tier assertions, three forks, allocation accounting and metric definitions; they ran sequentially on unclemax CPUs 2–9. Two full baseline compatibility runs bracket the candidate comparison. `pairs.md` contains every common cell; each report records its image hash. These measurements precede the ABI 10 stream directory fix and snapshot copy optimization, which require new image measurements.

## Pinned native probes

The four `*.bazel.log` files are the exact Bazel-built executables from invocation `b421e652-0695-4e45-aa09-34f336994916`, using their matching `libtape`, simdjson and simdutf shared libraries. They ran sequentially on the same unclemax CPU set. Retained stage2 ratios over the four exact fixtures are 1.346/1.426/1.383/1.377×; validation ratios are 1.486/1.660/1.610/1.583×. Directoryless big/huge ratios are both 1.055×, passing the 1.04× requirement. Retained big/huge miss the original 1.4× threshold; validation small/huge miss 1.6×. The other retained/validation fixture gates pass. A fresh S3n/S3x duplicate-price measurement remains pending.

Streams pass the unchanged 1.45× gate in all five rounds: 1.499/1.505/1.513/1.513/1.517×, at 1.216–1.227 GB/s. Typed flat scalar plans pass 1.1×: 1.882/1.703/1.692/1.734/1.681×. Each ISA candidate passes 30,280 exact-tape/malformed comparisons, but AVX2 gains only 0.1–2.2% and AVX-512 regresses. Both remain test-only under the 3% adoption rule.

The earlier `host-clang-native.log` uses host Clang 23 without the pinned toolchain/ThinLTO. Its stream miss is diagnostic; the pinned run above supplies the qualification result. Both raw logs remain available.

## Image measurements and remaining misses

The compatibility candidate reduces typed ingest to 144.919 ns from 177.763/185.170 ns and compiled ten-field reads to 27.124 ns from 91.301/90.821 ns. Generic ingest is 820.176 ns (baseline 826.653/791.002), JavaSink peak export 1182.964 ns (945.081/982.931), TruffleSink ingest 1902.973 ns (1706.550/1799.850), stream interop ingest 1289.081 ns (658.415/666.690), and snapshot ingest 2059.874 ns (1471.700/1562.995). These latter absolute targets remain missed; there is no all-green image claim.

The candidate allocation cells pass the corresponding compact goals: generic ingest 384 B includes 240 B of the ten-read harness, stream ingest 304.75 B includes that same 240 B, and snapshots use 1183.5 B. The native logical 1.5× memory criterion remains **not met (format constraint)** under the exact unchanged layout/fixtures. Physical reservations do not change the lower-bound proof. Myna upstream requests are prepared drafts, not filed issues.

## Post-measurement fixes awaiting image qualification

ABI 10 adds per-document stream directory intent: borrowed roots request directories, while sink events keep directoryless production. Speculative structural extents may use the directory path only when its directory capacity is already sufficient. Native sanitizer coverage alternates both modes for 200 documents and verifies exact standalone tapes; unknown flags do not consume the next document. Shared session backends retain the old overload as a compatible default.

Heap snapshots now copy native text with checked word loads, JVM segments with bulk copies, and heap ranges with arraycopy. They load each header once in their two range/relocation passes, preserve exact subtree text and YAML WIDE lexical spans, and allocate no additional buffer. Local DSL compilation, 21 tape tests, 62 JSON JVM tests and native stream/API ASan+UBSan pass. Native image acceptance, unchanged graph gates and timing remeasurement remain required.
