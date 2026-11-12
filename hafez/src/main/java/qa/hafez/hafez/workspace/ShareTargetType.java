package qa.hafez.hafez.workspace;

/** Who a sharing grant is given to. Read from the workspace, never written back. */
public enum ShareTargetType {
    /** "Anyone with the link" – a public link (rule R2). */
    ANYONE_WITH_LINK,
    /** A person outside the network (rule R3). */
    EXTERNAL_USER,
    /** A named person inside the network. */
    INTERNAL_USER,
    /** A directory group inside the network. */
    INTERNAL_GROUP,
    /** Everybody in the school domain – broad internal access (rule R5). */
    ALL_STAFF
}
