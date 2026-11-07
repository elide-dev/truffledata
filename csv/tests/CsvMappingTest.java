package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.interop.InteropLibrary;
import dev.elide.truffledata.DataLibrary;
import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CsvMappingTest {
  private static final DataLibrary DATA = DataLibrary.factory().getUncached();

  @TempDir Path dir;

  private Path file(String name, byte[] content) throws IOException {
    Path path = dir.resolve(name);
    Files.write(path, content);
    return path;
  }

  private static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  /** A generated file: quoted, escaped and multi-byte fields, every terminator. */
  private static byte[] generated(int records, long seed) {
    Random r = new Random(seed);
    StringBuilder sb = new StringBuilder("id,name,price,note\n");
    for (int i = 0; i < records; i++) {
      sb.append(i).append(',');
      sb.append(r.nextBoolean() ? "\"n \"\"" + i + "\"\" é\"" : "plain" + i).append(',');
      sb.append(r.nextBoolean() ? "" : String.valueOf(r.nextInt(1000) / 8.0)).append(',');
      sb.append(r.nextInt(20) == 0 ? "\"long " + "x".repeat(r.nextInt(3000)) + "\n中\"" : "z");
      sb.append(r.nextBoolean() ? "\n" : "\r\n");
    }
    return utf8(sb.toString());
  }

  private static List<List<@Nullable String>> rows(CsvBatch batch) {
    return Differential.rows(batch, new ArrayList<>());
  }

  @Test
  void callerChannelSurvivesMappingAndConvenienceOwners() throws IOException {
    Path path = file("channel.csv", utf8("name\nann\n"));
    try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
      channel.position(2);
      try (CsvMapping mapping = CsvMapping.map(channel, true)) {
        assertTrue(mapping.shared());
        assertEquals(
            rows(Csv.parse(utf8("name\nann\n"), CsvFormat.RFC4180)),
            rows(Csv.parse(mapping, CsvFormat.RFC4180)));
        assertEquals(2, channel.position());
      }
      assertTrue(channel.isOpen());
      CsvBatch batch = Csv.parse(channel, CsvFormat.RFC4180);
      batch.close();
      assertTrue(channel.isOpen());
      assertThrows(IllegalStateException.class, () -> batch.row(0));
      try (CsvReader reader = Csv.open(channel, CsvFormat.RFC4180)) {
        assertNotNull(reader.next());
      }
      assertTrue(channel.isOpen());
      assertEquals(2, channel.position());
    }
    FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
    try (CsvMapping mapping = CsvMapping.map(channel)) {
      channel.close();
      assertEquals(1, Csv.parse(mapping, CsvFormat.RFC4180).size());
    }
    try (FileChannel empty =
            FileChannel.open(file("empty.csv", new byte[0]), StandardOpenOption.READ);
        CsvMapping mapping = CsvMapping.map(empty)) {
      assertEquals(0, mapping.size());
    }
  }

  @Test
  void mappedParseEqualsHeapParse() throws IOException {
    byte[] bytes = generated(40_000, 1); // several 1 MiB windows
    assertTrue(bytes.length > 2 << 20);
    Path path = file("big.csv", bytes);
    try (CsvMapping mapping = CsvMapping.map(path)) {
      CsvBatch mapped = Csv.parse(mapping, CsvFormat.RFC4180);
      CsvBatch heap = Csv.parse(bytes, CsvFormat.RFC4180);
      assertTrue(mapped.mapped());
      assertFalse(heap.mapped());
      assertEquals(rows(heap), rows(mapped));
      assertEquals(Differential.names(heap), Differential.names(mapped));
      ListSink a = new ListSink();
      ListSink b = new ListSink();
      heap.export(a);
      mapped.export(b);
      assertEquals(a.rows, b.rows);
      // Typed.
      Schema schema =
          Schema.of(
              Field.requiredLong("id"),
              new Field("price", Field.Kind.FLOAT64, true, false),
              new Field("note", Field.Kind.STRING, false, false));
      CsvPlan plan = CsvPlan.compile(schema, CsvFormat.RFC4180);
      CsvBatch typed = plan.parse(mapping);
      CsvBatch typedHeap = plan.parse(bytes);
      assertSame(schema, typed.schema());
      for (int i = 0; i < typed.size(); i += 97) {
        assertEquals(typedHeap.row(i).getLong(0), typed.row(i).getLong(0));
        assertEquals(typedHeap.row(i).isNull(1), typed.row(i).isNull(1));
        assertEquals(typedHeap.row(i).getString(2), typed.row(i).getString(2));
      }
    }
    // Commons formats: escapes, null strings and complex spans decode from the mapping.
    String mysql = "a\t\\N\tb\\tc\nx\\\ty\t\\\\\tz\n";
    Path m = file("mysql.tsv", utf8(mysql));
    try (CsvMapping mapping = CsvMapping.map(m)) {
      CsvFormat format = CsvFormat.Predefined.MYSQL.format();
      assertEquals(rows(Csv.parse(utf8(mysql), format)), rows(Csv.parse(mapping, format)));
      ListSink a = new ListSink();
      ListSink b = new ListSink();
      Csv.parse(utf8(mysql), format).export(a);
      Csv.parse(mapping, format).export(b);
      assertEquals(a.rows, b.rows);
    }
  }

  @Test
  void mappedStreamEqualsHeapStream() throws IOException {
    byte[] bytes = generated(3_000, 2);
    Path path = file("stream.csv", bytes);
    List<List<@Nullable String>> whole = rows(Csv.parse(bytes, CsvFormat.RFC4180));
    for (int batchBytes : new int[] {64, 1000, 1 << 20}) {
      List<List<@Nullable String>> streamed = new ArrayList<>();
      int batches = 0;
      try (CsvMapping mapping = CsvMapping.map(path);
          CsvReader reader = Csv.open(mapping, CsvFormat.RFC4180, CsvLimits.DEFAULT, batchBytes)) {
        Schema schema = reader.schema();
        for (CsvBatch b; (b = reader.next()) != null; ) {
          assertSame(schema, b.schema());
          assertTrue(b.mapped());
          Differential.rows(b, streamed);
          batches++;
        }
        assertEquals(bytes.length, reader.position());
      }
      assertEquals(whole, streamed, "batchBytes " + batchBytes);
      if (batchBytes == 64) assertTrue(batches > 100);
    }
  }

  @Test
  void closeInvalidatesEveryRead() throws Exception {
    Path path = file("close.csv", utf8("id,name\n1,ann\n2,\"b\"\"o\"\n"));
    Schema schema =
        Schema.of(Field.requiredLong("id"), new Field("name", Field.Kind.STRING, false, false));
    CsvMapping mapping = CsvMapping.map(path);
    CsvBatch batch = CsvPlan.compile(schema, CsvFormat.RFC4180).parse(mapping);
    CsvRow row = batch.row(1);
    assertEquals("b\"o", row.getString(1));
    assertEquals(2L, DATA.readLong(row, 0));
    mapping.close();
    mapping.close(); // idempotent
    assertFalse(mapping.isOpen());
    List<Runnable> reads =
        List.of(
            () -> row.getLong(0),
            () -> row.getString(1),
            () -> row.isNull(0),
            () -> batch.row(0),
            () -> DATA.read(row, 1),
            () -> DATA.isPresent(row, 0),
            () -> {
              try {
                DATA.readLong(row, 0);
              } catch (com.oracle.truffle.api.nodes.UnexpectedResultException e) {
                throw new AssertionError(e);
              }
            },
            () -> {
              try {
                InteropLibrary.getUncached().readArrayElement(batch, 0);
              } catch (com.oracle.truffle.api.interop.InteropException e) {
                throw new AssertionError(e);
              }
            },
            () -> batch.export(new ListSink()),
            batch::snapshot);
    for (Runnable read : reads) {
      IllegalStateException e = assertThrows(IllegalStateException.class, read::run);
      assertEquals("CSV mapping closed", e.getMessage());
    }
    assertThrows(IllegalStateException.class, () -> Csv.parse(mapping, CsvFormat.RFC4180));
  }

  @Test
  void snapshotOutlivesMapping() throws IOException {
    Path path = file("snap.csv", utf8("a,b\n\"x,1\",é\n"));
    CsvBatch snapshot;
    try (CsvMapping mapping = CsvMapping.map(path)) {
      CsvBatch batch = Csv.parse(mapping, CsvFormat.RFC4180);
      snapshot = batch.snapshot();
      assertFalse(snapshot.mapped());
      assertEquals(batch.inputBytes(), snapshot.inputBytes());
    }
    assertEquals("x,1", snapshot.row(0).getString(0));
    assertEquals("é", snapshot.row(0).getString(1));
    CsvBatch heap = Csv.parse(utf8("a\n1\n"), CsvFormat.RFC4180);
    assertSame(heap, heap.snapshot());
  }

  @Test
  void confinedRejectsOtherThreads() throws Exception {
    Path path = file("thread.csv", utf8("a\nx\n"));
    try (CsvMapping mapping = CsvMapping.map(path)) {
      assertFalse(mapping.shared());
      CsvBatch batch = Csv.parse(mapping, CsvFormat.RFC4180);
      ExecutionException e =
          assertThrows(
              ExecutionException.class,
              () -> CompletableFuture.supplyAsync(() -> batch.row(0).getString(0)).get());
      assertInstanceOf(WrongThreadException.class, e.getCause());
      assertEquals("x", batch.row(0).getString(0));
    }
  }

  @Test
  void sharedServesOtherThreads() throws Exception {
    Path path = file("shared.csv", utf8("a\nx\n"));
    CsvMapping mapping = CsvMapping.map(path, true);
    assertTrue(mapping.shared());
    CsvBatch batch = Csv.parse(mapping, CsvFormat.RFC4180);
    assertEquals("x", CompletableFuture.supplyAsync(() -> batch.row(0).getString(0)).get());
    CompletableFuture.runAsync(mapping::close).get();
    assertThrows(IllegalStateException.class, () -> batch.row(0));
  }

  @Test
  void pathParseOwnsMapping() throws IOException {
    Path path = file("own.csv", utf8("﻿a,b\n1,2\n3,4\n"));
    CsvBatch batch = Csv.parse(path, CsvFormat.RFC4180);
    assertTrue(batch.mapped());
    assertEquals(List.of("a", "b"), Differential.names(batch));
    assertEquals("4", batch.row(1).getString(1));
    batch.close();
    assertThrows(IllegalStateException.class, () -> batch.row(0));
    // A reader over a path owns its mapping: closing it invalidates its batches.
    CsvBatch first;
    try (CsvReader reader = Csv.open(path, CsvFormat.RFC4180, CsvLimits.DEFAULT, 4)) {
      first = reader.next();
      assertNotNull(first);
      assertEquals("1", first.row(0).getString(0));
    }
    assertThrows(IllegalStateException.class, () -> first.row(0).getString(0));
    // Typed plans take paths too.
    Schema schema = Schema.of(Field.requiredLong("a"), Field.requiredLong("b"));
    try (CsvBatch typed = CsvPlan.compile(schema, CsvFormat.RFC4180).parse(path)) {
      assertEquals(4L, typed.row(1).getLong(1));
    }
  }

  @Test
  void heapBatchCloseIsNoOp() throws IOException {
    CsvBatch heap = Csv.parse(utf8("a\nx\n"), CsvFormat.RFC4180);
    heap.close();
    assertEquals("x", heap.row(0).getString(0));
    try (CsvReader reader = Csv.open(new ByteArrayInputStream(utf8("a\nx\n")), CsvFormat.RFC4180)) {
      CsvBatch b = reader.next();
      assertNotNull(b);
      reader.close();
      assertEquals("x", b.row(0).getString(0));
    }
  }

  @Test
  void emptyFile() throws IOException {
    Path path = file("empty.csv", new byte[0]);
    try (CsvMapping mapping = CsvMapping.map(path)) {
      assertEquals(0, mapping.size());
      assertEquals(0, Csv.parse(mapping, CsvFormat.RFC4180.withHeader(false)).size());
      assertThrows(IllegalArgumentException.class, () -> Csv.parse(mapping, CsvFormat.RFC4180));
      try (CsvReader reader = Csv.open(mapping, CsvFormat.RFC4180.withHeader(false))) {
        assertNull(reader.next());
      }
    }
  }

  @Test
  void writtenFilesReadBack() throws IOException {
    Path path = dir.resolve("written.csv");
    CsvFormat format =
        CsvFormat.Predefined.POSTGRESQL_CSV.format().toBuilder().setHeader("k", "v").build();
    try (CsvWriter writer = CsvWriter.open(Files.newOutputStream(path), format)) {
      writer.printRecord("a,b", null);
      writer.printRecord("", "c\nd");
    }
    CsvFormat read = format.toBuilder().setHeader().build();
    try (CsvBatch batch = Csv.parse(path, read)) {
      assertEquals("a,b", batch.row(0).getString(0));
      assertTrue(batch.row(0).isNull(1));
      assertEquals("", batch.row(1).getString(0));
      assertEquals("c\nd", batch.row(1).getString(1));
    }
  }
}
