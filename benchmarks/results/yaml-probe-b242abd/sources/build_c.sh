#!/bin/bash
# Local arm64 builds of the C parsers (libyaml 0.2.5, libfyaml 0.9.6).
set -e
S=/private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/107505d2-50ff-4334-8569-b06acaf924d5/scratchpad
cd "$S"
SAN="${SAN:-}"
OUT=build/libyaml$SAN
mkdir -p $OUT
Y=dl/libyaml/yaml-0.2.5
DEFS='-DYAML_VERSION_MAJOR=0 -DYAML_VERSION_MINOR=2 -DYAML_VERSION_PATCH=5 -DYAML_VERSION_STRING="0.2.5" -DYAML_DECLARE_STATIC'
FLAGS="${SANFLAGS:--O3 -DNDEBUG}"
for f in api dumper emitter loader parser reader scanner writer; do
  clang $FLAGS $DEFS -I$Y/include -c $Y/src/$f.c -o $OUT/$f.o
done
ar rcs $OUT/libyaml.a $OUT/*.o
clang $FLAGS $DEFS -I$Y/include $Y/tests/run-parser-test-suite.c $OUT/libyaml.a -o $OUT/run-parser-test-suite
echo libyaml ok
if [ -z "$SAN" ]; then
cmake -S dl/libfyaml/libfyaml-0.9.6 -B build/libfyaml -G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_POLICY_VERSION_MINIMUM=3.5 -DCMAKE_C_COMPILER=clang -DBUILD_SHARED_LIBS=OFF -DCMAKE_C_FLAGS=-O3 > build-libfyaml.log 2>&1 || { tail -30 build-libfyaml.log; exit 1; }
ninja -C build/libfyaml >> build-libfyaml.log 2>&1 || { tail -30 build-libfyaml.log; exit 1; }
echo libfyaml ok
ls build/libfyaml | head -60
fi
