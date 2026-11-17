package dev.elide.truffledata.tape;

import static dev.elide.truffledata.tape.TapeReader.*;
import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.Assumption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Close/refusal contracts of {@link TapeSession} for documents lent by a stream: a close that
 * arrives while a pinned export walks a streamed lend must refuse (throw) without releasing the
 * native session, leaving the borrow and the session usable. Uses the same fake-{@link
 * TapeSession.Backend} pattern as {@link TapeOwnerTest}; storage is a hand-built heap tape so the
 * walk keeps reading live words after the refused close.
 */
class TapeSessionCloseTest {
  /**
   * A 4-bit counter shelf for the "release ran under the walk" assertion: {@code release()}
   * increments {@code releases}, {@code reset()} increments {@code resets}; all other abstract
   * methods throw because the session under test never reaches them.
   */
  private static final class CountingBackend extends TapeSession.Backend<TapeOwner> {
    final HeapTape tape;
    int releases;
    int resets;

    CountingBackend(HeapTape tape) {
      this.tape = tape;
    }

    @Override
    protected TapeBorrow borrow() {
      return new TapeBorrow(tape, TapeFormat.of("JSON"));
    }

    @Override
    protected void reset() {
      resets++;
    }

    @Override
    protected void release() {
      releases++;
    }

    @Override
    protected TapeOwner retain() {
      throw new AssertionError("retain");
    }

    @Override
    protected void decode(byte[] data, int offset, int length, int encoding, TapeTextArena text) {
      throw new AssertionError("decode bytes");
    }

    @Override
    protected void decode(java.lang.foreign.MemorySegment data, long length) {
      throw new AssertionError("decode segment");
    }

    @Override
    protected TapeTextArena textArena(byte[] array) {
      throw new AssertionError("textArena");
    }

    @Override
    protected long streamCreate(int maxDocumentBytes, int growth) {
      throw new AssertionError("streamCreate");
    }

    @Override
    protected int streamFeed(long stream, byte[] data, int offset, int length) {
      throw new AssertionError("streamFeed bytes");
    }

    @Override
    protected long streamFeed(long stream, java.lang.foreign.MemorySegment data) {
      throw new AssertionError("streamFeed segment");
    }

    @Override
    protected void streamFinish(long stream) {
      throw new AssertionError("streamFinish");
    }

    @Override
    protected int streamNext(long stream) {
      throw new AssertionError("streamNext");
    }

    @Override
    protected void streamRelease(long stream) {
      throw new AssertionError("streamRelease");
    }
  }

  /** {"n":42,"s":"A"}: two keys, a signed wide long, and an owned string ("A" in the pool). */
  private static HeapTape document() {
    byte[] input = "{\"n\":42,\"s\":\"\\u0041\"}".getBytes(StandardCharsets.UTF_8);
    long[] words = {
      w(9, WIDE, 2, 8),
      0,
      w(7, ASCII | KEY, 1, 2),
      w(3, WIDE, 2, 5),
      42,
      w(7, ASCII | KEY, 1, 9),
      w(7, ASCII | OWNED | WIDE, 1, 0),
      (12L << 32) | 8,
    };
    return new HeapTape(words, new byte[] {'A'}, input, new long[0], new int[0], 0);
  }

  private static long w(int kind, long flags, int field, long low) {
    return ((long) kind << 60) | flags | ((long) field << 32) | low;
  }

  /** A session over the counting backend; {@code checkStatus} is unused and asserts if reached. */
  private static TapeSession<TapeOwner> session(CountingBackend backend) {
    return new TapeSession<>("JSON", 1L << 20, backend) {
      @Override
      protected void checkStatus(int status) {
        throw new AssertionError("unexpected status " + status);
      }
    };
  }

  /** A no-op stream handle: {@code lendStreamed} stores {@code by} but never calls back into it. */
  private static TapeStream<TapeOwner> stream(TapeSession<TapeOwner> open) {
    return new TapeStream<>(open, 0L, 0) {};
  }

  /**
   * Closing the session while a pinned export walks a streamed lend throws the pin guard's "message
   * is being exported" refusal, releases nothing, leaves the borrow tracked and the session usable,
   * and the walk drains to its end over live storage.
   */
  @Test
  void streamedCloseDuringPinnedExportRefusesWithoutReleasing() {
    CountingBackend backend = new CountingBackend(document());
    TapeSession<TapeOwner> session = session(backend);
    TapeStream<TapeOwner> stream = stream(session);
    TapeValue root = session.lendStreamed(session.guard(), stream);
    Assumption alive = root.owner().assumption();

    List<String> events = new ArrayList<>();
    IllegalStateException[] refused = new IllegalStateException[1];
    root.export(
        new TapeSink() {
          @Override
          public void beginObject(long members) {
            events.add("begin:" + members);
          }

          @Override
          public void key(TapeText name) {
            events.add("key:" + name.toJavaString());
          }

          @Override
          public void longValue(long value) {
            events.add("long:" + value);
          }

          @Override
          public void stringValue(TapeText text) {
            events.add("string:" + text.toJavaString());
            refused[0] =
                assertThrows(IllegalStateException.class, session::close, "close under the walk");
          }

          @Override
          public void end() {
            events.add("end");
          }

          @Override
          public void beginArray(long elements) {
            events.add("beginArray:" + elements);
          }

          @Override
          public void nullValue() {
            events.add("null");
          }

          @Override
          public void booleanValue(boolean value) {
            events.add("boolean:" + value);
          }

          @Override
          public void unsignedValue(long value) {
            events.add("unsigned:" + value);
          }

          @Override
          public void doubleValue(double value) {
            events.add("double:" + value);
          }

          @Override
          public void decimalValue(TapeText text) {
            events.add("decimal:" + text.toJavaString());
          }
        });

    // The refusal is the pin guard, not a release-path artifact.
    assertNotNull(refused[0], "close threw inside the walk");
    assertEquals("JSON message is being exported", refused[0].getMessage());

    // The native session was not freed (and the streamed borrow was not reset) under the walk.
    assertEquals(0, backend.releases, "release() ran during a refused close");
    assertEquals(0, backend.resets, "reset() ran during a refused close");

    // The walk drained past the refused close: it kept reading the (live) tape.
    assertEquals(List.of("begin:2", "key:n", "long:42", "key:s", "string:A", "end"), events);

    // The streamed borrow is still open: its liveness assumption was never invalidated.
    assertTrue(alive.isValid(), "borrow assumption invalidated by a refused close");
    assertEquals(TapeValue.Kind.OBJECT, root.kind(), "lent view is still readable");
    TapeOwner.CheckOpenNode.create().execute(root.owner());

    // The session is still usable: a subsequent close ends the streamed borrow and releases once.
    session.close();
    assertEquals(1, backend.releases, "the later close released the backend once");
    assertEquals(1, backend.resets, "the later close reset the streamed borrow once");
    assertFalse(alive.isValid(), "the later close invalidated the borrow assumption");
    assertThrows(IllegalStateException.class, () -> session.guard(), "session closed");
    assertThrows(IllegalStateException.class, root::kind, "borrow ended by the later close");
  }

  /**
   * A close with a streamed lend but no export in progress ends the borrow and releases the backend
   * exactly once; the lent view fails afterwards. This is the control for the pinned path: the same
   * close, when the walk holds the borrow, must refuse instead.
   */
  @Test
  void streamedCloseWithoutExportEndsTheBorrowAndReleases() {
    CountingBackend backend = new CountingBackend(document());
    TapeSession<TapeOwner> session = session(backend);
    TapeStream<TapeOwner> stream = stream(session);
    TapeValue root = session.lendStreamed(session.guard(), stream);
    Assumption alive = root.owner().assumption();
    assertEquals(TapeValue.Kind.OBJECT, root.kind());

    session.close();

    assertEquals(1, backend.releases, "close released the backend once");
    assertEquals(1, backend.resets, "close reset the streamed borrow once");
    assertFalse(alive.isValid(), "close invalidated the borrow assumption");
    assertThrows(IllegalStateException.class, root::kind, "lent view fails after close");
    assertThrows(IllegalStateException.class, () -> session.guard(), "session closed");
  }

  /**
   * A session or stream call that reaches {@link TapeSession#check()} while a pinned export walks a
   * streamed lend is refused, and the streamed borrow stays tracked so a second call is refused too
   * (rather than proceeding after the first refusal tore the tracking down).
   */
  @Test
  void reentryDuringPinnedStreamedExportIsRefusedAndStaysTracked() {
    CountingBackend backend = new CountingBackend(document());
    TapeSession<TapeOwner> session = session(backend);
    TapeStream<TapeOwner> stream = stream(session);
    TapeValue root = session.lendStreamed(session.guard(), stream);

    int[] refusals = {0};
    root.export(
        new TapeSink() {
          @Override
          public void beginObject(long members) {}

          @Override
          public void key(TapeText name) {}

          @Override
          public void longValue(long value) {}

          @Override
          public void stringValue(TapeText text) {
            // Two re-entries must both be refused: the streamed borrow is not torn down by the
            // first refusal, so the second re-entry retries (and refuses) the same end.
            for (int i = 0; i < 2; i++) {
              IllegalStateException refused =
                  assertThrows(
                      IllegalStateException.class, session::criticalDecodes, "re-entry " + i);
              assertEquals("JSON message is being exported", refused.getMessage());
              refusals[0]++;
            }
          }

          @Override
          public void end() {}

          @Override
          public void beginArray(long elements) {}

          @Override
          public void nullValue() {}

          @Override
          public void booleanValue(boolean value) {}

          @Override
          public void unsignedValue(long value) {}

          @Override
          public void doubleValue(double value) {}

          @Override
          public void decimalValue(TapeText text) {}
        });
    assertEquals(2, refusals[0], "both re-entries were refused");
    // Nothing was released or reset while the walk held the borrow.
    assertEquals(0, backend.releases, "release() ran during the pinned walk");
    assertEquals(0, backend.resets, "reset() ran during the pinned walk");

    // After the walk the streamed borrow is still tracked: a session call ends it (reset once)
    // and then the session can be closed (release once).
    assertEquals(0L, session.criticalDecodes(), "session usable after the walk");
    assertEquals(1, backend.resets, "the post-walk call ended the streamed borrow");
    session.close();
    assertEquals(1, backend.releases, "the later close released the backend once");
  }
}
