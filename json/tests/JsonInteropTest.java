package dev.elide.truffledata.json.tests;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.json.JsonDecoder;
import dev.elide.truffledata.json.JsonJvm;
import dev.elide.truffledata.json.JsonMessage;
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
}
