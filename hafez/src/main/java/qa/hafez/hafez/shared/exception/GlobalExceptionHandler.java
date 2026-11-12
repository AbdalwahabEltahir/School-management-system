package qa.hafez.hafez.shared.exception;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import qa.hafez.hafez.shared.web.CorrelationIds;

/**
 * Turns every exception into one small JSON body {@code {"status":404,"message":"...","correlationId":"..."}}.
 *
 * <ul>
 *   <li>Hafez's own {@link ApiException}s keep their status and plain-word message.</li>
 *   <li>Spring's standard web errors (unknown URL, wrong method, bad JSON, …) keep <b>their</b> status:
 *       this class builds on {@link ResponseEntityExceptionHandler} so they are not mistaken for crashes.</li>
 *   <li>Anything else is a real bug: logged in full with the correlation ID, reported to the user without
 *       internal detail (section 8.2). Quote the correlation ID to find it in the logs.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** The JSON error body. */
    public record ApiError(int status, String message, String correlationId) {}

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(error(e.getStatus().value(), e.getMessage()));
    }

    /** Bean-validation failures on request bodies: list the offending fields in plain words. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST.value(), message));
    }

    /** Every other Spring web error (404, 405, 415, …) arrives here with its own status; we only reshape the body. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        String message = body instanceof ProblemDetail problem && problem.getDetail() != null
                ? problem.getDetail() : "The request could not be processed.";
        return ResponseEntity.status(status).headers(headers).body(error(status.value(), message));
    }

    /** A genuine bug. Logged with the correlation ID; the user sees no internal detail. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(500,
                "Something went wrong. Please try again, or contact your school IT and quote the reference."));
    }

    private ApiError error(int status, String message) {
        return new ApiError(status, message, CorrelationIds.current());
    }
}
