package qa.sijill.sijill.workspace.google;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.JdbcOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Wiring for the Google Drive connector. The one important bit: OAuth2 tokens are kept in the database
 * (not in the web session), so the background checks can use the refresh token long after the person
 * signed in. Spring Security picks this bean up for sign-in automatically.
 */
@Configuration
@EnableConfigurationProperties(GoogleDriveProperties.class)
@ConditionalOnProperty(name = "sijill.google.drive.enabled", havingValue = "true")
class GoogleDriveConfig {

    @Bean
    OAuth2AuthorizedClientService authorizedClientService(JdbcTemplate jdbc, ClientRegistrationRepository registrations) {
        return new JdbcOAuth2AuthorizedClientService(jdbc, registrations);
    }
}
