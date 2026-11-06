package dev.elide.truffledata.json.tests;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import dev.elide.myna.values.NativeValueTape;
import dev.elide.myna.values.ValueTape;
import dev.elide.truffledata.*;
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
    require(child.rawText().equals("\"retained child\""));
    require(root.member("n").rawText().equals("1e9999999999999999"));
    try {
      root.member("a").rawText();
      throw new AssertionError("container raw text");
    } catch (IllegalStateException expected) {
    }
    var interop = InteropLibrary.getUncached();
    require(interop.hasMembers(root) && interop.hasArrayElements(root.member("a")));
    try {
      interop.asLong(interop.readArrayElement(root.member("a"), 2));
      throw new AssertionError("asLong on a JSON object");
    } catch (com.oracle.truffle.api.interop.UnsupportedMessageException expected) {
      // element 2 is an object: asLong is unsupported, which is the contract under test
    } catch (com.oracle.truffle.api.interop.InvalidArrayIndexException failure) {
      throw new AssertionError(failure);
    }
    try {
      require(interop.isNull(interop.readArrayElement(root.member("a"), 1)));
      require(interop.asBoolean(interop.readArrayElement(root.member("a"), 0)));
      require(interop.asString(interop.readMember(interop.readArrayElement(root.member("a"), 2), "s")).equals("retained child"));
      require(!interop.fitsInDouble(interop.readMember(root, "n")) && interop.isNumber(interop.readMember(root, "n")));
    } catch (com.oracle.truffle.api.interop.InteropException failure) {
      throw new AssertionError(failure);
    }
    var assumption = message.assumption();
    require(assumption.isValid());
    var snapshot = message.snapshot();
    message.close();
    message.close();
    require(!assumption.isValid());
    require(snapshot.root().member("a").element(2).member("s").stringValue().equals("retained child"));
    require(snapshot.root().member("n").numberText().equals("1e9999999999999999"));
    require(snapshot.root().member("a").element(2).member("s").rawText().equals("\"retained child\""));
    try {
      child.kind();
      throw new AssertionError("closed child kind readable");
    } catch (IllegalStateException expected) {
    }
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

  private static void schemaTyped() throws Exception {
    var data = DataLibrary.factory().getUncached();
    var schema =
        dev.elide.truffledata.Schema.of(
            dev.elide.truffledata.Field.integer("n", 8, true, false, false),
            new dev.elide.truffledata.Field(
                "s",
                dev.elide.truffledata.Field.Kind.STRING,
                true,
                true,
                dev.elide.truffledata.Constraints.NONE.withLength(
                    new dev.elide.truffledata.Constraints.Length(
                        1, 1, dev.elide.truffledata.Constraints.LengthUnit.CODE_POINTS))),
            dev.elide.truffledata.Field.integer("u", 64, false, false, false));
    var plan = dev.elide.truffledata.json.JsonPlan.compile(schema);
    byte[] input =
        "{\"n\":127,\"s\":\"😀\",\"u\":18446744073709551615}".getBytes(StandardCharsets.UTF_8);
    var record = plan.decode(input);
    Arrays.fill(input, (byte) 'x');
    for (String bad :
        new String[] {"{\"n\":128,\"u\":0}", "{\"n\":0,\"s\":\"ab\",\"u\":0}", "{\"n\":0}"}) {
      try (var unexpected = plan.decode(bad.getBytes(StandardCharsets.UTF_8))) {
        throw new AssertionError("Expected contract rejection");
      } catch (IllegalArgumentException expected) {
      }
    }
    try (var missing = plan.decode("{\"n\":0,\"u\":0}".getBytes(StandardCharsets.UTF_8))) {
      require(!missing.isPresent(1));
      require(data.read(missing, 1) == Missing.INSTANCE);
      require(!data.isPresent(missing, 1));
      require(missing.unsignedValue(2).signum() == 0);
    }
    try (var nullable =
        plan.decode("{\"n\":0,\"s\":null,\"u\":1}".getBytes(StandardCharsets.UTF_8))) {
      require(nullable.isPresent(1) && nullable.isNull(1));
      require(data.read(nullable, 1) == DataNull.INSTANCE);
      require(data.isPresent(nullable, 1));
    }
    plan.close();
    require(record.longValue(0) == 127);
    require(data.getSchema(record) == schema);
    require(data.readLong(record, 0) == 127);
    require(InteropLibrary.getUncached().readMember(record, "s").equals("😀"));
    require(data.read(record, 2) instanceof ExactNumber);
    try {
      new DataArray(new Object[] {record});
      throw new AssertionError("Scoped JSON record escaped into owned storage");
    } catch (ContractViolation expected) {
      /* explicit ownership */
    }
    try {
      data.readLong(record, 1);
      throw new AssertionError("Wrong shared primitive type");
    } catch (UnexpectedResultException expected) {
      require(expected.getResult().equals("😀"));
    }
    require(record.stringValue(1).equals("😀"));
    require(record.unsignedValue(2).toString().equals("18446744073709551615"));
    var rejected = new java.util.concurrent.atomic.AtomicInteger();
    Thread other =
        new Thread(
            () -> {
              try {
                record.longValue(0);
              } catch (IllegalStateException expected) {
                rejected.incrementAndGet();
              }
              try {
                record.close();
              } catch (IllegalStateException expected) {
                rejected.incrementAndGet();
              }
            });
    other.start();
    other.join();
    require(rejected.get() == 2);
    record.close();
    record.close();
    try {
      record.longValue(0);
      throw new AssertionError("Closed read");
    } catch (IllegalStateException expected) {
    }
    try {
      plan.decode(new byte[] {'0'});
      throw new AssertionError("Closed plan");
    } catch (IllegalStateException expected) {
    }
  }

  private static void accessorContracts() throws Exception {
    var data = DataLibrary.factory().getUncached();
    var schema =
        dev.elide.truffledata.Schema.of(
            new dev.elide.truffledata.Field(
                "n", dev.elide.truffledata.Field.Kind.INT64, true, true));
    var access = new dev.elide.truffledata.json.JsonAccess(schema, 0);
    var wrong =
        new dev.elide.truffledata.json.JsonAccess(
            dev.elide.truffledata.Schema.of(dev.elide.truffledata.Field.requiredLong("n")), 0);
    try (var plan = dev.elide.truffledata.json.JsonPlan.compile(schema)) {
      for (String input : new String[] {"{}", "{\"n\":null}"}) {
        try (var record = plan.decode(input.getBytes(StandardCharsets.UTF_8))) {
          require(
              data.read(record, 0) == (input.equals("{}") ? Missing.INSTANCE : DataNull.INSTANCE));
          try {
            data.readLong(record, 0);
            throw new AssertionError("Missing/null shared primitive");
          } catch (UnexpectedResultException expected) {
            /* null and absence preserved */
          }
          try {
            access.longValue(record);
            throw new AssertionError("Missing/null accessor");
          } catch (IllegalStateException expected) {
          }
        }
      }
      try (var record = plan.decode("{\"n\":7}".getBytes(StandardCharsets.UTF_8))) {
        require(access.longValue(record) == 7);
        require(data.readLong(record, 0) == 7);
        try {
          wrong.longValue(record);
          throw new AssertionError("Wrong schema accessor");
        } catch (IllegalArgumentException expected) {
        }
        try {
          access.doubleValue(record);
          throw new AssertionError("Wrong accessor kind");
        } catch (IllegalArgumentException expected) {
        }
        var rejected = new java.util.concurrent.atomic.AtomicInteger();
        var thread =
            new Thread(
                () -> {
                  try {
                    data.read(record, 0);
                  } catch (IllegalStateException expected) {
                    rejected.incrementAndGet();
                  }
                });
        thread.start();
        thread.join();
        require(rejected.get() == 1);
        record.close();
        try {
          data.read(record, 0);
          throw new AssertionError("Closed shared accessor");
        } catch (IllegalStateException expected) {
        }
      }
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

  public static void main(String[] args) throws Exception {
    typed();
    schemaTyped();
    accessorContracts();
    CompiledReads.run();
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
