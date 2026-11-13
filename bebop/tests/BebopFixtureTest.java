package dev.elide.truffledata.bebop;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.bebop.BebopException.Kind;
import dev.elide.truffledata.bebop.FixtureFiles.Case;
import dev.elide.truffledata.bebop.FixtureFiles.File;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Every case of the shared fixture files, checked against the independent abstract reference. */
final class BebopFixtureTest {
  private static Kind failure(Runnable action, String label) {
    return assertThrows(BebopException.class, action::run, label).kind();
  }

  private static Kind kind(String error) {
    return Kind.valueOf(error.toUpperCase(Locale.ROOT));
  }

  @Test
  void everyCase() {
    int ran = 0;
    for (String name : FixtureFiles.NAMES) {
      File file = FixtureFiles.load(name);
      boolean strict = file.profile().strict();
      for (Case c : file.cases()) {
        String label = name + ": " + c.name();
        BebopType root = Objects.requireNonNull(c.root());
        ran++;
        if (c.kind().equals("duration-adapter")) {
          long seconds;
          long nanos;
          if (c.host() instanceof BebopDuration d) {
            seconds = d.seconds();
            nanos = d.nanos();
          } else {
            List<?> pair = (List<?>) Objects.requireNonNull(c.host());
            seconds = (Long) pair.get(0);
            nanos = (Long) pair.get(1);
          }
          if (c.error() != null) {
            assertEquals(kind(c.error()), failure(() -> BebopDuration.normalized(seconds, nanos), label), label);
          } else {
            BebopDuration normalized = BebopDuration.normalized(seconds, nanos);
            assertEquals(c.value(), normalized, label);
            // The adapter's output is always what the strict encoder accepts.
            BebopCodec duration = BebopCodec.compile(file.schema(), BebopType.Scalar.DURATION, file.profile(), BebopLimits.DEFAULT);
            assertEquals(normalized, duration.decode(duration.encode(normalized)), label);
          }
          continue;
        }
        BebopCodec codec =
            BebopCodec.compile(file.schema(), root, file.profile(), c.limits() == null ? BebopLimits.DEFAULT : c.limits());
        switch (c.kind()) {
          case "wire-accept" -> {
            byte[] bytes = Objects.requireNonNull(c.bytes());
            Object expected = Abstract.of(file.schema(), root, Objects.requireNonNull(c.value()), strict);
            Object decoded = codec.decode(bytes);
            assertEquals(expected, Abstract.of(file.schema(), root, decoded, strict), label);
            codec.validate(bytes);
            assertEquals(expected, Abstract.of(file.schema(), root, codec.decodeReference(bytes), strict), label);
            if (c.canonical()) {
              assertEquals(Fixtures.hex(bytes), Fixtures.hex(codec.encode(c.value())), label);
              assertEquals(Fixtures.hex(bytes), Fixtures.hex(codec.encode(decoded)), label);
            } else {
              assertFalse(strict, label + ": a strict profile has no second encoding");
              assertNotEquals(Fixtures.hex(bytes), Fixtures.hex(codec.encode(decoded)), label);
            }
            // The same bytes read in place.
            assertEquals(codec.schemaIdentity(), codec.adopt(bytes.clone()).schemaIdentity(), label);
          }
          case "wire-reject" -> {
            byte[] bytes = Objects.requireNonNull(c.bytes());
            Kind expected = kind(Objects.requireNonNull(c.error()));
            assertEquals(expected, failure(() -> codec.decode(bytes), label), label);
            assertEquals(expected, failure(() -> codec.validate(bytes), label), label);
            assertEquals(expected, failure(() -> codec.decodeReference(bytes), label), label);
            assertEquals(expected, failure(() -> codec.open(bytes), label), label);
          }
          case "host-normalize" -> {
            byte[] bytes = Objects.requireNonNull(c.bytes());
            assertEquals(Fixtures.hex(bytes), Fixtures.hex(codec.encode(Objects.requireNonNull(c.host()))), label);
            assertEquals(
                Abstract.of(file.schema(), root, Objects.requireNonNull(c.value()), strict),
                Abstract.of(file.schema(), root, codec.decode(bytes), strict),
                label);
            // Normalization is the encoder's: the abstract value of the input is already the result's.
            assertEquals(
                Abstract.of(file.schema(), root, c.host(), strict),
                Abstract.of(file.schema(), root, codec.decode(bytes), strict),
                label);
          }
          case "host-reject" ->
              assertEquals(
                  kind(Objects.requireNonNull(c.error())),
                  failure(() -> codec.encode(Objects.requireNonNull(c.host())), label),
                  label);
          case "resource" -> {
            byte[] bytes = Objects.requireNonNull(c.bytes());
            Runnable operation =
                Objects.requireNonNull(c.operation()).equals("decode") ? () -> codec.decode(bytes) : () -> codec.validate(bytes);
            if (Objects.requireNonNull(c.error()).equals("none")) operation.run();
            else assertEquals(kind(c.error()), failure(operation, label), label);
          }
          default -> fail(label + ": unknown kind " + c.kind());
        }
      }
    }
    assertTrue(ran >= 185, ran + " cases");
  }
}
