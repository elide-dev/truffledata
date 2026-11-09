package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.strings.TruffleString;
import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.DataLibrary;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The lazy {@link CsvText} cell: content, reuse, lifetime, equality and interop. */
final class CsvTextTest {
  @TempDir Path dir;

  private static final String SAMPLE =
      "name,city,note\n"
          + "ann,Zürich,plain\n"
          + "\"b \"\"o\"\" b\",東京,\"x,y\"\n"
          + ",Ægir,\"emoji 😀 end\"\n"
          + "\"\",a-very-long-ascii-field-over-sixteen-bytes,\"\"\n";

  private static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static void assertSameText(CsvBatch batch) {
    CsvText reusable = new CsvText();
    for (int r = 0; r < batch.size(); r++) {
      CsvRow row = batch.row(r);
      for (int c = 0; c < batch.schema().size(); c++) {
        String expected = row.getString(c);
        CsvText fresh = row.getText(c);
        assertSame(reusable, row.getText(c, reusable));
        for (CsvText text : List.of(fresh, reusable)) {
          assertEquals(expected, text.toString());
          assertEquals(expected.length(), text.charLength());
          assertEquals(utf8(expected).length, text.length());
          assertTrue(text.contentEquals(utf8(expected)));
          assertEquals(
              TruffleString.fromJavaStringUncached(expected, TruffleString.Encoding.UTF_8),
              text.toTruffleString());
          byte[] copy = new byte[text.length() + 2];
          text.copyTo(copy, 1);
          assertArrayEquals(utf8(expected), java.util.Arrays.copyOfRange(copy, 1, copy.length - 1));
          for (int i = 0; i < text.length(); i++) assertEquals(utf8(expected)[i], text.byteAt(i));
          assertThrows(IndexOutOfBoundsException.class, () -> text.byteAt(utf8(expected).length));
        }
        assertEquals(fresh, reusable);
        assertEquals(fresh.hashCode(), reusable.hashCode());
      }
    }
  }

  @Test
  void textsMatchStringsOnHeapBatches() {
    assertSameText(Csv.parse(utf8(SAMPLE), CsvFormat.RFC4180));
    assertSameText(Csv.parseOwned(utf8(SAMPLE), CsvFormat.RFC4180));
    // Commons formats mark complex spans; the text decodes them the same way.
    assertSameText(
        Csv.parse(
            utf8("a,b\n\"x\\\"y\",z\\,w\n"),
            CsvFormat.Predefined.DEFAULT.format().toBuilder().setEscape('\\').build()));
  }

  @Test
  void textsMatchStringsOnMappedBatches() throws Exception {
    Path path = dir.resolve("sample.csv");
    Files.write(path, utf8(SAMPLE));
    try (CsvBatch batch = Csv.parse(path, CsvFormat.RFC4180)) {
      assertSameText(batch);
      assertSameText(batch.snapshot());
    }
  }

  @Test
  void contractViolationsLeaveTheTextUnchanged() {
    Schema schema =
        Schema.of(
            Field.requiredLong("n"),
            new Field("s", Field.Kind.STRING, true, false),
            new Field("o", Field.Kind.STRING, false, true));
    CsvBatch batch =
        CsvPlan.compile(schema, CsvFormat.RFC4180.withFlexible(true))
            .parse(utf8("n,s,o\n1,,x\n2,y\n"));
    CsvText text = batch.row(0).getText(2);
    assertEquals("x", text.toString());
    CsvRow first = batch.row(0);
    CsvRow second = batch.row(1);
    assertThrows(ContractViolation.class, () -> first.getText(0, text)); // INT64
    assertThrows(ContractViolation.class, () -> first.getText(1, text)); // null
    assertThrows(ContractViolation.class, () -> second.getText(2, text)); // missing
    assertEquals("x", text.toString());
    assertEquals("y", second.getText(1, text).toString());
  }

  @Test
  void unboundTextFailsEveryRead() {
    CsvText text = new CsvText();
    for (Runnable read : reads(text))
      assertThrows(IllegalStateException.class, read::run, "unbound read");
  }

  private static List<Runnable> reads(CsvText text) {
    CsvText other = Csv.parse(utf8("a\nv\n"), CsvFormat.RFC4180).row(0).getText(0);
    List<Object> sink = new java.util.ArrayList<>();
    return List.of(
        () -> sink.add(text.length()),
        () -> sink.add(text.charLength()),
        () -> sink.add(text.byteAt(0)),
        () -> text.copyTo(new byte[64], 0),
        () -> sink.add(text.contentEquals(new byte[0])),
        () -> sink.add(text.toString()),
        () -> sink.add(text.toTruffleString()),
        () -> sink.add(text.hashCode()),
        () -> sink.add(text.equals(other)),
        () -> sink.add(other.equals(text)));
  }

  @Test
  void closedMappingFailsEveryLaterRead() throws Exception {
    Path path = dir.resolve("mapped.csv");
    Files.write(path, utf8(SAMPLE));
    CsvBatch batch = CsvPlan.generic(CsvFormat.RFC4180).withTextCells(true).parse(path);
    CsvText plain = batch.row(0).getText(0);
    CsvText escaped = batch.row(1).getText(0);
    CsvText fetched = batch.row(0).getText(1);
    assertEquals("Zürich", fetched.toString());
    assertEquals(7, fetched.length()); // bytes fetched into the text before the close
    try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
      Value name = context.asValue(batch).getArrayElement(0).getMember("name");
      assertEquals("ann", name.asString());
      batch.close();
      assertTrue(name.isString());
      assertThrows(RuntimeException.class, name::asString);
    }
    for (CsvText text : List.of(plain, escaped, fetched))
      for (Runnable read : reads(text)) {
        IllegalStateException e = assertThrows(IllegalStateException.class, read::run);
        assertEquals("CSV mapping closed", e.getMessage());
      }
    // A reader that owns its mapping invalidates the texts of its batches on close.
    CsvReader reader = Csv.open(path, CsvFormat.RFC4180);
    CsvBatch first = reader.next();
    assertNotNull(first);
    CsvText text = first.row(0).getText(1);
    assertEquals("Zürich", text.toString());
    reader.close();
    assertThrows(IllegalStateException.class, text::toString);
    assertThrows(IllegalStateException.class, () -> first.row(0).getText(1, text));
  }

  @Test
  void equalityIsByContentAcrossBatchesAndNeverWithStrings() {
    CsvBatch a = Csv.parse(utf8("a\nsame\n\"sa\"\"me\"\nother\n"), CsvFormat.RFC4180);
    CsvBatch b = Csv.parse(utf8("x\nsame\n"), CsvFormat.RFC4180);
    CsvText same = a.row(0).getText(0);
    CsvText quoted = a.row(1).getText(0);
    CsvText other = a.row(2).getText(0);
    CsvText elsewhere = b.row(0).getText(0);
    assertEquals(same, elsewhere);
    assertEquals(same.hashCode(), elsewhere.hashCode());
    assertNotEquals(same, quoted);
    assertEquals("sa\"me", quoted.toString());
    assertNotEquals(same, other);
    assertNotEquals(same, (Object) "same");
    int h = 0;
    for (byte v : utf8("same")) h = 31 * h + v;
    assertEquals(h, same.hashCode());
    Set<CsvText> set = new HashSet<>(List.of(same, quoted, other, elsewhere));
    assertEquals(3, set.size());
  }

  @Test
  void charLengthCountsUtf16Units() {
    String[] cells = {"a", "ß", "€", "😀", "mix ß€😀 end", "x".repeat(40), "ü".repeat(40)};
    StringBuilder text = new StringBuilder("v\n");
    for (String cell : cells) text.append(cell).append('\n');
    CsvBatch batch = Csv.parse(utf8(text.toString()), CsvFormat.RFC4180);
    assertEquals(cells.length, batch.size());
    CsvText t = new CsvText();
    for (int i = 0; i < cells.length; i++) {
      assertEquals(cells[i].length(), batch.row(i).getText(0, t).charLength(), cells[i]);
      assertEquals(utf8(cells[i]).length, t.length());
    }
  }

  @Test
  void textCellsOptionChangesDataLibraryAndInteropValues() {
    byte[] input = utf8("name,n\nann,1\n");
    Schema schema = Schema.of(new Field("name", Field.Kind.STRING, false, false), Field.requiredLong("n"));
    CsvPlan strings = CsvPlan.compile(schema, CsvFormat.RFC4180);
    CsvPlan texts = strings.withTextCells(true);
    assertFalse(strings.textCells());
    assertTrue(texts.textCells());
    assertSame(texts, texts.withTextCells(true));
    assertSame(strings, strings.withTextCells(false));
    DataLibrary data = DataLibrary.factory().getUncached();
    Object plain = data.read(strings.parse(input).row(0), 0);
    Object lazy = data.read(texts.parse(input).row(0), 0);
    assertEquals("ann", plain);
    assertInstanceOf(CsvText.class, lazy);
    assertEquals("ann", lazy.toString());
    assertEquals(1L, data.read(texts.parse(input).row(0), 1));
    // The generic plan opts in the same way.
    Object generic = data.read(CsvPlan.generic(CsvFormat.RFC4180).withTextCells(true).parse(input).row(0), 0);
    assertInstanceOf(CsvText.class, generic);
    try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
      Value row = context.asValue(texts.parse(input)).getArrayElement(0);
      Value name = row.getMember("name");
      assertTrue(name.isString());
      assertEquals("ann", name.asString());
      assertEquals("ann", name.toString());
      assertEquals("ann", row.getArrayElement(0).asString());
      Value standalone = context.asValue(texts.parse(input).row(0).getText(0));
      assertTrue(standalone.isString());
      assertEquals("ann", standalone.asString());
    }
  }

  @Test
  void reusedTextReadsDoNotAllocatePerRow() {
    StringBuilder text = new StringBuilder("id,name,quoted\n");
    for (int i = 0; i < 10_000; i++) {
      text.append(i).append(",name").append(i).append(',');
      text.append(i % 4 == 0 ? "\"q \"\"" + i + "\"\"\"" : "plain").append('\n');
    }
    CsvBatch batch = Csv.parse(utf8(text.toString()), CsvFormat.RFC4180);
    assertEquals(10_000, batch.size());
    Supplier<Long> walk =
        () -> {
          CsvText t = new CsvText();
          long sum = 0;
          for (int r = 0; r < batch.size(); r++) {
            CsvRow row = batch.row(r);
            // Unescaped cells view the input; the escaped column is skipped (it fetches a copy).
            sum += row.getText(0, t).charLength() + row.getText(1, t).length();
          }
          return sum;
        };
    var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    long thread = Thread.currentThread().threadId();
    long expected = walk.get();
    for (int i = 0; i < 20; i++) assertEquals(expected, walk.get());
    long before = threads.getThreadAllocatedBytes(thread);
    long sum = walk.get();
    long allocated = threads.getThreadAllocatedBytes(thread) - before;
    assertEquals(expected, sum);
    // 10,000 CsvRow views may escape on the JVM (240 KB); nothing per cell.
    assertTrue(allocated < 10_000 * 32, "allocated " + allocated + " bytes for 10,000 rows");
  }
}
