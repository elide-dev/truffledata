#!/usr/bin/env bash
# Refreshes the vendored sources of truffledata-json-native from the repository and the Bazel
# external repositories. Run from anywhere after `bazel build //json/native:bindings`:
#   EXTERNAL="$(bazel info output_base)/external" json/native/crate/vendor.sh
set -euo pipefail
crate="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$crate/../../.." && pwd)"
: "${EXTERNAL:?set EXTERNAL to \$(bazel info output_base)/external}"
v="$crate/vendor"
rm -rf "$v"
mkdir -p "$v/json/native/generated" "$v/tape/native" "$v/myna/include/myna" "$v/simdjson" "$v/simdutf"
cp "$root"/json/native/{api.cc,api.h,decoder.h,decoder_common.h,parser_pool.h,session.h,stage1.h,stage2.h,status.h,stream.h,typed.h} "$v/json/native/"
cp "$root/bazel-bin/json/native/generated/seam.h" "$v/json/native/generated/seam.h"
cp "$root"/tape/native/{arena.h,ctape.h,index_memory.h,key_table.h,tape_writer.h,tape_typed.h,text_arena.h,typed_plan.h,typed_slots.h,text.cc} "$v/tape/native/"
cp "$EXTERNAL"/+http_archive+myna/include/myna/{values.h,values.hpp} "$v/myna/include/myna/"
cp "$EXTERNAL"/+http_archive+simdjson/singleheader/{simdjson.h,simdjson.cpp} "$v/simdjson/"
cp "$EXTERNAL"/rules_rust++crate+crates__simdutf-0.7.0/cpp/{simdutf.h,simdutf.cpp} "$v/simdutf/"
chmod -R u+w "$v"
