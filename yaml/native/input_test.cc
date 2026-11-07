// Input preparation (plan Task 2, input_test): strict UTF-8, the c-printable set, BOM handling and
// UTF-16/UTF-32 transcoding to the same tape as UTF-8.
#include "yaml/native/input.h"

#include "yaml/native/session.h"
#include "yaml/native/testing.h"

#include <cstdio>
#include <string>
#include <vector>

using namespace truffledata::yaml;
using truffledata::yaml::testing::render;

namespace {
bool rejected(const std::string& text) {
  std::vector<uint64_t> newlines((text.size() + 63) / 64 + 1);
  try {
    validate(text, newlines.data());
  } catch (const error& e) {
    return e.code == status_syntax;
  }
  return false;
}

std::string u8(std::initializer_list<int> bytes) {
  std::string s;
  for (int b : bytes) s.push_back(char(b));
  return s;
}

void utf8_strictness() {
  require(!rejected("plain ascii: text\n"));
  require(!rejected("é ü \xE4\xB8\xAD \xF0\x9F\x98\x80"));            // 2-, 3- and 4-byte forms
  require(rejected(u8({0xC0, 0xAF})));                                // overlong '/'
  require(rejected(u8({0xE0, 0x80, 0xAF})));                          // overlong
  require(rejected(u8({0xF0, 0x80, 0x80, 0xAF})));                    // overlong
  require(rejected(u8({0xED, 0xA0, 0x80})));                          // U+D800 surrogate
  require(rejected(u8({0xED, 0xBF, 0xBF})));                          // U+DFFF surrogate
  require(rejected(u8({0xF4, 0x90, 0x80, 0x80})));                    // U+110000
  require(rejected("ok " + u8({0xE4, 0xB8})));                        // truncated tail
  require(rejected("ok " + u8({0x80})));                              // stray continuation
}

void printable_set() {
  require(!rejected("tab\there\r\nlf\n"));
  for (int c = 0; c < 0x20; ++c) {
    std::string s = "a";
    s.push_back(char(c));
    bool allowed = c == '\t' || c == '\n' || c == '\r';
    require(rejected(s) == !allowed);
  }
  require(rejected("del \x7F"));
  require(!rejected(u8({0xC2, 0x85})));        // U+0085 NEL is printable
  require(!rejected(u8({0xC2, 0xA0})));        // U+00A0
  for (int c = 0x80; c <= 0x9F; ++c) {
    if (c == 0x85) continue;
    require(rejected(u8({0xC2, c})));          // C1 controls
  }
  require(rejected(u8({0xEF, 0xBF, 0xBE})));   // U+FFFE
  require(rejected(u8({0xEF, 0xBF, 0xBF})));   // U+FFFF
  require(!rejected(u8({0xEF, 0xBF, 0xBD})));  // U+FFFD
  require(!rejected(u8({0xEF, 0xBB, 0xBF})));  // U+FEFF (a BOM later in a stream is printable)
  // Long runs exercise the eight-byte fast path on both sides of each rejected byte.
  std::string longer(1000, 'x');
  require(!rejected(longer));
  for (size_t at : {size_t(0), size_t(7), size_t(8), size_t(63), size_t(64), size_t(999)}) {
    std::string bad = longer;
    bad[at] = '\x01';
    require(rejected(bad));
  }
}

void newline_bitmap() {
  std::string text = std::string(70, 'a') + "\n" + std::string(10, 'b') + "\n";
  std::vector<uint64_t> bits((text.size() + 63) / 64);
  validate(text, bits.data());
  require(has_newline(bits.data(), 0, text.size()));
  require(!has_newline(bits.data(), 0, 70));
  require(has_newline(bits.data(), 70, 71));
  require(!has_newline(bits.data(), 71, 81));
  require(count_newlines(bits.data(), 0, text.size()) == 2);
}

std::string utf16(const std::u16string& s, bool little, bool bom = true) {
  std::string out;
  if (bom) out += little ? "\xFF\xFE" : "\xFE\xFF";
  for (char16_t c : s) {
    char lo = char(c & 0xFF), hi = char(c >> 8);
    if (little) out += lo, out += hi;
    else out += hi, out += lo;
  }
  return out;
}
std::string utf32(const std::u32string& s, bool little) {
  std::string out = little ? std::string("\xFF\xFE\x00\x00", 4) : std::string("\x00\x00\xFE\xFF", 4);
  for (char32_t c : s) {
    char b[4] = {char(c & 0xFF), char(c >> 8 & 0xFF), char(c >> 16 & 0xFF), char(c >> 24)};
    if (little) out.append(b, 4);
    else out += b[3], out += b[2], out += b[1], out += b[0];
  }
  return out;
}

std::string decode(session& s, const std::string& bytes) {
  auto d = s.decode(reinterpret_cast<const uint8_t*>(bytes.data()), bytes.size(), 0);
  return render(d.view);
}
int32_t failure(session& s, const std::string& bytes) {
  try {
    s.decode(reinterpret_cast<const uint8_t*>(bytes.data()), bytes.size(), 0);
  } catch (const error& e) {
    return e.code;
  }
  return 0;
}

void encodings() {
  session_options options;
  session s(options);
  const std::u16string text16 = u"k: vé\nlist: [1, \U0001F600, x]\n";
  const std::u32string text32 = U"k: vé\nlist: [1, \U0001F600, x]\n";
  const std::string text8 = "k: v\xC3\xA9\nlist: [1, \xF0\x9F\x98\x80, x]\n";
  std::string expected = decode(s, text8);
  require(expected == "{\"k\":\"v\xC3\xA9\",\"list\":[1,\"\xF0\x9F\x98\x80\",\"x\"]}");
  require(decode(s, "\xEF\xBB\xBF" + text8) == expected);  // UTF-8 BOM skipped
  require(decode(s, utf16(text16, true)) == expected);
  require(decode(s, utf16(text16, false)) == expected);
  require(decode(s, utf32(text32, true)) == expected);
  require(decode(s, utf32(text32, false)) == expected);
  // Unaligned UTF-16 input decodes unit by unit with the same result.
  std::string shifted = " " + utf16(text16, true);
  auto d = s.decode(reinterpret_cast<const uint8_t*>(shifted.data()) + 1, shifted.size() - 1, 0);
  require(render(d.view) == expected);
  // An unpaired surrogate is rejected, aligned or not, and so is a truncated unit.
  std::u16string lone = u"k: ";
  lone.push_back(char16_t(0xD800));
  lone += u"x\n";
  require(failure(s, utf16(lone, true)) == status_syntax);
  require(failure(s, utf16(lone, false)) == status_syntax);
  std::string odd = " " + utf16(lone, true);
  try {
    s.decode(reinterpret_cast<const uint8_t*>(odd.data()) + 1, odd.size() - 1, 0);
    require(false);
  } catch (const error& e) {
    require(e.code == status_syntax);
  }
  std::u16string low = u"k: ";
  low.push_back(char16_t(0xDC00));
  require(failure(s, utf16(low, true)) == status_syntax);
  require(failure(s, utf16(u"k: v", true) + "x") == status_syntax);
  std::u32string bad32 = U"k: ";
  bad32.push_back(char32_t(0x110000));
  require(failure(s, utf32(bad32, true)) == status_syntax);
  bad32.back() = char32_t(0xD800);
  require(failure(s, utf32(bad32, false)) == status_syntax);
  // Transcoded input is still checked for c-printable: a NUL from UTF-16 is rejected.
  std::u16string nul = u"k: ";
  nul.push_back(char16_t(0));
  require(failure(s, utf16(nul, true)) == status_syntax);
  // Validation failures carry the line and column.
  try {
    std::string bad = "a: 1\nb: x\x01y\n";
    s.decode(reinterpret_cast<const uint8_t*>(bad.data()), bad.size(), 0);
    require(false);
  } catch (const error& e) {
    require(e.code == status_syntax && e.line == 2 && e.column == 5);
  }
}
}  // namespace

int main() {
  try {
    utf8_strictness();
    printable_set();
    newline_bitmap();
    encodings();
  } catch (const std::exception& e) {
    std::fprintf(stderr, "input_test failure: %s\n", e.what());
    return 1;
  }
  std::printf("input checks passed\n");
  return 0;
}
