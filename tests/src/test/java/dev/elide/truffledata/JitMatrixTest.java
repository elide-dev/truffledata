package dev.elide.truffledata;

import dev.elide.truffledata.bench.JitMatrix;
import org.junit.jupiter.api.Test;

class JitMatrixTest {
  @Test
  void publicAccessMatrix() throws Exception {
    JitMatrix.run(Boolean.getBoolean("truffledata.requireCompiled"));
  }
}
