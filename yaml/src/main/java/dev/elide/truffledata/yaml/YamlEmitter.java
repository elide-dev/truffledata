package dev.elide.truffledata.yaml;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.tape.TapeSink;
import dev.elide.truffledata.tape.TapeText;
import dev.elide.truffledata.tape.TapeValue;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Writes tape values (YAML, JSON or snapshots) as YAML through a {@link YamlWriter}.
 *
 * <p>Without {@link YamlEmitOptions#preserveMetadata()} the value is exported into the writer in
 * one pass: tags are dropped and aliases are expanded. With it, YAML metadata is carried over:
 * tags ({@link Yaml#tag}), anchors and aliases under their source names ({@link Yaml#anchor},
 * {@link Yaml#isAlias}), scalar and collection styles as hints ({@link Yaml#style}) and mapping
 * key kinds ({@link Yaml#keyKind}). An alias whose anchor lies outside the written subtree is
 * expanded, and the first expansion takes the anchor so later aliases refer to it.
 */
public final class YamlEmitter {
  private YamlEmitter() {}

  @TruffleBoundary
  public static String emit(TapeValue value) {
    return emit(value, YamlEmitOptions.DEFAULT);
  }

  @TruffleBoundary
  public static String emit(TapeValue value, YamlEmitOptions options) {
    StringBuilder out = new StringBuilder();
    emit(value, options, out);
    return out.toString();
  }

  @TruffleBoundary
  public static byte[] emitUtf8(TapeValue value, YamlEmitOptions options) {
    return emit(value, options).getBytes(StandardCharsets.UTF_8);
  }

  /** One document holding {@code value}, appended to {@code out}. */
  @TruffleBoundary
  public static void emit(TapeValue value, YamlEmitOptions options, Appendable out) {
    try (YamlWriter writer = new YamlWriter(out, options)) {
      write(value, writer);
    }
  }

  /** A stream of documents, one per value. */
  @TruffleBoundary
  public static String emitAll(Iterable<TapeValue> documents, YamlEmitOptions options) {
    StringBuilder out = new StringBuilder();
    try (YamlWriter writer = new YamlWriter(out, options)) {
      for (TapeValue document : documents) write(document, writer);
    }
    return out.toString();
  }

  /** Writes {@code value} as the next node of an open writer, honoring its options. */
  @TruffleBoundary
  public static void write(TapeValue value, YamlWriter writer) {
    if (writer.options().preserveMetadata()) new Walk(writer).node(value, false);
    else value.export(writer);
  }

  private static final class Walk {
    private final YamlWriter writer;
    private final WithoutMetadata scalars;

    Walk(YamlWriter writer) {
      this.writer = writer;
      this.scalars = new WithoutMetadata(writer);
    }

    void node(TapeValue v, boolean expanding) {
      if (!expanding && Yaml.isAlias(v)) {
        String target = v.targetAnchorName();
        if (target != null && writer.hasAnchor(target)) writer.alias(target);
        else node(v, true);
        return;
      }
      String tag = Yaml.tag(v);
      if (tag != null) writer.tag(tag);
      String anchor = expanding ? v.targetAnchorName() : Yaml.anchor(v);
      if (anchor != null) writer.anchor(anchor);
      if (!expanding) writer.style(Yaml.style(v));
      switch (v.kind()) {
        case OBJECT -> {
          writer.beginObject(v.size());
          for (Map.Entry<TapeValue, TapeValue> member : v.members()) {
            TapeValue key = member.getKey();
            String keyTag = Yaml.tag(key);
            if (keyTag != null) writer.tag(keyTag);
            String keyAnchor = Yaml.anchor(key);
            if (keyAnchor != null) writer.anchor(keyAnchor);
            writer.style(Yaml.style(key));
            writer.key(key.stringValue(), keyKind(key));
            node(member.getValue(), false);
          }
          writer.end();
        }
        case ARRAY -> {
          writer.beginArray(v.size());
          for (TapeValue element : v.elements()) node(element, false);
          writer.end();
        }
        // Scalars export with their exact kind; their metadata was written above.
        default -> v.export(scalars);
      }
    }
  }

  private static @Nullable KeyKind keyKind(TapeValue key) {
    return switch ((key.metadataWord() >>> 5) & 7) {
      case 1 -> KeyKind.NULL;
      case 2 -> KeyKind.BOOL;
      case 3 -> KeyKind.INT;
      case 4 -> KeyKind.FLOAT;
      default -> null;
    };
  }

  /**
   * Forwards an export's values and structure to a writer without its tag, anchor and alias
   * events (aliases are expanded).
   */
  static final class WithoutMetadata implements TapeSink {
    private final YamlWriter writer;

    WithoutMetadata(YamlWriter writer) {
      this.writer = writer;
    }

    @Override public void beginObject(long members) { writer.beginObject(members); }
    @Override public void key(TapeText key) { writer.key(key); }
    @Override public void beginArray(long elements) { writer.beginArray(elements); }
    @Override public void end() { writer.end(); }
    @Override public void nullValue() { writer.nullValue(); }
    @Override public void booleanValue(boolean value) { writer.booleanValue(value); }
    @Override public void longValue(long value) { writer.longValue(value); }
    @Override public void doubleValue(double value) { writer.doubleValue(value); }
    @Override public void unsignedValue(long bits) { writer.unsignedValue(bits); }
    @Override public void decimalValue(TapeText token) { writer.decimalValue(token); }
    @Override public void stringValue(TapeText text) { writer.stringValue(text); }
  }
}
