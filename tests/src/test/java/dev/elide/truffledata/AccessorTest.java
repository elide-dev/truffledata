package dev.elide.truffledata;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import dev.elide.truffledata.generated.TruffleDataContracts;
import dev.elide.truffledata.protobuf.ProtoPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/** Layout-bound accessors: identity, foreign-layout rejection, PE constancy and lengths. */
class AccessorTest {
  private static final String FLAT = "truffledata.lossless.Flat";
  // Flat ordinals: i64, flag, real, text, blob, present (optional INT32), u64 (EXACT).
  private static final int I64 = 1, FLAG = 10, REAL = 12, TEXT = 13, BLOB = 14, PRESENT = 16;
  private static final int U64 = 3;

  private static Context context() {
    Context context =
        Context.newBuilder("td-test")
            .allowExperimentalOptions(true)
            .option("engine.BackgroundCompilation", "false")
            .option("engine.CompilationFailureAction", "Throw")
            .option("compiler.TreatPerformanceWarningsAsErrors", "all")
            .option("compiler.MaximumGraalGraphSize", "10000")
            .build();
    context.initialize("td-test");
    context.enter();
    return context;
  }

  private static ProtoPlan flat(boolean language) {
    return TruffleDataContracts.create(
            language ? TestLanguage.current().language() : null, Limits.DEFAULT, 16, 1 << 20)
        .type(FLAT);
  }

  private static Object generated(ProtoPlan plan, long i64, String text) {
    var access = new TruffleDataContracts.Access_truffledata$lossless$Flat(plan);
    return access.newBuilder().set_i64(i64).set_text(text).set_flag(true).set_real(2.5).build();
  }

  private static Object owned(ProtoPlan plan, long i64, String text) {
    return plan.newBuilder().set(I64, i64).set(TEXT, text).set(FLAG, true).set(REAL, 2.5).build();
  }

  private static Object slab(ProtoPlan plan, long i64, String text) {
    return plan.decodeSlab(
        plan.encode((dev.elide.truffledata.protobuf.ProtoRow) owned(plan, i64, text)));
  }

  @Test
  void accessor_rejects_foreign_layout() {
    try (Context context = context()) {
      try {
        // One schema identity, two static layouts: schema identity alone must not bind a read.
        Schema schema =
            Schema.of(Field.requiredLong("n"), new Field("s", Field.Kind.STRING, false, true));
        var language = TestLanguage.current().language();
        var first = new StaticLayout(language, schema, Limits.DEFAULT);
        var second = new StaticLayout(language, schema, Limits.DEFAULT);
        AbstractRow a = first.create(1L, "a"), b = second.create(2L, Missing.INSTANCE);
        Accessor bound = a.accessor(0);
        assertSame(bound, first.create(3L, "c").accessor(0));
        assertSame(bound, first.accessor(0));
        assertNotSame(bound, b.accessor(0));
        assertTrue(bound.accepts(a));
        assertFalse(bound.accepts(b));
        assertFalse(bound.accepts("not a row"));
        assertThrows(ContractViolation.class, () -> bound.readLong(b));
        assertThrows(ContractViolation.class, () -> bound.read(b));
        assertThrows(ContractViolation.class, () -> a.accessor(1).present(b));
        assertSame(schema, bound.schema());
        assertEquals(0, bound.ordinal());
        assertEquals("n", bound.field().name());
        assertSites(List.of(a, b), new long[] {1, 2});

        // Two protobuf plans with equal schemas: owned rows, generated rows, compact rows.
        var left = flat(true);
        var right = flat(true);
        assertNotSame(left.schema(), right.schema());
        for (int representation = 0; representation < 3; representation++) {
          Object l =
              representation == 0
                  ? generated(left, 10, "l")
                  : representation == 1 ? owned(left, 10, "l") : slab(left, 10, "l");
          Object r =
              representation == 0
                  ? generated(right, 20, "r")
                  : representation == 1 ? owned(right, 20, "r") : slab(right, 20, "r");
          Accessor accessor = ((AbstractRow) l).accessor(I64);
          assertFalse(accessor.accepts(r));
          assertThrows(ContractViolation.class, () -> accessor.readLong(r));
          assertSites(List.of(l, r), new long[] {10, 20}, I64);
        }
        // Two batch schemas with equal fields bind distinct column accessors.
        AbstractRow x = batch(Schema.of(Field.requiredLong("n")), 7L);
        AbstractRow y = batch(Schema.of(Field.requiredLong("n")), 8L);
        assertFalse(x.accessor(0).accepts(y));
        assertThrows(ContractViolation.class, () -> x.accessor(0).read(y));
        assertSites(List.of(x, y), new long[] {7, 8});
      } finally {
        context.leave();
      }
    }
  }

  /** One read site over every row: exact results and one accessor cache entry per layout. */
  private static void assertSites(List<Object> rows, long[] expected) {
    assertSites(rows, expected, 0);
  }

  private static void assertSites(List<Object> rows, long[] expected, int ordinal) {
    var root = new SiteRoot(ordinal);
    for (int round = 0; round < 3; round++)
      for (int i = 0; i < rows.size(); i++)
        assertEquals(expected[i], root.getCallTarget().call(rows.get(i)));
    var accessed = Introspection.getSpecialization(root.read, "accessed");
    assertTrue(accessed.isActive());
    assertEquals(rows.size(), accessed.getInstances());
    assertFalse(Introspection.getSpecialization(root.read, "required").isActive());
  }

  private static final class SiteRoot extends RootNode {
    @Child ReadLongNode read = ReadLongNode.create();
    private final int ordinal;

    SiteRoot(int ordinal) {
      super(null);
      this.ordinal = ordinal;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      return read.execute(frame.getArguments()[0], ordinal);
    }
  }

  private static Batch.Row batch(Schema schema, Object... values) {
    return Batch.copyOf(schema, Collections.singletonList(values), Limits.DEFAULT).row(0);
  }

  @Test
  void accessor_is_pe_constant() {
    try (Context context = context()) {
      try {
        var language = TestLanguage.current().language();
        Schema schema =
            Schema.of(
                Field.requiredLong("n"),
                new Field("d", Field.Kind.FLOAT64, false, false),
                new Field("b", Field.Kind.BOOLEAN, false, false),
                new Field("s", Field.Kind.STRING, false, false));
        var rows =
            new Object[] {
              new StaticLayout(language, schema, Limits.DEFAULT).create(5L, 2.5, true, "ab😀"),
              batch(schema, 5L, 2.5, true, "ab😀"),
            };
        var plan = flat(true);
        var protobuf =
            new Object[] {
              owned(plan, 5, "ab😀"), generated(plan, 5, "ab😀"), slab(plan, 5, "ab😀")
            };
        for (Object[] set : new Object[][] {rows, protobuf}) {
          boolean flat = set == protobuf;
          var root =
              new ConstantRoot(flat ? I64 : 0, flat ? REAL : 1, flat ? FLAG : 2, flat ? TEXT : 3);
          var target = (OptimizedCallTarget) root.getCallTarget();
          for (Object row : set)
            for (int i = 0; i < 4; i++) assertEquals(12000L, target.call(row, 1000));
          for (int attempt = 0; attempt < 8 && !target.isValidLastTier(); attempt++) {
            target.compile(true);
            target.waitForCompilation();
            for (Object row : set) assertEquals(12000L, target.call(row, 1000));
          }
          root.compiled = false;
          for (Object row : set) assertEquals(12000L, target.call(row, 1000));
          assertTrue(target.isValidLastTier(), "Accessor reads must compile");
          assertTrue(root.compiled);
          assertTrue(NodeUtil.countNodes(root) <= 64);
          for (var node :
              new com.oracle.truffle.api.nodes.Node[] {
                root.longs, root.doubles, root.booleans, root.lengths
              })
            assertEquals(
                set.length, Introspection.getSpecialization(node, "accessed").getInstances());
        }
      } finally {
        context.leave();
      }
    }
  }

  /**
   * Reads four kinds through the shared nodes. Every accessed specialization asserts its cached
   * accessor is a partial-evaluation constant; a non-constant one fails compilation (Throw).
   */
  private static final class ConstantRoot extends RootNode {
    @Child ReadLongNode longs = ReadLongNode.create();
    @Child ReadDoubleNode doubles = ReadDoubleNode.create();
    @Child ReadBooleanNode booleans = ReadBooleanNode.create();
    @Child ReadLengthNode lengths = ReadLengthNode.create();
    private final int n, d, b, s;
    boolean compiled;

    ConstantRoot(int n, int d, int b, int s) {
      super(null);
      this.n = n;
      this.d = d;
      this.b = b;
      this.s = s;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object row = frame.getArguments()[0];
      int count = (Integer) frame.getArguments()[1];
      CompilerAsserts.partialEvaluationConstant(n);
      long sum = 0;
      // 5 + 2 (2.5 rounded down) + 1 (true) + 4 ("ab😀" in UTF-16 units) = 12 per iteration.
      for (int i = 0; i < count; i++)
        sum +=
            longs.execute(row, n)
                + (long) doubles.execute(row, d)
                + (booleans.execute(row, b) ? 1 : 0)
                + lengths.execute(row, s);
      if (CompilerDirectives.inInterpreter()) LoopNode.reportLoopCount(this, count);
      compiled |= CompilerDirectives.inCompiledCode();
      return sum;
    }
  }

  @Test
  void length_accessor_counts_utf16() {
    try (Context context = context()) {
      try {
        var language = TestLanguage.current().language();
        String[] texts = {"ascii", "é中", "😀", "a😀b"};
        int[] utf16 = {5, 2, 2, 4};
        int[] points = {5, 2, 1, 3};
        Schema schema =
            Schema.of(
                new Field("s", Field.Kind.STRING, false, false),
                new Field("p", Field.Kind.STRING, false, false)
                    .withConstraints(
                        Constraints.NONE.withLength(
                            new Constraints.Length(0, 100, Constraints.LengthUnit.CODE_POINTS))),
                new Field("a", Field.Kind.ARRAY, false, false),
                new Field("x", Field.Kind.STRING, false, false)
                    .withConstraints(
                        Constraints.NONE.withLength(
                            new Constraints.Length(1, 1, Constraints.LengthUnit.UTF16))));
        var layout = new StaticLayout(language, schema, Limits.DEFAULT);
        var lengths = new LengthRoot();
        for (int i = 0; i < texts.length; i++) {
          Object[] elements = new Object[i];
          java.util.Arrays.fill(elements, 0L);
          var array = new DataArray(elements);
          for (AbstractRow row :
              new AbstractRow[] {
                layout.create(texts[i], texts[i], array, "x"),
                batch(schema, texts[i], texts[i], array, "x")
              }) {
            assertEquals(utf16[i], row.accessor(0).length(row));
            assertEquals(points[i], row.accessor(1).length(row));
            assertEquals(i, row.accessor(2).length(row));
            assertEquals(1, row.accessor(3).length(row));
            assertEquals(utf16[i], lengths.getCallTarget().call(row, 0));
            assertEquals(points[i], lengths.getCallTarget().call(row, 1));
            assertEquals(i, lengths.getCallTarget().call(row, 2));
          }
          var plan = flat(i % 2 == 0);
          for (Object row :
              new Object[] {
                owned(plan, 1, texts[i]), generated(plan, 1, texts[i]), slab(plan, 1, texts[i])
              }) {
            assertEquals(utf16[i], ((AbstractRow) row).accessor(TEXT).length(row));
            assertEquals(utf16[i], lengths.getCallTarget().call(row, TEXT));
            assertEquals(0, ((AbstractRow) row).accessor(BLOB).length(row));
          }
        }
        assertThrows(
            ContractViolation.class,
            () -> {
              AbstractRow row = layout.create("s", "p", new DataArray(new Object[0]), "x");
              ((AbstractRow) owned(flat(true), 1, "")).accessor(I64).length(row);
            });
        var plan = flat(true);
        Object row = owned(plan, 1, "");
        assertThrows(ContractViolation.class, () -> ((AbstractRow) row).accessor(I64).length(row));
      } finally {
        context.leave();
      }
    }
  }

  private static final class LengthRoot extends RootNode {
    @Child ReadLengthNode read = ReadLengthNode.create();

    LengthRoot() {
      super(null);
    }

    @Override
    public Object execute(VirtualFrame frame) {
      return read.execute(frame.getArguments()[0], (Integer) frame.getArguments()[1]);
    }
  }

  /** Every accessor method of every adopting representation agrees with the DataLibrary. */
  @Test
  void accessorsAgreeWithTheLibrary() throws Exception {
    try (Context context = context()) {
      try {
        var language = TestLanguage.current().language();
        Schema schema =
            Schema.of(
                Field.requiredLong("n"),
                new Field("o", Field.Kind.INT64, true, true),
                new Field("d", Field.Kind.FLOAT64, false, false)
                    .withConstraints(Constraints.NONE.withFloatBits(32)),
                new Field("e", Field.Kind.FLOAT64, true, true),
                new Field("b", Field.Kind.BOOLEAN, true, true),
                new Field("s", Field.Kind.STRING, true, true),
                new Field("r", Field.Kind.RECORD, true, true),
                new Field("x", Field.Kind.ANY, true, true),
                Field.integer("narrow", 16, false, false, false));
        AbstractRow nested = batch(Schema.of(Field.requiredLong("q")), 3L);
        var layout = new StaticLayout(language, schema, Limits.DEFAULT);
        List<AbstractRow> rows = new ArrayList<>();
        for (Object[] values :
            new Object[][] {
              {1L, 2L, 1.5, 2.5, true, "s", nested, 9L, 65535L},
              {
                1L,
                DataNull.INSTANCE,
                1.5,
                Missing.INSTANCE,
                DataNull.INSTANCE,
                Missing.INSTANCE,
                Missing.INSTANCE,
                "x",
                0L
              },
            }) {
          rows.add(layout.create(values));
          rows.add(batch(schema, values));
          DynamicObject object = new DynamicObject(Shape.newBuilder().build()) {};
          for (int i = 0; i < values.length; i++)
            if (values[i] != Missing.INSTANCE)
              DynamicObject.PutNode.getUncached()
                  .execute(object, schema.field(i).name(), values[i]);
          rows.add(new DynamicRecord(object, schema));
        }
        for (AbstractRow row : rows) agree(row);
        // The library-backed default: dynamic records keep their library specializations.
        AbstractRow dynamic = rows.get(2);
        Accessor fallback = dynamic.accessor(0);
        assertInstanceOf(LibraryAccessor.class, fallback);
        assertSame(fallback, schema.accessor(0));
        assertTrue(fallback.accepts(rows.get(0)));
        assertFalse(fallback.accepts(nested));
        assertThrows(ContractViolation.class, () -> fallback.read(nested));
        assertNull(Accessor.bind(dynamic, 0));
        assertNull(Accessor.bind("not a row", 0));
        assertNull(Accessor.bind(dynamic, schema.size()));
        assertNull(Accessor.bind(dynamic, -1));
        assertFalse(Accessor.bound(null, dynamic));
        assertEquals(1, schema.accessor(6).length(rows.get(0)));
        assertThrows(ContractViolation.class, () -> schema.accessor(7).length(rows.get(0)));
        assertThrows(
            IllegalArgumentException.class,
            () -> schema.accessor(new Object(), 0, i -> nested.accessor(0)));
        assertThrows(
            IndexOutOfBoundsException.class,
            () -> schema.accessor(new Object(), schema.size(), i -> nested.accessor(0)));

        var plan = flat(true);
        var unlaid = flat(false);
        for (ProtoPlan p : new ProtoPlan[] {plan, unlaid}) {
          agree((AbstractRow) owned(p, 4, "t"));
          agree((AbstractRow) generated(p, 4, "t"));
          agree((AbstractRow) slab(p, 4, "t"));
          agree(
              (AbstractRow)
                  p.newBuilder()
                      .set(PRESENT, 3L)
                      .set(U64, java.math.BigInteger.valueOf(7))
                      .build());
        }
        for (int width = 1; width <= 2; width++) agree(compact(width));
      } finally {
        context.leave();
      }
    }
  }

  /** A representation that implements only accepts and read inherits DataLibrary's defaults. */
  @Test
  void minimalAccessorInheritsLibraryDefaults() throws Exception {
    Schema schema =
        Schema.of(
            Field.requiredLong("n"),
            new Field("d", Field.Kind.FLOAT64, false, false),
            new Field("b", Field.Kind.BOOLEAN, false, false),
            new Field("s", Field.Kind.STRING, false, true));
    AbstractRow row = batch(schema, 4L, 1.5, true, Missing.INSTANCE);
    var library = DataLibrary.factory().getUncached();
    for (int i = 0; i < schema.size(); i++) {
      Accessor minimal =
          new Accessor(schema, i) {
            @Override
            public boolean accepts(Object candidate) {
              return candidate == row;
            }

            @Override
            public Object read(Object candidate) {
              return library.read(candidate, ordinal());
            }
          };
      int ordinal = i;
      assertEquals(library.isPresent(row, i), minimal.present(row));
      assertEquals(
          outcome(() -> library.readLong(row, ordinal)), outcome(() -> minimal.readLong(row)));
      assertEquals(
          outcome(() -> library.readDouble(row, ordinal)), outcome(() -> minimal.readDouble(row)));
      assertEquals(
          outcome(() -> library.readBoolean(row, ordinal)),
          outcome(() -> minimal.readBoolean(row)));
      assertEquals(
          outcome(() -> ReadLengthNodeGen.getUncached().execute(row, ordinal)),
          outcome(() -> minimal.length(row)));
    }
  }

  private static AbstractRow compact(int width) {
    List<ProtoPlan.FieldPlan> fields = new ArrayList<>();
    for (int i = 0; i < width; i++)
      fields.add(
          new ProtoPlan.FieldPlan(
              "f" + i,
              i + 1,
              ProtoPlan.Kind.INT64,
              false,
              false,
              false,
              false,
              false,
              -1,
              "",
              null,
              0L));
    var plan =
        new ProtoPlan.Model(
                null,
                List.of(new ProtoPlan.Type("accessor.Compact", false, fields)),
                Limits.DEFAULT,
                8,
                32)
            .type("accessor.Compact");
    var builder = plan.newBuilder().set(0, 11L);
    if (width == 2) builder.set(1, 12L);
    return plan.decode(plan.encode(builder.build()));
  }

  /** Each accessor method returns or rejects exactly what the uncached library does. */
  static void agree(AbstractRow row) throws Exception {
    var library = DataLibrary.factory().getUncached();
    for (int i = 0; i < row.schema().size(); i++) {
      Accessor accessor = row.accessor(i);
      int ordinal = i;
      assertSame(accessor, row.accessor(i));
      assertTrue(accessor.accepts(row));
      assertEquals(norm(library.read(row, i)), norm(accessor.read(row)));
      assertEquals(library.isPresent(row, i), accessor.present(row));
      Object longValue = outcome(() -> library.readLong(row, ordinal));
      assertEquals(longValue, outcome(() -> accessor.readLong(row)));
      assertEquals(
          outcome(() -> library.readDouble(row, ordinal)), outcome(() -> accessor.readDouble(row)));
      assertEquals(
          outcome(() -> library.readBoolean(row, ordinal)),
          outcome(() -> accessor.readBoolean(row)));
      assertEquals(
          outcome(() -> ReadLengthNodeGen.getUncached().execute(row, ordinal)),
          outcome(() -> accessor.length(row)));
      assertEquals(
          longValue instanceof Rejected ? ContractViolation.class : longValue,
          outcome(() -> ReadLongNodeGen.getUncached().execute(row, ordinal)));
    }
  }

  /** Generated rows box exact numbers per read; ExactNumber compares by identity. */
  private static Object norm(Object value) {
    return value instanceof ExactNumber number ? number.decimal() : value;
  }

  private record Rejected(Object value) {}

  private interface Read {
    Object get() throws Exception;
  }

  /** The value, or the rejected value of an UnexpectedResultException, or the exception class. */
  private static Object outcome(Read read) {
    try {
      return norm(read.get());
    } catch (UnexpectedResultException e) {
      return new Rejected(norm(e.getResult()));
    } catch (RuntimeException e) {
      return e.getClass();
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
}
