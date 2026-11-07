package dev.elide.truffledata.json.tests;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.sun.management.ThreadMXBean;
import dev.elide.myna.values.NativeValueTape;
import dev.elide.myna.values.ValueTape;
import org.graalvm.word.Pointer;
import dev.elide.truffledata.json.JavaSink;
import dev.elide.truffledata.json.JsonMessage;
import dev.elide.truffledata.json.JsonSink;
import dev.elide.truffledata.json.JsonSvm;
import dev.elide.truffledata.json.JsonValue;
import dev.elide.truffledata.json.generated.Json;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.VoidPointer;

/** TEMPORARY SVM probe (not committed): attributes Java-side decode, read and export costs. */
@org.jspecify.annotations.NullUnmarked
public final class PerfProbe {
  private static volatile long sink;
  private static volatile Object retained;

  interface Op {
    long run() throws Exception;
  }

  private static double best(Op op, int reps) throws Exception {
    double best = Double.MAX_VALUE;
    for (int round = 0; round < 7; round++) {
      long t0 = System.nanoTime();
      long acc = 0;
      for (int r = 0; r < reps; r++) acc += op.run();
      double ns = (System.nanoTime() - t0) / (double) reps;
      sink = acc;
      if (ns < best) best = ns;
    }
    return best;
  }

  private static double bytes(Op op, int reps) throws Exception {
    ThreadMXBean counter = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    counter.setThreadAllocatedMemoryEnabled(true);
    long thread = Thread.currentThread().threadId();
    long acc = 0;
    for (int r = 0; r < reps; r++) acc += op.run(); // warm
    long before = counter.getThreadAllocatedBytes(thread);
    for (int r = 0; r < reps; r++) acc += op.run();
    sink = acc;
    return (counter.getThreadAllocatedBytes(thread) - before) / (double) reps;
  }

  private static VoidPointer result(long address) {
    return org.graalvm.word.WordFactory.pointer(address);
  }

  private static org.graalvm.nativeimage.c.type.CCharPointer data(long address) {
    return org.graalvm.word.WordFactory.pointer(address);
  }

  private static void line(String what, double ns, double bytes) {
    System.out.printf(Locale.ROOT, "%-56s %10.1f ns %10.1f B%n", what, ns, bytes);
  }

  private static String doc(int i) {
    StringBuilder tags = new StringBuilder("["), scores = new StringBuilder("[");
    for (int k = 0; k < 1 + i % 4; k++) tags.append(k == 0 ? "" : ",").append("\"t").append(k).append('"');
    for (int k = 0; k < 3 + (i & 1); k++) scores.append(k == 0 ? "" : ",").append(i + k);
    String note =
        (i & 1) == 1
            ? "\"line \\\"" + i + "\\\" with \\\\ escapes\\n\\t\\u00e9 and \\ud83d\\ude00 tail\""
            : "\"plain note for item " + i + " padded beyond the inline text limit\"";
    return "{\"id\":" + (1000 + 7 * i) + ",\"name\":\"item-" + i + "\",\"price\":" + i + ".25,\"active\":"
        + (i % 3 == 0) + ",\"tags\":" + tags + "],\"dims\":{\"w\":" + 3 * i + ",\"h\":" + i + ".5,\"unit\":\"cm\"},\"scores\":"
        + scores + "],\"note\":" + note + ",\"parent\":null}";
  }

  static final class Counting implements JsonSink {
    long sum;
    public void beginObject(long m) { sum += m; }
    public void key(JsonValue k) { sum++; }
    public void beginArray(long e) { sum += e; }
    public void end() {}
    public void nullValue() { sum++; }
    public void booleanValue(boolean v) { sum += v ? 1 : 0; }
    public void longValue(long v) { sum += v; }
    public void doubleValue(double v) { sum += (long) v; }
    public void unsignedValue(long b) { sum += b; }
    public void decimalValue(JsonValue t) { sum++; }
    public void stringValue(JsonValue t) { sum++; }
  }

  /** Materializes strings as the JavaSink does, but into nothing: isolates string cost. */
  static final class Strings implements JsonSink {
    long sum;
    public void beginObject(long m) {}
    public void key(JsonValue k) { sum += k.stringValue().length(); }
    public void beginArray(long e) {}
    public void end() {}
    public void nullValue() {}
    public void booleanValue(boolean v) {}
    public void longValue(long v) {}
    public void doubleValue(double v) {}
    public void unsignedValue(long b) {}
    public void decimalValue(JsonValue t) {}
    public void stringValue(JsonValue t) { sum += t.stringValue().length(); }
  }

  /** Prototype of a position-cached member lookup: one compare at the cached child, scan on miss. */
  private static long cachedFind(ValueTape tape, long object, long cachedKey, ValueTape.Utf8Key key) {
    long end = tape.next(object);
    if (cachedKey > object && cachedKey < end && tape.tag(cachedKey) == ValueTape.STRING
        && tape.stringEquals(cachedKey, key)) return cachedKey + 1;
    return tape.find(object, key);
  }

  public static void main(String[] args) throws Exception {
    byte[] small = doc(1).getBytes(StandardCharsets.UTF_8);
    byte[] empty = "{}".getBytes(StandardCharsets.UTF_8);
    StringBuilder bigText = new StringBuilder("[");
    for (int i = 0; i < 1000; i++) bigText.append(i == 0 ? "" : ",").append(doc(i));
    byte[] big = bigText.append(']').toString().getBytes(StandardCharsets.UTF_8);
    System.out.println("PerfProbe small=" + small.length + " B big=" + big.length + " B");
    int reps = 100_000;

    // --- decode + close through the public SVM API ---
    line("JsonSvm.decode+close small", best(() -> { try (JsonMessage m = JsonSvm.decode(small)) { return m.root().size(); } }, reps),
        bytes(() -> { try (JsonMessage m = JsonSvm.decode(small)) { return m.root().size(); } }, reps));
    line("JsonSvm.decode+close {}", best(() -> { try (JsonMessage m = JsonSvm.decode(empty)) { return m.root().size(); } }, reps),
        bytes(() -> { try (JsonMessage m = JsonSvm.decode(empty)) { return m.root().size(); } }, reps));
    line("JsonSvm.decode+close big (per doc)", best(() -> { try (JsonMessage m = JsonSvm.decode(big)) { return m.root().size(); } }, 200), 0);

    // --- raw ABI: no Java owner objects ---
    long resultAddress = UnmanagedMemory.calloc(Json.JsonResult.SIZE).rawValue();
    Op rawSmall = () -> {
      int status;
      try (PinnedObject pinned = PinnedObject.create(small)) {
        status = Json.decode(pinned.addressOfArrayElement(0), small.length, 1 << 26, 1 << 26, 1_000_000, 1 << 26, 128, result(resultAddress));
      }
      if (status != 0) throw new IllegalStateException("status " + status);
      long n = Json.JsonResult.lexical_count(result(resultAddress));
      Json.release(result(resultAddress));
      return n;
    };
    line("raw ABI decode+release small (pin per call)", best(rawSmall, reps), bytes(rawSmall, reps));
    long smallAddress, emptyAddress;
    try (PinnedObject pinnedSmall = PinnedObject.create(small); PinnedObject pinnedEmpty = PinnedObject.create(empty)) {
      smallAddress = pinnedSmall.addressOfArrayElement(0).rawValue();
      emptyAddress = pinnedEmpty.addressOfArrayElement(0).rawValue();
      Op hoisted = () -> {
        int status = Json.decode(data(smallAddress), small.length, 1 << 26, 1 << 26, 1_000_000, 1 << 26, 128, result(resultAddress));
        if (status != 0) throw new IllegalStateException("status " + status);
        long n = Json.JsonResult.lexical_count(result(resultAddress));
        Json.release(result(resultAddress));
        return n;
      };
      line("raw ABI decode+release small (pin hoisted)", best(hoisted, reps), bytes(hoisted, reps));
      Op hoistedEmpty = () -> {
        int status = Json.decode(data(emptyAddress), empty.length, 1 << 26, 1 << 26, 1_000_000, 1 << 26, 128, result(resultAddress));
        if (status != 0) throw new IllegalStateException("status " + status);
        long n = Json.JsonResult.lexical_count(result(resultAddress));
        Json.release(result(resultAddress));
        return n;
      };
      line("raw ABI decode+release {} (pin hoisted)", best(hoistedEmpty, reps), bytes(hoistedEmpty, reps));
    }
    line("Json.fingerprint() leaf call", best(() -> Json.fingerprint().rawValue() != 0 ? 1 : 0, reps), 0);
    line("UnmanagedMemory.calloc(88)+free", best(() -> { VoidPointer p = UnmanagedMemory.calloc(88); UnmanagedMemory.free(p); return 1; }, reps), 0);
    line("PinnedObject.create+close", best(() -> { try (PinnedObject p = PinnedObject.create(small)) { return p.addressOfArrayElement(0).rawValue() != 0 ? 1 : 0; } }, reps), 0);

    // --- reads on a retained message (AOT, not Truffle-compiled) ---
    try (JsonMessage message = JsonSvm.decode(small)) {
      JsonValue root = message.root();
      ValueTape.Utf8Key id = ValueTape.Utf8Key.of("id");
      ValueTape.Utf8Key parent = ValueTape.Utf8Key.of("parent");
      ValueTape.Utf8Key note = ValueTape.Utf8Key.of("note");
      line("member(id).longValue()  [first key]", best(() -> root.member(id).longValue(), reps), bytes(() -> root.member(id).longValue(), reps));
      line("member(parent).kind()   [last key, 9th]", best(() -> root.member(parent).kind().ordinal(), reps), 0);
      InteropLibrary interop = InteropLibrary.getUncached();
      line("interop readMember(id)+asLong (uncached)", best(() -> interop.asLong(interop.readMember(root, "id")), reps),
          bytes(() -> interop.asLong(interop.readMember(root, "id")), reps));
      line("interop readMember(parent)+isNull (uncached)", best(() -> interop.isNull(interop.readMember(root, "parent")) ? 1 : 0, reps), 0);
      // prototype position cache on a raw tape: resolve once, then probe at the cached key node.
      VoidPointer rawResult = UnmanagedMemory.calloc(Json.JsonResult.SIZE);
      try (PinnedObject pinned = PinnedObject.create(small)) {
        if (Json.decode(pinned.addressOfArrayElement(0), small.length, 1 << 26, 1 << 26, 1_000_000, 1 << 26, 128, rawResult) != 0)
          throw new IllegalStateException("raw decode");
      }
      NativeValueTape tape = new NativeValueTape((Pointer) Json.JsonResult.view(rawResult));
      line("(reference) tape.find(parent) direct", best(() -> tape.find(0, parent), reps), 0);
      line("(reference) tape.find(id) direct", best(() -> tape.find(0, id), reps), 0);
      long keyNode = tape.find(0, parent) - 1;
      line("prototype cachedFind(parent) hit", best(() -> cachedFind(tape, 0, keyNode, parent), reps), 0);
      Json.release(rawResult);
      UnmanagedMemory.free(rawResult);
      line("stringValue() note (region, ~60 B)", best(() -> root.member(note).stringValue().length(), reps), bytes(() -> root.member(note).stringValue().length(), reps));
      ValueTape.Utf8Key name = ValueTape.Utf8Key.of("name");
      line("stringValue() name (inline)", best(() -> root.member(name).stringValue().length(), reps), bytes(() -> root.member(name).stringValue().length(), reps));
      line("asTruffleString() note", best(() -> interop.asTruffleString(root.member(note)).byteLength(com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8), reps),
          bytes(() -> interop.asTruffleString(root.member(note)).byteLength(com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8), reps));
      Counting counting = new Counting();
      line("export CountingSink small", best(() -> { counting.sum = 0; message.export(counting); return counting.sum; }, reps), bytes(() -> { counting.sum = 0; message.export(counting); return counting.sum; }, reps));
      Strings strings = new Strings();
      line("export Strings sink small (keys+strings -> String)", best(() -> { strings.sum = 0; message.export(strings); return strings.sum; }, reps), bytes(() -> { strings.sum = 0; message.export(strings); return strings.sum; }, reps));
      line("export JavaSink small", best(() -> { JavaSink s = new JavaSink(); message.export(s); retained = s.result(); return 1; }, reps), bytes(() -> { JavaSink s = new JavaSink(); message.export(s); retained = s.result(); return 1; }, reps));
      line("snapshot() small", best(() -> { retained = message.snapshot(); return 1; }, reps), bytes(() -> { retained = message.snapshot(); return 1; }, reps));
    }
    try (JsonMessage message = JsonSvm.decode(big)) {
      Counting counting = new Counting();
      line("export CountingSink big (per 1000-doc array)", best(() -> { counting.sum = 0; message.export(counting); return counting.sum; }, 200), 0);
      line("export JavaSink big", best(() -> { JavaSink s = new JavaSink(); message.export(s); retained = s.result(); return 1; }, 100), bytes(() -> { JavaSink s = new JavaSink(); message.export(s); retained = s.result(); return 1; }, 50));
      JsonValue root = message.root();
      line("element(999) random access (indexed)", best(() -> root.element(999).size(), reps), 0);
    }
    UnmanagedMemory.free(result(resultAddress));
    System.out.println("PerfProbe done " + sink);
  }
}
