// Writes the DescriptorSet that bebopc builds for the given files: bebop_parse, then
// bebop_descriptor_build(BEBOP_DESC_FLAG_NONE), then bebop_descriptor_encode, as bebopc_compile does.
// Usage: descset OUT [-I DIR]... FILE...
#include <bebop.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

static bebop_file_result_t rd(const char* path, void* ctx) {
  (void)ctx;
  FILE* f = fopen(path, "rb");
  if (!f) return (bebop_file_result_t){.error = "File not found"};
  fseek(f, 0, SEEK_END);
  long len = ftell(f);
  fseek(f, 0, SEEK_SET);
  char* c = malloc((size_t)len + 1);
  size_t n = fread(c, 1, (size_t)len, f);
  fclose(f);
  c[n] = 0;
  return (bebop_file_result_t){.content = c, .content_len = n};
}

static void* al(void* ptr, size_t old, size_t n, void* ctx) {
  (void)old; (void)ctx;
  if (n == 0) { free(ptr); return NULL; }
  return realloc(ptr, n);
}

static bool ex(const char* path, void* ctx) {
  (void)ctx;
  struct stat st;
  return stat(path, &st) == 0 && S_ISREG(st.st_mode);
}

int main(int argc, char** argv) {
  if (argc < 3) return 2;
  const char* out = argv[1];
  const char* inc[16];
  unsigned ninc = 0;
  const char* files[64];
  size_t nfiles = 0;
  for (int i = 2; i < argc; i++) {
    if (strcmp(argv[i], "-I") == 0 && i + 1 < argc) inc[ninc++] = argv[++i];
    else files[nfiles++] = argv[i];
  }
  bebop_host_t host = {0};
  host.allocator.alloc = al;
  host.file_reader.read = rd;
  host.file_reader.exists = ex;
  host.includes.paths = inc;
  host.includes.count = ninc;
  bebop_context_t* ctx = bebop_context_create(&host);
  bebop_parse_result_t* result = NULL;
  bebop_status_t st = bebop_parse(ctx, files, nfiles, &result);
  if (st == BEBOP_FATAL || !result) { fprintf(stderr, "fatal parse\n"); return 1; }
  uint32_t errors = bebop_result_error_count(result);
  if (errors) {
    for (uint32_t i = 0; i < bebop_result_diagnostic_count(result); i++) {
      const bebop_diagnostic_t* d = bebop_result_diagnostic_at(result, i);
      fprintf(stderr, "diag: %s\n", bebop_diagnostic_message(d));
    }
    return 1;
  }
  bebop_descriptor_t* desc = NULL;
  if (bebop_descriptor_build(result, BEBOP_DESC_FLAG_NONE, &desc) != BEBOP_OK) return 1;
  const uint8_t* buf; size_t len;
  if (bebop_descriptor_encode(desc, &buf, &len) != BEBOP_OK) return 1;
  FILE* f = fopen(out, "wb");
  fwrite(buf, 1, len, f);
  fclose(f);
  fprintf(stderr, "%zu bytes\n", len);
  return 0;
}
