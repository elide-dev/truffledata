# CSV backend

The `//csv` module reads UTF-8 CSV into immutable column batches and writes CSV.

- **Dialects.** Strict RFC 4180 is the default. The Apache Commons CSV 1.14.1 dialects
  (every predefined format and option) are also supported, differential-tested against
  Commons itself.
- **Generic and typed reads.** Generic CSV reads every column as a string named by the
  header. Known-schema CSV converts INT64, FLOAT64 and BOOLEAN columns into primitive
  arrays during the same pass.
- **Guest and embedder access.** Guests read rows through `InteropLibrary` and
  `DataLibrary`, the core row model, so `ReadLongNode` and the other read nodes work
  unchanged. Embedders export rows in one pass.
- **Input.** `CsvReader` streams an `InputStream` in bounded batches. Files can also be
  memory-mapped, so STRING cells are never copied onto the heap.
- **Output.** `CsvWriter` writes with Commons `CSVPrinter` rules.

The delivery plan is
[`docs/superpowers/plans/2026-11-06-csv.md`](superpowers/plans/2026-11-06-csv.md)
(Tasks 9–15 cover the Commons scope).

The module publishes (from 0.8.0) as `dev.elide:truffledata-csv`:
one pure-Java jar for the JVM and Native Image, with no native attachments. The optional
native structural-index kernel publishes as `truffledata-csv-native` (static images, with
the `.a` and `.so`/`.dylib` attachments for Linux x86-64, Linux AArch64 and macOS AArch64), `truffledata-csv-jvm` (FFM) and
`truffledata-csv-bindings`, and is opted into with `CsvIndexing.loadNative`; see
[publishing](PUBLISHING.md#csv-distribution) and [Structural index](#structural-index).

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

## Decisions in force

| Decision | Current rule |
| --- | --- |
| Implementation | Pure Java, with no native code, seam or `@CFunction` in the jar. FFM is used only for mapped files. One jar serves JVM and SVM. Every parse path frames records over a structural index (built-in kernel: Java SWAR); an application may opt into the native AVX-512/AVX2/NEON kernel from the separate `truffledata-csv-native`/`-jvm` artifacts (`CsvIndexing.loadNative`, [Structural index](#structural-index)), which the jar neither bundles nor loads |
| Behaviour scope | Apache Commons CSV 1.14.1 parsing and printing (feature matrix below). Commons formats get Commons behaviour; our own default format stays strict |
| Strict dialect | RFC 4180 with four leniencies: lone CR terminators, skipped empty lines, flexible rows that may only be shorter, and ASCII case-insensitive `true`/`false` |
| Null | One rule (`Nulls`, below). An unconfigured strict format keeps the v1 rule: only an unquoted empty field is null, and only in a nullable typed column |
| Header | `CsvFormat.RFC4180.header() == true`. Commons presets have no header, as in Commons |
| Input | Heap `byte[]`, an `InputStream`, or a memory-mapped file (`CsvMapping`) |

## Strict format and strictness contract

This section is the contract of **strict** formats (`CsvFormat.strict() == true`), which
is the default. Commons formats follow Commons instead ([Commons formats](#commons-formats)).
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

`CsvFormat` is an immutable class with a `Builder` (`CsvFormat.builder()`,
`format.toBuilder()`). The setter names follow Commons `CSVFormat.Builder`.

`CsvFormat.RFC4180` is strict:

- `,` delimiter and `"` quote, with quoting on;
- header on, trim off, flexible off;
- CRLF record separator when writing.

Derive other formats with these methods:

- `withDelimiter(char)`, `withQuote(char)`, `withoutQuoting()`;
- `withHeader(boolean)`, or `withHeader(String...)` for explicit names;
- `withTrim(boolean)`, `withFlexible(boolean)`;
- `withNullStrings(String...)`, `withQuoteMode(QuoteMode)`.

| Option | Default | Meaning |
| --- | --- | --- |
| `delimiter` | `,` | One ASCII byte in `0x01..0x7F`, not CR or LF, distinct from `quote` |
| `quote` | `"` | Same rules as `delimiter` |
| `quoting` | on | Off makes the quote byte ordinary content |
| `header` | on | Take field names from the first record |
| `trim` | off | Strip ASCII space and tab around unquoted content and outside the quotes of quoted fields. Forbids a space or tab delimiter, and a space or tab quote while quoting is on |
| `flexible` | off | Accept rows shorter than the header or schema; absent trailing fields are missing |
| `headerNames` / `skipHeaderRecord` | none / off | Explicit column names; `skipHeaderRecord` drops the first record |
| `allowMissingColumnNames` | on | Off rejects a blank header name (`CSV header: missing name at column N`) |
| `duplicateHeaderMode` | `ALLOW_ALL` | `ALLOW_EMPTY` or `DISALLOW` reject duplicate names (`CSV header: duplicate name at column N`) |
| `ignoreHeaderCase` | off | Duplicate detection and typed binding ignore case |
| `nullStrings` / `quoteMode` | unset | The [null rule](#null-and-missing) |
| `maxRows` | 0 (all) | Data records to read; a header record does not count. Reading stops there, and later bytes are not scanned |
| `recordSeparator`, `headerComments`, `autoFlush` | CRLF, none, off | Writer options |

Strict formats reject these Commons-only options, naming the option: escape, comment
marker, multi-byte delimiter, `ignoreSurroundingSpaces`, `trailingDelimiter`,
`trailingData`, `lenientEof` and `ignoreEmptyLines(false)`. Encodings other than UTF-8
and type inference are not supported in either family.

## Commons formats

`CsvFormat.Predefined` mirrors Commons `CSVFormat.Predefined`, and
`CsvFormat.Predefined.X.format()` equals Commons' `X` option for option (asserted by
`CsvFormatTest`). Any format built with `setStrict(false)` is parsed by `CommonsScanner`,
a byte-level port of the Commons `Lexer` and `CSVParser`. Presets are `flexible` (Commons
records may be short) and have no header unless one is set.

| Format | Delimiter | Quote | Escape | Empty lines | Null string | Quote mode | Other |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `DEFAULT` | `,` | `"` | — | skipped | — | — | |
| `EXCEL` | `,` | `"` | — | records | — | — | `allowMissingColumnNames`, `trailingData`, `lenientEof` |
| `INFORMIX_UNLOAD` | `\|` | `"` | `\` | skipped | — | — | LF separator |
| `INFORMIX_UNLOAD_CSV` | `,` | `"` | — | skipped | — | — | LF separator |
| `MONGODB_CSV` | `,` | `"` | `"` | skipped | — | `MINIMAL` | escape equals quote: `a""b` unquoted reads `a"b` |
| `MONGODB_TSV` | tab | `"` | `"` | skipped | — | `MINIMAL` | |
| `MYSQL` | tab | none | `\` | records | `\N` | `ALL_NON_NULL` | LF separator |
| `ORACLE` | `,` | `"` | `\` | records | `\N` | `MINIMAL` | `trim`; platform line separator |
| `POSTGRESQL_CSV` | `,` | `"` | — | records | (empty) | `ALL_NON_NULL` | unquoted empty is null, `""` is empty |
| `POSTGRESQL_TEXT` | tab | none | `\` | records | `\N` | `ALL_NON_NULL` | LF separator |
| `RFC4180` | `,` | `"` | — | records | — | — | CRLF separator |
| `TDF` | tab | `"` | — | skipped | — | — | `ignoreSurroundingSpaces` |

Commons parsing rules, all reproduced:

- **Quotes.**
  - A quote is special only at the start of a token. `a"b` is the text `a"b`.
  - Whitespace (`Character.isWhitespace`, including U+3000 and the other multi-byte
    spaces) after a closing quote is skipped. Any other byte is an error, unless
    `trailingData` appends it.
  - An unterminated quote is an error, unless `lenientEof` ends the token at end of
    input.
- **Escapes.**
  - `\r`, `\n`, `\t`, `\b` and `\f` map to control characters.
  - An escaped meta character (escape, quote or comment marker) or an escaped line break
    reads as itself.
  - An escaped delimiter, written with an escape before each delimiter byte, reads as the
    delimiter.
  - An unknown escape keeps both bytes.
  - An escape at end of input is an error.
- **Comments.** A comment marker is recognized only at the first byte of a line. The
  line is skipped; it is not retained.
- **`ignoreEmptyLines(false)`.** An empty line is a record of one empty value.
- **`ignoreSurroundingSpaces`.** Whitespace before a token is skipped. For unquoted
  tokens, trailing whitespace is trimmed after unescaping.
- **`trim`.** `String.trim` is applied to every value, quoted or not.
- **`trailingDelimiter`.** A final empty value of a record is dropped.
- **Multi-byte delimiters.** Delimiters of up to 16 ASCII bytes are supported.
- **Headers.**
  - The header comes from the first record, from explicit names, or from explicit names
    with `skipHeaderRecord`.
  - Blank names (`String.trim` empty) and null names need `allowMissingColumnNames`.
  - Duplicates follow `DuplicateHeaderMode`, case-insensitively with `ignoreHeaderCase`.
  - An empty input with a first-record header gives an empty batch with no fields.

### Feature matrix versus Commons CSV 1.14.1

| Commons feature | Status |
| --- | --- |
| Delimiter (string), quote, escape, comment marker | Supported; ASCII meta characters only |
| `ignoreEmptyLines`, `ignoreSurroundingSpaces`, `trim`, `nullString` | Supported |
| Quote-mode-dependent nulls (`ALL_NON_NULL`, `NON_NUMERIC`) | Supported |
| Explicit header, first-record header, `skipHeaderRecord`, `allowMissingColumnNames`, `DuplicateHeaderMode`, `ignoreHeaderCase` | Supported |
| `trailingDelimiter`, `trailingData`, `lenientEof`, `maxRows` | Supported |
| CR, LF and CRLF terminators | Supported |
| The 12 predefined formats | Supported, option for option |
| `CSVPrinter`: quote modes `ALL`, `ALL_NON_NULL`, `MINIMAL`, `NON_NUMERIC`, `NONE`, escapes, null string, record separator, trailing delimiter, `trim`, header records, header comments, comments | Supported, character-for-character output |
| Several null strings | Extension (Commons has one); the writer prints the first |
| Input encoding | **Different:** strict UTF-8 only, and a UTF-8 BOM is skipped. Commons decodes with replacement and keeps the BOM. Input is bytes, so an invalid byte is a located error, not a silent U+FFFD |
| Non-ASCII meta characters | **Different:** rejected. ASCII keeps the scanner byte-level |
| Records wider than the header or first record | **Different:** rejected (`record has N fields, expected W`). Batches are columnar with one schema, and a wider row has no columns to land in. Shorter rows read as missing (`flexible`) |
| A record that `trailingDelimiter` empties (`ignoreEmptyLines(false)` and an empty line) | **Different:** a syntax error, `empty record with a trailing delimiter`. Commons silently stops parsing there and drops the rest of the input |
| Record comments, header and trailer comments | **Different:** skipped, not retained. Columnar batches carry no per-record text |
| Header map lookups for duplicates, `ignoreHeaderCase` member access | **Different:** core `Schema` reads duplicate names by ordinal only, and guest member names are exact. `ignoreHeaderCase` applies to duplicate detection and typed binding |
| Null header names | **Different:** read as `""` names. Commons omits them from `getHeaderNames()` |
| Header from the first record, when writing | **Different:** nothing is printed until `printBatch` supplies names. Commons prints an empty record |
| `QuoteMode.ALL` with a null string and no quote | **Different:** the writer rejects it. Commons prints `null` around the null string, a `String` concatenation of a null `Character` |
| Stale peek buffers at end of input with delimiters of 3 or more characters, and an escaped multi-byte delimiter near end of input | **Different:** not ported (Commons bugs). The differential suite ends such inputs with LF |
| `Reader`/`InputStream`/`ResultSet` values, `CSVParser` record numbers and character offsets, `getFirstEndOfLine` | Not in scope (Java I/O conveniences); batches report record indexes and byte offsets instead |


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

Commons formats apply the same limits. A comment line counts toward `maxRecordBytes`
while it is incomplete in a stream.

A stream holds at most one batch buffer and one incomplete record. An unterminated
quote in an endless stream therefore fails at `maxRecordBytes` (plus two bytes of slack
for a pending CRLF) instead of growing memory.

## API

### Generic

`Csv.parse(byte[] utf8, CsvFormat format[, CsvLimits limits])` copies the input once,
after checking `maxBatchBytes`, and returns a `CsvBatch` whose schema has one STRING
field per column. Fields are optional when `flexible`, and nullable when the format
configures null strings. Quoted fields are never copied at ingest: STRING columns hold
packed spans into the retained input. Doubled quotes, Commons escapes and trailing data
are decoded only when a field is read.

### Typed

`CsvPlan.compile(Schema schema, CsvFormat format)` builds an immutable, thread-safe plan.
`plan.parse(utf8[, limits])` and `plan.open(input[, limits, batchBytes])` return batches
with `schema() == plan.schema()`, so compiled caches keyed on schema identity stay
monomorphic. `plan.parse(utf8)` reads the array only during the call: a plan with STRING
fields retains one copy of it (its cells are spans into the copy), and a plan of only INT64,
FLOAT64 and BOOLEAN fields converts every value during the scan and retains no input
(`inputBytes()` is 0).

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

Heap `CsvBatch`es and their `CsvRow`s are immutable and unconfined, need no closing, and
are their own snapshot; share them across threads freely. The batch keeps its input
`byte[]`, and its arrays are never exposed. Mapped batches are scoped to their mapping
([Mapped input](#mapped-input)).

| `CsvBatch` | Behavior |
| --- | --- |
| `schema()`, `size()` | The batch schema and row count |
| `row(i)` | A new `CsvRow` view |
| `firstRecord()` | Stream data-record index of row 0 (the header is not a data record) |
| `inputBytes()` | Retained input length (mapped: the file bytes the batch addresses) |
| `export(CsvSink)` | One-pass export (below) |
| `mapped()`, `snapshot()`, `close()` | Mapped input (below). Heap batches: `false`, `this`, no-op |

| `CsvRow` | Behavior |
| --- | --- |
| `batch()`, `index()` | The owning batch and the row's index in it |
| `record()` | Stream data-record index, 0-based |
| `isNull(c)` | True only for a null (not a missing) field |
| `getString(c)`, `getLong(c)`, `getDouble(c)`, `getBoolean(c)` | The value; `ContractViolation` when the field is null, missing or of another kind |
| `getText(c)`, `getText(c, into)` | The STRING cell as a lazy `CsvText` bound to the batch ([Text cells](#text-cells)); the second form rebinds the caller's reusable text and allocates nothing. Same `ContractViolation` rules |

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

The export's `CsvText` is one reusable view of each cell's UTF-8 bytes after unescaping,
valid only during the `text` call (the same type as the retained text cells below).
Unescaped fields view the batch input directly; escaped fields view a scratch buffer. An
export allocates one `CsvText` and, only when the batch has escaped fields, one scratch
buffer that grows to the longest escaped field. Nothing is allocated per row or per field.
Exceptions from the sink propagate unchanged.

### Text cells

`CsvText` is a lazy STRING cell: the cell's UTF-8 bytes after unescaping, without a
`String`. `CsvRow.getText(c)` returns a new text bound to the batch; `getText(c, into)`
rebinds a text the caller already holds (`new CsvText()` is unbound until then) and returns
it, so one text can walk a whole batch without allocating, the way a Rust `StringRecord` is
reused. Reads: `length()` (bytes), `charLength()` (the `String` length in UTF-16 units,
counted from the bytes), `byteAt(i)`, `copyTo(target, offset)`, `contentEquals(utf8)`,
`toTruffleString()` (UTF-8, one copy, no intermediate `String`) and `toString()` (built
exactly as `getString` builds it, cached per binding).

- **No copy by default.** An unescaped cell of a heap batch views the batch input. Escaped
  cells and cells of a mapped batch fetch one decoded copy of their bytes on the first
  byte-level read (`length()` of an escaped cell, `charLength`, `byteAt`, `copyTo`,
  `contentEquals`, `toTruffleString`, `equals`, `hashCode`); `toString()` never takes that
  copy.
- **Batch lifetime.** A text lives as long as its batch: every read of a text over a mapped
  batch throws `IllegalStateException("CSV mapping closed")` once the mapping is closed
  (including a reader-owned mapping closed with the reader), even for bytes it had already
  fetched. Heap batches never close. An unbound text throws `IllegalStateException` on every
  read.
- **Equality and hashing are by content.** Two texts are equal when their unescaped UTF-8
  bytes are equal, whatever their batches; `hashCode` is the polynomial (31) hash of those
  bytes, consistent with `equals` but not with `String.hashCode`. A text never equals a
  `String` (use `contentEquals` or `toString`). A reused text changes content when rebound,
  so do not key collections by one.
- **Interop.** `CsvText` is an interop string: `isString`, `asString`, `asTruffleString`
  and `toDisplayString`. By default DataLibrary and interop reads still return Java
  `String`s; `CsvPlan.withTextCells(true)` (also on `CsvPlan.generic(format)`, the plan
  behind `Csv.parse`) makes them return `CsvText` instead. `getString`/`getText` do not
  depend on the option. Core contracts that require a Java `String` for STRING values
  (strict `Values.validate`, STRING length constraints, payload budgets) treat a `CsvText`
  as a type mismatch, so the option is for guests that consume the cell as an interop string.
- **Threads.** A text is as shareable as its batch for reads that do not fetch; fetching
  and `toString` caching write the text's fields, so a reusable text belongs to one thread.

## Null and missing

`Nulls` holds the one null rule. It is applied to a field's final value (after unescaping
and trimming) and to whether the field was quoted, as Commons `CSVParser.handleNull`
applies it:

- a value equal to one of `nullStrings` is null, unless the quote mode is `ALL_NON_NULL`
  or `NON_NUMERIC` and the field was quoted;
- with no null strings, an unquoted empty value is null only under those two quote
  modes.

A format **configures** nulls when it sets null strings or one of those quote modes. A
configured format behaves as follows:

- generic columns are nullable STRINGs;
- header values pass through the rule, and a null name is blank (`""`);
- in typed plans, a null token in a nullable field is null;
- a null token in a non-nullable field of any kind fails with `ContractViolation("CSV
  record R: Field 'x': null token in a non-nullable field")`. The message never contains
  the token.

| Format | `\N` | `"\N"` | (empty) | `""` |
| --- | --- | --- | --- | --- |
| `MYSQL`, `POSTGRESQL_TEXT` (`\N`, `ALL_NON_NULL`, no quote) | null | (no quoting) | `""` | (no quoting) |
| `ORACLE` (`\N`, `MINIMAL`) | null | null | `""` | `""` |
| `POSTGRESQL_CSV` (empty, `ALL_NON_NULL`) | `\N` | `\N` | null | `""` |
| `DEFAULT` and other unconfigured Commons formats | text | text | `""` | `""` |

Unconfigured strict formats keep the v1 rule, shown in this table:

| Input | Nullable column | Non-nullable STRING | Non-nullable INT64 / FLOAT64 / BOOLEAN |
| --- | --- | --- | --- |
| Unquoted empty field (`a,,b`; with `trim`, a field of blanks) | null (`DataNull`) | `""` | `ContractViolation` (`null`) |
| Quoted empty field (`""`) | `""` for STRING; conversion error otherwise | `""` | conversion error |
| Absent trailing field of a flexible short row | missing if optional; `ContractViolation` (`missing`) otherwise | — | — |
| Optional schema field absent from the header | missing in every row | — | — |

Under the v1 rule generic fields are non-nullable STRINGs, so generic CSV has no nulls:
an empty field is `""`. With `flexible`, generic fields are optional and absent ones are missing. Missing
and null stay distinct on every path: `isNull` is false for missing,
`DataLibrary.isPresent` is false only for missing, and export sends `nullValue` or
`missing`.

## Writing

`CsvWriter` follows the Commons `CSVPrinter` rules for every format, strict or Commons.

```java
try (CsvWriter writer = CsvWriter.open(out, CsvFormat.Predefined.POSTGRESQL_CSV.format())) {
  writer.printRecord("a,b", null, 42L);   // "a,b",,"42" (ALL_NON_NULL, null string "")
  writer.printComment("ignored without a comment marker");
  writer.printBatch(batch);               // one record per row; trailing missing fields omitted
}
```

| Method | Behaviour |
| --- | --- |
| `new CsvWriter(Appendable, format)`, `CsvWriter.open(OutputStream, format)` (UTF-8) | Prints the header comments, then the explicit header names unless `skipHeaderRecord` |
| `print(value)`, `println()`, `printRecord(values…)` | Values are `CharSequence`, `Number` (unquoted under `NON_NUMERIC`), other objects (`toString()`) or null (the first null string; `ALL` quotes it) |
| `printComment(text)` | One `marker + space` line per comment line; nothing without a comment marker |
| `printBatch(batch)` | Rows in order. When the format names columns from the first record and nothing was printed yet, the schema names print first as the header record |
| `recordCount()`, `flush()`, `close()` | As in Commons; `close()` closes a closeable target |

The quote modes work as in Commons:

- **`MINIMAL`** (also when the quote mode is unset) quotes a value when any of these
  holds:
  - it is empty and starts a record;
  - its first character is at most `#`;
  - it contains CR, LF, the quote, the escape or the delimiter;
  - its last character is at most a space.
- **`ALL`** and **`ALL_NON_NULL`** quote every non-null value.
- **`NON_NUMERIC`** quotes every value except numbers.
- **`NONE`** escapes CR (`\r`), LF (`\n`), the escape and each delimiter byte instead of
  quoting.

Inside quotes, the quote and the escape are preceded by the escape. The quote itself
serves as the escape when none is set.

Output equals Commons, character for character (`CommonsRoundTripTest`).

## Mapped input

`CsvMapping` is the owner of one read-only file mapping:

- `CsvMapping.map(path)` maps the file into a confined `Arena`;
- `CsvMapping.map(path, true)` maps it into a shared one.
- `CsvMapping.map(channel, shared)` maps a caller-owned `FileChannel` without
  opening a path, changing its position, or closing it. Closing either the channel
  or the mapping leaves the other usable. `Csv.parse(channel, ...)` and
  `Csv.open(channel, ...)` own and close only their mapping, so sandboxed hosts can
  supply a channel that already passed their file policy.

| Entry point | Owner of the mapping | Batches valid until |
| --- | --- | --- |
| `Csv.parse(mapping, format[, limits])`, `plan.parse(mapping[, limits])` | The caller | `mapping.close()` |
| `Csv.open(mapping, format[, limits, batchBytes])`, `plan.open(mapping, …)` | The caller | `mapping.close()` |
| `Csv.parse(path, format[, limits])`, `plan.parse(path[, limits])` | The returned batch | `batch.close()` |
| `Csv.open(path, format[, limits, batchBytes])`, `plan.open(path, …)` | The reader | `reader.close()` |

- **Zero-copy retention.** STRING cells stay spans into the mapping: a mapped batch holds
  no heap copy of the input. Typed cells are decoded at ingest into heap arrays, as for
  heap input.
- **Scanning.** The file is scanned through one reusable heap window (about 1 MiB for
  `parse`, `batchBytes` for streams). This costs one memcpy per byte and keeps a single
  `byte[]` scanner kernel. A whole-file `parse` still checks `maxBatchBytes`.
- **Lifetime.**
  - After the mapping closes, every read of its batches throws `IllegalStateException("CSV
    mapping closed")`: typed and STRING reads, `DataLibrary` and interop messages, `row`,
    `export` and `snapshot`.
  - The check is one null test and one field load on each read path, with no boundary.
    Heap batches pay only the null test.
- **Threads.** A confined mapping serves STRING reads and `close()` only on its own thread;
  other threads get `WrongThreadException`. A shared mapping serves any thread.
- **`snapshot()`.** Copies the bytes a mapped batch addresses into a heap batch that
  shares the primitive columns. The copy outlives the mapping.
- **Export.** Copies each STRING cell into a reusable scratch buffer before handing the
  sink a `CsvText`, so the export allocates nothing per cell.
- **`getString`.** A `String` owns its bytes, so a mapped cell passes through the heap once:
  it is copied into a per-thread 4 KiB buffer and the `String` is built from there (longer
  cells get their own copy). Nothing is allocated per cell besides the `String`.

Heap batches are unchanged: immutable, shareable, never closed (`close()` is a no-op).

## Guest interop

Both types are read-only.

| Receiver | Messages | Result |
| --- | --- | --- |
| `CsvBatch` | `hasArrayElements`, `getArraySize`, `isArrayElementReadable`, `readArrayElement` | An array of `CsvRow`s. `readArrayElement` is inline (no boundary), so compiled loops can virtualize the row |
| `CsvRow` | `hasMembers`, `getMembers`, `isMemberReadable`, `readMember` | Members named by the schema (the header), in header order |
| `CsvRow` | `hasArrayElements`, `getArraySize`, `isArrayElementReadable`, `readArrayElement` | Elements by ordinal; size is `schema.size()` |
| `CsvText` | `isString`, `asString`, `asTruffleString`, `toDisplayString` | A lazy STRING cell ([Text cells](#text-cells)); reads fail once its mapped batch is closed |

Field values are Java `String` (or `CsvText` under `CsvPlan.withTextCells(true)`), `Long`,
`Double` or `Boolean`, or `DataNull` (interop null). A missing field is not readable: `isMemberReadable` and `isArrayElementReadable`
are false, and reads throw `UnknownIdentifierException` or
`InvalidArrayIndexException`. Polyglot callers see `hasMember` false and `getMember`
null. Names that occur twice in the header are not readable by name.

## DataLibrary

`CsvRow` exports `DataLibrary`, so the core read nodes work on CSV rows.

| Message | Behavior |
| --- | --- |
| `getSchema` | The batch schema (the plan's schema for typed batches) |
| `read(i)` | `String`, `Long`, `Double`, `Boolean`, `DataNull` or `Missing`. STRING fields materialize through a static boundary, or return a `CsvText` for plans with `withTextCells(true)` |
| `readLong`, `readDouble`, `readBoolean` | A cached specialization (schema identity, constant field, required non-nullable field of that kind) reads the primitive array directly; the generic replacement throws `UnexpectedResultException` carrying the `read` value for null, missing or another kind |
| `isPresent(i)` | False only for missing fields |

## Errors

Nothing falls back to another parser, and nothing is accepted silently.

| Failure | Exception and message |
| --- | --- |
| Syntax | `IllegalArgumentException("CSV syntax at record R, byte B: <reason>")`, reasons `quote in an unquoted field` (strict), `unexpected byte after a closing quote`, `unterminated quoted field`, `record has N fields, expected W`, `escape at end of input` and `empty record with a trailing delimiter` (Commons) |
| Invalid UTF-8 | `IllegalArgumentException("CSV syntax at record R, byte B: invalid UTF-8")`, `B` being the first invalid byte |
| Limit | `IllegalArgumentException("CSV resource limit: <name>")`, name `maxBatchBytes`, `maxRecordBytes`, `maxColumns` or `maxBatchRows` |
| No header | `IllegalArgumentException("CSV header missing")` (strict formats; Commons formats give an empty batch) |
| Header names | `IllegalArgumentException("CSV header: missing name at column N")` or `"... duplicate name at column N"` |
| Mapping closed | `IllegalStateException("CSV mapping closed")` |
| Header mapping | `ContractViolation("CSV header: Field 'x': missing")` or `"... matched by two columns"` |
| Conversion | `ContractViolation("CSV record R: Field 'x': <reason>")`, reasons `invalid INT64`, `invalid FLOAT64`, `invalid BOOLEAN`, `null`, `null token in a non-nullable field`, `missing`, `floating width`, `integer width`, `integer range` |
| Unsupported plan | `IllegalArgumentException("CSV plan: Field 'x': <reason>")` |
| Wrong getter | `ContractViolation("Field 'x': expected a present, non-null INT64")` (or the requested kind) |
| Stream I/O | `IOException` from `CsvReader.next()` and `schema()`, unchanged |
| Invalid options | `IllegalArgumentException` from `CsvFormat.Builder.build()` (the Commons `validate()` rules plus ours), the `CsvLimits` constructor and `open` (`batchBytes` outside `1..maxBatchBytes`) |

`R` counts physical records from 1, the header included and empty lines excluded: the
record a person counts in the file. `B` is the 0-based byte offset from the start of the
input or stream, BOM included. `ContractViolation` extends `IllegalArgumentException`,
and its messages name the record and field but never include field text.

## Threading

Formats, limits, plans, and heap batches and rows are immutable and safe to share across
threads. Mapped batches are as thread-safe as their arena: confined arenas serve one
thread, shared arenas serve any. A `CsvReader` and a `CsvWriter` are single-threaded. The export's `CsvText` belongs to the
export that passes it; a text cell is as shareable as its batch for reads that do not fetch
or cache, and a reusable text belongs to one thread ([Text cells](#text-cells)).

## Performance

SVM / Native Image is the performance admission target. JVM correctness, API and
coverage checks remain required; JVM timing regressions do not veto a measured SVM gain.

### Byte windows and ownership

`Csv.parse(bytes, from, to, format, limits)` and `CsvPlan.parse(bytes, from, to, limits)`
parse the half-open array window, including an optional BOM at its start. Limits and
error offsets are relative to that window. Generic parsing and plans with STRING
columns retain one copy of the window; primitive-only plans retain no input.

`parseOwned` has corresponding whole-array and window overloads. It transfers the
backing array without a copy: the caller must keep every byte unchanged while the
batch or any row remains reachable. For a window, the entire backing array stays
retained; `inputBytes()` reports that retained size. `open(bytes, from, to, ...)`
streams the window without an initial window copy; keep it unchanged until the
reader closes. Each returned streaming batch owns its bytes.

## Structural index

Every entry point (`Csv.parse`, `Csv.open`, `CsvPlan.parse`/`open`, heap, stream and mapped
input) scans through `CsvScanner`, which frames records over a structural index
(`csv/native/README.md` has the contract): one 32-bit entry per delimiter, CR or LF outside
quotes, with flags proving a field well-formed in O(1). The consumer (`IndexScan`) produces the
same records, spans, return offsets and stops as the per-byte scanners.

- **Formats.** Every strict format, and Commons formats with a one-byte delimiter, no comment
  marker, no escape other than the quote, and none of `ignoreSurroundingSpaces`, Commons `trim`
  or `trailingDelimiter`. That covers `DEFAULT`, `EXCEL`, `RFC4180`, `INFORMIX_UNLOAD_CSV`,
  `POSTGRESQL_CSV`, `MONGODB_CSV` and `MONGODB_TSV`. The others (`INFORMIX_UNLOAD`, `MYSQL`,
  `ORACLE`, `POSTGRESQL_TEXT`, `TDF`, and any format with a comment marker or multi-byte
  delimiter) keep the per-byte Commons lexer.
- **Fallback.** A record the index path cannot prove (malformed quotes, blanks around quotes
  under `trim`, Commons trailing data or mid-token quotes, an open quote at end of input, a limit,
  a quoted tail of a stream buffer) goes to the per-byte scanner of the same dialect, one record,
  which reports the exact diagnostic or accepts it; indexing resumes after it.
- **Kernels.** `CsvIndexing.install(provider)` selects the kernel for parses and readers started
  afterwards; `CsvIndexing.JAVA` (Java SWAR) is the default, `null` turns the index path off.
  Inputs below `CsvIndexing.MIN_INDEX_BYTES` use the per-byte scanners.
- **Native kernel (opt-in).** `CsvIndexing.loadNative()` (native image) and
  `CsvIndexing.loadNative(Path)` (JVM) find the backend for the running runtime as a
  `CsvIndexing.NativeBackend` service, load it and return its provider; nothing is loaded or
  installed until the application calls them:

  ```java
  // Run at image RUN TIME, after startup (not in a build-time class initializer):
  // Native image with truffledata-csv-native and libtruffledata_csv.a linked:
  CsvIndexing.install(CsvIndexing.loadNative());
  // JVM with truffledata-csv-jvm, --enable-native-access=ALL-UNNAMED and the .so attachment:
  CsvIndexing.install(CsvIndexing.loadNative(Path.of("truffledata-csv-native-<v>-linux-x86_64.so")));
  ```

  The static backend (`SvmIndexer`) makes leaf calls up to 1 MiB; the JVM backend (`FfmIndexer`)
  loads the library once per path for the life of the process and makes critical heap calls.
  Both check the generated ABI fingerprint. A missing backend or library throws
  `IllegalStateException`. The Vector API kernel (`VectorIndexer.provider()`, `//csv/kernel`) is
  not published.
- **Fused batches.** Typed and generic batches are built inside the framing loop: each field goes
  straight into its column (STRING span, or the converted INT64, FLOAT64 or BOOLEAN) as the index
  delimits it, and the record commits at its terminator. A record the fused path cannot take in
  place (a grammar or constraint failure, a complex Commons span in a typed field, a width other
  than the binding's, UTF-8 outside the prevalidated range, `maxBatchRows`) goes through the
  per-record builder with the same spans, which reports the exact diagnostic or accepts it.
  At scan entry, a fusable binding whose retained fields are all STRING selects
  `IndexScan.frameSpans`: it writes shifted spans through `BatchBuilder.putSpan`, using the
  existing projection map and commit/fallback rules. Its separate host-inlining root keeps
  numeric parsers out of the string loop. Other fusable bindings retain `frameFused`, where
  `@BytecodeInterpreterSwitch` allows native images to inline the conversions. Other record
  consumers and non-fusable batches use `framePlain`, which carries no conversion code.
- **Reads.** Typed `CsvRow` getters read a dense column with one length test (nullable,
  optional, mapped and wrong-kind reads take a checked path); `getString` builds ASCII cells of
  up to 16 bytes as Latin-1 without the charset decoder, and unescapes strict doubled quotes into
  a per-thread buffer, so the `String` is the only allocation.
- **UTF-8.** Each buffer is validated once ahead of the scan (simdutf with a native provider);
  records in the valid prefix skip their own validation, so diagnostics are unchanged.
- **Numbers.** INT64 and FLOAT64 conversions use eight-digit SWAR steps, Clinger's exact path and
  the Eisel-Lemire algorithm; `Double.parseDouble` remains only for products too close to call.

### Run-skip seam of the per-byte scanners

`CsvKernel` (package-private, final) holds the run skips of the per-byte scanners. It has two
methods over `(byte[] input, int from, int to)`:

- `skipUnquoted` returns the first index whose byte is structural outside quotes: the
  delimiter's first byte, CR, LF, the quote (strict formats, while quoting) or the escape.
  It returns `to` when there is none.
- `skipQuoted` returns the first index holding the quote or the escape, or `to`.

Both scanners make every run skip through these two calls. The strict scanner and the
Commons lexer keep their record framing, quoting rules and spans. `CsvKernelTest` is the
contract: a replacement must match the byte-by-byte reference at every offset.

The strict RFC 4180 loop keeps its v1 shape. Per record, it adds one comparison for
`maxRows` and one add per STRING cell for windowed spans. Commons-only work (escapes,
comments, Unicode space trimming, trailing data, null strings) is paid only by Commons
formats and by formats that configure null strings.

### Gates

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
- `//tools/release:csv_kernel_consumer` (JVM, published `.so`) and
  `//tools/release:csv_kernel_native_test` (static image linking the published archive) run the
  published native kernel through `CsvIndexing.loadNative` and require every parse, stream and
  diagnostic to equal the built-in kernel's.

### Comparison results

The [span-only follow-up](../benchmarks/results/csv-span-only-20261108/README.md), admitted
locally as `fef9ae5`, measured complete retained batches followed by every String-cell read.
Eight same-worker x86 SVM fixture/size pairs used three fresh forks per binary and cell,
covering 78 cells and 468 processes including reference engines and typed-read controls.
The geometric-mean candidate/baseline time ratios across the eight string cells were:

| Input contract | Java-SWAR index | Native index |
|---|---:|---:|
| Copied `parse` input | 0.9853 | 0.9821 |
| Transferred `parseOwned` input | 0.9836 | 0.9802 |

This is a modest 1.5–2.0% aggregate reduction in elapsed time, with identical allocated
bytes per row. `parseOwned` already existed: its saving over copied input is separate
from this change, and the caller must keep the transferred array unchanged while any
batch or row remains reachable. Both measured paths retain the batch and materialize
every String cell. The paired baseline already includes the selected unescape path.

Individual cells and fork ranges remain noisy: copied/native escaped 1 MiB has a
1.0036 time ratio, and the apparent 0.9332 ratio at 128 MiB has overlapping ranges.
No outlier was dropped. Unchanged external string controls have aggregate ratios
1.0002 (FastCSV), 1.0003 (Jackson) and 1.0042 (kernel-native). Typed library paths
also see the builder/scan-entry changes, so their shifts cannot isolate span-dispatch
cost. The evidence records all ranges and hashes; it establishes neither a step-change
nor an Arm, x86-v3 or AVX2-only speedup. Kind-specialized typed writers and a native
column materializer remain deferred.

`//benchmarks/comparison:csv_measure` compares truffledata with FastCSV 4.4.0, Jackson
dataformat-csv 2.22.4, univocity-parsers 2.9.1 and Commons CSV 1.14.1. It runs on SVM (portable
and x86-64-v3 images) and on the JVM, over four synthetic fixtures at 1 MiB and 128 MiB, with Rust
`csv` and `csv-core` and a C++ consumer of the native index measured on the same worker. The
latest full cross-runtime matrix is `benchmarks/results/csv-3f23d0c/` (after the run-copy
unescape fix and the scan-loop split, with a pre-touched JVM heap and three forks per cell);
`standing.md` there lists every competitor
and reference per workload and runtime, and `delta.md` compares it with `csv-720f0c7` as
same-shard ratios. Shared-host timings are advisory; only figures from one shard compare. Where
the shipped native paths stand, as ratios within their shard:

- **Scan** (`CsvScanner`): first among the Java parsers in all 24 rows (2.3–19× the best of
  FastCSV and Jackson; univocity and Commons CSV have no scan-only mode) and ahead of Rust
  `csv-core` in all 24 (1.3–9.9×, leaving out one outlier probe). Against the C++ consumer of the
  same index it reaches 0.57–0.85× on SVM and 0.95–1.11× on the JVM (ahead in 5 of 8 JVM rows);
  against the in-harness reference consumer 0.87–0.98× on SVM (was 0.62–0.94× before the loop
  split) and 0.95–1.34× on the JVM.
- **Typed** (`CsvPlan`): first among the Java parsers in all 12 rows (2.3–3.1× FastCSV, the best
  of them). Against Rust `csv` (typed serde records): ahead on `mixed` everywhere (1.18–2.08×) and
  on JVM `numeric` (1.18–1.33×), behind on SVM `numeric` (0.61–0.73×). 0.57–0.86× the reference
  kernel consumer.
- **Strings** (`Csv.parse` + `getString`): on the JVM first among the Java parsers in all 8 rows
  (1.24–3.2× the best) and ahead of Rust's reused `StringRecord` in 6 of 8. On SVM ahead of the
  best Java parser in 6 of 16 rows: ahead on `quoted` (1.35–1.61×, except compat 128 MiB, a
  fault-bound cell) and about even on `escaped` (0.96–1.05×; `7ba51d1`, after the run, probes at
  1.25× Jackson on compat 1 MiB), behind FastCSV on the short-field
  `mixed` (0.74–0.95×) and `numeric` (0.58–0.96×) fixtures; behind Rust `StringRecord` in 13 of
  16 SVM rows (0.24–0.67× on the short-field fixtures): Rust reuses one record buffer, while this
  API returns one `String` per cell over a retained batch (CSV ledger).

## Myna Truffle cells (`truffledata.csv.cells`)

`-Dtruffledata.csv.cells=true` routes the optional native CSV kernel's calls through Truffle cells
that Myna generates from this module's seam, instead of the generated `@CFunction`/FFM calls:
`td_csv_index`, `td_csv_index_leaf`, `td_csv_validate_utf8` and `td_csv_kernel` (and, on Native
Image, the fingerprint check). The indexes, diagnostics and errors are the kernel's own. The route
is **off by default** and does not touch the pure-Java scanner of `truffledata-csv`; it is support
for Myna cells and makes no performance claim.

Measured: see docs/RELEASE_0_11_0.md

- **Artifact.** `dev.elide:truffledata-csv-cells` (Bazel `//csv/cells`) carries the route
  (`CsvCellsRoute`, `CellsSvmIndexer`, `CellsFfmIndexer`, `CsvCellsFeature`), both generated cell
  sets, Myna's Native Image metadata for the Native Image cells and its own
  `META-INF/native-image/dev.elide.truffledata/truffledata-csv-cells/native-image.properties`,
  which registers `CsvCellsFeature`. It depends on `truffledata-cells` (Myna's cells runtime),
  `truffledata-csv`, `truffledata-csv-bindings` and `truffledata-csv-native`, and uses the same
  natives as `truffledata-csv-native`. Nothing in the default CSV artifacts depends on it; with the
  flag off it need not be on the class path.
- **Native Image.** Put `truffledata-csv-native` and the cells artifact on the image class path and
  pass `-Dtruffledata.csv.cells=true` to `native-image`; no `--features` option is needed, since the
  jar registers `CsvCellsFeature`. With the flag the Feature registers the cells backend, which
  `SvmIndexer.Backend.load` defers to; without it the Feature registers nothing and the image
  contains no cells code. The flag and the route must agree or the image build fails, with no
  fallback: the build-time holder `CellsRoute` of `truffledata-csv-native` rejects the flag without
  the cells artifact on the image class path, and a registered cells backend without the flag.
  Setting the property when running an image has no effect.
- **JVM.** Pass the property to the JVM with `truffledata-csv-jvm` and the cells artifact on the
  class path. It is read once, when `CellsRoute` initializes (a `static final`), and selects the
  provider in `FfmIndexer.Backend.load`; the flag without the artifact fails there rather than
  falling back. Myna's FFM cells bind through the class loader's lookup, so the cells also
  `System.load` the fingerprint-checked library.
- **Seam and fingerprint.** The cells come from seams *derived* in the build from
  `csv/native/csv.seam`: only the module options change. The production seam, bindings, ABI
  fingerprint (`CsvNativeAbi`, which the backends verify) and natives are unchanged, and
  `//bazel:myna_contract --derived` checks each derived descriptor.
- **No `resource` or `borrow` declarations.** The seam has no create/release pairs, so there is no
  `resource` to declare, and Myna 0.14.0's call `borrow` cannot apply to `td_csv_index` or
  `td_csv_index_leaf`, because a call borrow requires every other parameter to be scalar and `out`
  is a `ptr<i32>`. Expressing it would need a C ABI change (see `csv/cells/BUILD.bazel`).

## Deferred

- Kind-specialized typed writers and a native index-to-column materializer. The current
  native CSV producer emits structural indexes; Java framing builds retained columns.
- A `MemorySegment`-native index consumer for mapped input (the window copy stays; see the
  comparison write-up for why it is not the mapped path's main cost).
- Widening core's STRING contract (`Values.validate`, length constraints) to interop strings so
  `CsvText` cells pass strict validation; today the text-cells option is CSV-only.
- Buffer recycling across heap batches.
- Wider boolean spellings, STRING length constraints, other encodings, type inference.
- Primitive and text columns in core `Batch`, so CSV and JDBC share column storage.
- A tree-shaped sink and a backend-neutral UTF-8 text view shared with JSON and YAML.
- CodSpeed simulation cases.
- SVM strings behind FastCSV on short fields and SVM `numeric` typed behind Rust `csv`
  (`benchmarks/results/csv-3f23d0c/README.md`).
- SVM 128 MiB allocating cells are page-fault bound on the shared hosts (each large array is
  mapped fresh per parse); a quiet-host confirmation of every figure.

## Verification

```sh
bazel test //csv/tests:jvm                      # scanner, UTF-8, batches, interop, reader, plans, export,
                                                # Commons differential and round-trip suites, nulls, writer, mapped input,
                                                # index-path differential (IndexedDifferentialTest), numbers
bazel test //csv/tests:jvm_indexed              # every suite again with the index path forced at every size
bazel test //csv/kernel/tests:jvm               # every kernel against the scalar oracle
bazel test //csv/tests:native_test              # SVM compiled read and export gates (Linux x86-64, RBE)
bazel test //tools/release:csv_consumer         # published jars on the JVM
bazel test //tools/release:csv_native_test      # published jars in a static image (Linux x86-64, RBE)
bazel test //tools/release:csv_kernel_consumer  # published native kernel on the JVM (FFM)
bazel test //tools/release:csv_kernel_native_test  # published native kernel in a static image (RBE)
```

The native provider validates its fingerprint when loaded. Call
`CsvIndexing.install(CsvIndexing.loadNative())` at image run time, including when
application packages use `--initialize-at-build-time`. The Native Image service is
`SvmIndexer$Backend` from `truffledata-csv-native`; the JVM service is
`FfmIndexer$Backend` from `truffledata-csv-jvm`, which must stay off the image
classpath. `CsvIndexing.JAVA` is the built-in portable fallback.
