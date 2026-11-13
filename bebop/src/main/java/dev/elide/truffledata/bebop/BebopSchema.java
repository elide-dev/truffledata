package dev.elide.truffledata.bebop;

import dev.elide.truffledata.bebop.BebopType.Scalar;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/** An immutable set of named definitions with a stable identity. */
public final class BebopSchema {
  /** A named definition. */
  public sealed interface Definition {
    String name();
  }

  /** An enum member. Values of a {@code uint64} enum are the value's bits. */
  public record Member(String name, long value) {}

  /**
   * An enum over an integer base type of at most 64 bits. A {@code flags} enum's values are
   * bitwise combinations of its members; only the general profile supports it, because no strict
   * rule for combinations is defined yet.
   */
  public record EnumDef(String name, Scalar base, List<Member> members, boolean flags)
      implements Definition {
    public EnumDef {
      members = List.copyOf(members);
      if (!base.integer64()) throw new IllegalArgumentException("Enum base type");
    }

    public EnumDef(String name, Scalar base, List<Member> members) {
      this(name, base, members, false);
    }
  }

  /** A struct field. */
  public record Field(String name, BebopType type) {}

  /** A struct: fields in definition order, without tags. */
  public record StructDef(String name, List<Field> fields) implements Definition {
    public StructDef {
      fields = List.copyOf(fields);
    }
  }

  /** A message field with its tag, 1 to 255. */
  public record TaggedField(int tag, String name, BebopType type) {
    public TaggedField {
      if (tag < 1 || tag > 255) throw new IllegalArgumentException("Field tag");
    }
  }

  /** A message: optional tagged fields behind an index. */
  public record MessageDef(String name, List<TaggedField> fields) implements Definition {
    public MessageDef {
      fields = List.copyOf(fields);
    }
  }

  /** A union branch with its discriminator, 0 to 255. */
  public record Branch(int discriminator, String name, BebopType type) {
    public Branch {
      if (discriminator < 0 || discriminator > 255)
        throw new IllegalArgumentException("Discriminator");
    }
  }

  /** A union of branches. */
  public record UnionDef(String name, List<Branch> branches) implements Definition {
    public UnionDef {
      branches = List.copyOf(branches);
    }
  }

  private final Map<String, Definition> definitions;
  private final String fingerprint;

  private BebopSchema(Map<String, Definition> definitions) {
    this.definitions = definitions;
    this.fingerprint = digest(render(definitions));
  }

  public static Builder builder() {
    return new Builder();
  }

  public @Nullable Definition definition(String name) {
    return definitions.get(name);
  }

  /** Every definition, in the order it was added. */
  public List<Definition> definitions() {
    return List.copyOf(definitions.values());
  }

  /** SHA-256 over a canonical rendering of every definition: the schema's exact identity. */
  public String fingerprint() {
    return fingerprint;
  }

  private static String render(Map<String, Definition> definitions) {
    StringBuilder out = new StringBuilder();
    for (Definition def : new TreeMap<>(definitions).values()) {
      switch (def) {
        case EnumDef e -> {
          out.append(e.flags() ? "flags enum " : "enum ")
              .append(quote(e.name()))
              .append(':')
              .append(e.base())
              .append('{');
          for (Member m : e.members())
            out.append(quote(m.name())).append('=').append(m.value()).append(';');
        }
        case StructDef s -> {
          out.append("struct ").append(quote(s.name())).append('{');
          for (Field f : s.fields()) {
            out.append(quote(f.name())).append(':');
            render(out, f.type());
            out.append(';');
          }
        }
        case MessageDef m -> {
          out.append("message ").append(quote(m.name())).append('{');
          for (TaggedField f : m.fields()) {
            out.append(quote(f.name())).append('(').append(f.tag()).append("):");
            render(out, f.type());
            out.append(';');
          }
        }
        case UnionDef u -> {
          out.append("union ").append(quote(u.name())).append('{');
          for (Branch b : u.branches()) {
            out.append(quote(b.name())).append('(').append(b.discriminator()).append("):");
            render(out, b.type());
            out.append(';');
          }
        }
      }
      out.append("}\n");
    }
    return out.toString();
  }

  private static void render(StringBuilder out, BebopType type) {
    switch (type) {
      case Scalar s -> out.append(s);
      case BebopType.Ref r -> out.append('@').append(quote(r.name()));
      case BebopType.Array a -> {
        out.append(a.set() ? "set[" : "array[");
        render(out, a.element());
        out.append(']');
      }
      case BebopType.FixedArray f -> {
        out.append("fixed[");
        render(out, f.element());
        out.append(',').append(f.length()).append(']');
      }
      case BebopType.Refined r -> {
        out.append("refined[");
        render(out, r.base());
        out.append(',').append(r.refinement()).append(']');
      }
      case BebopType.MapOf m -> {
        out.append("map[");
        render(out, m.key());
        out.append(',');
        render(out, m.value());
        out.append(']');
      }
    }
  }

  /** The canonical rendering of a type expression, as it appears in identities. */
  static String render(BebopType type) {
    StringBuilder out = new StringBuilder();
    render(out, type);
    return out.toString();
  }

  /** Length-prefixed, so no name can imitate the rendering's punctuation. */
  private static String quote(String name) {
    return name.length() + "\"" + name;
  }

  static String digest(String text) {
    try {
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(sha.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Builder of a schema. */
  public static final class Builder {
    private final Map<String, Definition> definitions = new LinkedHashMap<>();

    private Builder() {}

    public Builder add(Definition definition) {
      Objects.requireNonNull(definition);
      if (definitions.putIfAbsent(definition.name(), definition) != null)
        throw new IllegalArgumentException("Duplicate definition " + definition.name());
      return this;
    }

    public Builder enumeration(String name, Scalar base, Member... members) {
      return add(new EnumDef(name, base, List.of(members)));
    }

    /** A flags enum: see {@link EnumDef}. */
    public Builder flags(String name, Scalar base, Member... members) {
      return add(new EnumDef(name, base, List.of(members), true));
    }

    public Builder struct(String name, Field... fields) {
      return add(new StructDef(name, List.of(fields)));
    }

    public Builder message(String name, TaggedField... fields) {
      return add(new MessageDef(name, List.of(fields)));
    }

    public Builder union(String name, Branch... branches) {
      return add(new UnionDef(name, List.of(branches)));
    }

    public BebopSchema build() {
      return new BebopSchema(new LinkedHashMap<>(definitions));
    }
  }
}
