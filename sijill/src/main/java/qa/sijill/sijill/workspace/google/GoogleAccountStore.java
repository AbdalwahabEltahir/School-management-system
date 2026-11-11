package qa.sijill.sijill.workspace.google;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Remembers the identity of each Google account whose Drive is read (table connector_account). */
@Component
@ConditionalOnProperty(name = "sijill.google.drive.enabled", havingValue = "true")
class GoogleAccountStore {

    private final JdbcTemplate jdbc;

    GoogleAccountStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<String> principalName(String email) {
        return jdbc.queryForList("SELECT principal_name FROM connector_account WHERE provider = 'google' AND email = ?",
                String.class, email.toLowerCase()).stream().findFirst();
    }

    /** Every Google account that has signed in and whose Drive is therefore readable. */
    java.util.List<String> emails() {
        return jdbc.queryForList("SELECT email FROM connector_account WHERE provider = 'google' ORDER BY email", String.class);
    }

    /** Has School IT (or the Workspace sign-in) given this address a staff account? Students and parents are never read: their Drives stay private. */
    boolean isSijillPerson(String email) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM people WHERE lower(email) = ? AND role NOT IN ('STUDENT', 'PARENT')", Integer.class, email.toLowerCase());
        return n != null && n > 0;
    }

    void remember(String principalName, String email) {
        jdbc.update("""
                INSERT INTO connector_account (provider, principal_name, email, updated_at) VALUES ('google', ?, ?, now())
                ON CONFLICT (provider, email) DO UPDATE SET principal_name = EXCLUDED.principal_name,
                    updated_at = now()""", principalName, email);
    }
}
