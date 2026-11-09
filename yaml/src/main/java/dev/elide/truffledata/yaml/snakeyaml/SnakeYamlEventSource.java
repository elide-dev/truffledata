package dev.elide.truffledata.yaml.snakeyaml;

import dev.elide.truffledata.yaml.events.YamlEventSource;
import dev.elide.truffledata.yaml.events.YamlEvents;
import dev.elide.truffledata.yaml.events.YamlMark;
import dev.elide.truffledata.yaml.events.YamlScalarStyle;
import dev.elide.truffledata.yaml.events.YamlSyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.common.Anchor;
import org.snakeyaml.engine.v2.common.SpecVersion;
import org.snakeyaml.engine.v2.events.AliasEvent;
import org.snakeyaml.engine.v2.events.CollectionStartEvent;
import org.snakeyaml.engine.v2.events.DocumentEndEvent;
import org.snakeyaml.engine.v2.events.DocumentStartEvent;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.exceptions.Mark;
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.scanner.Scanner;
import org.snakeyaml.engine.v2.scanner.ScannerImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;
import org.snakeyaml.engine.v2.tokens.ScalarToken;
import org.snakeyaml.engine.v2.tokens.Token;

/**
 * The kernel's first event source: SnakeYAML Engine's low-level scanner and parser (no composer,
 * no constructor, no resolver), so every TruffleData policy above the event boundary is the
 * kernel's own. Only this class imports SnakeYAML.
 *
 * <p>Engine marks count code points; they are mapped back to byte offsets of the caller's UTF-8.
 * A scalar event's start mark covers its anchor and tag, so a recording scanner keeps the start of
 * the scalar token itself.
 */
public final class SnakeYamlEventSource implements YamlEventSource {
  public SnakeYamlEventSource() {}

  // Marks are needed for positions; the per-document code point limit is the kernel's input limit.
  // The version function accepts every %YAML version: the kernel applies its own 1.x policy.
  // Built per parse: no Engine object is held by this class, so images that initialize
  // dev.elide.truffledata at build time never carry Engine state in their heap.
  private static LoadSettings settings() {
    return LoadSettings.builder()
        .setUseMarks(true)
        .setParseComments(false)
        .setCodePointLimit(Integer.MAX_VALUE)
        .setVersionFunction(version -> version)
        .build();
  }

  @Override
  public void parse(byte[] utf8, int length, YamlEvents events) {
    LoadSettings settings = settings();
    String text = new String(utf8, 0, length, StandardCharsets.UTF_8);
    Positions positions = new Positions(utf8, length, text);
    Recorder scanner = new Recorder(new ScannerImpl(settings, new StreamReader(settings, text)));
    ParserImpl parser = new ParserImpl(settings, scanner);
    try {
      while (parser.hasNext()) {
        Event event = parser.next();
        switch (event.getEventId()) {
          case StreamStart -> events.streamStart();
          case StreamEnd -> events.streamEnd();
          case DocumentStart -> {
            DocumentStartEvent start = (DocumentStartEvent) event;
            Optional<SpecVersion> spec = start.getSpecVersion();
            int version = 0;
            if (spec.isPresent()) {
              int major = spec.get().getMajor(), minor = spec.get().getMinor();
              version = Math.min(major, 0x7fff) << 16 | Math.min(minor, 0xffff);
            }
            Map<String, String> tags = start.getTags();
            events.documentStart(start.isExplicit(), version, tags, positions.mark(event.getStartMark()));
          }
          case DocumentEnd ->
              events.documentEnd(((DocumentEndEvent) event).isExplicit(), positions.mark(event.getStartMark()));
          case MappingStart, SequenceStart -> {
            CollectionStartEvent start = (CollectionStartEvent) event;
            String anchor = anchor(start.getAnchor());
            String tag = start.getTag().orElse(null);
            YamlMark mark = positions.mark(event.getStartMark());
            if (event.getEventId() == Event.ID.MappingStart) events.mappingStart(anchor, tag, start.isFlow(), mark);
            else events.sequenceStart(anchor, tag, start.isFlow(), mark);
          }
          case MappingEnd -> events.mappingEnd(positions.mark(event.getStartMark()));
          case SequenceEnd -> events.sequenceEnd(positions.mark(event.getStartMark()));
          case Scalar -> scalar((ScalarEvent) event, scanner.take(), positions, events);
          case Alias ->
              events.alias(
                  ((AliasEvent) event).getAlias().getValue(), positions.mark(event.getStartMark()));
          default -> {} // comments are not requested
        }
      }
    } catch (MarkedYamlEngineException failure) {
      Optional<Mark> mark =
          failure.getProblemMark().isPresent() ? failure.getProblemMark() : failure.getContextMark();
      String problem = failure.getProblem();
      throw new YamlSyntaxException(
          problem == null || problem.isEmpty() ? String.valueOf(failure.getContext()) : problem,
          positions.mark(mark));
    } catch (YamlEngineException failure) {
      throw new YamlSyntaxException(String.valueOf(failure.getMessage()), YamlMark.UNKNOWN);
    }
  }

  private static void scalar(
      ScalarEvent event, @Nullable ScalarToken token, Positions positions, YamlEvents events) {
    YamlScalarStyle style =
        switch (event.getScalarStyle()) {
          case SINGLE_QUOTED -> YamlScalarStyle.SINGLE_QUOTED;
          case DOUBLE_QUOTED -> YamlScalarStyle.DOUBLE_QUOTED;
          case LITERAL -> YamlScalarStyle.LITERAL;
          case FOLDED -> YamlScalarStyle.FOLDED;
          default -> YamlScalarStyle.PLAIN;
        };
    // No token: an empty scalar inferred from structure (a missing value, `key:`, `- `).
    boolean empty = token == null;
    YamlMark start = positions.mark(token == null ? event.getEndMark() : token.getStartMark());
    YamlMark end = positions.mark(token == null ? event.getEndMark() : token.getEndMark());
    events.scalar(
        event.getValue(),
        style,
        empty,
        anchor(event.getAnchor()),
        event.getTag().orElse(null),
        start,
        empty ? start.offset() : end.offset());
  }

  private static @Nullable String anchor(Optional<Anchor> anchor) {
    return anchor.map(Anchor::getValue).orElse(null);
  }

  /** Remembers the scalar token the parser consumed for the event it is producing. */
  private static final class Recorder implements Scanner {
    private final Scanner scanner;
    private @Nullable ScalarToken scalar;

    Recorder(Scanner scanner) {
      this.scanner = scanner;
    }

    @Nullable ScalarToken take() {
      ScalarToken token = scalar;
      scalar = null;
      return token;
    }

    @Override
    public boolean checkToken(Token.ID... choices) {
      return scanner.checkToken(choices);
    }

    @Override
    public Token peekToken() {
      return scanner.peekToken();
    }

    @Override
    public Token next() {
      Token token = scanner.next();
      if (token instanceof ScalarToken value) scalar = value;
      return token;
    }

    @Override
    public boolean hasNext() {
      return scanner.hasNext();
    }

    @Override
    public void resetDocumentIndex() {
      scanner.resetDocumentIndex();
    }
  }

  /** Engine code point indices to byte offsets of the source UTF-8. */
  private static final class Positions {
    private final int length;
    private final int @Nullable [] offsets;

    Positions(byte[] utf8, int length, String text) {
      this.length = length;
      boolean ascii = true;
      for (int i = 0; i < length && ascii; i++) ascii = utf8[i] >= 0;
      if (ascii) {
        offsets = null;
        return;
      }
      int count = text.codePointCount(0, text.length());
      int[] table = new int[count + 1];
      for (int i = 0, at = 0; i < count; i++) {
        table[i] = at;
        int lead = utf8[at] & 255;
        at += lead < 0x80 ? 1 : lead < 0xE0 ? 2 : lead < 0xF0 ? 3 : 4;
      }
      table[count] = length;
      offsets = table;
    }

    int offset(int index) {
      int[] table = offsets;
      if (table == null) return Math.min(Math.max(index, 0), length);
      return table[Math.min(Math.max(index, 0), table.length - 1)];
    }

    YamlMark mark(Optional<Mark> mark) {
      if (mark.isEmpty()) return YamlMark.UNKNOWN;
      Mark m = mark.get();
      return new YamlMark(offset(m.getIndex()), m.getLine() + 1, m.getColumn() + 1);
    }
  }
}
