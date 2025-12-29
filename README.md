# Race Results Repository

A Spring Boot microservice providing version-controlled storage and management for rowing regatta timing data based on the Timing Data Interchange EMF schema.

## Overview

The Race Results Repository enables rowing regatta timers to accurately and rapidly share race results by persisting and managing EMF Timing Data Interchange models with full version control capabilities.

### Document Types

- **Start List**: Read-only chronologically ordered schedule of races and crew assignments (one per regatta)
- **Race Results**: Timing data captured by timers including crossing times and crew identifications (one per timer/milestone)

## Features Implemented

### ✅ Phase 1: Foundation & Core Infrastructure
- Maven project with Spring Boot 3.2.1
- Complete JPA entity model (Documents, Versions, Metadata, Tags, ACLs, Webhooks)
- MariaDB database schema with Flyway migrations
- Timing Data Interchange EMF schema integration

### ✅ Phase 2: Version Control & Document Management
- **Version Control Service**: Create, retrieve, list, compare, and rollback versions
- **Document Manager Service**: Create, update, retrieve, and delete documents
- **EMF Compare Integration**: Full model comparison with diff generation
- Application-level versioning with full model snapshots
- Checksum validation for data integrity

### ✅ Phase 3: Security & Authorization
- **JWT Authentication**: Token-based authentication with role and regatta claims
- **ACL-based Authorization**: Per-regatta, per-operation access control
- **Role-Based Access**: Regatta Admin, Timer, and Viewer roles
- **Ownership Validation**: Document ownership enforcement for Race Results
- **Rate Limiting**: Sliding window algorithm (100 reads/min, 20 writes/min, 30 searches/min)

### ✅ Phase 4: Search & Retrieval
- **Multi-criteria Search**: Document ID, regatta ID, type, timer, author, description
- **Match Types**: Exact, partial, and wildcard matching
- **Full-text Search**: MariaDB full-text indexing on descriptions
- **Metadata & Tag Filtering**: Search by metadata key-value pairs and tags
- **Caching**: Spring Cache for search results and documents

### ✅ Phase 5: REST API
- **Document Controller**: CRUD operations with authorization
- **Version Controller**: Version history, comparison, and rollback
- **Search Controller**: Advanced search with pagination
- **Webhook Controller**: Subscription management and MQTT info
- **OpenAPI/Swagger Documentation**: Complete API documentation at `/swagger-ui.html`

### ✅ Phase 6: Hessian RPC API
- **Binary RPC Protocol**: Efficient Java-to-Java communication
- **Feature Parity**: All REST operations available via Hessian
- **Endpoint**: `/hessian/repository`
- **Service Interface**: Strongly-typed Java interface

### ✅ Phase 7: MQTT Notifications
- **Topic Structure**: `regatta/{regattaId}/startlist` and `regatta/{regattaId}/results/{timerId}`
- **MQTT Client**: Auto-reconnecting client publishing to external broker
- **Event Publishing**: VERSION_CREATED, DOCUMENT_CREATED, FIELD_CHANGED, DOCUMENT_DELETED
- **Hierarchical Subscriptions**: Subscribe to all models in a regatta

### ✅ Phase 8: Webhook Notifications
- **Subscription Management**: Per-document or per-regatta subscriptions
- **Exponential Backoff Retry**: 2s, 4s, 8s, 16s delays (max 5 attempts)
- **HMAC-SHA256 Signatures**: Webhook payload verification
- **Full Document Delivery**: Complete model data in webhook payload
- **Failure Handling**: Auto-deactivation after 10 consecutive failures

## Architecture

```
Race Results Repository (Spring Boot)
├── API Layer
│   ├── REST Controllers (Document, Version, Search, Webhook)
│   └── Hessian RPC Servlet
├── Business Logic
│   ├── Document Manager
│   ├── Version Control Service
│   ├── Authorization Service
│   ├── Search Service
│   └── Notification Service
├── Integration Layer
│   ├── MQTT Publishing Service
│   └── Webhook Delivery Service
└── Data Layer
    ├── JPA Repositories
    └── MariaDB Database
```

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.2.1 |
| Build | Maven |
| Database | MariaDB 10.6+ |
| EMF | Eclipse Modeling Framework 2.35.0 |
| Authentication | JWT (JJWT 0.12.3) |
| RPC | Hessian 4.0.66 |
| Messaging | Eclipse Paho MQTT 1.2.5 |
| Documentation | SpringDoc OpenAPI 2.3.0 |
| Java Version | 17 |

## Quick Start

### Prerequisites

- Java 17+
- MariaDB 10.6+
- MQTT Broker (e.g., Eclipse Mosquitto)
- Maven 3.6+

### Configuration

Edit `src/main/resources/application.properties`:

```properties
# Database
spring.datasource.url=jdbc:mariadb://localhost:3306/race_results
spring.datasource.username=your_username
spring.datasource.password=your_password

# JWT Secret (use a strong 256-bit key in production)
jwt.secret=your-256-bit-secret-key-change-in-production-must-be-very-long

# MQTT Broker
mqtt.broker.url=tcp://localhost:1883
mqtt.client.id=race-results-repository
```

### Build & Run

```bash
# Build the project
mvn clean package

# Run the application
java -jar target/race-results-repository-1.0.0-SNAPSHOT.jar
```

The service will start on port 8080.

### API Documentation

Once running, access:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8080/api-docs

## API Examples

### Create Document (REST)

```bash
POST /api/v1/documents
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

{
  "type": "START_LIST",
  "regattaId": "HEAD2025",
  "author": "admin@example.com",
  "description": "Head of the Charles 2025 Start List",
  "tags": ["preliminary"],
  "metadata": {
    "venue": "Charles River",
    "date": "2025-10-19"
  },
  "modelData": "base64_encoded_emf_model"
}
```

### Search Documents (REST)

```bash
GET /api/v1/documents/search?regattaId=HEAD2025&type=RACE_RESULTS&matchType=EXACT
Authorization: Bearer {JWT_TOKEN}
```

### Subscribe to Webhook (REST)

```bash
POST /api/v1/webhooks/subscribe
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

{
  "regattaId": "HEAD2025",
  "webhookUrl": "https://example.com/webhook",
  "secretKey": "your-webhook-secret",
  "events": ["VERSION_CREATED", "FIELD_CHANGED"]
}
```

### Hessian RPC (Java Client)

```java
HessianProxyFactory factory = new HessianProxyFactory();
factory.addHeader("Authorization", "Bearer " + jwtToken);

RepositoryService service = (RepositoryService) factory.create(
    RepositoryService.class,
    "http://localhost:8080/hessian/repository"
);

// Create document
DocumentResponse doc = service.createDocument(request);

// Compare versions
ModelDiff diff = service.compareVersions(docId, 1L, 2L);
```

## MQTT Topics

### Start List
- Topic: `regatta/{regattaId}/startlist`
- Example: `regatta/HEAD2025/startlist`

### Race Results
- Topic: `regatta/{regattaId}/results/{timerId}`
- Example: `regatta/HEAD2025/results/timer001`

### Subscribe to All Results in a Regatta
- Pattern: `regatta/{regattaId}/results/#`
- Example: `regatta/HEAD2025/results/#`

## Security

### Roles & Permissions

| Role | Start List | Race Results (Own) | Race Results (Others) |
|------|-----------|-------------------|---------------------|
| **Regatta Admin** | Full Access | Full Access | Full Access |
| **Timer** | Read Only | Create, Read, Update, Rollback | Read (if permitted) |
| **Viewer** | Read Only | Read (if permitted) | Read (if permitted) |

### JWT Token Structure

```json
{
  "sub": "user123",
  "username": "john.doe",
  "roles": ["TIMER"],
  "regattaIds": ["HEAD2025", "HOCR2025"],
  "iat": 1735478400,
  "exp": 1735564800
}
```

## Rate Limits

| Operation | Limit | Window |
|-----------|-------|--------|
| Read | 100 requests | 60 seconds |
| Write | 20 requests | 60 seconds |
| Search | 30 requests | 60 seconds |

Exceeded requests receive HTTP 429 with `Retry-After` header.

## Database Schema

### Core Tables
- **documents**: Main document records with metadata
- **versions**: Version snapshots with full EMF model data
- **metadata**: Key-value metadata for documents
- **tags**: Document tags for categorization
- **acl_entries**: Access control lists per regatta
- **document_ownership**: Document ownership records
- **webhook_subscriptions**: Webhook registration
- **mqtt_subscriptions**: MQTT subscription tracking

## Project Structure

```
src/main/java/org/rowtown/
├── config/              # Spring configuration classes
├── controller/          # REST controllers
├── domain/              # Domain enums and types
│   └── entity/         # JPA entities
├── dto/                 # Data Transfer Objects
├── exception/           # Custom exceptions
├── hessian/             # Hessian RPC interface & implementation
├── notification/        # Notification event models
├── ratelimit/          # Rate limiting implementation
├── repository/          # JPA repositories
├── security/            # JWT and authentication
└── service/             # Business logic services
```

## Development Notes

- EMF models are serialized as XMI or JSON
- Version snapshots are stored as LONGBLOB in MariaDB
- Optimistic locking prevents concurrent update conflicts
- Flyway manages database schema migrations
- All services support both REST and Hessian RPC

## Future Enhancements

- Point-in-time restore for backups
- Advanced EMF Compare diff visualization
- WebSocket support for real-time updates
- Redis cache for improved performance
- Kubernetes deployment manifests
- Integration tests with Testcontainers

## License

Copyright © 2025 RowTown. All rights reserved.
