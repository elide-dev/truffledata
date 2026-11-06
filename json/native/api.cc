#include "json/native/api.h"
#include "json/native/decoder.h"
#include "json/native/typed.h"
#include <memory>

namespace {
struct handle {
  truffledata::json::document document;
  myna_value_view view;
  handle(std::string_view input, myna::value_limits values,
         truffledata::json::storage_limits storage)
      : document(input, values, storage), view(document.view()) {}
  handle(const char* data, size_t length, size_t capacity, myna::value_limits values,
         truffledata::json::storage_limits storage)
      : document(data, length, capacity, values, storage), view(document.view()) {}
};
int32_t publish(std::unique_ptr<handle> owned, JsonResult& result) {
  auto stats = owned->document.allocations();
  const auto& spans = owned->document.lexical_spans();
  // Publish only after parsing and validation have completed successfully.
  result = {owned.get(), &owned->view, stats.copied_input_bytes,
            stats.padded_input_bytes, stats.index_retained_bytes,
            stats.index_peak_bytes, stats.index_allocations,
            stats.parser_input_capacity, sizeof(handle),
            const_cast<uint64_t*>(spans.data()), spans.size()};
  (void)owned.release();
  return TD_JSON_OK;
}
int32_t translate() {
  try { throw; }
  catch (const std::bad_alloc&) { return TD_JSON_ALLOCATION; }
  catch (const std::length_error&) { return TD_JSON_LIMIT; }
  catch (const simdjson::simdjson_error& error) {
    if (error.error() == simdjson::MEMALLOC) return TD_JSON_ALLOCATION;
    if (error.error() == simdjson::CAPACITY || error.error() == simdjson::DEPTH_ERROR)
      return TD_JSON_LIMIT;
    return TD_JSON_SYNTAX;
  }
  catch (const std::invalid_argument&) { return TD_JSON_SYNTAX; }
  catch (...) { return TD_JSON_INTERNAL; }
}
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
        truffledata::json::storage_limits{size_t(max_input_bytes), size_t(max_index_bytes)}), result);
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
        truffledata::json::storage_limits{size_t(max_input_bytes), size_t(max_index_bytes)}), result);
  } catch (...) { return translate(); }
}

extern "C" void td_json_release(void* output) {
  if (!aligned(output)) return;
  auto& result = *static_cast<JsonResult*>(output);
  auto* owned = static_cast<handle*>(result.owner);
  result = {};
  delete owned;
}

extern "C" int32_t td_json_plan_create(const void* fields, uint64_t count,
    uint64_t max_name_bytes, void* output) {
  if (!aligned(output)) return TD_JSON_ARGUMENT;
  auto& result = *static_cast<JsonPlan*>(output);
  if (result.owner) return TD_JSON_ARGUMENT;
  if (count > 64) return TD_JSON_LIMIT;
  if (count && !aligned(fields)) return TD_JSON_ARGUMENT;
  try {
    const auto* source = static_cast<const JsonScalarField*>(fields);
    std::vector<truffledata::json::scalar_field> schema;
    schema.reserve(count);
    uint64_t names = 0;
    for (uint64_t i = 0; i < count; ++i) {
      const auto& field = source[i];
      if (field.kind > 4 || (field.flags & ~3u) || (!field.name && field.name_length))
        return TD_JSON_ARGUMENT;
      if (field.name_length > UINT32_MAX || field.name_length > max_name_bytes - names)
        return TD_JSON_LIMIT;
      names += field.name_length;
      std::string name;
      if (field.name_length) name.assign(reinterpret_cast<const char*>(field.name), field.name_length);
      schema.push_back({std::move(name), static_cast<truffledata::json::scalar_kind>(field.kind),
          bool(field.flags & 1), bool(field.flags & 2), field.minimum, field.maximum,
          field.unsigned_maximum, size_t(field.max_utf8_bytes), field.float_bits, field.length_unit,
          field.minimum_length, field.maximum_length});
    }
    auto owned = std::make_unique<truffledata::json::scalar_plan>(std::move(schema));
    result.owner = owned.release();
    return TD_JSON_OK;
  } catch (const std::bad_alloc&) { return TD_JSON_ALLOCATION; }
    catch (const std::invalid_argument&) { return TD_JSON_ARGUMENT; }
    catch (...) { return TD_JSON_INTERNAL; }
}

extern "C" void td_json_plan_release(void* output) {
  if (!aligned(output)) return;
  auto& result = *static_cast<JsonPlan*>(output);
  auto* owned = static_cast<truffledata::json::scalar_plan*>(result.owner);
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
  auto* plan = static_cast<const truffledata::json::scalar_plan*>(static_cast<const JsonPlan*>(plan_record)->owner);
  if (!plan) return TD_JSON_ARGUMENT;
  if (length > UINT32_MAX || length > max_input_bytes) return TD_JSON_LIMIT;
  try {
    auto owned = std::make_unique<truffledata::json::scalar_document>(*plan,
        std::string_view(reinterpret_cast<const char*>(data), length),
        truffledata::json::storage_limits{size_t(max_input_bytes), size_t(max_index_bytes)});
    auto stats = owned->allocations();
    result = {owned.get(), const_cast<JsonScalarSlot*>(owned->slots()), owned->size(),
        owned->present(), owned->nulls(), stats.copied_input_bytes, stats.padded_input_bytes,
        stats.index_retained_bytes, stats.index_peak_bytes, stats.index_allocations,
        stats.parser_input_capacity, sizeof(truffledata::json::scalar_document)};
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
  auto* owned = static_cast<truffledata::json::scalar_document*>(result.owner);
  result = {};
  delete owned;
}
