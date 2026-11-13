package dev.elide.truffledata.bebop;

import static dev.elide.truffledata.bebop.BebopException.malformed;

/**
 * The trailing index of a message: field boundaries, a tag directory and a control byte. Parsing
 * validates the whole index and accepts only its canonical form (smallest directory, minimal
 * boundary width), which is also the only form {@link #write} produces.
 */
final class MessageIndex {
  private static final int[] NO_TAGS = new int[0];

  private final byte[] buf;
  /** Present tags, ascending. */
  final int[] tags;

  final int count;
  private final int payloadStart;
  private final int payloadSize;
  private final int boundaries;
  private final int width;

  private MessageIndex(
      byte[] buf, int[] tags, int payloadStart, int payloadSize, int boundaries, int width) {
    this.buf = buf;
    this.tags = tags;
    this.count = tags.length;
    this.payloadStart = payloadStart;
    this.payloadSize = payloadSize;
    this.boundaries = boundaries;
    this.width = width;
  }

  /** Absolute start of the field at {@code rank}. */
  int start(int rank) {
    return payloadStart + (rank == 0 ? 0 : boundary(rank - 1));
  }

  /** Absolute end of the field at {@code rank}. */
  int end(int rank) {
    return payloadStart + (rank == count - 1 ? payloadSize : boundary(rank));
  }

  /** Rank of {@code tag}, or -1 if the field is absent. */
  int rank(int tag) {
    for (int i = 0; i < count; i++) {
      if (tags[i] == tag) return i;
      if (tags[i] > tag) break;
    }
    return -1;
  }

  private int boundary(int i) {
    return (int) load(buf, boundaries + i * width, width);
  }

  private static long load(byte[] buf, int at, int width) {
    return switch (width) {
      case 1 -> buf[at] & 0xFFL;
      case 2 -> (short) Writer.I16.get(buf, at) & 0xFFFFL;
      default -> (int) Writer.I32.get(buf, at) & 0xFFFFFFFFL;
    };
  }

  /** Parses the index of the message content {@code [start, end)}, {@code end > start}. */
  static MessageIndex parse(byte[] buf, int start, int end) {
    int control = buf[end - 1] & 0xFF;
    if ((control >>> 5) != 0) throw malformed("Reserved control bits are set");
    int code = control & 3;
    if (code == 3) throw malformed("Invalid boundary width");
    int width = 1 << code;
    int kind = (control >>> 2) & 7;
    int dirEnd = end - 1;
    int avail = dirEnd - start;
    int[] tags;
    int dirBytes;
    switch (kind) {
      case 0 -> {
        tags = NO_TAGS;
        dirBytes = 0;
      }
      case 1, 2, 3 -> {
        dirBytes = kind;
        if (avail < dirBytes) throw malformed("Truncated directory");
        tags = new int[kind];
        int prev = 0;
        for (int i = 0; i < kind; i++) {
          int tag = buf[dirEnd - kind + i] & 0xFF;
          if (tag <= prev) throw malformed("Directory tags are not ascending");
          tags[i] = prev = tag;
        }
      }
      case 4, 5, 6 -> {
        dirBytes = 1 << (kind - 4);
        if (avail < dirBytes) throw malformed("Truncated directory");
        long mask = load(buf, dirEnd - dirBytes, dirBytes);
        tags = new int[Long.bitCount(mask)];
        fill(tags, 0, (int) mask, 0);
      }
      default -> {
        if (avail < 1) throw malformed("Truncated directory");
        int blocks = buf[dirEnd - 1] & 0xFF;
        dirBytes = 1 + 5 * Integer.bitCount(blocks);
        if (avail < dirBytes) throw malformed("Truncated directory");
        int at = dirEnd - dirBytes;
        int n = 0;
        for (int b = 0, p = at; b < 8; b++) {
          if ((blocks & (1 << b)) == 0) continue;
          int mask = (int) Writer.I32.get(buf, p + 1);
          if ((buf[p] & 0xFF) != n) throw malformed("Directory rank is wrong");
          if (mask == 0) throw malformed("Directory block is empty");
          if (b == 7 && mask < 0) throw malformed("Tag 256 does not exist");
          n += Integer.bitCount(mask);
          p += 5;
        }
        tags = new int[n];
        for (int b = 0, p = at, k = 0; b < 8; b++) {
          if ((blocks & (1 << b)) == 0) continue;
          k = fill(tags, k, (int) Writer.I32.get(buf, p + 1), b * 32);
          p += 5;
        }
      }
    }
    int n = tags.length;
    if (kind != canonicalKind(n, n == 0 ? 0 : tags[n - 1]))
      throw malformed("Directory is not the canonical choice");
    long payload = (long) avail - dirBytes - (long) Math.max(n - 1, 0) * width;
    if (payload < 0) throw malformed("Index exceeds the message");
    if (n == 0 && payload != 0) throw malformed("Payload without a directory");
    if (code != widthCode(payload)) throw malformed("Boundary width is not minimal");
    int boundaries = start + (int) payload;
    long prev = 0;
    for (int i = 0; i < n - 1; i++) {
      long b = load(buf, boundaries + i * width, width);
      if (b < prev || b > payload) throw malformed("Field boundary is out of order or range");
      prev = b;
    }
    return new MessageIndex(buf, tags, start, (int) payload, boundaries, width);
  }

  private static int fill(int[] tags, int k, int mask, int base) {
    while (mask != 0) {
      tags[k++] = base + Integer.numberOfTrailingZeros(mask) + 1;
      mask &= mask - 1;
    }
    return k;
  }

  /**
   * The canonical directory kind for {@code n} fields: the smallest mask the highest tag fits,
   * blocks above tag 32, and a tiny directory when at most three fields make it strictly smaller.
   */
  static int canonicalKind(int n, int maxTag) {
    if (n == 0) return 0;
    int kind = maxTag <= 8 ? 4 : maxTag <= 16 ? 5 : maxTag <= 32 ? 6 : 7;
    int size = kind == 7 ? 6 : 1 << (kind - 4);
    return n <= 3 && n < size ? n : kind;
  }

  private static int widthCode(long payload) {
    return payload <= 0xFF ? 0 : payload <= 0xFFFF ? 1 : 2;
  }

  /**
   * Appends the index of a payload that began at {@code payloadStart}: {@code tags} ascending,
   * {@code starts} relative to the payload.
   */
  static void write(Writer w, int payloadStart, int[] tags, int[] starts, int n) {
    int payload = w.pos - payloadStart;
    int code = widthCode(payload);
    for (int i = 1; i < n; i++) {
      switch (code) {
        case 0 -> w.u8(starts[i]);
        case 1 -> w.u16(starts[i]);
        default -> w.u32(starts[i]);
      }
    }
    int kind = canonicalKind(n, n == 0 ? 0 : tags[n - 1]);
    switch (kind) {
      case 0 -> {}
      case 1, 2, 3 -> {
        for (int i = 0; i < n; i++) w.u8(tags[i]);
      }
      case 4, 5, 6 -> {
        int mask = 0;
        for (int i = 0; i < n; i++) mask |= 1 << (tags[i] - 1);
        switch (kind) {
          case 4 -> w.u8(mask);
          case 5 -> w.u16(mask);
          default -> w.u32(mask);
        }
      }
      default -> {
        int blocks = 0;
        for (int i = 0; i < n; ) {
          int block = (tags[i] - 1) >>> 5;
          int rank = i;
          int mask = 0;
          for (; i < n && (tags[i] - 1) >>> 5 == block; i++) mask |= 1 << ((tags[i] - 1) & 31);
          w.u8(rank);
          w.u32(mask);
          blocks |= 1 << block;
        }
        w.u8(blocks);
      }
    }
    w.u8(kind << 2 | code);
  }
}
