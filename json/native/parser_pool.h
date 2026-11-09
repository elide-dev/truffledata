#ifndef TRUFFLEDATA_JSON_PARSER_POOL_H
#define TRUFFLEDATA_JSON_PARSER_POOL_H
#include <memory>
#include <mutex>
#include <simdjson.h>
#include <vector>

namespace truffledata::json {
template<class Parser> class basic_parser_pool;

// Move-only lease of a pooled parser. Returning it to the pool is
// only valid once no decoded text still points into the parser's scratch.
template<class Parser> class basic_parser_lease final {
  std::unique_ptr<Parser> parser_;
  friend class basic_parser_pool<Parser>;
  explicit basic_parser_lease(std::unique_ptr<Parser> parser) : parser_(std::move(parser)) {}
public:
  basic_parser_lease(basic_parser_lease&&) noexcept = default;
  basic_parser_lease& operator=(basic_parser_lease&&) noexcept = default;
  basic_parser_lease(const basic_parser_lease&) = delete;
  basic_parser_lease& operator=(const basic_parser_lease&) = delete;
  ~basic_parser_lease();
  Parser& get() { return *parser_; }
};

// Process-wide, mutex-guarded pool retaining at most `capacity` idle parsers.
template<class Parser> class basic_parser_pool final {
  static constexpr size_t capacity = 8;
  // Parsers keep buffers sized for the largest input they have seen; pooling one that grew past
  // this would pin that memory indefinitely, so it is freed instead.
  static constexpr size_t max_retained_capacity = size_t(1) << 20;
  std::mutex mutex_;
  std::vector<std::unique_ptr<Parser>> idle_;
  static basic_parser_pool& instance() { static basic_parser_pool pool; return pool; }
  friend class basic_parser_lease<Parser>;
  void release(std::unique_ptr<Parser> parser) {
    if (parser->capacity() > max_retained_capacity) return;  // freed outside the lock
    std::lock_guard<std::mutex> lock(mutex_);
    if (idle_.size() < capacity) idle_.push_back(std::move(parser));
  }
public:
  static basic_parser_lease<Parser> acquire() {
    auto& pool = instance();
    std::lock_guard<std::mutex> lock(pool.mutex_);
    if (!pool.idle_.empty()) {
      auto parser = std::move(pool.idle_.back());
      pool.idle_.pop_back();
      return basic_parser_lease<Parser>(std::move(parser));
    }
    return basic_parser_lease<Parser>(std::make_unique<Parser>());
  }
  static constexpr size_t retained_capacity_limit() { return max_retained_capacity; }
  static size_t retained() {
    auto& pool = instance();
    std::lock_guard<std::mutex> lock(pool.mutex_);
    return pool.idle_.size();
  }
};

template<class Parser> inline basic_parser_lease<Parser>::~basic_parser_lease() {
  if (parser_) basic_parser_pool<Parser>::instance().release(std::move(parser_));
}
} // namespace truffledata::json
#endif
