#ifndef TRUFFLEDATA_JSON_STATUS_H
#define TRUFFLEDATA_JSON_STATUS_H
// C++ names of the json.seam `Status` codes (generated seam.h; api.h aliases them as TD_JSON_*)
// and the one mapping from decoder exceptions and simdjson errors to them, shared by the C ABI and
// the stream. Every value is the generated one: there is no second copy of a number here.
#include "json/native/generated/seam.h"
#include <simdjson.h>
#include <cstdint>
#include <new>
#include <stdexcept>

namespace truffledata::json {

enum status : int32_t {
  status_ok = TRUFFLEDATA_JSON_STATUS_OK,
  status_argument = TRUFFLEDATA_JSON_STATUS_ARGUMENT,
  status_limit = TRUFFLEDATA_JSON_STATUS_LIMIT,
  status_allocation = TRUFFLEDATA_JSON_STATUS_ALLOCATION,
  status_syntax = TRUFFLEDATA_JSON_STATUS_SYNTAX,
  status_internal = TRUFFLEDATA_JSON_STATUS_INTERNAL,
  status_contract = TRUFFLEDATA_JSON_STATUS_CONTRACT,
  status_text_too_small = TRUFFLEDATA_JSON_STATUS_TEXT_TOO_SMALL,
  // Streams (stream.h): no complete document is buffered, and the finished input ended inside one.
  // The values match TD_YAML_STREAM_EMPTY/TD_YAML_STREAM_TRUNCATED (one stream contract).
  status_stream_empty = TRUFFLEDATA_JSON_STATUS_STREAM_EMPTY,
  status_stream_truncated = TRUFFLEDATA_JSON_STATUS_STREAM_TRUNCATED,
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
