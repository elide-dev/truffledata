#ifndef TRUFFLEDATA_YAML_ERRORS_H
#define TRUFFLEDATA_YAML_ERRORS_H
// YAML decode failures. They never cross the C ABI: api.cc maps each to a td_yaml_status and copies
// the message (at most 255 bytes), line and column into the result. Messages are fixed-size so the
// error path itself never allocates.
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <exception>
#include <string_view>

namespace truffledata::yaml {

// ABI status codes (api.h re-exports them as TD_YAML_*).
enum status : int32_t {
  status_ok = 0,
  status_argument = 1,
  status_limit = 2,
  status_allocation = 3,
  status_syntax = 4,
  status_internal = 5,
  status_contract = 6,
  status_multiple_documents = 7,
  status_stream_empty = 8,
  status_stream_truncated = 9,
};

class error : public std::exception {
 public:
  static constexpr size_t npos = SIZE_MAX;
  static constexpr size_t capacity = 256;
  int32_t code;
  uint64_t line = 0;    // 1-based; 0 when unknown
  uint64_t column = 0;  // 1-based; 0 when unknown
  size_t offset;        // byte offset into the prepared input, or npos
  char text[capacity];
  size_t length;
  bool parser = false;  // raised by rapidyaml itself (through its error callbacks)
  // A duplicate found by the fused (directory-time) key check, which runs when the mapping closes
  // and so knows neither the key nor its position. The session re-decodes with per-key checks to
  // report both; this error is only ever seen if that re-decode unexpectedly succeeds.
  bool relocate = false;

  error(int32_t code_, std::string_view message, size_t offset_ = npos) : code(code_), offset(offset_) {
    length = message.size() < capacity - 1 ? message.size() : capacity - 1;
    std::memcpy(text, message.data(), length);
    text[length] = 0;
  }
  // Appends ": detail" within the fixed buffer (truncating).
  error& with(std::string_view detail) {
    for (char c : std::string_view(": ")) if (length < capacity - 1) text[length++] = c;
    for (char c : detail) if (length < capacity - 1) text[length++] = c;
    text[length] = 0;
    return *this;
  }
  error& relocated() {
    relocate = true;
    return *this;
  }
  error& from_parser() {
    parser = true;
    return *this;
  }
  error& at(uint64_t line_, uint64_t column_) {
    line = line_;
    column = column_;
    return *this;
  }
  const char* what() const noexcept override { return text; }
};

inline error syntax_error(std::string_view message, size_t offset = error::npos) {
  return error(status_syntax, message, offset);
}
inline error limit_error(std::string_view which) {
  return error(status_limit, "YAML resource limit").with(which);
}

}  // namespace truffledata::yaml
#endif
