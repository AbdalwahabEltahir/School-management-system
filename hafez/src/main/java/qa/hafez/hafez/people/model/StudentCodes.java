package qa.hafez.hafez.people.model;

import java.security.SecureRandom;

/** Student IDs: a plain 10-digit number (first digit never 0), like a school-issued ID. Easy to type, hard to guess. */
public final class StudentCodes {

    private static final SecureRandom RANDOM = new SecureRandom();

    private StudentCodes() {
    }

    public static String next() {
        StringBuilder sb = new StringBuilder();
        sb.append(1 + RANDOM.nextInt(9));
        for (int i = 0; i < 9; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
