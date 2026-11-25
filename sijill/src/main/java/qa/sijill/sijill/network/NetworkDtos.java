package qa.sijill.sijill.network;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response shapes of the EAA IT network screens. */
public final class NetworkDtos {

    private NetworkDtos() {
    }

    /** One school with its head counts and principal. */
    public record School(UUID id, String name, String domain, boolean active, boolean scored, boolean headOffice, int students, int staff,
                         int parents, int itAccounts, String principalName, String principalEmail, Instant firstCheckAt) {}

    public record RoleCount(String role, int count) {}

    /** A school with the head count for each role, for the school's own page. */
    public record SchoolDetail(School school, List<RoleCount> roles) {}

    public record StatusRequest(Boolean active) {}

    /** A School IT account of one school. {@code status} is ACTIVE, NEW or LEFT (suspended). */
    public record ItAccount(UUID id, String name, String email, String status, UUID siteId, String siteName, Instant createdAt) {}

    public record CreateItRequest(UUID siteId, String name, String email) {}

    public record ItStatusRequest(String status) {}

    /** One line of the activity log, with who did it and what it was about. */
    public record LogRow(long seq, Instant at, UUID actorId, String actorName, String actorRole, String action, String entityType, UUID entityId,
                         String targetName, UUID schoolId, String schoolName, String before, String after) {}

    /** A page of the log plus the choices the filters offer. */
    public record LogPage(long total, List<LogRow> rows, List<String> actions, List<String> areas) {}
}
