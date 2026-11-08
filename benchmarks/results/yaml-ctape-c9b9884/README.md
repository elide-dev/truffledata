# Compact YAML follow-up profile

Invocation `71870516-cb39-40e2-b2a7-b1a2d23dfe5f` samples the c9b9884 YAML
producer on Linux x86-64 RBE with the pinned toolchain, ThinLTO and
`--copt=-gline-tables-only`. The shared compact writer also includes the recorded
`shared-writer.patch`; YAML keeps directories enabled. SHA-256 hashes identify the
source headers and exact downloaded profiling executable.

Each cell runs for five CPU seconds at 250 samples/second. These short profiles
identify large categories; small differences of a few samples are not reliable.
The sampling run is not the throughput acceptance gate. Records-block completes
3,922 candidate decodes and 6,258 rapidyaml integer-handler scans. Raw load-relative
stacks are gzip-compressed under `profiles/`; symbolization requires the matching
binary. `yaml-decoder-20e3d44/profiles/fold.py` produced the folded stack and summary.

Using the original classifier, samples per 1,000 candidate decodes are:

| Category | Earlier Myna producer (20e3d44) | Current compact producer |
| --- | ---: | ---: |
| rapidyaml engine and remainder | 216.04 | 210.35 |
| handler dispatch, frames and collections | 43.86 | 54.56 |
| tape writes | 8.86 | 14.53 |
| metadata and array operations | 9.91 | 3.06 |
| scalar resolution | 8.86 | 12.49 |
| lexical spans | 14.93 | 14.53 |
| input validation | 6.47 | 7.39 |

The metadata change removes most of its earlier cost. Remaining overhead is
spread across the handler and writer; no safety check or acceptance floor changed.

Local macOS experiments remain **unpromoted**. The paired-library harness loads
independent dylibs built with Homebrew clang 23.1.1, `-O2 -DNDEBUG` and
`-fvisibility-inlines-hidden`, warms each 100 times, then alternates three 200-decode
samples over seven passes. Both variants use the same fixture and native options.
The host is shared; noisy passes are preserved. These figures do not replace RBE
qualification.

| Candidate | Best paired speedup | Decision |
| --- | ---: | --- |
| Force four scalar writer functions inline; combine u32 span bounds into one u64 comparison | 0.998× | Rejected |
| Cache policy eligibility for fused duplicates (same binary, five alternating passes) | 0.993× | Rejected |
| Specialize one- and two-digit integer conversion | 1.002× | Rejected |
| Reuse CORE-string classification after exact key-template matches | 1.007×, high host noise | Not promoted |
| Cache plain/quoted fast-path eligibility at tag, anchor and schema transitions | 1.003× | Rejected |
| Disable directory output while retaining duplicate sorting | 1.008× | Not promoted |

Experimental sources live outside the checkout. The key-classification candidate
has not completed correctness qualification; its timing is not release evidence.

The x86 SSE2 short-plain-scalar candidate has separate semantic qualification:
`sse2_short_equivalence.c` checks 16,777,216 byte/position/length/fill combinations
against scalar classification and exact masked words in an x86 binary. All pass.
The vector algorithm also passes 9,093 ASan/UBSan fast/general decoder comparisons
on arm64 (including all 402 conformance inputs and schema/duplicate policies).
Only the SSE2 branch is proposed for production; its Linux throughput gate remains
pending. The arm64 vector branch measured only 1.005× and is not retained.
