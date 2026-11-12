package qa.hafez.hafez.shared.exception;

import org.springframework.http.HttpStatus;

/** 403: the signed-in person's role does not allow this action (enforced on the server, never by hiding buttons). */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
