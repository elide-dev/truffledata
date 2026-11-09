// Shared stage-1 logic for the SIMD and SWAR kernels. Include it once per kernel translation
// unit after defining TD_CSV_ISA (a namespace name): every definition lands in that namespace and
// is force-inlined, so no out-of-line copy compiled for one instruction set can be linked into
// another kernel (simdjson's per-implementation namespace rule).
#ifndef TD_CSV_ISA
#error "define TD_CSV_ISA before including stage.h"
#endif

#include <cstring>

#include "index.h"

#define TD_CSV_INLINE inline __attribute__((always_inline))

namespace truffledata::csv::TD_CSV_ISA {
namespace {

TD_CSV_INLINE uint64_t prefix_xor_shift(uint64_t x) {
  x ^= x << 1;
  x ^= x << 2;
  x ^= x << 4;
  x ^= x << 8;
  x ^= x << 16;
  x ^= x << 32;
  return x;
}

TD_CSV_INLINE uint64_t below(int bit) { return (uint64_t{1} << bit) - 1; }

/** Writes one entry per set bit, in order; entries carry no flags. */
template <bool Exact>
TD_CSV_INLINE uint32_t* flatten(uint64_t bits, uint32_t base, uint32_t* out) {
  if (Exact) {
    while (bits) {
      *out++ = base + static_cast<uint32_t>(__builtin_ctzll(bits));
      bits &= bits - 1;
    }
    return out;
  }
  // simdjson-style: unconditional groups of eight. Entries past the count are overwritten later
  // or lie past the result, inside the block's 64-entry share of the output (index.h contract).
  int cnt = __builtin_popcountll(bits);
  for (int i = 0; i < 8; i++) {
    out[i] = base + static_cast<uint32_t>(__builtin_ctzll(bits | (uint64_t{1} << 63)));
    bits &= bits - 1;
  }
  if (cnt > 8) {
    for (int i = 8; i < 16; i++) {
      out[i] = base + static_cast<uint32_t>(__builtin_ctzll(bits | (uint64_t{1} << 63)));
      bits &= bits - 1;
    }
    if (cnt > 16) {
      int i = 16;
      do {
        out[i++] = base + static_cast<uint32_t>(__builtin_ctzll(bits));
        bits &= bits - 1;
      } while (bits);
    }
  }
  return out + cnt;
}

/** Carried state plus the per-block step from bitmaps to entries. */
struct Stage {
  uint64_t inside_carry;   // all ones when the previous block ended inside quotes
  uint64_t closing_carry;  // 1 when the previous block's last byte was a closing quote
  bool fq, fe, fu;         // pending flags of the field that continues into this block

  explicit Stage(uint64_t carry)
      : inside_carry(carry & CARRY_INSIDE ? ~uint64_t{0} : 0),
        closing_carry(carry & CARRY_CLOSING ? 1 : 0),
        fq(carry & CARRY_Q),
        fe(carry & CARRY_E),
        fu(carry & CARRY_U) {}

  uint64_t carry() const {
    return (inside_carry ? CARRY_INSIDE : 0) | (closing_carry ? CARRY_CLOSING : 0) |
           (fq ? CARRY_Q : 0) | (fe ? CARRY_E : 0) | (fu ? CARRY_U : 0);
  }

  /**
   * One block: `sep` marks delimiter, LF and CR bytes, `quote` quote bytes, `valid` the real
   * bytes of the block (all ones except in a zero-padded tail).
   */
  template <uint64_t (*PrefixXor)(uint64_t), bool Exact>
  TD_CSV_INLINE uint32_t* block(uint64_t sep, uint64_t quote, uint64_t valid, uint32_t base,
                                uint32_t* out) {
    if (quote == 0 && !fq) {
      // No quote here and none pending, so nothing is inside quotes (an open quote would have
      // set fq): every candidate is a separator and every flag is zero.
      uint64_t unq = ~sep & valid;
      closing_carry = 0;
      if (sep == 0) {
        fu = fu || unq != 0;
        return out;
      }
      int last = 63 - __builtin_clzll(sep);
      fe = false;
      fu = (unq >> last >> 1) != 0;
      return flatten<Exact>(sep, base, out);
    }
    uint64_t inside = PrefixXor(quote) ^ inside_carry;
    inside_carry = static_cast<uint64_t>(static_cast<int64_t>(inside) >> 63);
    uint64_t seps = sep & ~inside;
    uint64_t closing = quote & ~inside;
    uint64_t reopen = quote & inside & ((closing << 1) | closing_carry);
    // The last real byte: bit 63, or the last byte before a tail block's padding.
    closing_carry = (closing >> (63 - __builtin_clzll(valid))) & 1;
    uint64_t unq = ~inside & ~quote & ~sep & valid;
    uint64_t seen = 0;  // bits up to and including the previous separator
    while (seps) {
      uint64_t bit = seps & (0 - seps);
      uint64_t m = (bit - 1) & ~seen;
      bool q = fq || (quote & m), e = fe || (reopen & m), u = fu || (unq & m);
      *out++ = (base + static_cast<uint32_t>(__builtin_ctzll(bit))) | (q ? FLAG_QUOTED : 0) |
               (e ? FLAG_ESCAPED : 0) | (q && u ? FLAG_UNQUOTED : 0);
      fq = fe = fu = false;
      seen = bit | (bit - 1);
      seps ^= bit;
    }
    uint64_t tail = valid & ~seen;
    fq = fq || (quote & tail);
    fe = fe || (reopen & tail);
    fu = fu || (unq & tail);
    return out;
  }
};

/** Runs a mask loader over full 64-byte blocks, then over one padded tail block. */
template <class Masks, uint64_t (*PrefixXor)(uint64_t)>
TD_CSV_INLINE uint64_t run(const uint8_t* data, uint64_t length, uint32_t* out, Format format,
                           uint64_t* carry) {
  const Masks masks(format);
  Stage stage(*carry);
  uint32_t* o = out;
  uint64_t i = 0;
  for (; i + 64 <= length; i += 64) {
    uint64_t sep, quote;
    masks.load(data + i, &sep, &quote);
    o = stage.template block<PrefixXor, false>(sep, quote, ~uint64_t{0},
                                               static_cast<uint32_t>(i), o);
  }
  if (i < length) {
    alignas(64) uint8_t tail[64];
    uint64_t n = length - i;
    std::memcpy(tail, data + i, n);
    // Padding is a byte that is neither a separator nor a quote for this format.
    uint8_t pad = 'a';
    while (pad == format.delimiter || (format.quoting && pad == format.quote)) pad++;
    std::memset(tail + n, pad, 64 - n);
    uint64_t sep, quote;
    masks.load(tail, &sep, &quote);
    o = stage.template block<PrefixXor, true>(sep, quote, below(static_cast<int>(n)),
                                              static_cast<uint32_t>(i), o);
  }
  *carry = stage.carry();
  return static_cast<uint64_t>(o - out);
}

}  // namespace
}  // namespace truffledata::csv::TD_CSV_ISA
