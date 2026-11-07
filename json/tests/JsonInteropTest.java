package dev.elide.truffledata.json.tests;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import dev.elide.truffledata.json.JsonDecoder;
import dev.elide.truffledata.json.JsonJvm;
import dev.elide.truffledata.json.JsonMessage;
import dev.elide.truffledata.json.JsonValue;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

final class JsonInteropTest {
  private static final String SOURCE =
      "{\"z\":1,\"a\":[9007199254740993,18446744073709551615,1.5,-0,1E400,true,null],"
          + "\"s\":\"escaped \\u4e2d text that is long\",\"o\":{\"k\":2}}";

  private static Context context() {
    return Context.newBuilder().allowHostAccess(HostAccess.ALL).build();
  }

  private static JsonMessage decode(JsonDecoder decoder) {
    return decoder.decode(SOURCE.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void exposesObjectsAsOrderedMembers() {
    try (Context context = context();
        JsonDecoder decoder = JsonJvm.open(JvmScopedTest.library());
        JsonMessage message = decode(decoder)) {
      Value root = context.asValue(message.root());
      assertTrue(root.hasMembers());
      assertEquals(List.of("z", "a", "s", "o"), List.copyOf(root.getMemberKeys()));
      assertTrue(root.hasMember("o"));
      assertFalse(root.hasMember("missing"));
      assertEquals(2, root.getMember("o").getMember("k").asLong());
      assertNull(root.getMember("missing"));
    }
  }

  @Test
  void exposesArraysAndScalarsWithExactNumberContracts() {
    try (Context context = context();
        JsonDecoder decoder = JsonJvm.open(JvmScopedTest.library());
        JsonMessage message = decode(decoder)) {
      Value array = context.asValue(message.root()).getMember("a");
      assertTrue(array.hasArrayElements());
      assertEquals(7, array.getArraySize());
      Value big = array.getArrayElement(0);
      assertTrue(big.fitsInLong());
      assertEquals(9007199254740993L, big.asLong());
      assertFalse(big.fitsInDouble(), "2^53+1 is not exactly representable");
      Value unsigned = array.getArrayElement(1);
      assertFalse(unsigned.fitsInLong());
      assertTrue(unsigned.fitsInBigInteger());
      assertEquals(new BigInteger("18446744073709551615"), unsigned.asBigInteger());
      Value half = array.getArrayElement(2);
      assertTrue(half.fitsInDouble());
      assertFalse(half.fitsInLong());
      assertEquals(1.5, half.asDouble());
      Value negativeZero = array.getArrayElement(3);
      assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(negativeZero.asDouble()));
      Value huge = array.getArrayElement(4);
      assertTrue(huge.isNumber());
      assertFalse(huge.fitsInDouble());
      assertFalse(huge.fitsInLong());
      assertTrue(array.getArrayElement(5).asBoolean());
      assertTrue(array.getArrayElement(6).isNull());
      assertEquals("escaped 中 text that is long", context.asValue(message.root()).getMember("s").asString());
    }
  }

  @Test
  void rejectsReadsAfterClose() {
    try (Context context = context(); JsonDecoder decoder = JsonJvm.open(JvmScopedTest.library())) {
      JsonMessage message = decode(decoder);
      Value root = context.asValue(message.root());
      message.close();
      PolyglotException failure = assertThrows(PolyglotException.class, () -> root.getMember("z"));
      assertTrue(String.valueOf(failure.getMessage()).contains("JSON message closed"), failure.getMessage());
    }
  }
  @Test
  void unencodableMemberNamesMatchNothing() {
    try (Context context = context();
        JsonDecoder decoder = JsonJvm.open(JvmScopedTest.library());
        JsonMessage message = decoder.decode("{\"?\":1}".getBytes(StandardCharsets.UTF_8))) {
      Value root = context.asValue(message.root());
      assertEquals(1, root.getMember("?").asLong());
      // A lone surrogate has no UTF-8 encoding; lossy encoding would turn it into "?".
      assertFalse(root.hasMember("\uD800"));
      assertNull(root.getMember("\uD800"));
    }
  }

  @Test
  void memberLookupsBeyondTheNameCacheStayCorrect() {
    StringBuilder source = new StringBuilder("{");
    for (int i = 0; i < 8; i++) source.append(i == 0 ? "" : ",").append("\"k").append(i).append("\":").append(i);
    try (Context context = context();
        JsonDecoder decoder = JsonJvm.open(JvmScopedTest.library());
        JsonMessage message = decoder.decode(source.append('}').toString().getBytes(StandardCharsets.UTF_8))) {
      Value root = context.asValue(message.root());
      for (int round = 0; round < 3; round++)
        for (int i = 0; i < 8; i++) assertEquals(i, root.getMember("k" + i).asLong());
    }
  }
  @Test
  void negativeZeroAndExtremeDecimalsKeepExactContracts() {
    try (Context context = context();
        JsonDecoder decoder = JsonJvm.open(JvmScopedTest.library());
        JsonMessage message =
            decoder.decode(
                "[-0,1E999999999,1E-999999999,1180591620717411303424,1E400,-0.0]"
                    .getBytes(StandardCharsets.UTF_8))) {
      Value array = context.asValue(message.root());
      Value negativeZero = array.getArrayElement(0);
      assertTrue(negativeZero.fitsInDouble());
      assertFalse(negativeZero.fitsInInt(), "-0 loses its sign as an int");
      assertFalse(negativeZero.fitsInLong());
      assertFalse(negativeZero.fitsInBigInteger());
      assertFalse(array.getArrayElement(5).fitsInLong());
      long start = System.nanoTime();
      Value hugeExponent = array.getArrayElement(1);
      assertTrue(hugeExponent.isNumber());
      assertFalse(hugeExponent.fitsInLong());
      assertFalse(hugeExponent.fitsInBigInteger());
      assertFalse(hugeExponent.fitsInDouble());
      // Underflow rounds to a finite binary64, so the token is F64 0.0, not DECIMAL.
      Value tinyExponent = array.getArrayElement(2);
      assertEquals(0.0, tinyExponent.asDouble());
      assertEquals("1E-999999999", message.root().element(2).numberText());
      assertTrue(System.nanoTime() - start < 5_000_000_000L, "exponent size must not drive cost");
      Value twoToSeventy = array.getArrayElement(3);
      assertFalse(twoToSeventy.fitsInLong());
      assertTrue(twoToSeventy.fitsInDouble());
      assertEquals(new BigInteger("1180591620717411303424"), twoToSeventy.asBigInteger());
      Value overflow = array.getArrayElement(4);
      assertTrue(overflow.fitsInBigInteger());
      assertEquals(BigInteger.TEN.pow(400), overflow.asBigInteger());
      assertFalse(overflow.fitsInDouble());
    }
  }

  @Test
  void narrowNumbersAndTruffleStringsFollowExactContracts() throws Exception {
    InteropLibrary interop = InteropLibrary.getUncached();
    try (JsonDecoder decoder = JsonJvm.open(JvmScopedTest.library());
        JsonMessage message =
            decoder.decode("[1,300,70000,2.0,0.1,\"text\"]".getBytes(StandardCharsets.UTF_8))) {
      JsonValue root = message.root();
      JsonValue one = root.element(0);
      assertEquals(1, one.longValue());
      assertTrue(interop.fitsInByte(one));
      assertEquals((byte) 1, interop.asByte(one));
      assertTrue(interop.fitsInShort(one));
      assertEquals((short) 1, interop.asShort(one));
      assertEquals(1, interop.asInt(one));
      JsonValue beyondByte = root.element(1);
      assertFalse(interop.fitsInByte(beyondByte));
      assertThrows(UnsupportedMessageException.class, () -> interop.asByte(beyondByte));
      assertEquals((short) 300, interop.asShort(beyondByte));
      JsonValue beyondShort = root.element(2);
      assertFalse(interop.fitsInShort(beyondShort));
      assertThrows(UnsupportedMessageException.class, () -> interop.asShort(beyondShort));
      assertEquals(70000, interop.asInt(beyondShort));
      // An integral binary64 converts exactly to integer and float types.
      JsonValue two = root.element(3);
      assertEquals(2.0, two.doubleValue());
      assertEquals(2L, interop.asLong(two));
      assertTrue(interop.fitsInFloat(two));
      assertEquals(2.0f, interop.asFloat(two));
      JsonValue tenth = root.element(4);
      assertFalse(interop.fitsInFloat(tenth), "0.1 is not exact in binary32");
      assertThrows(UnsupportedMessageException.class, () -> interop.asFloat(tenth));
      assertThrows(UnsupportedMessageException.class, () -> interop.asLong(tenth));
      assertEquals("text", interop.asTruffleString(root.element(5)).toJavaStringUncached());
      assertThrows(UnsupportedMessageException.class, () -> interop.asTruffleString(one));
      assertEquals(JsonValue.Kind.NUMBER, JsonValue.Kind.valueOf("NUMBER"));
      assertEquals(6, JsonValue.Kind.values().length);
    }
  }
  @Test
  void largeExactIntegersFitDoubleAndHugeDecimalsStayCheap() {
    String huge = "9".repeat(20_000);
    try (Context context = context();
        JsonDecoder decoder = JsonJvm.open(JvmScopedTest.library());
        JsonMessage message =
            decoder.decode(
                ("[1152921504606846976,9223372036854775807,9223372036854775808,"
                        + "18446744073709551615,9007199254740993," + huge + "]")
                    .getBytes(StandardCharsets.UTF_8))) {
      Value array = context.asValue(message.root());
      assertTrue(array.getArrayElement(0).fitsInDouble(), "2^60 is an exact double");
      assertEquals(0x1p60, array.getArrayElement(0).asDouble());
      assertFalse(array.getArrayElement(1).fitsInDouble(), "2^63-1 rounds");
      assertTrue(array.getArrayElement(2).fitsInDouble(), "U64 2^63 is an exact double");
      assertFalse(array.getArrayElement(3).fitsInDouble(), "2^64-1 rounds");
      assertFalse(array.getArrayElement(4).fitsInDouble(), "2^53+1 rounds");
      long start = System.nanoTime();
      Value long20k = array.getArrayElement(5);
      assertTrue(long20k.isNumber());
      assertFalse(long20k.fitsInBigInteger());
      assertFalse(long20k.fitsInLong());
      assertFalse(long20k.fitsInDouble());
      assertTrue(System.nanoTime() - start < 2_000_000_000L, "token length must bound conversion cost");
      assertEquals(huge, message.root().element(5).numberText());
    }
  }

  /** One cached, adopted InteropLibrary call site (member caches and position hints per name). */
  private static final class Site extends com.oracle.truffle.api.nodes.RootNode {
    @Child InteropLibrary library = InteropLibrary.getFactory().createDispatched(3);

    Site() {
      super(null);
    }

    @Override
    public Object execute(com.oracle.truffle.api.frame.VirtualFrame frame) {
      return 0;
    }
  }

  private static InteropLibrary site() {
    Site root = new Site();
    root.adoptChildren();
    return root.library;
  }

  private static JsonMessage message(JsonDecoder decoder, String source) {
    return decoder.decode(source.getBytes(StandardCharsets.UTF_8));
  }

  // Plan Task C1: position hints are validated by hash, tag, length and bytes, never trusted.
  @Test
  void hintMissAfterShapeChange() throws Exception {
    try (JsonDecoder decoder = JsonJvm.open(JvmScopedTest.library());
        JsonMessage first = message(decoder, "{\"id\":1,\"a\":2,\"b\":3}");
        JsonMessage last = message(decoder, "{\"a\":2,\"b\":3,\"x\":\"id\",\"id\":4}");
        // Offset 7 (where `last` leaves the hint) is the *value* string "id" here, not a key.
        JsonMessage trap = message(decoder, "{\"a\":[1],\"b\":2,\"c\":\"id\"}");
        JsonMessage none = message(decoder, "{\"a\":2,\"b\":\"id\"}")) {
      // One cached call site, so one hint per member name across all receivers.
      InteropLibrary site = site();
      for (int round = 0; round < 3; round++) {
        assertEquals(1L, site.asLong(site.readMember(first.root(), "id")));
        assertTrue(site.isMemberReadable(first.root(), "id"));
        assertEquals(4L, site.asLong(site.readMember(last.root(), "id")));
        assertFalse(site.isMemberReadable(trap.root(), "id"));
        assertThrows(
            com.oracle.truffle.api.interop.UnknownIdentifierException.class,
            () -> site.readMember(trap.root(), "id"));
        assertFalse(site.isMemberReadable(none.root(), "id"));
        assertEquals("id", site.asString(site.readMember(none.root(), "b")));
      }
    }
    // A hint learned at offset 5 must not match a nested object's "id" at the same distance.
    try (JsonDecoder decoder = JsonJvm.open(JvmScopedTest.library());
        JsonMessage outer = message(decoder, "{\"a\":{\"b\":1},\"id\":5}");
        JsonMessage nested = message(decoder, "{\"c\":1,\"d\":{\"id\":7}}")) {
      InteropLibrary site = site();
      for (int round = 0; round < 3; round++) {
        assertEquals(5L, site.asLong(site.readMember(outer.root(), "id")));
        assertFalse(site.isMemberReadable(nested.root(), "id"));
        assertThrows(
            com.oracle.truffle.api.interop.UnknownIdentifierException.class,
            () -> site.readMember(nested.root(), "id"));
        assertTrue(site.isMemberReadable(outer.root(), "id"));
      }
    }
  }

  @Test
  void escapedLongAndNonAsciiKeysMatchTheirDecodedBytes() throws Exception {
    String longKey = "a_member_name_longer_than_sixteen_bytes";
    try (JsonDecoder decoder = JsonJvm.open(JvmScopedTest.library());
        JsonMessage message =
            message(
                decoder,
                "{\"\\u0069d\":9,\"" + longKey + "\":7,\"\u00e9t\u00e9\":5,\"k\\u00e9y_long_escaped_name\":6,\"\":3}")) {
      InteropLibrary site = site();
      JsonValue root = message.root();
      for (int round = 0; round < 2; round++) {
        assertEquals(9L, site.asLong(site.readMember(root, "id")));
        assertEquals(7L, site.asLong(site.readMember(root, longKey)));
        assertEquals(5L, site.asLong(site.readMember(root, "\u00e9t\u00e9")));
        assertEquals(6L, site.asLong(site.readMember(root, "k\u00e9y_long_escaped_name")));
        assertEquals(3L, site.asLong(site.readMember(root, "")));
        assertFalse(site.isMemberReadable(root, "\uD800"));
      }
      assertEquals(7L, root.member(dev.elide.truffledata.json.JsonKey.of(longKey)).longValue());
      assertEquals(longKey.length(), dev.elide.truffledata.json.JsonKey.of(longKey).utf8Length());
      assertThrows(IllegalArgumentException.class, () -> dev.elide.truffledata.json.JsonKey.of("\uD800"));
      assertThrows(java.util.NoSuchElementException.class, () -> root.member("absent"));
      assertThrows(IllegalStateException.class, () -> root.member("id").member("x"));
    }
  }

  @Test
  void wideObjectsResolveMembersThroughADirectory() throws Exception {
    StringBuilder source = new StringBuilder("{");
    for (int i = 0; i < 1000; i++) source.append(i == 0 ? "" : ",").append("\"k").append(i).append("\":").append(i);
    source.append("}");
    try (JsonDecoder decoder = JsonJvm.open(JvmScopedTest.library());
        JsonMessage message = message(decoder, source.toString())) {
      InteropLibrary site = site();
      JsonValue root = message.root();
      assertEquals(0, message.directories());
      for (int i = 0; i < 1000; i += 37) assertEquals((long) i, root.member("k" + i).longValue());
      assertEquals(999L, site.asLong(site.readMember(root, "k999")));
      assertFalse(site.isMemberReadable(root, "k1000"));
      assertEquals(1, message.directories());
      // Snapshots carry the side table: the same reads, and their own directory.
      var snapshot = message.snapshot();
      assertEquals(998L, site.asLong(site.readMember(snapshot.root(), "k998")));
      assertEquals(500L, snapshot.root().member("k500").longValue());
      assertFalse(site.isMemberReadable(snapshot.root(), "absent"));
      assertEquals(1, snapshot.directories());
      // A small nested object never builds one.
      try (JsonMessage small = message(decoder, "{\"o\":{\"a\":1,\"b\":2}}")) {
        assertEquals(2L, small.root().member("o").member("b").longValue());
        assertEquals(0, small.directories());
      }
    }
  }
}
