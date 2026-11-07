package dev.elide.truffledata.csv;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.DataNull;
import dev.elide.truffledata.Missing;
import java.util.ArrayList;
import java.util.List;

/**
 * Test sink: one {@code List<Object>} per row (record index first, then values as DataLibrary reads
 * them), checking that every row announces its width and reports each column once, in order.
 */
class ListSink implements CsvSink {
  final List<List<Object>> rows = new ArrayList<>();
  private List<Object> row = new ArrayList<>();
  private int fields = -1;
  private int next;

  @Override
  public void beginRow(long record, int fields) {
    assertEquals(-1, this.fields, "beginRow inside a row");
    this.fields = fields;
    next = 0;
    row = new ArrayList<>();
    row.add(record);
  }

  private void value(int column, Object value) {
    assertEquals(next++, column, "column order");
    row.add(value);
  }

  @Override
  public void text(int column, CsvText value) {
    value(column, value.toString());
  }

  @Override
  public void longValue(int column, long value) {
    value(column, value);
  }

  @Override
  public void doubleValue(int column, double value) {
    value(column, value);
  }

  @Override
  public void booleanValue(int column, boolean value) {
    value(column, value);
  }

  @Override
  public void nullValue(int column) {
    value(column, DataNull.INSTANCE);
  }

  @Override
  public void missing(int column) {
    value(column, Missing.INSTANCE);
  }

  @Override
  public void endRow() {
    assertEquals(fields, next, "columns per row");
    fields = -1;
    rows.add(row);
  }
}
