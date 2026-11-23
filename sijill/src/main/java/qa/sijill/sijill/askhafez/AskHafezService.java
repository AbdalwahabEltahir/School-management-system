package qa.sijill.sijill.askhafez;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.askhafez.AskHafezDtos.ChildSetting;
import qa.sijill.sijill.askhafez.AskHafezDtos.Reply;
import qa.sijill.sijill.askhafez.AskHafezDtos.Status;
import qa.sijill.sijill.classroom.ClassroomPeople;
import qa.sijill.sijill.shared.events.AuditPublisher;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Ask Hafez, a read-only helper shown as a floating button to teachers, parents and students. It answers only from what
 * the person can already open in Hafez (see {@link AskHafezAnswers}), keeps every question and answer in a log, and a
 * parent can switch it off for each child.
 */
@Service
@RequiredArgsConstructor
public class AskHafezService {

    private static final int MAX_QUESTION = 500;

    private final JdbcTemplate jdbc;
    private final ClassroomPeople people;
    private final AskHafezAnswers answers;
    private final AuditPublisher audit;

    private static boolean eligible(CurrentUser user) {
        return user.isAny(Role.TEACHER, Role.PARENT, Role.STUDENT);
    }

    private boolean offFor(UUID studentId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM ask_hafez_off WHERE student_id = ?", Integer.class, studentId);
        return n != null && n > 0;
    }

    /** Whether this person gets the button now, and a parent's switch for each child. */
    @Transactional(readOnly = true)
    public Status status(CurrentUser user) {
        if (!eligible(user)) {
            return new Status(false, false, List.of());
        }
        if (user.is(Role.STUDENT)) {
            boolean off = offFor(user.personId());
            return new Status(!off, off, List.of());
        }
        if (user.is(Role.PARENT)) {
            return new Status(true, false, children(user));
        }
        return new Status(true, false, List.of());
    }

    private List<ChildSetting> children(CurrentUser parent) {
        List<UUID> ids = people.childrenOf(parent.personId());
        var names = people.names(ids);
        return ids.stream().map(id -> new ChildSetting(id, names.getOrDefault(id, ""), !offFor(id))).toList();
    }

    /** A parent turns Ask Hafez on or off for one of their children. Logged. */
    @Transactional
    public List<ChildSetting> setChild(CurrentUser user, UUID studentId, boolean enabled) {
        if (!user.is(Role.PARENT) || !people.childrenOf(user.personId()).contains(studentId)) {
            throw new NotFoundException("The child");
        }
        if (enabled) {
            jdbc.update("DELETE FROM ask_hafez_off WHERE student_id = ?", studentId);
        } else {
            jdbc.update("INSERT INTO ask_hafez_off (student_id, turned_off_by) VALUES (?, ?) ON CONFLICT DO NOTHING", studentId, user.personId());
        }
        audit.log(user.orgId(), user.siteId(), "ASK_HAFEZ_CHANGED").by(user).on("Person", studentId)
                .change(enabled ? "off" : "on", enabled ? "on" : "off").save();
        return children(user);
    }

    /** Answers one question as the signed-in person and logs the exchange. */
    @Transactional
    public Reply ask(CurrentUser user, String question) {
        if (!eligible(user)) {
            throw new ForbiddenException("Ask Hafez is for teachers, parents and students.");
        }
        if (user.is(Role.STUDENT) && offFor(user.personId())) {
            throw new ForbiddenException("Ask Hafez has been turned off for you by your parent.");
        }
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            throw new BadRequestException("Please type a question.");
        }
        if (q.length() > MAX_QUESTION) {
            throw new BadRequestException("Please keep the question under " + MAX_QUESTION + " characters.");
        }
        AskHafezAnswers.Answer a = answers.answer(user, q);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ask_hafez_log (id, org_id, site_id, person_id, question, answer, intent) VALUES (?,?,?,?,?,?,?)",
                id, user.orgId(), user.siteId(), user.personId(), q, a.text().length() > 4000 ? a.text().substring(0, 4000) : a.text(), a.intent());
        return new Reply(id, a.text(), a.links(), a.suggestions(), a.arabic() ? "ar" : "en");
    }

    /** Thumbs up or down on one of the person's own answers. */
    @Transactional
    public void rate(CurrentUser user, UUID id, boolean up) {
        int n = jdbc.update("UPDATE ask_hafez_log SET rating = ? WHERE id = ? AND person_id = ?", up ? 1 : -1, id, user.personId());
        if (n == 0) {
            throw new NotFoundException("The answer");
        }
    }
}
