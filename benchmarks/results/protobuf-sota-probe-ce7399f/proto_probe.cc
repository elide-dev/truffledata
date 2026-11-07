// Temporary competitor probe for the protobuf performance plan (not production code).
// Decodes the consumer harness fixture (byte-identical generator) with protobuf C++ (generated
// classes, reused message and arena) and upb (table-driven, arena with a preallocated block), next
// to a structural scan floor shared with the Rust probe for cross-probe calibration.
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include "google/protobuf/arena.h"
#include "google/protobuf/type.pb.h"
#include "google/protobuf/type.upb.h"
#include "upb/mem/arena.h"

namespace {

void put_varint(std::string& out, uint64_t v) {
  while (v >= 0x80) {
    out.push_back(static_cast<char>((v & 0x7f) | 0x80));
    v >>= 7;
  }
  out.push_back(static_cast<char>(v));
}
void put_tag(std::string& out, uint32_t number, uint32_t wt) { put_varint(out, (number << 3) | wt); }
void put_bytes(std::string& out, uint32_t number, const std::string& data) {
  put_tag(out, number, 2);
  put_varint(out, data.size());
  out += data;
}
void put_int(std::string& out, uint32_t number, uint64_t v) {
  put_tag(out, number, 0);
  put_varint(out, v);
}

std::string type_message(int i, const std::string& mode) {
  int count = mode == "holdout" ? 12 + i % 13 : mode == "large" ? 2048 + (i % 8) * 64 : 3 + i % 6;
  std::string out;
  put_bytes(out, 1, "catalog-" + std::to_string(i) + "-\xce\xbb");
  for (int j = 0; j < count; j++) {
    std::string f;
    put_int(f, 1, 3);
    put_int(f, 2, 3);
    put_int(f, 3, j + 1);
    std::string name = "column-" + std::to_string(j) + (mode == "holdout" ? "-\xce\xbb\xf0\x9f\x98\x80" : "");
    put_bytes(f, 4, name);
    if ((i + j) % 3 != 0) put_int(f, 8, 1);
    put_bytes(out, 2, f);
  }
  put_bytes(out, 3, "identity");
  if ((i & 1) == 0) put_bytes(out, 3, "contact-\xf0\x9f\x98\x80");
  if (i % 3 != 0) {
    std::string sc;
    put_bytes(sc, 1, "schema-" + std::to_string(i) + ".proto");
    put_bytes(out, 5, sc);
  }
  put_int(out, 6, 1);
  return out;
}

inline bool read_varint(const uint8_t* p, size_t n, size_t& pos, uint64_t& v) {
  if (pos >= n) return false;
  uint8_t b = p[pos];
  if (b < 0x80) {
    pos++;
    v = b;
    return true;
  }
  v = b & 0x7f;
  int shift = 7;
  for (;;) {
    pos++;
    if (pos >= n) return false;
    b = p[pos];
    v |= static_cast<uint64_t>(b & 0x7f) << shift;
    if (b < 0x80) {
      pos++;
      return true;
    }
    shift += 7;
    if (shift > 63) return false;
  }
}

uint64_t scan_flat(const uint8_t* p, size_t n) {
  size_t pos = 0;
  uint64_t fields = 0, v;
  while (pos < n) {
    if (!read_varint(p, n, pos, v)) return ~0ull;
    fields++;
    switch (v & 7) {
      case 0:
        if (!read_varint(p, n, pos, v)) return ~0ull;
        break;
      case 1: pos += 8; break;
      case 2:
        if (!read_varint(p, n, pos, v)) return ~0ull;
        pos += v;
        break;
      case 5: pos += 4; break;
      default: return ~0ull;
    }
    if (pos > n) return ~0ull;
  }
  return fields;
}

static volatile uint64_t sink;

template <typename F>
void bench(const char* name, size_t bytes, int reps, F f) {
  double best = 1e300;
  for (int round = 0; round < 7; round++) {
    auto start = std::chrono::steady_clock::now();
    uint64_t s = 0;
    for (int r = 0; r < reps; r++) s += f();
    sink = s;
    double ns = std::chrono::duration<double, std::nano>(std::chrono::steady_clock::now() - start).count();
    if (ns < best) best = ns;
  }
  double per = best / reps;
  std::printf("PROBE %-28s %12.1f ns/rep %9.1f ns/msg %9.1f MB/s\n", name, per, per / 32.0, bytes / per * 1e3);
}

void run_mode(const std::string& mode, int reps) {
  std::vector<std::string> msgs;
  size_t bytes = 0;
  for (int i = 0; i < 32; i++) {
    msgs.push_back(type_message(i, mode));
    bytes += msgs.back().size();
  }
  std::printf("PROBE mode=%s messages=32 bytes=%zu avg=%zu\n", mode.c_str(), bytes, bytes / 32);
  std::string label;

  label = mode + "/scan_flat";
  bench(label.c_str(), bytes, reps, [&] {
    uint64_t s = 0;
    for (auto& m : msgs) s += scan_flat(reinterpret_cast<const uint8_t*>(m.data()), m.size());
    return s;
  });

  // protobuf C++: one reused message (ParseFromArray clears first).
  google::protobuf::Type reused;
  label = mode + "/cpp_reuse";
  bench(label.c_str(), bytes, reps, [&] {
    uint64_t s = 0;
    for (auto& m : msgs) {
      if (!reused.ParseFromArray(m.data(), static_cast<int>(m.size()))) std::abort();
      s += reused.fields_size() + reused.name().size();
    }
    return s;
  });
  label = mode + "/cpp_reuse_consume";
  bench(label.c_str(), bytes, reps, [&] {
    uint64_t s = 0;
    for (auto& m : msgs) {
      if (!reused.ParseFromArray(m.data(), static_cast<int>(m.size()))) std::abort();
      s += reused.name().size() + reused.oneofs_size() * 3 + reused.syntax();
      for (auto& f : reused.fields()) {
        s += f.name().size();
        if (f.packed()) s += f.number() * 31 + f.kind() + f.cardinality();
      }
    }
    return s;
  });

  // protobuf C++: fresh message per parse on an arena reset per batch of 32.
  google::protobuf::Arena arena;
  label = mode + "/cpp_arena";
  bench(label.c_str(), bytes, reps, [&] {
    uint64_t s = 0;
    for (auto& m : msgs) {
      auto* t = google::protobuf::Arena::Create<google::protobuf::Type>(&arena);
      if (!t->ParseFromArray(m.data(), static_cast<int>(m.size()))) std::abort();
      s += t->fields_size() + t->name().size();
    }
    arena.Reset();
    return s;
  });

  // upb: arena over a preallocated block, parse, free (no malloc in the steady state).
  size_t block = mode == "large" ? (4u << 20) : (64u << 10);
  std::vector<char> mem(block);
  label = mode + "/upb";
  bench(label.c_str(), bytes, reps, [&] {
    uint64_t s = 0;
    for (auto& m : msgs) {
      upb_Arena* a = upb_Arena_Init(mem.data(), mem.size(), &upb_alloc_global);
      const google_protobuf_Type* t = google_protobuf_Type_parse(m.data(), m.size(), a);
      if (!t) std::abort();
      size_t n;
      google_protobuf_Type_fields(t, &n);
      s += n + google_protobuf_Type_name(t).size;
      upb_Arena_Free(a);
    }
    return s;
  });
  label = mode + "/upb_consume";
  bench(label.c_str(), bytes, reps, [&] {
    uint64_t s = 0;
    for (auto& m : msgs) {
      upb_Arena* a = upb_Arena_Init(mem.data(), mem.size(), &upb_alloc_global);
      const google_protobuf_Type* t = google_protobuf_Type_parse(m.data(), m.size(), a);
      if (!t) std::abort();
      size_t n, o;
      google_protobuf_Type_oneofs(t, &o);
      s += google_protobuf_Type_name(t).size + o * 3 + google_protobuf_Type_syntax(t);
      const google_protobuf_Field* const* fields = google_protobuf_Type_fields(t, &n);
      for (size_t k = 0; k < n; k++) {
        const google_protobuf_Field* f = fields[k];
        s += google_protobuf_Field_name(f).size;
        if (google_protobuf_Field_packed(f))
          s += google_protobuf_Field_number(f) * 31 + google_protobuf_Field_kind(f) + google_protobuf_Field_cardinality(f);
      }
      upb_Arena_Free(a);
    }
    return s;
  });
}

}  // namespace

int main() {
  GOOGLE_PROTOBUF_VERIFY_VERSION;
  FILE* f = std::fopen("/proc/cpuinfo", "r");
  if (f) {
    char line[512];
    while (std::fgets(line, sizeof line, f))
      if (std::strncmp(line, "model name", 10) == 0) {
        std::printf("PROBE host=%s", line);
        break;
      }
    std::fclose(f);
  }
  run_mode("small", 2000);
  run_mode("holdout", 1000);
  run_mode("large", 8);
  return 0;
}
