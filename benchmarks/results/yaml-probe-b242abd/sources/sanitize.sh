#!/bin/bash
# ASan/UBSan builds of every native candidate, run over all 402 yaml-test-suite inputs and the fixtures.
S=/private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/107505d2-50ff-4334-8569-b06acaf924d5/scratchpad
cd $S
SF="-fsanitize=address,undefined -fno-sanitize-recover=all -fno-omit-frame-pointer -g -O1"
SAN=-asan SANFLAGS="$SF" bash tools/build_c.sh > /dev/null 2>&1 || echo "libyaml asan build failed"
cmake -S dl/libfyaml/libfyaml-0.9.6 -B build/libfyaml-asan -G Ninja -DCMAKE_BUILD_TYPE=Debug -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
  -DCMAKE_C_COMPILER=clang -DBUILD_SHARED_LIBS=OFF "-DCMAKE_C_FLAGS=$SF" "-DCMAKE_EXE_LINKER_FLAGS=-fsanitize=address,undefined" > build-libfyaml-asan.log 2>&1
ninja -C build/libfyaml-asan fy-tool >> build-libfyaml-asan.log 2>&1 || echo "libfyaml asan build failed"
# ryml probe against asan libyaml; libfyaml static lib from the asan build
SAN=-asan SANFLAGS="$SF" bash tools/build_cprobe.sh > build-cprobe-asan.log 2>&1 || { echo "cprobe asan build failed"; tail -5 build-cprobe-asan.log; }
T=$S/dl/yaml-test-suite/yaml-test-suite-data-2022-01-17
export ASAN_OPTIONS=detect_leaks=0:abort_on_error=0 UBSAN_OPTIONS=print_stacktrace=1
for tool in "build/cprobe-asan events" "build/libyaml-asan/run-parser-test-suite" "build/libfyaml-asan/fy-tool --testsuite"; do
  n=0; bad=0
  for f in $(find $T -name in.yaml | grep -v -E "/(name|tags)/") probe/fixtures/*.yaml; do
    n=$((n+1))
    if $tool $f 2>&1 >/dev/null | grep -q -E "Sanitizer|runtime error"; then
      bad=$((bad+1)); echo "  SANITIZER: $tool $f"
    fi
  done
  echo "$tool: $n inputs, $bad with sanitizer reports"
done
