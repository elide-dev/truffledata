#ifndef TRUFFLEDATA_JSON_STATUS_H
#define TRUFFLEDATA_JSON_STATUS_H
// C++ mirror of the td_json_status codes (api.h; api.cc asserts they agree) and the one mapping
// from decoder exceptions and simdjson errors to them, shared by the C ABI and the stream.
#include <simdjson.h>
#include <cstdint>
#include <new>
#include <stdexcept>

namespace truffledata::json {

enum status : int32_t {
  status_ok = 0,
  status_argument = 1,
  status_limit = 2,
  status_allocation = 3,
  status_syntax = 4,
  status_internal = 5,
  status_contract = 6,
  status_text_too_small = 7,
  // Streams (stream.h): no complete document is buffered, and the finished input ended inside one.
  // The values match TD_YAML_STREAM_EMPTY/TD_YAML_STREAM_TRUNCATED (one stream contract).
  status_stream_empty = 8,
  status_stream_truncated = 9,
};

inline int32_t status_of(simdjson::error_code error) noexcept {
  if (error == simdjson::MEMALLOC) return status_allocation;
  if (error == simdjson::CAPACITY || error == simdjson::DEPTH_ERROR) return status_limit;
  return status_syntax;
}

// Maps the exception being handled; call only inside a catch block.
inline int32_t current_exception_status() noexcept {
  try {
    throw;
  } catch (const std::bad_alloc&) {
    return status_allocation;
  } catch (const std::length_error&) {
    return status_limit;
  } catch (const simdjson::simdjson_error& error) {
    return status_of(error.error());
  } catch (const std::invalid_argument&) {
    return status_syntax;
  } catch (...) {
    return status_internal;
  }
}

}  // namespace truffledata::json
#endif
