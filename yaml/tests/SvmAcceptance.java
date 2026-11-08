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
