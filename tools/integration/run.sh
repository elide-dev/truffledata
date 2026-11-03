#!/usr/bin/env bash
# Reproducible real-driver acceptance with disposable local Docker services.
set -euo pipefail
cd "$(dirname "$0")/../.."
work=$(mktemp -d "${TMPDIR:-/tmp}/truffledata-services.XXXXXX")
prefix=$(basename "$work")
pg_id=''
mysql_id=''
cleanup() {
  status=$?
  if [[ $status != 0 ]]; then
    [[ -z $pg_id ]] || docker logs "$pg_id" > "$work/postgresql.log" 2>&1 || true
    [[ -z $mysql_id ]] || docker logs "$mysql_id" > "$work/mysql.log" 2>&1 || true
    echo "Database diagnostics: $work" >&2
  fi
  [[ -z $pg_id ]] || docker rm -f "$pg_id" >/dev/null 2>&1 || true
  [[ -z $mysql_id ]] || docker rm -f "$mysql_id" >/dev/null 2>&1 || true
  [[ $status != 0 ]] || rm -rf "$work"
  exit "$status"
}
trap cleanup EXIT
pg_id=$(docker run -d --name "$prefix-pg" -e POSTGRES_PASSWORD=truffledata -e POSTGRES_DB=truffledata \
  -p 127.0.0.1::5432 postgres:18@sha256:3a4264520e121176619f54ff867d12774bc81e95e7b84db58d63bccdb6144b81)
mysql_id=$(docker run -d --name "$prefix-mysql" -e MYSQL_ROOT_PASSWORD=truffledata -e MYSQL_DATABASE=truffledata \
  -p 127.0.0.1::3306 mysql:8.4@sha256:a0225d1ea6631c8bc2385dc4c2faba17bbf1834ecf369e1659a9a382bd8d5dd7)
ready=0
for ((attempt=0; attempt<60; attempt++)); do
  if docker exec "$pg_id" psql -h 127.0.0.1 -U postgres -d truffledata -Atc 'select 1' >/dev/null 2>&1 \
    && docker exec "$mysql_id" mysql -h 127.0.0.1 -uroot -ptruffledata truffledata -Nse 'select 1' >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
[[ $ready == 1 ]] || { echo 'Database readiness timed out' >&2; exit 1; }
pg_port=$(docker port "$pg_id" 5432/tcp)
mysql_port=$(docker port "$mysql_id" 3306/tcp)
export TRUFFLEDATA_POSTGRESQL_URL="jdbc:postgresql://127.0.0.1:${pg_port##*:}/truffledata?user=postgres&password=truffledata"
export TRUFFLEDATA_MYSQL_URL="jdbc:mysql://127.0.0.1:${mysql_port##*:}/truffledata?user=root&password=truffledata&allowPublicKeyRetrieval=true&sslMode=DISABLED"
profile=()
# Remote builds target Linux; local macOS tests need the macOS Java toolchain.
[[ $(uname -s) != Darwin ]] || profile=(--config=local)
"${BAZEL:-bazel}" test "${profile[@]}" //tests:drivers //benchmarks/jdbc:drivers_smoke --build_runfile_links --remote_download_toplevel --test_strategy=standalone --strategy=TestRunner=local \
  --test_env=TRUFFLEDATA_MYSQL_URL --test_env=TRUFFLEDATA_POSTGRESQL_URL
