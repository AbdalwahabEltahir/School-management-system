package qa.sijill.sijill.workspace.google;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

/**
 * A valid access token for one Drive account. Works from background threads: it loads the stored token
 * and, if it has expired, uses the stored refresh token to get a new one (and stores that).
 */
@Component
@ConditionalOnProperty(name = "sijill.google.drive.enabled", havingValue = "true")
class GoogleTokenProvider {

    private final GoogleAccountStore accounts;
    private final AuthorizedClientServiceOAuth2AuthorizedClientManager manager;

    GoogleTokenProvider(ClientRegistrationRepository registrations, OAuth2AuthorizedClientService clients,
                        GoogleAccountStore accounts) {
        this.accounts = accounts;
        this.manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
        this.manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().refreshToken().build());
    }

    /** The accounts that have signed in to Sijill, so their Drives can be read. */
    java.util.List<String> signedInAccounts() {
        return accounts.emails();
    }

    String accessToken(String email) {
        String principal = accounts.principalName(email).orElseThrow(() -> new IllegalStateException(
                "The Google Drive account " + email + " has not signed in yet. Sign in to Sijill once with that account."));
        OAuth2AuthorizedClient client = manager.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId("google").principal(principal).build());
        if (client == null) {
            throw new IllegalStateException("No stored Google token for " + email + ". Sign out and sign in again with that account.");
        }
        return client.getAccessToken().getTokenValue();
    }
}
