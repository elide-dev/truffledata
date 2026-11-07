#!/bin/bash
set -e
cd /private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/107505d2-50ff-4334-8569-b06acaf924d5/scratchpad/rprobe
export CC=/opt/homebrew/opt/llvm/bin/clang CXX=/opt/homebrew/opt/llvm/bin/clang++
[ -x "$CC" ] || { CC=$(which clang); CXX=$(which clang++); }
export CARGO_TARGET_AARCH64_APPLE_DARWIN_LINKER=/usr/bin/clang
cargo build --release "$@" 2>&1 | grep -E "^error|^warning: unused|-->|Finished" | head -30
ls -la target/release/rprobe target/release/ulyaml-events
