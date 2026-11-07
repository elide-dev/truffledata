#include "yaml/native/api.h"

#include "yaml/native/errors.h"
#include "yaml/native/session.h"
#include "yaml/native/stream.h"

#include <cstddef>
#include <cstring>
#include <new>
#include <stdexcept>

namespace {
using truffledata::yaml::error;
using truffledata::yaml::session;

bool aligned(const void* pointer) {
  return pointer && reinterpret_cast<uintptr_t>(pointer) % alignof(YamlResult) == 0;
}

void message(YamlResult& result, const char* text, size_t length, uint64_t line = 0, uint64_t column = 0) {
  if (length > error::capacity - 1) length = error::capacity - 1;
  char* out = reinterpret_cast<char*>(&result) + offsetof(YamlResult, error_message);
  std::memcpy(out, text, length);
  out[length] = 0;
  result.error_message_length = uint32_t(length);
  result.error_line = line;
  result.error_column = column;
}

// Maps the active exception to a status and records its message, line and column. The result is
// otherwise zero: nothing is published.
int32_t translate(YamlResult& result) {
  result = {};
  try {
    throw;
  } catch (const error& e) {
    message(result, e.text, e.length, e.line, e.column);
    return e.code;
  } catch (const std::bad_alloc&) {
    message(result, "native allocation failure", 25);
    return TD_YAML_ALLOCATION;
  } catch (const std::length_error& e) {
    char text[error::capacity];
    int n = std::snprintf(text, sizeof text, "YAML resource limit: %s", e.what());
    message(result, text, n < 0 ? 0 : size_t(n));
    return TD_YAML_LIMIT;
  } catch (const std::exception& e) {
    message(result, e.what(), std::strlen(e.what()));
    return TD_YAML_INTERNAL;
  } catch (...) {
    message(result, "unknown native failure", 22);
    return TD_YAML_INTERNAL;
  }
}

void tables(YamlResult& result, const session::document_view& d) {
  result.yaml_meta = const_cast<uint32_t*>(d.meta.data());
  result.tags = const_cast<truffledata::yaml::span_entry*>(d.tags.data());
  result.tag_count = d.tags.size();
  result.anchors = const_cast<truffledata::yaml::anchor_entry*>(d.anchors.data());
  result.anchor_count = d.anchors.size();
  result.alias_targets = const_cast<uint64_t*>(d.alias_targets.data());
  result.alias_count = d.alias_targets.size();
  result.strings = reinterpret_cast<uint8_t*>(const_cast<char*>(d.strings.data()));
  result.strings_length = d.strings.size();
  result.directives = const_cast<truffledata::yaml::directive_entry*>(d.directives.data());
  result.directive_count = d.directives.size();
}
static_assert(sizeof(truffledata::yaml::span_entry) == sizeof(YamlSpan), "YamlSpan layout");
static_assert(sizeof(truffledata::yaml::anchor_entry) == sizeof(YamlAnchor) &&
                  offsetof(truffledata::yaml::anchor_entry, offset) == offsetof(YamlAnchor, offset),
              "YamlAnchor layout");
static_assert(sizeof(truffledata::yaml::directive_entry) == sizeof(YamlTagDirective), "YamlTagDirective layout");

void publish_document(YamlResult& result, const session::document_view& d, const session& s) {
  result.view = const_cast<myna_value_view*>(&s.view());
  result.copied_input_bytes = d.input_length;
  result.padded_input_bytes = s.input_capacity();
  result.index_retained_bytes = s.memory().current();
  result.index_peak_bytes = s.memory().peak();
  result.index_allocations = d.index_allocations;
  result.parser_input_capacity = 0;
  result.control_bytes = sizeof(session);
  result.lexical = const_cast<uint64_t*>(d.lexical.data());
  result.lexical_count = d.lexical.size();
  result.key_hashes = const_cast<uint32_t*>(d.hashes.data());
  tables(result, d);
  result.source = reinterpret_cast<uint8_t*>(const_cast<char*>(d.source));
  result.source_length = d.source_length;
  result.yaml_version = d.version;
  result.document_flags = d.flags;
}

bool valid_options(const YamlOptionsStruct& o) {
  return o.max_depth <= 128 && o.max_input_bytes != 0 && o.schema <= TD_YAML_SCHEMA_YAML_1_1 &&
         o.duplicate_keys <= TD_YAML_DUPLICATE_KEYS_PAIRS && o.merge_keys <= 1 && o.aliases <= 1 &&
         o.unknown_tags <= 1 && o.version_directive <= 1 && o.complex_keys <= TD_YAML_COMPLEX_KEYS_PAIRS;
}
}  // namespace

extern "C" uint8_t* td_yaml_fingerprint() {
  static char fingerprint[] = TRUFFLEDATA_YAML_SEAM_ABI;
  return reinterpret_cast<uint8_t*>(fingerprint);
}

extern "C" void td_yaml_release(void* output) {
  if (!aligned(output)) return;
  auto& result = *static_cast<YamlResult*>(output);
  auto* owned = static_cast<truffledata::yaml::result_owner*>(result.owner);
  result = {};
  if (owned) owned->destroy();
}

extern "C" void* td_yaml_session_create(const void* options) {
  if (!aligned(options)) return nullptr;
  const auto& o = *static_cast<const YamlOptionsStruct*>(options);
  if (!valid_options(o)) return nullptr;
  try {
    truffledata::yaml::session_options s;
    s.rules.schema = truffledata::yaml::schema(o.schema);
    s.rules.last_wins = o.duplicate_keys == TD_YAML_DUPLICATE_KEYS_LAST_WINS;
    s.rules.duplicate_keys_pairs = o.duplicate_keys == TD_YAML_DUPLICATE_KEYS_PAIRS;
    s.rules.complex_keys_pairs = o.complex_keys == TD_YAML_COMPLEX_KEYS_PAIRS;
    s.rules.merge_keys = o.merge_keys != 0;
    s.rules.reject_aliases = o.aliases == TD_YAML_ALIASES_REJECT;
    s.rules.reject_unknown_tags = o.unknown_tags == TD_YAML_UNKNOWN_TAGS_REJECT;
    s.rules.honor_version = o.version_directive == TD_YAML_VERSION_DIRECTIVE_HONOR;
    s.rules.max_aliases = o.max_aliases;
    s.values = myna::value_limits{o.max_nodes, o.max_text_bytes, o.max_depth};
    s.storage = truffledata::tape::storage_limits{size_t(o.max_input_bytes), size_t(o.max_index_bytes)};
    s.max_alias_expansion = o.max_alias_expansion;
    return new session(s);
  } catch (...) {
    return nullptr;
  }
}

extern "C" int32_t td_yaml_session_decode(void* handle, const uint8_t* data, uint64_t length, uint32_t flags,
                                          uint8_t* text_out, uint64_t text_capacity, void* output) {
  if (!handle || !aligned(output)) return TD_YAML_ARGUMENT;
  auto& result = *static_cast<YamlResult*>(output);
  result = {};
  if (!data || (flags & ~uint32_t(TD_YAML_RETAIN_SOURCE)) || text_out || text_capacity) {
    message(result, "invalid YAML decode arguments", 29);
    return TD_YAML_ARGUMENT;
  }
  auto& s = *static_cast<session*>(handle);
  if (length > UINT32_MAX || length > s.options().storage.input_bytes) {
    message(result, "YAML resource limit: input bytes", 32);
    return TD_YAML_LIMIT;
  }
  try {
    auto d = s.decode(data, size_t(length), flags & TD_YAML_RETAIN_SOURCE ? session::retain_source : 0);
    publish_document(result, d, s);
    return TD_YAML_OK;
  } catch (...) {
    return translate(result);
  }
}

extern "C" int32_t td_yaml_session_retain(void* handle, void* output) {
  if (!handle || !aligned(output)) return TD_YAML_ARGUMENT;
  auto& result = *static_cast<YamlResult*>(output);
  if (result.owner || result.view) return TD_YAML_ARGUMENT;
  auto& s = *static_cast<session*>(handle);
  if (!s.live()) return TD_YAML_ARGUMENT;
  // Read before retain: the session forgets the document once its storage moves.
  auto d = s.current(s.input_length(), 0);
  size_t input = s.input_length();
  try {
    auto* owner = s.retain();
    result = {};
    result.owner = static_cast<truffledata::yaml::result_owner*>(owner);
    result.view = &owner->view;
    result.copied_input_bytes = input;
    result.padded_input_bytes = owner->input_capacity();
    result.index_retained_bytes = owner->bytes();
    result.index_peak_bytes = owner->bytes();
    result.control_bytes = sizeof(session::retained);
    result.lexical = const_cast<uint64_t*>(d.lexical.data());
    result.lexical_count = d.lexical.size();
    result.key_hashes = const_cast<uint32_t*>(d.hashes.data());
    tables(result, d);
    result.source = reinterpret_cast<uint8_t*>(const_cast<char*>(d.source));
    result.source_length = d.source_length;
    result.yaml_version = d.version;
    result.document_flags = d.flags;
    return TD_YAML_OK;
  } catch (...) {
    return translate(result);
  }
}

extern "C" void td_yaml_session_reset(void* handle) {
  if (handle) static_cast<session*>(handle)->reset();
}

extern "C" void td_yaml_session_release(void* handle) { delete static_cast<session*>(handle); }

// ---- Streams (plan Task 3) -------------------------------------------------------------------

extern "C" void* td_yaml_stream_create(void* handle, uint64_t max_document_bytes, uint64_t buffer_bytes) {
  if (!handle || max_document_bytes == 0 || max_document_bytes > UINT32_MAX) return nullptr;
  try {
    return new truffledata::yaml::stream(*static_cast<session*>(handle), size_t(max_document_bytes),
                                         size_t(buffer_bytes < (uint64_t(1) << 40) ? buffer_bytes : uint64_t(1) << 40));
  } catch (...) {
    return nullptr;
  }
}

extern "C" uint64_t td_yaml_stream_feed(void* handle, const uint8_t* data, uint64_t length) {
  if (!handle || (!data && length)) return 0;
  try {
    return static_cast<truffledata::yaml::stream*>(handle)->feed(data, size_t(length));
  } catch (...) {
    return 0;  // allocation failure: nothing accepted
  }
}

extern "C" void td_yaml_stream_finish(void* handle) {
  if (handle) static_cast<truffledata::yaml::stream*>(handle)->finish();
}

extern "C" int32_t td_yaml_stream_next(void* handle, void* output) {
  if (!handle || !aligned(output)) return TD_YAML_ARGUMENT;
  auto& result = *static_cast<YamlResult*>(output);
  result = {};
  auto& st = *static_cast<truffledata::yaml::stream*>(handle);
  session::document_view d;
  int32_t status = st.next(&d);
  if (status == TD_YAML_OK) {
    publish_document(result, d, st.owner());
    return status;
  }
  if (const auto& e = st.last_error()) message(result, e->text, e->length, e->line, e->column);
  return status;
}

extern "C" void td_yaml_stream_release(void* handle) { delete static_cast<truffledata::yaml::stream*>(handle); }
