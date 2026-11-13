package dev.elide.truffledata.bebop;

import dev.elide.truffledata.bebop.BebopType.Scalar;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * An encoding profile. {@link #GENERAL} is the indexed wire format with every type. A strict
 * profile admits an explicit primitive subset and accepts exactly one encoding per value; its
 * choices are inputs, never defaults, and it is identified as experimental by name.
 */
public final class BebopProfile {
  /** The upstream wire revision this module implements: the tag and its commit. */
  public static final String WIRE_REVISION =
      "bebop-2026.0.0-alpha.6@f73e40a1b118aff6d29d91a00ed3fdb7b4fe5e8c";

  public static final BebopProfile GENERAL =
      new BebopProfile(false, "general;wire=" + WIRE_REVISION, EnumSet.allOf(Scalar.class), 0, 0, 0, 0L);

  private final boolean strict;
  private final String identity;
  private final Set<Scalar> admitted;
  final int nan16;
  final int nanBrain16;
  final int nan32;
  final long nan64;

  private BebopProfile(
      boolean strict,
      String identity,
      Set<Scalar> admitted,
      int nan16,
      int nanBrain16,
      int nan32,
      long nan64) {
    this.strict = strict;
    this.identity = identity;
    this.admitted = admitted;
    this.nan16 = nan16;
    this.nanBrain16 = nanBrain16;
    this.nan32 = nan32;
    this.nan64 = nan64;
  }

  /**
   * Starts an experimental strict profile with choices of the caller's own. Its identity is marked
   * {@code custom}, so it can never be mistaken for a named rules revision.
   */
  public static Builder strict(String experimentalName) {
    return new Builder(experimentalName);
  }

  /**
   * The strict profile of rules revision 0.11, under the identifier the caller's project gives
   * that revision. Every choice is fixed: all primitive types are admitted; the canonical NaNs are
   * {@code 0x7E00} (float16), {@code 0x7FC0} (bfloat16), {@code 0x7FC00000} (float32) and {@code
   * 0x7FF8000000000000} (float64); and acceptance and equality are as documented for the strict
   * profile. Still experimental: the identity says so, and nothing here is frozen.
   */
  public static BebopProfile rules011(String revision) {
    Builder b = new Builder(revision);
    b.rules = true;
    return b.admitAll()
        .canonicalNaN16(0x7E00)
        .canonicalNaNBrain16(0x7FC0)
        .canonicalNaN32(0x7FC00000)
        .canonicalNaN64(0x7FF8000000000000L)
        .build();
  }

  public boolean strict() {
    return strict;
  }

  public boolean admits(Scalar kind) {
    return admitted.contains(kind);
  }

  /** Names the profile and every choice it makes. Two profiles interoperate only if equal here. */
  public String identity() {
    return identity;
  }

  @Override
  public String toString() {
    return identity;
  }

  /** Builder of a strict profile. */
  public static final class Builder {
    private final String name;
    private final EnumSet<Scalar> admitted = EnumSet.noneOf(Scalar.class);
    private boolean rules;
    private boolean hasNan16;
    private boolean hasNanBrain16;
    private int nan16;
    private int nanBrain16;
    private boolean hasNan32;
    private boolean hasNan64;
    private int nan32;
    private long nan64;

    private Builder(String name) {
      if (Objects.requireNonNull(name).isEmpty() || name.indexOf(';') >= 0)
        throw new IllegalArgumentException("Profile name");
      this.name = name;
    }

    /**
     * Admits primitive types. Every type has a canonical rule; each floating-point type also needs
     * its canonical NaN before {@link #build}.
     */
    public Builder admit(Scalar... kinds) {
      admitted.addAll(java.util.List.of(kinds));
      return this;
    }

    /** Admits every primitive type. */
    public Builder admitAll() {
      admitted.addAll(EnumSet.allOf(Scalar.class));
      return this;
    }

    /** The one accepted float16 NaN bit pattern (IEEE 754 binary16). */
    public Builder canonicalNaN16(int bits) {
      if ((bits & ~0xFFFF) != 0 || (bits & 0x7C00) != 0x7C00 || (bits & 0x3FF) == 0)
        throw new IllegalArgumentException("Not a NaN pattern");
      nan16 = bits;
      hasNan16 = true;
      return this;
    }

    /** The one accepted bfloat16 NaN bit pattern (the high half of a binary32). */
    public Builder canonicalNaNBrain16(int bits) {
      if ((bits & ~0xFFFF) != 0 || (bits & 0x7F80) != 0x7F80 || (bits & 0x7F) == 0)
        throw new IllegalArgumentException("Not a NaN pattern");
      nanBrain16 = bits;
      hasNanBrain16 = true;
      return this;
    }

    /** The one accepted float32 NaN bit pattern. */
    public Builder canonicalNaN32(int bits) {
      if (!Float.isNaN(Float.intBitsToFloat(bits)))
        throw new IllegalArgumentException("Not a NaN pattern");
      nan32 = bits;
      hasNan32 = true;
      return this;
    }

    /** The one accepted float64 NaN bit pattern. */
    public Builder canonicalNaN64(long bits) {
      if (!Double.isNaN(Double.longBitsToDouble(bits)))
        throw new IllegalArgumentException("Not a NaN pattern");
      nan64 = bits;
      hasNan64 = true;
      return this;
    }

    public BebopProfile build() {
      require(Scalar.FLOAT16, hasNan16);
      require(Scalar.BFLOAT16, hasNanBrain16);
      require(Scalar.FLOAT32, hasNan32);
      require(Scalar.FLOAT64, hasNan64);
      StringBuilder id =
          new StringBuilder("strict-experimental;")
              .append(rules ? "rules=" : "custom=")
              .append(name)
              .append(";wire=")
              .append(WIRE_REVISION)
              .append(";admit=");
      for (Scalar kind : admitted) id.append(kind.name().toLowerCase(Locale.ROOT)).append(',');
      if (admitted.contains(Scalar.FLOAT16)) id.append(";nan16=").append(Integer.toHexString(nan16));
      if (admitted.contains(Scalar.BFLOAT16))
        id.append(";nanbrain16=").append(Integer.toHexString(nanBrain16));
      if (admitted.contains(Scalar.FLOAT32)) id.append(";nan32=").append(Integer.toHexString(nan32));
      if (admitted.contains(Scalar.FLOAT64)) id.append(";nan64=").append(Long.toHexString(nan64));
      return new BebopProfile(
          true, id.toString(), admitted.clone(), nan16, nanBrain16, nan32, nan64);
    }

    private void require(Scalar kind, boolean hasNan) {
      if (admitted.contains(kind) && !hasNan)
        throw BebopException.unsupported(kind + " is admitted without a canonical NaN");
    }
  }
}
