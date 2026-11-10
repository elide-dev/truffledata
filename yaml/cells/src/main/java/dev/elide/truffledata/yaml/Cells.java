package dev.elide.truffledata.yaml;

import dev.elide.myna.truffle.NativeCell;
import dev.elide.myna.truffle.NativeCellRootNode;
import dev.elide.myna.truffle.NativePointer;

/**
 * What the cells backends of both runtimes share. It names no Native Image or FFM type, so the
 * HotSpot backends never load a static-image class and the static backends never load an FFM one.
 *
 * <p>How the route calls a cell (Myna 0.14.0, {@code docs/truffle-cells.md}):
 *
 * <ul>
 *   <li>Each scalar cell is held as its concrete generated {@code *_Cell} type and called through
 *       the typed method it overrides ({@code executeInt}, {@code executeLong}): no result box and
 *       no {@code UnexpectedResultException} handler.
 *   <li>A cell takes its arguments as an {@code Object[]} of interop values. Every session entry is
 *       a {@code @TruffleBoundary}, so partial evaluation never sees these calls and nothing
 *       guarantees that array is scalar-replaced. Each backend therefore keeps one argument array
 *       per cell and reuses it: a backend is confined to its owner's thread and the YAML natives
 *       never call back into Java, so a call cannot overlap another on the same array. Slots that
 *       never change (the session, the result slot, a plan handle) are filled once with a cached
 *       {@link NativePointer}.
 *   <li>What still allocates per call is what the cell API requires: one {@link NativePointer} for
 *       an address that changes per call (a pinned input, a per-call result), one {@code Long} box
 *       for a length above the {@code Long} cache, and the {@code Borrow} the generated cell makes
 *       for every byte-pointer parameter.
 *   <li>A slot that held a call-scoped address (a pinned array, a stack slot, a per-call result) is
 *       cleared when the call returns, so no such address outlives its call in an argument array.
 * </ul>
 *
 * <p>The seam declares no {@code borrow} and no {@code resource} for these imports because Myna
 * 0.14.0 rejects both for their shapes: a call borrow allows no pointer parameter besides the
 * buffer ({@code session_decode}, {@code stream_feed}, {@code typed_decode} and {@code
 * session_write_text} all take a handle or a result pointer), and a resource constructor takes
 * scalars only and returns the handle ({@code session_create} and {@code stream_create} take
 * pointers; plans and results come back through out-parameters). Inputs are therefore pinned or
 * staged here, exactly as the baseline backends do, and lent as pointers, with no extra copy.
 */
final class Cells {
  private Cells() {}

  /** Adopts {@code cell} under its own language-neutral root; a cell is adopted exactly once. */
  static <T extends NativeCell> T adopt(T cell) {
    new NativeCellRootNode(cell).adoptChildren();
    return cell;
  }

  /**
   * The pointer carrier of an address that changes rarely (a stream handle): one {@link
   * NativePointer} is kept until the address changes, so steady-state calls allocate none. A
   * pointer is a value, so a handle later reused at the same address gets an equal carrier.
   */
  static final class Handle {
    private long address;
    private NativePointer pointer = NativePointer.NULL;

    NativePointer of(long address) {
      if (address != this.address) {
        pointer = NativePointer.of(address);
        this.address = address;
      }
      return pointer;
    }

    /** Keeps the carrier a cell just returned for a new handle. */
    void set(NativePointer pointer) {
      this.pointer = pointer;
      this.address = pointer.address();
    }
  }
}
