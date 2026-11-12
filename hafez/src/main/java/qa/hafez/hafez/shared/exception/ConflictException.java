package qa.hafez.hafez.shared.exception;

import org.springframework.http.HttpStatus;

/** 409: the action conflicts with the current state (for example acting on an issue that is already closed). */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
