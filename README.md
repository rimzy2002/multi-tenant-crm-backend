# multi-tenant-crm-backend

Multi-tenant CRM REST API built with Java, Spring Boot, MySQL, and Spring Security.

## Tech Stack
* **Java**: 21 (LTS)
* **Framework**: Spring Boot 3.3.5
* **Persistence**: Spring Data JPA / Hibernate 6
* **Database**: MySQL 8.0+ (Production/Development), H2 (In-Memory Testing)
* **Migrations**: Flyway (MySQL-compatible)
* **Security**: Spring Security & JJWT 0.12.6 (Stateless JWT Authentication)
* **Validation**: Jakarta Bean Validation
* **Build Tool**: Apache Maven (with Maven Wrapper `mvnw`)

## Prerequisites
* Java Development Kit (JDK) 21 installed (`java -version`)
* MySQL Server 8.x running locally or via Docker

## Environment Configuration
Database connection and JWT security settings are externalized using environment variables. You can copy `.env.example` to set local values:

| Variable | Description | Default |
|---|---|---|
| `DB_URL` | MySQL JDBC Connection URL | `jdbc:mysql://localhost:3306/multi_tenant_crm` |
| `DB_USERNAME` | MySQL Database Username | `crm_app` |
| `DB_PASSWORD` | MySQL Database Password | *(empty)* |
| `PORT` | HTTP Server Port | `8080` |
| `FLYWAY_ENABLED` | Enable/Disable Flyway migrations | `true` |
| `JWT_SECRET` | 256-bit Hex/Base64 Secret Key | *Development default* |
| `JWT_EXPIRATION`| Access Token TTL (milliseconds) | `86400000` (24h) |

## Build and Test

Compile the project:
```bash
./mvnw clean compile
# On Windows CMD/PowerShell:
.\mvnw.cmd clean compile
```

Run test suite:
```bash
./mvnw test
# On Windows CMD/PowerShell:
.\mvnw.cmd test
```

Run application:
```bash
./mvnw spring-boot:run
# On Windows CMD/PowerShell:
.\mvnw.cmd spring-boot:run
```
