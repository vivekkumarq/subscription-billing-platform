#!/bin/sh
# Runs once, on first initialisation of the Postgres data volume.
# Keycloak keeps its own state, so it gets its own database on the same server.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE keycloak;
    GRANT ALL PRIVILEGES ON DATABASE keycloak TO "$POSTGRES_USER";
EOSQL
