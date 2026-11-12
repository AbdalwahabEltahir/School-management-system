package qa.hafez.hafez.workspace;

/** One sharing grant as reported by the workspace. {@code grantee} is null for a public link. */
public record WorkspacePermission(String grantee, ShareTargetType targetType, AccessLevel accessLevel) {
}
