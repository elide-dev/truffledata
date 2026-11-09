// Standalone native decode of the consumer fixture (plan 2026-11-08-beat-stock-svm P0.1).
// Calls the production Rust slab decoder through its C ABI with the schema program, limits and
// learned capacity the SVM image dumped (`truffledata-consumer --dump-fixture MODE DIR`), into one
// reused output buffer: no JVM, no transition, no pinning, no Java publication. Never shipped.
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

typedef int32_t (*decode_fn)(const uint8_t *, const uint8_t *, uint64_t, uint32_t, const uint8_t *,
                             uint8_t *, uint64_t, uint32_t, uint64_t, void *);
extern int32_t td_buffa_compile_layout(const uint8_t *, uint64_t, void *);
extern void td_buffa_release_layout(uint8_t *);
extern int32_t td_buffa_decode_slab(const uint8_t *, const uint8_t *, uint64_t, uint32_t,
                                    const uint8_t *, uint8_t *, uint64_t, uint32_t, uint64_t,
                                    void *);
extern int32_t td_buffa_decode_slab_small(const uint8_t *, const uint8_t *, uint64_t, uint32_t,
                                          const uint8_t *, uint8_t *, uint64_t, uint32_t, uint64_t,
                                          void *);

struct result {
  uint8_t *data;
  uint64_t count, capacity;
};
struct slab_result {
  uint64_t bytes;
  uint32_t records, handles, flags, error;
  uint64_t reserved;
};

#define ROWS 32
#define ROUNDS 15

static uint8_t *slurp(const char *dir, const char *name, size_t *length) {
  char path[4096];
  snprintf(path, sizeof path, "%s/%s", dir, name);
  FILE *file = fopen(path, "rb");
  if (!file) {
    perror(path);
    exit(2);
  }
  fseek(file, 0, SEEK_END);
  long size = ftell(file);
  fseek(file, 0, SEEK_SET);
  uint8_t *bytes = malloc(size > 0 ? (size_t)size : 1);
  if (!bytes || fread(bytes, 1, (size_t)size, file) != (size_t)size) exit(2);
  fclose(file);
  *length = (size_t)size;
  return bytes;
}

static double now(void) {
  struct timespec t;
  clock_gettime(CLOCK_MONOTONIC, &t);
  return t.tv_sec * 1e9 + t.tv_nsec;
}

static int compare(const void *a, const void *b) {
  double x = *(const double *)a, y = *(const double *)b;
  return (x > y) - (x < y);
}

int main(int argc, char **argv) {
  if (argc != 3) {
    fprintf(stderr, "usage: %s FIXTURE_DIR MODE\n", argv[0]);
    return 2;
  }
  const char *dir = argv[1];
  size_t schema_length, params_length, lengths[ROWS];
  uint8_t *schema = slurp(dir, "schema.bin", &schema_length);
  char *params = (char *)slurp(dir, "params.txt", &params_length);
  params = realloc(params, params_length + 1);
  params[params_length] = 0;
  unsigned depth;
  unsigned long long max_slab, capacity, extents[ROWS];
  char *cursor = params;
  int consumed;
  if (sscanf(cursor, "%u %llu %llu%n", &depth, &max_slab, &capacity, &consumed) != 3) return 2;
  cursor += consumed;
  for (int i = 0; i < ROWS; i++) {
    if (sscanf(cursor, "%llu%n", &extents[i], &consumed) != 1) return 2;
    cursor += consumed;
  }
  uint8_t *wires[ROWS];
  uint64_t input = 0;
  for (int i = 0; i < ROWS; i++) {
    char name[32];
    snprintf(name, sizeof name, "wire-%02d.bin", i);
    wires[i] = slurp(dir, name, &lengths[i]);
    input += lengths[i];
  }
  struct result compiled = {0};
  if (td_buffa_compile_layout(schema, schema_length, &compiled) != 0 || !compiled.data) return 3;
  uint8_t *output = malloc(capacity);
  struct slab_result slab;
  decode_fn entries[2] = {td_buffa_decode_slab, td_buffa_decode_slab_small};
  const char *names[2] = {"decode_slab", "decode_slab_small"};
  volatile uint64_t sink = 0;
  for (int e = 0; e < 2; e++) {
    // Same work as the image: every message decodes on its first attempt to the same extent.
    for (int i = 0; i < ROWS; i++) {
      int status = entries[e](compiled.data, wires[i], lengths[i], 1, NULL, output, capacity,
                              depth, max_slab, &slab);
      if (status != 0 || slab.bytes != extents[i]) {
        fprintf(stderr, "%s message %d: status %d extent %llu expected %llu\n", names[e], i,
                status, (unsigned long long)slab.bytes, extents[i]);
        return 4;
      }
    }
    double rounds[ROUNDS];
    for (int warm = 0; warm < 20000; warm++) {
      int i = warm % ROWS;
      entries[e](compiled.data, wires[i], lengths[i], 1, NULL, output, capacity, depth, max_slab,
                 &slab);
      sink += slab.bytes;
    }
    for (int r = 0; r < ROUNDS; r++) {
      uint64_t messages = 0;
      double start = now(), elapsed;
      do {
        for (int i = 0; i < ROWS; i++) {
          entries[e](compiled.data, wires[i], lengths[i], 1, NULL, output, capacity, depth,
                     max_slab, &slab);
          sink += slab.bytes;
        }
        messages += ROWS;
        elapsed = now() - start;
      } while (elapsed < 50e6);
      rounds[r] = elapsed / messages;
    }
    double sorted[ROUNDS];
    memcpy(sorted, rounds, sizeof sorted);
    qsort(sorted, ROUNDS, sizeof(double), compare);
    printf("{\"entry\":\"%s\",\"mode\":\"%s\",\"input_bytes_per_row\":%.3f,"
           "\"capacity\":%llu,\"ns_per_row_median\":%.3f,\"ns_per_row_min\":%.3f,"
           "\"ns_per_row_max\":%.3f,\"ns_per_row\":[",
           names[e], argv[2], (double)input / ROWS, capacity, sorted[ROUNDS / 2], sorted[0],
           sorted[ROUNDS - 1]);
    for (int r = 0; r < ROUNDS; r++) printf(r ? ",%.3f" : "%.3f", rounds[r]);
    printf("]}\n");
  }
  td_buffa_release_layout(compiled.data);
  return sink == 0xffffffffffffffffull;
}
