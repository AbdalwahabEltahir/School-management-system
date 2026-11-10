package qa.sijill.sijill.workspace;

/**
 * A file's content, read once into memory so it can be classified. It is never written to the database
 * or the logs (section 7: Sijill stores metadata and masked evidence only).
 *
 * @param mimeType the type of {@code data}: the file's own type, or the type it was exported to
 */
public record WorkspaceContent(String name, String mimeType, byte[] data) {
}
