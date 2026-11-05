package dev.elide.truffledata.json.tests;

import dev.elide.myna.values.NativeValueTape;
import dev.elide.myna.values.ValueTape;
import dev.elide.truffledata.json.generated.Json;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;

/** Executes generated static bindings and Myna's direct reader in a real native image. */
public final class SvmAcceptance {
  private static void require(boolean condition) {
    if (!condition) throw new AssertionError("SVM JSON acceptance");
  }

  private static int decode(String source, VoidPointer result) {
    byte[] input = source.getBytes(StandardCharsets.UTF_8);
    try (PinnedObject pinned = PinnedObject.create(input)) {
      int status =
          Json.decode(
              pinned.addressOfArrayElement(0),
              input.length,
              1 << 20,
              1 << 20,
              10000,
              1 << 20,
              128,
              result);
      Arrays.fill(input, (byte) 'x');
      return status;
    }
  }

  private static NativeValueTape tape(VoidPointer result) {
    Pointer descriptor = (Pointer) Json.JsonResult.view(result);
    require(descriptor.readInt(0) == ValueTape.ABI_VERSION);
    NativeValueTape tape = new NativeValueTape(descriptor);
    tape.validate();
    return tape;
  }

  private static void scoped() throws InterruptedException {
    byte[] input =
        "{\"a\":[true, null, {\"s\":\"retained child\"}],\"n\":1e9999999999999999}"
            .getBytes(StandardCharsets.UTF_8);
    var message = dev.elide.truffledata.json.JsonSvm.decode(input);
    var root = message.root();
    var child = root.member("a").element(2).member("s");
    Arrays.fill(input, (byte) 'x');
    require(child.stringValue().equals("retained child"));
    require(root.member("a").element(0).booleanValue());
    require(root.member("n").numberText().equals("1e9999999999999999"));
    java.util.concurrent.atomic.AtomicInteger rejected =
        new java.util.concurrent.atomic.AtomicInteger();
    Thread other =
        new Thread(
            () -> {
              try {
                child.stringValue();
              } catch (IllegalStateException expected) {
                rejected.incrementAndGet();
              }
              try {
                message.close();
              } catch (IllegalStateException expected) {
                rejected.incrementAndGet();
              }
            });
    other.start();
    other.join();
    require(rejected.get() == 2);
    require(child.stringValue().equals("retained child"));
    message.close();
    message.close();
    try {
      child.stringValue();
      throw new AssertionError("closed child readable");
    } catch (IllegalStateException expected) {
    }
    try {
      root.member("a");
      throw new AssertionError("closed root readable");
    } catch (IllegalStateException expected) {
    }
  }

  private static void typed() {
    VoidPointer fields = UnmanagedMemory.calloc(Json.JsonScalarField.SIZE);
    VoidPointer plan = UnmanagedMemory.calloc(Json.JsonPlan.SIZE);
    VoidPointer result = UnmanagedMemory.calloc(Json.JsonTypedResult.SIZE);
    if (fields.isNull() || plan.isNull() || result.isNull()) {
      if (fields.isNonNull()) UnmanagedMemory.free(fields);
      if (plan.isNonNull()) UnmanagedMemory.free(plan);
      if (result.isNonNull()) UnmanagedMemory.free(result);
      throw new OutOfMemoryError();
    }
    try {
      try (var name = CTypeConversion.toCString("n")) {
        Json.JsonScalarField.name(fields, name.get());
        Json.JsonScalarField.name_length(fields, 1);
        Json.JsonScalarField.kind(fields, 1);
        Json.JsonScalarField.flags(fields, 1);
        Json.JsonScalarField.minimum(fields, -128);
        Json.JsonScalarField.maximum(fields, 127);
        require(Json.plan_create(fields, 1, 1, plan) == 0);
      }
      byte[] input = "{\"n\":127}".getBytes(StandardCharsets.UTF_8);
      try (var pinned = PinnedObject.create(input)) {
        require(
            Json.typed_decode(
                    plan, pinned.addressOfArrayElement(0), input.length, 1024, 1024, result)
                == 0);
      }
      Json.plan_release(plan);
      require(Json.JsonTypedResult.present(result) == 1);
      require(Json.JsonTypedResult.nulls(result) == 0);
      require(Json.JsonTypedResult.count(result) == 1);
      require(Json.JsonScalarSlot.bits(Json.JsonTypedResult.slots(result)) == 127);
      require(Json.JsonTypedResult.index_allocations(result) == 1);
      Json.typed_release(result);
      Json.typed_release(result);
      require(Json.JsonTypedResult.owner(result).isNull());
    } finally {
      Json.typed_release(result);
      Json.plan_release(plan);
      UnmanagedMemory.free(result);
      UnmanagedMemory.free(plan);
      UnmanagedMemory.free(fields);
    }
  }

  public static void main(String[] args) throws InterruptedException {
    typed();
    scoped();
    require(CTypeConversion.toJavaString(Json.fingerprint()).equals(Json.ABI_FINGERPRINT));
    VoidPointer first = UnmanagedMemory.calloc(Json.JsonResult.SIZE);
    if (first.isNull()) throw new OutOfMemoryError();
    VoidPointer second = UnmanagedMemory.calloc(Json.JsonResult.SIZE);
    if (second.isNull()) {
      UnmanagedMemory.free(first);
      throw new OutOfMemoryError();
    }
    try {
      require(
          decode(
                  "{\"text\":\"a long escaped string \\u4e2d"
                      + " 😀\",\"n\":1e999999999999999999,\"a\":[true,null]}",
                  first)
              == 0);
      NativeValueTape view = tape(first);
      long text = view.find(0, ValueTape.Utf8Key.of("text"));
      long number = view.find(0, ValueTape.Utf8Key.of("n"));
      require(view.stringValue(text).equals("a long escaped string 中 😀"));
      require(view.numberText(number).equals("1e999999999999999999"));
      require(view.find(0, ValueTape.Utf8Key.of("absent")) == -1);
      require(
          Json.JsonResult.index_peak_bytes(first) >= Json.JsonResult.index_retained_bytes(first));
      require(decode("[]", first) == 1);
      for (String source :
          new String[] {"true", "null", "\"short\"", "[]", "{}", "-12.5e2", "[0,{\"x\":false}]"}) {
        require(decode(source, second) == 0);
        tape(second);
        Json.release(second);
        Json.release(second);
        require(Json.JsonResult.owner(second).isNull());
      }
      require(view.stringValue(text).equals("a long escaped string 中 😀"));
      for (String source :
          new String[] {"[1,]", "{\"a\":1,\"\\u0061\":2}", "[01]", "{} true", "\"\\uD800\""}) {
        require(decode(source, second) == 4);
        require(Json.JsonResult.owner(second).isNull());
        require(Json.JsonResult.view(second).isNull());
      }
    } finally {
      Json.release(second);
      Json.release(first);
      UnmanagedMemory.free(second);
      UnmanagedMemory.free(first);
    }
    System.out.println("JSON generated SVM binding and Myna reader acceptance passed");
  }
}
