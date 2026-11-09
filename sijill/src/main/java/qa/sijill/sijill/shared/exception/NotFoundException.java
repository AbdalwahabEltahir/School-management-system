package qa.sijill.sijill.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * 404. Also used instead of 403 when a user asks for a record they are not allowed to see, so the
 * existence of other people's files and issues is never revealed.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String what) {
        super(HttpStatus.NOT_FOUND, what + " was not found.");
    }
}
