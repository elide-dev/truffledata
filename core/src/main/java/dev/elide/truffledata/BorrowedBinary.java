package dev.elide.truffledata;

import com.oracle.truffle.api.interop.TruffleObject;

/**
 * Read-only binary payload whose owner controls its lifetime. Implementations export interop array
 * access and must reject reads after their lease closes. This representation is admitted only by
 * ANY adapter-read contracts. Normalization into owned batches/arrays rejects this representation;
 * snapshot before retaining detached data.
 */
public interface BorrowedBinary extends TruffleObject {
  int size();

  long get(int index);

  DataArray snapshot();
}
