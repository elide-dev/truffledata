#include "json/native/api.h"
#include "json/native/decoder.h"
#include <memory>

namespace {
struct handle {
  truffledata::json::document document;
  myna_value_view view;
  handle(std::string_view input, myna::value_limits values,
         truffledata::json::storage_limits storage)
      : document(input, values, storage), view(document.view()) {}
};
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
    auto owned = std::make_unique<handle>(
        std::string_view(reinterpret_cast<const char*>(data), length),
        myna::value_limits{max_nodes, max_text_bytes, max_depth},
        truffledata::json::storage_limits{size_t(max_input_bytes), size_t(max_index_bytes)});
    auto stats = owned->document.allocations();
    // Publish only after parsing and validation have completed successfully.
    result = {owned.get(), &owned->view, stats.copied_input_bytes,
              stats.padded_input_bytes, stats.index_retained_bytes,
              stats.index_peak_bytes, stats.index_allocations,
              stats.parser_input_capacity, sizeof(handle)};
    (void)owned.release();
    return TD_JSON_OK;
  } catch (const std::bad_alloc&) {
    return TD_JSON_ALLOCATION;
  } catch (const std::length_error&) {
    return TD_JSON_LIMIT;
  } catch (const simdjson::simdjson_error& error) {
    if (error.error() == simdjson::MEMALLOC) return TD_JSON_ALLOCATION;
    if (error.error() == simdjson::CAPACITY || error.error() == simdjson::DEPTH_ERROR)
      return TD_JSON_LIMIT;
    return TD_JSON_SYNTAX;
  } catch (const std::invalid_argument&) {
    return TD_JSON_SYNTAX;
  } catch (...) {
    return TD_JSON_INTERNAL;
  }
}

extern "C" void td_json_release(void* output) {
  if (!aligned(output)) return;
  auto& result = *static_cast<JsonResult*>(output);
  auto* owned = static_cast<handle*>(result.owner);
  result = {};
  delete owned;
}
