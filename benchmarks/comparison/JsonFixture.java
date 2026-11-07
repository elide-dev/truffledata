package dev.elide.truffledata.comparison;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import dev.elide.truffledata.tape.TapeSink;
import dev.elide.truffledata.tape.TapeText;
import dev.elide.truffledata.tape.TapeValue;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Mixed generic JSON fixture shared by the SVM comparison and the CodSpeed simulation cases. Every
 * engine consumes the same eight top-level and two nested members, so one checksum per document
 * holds for Gson trees, exported Java trees, interop views and snapshots alike.
 */
@NullMarked
public final class JsonFixture {
  private JsonFixture() {}

  /**
   * Document {@code i}: nested object, string and integer arrays, short, long and escaped strings,
   * I64 and F64 numbers, and null. Odd documents reverse the member order.
   */
  public static byte[] document(int i) {
    int tags = 1 + i % 4;
    int scores = 3 + (i & 1);
    StringBuilder tagList = new StringBuilder("[");
    for (int k = 0; k < tags; k++) tagList.append(k == 0 ? "" : ",").append("\"t").append(k).append('"');
    StringBuilder scoreList = new StringBuilder("[");
    for (int k = 0; k < scores; k++) scoreList.append(k == 0 ? "" : ",").append(i + k);
    // Odd notes carry quote, backslash, control, two-byte and surrogate-pair escapes; all exceed
    // Myna's 16-byte inline text, so they live in the decoded text region.
    String note =
        (i & 1) == 1
            ? "\"line \\\"" + i + "\\\" with \\\\ escapes\\n\\t\\u00e9 and \\ud83d\\ude00 tail\""
            : "\"plain note for item " + i + " padded beyond the inline text limit\"";
    String[] members = {
      "\"id\":" + (1000 + 7 * i),
      "\"name\":\"item-" + i + "\"",
      "\"price\":" + (i + 0.25),
      "\"active\":" + (i % 3 == 0),
      "\"tags\":" + tagList.append(']'),
      "\"dims\":{\"w\":" + 3 * i + ",\"h\":" + (i + 0.5) + ",\"unit\":\"cm\"}",
      "\"scores\":" + scoreList.append(']'),
      "\"note\":" + note,
      "\"parent\":null",
    };
    StringBuilder json = new StringBuilder("{");
    for (int k = 0; k < members.length; k++) {
      json.append(k == 0 ? "" : ",");
      json.append(members[(i & 1) == 0 ? k : members.length - 1 - k]);
    }
    return json.append('}').toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Checksum every engine computes for document {@code i}: id + (long) price + active + dims.w +
   * (long) dims.h + tag and score counts + one each for the note being a string and parent null.
   */
  public static long checksum(int i) {
    return (1000 + 7 * i) + i + (i % 3 == 0 ? 1 : 0) + 3 * i + i + (1 + i % 4) + (3 + (i & 1)) + 2;
  }

  /** Sum a {@link CountingSink} reaches for document {@code i}. */
  public static long walkChecksum(int i) {
    int tags = 1 + i % 4;
    int scores = 3 + (i & 1);
    long scoreSum = 0;
    for (int k = 0; k < scores; k++) scoreSum += i + k;
    long containers = 9 + 3 + tags + scores; // member and element counts announced up front
    long keys = 9 + 3;
    long numbers = (1000 + 7 * i) + i + 3 * i + i + scoreSum;
    long strings = 3 + tags; // name, unit, note, tags
    return containers + keys + numbers + (i % 3 == 0 ? 1 : 0) + strings + 1;
  }

  /** Gson's tree parser from UTF-8 bytes, rejecting trailing input (Gson tree parsing is lenient). */
  @TruffleBoundary
  public static JsonElement gsonParse(byte[] input) {
    return JsonParser.parseReader(
        new InputStreamReader(new ByteArrayInputStream(input), StandardCharsets.UTF_8));
  }

  /** Gson's tree parser over one line of an NDJSON buffer (the caller splits lines). */
  @TruffleBoundary
  public static JsonElement gsonParse(byte[] input, int offset, int length) {
    return JsonParser.parseReader(
        new InputStreamReader(new ByteArrayInputStream(input, offset, length), StandardCharsets.UTF_8));
  }

  /** The fixture's documents {@code 0..count-1} as NDJSON (one document per line). */
  public static byte[] ndjson(int count) {
    var out = new java.io.ByteArrayOutputStream();
    for (int i = 0; i < count; i++) {
      out.writeBytes(document(i));
      out.write('\n');
    }
    return out.toByteArray();
  }

  @TruffleBoundary
  public static long gsonRead(Object tree) {
    JsonObject o = ((JsonElement) tree).getAsJsonObject();
    JsonObject dims = o.getAsJsonObject("dims");
    return o.get("id").getAsLong()
        + (long) o.get("price").getAsDouble()
        + (o.get("active").getAsBoolean() ? 1 : 0)
        + dims.get("w").getAsLong()
        + (long) dims.get("h").getAsDouble()
        + o.getAsJsonArray("tags").size()
        + o.getAsJsonArray("scores").size()
        + (o.get("note").getAsJsonPrimitive().isString() ? 1 : 0)
        + (o.get("parent").isJsonNull() ? 1 : 0);
  }

  /** Reads the tree {@link dev.elide.truffledata.tape.JavaSink} builds for a fixture document. */
  @TruffleBoundary
  public static long javaRead(Object tree) {
    Map<?, ?> o = (Map<?, ?>) tree;
    Map<?, ?> dims = (Map<?, ?>) member(o, "dims");
    return (Long) member(o, "id")
        + (long) (double) (Double) member(o, "price")
        + ((Boolean) member(o, "active") ? 1 : 0)
        + (Long) member(dims, "w")
        + (long) (double) (Double) member(dims, "h")
        + ((List<?>) member(o, "tags")).size()
        + ((List<?>) member(o, "scores")).size()
        + (member(o, "note") instanceof String ? 1 : 0)
        + (o.containsKey("parent") && o.get("parent") == null ? 1 : 0);
  }

  private static final TruffleString.Encoding UTF_16 = TruffleString.Encoding.UTF_16;
  private static final TruffleString ID = TruffleString.fromJavaStringUncached("id", UTF_16);
  private static final TruffleString PRICE = TruffleString.fromJavaStringUncached("price", UTF_16);
  private static final TruffleString ACTIVE = TruffleString.fromJavaStringUncached("active", UTF_16);
  private static final TruffleString DIMS = TruffleString.fromJavaStringUncached("dims", UTF_16);
  private static final TruffleString W = TruffleString.fromJavaStringUncached("w", UTF_16);
  private static final TruffleString H = TruffleString.fromJavaStringUncached("h", UTF_16);
  private static final TruffleString TAGS = TruffleString.fromJavaStringUncached("tags", UTF_16);
  private static final TruffleString SCORES = TruffleString.fromJavaStringUncached("scores", UTF_16);
  private static final TruffleString NOTE = TruffleString.fromJavaStringUncached("note", UTF_16);
  private static final TruffleString PARENT = TruffleString.fromJavaStringUncached("parent", UTF_16);

  /** Reads the tree {@link dev.elide.truffledata.tape.TruffleSink} builds for a fixture document. */
  @TruffleBoundary
  public static long truffleRead(Object tree) {
    Map<?, ?> o = (Map<?, ?>) tree;
    Map<?, ?> dims = (Map<?, ?>) member(o, DIMS);
    return (Long) member(o, ID)
        + (long) (double) (Double) member(o, PRICE)
        + ((Boolean) member(o, ACTIVE) ? 1 : 0)
        + (Long) member(dims, W)
        + (long) (double) (Double) member(dims, H)
        + ((List<?>) member(o, TAGS)).size()
        + ((List<?>) member(o, SCORES)).size()
        + (member(o, NOTE) instanceof TruffleString ? 1 : 0)
        + (o.containsKey(PARENT) && o.get(PARENT) == null ? 1 : 0);
  }

  private static Object member(Map<?, ?> map, Object name) {
    return Objects.requireNonNull(map.get(name), name::toString);
  }

  /**
   * Guest-style reads through cached interop. Each member site has its own library so the
   * receiver's per-name key cache (three names per site) always hits.
   */
  public static final class InteropReader extends Node {
    @Child private InteropLibrary id = create();
    @Child private InteropLibrary price = create();
    @Child private InteropLibrary active = create();
    @Child private InteropLibrary dims = create();
    @Child private InteropLibrary w = create();
    @Child private InteropLibrary h = create();
    @Child private InteropLibrary tags = create();
    @Child private InteropLibrary scores = create();
    @Child private InteropLibrary note = create();
    @Child private InteropLibrary parent = create();
    @Child private InteropLibrary values = create();

    private static InteropLibrary create() {
      return InteropLibrary.getFactory().createDispatched(1);
    }

    public long read(Object doc) {
      try {
        Object box = dims.readMember(doc, "dims");
        return values.asLong(id.readMember(doc, "id"))
            + (long) values.asDouble(price.readMember(doc, "price"))
            + (values.asBoolean(active.readMember(doc, "active")) ? 1 : 0)
            + values.asLong(w.readMember(box, "w"))
            + (long) values.asDouble(h.readMember(box, "h"))
            + values.getArraySize(tags.readMember(doc, "tags"))
            + values.getArraySize(scores.readMember(doc, "scores"))
            + (values.isString(note.readMember(doc, "note")) ? 1 : 0)
            + (values.isNull(parent.readMember(doc, "parent")) ? 1 : 0);
      } catch (InteropException failure) {
        throw CompilerDirectives.shouldNotReachHere(failure);
      }
    }
  }

  /** Non-materializing sink: the export walk itself is all that runs. */
  public static final class CountingSink implements TapeSink {
    public long sum;

    @Override
    public void beginObject(long members) {
      sum += members;
    }

    @Override
    public void key(TapeText key) {
      sum += 1;
    }

    @Override
    public void beginArray(long elements) {
      sum += elements;
    }

    @Override
    public void end() {}

    @Override
    public void nullValue() {
      sum += 1;
    }

    @Override
    public void booleanValue(boolean value) {
      sum += value ? 1 : 0;
    }

    @Override
    public void longValue(long value) {
      sum += value;
    }

    @Override
    public void doubleValue(double value) {
      sum += (long) value;
    }

    @Override
    public void unsignedValue(long bits) {
      sum += bits;
    }

    @Override
    public void decimalValue(TapeText token) {
      sum += 1;
    }

    @Override
    public void stringValue(TapeText text) {
      sum += 1;
    }
  }
}
