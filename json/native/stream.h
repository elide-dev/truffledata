#ifndef TRUFFLEDATA_JSON_STREAM_H
#define TRUFFLEDATA_JSON_STREAM_H
// Streams of whitespace-separated JSON documents (NDJSON, JSON Lines, concatenated JSON; plan Task
// F1), with the stream carry-over contract shared with YAML (yaml/native/stream.h, YAML plan D6):
// bytes are fed into a bounded carry-over buffer (feed returns fewer bytes than offered when the
// buffer is full or still being read: backpressure), complete documents are decoded one at a time
// into the session (borrowed until the next call), finish() marks the end of input, and next()
// answers ok / stream_empty / stream_truncated / the failing status. A failed stream stays failed.
//
// Stage 1 indexes each buffered batch once. Root boundaries come from the structural index;
// stage 2 validates each root and emits its compact tape. A region is read in place, so feed() accepts nothing while one is open; a region
// closes when its last document has been delivered (at once, so feeding can resume) and its
// truncated tail (the stage-1 sentinel) is carried over to the front of the buffer for
// the next region. Until finish():
//   - a region never ends inside a UTF-8 sequence (stage 1 would reject the cut code point);
//   - a root number or literal that ends exactly at the end of the buffered bytes is held back
//     (more digits or letters may follow); containers and strings end at their closing byte, and
//     an unterminated one is simdjson's truncated tail.
// Each document's bytes are copied into the session's input buffer, which becomes its region, so a
// borrowed or retained document never refers to the carry-over buffer.
//
// Strict JSON: only whitespace separates documents (simdjson's comma_delimited format also accepts
// doubled, leading, trailing and missing commas, so it is not offered); a UTF-8 BOM is skipped only
// at the very start of the stream. Every document is decoded with the session's decoder: duplicate
// keys, number grammar and limits as for session_decode.
//
// Errors: a document-level failure (syntax, duplicate key, number grammar, limits) fails the stream
// with the document's extent known, and skip() resumes after it. Stage-1 failures (invalid UTF-8,
// unescaped control bytes) are reported by simdjson for a whole batch, without an extent: the
// batch's documents before the error may or may not be delivered first (it depends on where in
// the batch the error lies), and the failure cannot be skipped; neither can allocation failures,
// a truncated final document, or an incomplete document larger than max_document_bytes.
#include "json/native/session.h"
#include "json/native/status.h"

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <memory_resource>
#include <optional>

namespace truffledata::json {

class stream final {
 public:
  // The stream borrows `owner`, which must outlive it. Stage-1 batches normally cover batch_bytes;
  // a root crossing that bound uses buffered lookahead to preserve over-limit skip semantics.
  stream(session& owner, size_t max_document_bytes, size_t batch_bytes)
      : session_(owner), upstream_(owner.upstream()), max_document_(max_document_bytes), batch_(batch_bytes) {
    if (!max_document_bytes || max_document_bytes > UINT32_MAX / 2 || batch_bytes < max_document_bytes ||
        batch_bytes > UINT32_MAX)
      throw std::invalid_argument("JSON stream options");
  }
  stream(const stream&) = delete;
  stream& operator=(const stream&) = delete;
  ~stream() {
    if (data_) upstream_->deallocate(data_, capacity_, alignof(std::max_align_t));
  }

  // Appends up to `length` bytes and returns how many were accepted. Fewer than `length` means
  // the buffer (2 × max_document_bytes) is full or a region is still being read: call next() until
  // it returns stream_empty, then feed the rest. A finished or failed stream accepts nothing; an
  // allocation failure fails the stream (next() reports it).
  size_t feed(const uint8_t* bytes, size_t length) noexcept {
    if (finished_ || failed_ || active_ || !length || !bytes) return 0;
    compact();
    size_t room = 2 * max_document_ - size_;
    size_t take = length < room ? length : room;
    if (!take) return 0;
    try {
      reserve(size_ + take);
    } catch (...) {
      fail(current_exception_status());
      return 0;
    }
    std::memcpy(data_ + size_, bytes, take);
    size_ += take;
    fed_ += take;
    return take;
  }

  // No more input: a final number or literal is complete, and an incomplete final document is
  // reported as stream_truncated.
  void finish() noexcept { finished_ = true; }

  // Decodes the next complete document into the session (borrowed: valid until the next call on
  // this stream or the session). Returns status_ok, status_stream_empty (more input needed, or the
  // finished stream is exhausted), status_stream_truncated, or the failing status.
  int32_t next(session::document_view& out) noexcept {
    for (;;) {
      if (failed_) return failed_;
      if (!active_) {
        if (scanned_ && size_ == scanned_size_ && finished_ == scanned_finished_) return idle();
        if (int32_t s = open_region()) return fail(s);
        if (!active_) return idle();
      }
      if (index_ == parser_.size()) {
        close_region();
        continue;
      }
      const uint32_t* indexes = parser_.indexes();
      uint32_t first = index_, next = first + 1;
      size_t at = region_start_ + indexes[first];
      size_t end = 0;
      bool decoded = false;
      int32_t hinted_error = 0;
      uint8_t token = uint8_t(data_[at]);
      if (token == '{' || token == '[') {
        // Repeated record shapes usually keep the same structural extent. The complete stage-2
        // grammar verifies this candidate; a mismatch retries the exact boundary below. Existing
        // capacity is mandatory, so an oversized hint cannot cause a false allocation/limit error.
        for (uint32_t previous_count : previous_counts_) {
          if (previous_count <= 1 || previous_count > parser_.size() - first) continue;
          uint32_t candidate = first + previous_count;
          size_t candidate_end = region_start_ + indexes[candidate - 1] + 1;
          if (data_[candidate_end - 1] == (token == '{' ? '}' : ']')
              && candidate_end - at <= max_document_
              && session_.can_decode_streamed(candidate_end - at, previous_count)) {
            try {
              out = session_.decode_streamed(data_ + at, candidate_end - at, indexes + first,
                                             previous_count, indexes[first]);
              end = candidate_end; next = candidate; decoded = true;
            } catch (...) {
              hinted_error = current_exception_status();
              if (hinted_error != status_syntax && hinted_error != status_limit) {
                abandon(at); return fail(hinted_error);
              }
            }
            break;  // At most one speculative grammar walk per document.
          }
        }
        uint32_t depth = decoded ? 0 : 1;
        while (next < parser_.size() && depth) {
          uint8_t c = uint8_t(data_[region_start_ + indexes[next++]]);
          if (c == '{' || c == '[') ++depth;
          else if (c == '}' || c == ']') --depth;
        }
        if (depth) { abandon(at); return idle(); }
        if (!decoded) end = region_start_ + indexes[next - 1] + 1;
      } else if (token == '}' || token == ']' || token == ',' || token == ':') {
        end = at + 1;
      } else {
        end = next == parser_.size() ? region_tail_ : region_start_ + indexes[next];
        while (end > at && stage2_detail::whitespace(uint8_t(data_[end - 1]))) --end;
      }
      if (!scanned_finished_ && end == region_end_ && token != '{' && token != '[' && token != '"') {
        // A root number or literal touching the buffered end may continue in the next feed.
        abandon(at);
        return idle();
      }
      if (end - at > max_document_ || hinted_error == status_limit) {
        abandon(at);
        return fail(status_limit, end);
      }
      try {
        if (!decoded)
          out = session_.decode_streamed(data_ + at, end - at, indexes + first, next - first, indexes[first]);
      } catch (...) {
        int32_t status = current_exception_status();
        abandon(at);
        return status == status_syntax || status == status_limit ? fail(status, end) : fail(status);
      }
      delivered_ = end;
      ++documents_;
      index_ = next;
      uint32_t count = next - first;
      bool known = false;
      for (uint32_t previous : previous_counts_) known |= previous == count;
      if (!known) previous_counts_[count_cursor_++ & 3] = count;
      // Exhaustion is observable before the next call, so feed can immediately accept more bytes.
      if (index_ == parser_.size()) close_region();
      return status_ok;
    }
  }

  // Resumes a stream failed by a document-level error after that document. Returns status_ok, or
  // status_argument when the stream has not failed or its failure cannot be skipped.
  int32_t skip() noexcept {
    if (!failed_ || !skip_to_) return status_argument;
    start_ = skip_to_;
    delivered_ = start_;
    failed_ = 0;
    skip_to_ = 0;
    scanned_ = false;
    return status_ok;
  }

  // Buffered bytes not part of a delivered document: after stream_truncated, the incomplete final
  // document; after a failure, the failing document onwards.
  uint64_t pending() const noexcept { return size_ - (active_ ? delivered_ : start_); }
  uint64_t documents() const noexcept { return documents_; }
  uint64_t fed() const noexcept { return fed_; }
  bool finished() const noexcept { return finished_; }
  int32_t failed() const noexcept { return failed_; }
  size_t buffer_capacity() const noexcept { return capacity_; }
  size_t parser_capacity() const noexcept { return parser_.capacity(); }
  session& owner() const noexcept { return session_; }

 private:
  int32_t fail(int32_t status, size_t skip_to = 0) noexcept {
    failed_ = status;
    skip_to_ = skip_to;
    return status;
  }

  // Nothing new to read: report whether the stream is exhausted, waiting or truncated.
  int32_t idle() noexcept {
    size_t pending = size_ - start_;
    if (finished_) return pending ? fail(status_stream_truncated) : status_stream_empty;
    if (pending > max_document_) return fail(status_limit);
    return status_stream_empty;
  }

  int32_t open_region() noexcept {
    scanned_ = true;
    scanned_size_ = size_;
    scanned_finished_ = finished_;
    size_t end = size_ - incomplete_utf8(data_ + start_, size_ - start_);
    if (end == start_) return 0;
    // simdjson skips a leading BOM; strict JSON allows one only before the first document.
    if (discarded_ + start_ != 0 && end - start_ >= 3 && std::memcmp(data_ + start_, "\xEF\xBB\xBF", 3) == 0)
      return status_syntax;
    if (discarded_ + start_ == 0 && end - start_ >= 3 && std::memcmp(data_ + start_, "\xEF\xBB\xBF", 3) == 0)
      start_ += 3;
    if (end == start_) return 0;
    region_start_ = start_;
    size_t available_end = end;
    if (end - start_ > batch_) end = start_ + batch_ - incomplete_utf8(data_ + start_, batch_);
    bool partial = end < available_end;
    for (;;) {
      try {
        parser_.run(reinterpret_cast<const uint8_t*>(data_ + start_), end - start_,
                    partial ? simdjson::stage1_mode::streaming_partial : simdjson::stage1_mode::streaming_final);
      } catch (const simdjson::simdjson_error& error) {
        if (partial && (error.error() == simdjson::EMPTY || error.error() == simdjson::CAPACITY)) {
          // An oversized complete root can still be skipped. Use the buffered lookahead to
          // distinguish its known end from a genuinely incomplete over-limit document.
          end = available_end; partial = false; continue;
        }
        if (error.error() != simdjson::EMPTY) return status_of(error.error());
      } catch (...) { return current_exception_status(); }
      break;
    }
    region_end_ = end;
    // Partial batches leave the truncated root at indexes[n]; the final-batch mode moves
    // that same offset to the second sentinel, keeping indexes[n] as the buffer length.
    region_tail_ = region_start_ + parser_.indexes()[parser_.size() + (partial ? 0 : 1)];
    if (region_tail_ > end) return status_internal;
    delivered_ = start_;
    index_ = 0;
    active_ = parser_.size() != 0;
    if (!active_) start_ = region_tail_;
    return 0;
  }

  // All complete roots were consumed; retain the truncated final root for the next region.
  void close_region() noexcept {
    active_ = false;
    start_ = region_tail_;
    if (region_end_ < size_) scanned_ = false;
  }

  void abandon(size_t at) noexcept {
    active_ = false;
    start_ = at;
  }

  // Trailing bytes of an incomplete UTF-8 sequence (0 when the tail is complete or invalid).
  static size_t incomplete_utf8(const char* p, size_t n) noexcept {
    size_t i = n, k = 0;
    while (i > 0 && k < 3 && (uint8_t(p[i - 1]) & 0xC0) == 0x80) --i, ++k;
    if (i == 0) return 0;
    uint8_t lead = uint8_t(p[i - 1]);
    size_t need = lead >= 0xF8 ? 0 : lead >= 0xF0 ? 3 : lead >= 0xE0 ? 2 : lead >= 0xC0 ? 1 : 0;
    return need > k ? k + 1 : 0;
  }

  // Moves unconsumed bytes to the front of the buffer (only between regions).
  void compact() noexcept {
    if (start_ == 0) return;
    size_t keep = size_ - start_;
    if (keep) std::memmove(data_, data_ + start_, keep);
    discarded_ += start_;
    size_ = keep;
    start_ = 0;
    delivered_ = 0;
    scanned_ = false;
  }

  // Grows the buffer geometrically to hold `need` bytes plus simdjson's padding (zeroed).
  void reserve(size_t need) {
    size_t padded = need + simdjson::SIMDJSON_PADDING;
    if (padded <= capacity_) return;
    size_t limit = 2 * max_document_ + simdjson::SIMDJSON_PADDING;
    size_t capacity = capacity_ ? 2 * capacity_ : 4096;
    if (capacity < padded) capacity = padded;
    if (capacity > limit) capacity = limit;
    char* grown = static_cast<char*>(upstream_->allocate(capacity, alignof(std::max_align_t)));
    if (size_) std::memcpy(grown, data_, size_);
    std::memset(grown + size_, 0, capacity - size_);
    if (data_) upstream_->deallocate(data_, capacity_, alignof(std::max_align_t));
    data_ = grown;
    capacity_ = capacity;
  }

  session& session_;
  std::pmr::memory_resource* upstream_;
  size_t max_document_;
  size_t batch_;
  stage1_index parser_;  // Batch indexes stay live while session-owned document arrays are reused.
  uint32_t index_ = 0, count_cursor_ = 0, previous_counts_[4]{};
  size_t region_start_ = 0, region_tail_ = 0;
  char* data_ = nullptr;
  size_t capacity_ = 0;
  size_t size_ = 0;         // buffered bytes
  size_t start_ = 0;        // first byte not consumed (the open region's start while active)
  size_t delivered_ = 0;    // end of the last delivered document of the open region
  size_t region_end_ = 0;   // end of the open region
  size_t scanned_size_ = 0;  // size_ when the last region opened
  size_t skip_to_ = 0;      // where skip() resumes (0: the failure cannot be skipped)
  uint64_t discarded_ = 0;  // bytes compacted away before data_[0]
  uint64_t documents_ = 0;
  uint64_t fed_ = 0;
  int32_t failed_ = 0;
  bool active_ = false;
  bool scanned_ = false;
  bool scanned_finished_ = false;
  bool finished_ = false;
};

}  // namespace truffledata::json
#endif
