package dev.elide.truffledata.tape;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * A 0 raw span is the compact-tape ABI's documented "producer has no source token" state
 * ({@code tape/native/ctape.h}: "0 when the producer has none"): the YAML decoder publishes it by
 * default for quoted and block scalars ({@code YamlOptions.DEFAULT} has {@code retainSource=false},
 * and {@code JavaDecoder.lexical} returns 0 for every quoted style), and the public {@link
 * TapeBuilder#decimalValue(byte[], long, int)} (raw=0) reaches it too. The number interop surface
 * ({@link TapeValue#numberText()} and every {@code fitsIn*}/{@code as*} message) must answer from
 * the pooled digits / compact value rather than throw an unchecked {@link IllegalStateException}
 * out of the {@link InteropLibrary} boundary.
 *
 * <p>These tests build the tapes by hand (no native library) through the published {@link
 * TapeBuilder} over a YAML-format owner that takes its lexical span from the tape itself, so the
 * {@code hasAliases()} branch of {@link TapeOwner#numberText(long)} is the one exercised. The
 * end-to-end behavior across both YAML kernels is covered by
 * {@code yaml/tests/YamlDecimalZeroSpanTest.java}.
 */
class TapeBuilderDecimalRawSpanTest {
  private static final String BIG = "123456789012345678901234567890123"; // 33 integral digits, > uint64
  private static final BigInteger BIG_INT = new BigInteger(BIG);

  /** A confined YAML-format owner whose lexical span is the tape's own raw span. */
  private static final class YamlOwner extends TapeOwner {
    private final TapeFormat format;

    YamlOwner(TapeReader tape, TapeFormat format) {
      super(tape, Thread.currentThread());
      this.format = checkFormat(tape, format);
    }

    @Override public TapeFormat format() { return format; }
    @Override public Assumption assumption() { return Assumption.ALWAYS_VALID; }
    @Override public long lexical(long node) { return tape.span(node); }
    @Override public byte lexicalByte(long offset) { return tape.inputByte(offset); }
  }

  private static YamlOwner owner(HeapTape tape) {
    int[] meta = new int[(int) tape.nodeCount()];
    return new YamlOwner(tape, TapeFormat.of("YAML").withMetadata(meta));
  }

  /** A one-number YAML tape built with raw=0 (no source token retained). */
  private static TapeValue zeroSpanDecimal(String digits) {
    TapeBuilder b = new TapeBuilder(new byte[0]);
    b.decimalValue(digits.getBytes(StandardCharsets.UTF_8), 0, 0);
    return owner(b.build(0)).root();
  }

  private static TapeValue zeroSpanI64(long value) {
    TapeBuilder b = new TapeBuilder(new byte[0]);
    b.longValue(value, 0, 0);
    return owner(b.build(0)).root();
  }

  /** A YAML DECIMAL with a retained source token (non-zero raw span); the pre-existing happy path. */
  private static TapeValue retainedDecimal(String digits) {
    byte[] token = digits.getBytes(StandardCharsets.UTF_8);
    // HeapTape treats the last 7 input bytes as padding, so leave room for them.
    byte[] input = new byte[token.length + 7];
    System.arraycopy(token, 0, input, 0, token.length);
    TapeBuilder b = new TapeBuilder(input);
    long raw = ((long) 0 << 32) | token.length;
    b.decimalValue(token, raw, 0);
    return owner(b.build(0)).root();
  }

  @Test void decimalZeroSpanAnswersNumberTextAndStringValueFromPooledDigits() {
    TapeValue v = zeroSpanDecimal(BIG);
    assertEquals(TapeValue.Kind.NUMBER, v.kind());
    // numberText() is the documented "available regardless" surface; the pooled digits answer.
    assertEquals(BIG, v.numberText());
    assertEquals(BIG, v.stringValue());
  }

  @Test void decimalZeroSpanFitsAndConvertsAsBigIntegerFromPooledDigits() throws Exception {
    TapeValue v = zeroSpanDecimal(BIG);
    var interop = InteropLibrary.getUncached();
    assertTrue(interop.isNumber(v));
    // fitsIn* must return a boolean, never throw: a 33-digit integral value fits a BigInteger.
    assertTrue(interop.fitsInBigInteger(v));
    assertEquals(BIG_INT, interop.asBigInteger(v));
    // It does not fit any primitive integer; asLong signals "no fit" via UnsupportedMessageException,
    // never an unchecked IllegalStateException through the @ExportMessage boundary.
    assertFalse(interop.fitsInLong(v));
    assertThrows(UnsupportedMessageException.class, () -> interop.asLong(v));
    assertFalse(interop.fitsInInt(v));
    assertThrows(UnsupportedMessageException.class, () -> interop.asInt(v));
    // Not exactly representable as a binary64 either.
    assertFalse(interop.fitsInDouble(v));
    assertThrows(UnsupportedMessageException.class, () -> interop.asDouble(v));
  }

  @Test void i64ZeroSpanAnswersNumberTextAndInteropFromTheCompactValue() throws Exception {
    TapeValue v = zeroSpanI64(42);
    var interop = InteropLibrary.getUncached();
    // A quoted/tagged !!int "42" publishes an I64 with a 0 raw span; numberText() must answer from
    // the compact value, not the (absent) source token. A decimalText-only fallback would throw
    // "Not text" here, so this guards the all-number-kinds branch of numberFallback.
    assertEquals("42", v.numberText());
    assertTrue(interop.fitsInLong(v));
    assertEquals(42L, interop.asLong(v));
    assertTrue(interop.fitsInInt(v));
    assertEquals(42, interop.asInt(v));
    assertTrue(interop.fitsInBigInteger(v));
    assertEquals(BigInteger.valueOf(42L), interop.asBigInteger(v));
  }

  @Test void zeroSpanNumbersHaveNoRawText() {
    // rawText() is the source-spelling surface; a 0 span means there is no source, so it still
    // throws IllegalStateException (the documented "value has no raw text" path), unchanged by the
    // fix to numberText(). This guards against the number path accidentally absorbing rawText too.
    TapeValue decimal = zeroSpanDecimal(BIG);
    TapeValue i64 = zeroSpanI64(42);
    assertThrows(IllegalStateException.class, decimal::rawText);
    assertThrows(IllegalStateException.class, i64::rawText);
  }

  @Test void retainedDecimalSourceSpanKeepsTheExactLexicalSpelling() throws Exception {
    // Control: a DECIMAL with a retained source token takes the existing lexicalText path and is
    // unchanged by the fix; numberText() returns the source spelling and interop answers as before.
    TapeValue v = retainedDecimal(BIG);
    var interop = InteropLibrary.getUncached();
    assertEquals(BIG, v.numberText());
    assertEquals(BIG, v.stringValue());
    assertTrue(interop.fitsInBigInteger(v));
    assertEquals(BIG_INT, interop.asBigInteger(v));
    assertFalse(interop.fitsInLong(v));
    assertThrows(UnsupportedMessageException.class, () -> interop.asLong(v));
  }
}

