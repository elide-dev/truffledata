package dev.elide.truffledata.tape;

import dev.elide.myna.values.ValueTape;

/** Adapter for the public Myna interchange API and producers still publishing Myna descriptors. */
final class LegacyTapeReader extends TapeReader {
  final ValueTape value;
  final TapeSource source;
  LegacyTapeReader(ValueTape value) { this.value = value; source = (TapeSource) value; }
  @Override public long nodeCount() { return value.nodeCount(); }
  @Override public long step(long node) { return node + 1; }
  @Override protected long readWord(long index) { throw new UnsupportedOperationException("Myna tape has no compact words"); }
  @Override protected byte textByte(boolean owned, long offset) { throw new UnsupportedOperationException(); }
  @Override protected long textWord(boolean owned, long offset, int length) { throw new UnsupportedOperationException(); }
  @Override public long directoryEntry(long index) { return source.directoryEntry(index); }
  @Override public int metadata(long index) { return 0; }
  @Override public int kindAt(long node) { return value.kindAt(node); }
  @Override public long next(long node) { return value.next(node); }
  @Override public long first(long node) { return value.first(node); }
  @Override public long length(long node) { return value.length(node); }
  @Override public boolean booleanValue(long node) { return value.booleanValue(node); }
  @Override public long signedValue(long node) { return value.signedValue(node); }
  @Override public long unsignedBits(long node) { return value.unsignedBits(node); }
  @Override public double doubleValue(long node) { return value.doubleValue(node); }
  @Override public boolean ascii(long node) { return source.keyMeta(node) < 0; }
  @Override public int hash(long node) { return (source.keyMeta(node) >>> 16) & 0x7fff; }
  @Override public long directory(long node) { return value.version() == ValueTape.ABI_VERSION_V4 ? value.bits(node) : 0; }
  @Override public long directorySize() { return source.directorySize(); }
  @Override public boolean keyMatches(long node, TapeKey key) { return value.keyMatches(node, key.prepared); }
  @Override public long directoryKey(long object, int rank, TapeKey key) {
    return source.directoryKey(object, rank, key.hash, key.header, key.low, key.high, key.length > 8);
  }
  @Override public long resolve(long node) { return node; }
  @Override public long span(long node) { return 0; }
  @Override public byte inputByte(long offset) { throw new UnsupportedOperationException(); }
  @Override public int copyStringUtf8(long node, byte[] output, int offset) { return value.copyStringUtf8(node, output, offset); }
  @Override public String decimalText(long node) { return value.numberText(node); }
  @Override public String stringValue(long node) { return value.stringValue(node); }
  @Override public boolean stringEquals(long node, byte[] bytes) { return value.stringEquals(node, bytes); }
  @Override public long textWord(long node, int offset) {
    if (value.isInlineText(node)) return source.nodeLong(node * ValueTape.NODE_SIZE + 8 + offset);
    byte[] bytes = new byte[Math.toIntExact(value.length(node))];
    value.copyStringUtf8(node, bytes, 0);
    long word = 0;
    for (int i = 0; i < 8 && i + offset < bytes.length; i++) word |= (bytes[i + offset] & 255L) << (8 * i);
    return word;
  }
}
