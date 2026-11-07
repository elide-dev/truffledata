#ifndef TRUFFLEDATA_YAML_SCALARS_H
#define TRUFFLEDATA_YAML_SCALARS_H
// YAML scalar resolution (docs/superpowers/plans/2026-11-06-yaml.md, D2 and D3) as a pure function:
//
//   resolve(text, style, schema, tag, scratch) -> {kind, payload | text}
//
// Schemas:
//   CORE (YAML 1.2.2 §10.3, the default): null `null|Null|NULL|~|` (empty), bool true/false in three
//     casings, int `[-+]?[0-9]+`, `0o[0-7]+`, `0x[0-9a-fA-F]+`, float
//     `[-+]?(\.[0-9]+|[0-9]+(\.[0-9]*)?)([eE][-+]?[0-9]+)?`, `[-+]?\.(inf|Inf|INF)`, `\.(nan|NaN|NAN)`;
//     every other plain scalar is a string.
//   JSON (§10.2): only `null`, `true`, `false`, `-?(0|[1-9][0-9]*)` and
//     `-?(0|[1-9][0-9]*)(\.[0-9]*)?([eE][-+]?[0-9]+)?` resolve; any other plain scalar is an error.
//   FAILSAFE (§10.1): every scalar is a string.
//   YAML_1_1 (the YAML 1.1 type repository, https://yaml.org/type/): null `~|null|Null|NULL|`;
//     bool y/n/yes/no/on/off/true/false in their casings; int `[-+]?0b[01_]+`, `[-+]?0[0-7_]+`
//     (octal), `[-+]?(0|[1-9][0-9_]*)`, `[-+]?0x[0-9a-fA-F_]+`, `[-+]?[1-9][0-9_]*(:[0-5]?[0-9])+`
//     (sexagesimal); float `[-+]?([0-9][0-9_]*)?\.[0-9_]*([eE][-+][0-9]+)?` (at least one digit),
//     `[-+]?[0-9][0-9_]*(:[0-5]?[0-9])+\.[0-9_]*`, the core infinities and NaN; timestamps stay
//     strings with an implicit !!timestamp tag. `_` separators are dropped before conversion.
// Only plain scalars resolve implicitly; quoted and block scalars are strings unless tagged.
//
// Numbers map onto Myna as JSON does: int64 -> I64, above int64 within uint64 -> U64, larger -> DECIMAL
// (canonical decimal digits; hex, octal and binary spellings are converted), `-0` -> F64 -0.0,
// finite floats -> F64 (nearest binary64 by std::from_chars), floats that overflow binary64 ->
// DECIMAL (text normalized to JSON grammar), floats that underflow -> F64 ±0.0 (as simdjson does
// for JSON). `.inf`/`-.inf`/`.nan` are non-finite F64 (tape_writer::nonfinite).
//
// Explicit core tags (`!!str`, `!!int`, `!!float`, `!!bool`, `!!null`) apply to every scalar style
// and are enforced: the text must match the tag's forms (the core forms under CORE, JSON and
// FAILSAFE; the 1.1 forms under YAML_1_1), so `!!int "12"` is 12, `!!int abc` is an error and
// `!!str 12` is the string "12". The non-specific `!` forces a string.
#include "yaml/native/errors.h"

#include <myna/values.h>

#include <charconv>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <limits>
#include <memory_resource>
#include <string_view>

namespace truffledata::yaml {

enum class schema : uint32_t { core = 0, json = 1, failsafe = 2, yaml11 = 3 };

// Scalar styles in yaml_meta bits 0-2 (5 and 6 are container styles).
enum class scalar_style : uint8_t { plain = 0, single_quoted = 1, double_quoted = 2, literal = 3, folded = 4 };

// Tags the decoder knows; `other` is any other tag (local `!foo`, verbatim, %TAG handles).
enum class tag_kind : uint8_t {
  none, nonspecific, str, int_, float_, bool_, null_, seq, map,
  binary, timestamp, set, omap, pairs, merge, value, other
};

// Original kind of a scalar key (yaml_meta bits 5-7). Keys are stored as STRING nodes.
enum class key_kind : uint8_t { string = 0, null = 1, boolean = 2, integer = 3, floating = 4 };

struct scalar {
  uint32_t kind = MYNA_VALUE_STRING;  // NULL, BOOL, I64, U64, F64, DECIMAL or STRING
  key_kind key = key_kind::string;
  bool nonfinite = false;
  bool token = false;     // DECIMAL: `text` is the source token itself (already JSON grammar)
  uint64_t bits = 0;      // BOOL/I64/U64/F64 payload
  std::string_view text;  // STRING: the scalar; DECIMAL: JSON-grammar digits
  tag_kind implicit = tag_kind::none;  // YAML_1_1: an implicit !!timestamp on a string
};

namespace detail {
inline bool digit(char c) { return c >= '0' && c <= '9'; }
inline bool all(std::string_view s, bool (*pred)(char)) {
  if (s.empty()) return false;
  for (char c : s) if (!pred(c)) return false;
  return true;
}
inline bool octal(char c) { return c >= '0' && c <= '7'; }
inline bool hex(char c) { return digit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'); }
inline int hex_value(char c) { return c <= '9' ? c - '0' : (c | 0x20) - 'a' + 10; }

inline bool is_null(std::string_view s) {
  switch (s.size()) {
    case 0: return true;
    case 1: return s[0] == '~';
    case 4: return s == "null" || s == "Null" || s == "NULL";
    default: return false;
  }
}
inline int is_bool(std::string_view s) {
  if (s.size() == 4) return s == "true" || s == "True" || s == "TRUE" ? 1 : -1;
  if (s.size() == 5) return s == "false" || s == "False" || s == "FALSE" ? 0 : -1;
  return -1;
}
inline int is_special_float(std::string_view s) {  // 1 +inf, -1 -inf, 2 nan, 0 none
  std::string_view body = s;
  int sign = 1;
  if (!body.empty() && (body[0] == '+' || body[0] == '-')) { sign = body[0] == '-' ? -1 : 1; body.remove_prefix(1); }
  if (body == ".inf" || body == ".Inf" || body == ".INF") return sign;
  if (s == ".nan" || s == ".NaN" || s == ".NAN") return 2;
  return 0;
}
// Core float grammar: [-+]?(\.[0-9]+|[0-9]+(\.[0-9]*)?)([eE][-+]?[0-9]+)?
inline bool core_float(std::string_view s) {
  size_t i = 0, n = s.size();
  if (i < n && (s[i] == '+' || s[i] == '-')) ++i;
  size_t int_digits = 0, frac_digits = 0;
  while (i < n && digit(s[i])) ++i, ++int_digits;
  if (i < n && s[i] == '.') {
    ++i;
    while (i < n && digit(s[i])) ++i, ++frac_digits;
  }
  if (int_digits == 0 && frac_digits == 0) return false;
  if (i < n && (s[i] == 'e' || s[i] == 'E')) {
    ++i;
    if (i < n && (s[i] == '+' || s[i] == '-')) ++i;
    size_t exp = 0;
    while (i < n && digit(s[i])) ++i, ++exp;
    if (!exp) return false;
  }
  return i == n;
}
// JSON schema int: -?(0|[1-9][0-9]*)
inline bool json_int(std::string_view s) {
  size_t i = s.size() && s[0] == '-' ? 1 : 0;
  if (i >= s.size()) return false;
  if (s[i] == '0') return i + 1 == s.size();
  return all(s.substr(i), digit);
}
// JSON schema float: -?(0|[1-9][0-9]*)(\.[0-9]*)?([eE][-+]?[0-9]+)?
inline bool json_float(std::string_view s) {
  size_t i = 0, n = s.size();
  if (i < n && s[i] == '-') ++i;
  if (i >= n || !digit(s[i])) return false;
  if (s[i] == '0') ++i;
  else while (i < n && digit(s[i])) ++i;
  if (i < n && s[i] == '.') { ++i; while (i < n && digit(s[i])) ++i; }
  if (i < n && (s[i] == 'e' || s[i] == 'E')) {
    ++i;
    if (i < n && (s[i] == '+' || s[i] == '-')) ++i;
    size_t exp = 0;
    while (i < n && digit(s[i])) ++i, ++exp;
    if (!exp) return false;
  }
  return i == n;
}
// RFC 8259 number grammar (what the JSON backend accepts as a DECIMAL token).
inline bool rfc8259(std::string_view s) {
  size_t i = 0, n = s.size();
  if (i < n && s[i] == '-') ++i;
  if (i >= n || !digit(s[i])) return false;
  if (s[i] == '0') ++i;
  else while (i < n && digit(s[i])) ++i;
  if (i < n && s[i] == '.') {
    ++i;
    if (i >= n || !digit(s[i])) return false;
    while (i < n && digit(s[i])) ++i;
  }
  if (i < n && (s[i] == 'e' || s[i] == 'E')) {
    ++i;
    if (i < n && (s[i] == '+' || s[i] == '-')) ++i;
    if (i >= n || !digit(s[i])) return false;
    while (i < n && digit(s[i])) ++i;
  }
  return i == n;
}

inline char* allocate(std::pmr::memory_resource* scratch, size_t n) {
  return static_cast<char*>(scratch->allocate(n ? n : 1, 1));
}

// Largest radix-converted integer, in source digits: conversion is quadratic in the digit count.
inline constexpr size_t max_radix_digits = 1000;

// Canonical decimal text of a non-negative integer given as digits in `base` (2, 8 or 16), with
// `negative` prefixing '-'. Schoolbook conversion into base-1e9 limbs, bounded by max_radix_digits.
inline std::string_view radix_to_decimal(std::string_view digits, int base, bool negative,
                                         std::pmr::memory_resource* scratch) {
  if (digits.size() > max_radix_digits) throw limit_error("integer digits");
  // At most ceil(digits * log10(16) / 9) + 1 limbs.
  size_t cap = digits.size() * 2 / 15 + 2;
  auto* limbs = reinterpret_cast<uint32_t*>(scratch->allocate(cap * sizeof(uint32_t), alignof(uint32_t)));
  size_t used = 0;
  for (char c : digits) {
    uint64_t carry = uint64_t(base == 16 ? hex_value(c) : c - '0');
    for (size_t i = 0; i < used; ++i) {
      uint64_t v = uint64_t(limbs[i]) * uint64_t(base) + carry;
      limbs[i] = uint32_t(v % 1000000000u);
      carry = v / 1000000000u;
    }
    if (carry) limbs[used++] = uint32_t(carry);
  }
  size_t n = (negative ? 1 : 0) + (used ? used * 9 : 1);
  char* out = allocate(scratch, n);
  size_t at = 0;
  if (negative) out[at++] = '-';
  if (!used) { out[at++] = '0'; return {out, at}; }
  char head[16];
  auto r = std::to_chars(head, head + sizeof head, limbs[used - 1]);
  for (char* p = head; p < r.ptr; ++p) out[at++] = *p;
  for (size_t i = used - 1; i-- > 0;) {
    char limb[9];
    uint32_t v = limbs[i];
    for (int k = 8; k >= 0; --k) { limb[k] = char('0' + v % 10); v /= 10; }
    std::memcpy(out + at, limb, 9);
    at += 9;
  }
  return {out, at};
}

// An integer from its magnitude `digits` (decimal, no sign, no separators) and sign: I64, U64,
// F64 -0.0 for a negative zero, else DECIMAL with canonical text.
inline scalar decimal_integer(std::string_view token, std::string_view digits, bool negative,
                              std::pmr::memory_resource* scratch) {
  scalar r;
  r.key = key_kind::integer;
  size_t z = 0;
  while (z + 1 < digits.size() && digits[z] == '0') ++z;
  std::string_view mag = digits.substr(z);
  if (mag == "0") {
    if (negative) {
      double nz = -0.0;
      r.kind = MYNA_VALUE_F64;
      std::memcpy(&r.bits, &nz, sizeof nz);
    } else {
      r.kind = MYNA_VALUE_I64;
    }
    return r;
  }
  uint64_t v = 0;
  bool fits = mag.size() <= 20;
  if (fits) {
    auto parsed = std::from_chars(mag.data(), mag.data() + mag.size(), v);
    fits = parsed.ec == std::errc() && parsed.ptr == mag.data() + mag.size();
  }
  if (fits && !negative && v <= uint64_t(INT64_MAX)) { r.kind = MYNA_VALUE_I64; r.bits = v; return r; }
  if (fits && !negative) { r.kind = MYNA_VALUE_U64; r.bits = v; return r; }
  if (fits && negative && v <= uint64_t(INT64_MAX) + 1) {
    r.kind = MYNA_VALUE_I64;
    r.bits = uint64_t(0) - v;
    return r;
  }
  r.kind = MYNA_VALUE_DECIMAL;
  if (token.size() == mag.size() + (negative ? 1 : 0) && token.substr(negative ? 1 : 0) == mag &&
      (!negative || token[0] == '-')) {
    r.text = token;
    r.token = true;
    return r;
  }
  char* out = allocate(scratch, mag.size() + 1);
  size_t at = 0;
  if (negative) out[at++] = '-';
  std::memcpy(out + at, mag.data(), mag.size());
  r.text = {out, at + mag.size()};
  return r;
}

// A radix integer (2, 8, 16): `digits` without prefix or separators.
inline scalar radix_integer(std::string_view digits, int base, bool negative, std::pmr::memory_resource* scratch) {
  size_t z = 0;
  while (z + 1 < digits.size() && digits[z] == '0') ++z;
  std::string_view mag = digits.substr(z);
  uint64_t v = 0;
  bool fits = true;
  int bits_per = base == 16 ? 4 : base == 8 ? 3 : 1;
  if (mag.size() * size_t(bits_per) > 64 + size_t(bits_per)) fits = false;
  else {
    for (char c : mag) {
      uint64_t d = uint64_t(base == 16 ? hex_value(c) : c - '0');
      if (v > (UINT64_MAX - d) / uint64_t(base)) { fits = false; break; }
      v = v * uint64_t(base) + d;
    }
  }
  scalar r;
  r.key = key_kind::integer;
  if (fits) {
    if (v == 0) {
      if (negative) {
        double nz = -0.0;
        r.kind = MYNA_VALUE_F64;
        std::memcpy(&r.bits, &nz, sizeof nz);
      } else {
        r.kind = MYNA_VALUE_I64;
      }
      return r;
    }
    if (!negative && v <= uint64_t(INT64_MAX)) { r.kind = MYNA_VALUE_I64; r.bits = v; return r; }
    if (!negative) { r.kind = MYNA_VALUE_U64; r.bits = v; return r; }
    if (v <= uint64_t(INT64_MAX) + 1) { r.kind = MYNA_VALUE_I64; r.bits = uint64_t(0) - v; return r; }
  }
  r.kind = MYNA_VALUE_DECIMAL;
  r.text = radix_to_decimal(mag, base, negative, scratch);
  return r;
}

// Decimal exponent of the leading significant digit of a float token in core grammar (saturating),
// or INT64_MIN for zero. Used only when from_chars reports out of range: positive means overflow.
inline int64_t magnitude(std::string_view s) {
  size_t i = 0, n = s.size();
  if (i < n && (s[i] == '+' || s[i] == '-')) ++i;
  int64_t position = 0;  // digits before the point, from the first significant digit
  bool found = false;
  int64_t lead = 0;      // exponent of the first significant digit (relative, before e)
  int64_t int_digits = 0;
  while (i < n && digit(s[i])) {
    if (!found && s[i] != '0') { found = true; position = 0; }
    if (found) ++position;
    ++int_digits, ++i;
  }
  if (found) lead = position - 1;
  if (i < n && s[i] == '.') {
    ++i;
    int64_t k = 0;
    while (i < n && digit(s[i])) {
      ++k;
      if (!found && s[i] != '0') { found = true; lead = -k; }
      ++i;
    }
  }
  if (!found) return INT64_MIN;
  int64_t exp = 0;
  if (i < n && (s[i] == 'e' || s[i] == 'E')) {
    ++i;
    bool neg = false;
    if (i < n && (s[i] == '+' || s[i] == '-')) neg = s[i++] == '-';
    for (; i < n; ++i) {
      if (exp < (int64_t(1) << 40)) exp = exp * 10 + (s[i] - '0');
    }
    if (neg) exp = -exp;
  }
  (void)int_digits;
  return lead + exp;
}

// JSON-grammar text of a core-grammar float token (sign '+' dropped, missing leading or trailing
// digits added, leading zeros of the integer part removed); `token` when it already is JSON.
inline std::string_view json_float_text(std::string_view token, std::pmr::memory_resource* scratch) {
  if (rfc8259(token)) return token;
  char* out = allocate(scratch, token.size() + 3);
  size_t at = 0, i = 0, n = token.size();
  if (token[i] == '+') ++i;
  else if (token[i] == '-') out[at++] = token[i++];
  size_t start = i;
  while (i < n && digit(token[i])) ++i;
  std::string_view whole = token.substr(start, i - start);
  while (whole.size() > 1 && whole[0] == '0') whole.remove_prefix(1);
  if (whole.empty()) out[at++] = '0';
  else { std::memcpy(out + at, whole.data(), whole.size()); at += whole.size(); }
  if (i < n && token[i] == '.') {
    ++i;
    size_t f = i;
    while (i < n && digit(token[i])) ++i;
    out[at++] = '.';
    if (i == f) out[at++] = '0';
    else { std::memcpy(out + at, token.data() + f, i - f); at += i - f; }
  }
  if (i < n) { std::memcpy(out + at, token.data() + i, n - i); at += n - i; }
  return {out, at};
}

// A float token in core grammar (or a JSON/1.1 one normalized to it): F64, or DECIMAL on overflow.
inline scalar float_token(std::string_view token, std::pmr::memory_resource* scratch) {
  scalar r;
  r.key = key_kind::floating;
  std::string_view body = token[0] == '+' ? token.substr(1) : token;
  double d = 0;
  auto parsed = std::from_chars(body.data(), body.data() + body.size(), d);
  if (parsed.ec == std::errc() && parsed.ptr == body.data() + body.size() && std::isfinite(d)) {
    r.kind = MYNA_VALUE_F64;
    std::memcpy(&r.bits, &d, sizeof d);
    return r;
  }
  if (parsed.ec != std::errc::result_out_of_range && parsed.ec != std::errc())
    throw error(status_internal, "YAML float conversion");
  if (magnitude(token) <= 0) {  // underflow: nearest is a signed zero
    d = token[0] == '-' ? -0.0 : 0.0;
    r.kind = MYNA_VALUE_F64;
    std::memcpy(&r.bits, &d, sizeof d);
    return r;
  }
  r.kind = MYNA_VALUE_DECIMAL;
  r.text = json_float_text(token, scratch);
  r.token = r.text.data() == token.data();
  return r;
}

inline scalar special_float(int which) {
  scalar r;
  r.kind = MYNA_VALUE_F64;
  r.key = key_kind::floating;
  r.nonfinite = true;
  double d = which == 2 ? std::numeric_limits<double>::quiet_NaN()
                        : which < 0 ? -std::numeric_limits<double>::infinity() : std::numeric_limits<double>::infinity();
  std::memcpy(&r.bits, &d, sizeof d);
  return r;
}

// YAML 1.1 timestamp grammar:
//   [0-9]{4}-[0-9]{2}-[0-9]{2}
//   [0-9]{4}-[0-9]{1,2}-[0-9]{1,2}([Tt]|[ \t]+)[0-9]{1,2}:[0-9]{2}:[0-9]{2}(\.[0-9]*)?
//     (([ \t]*)Z|[-+][0-9]{1,2}(:[0-9]{2})?)?   (the zone may follow [ \t]*)
inline bool valid_timestamp(std::string_view s) {
  size_t i = 0, n = s.size();
  auto digits = [&](size_t lo, size_t hi) {
    size_t k = 0;
    while (i < n && k < hi && digit(s[i])) ++i, ++k;
    return k >= lo;
  };
  auto ch = [&](char c) {
    if (i < n && s[i] == c) { ++i; return true; }
    return false;
  };
  if (!digits(4, 4) || !ch('-')) return false;
  size_t month = i;
  if (!digits(1, 2)) return false;
  bool short_month = i - month == 1;
  if (!ch('-')) return false;
  size_t day = i;
  if (!digits(1, 2)) return false;
  bool short_day = i - day == 1;
  if (i == n) return !short_month && !short_day;  // the date-only form needs two-digit fields
  if (s[i] == 'T' || s[i] == 't') ++i;
  else if (s[i] == ' ' || s[i] == '\t') { while (i < n && (s[i] == ' ' || s[i] == '\t')) ++i; }
  else return false;
  if (!digits(1, 2) || !ch(':') || !digits(2, 2) || !ch(':') || !digits(2, 2)) return false;
  if (ch('.')) while (i < n && digit(s[i])) ++i;
  while (i < n && (s[i] == ' ' || s[i] == '\t')) ++i;
  if (i == n) return true;
  if (ch('Z')) return i == n;
  if (s[i] != '+' && s[i] != '-') return false;
  ++i;
  if (!digits(1, 2)) return false;
  if (ch(':') && !digits(2, 2)) return false;
  return i == n;
}

inline scalar null_scalar() {
  scalar r;
  r.kind = MYNA_VALUE_NULL;
  r.key = key_kind::null;
  return r;
}
inline scalar bool_scalar(bool v) {
  scalar r;
  r.kind = MYNA_VALUE_BOOL;
  r.key = key_kind::boolean;
  r.bits = v;
  return r;
}
inline scalar string_scalar(std::string_view s) {
  scalar r;
  r.text = s;
  return r;
}

// Core-schema int forms; `ok` false when `s` is not one.
inline scalar core_int(std::string_view s, bool& ok, std::pmr::memory_resource* scratch) {
  ok = true;
  if (s.size() > 2 && s[0] == '0' && s[1] == 'o' && all(s.substr(2), octal)) return radix_integer(s.substr(2), 8, false, scratch);
  if (s.size() > 2 && s[0] == '0' && s[1] == 'x' && all(s.substr(2), hex)) return radix_integer(s.substr(2), 16, false, scratch);
  bool negative = !s.empty() && s[0] == '-';
  size_t sign = !s.empty() && (s[0] == '-' || s[0] == '+') ? 1 : 0;
  size_t n = s.size() - sign;
  if (n && n <= 18) {
    // Fast path: up to 18 digits always fit int64.
    const char* p = s.data() + sign;
    uint64_t v = 0;
    size_t i = 0;
    for (; i < n; ++i) {
      unsigned d = unsigned(p[i]) - '0';
      if (d > 9) break;
      v = v * 10 + d;
    }
    if (i < n) { ok = false; return {}; }
    scalar r;
    r.key = key_kind::integer;
    if (v == 0 && negative) {
      double nz = -0.0;
      r.kind = MYNA_VALUE_F64;
      std::memcpy(&r.bits, &nz, sizeof nz);
      return r;
    }
    r.kind = MYNA_VALUE_I64;
    r.bits = negative ? uint64_t(0) - v : v;
    return r;
  }
  if (all(s.substr(sign), digit)) return decimal_integer(s, s.substr(sign), negative, scratch);
  ok = false;
  return {};
}
inline scalar core_float_value(std::string_view s, bool& ok, std::pmr::memory_resource* scratch) {
  ok = true;
  if (int special = is_special_float(s)) return special_float(special);
  if (core_float(s)) return float_token(s, scratch);
  ok = false;
  return {};
}
// ---- YAML 1.1 ------------------------------------------------------------------------------
inline int is_bool11(std::string_view s) {
  switch (s.size()) {
    case 1: return s[0] == 'y' || s[0] == 'Y' ? 1 : s[0] == 'n' || s[0] == 'N' ? 0 : -1;
    case 2:
      if (s == "on" || s == "On" || s == "ON") return 1;
      if (s == "no" || s == "No" || s == "NO") return 0;
      return -1;
    case 3:
      if (s == "yes" || s == "Yes" || s == "YES") return 1;
      if (s == "off" || s == "Off" || s == "OFF") return 0;
      return -1;
    default:
      return is_bool(s);
  }
}
inline bool digit_or_underscore(char c) { return digit(c) || c == '_'; }
inline bool octal_or_underscore(char c) { return octal(c) || c == '_'; }
inline bool hex_or_underscore(char c) { return hex(c) || c == '_'; }
inline bool binary_or_underscore(char c) { return c == '0' || c == '1' || c == '_'; }
inline bool has_digit(std::string_view s) {
  for (char c : s) if (c != '_') return true;
  return false;
}
// `s` without `_` separators (a copy in scratch only when there are some).
inline std::string_view strip(std::string_view s, std::pmr::memory_resource* scratch) {
  if (s.find('_') == std::string_view::npos) return s;
  char* out = allocate(scratch, s.size());
  size_t at = 0;
  for (char c : s) if (c != '_') out[at++] = c;
  return {out, at};
}
// Sexagesimal `[0-9]+(:[0-5]?[0-9])+` with an optional `.fraction` (separators already removed):
// the integral value in base 60, or false when `s` does not match. Overflow is a limit error.
inline bool sexagesimal(std::string_view s, uint64_t& value, std::string_view& fraction) {
  size_t colon = s.find(':');
  if (colon == std::string_view::npos || colon == 0) return false;
  std::string_view head = s.substr(0, colon);
  if (!all(head, digit)) return false;
  uint64_t v = 0;
  for (char c : head) {
    if (v > (UINT64_MAX - 9) / 10) throw limit_error("sexagesimal integer");
    v = v * 10 + uint64_t(c - '0');
  }
  size_t i = colon;
  fraction = {};
  while (i < s.size() && s[i] == ':') {
    ++i;
    size_t start = i;
    while (i < s.size() && digit(s[i]) && i - start < 2) ++i;
    std::string_view part = s.substr(start, i - start);
    if (part.empty()) return false;
    if (part.size() == 2 && part[0] > '5') return false;
    unsigned p = part.size() == 1 ? unsigned(part[0] - '0') : unsigned((part[0] - '0') * 10 + (part[1] - '0'));
    if (v > (UINT64_MAX - p) / 60) throw limit_error("sexagesimal integer");
    v = v * 60 + p;
  }
  if (i < s.size()) {
    if (s[i] != '.') return false;
    fraction = s.substr(i);
    for (size_t k = 1; k < fraction.size(); ++k)
      if (!digit(fraction[k])) return false;
  }
  value = v;
  return true;
}
inline scalar signed_integer(uint64_t v, bool negative) {
  scalar r;
  r.key = key_kind::integer;
  if (!negative && v <= uint64_t(INT64_MAX)) { r.kind = MYNA_VALUE_I64; r.bits = v; }
  else if (!negative) { r.kind = MYNA_VALUE_U64; r.bits = v; }
  else if (v == 0) {
    double nz = -0.0;
    r.kind = MYNA_VALUE_F64;
    std::memcpy(&r.bits, &nz, sizeof nz);
  } else if (v <= uint64_t(INT64_MAX) + 1) { r.kind = MYNA_VALUE_I64; r.bits = uint64_t(0) - v; }
  else throw limit_error("sexagesimal integer");
  return r;
}
inline scalar int11(std::string_view s, bool& ok, std::pmr::memory_resource* scratch) {
  ok = true;
  bool negative = !s.empty() && s[0] == '-';
  size_t sign = !s.empty() && (s[0] == '-' || s[0] == '+') ? 1 : 0;
  std::string_view body = s.substr(sign);
  if (body.size() > 2 && body[0] == '0' && body[1] == 'b' && all(body.substr(2), binary_or_underscore) &&
      has_digit(body.substr(2)))
    return radix_integer(strip(body.substr(2), scratch), 2, negative, scratch);
  if (body.size() > 2 && body[0] == '0' && body[1] == 'x' && all(body.substr(2), hex_or_underscore) &&
      has_digit(body.substr(2)))
    return radix_integer(strip(body.substr(2), scratch), 16, negative, scratch);
  if (body.size() > 1 && body[0] == '0' && all(body.substr(1), octal_or_underscore) && has_digit(body.substr(1)))
    return radix_integer(strip(body.substr(1), scratch), 8, negative, scratch);
  if (body == "0") return decimal_integer(s, body, negative, scratch);
  if (!body.empty() && body[0] >= '1' && body[0] <= '9') {
    if (all(body, digit_or_underscore)) {
      std::string_view digits = strip(body, scratch);
      return decimal_integer(digits.data() == body.data() ? s : std::string_view{}, digits, negative, scratch);
    }
    std::string_view plain = strip(body, scratch);
    uint64_t v;
    std::string_view fraction;
    if (plain.find('.') == std::string_view::npos && sexagesimal(plain, v, fraction)) return signed_integer(v, negative);
  }
  ok = false;
  return {};
}
inline scalar as_float(scalar r) {
  if (r.kind == MYNA_VALUE_I64 || r.kind == MYNA_VALUE_U64) {
    double d = r.kind == MYNA_VALUE_I64 ? double(int64_t(r.bits)) : double(r.bits);
    r.kind = MYNA_VALUE_F64;
    std::memcpy(&r.bits, &d, sizeof d);
  }
  r.key = key_kind::floating;
  return r;
}
inline scalar float11(std::string_view s, bool& ok, std::pmr::memory_resource* scratch) {
  ok = true;
  if (int special = is_special_float(s)) return special_float(special);
  size_t sign = !s.empty() && (s[0] == '-' || s[0] == '+') ? 1 : 0;
  std::string_view body = s.substr(sign);
  if (!body.empty() && digit(body[0]) && body.find(':') != std::string_view::npos) {
    // [0-9][0-9_]*(:[0-5]?[0-9])+\.[0-9_]*
    std::string_view plain = strip(body, scratch);
    uint64_t v;
    std::string_view fraction;
    if (sexagesimal(plain, v, fraction) && !fraction.empty()) {
      double d = double(v);
      if (fraction.size() > 1) {
        double f = 0;
        std::from_chars(fraction.data(), fraction.data() + fraction.size(), f);  // ".25"
        d += f;
      }
      if (s[0] == '-') d = -d;
      scalar r;
      r.kind = MYNA_VALUE_F64;
      r.key = key_kind::floating;
      std::memcpy(&r.bits, &d, sizeof d);
      return r;
    }
    ok = false;
    return {};
  }
  // ([0-9][0-9_]*)?\.[0-9_]*([eE][-+][0-9]+)?, with at least one digit
  size_t i = 0, n = body.size();
  bool digits = false;
  if (i < n && digit(body[i]))
    while (i < n && digit_or_underscore(body[i])) digits |= body[i] != '_', ++i;
  if (i >= n || body[i] != '.') { ok = false; return {}; }
  ++i;
  while (i < n && digit_or_underscore(body[i])) digits |= body[i] != '_', ++i;
  if (i < n && (body[i] == 'e' || body[i] == 'E')) {
    ++i;
    if (i >= n || (body[i] != '+' && body[i] != '-')) { ok = false; return {}; }
    ++i;
    size_t start = i;
    while (i < n && digit(body[i])) ++i;
    if (i == start) { ok = false; return {}; }
  }
  if (i != n || !digits) { ok = false; return {}; }
  return float_token(strip(s, scratch), scratch);
}
}  // namespace detail

// Implicit resolution of a plain scalar under `s` (no tag). JSON schema errors on non-JSON forms.
inline scalar resolve_plain(std::string_view text, schema s, std::pmr::memory_resource* scratch) {
  using namespace detail;
  switch (s) {
    case schema::failsafe:
      return string_scalar(text);
    case schema::json:
      if (text == "null") return null_scalar();
      if (text == "true") return bool_scalar(true);
      if (text == "false") return bool_scalar(false);
      if (json_int(text)) return decimal_integer(text, text[0] == '-' ? text.substr(1) : text, text[0] == '-', scratch);
      if (json_float(text)) return float_token(text, scratch);
      throw syntax_error("YAML JSON schema: plain scalar is not a JSON null, boolean or number");
    case schema::yaml11: {
      if (is_null(text)) return null_scalar();
      int b = is_bool11(text);
      if (b >= 0) return bool_scalar(b == 1);
      char c = text[0];
      if (digit(c) || c == '-' || c == '+' || c == '.') {
        bool ok;
        scalar r = int11(text, ok, scratch);
        if (ok) return r;
        r = float11(text, ok, scratch);
        if (ok) return r;
        if (valid_timestamp(text)) {
          r = string_scalar(text);
          r.implicit = tag_kind::timestamp;
          return r;
        }
      }
      return string_scalar(text);
    }
    case schema::core:
    default:
      break;
  }
  if (text.empty()) return null_scalar();
  char c = text[0];
  if (c == 'n' || c == 'N' || c == '~') {
    if (is_null(text)) return null_scalar();
    return string_scalar(text);
  }
  if (c == 't' || c == 'T' || c == 'f' || c == 'F') {
    int b = is_bool(text);
    return b < 0 ? string_scalar(text) : bool_scalar(b == 1);
  }
  if (digit(c) || c == '-' || c == '+' || c == '.') {
    bool ok;
    scalar r = core_int(text, ok, scratch);
    if (ok) return r;
    r = core_float_value(text, ok, scratch);
    if (ok) return r;
  }
  return string_scalar(text);
}

namespace detail {
// Exact powers of ten for the Clinger fast path (10^0 .. 10^15 are exact binary64 values).
inline constexpr double exact_powers[16] = {1e0, 1e1, 1e2,  1e3,  1e4,  1e5,  1e6,  1e7,
                                            1e8, 1e9, 1e10, 1e11, 1e12, 1e13, 1e14, 1e15};
}  // namespace detail

// The common untagged plain scalars under CORE, classified by their first byte and length and
// converted without the general resolver: empty, null and bool spellings, strings that cannot be
// anything else, integers of at most 18 digits and decimals `[-+]?[0-9]*\.[0-9]*` of at most 15
// digits (exactly rounded: both the digits and 10^k are exact binary64 values, so one IEEE
// division rounds once, as std::from_chars does). Returns false when `s` needs resolve_plain;
// whenever it returns true, `r` equals resolve_plain(s, schema::core) (pinned by scalars_test).
[[gnu::always_inline]] inline bool resolve_core_fast(std::string_view s, scalar& r) {
  using namespace detail;
  const size_t n = s.size();
  if (n == 0) {
    r = null_scalar();
    return true;
  }
  const unsigned char c = static_cast<unsigned char>(s[0]);
  if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.') {
    size_t i = (c == '-' || c == '+') ? 1 : 0;
    const bool negative = c == '-';
    uint64_t m = 0;
    size_t digits = 0;
    while (i < n) {
      unsigned d = unsigned(static_cast<unsigned char>(s[i])) - '0';
      if (d > 9) break;
      if (++digits > 18) return false;
      m = m * 10 + d;
      ++i;
    }
    if (i == n) {
      if (!digits) return false;  // "-", "+"
      r = scalar{};
      r.key = key_kind::integer;
      if (m == 0 && negative) {
        double nz = -0.0;
        r.kind = MYNA_VALUE_F64;
        std::memcpy(&r.bits, &nz, sizeof nz);
      } else {
        r.kind = MYNA_VALUE_I64;
        r.bits = negative ? uint64_t(0) - m : m;
      }
      return true;
    }
    if (s[i] != '.') return false;  // radix prefixes, exponents, other text
    size_t fraction = 0;
    for (++i; i < n; ++i) {
      unsigned d = unsigned(static_cast<unsigned char>(s[i])) - '0';
      if (d > 9) return false;  // exponents, a second '.', other text
      if (++digits > 15) return false;
      m = m * 10 + d;
      ++fraction;
    }
    if (!digits || digits > 15) return false;  // ".", "-.", or a long integer part
    double v = double(m) / exact_powers[fraction];
    if (negative) v = -v;
    r = scalar{};
    r.kind = MYNA_VALUE_F64;
    r.key = key_kind::floating;
    std::memcpy(&r.bits, &v, sizeof v);
    return true;
  }
  switch (c) {
    case 'n': case 'N': case '~':
      r = is_null(s) ? null_scalar() : string_scalar(s);
      return true;
    case 't': case 'T': case 'f': case 'F': {
      int b = is_bool(s);
      r = b < 0 ? string_scalar(s) : bool_scalar(b == 1);
      return true;
    }
    default:
      r = string_scalar(s);
      return true;
  }
}

// Resolution with an explicit tag (any style). Mismatches are syntax errors.
inline scalar resolve_tagged(std::string_view text, tag_kind tag, schema s, std::pmr::memory_resource* scratch) {
  using namespace detail;
  const bool v11 = s == schema::yaml11;
  bool ok = false;
  scalar r;
  switch (tag) {
    case tag_kind::str:
    case tag_kind::nonspecific:
    case tag_kind::other:
      return string_scalar(text);
    case tag_kind::null_:
      if (is_null(text)) return null_scalar();
      throw syntax_error("YAML !!null tag on a non-null scalar");
    case tag_kind::bool_: {
      int b = v11 ? is_bool11(text) : is_bool(text);
      if (b >= 0) return bool_scalar(b == 1);
      throw syntax_error("YAML !!bool tag on a non-boolean scalar");
    }
    case tag_kind::int_:
      r = v11 ? int11(text, ok, scratch) : core_int(text, ok, scratch);
      if (ok) return r;
      throw syntax_error("YAML !!int tag on a non-integer scalar");
    case tag_kind::float_:
      r = v11 ? float11(text, ok, scratch) : core_float_value(text, ok, scratch);
      if (ok) return r;
      if (v11) {
        r = int11(text, ok, scratch);  // a 1.1 integer read as a float
        if (ok) return as_float(r);
      }
      throw syntax_error("YAML !!float tag on a non-float scalar");
    case tag_kind::seq:
    case tag_kind::map:
      throw syntax_error("YAML collection tag on a scalar");
    default:
      throw syntax_error("YAML tag not supported on a scalar");
  }
}

// Resolves a scalar: tagged scalars by their tag, untagged plain scalars by the schema, and every
// other untagged scalar is a string.
inline scalar resolve(std::string_view text, scalar_style style, schema s, tag_kind tag,
                      std::pmr::memory_resource* scratch) {
  if (tag != tag_kind::none) return resolve_tagged(text, tag, s, scratch);
  if (style != scalar_style::plain) return detail::string_scalar(text);
  return resolve_plain(text, s, scratch);
}

// Canonical STRING text of a resolved scalar used as a mapping key (D2/D3: keys are strings, so
// `{1: a, 0x1: b}`, `{1: a, "1": b}` and `{null: a, ~: b}` are duplicates). null -> "" (as the
// `yaml` npm package stringifies it), booleans -> true/false, integers -> decimal, floats -> the
// shortest round-trip form (std::to_chars), non-finite floats -> .inf/-.inf/.nan, DECIMAL -> its
// JSON-grammar digits, strings -> themselves. Short results are formatted into `buffer`.
inline std::string_view key_text(const scalar& r, char (&buffer)[32]) {
  switch (r.kind) {
    case MYNA_VALUE_NULL: return {};
    case MYNA_VALUE_BOOL: return r.bits ? "true" : "false";
    case MYNA_VALUE_I64: {
      auto out = std::to_chars(buffer, buffer + sizeof buffer, int64_t(r.bits));
      return {buffer, size_t(out.ptr - buffer)};
    }
    case MYNA_VALUE_U64: {
      auto out = std::to_chars(buffer, buffer + sizeof buffer, r.bits);
      return {buffer, size_t(out.ptr - buffer)};
    }
    case MYNA_VALUE_F64: {
      double d;
      std::memcpy(&d, &r.bits, sizeof d);
      if (std::isnan(d)) return ".nan";
      if (std::isinf(d)) return d < 0 ? "-.inf" : ".inf";
      auto out = std::to_chars(buffer, buffer + sizeof buffer, d);
      return {buffer, size_t(out.ptr - buffer)};
    }
    default:
      return r.text;
  }
}

}  // namespace truffledata::yaml
#endif
