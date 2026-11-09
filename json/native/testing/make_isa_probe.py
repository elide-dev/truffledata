#!/usr/bin/env python3
"""Generate test-only target instantiations from the exact production stage2 walk."""
import pathlib
import sys
source = pathlib.Path(sys.argv[1]).read_text()
variants = []
for name, target, width in [
    ("avx2", "avx2,bmi,bmi2,lzcnt,popcnt", 32),
    ("avx512", "avx512bw,avx512vl,bmi2", 64),
]:
    code = source.replace("TRUFFLEDATA_JSON_STAGE2_H", "TRUFFLEDATA_JSON_TESTING_STAGE2_" + name.upper() + "_H")
    code = code.replace("namespace truffledata::json {", "namespace truffledata::json_" + name + " {\nusing namespace truffledata::json;")
    start = code.index("    __m128i v =", code.index("inline scan_hit scan_string"))
    end = code.index("#elif defined(__aarch64__)", start)
    if width == 32:
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
