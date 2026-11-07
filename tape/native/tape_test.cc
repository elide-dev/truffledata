// The shared tape layer's own checks. JSON parity of tape_writer against myna::value_builder stays
// pinned by //json/native:test; these cover what the YAML producer adds: non-finite F64 nodes, owned
// DECIMAL text, node rewriting (erase/append/truncate) and failure injection on the arena.
#include "tape/native/arena.h"
#include "tape/native/index_memory.h"
#include "tape/native/key_table.h"
#include "tape/native/tape_writer.h"

#include <cmath>
#include <cstdio>
#include <cstring>
#include <limits>
#include <stdexcept>
#include <string>

using namespace truffledata::tape;

namespace {
void require_at(bool condition, int line) {
  if (!condition) throw std::runtime_error("check failed at line " + std::to_string(line));
}
#define require(condition) require_at((condition), __LINE__)

class fault_memory final : public std::pmr::memory_resource {
 public:
  size_t fail_at, calls = 0, live = 0;
  explicit fault_memory(size_t fail) : fail_at(fail) {}

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

void nonfinite_and_decimal() {
  index_memory memory(1 << 20, std::pmr::new_delete_resource());
  block_arena arena(&memory);
  tape_writer w(&arena, myna::value_limits{});
  size_t root = w.begin(MYNA_VALUE_ARRAY, 0);
  bool threw = false;
  try { w.floating(std::numeric_limits<double>::infinity()); } catch (const std::invalid_argument&) { threw = true; }
  require(threw && w.node_count() == 1);
  w.nonfinite(-std::numeric_limits<double>::infinity());
  w.nonfinite(std::numeric_limits<double>::quiet_NaN());
  threw = false;
  try { w.nonfinite(1.0); } catch (const std::invalid_argument&) { threw = true; }
  require(threw);
  w.decimal("18446744073709551616");  // 20 bytes: owned
  w.decimal("31");                    // inline
  w.end(root, 4);
  require(w.has_nonfinite());
  auto view = w.view();
  require(view.flags == MYNA_VALUE_ALLOW_NON_FINITE && tape_writer::nonfinite_flag == 1u);
  // Myna's own builder accepts the same values only under the flag (Myna 0.10.0).
  {
    myna::value_builder builder(&arena, myna::value_limits{}, MYNA_VALUE_ALLOW_NON_FINITE);
    builder.begin_array();
    builder.floating(-std::numeric_limits<double>::infinity());
    builder.floating(std::numeric_limits<double>::quiet_NaN());
    builder.end();
    require(builder.view().flags == MYNA_VALUE_ALLOW_NON_FINITE);
    myna::value_builder strict(&arena);
    bool rejected = false;
    try { strict.floating(std::numeric_limits<double>::infinity()); } catch (const std::invalid_argument&) { rejected = true; }
    require(rejected);
  }
  double d;
  std::memcpy(&d, &view.nodes[1].payload, sizeof d);
  require(view.nodes[1].tag == MYNA_VALUE_F64 && std::isinf(d) && d < 0);
  std::memcpy(&d, &view.nodes[2].payload, sizeof d);
  require(std::isnan(d));
  require(view.nodes[3].tag == (MYNA_VALUE_DECIMAL | MYNA_VALUE_REGION_TEXT));
  require(w.text_of(3) == "18446744073709551616");
  require(view.nodes[4].tag == MYNA_VALUE_DECIMAL && w.text_of(4) == "31");
}

// [ "a", [1, 2], {"k": "v"}, true ] -> erase the nested array -> [ "a", {"k": "v"}, true ]
void erase_and_append() {
  index_memory memory(1 << 20, std::pmr::new_delete_resource());
  block_arena arena(&memory);
  tape_writer w(&arena, myna::value_limits{});
  size_t root = w.begin(MYNA_VALUE_ARRAY, 0);
  w.string("a");
  size_t inner = w.begin(MYNA_VALUE_ARRAY, 1);
  w.integer(1);
  w.integer(2);
  w.end(inner, 2);
  size_t object = w.begin(MYNA_VALUE_OBJECT, 1);
  w.string("k");
  w.string("a value longer than sixteen bytes");
  w.end(object, 1);
  w.boolean(true);
  require(w.node_count() == 9);
  w.erase(2, 3);
  w.end(root, 3);
  auto view = w.view();
  require(view.node_count == 6 && view.nodes[0].next == 6 && view.nodes[0].length == 3);
  require(view.nodes[2].tag == MYNA_VALUE_OBJECT && view.nodes[2].next == 5);
  require(w.text_of(3) == "k" && w.text_of(4) == "a value longer than sixteen bytes");
  require(view.nodes[4].next == 5 && view.nodes[5].tag == MYNA_VALUE_BOOL && view.nodes[5].next == 6);
  // Copy the object (nodes 2..4) to the end: next pointers shift by the distance moved.
  myna_value_node copy[3];
  std::memcpy(copy, view.nodes + 2, sizeof copy);
  w.truncate(6);
  w.append(copy, 3, 4);
  view = w.view();
  require(view.node_count == 9 && view.nodes[6].tag == MYNA_VALUE_OBJECT && view.nodes[6].next == 9);
  require(w.text_of(7) == "k" && view.nodes[8].next == 9);
}

void arena_failures() {
  for (size_t fail = 0;; ++fail) {
    fault_memory upstream(fail);
    bool failed = false;
    try {
      index_memory memory(1 << 24, &upstream);
      block_arena arena(&memory);
      tape_writer w(&arena, myna::value_limits{});
      key_table keys(&arena);
      size_t root = w.begin(MYNA_VALUE_OBJECT, 0);
      std::string names[100];
      for (int i = 0; i < 100; ++i) {
        names[i] = "key-" + std::to_string(i) + "-padded-beyond-inline";
        require(keys.insert(key_hash(names[i]), names[i]));
        w.string(names[i]);
        w.nonfinite(std::numeric_limits<double>::infinity());
      }
      require(!keys.insert(key_hash(names[7]), names[7]));
      w.end(root, 100);
      require(w.view().node_count == 201);
    } catch (const std::bad_alloc&) {
      failed = true;
    }
    require(upstream.live == 0);
    if (!failed) {
      require(fail > 2);
      std::printf("arena_failures: %zu upstream allocations, each failed once\n", fail);
      return;
    }
  }
}
// Inline text is packed with fixed overlapping loads (no variable memcpy): for every length 0-16
// the node holds the bytes zero-padded, exactly as a memcpy into zeroed words, and string_words
// (pre-packed words from a producer) writes the identical node and charges the same text bytes.
void inline_text_packing() {
  std::pmr::monotonic_buffer_resource memory;
  myna::value_limits limits;
  const char source[] = "\x01\x80\xFF" "abcdefghijklmnopqrstu";
  for (size_t n = 0; n <= MYNA_VALUE_INLINE_BYTES; ++n) {
    tape_writer w(&memory, limits);
    w.begin(MYNA_VALUE_ARRAY, 0);
    w.string(std::string_view(source + 1, n));
    uint64_t words[2] = {0, 0};
    std::memcpy(words, source + 1, n);
    w.string_words(words[0], words[1], uint32_t(n));
    w.end(0, 2);
    const myna_value_node* nodes = w.view().nodes;
    require(nodes[1].tag == MYNA_VALUE_STRING && nodes[1].length == n);
    require(nodes[1].payload == words[0] && nodes[1].next == words[1]);
    require(std::memcmp(&nodes[1], &nodes[2], sizeof(myna_value_node)) == 0);
    require(w.text_of(1) == std::string_view(source + 1, n));
  }
  tape_writer w(&memory, limits);
  bool rejected = false;
  try {
    w.string_words(0, 0, MYNA_VALUE_INLINE_BYTES + 1);
  } catch (const std::invalid_argument&) {
    rejected = true;
  }
  require(rejected);
  myna::value_limits small = limits;
  small.text_bytes = 3;
  tape_writer limited(&memory, small);
  bool charged = false;
  try {
    limited.string_words(0x6464646464ull, 0, 5);
  } catch (const std::length_error&) {
    charged = true;
  }
  require(charged);
}
}  // namespace

int main() {
  try {
    inline_text_packing();
    nonfinite_and_decimal();
    erase_and_append();
    arena_failures();
  } catch (const std::exception& e) {
    std::fprintf(stderr, "tape_test failure: %s\n", e.what());
    return 1;
  }
  std::printf("tape layer checks passed\n");
  return 0;
}
