package dev.elide.truffledata.bebop;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A union value. Under the general profile a branch the schema does not know decodes to its raw
 * content as a {@code byte[]}.
 */
public record BebopUnion(int discriminator, Object value) {
  public BebopUnion {
    Objects.requireNonNull(value);
    if (discriminator < 0 || discriminator > 255)
      throw new IllegalArgumentException("Discriminator");
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof BebopUnion that
        && discriminator == that.discriminator
        && BebopValues.equal(value, that.value);
  }

  @Override
  public int hashCode() {
    return discriminator;
  }
}
