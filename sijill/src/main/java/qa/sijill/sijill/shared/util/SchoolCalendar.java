package qa.sijill.sijill.shared.util;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Pure date rules of the school week in Qatar (proposal 5.4 and 9.3). No state, no Spring: easy to test.
 *
 * <ul>
 *   <li>School days are Sunday – Thursday; Friday and Saturday are the weekend.</li>
 *   <li>Quiet hours are 20:00 – 07:00 and the whole weekend; urgent messages ignore them.</li>
 *   <li>"Reminder days" and "one school day later" count school days only.</li>
 * </ul>
 */
public final class SchoolCalendar {

    public static final ZoneId ZONE = ZoneId.of("Asia/Qatar");
    private static final LocalTime QUIET_START = LocalTime.of(20, 0);
    private static final LocalTime QUIET_END = LocalTime.of(7, 0);

    private SchoolCalendar() {}

    public static boolean isSchoolDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.FRIDAY && day != DayOfWeek.SATURDAY;
    }

    /** Adds whole school days, skipping the weekend, keeping the time of day. {@code days == 0} returns {@code from}. */
    public static Instant addSchoolDays(Instant from, int days) {
        ZonedDateTime t = from.atZone(ZONE);
        int added = 0;
        while (added < days) {
            t = t.plusDays(1);
            if (isSchoolDay(t.toLocalDate())) {
                added++;
            }
        }
        return t.toInstant();
    }

    /** True during quiet hours: before 07:00, from 20:00, or on a weekend day. */
    public static boolean isQuietTime(Instant moment) {
        ZonedDateTime t = moment.atZone(ZONE);
        LocalTime time = t.toLocalTime();
        return !isSchoolDay(t.toLocalDate()) || !time.isBefore(QUIET_START) || time.isBefore(QUIET_END);
    }

    /**
     * When a message created at {@code now} may be delivered. Urgent messages go at once; others wait
     * until 07:00 on the next school day if created during quiet hours.
     */
    public static Instant nextDeliveryTime(Instant now, boolean urgent) {
        if (urgent) {
            return now;
        }
        ZonedDateTime t = now.atZone(ZONE);
        while (isQuietTime(t.toInstant())) {
            boolean beforeMorningOfSchoolDay = isSchoolDay(t.toLocalDate()) && t.toLocalTime().isBefore(QUIET_END);
            LocalDate day = beforeMorningOfSchoolDay ? t.toLocalDate() : t.toLocalDate().plusDays(1);
            t = day.atTime(QUIET_END).atZone(ZONE);
        }
        return t.toInstant();
    }
}
