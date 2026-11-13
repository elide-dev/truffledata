package dev.elide.truffledata.bebop;

import static dev.elide.truffledata.bebop.BebopType.Scalar.*;
import static dev.elide.truffledata.bebop.BebopType.ref;

import dev.elide.truffledata.bebop.BebopSchema.Branch;
import dev.elide.truffledata.bebop.BebopSchema.Field;
import dev.elide.truffledata.bebop.BebopSchema.Member;
import dev.elide.truffledata.bebop.BebopSchema.TaggedField;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Objects;

final class Fixtures {
  private Fixtures() {}

  static final BebopSchema SCHEMA =
      BebopSchema.builder()
          .struct("Point", new Field("x", FLOAT32), new Field("y", FLOAT32))
          .struct("Empty")
          .struct("Circle", new Field("radius", FLOAT32))
          .struct("Rectangle", new Field("width", FLOAT32), new Field("height", FLOAT32))
          .struct("Ack", new Field("code", INT32))
          .enumeration("Status", BYTE, new Member("ACTIVE", 1), new Member("GONE", 3))
          .message(
              "UserRequest",
              new TaggedField(1, "user_id", INT32),
              new TaggedField(2, "include_profile", BOOL))
          .message(
              "Request", new TaggedField(1, "method", STRING), new TaggedField(2, "path", STRING))
          .union(
              "Shape", new Branch(1, "Circle", ref("Circle")), new Branch(2, "Rectangle", ref("Rectangle")))
          .union("Event", new Branch(1, "Request", ref("Request")), new Branch(2, "Ack", ref("Ack")))
          .message(
              "Tree",
              new TaggedField(1, "value", INT32),
              new TaggedField(2, "children", BebopType.array(ref("Tree"))))
          .struct(
              "Row", new Field("id", UINT32), new Field("score", FLOAT64), new Field("flag", BOOL))
          .message(
              "Table",
              new TaggedField(1, "rows", BebopType.array(ref("Row"))),
              new TaggedField(2, "names", BebopType.array(STRING)),
              new TaggedField(3, "ids", BebopType.array(INT64)),
              new TaggedField(4, "blob", BebopType.array(BYTE)),
              new TaggedField(5, "small", BebopType.array(INT16)))
          .message(
              "Strict",
              new TaggedField(1, "flag", BOOL),
              new TaggedField(2, "real", FLOAT64),
              new TaggedField(3, "single", FLOAT32),
              new TaggedField(4, "byName", BebopType.map(STRING, INT32)),
              new TaggedField(5, "tags", BebopType.set(STRING)),
              new TaggedField(6, "status", ref("Status")),
              new TaggedField(7, "event", ref("Event")),
              new TaggedField(8, "text", STRING),
              new TaggedField(9, "empties", BebopType.array(ref("Empty"))),
              new TaggedField(10, "byId", BebopType.map(INT32, STRING)),
              new TaggedField(40, "far", INT64),
              new TaggedField(200, "farther", UINT16))
          .struct(
              "Mixed",
              new Field("name", STRING),
              new Field("n", INT32),
              new Field("tail", BebopType.array(STRING)),
              new Field("last", UINT16))
          .message(
              "Everything",
              new TaggedField(1, "b", BOOL),
              new TaggedField(2, "u8", BYTE),
              new TaggedField(3, "i8", INT8),
              new TaggedField(4, "i16", INT16),
              new TaggedField(5, "u16", UINT16),
              new TaggedField(6, "i32", INT32),
              new TaggedField(7, "u32", UINT32),
              new TaggedField(8, "i64", INT64),
              new TaggedField(9, "u64", UINT64),
              new TaggedField(10, "i128", INT128),
              new TaggedField(11, "u128", UINT128),
              new TaggedField(12, "f16", FLOAT16),
              new TaggedField(13, "f32", FLOAT32),
              new TaggedField(14, "f64", FLOAT64),
              new TaggedField(15, "bf16", BFLOAT16),
              new TaggedField(16, "id", UUID),
              new TaggedField(17, "at", TIMESTAMP),
              new TaggedField(18, "span", DURATION),
              new TaggedField(19, "text", STRING),
              new TaggedField(20, "status", ref("Status")),
              new TaggedField(21, "point", ref("Point")),
              new TaggedField(22, "row", ref("Row")),
              new TaggedField(23, "inner", ref("Request")),
              new TaggedField(24, "event", ref("Event")),
              new TaggedField(25, "tree", ref("Tree")),
              new TaggedField(26, "bytes", BebopType.array(BYTE)),
              new TaggedField(27, "digest", BebopType.fixed(BYTE, 4)),
              new TaggedField(28, "ints", BebopType.array(INT32)),
              new TaggedField(29, "names", BebopType.array(STRING)),
              new TaggedField(30, "rows", BebopType.array(ref("Row"))),
              new TaggedField(31, "tags", BebopType.set(STRING)),
              new TaggedField(32, "flags", BebopType.map(BOOL, INT32)),
              new TaggedField(33, "byName", BebopType.map(STRING, ref("Point"))),
              new TaggedField(34, "byId", BebopType.map(INT32, STRING)),
              new TaggedField(35, "grid", BebopType.fixed(BebopType.array(INT16), 3)),
              new TaggedField(36, "empties", BebopType.array(ref("Empty"))),
              new TaggedField(37, "mixed", ref("Mixed")),
              new TaggedField(38, "times", BebopType.array(TIMESTAMP)),
              new TaggedField(39, "halves", BebopType.array(FLOAT16)),
              new TaggedField(40, "level", ref("Level")),
              new TaggedField(41, "shapes", BebopType.array(ref("Shape"))),
              new TaggedField(42, "ids", BebopType.set(UUID)),
              new TaggedField(43, "byStatus", BebopType.map(ref("Status"), BOOL)),
              new TaggedField(44, "pair", BebopType.fixed(ref("Point"), 2)),
              new TaggedField(45, "big", BebopType.array(UINT64)),
              new TaggedField(46, "instant", BebopType.utcInstant()),
              new TaggedField(47, "instants", BebopType.array(BebopType.utcInstant())),
              new TaggedField(200, "wide", UINT16))
          .struct(
              "Skippy",
              new Field("name", STRING),
              new Field("status", ref("Status")),
              new Field("blob", BebopType.array(BYTE)),
              new Field("digest", BebopType.fixed(BYTE, 2)),
              new Field("ints", BebopType.array(INT32)),
              new Field("names", BebopType.array(STRING)),
              new Field("pair", BebopType.fixed(ref("Point"), 2)),
              new Field("byId", BebopType.map(INT32, STRING)),
              new Field("point", ref("Point")),
              new Field("mixed", ref("Mixed")),
              new Field("req", ref("Request")),
              new Field("shape", ref("Shape")),
              new Field("grid", BebopType.fixed(BebopType.array(INT16), 2)),
              new Field("levels", BebopType.array(ref("Level"))),
              new Field("labels", BebopType.fixed(STRING, 2)),
              new Field("fixedMap", BebopType.map(BYTE, UINT16)),
              new Field("last", UINT16))
          .message(
              "Columns",
              new TaggedField(1, "i8s", BebopType.array(INT8)),
              new TaggedField(2, "i32s", BebopType.array(INT32)),
              new TaggedField(3, "f16s", BebopType.array(FLOAT16)),
              new TaggedField(4, "bf16s", BebopType.array(BFLOAT16)),
              new TaggedField(5, "uuids", BebopType.array(UUID)),
              new TaggedField(6, "u16s", BebopType.array(UINT16)),
              new TaggedField(7, "bools", BebopType.array(BOOL)),
              new TaggedField(8, "f32s", BebopType.array(FLOAT32)),
              new TaggedField(9, "u64s", BebopType.array(UINT64)),
              new TaggedField(10, "skippy", ref("Skippy")),
              new TaggedField(11, "blocks", BebopType.array(ref("Block"))))
          .struct(
              "Block",
              new Field("digest", BebopType.fixed(BYTE, 2)),
              new Field("pair", BebopType.fixed(ref("Point"), 2)),
              new Field("grid", BebopType.fixed(INT32, 3)),
              new Field("level", ref("Level")),
              new Field("n", INT16))
          .enumeration("Level", INT64, new Member("LOW", -5), new Member("HIGH", 1L << 40))
          .build();

  /** The strict profile of rules revision 0.11, under a neutral identifier. */
  static final BebopProfile STRICT = BebopProfile.rules011("strict-rules-0.11");

  static BebopCodec general(BebopType root) {
    return BebopCodec.compile(SCHEMA, root, BebopProfile.GENERAL, BebopLimits.DEFAULT);
  }

  static BebopCodec strict(BebopType root) {
    return BebopCodec.compile(SCHEMA, root, STRICT, BebopLimits.DEFAULT);
  }

  /** Hex with whitespace and {@code //} comments, as in the wire specification's examples. */
  static byte[] hex(String text) {
    StringBuilder digits = new StringBuilder();
    for (String line : text.split("\n")) {
      int comment = line.indexOf("//");
      digits.append((comment < 0 ? line : line.substring(0, comment)).replace(" ", ""));
    }
    return HexFormat.of().parseHex(digits);
  }

  static String hex(byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
  }

  /** A file under {@code bebop/tests/descriptors}: descriptor sets and values made by upstream. */
  static byte[] upstream(String name) {
    Path path =
        Path.of(
            Objects.requireNonNull(System.getenv("TEST_SRCDIR")),
            Objects.requireNonNull(System.getenv("TEST_WORKSPACE")),
            "bebop/tests/descriptors",
            name);
    try {
      return Files.readAllBytes(path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
