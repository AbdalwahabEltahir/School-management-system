package qa.sijill.sijill.pickup.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import qa.sijill.sijill.pickup.model.PickupCollector;
import qa.sijill.sijill.pickup.model.PickupRelease;
import qa.sijill.sijill.pickup.model.PickupRequest;

public final class PickupDtos {

    private PickupDtos() {
    }

    /** A parent adds a collector. Only the last four characters of the ID number are kept. */
    public record CollectorRequest(String name, String relationship, String phone, String idNumber) {}

    public record ArriveRequest(UUID studentId, UUID collectorId) {}

    public record DeclineRequest(String reason) {}

    public record CollectorView(UUID id, UUID studentId, String name, String relationship, String phone, String idLast4) {
        public static CollectorView from(PickupCollector c) {
            return new CollectorView(c.getId(), c.getStudentId(), c.getName(), c.getRelationship().name(), c.getPhone(), c.getIdLast4());
        }
    }

    public record RequestView(UUID id, UUID studentId, String student, String section, String collector, String relationship,
                              String idLast4, String phone, String status, Instant arrivedAt, String verifiedBy, Instant verifiedAt,
                              String decidedBy, Instant decidedAt, String declineReason) {
        public static RequestView from(PickupRequest r) {
            return new RequestView(r.getId(), r.getStudentId(), r.getStudentName(), r.getSectionLabel(), r.getCollectorName(),
                    r.getCollectorRelationship().name(), r.getCollectorIdLast4(), r.getCollectorPhone(), r.getStatus().name(),
                    r.getArrivedAt(), r.getVerifiedByName(), r.getVerifiedAt(), r.getDecidedByName(), r.getDecidedAt(), r.getDeclineReason());
        }
    }

    public record ReleaseView(UUID id, String student, String section, String collector, String relationship, String releasedBy,
                              Instant releasedAt) {
        public static ReleaseView from(PickupRelease r) {
            return new ReleaseView(r.getId(), r.getStudentName(), r.getSectionLabel(), r.getCollectorName(),
                    r.getCollectorRelationship().name(), r.getReleasedByName(), r.getReleasedAt());
        }
    }

    /** The live board: children who have arrived, collectors being checked, and what finished today. */
    public record Board(List<RequestView> arrivals, List<RequestView> pending, List<RequestView> completed, boolean canAct) {}

    /** What a parent sees for one child. */
    public record ChildPickup(UUID studentId, String student, String section, List<CollectorView> collectors, RequestView current,
                              List<ReleaseView> recent) {}
}
