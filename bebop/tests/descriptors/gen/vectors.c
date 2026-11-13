// Encodes golden.vectors values with upstream's C runtime and bebopc-gen-c output.
// Usage: vectors OUTDIR
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "vectors.bb.h"

static const char* dir;
static Bebop_Context* ctx;

#define WRITE(name, fn, value)                                                   \
  do {                                                                           \
    Bebop_Writer* w = bebop_context_writer(ctx, 0);                              \
    Bebop_Result rr = fn(w, value);                                              \
    if (rr != BEBOP_RESULT_OK) { fprintf(stderr, "fail %s %d\n", name, (int)rr); exit(1); } \
    Bebop_View v = bebop_writer_view(w);                                         \
    char path[1024];                                                             \
    snprintf(path, sizeof path, "%s/%s", dir, name);                             \
    FILE* f = fopen(path, "wb");                                                 \
    fwrite(v.data, 1, v.length, f);                                              \
    fclose(f);                                                                   \
    printf("%-24s %6zu bytes  control %02x\n", name, v.length, v.data[v.length - 1]); \
  } while (0)

static uint8_t padding[70000];

/** A Directory with byte fields at the given tags (each holding its tag) and pad bytes, if any. */
static void directory(const char* name, const int* tags, int n, long pad) {
  Golden_Vectors_Directory d = {0};
  for (int i = 0; i < n; i++) {
    uint8_t t = (uint8_t)tags[i];
    switch (tags[i]) {
      case 1: BEBOP_SET(d.t1, t); break;
      case 2: BEBOP_SET(d.t2, t); break;
      case 3: BEBOP_SET(d.t3, t); break;
      case 4: BEBOP_SET(d.t4, t); break;
      case 5: BEBOP_SET(d.t5, t); break;
      case 8: BEBOP_SET(d.t8, t); break;
      case 9: BEBOP_SET(d.t9, t); break;
      case 16: BEBOP_SET(d.t16, t); break;
      case 17: BEBOP_SET(d.t17, t); break;
      case 32: BEBOP_SET(d.t32, t); break;
      case 33: BEBOP_SET(d.t33, t); break;
      case 255: BEBOP_SET(d.t255, t); break;
      default: exit(2);
    }
  }
  if (pad >= 0) BEBOP_SET(d.pad, ((Bebop_U8_Array){.data = padding, .length = (size_t)pad}));
  WRITE(name, Golden_Vectors_Directory_encode, &d);
}

#define DIR(name, pad, ...)                                         \
  do {                                                              \
    static const int t[] = {__VA_ARGS__};                           \
    directory(name, t, (int)(sizeof t / sizeof t[0]), pad);         \
  } while (0)

int main(int argc, char** argv) {
  (void)argc;
  dir = argv[1];
  ctx = bebop_context_new(NULL);
  for (size_t i = 0; i < sizeof padding; i++) padding[i] = (uint8_t)i;

  directory("dir.empty.bin", NULL, 0, -1);
  DIR("dir.tiny1.bin", -1, 9);
  DIR("dir.tiny1.far.bin", -1, 255);
  DIR("dir.tiny2.bin", -1, 9, 17);
  DIR("dir.tiny3.bin", -1, 9, 17, 32);
  DIR("dir.mask8.bin", -1, 1, 2, 3, 4);
  DIR("dir.mask16.bin", -1, 1, 2, 3, 16);
  DIR("dir.mask32.bin", -1, 1, 2, 3, 32);
  DIR("dir.blocks.bin", -1, 1, 2, 3, 33);
  DIR("dir.blocks.far.bin", -1, 1, 2, 3, 255);
  DIR("width1.bin", 10, 1, 2);
  DIR("width2.bin", 300, 1, 2);
  DIR("width4.bin", 70000, 1, 2);
  directory("width2.single.bin", NULL, 0, 300);

  Bebop_Timestamp tmax = {.seconds = INT64_MAX, .nanos = 999999999, .offset_ms = 86400000};
  Bebop_Timestamp tmin = {.seconds = INT64_MIN, .nanos = 0, .offset_ms = -86400000};
  Bebop_Timestamp tzero = {0};
  Bebop_Duration dmax = {.seconds = INT64_MAX, .nanos = 999999999};
  Bebop_Duration dmin = {.seconds = INT64_MIN, .nanos = -999999999};
  Bebop_Duration dzero = {0};

  Golden_Vectors_Times plain = {0};
  BEBOP_SET(plain.at, ((Bebop_Timestamp){.seconds = 1700000000, .nanos = 5, .offset_ms = 0}));
  BEBOP_SET(plain.span, ((Bebop_Duration){.seconds = 3, .nanos = 4}));
  WRITE("times.fields.bin", Golden_Vectors_Times_encode, &plain);

  static Bebop_Timestamp ats[3];
  ats[0] = tmax; ats[1] = tmin; ats[2] = tzero;
  static Bebop_Duration spans[3];
  spans[0] = dmax; spans[1] = dmin; spans[2] = dzero;
  Golden_Vectors_Stamped stamped = {
      .at = {.seconds = INT64_MIN, .nanos = 999999999, .offset_ms = 86400000},
      .span = {.seconds = -1, .nanos = -1},
      .sentinel = 0x7EADBEEF};
  Golden_Vectors_Times ext = {0};
  BEBOP_SET(ext.at, tmin);
  BEBOP_SET(ext.span, dmin);
  BEBOP_SET(ext.ats, ((Bebop_Timestamp_Array){.data = ats, .length = 3}));
  BEBOP_SET(ext.spans, ((Bebop_Duration_Array){.data = spans, .length = 3}));
  ext.fixedAts.has_value = true;
  ext.fixedAts.value[0] = tmax;
  ext.fixedAts.value[1] = tmin;
  ext.fixedSpans.has_value = true;
  ext.fixedSpans.value[0] = dmax;
  ext.fixedSpans.value[1] = dmin;
  memcpy((void*)&ext.stamped.value, &stamped, sizeof stamped);
  ext.stamped.has_value = true;
  WRITE("times.extremes.bin", Golden_Vectors_Times_encode, &ext);

  Golden_Vectors_Stamped only = {.at = tmax, .span = dmax, .sentinel = -2};
  Golden_Vectors_Times st = {0};
  memcpy((void*)&st.stamped.value, &only, sizeof only);
  st.stamped.has_value = true;
  WRITE("times.stamped.bin", Golden_Vectors_Times_encode, &st);
  return 0;
}
