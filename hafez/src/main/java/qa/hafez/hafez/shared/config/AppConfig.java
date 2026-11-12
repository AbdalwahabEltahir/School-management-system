package qa.hafez.hafez.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import qa.hafez.hafez.shared.util.SchoolCalendar;

/**
 * Application-wide infrastructure beans.
 *
 * <ul>
 *   <li>{@link Clock}: services ask this clock for "now" instead of calling {@code Instant.now()}, so
 *       tests can fix the time. It runs in the school time zone (Asia/Qatar).</li>
 *   <li>Scheduling: see {@code ScheduledJobs} for the list of background jobs.</li>
 *   <li>{@code checkExecutor}: runs long checks in the background so "Run a full check now" returns
 *       immediately instead of holding the HTTP request for hours.</li>
 * </ul>
 */
@Configuration
@EnableScheduling
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.system(SchoolCalendar.ZONE);
    }

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor checkExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("check-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        return executor;
    }
}
