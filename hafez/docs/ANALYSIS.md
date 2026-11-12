# Hafez: Phase 1 analysis

Status: **analysis only, no code changed in this phase** (the only additions are this file and `docs/ui-reference/`).
Date: 2026-11-10. Waiting for approval before Phase 2.

## 0. What was analysed

| Source | What it is | How it is used |
|---|---|---|
| `Hafez — Software Requirements Specification (SRS).pdf` (v1.0, 28 pages) | The file-governance product: 72 requirements (FR-1 to FR-12), NFR-1..20, SEC-1..12, DR-1..8 | **Spec A** |
| `School Management System-SRS.pdf` (22 pages, supplied in the request) | The academic platform: 49 FR, 15 NFR, 15 SEC, DAT, INF, INT, BR-001..014, UC-01..08 | **Spec B**. Treated as the same product under another name (confirmed by you). Spec A's appendix calls this "Phase 2: School portal". |
| `docs/ui-reference/admin-overview-dashboard.png` | Staff/admin overview dashboard | UI reference for staff portals |
| `docs/ui-reference/parent-portal-dashboard.png` | Parent dashboard | UI reference for the parent portal (Spec B) |
| The codebase (`hafez/`) | Spring Boot 4.1.2, Java 21, PostgreSQL 16, Flyway, Thymeleaf unused; front end is one static page (`static/index.html`, `static/app/app.js`, `static/app/theme.css`) | Subject of the analysis |

**Baseline (run on 2026-11-10):** `./mvnw test` passes, 65 tests, 0 failures, 0 errors; `node --check app.js` passes. There is no lint, type-check or front-end test tooling in the project (see section 4). Size: about 11,500 lines of Java, about 950 lines of front end, 31 tables in 4 Flyway migrations, 85 REST endpoints.

**How the two specs relate.** Spec A is what the code mostly implements. Spec B is almost entirely **not started**. Where they overlap: staff sign-in (A FR-5.1 / B FR-012), audit log (A FR-7 / B SEC-009, FR-049), lesson-plan review (A FR-10 / B FR-035, which B itself flags as undecided in OQ-07), exports (A FR-11 / B FR-045), role-based access (A FR-5.5 / B SEC-001), notifications (A FR-4 / B FR-047).

Status legend: **Done** = implemented and exercised by a test or an end-to-end run. **Done\*** = implemented, but not proven against the real external system (Google, mail). **Partial** = some of the requirement exists. **Missing** = nothing exists. **Wrong** = exists but contradicts the spec.

---

## 1. Traceability matrix, Spec A (Hafez Phase 1)

### 1.1 Checking and understanding (FR-1)

| ID | Requirement | Status | Files | Notes |
|---|---|---|---|---|
| FR-1.1 | Nightly full check at 02:00 | Done\* | `checks/job/ScheduledJobs`, `checks/service/CheckRunService` | Verified end to end against a stand-in Drive. Real Google only partly tested. Reads **My Drive** only, not shared drives. |
| FR-1.2 | Change feed, changes checked within 10 min | Partial | `ScheduledJobs.changeFeedCheck`, `workspace/google/GoogleDriveConnector.listChanges` | Runs every 5 min, but ignores `since` and re-lists the whole Drive (no Changes API). Fine for tests, risky for quota at scale. |
| FR-1.3 | Read text in memory, incl. Arabic/English OCR for scans | Partial | `files/integration/DocumentTextExtractor` | In-memory read of PDF, docx, xlsx, pptx, text. **No OCR**: scans go to Needs review. |
| FR-1.4 | Mask names, IDs, +974 phones, health words before any AI call | Partial | `files/service/MaskingService` | Masking exists and is tested, but there is **no AI client**, so nothing is masked-then-sent. Names are not masked by pattern. |
| FR-1.5 | Store a file profile (type, site, subject, flags, sensitivity, confidence, evidence, model version) | Partial | `files/model/FileProfile`, `LocalFileClassifier` | Everything stored except **subject**: the classifier never sets it (bug B-03). |
| FR-1.6 | Hard checks only raise sensitivity | Done | `LocalFileClassifier` | Unit-tested. |
| FR-1.7 | Low confidence or unreadable goes to Needs review | Done | `ClassificationService`, `LocalFileClassifier` | Verified end to end (scan and unclear file). |
| FR-1.8 | Locked, oversized, disallowed types skipped and listed in Recent checks | Partial | `CheckRunService.execute`, `GoogleDriveConnector.readContent` | Per-file failures are skipped and noted. Oversized/unsupported files go to Needs review instead of being skipped. No configurable disallowed-type list. |

### 1.2 Rules (FR-2)

| ID | Requirement | Status | Files | Notes |
|---|---|---|---|---|
| FR-2.1 | 10 starter rules as data | Done | `rules/service/StarterRules` | |
| FR-2.2 | Editing creates a new version; issues keep their version | Done | `rules/model/RuleVersion`, `RuleService` | |
| FR-2.3 | Live impact preview and message preview | Done | `RuleService.preview`, `/api/rules/preview` | Verified in the UI. |
| FR-2.4 | Draft, turn on, pause, delete with warning | Done | `RuleController`, `app.js` rules screen | |
| FR-2.5 | Network-wide change needs two people (IT proposes, another IT or Leadership approves) | Partial | `RuleService.changeableRule` | Pause/resume of network-wide rules is Leadership only. The two-person guard (proposer cannot approve own change) is not enforced or tested. |
| FR-2.6 | New check type = one new class | Done | `rules/check/FileCheck`, `CheckRegistry` | Registry fails at start-up on a missing type. |

### 1.3 Issues and follow-up (FR-3)

| ID | Requirement | Status | Files | Notes |
|---|---|---|---|---|
| FR-3.1 | One issue per file per broken rule | Done | `RuleEngineService` | |
| FR-3.2, 3.3 | Open and closed statuses | Done | `issues/model/IssueStatus` | |
| FR-3.4 | Only a re-check closes; "I've fixed it" re-checks within 2 min; tell the owner what is still wrong | Done | `IssueWorkflowService`, `FileService.refreshFromWorkspace` | Verified in the UI. |
| FR-3.5 | Reminder after reminder days; Needs follow-up one school day later | Done | `IssueFollowUpService` | |
| FR-3.6, 3.7 | Disagree and help flows (keep rule, mark fixed, remind, exception) | Done | `FollowUpController`, `ExceptionService` | Covered by `IssueLifecycleTest`. |
| FR-3.8 | Exceptions with reason and end date; expiry tells the owner | Done | `ExceptionService.expireDue` | |
| FR-3.9 | Coordinator answers saved as labelled feedback | Partial | `ClassificationService.classifyManually` | The answer is saved on the profile (`classifiedBy`). There is no separate labelled-feedback store for improving the checker. |

### 1.4 Notifications (FR-4)

| ID | Requirement | Status | Files | Notes |
|---|---|---|---|---|
| FR-4.1 | Message: what, why, how, "Hafez changed nothing" | Done | `IssueNotificationListener` | |
| FR-4.2 | Recipient matrix | Partial | `IssueNotificationListener`, `IncidentService` | Owner, coordinator, principal, leadership paths exist. "IT paged" on connection loss and some matrix cells are untested. |
| FR-4.3 | Quiet hours 20:00-07:00 and Fri-Sat | Done | `IssueNotificationListener` | |
| FR-4.4 | Language choice and **per-issue or daily summary** | Partial | `Person.digestMode` | Language works. `digestMode` is saved and shown but **never used**: no daily summary is built (bug B-06). |
| FR-4.5 | Email from a dedicated sender; every message logged | **Missing** (email) | `notifications/integration/OutboundMailer` | Messages are logged, but `OutboundMailer` is an interface with **no implementation**: no email is ever sent. |
| FR-4.6 | Header bell with unread count and Mark all read | Partial | `NotificationController`, `app.js` | Count is a sidebar badge; there is no header bell or dropdown. |

### 1.5 Users, roles and access (FR-5)

| ID | Requirement | Status | Files | Notes |
|---|---|---|---|---|
| FR-5.1 | OIDC sign-in plus MFA, no Hafez passwords | Partial | `shared/security/SecurityConfig` | OIDC works with Google. **MFA is not enforced or checked by the app** (left to the identity provider). A passwordless `dev` login exists in the `dev` profile only. |
| FR-5.2 | Only the school domains accepted; "not set up" message | Done | `people/service/CurrentUserService` | Exact SRS wording. |
| FR-5.3 | Generic accounts cannot sign in | Done | `Person.canSignIn` | |
| FR-5.4 | Roles and sites from directory groups; overrides logged | Done\* | `PeopleService.syncDirectory`, `RoleGroup` | The Gmail test connector has no directory (`getPeople` returns empty), so group mapping is untested against real data. |
| FR-5.5 | Every permission enforced on the server per the matrix | Partial | `shared/security/AccessPolicy` | Enforced in services. **No per-role/per-endpoint test matrix** (also required by SRS 9.1). |
| FR-5.6 | Leaving triggers R8; Left removes access and moves open issues to the coordinator | Partial | `PersonStatusChanged`, `RuleEngineService` | R8 and access removal work. Moving open issues to the coordinator on Left is unverified. |
| FR-5.7 | IT sync, search and filter, edit, Send welcome | Partial | `PeopleController`, `app.js` people screen | No search or filter, and no way to add a person (they are added by editing `application.yml`). |

### 1.6 Screens (FR-6)

| ID | Requirement | Status | Notes |
|---|---|---|---|
| FR-6.1 | Teacher: My files (cards with steps), My planning, The rules, Help | Partial | Issues are a table, not cards. **The rules** and **Help** pages are missing. "My planning" exists as Lesson plans. |
| FR-6.2 | Coordinator: Follow-up (filters, search, mark all), Planning review, My subject, People, Exceptions, Activity log | Partial | Missing: search, **Exceptions list**, **Activity log**, My subject. |
| FR-6.3 | Principal: My school (score badge, daily trend, four counts, issues by rule), Notifications, Planning review, Exceptions, Activity log | Partial | Missing: issues by rule, **Exceptions**, **Activity log**. |
| FR-6.4 | School IT: Overview, Rules, People, Settings, Activity log | Partial | **No IT Overview** (IT lands on Rules). |
| FR-6.5 | Leadership: Overview with Run first check, six tiles, schools table, **Urgent right now**, weekly report, **accuracy card**; Incidents; Exceptions; Rules; Activity log | Partial | Missing: Run first check on Overview, Urgent right now, accuracy card, **Exceptions**. |
| FR-6.6 | File details with evidence, sharing, **each rule's status, message sent**, history, "Hafez only reads" note | Partial | Rule status per file, messages sent and the read-only note are missing. |
| FR-6.7 | Empty, not-yet-checked, disconnected, skipped, paused-rule, leaver, quiet-hours states; confirm destructive actions | Partial | Empty states and some confirms exist; no disconnected banner (see FR-9.1). |
| FR-6.8 | Bilingual welcome page | **Missing** | The prototype welcome page was moved to `docs/`; nothing serves it. |

### 1.7 History, score, settings, planning, exports, backup (FR-7 to FR-12)

| ID | Requirement | Status | Files | Notes |
|---|---|---|---|---|
| FR-7.1 | Per-file history incl. messages and actions | Partial | `FileService.details`, `FileSnapshot` | Snapshots only; messages and actions are not on the file page. |
| FR-7.2 | Activity log of every action with before/after | Done | `audit/service/ActivityLogService` | |
| FR-7.3 | Append-only, hash-chained, **daily anchor in the backup vault** | Partial | `ActivityLogService.verifyChain` | Chain and verify work. No daily anchor. Nothing prevents the app's DB role from updating or deleting log rows (SEC-8). |
| FR-7.4 | Filter and CSV download by role | Partial | `ActivityLogController` | API filters exist; the UI has no filter controls. |
| FR-8.1-8.3 | Score formula, bands, daily snapshot, never per person | Done | `dashboard/service/ScoreService` | Unit-tested. |
| FR-9.1 | Connection: status, domain, scopes, schedule, last full check, Test, Run now, Disconnect (red banner on every page), Reconnect | Partial | `ConnectionController`, `app.js` | Status, test, run, disconnect, reconnect work. Domain, scopes and schedule are not shown. **No red banner on every page.** |
| FR-9.2 | Sites and folders: rename, add, edit, remove; standard folders | Partial (Should) | `SiteService` | Add and list only. No folder management. |
| FR-9.3 | Roles: rename, permissions, group, member count | Partial (Should) | `PeopleService.saveRoleGroup` | Group mapping only. |
| FR-9.4 | 14 document types, one-click sensitivity, new type needs examples | Done (Should) | `SettingsService` | |
| FR-9.5 | Messages settings: language, delivery, quiet hours, sender, reminder defaults | Partial | `SettingKeys` | Only sender address and a display label are settings. |
| FR-9.6 | Privacy and data: retention 1/2/5 years, AI service, masking, log download | Partial | `SettingKeys.LOG_RETENTION_YEARS` | Key exists; retention is not enforced by any job. |
| FR-10.1-10.4 | Planning review (submit, approve/return, statuses, reminder, scoped views) | Done | `planning/*` | Thursday reminder job exists. |
| FR-11.1 | Name-free downloads | Done | `audit/service/ExportService`, `NameMasker` | |
| FR-11.2 | IT unmasks one export for a logged reason | Partial (Should) | `ExportService` | API only; no UI. |
| FR-11.3 | Footer with who, when, sensitivity | Done | `ExportService.footer` | CSV footers only; confirm PDF. |
| FR-11.4 | Weekly report PDF every Sunday, per school and network | Partial | `dashboard/service/ReportService` | On-demand PDF only. **No Sunday job**, no per-school automatic run, not emailed. |
| FR-11.5 | Adoption measures per school | **Missing** (Should) | | |
| FR-12.1-12.3 | Hourly and nightly backup copy to the vault, manifests, checksum verification | **Missing** | `backup/service/BackupService` | Only job-run bookkeeping (`start`/`finish`); **nothing is copied anywhere**. |
| FR-12.4 | Restore request, IT approves (two people for a whole drive) | Partial | `RestoreService` | Request, approve, reject, complete flow exists. It records the request; it does not restore. |
| FR-12.5 | Nightly deletion-drift flag | **Missing** (Should) | | |
| FR-12.6 | Monthly and termly test restores | **Missing** | | |

### 1.8 Non-functional and security (Spec A sections 5 and 6)

| ID | Requirement | Status | Notes |
|---|---|---|---|
| NFR-1, 5 | 60,000 files in 4 h; 1 million files scale | Missing (untested) | No load test. Whole-Drive listing and `max-files` guard are not designed for this. |
| NFR-2 | Re-check under 2 min | Done | Synchronous single-file re-check. |
| NFR-3 | Change detection within 10 min | Partial | See FR-1.2. |
| NFR-4, 6, 7 | Screen load, availability, resilience | Untested | |
| NFR-8 | **0 writes to school files; failing write test on every release** | Partial | The connector interface has no write methods and the token is `drive.readonly`. The required automated **write-attempt test is missing**. |
| NFR-9 | 0 Restricted files reported fine | Partial | Unreadable and low-confidence files route to Needs review, but there is no labelled test proving it. |
| NFR-10 to 14 | Recall, precision, type accuracy, Arabic parity, alert quality | **Missing** | No labelled synthetic set, no held-back evaluation, no baseline report. Classifier is keyword rules (`local-rules-1`). |
| NFR-15 | Arabic first, RTL, Arabic-Indic digits, text in resource files | **Missing** | The UI is English, left-to-right, with strings hard-coded in `app.js`. Only issue text and rule steps follow the language setting. |
| NFR-16 | WCAG 2.1 AA | Unverified | No audit. Known: icon placeholders carry no labels, colour contrast unchecked. |
| NFR-17 | Configuration over code | Partial | Rules, doc types and roles are data; timings are constants. |
| NFR-18 | Connector and AI interfaces, tests, README | Partial | Connector interface, tests and README exist. No AI interface. |
| NFR-19 | IT health dashboard | **Missing** | |
| NFR-20 | Usage metering per school | **Missing** | Not needed until an AI service exists. |
| SEC-1 | Read-only scopes, write test, keys in a vault, 90-day rotation | Partial | Read-only scope yes; rest missing. |
| SEC-2 | MFA, phishing-resistant keys, new-device alerts, **8 h / 30 min idle** sessions | Partial | 8 h session only. **No idle timeout for IT and Leadership.** No new-device alerts. |
| SEC-3 | No shared accounts | Done | |
| SEC-4 | Server-side checks, **row-level security**, per-role access tests | Partial | Server-side checks yes. **No PostgreSQL RLS** and no cross-school access tests. |
| SEC-5, 6 | Exam-leak and deletion detection | Done\* | R6/R10, bulk-deletion incident; the backups half is missing (FR-12). |
| SEC-7 | Masking before AI | Partial | No AI yet. |
| SEC-8 | Hash chain, daily anchor, DB role cannot update or delete | Partial | Chain only. |
| SEC-9 | Isolated readers, size and type limits | Partial | Size limit yes; readers run in the app process. |
| SEC-10 | Parameterised queries, CSRF, secure cookies, **strict CSP, rate limits, WAF** | Partial | JPA parameters and CSRF yes. **No CSP, no HSTS, no rate limiting.** |
| SEC-11 | IT insider misuse | Partial | Masked evidence and counts only, but logged views of Restricted details are not implemented. |
| SEC-12 | Data in Qatar | Open | Hosting decision, and Drive data processing location (see Open Questions). |

**Spec A totals (72 FR, counted row by row; FR-12.1 to 12.3 count as three):** Done 30, Done\* 2 (FR-1.1, FR-5.4), Partial 32, Missing 8 (FR-4.5, 6.8, 11.5, 12.1, 12.2, 12.3, 12.5, 12.6), Wrong 0.

---

## 2. Traceability matrix, Spec B (School Management System)

Almost nothing exists. Rows are grouped; every ID in a group has the same status unless noted.

| IDs | Area | Status | What exists / what is missing |
|---|---|---|---|
| FR-001 to FR-005 | Public bilingual website, content editing, notices, archive | **Missing** | No public pages (all pages require sign-in), no CMS, no notices. |
| FR-006 | Add schools, switch on/off | Partial | `Site` create/list exists (Leadership). No Arabic names, code, logo, contacts, on/off switch. |
| FR-007 | Module switches per school | **Missing** | |
| FR-008 | SuperAdmin manages school Admin accounts | **Missing** | |
| FR-009 | Admin creates, edits, deactivates users | Partial | `people` table and edit of role/status. No create, no school-unique ID, no deactivate-with-records rule. |
| FR-010, 011 | Narrow permissions per user; student-parent links | **Missing** | |
| FR-012 | Staff SSO + MFA only | Partial | SSO yes; MFA not enforced (see FR-5.1). |
| FR-013 | Parent/student local account from invitation, password, second factor | **Missing** | Conflicts in spirit with A FR-5.1 "no Hafez passwords" (staff only). See Open Question 4. |
| FR-014 | Spreadsheet user import | **Missing** (Optional) | |
| FR-015 | Search and filter users | **Missing** | |
| FR-016 to FR-019 | Sessions, terms, classes 1-12, sections, subjects, assignments, enrolment, school days, periods | **Missing** | `Subject` exists as a name only. |
| FR-020, 021 | Timetable with clash refusal; role-specific PDF | **Missing** | |
| FR-022, 023 | ID card templates, printing, photos | **Missing** | |
| FR-024 to FR-027 | Attendance: schedule, recording, correction window, summaries | **Missing** | |
| FR-028 to FR-034 | Assessments, marks grid, Excel import, submit and lock, result cards, publish, reports | **Missing** | |
| FR-035 | Lesson plans | Partial | `planning` module reviews weekly plan **links**; SMS wants plan records (topic, objectives, status). Undecided (OQ-07 / INT-008). |
| FR-036, 037 | Plan overview; study materials upload/link | **Missing** | |
| FR-038 to FR-044 | Parent and student dashboards, meetings, reminders, school dashboard, network overview | **Missing** | A Leadership overview exists for file health, not school operations. |
| FR-045 | Every list exports PDF and CSV | Partial | Issues CSV, activity-log CSV, weekly PDF only. |
| FR-046 | Search and filters on every list | **Missing** | |
| FR-047 | In-app and e-mail notifications, unread count | Partial | In-app yes; e-mail missing (FR-4.5). |
| FR-048 | Language choice remembered | Partial | Stored; not applied to the interface. |
| FR-049 | History of changes to marks, attendance, publication, permissions | Partial | Audit log exists and records permission and role changes; marks and attendance do not exist yet. |
| NFR-001 to 004 | Arabic default and RTL, dates, browsers, WCAG | Missing / see A | Same gap as NFR-15 and NFR-16. **Digits conflict, see Open Question 5.** |
| NFR-005 to 015 | Speed, capacity, batch, usability, retries, config, monitoring, role tests, CSV in Excel, growth, replaceable parts | Mostly missing | Replaceable parts: connector interface only. |
| DAT-001 to 006 | School on every record, files outside DB, Unicode, retention, versioned scripts, export | Partial | `org_id` and `site_id` on tables, Flyway scripts, UTF-8 text. No RLS, retention job or school export. |
| BR-001 to 014 | Business rules | Missing | Apply as each module is built. |
| UC-01 to UC-08 | Use cases | Missing | Drive acceptance tests per module. |
| SEC-001 to SEC-015 | Spec B security | Partial | Overlaps A: SEC-001/003/009 partly done; **SEC-005 (parent passwords, lockout), SEC-006 (30 min idle), SEC-010 (upload scanning), SEC-011 (photos), SEC-013 (ASVS/pen test)** missing. |
| INF-001 to 008, INT-007 to 010 | Hosting, backups, deployment; Hafez boundary | Partial | INT-007 (no writes) holds by design. Hosting and pipeline not started. |

**Spec B totals (49 FR):** Done 0, Partial 8 (FR-006, 009, 012, 035, 045, 047, 048, 049), Missing 41.

---

## 3. Bug list

| ID | Location | Symptom | Root cause | Severity |
|---|---|---|---|---|
| B-01 | `oauth2_authorized_client` (V3), `GoogleDriveConfig` | Google access and **refresh tokens are stored unencrypted** in PostgreSQL | `JdbcOAuth2AuthorizedClientService` stores token bytes as-is | **High** |
| B-02 | `index.html`, `SecurityConfig.securityFilterChain` | The sign-in page and welcome link required by A section 4.1 can never be seen; unauthenticated visitors are redirected straight to Google | `anyRequest().authenticated()` covers the static app | Medium |
| B-03 | `LocalFileClassifier`, `CheckRunService.processFile` | Auto-classified files have no subject, so a **subject coordinator cannot browse them** (open by URL works) and subject scoping does not apply | The classifier never sets `subjectId`; `FileService.visibleFiles` filters by subject | **High** |
| B-04 | `FileService.visibleFiles` | Coordinators with a subject see an empty Files list for files with unknown subject, though `AccessPolicy` lets them classify those files | Inconsistent rules between list and detail | Medium |
| B-05 | `CheckRunService.reportConnectionLostIfDown` | A **misconfiguration** (missing folder ID, account not signed in) raises "connection lost for over an hour" incidents and notifications | Any failed run with no success in the last hour is treated as a lost connection | Medium |
| B-06 | `Person.digestMode` | "Daily summary" can be chosen but does nothing | Not implemented (FR-4.4) | Medium |
| B-07 | `GoogleDriveConnector.listChanges` | Every 5 minutes the whole Drive is re-listed, ignoring `since`; at 5,000 files that is hundreds of API calls per run | No use of the Drive Changes API | Medium |
| B-08 | `GoogleDriveConnector.getFile` | A re-check ("I've fixed it") calls Drive once per configured account until found | Account lookup is by trial | Low |
| B-09 | `people.role`, `people.status` columns | A person saved with an invalid role string (`IT`) made `/api/me` fail with a server error that the UI first showed as a sign-in page | No `CHECK` constraint on the enum columns; entity loading throws | Medium |
| B-10 | `app.js` (all screens) | Every label is English and left-to-right, although the people are Arabic-first | Strings hard-coded, no i18n layer (NFR-15) | Medium |
| B-11 | `OutboundMailer` | No e-mail leaves the system though the UI and README imply messages are delivered | No implementation | **High** for FR-4.5 |
| B-12 | `application.yml` | Real personal e-mail addresses are committed in `bootstrap` and `extra-people` | Test setup written into the shared config | Low |
| B-13 | `SecurityConfig` | No `Content-Security-Policy`, HSTS or rate limiting | Not configured (SEC-10) | Medium |
| B-14 | `application.yml` `server.servlet.session.timeout: 8h` | IT and Leadership sessions never expire on idle (SEC-2 wants 30 min) | Single session timeout | Medium |
| B-15 | `BackupService` | Reads like a backup service but copies nothing | Only records run state | **High** for FR-12 |
| B-16 | `ScheduledJobs` | No weekly report job, so Sunday reports are never produced | Missing job (FR-11.4) | Medium |
| B-17 | Test suite | No test proves another school's or subject's IDs are refused for each role | Missing per-role access matrix (A SRS 9.1, B NFR-012) | Medium |

---

## 4. Code-quality issues

- **Front end is one 600-line file** (`app.js`) mixing API access, state, templating, actions and forms. Needs splitting by layer: `api`, `state`, `views/<role>`, `components`.
- **No lint, formatter or type-check** for either language (no ESLint/Prettier, no Checkstyle/Spotless). The front end is untyped JavaScript with no tests.
- **Role labels, navigation and landing routes are duplicated** between `app.js` constants and server enums; they will drift as SMS roles are added.
- **Hard-coded values**: classifier confidence tiers and keyword lists are inline constants (`LocalFileClassifier`); quiet-hour times, reminder times and job cron strings are constants rather than settings (NFR-17).
- **Dead or leftover code**: `docs/prototype*` are unreferenced prototypes kept on purpose; `Hafez-Welcome.html` is orphaned (see FR-6.8).
- **Missing error handling**: `ACT` handlers in `app.js` assume a JSON body (`r.message`); a few returned `void` bodies were fixed during this session, others may remain.
- **Test gaps**: no controller/security tests besides `DevLoginTest`, no Google connector contract test beyond a stand-in server, no front-end tests.
- **Naming and structure**: the SRS describes 10 controllers, 14 services, 23 entities; the code has more classes in more feature modules. This is fine, but the SRS section 8 should be updated rather than the code forced to match.
- **Secrets**: no secrets in code (client ID and secret come from environment variables). Tokens at rest need encryption (B-01).

---

## 5. UI gap list

Reference images are in `docs/ui-reference/`. The current UI is `static/index.html` + `app/theme.css` (maroon accent on a near-black striped sidebar, placeholder circle icons, no charts except one line chart on the school page).

### 5.1 Staff portals, reference: `admin-overview-dashboard.png`

| Area | Reference | Current | Fix needed |
|---|---|---|---|
| Colour system | Deep **navy** sidebar, **blue** primary and links, soft blue-grey canvas, green/amber/red status | Maroon primary, near-black sidebar, grey canvas | Introduce a token file (colours, radii, shadows, spacing, type scale). Confirm palette (Open Question 6). |
| Brand block | "Hafez" large, Arabic "سجل" under it, "The Register" | Small wordmark with tag line | Rebuild brand block. |
| Navigation | Overview, Schools, File Monitoring, Issues (red count), Compliance Rules, Follow-up, divider, Audit History, People & Access, Settings; **System Operational** card with last scan time and skyline art | Role-specific plain list, no icons that mean anything, no status card | Real icon set, grouped nav, count badges, system status footer. |
| Top bar | Global **search**, **عربي \| EN** toggle, bell with count, user menu | No top bar | Add top bar; wire search (FR-046) and language switch (FR-048). |
| Hero | Greeting ("Good evening, Administrator") with skyline imagery | None | Greeting banner per role. |
| KPI cards | 4 cards: icon, big number, delta vs last week, **sparkline** (Files Monitored, Open Issues with urgent/normal split, Network Health with band pill, Pending Follow-ups) | Plain numbers, empty icon circles | New KPI card component with sparkline. |
| School Performance | Grid of school cards: dot, score %, progress bar, file count, sparkline | Table | Card grid on Overview. |
| Issues Trend | 30-day area/line chart, Total and Urgent, % change chip | Single line on the school page | Two-series chart component. |
| Critical Alerts | Panel with severity icons, file and school, relative time, count badge, "View all alerts" | Incidents page only | Panel on Overview (uses incidents API). |
| Recent Issues | Table: ID (SIJ-1042), file, school, rule, severity pill, status pill, age | Issue table without ID, school, rule, age | Add columns; issue reference numbers need a sequence (new field). |
| Compliance Overview | Donut: compliant / needs attention / critical, overall health, delta vs last month | Not present | Donut component (data from `ScoreService`). |
| Quick Actions | Dark panel: Run On-Demand Scan, Export Report, Manage Exceptions, View Audit Log | Buttons on separate pages | Panel on Overview. |
| Language / direction | Toggle between Arabic and English | English only, LTR | i18n layer with RTL (NFR-15). |

### 5.2 Parent portal, reference: `parent-portal-dashboard.png` (Spec B)

| Area | Reference | Current | Fix needed |
|---|---|---|---|
| Whole screen | "education above all" brand; sidebar (Dashboard, School Overview, Reports, Files & Resources, Staff Directory, Settings, Help & Support, support card); welcome hero with child photo; KPIs (Attendance 92%, Overall Performance 4.6/5, Behaviour 98%, Upcoming Events); Your Child's Progress chart with subject tabs; School Announcements; Quick Access (View Reports, Access Files, Staff Directory, OneDrive, School Calendar, Support); Recent Activity; footer banner | **No parent role or screens exist** | Build with the parent portal (Spec B FR-038 to FR-043). Note the reference shows **OneDrive**; confirm workspace (Open Question 8). Behaviour score and "overall performance" are not in the SRS data model. |

### 5.3 Cross-cutting

Responsive layouts at 360 to 1920 px (NFR-003), loading / empty / error states for every card, keyboard and screen-reader support, 44 px touch targets, visible focus, colour contrast of status pills (the reference's amber-on-white pills need checking against AA).

---

## 6. Open questions (need your answer, not guessed)

1. **Role model.** Spec A has Teacher, Coordinator, Principal, School IT, Leadership. Spec B has SuperAdmin, Admin, Teacher, Parent, Student, and itself asks (OQ-12) whether Principal and Coordinator are needed. Proposed mapping: Leadership = SuperAdmin, School IT = Admin, keep Coordinator and Principal, add Parent and Student (and Registrar from A's appendix). OK?
2. **Scope order.** There are ~74 requirements to finish in Spec A and 41 of 49 not started in Spec B. Which first: complete Hafez Phase 1 (A) before any SMS module, or interleave?
3. **Front-end approach.** Keep plain HTML/JS (restructured into modules plus ESLint and tests), or move to a framework? The SMS adds dozens of forms and a public CMS; "keep the stack" says plain JS, but this is a real cost decision.
4. **Parent and student accounts.** B FR-013 / SEC-005 require **local passwords + MFA + invitation by e-mail/SMS**, while A FR-5.1 says "no Hafez passwords". Confirm local accounts are wanted for parents and students only, and which e-mail and SMS providers to use (nothing is sending e-mail today, bug B-11).
5. **Digits.** A section 4.1 says **Arabic-Indic digits**; B NFR-002 says **Western digits** (its OQ-24). Which?
6. **Palette.** The staff reference is navy and blue; the parent reference is navy with a maroon accent; the current theme is maroon. Which palette is the product's: navy/blue for everything, or maroon accents as in the parent screen?
7. **Lesson plans (OQ-07 / INT-008).** Keep Hafez's "planning review of drive links" (done), or build the SMS lesson-plan records (FR-035), or both?
8. **Workspace.** The parent reference shows OneDrive; the connector is Google Drive. Is each school on Google, Microsoft, or mixed? A Microsoft connector would be a second implementation.
9. **Backup vault (FR-12).** Needs a second, in-country, write-once storage provider. Which provider, or defer FR-12.1-12.3 and FR-12.6 until one is chosen?
10. **AI service.** None is wired (classification is keyword rules). Keep rules-only for now (so NFR-10 to 14 are measured on the rules classifier), or choose an in-country model?
11. **Hosting vs data location.** SEC-012 requires data in Qatar; Google Drive processing is outside that. Is this accepted for the test phase?
12. **Arabic text.** Who supplies the Arabic interface strings and the Arabic fix steps for new rules?
13. **SMS open questions** that block modules (not resolved by me): grading scheme (OQ-05, blocks marks and result cards), attendance daily vs per period (OQ-06, blocks attendance), video meeting links (OQ-10), who edits the website (OQ-11), retention periods (OQ-14), whether SuperAdmin sees student-level data (OQ-15).

---

## 7. Proposed implementation plan

Order: critical bugs, then missing and partial Spec A requirements, then the SMS modules, then UI, then refactoring. UI and refactoring work on a screen happens when that screen is touched, so the new shell lands early (step P1) and later modules reuse it.

**P0. Critical and high bugs, security (no open question needed)**
1. B-01 encrypt stored tokens (AES-GCM, key from environment). *S*
2. B-02 public sign-in and welcome pages (FR-6.8) outside authentication. *S*
3. B-03/B-04 set the subject during classification and make list and detail rules agree. *M*
4. B-05 distinguish "not configured" from "connection lost". *S*
5. B-09 add `CHECK` constraints for role and status. *S*
6. B-13/B-14 CSP, HSTS, rate limiting, 30-minute idle for IT and Leadership. *M*
7. B-17 per-role access-test matrix (every endpoint with another school's and subject's IDs); NFR-8 automated write-attempt test. *M*
8. B-12 move test accounts out of `application.yml` into an untracked local profile. *S*

**P1. Shared front-end foundation (needs answers 3 and 6)**
9. Token file, component library (button, input, card, KPI, chart, table, pill, badge), shell (brand, nav groups, top bar with search, language, bell), i18n with RTL and resource files, accessibility baseline. Split `app.js` into modules, add ESLint and front-end tests. *L*

**P2. Complete Hafez Phase 1**
10. FR-4.4 daily summary, FR-4.5 e-mail implementation, FR-4.6 header bell, B-06/B-11. *M*
11. FR-11.4 Sunday weekly report job, FR-11.5 adoption measures, FR-11.2 unmask UI. *M*
12. Missing screens: Exceptions, Activity log for Coordinator and Principal, Teacher "The rules" and Help, IT Overview, Leadership urgent/accuracy panels, file-history messages (FR-6.1 to 6.7, 7.1), disconnected banner (FR-9.1). *L*
13. Settings gaps (FR-9.2, 9.3, 9.5, 9.6) and people search/filter/add (FR-5.7). *M*
14. FR-7.3 daily anchor and DB-role protection of log rows; FR-5.6 verification. *M*
15. NFR-10 to 14: labelled synthetic set plus evaluation report (separate script). *L*
16. FR-12 backup (blocked on answer 9). *L*
17. B-07 Changes API (before scaling). *M*

**P3. SMS (after answers 1, 2, 4, 5, 7, 8, 13). Each module ships with its UC acceptance test**
18. Foundation: roles, school scoping and RLS, module switches, users CRUD, parent-student links (FR-006 to 011, 013, 015). *L*
19. Website and notices (FR-001 to 005). *L*
20. Academic setup, enrolment, timetable (FR-016 to 021). *L*
21. Attendance (FR-024 to 027), marks and results (FR-028 to 034), lesson plans and materials (FR-035 to 037), ID cards (FR-022, 023). *L each*
22. Parent and student portals, meetings, dashboards (FR-038 to 044), shared functions (FR-045 to 049). *L*

**P4. Final verification**
23. Re-check every requirement, full build, lint and type-check, `docs/CHANGES.md`.

*S = under half a day, M = about a day, L = several days.*

---

**Stop point.** No code has been changed in this phase. Approve the plan (or reorder it) and answer the questions in section 6 that block what you want first. P0 does not depend on any open question and can start immediately.
