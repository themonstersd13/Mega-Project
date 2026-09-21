# SYNAPSE

**Spring Boot LLM Agent Orchestration System**

A production-ready framework for orchestrating Large Language Model (LLM) agents with organization, team, and role management.

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker and Docker Compose
- PostgreSQL 16+ (via Docker)
- A browser for the SYNAPSE Studio UI

## Project Structure

```
synapse/
├── src/
│   ├── main/
│   │   ├── java/com/synapse/
│   │   │   ├── SynapseApplication.java
│   │   │   └── security/
│   │   │       └── ApiKeyAuthFilter.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       └── db/migration/
│   │           ├── V1__init_extensions.sql
│   │           └── V2__core_org_role_team_agent.sql
│   └── test/java/com/synapse/
├── pom.xml
├── docker-compose.yml
└── README.md
```

## Quick Start

### 1. Start PostgreSQL

```bash
docker-compose up -d
```

Verify PostgreSQL is running:
```bash
docker-compose ps
```

### 2. Build the Project

```bash
mvn clean package
```

### 3. Run the Application

Development profile with debug logging:
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev" -Dspring-boot.run.environment="DB_HOST=localhost,DB_PORT=5432,DB_NAME=synapse_dev,DB_USER=synapse,DB_PASSWORD=synapse,API_KEY=dev-key"
```

Or set environment variables and run:
```bash
# Linux/Mac
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=synapse
export DB_USER=synapse
export DB_PASSWORD=synapse
export API_KEY=your-api-key
mvn spring-boot:run

# Windows (PowerShell)
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="synapse"
$env:DB_USER="synapse"
$env:DB_PASSWORD="synapse"
$env:API_KEY="your-api-key"
mvn spring-boot:run
```

### 4. Verify Health

```bash
curl http://localhost:8080/actuator/health
```

### 5. Test API Key Authentication

Without API key (should return 401):
```bash
curl http://localhost:8080/organizations
```

With API key:
```bash
curl -H "X-API-Key: your-api-key" http://localhost:8080/organizations
```

### 6. Open the Studio UI

Browse to:
```bash
http://localhost:8080/
```

The Studio UI includes workspace management, drag-and-drop task lanes, message and memory context, approvals, and evaluation reports.

## Configuration

### Environment Variables

- `DB_HOST` - PostgreSQL host (default: localhost)
- `DB_PORT` - PostgreSQL port (default: 5432)
- `DB_NAME` - Database name (default: synapse)
- `DB_USER` - Database user (default: synapse)
- `DB_PASSWORD` - Database password (default: synapse)
- `API_KEY` - API authentication key (optional, if empty, authentication is disabled)
- `SPRING_PROFILES_ACTIVE` - Active profiles (default: empty, use 'dev' for development)
  .\mvnw.cmd spring-boot:run
### Profiles

- **default**: Production-ready configuration with API key authentication required
- **dev**: Development configuration with DEBUG logging and optional API key authentication

## Database Migrations

Migrations are managed by Flyway and automatically applied on startup.

Current migrations:
- `V1__init_extensions.sql` - PostgreSQL extensions (vector, uuid-ossp)
- `V2__core_org_role_team_agent.sql` - Core schema (organization, role, team, agent)

## API Documentation

### Health Check (No Authentication Required)

```bash
GET /actuator/health
```

Response:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"}
  }
}
```

### Metrics (Requires X-API-Key)

```bash
GET /actuator/metrics
```

## Stopping Services

Stop PostgreSQL:
```bash
docker-compose down
```

Remove volumes and start fresh:
```bash
docker-compose down -v
```

## Development

### Running Tests

```bash
mvn test
```

### Building JAR

```bash
mvn clean package
```

The JAR will be available at `target/synapse-0.1.0-SNAPSHOT.jar`

## Security

- All non-health endpoints require `X-API-Key` header
- Database passwords must be provided via environment variables
- Flyway migrations ensure schema consistency
- PostgreSQL with pgvector for ML-ready embeddings

## Technologies

- **Framework**: Spring Boot 3.4.0
- **Database**: PostgreSQL 16 with pgvector
- **Migrations**: Flyway
- **ML Libraries**: Tribuo (regression-sgd, regression-tree)
- **Resilience**: Resilience4j
- **Testing**: Spring Boot Test, TestContainers

## License

Proprietary - SYNAPSE Project

## Support

For issues or questions, refer to the project documentation or implementation plan.
