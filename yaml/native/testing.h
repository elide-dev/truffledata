// Test-only helpers for //yaml/native tests: checks, a counting/failing memory resource, and a
// canonical text rendering of a Myna tape that shows kinds (`1` I64, `u1` U64, `f1.5` F64, `d1e400`
// DECIMAL, `"s"` STRING, `{"k":v}` OBJECT, `[...]` ARRAY, `null`, `true`).
#pragma once

#include <myna/values.h>

#include <charconv>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <memory_resource>
#include <new>
#include <stdexcept>
#include <string>
#include <string_view>

namespace truffledata::yaml::testing {

inline void require_at(bool condition, const char* what, int line) {
  if (!condition) throw std::runtime_error(std::string("check failed at line ") + std::to_string(line) + ": " + what);
}
#define require(condition) ::truffledata::yaml::testing::require_at((condition), #condition, __LINE__)

// Counts upstream allocations and fails the `fail_at`-th one (SIZE_MAX: never).
class fault_memory final : public std::pmr::memory_resource {
 public:
  size_t fail_at, calls = 0, live = 0;
  explicit fault_memory(size_t fail = SIZE_MAX) : fail_at(fail) {}

 private:
  void* do_allocate(size_t bytes, size_t alignment) override {
    if (calls++ == fail_at) throw std::bad_alloc();
    live += bytes;
    return std::pmr::new_delete_resource()->allocate(bytes, alignment);
  }
  void do_deallocate(void* pointer, size_t bytes, size_t alignment) override {
    live -= bytes;
    std::pmr::new_delete_resource()->deallocate(pointer, bytes, alignment);
  }
  bool do_is_equal(const std::pmr::memory_resource& other) const noexcept override { return this == &other; }
};

inline std::string_view node_text(const myna_value_view& view, uint64_t index) {
  const myna_value_node& node = view.nodes[index];
  if (node.tag & MYNA_VALUE_REGION_TEXT) {
    uint32_t region = uint32_t(node.payload >> 32);
    const uint8_t* base = region == 0 ? view.bytes : view.regions[region - 1].data;
    return {reinterpret_cast<const char*>(base + uint32_t(node.payload)), node.length};
  }
  return {reinterpret_cast<const char*>(&node.payload), node.length};
}

inline void quote(std::string& out, std::string_view s) {
  out.push_back('"');
  for (unsigned char c : s) {
    if (c == '"' || c == '\\') { out.push_back('\\'); out.push_back(char(c)); }
    else if (c == '\n') out += "\\n";
    else if (c == '\t') out += "\\t";
    else if (c < 0x20) { char b[8]; std::snprintf(b, sizeof b, "\\u%04x", c); out += b; }
    else out.push_back(char(c));
  }
  out.push_back('"');
}

// Renders the subtree at `index`; returns the index after it.
inline uint64_t render(const myna_value_view& view, uint64_t index, std::string& out) {
  const myna_value_node& node = view.nodes[index];
  switch (node.tag & MYNA_VALUE_KIND_MASK) {
    case MYNA_VALUE_NULL: out += "null"; return index + 1;
    case MYNA_VALUE_BOOL: out += node.payload ? "true" : "false"; return index + 1;
    case MYNA_VALUE_I64: out += std::to_string(int64_t(node.payload)); return index + 1;
    case MYNA_VALUE_U64: out += "u" + std::to_string(node.payload); return index + 1;
    case MYNA_VALUE_F64: {
      double d;
      std::memcpy(&d, &node.payload, sizeof d);
      out += 'f';
      if (std::isnan(d)) out += "nan";
      else if (std::isinf(d)) out += d < 0 ? "-inf" : "inf";
      else {
        char b[32];
        auto r = std::to_chars(b, b + sizeof b, d);
        out.append(b, r.ptr);
      }
      return index + 1;
    }
    case MYNA_VALUE_DECIMAL: out += 'd'; out += node_text(view, index); return index + 1;
    case MYNA_VALUE_STRING: quote(out, node_text(view, index)); return index + 1;
    case MYNA_VALUE_ARRAY: {
      out += '[';
      uint64_t at = index + 1;
      for (uint32_t i = 0; i < node.length; ++i) {
        if (i) out += ',';
        at = render(view, at, out);
      }
      out += ']';
      if (at != node.next) throw std::runtime_error("array next mismatch");
      return at;
    }
    case MYNA_VALUE_OBJECT: {
      out += '{';
      uint64_t at = index + 1;
      for (uint32_t i = 0; i < node.length; ++i) {
        if (i) out += ',';
        if ((view.nodes[at].tag & MYNA_VALUE_KIND_MASK) != MYNA_VALUE_STRING) throw std::runtime_error("non-string key");
        at = render(view, at, out);
        out += ':';
        at = render(view, at, out);
      }
      out += '}';
      if (at != node.next) throw std::runtime_error("object next mismatch");
      return at;
    }
  }
  throw std::runtime_error("unknown node kind");
}

inline std::string render(const myna_value_view& view) {
  std::string out;
  uint64_t end = render(view, 0, out);
  if (end != view.node_count) throw std::runtime_error("trailing nodes after the root");
  return out;
}

}  // namespace truffledata::yaml::testing
