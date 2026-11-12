package qa.sijill.sijill.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for errors that are reported to the client with a specific HTTP status.
 * Messages are written in plain words for staff (wording rules, proposal 10.3) and never contain
 * internal detail. {@code GlobalExceptionHandler} turns these into JSON responses.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
