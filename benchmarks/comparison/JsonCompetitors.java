package dev.elide.truffledata.comparison;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.dslplatform.json.DslJson;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsoniter.JsonIterator;
import com.jsoniter.ValueType;
import com.jsoniter.any.Any;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.simdjson.JsonValue;
import org.simdjson.SimdJsonParser;

/**
 * Benchmark-only JVM JSON libraries for the generic workload (the JSON scoreboard): each parses one
 * fixture document from its UTF-8 bytes into its own representation and reads the same ten members
 * as {@link JsonFixture#gsonRead}, so every cell reaches {@link JsonFixture#checksum}. Initialized
 * at image run time (library singletons and SIMD species are chosen on the running CPU).
 *
 * <p>Tree engines retain their trees for the read stages; streaming and single-buffer parsers
 * (Jackson's {@code JsonParser}, simdjson-java, whose values live in the parser's reused tape) are
 * ingest-only, like the session and stream cells.
 */
@NullMarked
final class JsonCompetitors {
  private JsonCompetitors() {}

  // Engine names are JsonComparison.COMPETITORS: that class is initialized at image build time and
  // must not reach this one's singletons.
  private static final ObjectMapper JACKSON = new ObjectMapper();
  private static final JsonFactory JACKSON_STREAM = new JsonFactory();
  private static final DslJson<Object> DSL = new DslJson<>();
  // One reusable parser per thread, as simdjson-java intends; benchmarks run on one thread.
  // Created on first use: simdjson-java needs 256- or 512-bit vector species, which a
  // -march=compatibility image does not offer (json_svm_v3 is the x86-64-v3 variant).
  private static @org.jspecify.annotations.Nullable SimdJsonParser simdjson;

  /** The engine named {@code engine} behind JsonComparison's interface (see Competitor). */
  static JsonComparison.Competitor engine(String engine) {
    return new Engine(engine);
  }

  private static final class Engine implements JsonComparison.Competitor {
    private final String name;

    Engine(String name) {
      this.name = name;
    }

    @Override
    @TruffleBoundary
    public long read(Object tree) {
      return JsonCompetitors.read(name, tree);
    }

    @Override
    @TruffleBoundary
    public long ingest(byte[] input) throws Exception {
      return JsonCompetitors.ingest(name, input);
    }
  }

  /**
   * Whether {@code engine} can run in this image: simdjson-java needs 256- or 512-bit vector
   * species (an x86-64-v3 image on a capable CPU); the others always can.
   */
  @TruffleBoundary
  static boolean available(String engine) {
    if (!engine.equals("simdjson-java")) return true;
    try {
      if (simdjson == null) simdjson = new SimdJsonParser();
      return true;
    } catch (ExceptionInInitializerError | NoClassDefFoundError | IllegalArgumentException unsupported) {
      return false;
    }
  }

  /** Parses {@code input} with {@code engine}'s tree API (tree engines only). */
  @TruffleBoundary
  static Object parse(String engine, byte[] input) throws Exception {
    return switch (engine) {
      case "jackson-tree" -> JACKSON.readTree(input);
      case "fastjson2-tree" -> JSON.parseObject(input);
      case "dsljson-map" -> Objects.requireNonNull(DSL.deserialize(Map.class, input, input.length));
      case "jsoniter-any" -> JsonIterator.deserialize(input);
      default -> throw new IllegalArgumentException("Not a tree engine: " + engine);
    };
  }

  /** Reads the ten fixture members of a tree {@link #parse} returned. */
  @TruffleBoundary
  static long read(String engine, Object tree) {
    return switch (engine) {
      case "jackson-tree" -> jackson((JsonNode) tree);
      case "fastjson2-tree" -> fastjson((JSONObject) tree);
      case "dsljson-map" -> map((Map<?, ?>) tree);
      case "jsoniter-any" -> jsoniter((Any) tree);
      default -> throw new IllegalArgumentException("Not a tree engine: " + engine);
    };
  }

  /** Parses and reads one document; the ingest stage of every engine. */
  @TruffleBoundary
  static long ingest(String engine, byte[] input) throws Exception {
    return switch (engine) {
      case "jackson-stream" -> jacksonStream(input);
      case "simdjson-java" -> {
        SimdJsonParser parser = simdjson;
        if (parser == null) simdjson = parser = new SimdJsonParser();
        yield simdjson(parser.parse(input, input.length));
      }
      default -> {
        Object tree = parse(engine, input);
        JsonComparison.retain(tree);
        yield read(engine, tree);
      }
    };
  }

  /** The numeric fixture's engines (WS8): Jackson and fastjson2 trees with their defaults. */
  static JsonComparison.Competitor numeric(String engine) {
    return new JsonComparison.Competitor() {
      @Override
      @TruffleBoundary
      public long read(Object tree) {
        throw new UnsupportedOperationException("numeric engines measure ingest only");
      }

      @Override
      @TruffleBoundary
      public long ingest(byte[] input) throws Exception {
        return JsonCompetitors.numericIngest(engine, input);
      }
    };
  }

  /**
   * Parses one numeric document into the engine's tree and reads {@link
   * JsonFixture#numericChecksum}'s members. Both libraries parse every coordinate at tree build
   * (Jackson's {@code DoubleNode}; fastjson2's default {@code BigDecimal} decimals).
   */
  @TruffleBoundary
  static long numericIngest(String engine, byte[] input) throws Exception {
    switch (engine) {
      case "jackson-numeric" -> {
        JsonNode o = JACKSON.readTree(input);
        JsonComparison.retain(o);
        JsonNode ring = o.get("geometry").get("coordinates").get(0);
        JsonNode first = ring.get(0), last = ring.get(ring.size() - 1);
        return o.get("properties").get("id").asLong()
            + ring.size()
            + JsonFixture.micros(first.get(0).asDouble())
            + JsonFixture.micros(first.get(1).asDouble())
            + JsonFixture.micros(last.get(0).asDouble())
            + JsonFixture.micros(last.get(1).asDouble());
      }
      case "fastjson2-numeric" -> {
        JSONObject o = JSON.parseObject(input);
        JsonComparison.retain(o);
        JSONArray ring = o.getJSONObject("geometry").getJSONArray("coordinates").getJSONArray(0);
        JSONArray first = ring.getJSONArray(0), last = ring.getJSONArray(ring.size() - 1);
        return o.getJSONObject("properties").getLongValue("id")
            + ring.size()
            + JsonFixture.micros(first.getDoubleValue(0))
            + JsonFixture.micros(first.getDoubleValue(1))
            + JsonFixture.micros(last.getDoubleValue(0))
            + JsonFixture.micros(last.getDoubleValue(1));
      }
      default -> throw new IllegalArgumentException("Not a numeric engine: " + engine);
    }
  }

  private static long jackson(JsonNode o) {
    JsonNode dims = o.get("dims");
    return o.get("id").asLong()
        + (long) o.get("price").asDouble()
        + (o.get("active").asBoolean() ? 1 : 0)
        + dims.get("w").asLong()
        + (long) dims.get("h").asDouble()
        + o.get("tags").size()
        + o.get("scores").size()
        + (o.get("note").isTextual() ? 1 : 0)
        + (o.get("parent").isNull() ? 1 : 0);
  }

  private static long fastjson(JSONObject o) {
    JSONObject dims = o.getJSONObject("dims");
    return o.getLongValue("id")
        + (long) o.getDoubleValue("price")
        + (o.getBooleanValue("active") ? 1 : 0)
        + dims.getLongValue("w")
        + (long) dims.getDoubleValue("h")
        + o.getJSONArray("tags").size()
        + o.getJSONArray("scores").size()
        + (o.get("note") instanceof String ? 1 : 0)
        + (o.containsKey("parent") && o.get("parent") == null ? 1 : 0);
  }

  // DSL-JSON's Object converter: LinkedHashMap, ArrayList, String, Boolean, null, and numbers as
  // Long or Double (BigDecimal beyond them).
  private static long map(Map<?, ?> o) {
    Map<?, ?> dims = (Map<?, ?>) member(o, "dims");
    return ((Number) member(o, "id")).longValue()
        + (long) ((Number) member(o, "price")).doubleValue()
        + ((Boolean) member(o, "active") ? 1 : 0)
        + ((Number) member(dims, "w")).longValue()
        + (long) ((Number) member(dims, "h")).doubleValue()
        + ((List<?>) member(o, "tags")).size()
        + ((List<?>) member(o, "scores")).size()
        + (member(o, "note") instanceof String ? 1 : 0)
        + (o.containsKey("parent") && o.get("parent") == null ? 1 : 0);
  }

  private static Object member(Map<?, ?> map, String name) {
    return Objects.requireNonNull(map.get(name), name);
  }

  private static long jsoniter(Any o) {
    Any dims = o.get("dims");
    return o.toLong("id")
        + (long) o.toDouble("price")
        + (o.toBoolean("active") ? 1 : 0)
        + dims.toLong("w")
        + (long) dims.toDouble("h")
        + o.get("tags").size()
        + o.get("scores").size()
        + (o.get("note").valueType() == ValueType.STRING ? 1 : 0)
        + (o.get("parent").valueType() == ValueType.NULL ? 1 : 0);
  }

  private static long simdjson(JsonValue o) {
    JsonValue dims = o.get("dims");
    return o.get("id").asLong()
        + (long) o.get("price").asDouble()
        + (o.get("active").asBoolean() ? 1 : 0)
        + dims.get("w").asLong()
        + (long) dims.get("h").asDouble()
        + count(o.get("tags").arrayIterator())
        + count(o.get("scores").arrayIterator())
        + (o.get("note").isString() ? 1 : 0)
        + (o.get("parent").isNull() ? 1 : 0);
  }

  private static long count(Iterator<?> elements) {
    long n = 0;
    for (; elements.hasNext(); elements.next()) n++;
    return n;
  }

  /** Jackson's streaming parser: one pass over the tokens, reading the members as they come. */
  private static long jacksonStream(byte[] input) throws Exception {
    long sum = 0;
    try (JsonParser p = JACKSON_STREAM.createParser(input)) {
      if (p.nextToken() != JsonToken.START_OBJECT) throw new IllegalArgumentException("object");
      while (p.nextToken() == JsonToken.FIELD_NAME) {
        String name = p.currentName();
        JsonToken value = p.nextToken();
        switch (name) {
          case "id" -> sum += p.getLongValue();
          case "price" -> sum += (long) p.getDoubleValue();
          case "active" -> sum += p.getBooleanValue() ? 1 : 0;
          case "tags", "scores" -> {
            while (p.nextToken() != JsonToken.END_ARRAY) {
              sum++;
              p.skipChildren();
            }
          }
          case "dims" -> {
            while (p.nextToken() == JsonToken.FIELD_NAME) {
              String inner = p.currentName();
              p.nextToken();
              if (inner.equals("w")) sum += p.getLongValue();
              else if (inner.equals("h")) sum += (long) p.getDoubleValue();
              else p.skipChildren();
            }
          }
          case "note" -> sum += value == JsonToken.VALUE_STRING ? 1 : 0;
          case "parent" -> sum += value == JsonToken.VALUE_NULL ? 1 : 0;
          default -> p.skipChildren();
        }
      }
      if (p.nextToken() != null) throw new IllegalArgumentException("Trailing JSON input");
    }
    return sum;
  }
}
