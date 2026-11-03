package dev.elide.truffledata;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.*;
import java.math.*;

/** Lossless representation for SQL DECIMAL and unsigned 64-bit Protobuf integers. */
@ExportLibrary(InteropLibrary.class)
public final class ExactNumber implements TruffleObject {
  private final BigDecimal value;

  public ExactNumber(BigDecimal value) {
    this.value = value;
  }

  public BigDecimal decimal() {
    return value;
  }

  @ExportMessage
  boolean isNumber() {
    return true;
  }

  @ExportMessage
  @TruffleBoundary
  boolean fitsInBigInteger() {
    return value.stripTrailingZeros().scale() <= 0;
  }

  @TruffleBoundary
  private boolean fits(long min, long max) {
    return fitsInBigInteger()
        && value.compareTo(BigDecimal.valueOf(min)) >= 0
        && value.compareTo(BigDecimal.valueOf(max)) <= 0;
  }

  @ExportMessage
  boolean fitsInByte() {
    return fits(Byte.MIN_VALUE, Byte.MAX_VALUE);
  }

  @ExportMessage
  boolean fitsInShort() {
    return fits(Short.MIN_VALUE, Short.MAX_VALUE);
  }

  @ExportMessage
  boolean fitsInInt() {
    return fits(Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  @ExportMessage
  boolean fitsInLong() {
    return fits(Long.MIN_VALUE, Long.MAX_VALUE);
  }

  @ExportMessage
  @TruffleBoundary
  boolean fitsInDouble() {
    double n = value.doubleValue();
    return Double.isFinite(n) && new BigDecimal(n).compareTo(value) == 0;
  }

  @ExportMessage
  @TruffleBoundary
  boolean fitsInFloat() {
    float n = value.floatValue();
    return Float.isFinite(n) && new BigDecimal((double) n).compareTo(value) == 0;
  }

  @ExportMessage
  byte asByte() throws UnsupportedMessageException {
    if (!fitsInByte()) throw UnsupportedMessageException.create();
    return value.byteValue();
  }

  @ExportMessage
  short asShort() throws UnsupportedMessageException {
    if (!fitsInShort()) throw UnsupportedMessageException.create();
    return value.shortValue();
  }

  @ExportMessage
  int asInt() throws UnsupportedMessageException {
    if (!fitsInInt()) throw UnsupportedMessageException.create();
    return value.intValue();
  }

  @ExportMessage
  long asLong() throws UnsupportedMessageException {
    if (!fitsInLong()) throw UnsupportedMessageException.create();
    return value.longValue();
  }

  @ExportMessage
  float asFloat() throws UnsupportedMessageException {
    if (!fitsInFloat()) throw UnsupportedMessageException.create();
    return value.floatValue();
  }

  @ExportMessage
  double asDouble() throws UnsupportedMessageException {
    if (!fitsInDouble()) throw UnsupportedMessageException.create();
    return value.doubleValue();
  }

  @ExportMessage
  @TruffleBoundary
  BigInteger asBigInteger() throws UnsupportedMessageException {
    if (!fitsInBigInteger()) throw UnsupportedMessageException.create();
    return value.toBigIntegerExact();
  }

  @ExportMessage
  @TruffleBoundary
  Object toDisplayString(boolean allowSideEffects) {
    return value.toPlainString();
  }
}
