package qa.sijill.sijill.setup.dev;

import java.util.List;

/**
 * <b>Demo only.</b> The five demonstration schools (A to E): realistic invented names and web domains, and how each
 * one is doing. Every school improves over the months, but they start from different places: A and B are strong,
 * C is in the middle, D and E are struggling and climbing.
 *
 * @param letter        A to E
 * @param base          how the school's marks sit against an average (added to every student's ability)
 * @param gain          how much marks improve across the history (credible upward trend)
 * @param absenceStart  share of absences four months ago
 * @param absenceEnd    share of absences now
 * @param openStart     extra open file issues four months ago (they have been fixed since)
 * @param clean         files with nothing wrong (school A adds these on top of its four showcase files)
 * @param personal      contact lists left in personal drives (a normal issue each)
 * @param publicLink    mark books shared with anyone who has the link (an urgent issue each)
 * @param tickets       support tickets opened over the months
 * @param lateSubjects  how many subjects have not yet submitted this week's grades
 */
public final class DemoSchools {

    private DemoSchools() {
    }

    public record Profile(String letter, String name, String domain, double base, double gain, double absenceStart,
                          double absenceEnd, int openStart, int clean, int personal, int publicLink, int tickets,
                          int lateSubjects) {}

    public static final List<Profile> ALL = List.of(
            new Profile("A", "Al Noor Academy", "alnoor-academy.edu.qa", .08, .03, .03, .02, 2, 120, 1, 0, 2, 0),
            new Profile("B", "Al Rayyan Model School", "rayyan-model.edu.qa", .03, .04, .06, .04, 3, 72, 2, 1, 3, 0),
            new Profile("C", "Doha Heritage School", "doha-heritage.edu.qa", -.02, .06, .08, .05, 5, 42, 3, 1, 4, 1),
            new Profile("D", "Al Khor Preparatory School", "alkhor-prep.edu.qa", -.10, .09, .13, .08, 6, 38, 3, 2, 6, 2),
            new Profile("E", "Al Wakrah Independent School", "wakrah-independent.edu.qa", -.14, .12, .16, .09, 7, 34, 4, 2, 7, 3));
}
