package qa.sijill.sijill.organisation.model;

/** Well-known keys for {@link Setting} rows, with their defaults (FR-8.5). Configuration over code. */
public final class SettingKeys {

    private SettingKeys() {}

    /** Classifier confidence below this goes to "Needs review" (0.0 – 1.0). */
    public static final String CONFIDENCE_THRESHOLD = "classifier.confidence-threshold";
    public static final String CONFIDENCE_THRESHOLD_DEFAULT = "0.80";

    /** Activity-log retention in years: 1, 2 or 5 (section 8.3). */
    public static final String LOG_RETENTION_YEARS = "privacy.log-retention-years";
    public static final String LOG_RETENTION_YEARS_DEFAULT = "2";

    /** Deletions per hour that trigger a bulk-deletion incident (section 5.4). */
    public static final String BULK_DELETION_THRESHOLD = "alerts.bulk-deletion-per-hour";
    public static final String BULK_DELETION_THRESHOLD_DEFAULT = "20";

    /** Sender address for outgoing messages (a dedicated account, section 5.4). */
    public static final String SENDER_ADDRESS = "messages.sender-address";

    /** Display label for a site: "School" or "Campus" (FR-8.3). */
    public static final String SITE_LABEL = "display.site-label";
    public static final String SITE_LABEL_DEFAULT = "School";
}
