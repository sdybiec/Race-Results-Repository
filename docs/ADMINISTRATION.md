# Race Results Repository — Administration Guide

Operational guide for deploying, configuring, securing, and maintaining the Race
Results Repository service. For local development see
[../DEVELOPMENT.md](../DEVELOPMENT.md); for the H2 single-node design see
[h2-production-support.md](h2-production-support.md).

- [1. Deployment topologies](#1-deployment-topologies)
- [2. Configuration reference](#2-configuration-reference)
- [3. Deploying the service](#3-deploying-the-service)
- [4. Database administration](#4-database-administration)
- [5. Backup & restore](#5-backup--restore)
- [6. Security administration](#6-security-administration)
- [7. Notifications (MQTT & webhooks)](#7-notifications-mqtt--webhooks)
- [8. Rate limiting](#8-rate-limiting)
- [9. Monitoring & logging](#9-monitoring--logging)
- [10. Troubleshooting](#10-troubleshooting)
- [11. Upgrades & schema migrations](#11-upgrades--schema-migrations)
- [12. Production hardening checklist](#12-production-hardening-checklist)

---

## 1. Deployment topologies

| Topology | Database | Profile | When to use |
|---|---|---|---|
| Multi-instance / HA | MariaDB 10.6+ | _(default, no profile)_ | Shared server DB, multiple app nodes, high availability |
| Single-node / embedded | H2 (file) | `prod-h2` | Appliance / on-site single server, no separate DB to operate |

H2 is a **single-writer** engine: run exactly one application instance against a
given H2 file. For more than one node, use MariaDB.

---

## 2. Configuration reference

Configuration comes from `src/main/resources/application.properties` (the
default/MariaDB profile) plus profile overlays (`application-<profile>.yml`).
**Override anything with environment variables or `--key=value` arguments** —
never edit secrets into the packaged jar.

### Profiles

| Profile | Database | MQTT broker | Purpose |
|---|---|---|---|
| _(default)_ | MariaDB | External | Production (multi-instance) |
| `prod-h2` | H2 file (persistent) | External (configurable) | Production (single-node/embedded) |
| `embedded-prod` | (default DB) | Embedded, persistent | Single-server with in-process broker |
| `dev` | H2 in-memory | Embedded | Local development (+ dev token endpoint) |
| `openapi` | H2 in-memory | Disabled | Export the static OpenAPI spec |
| `test` | H2 in-memory (or MariaDB) | Disabled | Automated tests / CI |

Activate with `--spring.profiles.active=<profile>` or `SPRING_PROFILES_ACTIVE`.

### Key properties

| Property | Default | Notes |
|---|---|---|
| `spring.datasource.url` | `jdbc:mariadb://localhost:3306/race_results` | JDBC URL |
| `spring.datasource.username` / `.password` | `race_results_user` / _placeholder_ | **Override in production** |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Schema owned by Flyway; Hibernate only validates |
| `spring.flyway.locations` | `classpath:db/migration/{vendor}` | `{vendor}` → `mariadb` or `h2` |
| `jwt.secret` | placeholder | **Must be a strong ≥256-bit key; override in production** |
| `jwt.expiration` | `86400000` (24h) | Access token lifetime (ms) |
| `jwt.refresh-expiration` | `604800000` (7d) | Refresh token lifetime (ms) |
| `mqtt.broker.url` | `tcp://localhost:1883` | External broker |
| `mqtt.embedded.enabled` | `false` | Embedded Moquette broker |
| `webhook.retry.max-attempts` | `5` | Delivery retries |
| `webhook.timeout` | `5000` | Per-delivery timeout (ms) |
| `rate.limit.read.requests` | `100` / 60s | See [§8](#8-rate-limiting) |
| `rate.limit.write.requests` | `20` / 60s | |
| `rate.limit.search.requests` | `30` / 60s | |
| `backup.directory` | `/var/backups/race-results` | Backup output dir |
| `backup.retention.days` | `30` | Retention window |
| `backup.schedule.full` | `0 0 2 * * *` | Daily 02:00 |
| `backup.schedule.incremental` | `0 0 */4 * * *` | Every 4 hours |

### Secrets to externalize

At minimum set these via environment in production:

```bash
export SPRING_DATASOURCE_USERNAME=...
export SPRING_DATASOURCE_PASSWORD=...
export JWT_SECRET=...          # maps to jwt.secret; must match the token issuer
```

> The default `jwt.secret` and DB password in `application.properties` are
> placeholders and must never be used in production.

---

## 3. Deploying the service

### Build

```bash
mvn clean package
# produces target/race-results-repository-1.0.0-SNAPSHOT.jar
```

### MariaDB (default topology)

1. Create the database and a least-privilege user:
   ```sql
   CREATE DATABASE race_results CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'race_results_user'@'%' IDENTIFIED BY 'STRONG_PASSWORD';
   GRANT ALL PRIVILEGES ON race_results.* TO 'race_results_user'@'%';
   FLUSH PRIVILEGES;
   ```
2. Run (Flyway applies `db/migration/mariadb` automatically on startup):
   ```bash
   SPRING_DATASOURCE_URL='jdbc:mariadb://db-host:3306/race_results' \
   SPRING_DATASOURCE_USERNAME=race_results_user \
   SPRING_DATASOURCE_PASSWORD='STRONG_PASSWORD' \
   JWT_SECRET='<256-bit-secret>' \
   java -jar race-results-repository-*.jar
   ```

### H2 (single-node / embedded topology)

```bash
DB_PATH=/var/lib/race-results/db/race_results \
DB_USER=sa DB_PASSWORD='STRONG_PASSWORD' \
JWT_SECRET='<256-bit-secret>' \
java -jar race-results-repository-*.jar --spring.profiles.active=prod-h2
```

- Store the DB under a persistent, backed-up path (`DB_PATH`).
- Ensure only the service account can read the H2 files (they contain all data).
- The H2 web console is disabled in this profile; keep it disabled.

The service listens on port `8080` (override with `SERVER_PORT`).

---

## 4. Database administration

### Migrations (Flyway, per-vendor)

Migrations live under `src/main/resources/db/migration/{mariadb,h2}` and are
applied automatically at startup. The `{vendor}` placeholder selects the set
matching the live connection.

```bash
mvn flyway:info      # show applied / pending migrations
mvn flyway:migrate   # apply pending migrations manually
mvn flyway:validate  # verify checksums against the DB history
```

`mvn` Flyway goals use the default datasource; point them at the target DB with
`-Dflyway.url=... -Dflyway.user=... -Dflyway.password=...` and
`-Dflyway.locations=classpath:db/migration/mariadb` (or `.../h2`).

### Schema validation

The app runs with `ddl-auto=validate`: on startup Hibernate verifies the entities
match the migrated schema and **refuses to start on a mismatch**. Column names
come from the entities' `@Column` values verbatim
(`PhysicalNamingStrategyStandardImpl`), so any new migration must use those exact
names. See [§10](#10-troubleshooting) for the typical validation error.

### Physical database backups

The application backup API ([§5](#5-backup--restore)) captures document/version
data. For a full physical backup of the database itself:

- **MariaDB:** `mysqldump --single-transaction race_results > dump.sql`
- **H2:** stop the app (or use `AUTO_SERVER`) and copy the `*.mv.db` file, or run
  H2's `SCRIPT TO 'backup.sql'` command.

---

## 5. Backup & restore

The service provides an application-level backup subsystem (document + version
data, GZIP-compressed) with a manifest per backup. All endpoints require the
**`REGATTA_ADMIN`** role.

### Scheduled backups

Enabled by default via cron expressions (see [§2](#2-configuration-reference)):
full daily at 02:00, incremental every 4 hours, into `backup.directory`, retained
for `backup.retention.days`. Ensure `backup.directory` exists, is writable by the
service account, and is itself backed up off-box.

### On-demand API

| Method & path | Action |
|---|---|
| `POST /api/v1/backups/full` | Create a full backup |
| `POST /api/v1/backups/incremental` | Create an incremental backup |
| `GET /api/v1/backups` | List backups (manifests) |
| `POST /api/v1/backups/{backupId}/restore/full` | Restore all data (**replaces** current data) |
| `POST /api/v1/backups/{backupId}/restore/selective` | Restore specific document IDs (JSON body: `[1,2,3]`) |
| `POST /api/v1/backups/restore/point-in-time?targetTime=ISO_DATETIME` | Restore to a point in time |
| `DELETE /api/v1/backups/{backupId}` | Delete a backup |

Example:

```bash
curl -X POST http://host:8080/api/v1/backups/full \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

> **Restore is destructive.** A full restore replaces existing data — take a
> fresh backup first and perform restores during a maintenance window.

---

## 6. Security administration

### Authentication model

- Stateless JWT bearer auth; no server sessions.
- Public (unauthenticated) paths: `/api-docs/**`, `/swagger-ui/**`,
  `/swagger-ui.html`, `/actuator/**`. Everything else — including the Hessian RPC
  endpoint `/hessian/repository` — requires a valid token.
- The `Using generated security password:` line Spring Boot logs at startup is a
  framework default the app does **not** use; ignore it.

### Tokens

Tokens are HMAC-signed with `jwt.secret` and carry: `sub` (userId), `username`,
`roles`, `regattaIds`. There is **no production login endpoint** — tokens are
expected from an external authentication service that shares `jwt.secret`.

- **Rotating `jwt.secret`:** all existing tokens become invalid immediately
  (signature mismatch). Coordinate rotation with the token issuer and expect
  clients to re-authenticate.
- A **dev-only** minting endpoint `POST /api/v1/auth/token` exists but is gated to
  the `dev` profile (`@Profile("dev")`) and must never be enabled in production.

### Roles & authorization (ACL)

Three roles: `REGATTA_ADMIN`, `TIMER`, `VIEWER`. Authorization is enforced per
regatta, resource type, and operation using rows in the `acl_entries` table,
which is seeded by the initial migration with the standard permission matrix
(admins full control; timers create/update their own race results; viewers
read/subscribe). To customize permissions, manage `acl_entries` rows (a token's
`regattaIds` may include `*` for all regattas).

---

## 7. Notifications (MQTT & webhooks)

### MQTT

- **External broker (recommended for multi-node):** set `mqtt.broker.url` and
  keep `mqtt.embedded.enabled=false`. The client auto-reconnects; if the broker
  is unavailable the app logs warnings and continues (notifications are skipped).
- **Embedded broker (single-server):** use the `embedded-prod` profile (Moquette,
  persistent, authentication required). See `application-embedded.yml`.
- **Topics:** `regatta/{regattaId}/startlist` and
  `regatta/{regattaId}/results/{timerId}`; subscribe to a whole regatta with
  `regatta/{regattaId}/results/#`.
- Clients discover connection details via `GET /api/v1/mqtt/info`.

### Webhooks

| Method & path | Action |
|---|---|
| `POST /api/v1/webhooks/subscribe` | Create a subscription (per-document or per-regatta) |
| `GET /api/v1/webhooks` | List subscriptions |
| `DELETE /api/v1/webhooks/{id}` | Remove a subscription |

Delivery uses exponential backoff (`2s → 16s`, up to `webhook.retry.max-attempts`)
and signs each payload with HMAC-SHA256 using the subscription's secret.
Subscriptions are **auto-deactivated after 10 consecutive failures** — monitor
logs for endpoints that go silent and have subscribers re-create them.

---

## 8. Rate limiting

A sliding-window limiter protects the API. Defaults (requests / seconds):

| Class | Limit |
|---|---|
| Reads | 100 / 60s |
| Writes | 20 / 60s |
| Searches | 30 / 60s |

Tune via `rate.limit.{read,write,search}.{requests,duration}`. Raise limits for
trusted internal clients; over-tight limits surface as HTTP 429 to callers.

---

## 9. Monitoring & logging

### Health/metrics endpoints (action required)

> **Note:** `spring-boot-starter-actuator` is **not currently a dependency**, so
> `/actuator/health`, `/actuator/metrics`, etc. return 404 even though the
> security config already permits `/actuator/**`. To enable operational
> monitoring, add the starter and expose the endpoints:
>
> ```xml
> <dependency>
>   <groupId>org.springframework.boot</groupId>
>   <artifactId>spring-boot-starter-actuator</artifactId>
> </dependency>
> ```
> ```properties
> management.endpoints.web.exposure.include=health,info,metrics,flyway
> management.endpoint.health.show-details=when_authorized
> ```
> Until then, use process/HTTP liveness checks (e.g. a `200` from
> `/swagger-ui.html`) and log-based monitoring.

### Logging

- Default levels: root `INFO`, `org.rowtown.rms.rrr` `DEBUG`. Lower the app
  package to `INFO` in production to reduce noise
  (`logging.level.org.rowtown.rms.rrr=INFO`).
- `spring.jpa.show-sql` is `false` in the default profile — keep it off in
  production.
- Route logs to your aggregator; watch for: Flyway/validation failures at
  startup, repeated MQTT reconnect warnings, and webhook auto-deactivation.

---

## 10. Troubleshooting

| Symptom | Cause & fix |
|---|---|
| Startup fails: `Schema-validation: missing column [...]` | A migration doesn't match the entities. Column names must equal the `@Column` values exactly; fix the vendor migration (both `mariadb` and `h2`). |
| Startup fails: `Migration V... failed` (H2) | MariaDB-only SQL ran on H2. Ensure the H2 migration under `db/migration/h2` uses native H2 types; `{vendor}` must resolve correctly. |
| Startup fails: Flyway checksum mismatch | A previously applied migration file changed. Run `mvn flyway:repair` against that DB, then restart. |
| Startup fails instantiating `hessianServlet` | Hessian must receive the Spring bean (already configured). If reintroduced, don't use `home-class` init params with a constructor-injected service. |
| Every request returns 401 | Missing/invalid `Authorization: Bearer <token>`, or `jwt.secret` differs from the issuer's. Verify the secret and token expiry. |
| `/actuator/health` returns 404 | Actuator not on the classpath — see [§9](#9-monitoring--logging). |
| MQTT warnings in logs | Broker unreachable. The app continues without notifications; fix `mqtt.broker.url` or start the broker. |
| HTTP 429 responses | Rate limit exceeded — see [§8](#8-rate-limiting). |

Enable a full startup diagnostic with `--debug` to print the condition
evaluation report.

---

## 11. Upgrades & schema migrations

1. **Back up first** (physical DB dump + an app-level full backup).
2. Deploy the new jar. Flyway applies any new `V{n}` migrations on startup;
   `validate` guarantees the entities match afterwards.
3. Every schema change ships as **paired** migrations under
   `db/migration/mariadb` and `db/migration/h2` with the same version number.
   The CI matrix (`.github/workflows/ci.yml`) runs the suite against both engines
   to catch drift before release.
4. For zero-downtime on MariaDB multi-node, use backward-compatible migrations
   (add columns/tables before removing) and roll instances gradually.
5. **Rollback:** restore the pre-upgrade backup. Flyway does not auto-revert;
   forward-only migrations plus restore-from-backup is the supported path.

---

## 12. Production hardening checklist

- [ ] Strong, externalized `jwt.secret` (≥256-bit), shared only with the token issuer.
- [ ] Real DB credentials via environment, not in `application.properties`.
- [ ] `dev` profile **not** active (the `/api/v1/auth/token` minter must be absent).
- [ ] H2 web console disabled (it is, under `prod-h2`); H2 files readable only by the service account.
- [ ] TLS terminated in front of the service; MQTT/webhook endpoints reachable only as intended.
- [ ] `backup.directory` exists, is writable, and is replicated off-box; test a restore.
- [ ] Rate limits reviewed for expected load.
- [ ] Actuator enabled and secured for health/metrics (see [§9](#9-monitoring--logging)).
- [ ] Application log level set to `INFO`; logs shipped to an aggregator with alerts on startup failures.
- [ ] Single H2 instance per file (or MariaDB for multi-node).
