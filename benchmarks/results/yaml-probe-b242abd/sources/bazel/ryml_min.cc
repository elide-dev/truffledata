#include <c4/yml/yml.hpp>
#include <c4/yml/parse_engine.def.hpp>
#include <c4/yml/extra/event_handler_ints.hpp>
#include <cstdio>
#include <string>
#include <vector>

int main(int argc, char **argv) {
  std::string src = argc > 1 ? argv[1] : "a: [1, 2]";
  std::vector<int> evts(64 + src.size() * 4);
  std::string arena(64 + src.size(), '\0');
  c4::yml::extra::EventHandlerInts h;
  c4::yml::ParseEngine<c4::yml::extra::EventHandlerInts> p(&h);
  h.reset(c4::substr(&src[0], src.size()), c4::substr(&arena[0], arena.size()), evts.data(), (int)evts.size());
  p.parse_in_place_ev("min", c4::substr(&src[0], src.size()));
  std::printf("%d\n", (int)h.required_size_events());
  return 0;
}
