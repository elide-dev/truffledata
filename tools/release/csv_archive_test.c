// Exercise every public entry point through the isolated published archive.
#include "csv/native/generated/seam.h"
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#define CHECK(x) do { if (!(x)) { fprintf(stderr, "check failed at line %d\n", __LINE__); return 1; } } while (0)
int main(void) {
  CHECK(strcmp((const char *)td_csv_fingerprint(), TRUFFLEDATA_CSV_SEAM_ABI) == 0);
  const uint8_t input[] = "a,\"b\"\"c\"\r\nd";
  const int32_t format = ',' | ('"' << 8) | (1 << 16);
  int32_t output[sizeof(input)];
  for (int32_t kernel = 0; kernel <= 5; ++kernel) {
    if (td_csv_kernel(kernel) < 0) continue;
    CHECK(td_csv_index(input, sizeof(input) - 1, output, format, kernel, 0) == (3 << 8 | 16));
    CHECK(output[0] == 1 && (uint32_t)output[1] == (8u | (1u << 31) | (1u << 30)) && output[2] == 9);
    CHECK(td_csv_index_leaf(input, sizeof(input) - 1, output, format, kernel, 0) == (3 << 8 | 16));
  }
  CHECK(td_csv_validate_utf8(input, sizeof(input) - 1) == -1);
  const uint8_t invalid[] = {'x', 0xff};
  CHECK(td_csv_validate_utf8(invalid, sizeof(invalid)) == 1);
  return 0;
}
