# SYNAPSE - Quick Start Guide

## Prerequisites

- Java 21+
- Maven 3.9+ (automatically downloaded if not available)
- Docker Desktop (for PostgreSQL)
- Windows 10+ or Linux/Mac with PowerShell Core

## Phase 0: Scaffolding - Complete ✓

The SYNAPSE project has been successfully scaffolded with:
- ✓ Maven project structure with Spring Boot 3.4.0
- ✓ PostgreSQL with pgvector configuration
- ✓ Database migrations (Flyway)
- ✓ API Key authentication filter
- ✓ Sample REST endpoint
- ✓ Built JAR artifact (62 MB)

## Quick Start (5 minutes)

### Step 1: Start PostgreSQL

**Option A: Using Docker Compose (Recommended)**
```powershell
cd E:\Mega Project\synapse
docker-compose up -d
```

Wait for PostgreSQL to be ready (check `docker-compose ps`):
```
synapse-postgres   running   ✓
```

### Step 2: Set Environment Variables (Optional)

Create a `.env` file in the project root or set variables manually:

```powershell
# PowerShell
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="synapse"
$env:DB_USER="synapse"
$env:DB_PASSWORD="synapse"
$env:API_KEY="dev-key-12345"
$env:SPRING_PROFILES_ACTIVE="dev"
```

Or modify `.env` file in project root.

### Step 3: Run the Application

**Option A: Using Setup Script (Easiest)**
```powershell
cd E:\Mega Project\synapse
powershell -ExecutionPolicy Bypass -File .\setup.ps1 -Action run
```

**Option B: Direct Java Execution**
```powershell
cd E:\Mega Project\synapse
$env:DB_HOST="localhost"
$env:DB_PORT="5432"
$env:DB_NAME="synapse"
$env:DB_USER="synapse"
$env:DB_PASSWORD="synapse"
$env:API_KEY="dev-key-12345"
java -jar .\target\synapse-0.1.0-SNAPSHOT.jar
```

You should see output like:
```
Started SynapseApplication in 3.5 seconds (process running for 5.2s)
```

### Step 4: Verify Application is Running

**Health Check (No Authentication)**
```powershell
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"}
  }
}
```

**Test API Authentication**

Without API key (should return 401):
```powershell
curl http://localhost:8080/organizations
```

With API key (should return 200):
```powershell
curl -H "X-API-Key: dev-key-12345" http://localhost:8080/organizations
```

Expected response: a JSON workspace list.

### Step 5: Open the Studio UI

```powershell
start http://localhost:8080/
```

The Studio UI includes workspace management, drag-and-drop task lanes, shared context, approvals, and evaluation reports.

## Common Tasks

### Build the Project
```powershell
cd E:\Mega Project\synapse
powershell -ExecutionPolicy Bypass -File .\mvnw.ps1 clean package -DskipTests
```

### Stop PostgreSQL
```powershell
cd E:\Mega Project\synapse
docker-compose down
```

### Remove PostgreSQL Volume and Start Fresh
```powershell
cd E:\Mega Project\synapse
docker-compose down -v
docker-compose up -d
```

### View Application Logs

The application writes logs to console. You can also access metrics:
```powershell
curl -H "X-API-Key: dev-key-12345" http://localhost:8080/actuator/metrics
```

### Run Unit Tests
```powershell
cd E:\Mega Project\synapse
powershell -ExecutionPolicy Bypass -File .\mvnw.ps1 test
```

## Project Structure

```
synapse/
├── src/
│   ├── main/
│   │   ├── java/com/synapse/
│   │   │   ├── SynapseApplication.java       # Main Spring Boot entry point
│   │   │   ├── controller/
│   │   │   │   └── OrganizationController.java
│   │   │   └── security/
│   │   │       └── ApiKeyAuthFilter.java      # API Key authentication
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       └── db/migration/
│   │           ├── V1__init_extensions.sql
│   │           └── V2__core_org_role_team_agent.sql
│   └── test/java/com/synapse/
├── .mvn/wrapper/                    # Maven wrapper for easy builds
├── mvnw.ps1                         # Maven wrapper script (PowerShell)
├── setup.ps1                        # Setup script
├── docker-compose.yml               # PostgreSQL container config
├── .env                             # Environment variables
├── pom.xml                          # Maven configuration
├── README.md                        # Full documentation
└── QUICKSTART.md                    # This file
```

## Troubleshooting

### Docker Container Won't Start
```powershell
# Check if Docker Desktop is running
docker --version

# If needed, restart Docker Desktop
```

### Port 5432 Already in Use
```powershell
# Find process using port 5432
Get-NetTCPConnection -LocalPort 5432

# Either:
# 1. Change DB_PORT in .env
# 2. Stop the conflicting process
# 3. Stop existing containers: docker-compose down
```

### Maven Build Fails
```powershell
# Clear Maven cache
powershell -ExecutionPolicy Bypass -File .\mvnw.ps1 clean

# Rebuild with verbose output
powershell -ExecutionPolicy Bypass -File .\mvnw.ps1 -X clean package
```

### Application Won't Connect to Database
1. Verify PostgreSQL is running: `docker-compose ps`
2. Check environment variables are set correctly
3. Verify database credentials in .env match docker-compose.yml
4. Check logs for specific errors

## Next Steps (Phase 1+)

The scaffolding is complete. Next phases will include:

1. **Phase 1: Core Entities** - Implement JPA entities and repositories
2. **Phase 2: API Development** - REST endpoints for organizations, teams, agents
3. **Phase 3: Authentication & Authorization** - Full RBAC implementation
4. **Phase 4: Agent Orchestration** - LLM agent management and execution
5. **Phase 5: Testing & Documentation** - Complete test coverage and API docs

## Support

Refer to:
- `README.md` - Full project documentation
- `implementation_plan.md` - Detailed requirements and specs
- Spring Boot docs: https://spring.io/projects/spring-boot
- PostgreSQL docs: https://www.postgresql.org/docs/
- Flyway docs: https://flywaydb.org/documentation/

## Project Details

- **Framework**: Spring Boot 3.4.0
- **Database**: PostgreSQL 16 with pgvector
- **Build Tool**: Maven 3.9+
- **Java Version**: 24
- **Migrations**: Flyway
- **Authentication**: API Key (X-API-Key header)

---

**Status**: Phase 0 Complete - Project scaffolded and ready for development.

Date Created: 2026-08-27
