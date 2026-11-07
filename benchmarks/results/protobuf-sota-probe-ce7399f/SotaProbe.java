package dev.elide.truffledata.comparison;

import com.google.protobuf.SourceContext;
import com.google.protobuf.Syntax;
import com.google.protobuf.Type;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.LongSupplier;

/**
 * Temporary probe for the protobuf SOTA plan (not production code). Pure-Java decoders writing the
 * production {@code TDSLAB1} slab format into a {@code byte[]} (records with handle and presence
 * words, 4-byte slots, string headers with copied payloads, lists), measured against
 * protobuf-java, on the JVM (Graal JIT) and as a Native Image (AOT, no profiles):
 *
 * <ul>
 *   <li>{@code java_gen}: schema-generated style (constant tags, slots and presence bits; one
 *       method per message type), what Task A3's {@code SlabDecoder_<Type>} emits.
 *   <li>{@code java_table}: table-driven over a field table with an explicit record stack, what
 *       the runtime-plan {@code SlabCodec} does.
 * </ul>
 *
 * Both validate strings strictly (ASCII word check, scalar UTF-8 with UTF-16 unit count) and skip
 * unknown fields without preserving them (as the Rust probes do).
 */
public final class SotaProbe {
  static final VarHandle I32 = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
  static final VarHandle I64 = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

  static byte[][] fixture(String mode) {
    byte[][] wires = new byte[32][];
    for (int i = 0; i < 32; i++) {
      var b = Type.newBuilder().setName("catalog-" + i + "-λ").setSyntax(Syntax.SYNTAX_PROTO3);
      int count =
          switch (mode) {
            case "holdout" -> 12 + i % 13;
            case "large" -> 2048 + (i % 8) * 64;
            default -> 3 + i % 6;
          };
      if (i % 3 != 0) b.setSourceContext(SourceContext.newBuilder().setFileName("schema-" + i + ".proto"));
      b.addOneofs("identity");
      if ((i & 1) == 0) b.addOneofs("contact-😀");
      for (int j = 0; j < count; j++)
        b.addFields(
            com.google.protobuf.Field.newBuilder()
                .setName("column-" + j + (mode.equals("holdout") ? "-λ😀" : ""))
                .setNumber(j + 1)
                .setKind(com.google.protobuf.Field.Kind.TYPE_INT64)
                .setCardinality(com.google.protobuf.Field.Cardinality.CARDINALITY_REPEATED)
                .setPacked((i + j) % 3 != 0));
      wires[i] = b.build().toByteArray();
    }
    return wires;
  }

  // ---- slab writer (TDSLAB1 subset) ----
  static final int TYPE_BYTES = 32, FIELD_BYTES = 48, SC_BYTES = 16;

  static class Writer {
    byte[] out = new byte[16 << 20];
    int cursor, handles, records;
    byte[] in;
    int pos;

    final void reset(byte[] input) {
      in = input;
      pos = 0;
      cursor = 32;
      handles = 0;
      records = 0;
    }

    final long varint() {
      byte[] in = this.in;
      int p = pos;
      byte b = in[p++];
      if (b >= 0) {
        pos = p;
        return b;
      }
      byte c = in[p++];
      if (c >= 0) {
        pos = p;
        return (b & 0x7f) | (c << 7);
      }
      long v = (b & 0x7f) | ((c & 0x7f) << 7);
      int shift = 14;
      while (true) {
        b = in[p++];
        v |= (long) (b & 0x7f) << shift;
        if (b >= 0) {
          pos = p;
          return v;
        }
        shift += 7;
        if (shift > 63) throw new IllegalStateException("varint");
      }
    }

    final int length(int end) {
      int n = (int) varint();
      if (n < 0 || n > end - pos) throw new IllegalStateException("length");
      return n;
    }

    final void skip(int tag, int end) {
      switch (tag & 7) {
        case 0 -> varint();
        case 1 -> pos += 8;
        case 2 -> pos += length(end);
        case 5 -> pos += 4;
        default -> throw new IllegalStateException("wire");
      }
      if (pos > end) throw new IllegalStateException("overrun");
    }

    final int record(int bytes) {
      int at = (cursor + 7) & ~7;
      byte[] out = this.out;
      if (at + bytes + 8 > out.length) throw new IllegalStateException("capacity");
      if (at != cursor) I64.set(out, cursor, 0L);
      Arrays.fill(out, at, at + bytes, (byte) 0);
      I32.set(out, at, handles++);
      records++;
      cursor = at + bytes;
      return at;
    }

    /** Strict UTF-8 validation with UTF-16 unit count; returns units, or -1 for ASCII. */
    static int validate(byte[] in, int s, int n) {
      int i = s, end = s + n;
      while (i + 8 <= end) {
        if (((long) I64.get(in, i) & 0x8080808080808080L) != 0) break;
        i += 8;
      }
      while (i < end && in[i] >= 0) i++;
      if (i == end) return -1;
      int units = i - s;
      while (i < end) {
        int b = in[i] & 0xff;
        if (b < 0x80) {
          i++;
          units++;
          continue;
        }
        int width, low, high;
        if (b >= 0xc2 && b <= 0xdf) { width = 2; low = 0x80; high = 0xbf; }
        else if (b == 0xe0) { width = 3; low = 0xa0; high = 0xbf; }
        else if ((b >= 0xe1 && b <= 0xec) || b == 0xee || b == 0xef) { width = 3; low = 0x80; high = 0xbf; }
        else if (b == 0xed) { width = 3; low = 0x80; high = 0x9f; }
        else if (b == 0xf0) { width = 4; low = 0x90; high = 0xbf; }
        else if (b >= 0xf1 && b <= 0xf3) { width = 4; low = 0x80; high = 0xbf; }
        else if (b == 0xf4) { width = 4; low = 0x80; high = 0x8f; }
        else throw new IllegalStateException("utf8");
        if (i + 1 >= end) throw new IllegalStateException("utf8");
        int b1 = in[i + 1] & 0xff;
        if (b1 < low || b1 > high) throw new IllegalStateException("utf8");
        for (int k = 2; k < width; k++) {
          if (i + k >= end || (in[i + k] & 0xc0) != 0x80) throw new IllegalStateException("utf8");
        }
        i += width;
        units += width == 4 ? 2 : 1;
      }
      return units;
    }

    /** String header and payload copy at the 4-aligned cursor; returns the header offset. */
    final int string(int s, int n) {
      int units = validate(in, s, n);
      int header = units < 0 ? 8 : 12;
      int h = (cursor + 3) & ~3;
      byte[] out = this.out;
      int end = h + header + n;
      if (end + 8 > out.length) throw new IllegalStateException("capacity");
      if (h != cursor) I32.set(out, cursor, 0);
      I32.set(out, h, units < 0 ? n | 0x80000000 : n);
      I32.set(out, h + 4, handles++);
      if (units >= 0) I32.set(out, h + 8, units);
      System.arraycopy(in, s, out, h + header, n);
      I32.set(out, end, 0);
      cursor = end;
      return h;
    }

    final int list(int[] elements, int count) {
      int at = (cursor + 7) & ~7;
      byte[] out = this.out;
      if (at + 8 + 4 * count + 8 > out.length) throw new IllegalStateException("capacity");
      if (at != cursor) I64.set(out, cursor, 0L);
      I32.set(out, at, count);
      I32.set(out, at + 4, 0);
      for (int k = 0; k < count; k++) I32.set(out, at + 8 + 4 * k, elements[k]);
      cursor = at + 8 + 4 * count;
      return at;
    }

    final int finish() {
      int bytes = (cursor + 7) & ~7;
      byte[] out = this.out;
      I32.set(out, 0, 0x31534454);
      I32.set(out, 4, 1 | 8);
      I32.set(out, 8, bytes);
      I32.set(out, 12, records);
      I32.set(out, 16, handles);
      I32.set(out, 20, 32);
      return bytes;
    }
  }

  // ---- java_gen: schema-generated style ----
  static final class Gen extends Writer {
    int[] fields = new int[4096];
    int[] oneofs = new int[16];

    int field(int end) {
      int rec = record(FIELD_BYTES);
      int presence = 0;
      int options = 0;
      byte[] out = this.out;
      while (pos < end) {
        int tag = (int) varint();
        switch (tag) {
          case 0x08 -> { int v = (int) varint(); I32.set(out, rec + 8, v); if (v != 0) presence |= 1; }
          case 0x10 -> { int v = (int) varint(); I32.set(out, rec + 12, v); if (v != 0) presence |= 2; }
          case 0x18 -> { int v = (int) varint(); I32.set(out, rec + 16, v); if (v != 0) presence |= 4; }
          case 0x22 -> { int n = length(end); int h = string(pos, n); pos += n; I32.set(out, rec + 20, h); if (n != 0) presence |= 8; }
          case 0x32 -> { int n = length(end); int h = string(pos, n); pos += n; I32.set(out, rec + 24, h); if (n != 0) presence |= 16; }
          case 0x38 -> { int v = (int) varint(); I32.set(out, rec + 28, v); if (v != 0) presence |= 32; }
          case 0x40 -> { long v = varint(); I32.set(out, rec + 32, v != 0 ? 1 : 0); if (v != 0) presence |= 64; }
          case 0x4a -> { int n = length(end); pos += n; options++; presence |= 128; }
          case 0x52 -> { int n = length(end); int h = string(pos, n); pos += n; I32.set(out, rec + 40, h); if (n != 0) presence |= 256; }
          case 0x5a -> { int n = length(end); int h = string(pos, n); pos += n; I32.set(out, rec + 44, h); if (n != 0) presence |= 512; }
          default -> skip(tag, end);
        }
      }
      if (pos != end) throw new IllegalStateException("overrun");
      if (options != 0) I32.set(out, rec + 36, options);
      I32.set(out, rec + 4, presence);
      return rec;
    }

    int sourceContext(int end) {
      int rec = record(SC_BYTES);
      int presence = 0;
      while (pos < end) {
        int tag = (int) varint();
        if (tag == 0x0a) { int n = length(end); int h = string(pos, n); pos += n; I32.set(out, rec + 8, h); if (n != 0) presence |= 1; }
        else skip(tag, end);
      }
      I32.set(out, rec + 4, presence);
      return rec;
    }

    int type(byte[] in) {
      reset(in);
      int rec = record(TYPE_BYTES);
      int presence = 0;
      int fieldCount = 0, oneofCount = 0, options = 0;
      int end = in.length;
      while (pos < end) {
        int tag = (int) varint();
        switch (tag) {
          case 0x0a -> { int n = length(end); int h = string(pos, n); pos += n; I32.set(out, rec + 8, h); if (n != 0) presence |= 1; }
          case 0x12 -> { int n = length(end); fields[fieldCount++] = field(pos + n); presence |= 2; }
          case 0x1a -> { int n = length(end); int h = string(pos, n); pos += n; oneofs[oneofCount++] = h; presence |= 4; }
          case 0x22 -> { int n = length(end); pos += n; options++; presence |= 8; }
          case 0x2a -> { int n = length(end); int c = sourceContext(pos + n); I32.set(out, rec + 24, c); presence |= 16; }
          case 0x30 -> { int v = (int) varint(); I32.set(out, rec + 28, v); if (v != 0) presence |= 32; }
          default -> skip(tag, end);
        }
      }
      if (fieldCount > 0) I32.set(out, rec + 12, list(fields, fieldCount));
      if (oneofCount > 0) I32.set(out, rec + 16, list(oneofs, oneofCount));
      if (options != 0) I32.set(out, rec + 20, options);
      I32.set(out, rec + 4, presence);
      return finish();
    }
  }

  // ---- java_table: table-driven with an explicit record stack ----
  static final int K_VARINT32 = 0, K_BOOL = 2, K_STRING = 3, K_MESSAGE = 4, K_RMESSAGE = 5, K_RSTRING = 6, K_RVARINT = 7, K_RMESSAGE_SKIP = 10;

  static final class Table {
    final int[] tag, slot, child, kind, next;
    final int[] lookup = new int[32];
    final int recordBytes;

    Table(int recordBytes, int[][] specs) {
      int n = specs.length;
      tag = new int[n]; slot = new int[n]; child = new int[n]; kind = new int[n]; next = new int[n];
      this.recordBytes = recordBytes;
      for (int i = 0; i < n; i++) {
        int number = specs[i][0], k = specs[i][1];
        int wt = (k == K_STRING || k == K_MESSAGE || k == K_RMESSAGE || k == K_RSTRING || k == K_RMESSAGE_SKIP) ? 2 : 0;
        boolean repeated = k == K_RMESSAGE || k == K_RSTRING || k == K_RVARINT || k == K_RMESSAGE_SKIP;
        tag[i] = number << 3 | wt;
        slot[i] = 8 + 4 * i;
        child[i] = specs[i][2];
        kind[i] = k;
        next[i] = repeated ? i : Math.min(i + 1, n - 1);
        lookup[number] = i + 1;
      }
    }
  }

  static final class Tab extends Writer {
    final Table[] tables = {
      new Table(TYPE_BYTES, new int[][] {{1, K_STRING, 0}, {2, K_RMESSAGE, 1}, {3, K_RSTRING, 0}, {4, K_RMESSAGE_SKIP, 3}, {5, K_MESSAGE, 2}, {6, K_VARINT32, 0}}),
      new Table(FIELD_BYTES, new int[][] {{1, K_VARINT32, 0}, {2, K_VARINT32, 0}, {3, K_VARINT32, 0}, {4, K_STRING, 0}, {6, K_STRING, 0}, {7, K_VARINT32, 0}, {8, K_BOOL, 0}, {9, K_RMESSAGE_SKIP, 3}, {10, K_STRING, 0}, {11, K_STRING, 0}}),
      new Table(SC_BYTES, new int[][] {{1, K_STRING, 0}}),
      new Table(16, new int[][] {{1, K_STRING, 0}}),
    };
    // frames: ty, end, rec, presence, next, mark, ordinal, singular
    final int[] frames = new int[64 * 8];
    final int[] pendingOrdinal = new int[4096];
    final int[] pendingValue = new int[4096];
    int pendingLen;
    final int[] tmp = new int[4096];

    int closeLists(Table t, int rec, int mark, int presence) {
      int n = pendingLen;
      int k = mark;
      int written = 0;
      while (k < n) {
        int ordinal = pendingOrdinal[k];
        int start = k;
        while (k < n && pendingOrdinal[k] == ordinal) k++;
        if ((written & (1 << ordinal)) != 0) throw new IllegalStateException("interleaved");
        written |= 1 << ordinal;
        int count = k - start;
        System.arraycopy(pendingValue, start, tmp, 0, count);
        int at = list(tmp, count);
        I32.set(out, rec + t.slot[ordinal], at);
        presence |= 1 << ordinal;
      }
      pendingLen = mark;
      return presence;
    }

    int decode(byte[] in) {
      reset(in);
      pendingLen = 0;
      int depth = 0;
      Table t = tables[0];
      int ty = 0;
      int rec = record(t.recordBytes);
      int presence = 0;
      int end = in.length;
      int next = 0;
      int mark = 0;
      byte[] out = this.out;
      while (true) {
        if (pos >= end) {
          if (pendingLen > mark) presence = closeLists(t, rec, mark, presence);
          I32.set(out, rec + 4, presence);
          if (depth == 0) return finish();
          depth--;
          int f = depth * 8;
          int child = rec;
          ty = frames[f];
          t = tables[ty];
          end = frames[f + 1];
          rec = frames[f + 2];
          presence = frames[f + 3];
          next = frames[f + 4];
          mark = frames[f + 5];
          int ordinal = frames[f + 6];
          if (frames[f + 7] != 0) {
            I32.set(out, rec + t.slot[ordinal], child);
            presence |= 1 << ordinal;
          } else {
            pendingOrdinal[pendingLen] = ordinal;
            pendingValue[pendingLen++] = child;
          }
          continue;
        }
        int raw = (int) varint();
        int e;
        if (t.tag[next] == raw) {
          e = next;
        } else {
          int number = raw >>> 3;
          int idx = number < 32 ? t.lookup[number] : 0;
          if (idx == 0) { skip(raw, end); continue; }
          e = idx - 1;
          if (t.tag[e] != raw) {
            if ((raw & 7) == 2 && t.kind[e] == K_RVARINT) {
              int n = length(end);
              int packedEnd = pos + n;
              while (pos < packedEnd) { pendingOrdinal[pendingLen] = e; pendingValue[pendingLen++] = (int) varint(); }
              next = e;
              continue;
            }
            skip(raw, end);
            continue;
          }
        }
        next = t.next[e];
        int slot = rec + t.slot[e];
        switch (t.kind[e]) {
          case K_VARINT32 -> { int v = (int) varint(); I32.set(out, slot, v); if (v != 0) presence |= 1 << e; }
          case K_BOOL -> { long v = varint(); I32.set(out, slot, v != 0 ? 1 : 0); if (v != 0) presence |= 1 << e; }
          case K_STRING -> { int n = length(end); int h = string(pos, n); pos += n; I32.set(out, slot, h); if (n != 0) presence |= 1 << e; }
          case K_RSTRING -> { int n = length(end); int h = string(pos, n); pos += n; pendingOrdinal[pendingLen] = e; pendingValue[pendingLen++] = h; }
          case K_RVARINT -> { int v = (int) varint(); pendingOrdinal[pendingLen] = e; pendingValue[pendingLen++] = v; }
          case K_RMESSAGE_SKIP -> { int n = length(end); pos += n; I32.set(out, slot, (int) I32.get(out, slot) + 1); presence |= 1 << e; }
          case K_MESSAGE, K_RMESSAGE -> {
            int n = length(end);
            if (depth >= 63) throw new IllegalStateException("depth");
            int f = depth * 8;
            frames[f] = ty; frames[f + 1] = end; frames[f + 2] = rec; frames[f + 3] = presence;
            frames[f + 4] = next; frames[f + 5] = mark; frames[f + 6] = e; frames[f + 7] = t.kind[e] == K_MESSAGE ? 1 : 0;
            depth++;
            ty = t.child[e];
            t = tables[ty];
            rec = record(t.recordBytes);
            presence = 0;
            end = pos + n;
            next = 0;
            mark = pendingLen;
          }
          default -> throw new IllegalStateException("kind");
        }
      }
    }
  }

  static long sink;

  static void bench(String name, long bytes, int reps, LongSupplier f) {
    long best = Long.MAX_VALUE;
    for (int round = 0; round < 10; round++) {
      long start = System.nanoTime();
      long s = 0;
      for (int r = 0; r < reps; r++) s += f.getAsLong();
      long ns = System.nanoTime() - start;
      sink += s;
      if (ns < best) best = ns;
    }
    double per = (double) best / reps;
    System.out.printf("PROBE %-28s %12.1f ns/rep %9.1f ns/msg %9.1f MB/s%n", name, per, per / 32.0, bytes / per * 1e3);
  }

  static void runMode(String mode, int reps, boolean stock) {
    byte[][] msgs = fixture(mode);
    long bytes = 0;
    for (var m : msgs) bytes += m.length;
    System.out.printf("PROBE mode=%s messages=32 bytes=%d avg=%d%n", mode, bytes, bytes / 32);
    Gen gen = new Gen();
    Tab tab = new Tab();
    long genBytes = 0, tabBytes = 0;
    for (var m : msgs) {
      genBytes += gen.type(m);
      tabBytes += tab.decode(m);
    }
    System.out.printf("PROBE %s/sizes message[1]=%d B java_gen=%d B java_table=%d B all=%d %d%n", mode, msgs[1].length, gen.type(msgs[1]), tab.decode(msgs[1]), genBytes, tabBytes);
    for (int warm = 0; warm < 3; warm++) {
      if (stock)
        bench(mode + "/stock_parse", bytes, reps, () -> {
          long s = 0;
          for (var m : msgs) {
            try { Type t = Type.parseFrom(m); s += t.getFieldsCount() + t.getName().length(); } catch (Exception e) { throw new RuntimeException(e); }
          }
          return s;
        });
      bench(mode + "/java_gen", bytes, reps, () -> {
        long s = 0;
        for (var m : msgs) s += gen.type(m) + gen.out[40];
        return s;
      });
      bench(mode + "/java_table", bytes, reps, () -> {
        long s = 0;
        for (var m : msgs) s += tab.decode(m) + tab.out[40];
        return s;
      });
    }
  }

  public static void main(String[] args) {
    System.out.println("PROBE jvm=" + System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version") + " image=" + System.getProperty("org.graalvm.nativeimage.imagecode"));
    try {
      var cpu = Files.readAllLines(Path.of("/proc/cpuinfo")).stream().filter(l -> l.startsWith("model name")).findFirst().orElse("");
      System.out.println("PROBE host=" + cpu);
    } catch (Exception ignored) {
    }
    boolean stock = args.length == 0 || !args[0].equals("nostock");
    runMode("small", 2000, stock);
    runMode("holdout", 1000, stock);
    runMode("large", 8, stock);
    System.out.println("PROBE sink=" + sink);
  }
}
