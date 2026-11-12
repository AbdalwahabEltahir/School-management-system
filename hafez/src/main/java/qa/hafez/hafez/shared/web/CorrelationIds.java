package qa.hafez.hafez.shared.web;

import java.util.UUID;
import org.slf4j.MDC;

/** Helpers for the correlation ID outside HTTP requests (scheduled jobs, background check runs). */
public final class CorrelationIds {

    /** The logging MDC key; the log pattern in application.yml prints {@code %X{correlationId}}. */
    public static final String MDC_KEY = "correlationId";

    private CorrelationIds() {}

    public static String newId() {
        return UUID.randomUUID().toString().substring(0, 13); // e.g. 3f2a9c1e-5b7d: short but unique enough to grep
    }

    /** The ID of the current request or job, or null if there is none. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** Runs a job with its own correlation ID so all of its log lines can be found together. */
    public static void runWithNewId(String jobName, Runnable job) {
        runWith(jobName + "-" + newId(), job);
    }

    /**
     * Runs a job under an existing correlation ID. A background check started by a click uses the
     * request's ID, so the click and the whole check can be followed in the logs with one search.
     */
    public static void runWith(String id, Runnable job) {
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, id == null ? newId() : id);
        try {
            job.run();
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }
}
