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
import java.math.BigDecimal;
import java.math.MathContext;
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

  // ---- Numeric-heavy fixture (beat-stock plan WS8): canada-style GeoJSON coordinate rings. ----

  /** Points in numeric document {@code i}'s single polygon ring (closed: last equals first). */
  public static int numericPoints(int i) {
    return 160 + 4 * (i % 8);
  }

  /** Deterministic 64-bit LCG (Knuth MMIX constants); json_typed_pair.py derives the same values. */
  private static long next(long state) {
    return state * 6364136223846793005L + 1442695040888963407L;
  }

  /**
   * Coordinates of document {@code i} in integer microdegrees: {@code [lon0, lat0, lon1, lat1,
   * ...]}, longitudes in [-141, -52), latitudes in [41, 84) as in canada.json's extent. The ring is
   * closed (last point equals the first), as GeoJSON requires.
   */
  public static long[] numericMicros(int i) {
    int points = numericPoints(i);
    long[] micros = new long[2 * points];
    long state = 0x9E3779B97F4A7C15L ^ (i + 1);
    for (int p = 0; p < points - 1; p++) {
      state = next(state);
      micros[2 * p] = -141_000_000L + Long.remainderUnsigned(state >>> 11, 89_000_000L);
      state = next(state);
      micros[2 * p + 1] = 41_000_000L + Long.remainderUnsigned(state >>> 11, 43_000_000L);
    }
    micros[2 * points - 2] = micros[0];
    micros[2 * points - 1] = micros[1];
    return micros;
  }

  /**
   * A coordinate spelled as canada.json spells them: the exact binary value of the nearest double
   * to {@code micros / 10^6}, rounded to 17 significant digits (for example
   * {@code -65.613616999999977}), so most fractions carry 14–15 digits. 17 digits round-trip.
   */
  static String coordinate(long micros) {
    double value = micros / 1e6;
    return new BigDecimal(value).round(new MathContext(17)).toPlainString();
  }

  /**
   * Numeric document {@code i}: one GeoJSON Feature (about 6–7 KB) whose Polygon ring holds
   * {@link #numericPoints} {@code [lon,lat]} pairs of 17-significant-digit doubles. The 32
   * documents together are about 210 KB, the size of the native big fixture (1,000 mixed
   * documents). Generated, not copied: no third-party data.
   */
  public static byte[] numericDocument(int i) {
    long[] micros = numericMicros(i);
    StringBuilder json = new StringBuilder(8192);
    json.append("{\"type\":\"Feature\",\"properties\":{\"name\":\"ring-").append(i)
        .append("\",\"id\":").append(i)
        .append("},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[");
    for (int p = 0; p < micros.length / 2; p++) {
      json.append(p == 0 ? "[" : ",[").append(coordinate(micros[2 * p])).append(',')
          .append(coordinate(micros[2 * p + 1])).append(']');
    }
    return json.append("]]}}").toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Checksum every numeric engine computes for document {@code i}: properties.id + the ring's point
   * count + the first and last points' coordinates rounded to integer microdegrees.
   */
  public static long numericChecksum(int i) {
    long[] micros = numericMicros(i);
    int n = micros.length;
    return i + n / 2 + micros[0] + micros[1] + micros[n - 2] + micros[n - 1];
  }

  static long micros(double degrees) {
    return Math.round(degrees * 1e6);
  }

  @TruffleBoundary
  public static long gsonNumericRead(Object tree) {
    JsonObject o = ((JsonElement) tree).getAsJsonObject();
    var ring =
        o.getAsJsonObject("geometry").getAsJsonArray("coordinates").get(0).getAsJsonArray();
    var first = ring.get(0).getAsJsonArray();
    var last = ring.get(ring.size() - 1).getAsJsonArray();
    return o.getAsJsonObject("properties").get("id").getAsLong()
        + ring.size()
        + micros(first.get(0).getAsDouble())
        + micros(first.get(1).getAsDouble())
        + micros(last.get(0).getAsDouble())
        + micros(last.get(1).getAsDouble());
  }

  /** The numeric reads through cached interop, one library per site (like {@link InteropReader}). */
  public static final class NumericInteropReader extends Node {
    @Child private InteropLibrary properties = create();
    @Child private InteropLibrary id = create();
    @Child private InteropLibrary geometry = create();
    @Child private InteropLibrary coordinates = create();
    @Child private InteropLibrary rings = create();
    @Child private InteropLibrary ring = create();
    @Child private InteropLibrary point = create();
    @Child private InteropLibrary values = create();

    private static InteropLibrary create() {
      return InteropLibrary.getFactory().createDispatched(1);
    }

    public long read(Object doc) {
      try {
        Object box = properties.readMember(doc, "properties");
        Object shape = geometry.readMember(doc, "geometry");
        Object first = rings.readArrayElement(coordinates.readMember(shape, "coordinates"), 0);
        long size = ring.getArraySize(first);
        Object a = ring.readArrayElement(first, 0);
        Object b = ring.readArrayElement(first, size - 1);
        return values.asLong(id.readMember(box, "id"))
            + size
            + micros(values.asDouble(point.readArrayElement(a, 0)))
            + micros(values.asDouble(point.readArrayElement(a, 1)))
            + micros(values.asDouble(point.readArrayElement(b, 0)))
            + micros(values.asDouble(point.readArrayElement(b, 1)));
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
