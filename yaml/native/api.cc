#include "yaml/native/api.h"

#include "yaml/native/errors.h"
#include "yaml/native/session.h"
#include "yaml/native/stream.h"
#include "yaml/native/typed.h"

#include <cstddef>
#include <cstring>
#include <new>
#include <stdexcept>

namespace {
using truffledata::yaml::error;
using yaml_session = truffledata::yaml::session;

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

void tables(YamlResult& result, const yaml_session::document_view& d) {
  result.yaml_meta = const_cast<uint32_t*>(d.meta.data());
  result.tags = const_cast<truffledata::yaml::span_entry*>(d.tags.data());
  result.tag_count = d.tags.size();
  result.anchors = const_cast<truffledata::yaml::anchor_entry*>(d.anchors.data());
  result.anchor_count = d.anchors.size();
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

void publish_document(YamlResult& result, const yaml_session::document_view& d, const yaml_session& s) {
  result.view = const_cast<td_tape_view*>(&s.view());
  result.copied_input_bytes = d.input_length;
  result.padded_input_bytes = s.input_capacity();
  result.index_retained_bytes = s.memory().current();
  result.index_peak_bytes = s.memory().peak();
  result.index_allocations = d.index_allocations;
  result.parser_input_capacity = 0;
  result.control_bytes = sizeof(yaml_session);
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
truffledata::yaml::session_options session_options(const YamlOptionsStruct& o) {
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
  return s;
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
    return new yaml_session(session_options(o));
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
  auto& s = *static_cast<yaml_session*>(handle);
  if (length > UINT32_MAX || length > s.options().storage.input_bytes) {
    message(result, "YAML resource limit: input bytes", 32);
    return TD_YAML_LIMIT;
  }
  try {
    auto d = s.decode(data, size_t(length), flags & TD_YAML_RETAIN_SOURCE ? yaml_session::retain_source : 0);
    publish_document(result, d, s);
    return TD_YAML_OK;
  } catch (...) {
    return translate(result);
  }
}

extern "C" int32_t td_yaml_session_write_text(void* handle, uint8_t* text_out,
                                                uint64_t capacity, void* output) {
  if (!handle || !aligned(output)) return TD_YAML_ARGUMENT;
  auto& result = *static_cast<YamlTextResult*>(output);
  result = {};
  auto& s = *static_cast<yaml_session*>(handle);
  if (!s.live() || (!text_out && capacity != 0) || (reinterpret_cast<uintptr_t>(text_out) & 1))
    return TD_YAML_ARGUMENT;
  try {
    result.required_capacity = s.text_capacity();
    if (!text_out) return TD_YAML_OK;
    if (capacity < result.required_capacity) return TD_YAML_TEXT_TOO_SMALL;
    const uint64_t* table = nullptr;
    result.bytes_written = s.write_text(text_out, size_t(capacity), table);
    result.table = const_cast<uint64_t*>(table);
    return TD_YAML_OK;
  } catch (...) {
    result = {};
    YamlResult diagnostic{};
    return translate(diagnostic);
  }
}

extern "C" int32_t td_yaml_session_retain(void* handle, void* output) {
  if (!handle || !aligned(output)) return TD_YAML_ARGUMENT;
  auto& result = *static_cast<YamlResult*>(output);
  if (result.owner || result.view) return TD_YAML_ARGUMENT;
  auto& s = *static_cast<yaml_session*>(handle);
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
    result.control_bytes = sizeof(yaml_session::retained);
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
  if (handle) static_cast<yaml_session*>(handle)->reset();
}

extern "C" void td_yaml_session_release(void* handle) { delete static_cast<yaml_session*>(handle); }

// ---- Streams (plan Task 3) -------------------------------------------------------------------

extern "C" void* td_yaml_stream_create(void* handle, uint64_t max_document_bytes, uint64_t buffer_bytes) {
  if (!handle || max_document_bytes == 0 || max_document_bytes > UINT32_MAX) return nullptr;
  try {
    return new truffledata::yaml::stream(*static_cast<yaml_session*>(handle), size_t(max_document_bytes),
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
  yaml_session::document_view d;
  int32_t status = st.next(&d);
  if (status == TD_YAML_OK) {
    publish_document(result, d, st.owner());
    return status;
  }
  if (const auto& e = st.last_error()) message(result, e->text, e->length, e->line, e->column);
  return status;
}

extern "C" void td_yaml_stream_release(void* handle) { delete static_cast<truffledata::yaml::stream*>(handle); }

namespace {
struct yaml_plan {
  truffledata::yaml::typed_plan plan;
  truffledata::yaml::session_options options;
  yaml_plan(std::vector<std::vector<truffledata::yaml::scalar_field>> fields,
            truffledata::yaml::session_options options)
      : plan(std::move(fields), "YAML"), options(options) {
    // Closed typed records always reject duplicate fields, regardless of generic last-wins policy.
    this->options.rules.last_wins = false;
    this->options.rules.duplicate_keys_pairs = false;
  }
};
static_assert(sizeof(truffledata::yaml::scalar_slot) == sizeof(YamlScalarSlot));
static_assert(offsetof(truffledata::yaml::scalar_slot, lexical_length) == offsetof(YamlScalarSlot, lexical_length));
static_assert(sizeof(truffledata::yaml::record_entry) == sizeof(YamlRecordEntry));
static_assert(offsetof(truffledata::yaml::record_entry, plan) == offsetof(YamlRecordEntry, plan));
} // namespace

extern "C" int32_t td_yaml_plan_create(const void* records, uint64_t record_count,
    const void* fields, uint64_t field_count, uint64_t max_name_bytes, const void* options, void* output) {
  if (!aligned(output) || !aligned(options) || !valid_options(*static_cast<const YamlOptionsStruct*>(options)))
    return TD_YAML_ARGUMENT;
  auto& result = *static_cast<YamlPlan*>(output);
  if (result.owner) return TD_YAML_ARGUMENT;
  if (!record_count || !records
      || reinterpret_cast<uintptr_t>(records) % alignof(YamlRecordPlan))
    return TD_YAML_ARGUMENT;
  if (record_count > 65536 || field_count > 64 * record_count)
    return TD_YAML_LIMIT;
  if (field_count && !aligned(fields)) return TD_YAML_ARGUMENT;
  const auto* layout = static_cast<const YamlRecordPlan*>(records);
  uint64_t next = 0;
  // Records partition the field table in order: record i owns
  // fields [fields_offset, fields_offset + field_count).
  for (uint64_t r = 0; r < record_count; ++r) {
    if (layout[r].field_count > 64) return TD_YAML_LIMIT;
    if (layout[r].fields_offset != next || layout[r].field_count > field_count - next)
      return TD_YAML_ARGUMENT;
    next += layout[r].field_count;
  }
  if (next != field_count) return TD_YAML_ARGUMENT;
  try {
    const auto* source = static_cast<const YamlScalarField*>(fields);
    std::vector<std::vector<truffledata::yaml::scalar_field>> schema(record_count);
    uint64_t names = 0;
    for (uint64_t r = 0; r < record_count; ++r) {
      auto& record = schema[r];
      record.reserve(layout[r].field_count);
      for (uint64_t i = layout[r].fields_offset; i < layout[r].fields_offset + layout[r].field_count; ++i) {
        const auto& field = source[i];
        if (field.kind > 5 || (field.flags & ~7u) || (!field.name && field.name_length))
          return TD_YAML_ARGUMENT;
        if (field.name_length > UINT32_MAX || field.name_length > max_name_bytes - names)
          return TD_YAML_LIMIT;
        names += field.name_length;
        std::string name;
        if (field.name_length) name.assign(reinterpret_cast<const char*>(field.name), field.name_length);
        record.push_back({std::move(name), static_cast<truffledata::yaml::scalar_kind>(field.kind),
            bool(field.flags & 1), bool(field.flags & 2), field.minimum, field.maximum,
            field.unsigned_maximum, size_t(field.max_utf8_bytes), field.float_bits, field.length_unit,
            field.minimum_length, field.maximum_length, bool(field.flags & 4), field.child});
      }
    }
    auto owned = std::make_unique<yaml_plan>(std::move(schema), session_options(*static_cast<const YamlOptionsStruct*>(options)));
    result.owner = owned.release();
    return TD_YAML_OK;
  } catch (const std::bad_alloc&) { return TD_YAML_ALLOCATION; }
    catch (const std::length_error&) { return TD_YAML_LIMIT; }
    catch (const std::invalid_argument&) { return TD_YAML_ARGUMENT; }
    catch (...) { return TD_YAML_INTERNAL; }
}

extern "C" void td_yaml_plan_release(void* output) {
  if (!aligned(output)) return;
  auto& result = *static_cast<YamlPlan*>(output);
  auto* owned = static_cast<yaml_plan*>(result.owner);
  result = {};
  delete owned;
}

extern "C" int32_t td_yaml_typed_decode(const void* plan_record, const uint8_t* data,
    uint64_t length, void* output) {
  if (!aligned(output)) return TD_YAML_ARGUMENT;
  auto& result = *static_cast<YamlTypedResult*>(output);
  if (result.owner || result.slots) return TD_YAML_ARGUMENT;
  result = {};
  if (!aligned(plan_record) || !data) return TD_YAML_ARGUMENT;
  auto* plan = static_cast<const yaml_plan*>(static_cast<const YamlPlan*>(plan_record)->owner);
  if (!plan) return TD_YAML_ARGUMENT;
  try {
    auto owned = std::make_unique<truffledata::yaml::typed_document>(plan->plan,
        std::string_view(reinterpret_cast<const char*>(data), length), plan->options);
    auto stats = owned->allocations();
    result.owner = owned.get();
    result.slots = const_cast<truffledata::yaml::scalar_slot*>(owned->slots());
    result.count = owned->size();
    result.present = owned->present(); result.nulls = owned->nulls();
    result.copied_input_bytes = stats.copied_input_bytes;
    result.padded_input_bytes = stats.padded_input_bytes;
    result.index_retained_bytes = stats.index_retained_bytes;
    result.index_peak_bytes = stats.index_peak_bytes;
    result.index_allocations = stats.index_allocations;
    result.control_bytes = sizeof(truffledata::yaml::typed_document);
    result.records = const_cast<truffledata::yaml::record_entry*>(owned->records());
    result.record_count = owned->record_count();
    (void)owned.release();
    return TD_YAML_OK;
  } catch (const std::invalid_argument& e) {
    auto* text = reinterpret_cast<char*>(&result) + offsetof(YamlTypedResult, error_message);
    size_t length = std::min(std::strlen(e.what()), size_t(255));
    std::memcpy(text, e.what(), length); text[length] = 0;
    result.error_message_length = uint32_t(length);
    return TD_YAML_CONTRACT;
  } catch (...) {
    YamlResult error{};
    int status = translate(error);
    result.error_line = error.error_line; result.error_column = error.error_column;
    result.error_message_length = error.error_message_length;
    std::memcpy(reinterpret_cast<char*>(&result) + offsetof(YamlTypedResult, error_message),
                reinterpret_cast<const char*>(&error) + offsetof(YamlResult, error_message), 256);
    return status;
  }
}

extern "C" void td_yaml_typed_release(void* output) {
  if (!aligned(output)) return;
  auto& result = *static_cast<YamlTypedResult*>(output);
  auto* owned = static_cast<truffledata::yaml::typed_document*>(result.owner);
  result = {};
  delete owned;
}
