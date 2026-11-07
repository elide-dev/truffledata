#!/usr/bin/env bash
# release_archive.sh FORMAT OUTPUT MANIFEST CC BIN ARCHIVE...
#
# Merges static archives into one release archive whose members a consumer links with or without LTO
# (docs/superpowers/plans/2026-11-07-release-matrix-ledger.md, Task R1):
#
#   elf    every member that has LLVM bitcode becomes a fat object: machine code plus a ThinLTO-summarized
#          `.llvm.lto` section (clang -flto=thin -ffat-lto-objects). ld.lld reads that section under -flto=thin
#          (--fat-lto-objects) and links the machine code otherwise; other linkers drop it (SHF_EXCLUDE).
#   macho  Mach-O has no fat LTO object format (LLVM's EmbedBitcode pass is ELF/COFF only, and neither ld64 nor
#          ld64.lld reads __LLVM,__bitcode for LTO), so every member becomes machine code.
#
# Inputs carry bitcode: C/C++ objects under --features=thin_lto, Rust crates under -Clinker-plugin-lto (the
# release_archive transition). Per member:
#   raw bitcode, C/C++             compiled alone (one translation unit, one member)
#   raw bitcode, Rust (*.rcgu.o)   llvm-linked per input archive into one module and compiled once, so the machine
#                                  code keeps the cross-crate inlining rustc's -Clto=thin gives the default staticlib
#   ELF object with .llvm.lto      kept (already fat)
#   ELF object with rustc .llvmbc  (prebuilt std rlibs) its embedded bitcode compiled into a fat object;
#                                  compiler_builtins stays machine code without .llvmbc, as rustc never LTOs it
#   other machine code             kept (compiler-rt objects bundled by compiler_builtins)
#   foo.o beside foo.pic.o         dropped: cc_static_library collects both; the PIC twin serves every consumer
# MANIFEST lists each output member and its kind (fat or machine-code) for the release gate.
set -euo pipefail
format=$1 output=$2 manifest=$3 cc=$4 bin=$5
shift 5
case $format in
  elf) codegen=(-flto=thin -ffat-lto-objects) ar_format=gnu compiled=fat ;;
  macho) codegen=() ar_format=darwin compiled=machine-code ;;
  *) echo "release_archive: unknown object format $format" >&2; exit 1 ;;
esac
root=$PWD
abs() { case $1 in /*) echo "$1" ;; *) echo "$root/$1" ;; esac; }
cc=$(abs "$cc") bin=$(abs "$bin") output=$(abs "$output") manifest=$(abs "$manifest")
work=$output.work
rm -rf "$work" "$output"
mkdir -p "$work/out"
trap 'rm -rf "$work"' EXIT

compile() { # compile IR OBJECT
  "$cc" -O3 -fPIC -ffunction-sections -fdata-sections -Wno-override-module ${codegen[@]+"${codegen[@]}"} -x ir -c "$1" -o "$2"
}
is_bitcode() { case $(od -An -N4 -tx1 "$1" | tr -d ' \n') in 4243c0de | dec0170b) return 0 ;; *) return 1 ;; esac; }
sections() { "$bin/llvm-readobj" --sections "$1" | sed -n 's/^ *Name: \([^ ]*\) .*/\1/p'; }

names=() kinds=()
place() { # place FILE NAME KIND: add FILE as output member NAME (made unique across input archives)
  local name=$2 n=1
  while [[ -e $work/out/$name ]]; do name=$n-$2; n=$((n + 1)); done
  mv "$1" "$work/out/$name"
  names+=("$name")
  kinds+=("$3")
}

index=0
for archive in "$@"; do
  index=$((index + 1))
  archive=$(abs "$archive")
  dir=$work/in$index
  mkdir -p "$dir"
  listing=$("$bin/llvm-ar" t "$archive")
  rust=() rust_slot=-1 seen= position=0
  while IFS= read -r name; do
    # Members may share a name (rapidyaml's and c4core's version.o): extract each instance on its own.
    occurrence=$(($(grep -Fxc "$name" <<< "$seen" || true) + 1))
    seen+=$name$'\n'
    position=$((position + 1))
    if [[ $name == *.o && $name != *.pic.o ]] && (($(grep -Fxc "${name%.o}.pic.o" <<< "$listing" || true) >= occurrence)); then
      continue
    fi
    mkdir "$dir/$position"
    (cd "$dir/$position" && "$bin/llvm-ar" xN "$occurrence" "$archive" "$name")
    member=$dir/$position/$name
    if is_bitcode "$member"; then
      if [[ $name == *.rcgu.o ]]; then
        rust+=("$member")
        if ((rust_slot < 0)); then rust_slot=${#names[@]}; names+=(""); kinds+=("$compiled"); fi
      else
        compile "$member" "$member.out"
        place "$member.out" "$name" "$compiled"
      fi
    elif [[ $format == macho ]]; then
      if sections "$member" | grep -qx '__bitcode'; then
        "$bin/llvm-bitcode-strip" -r "$member" -o "$member.out"
        mv "$member.out" "$member"
      fi
      place "$member" "$name" machine-code
    else
      secs=$(sections "$member")
      if grep -qx '.llvm.lto' <<< "$secs"; then
        place "$member" "$name" fat
      elif grep -qx '.llvmbc' <<< "$secs" && [[ $name != *compiler_builtins* ]]; then
        "$bin/llvm-objcopy" --dump-section=.llvmbc="$member.bc" "$member"
        compile "$member.bc" "$member.out"
        place "$member.out" "$name" fat
      else
        if grep -qx '.llvmbc' <<< "$secs"; then
          "$bin/llvm-objcopy" --remove-section=.llvmbc --remove-section=.llvmcmd "$member"
        fi
        place "$member" "$name" machine-code
      fi
    fi
  done <<< "$listing"
  if ((rust_slot >= 0)); then
    name=$(basename "$archive" .a).rust.o
    [[ ! -e $work/out/$name ]] || name=$index-$name
    "$bin/llvm-link" "${rust[@]}" -o "$dir/rust.bc"
    compile "$dir/rust.bc" "$work/out/$name"
    names[rust_slot]=$name
  fi
  rm -rf "$dir"
done
(cd "$work/out" && "$bin/llvm-ar" rcsD --format="$ar_format" "$output" "${names[@]}")
for i in "${!names[@]}"; do printf '%s\t%s\n' "${names[$i]}" "${kinds[$i]}"; done > "$manifest"
