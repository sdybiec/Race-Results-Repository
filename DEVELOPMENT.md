# Development Setup Guide

This guide explains how to set up and run the Race Results Repository for local development.

## Running with H2 Database (Development)

For quick local development without setting up MariaDB, you can use the embedded H2 database.

### Start with H2

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Or with Maven wrapper:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Or from your IDE, set the active profile to `dev`:
- **IntelliJ IDEA**: Run > Edit Configurations > Active profiles: `dev`
- **Eclipse**: Run Configurations > Arguments tab > VM arguments: `-Dspring.profiles.active=dev`
- **VS Code**: Add to launch.json: `"args": ["--spring.profiles.active=dev"]`

### H2 Console

When running with the dev profile, the H2 console is available at:

```
http://localhost:8080/h2-console
```

**Connection Details:**
- **JDBC URL**: `jdbc:h2:mem:race_results;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE`
- **Username**: `sa`
- **Password**: _(leave empty)_
- **Driver Class**: `org.h2.Driver`

### Features in Dev Profile

- **In-memory H2 database** - No installation required
- **H2 Console** - Web-based database browser
- **Verbose logging** - DEBUG level for application code
- **SQL logging** - See all SQL queries
- **Permissive rate limits** - Higher limits for testing
- **Fast startup** - No external database connection
- **Auto-reload** - Database resets on application restart

## Running with MariaDB (Production-like)

For production-like testing with MariaDB:

### 1. Install MariaDB

**macOS:**
```bash
brew install mariadb
brew services start mariadb
```

**Ubuntu/Debian:**
```bash
sudo apt-get install mariadb-server
sudo systemctl start mariadb
```

**Windows:**
Download from https://mariadb.org/download/

### 2. Create Database and User

```sql
CREATE DATABASE race_results;
CREATE USER 'race_results_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON race_results.* TO 'race_results_user'@'localhost';
FLUSH PRIVILEGES;
```

### 3. Update Configuration

Edit `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mariadb://localhost:3306/race_results?createDatabaseIfNotExist=true
spring.datasource.username=race_results_user
spring.datasource.password=your_password
```

### 4. Run Application

```bash
mvn spring-boot:run
```

## Running with H2 (Embedded Production)

For a **single-node / embedded** install (no separate database server), the
`prod-h2` profile runs against a **persistent, file-based** H2 database. The
schema is managed by Flyway (`db/migration/h2`) with `ddl-auto=validate`.

```bash
mvn clean package

DB_PATH=/var/lib/race-results/db/race_results \
DB_USER=sa DB_PASSWORD='change-me' \
java -jar target/race-results-repository-*.jar --spring.profiles.active=prod-h2
```

- File-based storage survives restarts (unlike the in-memory `dev` database).
- `DB_PATH`, `DB_USER`, `DB_PASSWORD` come from the environment; the H2 web
  console is disabled.
- H2 is a single-writer engine — suitable for single-node/embedded only. Use
  MariaDB for multi-instance / HA deployments.

See [docs/h2-production-support.md](docs/h2-production-support.md) for the full
design and operational guidance.

## Database Migration

The application uses Flyway with **per-vendor** migrations so the same schema can
be built on both MariaDB and H2. Flyway's `{vendor}` placeholder resolves to the
connected engine and selects the matching directory:

```
src/main/resources/db/migration/
├── mariadb/V1__initial_schema.sql   # MariaDB: InnoDB, utf8mb4, LONGBLOB, prefix indexes
└── h2/V1__initial_schema.sql        # native H2: BLOB, VARCHAR, standalone CREATE INDEX
```

The location is configured as `classpath:db/migration/{vendor}` (in
`application.properties` and the H2 profiles).

### Creating New Migrations

Because each engine has its own directory, a schema change must be written for
**both** vendors:

1. Add `V{version}__{description}.sql` under **both** `db/migration/mariadb/` and
   `db/migration/h2/`, using the same version number and description
   (e.g. `V2__add_user_preferences_table.sql`).
2. Keep the two files equivalent; only express the dialect differences
   (`LONGBLOB` vs `BLOB`, inline `INDEX` vs standalone `CREATE INDEX`, etc.).
3. Flyway applies migrations automatically on startup.

> **Column names:** entity `@Column` names are used **verbatim** as physical
> column names (`spring.jpa.hibernate.naming.physical-strategy` is set to
> `PhysicalNamingStrategyStandardImpl`). Column names in both migrations must
> match the `@Column` names exactly, or `ddl-auto=validate` will fail.

Drift between the two migration sets (or between a migration and the entities) is
caught automatically by CI — see [Continuous Integration](#continuous-integration).

## MQTT Broker Setup

The application supports MQTT notifications for real-time updates. You have three options:

### Option 1: Embedded Moquette Broker (Recommended for Development)

The easiest option - no external broker needed! The `dev` profile automatically starts an embedded MQTT broker.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The embedded broker:
- ✅ Starts automatically with the application
- ✅ No installation or configuration required
- ✅ Runs on `localhost:1883`
- ✅ In-memory storage (resets on restart)
- ✅ Perfect for development and testing

**Using embedded broker in other profiles:**

```bash
# With default profile
mvn spring-boot:run -Dspring-boot.run.arguments="--mqtt.embedded.enabled=true"

# Or use the embedded profile
mvn spring-boot:run -Dspring-boot.run.profiles=embedded
```

**Configuration:**

```yaml
mqtt:
  embedded:
    enabled: true
    host: localhost
    port: 1883
    allow-anonymous: true
    persistent: false  # Use in-memory storage
```

### Option 2: Eclipse Mosquitto (Production)

For production deployments, use an external broker like Mosquitto.

**Install:**
```bash
# macOS
brew install mosquitto
brew services start mosquitto

# Ubuntu/Debian
sudo apt-get install mosquitto
sudo systemctl start mosquitto

# Windows
# Download from https://mosquitto.org/download/
```

**Configuration:**

```properties
mqtt.embedded.enabled=false
mqtt.broker.url=tcp://localhost:1883
```

### Option 3: Docker

```bash
docker run -d -p 1883:1883 -p 9001:9001 eclipse-mosquitto
```

### Skip MQTT

If you don't need MQTT notifications, the application will log warnings but continue to work.

## Testing

### Run All Tests

```bash
mvn test
```

By default the suite runs against an in-memory **H2** database. Unlike a plain
`create-drop`, tests boot with the **real Flyway migrations** and
`ddl-auto=validate`, so a migration that drifts from the entities fails the
build.

### Run a Specific Test

```bash
mvn test -Dtest=DocumentManagerServiceTest
```

### Run Integration Tests

```bash
mvn verify
```

### Run the Suite Against MariaDB

The test datasource is configurable via `TEST_DB_*` environment variables
(defaulting to H2). To run against a throwaway MariaDB — matching the CI
`mariadb` axis:

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

## Continuous Integration

`.github/workflows/ci.yml` runs the full test suite in a matrix against **both**
H2 and MariaDB on every push and pull request. Because the suite boots with
Flyway + `validate`, this catches migration drift on whichever engine it occurs.
The `h2` axis needs no services; the `mariadb` axis starts a MariaDB 11 container.

## API Documentation

Once the application is running, access:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

### Export a Static OpenAPI File

```bash
mvn verify -Popenapi
```

This boots the app on H2 (no external services), fetches `/api-docs`, and writes
`docs/api/openapi.json`. See [docs/api/README.md](docs/api/README.md).

## Authentication

Every endpoint except the docs (`/swagger-ui.html`, `/api-docs/**`) and
`/actuator/**` requires a **JWT bearer token** — the app is stateless and uses no
sessions. The `Using generated security password: ...` line Spring Boot logs at
startup is a framework default this app does **not** use; ignore it.

### Get a Token (dev profile)

There is no login endpoint in production (tokens are issued by an external auth
service). For local development, the `dev` profile exposes a token minter at
`POST /api/v1/auth/token`:

```bash
# Empty body -> admin token (REGATTA_ADMIN, TIMER, VIEWER) for all regattas
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token | jq -r .token)

# Or a custom identity / roles / regatta scope
curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"userId":"alice","username":"alice","roles":["TIMER"],"regattaIds":["REG2025"]}'
```

Send it on each request:

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/documents/1
```

> The token endpoint is gated to the `dev` profile (`@Profile("dev")`) and is
> never available in production.

## Development Workflow

### 1. Start Application

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 2. Access H2 Console

Visit http://localhost:8080/h2-console to browse the database.

### 3. Test API Endpoints

Use Swagger UI at http://localhost:8080/swagger-ui.html

Or use curl:

```bash
# Health check (public)
curl http://localhost:8080/actuator/health

# Get a dev JWT (see the Authentication section)
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token | jq -r .token)

# Create a document (requires the JWT token)
curl -X POST http://localhost:8080/api/v1/documents \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "START_LIST",
    "regattaId": "HOSR2025",
    "author": "admin@example.com",
    "modelData": "..."
  }'
```

### 4. Monitor Logs

Application logs show:
- SQL queries (when `spring.jpa.show-sql=true`)
- Request/response details
- Authentication events
- MQTT messages

### 5. Reset Database

Since H2 is in-memory, simply restart the application to reset the database.

## IDE Setup

### IntelliJ IDEA

1. Open the project
2. Wait for Maven to import dependencies
3. Create Run Configuration:
   - Main class: `org.rowtown.rms.rrr.RaceResultsRepositoryApplication`
   - VM options: `-Dspring.profiles.active=dev`
   - Module: `race-results-repository`

### Visual Studio Code

1. Install Java Extension Pack
2. Install Spring Boot Extension Pack
3. Open folder
4. Run with `F5` or create `.vscode/launch.json`:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Spring Boot Dev",
      "request": "launch",
      "mainClass": "org.rowtown.rms.rrr.RaceResultsRepositoryApplication",
      "projectName": "race-results-repository",
      "args": "--spring.profiles.active=dev"
    }
  ]
}
```

### Eclipse

1. Import as Maven project
2. Right-click project > Run As > Run Configurations
3. Create new Spring Boot App configuration
4. Set Profile to `dev`

## Troubleshooting

### Port Already in Use

Change port in `application-dev.yml`:

```yaml
server:
  port: 8081
```

### Flyway Migration Failed

Clear Flyway metadata:

```sql
-- In H2 Console
DROP TABLE flyway_schema_history;
```

Then restart application.

### MQTT Connection Failed

MQTT is optional. If you don't have a broker:

1. The application will log warnings but continue
2. Webhook notifications will still work
3. Or install Mosquitto (see above)

### H2 Console Not Working

Ensure dev profile is active:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Out of Memory

Increase heap size:
```bash
export MAVEN_OPTS="-Xmx2g"
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Configuration Profiles

| Profile | Database | MQTT Broker | Use Case |
|---------|----------|-------------|----------|
| `dev` | H2 in-memory | Embedded Moquette | Local development (+ dev token endpoint) |
| `prod-h2` | H2 file (persistent) | External (configurable) | Single-node / embedded production |
| `openapi` | H2 in-memory | Disabled | Export the static OpenAPI spec |
| `embedded` | Use default | Embedded Moquette | Dev without external broker |
| `embedded-prod` | Use default | Embedded (persistent) | Single-server production |
| `test` | H2 in-memory (or MariaDB via `TEST_DB_*`) | Disabled | Automated testing / CI |
| _(default, no profile)_ | MariaDB | External (Mosquitto) | Production deployment |

Switch profiles with:
```bash
--spring.profiles.active=dev
```

Or set environment variable:
```bash
export SPRING_PROFILES_ACTIVE=dev
```

## Quick Reference

| Command | Description |
|---------|-------------|
| `mvn spring-boot:run -Dspring-boot.run.profiles=dev` | Start with in-memory H2 |
| `mvn spring-boot:run -Dspring-boot.run.profiles=prod-h2` | Start with persistent (file) H2 |
| `mvn test` | Run tests (H2 by default) |
| `mvn verify -Popenapi` | Export static OpenAPI spec to `docs/api/openapi.json` |
| `mvn clean install` | Build JAR |
| `curl -X POST localhost:8080/api/v1/auth/token` | Get a dev JWT (dev profile) |
| `mvn flyway:info` | Show migration status |
| `mvn flyway:migrate` | Run migrations |
| http://localhost:8080/h2-console | H2 database console (dev profile) |
| http://localhost:8080/swagger-ui.html | API documentation |
| http://localhost:8080/actuator/health | Health check |

## Next Steps

- Review [API Documentation](http://localhost:8080/swagger-ui.html)
- Read [SPECIFICATION.md](SPECIFICATION.md) for architecture details
- See [race-timer-client/README.md](race-timer-client/README.md) for client library
- See [race-timer-client-web/README.md](race-timer-client-web/README.md) for React client
