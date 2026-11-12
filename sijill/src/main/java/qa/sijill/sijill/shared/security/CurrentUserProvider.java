package qa.sijill.sijill.shared.security;

/**
 * "Who is calling?" as an interface, implemented by the {@code people} module (which owns the people
 * table). Lets shared web code resolve the signed-in person without depending on {@code people}.
 */
public interface CurrentUserProvider {

    /** The signed-in person; throws {@code UnauthenticatedException} (HTTP 401) if there is none. */
    CurrentUser require();
}
