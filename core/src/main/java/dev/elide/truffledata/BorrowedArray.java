package dev.elide.truffledata;

import com.oracle.truffle.api.interop.TruffleObject;

/** Scoped adapter array. Every access must check its owner; owned containers reject this value. */
public interface BorrowedArray extends TruffleObject {
  int size();

  Object element(int index);

  DataArray snapshot();
}
