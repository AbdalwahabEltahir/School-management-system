package qa.hafez.hafez.backup.model;

/** How much a restore request covers. Whole-drive and whole-school restores need two approvers (section 9.4). */
public enum RestoreScope {
    FILE, SUBJECT_DRIVE, WHOLE_SCHOOL
}
