package qa.sijill.sijill.classroom;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qa.sijill.sijill.classroom.ClassroomDtos.ChatContact;
import qa.sijill.sijill.classroom.ClassroomDtos.ChatMessage;
import qa.sijill.sijill.classroom.ClassroomDtos.ChatThread;
import qa.sijill.sijill.shared.exception.BadRequestException;
import qa.sijill.sijill.shared.exception.ForbiddenException;
import qa.sijill.sijill.shared.exception.NotFoundException;
import qa.sijill.sijill.shared.model.NotificationKind;
import qa.sijill.sijill.shared.model.Role;
import qa.sijill.sijill.shared.security.CurrentUser;

/**
 * Chat with the teacher. A student can write to the teachers of their class; a parent to the teachers of their
 * children's classes; a teacher to the students they teach and those students' parents. Nobody else can be reached,
 * and every message stays inside the school.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MAX_LENGTH = 2000;

    private final JdbcTemplate jdbc;
    private final ClassroomPeople people;

    private record Msg(UUID id, UUID sender, String body, Instant at, Instant readAt) {}

    private static boolean isTeacher(CurrentUser u) {
        return u.isAny(Role.TEACHER, Role.COORDINATOR);
    }

    /** Everyone this person may write to, with the latest message and how many are unread, most recent first. */
    @Transactional(readOnly = true)
    public List<ChatContact> contacts(CurrentUser user) {
        Map<UUID, String> about = reachable(user);
        Map<UUID, String> names = people.names(about.keySet());
        List<ChatContact> out = new ArrayList<>();
        for (Map.Entry<UUID, String> e : about.entrySet()) {
            UUID other = e.getKey();
            List<Msg> msgs = messages(user, other, 1);
            Msg last = msgs.isEmpty() ? null : msgs.get(msgs.size() - 1);
            out.add(new ChatContact(other, names.getOrDefault(other, ""), people.roleOf(other), e.getValue(), unread(user, other),
                    last == null ? null : last.at(), last == null ? null : preview(last.body())));
        }
        out.sort(Comparator.comparing((ChatContact c) -> c.lastAt() == null ? Instant.EPOCH : c.lastAt()).reversed()
                .thenComparing(ChatContact::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** The conversation with one person; their messages to me are marked read. */
    @Transactional
    public ChatThread thread(CurrentUser user, UUID other) {
        Map<UUID, String> about = reachable(user);
        if (!about.containsKey(other)) {
            throw new NotFoundException("The person");
        }
        UUID[] pair = pair(user, other);
        jdbc.update("UPDATE chat_messages SET read_at = now() WHERE teacher_id = ? AND other_id = ? AND sender_id = ? AND read_at IS NULL",
                pair[0], pair[1], other);
        Map<UUID, String> names = people.names(List.of(user.personId(), other));
        List<ChatMessage> list = messages(user, other, 500).stream()
                .map(m -> new ChatMessage(m.id(), m.sender().equals(user.personId()), names.getOrDefault(m.sender(), ""), m.body(), m.at())).toList();
        Msg last = list.isEmpty() ? null : new Msg(null, null, list.get(list.size() - 1).body(), list.get(list.size() - 1).at(), null);
        ChatContact with = new ChatContact(other, names.getOrDefault(other, ""), people.roleOf(other), about.get(other), 0,
                last == null ? null : last.at(), last == null ? null : preview(last.body()));
        return new ChatThread(with, list);
    }

    @Transactional
    public ChatThread send(CurrentUser user, UUID other, String body) {
        String text = body == null ? "" : body.strip();
        if (text.isEmpty()) {
            throw new BadRequestException("Write a message first.");
        }
        if (text.length() > MAX_LENGTH) {
            throw new BadRequestException("Please keep a message under " + MAX_LENGTH + " characters.");
        }
        if (!reachable(user).containsKey(other)) {
            throw new ForbiddenException("You can only write to your own teachers, students and their parents.");
        }
        UUID[] pair = pair(user, other);
        jdbc.update("INSERT INTO chat_messages (org_id, site_id, teacher_id, other_id, sender_id, body) VALUES (?,?,?,?,?,?)",
                user.orgId(), user.siteId(), pair[0], pair[1], user.personId(), text);
        people.tell(List.of(other), user.personId(), NotificationKind.MESSAGE, "رسالة جديدة من " + user.name(),
                "New message from " + user.name(), preview(text));
        return thread(user, other);
    }

    @Transactional(readOnly = true)
    public int unreadTotal(CurrentUser user) {
        Integer n = jdbc.queryForObject(isTeacher(user)
                ? "SELECT count(*) FROM chat_messages WHERE teacher_id = ? AND sender_id <> teacher_id AND read_at IS NULL"
                : "SELECT count(*) FROM chat_messages WHERE other_id = ? AND sender_id = teacher_id AND read_at IS NULL", Integer.class, user.personId());
        return n == null ? 0 : n;
    }

    // ---- helpers

    /** Who this person may write to, and a short line saying how they are connected. */
    private Map<UUID, String> reachable(CurrentUser user) {
        Map<UUID, String> out = new LinkedHashMap<>();
        if (user.is(Role.STUDENT)) {
            people.sectionOf(user.personId()).ifPresent(s -> addTeachers(out, s, null));
        } else if (user.is(Role.PARENT)) {
            Map<UUID, String> kids = people.names(people.childrenOf(user.personId()));
            kids.forEach((kid, name) -> people.sectionOf(kid).ifPresent(s -> addTeachers(out, s, name)));
        } else if (isTeacher(user)) {
            Map<UUID, String> sections = people.sectionLabels(user.siteId());
            for (UUID section : people.pairsOf(user.personId()).stream().map(ClassroomPeople.Pair::sectionId).distinct().toList()) {
                String label = sections.getOrDefault(section, "");
                List<UUID> students = people.studentsOf(section);
                Map<UUID, String> names = people.names(students);
                for (UUID st : students) {
                    out.putIfAbsent(st, "Student · " + label);
                    for (UUID parent : people.parentsOf(List.of(st))) {
                        out.merge(parent, "Parent of " + names.getOrDefault(st, "") + " · " + label, (a, b) -> a + ", " + b.replace("Parent of ", ""));
                    }
                }
            }
        } else {
            throw new ForbiddenException("Chat is for teachers, students and parents.");
        }
        out.remove(user.personId());
        return out;
    }

    private void addTeachers(Map<UUID, String> out, UUID section, String child) {
        for (Map<String, Object> row : people.teachersOf(section)) {
            UUID teacher = (UUID) row.get("teacher_id");
            String subject = (String) row.get("subject");
            String line = subject + (child == null ? "" : " · " + child);
            out.merge(teacher, line, (a, b) -> a.contains(b) ? a : a + ", " + b);
        }
    }

    /** teacher_id, other_id for the conversation between this person and another. */
    private UUID[] pair(CurrentUser user, UUID other) {
        return isTeacher(user) ? new UUID[]{user.personId(), other} : new UUID[]{other, user.personId()};
    }

    private List<Msg> messages(CurrentUser user, UUID other, int limit) {
        UUID[] pair = pair(user, other);
        List<Msg> rows = jdbc.query("SELECT id, sender_id, body, created_at, read_at FROM chat_messages WHERE teacher_id = ? AND other_id = ? "
                        + "ORDER BY created_at DESC LIMIT ?",
                (rs, i) -> new Msg(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getTimestamp(4).toInstant(),
                        toInstant(rs.getTimestamp(5))), pair[0], pair[1], limit);
        List<Msg> out = new ArrayList<>(rows);
        java.util.Collections.reverse(out);
        return out;
    }

    private int unread(CurrentUser user, UUID other) {
        UUID[] pair = pair(user, other);
        Integer n = jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE teacher_id = ? AND other_id = ? AND sender_id = ? AND read_at IS NULL",
                Integer.class, pair[0], pair[1], other);
        return n == null ? 0 : n;
    }

    private static Instant toInstant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static String preview(String s) {
        return s.length() > 140 ? s.substring(0, 140) + "…" : s;
    }
}
