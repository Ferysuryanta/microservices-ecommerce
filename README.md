# Microservices E-Commerce

Backend microservices project using Java 21, Spring Boot, Maven, Docker,
and PostgreSQL.

> **Project status:** Auth Service is the currently implemented and
> runnable service. Product, Cart, Order, and Payment services are not
> documented as implemented yet.

## 1. Project structure

``` text
microservices-ecommerce/
├── docs/
├── infrastructure/
├── postman-collection/
└── services/
    └── auth-service/
        ├── src/main/
        ├── src/test/
        ├── .env
        ├── .env.example
        ├── Dockerfile
        ├── docker-compose.yml
        └── pom.xml
```

## 2. Prerequisites

-   Java 21
-   Docker Engine and Docker Compose plugin
-   Git
-   Postman (for API testing)

Check installations:

``` bash
java -version
docker --version
docker compose version
git --version
```

## 3. Configure environment

Open the Auth Service directory:

``` bash
cd ~/workspace/Development/microservices-ecommerce/services/auth-service
```

Create `.env` if it does not exist:

``` bash
cp .env.example .env
```

Review `.env`, `.env.example`, `docker-compose.yml`, and
`src/main/resources/application.yml`. Keep variable names consistent.
Never commit `.env` or real credentials.

Current local configuration:

  Component                          Value
  ---------------------------------- -------------
  PostgreSQL container               `auth-db`
  Database                           `auth_db`
  Database user                      `auth_user`
  PostgreSQL host port               `5433`
  PostgreSQL container port          `5432`
  Auth Service host/container port   `8081`

## 4. Run with Docker Compose (recommended)

Run from `services/auth-service`:

``` bash
docker compose up --build -d
docker compose ps
```

Expected state: - `auth-db` is `healthy` - `auth-service` is running

Follow application logs:

``` bash
docker compose logs -f auth-service
```

Follow database logs:

``` bash
docker compose logs -f auth-db
```

Stop containers:

``` bash
docker compose down
```

Stop containers and delete persisted Compose volumes:

``` bash
docker compose down -v
```

> **Warning:** `down -v` deletes local PostgreSQL data. Use it only when
> you intentionally want to reset the database.

Rebuild after application changes:

``` bash
docker compose up --build -d
```

## 5. Run the application locally (database in Docker)

Start only PostgreSQL:

``` bash
docker compose up -d auth-db
```

Run Spring Boot from `services/auth-service`:

``` bash
./mvnw spring-boot:run
```

On Windows PowerShell:

``` powershell
.\mvnw.cmd spring-boot:run
```

Ensure local application configuration uses the host-mapped PostgreSQL
port `5433` and the correct environment variables.

## 6. Database and Flyway

Flyway migrations are located at:

``` text
src/main/resources/db/migration/
```

Flyway applies pending migrations on application startup. Hibernate
validates the mapped schema.

Connect to PostgreSQL:

``` bash
docker compose exec auth-db psql -U auth_user -d auth_db
```

Useful SQL:

``` sql
\dt

SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT
    id, user_id, token_family, created_at,
    expires_at, revoked_at, replaced_by_token_id
FROM refresh_tokens
ORDER BY created_at DESC;
```

Exit:

``` sql
\q
```

The database stores refresh-token hashes, not raw refresh tokens. Do not
share token hashes or raw tokens in screenshots or logs.

## 7. Health check and application info

Base URL:

``` text
http://localhost:8081
```

Health:

``` http
GET http://localhost:8081/actuator/health
```

Expected response:

``` json
{"status":"UP"}
```

Application information:

``` http
GET http://localhost:8081/actuator/info
```

The returned info depends on `info.app` configuration.

## 8. Swagger / OpenAPI

When enabled, Swagger UI:

``` text
http://localhost:8081/swagger-ui/index.html
```

OpenAPI JSON:

``` text
http://localhost:8081/v3/api-docs
```

Keep interactive API documentation restricted or disabled in production
unless access is controlled.

## 9. Auth API endpoints

Base URL: `http://localhost:8081`

  ----------------------------------------------------------------------------
  Method            Endpoint               Purpose           Authentication
  ----------------- ---------------------- ----------------- -----------------
  POST              `/api/auth/register`   Register user     Not required

  POST              `/api/auth/login`      Login and issue   Not required
                                           tokens            

  GET               `/api/auth/me`         Current user      Bearer access
                                           profile           token

  POST              `/api/auth/refresh`    Rotate refresh    Refresh token in
                                           token and issue   JSON body
                                           new tokens        

  POST              `/api/auth/logout`     Revoke refresh    Refresh token in
                                           token             JSON body
  ----------------------------------------------------------------------------

### Register

``` http
POST /api/auth/register
Content-Type: application/json
```

Example body (confirm field names against `RegisterRequest`):

``` json
{
  "email": "user@example.com",
  "password": "ChangeMe123!",
  "fullName": "Example User"
}
```

### Login

``` http
POST /api/auth/login
Content-Type: application/json
```

Example body (confirm field names against `LoginRequest`):

``` json
{
  "email": "user@example.com",
  "password": "ChangeMe123!"
}
```

Successful login returns an access token and refresh token. Example
response shape:

``` json
{
  "accessToken": "<access-token>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "refreshToken": "<refresh-token>"
}
```

The actual TTL is controlled by application configuration.

### Current user

``` http
GET /api/auth/me
Authorization: Bearer <access-token>
```

Use the access token from login or refresh. Do not use the refresh token
as a Bearer access token.

### Refresh token

``` http
POST /api/auth/refresh
Content-Type: application/json
```

``` json
{
  "refreshToken": "<latest-refresh-token>"
}
```

A successful refresh returns a new access token and a **new refresh
token**.

Refresh-token rotation: 1. Login issues access and refresh tokens. 2.
Send the current refresh token to `/api/auth/refresh`. 3. The server
revokes the old refresh token and issues a replacement. 4. Save and use
the replacement token from the response. 5. Do not reuse the old token;
a revoked/reused token should be rejected with `401 Unauthorized`.

A refresh-token request does **not** require the access token to expire
first. Refresh tokens are validated independently. A 401 can mean the
supplied token is wrong, expired, revoked, already rotated, or missing.

### Logout

``` http
POST /api/auth/logout
Content-Type: application/json
```

``` json
{
  "refreshToken": "<latest-refresh-token>"
}
```

Logout revokes the supplied refresh token. Use the latest refresh token
from login or the most recent successful refresh.

## 10. Recommended Postman sequence

Set collection variable:

``` text
baseUrl = http://localhost:8081
```

Test in this order:

1.  `POST {{baseUrl}}/api/auth/register`
2.  `POST {{baseUrl}}/api/auth/login`
3.  `GET {{baseUrl}}/api/auth/me` using the access token as Bearer
    token.
4.  `POST {{baseUrl}}/api/auth/refresh` using the latest refresh token
    in the JSON body.
5.  `GET {{baseUrl}}/api/auth/me` using the new access token.
6.  `POST {{baseUrl}}/api/auth/logout` using the latest refresh token.
7.  Try refresh with the revoked token; expect `401 Unauthorized`.

Do not send the same refresh token more than once after a successful
rotation. If a Postman collection exists in `postman-collection/`,
import it and configure its base URL variable.

## 11. Automated tests

Run from `services/auth-service`:

``` bash
./mvnw clean test
```

Compile:

``` bash
./mvnw clean compile
```

Package:

``` bash
./mvnw clean package
```

Run a focused test class:

``` bash
./mvnw -Dtest=AuthServiceTest test
```

If integration tests use the `test` Spring profile, ensure
`auth_test_db` exists and that `src/test/resources/application-test.yml`
has the correct connection settings.

## 12. Troubleshooting

### Port 8081 is already in use

``` bash
sudo ss -ltnp | grep ':8081'
```

Stop the conflicting process or change the Compose host-port mapping and
update the API base URL.

### Port 5433 is already in use

``` bash
sudo ss -ltnp | grep ':5433'
```

Stop the conflicting process or change the PostgreSQL host-port mapping
and update the datasource URL.

### Container fails to start

``` bash
docker compose ps
docker compose logs --tail=200 auth-service
docker compose logs --tail=200 auth-db
```

Check database health, environment variables, datasource URL, and
startup errors.

### Actuator returns 401

Verify that the security configuration permits the exact paths:

``` java
.requestMatchers(
    "/actuator/health",
    "/actuator/info"
).permitAll()
```

Verify web exposure in the active `application.yml`:

``` yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

Rebuild and restart after configuration changes:

``` bash
docker compose up --build -d
```

### Refresh endpoint returns 401

-   Send a non-empty `refreshToken` in the JSON body.
-   Use the raw refresh token from the latest successful login/refresh
    response.
-   Do not send the JWT access token to the refresh endpoint.
-   Do not reuse a token that has already been rotated or revoked.
-   Inspect the corresponding database record without sharing its hash.
-   Confirm the service is connected to the intended database.

### Flyway / Hibernate reports missing table or column

Check migration history:

``` sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Compare entity mappings and repository SQL with actual database column
names. For example, the refresh-token schema uses
`replaced_by_token_id`.

Prefer a new Flyway migration for schema changes rather than making
undocumented manual database changes.

## 13. Security notes

-   Never commit `.env`, database passwords, JWT signing secrets, or raw
    tokens.
-   Use a strong, unique JWT signing secret outside source control.
-   If a signing secret has been exposed, rotate it and invalidate
    tokens signed with the old secret as appropriate.
-   Store refresh tokens securely. For browser clients, consider Secure,
    HttpOnly, SameSite cookies.
-   Use HTTPS outside local development.
-   Restrict Swagger and detailed actuator endpoints in production.
-   Never log passwords, raw tokens, or token hashes.

## 14. Roadmap status

  -----------------------------------------------------------------------
  Component / capability              Status
  ----------------------------------- -----------------------------------
  Auth Service project setup          Implemented

  PostgreSQL with Docker Compose      Implemented

  Flyway migrations                   Implemented

  User registration and login         Implemented

  Password hashing                    Implemented

  JWT authentication and `/me`        Implemented

  Refresh-token persistence and       Implemented; continue validating
  rotation                            end-to-end

  Logout / refresh-token revocation   Endpoint implemented; verify with
                                      Postman

  Actuator health and info            Enabled and verified locally

  Swagger / OpenAPI                   Configured

  Docker image build and container    Verified locally
  startup                             

  Final production-readiness review   Pending

  Product, Cart, Order, Payment       Not yet implemented/documented
  services                            
  -----------------------------------------------------------------------

## Quick start

``` bash
cd ~/workspace/Development/microservices-ecommerce/services/auth-service
cp .env.example .env   # only if .env does not exist
# Edit .env and verify its values
docker compose up --build -d
docker compose ps
docker compose logs -f auth-service
```

Then open: - Health: http://localhost:8081/actuator/health - Swagger:
http://localhost:8081/swagger-ui/index.html - API base URL:
http://localhost:8081
