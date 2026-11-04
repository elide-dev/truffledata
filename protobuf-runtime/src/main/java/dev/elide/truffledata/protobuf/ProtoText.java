package dev.elide.truffledata.protobuf;

import dev.elide.truffledata.*;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Descriptor-free, bounded text-format parser. Parsing is confined to ProtoPlan's boundary. */
final class ProtoText {
  private final String input;
  private final ProtoRow.Budget budget;
  private int position;

  private ProtoText(ProtoPlan plan, String input) {
    // Also bounds tokenizer and unescape scratch allocation before any substrings are created.
    if (input.length() > plan.model.maxWireBytes / 2)
      throw new ContractViolation("Protobuf text input budget exceeded");
    this.input = input;
    this.budget = new ProtoRow.Budget(plan.model.limits);
  }

  static ProtoRow decode(ProtoPlan plan, String input) {
    ProtoText parser = new ProtoText(plan, input);
    ProtoRow result = parser.message(plan, '\0', 1);
    parser.space();
    if (parser.position != input.length()) throw parser.bad("Trailing input");
    return result;
  }

  private ContractViolation bad(String message) {
    return new ContractViolation(message + " at text offset " + position);
  }

  private void space() {
    while (position < input.length()) {
      char c = input.charAt(position);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f') position++;
      else if (c == '#') {
        while (position < input.length() && input.charAt(position) != '\n') position++;
      } else break;
    }
  }

  private boolean take(char c) {
    space();
    if (position < input.length() && input.charAt(position) == c) {
      position++;
      return true;
    }
    return false;
  }

  private void need(char c) {
    if (!take(c)) throw bad("Expected '" + c + "'");
  }

  private String token() {
    space();
    int start = position;
    while (position < input.length()) {
      char c = input.charAt(position);
      if ((c >= 'a' && c <= 'z')
          || (c >= 'A' && c <= 'Z')
          || (c >= '0' && c <= '9')
          || c == '_'
          || c == '+'
          || c == '-'
          || c == '.') position++;
      else break;
    }
    if (position == start) throw bad("Expected identifier or number");
    return input.substring(start, position);
  }

  private ProtoRow message(ProtoPlan plan, char close, int depth) {
    if (depth > plan.model.maxDepth) throw bad("Protobuf nesting budget exceeded");
    budget.cells(plan.size());
    ProtoRow.Builder builder = plan.newBuilder();
    boolean[] seen = new boolean[plan.size()];
    Map<Integer, List<Object>> repeated = new HashMap<>();
    while (true) {
      space();
      if (close != '\0' && take(close)) break;
      if (position == input.length()) {
        if (close != '\0') throw bad("Unterminated message");
        break;
      }
      String name = token();
      int ordinal = -1;
      for (int i = 0; i < plan.size(); i++) {
        var field = plan.field(i);
        String textName =
            field.kind() == ProtoPlan.Kind.GROUP
                ? field.typeName().substring(field.typeName().lastIndexOf('.') + 1)
                : field.name();
        if (textName.equals(name)) {
          ordinal = i;
          break;
        }
      }
      if (ordinal < 0) throw bad("Unknown field: " + name);
      var field = plan.field(ordinal);
      if (!field.repeated()) {
        if (seen[ordinal]) throw bad("Duplicate singular field: " + name);
        if (field.oneof() >= 0) {
          for (int i = 0; i < seen.length; i++)
            if (seen[i] && plan.field(i).oneof() == field.oneof())
              throw bad("Multiple oneof members");
        }
      }
      seen[ordinal] = true;
      if (field.kind().message()) take(':');
      else need(':');
      if (field.repeated()) {
        List<Object> items = repeated.computeIfAbsent(ordinal, ignored -> new ArrayList<>());
        if (take('[')) {
          if (!take(']')) {
            do {
              budget.cells(1);
              items.add(value(plan, ordinal, depth));
            } while (take(','));
            need(']');
          }
        } else {
          budget.cells(1);
          items.add(value(plan, ordinal, depth));
        }
      } else builder.set(ordinal, value(plan, ordinal, depth));
      if (!take(';')) take(',');
    }
    for (var entry : repeated.entrySet()) {
      List<Object> items = entry.getValue();
      if (plan.field(entry.getKey()).map()) {
        Map<Object, Object> keys = new LinkedHashMap<>();
        for (Object item : items)
          keys.put(ProtoRow.mapKey(DataLibrary.factory().getUncached().read(item, 0)), item);
        builder.set(entry.getKey(), new DataArray(keys.values().toArray()));
      } else builder.set(entry.getKey(), new DataArray(items.toArray()));
    }
    return builder.build();
  }

  private Object value(ProtoPlan plan, int ordinal, int depth) {
    var field = plan.field(ordinal);
    if (field.kind().message()) {
      if (depth >= plan.model.maxDepth) throw bad("Protobuf nesting budget exceeded");
      char close;
      if (take('{')) close = '}';
      else if (take('<')) close = '>';
      else throw bad("Expected message body");
      return message(plan.nested(ordinal), close, depth + 1);
    }
    if (field.kind() == ProtoPlan.Kind.STRING || field.kind() == ProtoPlan.Kind.BYTES) {
      byte[] bytes = quoted();
      if (field.kind() == ProtoPlan.Kind.BYTES) {
        budget.cells(bytes.length);
        return DataArray.copyOfBytes(ByteBuffer.wrap(bytes));
      }
      String text;
      try {
        text =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
      } catch (CharacterCodingException invalid) {
        throw bad("Invalid UTF-8 string");
      }
      budget.bytes(2L * text.length());
      return text;
    }
    String text = token();
    try {
      return switch (field.kind()) {
        case BOOL ->
            switch (text) {
              case "true", "True", "t", "1" -> true;
              case "false", "False", "f", "0" -> false;
              default -> throw bad("Invalid boolean");
            };
        case FLOAT, DOUBLE -> floating(text, field.kind() == ProtoPlan.Kind.FLOAT);
        case ENUM -> {
          var enumeration = Objects.requireNonNull(field.enumType());
          Integer number = enumeration.values().get(text);
          long value = number == null ? integer(text).intValueExact() : number.longValue();
          if (!enumeration.accepts((int) value)) throw bad("Unknown closed-enum number");
          yield value;
        }
        case UINT64, FIXED64 -> Values.normalize(integer(text));
        default -> integer(text).longValueExact();
      };
    } catch (NumberFormatException | ArithmeticException e) {
      throw bad("Invalid or out-of-range number");
    }
  }

  private static BigInteger integer(String text) {
    if (text.length() > 128) throw new NumberFormatException("Integer token too long");
    boolean negative = text.startsWith("-");
    String digits = negative || text.startsWith("+") ? text.substring(1) : text;
    int radix = 10;
    if (digits.startsWith("0x") || digits.startsWith("0X")) {
      radix = 16;
      digits = digits.substring(2);
    } else if (digits.length() > 1 && digits.startsWith("0")) radix = 8;
    // BigInteger accepts embedded signs after our prefix; reject those explicitly.
    if (digits.isEmpty() || digits.charAt(0) == '-' || digits.charAt(0) == '+')
      throw new NumberFormatException();
    BigInteger value = new BigInteger(digits, radix);
    return negative ? value.negate() : value;
  }

  private static double floating(String text, boolean single) {
    String lower = text.toLowerCase(Locale.ROOT);
    double value =
        switch (lower) {
          case "inf", "infinity", "+inf", "+infinity" -> Double.POSITIVE_INFINITY;
          case "-inf", "-infinity" -> Double.NEGATIVE_INFINITY;
          case "nan" -> Double.NaN;
          default -> Double.parseDouble(text);
        };
    return single ? (double) (float) value : value;
  }

  private byte[] quoted() {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    boolean any = false;
    while (true) {
      space();
      if (position == input.length()) break;
      char quote = input.charAt(position);
      if (quote != '\'' && quote != '"') break;
      any = true;
      int start = ++position;
      boolean closed = false;
      while (position < input.length()) {
        char c = input.charAt(position++);
        if (c == quote) {
          closed = true;
          break;
        }
        if (c == '\n' || c == '\r') throw bad("Newline in string literal");
        if (c == '\\') {
          if (position == input.length()) throw bad("Unterminated escape");
          char escaped = input.charAt(position++);
          if (escaped == '\n' || escaped == '\r') throw bad("Newline in escape");
        }
      }
      if (!closed) throw bad("Unterminated string literal");
      String raw = input.substring(start, position - 1);
      ProtoPlan.validateString(raw);
      unescape(raw, result);
    }
    if (!any) throw bad("Expected quoted string");
    return result.toByteArray();
  }

  /** Text-format byte escapes, with Unicode escapes encoded as UTF-8. */
  private void unescape(String raw, ByteArrayOutputStream output) {
    byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
    for (int i = 0; i < bytes.length; i++) {
      int c = bytes[i] & 255;
      if (c != '\\') {
        output.write(c);
        continue;
      }
      if (++i == bytes.length) throw bad("Unterminated escape");
      c = bytes[i] & 255;
      if (c >= '0' && c <= '7') {
        int value = c - '0';
        for (int count = 1;
            count < 3 && i + 1 < bytes.length && bytes[i + 1] >= '0' && bytes[i + 1] <= '7';
            count++) value = (value << 3) | (bytes[++i] - '0');
        output.write(value);
        continue;
      }
      switch (c) {
        case 'a' -> output.write(7);
        case 'b' -> output.write(8);
        case 'f' -> output.write(12);
        case 'n' -> output.write(10);
        case 'r' -> output.write(13);
        case 't' -> output.write(9);
        case 'v' -> output.write(11);
        case '\\', '\'', '"', '?' -> output.write(c);
        case 'x', 'X' -> {
          if (i + 1 == bytes.length || hex(bytes[i + 1]) < 0)
            throw bad("Expected hexadecimal escape");
          int value = hex(bytes[++i]);
          if (i + 1 < bytes.length && hex(bytes[i + 1]) >= 0)
            value = (value << 4) | hex(bytes[++i]);
          output.write(value);
        }
        case 'u', 'U' -> {
          int digits = c == 'u' ? 4 : 8;
          long value = 0;
          for (int j = 0; j < digits; j++) {
            if (++i == bytes.length || hex(bytes[i]) < 0) throw bad("Invalid Unicode escape");
            value = (value << 4) | hex(bytes[i]);
          }
          if (value > 0x10ffff || (value >= 0xd800 && value <= 0xdfff))
            throw bad("Invalid Unicode scalar");
          output.writeBytes(
              new String(Character.toChars((int) value)).getBytes(StandardCharsets.UTF_8));
        }
        default -> throw bad("Invalid string escape");
      }
    }
  }

  private static int hex(int c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
  }
}
