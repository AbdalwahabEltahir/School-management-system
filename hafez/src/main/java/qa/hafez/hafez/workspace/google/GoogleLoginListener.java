package qa.hafez.hafez.workspace.google;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * When a configured Drive account, or anybody who has an account in Hafez, signs in, remember its identity so the
 * connector can use its stored token. That sign-in is the consent to read the Drive (drive.readonly). Google accounts
 * that have no Hafez account are ignored, so their Drives are never read.
 */
@Component
@ConditionalOnProperty(name = "hafez.google.drive.enabled", havingValue = "true")
class GoogleLoginListener {

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginListener.class);

    private final GoogleDriveProperties props;
    private final GoogleAccountStore accounts;

    GoogleLoginListener(GoogleDriveProperties props, GoogleAccountStore accounts) {
        this.props = props;
        this.accounts = accounts;
    }

    @EventListener
    void onLogin(InteractiveAuthenticationSuccessEvent event) {
        if (!(event.getAuthentication() instanceof OAuth2AuthenticationToken token)
                || !"google".equals(token.getAuthorizedClientRegistrationId())) {
            return;
        }
        String email = token.getPrincipal().getAttribute("email");
        boolean configured = email != null && props.accounts().stream().anyMatch(email::equalsIgnoreCase);
        if (email != null && (configured || accounts.isHafezPerson(email))) {
            accounts.remember(token.getName(), email.toLowerCase());
            log.info("Google Drive account {} signed in; its token will be used to read Drive", email);
        }
    }
}
