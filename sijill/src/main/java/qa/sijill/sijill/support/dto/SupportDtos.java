package qa.sijill.sijill.support.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import qa.sijill.sijill.support.model.SupportTicket;
import qa.sijill.sijill.support.model.TicketReply;

public final class SupportDtos {

    private SupportDtos() {
    }

    /** What a head teacher sends. The attachment, if any, is base64 text. */
    public record CreateRequest(String title, String description, String category, String urgency, String audience,
                                String attachmentName, String attachmentData) {}

    public record ReplyRequest(String body) {}

    public record StatusRequest(String status) {}

    public record AssignRequest(UUID assigneeId) {}

    public record TicketView(UUID id, UUID siteId, String school, String title, String category, String urgency, String audience, String status,
                             String createdBy, UUID assignedTo, String assignedToName, boolean hasAttachment, String attachmentName,
                             Instant createdAt, Instant updatedAt, Instant closedAt, int replies) {}

    public record ReplyView(UUID id, String author, String role, String body, Instant createdAt) {
        public static ReplyView from(TicketReply r) {
            return new ReplyView(r.getId(), r.getAuthorName(), r.getAuthorRole(), r.getBody(), r.getCreatedAt());
        }
    }

    public record TicketDetail(TicketView ticket, String description, List<ReplyView> replies, boolean canReply, boolean canManage) {}

    public record Assignee(UUID id, String name) {}

    public static TicketView view(SupportTicket t, String school, int replies) {
        return new TicketView(t.getId(), t.getSiteId(), school, t.getTitle(), t.getCategory().name(), t.getUrgency().name(), t.getAudience().name(),
                t.getStatus().name(), t.getCreatedByName(), t.getAssignedTo(), t.getAssignedToName(), t.getAttachmentName() != null,
                t.getAttachmentName(), t.getCreatedAt(), t.getUpdatedAt(), t.getClosedAt(), replies);
    }
}
