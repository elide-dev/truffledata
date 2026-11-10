#!/usr/bin/env bash
# Flag-off shape of the YAML images. Usage: flag_off_image_test.sh BASELINE CELLS_OFF CELLS_ON.
# A Native Image keeps the name of every type it contains, so a type name that is absent from the
# binary is a type the image does not have.
set -euo pipefail
cd "${TEST_SRCDIR}/${TEST_WORKSPACE}"
baseline="$1" off="$2" on="$3"

count() { grep -a -c -F -e "$2" "$1" || true; }

# The route's selector, the route's classes, the generated cells and the myna-truffle runtime.
names=(
  "dev.elide.truffledata.yaml.SvmCells"
  "dev.elide.truffledata.yaml.CellsSvm"
  "dev.elide.truffledata.yaml.cells.generated"
  "dev.elide.myna.truffle"
)

status=0
for name in "${names[@]}"; do
  present=$(count "$on" "$name")
  echo "flag on:  $name: $present"
  if [ "$present" -eq 0 ]; then
    echo "FAIL: the image built with the flag does not name $name: the scan cannot tell" >&2
    status=1
  fi
  for image in "$baseline" "$off"; do
    present=$(count "$image" "$name")
    echo "flag off: $(basename "$image"): $name: $present"
    if [ "$present" -ne 0 ]; then
      echo "FAIL: $(basename "$image") was built without the flag but names $name" >&2
      status=1
    fi
  done
done

# Informational: section sizes of the two flag-off images (class path and Feature differ).
if command -v size >/dev/null 2>&1; then
  for image in "$baseline" "$off" "$on"; do
    echo "sections of $(basename "$image"):"
    size -A "$image" | grep -E '^\.(text|svm_heap|rodata) ' || true
  done
fi

# The flag-off image with the cells artifact present runs the YAML native acceptance on the static backend.
"$off"
exit "$status"
