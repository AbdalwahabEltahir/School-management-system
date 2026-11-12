package qa.hafez.hafez.workspace;

import java.util.List;

/**
 * A directory entry as reported by the workspace. {@code groups} are mapped to Hafez roles through
 * {@code RoleGroup}; {@code suspended} means the account has left.
 */
public record WorkspacePerson(String email, String displayName, List<String> groups, boolean suspended) {
}
