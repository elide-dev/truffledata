// Exercise the YAML C ABI through the isolated published archive: a C consumer with no Bazel C++ deps.
#include "yaml/native/api.h"
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#define CHECK(x) do { if (!(x)) { fprintf(stderr, "check failed at line %d\n", __LINE__); return 1; } } while (0)

static int32_t decode(void *session, const char *text, struct YamlResult *result) {
  memset(result, 0, sizeof(*result));
  return td_yaml_session_decode(session, (const uint8_t *)text, strlen(text), 0, NULL, 0, result);
}

int main(void) {
  CHECK(strcmp((const char *)td_yaml_fingerprint(), TRUFFLEDATA_YAML_SEAM_ABI) == 0);
  struct YamlOptionsStruct options;
  memset(&options, 0, sizeof(options));
  options.max_input_bytes = 1u << 20;
  options.max_index_bytes = 1u << 20;
  options.max_nodes = 1u << 16;
  options.max_text_bytes = 1u << 20;
  options.max_aliases = 64;
  options.max_alias_expansion = 1u << 16;
  options.max_depth = 64;
  void *session = td_yaml_session_create(&options);
  CHECK(session != NULL);

  // A borrowed document, then the same document retained past a reset.
  struct YamlResult result;
  CHECK(decode(session, "name: td\nitems: &a [1, 2.5, true, ~]\nalias: *a\n", &result) == TD_YAML_OK);
  CHECK(result.owner == NULL && result.view != NULL && result.yaml_meta != NULL);
  CHECK(result.document_flags & TD_YAML_DOC_PRESENT);
  CHECK(result.anchor_count == 1);
  struct YamlResult kept;
  memset(&kept, 0, sizeof(kept));
  CHECK(td_yaml_session_retain(session, &kept) == TD_YAML_OK);
  CHECK(kept.owner != NULL && kept.view != NULL && kept.anchor_count == 1);
  td_yaml_session_reset(session);
  td_yaml_release(&kept);

  // A failure publishes only its error and leaves the session reusable.
  CHECK(decode(session, "key: [unterminated\n", &result) == TD_YAML_SYNTAX);
  CHECK(result.view == NULL && result.error_line >= 1 && result.error_message_length > 0);
  CHECK(decode(session, "--- 1\n--- 2\n", &result) == TD_YAML_MULTIPLE_DOCUMENTS);

  // A two-document stream, one document at a time.
  void *stream = td_yaml_stream_create(session, 1u << 16, 1u << 16);
  CHECK(stream != NULL);
  const char documents[] = "--- a\n--- [b]\n";
  CHECK(td_yaml_stream_feed(stream, (const uint8_t *)documents, sizeof(documents) - 1) == sizeof(documents) - 1);
  td_yaml_stream_finish(stream);
  int count = 0;
  for (;;) {
    memset(&result, 0, sizeof(result));
    int32_t status = td_yaml_stream_next(stream, 0, &result);
    if (status == TD_YAML_STREAM_EMPTY) break;
    CHECK(status == TD_YAML_OK && result.view != NULL);
    ++count;
  }
  CHECK(count == 2);
  td_yaml_stream_release(stream);
  td_yaml_session_release(session);
  printf("td_yaml: isolated archive reports %s\n", (const char *)td_yaml_fingerprint());
  return 0;
}
