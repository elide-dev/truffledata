package dev.elide.truffledata.comparison;

import com.google.protobuf.SourceContext;
import com.google.protobuf.Syntax;
import com.google.protobuf.Type;
import dev.elide.truffledata.Limits;
import dev.elide.truffledata.generated.ComparisonContracts;
import dev.elide.truffledata.protobuf.SlabCodec;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.IntToLongFunction;
import org.graalvm.nativeimage.ImageInfo;
import org.jspecify.annotations.NullUnmarked;

/**
 * A3 writer diagnosis. Probe writers reuse storage; production writers return fresh owning slabs.
 */
@NullUnmarked
public final class SlabWriterProbe {
  static final VarHandle I32 =
      MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
  static final VarHandle I64 =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
  static final int VARIANT = Integer.getInteger("slab.writer.variant", 0);
  static volatile Object retained;
  static volatile long checksum;

  static void put32(byte[] out, int at, int value) {
    if (VARIANT != 3) {
      I32.set(out, at, value);
      return;
    }
    out[at] = (byte) value;
    out[at + 1] = (byte) (value >>> 8);
    out[at + 2] = (byte) (value >>> 16);
    out[at + 3] = (byte) (value >>> 24);
  }

  static void put64(byte[] out, int at, long value) {
    if (VARIANT != 3) {
      I64.set(out, at, value);
      return;
    }
    put32(out, at, (int) value);
    put32(out, at + 4, (int) (value >>> 32));
  }

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
      if (i % 3 != 0)
        b.setSourceContext(SourceContext.newBuilder().setFileName("schema-" + i + ".proto"));
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
  // Current Type includes the default edition slot; the retained 2026-11-07 probe used 32.
  static final int TYPE_BYTES = 40, FIELD_BYTES = 48, SC_BYTES = 16;

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
      if (VARIANT == 4) {
        byte[] input = in;
        byte first = input[pos++];
        if (first >= 0) return first;
        byte second = input[pos++];
        if (second >= 0) return (first & 0x7f) | (second << 7);
        long value = (first & 0x7f) | ((second & 0x7f) << 7);
        int shift = 14;
        while (true) {
          byte next = input[pos++];
          value |= (long) (next & 0x7f) << shift;
          if (next >= 0) return value;
          shift += 7;
          if (shift > 63) throw new IllegalStateException("varint");
        }
      }
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
      if (at != cursor) put64(out, cursor, 0L);
      if (VARIANT == 2) for (int p = at; p < at + bytes; p += 8) put64(out, p, 0L);
      else Arrays.fill(out, at, at + bytes, (byte) 0);
      put32(out, at, handles++);
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
        if (b >= 0xc2 && b <= 0xdf) {
          width = 2;
          low = 0x80;
          high = 0xbf;
        } else if (b == 0xe0) {
          width = 3;
          low = 0xa0;
          high = 0xbf;
        } else if ((b >= 0xe1 && b <= 0xec) || b == 0xee || b == 0xef) {
          width = 3;
          low = 0x80;
          high = 0xbf;
        } else if (b == 0xed) {
          width = 3;
          low = 0x80;
          high = 0x9f;
        } else if (b == 0xf0) {
          width = 4;
          low = 0x90;
          high = 0xbf;
        } else if (b >= 0xf1 && b <= 0xf3) {
          width = 4;
          low = 0x80;
          high = 0xbf;
        } else if (b == 0xf4) {
          width = 4;
          low = 0x80;
          high = 0x8f;
        } else throw new IllegalStateException("utf8");
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
      if (h != cursor) put32(out, cursor, 0);
      put32(out, h, units < 0 ? n | 0x80000000 : n);
      put32(out, h + 4, handles++);
      if (units >= 0) put32(out, h + 8, units);
      if (VARIANT == 1 && n <= 16) {
        if (n >= 8) {
          I64.set(out, h + header, (long) I64.get(in, s));
          I64.set(out, h + header + n - 8, (long) I64.get(in, s + n - 8));
        } else for (int j = 0; j < n; j++) out[h + header + j] = in[s + j];
      } else System.arraycopy(in, s, out, h + header, n);
      put32(out, end, 0);
      cursor = end;
      return h;
    }

    final int list(int[] elements, int count) {
      int at = (cursor + 7) & ~7;
      byte[] out = this.out;
      if (at + 8 + 4 * count + 8 > out.length) throw new IllegalStateException("capacity");
      if (at != cursor) put64(out, cursor, 0L);
      put32(out, at, count);
      put32(out, at + 4, 0);
      for (int k = 0; k < count; k++) put32(out, at + 8 + 4 * k, elements[k]);
      cursor = at + 8 + 4 * count;
      return at;
    }

    final int finish() {
      int bytes = (cursor + 7) & ~7;
      byte[] out = this.out;
      if (bytes != cursor) I64.set(out, cursor, 0L);
      put32(out, 0, 0x31534454);
      put32(out, 4, 1 | 8);
      put32(out, 8, bytes);
      put32(out, 12, records);
      put32(out, 16, handles);
      put32(out, 20, 32);
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
          case 0x08 -> {
            int v = (int) varint();
            put32(out, rec + 8, v);
            if (v != 0) presence |= 1;
          }
          case 0x10 -> {
            int v = (int) varint();
            put32(out, rec + 12, v);
            if (v != 0) presence |= 2;
          }
          case 0x18 -> {
            int v = (int) varint();
            put32(out, rec + 16, v);
            if (v != 0) presence |= 4;
          }
          case 0x22 -> {
            int n = length(end);
            int h = string(pos, n);
            pos += n;
            put32(out, rec + 20, h);
            if (n != 0) presence |= 8;
          }
          case 0x32 -> {
            int n = length(end);
            int h = string(pos, n);
            pos += n;
            put32(out, rec + 24, h);
            if (n != 0) presence |= 16;
          }
          case 0x38 -> {
            int v = (int) varint();
            put32(out, rec + 28, v);
            if (v != 0) presence |= 32;
          }
          case 0x40 -> {
            long v = varint();
            put32(out, rec + 32, v != 0 ? 1 : 0);
            if (v != 0) presence |= 64;
          }
          case 0x4a -> {
            int n = length(end);
            pos += n;
            options++;
            presence |= 128;
          }
          case 0x52 -> {
            int n = length(end);
            int h = string(pos, n);
            pos += n;
            put32(out, rec + 40, h);
            if (n != 0) presence |= 256;
          }
          case 0x5a -> {
            int n = length(end);
            int h = string(pos, n);
            pos += n;
            put32(out, rec + 44, h);
            if (n != 0) presence |= 512;
          }
          default -> skip(tag, end);
        }
      }
      if (pos != end) throw new IllegalStateException("overrun");
      if (options != 0) put32(out, rec + 36, options);
      put32(out, rec + 4, presence);
      return rec;
    }

    int sourceContext(int end) {
      int rec = record(SC_BYTES);
      int presence = 0;
      while (pos < end) {
        int tag = (int) varint();
        if (tag == 0x0a) {
          int n = length(end);
          int h = string(pos, n);
          pos += n;
          put32(out, rec + 8, h);
          if (n != 0) presence |= 1;
        } else skip(tag, end);
      }
      put32(out, rec + 4, presence);
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
          case 0x0a -> {
            int n = length(end);
            int h = string(pos, n);
            pos += n;
            put32(out, rec + 8, h);
            if (n != 0) presence |= 1;
          }
          case 0x12 -> {
            int n = length(end);
            fields[fieldCount++] = field(pos + n);
            presence |= 2;
          }
          case 0x1a -> {
            int n = length(end);
            int h = string(pos, n);
            pos += n;
            oneofs[oneofCount++] = h;
            presence |= 4;
          }
          case 0x22 -> {
            int n = length(end);
            pos += n;
            options++;
            presence |= 8;
          }
          case 0x2a -> {
            int n = length(end);
            int c = sourceContext(pos + n);
            put32(out, rec + 24, c);
            presence |= 16;
          }
          case 0x30 -> {
            int v = (int) varint();
            put32(out, rec + 28, v);
            if (v != 0) presence |= 32;
          }
          default -> skip(tag, end);
        }
      }
      if (fieldCount > 0) put32(out, rec + 12, list(fields, fieldCount));
      if (oneofCount > 0) put32(out, rec + 16, list(oneofs, oneofCount));
      if (options != 0) put32(out, rec + 20, options);
      put32(out, rec + 4, presence);
      return finish();
    }
  }

  static final class Span {
    long[] words = new long[1 << 16];
    int top;
    long[] fields = new long[4096];
    int fieldCount;
    long[] oneofs = new long[16];
    int oneofCount;
    byte[] input;
    int pos;
    boolean validate;

    int alloc(int n) {
      int at = top;
      if (at + n > words.length) words = java.util.Arrays.copyOf(words, words.length * 2);
      java.util.Arrays.fill(words, at, at + n, 0L);
      top = at + n;
      return at;
    }

    long varint() {
      byte[] in = input;
      int p = pos;
      byte b = in[p++];
      if (b >= 0) {
        pos = p;
        return b;
      }
      long v = b & 0x7f;
      int shift = 7;
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

    static void text(byte[] in, int s, int n, boolean validate) {
      if (!validate) return;
      int i = s, end = s + n;
      while (i < end && in[i] >= 0) i++;
      if (i == end) return;
      // Non-ASCII tail: scalar UTF-8 validation.
      while (i < end) {
        int b = in[i] & 0xff;
        int need;
        if (b < 0x80) need = 0;
        else if (b >= 0xc2 && b < 0xe0) need = 1;
        else if (b >= 0xe0 && b < 0xf0) need = 2;
        else if (b >= 0xf0 && b < 0xf5) need = 3;
        else throw new IllegalStateException("utf8");
        if (i + need >= end) throw new IllegalStateException("utf8");
        for (int k = 1; k <= need; k++)
          if ((in[i + k] & 0xc0) != 0x80) throw new IllegalStateException("utf8");
        i += need + 1;
      }
    }

    int skip(int wt) {
      switch (wt) {
        case 0 -> varint();
        case 1 -> pos += 8;
        case 2 -> pos += (int) varint();
        case 5 -> pos += 4;
        default -> throw new IllegalStateException("wire");
      }
      return pos;
    }

    int field(int end, int base) {
      int record = alloc(11);
      long presence = 0;
      long options = 0;
      while (pos < end) {
        long tag = varint();
        switch ((int) tag) {
          case 0x08 -> {
            words[record + 1] = varint();
            presence |= 2;
          }
          case 0x10 -> {
            words[record + 2] = varint();
            presence |= 4;
          }
          case 0x18 -> {
            words[record + 3] = varint();
            presence |= 8;
          }
          case 0x22 -> {
            int n = (int) varint();
            text(input, pos, n, validate);
            words[record + 4] = ((long) (pos) << 32) | n;
            pos += n;
            presence |= 16;
          }
          case 0x32 -> {
            int n = (int) varint();
            text(input, pos, n, validate);
            words[record + 5] = ((long) (pos) << 32) | n;
            pos += n;
            presence |= 32;
          }
          case 0x38 -> {
            words[record + 6] = varint();
            presence |= 64;
          }
          case 0x40 -> {
            words[record + 7] = varint();
            presence |= 128;
          }
          case 0x4a -> {
            int n = (int) varint();
            pos += n;
            options++;
            presence |= 256;
          }
          case 0x52 -> {
            int n = (int) varint();
            text(input, pos, n, validate);
            words[record + 9] = ((long) (pos) << 32) | n;
            pos += n;
            presence |= 512;
          }
          case 0x5a -> {
            int n = (int) varint();
            text(input, pos, n, validate);
            words[record + 10] = ((long) (pos) << 32) | n;
            pos += n;
            presence |= 1024;
          }
          default -> skip((int) tag & 7);
        }
      }
      if (pos != end) throw new IllegalStateException("overrun");
      words[record + 8] = options;
      words[record] = presence;
      return record;
    }

    int type(byte[] in, boolean validate) {
      this.input = in;
      this.pos = 0;
      this.validate = validate;
      this.top = 0;
      fieldCount = 0;
      oneofCount = 0;
      int record = alloc(7);
      long presence = 0;
      int end = in.length;
      while (pos < end) {
        long tag = varint();
        switch ((int) tag) {
          case 0x0a -> {
            int n = (int) varint();
            text(in, pos, n, validate);
            words[record + 1] = ((long) pos << 32) | n;
            pos += n;
            presence |= 2;
          }
          case 0x12 -> {
            int n = (int) varint();
            int child = field(pos + n, 0);
            fields[fieldCount++] = child;
            presence |= 4;
          }
          case 0x1a -> {
            int n = (int) varint();
            text(in, pos, n, validate);
            oneofs[oneofCount++] = ((long) pos << 32) | n;
            pos += n;
            presence |= 8;
          }
          case 0x22 -> {
            int n = (int) varint();
            pos += n;
            words[record + 4]++;
            presence |= 16;
          }
          case 0x2a -> {
            int n = (int) varint();
            int child = alloc(2);
            int subEnd = pos + n;
            while (pos < subEnd) {
              long t = varint();
              if (t == 0x0a) {
                int nn = (int) varint();
                text(in, pos, nn, validate);
                words[child + 1] = ((long) pos << 32) | nn;
                pos += nn;
                words[child] = 2;
              } else skip((int) t & 7);
            }
            words[record + 5] = child;
            presence |= 32;
          }
          case 0x30 -> {
            words[record + 6] = varint();
            presence |= 64;
          }
          default -> skip((int) tag & 7);
        }
      }
      if (fieldCount > 0) {
        int at = alloc(1 + fieldCount);
        words[at] = fieldCount;
        System.arraycopy(fields, 0, words, at + 1, fieldCount);
        words[record + 2] = at;
      }
      if (oneofCount > 0) {
        int at = alloc(1 + oneofCount);
        words[at] = oneofCount;
        System.arraycopy(oneofs, 0, words, at + 1, oneofCount);
        words[record + 3] = at;
      }
      words[record] = presence;
      return record;
    }
  }

  static void measure(String mode, String name, byte[][] wires, IntToLongFunction operation) {
    long warmup = Long.getLong("slab.probe.warmupMillis", 6000) * 1_000_000;
    long duration = Long.getLong("slab.probe.sampleMillis", 200) * 1_000_000;
    long until = System.nanoTime() + warmup;
    long sum = 0;
    while (System.nanoTime() < until) for (int i = 0; i < 32; i++) sum += operation.applyAsLong(i);
    var memory =
        (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    if (!memory.isThreadAllocatedMemorySupported())
      throw new AssertionError("Allocation counter unavailable");
    memory.setThreadAllocatedMemoryEnabled(true);
    long calibration = memory.getThreadAllocatedBytes(Thread.currentThread().threadId());
    retained = new byte[1 << 20];
    if (calibration < 0
        || memory.getThreadAllocatedBytes(Thread.currentThread().threadId()) - calibration
            < 1 << 20) throw new AssertionError("Allocation counter calibration failed");
    double[] ns = new double[7], bytes = new double[7];
    for (int sample = 0; sample < ns.length; sample++) {
      long count = 0, before = memory.getThreadAllocatedBytes(Thread.currentThread().threadId());
      long start = System.nanoTime(), end;
      do {
        for (int i = 0; i < 32; i++) sum += operation.applyAsLong(i);
        count += 32;
        end = System.nanoTime();
      } while (end - start < duration);
      ns[sample] = (double) (end - start) / count;
      bytes[sample] =
          (double) (memory.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before)
              / count;
    }
    checksum += sum;
    System.out.printf(
        Locale.ROOT,
        "{\"mode\":\"%s\",\"case\":\"%s\",\"variant\":%d,\"runtime\":\"%s\",\"input_bytes\":%.1f,\"ns_per_message\":%s,\"bytes_per_message\":%s}%n",
        mode,
        name,
        VARIANT,
        ImageInfo.inImageRuntimeCode() ? "svm" : "jvm",
        Arrays.stream(wires).mapToInt(w -> w.length).average().orElseThrow(),
        Arrays.toString(ns),
        Arrays.toString(bytes));
  }

  public static void main(String[] args) throws Exception {
    if (VARIANT < 0 || VARIANT > 4) throw new IllegalArgumentException("variant");
    System.out.println(
        "VM " + System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"));
    if (Files.exists(Path.of("/proc/cpuinfo")))
      System.out.println(
          Files.readAllLines(Path.of("/proc/cpuinfo")).stream()
              .filter(s -> s.startsWith("model name"))
              .findFirst()
              .orElse("unknown CPU"));
    var plan =
        ComparisonContracts.create(null, Limits.DEFAULT, 32, 1 << 24).type("google.protobuf.Type");
    var generated = new ComparisonContracts.SlabDecoder_google$protobuf$Type(plan);
    for (String mode : List.of("small", "holdout", "large")) {
      byte[][] wires = fixture(mode);
      Gen reused = new Gen();
      Span spans = new Span();
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] wire : wires) {
        int extent = reused.type(wire);
        I32.set(reused.out, 24, plan.layout().fingerprint());
        digest.update(reused.out, 0, extent);
        byte[] expected = SlabCodec.decode(plan.layout(), wire);
        if (!Arrays.equals(expected, generated.decode(wire)))
          throw new AssertionError("production producer parity " + mode);
        if (!Arrays.equals(expected, Arrays.copyOf(reused.out, extent)))
          throw new AssertionError(
              "probe writer differs from production "
                  + mode
                  + " offset="
                  + Arrays.mismatch(expected, Arrays.copyOf(reused.out, extent))
                  + " lengths="
                  + expected.length
                  + "/"
                  + extent);
      }
      System.out.println("WRITER_BYTES " + mode + " " + HexFormat.of().formatHex(digest.digest()));
      if (VARIANT == 0) {
        measure(
            mode,
            "stock",
            wires,
            i -> {
              try {
                Type value = Type.parseFrom(wires[i]);
                retained = value;
                return value.getFieldsCount();
              } catch (Exception e) {
                throw new AssertionError(e);
              }
            });
        measure(
            mode,
            "generated-owning",
            wires,
            i -> {
              byte[] value = generated.decode(wires[i]);
              retained = value;
              return value.length;
            });
        measure(
            mode,
            "table-owning",
            wires,
            i -> {
              byte[] value = SlabCodec.decode(plan.layout(), wires[i]);
              retained = value;
              return value.length;
            });
        measure(
            mode,
            "probe-spans-reused",
            wires,
            i -> {
              spans.type(wires[i], true);
              return spans.top + spans.words[0];
            });
      }
      measure(mode, "probe-slab-reused", wires, i -> reused.type(wires[i]) + reused.out[40]);
    }
    System.out.println("CHECKSUM " + checksum);
  }
}
