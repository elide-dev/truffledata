#ifndef TRUFFLEDATA_JSON_STAGE1_H
#define TRUFFLEDATA_JSON_STAGE1_H
#include <simdjson.h>
#include <cstddef>
#include <cstdint>
#include <stdexcept>

namespace truffledata::json {

// The implementation member and structural index are public simdjson surfaces, but the
// implementation type lives in internal::. Any pin change requalifies the differential tests.
static_assert(simdjson::SIMDJSON_VERSION_MAJOR == 5 && simdjson::SIMDJSON_VERSION_MINOR == 1
              && simdjson::SIMDJSON_VERSION_REVISION == 1, "Requalify the JSON stage-1 contract");
class stage1_index final {
  simdjson::dom::parser parser_;
 public:
  void run(const uint8_t* input, size_t size,
           simdjson::stage1_mode mode = simdjson::stage1_mode::regular) {
    if (!parser_.implementation || parser_.capacity() < size) {
      auto error = parser_.allocate(size ? size : 1);
      if (error == simdjson::MEMALLOC) throw std::bad_alloc();
      if (error != simdjson::SUCCESS) throw std::length_error(simdjson::error_message(error));
    }
    auto error = parser_.implementation->stage1(input, size, mode);
    if (error == simdjson::MEMALLOC) throw std::bad_alloc();
    if (error != simdjson::SUCCESS) throw simdjson::simdjson_error(error);
  }
  const uint32_t* indexes() const { return parser_.implementation->structural_indexes.get(); }
  uint32_t size() const { return parser_.implementation->n_structural_indexes; }
  size_t capacity() const { return parser_.capacity(); }
  void release_above(size_t limit) {
    if (parser_.capacity() > limit) parser_ = simdjson::dom::parser();
  }
};
}
#endif
