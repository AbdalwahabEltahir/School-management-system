package qa.sijill.sijill.approvals;

/** The actions that need a second person's approval. Only deleting a file does. */
public final class ApprovalActions {
    private ApprovalActions() {}

    /** Removing a file from Hafez's register. The drive itself is never touched. */
    public static final String FILE_DELETE = "FILE_DELETE";
}
