#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "$0")/.."

if [[ $# -ne 4 || "$1" != "--apply" ]]; then
    echo "Uso: --apply APP_ROLE OWNER_ROLE SCHEMA" >&2
    exit 2
fi

for nome in PGHOST PGPORT PGDATABASE PGUSER BEVANS_CONFIRMAR_DB; do
    if [[ -z "${!nome:-}" ]]; then
        echo "BLOQUEADO: variável $nome obrigatória." >&2
        exit 2
    fi
done

if [[ "$PGDATABASE" != "$BEVANS_CONFIRMAR_DB" ]]; then
    echo "BLOQUEADO: banco não confirmado." >&2
    exit 2
fi

psql -X -v ON_ERROR_STOP=1 --single-transaction \
    -h "$PGHOST" -p "$PGPORT" \
    -U "$PGUSER" -d "$PGDATABASE" \
    -v "app_role=$2" \
    -v "owner_role=$3" \
    -v "schema_name=$4" \
    -f scripts/sql/grant-app-permissions.sql

echo "Privilégios aplicados com sucesso."
