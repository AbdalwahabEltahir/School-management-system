package qa.hafez.hafez.people.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Which students a parent may follow. Only School IT creates the links. */
@Component
@RequiredArgsConstructor
public class ParentLinks {

    private final JdbcTemplate jdbc;

    public void link(UUID parentId, UUID studentId) {
        jdbc.update("INSERT INTO parent_links (parent_id, student_id) VALUES (?, ?) ON CONFLICT DO NOTHING", parentId, studentId);
    }

    public List<UUID> childrenOf(UUID parentId) {
        return jdbc.queryForList("SELECT student_id FROM parent_links WHERE parent_id = ? ORDER BY created_at", UUID.class, parentId);
    }

    public boolean isLinked(UUID parentId, UUID studentId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM parent_links WHERE parent_id = ? AND student_id = ?", Integer.class, parentId, studentId);
        return n != null && n > 0;
    }
}
