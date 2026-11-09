package qa.sijill.sijill.rules.model;

/**
 * The nine check types a rule can use (proposal 5.3).
 * Each type is implemented by exactly one {@code FileCheck} class in the {@code check} package;
 * a new kind of check is one new class behind that interface.
 */
public enum CheckType {
    /** Where the file is saved. */
    LOCATION,
    /** "Anyone with the link" sharing. */
    PUBLIC_LINK,
    /** Sharing outside the network. */
    OUTSIDE_SHARING,
    /** Who inside the network can open it. */
    INTERNAL_ACCESS,
    /** Duplicate copies. */
    DUPLICATES,
    /** Shared before an exam date. */
    SHARING_BEFORE_DATE,
    /** The owner is leaving. */
    OWNER_LEAVING,
    /** The owner is a generic / shared account. */
    GENERIC_OWNER,
    /** The file was deleted or trashed. */
    DELETED_OR_TRASHED
}
