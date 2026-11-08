#ifndef TRUFFLEDATA_YAML_STREAM_H
#define TRUFFLEDATA_YAML_STREAM_H
// Multi-document YAML input (docs/superpowers/plans/2026-11-06-yaml.md, Task 3), with the JSON
// F1 contract: bytes are fed into a carry-over buffer (feed returns fewer bytes than offered when
// the buffer is full: backpressure), complete documents are decoded one at a time into the
// session (borrowed until the next call), and documents(byte[]) is this stream over a complete
// buffer.
//
// Document boundaries are found by a line scan, never by parsing:
//   - a line starting with `---` or `...` followed by space, tab, CR, LF or the end of input is a
//     c-forbidden marker line (YAML 1.2.2 §9.1.4): no content can continue across it;
//   - `---` starts a new document when the current one already started (content or `---`);
//     directive and comment lines before it belong to the new document;
//   - `...` ends the current document after its line;
//   - a `%` line at column 0 is a directive (and belongs to the following document) only when no
//     document is open; otherwise it stays with the open document, for rapidyaml to judge.
// Each chunk then goes through the session's single-document decode, so rapidyaml, not the
// splitter, decides validity. A chunk that holds no document (comments only) is skipped.
#include "yaml/native/errors.h"
#include "yaml/native/session.h"

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <memory_resource>
#include <optional>

namespace truffledata::yaml {

class stream final {
 public:
  stream(session& owner, size_t max_document_bytes, size_t buffer_bytes,
         std::pmr::memory_resource* upstream = std::pmr::get_default_resource())
      : session_(owner),
        upstream_(upstream),
        max_document_(max_document_bytes ? max_document_bytes : 1),
        buffer_bytes_(buffer_bytes ? buffer_bytes : 4096) {}
  stream(const stream&) = delete;
  stream& operator=(const stream&) = delete;
  ~stream() {
    if (data_) upstream_->deallocate(data_, capacity_, 1);
  }

  // Appends up to `length` bytes; returns how many were accepted. Fewer than `length` means the
  // buffer is full: consume documents with next() and feed the rest. While no complete document
  // is buffered, the buffer may grow to hold one document of max_document_bytes.
  size_t feed(const uint8_t* bytes, size_t length) {
    if (finished_ || failed_) return 0;
    compact();
    size_t pending = size_ - start_;
    size_t limit = buffer_bytes_;
    // An open document may grow the buffer, a buffer at a time, up to max_document_bytes + 1.
    if (!complete_ && pending >= limit) limit = std::min(pending + buffer_bytes_, max_document_ + 1);
    size_t room = pending < limit ? limit - pending : 0;
    size_t take = length < room ? length : room;
    if (!take) return 0;
    reserve(size_ + take);
    std::memcpy(data_ + size_, bytes, take);
    size_ += take;
    fed_ += take;
    scan();
    return take;
  }

  void finish() {
    finished_ = true;
    scan();
  }

  // Decodes the next document into the session (borrowed until the next call). Returns
  // status_ok with the document view, status_stream_empty when no complete document is buffered
  // (more input needed, or the finished stream is exhausted), status_stream_truncated when the
  // finished stream ends inside an incomplete final document, and any decode status otherwise.
  // A failed stream stays failed.
  int32_t next(session::document_view* out, uint32_t flags = 0) {
    for (;;) {
      if (failed_) return failed_;
      size_t end;
      if (complete_) {
        end = boundary_;
      } else if (finished_) {
        if (scan_ < size_) return fail(status_internal);
        end = size_;
        if (end == start_) return status_stream_empty;
      } else {
        if (size_ - start_ > max_document_) return fail(status_limit);
        return status_stream_empty;
      }
      const uint8_t* chunk = reinterpret_cast<const uint8_t*>(data_ + start_);
      size_t length = end - start_;
      bool last = !complete_;
      start_ = end;
      complete_ = false;
      document_open_ = false;
      scan();
      if (length > max_document_) return fail(status_limit);
      if (last && incomplete_utf8(chunk, length)) return fail(status_stream_truncated);
      try {
        *out = session_.decode(chunk, length, flags);
      } catch (const error& e) {
        last_error_ = e;
        // rapidyaml reporting a syntax error on the last line of the final document: the input
        // ended inside it (an open flow collection or quoted scalar, a dangling key).
        bool at_end = last && finished_ && e.parser && e.code == status_syntax && e.line >= lines(chunk, length);
        return fail(at_end ? status_stream_truncated : e.code);
      } catch (const std::bad_alloc&) {
        return fail(status_allocation);
      }
      if (!(out->flags & doc_present)) continue;  // comments or blank lines only
      ++documents_;
      return status_ok;
    }
  }

  uint64_t documents() const { return documents_; }
  session& owner() const { return session_; }
  uint64_t fed() const { return fed_; }
  bool failed() const { return failed_ != 0; }
  // The error that failed the stream (when it came from a document decode).
  const std::optional<error>& last_error() const { return last_error_; }

 private:
  int32_t fail(int32_t status) {
    failed_ = status;
    return status;
  }

  static bool marker(const char* p, size_t left, char c, bool at_eof) {
    if (left < 3 || p[0] != c || p[1] != c || p[2] != c) return false;
    if (left == 3) return at_eof;
    char next = p[3];
    return next == ' ' || next == '\t' || next == '\r' || next == '\n';
  }

  // Classifies complete lines from scan_ until a boundary is found (complete_ set) or the
  // buffered input is exhausted. A final line without LF is classified only after finish().
  void scan() {
    while (!complete_ && scan_ < size_) {
      const char* line = data_ + scan_;
      size_t left = size_ - scan_;
      // Inside an open document only `---`, `...` (perhaps after a BOM) can change anything, so a
      // line starting with any other byte is skipped by an inline LF search instead of the full
      // classification below (which reaches the same decision for it).
      if (document_open_ && line[0] != '-' && line[0] != '.' && uint8_t(line[0]) != 0xEF) {
        const char* lf = find_lf(line, left);
        if (!lf) {
          if (!finished_) return;
          scan_ = size_;
        } else {
          scan_ += size_t(lf - line) + 1;
        }
        continue;
      }
      const char* lf = static_cast<const char*>(std::memchr(line, '\n', left));
      if (!lf && !finished_) {
        // A partial line can only be judged once it is complete, except that it cannot be a
        // marker or directive when its prefix already rules that out.
        return;
      }
      size_t length = lf ? size_t(lf - line) + 1 : left;
      const char* body = line;
      size_t body_left = length;
      if (body_left >= 3 && uint8_t(body[0]) == 0xEF && uint8_t(body[1]) == 0xBB && uint8_t(body[2]) == 0xBF) {
        body += 3;  // a BOM may precede a document's first line
        body_left -= 3;
      }
      bool eof = !lf;
      if (marker(body, body_left, '-', eof)) {
        if (document_open_) {
          boundary_ = scan_;  // `---` starts the next document
          complete_ = true;
          return;
        }
        document_open_ = true;
      } else if (marker(body, body_left, '.', eof)) {
        scan_ += length;
        boundary_ = scan_;  // `...` ends this document after its line
        complete_ = true;
        return;
      } else if (body_left && body[0] == '%' && !document_open_) {
        // a directive of the next document
      } else if (!blank_or_comment(body, body_left)) {
        document_open_ = true;
      }
      scan_ += length;
    }
  }

  // The first LF in p[0, n), or null: eight bytes per step (exact SWAR match), no library call
  // per line.
  static const char* find_lf(const char* p, size_t n) {
    size_t i = 0;
    for (; i + 8 <= n; i += 8) {
      uint64_t x;
      std::memcpy(&x, p + i, 8);
      uint64_t hit = detail::equal(x, '\n');
      if (hit) return p + i + size_t(__builtin_ctzll(hit) / 8);
    }
    for (; i < n; ++i)
      if (p[i] == '\n') return p + i;
    return nullptr;
  }

  static bool blank_or_comment(const char* p, size_t n) {
    size_t i = 0;
    while (i < n && (p[i] == ' ' || p[i] == '\t' || p[i] == '\r' || p[i] == '\n')) ++i;
    return i == n || p[i] == '#';
  }

  // The last code point of a final document cut short (only checked after finish()).
  static bool incomplete_utf8(const uint8_t* p, size_t n) {
    size_t i = n, k = 0;
    while (i > 0 && k < 4 && (p[i - 1] & 0xC0) == 0x80) --i, ++k;
    if (i == 0) return k > 0;
    uint8_t lead = p[i - 1];
    size_t need = lead < 0x80 ? 0 : lead >= 0xF0 ? 3 : lead >= 0xE0 ? 2 : lead >= 0xC0 ? 1 : 0;
    return need > k;
  }

  static uint64_t lines(const uint8_t* p, size_t n) {
    uint64_t count = 1;
    for (size_t i = 0; i < n; ++i) count += p[i] == '\n';
    return count;
  }

  // Moves unconsumed bytes to the front of the buffer.
  void compact() {
    if (start_ == 0) return;
    size_t keep = size_ - start_;
    if (keep) std::memmove(data_, data_ + start_, keep);
    size_ = keep;
    scan_ -= start_;
    if (complete_) boundary_ -= start_;
    start_ = 0;
  }

  void reserve(size_t need) {
    if (need <= capacity_) return;
    size_t capacity = capacity_ ? capacity_ * 2 : 4096;
    if (capacity < need) capacity = need;
    char* grown = static_cast<char*>(upstream_->allocate(capacity, 1));
    if (size_) std::memcpy(grown, data_, size_);
    if (data_) upstream_->deallocate(data_, capacity_, 1);
    data_ = grown;
    capacity_ = capacity;
  }

  session& session_;
  std::pmr::memory_resource* upstream_;
  size_t max_document_;
  size_t buffer_bytes_;
  char* data_ = nullptr;
  size_t capacity_ = 0;
  size_t size_ = 0;      // buffered bytes
  size_t start_ = 0;     // first byte of the current document
  size_t scan_ = 0;      // next line to classify
  size_t boundary_ = 0;  // end of the current document when complete_
  bool complete_ = false;
  bool document_open_ = false;
  bool finished_ = false;
  int32_t failed_ = 0;
  uint64_t documents_ = 0;
  uint64_t fed_ = 0;
  std::optional<error> last_error_;
};

}  // namespace truffledata::yaml
#endif
