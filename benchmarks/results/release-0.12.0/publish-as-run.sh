#!/usr/bin/env bash
# Stage by default. --publish signs, validates with Central, uploads to R2, then publishes Central.
#
# Prebuilt natives ship for linux-x86_64, linux-aarch64 and macos-aarch64 (docs/PUBLISHING.md). On an Apple silicon
# Mac this script builds all three: Linux amd64 and arm64 on BuildBuddy RBE (--config=remote, --config=remote_arm64)
# and macOS locally (--config=local). Elsewhere, set MACOS_NATIVES to a directory holding a macOS
# `bazel build --config=local //tools/release:natives --//tools/release:version=<version>` output tree
# (bazel-bin/tools/release from that build, for example a CI artifact).
set -euo pipefail
cd /Users/sam/workspace/graalvm/truffledata
version=${1:?usage: publish.sh VERSION [--publish]}
mode=${2:---stage}
[[ $# -le 2 && ( $mode == --stage || $mode == --publish ) ]] || { echo 'usage: publish.sh VERSION [--publish]' >&2; exit 1; }
python3 - "$version" <<'PY'
import sys
sys.path.insert(0, 'tools/release')
from maven_repo import validate_version
validate_version(sys.argv[1])
if 'SNAPSHOT' in sys.argv[1].upper():
    sys.exit('Use an explicit immutable release version, for example 0.1.0-alpha.1')
PY
bucket=${MAVEN_BUCKET:-elide:elide-maven}
public=https://maven.elide.dev
modules=(core jdbc jdbc-sqlite jdbc-mysql jdbc-postgresql protobuf protobuf-runtime protobuf-buffa protobuf-buffa-bindings cells protobuf-buffa-cells tape tape-jvm json json-bindings json-jvm json-cells yaml yaml-bindings yaml-native yaml-jvm yaml-java yaml-snakeyaml yaml-cells csv csv-bindings csv-native csv-jvm csv-cells bom)
bazel=${BAZEL:-$(command -v bazelisk || command -v bazel)}
flag=--//tools/release:version=$version
host=$(uname -s)-$(uname -m)
mkdir -p build
repo=$(mktemp -d "$PWD/build/release-repo.XXXXXX")
natives=$repo.natives
mkdir -p "$natives"
if [[ $host != Darwin-arm64 && -z ${MACOS_NATIVES:-} ]]; then
  echo 'The macos-aarch64 natives build locally on an Apple silicon Mac; elsewhere set MACOS_NATIVES (see the header).' >&2
  exit 1
fi
if [[ $mode == --publish ]]; then
  git rev-parse --verify HEAD > /dev/null
  [[ -z $(git status --porcelain) ]] || { echo 'Commit the release inputs before publishing.' >&2; exit 1; }
  # Listing failures (including auth/network failures) must never imply an absent version.
  artifacts=$(rclone lsf "$bucket/dev/elide/" --dirs-only)
  for module in "${modules[@]}"; do
    artifact=truffledata-$module
    if printf '%s\n' "$artifacts" | grep -Fxq "$artifact/"; then
      versions=$(rclone lsf "$bucket/dev/elide/$artifact/" --dirs-only)
      if printf '%s\n' "$versions" | grep -Fxq "$version/"; then
        echo "$artifact:$version already exists; versions are immutable." >&2; exit 1
      fi
      # Existing artifacts must have valid metadata; don't silently lose history.
      mkdir -p "$repo/dev/elide/$artifact"
      rclone copyto "$bucket/dev/elide/$artifact/maven-metadata.xml" "$repo/dev/elide/$artifact/maven-metadata.xml"
    fi
  done
fi
# Gates CI already ran on the release code (//..., x86-64 native lanes, JIT gate, musl tests) are skipped at
# Sam's instruction (2026-11-11). Kept: the per-platform consumer gates, which CI never runs.
platform_gates=(//tools/release:native_consumers //tools/release:buffa_consumer //tools/release:json_consumer //tools/release:yaml_consumer //tools/release:csv_kernel_consumer)
"$bazel" test --config=remote_arm64 "${platform_gates[@]}" "$flag"
[[ $host != Darwin-arm64 ]] || "$bazel" test --config=local "${platform_gates[@]}" "$flag"

# One platform's //tools/release:natives build, copied out of bazel-out before the next invocation.
collect() { # collect CLASSIFIER BAZEL_ARGS...
  local classifier=$1 target=//tools/release:natives
  [[ $classifier != *-musl ]] || target=//tools/release:musl_natives
  shift
  "$bazel" build "$@" "$target" "$flag"
  mkdir -p "$natives/$classifier"
  "$bazel" cquery "$@" "$target" "$flag" --output=files 2> /dev/null | while IFS= read -r file; do
    relative=${file#*/bin/tools/release/}
    mkdir -p "$natives/$classifier/$(dirname "$relative")"
    cp -L "$file" "$natives/$classifier/$relative"
  done
}
collect linux-x86_64 --config=remote
collect linux-aarch64 --config=remote_arm64
collect linux-x86_64-musl --config=remote_musl
collect linux-aarch64-musl --config=remote_arm64_musl
if [[ $host == Darwin-arm64 ]]; then
  collect macos-aarch64 --config=local
else
  cp -RL "$MACOS_NATIVES" "$natives/macos-aarch64"
fi
"$bazel" build //tools/release:publications "$flag"
python3 tools/release/maven_repo.py bazel-bin/tools/release "$repo" "$version" --natives "$natives"/*

# The release gate for the prebuilt natives, with the host's toolchain LLVM tools (they read ELF and Mach-O).
case $host in
  Darwin-arm64) toolchain=@elide_toolchain_darwin_arm64//:bin; sdk=graalvm_macos_aarch64 ;;
  Linux-x86_64) toolchain=@elide_toolchain_linux_amd64//:bin; sdk=graalvm_linux_amd64 ;;
  Linux-aarch64) toolchain=@elide_toolchain_linux_arm64//:bin; sdk=graalvm_linux_aarch64 ;;
  *) echo "No toolchain LLVM tools for $host" >&2; exit 1 ;;
esac
"$bazel" build --config=local "$toolchain" "@$sdk//:jdk"
llvm=$("$bazel" info --config=local execution_root 2> /dev/null)/$(dirname "$("$bazel" cquery --config=local "$toolchain" --output=files 2> /dev/null | grep '/bin/llvm-nm$')")
execution=$("$bazel" info --config=local execution_root 2> /dev/null)
java_file=$("$bazel" cquery --config=local "@$sdk//:bin-java-filegroup" --output=files 2> /dev/null)
JAVA_HOME=$(dirname "$(dirname "$execution/$java_file")") python3 tools/release/gradle_consumer.py --repository "$repo" --version "$version"
python3 tools/release/check_natives.py --llvm-bin "$llvm" --version "$version" --repository "$repo" --manifests "$natives"
bundles=$repo.bundles
revision=$(git rev-parse HEAD)
epoch=$(git show -s --format=%ct HEAD)
dirty=()
[[ -z $(git status --porcelain) ]] || dirty=(--dirty)
python3 tools/release/native_bundle.py --repository "$repo" --version "$version" --module csv-native \
  --header "$repo/dev/elide/truffledata-csv-native/$version/truffledata-csv-native-$version-native.h" \
  --llvm-bin "$llvm" --output "$bundles" --revision "$revision" --source-date-epoch "$epoch" ${dirty[@]+"${dirty[@]}"}
echo "Staged native bundles: $bundles"
echo "Staged Maven repository: $repo"
[[ $mode == --publish ]] || exit 0
# Validate a signed Central deployment before making the release discoverable.
# CENTRAL_CREDENTIALS_FILE may point to private JSON or Maven publishing settings XML.
python3 tools/release/central.py sign --repository "$repo" --version "$version"
# Central deferred under #28 (quota): no bundle upload, validation or publish.
# Run only one publisher at a time: R2/rclone has no multi-object transaction.
# Upload and verify all immutable payloads before making the version discoverable in metadata.
for module in "${modules[@]}"; do
  artifact=truffledata-$module
  path=dev/elide/$artifact/$version
  rclone copy "$repo/$path" "$bucket/$path" --immutable --checksum
  rclone check "$repo/$path" "$bucket/$path" --one-way
  for payload in "$repo/$path"/*; do
    case $payload in *.md5|*.sha1|*.sha256|*.sha512) continue ;; esac
    file=$(basename "$payload")
    curl --fail --silent --show-error "$public/$path/$file" -o "$repo/public-check"
    cmp "$repo/$path/$file" "$repo/public-check"
  done
done
for module in "${modules[@]}"; do
  path=dev/elide/truffledata-$module
  rclone copy "$repo/$path" "$bucket/$path" --include 'maven-metadata.xml*' --checksum
  rclone check "$repo/$path" "$bucket/$path" --include 'maven-metadata.xml*' --one-way
done
rm -f "$repo/public-check"
echo "Published dev.elide:truffledata-{core,jdbc,jdbc-sqlite,jdbc-mysql,jdbc-postgresql,protobuf,protobuf-runtime,protobuf-buffa,protobuf-buffa-bindings,cells,protobuf-buffa-cells,tape,tape-jvm,json,json-bindings,json-jvm,json-cells,yaml,yaml-bindings,yaml-native,yaml-jvm,yaml-java,yaml-snakeyaml,yaml-cells,csv,csv-bindings,csv-native,csv-jvm,csv-cells,bom}:$version at $public"
