package dev.elide.truffledata.yaml;

import static dev.elide.truffledata.tape.TapeReader.*;

import dev.elide.truffledata.tape.SvmTape;
import dev.elide.truffledata.tape.TapeReader;
import dev.elide.truffledata.yaml.generated.Yaml;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/** Native YAML lifetime, metadata and compact storage in one retained owner. */
class SvmYamlMessage extends YamlMessage implements TapeReader {
  private final Pointer words, pool, input, directory, meta;
  private final long count, poolBytes, inputBytes, directoryCount, resultAddress;
  private final int flags;

  private SvmYamlMessage(Pointer d, long resultAddress, YamlMetadata metadata) {
    super(metadata);
    words = d.readWord(8);
    pool = d.readWord(24);
    input = d.readWord(40);
    directory = d.readWord(56);
    meta = d.readWord(72);
    count = d.readLong(16);
    poolBytes = d.readLong(32);
    inputBytes = d.readLong(48);
    directoryCount = d.readLong(64);
    flags = d.readInt(4);
    this.resultAddress = resultAddress;
  }

  // Validate before reading/copying metadata or exposing the owner's unchecked native loads.
  static long validate(Pointer d) {
    if (d.isNull() || (d.rawValue() & 7) != 0 || d.readInt(0) != 1 || (d.readInt(4) & ~15) != 0)
      throw new IllegalArgumentException("Invalid YAML compact descriptor");
    long n = d.readLong(16), p = d.readLong(32), i = d.readLong(48), e = d.readLong(64), m = d.readLong(80);
    Pointer w = d.readWord(8), pb = d.readWord(24), ib = d.readWord(40), dir = d.readWord(56), mt = d.readWord(72);
    if (n <= 0 || n > 0xffffffffL || p < 0 || p > 0xffffffffL || i < 0 || i > 0xffffffffL
        || e < 0 || e > 0xffffffffL || w.isNull() || (w.rawValue() & 7) != 0
        || (p > 0 && pb.isNull()) || (i > 0 && ib.isNull())
        || (e > 0 && (dir.isNull() || (dir.rawValue() & 7) != 0))
        || (((d.readInt(4) & 2) != 0) ? m != n || mt.isNull() : m != 0))
      throw new IllegalArgumentException("Invalid YAML compact storage");
    return n;
  }

  static YamlMessage create(Pointer d, long resultAddress, YamlMetadata metadata) {
    return hasAliases(metadata) ? new SvmYamlMessage(d, resultAddress, metadata)
        : new Plain(d, resultAddress, metadata);
  }

  private static final class Plain extends SvmYamlMessage {
    Plain(Pointer d, long resultAddress, YamlMetadata metadata) { super(d, resultAddress, metadata); }
    @Override protected boolean aliases() { return false; }
  }

  @Override protected final TapeReader reader() { return this; }
  @Override protected final long lexical(long node) { return span(node); }
  @Override protected final byte lexicalByte(long offset) { return textByte(false, offset); }
  @Override protected final void release() {
    VoidPointer result = WordFactory.pointer(resultAddress);
    Yaml.release(result);
    UnmanagedMemory.free(result);
  }
  @Override public long nodeCount() { return count; }
  @Override public long poolSize() { return poolBytes; }
  @Override public long inputSize() { return inputBytes; }
  @Override public long directorySize() { return directoryCount; }
  @Override public int flags() { return flags; }
  @Override public long readWord(long index) { return words.readLong(WordFactory.signed(index * 8)); }
  @Override public byte textByte(boolean owned, long offset) {
    if (offset < 0 || offset >= (owned ? poolBytes : inputBytes)) throw new IndexOutOfBoundsException("Tape text");
    return (owned ? pool : input).readByte(WordFactory.signed(offset));
  }
  @Override public long textWord(boolean owned, long offset, int length) {
    if (length == 0) return 0;
    long size = owned ? poolBytes : inputBytes;
    Pointer data = owned ? pool : input;
    if (!owned || offset <= size - 8) {
      long value = data.readLong(WordFactory.signed(offset));
      return length == 8 ? value : value & (-1L >>> (64 - length * 8));
    }
    long value = 0;
    for (int j = 0; j < length; j++) value |= (data.readByte(WordFactory.signed(offset + j)) & 255L) << (j * 8);
    return value;
  }
  @Override public long directoryEntry(long index) {
    return directory.readLong(WordFactory.signed(index * 8));
  }
  @Override public int metadata(long index) {
    if (index < 0 || index >= count) throw new IndexOutOfBoundsException("Tape metadata");
    return (flags & 2) == 0 ? 0 : meta.readInt(WordFactory.signed(index * 4));
  }
  @Override public String stringValue(long node) {
    long w = word(node);
    int length = field(w);
    if (kind(w) != 7 || length < 256 || (w & ASCII) != 0) return TapeReader.super.stringValue(node);
    return SvmTape.decodeUtf8(((w & OWNED) == 0 ? input : pool).add(WordFactory.signed(low(w))), length);
  }
}
