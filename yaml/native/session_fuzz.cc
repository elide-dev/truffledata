#include "yaml/native/session.h"
#include <cstdio>
#include <cstdlib>
#include <memory>
using namespace truffledata;
namespace {
[[noreturn]] void fail(const char* what, const char* detail) {
  std::fprintf(stderr, "yaml session harness: %s: %s\n", what, detail);
  std::abort();
}
void check(const td_tape_view& view) {
  if (const char* reason = tape::ctape::validate(view)) fail("invalid tape", reason);
}
struct Release { void operator()(yaml::session::retained* p) const { p->destroy(); } };
}
extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  if (size < 2 || size > 65536) return 0;
  const uint8_t control = data[0];
  const size_t split = size > 2 ? data[1] % (size - 1) : 0;
  data += 2; size -= 2;
  yaml::session_options options{};
  options.values = {4096, 65536, 32};
  options.storage = {262144, 2 * 1024 * 1024};
  options.rules.schema = static_cast<yaml::schema>(control & 3);
  options.rules.last_wins = control & 4;
  options.rules.merge_keys = control & 8;
  options.rules.reject_aliases = control & 16;
  options.rules.max_aliases = 128;
  options.max_alias_expansion = 4096;
  std::unique_ptr<yaml::session::retained, Release> retained;
  {
  yaml::session parser(options);
  auto decode = [&](const uint8_t* bytes, size_t length, bool keep) {
    try {
      parser.decode(bytes, length, (control >> 5) & 6);
      if (parser.live()) {
        check(parser.view());
        if (keep) retained.reset(parser.retain());
      }
    } catch (const yaml::error& error) {
      if (error.code != yaml::status_syntax && error.code != yaml::status_limit &&
          error.code != yaml::status_multiple_documents) {
        std::fprintf(stderr, "yaml session harness: status %d at %llu:%llu%s\n", int(error.code),
                     (unsigned long long)error.line, (unsigned long long)error.column, error.parser ? " (parser)" : "");
        fail("unexpected error", error.what());
      }
    } catch (const std::length_error&) {
      // Counted index allocation budget.
    } catch (const std::bad_alloc&) {
    }
    if (retained) check(retained->view);
  };
  decode(data, size, true);
  decode(data, split, false);
  decode(data + split, size - split, false);
  decode(data, size, false);
  parser.reset();
  if (retained) check(retained->view);
  const char valid[] = R"({"ok": [1, "text"]})";
  parser.decode(reinterpret_cast<const uint8_t*>(valid), sizeof(valid) - 1, 4);
  check(parser.view());
  if (retained) check(retained->view);
  }
  if (retained) check(retained->view);
  return 0;
}
