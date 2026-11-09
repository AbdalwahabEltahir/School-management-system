package qa.sijill.sijill.shared.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Web security (proposal section 4 and 8.2).
 *
 * <ul>
 *   <li><b>Sign-in:</b> OpenID Connect against the school workspace when a client registration is
 *       configured ({@code spring.security.oauth2.client.registration.*} in application.yml). Without
 *       one (local development) Spring's form login is used so the app still starts.</li>
 *   <li><b>Everything is authenticated</b> except the health probe. <i>Who may do what</i> is decided
 *       server-side by {@link AccessPolicy}, not here.</li>
 *   <li><b>CSRF</b> protection stays on; the token is exposed in an {@code XSRF-TOKEN} cookie that the
 *       front end echoes back in the {@code X-XSRF-TOKEN} header.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                // dev-only sign-in links; the controller exists only in the "dev" profile, so elsewhere this is a 404
                .requestMatchers("/dev/**").permitAll()
                .anyRequest().authenticated());

        http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));

        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(Customizer.withDefaults());
        } else {
            http.formLogin(Customizer.withDefaults());
        }
        return http.build();
    }
}
