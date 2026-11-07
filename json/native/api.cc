#include "json/native/api.h"
#include "json/native/decoder.h"
#include "json/native/session.h"
#include "json/native/status.h"
#include "json/native/stream.h"
#include "json/native/typed.h"
#include <memory>

static_assert(TD_JSON_OK == truffledata::json::status_ok && TD_JSON_ARGUMENT == truffledata::json::status_argument
              && TD_JSON_LIMIT == truffledata::json::status_limit && TD_JSON_ALLOCATION == truffledata::json::status_allocation
              && TD_JSON_SYNTAX == truffledata::json::status_syntax && TD_JSON_INTERNAL == truffledata::json::status_internal
              && TD_JSON_CONTRACT == truffledata::json::status_contract
              && TD_JSON_TEXT_TOO_SMALL == truffledata::json::status_text_too_small
              && TD_JSON_STREAM_EMPTY == truffledata::json::status_stream_empty
              && TD_JSON_STREAM_TRUNCATED == truffledata::json::status_stream_truncated,
              "api.h and status.h agree");

namespace {
// Owner of a td_json_decode result; retained session results are the other result_owner.
struct handle final : truffledata::json::result_owner {
  truffledata::json::document document;
  myna_value_view_v4 view;
  handle(std::string_view input, myna::value_limits values,
         truffledata::tape::storage_limits storage)
      : document(input, values, storage), view(document.view_v4()) {}
  handle(const char* data, size_t length, size_t capacity, myna::value_limits values,
         truffledata::tape::storage_limits storage)
      : document(data, length, capacity, values, storage), view(document.view_v4()) {}
  void destroy() noexcept override { delete this; }
};
int32_t publish(std::unique_ptr<handle> owned, JsonResult& result) {
  auto stats = owned->document.allocations();
  const auto& spans = owned->document.lexical_spans();
  const auto& hashes = owned->document.key_hashes();
  // Publish only after parsing and validation have completed successfully.
  result = {static_cast<truffledata::json::result_owner*>(owned.get()), &owned->view, stats.copied_input_bytes,
            stats.padded_input_bytes, stats.index_retained_bytes,
            stats.index_peak_bytes, stats.index_allocations,
            stats.parser_input_capacity, sizeof(handle),
            const_cast<uint64_t*>(spans.data()), spans.size(),
            const_cast<uint32_t*>(hashes.data())};
  (void)owned.release();
  return TD_JSON_OK;
}
int32_t translate() { return truffledata::json::current_exception_status(); }
bool aligned(const void* pointer) {
  return pointer && reinterpret_cast<uintptr_t>(pointer) % alignof(JsonResult) == 0;
}
}

extern "C" uint8_t* td_json_fingerprint() {
  static char fingerprint[] = TRUFFLEDATA_JSON_SEAM_ABI;
  return reinterpret_cast<uint8_t*>(fingerprint);
}

extern "C" int32_t td_json_decode(const uint8_t* data, uint64_t length,
    uint64_t max_input_bytes, uint64_t max_index_bytes, uint64_t max_nodes,
    uint64_t max_text_bytes, uint32_t max_depth, void* output) {
  if (!aligned(output)) return TD_JSON_ARGUMENT;
  auto& result = *static_cast<JsonResult*>(output);
  if (result.owner || result.view) return TD_JSON_ARGUMENT;
  result = {};
  if (!data || max_depth > 128) return TD_JSON_ARGUMENT;
  if (length > max_input_bytes || length > UINT32_MAX) return TD_JSON_LIMIT;
  try {
    return publish(std::make_unique<handle>(
        std::string_view(reinterpret_cast<const char*>(data), length),
        myna::value_limits{max_nodes, max_text_bytes, max_depth},
        truffledata::tape::storage_limits{size_t(max_input_bytes), size_t(max_index_bytes)}), result);
  } catch (...) { return translate(); }
}

extern "C" int32_t td_json_decode_adopted(const uint8_t* data, uint64_t length,
    uint64_t capacity, uint64_t max_input_bytes, uint64_t max_index_bytes,
    uint64_t max_nodes, uint64_t max_text_bytes, uint32_t max_depth, void* output) {
  if (!aligned(output)) return TD_JSON_ARGUMENT;
  auto& result = *static_cast<JsonResult*>(output);
  if (result.owner || result.view) return TD_JSON_ARGUMENT;
  result = {};
  if (!data || max_depth > 128 || capacity < length) return TD_JSON_ARGUMENT;
  if (length > max_input_bytes || length > UINT32_MAX) return TD_JSON_LIMIT;
  try {
    return publish(std::make_unique<handle>(
        reinterpret_cast<const char*>(data), size_t(length), size_t(capacity),
        myna::value_limits{max_nodes, max_text_bytes, max_depth},
        truffledata::tape::storage_limits{size_t(max_input_bytes), size_t(max_index_bytes)}), result);
  } catch (...) { return translate(); }
}

extern "C" void td_json_release(void* output) {
  if (!aligned(output)) return;
  auto& result = *static_cast<JsonResult*>(output);
  auto* owned = static_cast<truffledata::json::result_owner*>(result.owner);
  result = {};
  if (owned) owned->destroy();
}

extern "C" int32_t td_json_plan_create(const void* records, uint64_t record_count,
    const void* fields, uint64_t field_count, uint64_t max_name_bytes, void* output) {
  if (!aligned(output)) return TD_JSON_ARGUMENT;
  auto& result = *static_cast<JsonPlan*>(output);
  if (result.owner) return TD_JSON_ARGUMENT;
  if (!record_count || !records
      || reinterpret_cast<uintptr_t>(records) % alignof(JsonRecordPlan))
    return TD_JSON_ARGUMENT;
  if (record_count > TD_JSON_MAX_PLAN_RECORDS || field_count > 64 * record_count)
    return TD_JSON_LIMIT;
  if (field_count && !aligned(fields)) return TD_JSON_ARGUMENT;
  const auto* layout = static_cast<const JsonRecordPlan*>(records);
  uint64_t next = 0;
  // Records partition the field table in order: record i owns
  // fields [fields_offset, fields_offset + field_count).
  for (uint64_t r = 0; r < record_count; ++r) {
    if (layout[r].field_count > 64) return TD_JSON_LIMIT;
    if (layout[r].fields_offset != next || layout[r].field_count > field_count - next)
      return TD_JSON_ARGUMENT;
    next += layout[r].field_count;
  }
  if (next != field_count) return TD_JSON_ARGUMENT;
  try {
    const auto* source = static_cast<const JsonScalarField*>(fields);
    std::vector<std::vector<truffledata::json::scalar_field>> schema(record_count);
    uint64_t names = 0;
    for (uint64_t r = 0; r < record_count; ++r) {
      auto& record = schema[r];
      record.reserve(layout[r].field_count);
      for (uint64_t i = layout[r].fields_offset; i < layout[r].fields_offset + layout[r].field_count; ++i) {
        const auto& field = source[i];
        if (field.kind > 5 || (field.flags & ~7u) || (!field.name && field.name_length))
          return TD_JSON_ARGUMENT;
        if (field.name_length > UINT32_MAX || field.name_length > max_name_bytes - names)
          return TD_JSON_LIMIT;
        names += field.name_length;
        std::string name;
        if (field.name_length) name.assign(reinterpret_cast<const char*>(field.name), field.name_length);
        record.push_back({std::move(name), static_cast<truffledata::json::scalar_kind>(field.kind),
            bool(field.flags & 1), bool(field.flags & 2), field.minimum, field.maximum,
            field.unsigned_maximum, size_t(field.max_utf8_bytes), field.float_bits, field.length_unit,
            field.minimum_length, field.maximum_length, bool(field.flags & 4), field.child});
      }
    }
    auto owned = std::make_unique<truffledata::json::typed_plan>(std::move(schema));
    result.owner = owned.release();
    return TD_JSON_OK;
  } catch (const std::bad_alloc&) { return TD_JSON_ALLOCATION; }
    catch (const std::length_error&) { return TD_JSON_LIMIT; }
    catch (const std::invalid_argument&) { return TD_JSON_ARGUMENT; }
    catch (...) { return TD_JSON_INTERNAL; }
}

extern "C" void td_json_plan_release(void* output) {
  if (!aligned(output)) return;
  auto& result = *static_cast<JsonPlan*>(output);
  auto* owned = static_cast<truffledata::json::typed_plan*>(result.owner);
  result = {};
  delete owned;
}

extern "C" int32_t td_json_typed_decode(const void* plan_record, const uint8_t* data,
    uint64_t length, uint64_t max_input_bytes, uint64_t max_index_bytes, void* output) {
  if (!aligned(output)) return TD_JSON_ARGUMENT;
  auto& result = *static_cast<JsonTypedResult*>(output);
  if (result.owner || result.slots) return TD_JSON_ARGUMENT;
  result = {};
  if (!aligned(plan_record) || !data) return TD_JSON_ARGUMENT;
  auto* plan = static_cast<const truffledata::json::typed_plan*>(static_cast<const JsonPlan*>(plan_record)->owner);
  if (!plan) return TD_JSON_ARGUMENT;
  if (length > UINT32_MAX || length > max_input_bytes) return TD_JSON_LIMIT;
  try {
    auto owned = std::make_unique<truffledata::json::typed_document>(*plan,
        std::string_view(reinterpret_cast<const char*>(data), length),
        truffledata::tape::storage_limits{size_t(max_input_bytes), size_t(max_index_bytes)});
    auto stats = owned->allocations();
    result = {owned.get(), const_cast<JsonScalarSlot*>(owned->slots()), owned->size(),
        owned->present(), owned->nulls(), stats.copied_input_bytes, stats.padded_input_bytes,
        stats.index_retained_bytes, stats.index_peak_bytes, stats.index_allocations,
        stats.parser_input_capacity, sizeof(truffledata::json::typed_document),
        const_cast<JsonRecordEntry*>(owned->records()), owned->record_count()};
    (void)owned.release();
    return TD_JSON_OK;
  } catch (const std::bad_alloc&) { return TD_JSON_ALLOCATION; }
    catch (const std::length_error&) { return TD_JSON_LIMIT; }
    catch (const simdjson::simdjson_error& error) {
      if (error.error() == simdjson::MEMALLOC) return TD_JSON_ALLOCATION;
      if (error.error() == simdjson::CAPACITY || error.error() == simdjson::DEPTH_ERROR) return TD_JSON_LIMIT;
      if (error.error() == simdjson::INCORRECT_TYPE || error.error() == simdjson::NUMBER_OUT_OF_RANGE)
        return TD_JSON_CONTRACT;
      return TD_JSON_SYNTAX;
    }
    catch (const std::invalid_argument&) { return TD_JSON_CONTRACT; }
    catch (...) { return TD_JSON_INTERNAL; }
}

extern "C" void td_json_typed_release(void* output) {
  if (!aligned(output)) return;
  auto& result = *static_cast<JsonTypedResult*>(output);
  auto* owned = static_cast<truffledata::json::typed_document*>(result.owner);
  result = {};
  delete owned;
}

// ---- Sessions (plan Task B1) ----------------------------------------------------------------

namespace {
using truffledata::json::session;
// The 4 KiB bound of the leaf decode: at the probe's ≈ 150 MB/s worst case this is ≈ 30 µs.
constexpr uint64_t critical_limit = TD_JSON_CRITICAL_LIMIT;

constexpr uint32_t known_flags = TD_JSON_WANT_TEXT | TD_JSON_UTF16_INPUT | TD_JSON_LATIN1_INPUT;

// Borrowed: no owner, so td_json_release never frees session storage.
void publish_borrowed(const session& s, const session::document_view& d, JsonResult& result,
                      const uint64_t* table = nullptr, size_t text_bytes = 0) {
  const auto& memory = s.memory();
  result = {nullptr, const_cast<myna_value_view_v4*>(&s.view_v4()), d.input_length, s.input_capacity(),
            memory.current(), memory.peak(), d.index_allocations, s.parser_capacity(),
            sizeof(session), const_cast<uint64_t*>(d.lexical.data()), d.lexical.size(),
            const_cast<uint32_t*>(d.hashes.data()), const_cast<uint64_t*>(table), text_bytes};
}

int32_t session_decode(session& s, const uint8_t* data, uint64_t length, uint32_t flags,
                       uint8_t* text_out, uint64_t text_capacity, JsonResult& result) {
  result = {};
  if (!data || (flags & ~known_flags)) return TD_JSON_ARGUMENT;
  bool text = flags & TD_JSON_WANT_TEXT;
  if (text ? !text_out || reinterpret_cast<uintptr_t>(text_out) % 2 || text_capacity > INT32_MAX
           : text_out || text_capacity)
    return TD_JSON_ARGUMENT;
  bool utf16 = flags & TD_JSON_UTF16_INPUT, latin1 = flags & TD_JSON_LATIN1_INPUT;
  if (utf16 && (latin1 || length % 2 || reinterpret_cast<uintptr_t>(data) % 2)) return TD_JSON_ARGUMENT;
  // UTF-16 input may be up to twice its UTF-8 form; the session checks the transcoded length.
  if (length > (utf16 ? 2ull * UINT32_MAX : uint64_t(UINT32_MAX))) return TD_JSON_LIMIT;
  try {
    auto d = s.decode(reinterpret_cast<const char*>(data), size_t(length),
                      utf16    ? session::encoding::utf16le
                      : latin1 ? session::encoding::latin1
                               : session::encoding::utf8);
    const uint64_t* table = nullptr;
    size_t text_bytes = 0;
    if (text) {
      text_bytes = s.write_text(text_out, size_t(text_capacity), table);
      if (text_bytes == truffledata::tape::text_too_small) return TD_JSON_TEXT_TOO_SMALL;
    }
    publish_borrowed(s, d, result, table, text_bytes);
    return TD_JSON_OK;
  } catch (...) { return translate(); }
}
}

extern "C" void* td_json_session_create(const void* limits) {
  if (!aligned(limits)) return nullptr;
  const auto& l = *static_cast<const JsonLimitsStruct*>(limits);
  if (l.max_depth > 128 || l.max_input_bytes == 0) return nullptr;
  try {
    return new session(myna::value_limits{l.max_nodes, l.max_text_bytes, l.max_depth},
        truffledata::tape::storage_limits{size_t(l.max_input_bytes), size_t(l.max_index_bytes)});
  } catch (...) { return nullptr; }
}

extern "C" int32_t td_json_session_decode(void* handle, const uint8_t* data, uint64_t length,
    uint32_t flags, uint8_t* text_out, uint64_t text_capacity, void* output) {
  if (!handle || !aligned(output)) return TD_JSON_ARGUMENT;
  return session_decode(*static_cast<session*>(handle), data, length, flags, text_out,
                        text_capacity, *static_cast<JsonResult*>(output));
}

extern "C" int32_t td_json_session_decode_small(void* handle, const uint8_t* data, uint64_t length,
    uint32_t flags, uint8_t* text_out, uint64_t text_capacity, void* output) {
  if (!handle || !aligned(output)) return TD_JSON_ARGUMENT;
  if (length > critical_limit) {
    *static_cast<JsonResult*>(output) = {};
    return TD_JSON_ARGUMENT;
  }
  return session_decode(*static_cast<session*>(handle), data, length, flags, text_out,
                        text_capacity, *static_cast<JsonResult*>(output));
}

extern "C" int32_t td_json_session_retain(void* handle, void* output) {
  if (!handle || !aligned(output)) return TD_JSON_ARGUMENT;
  auto& result = *static_cast<JsonResult*>(output);
  if (result.owner || result.view) return TD_JSON_ARGUMENT;
  auto& s = *static_cast<session*>(handle);
  if (!s.live()) return TD_JSON_ARGUMENT;
  // Read before retain: the session forgets the document once its storage moves.
  auto lexical = s.lexical_spans();
  auto hashes = s.key_hashes();
  size_t input = s.input_length();
  try {
    auto* owner = s.retain();
    result = {static_cast<truffledata::json::result_owner*>(owner), &owner->view, input,
              input + simdjson::SIMDJSON_PADDING, owner->bytes(), owner->bytes(), 0, 0,
              sizeof(session::retained), const_cast<uint64_t*>(lexical.data()), lexical.size(),
              const_cast<uint32_t*>(hashes.data())};
    return TD_JSON_OK;
  } catch (...) { return translate(); }
}

extern "C" void td_json_session_reset(void* handle) {
  if (handle) static_cast<session*>(handle)->reset();
}

extern "C" void td_json_session_release(void* handle) {
  delete static_cast<session*>(handle);
}

// ---- Streams (plan Task F1) -----------------------------------------------------------------

extern "C" void* td_json_stream_create(void* handle, uint64_t max_document_bytes, uint64_t batch_bytes) {
  if (!handle) return nullptr;
  try {
    return new truffledata::json::stream(*static_cast<session*>(handle), size_t(max_document_bytes),
                                         size_t(batch_bytes));
  } catch (...) { return nullptr; }
}

extern "C" uint64_t td_json_stream_feed(void* handle, const uint8_t* data, uint64_t length) {
  if (!handle) return 0;
  return static_cast<truffledata::json::stream*>(handle)->feed(data, size_t(length));
}

extern "C" void td_json_stream_finish(void* handle) {
  if (handle) static_cast<truffledata::json::stream*>(handle)->finish();
}

extern "C" int32_t td_json_stream_next(void* handle, void* output) {
  if (!handle || !aligned(output)) return TD_JSON_ARGUMENT;
  auto& result = *static_cast<JsonResult*>(output);
  result = {};
  auto& s = *static_cast<truffledata::json::stream*>(handle);
  session::document_view d{};
  int32_t status = s.next(d);
  if (status == TD_JSON_OK) publish_borrowed(s.owner(), d, result);
  return status;
}

extern "C" int32_t td_json_stream_skip(void* handle) {
  if (!handle) return TD_JSON_ARGUMENT;
  return static_cast<truffledata::json::stream*>(handle)->skip();
}

extern "C" uint64_t td_json_stream_truncated_bytes(void* handle) {
  return handle ? static_cast<truffledata::json::stream*>(handle)->pending() : 0;
}

extern "C" void td_json_stream_release(void* handle) {
  delete static_cast<truffledata::json::stream*>(handle);
}
