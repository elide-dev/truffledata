/* Deliberately incompatible library: AUTO must not hide an ABI mismatch. The all-zero fingerprint
 * matches no generated binding (ABI 12: logical totals in SlabResult), and the library exports none of the ABI 12
 * slab symbols, so a backend that skipped the fingerprint check would fail at symbol lookup. */
const char *td_buffa_fingerprint(void) {
  return "0000000000000000000000000000000000000000000000000000000000000000";
}
