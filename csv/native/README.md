# CSV structural index kernels

A simdjson stage-1 style structural indexer for CSV, in native code (`csv/native`, C++ over a
Myna seam) and in pure Java. Every kernel produces the same index, so a scanner can pick one per
runtime and input size. Every `//csv` parse path frames records over this index (see
[Consuming the index](#consuming-the-index)): the contract (`CsvIndex`, `CsvIndexer`), the block
step and the SWAR kernel live in `//csv` (package `dev.elide.truffledata.csv.kernel`); the
reference kernels, the Vector API kernel and the native backends live in `//csv/kernel`, each
with a `CsvIndexing` provider.

## The index

Inputs: bytes (a heap `byte[]` range, or native memory on the JVM), and a format of delimiter
`d`, quote `q` and whether quoting is on. LF and CR are always record separators. Trim, comments,
escape characters and null tokens are scanner concerns, not kernel concerns.

Over the byte stream formed by concatenating every call's input:

- `inside[i]` is the XOR of `quote[0..i]`, inclusive: 1 from an opening quote up to the byte
  before its closing quote.
- A **separator** is a delimiter, LF or CR byte with `inside[i] == 0`.
- `reopen[i]` is a quote right after a closing quote: the second byte of a doubled quote.
- `unquoted[i]` is a byte outside quotes that is neither a quote nor a separator.

A field is the byte range between consecutive separators. Each separator emits one 32-bit
entry:

| Bits | Meaning |
| --- | --- |
| 0..28 | Offset of the separator, relative to the call's first byte |
| 29 `X` | `Q` and the field holds an unquoted byte |
| 30 `E` | The field holds a reopen |
| 31 `Q` | The field holds a quote byte |

The separator byte itself (`input[offset]`) tells a delimiter from LF or CR, so kinds need no
bits. A field with `Q` and without `X` is always a well-formed quoted field: it starts and ends
with a quote, its content is the bytes between them, and its only interior quotes are doubled
pairs, present exactly when `E` is set. `Q` with `X` is malformed under strict RFC 4180, or has
blanks around its quotes under trim. Fields without `Q` are plain unquoted bytes.

**Calls and carry.** A call indexes `indexable(length, capacity)` bytes: all of them when the
output can hold one entry per byte, else whole 64-byte blocks, so output never overflows and
every kernel consumes the same prefix. A 5-bit carry connects calls: inside quotes, last byte was
a closing quote, and the pending `Q`, `E` and unquoted flags of the trailing, still unterminated
field (`CsvIndex.pendingFlags`). Calls may cut the stream anywhere, including inside CRLF,
between doubled quotes and inside UTF-8 sequences. One call covers at most 2^29 - 1 bytes.
Indexing allocates nothing.

## Kernels

| Kernel | Where | How the 64-bit bitmaps are built |
| --- | --- | --- |
| `java-scalar` | `ScalarIndexer` | Byte at a time; the oracle every other kernel is tested against |
| `java-table` | `TableIndexer` | Branch-light scalar: a byte-class table builds the bitmaps |
| `java-swar` | `SwarIndexer` | Eight little-endian longs per block, exact SWAR zero-byte tests |
| `java-vector*` | `VectorIndexer` | `jdk.incubator.vector` lane compares and `VectorMask.toLong` |
| `native-swar` | `index_swar.cc` | The SWAR arithmetic in C++ (portable fallback) |
| `native-neon` | `index_neon.cc` | Four 16-byte compares, simdjson's NEON movemask, PMULL prefix XOR |
| `native-avx2` | `index_avx2.cc` | Two 32-byte compares, `movemask`, PCLMULQDQ prefix XOR |
| `native-avx512` | `index_avx512.cc` | One 64-byte `cmpeq` mask per byte class |

The bitmap kernels share one block step (`stage.h`, `BlockStep.java`): a block with no quote and
no pending quoted field writes separator offsets directly (the common case); otherwise the
prefix XOR yields the inside mask and flags are computed per field. Each native instruction set
compiles in its own translation unit with its own flags, and every shared body is force-inlined
inside an anonymous namespace, so no wider-ISA copy can leak into a narrower kernel.
`dispatch.cc` picks AVX-512BW, AVX2 or SWAR at run time on x86-64 and NEON on arm64.

`td_csv_validate_utf8` validates with simdutf and reports the first invalid byte with the Java
validator's exact rules (simdutf decides validity, a scalar pass finds the offset only on
failure).

## Seam

`csv.seam` declares `fingerprint`, `kernel`, `index`, `index_leaf` and `validate_utf8`. `index`
and `index_leaf` are the same function under two symbols: `index` keeps a normal thread
transition (large calls never delay a safepoint), `index_leaf` is a Myna native leaf for small
calls. Results pack `count << 8 | carry`; negative results are argument errors.

- **SVM** (`SvmIndexer`, static library): pins the input and output arrays and calls `index` or,
  below a size threshold, `index_leaf`.
- **JVM** (`FfmIndexer`, `csv_shared.so`): heap ranges up to a threshold go through a critical
  downcall of `index_leaf` with heap access (no copy). Myna's generated critical factories refuse
  heap segments, so this handle is built from Myna's descriptor with
  `Linker.Option.critical(true)`. Larger heap ranges are copied into a native buffer. Native
  memory (a mapped file) is indexed in place with `index(MemorySegment, ...)`.

## Consuming the index

The shipped consumer is `IndexScan` in `//csv`, behind `CsvScanner.scan`; it serves every strict
format and the Commons formats the index represents (one-byte delimiter, no comment marker, no
escape other than the quote, none of the Commons trims or a trailing delimiter), and it reproduces
the Commons lexer's framing (empty lines commit with the next record; `ignoreEmptyLines(false)`
keeps them as one-field records; a doubled-quote field is one raw complex span). The reference
consumer below is `IndexedScanner` in `//csv/kernel`.

`IndexedScanner` is the reference consumer and has the strict scanner's contract
(`CsvScanner.scan`): same records, same D2 spans (`offset << 32 | length << 2 | quoted << 1 |
escaped`), same return offsets, empty lines skipped, incomplete tails left for the next buffer.
Per entry it does:

1. `p = base + offset`; skip the LF of a CRLF pair already consumed.
2. At a record start, a CR or LF separator with no bytes before it is an empty line.
3. The field is `[fieldStart, p)`. With `Q` and not `X`: content is `[fieldStart + 1, p - 1)`,
   escaped when `E`. Without `Q`: the bytes as they are (trimmed when the format trims).
   With `X`: hand over (below).
4. A delimiter starts the next field. LF ends the record; CR ends it, consuming a following LF.
   A CR as the last byte of a non-final buffer is incomplete.

At the end of the range, a final unterminated field takes its flags from the carry; an open
quote at end of input is an error.

The fast path never raises a diagnostic. It stops before any record the strict scanner might
reject and sets `fallback()`: a field with `X`, an open quote at end of input, too many fields,
too many record bytes, or an incomplete tail (without EOF) that holds a quote or is already
`maxColumns` wide. The integration then runs the strict scanner on that one record (it reports the
exact error, or accepts the record, for example blanks around quotes under trim) and resumes the
fast path after it, rebasing the strict scanner's record number by the records already
reported. `//csv/kernel/tests:jvm` pins this shape: every record, return offset and error message
equals the strict scanner's on a randomized corpus, including malformed input.

What a scanner seam over `(bytes, offsets) -> structural positions` needs from a kernel is
therefore one method: `int index(byte[] input, int from, int length, int[] out)` plus the carry.
Commons CSV features map onto it as follows: a custom one-byte delimiter or quote is a format
byte; null tokens, quote modes, `trailingData` and `lenientEof` are consumer-side (the last two
only affect `X` fields or an open quote at end of input, which go to the per-byte lexer); an
escape equal to the quote changes nothing a `Q`-without-`X` field can hold. These keep the
per-byte Commons lexer for the whole input: an escape other than the quote (not representable in
the quote-parity mask), a comment marker (a quote inside a comment line would flip the mask),
multi-byte delimiters, `ignoreSurroundingSpaces` and Commons `trim` (they change values per
byte), and `trailingDelimiter`. Memory-mapped input is scanned through a reused heap window (see
`docs/CSV.md`).

## Measured choice

The CSV comparison (`benchmarks/comparison/CSV.md`, raw samples in
`benchmarks/results/csv-f5daec1/`) chose these defaults:

- **SVM:** the native kernel, as a leaf call for chunks of at most 1 MiB and a normal transition
  above that. It beats Java SWAR from 4 KiB up. Fallback without the library: `java-swar`.
  The Vector API is not intrinsified in `-march=compatibility` images (0.05 GB/s), so it is used
  only in x86-64-v3 images.
- **JVM:** the native kernel through the critical heap call. Fallback: `java-vector`.
- **Below 4 KiB:** not measured yet. The strict scanner stays a candidate there.

`IndexedScanner` should read the index in calls of up to 1 Mi entries for native kernels and
64 Ki entries for Java kernels, the capacities the measurements used.

## Tests

```sh
bazel test //csv/native:index_test //csv/native:api_test //csv/kernel/tests:jvm
bazel test //csv/native:index_test //csv/native:api_test \
  --copt=-fsanitize=address,undefined --copt=-fno-sanitize-recover=all \
  --linkopt=-fsanitize=address,undefined
```

`index_test` compares every supported native kernel with the scalar oracle over random corpora
in eight formats (including non-ASCII format bytes), with one call boundary at every offset,
random multi-call partitions, every byte value next to structure, and 1 MiB inputs cut by the
capacity rule. It overrides `operator new` to prove indexing allocates nothing; the kernels have
no allocation, so there is no allocation failure to inject. `api_test` covers argument errors,
packed results, carries, kernel selection and 200,000 random UTF-8 strings against the scalar
rules.
