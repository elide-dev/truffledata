package dev.elide.truffledata.bebop;

import static dev.elide.truffledata.bebop.BebopException.malformed;
import static dev.elide.truffledata.bebop.BebopException.unsupported;
import static dev.elide.truffledata.bebop.BebopType.Scalar.*;

import dev.elide.truffledata.bebop.BebopSchema.Branch;
import dev.elide.truffledata.bebop.BebopSchema.Definition;
import dev.elide.truffledata.bebop.BebopSchema.EnumDef;
import dev.elide.truffledata.bebop.BebopSchema.Field;
import dev.elide.truffledata.bebop.BebopSchema.Member;
import dev.elide.truffledata.bebop.BebopSchema.MessageDef;
import dev.elide.truffledata.bebop.BebopSchema.StructDef;
import dev.elide.truffledata.bebop.BebopSchema.TaggedField;
import dev.elide.truffledata.bebop.BebopSchema.UnionDef;
import dev.elide.truffledata.bebop.BebopType.Scalar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Loads the {@code DescriptorSet} that {@code bebopc} compiles from {@code .bop} schemas into a
 * {@link BebopSchema}.
 *
 * <p>A descriptor set is an ordinary Bebop value of upstream's {@code bebop/descriptor.bop} (tag
 * {@code 2026.0.0-alpha.6}) in the indexed message layout, so it is decoded by {@link BebopCodec}
 * under the general profile with {@link #SCHEMA}, then converted. Every definition of every schema
 * in the set, nested ones included, is named by its fully-qualified name, and every reference is
 * to a fully-qualified name. Services, constants and decorator definitions are not data types and
 * are left out.
 *
 * <p>Sets: upstream has no set type, and no convention marks an array as a set. Every array is a
 * sequence unless {@link Options#setDecorator} names a decorator; a struct or message field that
 * carries it must be a dynamic array, which becomes a set. {@code bebopc} records a decorator
 * usage by its spelling at the usage site, not by the definition's fully-qualified name, so the
 * option is compared with that spelling exactly.
 *
 * <p>Failures: bytes that are not a descriptor set fail as the codec reports them ({@code
 * MALFORMED} or {@code LIMIT}); a descriptor that contradicts itself (a missing name or type,
 * duplicate names, tags or discriminators, a reference to nothing or to a non-type, an enum member
 * outside its base type) is {@code MALFORMED}; anything the codec cannot represent (an unknown
 * definition or type kind, another edition, a non-integer enum base, an unsupported map key, a set
 * decorator on a field that is not a dynamic array) is {@code UNSUPPORTED}. Nothing is dropped
 * silently except the non-data definitions above.
 */
public final class BebopDescriptors {
  private BebopDescriptors() {}

  /** Load options: decode limits, and the decorator that marks an array field as a set. */
  public record Options(BebopLimits limits, @Nullable String setDecorator) {
    public static final Options DEFAULT = new Options(BebopLimits.DEFAULT, null);

    public Options {
      Objects.requireNonNull(limits);
      if (setDecorator != null && setDecorator.isEmpty())
        throw new IllegalArgumentException("Set decorator name");
    }

    public Options withLimits(BebopLimits limits) {
      return new Options(limits, setDecorator);
    }

    /** Fields carrying a decorator spelled exactly {@code name} are sets. */
    public Options withSetDecorator(String name) {
      return new Options(limits, Objects.requireNonNull(name));
    }
  }

  /** Loads a descriptor set with the default limits and no set decorator. */
  public static BebopSchema load(byte[] descriptorSet) {
    return load(descriptorSet, Options.DEFAULT);
  }

  public static BebopSchema load(byte[] descriptorSet, Options options) {
    BebopCodec codec = BebopCodec.compile(SCHEMA, ROOT, BebopProfile.GENERAL, options.limits());
    return new Loader(options.setDecorator()).load((BebopRecord) codec.decode(descriptorSet));
  }

  // ---------------------------------------------------------------------------------------------
  // The descriptor schema: upstream's bebop/descriptor.bop, field for field. Every message's tags
  // run from 1 without gaps, so a field's ordinal is its tag minus one.

  static final BebopType ROOT = BebopType.ref("bebop.DescriptorSet");

  private static final String[] TYPE_KINDS = {
    "UNKNOWN", "BOOL", "BYTE", "INT8", "INT16", "UINT16", "INT32", "UINT32", "INT64", "UINT64",
    "INT128", "UINT128", "FLOAT16", "FLOAT32", "FLOAT64", "BFLOAT16", "STRING", "UUID", "TIMESTAMP",
    "DURATION", "ARRAY", "FIXED_ARRAY", "MAP", "DEFINED"
  };

  /** Scalars by {@code TypeKind} value, 1 to 19. */
  private static final Scalar[] SCALARS = {
    BOOL, BYTE, INT8, INT16, UINT16, INT32, UINT32, INT64, UINT64, INT128, UINT128, FLOAT16,
    FLOAT32, FLOAT64, BFLOAT16, STRING, UUID, TIMESTAMP, DURATION
  };

  private static final int ARRAY = 20;
  private static final int FIXED_ARRAY = 21;
  private static final int MAP = 22;
  private static final int DEFINED = 23;

  private static final String[] DEFINITION_KINDS = {
    "UNKNOWN", "ENUM", "STRUCT", "MESSAGE", "UNION", "SERVICE", "CONST", "DECORATOR"
  };

  private static final int EDITION_2026 = 1000;

  /** Upstream's descriptor schema, with definitions named by fully-qualified name. */
  static final BebopSchema SCHEMA = descriptorSchema();

  private static BebopSchema descriptorSchema() {
    BebopType typeKind = BebopType.ref("bebop.TypeKind");
    BebopType type = BebopType.ref("bebop.TypeDescriptor");
    BebopType literal = BebopType.ref("bebop.LiteralValue");
    BebopType usages = BebopType.array(BebopType.ref("bebop.DecoratorUsage"));
    return BebopSchema.builder()
        .add(members("bebop.TypeKind", BYTE, false, TYPE_KINDS))
        .add(members("bebop.DefinitionKind", BYTE, false, DEFINITION_KINDS))
        .add(
            members(
                "bebop.MethodType",
                BYTE,
                false,
                "UNKNOWN",
                "UNARY",
                "SERVER_STREAM",
                "CLIENT_STREAM",
                "DUPLEX_STREAM"))
        .add(members("bebop.Visibility", BYTE, false, "DEFAULT", "EXPORT", "LOCAL"))
        .add(
            members(
                "bebop.LiteralKind",
                BYTE,
                false,
                "UNKNOWN",
                "BOOL",
                "INT",
                "FLOAT",
                "STRING",
                "UUID",
                "BYTES",
                "TIMESTAMP",
                "DURATION"))
        .flags(
            "bebop.DecoratorTarget",
            BYTE,
            new Member("NONE", 0),
            new Member("ENUM", 1),
            new Member("STRUCT", 2),
            new Member("MESSAGE", 4),
            new Member("UNION", 8),
            new Member("FIELD", 16),
            new Member("SERVICE", 32),
            new Member("METHOD", 64),
            new Member("BRANCH", 128),
            new Member("ALL", 255))
        .enumeration(
            "bebop.Edition",
            INT32,
            new Member("UNKNOWN", 0),
            new Member("EDITION_2026", EDITION_2026),
            new Member("MAX", 0x7FFFFFFF))
        .message(
            "bebop.TypeDescriptor",
            tagged(1, "kind", typeKind),
            tagged(2, "array_element", type),
            tagged(3, "fixed_array_element", type),
            tagged(4, "fixed_array_size", UINT32),
            tagged(5, "map_key", type),
            tagged(6, "map_value", type),
            tagged(7, "defined_fqn", STRING))
        .message(
            "bebop.LiteralValue",
            tagged(1, "kind", BebopType.ref("bebop.LiteralKind")),
            tagged(2, "bool_value", BOOL),
            tagged(3, "int_value", INT64),
            tagged(4, "float_value", FLOAT64),
            tagged(5, "string_value", STRING),
            tagged(6, "uuid_value", UUID),
            tagged(7, "raw_value", STRING),
            tagged(8, "bytes_value", BebopType.array(BYTE)),
            tagged(9, "timestamp_value", TIMESTAMP),
            tagged(10, "duration_value", DURATION))
        .struct("bebop.DecoratorArg", new Field("name", STRING), new Field("value", literal))
        .message(
            "bebop.DecoratorUsage",
            tagged(1, "fqn", STRING),
            tagged(2, "args", BebopType.array(BebopType.ref("bebop.DecoratorArg"))),
            tagged(3, "export_data", BebopType.map(STRING, literal)))
        .message(
            "bebop.FieldDescriptor",
            tagged(1, "name", STRING),
            tagged(2, "documentation", STRING),
            tagged(3, "type", type),
            tagged(4, "index", UINT32),
            tagged(5, "decorators", usages))
        .message(
            "bebop.EnumMemberDescriptor",
            tagged(1, "name", STRING),
            tagged(2, "documentation", STRING),
            tagged(3, "value", UINT64),
            tagged(4, "decorators", usages),
            tagged(5, "value_expr", STRING))
        .message(
            "bebop.UnionBranchDescriptor",
            tagged(1, "discriminator", BYTE),
            tagged(2, "documentation", STRING),
            tagged(3, "inline_fqn", STRING),
            tagged(4, "type_ref_fqn", STRING),
            tagged(5, "name", STRING),
            tagged(6, "decorators", usages))
        .message(
            "bebop.MethodDescriptor",
            tagged(1, "name", STRING),
            tagged(2, "documentation", STRING),
            tagged(3, "request_type", type),
            tagged(4, "response_type", type),
            tagged(5, "method_type", BebopType.ref("bebop.MethodType")),
            tagged(6, "id", UINT32),
            tagged(7, "decorators", usages))
        .message(
            "bebop.EnumDef",
            tagged(1, "base_type", typeKind),
            tagged(2, "members", BebopType.array(BebopType.ref("bebop.EnumMemberDescriptor"))),
            tagged(3, "is_flags", BOOL))
        .message(
            "bebop.StructDef",
            tagged(1, "fields", BebopType.array(BebopType.ref("bebop.FieldDescriptor"))),
            tagged(2, "is_mutable", BOOL),
            tagged(3, "fixed_size", UINT32))
        .message(
            "bebop.MessageDef",
            tagged(1, "fields", BebopType.array(BebopType.ref("bebop.FieldDescriptor"))))
        .message(
            "bebop.UnionDef",
            tagged(1, "branches", BebopType.array(BebopType.ref("bebop.UnionBranchDescriptor"))))
        .message(
            "bebop.ServiceDef",
            tagged(1, "methods", BebopType.array(BebopType.ref("bebop.MethodDescriptor"))))
        .message("bebop.ConstDef", tagged(1, "type", type), tagged(2, "value", literal))
        .message(
            "bebop.DecoratorParamDef",
            tagged(1, "name", STRING),
            tagged(2, "description", STRING),
            tagged(3, "type", typeKind),
            tagged(4, "required", BOOL),
            tagged(5, "default_value", literal),
            tagged(6, "allowed_values", BebopType.array(literal)))
        .message(
            "bebop.DecoratorDef",
            tagged(1, "targets", BebopType.ref("bebop.DecoratorTarget")),
            tagged(2, "allow_multiple", BOOL),
            tagged(3, "params", BebopType.array(BebopType.ref("bebop.DecoratorParamDef"))),
            tagged(4, "validate_source", STRING),
            tagged(5, "export_source", STRING))
        .message(
            "bebop.DefinitionDescriptor",
            tagged(1, "kind", BebopType.ref("bebop.DefinitionKind")),
            tagged(2, "name", STRING),
            tagged(3, "fqn", STRING),
            tagged(4, "documentation", STRING),
            tagged(5, "visibility", BebopType.ref("bebop.Visibility")),
            tagged(6, "decorators", usages),
            tagged(7, "nested", BebopType.array(BebopType.ref("bebop.DefinitionDescriptor"))),
            tagged(8, "enum_def", BebopType.ref("bebop.EnumDef")),
            tagged(9, "struct_def", BebopType.ref("bebop.StructDef")),
            tagged(10, "message_def", BebopType.ref("bebop.MessageDef")),
            tagged(11, "union_def", BebopType.ref("bebop.UnionDef")),
            tagged(12, "service_def", BebopType.ref("bebop.ServiceDef")),
            tagged(13, "const_def", BebopType.ref("bebop.ConstDef")),
            tagged(14, "decorator_def", BebopType.ref("bebop.DecoratorDef")))
        .message(
            "bebop.Location",
            tagged(1, "path", BebopType.array(INT32)),
            tagged(2, "span", BebopType.fixed(INT32, 4)),
            tagged(3, "leading_comments", STRING),
            tagged(4, "trailing_comments", STRING),
            tagged(5, "detached_comments", BebopType.array(STRING)))
        .message(
            "bebop.SourceCodeInfo",
            tagged(1, "locations", BebopType.array(BebopType.ref("bebop.Location"))))
        .message(
            "bebop.SchemaDescriptor",
            tagged(1, "path", STRING),
            tagged(2, "package", STRING),
            tagged(3, "edition", BebopType.ref("bebop.Edition")),
            tagged(4, "imports", BebopType.array(STRING)),
            tagged(5, "definitions", BebopType.array(BebopType.ref("bebop.DefinitionDescriptor"))),
            tagged(6, "source_code_info", BebopType.ref("bebop.SourceCodeInfo")))
        .message(
            "bebop.DescriptorSet",
            tagged(1, "schemas", BebopType.array(BebopType.ref("bebop.SchemaDescriptor"))))
        .build();
  }

  private static EnumDef members(String name, Scalar base, boolean flags, String... names) {
    List<Member> members = new ArrayList<>();
    for (int i = 0; i < names.length; i++) members.add(new Member(names[i], i));
    return new EnumDef(name, base, members, flags);
  }

  private static TaggedField tagged(int tag, String name, BebopType type) {
    return new TaggedField(tag, name, type);
  }

  // ---------------------------------------------------------------------------------------------

  /** Converts one decoded descriptor set. */
  private static final class Loader {
    private final @Nullable String setDecorator;
    private final Map<String, Definition> data = new LinkedHashMap<>();
    private final Map<String, String> skipped = new HashMap<>();

    Loader(@Nullable String setDecorator) {
      this.setDecorator = setDecorator;
    }

    BebopSchema load(BebopRecord set) {
      for (Object schema : list(at(set, 1))) schema((BebopRecord) schema);
      BebopSchema.Builder builder = BebopSchema.builder();
      for (Definition def : data.values()) {
        check(def);
        builder.add(def);
      }
      return builder.build();
    }

    private void schema(BebopRecord schema) {
      Object edition = at(schema, 3);
      if (edition != null && (Integer) edition != EDITION_2026)
        throw unsupported("Edition " + edition + " of " + at(schema, 1));
      for (Object def : list(at(schema, 5))) definition((BebopRecord) def);
    }

    private void definition(BebopRecord def) {
      String fqn = text(at(def, 3), "A definition has no fully-qualified name");
      Object kindValue = at(def, 1);
      if (kindValue == null || (Integer) kindValue == 0)
        throw malformed("Definition " + fqn + " has no kind");
      int kind = (Integer) kindValue;
      if (kind >= DEFINITION_KINDS.length)
        throw unsupported("Definition " + fqn + " has the unknown kind " + kind);
      if (data.containsKey(fqn) || skipped.containsKey(fqn))
        throw malformed("Duplicate definition " + fqn);
      for (int k = 1; k < DEFINITION_KINDS.length; k++)
        if ((at(def, 7 + k) != null) != (k == kind))
          throw malformed("Definition " + fqn + " does not have exactly the body of its kind");
      BebopRecord body = (BebopRecord) Objects.requireNonNull(at(def, 7 + kind));
      switch (kind) {
        case 1 -> data.put(fqn, enumeration(fqn, body));
        case 2 -> data.put(fqn, new StructDef(fqn, structFields(fqn, body)));
        case 3 -> data.put(fqn, new MessageDef(fqn, messageFields(fqn, body)));
        case 4 -> data.put(fqn, union(fqn, body));
        default -> skipped.put(fqn, DEFINITION_KINDS[kind].toLowerCase(Locale.ROOT));
      }
      for (Object nested : list(at(def, 7))) definition((BebopRecord) nested);
    }

    private static EnumDef enumeration(String fqn, BebopRecord body) {
      Object baseValue = at(body, 1);
      if (baseValue == null) throw malformed("Enum " + fqn + " has no base type");
      int kind = (Integer) baseValue;
      Scalar base = kind >= 1 && kind <= SCALARS.length ? SCALARS[kind - 1] : null;
      if (base == null || !base.integer64())
        throw unsupported("Enum " + fqn + " has the base type " + kindName(kind));
      int bits = base.width() * 8;
      List<Member> members = new ArrayList<>();
      Set<String> names = new HashSet<>();
      for (Object m : list(at(body, 2))) {
        BebopRecord member = (BebopRecord) m;
        String name = text(at(member, 1), "A member of enum " + fqn + " has no name");
        if (!names.add(name)) throw malformed("Duplicate member " + name + " in " + fqn);
        Object raw = at(member, 3);
        if (raw == null) throw malformed("Member " + name + " of " + fqn + " has no value");
        // Stored as uint64: sign-extended for signed bases, so the value must be exactly that.
        long value = (Long) raw;
        long fitted =
            switch (base) {
              case INT8, INT16, INT32 -> value << (64 - bits) >> (64 - bits);
              case BYTE, UINT16, UINT32 -> value & ((1L << bits) - 1);
              default -> value;
            };
        if (fitted != value)
          throw malformed("Member " + name + " of " + fqn + " does not fit " + base);
        members.add(new Member(name, value));
      }
      return new EnumDef(fqn, base, members, Boolean.TRUE.equals(at(body, 3)));
    }

    /** A struct or message field as the descriptor states it; {@code tag} is 0 for structs. */
    private record Parsed(String name, BebopType type, long tag) {}

    private List<Field> structFields(String fqn, BebopRecord body) {
      List<Field> out = new ArrayList<>();
      for (Parsed f : fields(fqn, body)) {
        if (f.tag() != 0)
          throw malformed("Struct field " + f.name() + " of " + fqn + " has a tag");
        out.add(new Field(f.name(), f.type()));
      }
      return out;
    }

    private List<TaggedField> messageFields(String fqn, BebopRecord body) {
      List<TaggedField> out = new ArrayList<>();
      Set<Long> tags = new HashSet<>();
      for (Parsed f : fields(fqn, body)) {
        if (f.tag() < 1 || f.tag() > 255)
          throw malformed("Field " + f.name() + " of " + fqn + " has the tag " + f.tag());
        if (!tags.add(f.tag())) throw malformed("Duplicate tag " + f.tag() + " in " + fqn);
        out.add(new TaggedField((int) f.tag(), f.name(), f.type()));
      }
      return out;
    }

    private List<Parsed> fields(String fqn, BebopRecord body) {
      List<Parsed> out = new ArrayList<>();
      Set<String> names = new HashSet<>();
      for (Object f : list(at(body, 1))) {
        BebopRecord field = (BebopRecord) f;
        String name = text(at(field, 1), "A field of " + fqn + " has no name");
        if (!names.add(name)) throw malformed("Duplicate field " + name + " in " + fqn);
        Object typeValue = at(field, 3);
        if (typeValue == null) throw malformed("Field " + name + " of " + fqn + " has no type");
        BebopType type = type((BebopRecord) typeValue);
        if (setDecorator != null && decorated(field, setDecorator)) {
          if (!(type instanceof BebopType.Array array))
            throw unsupported(
                "Field " + name + " of " + fqn + " is marked as a set but is not a dynamic array");
          type = BebopType.set(array.element());
        }
        Object index = at(field, 4);
        out.add(new Parsed(name, type, index == null ? 0 : (Long) index));
      }
      return out;
    }

    private static boolean decorated(BebopRecord field, String decorator) {
      for (Object u : list(at(field, 5)))
        if (decorator.equals(at((BebopRecord) u, 1))) return true;
      return false;
    }

    private static UnionDef union(String fqn, BebopRecord body) {
      List<Branch> branches = new ArrayList<>();
      Set<Integer> discriminators = new HashSet<>();
      Set<String> names = new HashSet<>();
      for (Object b : list(at(body, 1))) {
        BebopRecord branch = (BebopRecord) b;
        Object discriminator = at(branch, 1);
        if (discriminator == null) throw malformed("A branch of " + fqn + " has no discriminator");
        if (!discriminators.add((Integer) discriminator))
          throw malformed("Duplicate discriminator " + discriminator + " in " + fqn);
        Object inline = at(branch, 3);
        Object target = at(branch, 4);
        String branchName = "Branch " + discriminator + " of " + fqn;
        if ((inline == null) == (target == null))
          throw malformed(branchName + " is not exactly one of inline or a reference");
        String type = text(inline != null ? inline : target, branchName + " has no type");
        // As upstream's accessor: an inline branch is named by its definition's last component.
        Object explicit = at(branch, 5);
        String name =
            explicit != null
                ? text(explicit, branchName + " has no name")
                : type.substring(type.lastIndexOf('.') + 1);
        if (name.isEmpty() || !names.add(name))
          throw malformed(branchName + " has a missing or duplicate name");
        branches.add(new Branch((Integer) discriminator, name, BebopType.ref(type)));
      }
      return new UnionDef(fqn, branches);
    }

    private static BebopType type(BebopRecord type) {
      Object kindValue = at(type, 1);
      if (kindValue == null || (Integer) kindValue == 0) throw malformed("A type without a kind");
      int kind = (Integer) kindValue;
      if (kind <= SCALARS.length) return SCALARS[kind - 1];
      return switch (kind) {
        case ARRAY -> BebopType.array(type(part(type, 2, "array element")));
        case FIXED_ARRAY -> {
          Object size = at(type, 4);
          long length = size == null ? 0 : (Long) size;
          if (length < 1 || length > 65535)
            throw malformed("Fixed array length " + length + " is out of range");
          yield BebopType.fixed(type(part(type, 3, "fixed array element")), (int) length);
        }
        case MAP -> BebopType.map(type(part(type, 5, "map key")), type(part(type, 6, "map value")));
        case DEFINED -> BebopType.ref(text(at(type, 7), "A defined type has no name"));
        default -> throw unsupported("Unknown type kind " + kind);
      };
    }

    private static BebopRecord part(BebopRecord type, int tag, String what) {
      Object part = at(type, tag);
      if (part == null) throw malformed("A type without its " + what);
      return (BebopRecord) part;
    }

    /** References resolve to data definitions, and map keys are types the codec can key. */
    private void check(Definition def) {
      switch (def) {
        case EnumDef _ -> {}
        case StructDef s -> {
          for (Field f : s.fields()) check(f.type(), s.name());
        }
        case MessageDef m -> {
          for (TaggedField f : m.fields()) check(f.type(), m.name());
        }
        case UnionDef u -> {
          for (Branch b : u.branches()) check(b.type(), u.name());
        }
      }
    }

    private void check(BebopType type, String owner) {
      switch (type) {
        case Scalar _ -> {}
        case BebopType.Ref r -> {
          if (data.containsKey(r.name())) return;
          String other = skipped.get(r.name());
          throw malformed(
              owner
                  + (other == null
                      ? " refers to the unknown type " + r.name()
                      : " refers to the " + other + " " + r.name() + ", which is not a data type"));
        }
        case BebopType.Refined r -> check(r.base(), owner);
        case BebopType.Array a -> check(a.element(), owner);
        case BebopType.FixedArray f -> check(f.element(), owner);
        case BebopType.MapOf m -> {
          check(m.key(), owner);
          check(m.value(), owner);
          boolean keyed =
              m.key() instanceof Scalar s
                      && (s.integer() || s == BOOL || s == STRING || s == UUID)
                  || m.key() instanceof BebopType.Ref r && data.get(r.name()) instanceof EnumDef;
          if (!keyed) throw unsupported(owner + " has a map key the codec cannot key");
        }
      }
    }
  }

  private static @Nullable Object at(BebopRecord record, int tag) {
    return record.get(tag - 1);
  }

  private static List<?> list(@Nullable Object value) {
    return value == null ? List.of() : (List<?>) value;
  }

  private static String text(@Nullable Object value, String what) {
    if (value == null || ((String) value).isEmpty()) throw malformed(what);
    return (String) value;
  }

  private static String kindName(int kind) {
    return kind < TYPE_KINDS.length ? TYPE_KINDS[kind] : Integer.toString(kind);
  }
}
