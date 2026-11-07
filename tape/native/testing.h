#ifndef TRUFFLEDATA_TAPE_TESTING_H
#define TRUFFLEDATA_TAPE_TESTING_H
// Test-only JSON corpus shared by the tape and JSON native tests (moved from
// json/native/decoder_test.cc; tape-gbps plan Task 1): the comparison harness's fixture document,
// objects with a chosen duplicate key, and the 200-document differential corpus.
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace truffledata::tape::testing {

// An object of `count` members "kA", "k1", "k2", ...; the member at `duplicate_at` (SIZE_MAX: none)
// is spelled `spelling` instead (raw JSON key text, escapes allowed).
inline std::string object_with_keys(size_t count, size_t duplicate_at, const std::string& spelling) {
  std::string source = "{";
  for (size_t i = 0; i < count; ++i) {
    if (i) source += ',';
    std::string key = i == duplicate_at ? spelling : (i == 0 ? "kA" : "k" + std::to_string(i));
    source += "\"" + key + "\":" + std::to_string(i);
  }
  return source + "}";
}

// The fixture document of the comparison harness and the perf probe (nine members, 210 B for i=1).
inline std::string fixture(int i) {
  std::string tags = "[", scores = "[";
  for (int k = 0; k < 1 + i % 4; k++) tags += (k ? ",\"t" : "\"t") + std::to_string(k) + "\"";
  for (int k = 0; k < 3 + (i & 1); k++) scores += (k ? "," : "") + std::to_string(i + k);
  std::string note = (i & 1) ? "\"line \\\"" + std::to_string(i) + "\\\" with \\\\ escapes\\n\\t\\u00e9 and \\ud83d\\ude00 tail\""
                             : "\"plain note for item " + std::to_string(i) + " padded beyond the inline text limit\"";
  return "{\"id\":" + std::to_string(1000 + 7 * i) + ",\"name\":\"item-" + std::to_string(i) + "\",\"price\":" +
         std::to_string(i) + ".25,\"active\":" + (i % 3 == 0 ? "true" : "false") + ",\"tags\":" + tags + "],\"dims\":{\"w\":" +
         std::to_string(3 * i) + ",\"h\":" + std::to_string(i) + ".5,\"unit\":\"cm\"},\"scores\":" + scores + "],\"note\":" + note +
         ",\"parent\":null}";
}

// 200 documents: fixture shapes plus escapes, exact numbers, deep nesting, wide objects, scalars.
inline std::vector<std::string> corpus() {
  std::vector<std::string> documents;
  for (int i = 0; i < 160; ++i) documents.push_back(fixture(i));
  std::string big = "[";
  for (int i = 0; i < 300; ++i) big += (i ? "," : "") + fixture(i);
  documents.push_back(big + "]");
  std::string dense = "[";
  for (int i = 0; i < 2000; ++i) dense += i ? ",1" : "1";
  documents.push_back(dense + "]");
  for (int i = 0; i < 38; ++i) {
    switch (i % 6) {
      case 0: documents.push_back("[" + std::to_string(i) + ",-0,1E400,18446744073709551615,12345678901234567890123,-1.5e-300]"); break;
      case 1: documents.push_back(object_with_keys(20 + i * 10, SIZE_MAX, "")); break;
      case 2: documents.push_back(std::string(size_t(40 + i), '[') + std::to_string(i) + std::string(size_t(40 + i), ']')); break;
      case 3: documents.push_back("\"" + std::string(size_t(i * 7), 'x') + "\\n\""); break;
      case 4: documents.push_back("{\"\\u00e9\":\"" + std::string(size_t(i), 'y') + "\",\"k\":{\"\":[]}}"); break;
      default: documents.push_back(i % 2 ? "null" : "{}"); break;
    }
  }
  return documents;
}

}  // namespace truffledata::tape::testing
#endif
