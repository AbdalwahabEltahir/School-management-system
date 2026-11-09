package qa.sijill.sijill.backup.model;

/** Progress of a restore request (FR-13.3). Sijill itself never restores; School IT does, with workspace tools. */
public enum RestoreStatus {
    REQUESTED, APPROVED, RESTORED, REJECTED
}
