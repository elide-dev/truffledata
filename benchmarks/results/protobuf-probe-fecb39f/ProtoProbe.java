package dev.elide.truffledata.comparison;

import com.google.protobuf.SourceContext;
import com.google.protobuf.Syntax;
import com.google.protobuf.Type;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.LongSupplier;

/**
 * Temporary JVM probe for the protobuf performance plan (not production code). Compares
 * protobuf-java parsing of the consumer fixture against a pure-Java schema-specialized decoder
 * into a flat {@code long[]} slab, and measures FFM downcall and heap-to-native copy costs.
 */
public final class ProtoProbe {
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

  // ---- pure-Java slab decoder (same record layout as the Rust probe) ----
  static final class Slab {
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
        for (int k = 1; k <= need; k++) if ((in[i + k] & 0xc0) != 0x80) throw new IllegalStateException("utf8");
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
          case 0x08 -> { words[record + 1] = varint(); presence |= 2; }
          case 0x10 -> { words[record + 2] = varint(); presence |= 4; }
          case 0x18 -> { words[record + 3] = varint(); presence |= 8; }
          case 0x22 -> { int n = (int) varint(); text(input, pos, n, validate); words[record + 4] = ((long) (pos) << 32) | n; pos += n; presence |= 16; }
          case 0x32 -> { int n = (int) varint(); text(input, pos, n, validate); words[record + 5] = ((long) (pos) << 32) | n; pos += n; presence |= 32; }
          case 0x38 -> { words[record + 6] = varint(); presence |= 64; }
          case 0x40 -> { words[record + 7] = varint(); presence |= 128; }
          case 0x4a -> { int n = (int) varint(); pos += n; options++; presence |= 256; }
          case 0x52 -> { int n = (int) varint(); text(input, pos, n, validate); words[record + 9] = ((long) (pos) << 32) | n; pos += n; presence |= 512; }
          case 0x5a -> { int n = (int) varint(); text(input, pos, n, validate); words[record + 10] = ((long) (pos) << 32) | n; pos += n; presence |= 1024; }
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
          case 0x0a -> { int n = (int) varint(); text(in, pos, n, validate); words[record + 1] = ((long) pos << 32) | n; pos += n; presence |= 2; }
          case 0x12 -> { int n = (int) varint(); int child = field(pos + n, 0); fields[fieldCount++] = child; presence |= 4; }
          case 0x1a -> { int n = (int) varint(); text(in, pos, n, validate); oneofs[oneofCount++] = ((long) pos << 32) | n; pos += n; presence |= 8; }
          case 0x22 -> { int n = (int) varint(); pos += n; words[record + 4]++; presence |= 16; }
          case 0x2a -> {
            int n = (int) varint();
            int child = alloc(2);
            int subEnd = pos + n;
            while (pos < subEnd) {
              long t = varint();
              if (t == 0x0a) { int nn = (int) varint(); text(in, pos, nn, validate); words[child + 1] = ((long) pos << 32) | nn; pos += nn; words[child] = 2; }
              else skip((int) t & 7);
            }
            words[record + 5] = child;
            presence |= 32;
          }
          case 0x30 -> { words[record + 6] = varint(); presence |= 64; }
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

  static void runMode(String mode, int reps) throws Exception {
    byte[][] msgs = fixture(mode);
    long bytes = 0;
    for (var m : msgs) bytes += m.length;
    System.out.printf("PROBE mode=%s messages=32 bytes=%d avg=%d%n", mode, bytes, bytes / 32);
    // Warm up every variant before timing (C2 needs several thousand iterations).
    Slab slab = new Slab();
    for (int warm = 0; warm < 3; warm++) {
      bench(mode + "/stock_parse", bytes, reps, () -> {
        long s = 0;
        for (var m : msgs) {
          try { Type t = Type.parseFrom(m); s += t.getFieldsCount() + t.getName().length(); } catch (Exception e) { throw new RuntimeException(e); }
        }
        return s;
      });
      bench(mode + "/stock_parse_consume", bytes, reps, () -> {
        long s = 0;
        for (var m : msgs) {
          try {
            Type t = Type.parseFrom(m);
            s += t.getName().length() + t.getOneofsCount() * 3 + t.getSyntaxValue();
            for (var f : t.getFieldsList()) {
              s += f.getName().length();
              if (f.getPacked()) s += f.getNumber() * 31 + f.getKindValue() + f.getCardinalityValue();
            }
          } catch (Exception e) { throw new RuntimeException(e); }
        }
        return s;
      });
      bench(mode + "/java_slab", bytes, reps, () -> {
        long s = 0;
        for (var m : msgs) { slab.type(m, true); s += slab.words[0] + slab.top; }
        return s;
      });
      bench(mode + "/java_slab_novf", bytes, reps, () -> {
        long s = 0;
        for (var m : msgs) { slab.type(m, false); s += slab.words[0] + slab.top; }
        return s;
      });
      bench(mode + "/java_slab_consume", bytes, reps, () -> {
        long s = 0;
        for (var m : msgs) {
          int r = slab.type(m, true);
          long[] w = slab.words;
          s += (w[r + 1] & 0xffffffffL) + w[r + 6];
          int list = (int) w[r + 2];
          if (list != 0) {
            int n = (int) w[list];
            for (int k = 0; k < n; k++) {
              int f = (int) w[list + 1 + k];
              s += (w[f + 4] & 0xffffffffL);
              if (w[f + 7] != 0) s += w[f + 3] * 31 + w[f + 1] + w[f + 2];
            }
          }
          int oneofs = (int) w[r + 3];
          if (oneofs != 0) s += w[oneofs] * 3;
        }
        return s;
      });
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("PROBE jvm=" + System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"));
    try {
      var cpu = Files.readAllLines(Path.of("/proc/cpuinfo")).stream().filter(l -> l.startsWith("model name")).findFirst().orElse("");
      System.out.println("PROBE host=" + cpu);
    } catch (Exception ignored) {
    }
    runMode("small", 2000);
    runMode("holdout", 1000);
    runMode("large", 8);

    // FFM downcall costs through the shipped Buffa shared library (fingerprint is a bounded leaf).
    String srcdir = System.getenv("TEST_SRCDIR");
    if (srcdir != null) {
      Path library;
      try (var stream = Files.walk(Path.of(srcdir))) {
        library = stream.filter(p -> p.toString().endsWith("libtruffledata_buffa.so") || p.toString().endsWith("shared.so")).findFirst().orElse(null);
      }
      if (library != null) {
        try (Arena arena = Arena.ofConfined()) {
          SymbolLookup lookup = SymbolLookup.libraryLookup(library, arena);
          Linker linker = Linker.nativeLinker();
          var symbol = lookup.find("td_buffa_fingerprint").orElseThrow();
          MethodHandle normal = linker.downcallHandle(symbol, FunctionDescriptor.of(ValueLayout.ADDRESS));
          MethodHandle critical = linker.downcallHandle(symbol, FunctionDescriptor.of(ValueLayout.ADDRESS), Linker.Option.critical(false));
          for (int warm = 0; warm < 3; warm++) {
            bench("ffm/downcall_normal", 32, 100000, () -> {
              try { long s = 0; for (int i = 0; i < 32; i++) s += ((MemorySegment) normal.invokeExact()).address(); return s; } catch (Throwable t) { throw new RuntimeException(t); }
            });
            bench("ffm/downcall_critical", 32, 100000, () -> {
              try { long s = 0; for (int i = 0; i < 32; i++) s += ((MemorySegment) critical.invokeExact()).address(); return s; } catch (Throwable t) { throw new RuntimeException(t); }
            });
          }
          byte[][] msgs = fixture("small");
          MemorySegment native150 = arena.allocate(1 << 16);
          byte[] firstMsg = msgs[0];
          for (int warm = 0; warm < 3; warm++)
            bench("ffm/copy_150B_to_native", firstMsg.length * 32L, 100000, () -> {
              long s = 0;
              for (int i = 0; i < 32; i++) {
                MemorySegment.copy(msgs[i], 0, native150, ValueLayout.JAVA_BYTE, 0, msgs[i].length);
                s += native150.get(ValueLayout.JAVA_BYTE, 3);
              }
              return s;
            });
        }
      } else {
        System.out.println("PROBE ffm: shared library not found under TEST_SRCDIR");
      }
    }
    System.out.println("PROBE sink=" + sink);
  }
}
