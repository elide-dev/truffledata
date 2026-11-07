#!/usr/bin/env bash
# Build reproducible licensed publication jars using the selected JDK.
set -euo pipefail
kind=$1 output=$2 jdk=$3 source=$4 classpath=$5
shift 5
root=$PWD
case $output in /*) ;; *) output=$root/$output ;; esac
case $jdk in /*) ;; *) jdk=$root/$jdk ;; esac
case $source in /*) ;; *) source=$root/$source ;; esac
work=$output.work
mkdir -p "$work/META-INF"
trap 'rm -rf "$work"' EXIT
for license in "$@"; do cp "$license" "$work/META-INF/$(basename "$license")"; done
if [[ $kind == javadoc ]]; then
  mkdir -p "$work/src" "$work/docs"
  (cd "$work/src" && "$jdk/bin/jar" --extract --file "$source")
  find "$work/src" -name '*.java' -type f | LC_ALL=C sort > "$work/sources"
  "$jdk/bin/javadoc" -quiet -Xdoclint:none -notimestamp --source 25 -classpath "$classpath" -d "$work/docs" @"$work/sources"
  cp -R "$work/META-INF" "$work/docs/"
  "$jdk/bin/jar" --create --file "$output" --date=1980-01-01T00:00:02Z -C "$work/docs" .
else
  cp "$source" "$output"
  "$jdk/bin/jar" --update --file "$output" --date=1980-01-01T00:00:02Z -C "$work" META-INF
fi
