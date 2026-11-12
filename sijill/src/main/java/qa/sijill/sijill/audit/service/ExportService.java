package qa.sijill.sijill.audit.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.audit.model.ActivityLogEntry;
import qa.sijill.sijill.audit.model.ExportRecord;
import qa.sijill.sijill.audit.repository.ExportRecordRepository;
import qa.sijill.sijill.issues.model.Issue;
import qa.sijill.sijill.issues.service.IssueService;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.people.repository.PersonRepository;
import qa.sijill.sijill.shared.events.ActivityActions;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;
import qa.sijill.sijill.shared.util.NameMasker;

/**
 * <b>Name-free exports</b> (proposal 5.10, design principle 11). Every download uses initials or IDs;
 * full names appear on screen only. School IT may unmask <i>one</i> export, which needs a reason and is
 * recorded. Every export is stored as an {@link ExportRecord} and audited, and every document ends with a
 * footer saying who exported it, when, and its sensitivity.
 *
 * <p>Cells are also neutralised against CSV/spreadsheet formula injection (a leading {@code = + - @}).
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    private static final String SENSITIVITY = "Confidential";

    private final IssueService issueService;
    private final ActivityLogService activityLog;
    private final PersonRepository people;
    private final ExportRecordRepository exports;
    private final AuditPublisher audit;
    private final Clock clock;

    /** The user's issues as CSV. Always masked: owners appear as initials, files by id, never by name. */
    @Transactional
    public String issuesCsv(CurrentUser user) {
        List<Issue> issues = issueService.listFor(user, IssueService.Filter.ALL);
        Map<UUID, Person> owners = people.findAllById(issues.stream().map(Issue::getOwnerId)
                .filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Person::getId, Function.identity()));

        StringBuilder csv = new StringBuilder("issue,severity,status,opened_at,closed_at,owner\n");
        for (Issue i : issues) {
            Person owner = owners.get(i.getOwnerId());
            csv.append(row(shortId(i.getId()), i.getSeverity(), i.getStatus(), i.getOpenedAt(), i.getClosedAt(),
                    owner == null ? "-" : NameMasker.initials(owner.getDisplayName())));
        }
        record(user, "issues", true, null, issues.size());
        return csv.append(footer(user)).toString();
    }

    /**
     * The activity log as CSV, scoped to the user's role. Actors are initials unless School IT unmasks
     * the export with a reason.
     */
    @Transactional
    public String activityLogCsv(CurrentUser user, boolean unmask, String reason) {
        if (unmask && (!user.is(Role.SCHOOL_IT) || reason == null || reason.isBlank())) {
            throw new ForbiddenException("Only School IT can unmask an export, and it needs a reason.");
        }
        if (reason != null && reason.length() > 500) {
            throw new BadRequestException("Please keep the reason under 500 characters.");
        }
        List<ActivityLogEntry> entries = activityLog.list(user, null, 0, 5000);
        Map<UUID, Person> actors = people.findAllById(entries.stream().map(ActivityLogEntry::getActorId)
                .filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Person::getId, Function.identity()));

        StringBuilder csv = new StringBuilder("seq,time,actor,action,entity,entity_id,before,after\n");
        for (ActivityLogEntry e : entries) {
            Person actor = actors.get(e.getActorId());
            String who = actor == null ? "system"
                    : unmask ? actor.getDisplayName() : NameMasker.initials(actor.getDisplayName());
            csv.append(row(e.getSeq(), e.getCreatedAt(), who, e.getAction(), e.getEntityType(), e.getEntityId(),
                    e.getBeforeState(), e.getAfterState()));
        }
        record(user, "activity-log", !unmask, reason, entries.size());
        return csv.append(footer(user)).toString();
    }

    /** Stores who exported what, masked or not, and why (FR-10.2), and audits it. */
    private void record(CurrentUser user, String kind, boolean masked, String reason, int rows) {
        ExportRecord r = new ExportRecord();
        r.setOrgId(user.orgId());
        r.setSiteId(user.siteId());
        r.setExportedBy(user.personId());
        r.setExportKind(kind);
        r.setMasked(masked);
        r.setUnmaskReason(masked ? null : reason);
        r.setRowCount(rows);
        exports.save(r);
        audit.log(user.orgId(), user.siteId(), ActivityActions.EXPORT_CREATED).by(user).on("Export", r.getId())
                .change(null, kind + (masked ? " (masked)" : " (UNMASKED)")).save();
    }

    /** Every document footer shows who, when and the sensitivity (FR-10.2). */
    private String footer(CurrentUser user) {
        return "# Exported by " + NameMasker.initials(user.name()) + " at " + Instant.now(clock)
                + " | Sensitivity: " + SENSITIVITY + "\n";
    }

    private String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private String row(Object... cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(cell(cells[i]));
        }
        return line.append('\n').toString();
    }

    /** One CSV cell: quoted when needed, and neutralised against formula injection. */
    static String cell(Object value) {
        String s = value == null ? "" : value.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
