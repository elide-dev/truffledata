// Task F1: streams of JSON documents over iterate_many with carry-over (json/native/stream.h).
#include "json/native/api.h"
#include "json/native/session.h"
#include "json/native/stream.h"

#include <myna/values.h>

#include <cstdint>
#include <cstdio>
#include <cstring>
#include <memory_resource>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

void require(bool condition, const char* what) {
  if (!condition) {
    std::fprintf(stderr, "stream check failed: %s\n", what);
    throw std::runtime_error(what);
  }
}
#define REQUIRE(c) require((c), #c)

std::string text_of(const myna_value_view* view, size_t index) {
  const auto& node = view->nodes[index];
  if ((node.tag & MYNA_VALUE_REGION_TEXT) == 0) {
    std::string out;
    for (uint32_t i = 0; i < node.length; ++i) out += char(myna_value_inline_byte(&node, i));
    return out;
  }
  auto region = node.payload >> 32;
  const uint8_t* data = region == 0 ? view->bytes : view->regions[region - 1].data;
  return std::string(reinterpret_cast<const char*>(data + uint32_t(node.payload)), node.length);
}

// Order-sensitive digest of a tape: kinds, lengths, scalar payloads and text, plus the lexical
// spans and key metadata, so two decodes of one document agree exactly.
uint64_t digest(const JsonResult& result) {
  auto* view = static_cast<const myna_value_view*>(result.view);
  uint64_t h = 1469598103934665603ull;
  auto mix = [&](uint64_t v) {
    for (int i = 0; i < 8; ++i) {
      h ^= (v >> (8 * i)) & 0xff;
      h *= 1099511628211ull;
    }
  };
  for (size_t i = 0; i < view->node_count; ++i) {
    const auto& node = view->nodes[i];
    uint32_t kind = node.tag & MYNA_VALUE_KIND_MASK;
    mix(kind);
    mix(node.length);
    if (kind == MYNA_VALUE_STRING || kind == MYNA_VALUE_DECIMAL) {
      for (char c : text_of(view, i)) mix(uint8_t(c));
    } else if (kind != MYNA_VALUE_ARRAY && kind != MYNA_VALUE_OBJECT) {
      mix(node.payload);
    }
    mix(static_cast<const uint64_t*>(result.lexical)[i]);
    mix(static_cast<const uint32_t*>(result.key_hashes)[i]);
  }
  return h;
}

// Deterministic documents of every root kind, separated by assorted whitespace (or nothing
// between containers), with escapes, non-ASCII text, long strings and numbers of every class.
struct corpus {
  std::string bytes;
  std::vector<std::string> documents;
};
corpus make_corpus(size_t count) {
  corpus out;
  uint64_t state = 0x9e3779b97f4a7c15ull;
  auto next = [&]() {
    state ^= state << 13;
    state ^= state >> 7;
    state ^= state << 17;
    return state;
  };
  const char* separators[] = {"\n", " ", "\r\n", "\t\n  ", "\n\n"};
  bool previous_container = false;
  for (size_t i = 0; i < count; ++i) {
    std::string doc;
    switch (next() % 9) {
      case 0: doc = "{\"id\":" + std::to_string(i) + ",\"name\":\"item-" + std::to_string(i) +
                    "\",\"note\":\"line \\\"" + std::to_string(i % 7) + "\\\" caf\xc3\xa9 \xe4\xb8\xad \\u00e9\",\"tags\":[\"t0\",\"t1\"],\"dims\":{\"w\":3,\"h\":1.5}}"; break;
      case 1: doc = "[" + std::to_string(i) + ",-0,1E400,18446744073709551615,12345678901234567890123,true,null]"; break;
      case 2: doc = std::to_string(int64_t(next() % 2000000) - 1000000); break;
      case 3: doc = "\"plain string number " + std::to_string(i) + " beyond the inline limit\""; break;
      case 4: doc = (next() & 1) ? "true" : "null"; break;
      case 5: doc = "{\"k\":{\"nested\":[{\"deep\":[" + std::to_string(i) + "]}]},\"s\":\"\\ud83d\\ude00\"}"; break;
      case 6: doc = std::to_string(i) + ".25e-3"; break;
      case 7: doc = "{}"; break;
      default: doc = "[\"" + std::string(100 + i % 150, char('a' + i % 26)) + "\"]"; break;
    }
    bool container = doc[0] == '{' || doc[0] == '[';
    // Containers may be concatenated with no separator; anything else needs whitespace.
    if (i && !(container && previous_container && next() % 3 == 0)) out.bytes += separators[next() % 5];
    out.bytes += doc;
    out.documents.push_back(doc);
    previous_container = container;
  }
  out.bytes += "\n";
  return out;
}

const JsonLimitsStruct limits{1 << 20, 1 << 20, 100000, 1 << 20, 128};

std::vector<uint64_t> expected_digests(const corpus& c) {
  void* s = td_json_session_create(&limits);
  std::vector<uint64_t> out;
  for (const auto& doc : c.documents) {
    JsonResult r{};
    REQUIRE(td_json_session_decode(s, reinterpret_cast<const uint8_t*>(doc.data()), doc.size(), 0, nullptr, 0, &r) == TD_JSON_OK);
    out.push_back(digest(r));
  }
  td_json_session_release(s);
  return out;
}

// Feeds `input` in chunks of `chunk` bytes, draining after every feed, and returns the digests.
std::vector<uint64_t> run_stream(void* stream, const std::string& input, size_t chunk, int32_t* final_status = nullptr) {
  std::vector<uint64_t> out;
  JsonResult r{};
  size_t at = 0;
  auto drain = [&]() {
    for (;;) {
      int32_t status = td_json_stream_next(stream, &r);
      if (status == TD_JSON_OK) {
        out.push_back(digest(r));
        continue;
      }
      return status;
    }
  };
  int stalled = 0;
  while (at < input.size()) {
    size_t length = input.size() - at < chunk ? input.size() - at : chunk;
    uint64_t accepted = td_json_stream_feed(stream, reinterpret_cast<const uint8_t*>(input.data() + at), length);
    at += accepted;
    // Draining to stream_empty always makes room: feed never stalls twice in a row.
    stalled = accepted ? 0 : stalled + 1;
    REQUIRE(stalled < 2);
    REQUIRE(drain() == TD_JSON_STREAM_EMPTY);
  }
  td_json_stream_finish(stream);
  int32_t status = drain();
  if (final_status) *final_status = status;
  else REQUIRE(status == TD_JSON_STREAM_EMPTY);
  return out;
}

// Review focus 5: no document is duplicated or dropped at any chunk or batch boundary.
void stream_boundary_exactness() {
  corpus c = make_corpus(10000);
  std::vector<uint64_t> expected = expected_digests(c);
  struct shape { uint64_t max_document, batch; } shapes[] = {{1 << 20, 1 << 20}, {512, 512}, {300, 4096}};
  for (auto [max_document, batch] : shapes) {
    for (size_t chunk : {size_t(1), size_t(7), size_t(64), size_t(4096), size_t(1000000)}) {
      if (chunk == 1 && max_document != 512) continue;  // one byte at a time once is enough
      void* s = td_json_session_create(&limits);
      void* stream = td_json_stream_create(s, max_document, batch);
      REQUIRE(stream != nullptr);
      std::vector<uint64_t> got = run_stream(stream, c.bytes, chunk);
      REQUIRE(got.size() == expected.size());
      REQUIRE(got == expected);
      REQUIRE(td_json_stream_truncated_bytes(stream) == 0);
      td_json_stream_release(stream);
      td_json_session_release(s);
    }
  }
}

struct harness {
  void* session;
  void* stream;
  JsonResult result{};
  explicit harness(uint64_t max_document = 1 << 20, uint64_t batch = 1 << 20) {
    session = td_json_session_create(&limits);
    stream = td_json_stream_create(session, max_document, batch);
    REQUIRE(stream != nullptr);
  }
  ~harness() {
    td_json_stream_release(stream);
    td_json_session_release(session);
  }
  uint64_t feed(const std::string& bytes) {
    return td_json_stream_feed(stream, reinterpret_cast<const uint8_t*>(bytes.data()), bytes.size());
  }
  int32_t next() { return td_json_stream_next(stream, &result); }
  std::string root_text() { return text_of(static_cast<const myna_value_view*>(result.view), 0); }
  uint64_t root_payload() { return static_cast<const myna_value_view*>(result.view)->nodes[0].payload; }
  uint32_t root_kind() { return static_cast<const myna_value_view*>(result.view)->nodes[0].tag & MYNA_VALUE_KIND_MASK; }
};

void truncation_and_scalars() {
  {
    // A final incomplete container is reported after finish, with its bytes.
    harness h;
    REQUIRE(h.feed("{\"a\":1}\n{\"b\":") == 13);
    REQUIRE(h.next() == TD_JSON_OK);
    REQUIRE(h.next() == TD_JSON_STREAM_EMPTY);
    REQUIRE(td_json_stream_truncated_bytes(h.stream) == 5);
    td_json_stream_finish(h.stream);
    REQUIRE(h.next() == TD_JSON_STREAM_TRUNCATED);
    REQUIRE(h.next() == TD_JSON_STREAM_TRUNCATED);  // stays failed
    REQUIRE(td_json_stream_truncated_bytes(h.stream) == 5);
    REQUIRE(td_json_stream_skip(h.stream) == TD_JSON_ARGUMENT);
  }
  for (const char* tail : {"\"abc", "[1, 2", "{\"k\":\"ab", "\"caf\xc3", "\xe4\xb8"}) {
    harness h;
    REQUIRE(h.feed(std::string("[0] ") + tail) > 0);
    td_json_stream_finish(h.stream);
    REQUIRE(h.next() == TD_JSON_OK);
    REQUIRE(h.next() == TD_JSON_STREAM_TRUNCATED);
  }
  {
    // A root number touching the end of the input may continue until finish.
    harness h;
    REQUIRE(h.feed("12") == 2);
    REQUIRE(h.next() == TD_JSON_STREAM_EMPTY);
    REQUIRE(h.feed("3") == 1);
    REQUIRE(h.next() == TD_JSON_STREAM_EMPTY);
    REQUIRE(h.feed(" tr") == 3);
    REQUIRE(h.next() == TD_JSON_OK && h.root_kind() == MYNA_VALUE_I64 && h.root_payload() == 123);
    REQUIRE(h.next() == TD_JSON_STREAM_EMPTY);
    REQUIRE(h.feed("ue") == 2);
    REQUIRE(h.next() == TD_JSON_STREAM_EMPTY);
    td_json_stream_finish(h.stream);
    REQUIRE(h.next() == TD_JSON_OK && h.root_kind() == MYNA_VALUE_BOOL);
    REQUIRE(h.next() == TD_JSON_STREAM_EMPTY);
    REQUIRE(h.next() == TD_JSON_STREAM_EMPTY);
    REQUIRE(h.feed("[1]") == 0);  // finished
  }
  {
    // A finished final literal that is not one is a syntax error, not truncation.
    harness h;
    h.feed("[1] tru");
    td_json_stream_finish(h.stream);
    REQUIRE(h.next() == TD_JSON_OK);
    REQUIRE(h.next() == TD_JSON_SYNTAX);
  }
  {
    // Whitespace-only input and empty input are no documents.
    harness h;
    h.feed(" \n\t\r\n ");
    REQUIRE(h.next() == TD_JSON_STREAM_EMPTY);
    td_json_stream_finish(h.stream);
    REQUIRE(h.next() == TD_JSON_STREAM_EMPTY);
  }
}

void errors_and_skip() {
  {
    // Document-level errors fail the stream until skip() resumes after the document.
    harness h;
    h.feed("{\"a\":1}\n[1,]\n{\"a\":1,\"a\":2}\n[01]\n} {\"c\":3}\n");
    td_json_stream_finish(h.stream);
    REQUIRE(h.next() == TD_JSON_OK);
    for (int bad = 0; bad < 4; ++bad) {
      REQUIRE(h.next() == TD_JSON_SYNTAX);
      REQUIRE(!h.result.view);
      REQUIRE(h.next() == TD_JSON_SYNTAX);  // stays failed
      REQUIRE(h.feed("[9]") == 0);           // a failed stream accepts nothing
      REQUIRE(td_json_stream_skip(h.stream) == TD_JSON_OK);
    }
    REQUIRE(h.next() == TD_JSON_OK && h.root_kind() == MYNA_VALUE_OBJECT);
    REQUIRE(text_of(static_cast<const myna_value_view*>(h.result.view), 1) == "c");
    REQUIRE(h.next() == TD_JSON_STREAM_EMPTY);
    REQUIRE(td_json_stream_skip(h.stream) == TD_JSON_ARGUMENT);  // not failed
  }
  {
    // Stage-1 errors cover the batch and cannot be skipped.
    for (const char* bad : {"[1]\n\"\xff\"\n[2]\n", "[1]\n\"a\x01\"\n[2]\n"}) {
      harness h;
      h.feed(bad);
      td_json_stream_finish(h.stream);
      REQUIRE(h.next() == TD_JSON_SYNTAX);
      REQUIRE(td_json_stream_skip(h.stream) == TD_JSON_ARGUMENT);
    }
  }
  {
    // A BOM is accepted before the first document only.
    harness h;
    h.feed("\xEF\xBB\xBF[1]\n");
    REQUIRE(h.next() == TD_JSON_OK);
    REQUIRE(h.next() == TD_JSON_STREAM_EMPTY);
    h.feed("\xEF\xBB\xBF[2]\n");
    REQUIRE(h.next() == TD_JSON_SYNTAX);
    harness split;
    split.feed("\xEF");
    REQUIRE(split.next() == TD_JSON_STREAM_EMPTY);
    split.feed("\xBB\xBF{}");
    td_json_stream_finish(split.stream);
    REQUIRE(split.next() == TD_JSON_OK && split.root_kind() == MYNA_VALUE_OBJECT);
  }
  {
    // Commas do not separate documents.
    harness h;
    h.feed("[1],[2]\n");
    REQUIRE(h.next() == TD_JSON_OK);
    REQUIRE(h.next() == TD_JSON_SYNTAX);
  }
  {
    // Limits: a complete document above max_document_bytes can be skipped; an incomplete one that
    // already exceeds it cannot. Session limits apply per document.
    harness h(16, 16);
    const std::string input = "[1]\n[\"0123456789abcdef\"]\n[2]\n";
    REQUIRE(h.feed(input) == input.size());
    REQUIRE(h.next() == TD_JSON_OK);
    REQUIRE(h.next() == TD_JSON_LIMIT);
    REQUIRE(td_json_stream_skip(h.stream) == TD_JSON_OK);
    REQUIRE(h.next() == TD_JSON_OK && h.root_kind() == MYNA_VALUE_ARRAY);
    REQUIRE(h.next() == TD_JSON_STREAM_EMPTY);
    REQUIRE(h.feed("[\"0123456789abcdef") == 18);
    REQUIRE(h.next() == TD_JSON_LIMIT);
    REQUIRE(td_json_stream_skip(h.stream) == TD_JSON_ARGUMENT);
    JsonLimitsStruct deep = limits;
    deep.max_depth = 2;
    void* s = td_json_session_create(&deep);
    void* stream = td_json_stream_create(s, 64, 64);
    td_json_stream_feed(stream, reinterpret_cast<const uint8_t*>("[[[1]]] [[1]]"), 13);
    td_json_stream_finish(stream);
    JsonResult r{};
    REQUIRE(td_json_stream_next(stream, &r) == TD_JSON_LIMIT);
    REQUIRE(td_json_stream_skip(stream) == TD_JSON_OK);
    REQUIRE(td_json_stream_next(stream, &r) == TD_JSON_OK);
    td_json_stream_release(stream);
    td_json_session_release(s);
  }
  {
    // Options and arguments.
    void* s = td_json_session_create(&limits);
    REQUIRE(td_json_stream_create(s, 0, 64) == nullptr);
    REQUIRE(td_json_stream_create(s, 128, 64) == nullptr);
    REQUIRE(td_json_stream_create(s, uint64_t(1) << 31, uint64_t(1) << 31) == nullptr);
    REQUIRE(td_json_stream_create(nullptr, 64, 64) == nullptr);
    void* stream = td_json_stream_create(s, 64, 64);
    JsonResult misaligned[2]{};
    REQUIRE(td_json_stream_next(stream, reinterpret_cast<uint8_t*>(misaligned) + 1) == TD_JSON_ARGUMENT);
    REQUIRE(td_json_stream_next(nullptr, misaligned) == TD_JSON_ARGUMENT);
    REQUIRE(td_json_stream_feed(stream, nullptr, 4) == 0);
    REQUIRE(td_json_stream_skip(nullptr) == TD_JSON_ARGUMENT);
    REQUIRE(td_json_stream_truncated_bytes(nullptr) == 0);
    td_json_stream_release(stream);
    td_json_stream_release(nullptr);
    td_json_session_release(s);
  }
}

void backpressure_and_retain() {
  // The buffer holds at most 2 × max_document_bytes; feed accepts what fits.
  harness h(64, 64);
  std::string many;
  for (int i = 0; i < 100; ++i) many += "[" + std::to_string(i) + "]\n";
  REQUIRE(h.feed(many) == 128);
  REQUIRE(h.next() == TD_JSON_OK);
  REQUIRE(h.feed(many) == 0);  // the region is still being read
  // Retain moves the borrowed document out of the session: it survives the stream and session.
  JsonResult kept{};
  REQUIRE(td_json_session_retain(h.session, &kept) == TD_JSON_OK && kept.owner);
  size_t delivered = 1, at = 128;
  for (;;) {
    int32_t status;
    while ((status = h.next()) == TD_JSON_OK) ++delivered;
    REQUIRE(status == TD_JSON_STREAM_EMPTY);
    if (at == many.size()) break;
    uint64_t accepted = h.feed(many.substr(at));
    REQUIRE(accepted > 0 && accepted <= 128);
    at += accepted;
  }
  REQUIRE(delivered == 100);
  // A borrowed document survives feed(): its bytes live in the session, not the carry-over buffer.
  const std::string borrowed = "[\"borrowed text beyond inline\"]\n";
  REQUIRE(h.feed(borrowed) == borrowed.size());
  REQUIRE(h.next() == TD_JSON_OK);
  REQUIRE(h.feed(std::string(64, ' ')) == 64);
  REQUIRE(text_of(static_cast<const myna_value_view*>(h.result.view), 1) == "borrowed text beyond inline");
  auto* view = static_cast<const myna_value_view*>(kept.view);
  REQUIRE(view->nodes[0].tag == MYNA_VALUE_ARRAY && view->nodes[1].payload == 0);
  td_json_stream_release(h.stream);
  td_json_session_release(h.session);
  h.session = td_json_session_create(&limits);
  h.stream = td_json_stream_create(h.session, 64, 64);
  REQUIRE(view->nodes[1].payload == 0 && static_cast<const uint64_t*>(kept.lexical)[1] == ((uint64_t(1) << 32) | 1));
  td_json_release(&kept);
}

class counting_memory final : public std::pmr::memory_resource {
 public:
  size_t fail_at = SIZE_MAX, calls = 0, live = 0;
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

void allocations() {
  using truffledata::json::session;
  using truffledata::json::stream;
  corpus c = make_corpus(300);
  // Every upstream allocation of a stream run can fail: the failure is reported (by feed's stream
  // or the document's decode) and everything is freed.
  auto run = [&](counting_memory& memory) {
    session s(myna::value_limits{}, truffledata::json::storage_limits{}, &memory);
    stream st(s, 4096, 4096);
    size_t at = 0, documents = 0;
    session::retained* kept = nullptr;
    int32_t status = 0;
    while (true) {
      size_t accepted = st.feed(reinterpret_cast<const uint8_t*>(c.bytes.data() + at),
                                c.bytes.size() - at < 1000 ? c.bytes.size() - at : 1000);
      at += accepted;
      if (at == c.bytes.size()) st.finish();
      session::document_view d{};
      while ((status = st.next(d)) == truffledata::json::status_ok) {
        if (++documents == 100 && !kept) {
          try { kept = s.retain(); } catch (const std::bad_alloc&) { status = truffledata::json::status_allocation; break; }
        }
      }
      if (status != truffledata::json::status_stream_empty || st.finished()) break;
    }
    if (kept) kept->destroy();
    return std::pair<int32_t, size_t>(status, documents);
  };
  counting_memory counting;
  auto [status, documents] = run(counting);
  REQUIRE(status == truffledata::json::status_stream_empty && documents == c.documents.size());
  REQUIRE(counting.live == 0 && counting.calls > 3);
  for (size_t i = 0; i < counting.calls; ++i) {
    counting_memory memory;
    memory.fail_at = i;
    auto [failed, delivered] = run(memory);
    REQUIRE(failed == truffledata::json::status_allocation);
    REQUIRE(memory.live == 0);
    (void)delivered;
  }
  // Steady state: no upstream allocation and no index allocation across 10,000 documents.
  corpus big = make_corpus(10000);
  counting_memory steady;
  {
    session s(myna::value_limits{}, truffledata::json::storage_limits{}, &steady);
    stream st(s, 1 << 20, 1 << 20);
    size_t at = 0, documents = 0, calls_after_warmup = 0;
    session::document_view d{};
    while (at < big.bytes.size()) {
      at += st.feed(reinterpret_cast<const uint8_t*>(big.bytes.data() + at),
                    big.bytes.size() - at < 65536 ? big.bytes.size() - at : 65536);
      while (st.next(d) == truffledata::json::status_ok) {
        if (++documents == 2000) calls_after_warmup = steady.calls;
        if (documents > 2000) REQUIRE(d.index_allocations == 0);
      }
    }
    st.finish();
    while (st.next(d) == truffledata::json::status_ok) ++documents;
    REQUIRE(documents == big.documents.size());
    REQUIRE(steady.calls == calls_after_warmup);
    REQUIRE(st.buffer_capacity() <= 2 * (size_t(1) << 20) + simdjson::SIMDJSON_PADDING);
  }
  REQUIRE(steady.live == 0);
}

}  // namespace

int main() {
  truncation_and_scalars();
  errors_and_skip();
  backpressure_and_retain();
  stream_boundary_exactness();
  allocations();
  std::puts("stream tests passed");
  return 0;
}
