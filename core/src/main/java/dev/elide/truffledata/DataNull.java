package dev.elide.truffledata;

import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.*;

/** Explicit data null; Java null never crosses interop. */
@ExportLibrary(InteropLibrary.class)
public final class DataNull implements TruffleObject {
  public static final DataNull INSTANCE = new DataNull();

  private DataNull() {}

  @ExportMessage
  boolean isNull() {
    return true;
  }

  @Override
  public String toString() {
    return "null";
  }
}
