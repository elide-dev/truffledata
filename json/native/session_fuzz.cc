// One input is a reproducible sequence; no state leaks between fuzz iterations.
#include "json/native/session.h"
#include "json/native/status.h"
#include <cstdio>
#include <cstdlib>
#include <memory>
#include <vector>

using namespace truffledata;
namespace {
[[noreturn]] void fail(const char* what, const char* detail) {
  std::fprintf(stderr, "json session harness: %s: %s\n", what, detail);
  std::abort();
}
void check(const td_tape_view& view) {
  if (const char* reason = tape::ctape::validate(view)) fail("invalid tape", reason);
}
struct Release { void operator()(json::session::retained* p) const { p->destroy(); } };
}
extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  if (size < 2 || size > 65536) return 0;
  const uint8_t control = data[0];
  const size_t split = size > 2 ? data[1] % (size - 1) : 0;
  data += 2; size -= 2;
  std::unique_ptr<json::session::retained, Release> retained;
  {
  json::session parser({4096, 65536, 32}, {131072, 2 * 1024 * 1024});
  auto decode = [&](const uint8_t* bytes, size_t length, bool keep) {
    try {
      // UTF-16's ABI requires aligned input. Preserve odd byte lengths for rejection.
      std::vector<char16_t> aligned((length + 1) / 2 + 1);
      std::memcpy(aligned.data(), bytes, length);
      auto encoding = static_cast<json::session::encoding>(control % 3);
      parser.decode(reinterpret_cast<const char*>(aligned.data()), length, encoding,
                    (control & 4) != 0, (control & 8) != 0);
      if (parser.live()) {
        check(parser.view());
        if (keep) retained.reset(parser.retain());
      }
    } catch (...) {
      int status = json::current_exception_status();
      if (status != json::status_syntax && status != json::status_limit &&
          status != json::status_allocation) {
        std::fprintf(stderr, "json session harness: status %d\n", status);
        try { throw; } catch (const std::exception& e) { fail("unexpected error", e.what()); } catch (...) {}
        fail("unexpected error", "non-standard exception");
      }
    }
    if (retained) check(retained->tape);
  };
  decode(data, size, true);
  decode(data, split, false);
  decode(data + split, size - split, false);
  decode(data, size, false);  // Reuse after either success or failure.
  parser.reset();
  if (retained) check(retained->tape);
  // Known valid input must still work after the adversarial sequence.
  parser.decode("{\"ok\":[1,\"text\"]}", 17);
  check(parser.view());
  if (retained) check(retained->tape);
  }
  if (retained) check(retained->tape);
  return 0;
}
