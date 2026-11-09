package qa.sijill.sijill.rules.check;

import java.time.LocalDate;
import java.util.List;

import qa.sijill.sijill.files.model.FilePermission;
import qa.sijill.sijill.files.model.FileProfile;
import qa.sijill.sijill.files.model.SchoolFile;
import qa.sijill.sijill.people.model.Person;
import qa.sijill.sijill.rules.model.RuleVersion;

/**
 * Everything a {@link FileCheck} may look at, assembled once per (file, rule) by the rule engine.
 *
 * @param file        the file's metadata
 * @param permissions the file's current sharing grants
 * @param profile     what Sijill understood about the file; may be null if it has not been classified
 * @param owner       the owner's directory record; may be null if the owner is not in the directory
 * @param rule        the rule version being evaluated (gives access to its {@code parameter})
 * @param today       today's date in the school's time zone (passed in so checks stay testable)
 */
public record CheckContext(SchoolFile file, List<FilePermission> permissions, FileProfile profile,
                           Person owner, RuleVersion rule, LocalDate today) {
}
