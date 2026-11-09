#include "json/native/session.h"
#include "json/native/status.h"
#include <algorithm>
#include <cstdio>
#include <string>
#include <vector>

using namespace truffledata;
namespace {
uint64_t random_state = 0x91a547cd82e603bf;
uint32_t next_random() {
  random_state ^= random_state >> 12;
  random_state ^= random_state << 25;
  random_state ^= random_state >> 27;
  return uint32_t((random_state * 0x2545f4914f6cdd1dull) >> 32);
}
std::string quote(const std::string& value, bool escaped) {
  std::string out = "\"";
  for (char c : value) {
    if (escaped || uint8_t(c) < 32) { out += "\\u00"; out += "0123456789abcdef"[uint8_t(c) >> 4]; out += "0123456789abcdef"[c & 15]; }
    else out += c;
  }
  out += '"';
  return out;
}
}
int main() {
  // A changed suffix reuses prefix views into the same template. Reinstalling that shape must
  // not turn the unchanged keys into NULs and let later escaped-NUL duplicates match it.
  for (int mode : {0, 1, 2}) {
    json::session warm({}, {});
    // The first object of a depth only primes it; the second installs the template.
    for (const char* source : {R"({"aa":1,"bb":2,"cc":3})", R"({"aa":1,"bb":2,"cc":3})", R"({"aa":1,"bb":2,"dd":3})"})
      warm.decode(source, std::strlen(source), json::session::encoding::utf8, mode == 2, mode == 0);
    const std::string duplicate = R"({"\u0000\u0000":1,"\u0000\u0000":2,"dd":3})";
    int status = json::status_ok;
    try { warm.decode(duplicate.data(), duplicate.size(), json::session::encoding::utf8, mode == 2, mode == 0); }
    catch (...) { status = json::current_exception_status(); }
    if (status != json::status_syntax) throw std::runtime_error("aliased shape prefix accepted duplicate");
  }
  json::session parser({}, {});
  std::vector<std::string> keys;
  std::string source;
  for (size_t iteration = 0; iteration < 1000000; ++iteration) {
    size_t count = next_random() % 42;
    keys.clear(); source = "{";
    bool escaped = iteration % 3 == 0;
    for (size_t i = 0; i < count; ++i) {
      std::string key = "k" + std::to_string(next_random() % 200);
      if (iteration % 17 == 0) key.assign(next_random() % 17, '\0');
      if (iteration % 13 == 0) key += std::string(next_random() % 40, 'x');
      if (i && next_random() % 4 == 0) key = keys[next_random() % i];
      keys.push_back(key);
      if (i) source += ',';
      source += quote(key, escaped && i % 2 == 0);
      source += i % 3 ? ":0" : ":[{\"inner\":\"v\\n\",\"other\":1}]";
    }
    source += '}';
    std::sort(keys.begin(), keys.end());
    bool duplicate = std::adjacent_find(keys.begin(), keys.end()) != keys.end();
    for (int mode : {0, 1, 2}) {
      bool validate = mode == 2, directories = mode == 0;
      int status = json::status_ok;
      try { parser.decode(source.data(), source.size(), json::session::encoding::utf8, validate, directories); }
      catch (...) { status = json::current_exception_status(); }
      if (status != (duplicate ? json::status_syntax : json::status_ok)) {
        std::fprintf(stderr, "shape fuzz mismatch at %zu mode=%d status=%d duplicate=%d: %s\n",
                     iteration, mode, status, duplicate, source.c_str());
        return 1;
      }
      if (!duplicate && !validate && tape::ctape::validate(parser.view())) {
        std::fprintf(stderr, "shape fuzz invalid tape at %zu\n", iteration); return 1;
      }
    }
  }
  std::puts("1,000,000 generated key sequences agree with a sorted decoded-key oracle in indexed, directoryless and validate-only modes");
}
