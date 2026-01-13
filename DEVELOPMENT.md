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
- **JDBC URL**: `jdbc:h2:mem:race_results`
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

## Database Migration

The application uses Flyway for database migrations. Migrations are located in:

```
src/main/resources/db/migration/
```

### Current Migrations

- **V1__initial_schema.sql** - Initial database schema with tables for documents, versions, ACLs, webhooks, etc.

### Creating New Migrations

1. Create a new SQL file in `src/main/resources/db/migration/`
2. Follow naming convention: `V{version}__{description}.sql`
   - Example: `V3__add_user_preferences_table.sql`
3. Flyway will automatically apply migrations on startup

### Migration Compatibility

Migrations are written to be compatible with both H2 (MySQL mode) and MariaDB:

- ✅ Standard SQL syntax works on both
- ✅ AUTO_INCREMENT works on both
- ✅ TIMESTAMP, BIGINT, VARCHAR types work on both
- ⚠️ FULLTEXT indexes are MariaDB-only (commented out for H2)
- ⚠️ ENGINE and CHARSET clauses are ignored by H2

## MQTT Broker Setup (Optional)

For testing real-time notifications, you'll need an MQTT broker.

### Using Eclipse Mosquitto

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

Default settings in `application-dev.yml`:
```yaml
mqtt:
  broker:
    url: tcp://localhost:1883
```

### Using Docker

```bash
docker run -d -p 1883:1883 -p 9001:9001 eclipse-mosquitto
```

### Skip MQTT (Development)

If you don't need MQTT notifications, the application will log warnings but continue to work.

## Testing

### Run All Tests

```bash
mvn test
```

### Run Specific Test

```bash
mvn test -Dtest=DocumentManagerServiceTest
```

### Run Integration Tests

```bash
mvn verify
```

Tests automatically use H2 in-memory database.

## API Documentation

Once the application is running, access:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

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
# Health check
curl http://localhost:8080/actuator/health

# Create a document (requires JWT token)
curl -X POST http://localhost:8080/api/documents \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
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
   - Main class: `org.rowtown.Application`
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
      "mainClass": "org.rowtown.Application",
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

| Profile | Database | Use Case |
|---------|----------|----------|
| `dev` | H2 in-memory | Local development |
| `test` | H2 in-memory | Automated testing |
| `prod` | MariaDB | Production deployment |

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
| `mvn spring-boot:run -Dspring-boot.run.profiles=dev` | Start with H2 |
| `mvn test` | Run tests |
| `mvn clean install` | Build JAR |
| `mvn flyway:info` | Show migration status |
| `mvn flyway:migrate` | Run migrations |
| http://localhost:8080/h2-console | H2 database console |
| http://localhost:8080/swagger-ui.html | API documentation |
| http://localhost:8080/actuator/health | Health check |

## Next Steps

- Review [API Documentation](http://localhost:8080/swagger-ui.html)
- Read [SPECIFICATION.md](SPECIFICATION.md) for architecture details
- See [race-timer-client/README.md](race-timer-client/README.md) for client library
- See [race-timer-client-web/README.md](race-timer-client-web/README.md) for React client
