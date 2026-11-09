# Sijill

Sijill is a web application that helps school networks keep their cloud-workspace files in good order. It connects to a school's workspace, checks files against configurable rules (public links, outside sharing, generic owners, owners leaving, duplicates and so on), raises issues for the people responsible, and tracks them through to resolution. A dashboard shows a health score for each site.

The full requirements are in [Sijill — Software Requirements Specification (SRS).pdf](Sijill%20—%20Software%20Requirements%20Specification%20(SRS).pdf).

## Tech stack

- Java 21, Spring Boot 4.1.2 (Maven)
- Spring MVC, Thymeleaf, Spring Security (OIDC sign-in)
- Spring Data JPA, PostgreSQL 16, Flyway migrations
- OpenPDF, for the weekly PDF report
- Lombok
- Testcontainers, for tests

## Repository layout

```
Sijjil/
├── Sijill — Software Requirements Specification (SRS).pdf
└── sijill/                      Spring Boot application
    ├── docker-compose.yml       Local PostgreSQL
    ├── pom.xml
    └── src/
        ├── main/java/qa/sijill/sijill/
        │   ├── audit/           Tamper-evident activity log, CSV exports
        │   ├── backup/          Backup runs and restore requests (approval flow)
        │   ├── checks/          Workspace connection, check runs, scheduled jobs
        │   ├── dashboard/       Health scores, incidents, weekly PDF report
        │   ├── files/           Files, permissions, classification, masking
        │   ├── issues/          Issue lifecycle, exceptions, follow-ups
        │   ├── notifications/   In-app notifications, e-mail delivery
        │   ├── organisation/    Organisation, sites, subjects
        │   ├── people/          Staff and roles
        │   ├── planning/        Planning module
        │   ├── rules/           Rule engine and the individual file checks
        │   ├── setup/           First-run bootstrap and dev seed data
        │   ├── shared/          Security, config, events, common utilities
        │   └── workspace/       Workspace connector abstraction
        └── main/resources/
            ├── application.yml
            ├── db/migration/    Flyway SQL migrations
            ├── static/          HTML prototype and assets
            └── templates/
```

Code is organised by feature module (each with `controller`, `service`, `repository`, `model` and `dto` packages). `ModuleStructureTest` checks the module boundaries.

## Getting started

### Prerequisites

- JDK 21
- Docker, for the local database
- Maven is optional because the repo includes the Maven wrapper

### 1. Start the database

```bash
cd sijill
docker compose up -d
```

This starts PostgreSQL on **localhost:5433** with database `sijill`, user `sijill` and password `sijill_dev`.

### 2. Run the app

```bash
# Windows
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev

# macOS / Linux
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The app runs on <http://localhost:8080>. Flyway creates the schema on startup.

The `dev` profile enables a password-less login page where you pick a role, and it loads demo data. It is **for development only**.

### 3. Run the tests

```bash
./mvnw test
```

Integration tests use Testcontainers, so Docker must be running.

## Configuration

Settings are in [application.yml](sijill/src/main/resources/application.yml).

| Setting | Env variable | Default |
|---|---|---|
| Database URL | `DB_URL` | `jdbc:postgresql://localhost:5433/sijill` |
| Database host / port / name | `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost`, `5433`, `sijill` |
| Database credentials | `DB_USERNAME`, `DB_PASSWORD` | `sijill` / `sijill_dev` |
| Scheduled jobs | `sijill.jobs.enabled` | `true` |

`sijill.jobs.enabled` controls the nightly check, reminders and message delivery. Set it to `false` to turn them all off.

### Sign-in (OIDC)

Staff sign in with their school workspace account, so Sijill stores no passwords. To enable OIDC, uncomment the `spring.security.oauth2.client` block in `application.yml` and set:

```
OIDC_CLIENT_ID
OIDC_CLIENT_SECRET
OIDC_ISSUER_URI
```

Once a registration exists, the app switches from the dev form login to OIDC automatically. Sessions expire after 8 hours.

### First-run bootstrap

On a new installation, uncomment the `sijill.bootstrap` block in `application.yml` to create the organisation and the first Leadership user:

```yaml
sijill:
  bootstrap:
    enabled: true
    org-name: Example School Network
    head-office-name: Head Office
    head-office-domain: example.edu
    admin-email: first.leader@example.edu
    admin-name: First Leader
```

This does nothing once an organisation exists.

## API overview

All endpoints are under `/api`.

| Area | Endpoints |
|---|---|
| Sites, subjects, doc types | `/api/sites`, `/api/subjects`, `/api/doc-types` |
| Connection | `/api/connection/{siteId}/test`, `/disconnect`, `/reconnect`, `/check`, `/checks` |
| Dashboard | `/api/dashboard/overview`, `/sites/{siteId}/score`, `/weekly-report.pdf`, `/incidents` |
| Files | `/api/files/mine`, `/{fileId}`, `/review-queue`, `/{fileId}/classification` |
| Issues | `/api/issues/{issueId}`, `/history`, `/fixed`, `/disagree`, `/help` |
| Follow-up | `/api/follow-up/counts`, `/{issueId}/keep-rule`, `/mark-fixed`, `/remind`, `/follow-up` |
| Exceptions | `/api/exceptions/issues/{issueId}`, `/{exceptionId}/end` |
| Notifications | `/api/notifications/unread-count`, `/{id}/read`, `/read-all` |
| Backup and restore | `/api/backup/sites/{siteId}/runs`, `/restores` (create, approve, reject, complete) |
| Audit | `/api/activity-log`, `/activity-log/verify`, `/activity-log/download`, `/exports/issues.csv` |

## Observability

- Spring Boot Actuator is enabled.
- Every log line carries a correlation ID (`%X{correlationId}`). Search the logs for one value to follow a request or background job through the controller, services, re-check and e-mail.

## Prototype

Static UI prototypes are in `sijill/src/main/resources/static/` (`Sijill-Prototype.html`, `Sijill-Welcome.html`). They are served by the running app.

## License

No license has been specified yet.
