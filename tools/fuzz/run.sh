#!/usr/bin/env bash
# Keep a shell parent so remote execution preserves diagnostics for signalled children.
set -uo pipefail
"$@"
status=$?
exit "$status"
