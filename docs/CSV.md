# CSV backend (unreleased)

The `//csv` module reads strict RFC 4180 CSV from UTF-8 bytes into immutable column
batches. Generic CSV reads every column as a string named by the header; known-schema
CSV converts INT64, FLOAT64 and BOOLEAN columns into primitive arrays during the same
pass. Guests read rows through `InteropLibrary` and `DataLibrary` (the core row model,
so `ReadLongNode` and the other read nodes work unchanged), embedders export rows in
one pass, and `CsvReader` streams an `InputStream` in bounded batches. The delivery
plan is [`docs/superpowers/plans/2026-11-06-csv.md`](superpowers/plans/2026-11-06-csv.md).

The module publishes (from the release after 0.7.0) as `dev.elide:truffledata-csv`:
one pure-Java jar for the JVM and Native Image, with no native attachments; see
[publishing](PUBLISHING.md#csv-distribution).

```java
CsvBatch batch = Csv.parse(utf8, CsvFormat.RFC4180);  // header on by default
String name = batch.row(0).getString(0);

Schema schema = Schema.of(
    Field.requiredLong("id"),
    new Field("price", Field.Kind.FLOAT64, true, false),   // nullable
    new Field("active", Field.Kind.BOOLEAN, false, false));
CsvPlan plan = CsvPlan.compile(schema, CsvFormat.RFC4180);
try (CsvReader reader = plan.open(input)) {
  for (CsvBatch b; (b = reader.next()) != null; ) {
    for (int r = 0; r < b.size(); r++) total += b.row(r).getLong(0);
    b.export(sink);                                       // one pass, no per-row allocation
  }
}
```

## Current defaults (open decisions)

These defaults are in force today but are still open decisions and may change before
the first release:

| Decision | Current default |
| --- | --- |
| Implementation | Pure Java. No native code, seam, FFM or `@CFunction`; one jar serves JVM and SVM |
| Dialect | Strict RFC 4180 with four leniencies: lone CR terminators, skipped empty lines, flexible rows that may only be shorter, and ASCII case-insensitive `true`/`false` |
| Null | Only an unquoted empty field is null, and only in a nullable column |
| Header | On by default (`CsvFormat.RFC4180.header() == true`) |
| Input | Heap `byte[]` (or an `InputStream` read into heap buffers) only; no off-heap or mmap adoption |

## Format and strictness contract

Records follow RFC 4180: a delimiter, a quote, and a doubled quote (`""`) as the escape
inside quoted fields. Quoted fields may contain the delimiter, CR and LF.

- **Terminators:** CRLF, LF and lone CR. The final record may omit its terminator.
- **Empty lines** (zero bytes between terminators) are skipped. They are not records and
  are not counted in diagnostics. A line holding only blanks or a delimiter is a record.
- **BOM:** a UTF-8 BOM (`EF BB BF`) at the start of the input is skipped. It is never part
  of the first header name or field.
- **UTF-8:** every record must be strict UTF-8: no overlong forms, no surrogates, nothing
  above U+10FFFF and no sequence truncated at end of input. Other encodings are not
  accepted.
- **Strict rejections:**
  - a quote inside an unquoted field (`a"b`, "bare quote");
  - any byte between a closing quote and the next delimiter or terminator (`"a"b`),
    except blanks when `trim` is on;
  - an unterminated quoted field at end of input;
  - a row wider than the header (or schema), always;
  - a row narrower than the header (or schema), unless `flexible`.
- **Flexible rows** may only be shorter. Their absent trailing fields read as missing
  (`Missing`, not null). Longer rows are always rejected.
- **Header:** the first record names the columns. Names are unescaped, trimmed when
  `trim` is on, and UTF-8 validated before decoding. Without a header, generic columns
  are named `"0"`..`"n-1"` and the first record sets the width. Duplicate header names
  stay readable by ordinal; named access to them fails, per the core `Schema` contract.
- **Empty input:** with a header, `Csv.parse` throws `CSV header missing`; without one it
  returns an empty batch with a zero-field schema. A stream holding only a header has a
  schema and no batches.

### Options

`CsvFormat(delimiter, quote, quoting, header, trim, flexible)` is an immutable record.
`CsvFormat.RFC4180` is `,`, `"`, quoting on, header on, trim off, flexible off; derive
others with `withDelimiter(char)`, `withQuote(char)`, `withoutQuoting()`,
`withHeader(boolean)`, `withTrim(boolean)` and `withFlexible(boolean)`.

| Option | Default | Meaning |
| --- | --- | --- |
| `delimiter` | `,` | One ASCII byte in `0x01..0x7F`, not CR or LF, distinct from `quote` |
| `quote` | `"` | Same rules as `delimiter` |
| `quoting` | on | Off makes the quote byte ordinary content |
| `header` | on | Take field names from the first record |
| `trim` | off | Strip ASCII space and tab around unquoted content and outside the quotes of quoted fields. Forbids a space or tab delimiter, and a space or tab quote while quoting is on |
| `flexible` | off | Accept rows shorter than the header or schema; absent trailing fields are missing |

Not supported: backslash escapes, comment lines, multi-byte delimiters, null tokens
(`NA`, `\N`), encodings other than UTF-8, and type inference.

## Limits

`CsvLimits(maxBatchBytes, maxRecordBytes, maxColumns, maxBatchRows)` bounds memory. All
values must be positive, `maxRecordBytes <= maxBatchBytes`, `maxBatchBytes <=
Integer.MAX_VALUE - 64`, and `maxRecordBytes < 2^30` (the packed span length field).

| Limit | `DEFAULT` | Bounds |
| --- | --- | --- |
| `maxBatchBytes` | 64 MiB | One batch's input; `parse` checks it before copying |
| `maxRecordBytes` | 1 MiB | One record's content, terminator excluded, on every path; also the bytes a stream buffers for an incomplete record |
| `maxColumns` | 4096 | Fields per record |
| `maxBatchRows` | 1,048,576 | Rows per batch. `parse` fails past it; a stream starts a new batch |

A stream holds at most one batch buffer and one incomplete record. An unterminated
quote in an endless stream therefore fails at `maxRecordBytes` (plus two bytes of slack
for a pending CRLF) instead of growing memory.

## API

### Generic

`Csv.parse(byte[] utf8, CsvFormat format[, CsvLimits limits])` copies the input once,
after checking `maxBatchBytes`, and returns a `CsvBatch` whose schema has one
non-nullable STRING field per column (optional when `flexible`). Quoted fields are
never copied at ingest: STRING columns hold packed spans into the retained input, and
doubled quotes are unescaped only when a field is read.

### Typed

`CsvPlan.compile(Schema schema, CsvFormat format)` builds an immutable, thread-safe plan.
`plan.parse(utf8[, limits])` and `plan.open(input[, limits, batchBytes])` return batches
with `schema() == plan.schema()`, so compiled caches keyed on schema identity stay
monomorphic.

| Kind | Accepted content | Notes |
| --- | --- | --- |
| INT64 | `-?[0-9]+` within int64 | Leading zeros allowed. `+`, blanks (without `trim`) and overflow are rejected. Integer width and range constraints apply |
| FLOAT64 | `[+-]?([0-9]+(\.[0-9]*)?\|\.[0-9]+)([eE][+-]?[0-9]+)?` | Correctly rounded, bit-identical to `Double.parseDouble`. Infinite results (`1e400`) and `NaN` are rejected; underflow reads as signed zero; `-0` is `-0.0`. `floatBits == 32` requires an exact binary32 |
| BOOLEAN | `true` or `false`, ASCII case-insensitive | Anything else (`yes`, `1`) is rejected |
| STRING | Any valid UTF-8 | Length constraints are rejected when the plan compiles |
| EXACT, ARRAY, RECORD, ANY | — | Rejected when the plan compiles |

Quoted numeric and boolean fields convert like unquoted ones (`"42"` is 42); escaped
content and `""` are conversion errors.

- **With a header**, header names map to schema fields by exact name, in any order.
  Header columns outside the schema are skipped (projection). A schema field absent
  from the header fails at the header unless it is `optional`, in which case every row
  reads it as missing. A field matched by two header columns fails. Field names must be
  unique.
- **Without a header**, columns map by position, and the record width must equal
  `schema.size()`. With `flexible`, shorter rows are allowed when every absent field is
  optional. Positional plans may repeat field names.

### Streaming

`Csv.open(input, format[, limits, batchBytes])` and `plan.open(input[, limits,
batchBytes])` return a single-threaded `CsvReader` that owns and closes its stream.
`batchBytes` (default 1 MiB, at most `maxBatchBytes`) sizes each fresh batch buffer.

| Method | Behavior |
| --- | --- |
| `schema()` | Reads the header (or first record) on first call; the same instance for every batch |
| `next()` | The next batch of at least one row, or `null` at end of input. Propagates `IOException` |
| `records()` | Data records returned so far |
| `position()` | Stream bytes consumed into returned batches, BOM and header included |
| `close()` | Closes the stream; idempotent. `next()` and `schema()` then throw `IllegalStateException("CSV reader closed")` |

Each batch owns a fresh buffer; buffers are never recycled, so returned batches stay
valid after later reads and after close. Records cut by a buffer boundary (including
between the quotes of `""`, between CR and LF and inside a UTF-8 sequence) parse exactly
as in the whole input.

### Batches and rows

`CsvBatch` and `CsvRow` are immutable and unconfined, need no closing, and are their own
snapshot; share them across threads freely. The batch keeps its input `byte[]`; its
arrays are never exposed.

| `CsvBatch` | Behavior |
| --- | --- |
| `schema()`, `size()` | The batch schema and row count |
| `row(i)` | A new `CsvRow` view |
| `firstRecord()` | Stream data-record index of row 0 (the header is not a data record) |
| `inputBytes()` | Retained input length |
| `export(CsvSink)` | One-pass export (below) |

| `CsvRow` | Behavior |
| --- | --- |
| `batch()`, `index()` | The owning batch and the row's index in it |
| `record()` | Stream data-record index, 0-based |
| `isNull(c)` | True only for a null (not a missing) field |
| `getString(c)`, `getLong(c)`, `getDouble(c)`, `getBoolean(c)` | The value; `ContractViolation` when the field is null, missing or of another kind |

Memory for a generic batch is the input plus 8 bytes per field plus array headers.
Typed columns cost 8 bytes (BOOLEAN: 1 byte) per cell, plus 1 byte per cell for
nullable or optional fields.

### Export

`CsvBatch.export(CsvSink sink)` walks rows in order and, within each row, every schema
field once in schema order:

```java
public interface CsvSink {
  void beginRow(long record, int fields);   // stream data-record index; fields == schema.size()
  void text(int column, CsvText value);     // STRING cells
  void longValue(int column, long value);
  void doubleValue(int column, double value);
  void booleanValue(int column, boolean value);
  void nullValue(int column);
  void missing(int column);                 // flexible short rows, optional fields absent from the header
  void endRow();
}
```

`CsvText` is a reusable view of one cell's UTF-8 bytes after unescaping, valid only
during the `text` call: `length()`, `byteAt(i)`, `copyTo(target, offset)`,
`contentEquals(utf8)`, `toTruffleString()` (UTF-8, one copy, no intermediate `String`) and
`toString()`. Unescaped fields view the batch input directly; escaped fields view a
scratch buffer. An export allocates one `CsvText` and, only when the batch has escaped
fields, one scratch buffer that grows to the longest escaped field. Nothing is allocated
per row or per field. Exceptions from the sink propagate unchanged.

## Null and missing

| Input | Nullable column | Non-nullable STRING | Non-nullable INT64 / FLOAT64 / BOOLEAN |
| --- | --- | --- | --- |
| Unquoted empty field (`a,,b`; with `trim`, a field of blanks) | null (`DataNull`) | `""` | `ContractViolation` (`null`) |
| Quoted empty field (`""`) | `""` for STRING; conversion error otherwise | `""` | conversion error |
| Absent trailing field of a flexible short row | missing if optional; `ContractViolation` (`missing`) otherwise | — | — |
| Optional schema field absent from the header | missing in every row | — | — |

Generic fields are non-nullable STRINGs, so generic CSV has no nulls: an empty field is
`""`. With `flexible`, generic fields are optional and absent ones are missing. Missing
and null stay distinct on every path: `isNull` is false for missing,
`DataLibrary.isPresent` is false only for missing, and export sends `nullValue` or
`missing`.

## Guest interop

Both types are read-only.

| Receiver | Messages | Result |
| --- | --- | --- |
| `CsvBatch` | `hasArrayElements`, `getArraySize`, `isArrayElementReadable`, `readArrayElement` | An array of `CsvRow`s. `readArrayElement` is inline (no boundary), so compiled loops can virtualize the row |
| `CsvRow` | `hasMembers`, `getMembers`, `isMemberReadable`, `readMember` | Members named by the schema (the header), in header order |
| `CsvRow` | `hasArrayElements`, `getArraySize`, `isArrayElementReadable`, `readArrayElement` | Elements by ordinal; size is `schema.size()` |

Field values are Java `String`, `Long`, `Double` or `Boolean`, or `DataNull` (interop
null). A missing field is not readable: `isMemberReadable` and `isArrayElementReadable`
are false, and reads throw `UnknownIdentifierException` or
`InvalidArrayIndexException`. Polyglot callers see `hasMember` false and `getMember`
null. Names that occur twice in the header are not readable by name.

## DataLibrary

`CsvRow` exports `DataLibrary`, so the core read nodes work on CSV rows.

| Message | Behavior |
| --- | --- |
| `getSchema` | The batch schema (the plan's schema for typed batches) |
| `read(i)` | `String`, `Long`, `Double`, `Boolean`, `DataNull` or `Missing`. STRING fields materialize through a static boundary |
| `readLong`, `readDouble`, `readBoolean` | A cached specialization (schema identity, constant field, required non-nullable field of that kind) reads the primitive array directly; the generic replacement throws `UnexpectedResultException` carrying the `read` value for null, missing or another kind |
| `isPresent(i)` | False only for missing fields |

## Errors

Nothing falls back to another parser, and nothing is accepted silently.

| Failure | Exception and message |
| --- | --- |
| Syntax | `IllegalArgumentException("CSV syntax at record R, byte B: <reason>")`, reasons `quote in an unquoted field`, `unexpected byte after a closing quote`, `unterminated quoted field`, `record has N fields, expected W` |
| Invalid UTF-8 | `IllegalArgumentException("CSV syntax at record R, byte B: invalid UTF-8")`, `B` being the first invalid byte |
| Limit | `IllegalArgumentException("CSV resource limit: <name>")`, name `maxBatchBytes`, `maxRecordBytes`, `maxColumns` or `maxBatchRows` |
| No header | `IllegalArgumentException("CSV header missing")` |
| Header mapping | `ContractViolation("CSV header: Field 'x': missing")` or `"... matched by two columns"` |
| Conversion | `ContractViolation("CSV record R: Field 'x': <reason>")`, reasons `invalid INT64`, `invalid FLOAT64`, `invalid BOOLEAN`, `null`, `missing`, `floating width`, `integer width`, `integer range` |
| Unsupported plan | `IllegalArgumentException("CSV plan: Field 'x': <reason>")` |
| Wrong getter | `ContractViolation("Field 'x': expected a present, non-null INT64")` (or the requested kind) |
| Stream I/O | `IOException` from `CsvReader.next()` and `schema()`, unchanged |
| Invalid options | `IllegalArgumentException` from the `CsvFormat` and `CsvLimits` constructors and from `open` (`batchBytes` outside `1..maxBatchBytes`) |

`R` counts physical records from 1, the header included and empty lines excluded: the
record a person counts in the file. `B` is the 0-based byte offset from the start of the
input or stream, BOM included. `ContractViolation` extends `IllegalArgumentException`,
and its messages name the record and field but never include field text.

## Threading

Formats, limits, plans, batches and rows are immutable and safe to share across threads.
A `CsvReader` is single-threaded. A `CsvText` belongs to the export that passes it.

## Performance

The pure-Java producer rests on a throughput probe recorded in the plan (decision D1),
which found a Java span scanner within the range of Rust `csv-core` and a C++ scalar
scanner for short fields. That probe is advisory, not a harness result.

The gates below are enforced; they bound shape and allocation, not throughput:

- `CsvExportTest.exportDoesNotAllocatePerRow` (JVM): after warm-up, a 10,000-row export
  into a counting sink allocates less than 1 KiB.
- `//csv/tests:native_test` (SVM, Linux x86-64, RBE) compiles typed reads across two
  batches of one reader, row iteration through interop `readArrayElement`, and the export
  walk, with observed compiled execution, performance warnings as errors, Graal graphs of
  at most 10,000 nodes, ASTs of at most 192 nodes, and at most 0.05 Java bytes per read
  (per row for the export walk).
- `//tools/release:csv_native_test` runs the published-jar consumer in a static image.

> **Comparison results: pending (Task 7).** Measurements against FastCSV and Apache
> Commons CSV will come from `//benchmarks/comparison:csv_measure`, with raw samples
> under `benchmarks/results/csv-<sha>/` and the write-up in
> `benchmarks/comparison/CSV.md`. Until then, this page makes no throughput or
> allocation claim relative to other parsers.

## Deferred

- A SWAR or SIMD unquoted-run skip in the scanner (the comparison decides; Java first).
- Off-heap and mmap input, and buffer recycling across batches. Either makes batches
  scoped and needs a shared owner.
- More options: null tokens, wider boolean spellings, STRING length constraints,
  comments, backslash escapes, other encodings, type inference.
- Primitive and text columns in core `Batch`, so CSV and JDBC share column storage.
- A tree-shaped sink and a backend-neutral UTF-8 text view shared with JSON and YAML.
- CSV writing, and CodSpeed simulation cases.

## Verification

```sh
bazel test //csv/tests:jvm                      # scanner, UTF-8, batches, interop, reader, plans, export
bazel test //csv/tests:native_test              # SVM compiled read and export gates (Linux x86-64, RBE)
bazel test //tools/release:csv_consumer         # published jars on the JVM
bazel test //tools/release:csv_native_test      # published jars in a static image (Linux x86-64, RBE)
```
