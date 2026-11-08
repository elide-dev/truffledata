#include "json/native/testing/text_fusion_session.h"
#include "json/native/session.h"
#include "json/native/status.h"
#include "tape/native/testing.h"
#include <algorithm>
#include <cstdio>
#include <vector>
using namespace truffledata;
namespace {
void require(bool value) { if (!value) throw std::runtime_error("fused text mismatch"); }
struct result { int status; size_t used; std::vector<uint64_t> words, table; std::vector<uint8_t> bytes; };
template<class Session> result decode(Session& session, const std::string& source, bool fused, size_t capacity) {
  result out{};
  out.bytes.assign(capacity, 0xa5);
  // Capacity zero still receives a valid pointer, as the C API requires.
  uint8_t zero = 0;
  auto* bytes = capacity ? out.bytes.data() : &zero;
  try {
    auto d = [&] {
      if constexpr (std::is_same_v<Session, json::session>) {
        return session.decode(source.data(), source.size(), Session::encoding::utf8, false, false);
      } else {
        return session.decode(source.data(), source.size(), Session::encoding::utf8,
                              false, false, fused ? bytes : nullptr, capacity);
      }
    }();
    const uint64_t* table;
    size_t used = session.write_text(bytes, capacity, table);
    if (used == tape::text_too_small) { out.status = json::status_text_too_small; return out; }
    out.status = json::status_ok; out.used = used;
    out.words.assign(d.tape->words, d.tape->words + d.tape->word_count);
    out.table.assign(table, table + d.tape->word_count);
  } catch (...) { out.status = json::current_exception_status(); }
  return out;
}
void compare(json::session& old, json_text_fusion::session& fresh, const std::string& source, size_t capacity) {
  auto expected = decode(old, source, false, capacity), actual = decode(fresh, source, true, capacity);
  require(expected.status == actual.status);
  if (expected.status == json::status_ok) {
    require(expected.used == actual.used && expected.words == actual.words && expected.table == actual.table);
    require(std::equal(expected.bytes.begin(), expected.bytes.begin() + expected.used, actual.bytes.begin()));
  }
}
}
int main() {
  json::session old({}, {});
  json_text_fusion::session fresh({}, {});
  auto corpus = tape::testing::corpus();
  corpus.insert(corpus.end(), {R"({"clé":"中文 😀","é":"é","x":"\u0000\n\t"})", R"(["","é","Ā","😀",null,1])"});
  size_t cases = 0;
  for (unsigned round = 0; round < 3; ++round) {
    for (const auto& source : corpus) {
      auto required = decode(old, source, false, source.size() * 2 + 16).used;
      for (size_t capacity : {size_t(0), size_t(1), required ? required - 1 : 0, required, source.size() * 2 + 16}) {
        compare(old, fresh, source, capacity); ++cases;
      }
    }
  }
  // Text capacity and cache warmness never change malformed-input status or allow stale output.
  for (auto source : {R"(["text","\q"])", R"({"a":"long string","a":1})", R"(["é",1,])", R"(["x",01])"}) {
    for (size_t capacity : {size_t(0), size_t(1), size_t(4096)}) {
      compare(old, fresh, source, capacity); ++cases;
      compare(old, fresh, corpus[0], 4096); ++cases;
    }
  }
  // Exact budget and one-byte-below budget retain the postpass's limit behavior.
  for (size_t budget : {size_t(4096), size_t(8191), size_t(8192), size_t(16384)}) {
    tape::storage_limits limits{}; limits.index_bytes = budget;
    json::session before({}, limits);
    json_text_fusion::session after({}, limits);
    for (size_t i : {size_t(0), size_t(161), size_t(1), size_t(0)}) {
      compare(before, after, corpus[i], corpus[i].size() * 2 + 16); ++cases;
    }
  }
  std::printf("fused text exact/status parity: %zu cases\n", cases);
}
