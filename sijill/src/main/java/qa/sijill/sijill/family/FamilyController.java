package qa.sijill.sijill.family;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import qa.sijill.sijill.family.FamilyDtos.BookRequest;
import qa.sijill.sijill.family.FamilyDtos.BookingView;
import qa.sijill.sijill.family.FamilyDtos.CancelRequest;
import qa.sijill.sijill.family.FamilyDtos.ConsentRequest;
import qa.sijill.sijill.family.FamilyDtos.DeclineRequest;
import qa.sijill.sijill.family.FamilyDtos.EarlyBoard;
import qa.sijill.sijill.family.FamilyDtos.EarlyPickupRequest;
import qa.sijill.sijill.family.FamilyDtos.EarlyPickupView;
import qa.sijill.sijill.family.FamilyDtos.EventDetail;
import qa.sijill.sijill.family.FamilyDtos.EventRequest;
import qa.sijill.sijill.family.FamilyDtos.EventView;
import qa.sijill.sijill.family.FamilyDtos.EventsPage;
import qa.sijill.sijill.family.FamilyDtos.ExcuseRequest;
import qa.sijill.sijill.family.FamilyDtos.ExcuseReview;
import qa.sijill.sijill.family.FamilyDtos.ExcuseView;
import qa.sijill.sijill.family.FamilyDtos.ParentAbsences;
import qa.sijill.sijill.family.FamilyDtos.ParentMeetings;
import qa.sijill.sijill.family.FamilyDtos.RiskBoard;
import qa.sijill.sijill.family.FamilyDtos.RiskNote;
import qa.sijill.sijill.family.FamilyDtos.RiskTell;
import qa.sijill.sijill.family.FamilyDtos.RiskView;
import qa.sijill.sijill.family.FamilyDtos.SlotsRequest;
import qa.sijill.sijill.family.FamilyDtos.StaffAbsences;
import qa.sijill.sijill.family.FamilyDtos.TeacherMeetings;
import qa.sijill.sijill.shared.security.CurrentUser;

/** At-risk students, absence notes, trips and events, early pick-ups and parent-teacher meetings. Rules live in the services. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FamilyController {

    private final RiskService risk;
    private final AbsenceService absences;
    private final EventService events;
    private final EarlyPickupService early;
    private final MeetingService meetings;

    // ---- 1. at-risk students
    @GetMapping("/at-risk")
    public RiskBoard atRisk(CurrentUser user) {
        return risk.board(user);
    }

    @PostMapping("/at-risk/{id}/note")
    public RiskView riskNote(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody RiskNote body) {
        return risk.note(user, id, body.note());
    }

    @PostMapping("/at-risk/{id}/tell-parents")
    public RiskView riskTell(CurrentUser user, @PathVariable UUID id, @RequestBody(required = false) RiskTell body) {
        return risk.tellParents(user, id, body == null ? null : body.message());
    }

    // ---- 2. absence notes
    @GetMapping("/absences/mine")
    public ParentAbsences myAbsences(CurrentUser user) {
        return absences.mine(user);
    }

    @PostMapping("/absences")
    public ExcuseView submitExcuse(CurrentUser user, @Valid @RequestBody ExcuseRequest body) {
        return absences.submit(user, body);
    }

    @PostMapping("/absences/{id}/cancel")
    public ExcuseView cancelExcuse(CurrentUser user, @PathVariable UUID id) {
        return absences.cancel(user, id);
    }

    @GetMapping("/absences/board")
    public StaffAbsences absenceBoard(CurrentUser user) {
        return absences.board(user);
    }

    @PostMapping("/absences/{id}/review")
    public ExcuseView reviewExcuse(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody ExcuseReview body) {
        return absences.review(user, id, body.decision(), body.note());
    }

    // ---- 3. trips and events
    @GetMapping("/events")
    public EventsPage events(CurrentUser user) {
        return events.list(user);
    }

    @PostMapping("/events")
    public EventDetail createEvent(CurrentUser user, @Valid @RequestBody EventRequest body) {
        return events.create(user, body);
    }

    @GetMapping("/events/{id}")
    public EventDetail event(CurrentUser user, @PathVariable UUID id) {
        return events.detail(user, id);
    }

    @PostMapping("/events/{id}/consent")
    public EventView consent(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody ConsentRequest body) {
        return events.answer(user, id, body.studentId(), body.decision(), body.note(), body.agreed());
    }

    @PostMapping("/events/{id}/remind")
    public Map<String, Integer> remind(CurrentUser user, @PathVariable UUID id) {
        return Map.of("reminded", events.remind(user, id));
    }

    @PostMapping("/events/{id}/cancel")
    public EventDetail cancelEvent(CurrentUser user, @PathVariable UUID id, @RequestBody CancelRequest body) {
        return events.cancel(user, id, body.reason());
    }

    // ---- 4. early pick-up
    @GetMapping("/early-pickups/mine")
    public List<EarlyPickupView> myEarly(CurrentUser user) {
        return early.mine(user);
    }

    @PostMapping("/early-pickups")
    public EarlyPickupView requestEarly(CurrentUser user, @Valid @RequestBody EarlyPickupRequest body) {
        return early.request(user, body);
    }

    @PostMapping("/early-pickups/{id}/cancel")
    public EarlyPickupView cancelEarly(CurrentUser user, @PathVariable UUID id) {
        return early.cancel(user, id);
    }

    @GetMapping("/early-pickups/board")
    public EarlyBoard earlyBoard(CurrentUser user) {
        return early.board(user);
    }

    @PostMapping("/early-pickups/{id}/approve")
    public EarlyPickupView approveEarly(CurrentUser user, @PathVariable UUID id) {
        return early.decide(user, id, true, null);
    }

    @PostMapping("/early-pickups/{id}/decline")
    public EarlyPickupView declineEarly(CurrentUser user, @PathVariable UUID id, @RequestBody DeclineRequest body) {
        return early.decide(user, id, false, body.reason());
    }

    // ---- 5. parent-teacher meetings
    @GetMapping("/meetings/teacher")
    public TeacherMeetings teacherMeetings(CurrentUser user) {
        return meetings.teacher(user);
    }

    @PostMapping("/meetings/slots")
    public TeacherMeetings openSlots(CurrentUser user, @Valid @RequestBody SlotsRequest body) {
        return meetings.open(user, body);
    }

    @PostMapping("/meetings/slots/{id}/remove")
    public TeacherMeetings removeSlot(CurrentUser user, @PathVariable UUID id, @RequestBody(required = false) CancelRequest body) {
        return meetings.removeSlot(user, id, body == null ? null : body.reason());
    }

    @GetMapping("/meetings/parent")
    public ParentMeetings parentMeetings(CurrentUser user) {
        return meetings.parent(user);
    }

    @PostMapping("/meetings/book")
    public BookingView book(CurrentUser user, @Valid @RequestBody BookRequest body) {
        return meetings.book(user, body.slotId(), body.studentId(), body.topic());
    }

    @PostMapping("/meetings/bookings/{id}/cancel")
    public BookingView cancelBooking(CurrentUser user, @PathVariable UUID id, @RequestBody(required = false) CancelRequest body) {
        return meetings.cancelByParent(user, id, body == null ? null : body.reason());
    }
}
