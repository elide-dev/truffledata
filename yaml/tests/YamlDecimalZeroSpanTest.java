package dev.elide.truffledata.yaml.tests;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import dev.elide.truffledata.tape.TapeValue;
import dev.elide.truffledata.yaml.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Quoted YAML scalars carrying an explicit numeric tag (and block scalars) resolve to a compact
 * number node that the decoder publishes with a 0 raw span when {@code retainSource} is off (the
 * default: {@code JavaDecoder.lexical} returns 0 for every quoted and block style). The shared
 * {@link TapeValue} view over such a node must answer the number interop surface ({@code
 * numberText()} and every {@code fitsIn*}/{@code as*} message) from the pooled digits / compact
 * value instead of throwing an unchecked {@link IllegalStateException} out of the {@link
 * InteropLibrary} boundary. Both the pure-Java kernel ({@link YamlJava}) and the native kernel
 * ({@link YamlJvm}) reproduce identically.
 */
final class YamlDecimalZeroSpanTest {
  private static final String BIG = "123456789012345678901234567890123"; // 33 integral digits, > uint64
  private static final BigInteger BIG_INT = new BigInteger(BIG);

  private static final byte[] DOC =
      bytes(
          "n: !!int \"123456789012345678901234567890123\"\n"
              + "q: !!int '123456789012345678901234567890123'\n"
              + "g: !!float \"1e400\"\n"
              + "i: !!int \"42\"\n"
              + "d: !!float \"1.5\"\n"
              + "m: 123456789012345678901234567890123\n");

  private static Path library() {
    String override = System.getProperty("yaml.library");
    return override != null
        ? Path.of(override)
        : Path.of(System.getenv("TEST_SRCDIR"), System.getenv("TEST_WORKSPACE"), "yaml/native/yaml_shared.so");
  }

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static void probe(YamlSession session) throws Exception {
    try (YamlMessage message = session.decode(DOC)) {
      var root = message.root();
      var interop = InteropLibrary.getUncached();

      // Quoted (double and single) !!int beyond uint64 -> DECIMAL with a 0 raw span.
      TapeValue n = root.member("n");
      assertEquals(TapeValue.Kind.NUMBER, n.kind());
      assertEquals(BIG, n.numberText());
      assertEquals(BIG, n.stringValue());
      assertTrue(interop.fitsInBigInteger(n));
      assertEquals(BIG_INT, interop.asBigInteger(n));
      assertFalse(interop.fitsInLong(n));
      assertThrows(UnsupportedMessageException.class, () -> interop.asLong(n));
      assertFalse(interop.fitsInDouble(n)); // 33 significant digits are not exactly a binary64
      assertThrows(UnsupportedMessageException.class, () -> interop.asDouble(n));

      TapeValue q = root.member("q");
      assertEquals(BIG, q.numberText());
      assertTrue(interop.fitsInBigInteger(q));
      assertEquals(BIG_INT, interop.asBigInteger(q));
      assertThrows(UnsupportedMessageException.class, () -> interop.asLong(q));

      // Quoted !!float overflow -> DECIMAL with a 0 raw span; integral and 401 digits, fits BigInteger.
      TapeValue g = root.member("g");
      assertEquals("1e400", g.numberText());
      assertTrue(interop.fitsInBigInteger(g));
      assertEquals(BigInteger.TEN.pow(400), interop.asBigInteger(g));
      assertFalse(interop.fitsInLong(g));
      assertThrows(UnsupportedMessageException.class, () -> interop.asLong(g));

      // Quoted !!int that fits a long -> I64 with a 0 raw span; numberText and interop answer.
      TapeValue i = root.member("i");
      assertEquals(TapeValue.Kind.NUMBER, i.kind());
      assertEquals("42", i.numberText());
      assertTrue(interop.fitsInLong(i));
      assertEquals(42L, interop.asLong(i));
      assertTrue(interop.fitsInBigInteger(i));
      assertEquals(BigInteger.valueOf(42L), interop.asBigInteger(i));

      // Quoted !!float that fits binary64 -> F64 with a 0 raw span; numberText and interop answer.
      TapeValue d = root.member("d");
      assertEquals("1.5", d.numberText());
      assertTrue(interop.fitsInDouble(d));
      assertEquals(1.5, interop.asDouble(d));
      assertFalse(interop.fitsInLong(d)); // 1.5 is not integral
      assertThrows(UnsupportedMessageException.class, () -> interop.asLong(d));

      // Control: a plain single-line scalar keeps a real raw span and behaves exactly as documented.
      TapeValue m = root.member("m");
      assertEquals(BIG, m.numberText());
      assertTrue(interop.fitsInBigInteger(m));
      assertEquals(BIG_INT, interop.asBigInteger(m));
      assertFalse(interop.fitsInLong(m));
      assertThrows(UnsupportedMessageException.class, () -> interop.asLong(m));
      assertEquals(BIG, m.rawText()); // plain source spelling is retained regardless of retainSource

      // Quoted tagged numbers have no source spelling without retainSource: rawText still throws
      // (the documented "value has no raw text" path), unchanged by the number-text fix.
      assertThrows(IllegalStateException.class, n::rawText);
      assertThrows(IllegalStateException.class, i::rawText);
    }
  }

  @Test void javaKernelAnswersNumberInteropForZeroSpanQuotedTaggedScalars() throws Exception {
    try (YamlSession session = YamlJava.openSession()) {
      probe(session);
    }
  }

  @Test void nativeKernelAnswersNumberInteropForZeroSpanQuotedTaggedScalars() throws Exception {
    try (YamlSession session = YamlJvm.openSession(library())) {
      probe(session);
    }
  }
}
