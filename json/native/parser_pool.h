#ifndef TRUFFLEDATA_JSON_PARSER_POOL_H
#define TRUFFLEDATA_JSON_PARSER_POOL_H
#include <memory>
#include <mutex>
#include <simdjson.h>
#include <vector>

namespace truffledata::json {
class parser_pool;

// Move-only lease of a pooled On-Demand parser. Returning it to the pool is
// only valid once no decoded text still points into the parser's scratch.
class parser_lease final {
  std::unique_ptr<simdjson::ondemand::parser> parser_;
  friend class parser_pool;
  explicit parser_lease(std::unique_ptr<simdjson::ondemand::parser> parser) : parser_(std::move(parser)) {}
public:
  parser_lease(parser_lease&&) noexcept = default;
  parser_lease& operator=(parser_lease&&) noexcept = default;
  parser_lease(const parser_lease&) = delete;
  parser_lease& operator=(const parser_lease&) = delete;
  ~parser_lease();
  simdjson::ondemand::parser& get() { return *parser_; }
};

// Process-wide, mutex-guarded pool retaining at most `capacity` idle parsers.
class parser_pool final {
  static constexpr size_t capacity = 8;
  // Parsers keep buffers sized for the largest input they have seen; pooling one that grew past
  // this would pin that memory indefinitely, so it is freed instead.
  static constexpr size_t max_retained_capacity = size_t(1) << 20;
  std::mutex mutex_;
  std::vector<std::unique_ptr<simdjson::ondemand::parser>> idle_;
  static parser_pool& instance() { static parser_pool pool; return pool; }
  friend class parser_lease;
  void release(std::unique_ptr<simdjson::ondemand::parser> parser) {
    if (parser->capacity() > max_retained_capacity) return;  // freed outside the lock
    std::lock_guard<std::mutex> lock(mutex_);
    if (idle_.size() < capacity) idle_.push_back(std::move(parser));
  }
public:
  static parser_lease acquire() {
    auto& pool = instance();
    std::lock_guard<std::mutex> lock(pool.mutex_);
    if (!pool.idle_.empty()) {
      auto parser = std::move(pool.idle_.back());
      pool.idle_.pop_back();
      return parser_lease(std::move(parser));
    }
    return parser_lease(std::make_unique<simdjson::ondemand::parser>());
  }
  static constexpr size_t retained_capacity_limit() { return max_retained_capacity; }
  static size_t retained() {
    auto& pool = instance();
    std::lock_guard<std::mutex> lock(pool.mutex_);
    return pool.idle_.size();
  }
};

inline parser_lease::~parser_lease() {
  if (parser_) parser_pool::instance().release(std::move(parser_));
}
} // namespace truffledata::json
#endif
