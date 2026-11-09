#include <cstdlib>
#include <climits>
#include <cstring>
int main(int argc, char** argv) {
  if (argc != 2) return 2;
  if (std::strcmp(argv[1], "address") == 0) {
    volatile char* p = static_cast<char*>(std::malloc(8));
    p[8] = 1;
    std::free(const_cast<char*>(p));
  } else {
    volatile int value = INT_MAX;
    volatile int result = value + argc;
    (void)result;
  }
  return 0;
}
