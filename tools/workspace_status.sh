#!/usr/bin/env bash
# Bazel workspace status (--workspace_status_command, .bazelrc): repository annotations BuildBuddy shows on every
# invocation (repo URL, commit, branch, tree status), after BuildBuddy's own workspace_status.sh. Credentials in the remote
# URL are stripped. Values are volatile (no STABLE_ keys), so they never invalidate actions.
set -eo pipefail
if ! git rev-parse --git-dir > /dev/null 2>&1; then
  echo "COMMIT_SHA unknown"
  echo "GIT_BRANCH unknown"
  echo "GIT_TREE_STATUS Unknown"
  exit 0
fi
repo_url=$(git config --get remote.origin.url || true)
repo_url=$(printf '%s' "$repo_url" | sed -E 's#(https?://)[^/@]*@#\1#')
echo "REPO_URL $repo_url"
echo "COMMIT_SHA $(git rev-parse HEAD)"
# Empty on a detached HEAD (CI checkouts): CI supplies the branch itself.
echo "GIT_BRANCH ${GITHUB_HEAD_REF:-${GITHUB_REF_NAME:-$(git branch --show-current)}}"
echo "GIT_TREE_STATUS $(git diff-index --quiet HEAD -- && echo Clean || echo Modified)"
