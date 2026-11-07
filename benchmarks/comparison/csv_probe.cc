// Native reference probe for the CSV comparison (not shipped): the csv/native kernels without any
// Java in the loop, a C++ consumer of the index (records and fields, as IndexedScanner), and
// simdutf validation. Prints one JSON line per measurement with raw samples (ns per byte).
//
// usage: csv_probe <dataset> <fixture path>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <functional>
#include <iterator>
#include <string>
#include <vector>

#include "csv/native/index.h"
#include "simdutf.h"

using namespace truffledata::csv;

namespace {

volatile uint64_t sink;

std::string samples(const std::vector<double>& values) {
  std::string out = "[";
  for (size_t i = 0; i < values.size(); i++) {
    char buffer[32];
    std::snprintf(buffer, sizeof buffer, "%s%.6f", i ? ", " : "", values[i]);
    out += buffer;
  }
  return out + "]";
}

/** Warm up, then five samples of at least 0.3 s each; every run must return `expected`. */
std::vector<double> measure(size_t bytes, uint64_t expected, const std::function<uint64_t()>& run) {
  using clock = std::chrono::steady_clock;
  auto warm = clock::now() + std::chrono::seconds(1);
  for (int i = 0; i < 3 || clock::now() < warm; i++) {
    if (run() != expected) {
      std::fprintf(stderr, "checksum mismatch\n");
      std::exit(1);
    }
  }
  std::vector<double> result;
  for (int s = 0; s < 5; s++) {
    auto start = clock::now();
    uint64_t ops = 0;
    double elapsed;
    do {
      uint64_t value = run();
      if (value != expected) std::exit(1);
      sink = value;
      ops++;
      elapsed = std::chrono::duration<double, std::nano>(clock::now() - start).count();
    } while (elapsed < 3e8);
    result.push_back(elapsed / (static_cast<double>(ops) * static_cast<double>(bytes)));
  }
  return result;
}

const char* kernel_name(Kernel k) {
  switch (k) {
    case SCALAR: return "scalar";
    case SWAR: return "swar";
    case NEON: return "neon";
    case AVX2: return "avx2";
    case AVX512: return "avx512";
    default: return "auto";
  }
}

constexpr uint64_t CAPACITY = 1 << 20;  // entries per call, as the Java harness's native engines

uint64_t index_only(Kernel kernel, const std::string& data, std::vector<uint32_t>& out) {
  uint64_t carry = 0, entries = 0;
  const uint8_t* p = reinterpret_cast<const uint8_t*>(data.data());
  for (uint64_t pos = 0; pos < data.size();) {
    uint64_t n = indexable(data.size() - pos, out.size());
    entries += index(kernel, p + pos, n, out.data(), Format{',', '"', true}, &carry);
    pos += n;
  }
  return entries << 8 | carry;
}

/**
 * Records and fields from the index: the IndexedScanner fast path in C++ (valid input only;
 * fixtures are valid, and any flagged-malformed field aborts the probe).
 */
uint64_t scan(Kernel kernel, const std::string& data, std::vector<uint32_t>& out,
              std::vector<uint64_t>& spans) {
  uint64_t carry = 0, records = 0, fields = 0;
  const uint8_t* p = reinterpret_cast<const uint8_t*>(data.data());
  uint64_t field_start = 0, count = 0, skip = UINT64_MAX;
  for (uint64_t pos = 0; pos < data.size();) {
    uint64_t n = indexable(data.size() - pos, out.size());
    uint64_t entries = index(kernel, p + pos, n, out.data(), Format{',', '"', true}, &carry);
    for (uint64_t k = 0; k < entries; k++) {
      uint32_t e = out[k];
      uint64_t at = pos + (e & OFFSET_MASK);
      if (at == skip) continue;
      uint8_t b = p[at];
      if (b != ',' && count == 0 && at == field_start) {
        uint64_t next = at + 1;
        if (b == '\r' && next < data.size() && p[next] == '\n') skip = next++;
        field_start = next;
        continue;
      }
      if (e & FLAG_UNQUOTED) std::abort();
      uint64_t span = (e & FLAG_QUOTED)
                          ? ((field_start + 1) << 32 | (at - field_start - 2) << 2 | 2 |
                             ((e & FLAG_ESCAPED) ? 1 : 0))
                          : (field_start << 32 | (at - field_start) << 2);
      if (count == spans.size()) spans.resize(2 * count);
      spans[count++] = span;
      if (b == ',') {
        field_start = at + 1;
        continue;
      }
      uint64_t end = at + 1;
      if (b == '\r' && end < data.size() && p[end] == '\n') skip = end++;
      records++;
      fields += count;
      sink = spans[0];
      count = 0;
      field_start = end;
    }
    pos += n;
  }
  if (field_start < data.size() || count > 0) {
    records++;
    fields += count + 1;
  }
  return records * 1000003ull + fields;
}

}  // namespace

int main(int argc, char** argv) {
  if (argc != 3) {
    std::fprintf(stderr, "usage: csv_probe <dataset> <fixture>\n");
    return 2;
  }
  std::ifstream file(argv[2], std::ios::binary);
  std::string data((std::istreambuf_iterator<char>(file)), std::istreambuf_iterator<char>());
  if (data.empty()) return 2;
  std::vector<uint32_t> out(CAPACITY);
  std::vector<uint64_t> spans(16);
  auto emit = [&](const char* workload, const char* engine, const char* kernel, uint64_t checksum,
                  const std::vector<double>& ns) {
    std::printf(
        "{\"workload\":\"%s\",\"engine\":\"%s\",\"kernel\":\"%s\",\"dataset\":\"%s\",\"bytes\":%zu,"
        "\"stage\":\"ingest\",\"runtime\":\"native\",\"checksum\":%llu,\"ns_per_byte\":%s}\n",
        workload, engine, kernel, argv[1], data.size(), static_cast<unsigned long long>(checksum),
        samples(ns).c_str());
    std::fflush(stdout);
  };
  for (Kernel k : {SCALAR, SWAR, NEON, AVX2, AVX512}) {
    if (!supported(k)) continue;
    uint64_t expected = index_only(k, data, out);
    std::string engine = std::string("native-index-") + kernel_name(k);
    emit("index", engine.c_str(), kernel_name(k), expected,
         measure(data.size(), expected, [&] { return index_only(k, data, out); }));
  }
  Kernel best = best_kernel();
  uint64_t expected = scan(best, data, out, spans);
  emit("scan", "native-indexed-scan", kernel_name(best), expected,
       measure(data.size(), expected, [&] { return scan(best, data, out, spans); }));
  emit("utf8", "simdutf-validate", "", 1,
       measure(data.size(), 1, [&] {
         return static_cast<uint64_t>(simdutf::validate_utf8(data.data(), data.size()));
       }));
  return 0;
}
