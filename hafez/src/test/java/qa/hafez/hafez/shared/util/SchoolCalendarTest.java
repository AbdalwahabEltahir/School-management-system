package qa.hafez.hafez.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/** The school week in Qatar: Sunday – Thursday are school days; quiet hours are 20:00 – 07:00 and the weekend. */
class SchoolCalendarTest {

    /** 2026-11-08 is a Sunday. */
    private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 8);

    private static Instant at(LocalDate day, int hour, int minute) {
        return LocalDateTime.of(day, java.time.LocalTime.of(hour, minute)).atZone(SchoolCalendar.ZONE).toInstant();
    }

    @Test
    void fridayAndSaturdayAreNotSchoolDays() {
        assertThat(SchoolCalendar.isSchoolDay(SUNDAY)).isTrue();                 // Sunday
        assertThat(SchoolCalendar.isSchoolDay(SUNDAY.plusDays(4))).isTrue();     // Thursday
        assertThat(SchoolCalendar.isSchoolDay(SUNDAY.plusDays(5))).isFalse();    // Friday
        assertThat(SchoolCalendar.isSchoolDay(SUNDAY.plusDays(6))).isFalse();    // Saturday
    }

    @Test
    void addingSchoolDaysSkipsTheWeekend() {
        Instant thursdayNoon = at(SUNDAY.plusDays(4), 12, 0);
        // one school day after Thursday is Sunday, not Friday
        assertThat(SchoolCalendar.addSchoolDays(thursdayNoon, 1)).isEqualTo(at(SUNDAY.plusDays(7), 12, 0));
        assertThat(SchoolCalendar.addSchoolDays(thursdayNoon, 0)).isEqualTo(thursdayNoon);
        assertThat(SchoolCalendar.addSchoolDays(at(SUNDAY, 9, 30), 2)).isEqualTo(at(SUNDAY.plusDays(2), 9, 30));
    }

    @Test
    void quietHoursAreNightsAndWeekends() {
        assertThat(SchoolCalendar.isQuietTime(at(SUNDAY, 6, 59))).isTrue();
        assertThat(SchoolCalendar.isQuietTime(at(SUNDAY, 7, 0))).isFalse();
        assertThat(SchoolCalendar.isQuietTime(at(SUNDAY, 19, 59))).isFalse();
        assertThat(SchoolCalendar.isQuietTime(at(SUNDAY, 20, 0))).isTrue();
        assertThat(SchoolCalendar.isQuietTime(at(SUNDAY.plusDays(5), 12, 0))).isTrue(); // Friday noon
    }

    @Test
    void urgentMessagesIgnoreQuietHours() {
        Instant night = at(SUNDAY, 23, 0);
        assertThat(SchoolCalendar.nextDeliveryTime(night, true)).isEqualTo(night);
    }

    @Test
    void normalMessagesWaitUntilTheNextSchoolMorning() {
        // Sunday 23:00 -> Monday 07:00
        assertThat(SchoolCalendar.nextDeliveryTime(at(SUNDAY, 23, 0), false)).isEqualTo(at(SUNDAY.plusDays(1), 7, 0));
        // Sunday 03:00 -> Sunday 07:00 (same day, before school)
        assertThat(SchoolCalendar.nextDeliveryTime(at(SUNDAY, 3, 0), false)).isEqualTo(at(SUNDAY, 7, 0));
        // Thursday 21:00 -> Sunday 07:00 (over the weekend)
        assertThat(SchoolCalendar.nextDeliveryTime(at(SUNDAY.plusDays(4), 21, 0), false)).isEqualTo(at(SUNDAY.plusDays(7), 7, 0));
        // During the day: immediately
        Instant noon = at(SUNDAY, 12, 0);
        assertThat(SchoolCalendar.nextDeliveryTime(noon, false)).isEqualTo(noon);
    }
}
