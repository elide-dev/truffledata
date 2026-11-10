package dev.elide.truffledata.json;

import java.nio.charset.StandardCharsets;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/**
 * The JSON cells route alone, in an image built like a consumer's (the flag and {@code
 * JsonCellsFeature}, no assertions, nothing that calls the production bindings of the routed
 * imports): //json/cells/tests:aot_shape runs it, then reads the compiled shape of the route from
 * its symbols (each routed import reached through its cell's static {@code invoke}, no {@code
 * execute} call).
 */
public final class CellsShape {
  private CellsShape() {}

  public static void main(String[] args) {
    if (!JsonCellsRoute.ENABLED) throw new IllegalStateException("Image built without the JSON cells route");
    byte[] document = "{\"a\":[1,true,\"text\"],\"b\":{\"c\":-2.5}}".getBytes(StandardCharsets.UTF_8);
    String expected;
    try (JsonMessage message = JsonSvm.decode(document)) {
      expected = CellsInputs.render(message);
    }
    for (int round = 0; round < 1000; round++) {
      try (JsonMessage message = JsonSvm.decode(document, JsonLimits.DEFAULT)) {
        if (!expected.equals(CellsInputs.render(message))) throw new IllegalStateException("decode");
      }
      // Padded, so the region is parsed in place and must outlive the message.
      long capacity = document.length + 64;
      Pointer region = UnmanagedMemory.calloc(WordFactory.unsigned(capacity));
      if (region.isNull()) throw new OutOfMemoryError();
      long address = region.rawValue();
      int[] released = {0};
      try {
        for (int b = 0; b < document.length; b++) region.writeByte(b, document[b]);
        try (JsonMessage message =
            JsonSvm.adopt(address, document.length, capacity, () -> released[0]++)) {
          if (!expected.equals(CellsInputs.render(message))) throw new IllegalStateException("adopt");
        }
      } finally {
        UnmanagedMemory.free(region);
      }
      if (released[0] != 1) throw new IllegalStateException("Adopted release ran " + released[0]);
    }
    try {
      JsonSvm.decode("[1,".getBytes(StandardCharsets.UTF_8)).close();
      throw new IllegalStateException("Malformed input decoded");
    } catch (IllegalArgumentException refused) {
      // Its result was released on the way out.
    }
    System.out.println("JSON cells route (no assertions) ok: " + expected);
  }
}
