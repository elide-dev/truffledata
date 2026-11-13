package dev.elide.truffledata.yaml.tests;

import static org.junit.jupiter.api.Assertions.*;

import dev.elide.truffledata.yaml.events.YamlEvents;
import dev.elide.truffledata.yaml.events.YamlMark;
import dev.elide.truffledata.yaml.events.YamlScalarStyle;
import dev.elide.truffledata.yaml.events.YamlSyntaxException;
import dev.elide.truffledata.yaml.snakeyaml.SnakeYamlEventSource;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

/**
 * Regression tests for the SnakeYAML event source's handler-exception contract: an exception thrown
 * by the handler propagates unchanged and ends the parse, while invalid input throws {@link
 * YamlSyntaxException} with the parser's failure chained as its cause.
 */
final class SnakeYamlEventSourceTest {
  private static final byte[] VALID = "a: 1\n".getBytes(StandardCharsets.UTF_8);

  /** A handler that throws {@code toThrow} from its {@code throwAt}-th callback. */
  private static final class ThrowingHandler implements YamlEvents {
    private final int throwAt;
    private final RuntimeException toThrow;
    private final AtomicInteger callbacks = new AtomicInteger();

    ThrowingHandler(int throwAt, RuntimeException toThrow) {
      this.throwAt = throwAt;
      this.toThrow = toThrow;
    }

    private void gate() {
      if (callbacks.incrementAndGet() == throwAt) throw toThrow;
    }

    @Override
    public void streamStart() {
      gate();
    }

    @Override
    public void streamEnd() {
      gate();
    }

    @Override
    public void documentStart(
        boolean explicit, int version, Map<String, String> tags, YamlMark mark) {
      gate();
    }

    @Override
    public void documentEnd(boolean explicit, YamlMark mark) {
      gate();
    }

    @Override
    public void mappingStart(
        @Nullable String anchor, @Nullable String tag, boolean flow, YamlMark mark) {
      gate();
    }

    @Override
    public void mappingEnd(YamlMark mark) {
      gate();
    }

    @Override
    public void sequenceStart(
        @Nullable String anchor, @Nullable String tag, boolean flow, YamlMark mark) {
      gate();
    }

    @Override
    public void sequenceEnd(YamlMark mark) {
      gate();
    }

    @Override
    public void scalar(
        String value,
        YamlScalarStyle style,
        boolean empty,
        @Nullable String anchor,
        @Nullable String tag,
        YamlMark start,
        int end) {
      gate();
    }

    @Override
    public void alias(String name, YamlMark mark) {
      gate();
    }

    int callbacks() {
      return callbacks.get();
    }
  }

  /** A handler that counts callbacks without throwing. */
  private static final class CountingHandler implements YamlEvents {
    private final AtomicInteger callbacks = new AtomicInteger();

    @Override
    public void streamStart() {
      callbacks.incrementAndGet();
    }

    @Override
    public void streamEnd() {
      callbacks.incrementAndGet();
    }

    @Override
    public void documentStart(
        boolean explicit, int version, Map<String, String> tags, YamlMark mark) {
      callbacks.incrementAndGet();
    }

    @Override
    public void documentEnd(boolean explicit, YamlMark mark) {
      callbacks.incrementAndGet();
    }

    @Override
    public void mappingStart(
        @Nullable String anchor, @Nullable String tag, boolean flow, YamlMark mark) {
      callbacks.incrementAndGet();
    }

    @Override
    public void mappingEnd(YamlMark mark) {
      callbacks.incrementAndGet();
    }

    @Override
    public void sequenceStart(
        @Nullable String anchor, @Nullable String tag, boolean flow, YamlMark mark) {
      callbacks.incrementAndGet();
    }

    @Override
    public void sequenceEnd(YamlMark mark) {
      callbacks.incrementAndGet();
    }

    @Override
    public void scalar(
        String value,
        YamlScalarStyle style,
        boolean empty,
        @Nullable String anchor,
        @Nullable String tag,
        YamlMark start,
        int end) {
      callbacks.incrementAndGet();
    }

    @Override
    public void alias(String name, YamlMark mark) {
      callbacks.incrementAndGet();
    }

    int callbacks() {
      return callbacks.get();
    }
  }

  /** A {@link MarkedYamlEngineException} a handler might throw from its own YAML work. */
  private static final class HandlerMarkedException extends MarkedYamlEngineException {
    HandlerMarkedException(String problem) {
      super(problem, Optional.empty(), problem, Optional.empty());
    }
  }

  // a: 1\n drives 8 events: streamStart(1), documentStart(2), mappingStart(3),
  // scalar(4, key "a"), scalar(5, value "1"), mappingEnd(6), documentEnd(7), streamEnd(8).

  @Test
  void handlerThrownYamlEngineExceptionPropagatesUnchanged() {
    YamlEngineException handlerThrown = new YamlEngineException("handler threw");
    ThrowingHandler handler = new ThrowingHandler(4, handlerThrown);
    var caught =
        assertThrows(
            YamlEngineException.class,
            () -> new SnakeYamlEventSource().parse(VALID, VALID.length, handler));
    assertSame(handlerThrown, caught, "handler-thrown exception must propagate unchanged");
    assertEquals(4, handler.callbacks(), "parse must end after the handler throws");
  }

  @Test
  void handlerThrownMarkedYamlEngineExceptionPropagatesUnchanged() {
    HandlerMarkedException handlerThrown = new HandlerMarkedException("handler threw (marked)");
    ThrowingHandler handler = new ThrowingHandler(4, handlerThrown);
    var caught =
        assertThrows(
            MarkedYamlEngineException.class,
            () -> new SnakeYamlEventSource().parse(VALID, VALID.length, handler));
    assertSame(handlerThrown, caught, "handler-thrown marked exception must propagate unchanged");
    assertEquals(4, handler.callbacks(), "parse must end after the handler throws");
  }

  @Test
  void handlerThrownRuntimeExceptionPropagatesUnchanged() {
    RuntimeException handlerThrown = new RuntimeException("handler threw (runtime)");
    ThrowingHandler handler = new ThrowingHandler(4, handlerThrown);
    var caught =
        assertThrows(
            RuntimeException.class,
            () -> new SnakeYamlEventSource().parse(VALID, VALID.length, handler));
    assertSame(handlerThrown, caught, "handler-thrown exception must propagate unchanged");
    assertFalse(
        caught instanceof YamlSyntaxException,
        "a handler failure must not be misreported as a syntax error");
    assertEquals(4, handler.callbacks(), "parse must end after the handler throws");
  }

  @Test
  void handlerThrownFromStreamStartPropagatesUnchanged() {
    YamlEngineException handlerThrown = new YamlEngineException("stream start");
    ThrowingHandler handler = new ThrowingHandler(1, handlerThrown);
    var caught =
        assertThrows(
            YamlEngineException.class,
            () -> new SnakeYamlEventSource().parse(VALID, VALID.length, handler));
    assertSame(
        handlerThrown, caught, "handler-thrown exception must propagate from the first callback");
    assertEquals(1, handler.callbacks(), "parse must end after the handler throws");
  }

  @Test
  void handlerThrownFromMappingEndPropagatesUnchanged() {
    YamlEngineException handlerThrown = new YamlEngineException("mapping end");
    ThrowingHandler handler = new ThrowingHandler(6, handlerThrown);
    var caught =
        assertThrows(
            YamlEngineException.class,
            () -> new SnakeYamlEventSource().parse(VALID, VALID.length, handler));
    assertSame(
        handlerThrown, caught, "handler-thrown exception must propagate from a late callback");
    assertEquals(6, handler.callbacks(), "parse must end after the handler throws");
  }

  @Test
  void invalidInputThrowsYamlSyntaxExceptionWithCause() {
    byte[] invalid = "a: [1, 2\n".getBytes(StandardCharsets.UTF_8);
    var caught =
        assertThrows(
            YamlSyntaxException.class,
            () -> new SnakeYamlEventSource().parse(invalid, invalid.length, new CountingHandler()));
    assertNotNull(caught.getCause(), "a syntax error must chain the engine exception as its cause");
    assertTrue(
        caught.getCause() instanceof YamlEngineException, "the cause must be the engine exception");
    assertNotNull(caught.detail(), "the syntax error must carry a detail");
    assertFalse(caught.detail().isEmpty(), "the syntax error detail must not be empty");
  }

  @Test
  void validInputDecodesWithoutException() {
    CountingHandler handler = new CountingHandler();
    new SnakeYamlEventSource().parse(VALID, VALID.length, handler);
    assertEquals(
        8,
        handler.callbacks(),
        "a: 1\\n drives 8 events: streamStart, documentStart, mappingStart, scalar(key),"
            + " scalar(value), mappingEnd, documentEnd, streamEnd");
  }

  @Test
  void yamlSyntaxExceptionChainsCause() {
    YamlEngineException cause = new YamlEngineException("root cause");
    YamlSyntaxException exception = new YamlSyntaxException("detail", YamlMark.UNKNOWN, cause);
    assertEquals("detail", exception.detail());
    assertEquals(YamlMark.UNKNOWN, exception.mark());
    assertSame(cause, exception.getCause());
    assertTrue(exception.getMessage().contains("detail"));
  }
}
