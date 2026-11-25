package qa.sijill.sijill.family;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Request and response shapes of the family-engagement features. */
public final class FamilyDtos {

    private FamilyDtos() {
    }

    // ---- 1. at-risk students
    /** One student whose average in one subject is below the pass line. {@code absences30}: absent days in the last 30 days. */
    public record RiskView(UUID id, UUID studentId, String student, UUID sectionId, String section, UUID subjectId, String subject,
                           BigDecimal averagePct, BigDecimal lowestPct, int assessments, String level, String status,
                           Instant raisedAt, Instant resolvedAt, String note, String notedBy, Instant notedAt,
                           Instant parentsToldAt, int absences30, boolean canAct) {}

    public record RiskBoard(int passLine, int highLine, List<RiskView> open, List<RiskView> improved) {}

    public record RiskNote(@NotBlank String note) {}

    public record RiskTell(String message) {}

    // ---- 2. absence notes
    public record ExcuseRequest(@NotNull UUID studentId, @NotNull LocalDate fromDate, LocalDate toDate, @NotBlank String category,
                                String details) {}

    public record ExcuseReview(@NotBlank String decision, String note) {}

    public record ExcuseView(UUID id, UUID studentId, String student, String section, String parent, LocalDate fromDate,
                             LocalDate toDate, String category, String details, String status, String reviewedBy,
                             Instant reviewedAt, String reviewNote, Instant createdAt) {}

    /** A day the child was marked absent with no note sent for it yet. */
    public record UnexplainedDay(UUID studentId, String student, LocalDate date) {}

    public record ParentAbsences(List<ChildRef> children, List<UnexplainedDay> unexplained, List<ExcuseView> notes) {}

    public record StaffAbsences(List<ExcuseView> pending, List<ExcuseView> today, List<ExcuseView> recent, boolean canReview) {}

    public record ChildRef(UUID id, String name, String section) {}

    // ---- 3. trips and events
    public record EventRequest(@NotBlank String title, String details, String location, @NotNull LocalDate date,
                               @NotNull LocalTime startTime, @NotNull LocalTime endTime, BigDecimal cost,
                               @NotNull LocalDate consentDeadline, @NotNull List<UUID> sectionIds) {}

    public record ConsentRequest(@NotNull UUID studentId, @NotBlank String decision, String note, boolean agreed) {}

    public record CancelRequest(String reason) {}

    /** {@code decision} for a parent's child: APPROVED, DECLINED or null (not answered yet). */
    public record ChildConsent(UUID studentId, String student, String decision, String note, Instant decidedAt, String decidedBy) {}

    public record EventView(UUID id, String title, String details, String location, Instant startsAt, Instant endsAt,
                            BigDecimal cost, LocalDate consentDeadline, boolean open, boolean cancelled, String cancelReason,
                            List<String> sections, String organiser, boolean mine, int students, int approved, int declined,
                            int waiting, List<ChildConsent> children) {}

    public record EventDetail(EventView event, List<ChildConsent> roster) {}

    public record EventsPage(List<EventView> upcoming, List<EventView> past, boolean canCreate, String statementEn, String statementAr) {}

    // ---- 4. early pick-up
    public record EarlyPickupRequest(@NotNull UUID studentId, @NotNull LocalDate date, @NotNull LocalTime time, UUID collectorId,
                                     @NotBlank String reason) {}

    public record DeclineRequest(String reason) {}

    public record EarlyPickupView(UUID id, UUID studentId, String student, String section, String parent, LocalDate date,
                                  LocalTime time, String collector, String relationship, String reason, String status,
                                  String decidedBy, Instant decidedAt, String declineReason, Instant createdAt, boolean pickedUp) {}

    public record EarlyBoard(List<EarlyPickupView> pending, List<EarlyPickupView> today, List<EarlyPickupView> upcoming, boolean canDecide) {}

    // ---- 5. parent-teacher meetings
    public record SlotsRequest(@NotNull LocalDate date, @NotNull LocalTime from, @NotNull LocalTime to, int minutes,
                               @NotBlank String mode, String place) {}

    public record BookRequest(@NotNull UUID slotId, @NotNull UUID studentId, @NotBlank String topic) {}

    public record SlotView(UUID id, Instant startsAt, int minutes, String mode, String place, boolean booked, UUID bookingId,
                           String parent, String student, String topic) {}

    public record TeacherMeetings(List<SlotView> slots, int booked, int free) {}

    public record TeacherOption(UUID teacherId, String teacher, String subjects, List<SlotView> free) {}

    public record ChildTeachers(UUID studentId, String student, String section, List<TeacherOption> teachers) {}

    public record BookingView(UUID id, UUID slotId, Instant startsAt, int minutes, String mode, String place, String teacher,
                              String student, String topic, String status, String cancelReason, String cancelledBy) {}

    public record ParentMeetings(List<ChildTeachers> children, List<BookingView> bookings) {}
}
