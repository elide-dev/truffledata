package dev.elide.truffledata;

import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.*;

/** Internal absence marker. Named/ordinal interop reads of absent fields throw. */
@ExportLibrary(InteropLibrary.class)
public final class Missing implements TruffleObject {
  public static final Missing INSTANCE = new Missing();

  private Missing() {}

  @ExportMessage
  Object toDisplayString(boolean allowSideEffects) {
    return "<missing>";
  }
}
