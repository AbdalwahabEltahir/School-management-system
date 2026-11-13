package qa.sijill.sijill.setup.dev;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.academic.model.Assessment;
import qa.sijill.sijill.academic.model.AttendanceRecord;
import qa.sijill.sijill.academic.model.AttendanceStatus;
import qa.sijill.sijill.academic.model.Enrolment;
import qa.sijill.sijill.academic.model.Mark;
import qa.sijill.sijill.academic.model.Notice;
import qa.sijill.sijill.academic.model.NoticeAudience;
import qa.sijill.sijill.academic.model.PointEntry;
import qa.sijill.sijill.academic.model.PointSource;
import qa.sijill.sijill.academic.model.ResultPublication;
import qa.sijill.sijill.academic.model.Term;
import qa.sijill.sijill.academic.model.TeachingAssignment;
import qa.sijill.sijill.academic.repository.AssessmentRepository;
import qa.sijill.sijill.academic.repository.AttendanceRecordRepository;
import qa.sijill.sijill.academic.repository.EnrolmentRepository;
import qa.sijill.sijill.academic.repository.MarkRepository;
import qa.sijill.sijill.academic.repository.NoticeRepository;
import qa.sijill.sijill.academic.repository.PointEntryRepository;
import qa.sijill.sijill.academic.repository.ResultPublicationRepository;
import qa.sijill.sijill.academic.repository.TeachingAssignmentRepository;
import qa.sijill.sijill.academic.repository.TermRepository;
import qa.sijill.sijill.dashboard.model.HealthScoreSnapshot;
import qa.sijill.sijill.dashboard.repository.HealthScoreRepository;
import qa.sijill.sijill.dashboard.service.ScoreService;
import qa.sijill.sijill.organisation.model.Site;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.shared.util.SchoolCalendar;
import qa.sijill.sijill.support.model.SupportTicket;
import qa.sijill.sijill.support.model.TicketAudience;
import qa.sijill.sijill.support.model.TicketCategory;
import qa.sijill.sijill.support.model.TicketReply;
import qa.sijill.sijill.support.model.TicketStatus;
import qa.sijill.sijill.support.model.TicketUrgency;
import qa.sijill.sijill.support.repository.SupportTicketRepository;
import qa.sijill.sijill.support.repository.TicketReplyRepository;

/**
 * <b>Demo only.</b> Fills a school with about four months of believable history: attendance, weekly grades, recognition
 * points, notices, the file-health score and support tickets. Each school follows its {@link DemoSchools.Profile}, so a
 * strong school and a struggling one look different, and every school trends upward rather than declining.
 * Records are written straight to the tables with their original dates; the live rules are not involved.
 */
@Component
@RequiredArgsConstructor
public class DemoHistory {

    /** About four months of school weeks, ending with the current week. */
    static final int WEEKS = 17;

    private final AttendanceRecordRepository attendance;
    private final PointEntryRepository points;
    private final AssessmentRepository assessments;
    private final MarkRepository marks;
    private final ResultPublicationRepository publications;
    private final TeachingAssignmentRepository assignments;
    private final EnrolmentRepository enrolments;
    private final NoticeRepository notices;
    private final TermRepository terms;
    private final HealthScoreRepository snapshots;
    private final SupportTicketRepository tickets;
    private final TicketReplyRepository replies;
    private final Clock clock;

    private static final double[] ABILITY = {.88, .74, .60, .81, .67, .86, .77, .52, .71, .93, .63, .79};
    private static final String[] PRAISE = {"Helped a classmate", "Excellent homework this week", "Great class presentation",
            "Kindness in class", "Led the reading group", "Improved effort in maths", "Tidy and prepared every day"};

    // ========================================================================= attendance, points, grades

    /** Attendance, recognition points, weekly grades and published Term 1 results for every class of the school. */
    @Transactional
    public void academics(Site site, DemoSchools.Profile p, Person recorder, Person publisher) {
        Random rnd = new Random(("academics-" + p.letter()).hashCode());
        LocalDate today = LocalDate.now(clock.withZone(SchoolCalendar.ZONE));
        List<Enrolment> all = enrolments.findBySiteId(site.getId());
        Map<UUID, List<Enrolment>> bySection = all.stream().collect(Collectors.groupingBy(Enrolment::getSectionId));
        List<TeachingAssignment> teaching = assignments.findBySiteId(site.getId());
        List<UUID> teachers = teaching.stream().map(TeachingAssignment::getTeacherId).distinct().toList();

        // ---- attendance and the automatic points that follow it (up to yesterday; today belongs to the live app)
        int days = WEEKS * 7;
        List<AttendanceRecord> records = new ArrayList<>();
        List<PointEntry> pointRows = new ArrayList<>();
        for (int back = days; back >= 1; back--) {
            LocalDate date = today.minusDays(back);
            if (!SchoolCalendar.isSchoolDay(date)) {
                continue;
            }
            double t = 1.0 - (double) back / days;                       // 0 = oldest day, 1 = yesterday
            double absence = p.absenceStart() + (p.absenceEnd() - p.absenceStart()) * t;
            Instant at = date.atTime(7, 40).atZone(SchoolCalendar.ZONE).toInstant();
            for (List<Enrolment> roster : bySection.values()) {
                for (int i = 0; i < roster.size(); i++) {
                    double chance = absence * (i == 1 ? 2.4 : i == 4 ? 1.6 : 0.8);   // two students are often away
                    double r = rnd.nextDouble();
                    AttendanceStatus status = r < chance ? AttendanceStatus.ABSENT
                            : r < chance + .02 ? AttendanceStatus.LEAVE : AttendanceStatus.PRESENT;
                    AttendanceRecord rec = new AttendanceRecord();
                    rec.setOrgId(site.getOrgId());
                    rec.setSiteId(site.getId());
                    rec.setSectionId(roster.get(i).getSectionId());
                    rec.setStudentId(roster.get(i).getStudentId());
                    rec.setAttendanceDate(date);
                    rec.setStatus(status);
                    rec.setRecordedBy(recorder.getId());
                    rec.setCreatedAt(at);
                    records.add(rec);
                    if (status == AttendanceStatus.PRESENT) {
                        pointRows.add(point(site, roster.get(i).getStudentId(), 1, "Present at school", PointSource.ATTENDANCE, null, date));
                    }
                }
            }
        }
        attendance.saveAll(records);

        // ---- recognition points from teachers: a few every week, more in a school that is doing well
        if (!teachers.isEmpty()) {
            int perWeek = p.base() > 0 ? 5 : 3;
            for (int w = WEEKS - 1; w >= 0; w--) {
                LocalDate ws = SchoolCalendar.weekStart(today.minusWeeks(w));
                for (int k = 0; k < perWeek; k++) {
                    List<Enrolment> roster = new ArrayList<>(bySection.values().stream().flatMap(List::stream).toList());
                    if (roster.isEmpty()) {
                        break;
                    }
                    LocalDate day = ws.plusDays(rnd.nextInt(5));
                    if (!day.isBefore(today)) {
                        continue;
                    }
                    pointRows.add(point(site, roster.get(rnd.nextInt(roster.size())).getStudentId(), 2 + rnd.nextInt(4),
                            PRAISE[rnd.nextInt(PRAISE.length)], PointSource.MANUAL, teachers.get(rnd.nextInt(teachers.size())), day));
                }
            }
        }
        points.saveAll(pointRows);

        // ---- weekly grades: one assessment per class and subject every week, marks that climb over the months
        List<UUID> subjects = teaching.stream().map(TeachingAssignment::getSubjectId).distinct().sorted().toList();
        List<Term> termList = terms.findBySiteIdOrderByNumberAsc(site.getId());
        Instant now = Instant.now(clock);
        for (int w = WEEKS - 1; w >= 0; w--) {
            LocalDate ws = SchoolCalendar.weekStart(today.minusWeeks(w));
            double progress = 1.0 - (double) w / (WEEKS - 1);            // 0 = oldest week, 1 = this week
            for (TeachingAssignment ta : teaching) {
                int subjectIndex = subjects.indexOf(ta.getSubjectId());
                boolean current = w == 0;
                boolean late = current && subjectIndex >= subjects.size() - p.lateSubjects();
                Assessment a = new Assessment();
                a.setOrgId(site.getOrgId());
                a.setSiteId(site.getId());
                a.setSectionId(ta.getSectionId());
                a.setSubjectId(ta.getSubjectId());
                a.setTerm(termFor(termList, ws));
                a.setName("Week " + (WEEKS - w) + " quiz");
                a.setMaxMark(BigDecimal.valueOf(20));
                a.setWeekStart(ws);
                a.setCreatedAt(ws.plusDays(1).atTime(8, 0).atZone(SchoolCalendar.ZONE).toInstant());
                Instant due = ws.plusDays(3).atTime(14, 0).atZone(SchoolCalendar.ZONE).toInstant();
                a.setSubmittedAt(late ? null : due.isAfter(now) ? now : due);
                assessments.save(a);
                List<Enrolment> roster = bySection.getOrDefault(ta.getSectionId(), List.of());
                List<Mark> rows = new ArrayList<>();
                for (int i = 0; i < roster.size(); i++) {
                    if (late && i % 2 == 1) {
                        continue;                                         // the teacher has only entered half so far
                    }
                    Mark m = new Mark();
                    m.setOrgId(site.getOrgId());
                    m.setSiteId(site.getId());
                    m.setAssessmentId(a.getId());
                    m.setStudentId(roster.get(i).getStudentId());
                    if (rnd.nextDouble() < .02) {
                        m.setAbsent(true);
                    } else {
                        double ability = ABILITY[i % ABILITY.length] + p.base() + p.gain() * progress + (rnd.nextDouble() - .5) * .16;
                        double v = Math.max(.15, Math.min(1.0, ability)) * 20;
                        m.setValue(BigDecimal.valueOf(Math.round(v * 2) / 2.0).setScale(1, RoundingMode.HALF_UP));
                    }
                    m.setCreatedAt(a.getCreatedAt());
                    rows.add(m);
                }
                marks.saveAll(rows);
            }
        }

        // ---- Term 1 results were published when the term ended
        for (UUID sectionId : bySection.keySet()) {
            if (publications.findBySectionIdAndTerm(sectionId, 1).isEmpty()) {
                ResultPublication rp = new ResultPublication();
                rp.setOrgId(site.getOrgId());
                rp.setSiteId(site.getId());
                rp.setSectionId(sectionId);
                rp.setTerm(1);
                rp.setPublishedAt(today.minusWeeks(7).atTime(10, 0).atZone(SchoolCalendar.ZONE).toInstant());
                rp.setPublishedBy(publisher.getId());
                publications.save(rp);
            }
        }
    }

    private static int termFor(List<Term> list, LocalDate date) {
        for (Term t : list) {
            if (!date.isBefore(t.getStartsOn()) && !date.isAfter(t.getEndsOn())) {
                return t.getNumber();
            }
        }
        return list.isEmpty() || date.isBefore(list.get(0).getStartsOn()) ? 1 : list.get(list.size() - 1).getNumber();
    }

    private PointEntry point(Site site, UUID student, int amount, String reason, PointSource source, UUID by, LocalDate on) {
        PointEntry e = new PointEntry();
        e.setOrgId(site.getOrgId());
        e.setSiteId(site.getId());
        e.setStudentId(student);
        e.setAmount(amount);
        e.setReason(reason);
        e.setSource(source);
        e.setAwardedBy(by);
        e.setAwardedOn(on);
        e.setCreatedAt(on.atTime(source == PointSource.ATTENDANCE ? 8 : 12, 0).atZone(SchoolCalendar.ZONE).toInstant());
        return e;
    }

    // ========================================================================= notices

    private record Note(String en, String ar, String bodyEn, String bodyAr) {}

    private static final List<Note> NOTES = List.of(
            new Note("Term 1 begins", "بداية الفصل الأول", "Welcome back. Lessons follow the new timetable from Sunday.", "أهلاً بعودتكم. تبدأ الحصص وفق الجدول الجديد من يوم الأحد."),
            new Note("Parent evening", "أمسية أولياء الأمور", "Parents are invited to meet the teachers. Please book a slot with the school office.", "ندعو أولياء الأمور للقاء المعلمين. يرجى حجز موعد من مكتب المدرسة."),
            new Note("Sports day activities", "فعاليات اليوم الرياضي", "Students should wear sports clothes and bring water.", "على الطلاب ارتداء الملابس الرياضية وإحضار الماء."),
            new Note("Mid-term exam timetable", "جدول اختبارات منتصف الفصل", "The timetable is published. Revision sessions run after school.", "تم نشر الجدول. تُقام حصص المراجعة بعد الدوام."),
            new Note("Term 1 results are out", "نتائج الفصل الأول صدرت", "Parents can see the results from the Results page.", "يمكن لأولياء الأمور الاطلاع على النتائج من صفحة النتائج."),
            new Note("Reading week", "أسبوع القراءة", "Every class reads together for twenty minutes a day.", "يقرأ كل صف معاً عشرين دقيقة يومياً."));

    /** Six notices spread over the months; the last one is still showing. */
    @Transactional
    public void notices(Site site, Person author) {
        LocalDate today = LocalDate.now(clock.withZone(SchoolCalendar.ZONE));
        int step = WEEKS * 7 / NOTES.size();
        for (int i = 0; i < NOTES.size(); i++) {
            Note n = NOTES.get(i);
            LocalDate from = today.minusDays((long) (NOTES.size() - i) * step - 3);
            Notice notice = new Notice();
            notice.setOrgId(site.getOrgId());
            notice.setSiteId(site.getId());
            notice.setTitleEn(n.en());
            notice.setTitleAr(n.ar());
            notice.setBodyEn(n.bodyEn());
            notice.setBodyAr(n.bodyAr());
            notice.setStartsAt(from.atTime(7, 0).atZone(SchoolCalendar.ZONE).toInstant());
            notice.setEndsAt(from.plusDays(i == NOTES.size() - 1 ? 14 : 12).atTime(15, 0).atZone(SchoolCalendar.ZONE).toInstant());
            notice.setAudience(NoticeAudience.ALL);
            notice.setCreatedBy(author.getId());
            notice.setCreatedAt(notice.getStartsAt());
            notices.save(notice);
        }
    }

    // ========================================================================= file-health score

    /** A daily score for the last four months: more open issues long ago, fewer now, so the line rises. */
    @Transactional
    public void scores(Site site, DemoSchools.Profile p) {
        LocalDate today = LocalDate.now(clock);
        HealthScoreSnapshot now = snapshots.findBySiteIdAndScoreDate(site.getId(), today).orElse(null);
        if (now == null) {
            return;
        }
        Random rnd = new Random(("scores-" + p.letter()).hashCode());
        int days = 120;
        List<HealthScoreSnapshot> rows = new ArrayList<>();
        for (int d = 1; d <= days; d++) {
            if (snapshots.findBySiteIdAndScoreDate(site.getId(), today.minusDays(d)).isPresent()) {
                continue;
            }
            double t = (double) d / days;                                 // 0 = today, 1 = four months ago
            int normal = Math.max(0, (int) Math.round(now.getOpenNormal() + p.openStart() * t * (0.8 + rnd.nextDouble() * 0.4)));
            int urgent = Math.max(0, now.getOpenUrgent() + (t > .55 && p.openStart() > 6 ? 1 : 0) + (t > .8 && p.openStart() > 8 ? 1 : 0));
            int files = Math.max(5, (int) Math.round(now.getFilesChecked() * (0.8 + 0.2 * (1 - t))));
            HealthScoreSnapshot s = new HealthScoreSnapshot();
            s.setOrgId(site.getOrgId());
            s.setSiteId(site.getId());
            s.setScoreDate(today.minusDays(d));
            s.setFilesChecked(files);
            s.setOpenNormal(normal);
            s.setOpenUrgent(urgent);
            s.setScore(ScoreService.calculate(normal, urgent, files));
            rows.add(s);
        }
        snapshots.saveAll(rows);
    }

    // ========================================================================= support tickets

    private record Template(String title, TicketCategory category, TicketUrgency urgency, String description, String answer, String followUp, String closing) {}

    private static final List<Template> TEMPLATES = List.of(
            new Template("Google Drive connection shows an error", TicketCategory.GOOGLE_DRIVE, TicketUrgency.HIGH,
                    "Since this morning the connection page shows an error and the nightly check has not run.",
                    "We re-authorised the connection from head office. Please check the Connection page again tomorrow.",
                    "It shows connected now. Thank you.", "Confirmed: the nightly check ran normally. Closing."),
            new Template("A new teacher needs an account", TicketCategory.ACCOUNTS, TicketUrgency.NORMAL,
                    "A new Science teacher joins on Sunday and needs access to Hafez.",
                    "The account is ready. They can sign in with their school e-mail.", "They are in. Thanks.", "Done. Closing."),
            new Template("Week marks missing from the performance matrix", TicketCategory.ACADEMIC_DATA, TicketUrgency.HIGH,
                    "One class's marks for last week do not appear in the matrix.",
                    "The assessment had not been submitted. Please ask the teacher to submit it; the matrix updates straight away.",
                    "Submitted, and it shows now.", "Resolved. Closing."),
            new Template("Parents say their invitation codes are rejected", TicketCategory.ACCOUNTS, TicketUrgency.URGENT,
                    "Several parents tried their codes and were told the code is not valid.",
                    "Codes expire after 14 days. Please issue new codes from the Invitations page.",
                    "New codes worked.", "Resolved. Closing."),
            new Template("A mark book is open to anyone with the link", TicketCategory.SECURITY, TicketUrgency.URGENT,
                    "A teacher's mark book is shared with anyone who has the link. We want to confirm the right steps.",
                    "Thank you for reporting it. Remove the public link; Hafez checks again automatically and will close the issue.",
                    "The link is removed.", "Verified fixed. Closing."),
            new Template("Request for a second School IT account", TicketCategory.ACCOUNTS, TicketUrgency.LOW,
                    "Our IT officer is on leave next month. Can a colleague hold the role meanwhile?",
                    "Yes. A role change needs a second EAA administrator to approve, which we will arrange.",
                    "Thank you.", "Approved and applied. Closing."),
            new Template("Timetable clash for a Science teacher", TicketCategory.TECHNICAL, TicketUrgency.NORMAL,
                    "The timetable will not let us place a Science lesson in period 3.",
                    "The teacher already has a lesson in that period for another class. Choose another period.",
                    "That fixed it.", "Resolved. Closing."),
            new Template("Attendance officer cannot save today's register", TicketCategory.TECHNICAL, TicketUrgency.HIGH,
                    "Saving the register shows an error for one class.",
                    "Attendance can be corrected the same day only. We fixed the class setup; please try again.",
                    "Saved now.", "Resolved. Closing."));

    /** Tickets from the school's principal to Leadership, spread over the months: older ones closed, the newest still open. */
    @Transactional
    public void tickets(Site site, DemoSchools.Profile p, Person principal, List<Person> admins) {
        if (admins.isEmpty() || p.tickets() == 0) {
            return;
        }
        Random rnd = new Random(("tickets-" + p.letter()).hashCode());
        Instant now = Instant.now(clock);
        int n = Math.min(p.tickets(), TEMPLATES.size());
        long span = WEEKS * 7L - 8;
        for (int i = 0; i < n; i++) {
            Template tpl = TEMPLATES.get((i + p.letter().charAt(0)) % TEMPLATES.size());
            Instant created = now.minusSeconds((span - (span - 3) * i / Math.max(1, n - 1)) * 86400 - rnd.nextInt(40000));
            boolean newest = i == n - 1;
            boolean second = i == n - 2 && n > 3;
            Person admin = admins.get(rnd.nextInt(admins.size()));
            SupportTicket t = new SupportTicket();
            t.setOrgId(site.getOrgId());
            t.setSiteId(site.getId());
            t.setTitle(tpl.title());
            t.setDescription(tpl.description());
            t.setCategory(tpl.category());
            t.setUrgency(tpl.urgency());
            t.setAudience(TicketAudience.LEADERSHIP);
            t.setCreatedBy(principal.getId());
            t.setCreatedByName(principal.getDisplayName());
            t.setCreatedAt(created);
            Instant last = created;
            List<TicketReply> thread = new ArrayList<>();
            if (!newest) {
                t.setAssignedTo(admin.getId());
                t.setAssignedToName(admin.getDisplayName());
                last = created.plusSeconds(86400 + rnd.nextInt(30000));
                thread.add(reply(site, admin, "EAA_ADMIN", tpl.answer(), last));
            }
            if (!newest && !second) {
                Instant f = last.plusSeconds(40000 + rnd.nextInt(40000));
                thread.add(reply(site, principal, "PRINCIPAL", tpl.followUp(), f));
                last = f.plusSeconds(30000 + rnd.nextInt(60000));
                thread.add(reply(site, admin, "EAA_ADMIN", tpl.closing(), last));
                t.setStatus(TicketStatus.CLOSED);
                t.setClosedAt(last);
            } else {
                t.setStatus(newest ? TicketStatus.OPEN : TicketStatus.IN_PROGRESS);
            }
            t.setUpdatedAt(last);
            tickets.save(t);
            for (TicketReply r : thread) {
                r.setTicketId(t.getId());
            }
            replies.saveAll(thread);
        }
    }

    /** Two everyday tickets from a teacher to the school's School IT: one solved, one still open. */
    @Transactional
    public void schoolItTickets(Site site, Person teacher, Person it) {
        Instant now = Instant.now(clock);
        Object[][] rows = {
                {"Projector in room 12 will not connect to the network", TicketCategory.TECHNICAL, TicketUrgency.NORMAL,
                        "The projector shows no network. I cannot open the lesson slides.", "I reset the access point in that room. Please try again.",
                        "Working now, thank you.", "Closing."},
                {"I cannot sign in to my school account on the library computer", TicketCategory.ACCOUNTS, TicketUrgency.LOW,
                        "It says my account is not set up.", null, null, null}};
        for (int i = 0; i < rows.length; i++) {
            Object[] r = rows[i];
            boolean solved = r[4] != null;
            Instant created = now.minusSeconds((solved ? 40L : 2L) * 86400);
            SupportTicket t = new SupportTicket();
            t.setOrgId(site.getOrgId());
            t.setSiteId(site.getId());
            t.setTitle((String) r[0]);
            t.setCategory((TicketCategory) r[1]);
            t.setUrgency((TicketUrgency) r[2]);
            t.setDescription((String) r[3]);
            t.setAudience(TicketAudience.SCHOOL_IT);
            t.setCreatedBy(teacher.getId());
            t.setCreatedByName(teacher.getDisplayName());
            t.setCreatedAt(created);
            List<TicketReply> thread = new ArrayList<>();
            Instant last = created;
            if (solved) {
                t.setAssignedTo(it.getId());
                t.setAssignedToName(it.getDisplayName());
                last = created.plusSeconds(7200);
                thread.add(reply(site, it, "SCHOOL_IT", (String) r[4], last));
                thread.add(reply(site, teacher, "TEACHER", (String) r[5], last.plusSeconds(3600)));
                last = last.plusSeconds(7200);
                thread.add(reply(site, it, "SCHOOL_IT", (String) r[6], last));
                t.setStatus(TicketStatus.CLOSED);
                t.setClosedAt(last);
            }
            t.setUpdatedAt(last);
            tickets.save(t);
            for (TicketReply reply : thread) {
                reply.setTicketId(t.getId());
            }
            replies.saveAll(thread);
        }
    }

    private TicketReply reply(Site site, Person author, String role, String body, Instant at) {
        TicketReply r = new TicketReply();
        r.setOrgId(site.getOrgId());
        r.setSiteId(site.getId());
        r.setAuthorId(author.getId());
        r.setAuthorName(author.getDisplayName());
        r.setAuthorRole(role);
        r.setBody(body);
        r.setCreatedAt(at);
        return r;
    }
}
