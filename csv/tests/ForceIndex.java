package dev.elide.truffledata.csv;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Registered by service autodetection in {@code //csv/tests:jvm_indexed} only: every CSV suite runs
 * with the index path forced at every input size, over SWAR with one 64-byte block per kernel call,
 * so the unchanged suites check the index path (and its per-byte fallback) as well.
 */
public final class ForceIndex implements BeforeAllCallback, AfterAllCallback {
  private CsvIndexing.@org.jspecify.annotations.Nullable Provider saved;

  @Override
  public void beforeAll(ExtensionContext context) {
    saved = CsvIndexing.provider();
    CsvIndexing.install(IndexedDifferentialTest.SMALL);
    CsvIndexing.minIndexBytes = 0;
  }

  @Override
  public void afterAll(ExtensionContext context) {
    CsvIndexing.install(saved);
    CsvIndexing.minIndexBytes = CsvIndexing.MIN_INDEX_BYTES;
  }
}
