# Changes since the Phase 1 analysis

Date: 2026-11-10. Follows `docs/ANALYSIS.md`. Requested scope: the student and teacher portals (Phase 2), working
Leadership dashboard tiles, and accurate notifications per file type, for Leadership as well.

## Summary

1. **Notifications are now checked per file type and urgency**, including Leadership, with 13 integration tests.
2. **The Leadership overview is verified and usable**: its numbers are tested, it has a "Urgent right now" panel and a
   "Run the first check" button, and a one-site test setup can score the head office.
3. **School portal for teachers and students** (new `academic` module): sections and enrolment, teaching
   assignments, timetable with clash refusal, attendance, marks and results, notices, study materials, and a home
   screen for each role. School IT acts as the school Admin.

Test suite: **99 tests, 0 failures** (was 65). `./mvnw clean test`.

## 1. Notifications (Spec A FR-4.2, FR-3, SEC-5/6)

| Bug | Cause | Fix |
|---|---|---|
| A Restricted contact list never alerted Leadership | The classifier set "student data" only when the text said "student"; the leadership alert needs Restricted + student or health data | Every student-record type (marks, reports, behaviour, medical, special needs, contacts) is student data (`LocalFileClassifier`) |
| The exam-leak rule R6 could never fire for files read from Drive | Nothing ever set the exam date | The classifier reads the date from an exam paper (ISO, `15/12/2026`, `15 December 2026`, Arabic-Indic digits); a coordinator can set it when classifying; a coordinator's date is never overwritten (`ClassificationService`) |
| Connection-lost alerts went to coordinators and owners, not to IT | One audience for every incident | Audience per incident type (`IssueNotificationListener.on(IncidentRaised)`): exam leak and important deletion alert owner, coordinator, principal, leadership; "urgent still open a day" alerts principal and leadership; connection lost alerts principal, leadership and School IT |

`NotificationMatrixTest` walks the realistic combinations and checks owner, coordinator, principal and leadership each
time: public link or outside sharing on a Restricted mark book, contact list, medical record (urgent, leadership
alerted); Restricted file without personal data (urgent, leadership not alerted); public link on an Internal policy
(nothing); school file in a personal drive (normal: owner and principal only); exam shared before its date and after it;
deleting a policy; lost connection; urgent issue unresolved.

## 2. Leadership dashboard

- `LeadershipOverviewTest`: files checked, open, urgent, needing follow-up, fixed by staff and files in personal drives
  add up across schools and follow fixes; the head office is excluded; only Leadership may read it.
- Screen: tiles with explanatory notes, **Urgent right now** (open incidents with Resolve), **Run the first check** per
  school that has none, weekly report, schools table with the formula and bands.
- **Why the dashboard was empty on the Gmail test setup:** the overview counts scored schools and the head office is
  never scored, and the test has only a head office. `hafez.scoring.allow-head-office: true` (test setting, in
  `application.yml`) lets Leadership include it under **Sites & settings** (Include/Exclude button).

## 3. School portal (Spec B)

New module `qa.hafez.hafez.academic`, migration `V5__school_portal.sql`, role `STUDENT`.

| Requirement | Status |
|---|---|
| FR-017, FR-018 sections, enrolment, teaching assignments | Done (no academic sessions or terms setup; terms 1 to 3 are fixed) |
| BR-002 one section per student | Done, tested |
| FR-020, BR-003 timetable; clash refused and named (section, teacher, room) | Done, tested |
| FR-021 timetable per role | Done on screen; **PDF download not built** |
| FR-024, FR-025 today's schedule, attendance with default Present | Done, tested |
| FR-026, BR-008 teacher corrects same day only; Admin later with a reason | Done, tested (API); **Admin correction screen not built** |
| FR-027 attendance summaries | API only (`/api/attendance/sections/{id}/summary`); no screen, no export |
| FR-028 to FR-031, BR-005, BR-006 assessments, grid entry, all-or-nothing validation, submit and lock, Admin unlock with reason, teacher told | Done, tested; **Excel import not built** |
| FR-033, BR-007 publish per section; students see nothing before; unpublish needs a reason | Done, tested |
| FR-032 result cards (PDF), FR-034 school reports (PDF/CSV) | **Not built** |
| FR-037 study materials: https links only | Done, tested; **file upload not built** |
| FR-004, BR-009, BR-010 bilingual notices with dates and audience | Done, tested; no archive (FR-005) |
| FR-042 student dashboard | Done (timetable, notices, attendance, results, materials) |
| FR-049 history of marks, attendance, publication | Done through the audit log |
| BR-012 own data only; cross-school isolation | Done, tested (`SchoolPortalTest`) |
| FR-035 lesson plan records, FR-036, FR-022/023 ID cards, FR-040 meetings, FR-038/039 parent portal, FR-001 to 003 public website, FR-006 to 015 user admin, FR-013 local student accounts | **Not built** |

Roles: School IT is the school Admin (my proposed mapping, not yet confirmed). A student signs in the same way as staff
(school or Google account); the local password account with a second factor (FR-013) needs the open decision on
providers (ANALYSIS.md question 4). Add a student in `extra-people` with `role: STUDENT`, then enrol them under
**Academic setup**.

UI changes: teacher navigation (Today, Attendance, Marks, Study materials, Notices), student navigation (My day, My
results, Study materials, Notices), School IT (Academic setup, Assessments & results, Notices). A student is redirected
to their own screens; the rules list is refused for students.

## Bugs found and fixed while building

| ID | Cause | Fix |
|---|---|---|
| P-1 | Migration declared `SMALLINT` where entities use `int`; Hibernate schema validation refused to start | `INTEGER` in V5 |
| P-2 | Teacher's "share a link" offered every subject, so a valid-looking choice was refused | The form lists only the section and subject pairs the teacher teaches |
| P-3 | A student could open staff routes and read `/api/rules` | Router redirects students home; `RuleQueryService.list` refuses `STUDENT` |
| P-4 | Forms stacked every field full width | CSS for `.row` children |
| P-5 | A class compiled by the editor with an unresolved import was picked up by Maven | Clean builds (`mvn clean test`) before every verification |

## Not done / open

- Everything marked **Not built** above, and the Phase 1 gaps listed in `docs/ANALYSIS.md` (e-mail sending, daily
  summary, backup copies, MFA enforcement, idle timeout for IT and Leadership, content-security policy, token
  encryption, labelled accuracy evaluation).
- The Leadership "accuracy card" (FR-6.5) needs the evaluation set.
- UI reference match (navy theme, top bar, charts, hero) is still the planned P1 front-end work.
- Open questions in ANALYSIS.md section 6 are unchanged; the role mapping and the digits (Arabic-Indic or Western)
  are the most important.

## How to run and test

```powershell
.\mvnw.cmd clean test                      # 99 tests; needs Docker for PostgreSQL
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev   # demo school with students, timetable, marks
```
Dev sign-in: `http://localhost:8080/dev/login` (leadership, it, principal, coordinator, teacher, student).
On the real database the new migration (V5) applies automatically at start-up.

## Update: urgent list and who does what

- **Urgent list (Leadership overview).** The "Urgent" tile counted open urgent *issues* but the panel under it listed only
  *incidents*, which are rarer (exam leak, deletion, lost connection, or an urgent issue left a whole school day). So the
  tile could say 3 while the panel said "Nothing urgent". The panel now lists the urgent issues too (school, rule, status,
  open since; never file names): `GET /api/dashboard/urgent-issues`, tested against the tile.
- **Coordinator sets the school up.** Terms, classes and sections, enrolment, teaching assignments and the timetable are
  now Coordinator-only (School IT is refused). New `terms` table (V6), `PUT/GET /api/academic/terms`, "Academic setup"
  moved to the Coordinator menu.
- **Teacher sets the assessments and enters the grades.** A teacher creates assessments for the sections and subjects they
  teach, for a term the coordinator has created (Marks screen). School IT no longer creates assessments.
- **Unchanged (needs your decision):** School IT still unlocks submitted marks, publishes and unpublishes results,
  corrects attendance after the day, and runs notices.
- Tests: 101, 0 failures.
