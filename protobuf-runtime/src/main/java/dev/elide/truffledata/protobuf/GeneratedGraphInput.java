package dev.elide.truffledata.protobuf;

import dev.elide.truffledata.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jspecify.annotations.Nullable;

/** One bounded cursor and cumulative budget for an entire generated message graph. */
public final class GeneratedGraphInput {
  private static final byte[] EMPTY = new byte[0];
  private final byte[] source;
  private final ProtoPlan root;
  private int position;
  private int limit;
  private long cells;
  private long bytes;

  public GeneratedGraphInput(ProtoPlan root, byte[] source) {
    if (source.length > root.maxWireBytes()) throw invalid("Wire byte budget exceeded");
    this.root = root;
    this.source = source;
    limit = source.length;
  }

  public static ContractViolation invalid(String reason) {
    return new ContractViolation(reason);
  }

  public int position() {
    return position;
  }

  public int limit() {
    return limit;
  }

  public void limit(int end) {
    if (end < position || end > source.length) throw invalid("Invalid generated region");
    limit = end;
  }

  public boolean remaining() {
    return position < limit;
  }

  public void message(int fields, int depth) {
    if (depth < 1 || depth > root.maxDepth()) throw invalid("Protobuf nesting budget exceeded");
    cells(fields);
  }

  public void cells(long count) {
    if (count < 0 || count > root.limits().maxCells() - cells)
      throw invalid("Generated cell budget exceeded");
    cells += count;
  }

  private void bytes(long count) {
    if (count < 0 || count > root.limits().maxStringBytes() - bytes)
      throw invalid("Generated payload budget exceeded");
    bytes += count;
  }

  /** Retained totals are separate from cumulative parsing work, including discarded values. */
  public long retainedCells(long total, long count) {
    if (total < 0 || count < 0 || count > root.limits().maxCells() - total)
      throw invalid("Generated retained cell budget exceeded");
    return total + count;
  }

  public long retainedBytes(long total, long count) {
    if (total < 0 || count < 0 || count > root.limits().maxStringBytes() - total)
      throw invalid("Generated retained payload budget exceeded");
    return total + count;
  }

  /** Only maps need a retained traversal, after duplicate keys have been collapsed. */
  public Footprint mapFootprint(DataArray entries, int depth) {
    var budget = new ProtoRow.Budget(root.limits());
    budget.value(entries, depth, root.maxDepth());
    return new Footprint(budget.cellCount(), budget.byteCount());
  }

  public record Footprint(long cells, long bytes) {}

  public int tag() {
    long tag = varint();
    if (tag < 0 || tag > 0xffffffffL || (tag >>> 3) == 0 || (tag & 7) > 5)
      throw invalid("Invalid wire tag");
    return (int) tag;
  }

  public long varint() {
    long value = 0;
    for (int shift = 0; shift < 64; shift += 7) {
      if (position == limit) throw invalid("Truncated varint");
      int next = source[position++] & 255;
      if (shift == 63 && (next & 254) != 0) throw invalid("Varint overflow");
      value |= (long) (next & 127) << shift;
      if ((next & 128) == 0) return value;
    }
    throw invalid("Malformed varint");
  }

  public long fixed(int width) {
    if ((width != 4 && width != 8) || width > limit - position)
      throw invalid("Truncated fixed field");
    long value = 0;
    for (int i = 0; i < width; i++) value |= (long) (source[position++] & 255) << (8 * i);
    return value;
  }

  private int length() {
    long size = varint();
    if (size < 0 || size > limit - position) throw invalid("Truncated length-delimited field");
    return (int) size;
  }

  public int end() {
    int size = length();
    return position + size;
  }

  public String string() {
    int length = length();
    bytes(2L * GeneratedInput.utf16Length(source, position, length));
    String value = new String(source, position, length, StandardCharsets.UTF_8);
    position += length;
    return value;
  }

  public DataArray bytes() {
    int length = length();
    cells(length);
    DataArray value = DataArray.copyOfBytes(source, position, length);
    position += length;
    return value;
  }

  public ByteArrayOutputStream unknown(
      @Nullable ByteArrayOutputStream out, int start, int tag, int depth) {
    skip(tag, depth);
    return retain(out, start);
  }

  public ByteArrayOutputStream retain(@Nullable ByteArrayOutputStream out, int start) {
    if (start < 0 || start > position) throw invalid("Invalid unknown region");
    bytes(position - start);
    if (out == null) out = new ByteArrayOutputStream();
    out.write(source, start, position - start);
    return out;
  }

  public ByteArrayOutputStream enumeration(
      @Nullable ByteArrayOutputStream out, int number, long value) {
    long tag = (long) number << 3;
    bytes(GeneratedOutput.varintSize(tag) + GeneratedOutput.varintSize(value));
    if (out == null) out = new ByteArrayOutputStream();
    write(out, tag);
    write(out, value);
    return out;
  }

  private static void write(ByteArrayOutputStream out, long value) {
    while ((value & ~127L) != 0) {
      out.write((int) (value & 127) | 128);
      value >>>= 7;
    }
    out.write((int) value);
  }

  private void skip(int tag, int depth) {
    switch (tag & 7) {
      case 0 -> varint();
      case 1 -> fixed(8);
      case 2 -> {
        int count = length();
        position += count;
      }
      case 3 -> {
        if (depth >= root.maxDepth()) throw invalid("Unknown group depth exceeded");
        while (remaining()) {
          int child = tag();
          if ((child & 7) == 4) {
            if ((child >>> 3) != (tag >>> 3)) throw invalid("Mismatched group");
            return;
          }
          skip(child, depth + 1);
        }
        throw invalid("Unterminated group");
      }
      case 5 -> fixed(4);
      default -> throw invalid("Unexpected end group");
    }
  }

  public static byte[] unknownBytes(@Nullable ByteArrayOutputStream out) {
    return out == null ? EMPTY : out.toByteArray();
  }

  /** Map keys use value equality even for the ExactNumber representation. */
  public static DataArray map(List<Object> entries) {
    Map<Object, Object> unique = new LinkedHashMap<>();
    for (Object item : entries) {
      var row = (ProtoAccess) item;
      unique.put(ProtoRow.mapKey(row.get(0)), row);
    }
    return DataArray.copyOf(unique.values());
  }
}
