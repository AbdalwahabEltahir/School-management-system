package qa.sijill.sijill.people.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

import qa.sijill.sijill.shared.exception.ConflictException;

/** Slows down guessing on the public sign-in and registration calls: at most N tries per key in a time window. */
@Component
public class AttemptLimiter {

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public void hit(String key, int max, Duration window) {
        Instant now = Instant.now();
        Deque<Instant> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(window))) {
                q.pollFirst();
            }
            if (q.size() >= max) {
                throw new ConflictException("Too many tries. Please wait a few minutes and try again.");
            }
            q.addLast(now);
        }
    }
}
