#include <cstdint>
#include <fstream>
#include <iterator>
#include <vector>
#include <cstdlib>
#include <cstdio>
extern "C" int LLVMFuzzerTestOneInput(const uint8_t*, size_t);
int main(int argc, char** argv) {
  const uint8_t empty = 0;
  LLVMFuzzerTestOneInput(&empty, 0);
  for (int i = 1; i < argc; ++i) {
    std::ifstream in(argv[i], std::ios::binary);
    if (!in) { std::fprintf(stderr, "Cannot open corpus file: %s\n", argv[i]); std::abort(); }
    std::vector<uint8_t> bytes((std::istreambuf_iterator<char>(in)), {});
    for (int repeat = 0; repeat < 2; ++repeat)
      LLVMFuzzerTestOneInput(bytes.empty() ? &empty : bytes.data(), bytes.size());
  }
}
