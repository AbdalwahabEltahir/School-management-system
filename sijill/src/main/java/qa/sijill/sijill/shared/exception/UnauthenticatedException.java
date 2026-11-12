package qa.sijill.sijill.shared.exception;

import org.springframework.http.HttpStatus;

/** 401: nobody is signed in, or the signed-in account is not set up in Hafez. */
public class UnauthenticatedException extends ApiException {

    public UnauthenticatedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
