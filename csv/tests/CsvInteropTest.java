package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

final class CsvInteropTest {
  private static Context context() {
    return Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
  }

  private static CsvBatch parse(String csv, CsvFormat format) {
    return Csv.parse(csv.getBytes(StandardCharsets.UTF_8), format);
  }

  @Test
  void batchIsAnArrayOfRows() {
    try (Context context = context()) {
      CsvBatch batch = parse("name,age,tail\nann,3,x\n\"b \"\"o\"\" b\",4,y\n", CsvFormat.RFC4180);
      Value rows = context.asValue(batch);
      assertTrue(rows.hasArrayElements());
      assertEquals(2, rows.getArraySize());
      assertFalse(rows.hasMembers());
      Value row = rows.getArrayElement(1);
      assertTrue(row.hasMembers());
      assertEquals(List.of("name", "age", "tail"), List.copyOf(row.getMemberKeys()));
      assertEquals("b \"o\" b", row.getMember("name").asString());
      assertTrue(row.getMember("name").isString());
      assertEquals("4", row.getArrayElement(1).asString());
      assertEquals(3, row.getArraySize());
      assertTrue(row.hasMember("tail"));
      assertFalse(row.hasMember("nope"));
      assertNull(row.getMember("nope"));
      assertThrows(ArrayIndexOutOfBoundsException.class, () -> rows.getArrayElement(2));
      assertThrows(ArrayIndexOutOfBoundsException.class, () -> rows.getArrayElement(-1));
    }
  }

  @Test
  void flexibleShortRowHidesAbsentMembers() {
    try (Context context = context()) {
      CsvBatch batch = parse("name,tail\nann\n", CsvFormat.RFC4180.withFlexible(true));
      Value row = context.asValue(batch).getArrayElement(0);
      assertEquals("ann", row.getMember("name").asString());
      assertFalse(row.hasMember("tail"));
      assertNull(row.getMember("tail"));
      assertThrows(ArrayIndexOutOfBoundsException.class, () -> row.getArrayElement(1));
    }
  }

  @Test
  void positionalRowsExposeOrdinalNames() {
    try (Context context = context()) {
      CsvBatch batch = parse("a,b\n", CsvFormat.RFC4180.withHeader(false));
      Value row = context.asValue(batch).getArrayElement(0);
      assertEquals(List.of("0", "1"), List.copyOf(row.getMemberKeys()));
      assertEquals("b", row.getMember("1").asString());
    }
  }
}
