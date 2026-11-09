package qa.sijill.sijill.people.model;

/** Lifecycle of a person, mirrored from the directory (proposal section 4, "Leavers"). */
public enum PersonStatus {
    /** In the directory, welcome message not sent yet. */
    NEW,
    ACTIVE,
    /** Leaving soon: rule R8 asks them to move school files out of their personal drive. */
    LEAVING,
    /** Gone: access is removed and open issues move to the coordinator. */
    LEFT
}
