// Scalar resolution against the D2 table (docs/superpowers/plans/2026-11-06-yaml.md): every core
// form, the JSON and failsafe schemas, number ranges and explicit core tags.
#include "yaml/native/scalars.h"

#include "yaml/native/testing.h"

#include <cmath>
#include <cstdio>
#include <cstring>
#include <limits>
#include <string>

using namespace truffledata::yaml;

namespace {
std::pmr::monotonic_buffer_resource scratch_memory;

scalar plain(std::string_view text, schema s = schema::core) {
  return resolve(text, scalar_style::plain, s, tag_kind::none, &scratch_memory);
}
scalar tagged(std::string_view text, tag_kind tag, scalar_style style = scalar_style::plain) {
  return resolve(text, style, schema::core, tag, &scratch_memory);
}
double f64(const scalar& r) {
  double d;
  std::memcpy(&d, &r.bits, sizeof d);
  return d;
}
bool is_i64(const scalar& r, int64_t v) { return r.kind == MYNA_VALUE_I64 && int64_t(r.bits) == v && r.key == key_kind::integer; }
bool is_u64(const scalar& r, uint64_t v) { return r.kind == MYNA_VALUE_U64 && r.bits == v; }
bool is_f64(const scalar& r, double v) {
  return r.kind == MYNA_VALUE_F64 && !r.nonfinite && f64(r) == v && std::signbit(f64(r)) == std::signbit(v);
}
bool is_decimal(const scalar& r, std::string_view text) { return r.kind == MYNA_VALUE_DECIMAL && r.text == text; }
bool is_string(const scalar& r, std::string_view text) {
  return r.kind == MYNA_VALUE_STRING && r.text == text && r.key == key_kind::string;
}
template <class F>
bool syntax(F f) {
  try {
    f();
  } catch (const error& e) {
    return e.code == status_syntax;
  }
  return false;
}

void core_table() {
  // null
  for (auto s : {"", "~", "null", "Null", "NULL"}) {
    auto r = plain(s);
    require(r.kind == MYNA_VALUE_NULL && r.key == key_kind::null);
  }
  for (auto s : {"nULL", "NUll", "none", "Nil"}) require(is_string(plain(s), s));
  // bool
  for (auto s : {"true", "True", "TRUE"}) require(plain(s).kind == MYNA_VALUE_BOOL && plain(s).bits == 1);
  for (auto s : {"false", "False", "FALSE"}) require(plain(s).kind == MYNA_VALUE_BOOL && plain(s).bits == 0);
  for (auto s : {"tRUE", "yes", "no", "on", "off", "y", "n", "Yes", "ON"}) require(is_string(plain(s), s));
  // int
  require(is_i64(plain("0"), 0));
  require(is_i64(plain("12"), 12));
  require(is_i64(plain("+12"), 12));
  require(is_i64(plain("-12"), -12));
  require(is_i64(plain("0755"), 755));  // 1.1 octal is decimal under core
  require(is_i64(plain("0o17"), 15));
  require(is_i64(plain("0o0"), 0));
  require(is_i64(plain("0x1F"), 31));
  require(is_i64(plain("0x1f"), 31));
  require(is_i64(plain("9223372036854775807"), INT64_MAX));
  require(is_i64(plain("-9223372036854775808"), INT64_MIN));
  require(is_u64(plain("9223372036854775808"), 9223372036854775808ull));
  require(is_u64(plain("18446744073709551615"), UINT64_MAX));
  require(is_u64(plain("0xFFFFFFFFFFFFFFFF"), UINT64_MAX));
  require(is_decimal(plain("18446744073709551616"), "18446744073709551616"));
  require(plain("18446744073709551616").token);
  require(is_decimal(plain("-9223372036854775809"), "-9223372036854775809"));
  require(is_decimal(plain("+18446744073709551616"), "18446744073709551616"));
  require(!plain("+18446744073709551616").token);
  require(is_decimal(plain("000018446744073709551616"), "18446744073709551616"));
  require(is_decimal(plain("0x10000000000000000"), "18446744073709551616"));
  require(is_decimal(plain("0o2000000000000000000000"), "18446744073709551616"));
  require(is_decimal(plain("0x123456789ABCDEF0123456789ABCDEF"), "1512366075204170929049582354406559215"));
  // `0x1_0000_0000_0000_0000` has YAML 1.1 digit separators: under core it is a string (the
  // DECIMAL 18446744073709551616 under YAML_1_1 is pinned with the 1.1 schema).
  require(is_string(plain("0x1_0000_0000_0000_0000"), "0x1_0000_0000_0000_0000"));
  require(is_string(plain("0b101"), "0b101"));
  require(is_string(plain("-0x1F"), "-0x1F"));
  require(is_string(plain("0x"), "0x"));
  require(is_string(plain("0o8"), "0o8"));
  require(is_string(plain("1_000"), "1_000"));
  require(is_string(plain("1:20"), "1:20"));
  // negative zero is F64 -0.0, as in the JSON backend
  require(is_f64(plain("-0"), -0.0));
  require(is_f64(plain("-000"), -0.0));
  require(is_i64(plain("+0"), 0));
  // float
  require(is_f64(plain("1.5"), 1.5));
  require(is_f64(plain("-1.5"), -1.5));
  require(is_f64(plain("+1.5"), 1.5));
  require(is_f64(plain(".5"), 0.5));
  require(is_f64(plain("-.5"), -0.5));
  require(is_f64(plain("1."), 1.0));
  require(is_f64(plain("1e3"), 1000.0));
  require(is_f64(plain("1E-3"), 0.001));
  require(is_f64(plain("1.5e+3"), 1500.0));
  require(is_f64(plain("0.1"), 0.1));
  require(is_f64(plain("1e-400"), 0.0));
  require(is_f64(plain("-1e-400"), -0.0));
  require(plain("1.5").key == key_kind::floating);
  for (auto s : {"1e", "1e+", ".", "+.", "1.2.3", "e3", ".e3", "1,5", "1 5"}) require(is_string(plain(s), s));
  require(is_decimal(plain("1e400"), "1e400") && plain("1e400").token);
  require(is_decimal(plain("-1E400"), "-1E400"));
  require(is_decimal(plain("+1e400"), "1e400"));
  require(is_decimal(plain(".5e400"), "0.5e400"));
  require(is_decimal(plain("1.e400"), "1.0e400"));
  require(is_decimal(plain("01.5e400"), "1.5e400"));
  // non-finite
  auto inf = plain(".inf");
  require(inf.kind == MYNA_VALUE_F64 && inf.nonfinite && std::isinf(f64(inf)) && f64(inf) > 0);
  require(plain("+.inf").nonfinite && f64(plain("+.Inf")) > 0);
  auto ninf = plain("-.Inf");
  require(ninf.nonfinite && std::isinf(f64(ninf)) && f64(ninf) < 0);
  require(plain("-.INF").nonfinite);
  auto nan = plain(".NaN");
  require(nan.nonfinite && std::isnan(f64(nan)) && nan.key == key_kind::floating);
  require(plain(".nan").nonfinite && plain(".NAN").nonfinite);
  for (auto s : {"-.nan", "+.nan", ".Nan", "inf", "-inf", "nan", ".infinity", "Infinity"}) require(is_string(plain(s), s));
  // strings
  require(is_string(plain("hello world"), "hello world"));
  require(is_string(plain("2001-12-14"), "2001-12-14"));
  require(is_string(plain("<<"), "<<"));
}

void styles_and_tags() {
  // Only plain scalars resolve implicitly.
  for (auto style : {scalar_style::single_quoted, scalar_style::double_quoted, scalar_style::literal, scalar_style::folded}) {
    require(is_string(resolve("12", style, schema::core, tag_kind::none, &scratch_memory), "12"));
    require(is_string(resolve("null", style, schema::core, tag_kind::none, &scratch_memory), "null"));
  }
  require(is_string(tagged("12", tag_kind::str), "12"));
  require(is_string(tagged("0x1F", tag_kind::str), "0x1F"));
  require(is_string(tagged("true", tag_kind::nonspecific), "true"));
  require(is_i64(tagged("12", tag_kind::int_, scalar_style::double_quoted), 12));
  require(is_i64(tagged("0x1F", tag_kind::int_), 31));
  require(syntax([] { tagged("abc", tag_kind::int_); }));
  require(syntax([] { tagged("1.5", tag_kind::int_); }));
  require(is_f64(tagged("1", tag_kind::float_), 1.0));
  require(syntax([] { tagged("0x10", tag_kind::float_); }));
  require(is_f64(tagged("1.5", tag_kind::float_, scalar_style::single_quoted), 1.5));
  require(tagged(".inf", tag_kind::float_).nonfinite);
  require(syntax([] { tagged("abc", tag_kind::float_); }));
  require(tagged("TRUE", tag_kind::bool_).kind == MYNA_VALUE_BOOL);
  require(syntax([] { tagged("yes", tag_kind::bool_); }));
  require(tagged("~", tag_kind::null_).kind == MYNA_VALUE_NULL);
  require(tagged("", tag_kind::null_).kind == MYNA_VALUE_NULL);
  require(syntax([] { tagged("nil", tag_kind::null_); }));
  require(syntax([] { tagged("a", tag_kind::seq); }));
  require(syntax([] { tagged("a", tag_kind::map); }));
}

void json_schema() {
  require(plain("null", schema::json).kind == MYNA_VALUE_NULL);
  require(plain("true", schema::json).kind == MYNA_VALUE_BOOL);
  require(is_i64(plain("-12", schema::json), -12));
  require(is_f64(plain("-0", schema::json), -0.0));
  require(is_f64(plain("1.5e3", schema::json), 1500.0));
  require(is_u64(plain("18446744073709551615", schema::json), UINT64_MAX));
  require(is_decimal(plain("1E400", schema::json), "1E400"));
  for (auto s : {".5", "+1", "0x1F", "~", "", "Null", "TRUE", "01", "0o7", ".inf", "hello", "1_0"}) {
    std::string text = s;
    require(syntax([&] { plain(text, schema::json); }));
  }
  // Quoted scalars are strings under every schema.
  require(is_string(resolve("hello", scalar_style::double_quoted, schema::json, tag_kind::none, &scratch_memory), "hello"));
}

void failsafe_schema() {
  for (auto s : {"", "~", "null", "true", "12", "1.5", ".inf", "0x1F", "hello"})
    require(is_string(plain(s, schema::failsafe), s));
  // Explicit core tags still apply.
  require(is_i64(resolve("12", scalar_style::plain, schema::failsafe, tag_kind::int_, &scratch_memory), 12));
}

void key_texts() {
  char buffer[32];
  require(key_text(plain("~"), buffer).empty());
  require(key_text(plain("null"), buffer).empty());
  require(key_text(plain("TRUE"), buffer) == "true");
  require(key_text(plain("0x1F"), buffer) == "31");
  require(key_text(plain("+01"), buffer) == "1");
  require(key_text(plain("18446744073709551615"), buffer) == "18446744073709551615");
  require(key_text(plain("1.50"), buffer) == "1.5");
  require(key_text(plain("-0"), buffer) == "-0");
  require(key_text(plain(".Inf"), buffer) == ".inf");
  require(key_text(plain("-.inf"), buffer) == "-.inf");
  require(key_text(plain(".NAN"), buffer) == ".nan");
  require(key_text(plain("1e400"), buffer) == "1e400");
  require(key_text(plain("abc"), buffer) == "abc");
}
// resolve_core_fast equals resolve_plain under CORE whenever it answers: kind, key kind, payload
// bits and text, over fixed boundary tokens and 200,000 pseudo-random tokens drawn from the
// characters that matter to the grammar (decimals of up to 15 digits are exactly rounded).
bool same(const scalar& a, const scalar& b) {
  return a.kind == b.kind && a.key == b.key && a.nonfinite == b.nonfinite && a.bits == b.bits && a.text == b.text &&
         a.token == b.token && a.implicit == b.implicit;
}
void core_fast_matches_general() {
  size_t answered = 0, checked = 0;
  auto check = [&](const std::string& token) {
    scalar fast;
    ++checked;
    if (!resolve_core_fast(token, fast)) return;
    ++answered;
    scalar general = plain(token);
    if (!same(fast, general)) std::fprintf(stderr, "resolve_core_fast differs on '%s'\n", token.c_str());
    require(same(fast, general));
  };
  for (const char* token : {"", "~", "~~", "null", "Null", "NULL", "nULL", "true", "True", "TRUE", "false", "False",
                            "FALSE", "fals", "truee", "0", "-0", "+0", "00", "-", "+", ".", "-.", "1.", ".5", "-.5",
                            "+.5", "0.25", "-0.0", "1.25", "123456789012345.", "1234567890123456.", "0.000000000000001",
                            "999999999999999", "999999999999999999", "9999999999999999999", "-999999999999999999",
                            "0x1F", "0o17", ".inf", ".nan", "1e5", "1.5e5", "1.5.5", "abc", "nope", "t0", "-x", "+x"})
    check(token);
  const char alphabet[] = "0123456789000111.-+eExo_abnultrsfNTF~ ";
  uint64_t state = 0x9E3779B97F4A7C15ull;
  auto next = [&] {
    state ^= state << 13, state ^= state >> 7, state ^= state << 17;
    return state;
  };
  for (int i = 0; i < 200000; ++i) {
    size_t length = next() % 21;
    std::string token;
    bool numeric = next() % 2;
    for (size_t k = 0; k < length; ++k) {
      if (numeric) {
        uint64_t r = next() % 16;
        token.push_back(r < 12 ? char('0' + r % 10) : r == 12 ? '.' : r == 13 ? '-' : r == 14 ? '+' : 'e');
      } else {
        token.push_back(alphabet[next() % (sizeof alphabet - 1)]);
      }
    }
    check(token);
  }
  std::printf("core_fast_matches_general: %zu of %zu tokens answered by the fast resolver, all equal\n", answered,
              checked);
  require(answered > checked / 4);
}
}  // namespace

int main() {
  try {
    core_fast_matches_general();
    core_table();
    styles_and_tags();
    json_schema();
    failsafe_schema();
    key_texts();
    // Radix conversion is bounded: a 1,001-digit hex integer is a resource-limit error.
    std::string big = "0x" + std::string(detail::max_radix_digits + 1, 'F');
    bool limited = false;
    try {
      plain(big);
    } catch (const error& e) {
      limited = e.code == status_limit;
    }
    require(limited);
    require(plain("0x" + std::string(detail::max_radix_digits, 'F')).kind == MYNA_VALUE_DECIMAL);
  } catch (const std::exception& e) {
    std::fprintf(stderr, "scalars_test failure: %s\n", e.what());
    return 1;
  }
  std::printf("scalar resolution checks passed\n");
  return 0;
}
