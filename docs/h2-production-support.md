# H2 as a Production Database (Single-Node / Embedded)

## Status

Implemented. This document records the design decisions and the concrete changes
that let the Race Results Repository run on a persistent H2 database for
single-node / embedded deployments, alongside the existing MariaDB support.

## Motivation

The application is designed for MariaDB, but a single-node / embedded install
(e.g. a self-contained appliance at a regatta with no separate database server)
benefits from an in-process, zero-administration database. H2 fits that use case.

## Decisions

| Decision | Choice |
|---|---|
| Deployment shape | Single-node / embedded only |
| Schema management | Flyway with **per-vendor** migrations |
| H2 typing | **Native H2 types** (no `MODE=MySQL` compatibility mode) |
| Persistence | **File-based** H2 (`jdbc:h2:file:...`) |
| Schema authority | Flyway owns the schema; Hibernate runs `ddl-auto=validate` |

H2 is **not** supported for multi-instance / high-availability deployments — it
is a single-writer engine. Use MariaDB for those.

## What made this feasible

The application layer was already database-agnostic:

- All repository queries are **JPQL** (no `nativeQuery=true`, no `FULLTEXT` /
  `MATCH ... AGAINST`).
- Primary keys use `GenerationType.IDENTITY`, supported by both engines.
- Backups (`BackupService`) serialize entities to JSON via JPA — no `mysqldump`
  or engine-specific dump logic.

The only database-specific coupling was in (a) three entity `columnDefinition`s
and (b) the single Flyway migration, which was written in MariaDB dialect.

## Changes

### 1. Portable entity mappings

Removed vendor-specific `columnDefinition`s so Hibernate no longer emits
MySQL-only type names (which previously forced H2 into MySQL compatibility mode):

| Entity | Field | Before | After |
|---|---|---|---|
| `Version` | `model_snapshot` | `@Lob columnDefinition="LONGBLOB"` | `@Lob` (dialect chooses `BLOB` / `longblob`) |
| `DocumentMetadata` | `value` | `columnDefinition="TEXT"` | `@Column(length = 4000)` (portable `VARCHAR`) |
| `WebhookSubscription` | `events` | `columnDefinition="JSON"` | `@Column(length = 2000)` (JSON stored as text) |

Notes:
- `meta_value` is a bounded `VARCHAR` so it can participate in the
  `(meta_key, meta_value)` search index. On MariaDB the composite index uses a
  `meta_value(255)` prefix (InnoDB key-length limit); H2 has no such limit.
- `events` holds a short JSON array of event names; a `VARCHAR` is sufficient and
  avoids binding to a vendor JSON type.

### 2. Per-vendor Flyway migrations

Flyway's `{vendor}` location placeholder resolves to the connected engine, so
each database gets its own migration set:

```
src/main/resources/db/migration/
├── mariadb/V1__initial_schema.sql   # InnoDB, utf8mb4, prefix indexes, LONGBLOB
└── h2/V1__initial_schema.sql        # native H2: BLOB, VARCHAR, CREATE INDEX
```

The H2 migration differs from MariaDB only where the dialects differ:
- `BLOB` instead of `LONGBLOB`; `VARCHAR` instead of `TEXT` / `JSON`.
- Indexes are separate `CREATE INDEX` statements (H2 has no inline `INDEX`).
- No `ENGINE` / `CHARSET` / `COLLATE` clauses.
- The `(meta_key, meta_value)` index has no prefix length.

Both migrations seed the same default ACL rows.

Flyway `locations` were changed from `classpath:db/migration` to
`classpath:db/migration/{vendor}` in `application.properties` (MariaDB) and
`application-dev.yml` (H2 dev). This also fixes the previously broken `dev`
profile, which had been pointed at the MariaDB-only migration.

### 3. Physical naming strategy

The entities use explicit `@Column` names that are a deliberate mix of camelCase
(`regattaId`, `documentType`, `timerId`, `milestoneId`, `versionType`) and
snake_case (`document_id`, `created_at`, ...), and the Flyway migrations were
written to match those names verbatim. Spring Boot's default
`CamelCaseToUnderscoresNamingStrategy` rewrites the camelCase names to snake_case
(`regattaId` → `regatta_id`), so `ddl-auto=validate` fails with e.g.
`missing column [regatta_id] in table [acl_entries]`.

`application.properties` now sets:

```
spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
```

so `@Column` names are used as physical column names. This is a global setting
(applies to MariaDB, H2, and the create-drop test/openapi profiles) and fixes a
pre-existing latent bug: `validate` against a Flyway-managed schema had never
actually succeeded on MariaDB either, because tests use `create-drop`.

(Hibernate canonicalizes unquoted identifiers to lower case when validating, so
the mixed-case names still match regardless of how H2 or MariaDB stores them.)

### 4. Production H2 profile

`application-prod-h2.yml` (activate with `--spring.profiles.active=prod-h2`):

- File-based H2: `jdbc:h2:file:${DB_PATH}` with `AUTO_SERVER=TRUE` and
  `DATABASE_TO_LOWER=TRUE` (native mode — no `MODE=MySQL`).
- `ddl-auto=validate` with Flyway managing the schema.
- Credentials and DB path via environment variables (`DB_USER`, `DB_PASSWORD`,
  `DB_PATH`).
- H2 web console disabled.

## How to run

```bash
# Build
mvn clean package

# Run against a persistent H2 database
DB_PATH=/var/lib/race-results/db/race_results \
DB_USER=sa \
DB_PASSWORD='change-me' \
java -jar target/race-results-repository-*.jar --spring.profiles.active=prod-h2
```

MariaDB remains the default (run with no profile, or `prod`-style config).

## Profile / database matrix

| Profile | Database | Schema strategy | Purpose |
|---|---|---|---|
| (default) | MariaDB | Flyway `mariadb` + `validate` | Production (server DB) |
| `prod-h2` | H2 file | Flyway `h2` + `validate` | Production (embedded) |
| `dev` | H2 mem | Flyway `h2` + `validate` | Local development |
| `openapi` | H2 mem | Hibernate `create-drop` | OpenAPI spec export |
| `test` | H2 mem (default) or MariaDB | Flyway `{vendor}` + `validate` | Automated tests / CI drift matrix |

## Operational guidance

- **Backups**: the app-level JSON backup (`BackupService`) is engine-agnostic and
  works as-is. Additionally back up the H2 files (or use H2's `SCRIPT` command)
  for a physical restore path.
- **Concurrency**: H2 is single-writer. Keep the Hikari pool small; do not run
  multiple app instances against the same file database.
- **Console**: never enable the H2 web console in production.
- **Search**: current search uses portable `LIKE` queries. If MariaDB `FULLTEXT`
  search is added later, it will not port to H2 and would need an H2-specific
  full-text approach.

## Trade-offs and ongoing costs

- **Migration drift**: every future schema change must be authored for *both*
  `db/migration/mariadb` and `db/migration/h2`. This is guarded by CI (see below).
- **`meta_value` capacity** was reduced from `TEXT` (~64 KB) to `VARCHAR(4000)`
  to keep the column indexable across both engines. Revisit if larger metadata
  values are required.

## CI: drift detection across both databases

`.github/workflows/ci.yml` runs the full test suite in a matrix against **both**
engines:

| Axis | Database | Migrations exercised |
|---|---|---|
| `h2` | in-memory H2 | `db/migration/h2` |
| `mariadb` | MariaDB 11 (container) | `db/migration/mariadb` |

This is possible because the `test` profile
(`src/test/resources/application-test.properties`) now:

- boots with `spring.flyway.enabled=true` + `ddl-auto=validate` (instead of the
  previous `create-drop` with Flyway off), so the entities are validated against
  the schema built by the real migrations; and
- takes its datasource from `TEST_DB_*` environment variables, defaulting to
  in-memory H2 so local `mvn test` needs no external services.

The Flyway `{vendor}` placeholder selects the matching migration directory from
the JDBC connection, so each axis validates its own migration set. If a future
schema change is applied to only one vendor's migration (or diverges from the
entities), the corresponding axis fails `validate` and the build goes red.

Run the MariaDB axis locally against a throwaway container:

```bash
docker run -d --name mariadb -p 3306:3306 \
  -e MARIADB_DATABASE=race_results_test -e MARIADB_USER=race_test \
  -e MARIADB_PASSWORD=race_test_pw -e MARIADB_ROOT_PASSWORD=root_pw mariadb:11

TEST_DB_URL='jdbc:mariadb://127.0.0.1:3306/race_results_test' \
TEST_DB_DRIVER=org.mariadb.jdbc.Driver \
TEST_DB_USER=race_test TEST_DB_PASSWORD=race_test_pw \
TEST_DB_DIALECT=org.hibernate.dialect.MariaDBDialect \
mvn -B verify
```

## Compatibility note for existing MariaDB deployments

The MariaDB migration moved to `db/migration/mariadb/` and its `meta_value` /
`events` column types changed (`TEXT`→`VARCHAR(4000)`, `JSON`→`VARCHAR(2000)`),
which changes the Flyway checksum for `V1`. As the project is pre-1.0
(`1.0.0-SNAPSHOT`) this is assumed to predate any real deployment. An existing
MariaDB instance would need `flyway repair` (to re-align the checksum) or a fresh
baseline.

## Follow-up / verification

The build could not be compiled or run in the authoring environment (no network
for Maven dependencies). Before relying on this, run:

```bash
mvn verify                                   # tests (H2 create-drop)
mvn spring-boot:run -Dspring-boot.run.profiles=prod-h2   # boots on file H2 + validate
```

Confirm the app starts under `prod-h2` (Flyway applies `h2/V1`, Hibernate
`validate` passes) and that basic CRUD works.
