package dev.elide.truffledata.protobuf;

import com.oracle.truffle.api.CompilerDirectives;
import dev.elide.truffledata.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/** Bounded cursor for generated codecs. It never escapes into the decoded message. */
public final class GeneratedInput {
  private static final byte[] EMPTY = new byte[0];
  private final byte[] source;
  private final ProtoPlan plan;
  private long cells;
  private long stringBytes;
  private int position;
  private @Nullable ByteArrayOutputStream unknown;

  public GeneratedInput(ProtoPlan plan, byte[] source) {
    if (source.length > plan.maxWireBytes()) throw bad("Wire byte budget exceeded");
    this.source = source;
    this.plan = plan;
    cells = plan.size();
    chargeCells(0);
  }

  private static ContractViolation bad(String reason) {
    CompilerDirectives.transferToInterpreterAndInvalidate();
    return new ContractViolation(reason);
  }

  public static int zigzag32(long value) {
    int n = (int) value;
    return (n >>> 1) ^ -(n & 1);
  }

  public static long zigzag64(long value) {
    return (value >>> 1) ^ -(value & 1);
  }

  public static Object unsigned(long value) {
    return Values.normalize(
        value >= 0
            ? java.math.BigInteger.valueOf(value)
            : java.math.BigInteger.valueOf(value & Long.MAX_VALUE).setBit(63));
  }

  public boolean remaining() {
    return position < source.length;
  }

  public int position() {
    return position;
  }

  public int tag() {
    long value = varint();
    if (value < 0 || value > 0xffffffffL || (value >>> 3) == 0 || (value & 7) > 5)
      throw bad("Invalid wire tag");
    return (int) value;
  }

  public long varint() {
    long value = 0;
    for (int shift = 0; shift < 64; shift += 7) {
      if (!remaining()) throw bad("Truncated varint");
      int next = source[position++] & 255;
      if (shift == 63 && (next & 254) != 0) throw bad("Varint overflow");
      value |= (long) (next & 127) << shift;
      if ((next & 128) == 0) return value;
    }
    throw bad("Malformed varint");
  }

  public long fixed(int width) {
    if ((width != 4 && width != 8) || width > source.length - position)
      throw bad("Truncated fixed field");
    long value = 0;
    for (int i = 0; i < width; i++) value |= (long) (source[position++] & 255) << (i * 8);
    return value;
  }

  private int length() {
    long count = varint();
    if (count < 0 || count > source.length - position)
      throw bad("Truncated length-delimited field");
    return (int) count;
  }

  public String string() {
    int length = length();
    chargeBytes(2L * utf16Length(source, position, length));
    String value = new String(source, position, length, StandardCharsets.UTF_8);
    position += length;
    return value;
  }

  static int utf16Length(byte[] source, int start, int count) {
    int cursor = start, end = start + count, units = 0;
    while (cursor < end) {
      int first = source[cursor++] & 255;
      if (first < 128) {
        units++;
        continue;
      }
      int tails, code, minimum;
      if (first >= 0xc2 && first <= 0xdf) {
        tails = 1;
        code = first & 31;
        minimum = 0x80;
      } else if (first >= 0xe0 && first <= 0xef) {
        tails = 2;
        code = first & 15;
        minimum = 0x800;
      } else if (first >= 0xf0 && first <= 0xf4) {
        tails = 3;
        code = first & 7;
        minimum = 0x10000;
      } else throw bad("Invalid UTF-8 leading byte");
      if (tails > end - cursor) throw bad("Truncated UTF-8");
      for (int i = 0; i < tails; i++) {
        int next = source[cursor++] & 255;
        if ((next & 0xc0) != 0x80) throw bad("Invalid UTF-8 continuation");
        code = code << 6 | next & 63;
      }
      if (code < minimum || code > 0x10ffff || (code >= 0xd800 && code <= 0xdfff))
        throw bad("Invalid Unicode scalar");
      units += code >= 0x10000 ? 2 : 1;
    }
    return units;
  }

  public DataArray bytes() {
    int length = length();
    chargeCells(length);
    DataArray result = DataArray.copyOfBytes(source, position, length);
    position += length;
    return result;
  }

  private void chargeCells(long count) {
    if (count > plan.limits().maxCells() - cells) throw bad("Generated cell budget exceeded");
    cells += count;
  }

  private void chargeBytes(long count) {
    if (count > plan.limits().maxStringBytes() - stringBytes)
      throw bad("Generated payload budget exceeded");
    stringBytes += count;
  }

  public void unknown(int start, int tag) {
    skip(tag, 1);
    retain(start);
  }

  /** Retain a consumed closed-enum value as an unknown field. */
  public void retain(int start) {
    if (start < 0 || start > position) throw new IllegalArgumentException("Invalid offset");
    chargeBytes(position - start);
    if (unknown == null) unknown = new ByteArrayOutputStream();
    unknown.write(source, start, position - start);
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
        if (depth >= plan.maxDepth()) throw bad("Unknown group depth exceeded");
        while (remaining()) {
          int child = tag();
          if ((child & 7) == 4) {
            if ((child >>> 3) != (tag >>> 3)) throw bad("Mismatched group");
            return;
          }
          skip(child, depth + 1);
        }
        throw bad("Unterminated group");
      }
      case 5 -> fixed(4);
      default -> throw bad("Unexpected end group");
    }
  }

  public byte[] unknownFields() {
    return unknown == null ? EMPTY : unknown.toByteArray();
  }
}
