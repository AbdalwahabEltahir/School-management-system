package qa.sijill.sijill.shared.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
                                            @Value("${sijill.google.drive.enabled:false}") boolean driveEnabled) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                // dev-only sign-in links; the controller exists only in the "dev" profile, so elsewhere this is a 404
                .requestMatchers("/dev/**").permitAll()
                .anyRequest().authenticated());

        // Single-page front end: plain (non-XOR) token handler, and a filter that makes sure the cookie is
        // written on every response (Spring only writes it once something reads the deferred token).
        http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
        http.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        ClientRegistrationRepository registrations = clientRegistrations.getIfAvailable();
        if (registrations != null) {
            // Always show Google's account chooser. When Sijill reads Drive it also asks for offline access
            // (a refresh token) and consent, so background checks keep working after the person signs out.
            var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations,
                    OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
            resolver.setAuthorizationRequestCustomizer(c -> c.additionalParameters(p -> {
                p.put("prompt", driveEnabled ? "select_account consent" : "select_account");
                if (driveEnabled) {
                    p.put("access_type", "offline");
                }
            }));
            http.oauth2Login(login -> login.authorizationEndpoint(a -> a.authorizationRequestResolver(resolver))
                    .failureHandler((request, response, exception) -> {
                        // Spring only shows "Invalid credentials"; the real reason from Google goes to the log
                        log.warn("Sign-in failed: {}", exception.getMessage());
                        response.sendRedirect(request.getContextPath() + "/login?error");
                    }));
        } else {
            http.formLogin(Customizer.withDefaults());
        }
        return http.build();
    }

    /** Reads the deferred CSRF token so the {@code XSRF-TOKEN} cookie is always sent to the browser. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
