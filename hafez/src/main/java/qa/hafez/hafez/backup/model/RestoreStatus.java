package qa.hafez.hafez.backup.model;

/** Progress of a restore request (FR-13.3). Hafez itself never restores; School IT does, with workspace tools. */
public enum RestoreStatus {
    REQUESTED, APPROVED, RESTORED, REJECTED
}
