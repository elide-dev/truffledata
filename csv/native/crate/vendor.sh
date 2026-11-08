#!/usr/bin/env bash
# Refreshes the vendored sources of truffledata-csv-native from the repository and the Bazel
# external repositories. Run from anywhere after `bazel build //csv/native:bindings`:
#   EXTERNAL="$(bazel info output_base)/external" csv/native/crate/vendor.sh
set -euo pipefail
crate="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$crate/../../.." && pwd)"
: "${EXTERNAL:?set EXTERNAL to \$(bazel info output_base)/external}"
v="$crate/vendor"
rm -rf "$v"
mkdir -p "$v/csv/native/generated" "$v/simdutf"
cp "$root"/csv/native/{api.cc,api.h,dispatch.cc,index.h,stage.h,utf8.h,index_avx2.cc,index_avx512.cc,index_neon.cc,index_swar.cc} "$v/csv/native/"
cp "$root/bazel-bin/csv/native/generated/seam.h" "$v/csv/native/generated/seam.h"
cp "$EXTERNAL"/rules_rust++crate+crates__simdutf-0.7.0/cpp/{simdutf.h,simdutf.cpp} "$v/simdutf/"
chmod -R u+w "$v"
