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
<<<<<<< HEAD
            ├── static/          Web UI (index.html, app/)
=======
            ├── static/          HTML prototype and assets
>>>>>>> f7086e57728570d0b6d7288afc1428e852b86cf6
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
<<<<<<< HEAD
| Google Drive folder to scan | `GOOGLE_DRIVE_ROOT_FOLDER_ID` | `root` (whole My Drive) |

`sijill.jobs.enabled` controls the nightly check, reminders and message delivery. Set it to `false` to turn them all off.

## School portal (teachers and students)

Besides checking files, Sijill has a school portal. School IT sets the school up under **Academic setup** (sections,
students, who teaches what, the weekly timetable) and **Assessments & results**, and publishes **Notices**.

| Role | What they do |
|---|---|
| School IT | Sections, enrolment, teaching assignments, timetable (a clash is refused and named), assessments, unlock marks with a reason, publish results, notices |
| Teacher | Today's lessons, attendance (same day only), marks (enter, submit to lock), study-material links, notices |
| Student | My day (timetable, notices, attendance, materials), My results (only after publishing, only their own) |

To try it with demo data run the app with the `dev` profile and sign in at `/dev/login` as `teacher`, `student` or `it`.
To add a student on a real installation, list them in `extra-people` with `role: STUDENT`, then enrol them under Academic setup.
What is and is not built is in `sijill/docs/CHANGES.md`.

### Reading Google Drive

Sijill reads documents from Google Drive through a **read-only** connector (`workspace/google/`). In this test setup it reads the whole My Drive of each Google account listed under `sijill.google.drive.accounts`. To limit it to one folder, set `GOOGLE_DRIVE_ROOT_FOLDER_ID` to the ID from that folder's URL, `drive.google.com/drive/folders/<ID>`.

1. Start the app and sign in **once** with each listed Drive account. Google asks for read-only Drive access; the app is unverified, so choose "Advanced" and then continue. The account's token is stored in the database so the nightly and five-minute checks keep working after you sign out.
2. Open **Connection** (Leadership or School IT) and press **Run a check now**.

What happens in a check: the connector lists the folder and its sharing, the classifier reads each document in memory (PDF, Word, Excel, PowerPoint, text; Arabic and English), the nine rule checks run, and issues are opened for the file owners. Document text is never stored: only the type, sensitivity, a confidence score and generic evidence lines such as "Contains 3 student ID numbers".

Things to know:
- If a listed account has not signed in, or its Drive cannot be read, the whole check fails, so nobody's files are wrongly recorded as deleted.
- Files that are only "shared with me" are not part of My Drive, so they are not read.
- A Drive with more than `max-files` files (5000) makes the check fail, because a partial list would make the missing files look deleted.
- Everyone except the listed Drive accounts counts as **outside the network** unless listed in `sijill.google.drive.internal-emails` or `internal-domains`.
- The Drive counts as a school shared drive for rule R1 (`drive-type: SHARED`). Rule R4 needs student records in a folder whose path starts with "Student Records".
- Scanned PDFs (no text) and unclear files go to the coordinator's **Review queue**.
- Sharing changes do not change a file's modified time, so the five-minute check re-lists the whole Drive. That is fine for a test account.
- A school deployment would use a service account with domain-wide delegation behind the same `WorkspaceConnector` interface.

=======

`sijill.jobs.enabled` controls the nightly check, reminders and message delivery. Set it to `false` to turn them all off.

>>>>>>> f7086e57728570d0b6d7288afc1428e852b86cf6
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

<<<<<<< HEAD
## Web UI

The app's front end is a single page in plain HTML and JavaScript, served by Spring Boot at <http://localhost:8080/>. It calls the same `/api` endpoints listed above and shows the screens each role is allowed to use:

| Role | Screens |
|---|---|
| Teacher | My issues (with fix steps, "I've fixed it", disagree, ask for help), my files, lesson plans, notifications |
| Coordinator | Follow-up, all issues (keep rule, remind, allow exception), review queue (classify files), files, plan review, people, restores |
| Principal | My school, issues, files, lesson plans (with weekly totals), restores, weekly PDF report |
| School IT | Rules (create, preview, pause, delete, approve), people and role groups, sites and settings, connection, restores, incidents, activity log |
| Leadership | Network overview and school trends, incidents, rules, sites, connection, activity log, weekly report |

The server decides what each person may do; the UI only chooses which screens to offer. Files are in `sijill/src/main/resources/static/` (`index.html`, `app/app.js`, `app/theme.css`).

The original clickable design mock-up (made-up data, no backend) is kept for reference in `sijill/docs/`.
=======
## Prototype

Static UI prototypes are in `sijill/src/main/resources/static/` (`Sijill-Prototype.html`, `Sijill-Welcome.html`). They are served by the running app.
>>>>>>> f7086e57728570d0b6d7288afc1428e852b86cf6

## License

No license has been specified yet.
