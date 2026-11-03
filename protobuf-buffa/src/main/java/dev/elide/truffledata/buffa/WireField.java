package dev.elide.truffledata.buffa;

import com.oracle.truffle.api.library.*;
import dev.elide.truffledata.*;

/** A wire row may carry a lease-backed binary value; snapshots use ordinary DataArray bytes. */
@ExportLibrary(DataLibrary.class)
final class WireField extends AbstractRow {
  private final WireMessage message;
  private final Object number;
  private final Object wire;
  private final Object value;

  WireField(WireMessage message, long number, long wire, Object value) {
    super(BuffaWire.SCHEMA);
    this.message = message;
    this.number = number;
    this.wire = wire;
    this.value = value;
  }

  @ExportMessage
  Schema getSchema() {
    return descriptor;
  }

  @ExportMessage
  Object read(int index) {
    message.check();
    return switch (index) {
      case 0 -> number;
      case 1 -> wire;
      case 2 -> value;
      default -> throw new IndexOutOfBoundsException(index);
    };
  }
}
