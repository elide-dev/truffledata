#!/bin/bash
# Builds rapidyaml 0.16.0 (+c4core, +extra ints handler) and the C++ probe locally (arm64).
# SAN=-asan SANFLAGS="-fsanitize=address,undefined -fno-sanitize-recover=all -g -O1" for a sanitizer build.
set -e
S=/private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/107505d2-50ff-4334-8569-b06acaf924d5/scratchpad
cd "$S"
SAN="${SAN:-}"
FLAGS="${SANFLAGS:--O3 -DNDEBUG}"
R=dl/rapidyaml/rapidyaml.v0.16.0
OUT=build/ryml$SAN
mkdir -p $OUT
INC="-I$R/src -I$R/src_extra -I$R/ext/c4core.src -DRYML_DEFAULT_CALLBACK_USES_EXCEPTIONS"
SRCS="$(ls $R/src/c4/yml/*.cpp) $(ls $R/ext/c4core.src/c4/*.cpp) $(ls $R/src_extra/c4/yml/extra/*.cpp)"
objs=""
for f in $SRCS; do
  o=$OUT/$(echo $f | tr '/' '_' | sed 's/\.cpp$/.o/')
  if [ ! -f $o ]; then clang++ -std=c++17 $FLAGS $INC -c $f -o $o & fi
  objs="$objs $o"
done
wait
rm -f $OUT/libryml.a
ar rcs $OUT/libryml.a $objs
ls -la $OUT/libryml.a
FY=dl/libfyaml/libfyaml-0.9.6
clang $FLAGS -I$FY/include -c probe/fyprobe.c -o $OUT/fyprobe.o
clang++ -std=c++17 $FLAGS $INC -Idl/libyaml/yaml-0.2.5/include -DYAML_DECLARE_STATIC -I$FY/include -Imyna/include \
  probe/cprobe.cpp $OUT/fyprobe.o $OUT/libryml.a build/libyaml$SAN/libyaml.a build/libfyaml/libfyaml.a -o build/cprobe$SAN
ls -la build/cprobe$SAN
