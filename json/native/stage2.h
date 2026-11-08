#ifndef TRUFFLEDATA_JSON_STAGE2_H
#define TRUFFLEDATA_JSON_STAGE2_H
#include "json/native/decoder_common.h"
#include "json/native/stage1.h"
#include <cstdint>
#include <cstring>
#include <string_view>

namespace truffledata::json {
namespace stage2_detail {
[[noreturn, gnu::noinline, gnu::cold]] inline void syntax(const char* what) { throw std::invalid_argument(what); }
inline bool whitespace(uint8_t c) { return c == ' ' || c == '\n' || c == '\r' || c == '\t'; }
inline bool terminator(uint8_t c) {
  return whitespace(c) || c == ',' || c == ']' || c == '}';
}
// Validation needs numeric range only when it changes DECIMAL text-limit accounting. A strict
// fixed-point token with at most 18 digits always fits I64 or a finite, non-underflowing F64.
// Longer tokens and every exponent keep the pinned parser's full classification.
[[gnu::always_inline]] inline bool bounded_number(const uint8_t* text, uint32_t length) {
  if (length == 0 || length > 20) return false;
  uint32_t at = text[0] == '-' ? 1 : 0, digits = 0;
  if (at == length) return false;
  if (text[at] == '0') { ++at; ++digits; }
  else {
    if (text[at] < '1' || text[at] > '9') return false;
    do { ++at; ++digits; } while (at < length && text[at] >= '0' && text[at] <= '9');
  }
  if (at < length && text[at] == '.') {
    ++at;
    if (at == length || text[at] < '0' || text[at] > '9') return false;
    do { ++at; ++digits; } while (at < length && text[at] >= '0' && text[at] <= '9');
  }
  return at == length && digits <= 18;
}
// Common signed integers need neither overflow arithmetic nor floating-point conversion.
[[gnu::always_inline]] inline bool integer18(const uint8_t* text, uint32_t length, uint64_t& bits) {
  bool negative = text[0] == '-';
  uint32_t at = negative ? 1 : 0, digits = length - at;
  if (digits == 0 || digits > 18 || (digits > 1 && text[at] == '0')) return false;
  uint64_t value = 0;
  for (; at < length; ++at) {
    uint32_t digit = uint32_t(text[at]) - '0';
    if (digit > 9) return false;
    value = value * 10 + digit;
  }
  bits = negative ? uint64_t(0) - value : value;
  return true;
}
struct scan_hit { uint32_t length; bool quote, ascii; };
// Input has SIMDJSON_PADDING readable bytes. The returned delimiter must still precede end;
// padding is only load safety, never accepted as part of a token.
[[gnu::always_inline]] inline scan_hit scan_string(const uint8_t* text, const uint8_t* end) {
  uint32_t offset = 0;
  bool ascii = true;
  for (;;) {
    if (text + offset >= end) syntax("unterminated JSON string");
#if defined(__x86_64__)
    __m128i v = _mm_loadu_si128(reinterpret_cast<const __m128i*>(text + offset));
    uint32_t matches = uint32_t(_mm_movemask_epi8(_mm_or_si128(
        _mm_cmpeq_epi8(v, _mm_set1_epi8('"')), _mm_cmpeq_epi8(v, _mm_set1_epi8('\\')))));
    uint32_t high = uint32_t(_mm_movemask_epi8(v));
    if (matches) {
      uint32_t at = uint32_t(__builtin_ctz(matches));
      if (text + offset + at >= end) syntax("unterminated JSON string");
      ascii &= (high & ((1u << at) - 1)) == 0;
      return {offset + at, text[offset + at] == '"', ascii};
    }
    ascii &= high == 0;
    offset += 16;
#elif defined(__aarch64__)
    uint8x16_t v = vld1q_u8(text + offset);
    uint8x16_t matches = vorrq_u8(vceqq_u8(v, vdupq_n_u8('"')), vceqq_u8(v, vdupq_n_u8('\\')));
    uint64x2_t lanes = vreinterpretq_u64_u8(matches);
    uint64_t low = vgetq_lane_u64(lanes, 0), high = vgetq_lane_u64(lanes, 1);
    if (low || high) {
      uint32_t at = low ? uint32_t(__builtin_ctzll(low) / 8) : 8 + uint32_t(__builtin_ctzll(high) / 8);
      if (text + offset + at >= end) syntax("unterminated JSON string");
      static constexpr uint8_t positions[] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
      uint8x16_t relevant = vcltq_u8(vld1q_u8(positions), vdupq_n_u8(uint8_t(at)));
      ascii &= vmaxvq_u8(vandq_u8(v, relevant)) < 128;
      return {offset + at, text[offset + at] == '"', ascii};
    }
    ascii &= vmaxvq_u8(v) < 128;
    offset += 16;
#else
    uint8_t c = text[offset];
    if (c == '"' || c == '\\') return {offset, c == '"', ascii};
    ascii &= c < 128;
    ++offset;
#endif
  }
}
inline int hex(uint8_t c) {
  if (c >= '0' && c <= '9') return c - '0';
  if (c >= 'a' && c <= 'f') return c - 'a' + 10;
  if (c >= 'A' && c <= 'F') return c - 'A' + 10;
  return -1;
}

}

// Grammar and token semantics over a stage-1 structural index. Writer sees the same events as
// the On-Demand oracle. One invocation publishes nothing until the whole root passes.
template<class Writer> class stage2_walk {
  const uint8_t* input_;
  const uint8_t* end_;
  const uint32_t* indexes_;
  uint32_t count_, at_ = 0, origin_;
  uint32_t offset(uint32_t at) const { return indexes_[at] - origin_; }
  Writer& writer_;
  struct frame { size_t index; uint64_t count; bool object; };
  frame frames_[128];
  uint32_t depth_ = 0;
  uint32_t root_end_ = 0;
  using span = tape::ctape::span;

  struct number_writer {
    Writer& writer;
    span token;
    bool negative, emitted = false, finite = true;
    void append_s64(int64_t value) {
      if (value == 0 && negative) writer.parsed_number(tape::ctape::kind_f64, uint64_t(1) << 63, token);
      else writer.parsed_number(tape::ctape::kind_i64, uint64_t(value), token);
      emitted = true;
    }
    void append_u64(uint64_t value) { writer.parsed_number(tape::ctape::kind_u64, value, token); emitted = true; }
    void append_double(double value) {
      finite = std::isfinite(value);
      if (finite) {
        uint64_t bits; std::memcpy(&bits, &value, sizeof value);
        writer.parsed_number(tape::ctape::kind_f64, bits, token); emitted = true;
      }
    }
  };
  template<bool Copy = true> uint32_t unescape(const uint8_t* text, const uint8_t*& closing, stage2_detail::scan_hit run, bool& ascii) {
    uint8_t* first = nullptr;
    if constexpr (Copy) first = writer_.pool_reserve(size_t(end_ - text));
    uint8_t* out = first;
    uint32_t length = 0;
    auto emit = [&](uint8_t byte) {
      if constexpr (Copy) *out++ = byte;
      else ++length;
    };
    ascii = true;
    for (;;) {
      ascii &= run.ascii;
      if constexpr (Copy) { std::memcpy(out, text, run.length); out += run.length; }
      else length += run.length;
      text += run.length;
      if (run.quote) { closing = text; break; }
      if (end_ - text < 2) stage2_detail::syntax("truncated JSON escape");
      uint8_t escape = text[1]; text += 2;
      switch (escape) {
        case '"': emit('"'); break;
        case '\\': emit('\\'); break;
        case '/': emit('/'); break;
        case 'b': emit('\b'); break;
        case 'f': emit('\f'); break;
        case 'n': emit('\n'); break;
        case 'r': emit('\r'); break;
        case 't': emit('\t'); break;
        case 'u': {
          auto read4 = [&](const uint8_t* p) {
            if (end_ - p < 4) stage2_detail::syntax("truncated JSON Unicode escape");
            int value = 0;
            for (int i = 0; i < 4; ++i) {
              int digit = stage2_detail::hex(p[i]);
              if (digit < 0) stage2_detail::syntax("invalid JSON Unicode escape");
              value = value * 16 + digit;
            }
            return value;
          };
          int codepoint = read4(text); text += 4;
          if (codepoint >= 0xd800 && codepoint <= 0xdbff) {
            if (end_ - text < 6 || text[0] != '\\' || text[1] != 'u')
              stage2_detail::syntax("unpaired JSON surrogate");
            int low = read4(text + 2);
            if (low < 0xdc00 || low > 0xdfff) stage2_detail::syntax("unpaired JSON surrogate");
            codepoint = 0x10000 + ((codepoint - 0xd800) << 10) + low - 0xdc00;
            text += 6;
          } else if (codepoint >= 0xdc00 && codepoint <= 0xdfff) stage2_detail::syntax("unpaired JSON surrogate");
          ascii &= codepoint < 0x80;
          if (codepoint < 0x80) emit(uint8_t(codepoint));
          else if (codepoint < 0x800) {
            emit(uint8_t(0xc0 | (codepoint >> 6))); emit(uint8_t(0x80 | (codepoint & 63)));
          } else if (codepoint < 0x10000) {
            emit(uint8_t(0xe0 | (codepoint >> 12))); emit(uint8_t(0x80 | ((codepoint >> 6) & 63)));
            emit(uint8_t(0x80 | (codepoint & 63)));
          } else {
            emit(uint8_t(0xf0 | (codepoint >> 18))); emit(uint8_t(0x80 | ((codepoint >> 12) & 63)));
            emit(uint8_t(0x80 | ((codepoint >> 6) & 63))); emit(uint8_t(0x80 | (codepoint & 63)));
          }
          break;
        }
        default: stage2_detail::syntax("invalid JSON escape");
      }
      run = stage2_detail::scan_string(text, end_);
    }
    if constexpr (Copy) {
      length = uint32_t(out - first);
      writer_.pool_commit(length);
    }
    return length;
  }
  [[gnu::always_inline]] void string(uint32_t start, bool key) {
    const uint8_t* text = input_ + start + 1;
    if (key) {
      uint32_t length = writer_.cached_key_ref(frames_[depth_ - 1].index, start + 1, uint32_t(end_ - text));
      if (length != UINT32_MAX) { root_end_ = start + length + 2; return; }
    }
    auto scan = stage2_detail::scan_string(text, end_);
    if (scan.quote) {
      if (key) writer_.key_text(frames_[depth_ - 1].index, text, scan.length, true);
      writer_.string_ref({start + 1, scan.length}, scan.ascii, key);
      root_end_ = start + scan.length + 2;
      return;
    }
    if constexpr (!Writer::retains_values) {
      if (!key) {
        const uint8_t* closing;
        bool ascii;
        uint32_t length = unescape<false>(text, closing, scan, ascii);
        root_end_ = uint32_t(closing + 1 - input_);
        writer_.string_owned(0, length, {start, root_end_ - start}, ascii, false);
        return;
      }
    }
    uint32_t offset = uint32_t(writer_.pool_size());
    const uint8_t* closing;
    bool ascii;
    uint32_t length = unescape(text, closing, scan, ascii);
    const uint8_t* decoded = writer_.pool_data() + offset;
    if (key) writer_.key_text(frames_[depth_ - 1].index, decoded, length);
    root_end_ = uint32_t(closing + 1 - input_);
    writer_.string_owned(offset, length, {start, root_end_ - start}, ascii, key);
  }
  [[gnu::always_inline]] void number(uint32_t start) {
    uint32_t end = at_ == count_ ? uint32_t(end_ - input_) : offset(at_);
    while (end > start && stage2_detail::whitespace(input_[end - 1])) --end;
    if constexpr (!Writer::retains_values) {
      if (stage2_detail::bounded_number(input_ + start, end - start)) {
        writer_.commit_number({start, end - start}); root_end_ = end; return;
      }
    }
    if constexpr (Writer::retains_values) {
      uint64_t bits;
      if (stage2_detail::integer18(input_ + start, end - start, bits)) {
        uint32_t kind = bits == 0 && input_[start] == '-' ? tape::ctape::kind_f64 : tape::ctape::kind_i64;
        if (kind == tape::ctape::kind_f64) bits = uint64_t(1) << 63;
        writer_.parsed_number(kind, bits, {start, end - start});
        writer_.commit_number({start, end - start}); root_end_ = end; return;
      }
    }
    number_writer out{writer_, {start, end - start}, input_[start] == '-'};
    const uint8_t* source = input_ + start;
    // The pinned number parser requires a structural/whitespace terminator, including at EOF.
    // Its callback can precede error detection; the provisional numeric words roll back on error.
    if (input_ + end == end_) {
      size_t length = end - start;
      auto copy = writer_.pool_reserve(length + simdjson::SIMDJSON_PADDING);
      std::memcpy(copy, source, length);
      std::memset(copy + length, ' ', simdjson::SIMDJSON_PADDING);
      source = copy;
    }
    if (simdjson::builtin::numberparsing::parse_number(source, out) != simdjson::SUCCESS || !out.finite) {
      if (out.emitted) writer_.rollback_number();
      if (!json::number({reinterpret_cast<const char*>(input_ + start), end - start}))
        stage2_detail::syntax("invalid JSON number");
      writer_.decimal_ref({start, end - start});
    } else writer_.commit_number({start, end - start});
    root_end_ = end;
  }
  [[gnu::always_inline]] void literal(uint32_t start, const char* spelling, uint32_t length) {
    if (size_t(end_ - input_ - start) < length || std::memcmp(input_ + start, spelling, length) != 0
        || (input_ + start + length < end_ && !stage2_detail::terminator(input_[start + length]))) stage2_detail::syntax("invalid JSON literal");
    root_end_ = start + length;
  }
 public:
  stage2_walk(const uint8_t* input, size_t length, const uint32_t* indexes, uint32_t count, Writer& writer,
              uint32_t origin = 0)
      : input_(input), end_(input + length), indexes_(indexes), count_(count), origin_(origin), writer_(writer) {}
  uint32_t position() const { return at_; }
  uint32_t root_end() const { return root_end_; }
  void document() {
    if (at_ >= count_) stage2_detail::syntax("empty JSON input");
    goto value;
  value: {
      if (at_ >= count_) stage2_detail::syntax("missing JSON value");
      uint32_t start = offset(at_++);
      uint8_t token = input_[start];
      switch (token) {
        case '{': case '[': {
          if (depth_ == 128) throw std::length_error("JSON depth limit");
          bool object = token == '{';
          size_t index = writer_.begin(object ? tape::ctape::kind_object : tape::ctape::kind_array, depth_, start);
          frames_[depth_++] = {index, 0, object};
          if (at_ < count_ && input_[offset(at_)] == (object ? '}' : ']')) {
            root_end_ = offset(at_++) + 1; writer_.end(index, 0); --depth_; goto after;
          }
          if (object) goto key;
          goto value;
        }
        case '"': string(start, false); goto after;
        case 't': literal(start, "true", 4); writer_.boolean(true); goto after;
        case 'f': literal(start, "false", 5); writer_.boolean(false); goto after;
        case 'n': literal(start, "null", 4); writer_.null(); goto after;
        default:
          if (token == '-' || (token >= '0' && token <= '9')) { number(start); goto after; }
          stage2_detail::syntax("unexpected JSON token");
      }
    }
  after: {
      for (;;) {
        if (depth_ == 0) return;
        frame& current = frames_[depth_ - 1]; ++current.count;
        if (at_ >= count_) stage2_detail::syntax("unterminated JSON container");
        uint32_t start = offset(at_++); uint8_t token = input_[start];
        if (token == ',') { if (current.object) goto key; goto value; }
        if (token == (current.object ? '}' : ']')) {
          writer_.end(current.index, current.count); root_end_ = start + 1; --depth_; continue;
        }
        stage2_detail::syntax("expected comma or closing bracket");
      }
    }
  key: {
      if (at_ >= count_ || input_[offset(at_)] != '"') stage2_detail::syntax("expected JSON key");
      string(offset(at_++), true);
      if (at_ >= count_ || input_[offset(at_++)] != ':') stage2_detail::syntax("expected JSON colon");
      goto value;
    }
  }
  void run() {
    document();
    if (at_ != count_) stage2_detail::syntax("trailing JSON input");
  }
};
}
#endif
