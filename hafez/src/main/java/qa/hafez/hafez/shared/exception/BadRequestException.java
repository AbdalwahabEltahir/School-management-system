package qa.hafez.hafez.shared.exception;

import org.springframework.http.HttpStatus;

/** 400: the request is well-formed but not acceptable (for example a missing mandatory note). */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
