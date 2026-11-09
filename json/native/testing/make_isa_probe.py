#!/usr/bin/env python3
"""Generate test-only target instantiations from the exact production stage2 walk."""
import pathlib
import sys
source = pathlib.Path(sys.argv[1]).read_text()
variants = []
# (name, target attribute, string-scan width, simdjson implementation whose number kernel the
# variant calls). Under -march=compatibility `simdjson::builtin` is the fallback kernel; the
# westmere/haswell kernels parse eight digits with SSSE3/AVX2 multiply-adds (plan WS8 probe).
for name, target, width, numbers in [
    ("avx2", "avx2,bmi,bmi2,lzcnt,popcnt", 32, "haswell"),
    ("avx512", "avx512bw,avx512vl,bmi2", 64, "haswell"),
    ("sse42", "sse4.2,pclmul,popcnt", 16, "westmere"),
    ("avx2_scalar_numbers", "avx2,bmi,bmi2,lzcnt,popcnt", 32, "builtin"),
]:
    code = source.replace("TRUFFLEDATA_JSON_STAGE2_H", "TRUFFLEDATA_JSON_TESTING_STAGE2_" + name.upper() + "_H")
    code = code.replace("namespace truffledata::json {", "namespace truffledata::json_" + name + " {\nusing namespace truffledata::json;")
    assert code.count("simdjson::builtin::numberparsing::parse_number") == 1
    code = code.replace("simdjson::builtin::numberparsing::parse_number", "simdjson::" + numbers + "::numberparsing::parse_number")
    start = code.index("    __m128i v =", code.index("inline scan_hit scan_string"))
    end = code.index("#elif defined(__aarch64__)", start)
    if width == 16:
        scanner = code[start:end]
    elif width == 32:
        scanner = '''    __m256i v = _mm256_loadu_si256(reinterpret_cast<const __m256i*>(text + offset));
    uint32_t matches = uint32_t(_mm256_movemask_epi8(_mm256_or_si256(
        _mm256_cmpeq_epi8(v, _mm256_set1_epi8('"')), _mm256_cmpeq_epi8(v, _mm256_set1_epi8('\\\\')))));
    uint32_t high = uint32_t(_mm256_movemask_epi8(v));
    if (matches) {
      uint32_t at = uint32_t(__builtin_ctz(matches));
      if (text + offset + at >= end) syntax("unterminated JSON string");
      ascii &= (high & ((1u << at) - 1)) == 0;
      return {offset + at, text[offset + at] == '"', ascii};
    }
    ascii &= high == 0;
    offset += 32;
'''
    else:
        scanner = '''    __m512i v = _mm512_loadu_si512(reinterpret_cast<const void*>(text + offset));
    uint64_t matches = uint64_t(_mm512_cmpeq_epi8_mask(v, _mm512_set1_epi8('"')) |
        _mm512_cmpeq_epi8_mask(v, _mm512_set1_epi8('\\\\')));
    uint64_t high = uint64_t(_mm512_movepi8_mask(v));
    if (matches) {
      uint32_t at = uint32_t(__builtin_ctzll(matches));
      if (text + offset + at >= end) syntax("unterminated JSON string");
      ascii &= (high & ((uint64_t(1) << at) - 1)) == 0;
      return {offset + at, text[offset + at] == '"', ascii};
    }
    ascii &= high == 0;
    offset += 64;
'''
    code = code[:start] + scanner + code[end:]
    # All walk methods and their scan/unescape kernels share one target attribute. The caller
    # remains baseline and calls the selected template only after an explicit CPU check.
    variants.append('#pragma clang attribute push(__attribute__((target("' + target + '"))), apply_to=function)\n' + code + '\n#pragma clang attribute pop\n')
pathlib.Path(sys.argv[2]).write_text('#include "json/native/decoder.h"\n#if defined(__x86_64__)\n#include <immintrin.h>\n' + '\n'.join(variants) + '\n#endif\n')
