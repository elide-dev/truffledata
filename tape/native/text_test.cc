#include <cstdint>
#include <stdexcept>
#include <string>
#include <vector>
extern "C" int64_t td_tape_utf8_to_utf16(const uint8_t*, uint64_t, uint16_t*, uint64_t);
void require(bool value) { if (!value) throw std::runtime_error("tape transcoder"); }
int main() {
  require(td_tape_utf8_to_utf16(nullptr, 0, nullptr, 0) == 0);
  std::string input = "😀";
  for (int i = 0; i < 2000; ++i) input += "é";
  input += "中";
  std::vector<uint16_t> output(input.size(), 0xffff);
  auto* bytes = reinterpret_cast<const uint8_t*>(input.data());
  require(td_tape_utf8_to_utf16(bytes, input.size(), output.data(), 2002) == -1);
  require(output[0] == 0xffff);
  require(td_tape_utf8_to_utf16(bytes, input.size(), output.data(), output.size()) == 2003);
  require(output[0] == 0xd83d && output[1] == 0xde00 && output[2] == 0xe9 && output[2002] == 0x4e2d);
  require(output[2003] == 0xffff);
  const uint8_t bad[] = {0xed, 0xa0, 0x80};
  output[0] = 0xffff;
  require(td_tape_utf8_to_utf16(bad, 3, output.data(), output.size()) == -1 && output[0] == 0xffff);
}
