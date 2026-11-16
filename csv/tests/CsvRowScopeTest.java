package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.Batch;
import dev.elide.truffledata.ContractViolation;
import dev.elide.truffledata.DataArray;
import dev.elide.truffledata.DataLibrary;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Limits;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.Values;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression for the ownership-transfer gate over {@link CsvRow}: a row over a mapped batch borrows
 * a closable {@link CsvMapping} and must be rejected up front by owned core containers, while a row
 * over a heap batch (or a {@link CsvBatch#snapshot()} of a mapped batch) is owned and accepted.
 */
final class CsvRowScopeTest {
  private static final DataLibrary DATA = DataLibrary.factory().getUncached();
  private static final String SCOPE_MESSAGE =
      "Scoped rows require an explicit snapshot before ownership transfer";

  @TempDir Path dir;

  private static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private Path file(String name, String content) throws IOException {
    Path path = dir.resolve(name);
    Files.write(path, utf8(content));
    return path;
  }

  private static void assertScoped(Runnable action) {
    ContractViolation e = assertThrows(ContractViolation.class, action::run);
    assertEquals(SCOPE_MESSAGE, e.getMessage());
  }

  private static Schema typedSchema() {
    return Schema.of(Field.requiredLong("id"), new Field("name", Field.Kind.STRING, false, false));
  }

  @Test
  void mappedRowIsScopedAndRejectedByOwnershipTransfer() throws IOException {
    Schema schema = typedSchema();
    CsvPlan plan = CsvPlan.compile(schema, CsvFormat.RFC4180);
    try (CsvMapping mapping = CsvMapping.map(file("mapped.csv", "id,name\n1,ann\n"))) {
      CsvBatch mapped = plan.parse(mapping);
      assertTrue(mapped.mapped());
      CsvRow row = mapped.row(0);
      // Guest reads within the owner's lifetime are unaffected by the scoped mark.
      assertEquals(1L, row.getLong(0));
      assertEquals("ann", row.getString(1));
      assertEquals("ann", DATA.read(row, 1));
      assertTrue(DATA.isPresent(row, 0));
      // Field validation still accepts scoped RECORD/ANY values for guest reads.
      Field rec = new Field("rec", Field.Kind.RECORD, true, false);
      Field any = new Field("rec", Field.Kind.ANY, true, false);
      assertSame(row, Values.validate(rec, row));
      assertSame(row, Values.validate(any, row));
      // Every ownership-transfer boundary rejects the borrowed row up front with the
      // standard message, instead of deferring a dangling-row failure to first read.
      assertScoped(() -> Values.requireInterop(row));
      assertScoped(() -> Values.normalize(row));
      assertScoped(() -> new DataArray(new Object[] {row}));
      assertScoped(() -> DataArray.copyOf(List.of(row)));
      assertScoped(
          () ->
              Batch.copyOf(
                  Schema.of(rec), Collections.singletonList(new Object[] {row}), Limits.DEFAULT));
      assertScoped(
          () ->
              Batch.copyOf(
                  Schema.of(any), Collections.singletonList(new Object[] {row}), Limits.DEFAULT));
    }
  }

  @Test
  void heapRowIsOwnedAndAcceptedByOwnershipTransfer() {
    Schema schema = typedSchema();
    CsvPlan plan = CsvPlan.compile(schema, CsvFormat.RFC4180);
    CsvBatch heap = plan.parse(utf8("id,name\n3,pat\n"));
    assertFalse(heap.mapped());
    CsvRow row = heap.row(0);
    // Guest reads work, and the row is owned: every ownership-transfer boundary accepts it.
    assertEquals(3L, row.getLong(0));
    assertEquals("pat", row.getString(1));
    Values.requireInterop(row);
    Values.normalize(row);
    DataArray array = DataArray.copyOf(List.of(row));
    assertSame(row, array.get(0));
    Field rec = new Field("rec", Field.Kind.RECORD, true, false);
    Batch batch =
        Batch.copyOf(Schema.of(rec), Collections.singletonList(new Object[] {row}), Limits.DEFAULT);
    assertSame(row, DATA.read(batch.row(0), 0));
    CsvRow stored = (CsvRow) DATA.read(batch.row(0), 0);
    assertEquals(3L, stored.getLong(0));
    assertEquals("pat", stored.getString(1));
    // A heap batch's close is a no-op; the owned row and the containers that hold it survive.
    heap.close();
    assertEquals("pat", row.getString(1));
    assertSame(row, DATA.read(batch.row(0), 0));
    assertEquals("pat", ((CsvRow) DATA.read(batch.row(0), 0)).getString(1));
  }

  @Test
  void snapshotDetachesRowAndSurvivesMappingClose() throws IOException {
    Schema schema = typedSchema();
    CsvPlan plan = CsvPlan.compile(schema, CsvFormat.RFC4180);
    Field rec = new Field("rec", Field.Kind.RECORD, true, false);
    CsvBatch snapshot;
    try (CsvMapping mapping = CsvMapping.map(file("snap.csv", "id,name\n7,zed\n"))) {
      CsvBatch mapped = plan.parse(mapping);
      assertTrue(mapped.mapped());
      // A mapped row is scoped and rejected at the boundary.
      CsvRow mappedRow = mapped.row(0);
      assertScoped(() -> DataArray.copyOf(List.of(mappedRow)));
      assertScoped(
          () ->
              Batch.copyOf(
                  Schema.of(rec),
                  Collections.singletonList(new Object[] {mappedRow}),
                  Limits.DEFAULT));
      // The snapshot detaches the batch onto the heap; its rows are owned.
      snapshot = mapped.snapshot();
      assertFalse(snapshot.mapped());
      assertSame(snapshot, snapshot.snapshot());
      CsvRow detached = snapshot.row(0);
      assertEquals(7L, detached.getLong(0));
      assertEquals("zed", detached.getString(1));
      Values.requireInterop(detached);
      DataArray array = DataArray.copyOf(List.of(detached));
      assertSame(detached, array.get(0));
      Batch batch =
          Batch.copyOf(
              Schema.of(rec), Collections.singletonList(new Object[] {detached}), Limits.DEFAULT);
      assertSame(detached, DATA.read(batch.row(0), 0));
    }
    // After the mapping closes, the detached heap row and the owned containers that hold it
    // remain readable (this is the lifetime the up-front rejection forces a snapshot for).
    assertFalse(snapshot.mapped());
    assertEquals(7L, snapshot.row(0).getLong(0));
    assertEquals("zed", snapshot.row(0).getString(1));
    CsvRow detached = snapshot.row(0);
    Values.requireInterop(detached);
    DataArray array = DataArray.copyOf(List.of(detached));
    assertSame(detached, array.get(0));
    Batch batch =
        Batch.copyOf(
            Schema.of(rec), Collections.singletonList(new Object[] {detached}), Limits.DEFAULT);
    CsvRow stored = (CsvRow) DATA.read(batch.row(0), 0);
    assertEquals(7L, stored.getLong(0));
    assertEquals("zed", stored.getString(1));
  }
}
