// libfyaml event loop in C (its public header does not compile as C++ on this toolchain).
#include <libfyaml.h>
#include <stdlib.h>
#include <stdio.h>

// kinds: 0 doc start, 1 doc end, 2 seq start, 3 map start, 4 end, 5 scalar, 6 alias
typedef void (*fy_probe_cb)(void *user, int kind, const char *text, size_t len, int plain);

void *fy_probe_create(void) {
  struct fy_parse_cfg cfg = {0};
  cfg.flags = FYPCF_QUIET | FYPCF_DEFAULT_VERSION_1_2;
  return fy_parser_create(&cfg);
}

void fy_probe_destroy(void *p) { fy_parser_destroy((struct fy_parser *)p); }

// FY_FRESH=1 creates a parser per run instead of fy_parser_reset() reuse.
unsigned long long fy_probe_run(void *p, const char *src, size_t n, fy_probe_cb cb, void *user) {
  static int fresh = -1;
  if (fresh < 0) fresh = getenv("FY_FRESH") != NULL;
  struct fy_parser *fyp = fresh ? (struct fy_parser *)fy_probe_create() : (struct fy_parser *)p;
  if (!fresh) fy_parser_reset(fyp);
  if (fy_parser_set_string(fyp, src, n)) abort();
  unsigned long long count = 0;
  for (;;) {
    struct fy_event *ev = fy_parser_parse(fyp);
    if (!ev) break;
    enum fy_event_type t = ev->type;
    ++count;
    size_t len = 0;
    const char *text = NULL;
    switch (t) {
      case FYET_DOCUMENT_START: if (cb) cb(user, 0, 0, 0, 0); break;
      case FYET_DOCUMENT_END: if (cb) cb(user, 1, 0, 0, 0); break;
      case FYET_SEQUENCE_START: if (cb) cb(user, 2, 0, 0, 0); break;
      case FYET_MAPPING_START: if (cb) cb(user, 3, 0, 0, 0); break;
      case FYET_SEQUENCE_END: case FYET_MAPPING_END: if (cb) cb(user, 4, 0, 0, 0); break;
      case FYET_SCALAR:
        text = fy_token_get_text(ev->scalar.value, &len);
        count += len;
        if (cb) cb(user, 5, text, len, fy_token_scalar_style(ev->scalar.value) == FYSS_PLAIN && !ev->scalar.tag);
        break;
      case FYET_ALIAS: if (cb) cb(user, 6, 0, 0, 0); break;
      default: break;
    }
    fy_parser_event_free(fyp, ev);
    if (t == FYET_STREAM_END) break;
  }
  if (fy_parser_get_stream_error(fyp)) { fprintf(stderr, "libfyaml error\n"); abort(); }
  if (fresh) fy_parser_destroy(fyp);
  return count;
}
