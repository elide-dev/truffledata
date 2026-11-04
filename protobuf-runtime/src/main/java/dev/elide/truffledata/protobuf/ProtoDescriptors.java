package dev.elide.truffledata.protobuf;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import dev.elide.truffledata.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jspecify.annotations.Nullable;

/**
 * Bounded FileDescriptorSet compilation without protobuf-java or reflective descriptor objects.
 * Accepts proto2/proto3 descriptor sets with imports included. Editions, extensions and MessageSet
 * require another backend. Custom options are retained neither as assertions nor in the model.
 * Descriptor nesting is capped at 128; maxDepth applies to decoded data.
 */
public final class ProtoDescriptors {
  private ProtoDescriptors() {}

  @TruffleBoundary
  public static ProtoPlan compile(
      @Nullable TruffleLanguage<?> language,
      byte[] descriptors,
      String messageName,
      Limits limits,
      int maxDepth,
      int maxWireBytes) {
    if (descriptors.length > maxWireBytes || maxDepth < 1 || maxDepth > 128)
      throw bad("Descriptor byte/depth budget");
    try {
      return new Compiler(descriptors, limits, maxDepth, maxWireBytes)
          .compile(language, messageName);
    } catch (ArithmeticException invalid) {
      throw bad("Descriptor integer overflow");
    }
  }

  private static ContractViolation bad(String reason) {
    return new ContractViolation(reason);
  }

  private static String identifier(String value) {
    if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) throw bad("Invalid descriptor identifier");
    return value;
  }

  private record Slot(int number, int wire, int start, int end, long scalar) {}

  private record FileInfo(Node node, String name, String syntax, List<String> dependencies) {}

  private record TypeInfo(Node node, FileInfo file, String name) {}

  private static final class Compiler {
    final byte[] bytes;
    final Limits limits;
    final int depth;
    final int wireLimit;
    long cells;
    long stringBytes;
    final Map<String, FileInfo> files = new LinkedHashMap<>();
    final Map<String, TypeInfo> messages = new LinkedHashMap<>();
    final Map<String, TypeInfo> enums = new LinkedHashMap<>();
    final Map<String, ProtoPlan.EnumType> enumPlans = new HashMap<>();
    final Map<String, Long> enumDefaults = new HashMap<>();

    Compiler(byte[] bytes, Limits limits, int depth, int wireLimit) {
      this.bytes = bytes;
      this.limits = limits;
      this.depth = depth;
      this.wireLimit = wireLimit;
    }

    void cell() {
      if (++cells > limits.maxCells()) throw bad("Descriptor cell budget");
    }

    String concatenate(String scope, String name) {
      if (scope.isEmpty()) return name;
      long charge = 2L * ((long) scope.length() + 1 + name.length());
      if (charge > limits.maxStringBytes() - stringBytes)
        throw bad("Qualified descriptor name budget");
      stringBytes += charge;
      return scope + "." + name;
    }

    ProtoPlan compile(@Nullable TruffleLanguage<?> language, String name) {
      var root = new Node(this, 0, bytes.length, 1);
      for (var slot : root.slots(1, 2)) {
        if (files.size() >= limits.maxColumns()) throw bad("Descriptor file budget");
        Node file = root.child(slot);
        String filename = file.text(1, "");
        if (filename.isEmpty() || files.containsKey(filename))
          throw bad("Duplicate/empty descriptor file");
        String syntax = file.text(12, "proto2");
        if (syntax.isEmpty()) syntax = "proto2";
        if (!syntax.equals("proto2") && !syntax.equals("proto3"))
          throw bad("Unsupported protobuf syntax/edition");
        files.put(filename, new FileInfo(file, filename, syntax, file.texts(3)));
      }
      Set<String> built = new HashSet<>();
      while (built.size() < files.size()) {
        boolean progress = false;
        for (var file : files.values()) {
          if (built.contains(file.name()) || !built.containsAll(file.dependencies())) continue;
          built.add(file.name());
          progress = true;
        }
        if (!progress) throw bad("Missing or cyclic descriptor imports");
      }
      for (var file : files.values()) {
        String scope = file.node().text(2, "");
        if (!scope.isEmpty()) for (String component : scope.split("\\.", -1)) identifier(component);
        for (var slot : file.node().slots(4, 2)) register(file.node().child(slot), file, scope);
        for (var slot : file.node().slots(5, 2)) registerEnum(file.node().child(slot), file, scope);
        for (int index : file.node().integers(10))
          if (index < 0 || index >= file.dependencies().size())
            throw bad("Invalid public import index");
        for (int index : file.node().integers(11))
          if (index < 0 || index >= file.dependencies().size())
            throw bad("Invalid weak import index");
      }
      if (!messages.containsKey(name)) throw bad("Unknown protobuf message: " + name);
      List<ProtoPlan.Type> definitions = new ArrayList<>();
      Set<String> visited = new HashSet<>();
      Deque<String> pending = new ArrayDeque<>();
      pending.add(name);
      while (!pending.isEmpty()) {
        String next = pending.removeFirst();
        if (!visited.add(next)) continue;
        if (visited.size() > limits.maxColumns()) throw bad("Descriptor type budget");
        TypeInfo info = Objects.requireNonNull(messages.get(next));
        Node node = info.node();
        if (node.has(5, 2) || node.has(6, 2) || node.message(7).number(1, 0) != 0)
          throw bad("Extensions/MessageSet require an explicit backend");
        List<ProtoPlan.FieldPlan> fields = new ArrayList<>();
        var fieldSlots = info.node().slots(2, 2);
        int oneofs = info.node().slots(8, 2).size();
        if (fieldSlots.size() > limits.maxColumns() || oneofs > limits.maxColumns())
          throw bad("Descriptor field/oneof budget");
        Set<String> oneofNames = new HashSet<>();
        for (var slot : info.node().slots(8, 2))
          if (!oneofNames.add(identifier(info.node().child(slot).text(1, ""))))
            throw bad("Duplicate oneof name");
        for (var slot : fieldSlots) {
          Node field = info.node().child(slot);
          String fieldName = identifier(field.text(1, ""));
          int number = Math.toIntExact(field.number(3, 0));
          int type = Math.toIntExact(field.number(5, 0));
          int label = Math.toIntExact(field.number(4, 1));
          if (type < 1 || type > 18 || label < 1 || label > 3)
            throw bad("Invalid descriptor kind/label");
          var kind = ProtoPlan.Kind.values()[type - 1];
          boolean repeated = label == 3, required = label == 2;
          boolean proto3 = info.file().syntax().equals("proto3");
          if (required && proto3) throw bad("Required proto3 field");
          int oneof = field.has(9, 0) ? Math.toIntExact(field.number(9, 0)) : -1;
          if (oneof < -1 || oneof >= oneofs || (oneof >= 0 && label != 1))
            throw bad("Invalid oneof membership");
          boolean optional = field.number(17, 0) != 0;
          if (optional && (!proto3 || label != 1 || oneof < 0))
            throw bad("Invalid proto3 optional field");
          String reference = field.text(6, "");
          ProtoPlan.EnumType enumeration = null;
          String typeName = "";
          if (kind.message() || kind == ProtoPlan.Kind.ENUM) {
            String resolved = resolve(reference, info.name(), kind.message());
            TypeInfo target =
                Objects.requireNonNull((kind.message() ? messages : enums).get(resolved));
            if (!visible(info.file(), target.file().name()))
              throw bad("Type is not imported: " + resolved);
            if (kind.message()) {
              typeName = resolved;
              pending.add(resolved);
            } else enumeration = enumeration(target);
          } else if (!reference.isEmpty()) throw bad("Scalar field has a type reference");
          boolean map =
              repeated
                  && kind == ProtoPlan.Kind.MESSAGE
                  && mapEntry(Objects.requireNonNull(messages.get(typeName)).node());
          boolean presence = !repeated && (!proto3 || kind.message() || oneof >= 0 || optional);
          Node options = field.message(8);
          boolean packed =
              options.has(2, 0) ? options.number(2, 0) != 0 : proto3 && repeated && kind.packable();
          Object fallback = scalarDefault(kind);
          if (enumeration != null && !repeated)
            fallback = Objects.requireNonNull(enumDefaults.get(enumeration.name()));
          if (field.has(7, 2)) {
            if (proto3 || repeated || kind.message()) throw bad("Invalid explicit default");
            fallback = defaultValue(kind, field.text(7, ""), enumeration, fallback);
          }
          for (String reserved : info.node().texts(10))
            if (reserved.equals(fieldName)) throw bad("Reserved field name");
          for (var reserved : info.node().slots(9, 2)) {
            Node range = info.node().child(reserved);
            if (number >= range.number(1, 0) && number < range.number(2, 0))
              throw bad("Reserved field number");
          }
          var definition =
              field.has(10, 2)
                  ? new ProtoPlan.FieldPlan(
                      fieldName,
                      number,
                      kind,
                      repeated,
                      map,
                      presence,
                      required,
                      packed,
                      oneof,
                      typeName,
                      enumeration,
                      fallback,
                      field.text(10, ""))
                  : new ProtoPlan.FieldPlan(
                      fieldName,
                      number,
                      kind,
                      repeated,
                      map,
                      presence,
                      required,
                      packed,
                      oneof,
                      typeName,
                      enumeration,
                      fallback);
          fields.add(definition);
        }
        definitions.add(new ProtoPlan.Type(info.name(), mapEntry(info.node()), fields));
      }
      return new ProtoPlan.Model(language, definitions, limits, depth, wireLimit).type(name);
    }

    boolean visible(FileInfo source, String target) {
      if (source.name().equals(target)) return true;
      Set<String> visited = new HashSet<>();
      Deque<String> pending = new ArrayDeque<>(source.dependencies());
      while (!pending.isEmpty()) {
        String name = pending.removeFirst();
        if (!visited.add(name)) continue;
        if (name.equals(target)) return true;
        var file = Objects.requireNonNull(files.get(name));
        for (int index : file.node().integers(10)) pending.add(file.dependencies().get(index));
      }
      return false;
    }

    void register(Node node, FileInfo file, String scope) {
      String name = concatenate(scope, identifier(node.text(1, "")));
      if (messages.containsKey(name) || enums.containsKey(name))
        throw bad("Duplicate descriptor type");
      messages.put(name, new TypeInfo(node, file, name));
      for (var slot : node.slots(3, 2)) register(node.child(slot), file, name);
      for (var slot : node.slots(4, 2)) registerEnum(node.child(slot), file, name);
    }

    void registerEnum(Node node, FileInfo file, String scope) {
      String name = concatenate(scope, identifier(node.text(1, "")));
      if (messages.containsKey(name) || enums.containsKey(name))
        throw bad("Duplicate descriptor type");
      enums.put(name, new TypeInfo(node, file, name));
    }

    boolean mapEntry(Node node) {
      return node.message(7).number(7, 0) != 0;
    }

    String resolve(String name, String scope, boolean message) {
      var symbols = message ? messages : enums;
      if (name.startsWith(".")) {
        String full = name.substring(1);
        var target = symbols.get(full);
        if (target != null) return target.name();
      } else {
        String prefix = scope;
        while (true) {
          String candidate = concatenate(prefix, name);
          var target = symbols.get(candidate);
          if (target != null) return target.name();
          if (prefix.isEmpty()) break;
          int dot = prefix.lastIndexOf('.');
          prefix = dot < 0 ? "" : prefix.substring(0, dot);
        }
      }
      throw bad("Unknown protobuf type: " + name);
    }

    ProtoPlan.EnumType enumeration(TypeInfo info) {
      var existing = enumPlans.get(info.name());
      if (existing != null) return existing;
      Map<String, Integer> values = new LinkedHashMap<>();
      for (var slot : info.node().slots(2, 2)) {
        Node value = info.node().child(slot);
        String name = identifier(value.text(1, ""));
        int number = (int) value.number(2, 0);
        if (values.put(name, number) != null) throw bad("Duplicate enum name");
      }
      if (values.isEmpty()) throw bad("Empty enum");
      long first = values.values().iterator().next();
      boolean open = info.file().syntax().equals("proto3");
      if (open && first != 0) throw bad("Nonzero proto3 enum default");
      var result = new ProtoPlan.EnumType(info.name(), open, values);
      enumPlans.put(info.name(), result);
      enumDefaults.put(info.name(), first);
      return result;
    }

    Object scalarDefault(ProtoPlan.Kind kind) {
      return switch (kind) {
        case STRING -> "";
        case BYTES -> DataArray.copyOfBytes(new byte[0]);
        case BOOL -> false;
        case FLOAT, DOUBLE -> 0.0;
        case UINT64, FIXED64 -> Values.normalize(java.math.BigInteger.ZERO);
        case MESSAGE, GROUP -> Missing.INSTANCE;
        default -> 0L;
      };
    }

    Object defaultValue(
        ProtoPlan.Kind kind,
        String text,
        ProtoPlan.@Nullable EnumType enumeration,
        Object fallback) {
      if (kind == ProtoPlan.Kind.BOOL && !text.equals("true") && !text.equals("false"))
        throw bad("Invalid boolean default");
      var field =
          new ProtoPlan.FieldPlan(
              "value", 1, kind, false, false, true, false, false, -1, "", enumeration, fallback);
      String literal =
          kind == ProtoPlan.Kind.STRING
              ? quote(text)
              : kind == ProtoPlan.Kind.BYTES ? '"' + text + '"' : text;
      String input = "value: " + literal;
      int inputBudget = Math.multiplyExact(input.length(), 2);
      var model =
          new ProtoPlan.Model(
              null,
              List.of(new ProtoPlan.Type("Default", false, List.of(field))),
              limits,
              1,
              inputBudget);
      return model.type("Default").parseText(input).get(0);
    }

    String quote(String text) {
      StringBuilder result = new StringBuilder("\"");
      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        switch (c) {
          case '\\' -> result.append("\\\\");
          case '"' -> result.append("\\\"");
          case '\n' -> result.append("\\n");
          case '\r' -> result.append("\\r");
          case '\t' -> result.append("\\t");
          default -> result.append(c);
        }
      }
      return result.append('"').toString();
    }
  }

  private static final class Node {
    final Compiler compiler;
    final int depth;
    final List<Slot> fields = new ArrayList<>();
    final Map<Slot, Node> children = new HashMap<>();
    final Map<Slot, String> strings = new HashMap<>();
    final Map<Integer, List<Integer>> integerLists = new HashMap<>();
    final Map<Integer, Node> messages = new HashMap<>();

    Node(Compiler compiler, int start, int end, int depth) {
      this.compiler = compiler;
      this.depth = depth;
      if (depth > 128) throw bad("Descriptor nesting budget");
      var cursor = new Cursor(compiler, start, end);
      while (cursor.position < end) {
        int tag = cursor.tag();
        int wire = tag & 7;
        long value = 0;
        int from = cursor.position;
        if (wire == 0) value = cursor.varint();
        else if (wire == 2) {
          int length = cursor.length();
          from = cursor.position;
          cursor.position += length;
        } else cursor.skip(tag, depth);
        compiler.cell();
        fields.add(new Slot(tag >>> 3, wire, from, cursor.position, value));
      }
    }

    List<Slot> slots(int number, int wire) {
      return fields.stream().filter(s -> s.number() == number && s.wire() == wire).toList();
    }

    boolean has(int number, int wire) {
      return fields.stream().anyMatch(s -> s.number() == number && s.wire() == wire);
    }

    long number(int number, long fallback) {
      long value = fallback;
      for (var field : fields)
        if (field.number() == number && field.wire() == 0) value = field.scalar();
      return value;
    }

    Node child(Slot slot) {
      return children.computeIfAbsent(slot, s -> new Node(compiler, s.start(), s.end(), depth + 1));
    }

    Node message(int number) {
      return messages.computeIfAbsent(
          number,
          n -> {
            Node result = new Node(compiler, 0, 0, depth + 1);
            for (var slot : slots(n, 2)) result.fields.addAll(child(slot).fields);
            return result;
          });
    }

    String text(int number, String fallback) {
      if (!has(number, 2)) return fallback;
      var found = slots(number, 2);
      return text(found.getLast());
    }

    List<String> texts(int number) {
      return slots(number, 2).stream().map(this::text).toList();
    }

    String text(Slot slot) {
      return strings.computeIfAbsent(
          slot,
          s -> {
            int length = s.end() - s.start();
            long charge = 2L * GeneratedInput.utf16Length(compiler.bytes, s.start(), length);
            if (charge > compiler.limits.maxStringBytes() - compiler.stringBytes)
              throw bad("Descriptor string budget");
            compiler.stringBytes += charge;
            return new String(compiler.bytes, s.start(), length, StandardCharsets.UTF_8);
          });
    }

    List<Integer> integers(int number) {
      return integerLists.computeIfAbsent(number, this::readIntegers);
    }

    List<Integer> readIntegers(int number) {
      List<Integer> result = new ArrayList<>();
      for (var field : fields) {
        if (field.number() != number) continue;
        if (field.wire() == 0) result.add((int) field.scalar());
        else if (field.wire() == 2) {
          var cursor = new Cursor(compiler, field.start(), field.end());
          while (cursor.position < field.end()) {
            compiler.cell();
            result.add((int) cursor.varint());
          }
        }
      }
      return result;
    }
  }

  private static final class Cursor {
    final Compiler compiler;
    final int end;
    int position;

    Cursor(Compiler compiler, int start, int end) {
      this.compiler = compiler;
      this.position = start;
      this.end = end;
    }

    long varint() {
      long value = 0;
      for (int shift = 0; shift < 64; shift += 7) {
        if (position >= end) throw bad("Truncated descriptor varint");
        int next = compiler.bytes[position++] & 255;
        if (shift == 63 && (next & 254) != 0) throw bad("Descriptor varint overflow");
        value |= (long) (next & 127) << shift;
        if ((next & 128) == 0) return value;
      }
      throw bad("Malformed descriptor varint");
    }

    int tag() {
      long value = varint();
      if (value < 0 || value > 0xffffffffL || (value >>> 3) == 0 || (value & 7) > 5)
        throw bad("Invalid descriptor tag");
      return (int) value;
    }

    int length() {
      long count = varint();
      if (count < 0 || count > end - position) throw bad("Truncated descriptor field");
      return (int) count;
    }

    void advance(int count) {
      if (count > end - position) throw bad("Truncated descriptor fixed field");
      position += count;
    }

    void skip(int tag, int depth) {
      switch (tag & 7) {
        case 0 -> varint();
        case 1 -> advance(8);
        case 2 -> {
          int length = length();
          advance(length);
        }
        case 3 -> {
          if (depth >= 128) throw bad("Descriptor group depth");
          while (position < end) {
            int child = tag();
            compiler.cell();
            if ((child & 7) == 4) {
              if ((child >>> 3) != (tag >>> 3)) throw bad("Mismatched descriptor group");
              return;
            }
            skip(child, depth + 1);
          }
          throw bad("Unterminated descriptor group");
        }
        case 5 -> advance(4);
        default -> throw bad("Unexpected descriptor end group");
      }
    }
  }
}
