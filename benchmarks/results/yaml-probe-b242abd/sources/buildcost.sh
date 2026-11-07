#!/bin/bash
# Serial -O3 compile time and archive/text size per native candidate (local arm64, Homebrew clang 23).
S=/private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/107505d2-50ff-4334-8569-b06acaf924d5/scratchpad
cd $S
rm -rf build/cost && mkdir -p build/cost/ryml build/cost/libyaml
R=dl/rapidyaml/rapidyaml.v0.16.0
INC="-I$R/src -I$R/src_extra -I$R/ext/c4core.src -DRYML_DEFAULT_CALLBACK_USES_EXCEPTIONS -DRYML_WITH_TAB_TOKENS -DNDEBUG"
t0=$(date +%s.%N 2>/dev/null || python3 -c 'import time;print(time.time())')
start=$(python3 -c 'import time;print(time.time())')
for f in $(ls $R/src/c4/yml/*.cpp) $(ls $R/ext/c4core.src/c4/*.cpp); do
  clang++ -std=c++17 -O3 $INC -c $f -o build/cost/ryml/$(basename $f .cpp).o
done
# the parse engine is a template: instantiate it once for an event handler, as the YAML decoder will
printf '#include <c4/yml/parse_engine.def.hpp>\n#include <c4/yml/extra/event_handler_ints.hpp>\ntemplate class c4::yml::ParseEngine<c4::yml::extra::EventHandlerInts>;\n' > build/cost/inst.cpp
clang++ -std=c++17 -O3 $INC -c build/cost/inst.cpp -o build/cost/ryml/inst.o
end=$(python3 -c 'import time;print(time.time())')
echo "rapidyaml 0.16.0 (+c4core, one handler instantiation): serial compile $(python3 -c "print(round($end-$start,1))") s"
ar rcs build/cost/libryml.a build/cost/ryml/*.o
size build/cost/ryml/inst.o | tail -1 | awk '{print "  handler instantiation __TEXT bytes: "$1}'
ls -l build/cost/libryml.a | awk '{print "  archive bytes: "$5}'
start=$(python3 -c 'import time;print(time.time())')
Y=dl/libyaml/yaml-0.2.5
for f in api dumper emitter loader parser reader scanner writer; do
  clang -O3 -DNDEBUG -DYAML_VERSION_MAJOR=0 -DYAML_VERSION_MINOR=2 -DYAML_VERSION_PATCH=5 '-DYAML_VERSION_STRING="0.2.5"' -DYAML_DECLARE_STATIC -I$Y/include -c $Y/src/$f.c -o build/cost/libyaml/$f.o
done
end=$(python3 -c 'import time;print(time.time())')
echo "libyaml 0.2.5: serial compile $(python3 -c "print(round($end-$start,1))") s"
ar rcs build/cost/libyaml.a build/cost/libyaml/*.o
ls -l build/cost/libyaml.a | awk '{print "  archive bytes: "$5}'
ls -l build/libfyaml/libfyaml.a | awk '{print "libfyaml 0.9.6 archive bytes (CMake Release): "$5}'
grep -c "Building C object" build-libfyaml.log | awk '{print "  libfyaml C objects built by CMake (all targets): "$1}'
ls -l rprobe/target/release/rprobe | awk '{print "rprobe (saphyr + yaml-rust2 + unsafe-libyaml + myna-values, thin LTO, debug=1) bytes: "$5}'
