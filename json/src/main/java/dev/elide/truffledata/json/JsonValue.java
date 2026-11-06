package dev.elide.truffledata.json;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.myna.values.ValueTape;
import java.util.NoSuchElementException;

/** Reusable scoped index view. Every operation checks its message before touching native memory. */
public final class JsonValue {
  private final JsonOwner owner;
  private final long node;

  JsonValue(JsonOwner owner, long node) {
    this.owner = owner;
    this.node = node;
  }

  public enum Kind {
    NULL,
    BOOLEAN,
    NUMBER,
    STRING,
    ARRAY,
    OBJECT
  }

  public Kind kind() {
    owner.check();
    return switch (owner.tape.tag(node)) {
      case ValueTape.NULL -> Kind.NULL;
      case ValueTape.BOOL -> Kind.BOOLEAN;
      case ValueTape.STRING -> Kind.STRING;
      case ValueTape.ARRAY -> Kind.ARRAY;
      case ValueTape.OBJECT -> Kind.OBJECT;
      case ValueTape.I64, ValueTape.U64, ValueTape.F64, ValueTape.DECIMAL -> Kind.NUMBER;
      default -> throw new IllegalStateException("Unknown JSON node kind");
    };
  }

  public boolean booleanValue() {
    owner.check();
    return owner.tape.booleanValue(node);
  }

  @TruffleBoundary
  public String stringValue() {
    owner.check();
    return owner.tape.stringValue(node);
  }

  /** Exact lexical token; no bounded decimal or floating-point conversion. */
  @TruffleBoundary
  public String numberText() {
    if (kind() != Kind.NUMBER) throw new IllegalStateException("Not a JSON number");
    return owner.lexicalText(node);
  }

  /** Source spelling of a string, key or number, including quotes and escapes. */
  @TruffleBoundary
  public String rawText() {
    Kind kind = kind();
    if (kind != Kind.NUMBER && kind != Kind.STRING)
      throw new IllegalStateException("JSON containers, booleans and null have no raw text");
    return owner.lexicalText(node);
  }

  public long size() {
    Kind kind = kind();
    if (kind != Kind.ARRAY && kind != Kind.OBJECT)
      throw new IllegalStateException("Not a JSON container");
    return owner.tape.length(node);
  }

  /** Prepare and retain a key with ValueTape.Utf8Key.of for repeated lookup. */
  public JsonValue member(ValueTape.Utf8Key key) {
    owner.check();
    long found = owner.tape.find(node, key);
    if (found < 0) throw new NoSuchElementException("Missing JSON member");
    return new JsonValue(owner, found);
  }

  @TruffleBoundary
  public JsonValue member(String key) {
    return member(ValueTape.Utf8Key.of(key));
  }

  /** Sequential traversal of child subtrees; a future array index will accelerate random access. */
  public JsonValue element(long index) {
    if (kind() != Kind.ARRAY) throw new IllegalStateException("Not a JSON array");
    long count = owner.tape.length(node);
    if (index < 0 || index >= count) throw new IndexOutOfBoundsException("JSON array index");
    long child = owner.tape.first(node);
    for (long i = 0; i < index; ++i) child = owner.tape.next(child);
    return new JsonValue(owner, child);
  }
}
