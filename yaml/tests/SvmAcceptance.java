package dev.elide.truffledata.yaml.tests;

import dev.elide.truffledata.tape.JavaSink;
import dev.elide.truffledata.tape.TapeSnapshot;
import dev.elide.truffledata.tape.TapeValue;
import dev.elide.truffledata.yaml.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** The same semantic/lifetime assertions run through JVM FFM and static bindings. */
public final class SvmAcceptance {
  private SvmAcceptance() {}

  static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  static void require(boolean condition) {
    if (!condition) throw new AssertionError();
  }

  static void rejected(Runnable action) {
    try {
      action.run();
      throw new AssertionError("Expected rejection");
    } catch (IllegalArgumentException | IllegalStateException expected) {
    }
  }

  public static void verify(YamlSession session) {
    sharedText(session);
    TapeSnapshot snapshot;
    try (YamlMessage message =
        session.decode(
            bytes(
                "a: &item {x: 31, text: 'hello'}\n"
                    + "b: *item\n"
                    + "n: .nan\n"
                    + "i: .inf\n"
                    + "hex: 0x1F\n"
                    + "big: 0x10000000000000000\n"))) {
      require(message.root().member("b").member("x").longValue() == 31);
      require(Double.isNaN(message.root().member("n").doubleValue()));
      require(message.root().member("i").doubleValue() == Double.POSITIVE_INFINITY);
      require(message.root().member("hex").numberText().equals("31"));
      require(message.root().member("hex").rawText().equals("0x1F"));
      require(message.root().member("big").numberText().equals("18446744073709551616"));
      JavaSink sink = new JavaSink();
      message.export(sink);
      Map<?, ?> tree = (Map<?, ?>) sink.result();
      require(tree != null && tree.get("a") == tree.get("b"));
      snapshot = message.snapshot();
    }
    require(snapshot.root().member("b").member("text").stringValue().equals("hello"));
    JavaSink copied = new JavaSink();
    snapshot.export(copied);
    Map<?, ?> copiedTree = (Map<?, ?>) copied.result();
    require(copiedTree != null && copiedTree.get("a") == copiedTree.get("b"));
    TapeValue escaped = session.with(bytes("x: 1"), root -> root.member("x"));
    rejected(escaped::longValue);
    rejected(() -> session.decode(bytes("x: 1\nx: 2")));
    rejected(() -> session.decode(bytes("---\na\n---\nb\n")));
    try (YamlMessage empty = session.decode(new byte[0])) {
      require(empty.root().kind() == TapeValue.Kind.NULL);
    }
    try (YamlStream stream = session.stream(new YamlStreamOptions(1024, 32))) {
      byte[] input = bytes("---\nx: 1\n---\nx: 2\n");
      require(stream.feed(input) == input.length);
      stream.finish();
      TapeValue first = stream.next();
      require(first != null && first.member("x").longValue() == 1);
      YamlMessage retained = stream.retain();
      try (retained) {
        TapeValue second = stream.next();
        require(second != null && second.member("x").longValue() == 2);
        require(stream.next() == null);
        require(retained.root().member("x").longValue() == 1);
      }
    }
  }

  private static void sharedText(YamlSession session) {
    String key = "x".repeat(1024);
    StringBuilder input = new StringBuilder("anchor: &key " + key + "\nitems: [");
    for (int i = 0; i < 20; i++) input.append(i == 0 ? "" : ", ").append("{*key : value}");
    input.append("]\nunicode: [café, 雪, 😀, '', ascii]\n");
    var keys = new dev.elide.truffledata.tape.TruffleSink.KeyCache();
    var sink = new dev.elide.truffledata.tape.TruffleSink(keys);
    session.parse(bytes(input.toString()), sink);
    Map<?, ?> first = (Map<?, ?>) java.util.Objects.requireNonNull(sink.result());
    var utf16 = com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16;
    var names = com.oracle.truffle.api.strings.TruffleString.fromJavaStringUncached("unicode", utf16);
    var values = (java.util.List<?>) java.util.Objects.requireNonNull(first.get(names));
    require(values.size() == 5);
    for (int i = 0; i < 5; i++) require(((com.oracle.truffle.api.strings.TruffleString) values.get(i))
        .toJavaStringUncached().equals(new String[] {"café", "雪", "😀", "", "ascii"}[i]));
    // A second parse cannot overwrite arrays retained by strings in the first result.
    session.parse(bytes("unicode: [other]"), new dev.elide.truffledata.tape.TruffleSink(keys));
    require(((com.oracle.truffle.api.strings.TruffleString) values.get(1)).toJavaStringUncached().equals("雪"));
    var items = (java.util.List<?>) java.util.Objects.requireNonNull(first.get(
        com.oracle.truffle.api.strings.TruffleString.fromJavaStringUncached("items", utf16)));
    require(items.size() == 20);
    for (Object item : items) {
      Map<?, ?> map = (Map<?, ?>) item;
      require(map.size() == 1);
      require(((com.oracle.truffle.api.strings.TruffleString) map.keySet().iterator().next()).toJavaStringUncached().equals(key));
    }
  }

  public static void main(String[] args) {
    try (YamlSession session = YamlSession.open()) {
      verify(session);
    }
    try (YamlSession session = YamlSession.open()) {
      TypedAcceptance.verify(session);
    }
    CompiledReads.run();
    System.out.println("YAML generated SVM bindings, aliases, snapshots and streams passed");
  }
}
