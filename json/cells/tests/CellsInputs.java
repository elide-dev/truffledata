package dev.elide.truffledata.json;

import dev.elide.truffledata.tape.TapeSink;
import dev.elide.truffledata.tape.TapeText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The JSON cells parity inputs, shared by the HotSpot and Native Image lanes: the session fuzzer's
 * seed corpus ({@code json/native/corpus/session}, two control bytes then the document), fixed
 * valid, malformed and limit-breaking documents, and a seeded mutation fuzz over all of them
 * (deterministic, so both routes see exactly the same bytes). Outcomes are compared as strings:
 * either the full canonical export of the message (every value, key, number bit pattern and text
 * byte) or the exception's type and message.
 */
final class CellsInputs {
  static final long SEED = 0x6a736f6e63656c6cL;
  static final int MUTATIONS = 600;

  /** Small budgets so the node, text, index, input and depth limits all fire on the inputs. */
  static final JsonLimits[] LIMITS = {
    JsonLimits.DEFAULT,
    new JsonLimits(64, 1 << 20, 1 << 20, 1 << 20, 128),
    new JsonLimits(1 << 20, 1 << 20, 8, 1 << 20, 128),
    new JsonLimits(1 << 20, 1 << 20, 1 << 20, 4, 128),
    new JsonLimits(1 << 20, 64, 1 << 20, 1 << 20, 128),
    new JsonLimits(1 << 20, 1 << 20, 1 << 20, 1 << 20, 3),
  };

  private CellsInputs() {}

  static List<byte[]> inputs() throws IOException {
    List<byte[]> seeds = new ArrayList<>();
    Path corpus = Path.of("json/native/corpus/session");
    try (Stream<Path> files = Files.list(corpus)) {
      for (Path file : files.sorted().toList()) {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length < 2) throw new IllegalStateException("Short corpus entry " + file);
        seeds.add(java.util.Arrays.copyOfRange(bytes, 2, bytes.length));
      }
    }
    if (seeds.size() < 4) throw new IllegalStateException("Session corpus missing: " + seeds.size());
    for (String text :
        new String[] {
          "{}",
          "[]",
          "null",
          "true",
          "0",
          "-0",
          "1e999999999999999999",
          "-1.5e-308",
          "18446744073709551615",
          "-9223372036854775808",
          "123456789012345678901234567890",
          "\"\"",
          "\"a long escaped string \\u4e2d 😀\\n\\t\\\"\"",
          "{\"a\":1,\"a\":2}",
          "{\"text\":\"x\",\"n\":[1,2.5,-3,true,false,null,{\"k\":\"v\"}]}",
          "[" + "1,".repeat(200) + "1]",
          "[".repeat(127) + "]".repeat(127),
          "[".repeat(128) + "]".repeat(128),
          "[".repeat(129) + "]".repeat(129),
          "{\"k\":\"" + "x".repeat(5000) + "\"}",
          // Malformed.
          "",
          " ",
          "{",
          "}",
          "[1,]",
          "{\"a\"}",
          "{\"a\":}",
          "tru",
          "nul",
          "01",
          "1.",
          "\"\\uD800\"",
          "\"\\x\"",
          "\"unterminated",
          "[1] [2]",
          "{\"a\":1}garbage",
        })
      seeds.add(text.getBytes(StandardCharsets.UTF_8));
    seeds.add(new byte[] {'"', (byte) 0xff, '"'});
    seeds.add(new byte[] {'"', (byte) 0xc3, '"'});
    seeds.add(new byte[] {'"', 0, '"'});
    seeds.add(new byte[] {'[', '1', 0, ']'});

    List<byte[]> inputs = new ArrayList<>(seeds);
    SplittableRandom random = new SplittableRandom(SEED);
    byte[] alphabet = "{}[],:\"\\ 0123456789.eE+-tfnul\u00ff".getBytes(StandardCharsets.ISO_8859_1);
    for (int i = 0; i < MUTATIONS; i++) {
      byte[] seed = seeds.get(random.nextInt(seeds.size()));
      byte[] input = seed.clone();
      int edits = 1 + random.nextInt(4);
      for (int e = 0; e < edits && input.length > 0; e++) {
        int at = random.nextInt(input.length);
        switch (random.nextInt(4)) {
          case 0 -> input[at] = alphabet[random.nextInt(alphabet.length)];
          case 1 -> input[at] = (byte) random.nextInt(256);
          case 2 -> input = java.util.Arrays.copyOf(input, at);
          default -> {
            byte[] longer = new byte[input.length + 1];
            System.arraycopy(input, 0, longer, 0, at);
            longer[at] = alphabet[random.nextInt(alphabet.length)];
            System.arraycopy(input, at, longer, at + 1, input.length - at);
            input = longer;
          }
        }
      }
      inputs.add(input);
    }
    return inputs;
  }

  /** The outcome of one decode: the canonical message, or the failure's type and message. */
  static String outcome(Supplier<JsonMessage> decode) {
    JsonMessage message;
    try {
      message = decode.get();
    } catch (RuntimeException | OutOfMemoryError failure) {
      return "error " + failure.getClass().getName() + ": " + failure.getMessage();
    }
    try {
      return render(message);
    } finally {
      message.close();
    }
  }

  static String render(JsonMessage message) {
    StringBuilder out = new StringBuilder("ok ");
    out.append(message.directories()).append('/').append(message.indexedArrays()).append(' ');
    message.export(new Canonical(out));
    return out.toString();
  }

  /** Every token with its exact bits and bytes. */
  private record Canonical(StringBuilder out) implements TapeSink {
    private void text(char tag, TapeText text) {
      byte[] bytes = new byte[text.utf8Length()];
      text.copyUtf8(bytes, 0);
      out.append(tag).append(HexFormat.of().formatHex(bytes)).append(' ');
    }

    @Override
    public void beginObject(long members) {
      out.append("{").append(members).append(' ');
    }

    @Override
    public void key(TapeText key) {
      text('k', key);
    }

    @Override
    public void beginArray(long elements) {
      out.append("[").append(elements).append(' ');
    }

    @Override
    public void end() {
      out.append("e ");
    }

    @Override
    public void nullValue() {
      out.append("n ");
    }

    @Override
    public void booleanValue(boolean value) {
      out.append(value ? "t " : "f ");
    }

    @Override
    public void longValue(long value) {
      out.append('l').append(value).append(' ');
    }

    @Override
    public void doubleValue(double value) {
      out.append('d').append(Long.toHexString(Double.doubleToRawLongBits(value))).append(' ');
    }

    @Override
    public void unsignedValue(long bits) {
      out.append('u').append(Long.toUnsignedString(bits)).append(' ');
    }

    @Override
    public void decimalValue(TapeText token) {
      text('D', token);
    }

    @Override
    public void stringValue(TapeText text) {
      text('s', text);
    }
  }
}
