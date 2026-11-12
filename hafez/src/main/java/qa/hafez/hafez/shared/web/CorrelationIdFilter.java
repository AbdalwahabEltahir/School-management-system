package qa.hafez.hafez.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every HTTP request a <b>correlation ID</b> that appears in every log line (via the logging MDC)
 * and in the {@code X-Correlation-Id} response header. With it you can follow one "I've fixed it" press
 * through the controller, the services, the re-check and the e-mail by searching the logs for one value.
 *
 * <p>A caller (for example a reverse proxy) may supply its own {@code X-Correlation-Id}; background
 * jobs create theirs with {@link CorrelationIds#runWithNewId}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        // Only accept short, safe values from callers so log lines cannot be forged.
        String id = incoming != null && incoming.matches("[A-Za-z0-9-]{8,64}") ? incoming : CorrelationIds.newId();
        MDC.put(CorrelationIds.MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }
}
