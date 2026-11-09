package qa.sijill.sijill.shared.model;

import java.util.EnumSet;
import java.util.Set;

/** What a {@link JobRun} did. Check runs follow FR-1.1; backup runs follow section 9.2. */
public enum JobType {
    /** The first check, run by Leadership or School IT. */
    FIRST_CHECK,
    /** The nightly full check at 02:00. */
    FULL_CHECK,
    /** The change feed, within minutes of a change. */
    CHANGE_FEED_CHECK,
    /** "Run a full check now". */
    MANUAL_CHECK,
    /** Hourly copy of changed files to the backup vault. */
    HOURLY_BACKUP,
    /** Nightly reconciliation of the vault. */
    NIGHTLY_BACKUP;

    public static final Set<JobType> CHECKS = EnumSet.of(FIRST_CHECK, FULL_CHECK, CHANGE_FEED_CHECK, MANUAL_CHECK);
    public static final Set<JobType> BACKUPS = EnumSet.of(HOURLY_BACKUP, NIGHTLY_BACKUP);
}
