"""Shared safety checks for positive native images and the expected-failure control."""

TRUFFLE_JIT_CHECKS = [
    "-H:+TruffleCheckBlockListMethods",
    "-H:+TruffleCheckNeverPartOfCompilation",
]
