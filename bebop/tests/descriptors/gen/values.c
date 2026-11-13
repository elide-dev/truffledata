// Encodes golden values with upstream's C runtime and generated code. Usage: values OUTDIR
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "scalars.bb.h"
#include "types.bb.h"
#include "flags.bb.h"
#include "bare.bb.h"

static const char* dir;
static Bebop_Context* ctx;

#define WRITE(name, fn, value)                                                   \
  do {                                                                           \
    Bebop_Writer* w = bebop_context_writer(ctx, 0);                              \
    Bebop_Result rr = fn(w, value); if (rr != BEBOP_RESULT_OK) { fprintf(stderr, "fail %s %d\n", name, (int)rr); exit(1); } \
    Bebop_View v = bebop_writer_view(w);                                         \
    char path[1024];                                                             \
    snprintf(path, sizeof path, "%s/%s", dir, name);                             \
    FILE* f = fopen(path, "wb");                                                 \
    fwrite(v.data, 1, v.length, f);                                              \
    fclose(f);                                                                   \
    printf("%-28s %zu bytes\n", name, v.length);                                 \
  } while (0)

static Bebop_UUID uuid_seq(uint8_t start) {
  Bebop_UUID u;
  for (int i = 0; i < 16; i++) u.bytes[i] = (uint8_t)(start + i);
  return u;
}

int main(int argc, char** argv) {
  (void)argc;
  dir = argv[1];
  ctx = bebop_context_new(NULL);

  Bebop_Int128 i128 = -2;
  Bebop_UInt128 u128 = ((Bebop_UInt128)1 << 100) + 7;
  Bebop_Timestamp at = {.seconds = 1700000000, .nanos = 123456789, .offset_ms = -3600000};
  Bebop_Duration span = {.seconds = -5, .nanos = -500};
  Bebop_String s = BEBOP_STRING("h\xc3\xa9llo");
  Bebop_UUID id = uuid_seq(0);
  Bebop_BFloat16 bf16 = bebop_bfloat16_from_float(1.0f);

  Golden_Scalars_AllScalars all = {
      .b = true, .u8 = 0xAB, .i8 = -5, .i16 = -300, .u16 = 60000, .i32 = -123456,
      .u32 = 4000000000u, .i64 = -1234567890123LL, .u64 = 0xFEDCBA9876543210ULL,
      .i128 = i128, .u128 = u128, .f16 = (Bebop_Float16)1.5f, .f32 = -2.25f,
      .f64 = 3.141592653589793, .bf16 = bf16, .s = s, .id = id, .at = at, .span = span};
  WRITE("all_scalars.struct.bin", Golden_Scalars_AllScalars_encode, &all);

  Golden_Scalars_AllScalarsMessage m = {0};
  BEBOP_SET(m.b, true); BEBOP_SET(m.u8, 0xAB); BEBOP_SET(m.i8, -5); BEBOP_SET(m.i16, -300);
  BEBOP_SET(m.u16, 60000); BEBOP_SET(m.i32, -123456); BEBOP_SET(m.u32, 4000000000u);
  BEBOP_SET(m.i64, -1234567890123LL); BEBOP_SET(m.u64, 0xFEDCBA9876543210ULL);
  BEBOP_SET(m.i128, i128); BEBOP_SET(m.u128, u128); BEBOP_SET(m.f16, (Bebop_Float16)1.5f);
  BEBOP_SET(m.f32, -2.25f); BEBOP_SET(m.f64, 3.141592653589793); BEBOP_SET(m.bf16, bf16);
  BEBOP_SET(m.s, s); BEBOP_SET(m.id, id); BEBOP_SET(m.at, at); BEBOP_SET(m.span, span);
  WRITE("all_scalars.message.bin", Golden_Scalars_AllScalarsMessage_encode, &m);

  Golden_Scalars_AllScalarsMessage sparse = {0};
  BEBOP_SET(sparse.u16, 7); BEBOP_SET(sparse.s, BEBOP_STRING("")); BEBOP_SET(sparse.span, span);
  WRITE("sparse.message.bin", Golden_Scalars_AllScalarsMessage_encode, &sparse);

  Golden_Scalars_AllScalarsMessage empty = {0};
  WRITE("empty.message.bin", Golden_Scalars_AllScalarsMessage_encode, &empty);

  static char longtext[301];
  memset(longtext, 'x', 300);
  Golden_Scalars_AllScalarsMessage wide = {0};
  BEBOP_SET(wide.b, false); BEBOP_SET(wide.s, ((Bebop_String){.data = longtext, .length = 300}));
  BEBOP_SET(wide.i32, 1);
  WRITE("wide.message.bin", Golden_Scalars_AllScalarsMessage_encode, &wide);

  // Envelope: nested messages, inline-message union branch, arrays, fixed arrays, maps, tag 200.
  Golden_Types_Envelope_Header header = {0};
  BEBOP_SET(header.id, uuid_seq(0x10));
  BEBOP_SET(header.sent, at);
  static float pts[2][2] = {{1.0f, 2.0f}, {3.0f, 4.0f}};
  Golden_Types_Shape_Polygon polygon = {0};
  BEBOP_SET(polygon.points, ((Bebop_F32Fa2_Array){.data = pts, .length = 2}));
  Golden_Types_Shape body = {.discriminator = GOLDEN_TYPES_SHAPE_POLYGON, .polygon = polygon};
  static Bebop_String tags[2];
  tags[0] = BEBOP_STRING("b");
  tags[1] = BEBOP_STRING("a");

  Golden_Types_Envelope env = {0};
  BEBOP_SET(env.header, &header);
  BEBOP_SET(env.body, &body);
  BEBOP_SET(env.tags, ((Bebop_String_Array){.data = tags, .length = 2}));
  env.matrix.has_value = true;
  for (int i = 0; i < 2; i++) for (int j = 0; j < 3; j++) env.matrix.value[i][j] = i * 3 + j + 1;

  bebop_map_init(&env.byColor.value, ctx, BEBOP_MAP_KEY_U16);
  env.byColor.has_value = true;
  static uint16_t k80 = 80, k1 = 1;
  static int64_t longs[2] = {1, -2};
  static Bebop_I64_Array a80 = {.data = longs, .length = 2}, a1 = {.data = longs, .length = 0};
  bebop_map_set(&env.byColor.value, &k80, &a80);
  bebop_map_set(&env.byColor.value, &k1, &a1);

  env.digest.has_value = true;
  for (int i = 0; i < 16; i++) env.digest.value[i] = (uint8_t)(0xF0 + i);

  bebop_map_init(&env.nested.value, ctx, BEBOP_MAP_KEY_UUID);
  env.nested.has_value = true;
  static Bebop_Map inner;
  bebop_map_init(&inner, ctx, BEBOP_MAP_KEY_I32);
  static int32_t kn = -1, kp = 2;
  static Bebop_String words[2];
  words[0] = BEBOP_STRING("one");
  words[1] = BEBOP_STRING("two");
  static Bebop_String_Array wa = {.data = words, .length = 2}, wb = {.data = words, .length = 0};
  bebop_map_set(&inner, &kn, &wa);
  bebop_map_set(&inner, &kp, &wb);
  static Bebop_UUID nk;
  nk = uuid_seq(0x20);
  bebop_map_set(&env.nested.value, &nk, &inner);

  static int16_t j0[2] = {1, -1};
  static Bebop_I16_Array jag[2];
  jag[0] = (Bebop_I16_Array){.data = j0, .length = 2};
  jag[1] = (Bebop_I16_Array){.data = j0, .length = 0};
  BEBOP_SET(env.jagged, ((Bebop_I16_Array_Array){.data = jag, .length = 2}));

  Golden_Types_Outer_Inner_Deeper d1 = {0}, d2 = {0};
  BEBOP_SET(d1.n, 42);
  Golden_Types_Outer_Inner c0 = {.label = BEBOP_STRING("left"), .deep = d1};
  Golden_Types_Outer_Inner c1 = {.label = BEBOP_STRING("right"), .deep = d2};
  env.corners.has_value = true;
  memcpy((void*)&env.corners.value[0], &c0, sizeof c0);
  memcpy((void*)&env.corners.value[1], &c1, sizeof c1);

  bebop_map_init(&env.small.value, ctx, BEBOP_MAP_KEY_BOOL);
  env.small.has_value = true;
  static bool kt = true, kf = false;
  static Golden_Types_Small high = GOLDEN_TYPES_SMALL_HIGH, none = GOLDEN_TYPES_SMALL_NONE;
  bebop_map_set(&env.small.value, &kt, &high);
  bebop_map_set(&env.small.value, &kf, &none);

  BEBOP_SET(env.far, true);
  WRITE("envelope.bin", Golden_Types_Envelope_encode, &env);

  Golden_Types_Shape circle = {.discriminator = GOLDEN_TYPES_SHAPE_CIRCLE, .circle = {.radius = 2.5f}};
  WRITE("shape.circle.bin", Golden_Types_Shape_encode, &circle);
  Golden_Types_Shape emptyShape = {.discriminator = GOLDEN_TYPES_SHAPE_EMPTY};
  WRITE("shape.empty.bin", Golden_Types_Shape_encode, &emptyShape);
  Golden_Types_Shape plain = {.discriminator = GOLDEN_TYPES_SHAPE_PLAIN, .plain = all};
  WRITE("shape.plain.bin", Golden_Types_Shape_encode, &plain);

  static int32_t xs[3] = {1, 2, 3};
  static uint8_t ys[1] = {9};
  static int32_t olds[1] = {7};
  Bare bare = {.xs = {.data = xs, .length = 3}, .ys = {.data = ys, .length = 1}};
  Wrapper_Choice choice = {.discriminator = WRAPPER_CHOICE_B};
  BEBOP_SET(choice.b.b, BEBOP_STRING("x"));
  Wrapper wrapper = {0};
  memcpy((void*)&wrapper.bare.value, &bare, sizeof bare);
  wrapper.bare.has_value = true;
  BEBOP_SET(wrapper.old, ((Bebop_I32_Array){.data = olds, .length = 1}));
  BEBOP_SET(wrapper.choice, &choice);
  WRITE("wrapper.bin", Wrapper_encode, &wrapper);

  Golden_Flags_File file = {.name = BEBOP_STRING("a.txt"),
                            .perm = (Golden_Flags_Perm)(GOLDEN_FLAGS_PERM_READ | GOLDEN_FLAGS_PERM_EXEC)};
  WRITE("file.flags.bin", Golden_Flags_File_encode, &file);
  return 0;
}
