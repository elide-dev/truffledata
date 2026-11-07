package dev.elide.truffledata.csv;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * The owner of one read-only memory-mapped CSV file: a {@code FileChannel} mapping in a confined
 * (default) or shared {@link Arena}. Batches parsed from it keep their STRING cells as spans into
 * the mapping, with no heap copy of the input.
 *
 * <p>{@link #close()} unmaps the file and invalidates every batch over it: afterwards each read of
 * such a batch (typed, STRING, interop, export, snapshot) throws {@code IllegalStateException("CSV
 * mapping closed")}. Take {@link CsvBatch#snapshot()} first to keep a batch. A confined mapping can
 * be read and closed only by the thread that mapped it (other threads get {@code
 * WrongThreadException} on reads that touch the file); a shared mapping can be read and closed from
 * any thread.
 */
public final class CsvMapping implements AutoCloseable {
  private final Arena arena;
  private final boolean shared;
  final MemorySegment segment;

  /** Plain field: read on every batch read (a null test and one load), written once by close. */
  boolean open = true;

  private CsvMapping(Arena arena, boolean shared, MemorySegment segment) {
    this.arena = arena;
    this.shared = shared;
    this.segment = segment;
  }

  /** Maps {@code path} read-only into a confined arena. */
  public static CsvMapping map(Path path) throws IOException {
    return map(path, false);
  }

  /** Maps {@code path} read-only into a shared ({@code true}) or confined arena. */
  public static CsvMapping map(Path path, boolean shared) throws IOException {
    try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
      return map(channel, shared);
    }
  }

  /** Maps a caller-owned channel into a confined arena; never closes the channel. */
  public static CsvMapping map(FileChannel channel) throws IOException {
    return map(channel, false);
  }

  /**
   * Maps the entire file without changing the caller-owned channel's position or closing it.
   * Closing the channel leaves this mapping valid; closing the mapping leaves the channel open.
   */
  public static CsvMapping map(FileChannel channel, boolean shared) throws IOException {
    Arena arena = shared ? Arena.ofShared() : Arena.ofConfined();
    try {
      long size = channel.size();
      MemorySegment segment =
          size == 0
              ? MemorySegment.ofArray(new byte[0])
              : channel.map(FileChannel.MapMode.READ_ONLY, 0, size, arena);
      return new CsvMapping(arena, shared, segment);
    } catch (IOException | RuntimeException | Error e) {
      arena.close();
      throw e;
    }
  }

  /** Mapped bytes. */
  public long size() {
    return segment.byteSize();
  }

  public boolean shared() {
    return shared;
  }

  public boolean isOpen() {
    return open;
  }

  /** Unmaps the file and invalidates every batch over it; idempotent. */
  @Override
  public void close() {
    if (!open) return;
    arena.close(); // a confined arena throws WrongThreadException from another thread
    open = false;
  }

  void checkOpen() {
    if (!open) throw closed();
  }

  @TruffleBoundary
  static IllegalStateException closed() {
    return new IllegalStateException("CSV mapping closed");
  }

  /** Copies [offset, offset + length) of the file into {@code target} at {@code at}. */
  void copy(long offset, byte[] target, int at, int length) {
    checkOpen();
    MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, offset, target, at, length);
  }
}
