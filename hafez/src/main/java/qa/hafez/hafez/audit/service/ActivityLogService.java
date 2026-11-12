package qa.hafez.hafez.audit.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.hafez.hafez.audit.model.ActivityLogEntry;
import qa.hafez.hafez.audit.repository.ActivityLogRepository;
import qa.hafez.hafez.organisation.repository.OrgRepository;
import qa.hafez.hafez.shared.events.AuditEvent;
import qa.hafez.hafez.shared.exception.ForbiddenException;
import qa.hafez.hafez.shared.model.Role;
import qa.hafez.hafez.shared.security.CurrentUser;

/**
 * The tamper-evident activity log (FR-5.2, FR-5.3). Modules do not call this class: they publish an
 * {@link AuditEvent} (via {@code AuditPublisher}) and this service appends it, <i>in the publisher's
 * transaction</i>, so a change and its log entry commit or roll back together.
 *
 * <h3>Hash chain</h3>
 * Each organisation has its own chain. An entry's hash covers its content <i>and</i> the previous entry's
 * hash, so editing or deleting any row breaks every later hash; {@link #verifyChain} finds the first
 * break. The organisation row is locked while appending, so two writers can never take the same sequence
 * number. A database trigger additionally rejects UPDATE/DELETE on the table (migration V2).
 */
@Service
@RequiredArgsConstructor
public class ActivityLogService {

    /** The "previous hash" of the very first entry of a chain. */
    public static final String GENESIS_HASH = "0".repeat(64);

    private static final int VERIFY_PAGE_SIZE = 500;
    private static final int MAX_PAGE_SIZE = 5000;
    private static final char SEP = '\u001F'; // field separator inside the hashed text

    private final ActivityLogRepository log;
    private final OrgRepository orgs;
    private final Clock clock;

    // ================================================================== writing (event listener)

    /** Appends an audit event to its organisation's chain. */
    @EventListener
    @Transactional
    public void on(AuditEvent event) {
        // Serialise writers per organisation so sequence numbers have no gaps or duplicates.
        orgs.lockById(event.orgId()).orElseThrow(() -> new IllegalStateException("Unknown organisation"));

        ActivityLogEntry entry = new ActivityLogEntry();
        entry.setOrgId(event.orgId());
        entry.setSiteId(event.siteId());
        entry.setActorId(event.actorId());
        entry.setAction(event.action());
        entry.setEntityType(event.entityType());
        entry.setEntityId(event.entityId());
        entry.setSubjectId(event.subjectId());
        entry.setBeforeState(truncate(event.before(), 2000));
        entry.setAfterState(truncate(event.after(), 2000));

        Optional<ActivityLogEntry> last = log.findFirstByOrgIdOrderBySeqDesc(event.orgId());
        entry.setSeq(last.map(l -> l.getSeq() + 1).orElse(1L));
        entry.setPrevHash(last.map(ActivityLogEntry::getHash).orElse(GENESIS_HASH));
        // PostgreSQL stores microseconds; hash the value that will actually be stored.
        entry.setCreatedAt(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
        entry.setHash(computeHash(entry));
        log.save(entry);
    }

    // ================================================================== verifying

    /** Result of {@link #verifyChain}. {@code firstBrokenSeq} is null when the chain is intact. */
    public record ChainVerification(boolean valid, long entriesChecked, Long firstBrokenSeq) {}

    /**
     * Re-computes every hash of the organisation's chain and checks the links. Leadership and School IT
     * can run this to prove the log has not been tampered with.
     */
    @Transactional(readOnly = true)
    public ChainVerification verifyChain(CurrentUser user) {
        if (!user.isAny(Role.LEADERSHIP, Role.EAA_ADMIN, Role.SCHOOL_IT)) {
            throw new ForbiddenException("Only School IT and Leadership can verify the log.");
        }
        return verifyChain(user.orgId());
    }

    ChainVerification verifyChain(UUID orgId) {
        String expectedPrev = GENESIS_HASH;
        long expectedSeq = 1;
        long checked = 0;
        long afterSeq = 0;

        while (true) {
            List<ActivityLogEntry> page = log.findByOrgIdAndSeqGreaterThanOrderBySeqAsc(
                    orgId, afterSeq, PageRequest.ofSize(VERIFY_PAGE_SIZE));
            if (page.isEmpty()) {
                return new ChainVerification(true, checked, null);
            }
            for (ActivityLogEntry e : page) {
                boolean ok = e.getSeq() == expectedSeq
                        && expectedPrev.equals(e.getPrevHash())
                        && computeHash(e).equals(e.getHash());
                if (!ok) {
                    return new ChainVerification(false, checked, expectedSeq);
                }
                expectedPrev = e.getHash();
                expectedSeq++;
                checked++;
                afterSeq = e.getSeq();
            }
        }
    }

    /** SHA-256 over the entry's content and the previous hash, as lower-case hex. */
    static String computeHash(ActivityLogEntry e) {
        String text = String.join(String.valueOf(SEP),
                e.getPrevHash(),
                String.valueOf(e.getSeq()),
                str(e.getOrgId()), str(e.getSiteId()), str(e.getActorId()),
                str(e.getAction()), str(e.getEntityType()), str(e.getEntityId()), str(e.getSubjectId()),
                str(e.getBeforeState()), str(e.getAfterState()),
                String.valueOf(ChronoUnit.MICROS.between(Instant.EPOCH, e.getCreatedAt())));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex); // cannot happen on a standard JVM
        }
    }

    // ================================================================== reading

    /**
     * The log as the user is allowed to see it (proposal 4 matrix): Leadership the whole network,
     * Principal and School IT their school, a coordinator her subject; teachers none.
     *
     * @param action optional exact action code to filter by
     */
    @Transactional(readOnly = true)
    public List<ActivityLogEntry> list(CurrentUser user, String action, int page, int size) {
        var paging = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        boolean filter = action != null && !action.isBlank();
        return switch (user.role()) {
            case LEADERSHIP, EAA_ADMIN -> filter
                    ? log.findByOrgIdAndActionOrderBySeqDesc(user.orgId(), action, paging)
                    : log.findByOrgIdOrderBySeqDesc(user.orgId(), paging);
            case PRINCIPAL, SCHOOL_IT -> filter
                    ? log.findBySiteIdAndActionOrderBySeqDesc(user.siteId(), action, paging)
                    : log.findBySiteIdOrderBySeqDesc(user.siteId(), paging);
            case COORDINATOR -> {
                if (user.subjectId() == null) { // school-level coordinator reads the school's log
                    yield filter
                            ? log.findBySiteIdAndActionOrderBySeqDesc(user.siteId(), action, paging)
                            : log.findBySiteIdOrderBySeqDesc(user.siteId(), paging);
                }
                yield filter
                        ? log.findBySiteIdAndSubjectIdAndActionOrderBySeqDesc(user.siteId(), user.subjectId(), action, paging)
                        : log.findBySiteIdAndSubjectIdOrderBySeqDesc(user.siteId(), user.subjectId(), paging);
            }
            case TEACHER, ATTENDANCE_OFFICER, STUDENT, PARENT -> throw new ForbiddenException("The activity log is not available for your role.");
        };
    }

    // ================================================================== helpers

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String truncate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
