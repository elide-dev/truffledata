package dev.elide.truffledata.bebop;

import java.util.Objects;

/**
 * A validator of encoded bytes for a codec. {@link #JAVA} is the reference; any other validator
 * accepts exactly the same inputs and reports the same failure, and is used only when asked for.
 */
public abstract class BebopValidator {
  /** The codec's own validator: pure Java, on every runtime. */
  public static final BebopValidator JAVA =
      new BebopValidator() {
        @Override
        public String name() {
          return "java";
        }

        @Override
        public void validate(BebopCodec codec, byte[] bytes, int offset, int length) {
          codec.validate(bytes, offset, length);
        }
      };

  /** Failure reasons by code, shared with native validators. Code 0 is success. */
  private static final String[] REASONS = {
    "",
    "Truncated value",
    "Length is out of range",
    "Count exceeds the remaining bytes",
    "Collection exceeds the count limit",
    "Work limit reached",
    "Allocation limit reached",
    "Nesting exceeds the depth limit",
    "String exceeds the remaining bytes",
    "String terminator is not NUL",
    "String is not valid UTF-8",
    "Boolean is neither 00 nor 01",
    "NaN is not the canonical pattern",
    "Timestamp is outside the canonical range",
    "Duration is not in canonical form",
    "Not a member of the enum",
    "Set elements are not distinct and ascending",
    "Map keys are not distinct and ascending",
    "Duplicate map key",
    "Unknown field tag",
    "Field does not fill its indexed range",
    "Unknown union discriminator",
    "Branch does not fill the union",
    "Trailing bytes",
    "Input exceeds the byte limit",
    "Reserved control bits are set",
    "Invalid boundary width",
    "Truncated directory",
    "Directory tags are not ascending",
    "Directory rank is wrong",
    "Directory block is empty",
    "Tag 256 does not exist",
    "Directory is not the canonical choice",
    "Index exceeds the message",
    "Payload without a directory",
    "Boundary width is not minimal",
    "Field boundary is out of order or range",
    "Timestamp has an offset under a UTC-instant schema",
  };

  protected BebopValidator() {}

  /** Identifies the implementation, for diagnostics and measurements. */
  public abstract String name();

  /**
   * Checks exactly {@code [offset, offset + length)}, throwing what {@link
   * BebopCodec#validate(byte[], int, int)} would.
   */
  public abstract void validate(BebopCodec codec, byte[] bytes, int offset, int length);

  /** The codec's schema program. Shared and immutable: implementations must only read it. */
  protected static byte[] program(BebopCodec codec) {
    return codec.program();
  }

  /** Turns a native validator's status into the failure it stands for; 0 is success. */
  protected static void report(long status) {
    if (status == 0) return;
    if (status < 0 || status >= REASONS.length)
      throw new IllegalStateException("Native validator rejected its schema program");
    String reason = REASONS[(int) status];
    throw switch ((int) status) {
      case 4, 5, 6, 7, 24 -> BebopException.limit(reason);
      default -> BebopException.malformed(reason);
    };
  }

  @Override
  public String toString() {
    return Objects.requireNonNull(name());
  }
}
