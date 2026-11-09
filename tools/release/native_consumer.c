// A C consumer of one release native (tools/release/native_consumer.bzl): it links the release static archive (with
// or without LTO), calls the module's ABI fingerprint (and the JSON Rust bridge probe), then loads the release shared
// library with dlopen and checks it reports the same fingerprint.
#ifndef STATIC_ONLY
#include <dlfcn.h>
#endif
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#define STRINGIFY(x) #x
#define NAME(x) STRINGIFY(x)

extern uint8_t *FINGERPRINT(void);
#ifdef PROBE
extern uint32_t PROBE(void);
#endif

// Seam ABI fingerprints are 64 lowercase hex digits; Rust's is not NUL-terminated.
#define FINGERPRINT_LENGTH 64

static int valid(const char *fingerprint) {
  if (fingerprint == NULL) return 0;
  for (int i = 0; i < FINGERPRINT_LENGTH; i++) {
    char c = fingerprint[i];
    if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return 0;
  }
  return 1;
}

int main(int argc, char **argv) {
  const char *linked = (const char *)FINGERPRINT();
  if (!valid(linked)) {
    fprintf(stderr, "%s: no ABI fingerprint from the static archive\n", NAME(FINGERPRINT));
    return 1;
  }
#ifdef PROBE
  if (PROBE() != 1) {
    fprintf(stderr, "%s: the static archive's Rust bridge is missing\n", NAME(PROBE));
    return 1;
  }
#endif
  #ifdef STATIC_ONLY
  printf("%s: static archive reports %.64s\n", NAME(FINGERPRINT), linked);
  return 0;
  #else
  if (argc != 2) {
    fprintf(stderr, "usage: %s SHARED_LIBRARY\n", argv[0]);
    return 2;
  }
  void *library = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
  if (library == NULL) {
    fprintf(stderr, "dlopen %s: %s\n", argv[1], dlerror());
    return 1;
  }
  uint8_t *(*shared_fingerprint)(void) = (uint8_t * (*)(void)) dlsym(library, NAME(FINGERPRINT));
  if (shared_fingerprint == NULL) {
    fprintf(stderr, "dlsym %s: %s\n", NAME(FINGERPRINT), dlerror());
    return 1;
  }
  const char *shared = (const char *)shared_fingerprint();
  if (!valid(shared) || memcmp(linked, shared, FINGERPRINT_LENGTH) != 0) {
    fprintf(stderr, "%s: static archive reports %.64s, shared library %.64s\n", NAME(FINGERPRINT), linked,
            valid(shared) ? shared : "(invalid)");
    return 1;
  }
  printf("%s: static and shared report %.64s\n", NAME(FINGERPRINT), linked);
  return 0;
  #endif
}
