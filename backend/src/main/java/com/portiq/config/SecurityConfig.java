package com.portiq.config;

import com.portiq.security.ClientIpResolver;
import com.portiq.security.JsonAuthenticationEntryPoint;
import com.portiq.security.JwtAuthFilter;
import com.portiq.security.RateLimitFilter;
import com.portiq.security.RateLimiter;
import com.portiq.security.SecurityAuditLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * This application serves JSON and nothing else, so the safest possible policy applies: no
     * script, style, image or frame may load, and the responses may not be framed at all. It costs
     * nothing here and neutralises a whole class of attacks that depend on a browser treating an
     * API response as a document.
     */
    private static final String API_CONTENT_SECURITY_POLICY =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'; sandbox";

    /**
     * Swagger UI is a real page and needs its own bundle. It still may not be framed, load anything
     * off-site, or send a form anywhere - only the inline styles and same-origin scripts it ships
     * with are allowed. This applies only when the docs are exposed at all.
     */
    private static final String DOCS_CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data:; font-src 'self' data:; connect-src 'self'; object-src 'none'; "
                    + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    /** Features this API has no use for. Denying them costs nothing and shrinks the attack surface. */
    private static final String PERMISSIONS_POLICY =
            "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), "
                    + "microphone=(), payment=(), usb=(), interest-cohort=()";

    private static final String[] DOCS_PATHS = {
            "/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/api-docs.yaml", "/v3/api-docs/**"
    };

    private final JwtAuthFilter jwtAuthFilter;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final SecurityAuditLogger audit;

    /**
     * Origins allowed to call this API from a browser. Deliberately an explicit list with no
     * wildcard support: the responses carry portfolio data, and {@code *} would let any page on the
     * internet read it through a victim's browser.
     */
    @Value("${app.security.cors.allowed-origins}")
    private String[] allowedOrigins;

    /** Whether the OpenAPI spec and Swagger UI are reachable without logging in. */
    @Value("${app.security.docs.public:true}")
    private boolean docsPublic;

    @Value("${app.security.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @Value("${app.security.hsts.max-age-seconds:31536000}")
    private long hstsMaxAgeSeconds;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          JsonAuthenticationEntryPoint authenticationEntryPoint,
                          RateLimiter rateLimiter,
                          ClientIpResolver clientIpResolver,
                          SecurityAuditLogger audit) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.audit = audit;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Cost 12 rather than the library default of 10: roughly four times the work per guess for
        // an attacker holding the hashes, and still only tens of milliseconds on a real login.
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        RequestMatcher docsMatcher = docsMatcher();

        http
                // No cookies are used - the session token travels in an Authorization header that a
                // cross-site request cannot set - so there is nothing for CSRF tokens to protect.
                // This holds only as long as authentication stays header-based; the day a cookie is
                // introduced, CSRF protection has to come back with it.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(authenticationEntryPoint))
                .headers(headers -> headers
                        // The H2 console lives in a frameset, so it is the sole reason to relax
                        // this - and only in the dev profile where the console exists at all.
                        .frameOptions(frame -> {
                            if (h2ConsoleEnabled) {
                                frame.sameOrigin();
                            } else {
                                frame.deny();
                            }
                        })
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(hstsMaxAgeSeconds))
                        .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "no-referrer"))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(docsMatcher,
                                new StaticHeadersWriter("Content-Security-Policy", DOCS_CONTENT_SECURITY_POLICY)))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(new NegatedRequestMatcher(docsMatcher),
                                new StaticHeadersWriter("Content-Security-Policy", API_CONTENT_SECURITY_POLICY))))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/auth/login", "/api/auth/webauthn/login/**").permitAll();
                    auth.requestMatchers("/actuator/health", "/actuator/info").permitAll();

                    if (docsPublic) {
                        auth.requestMatchers(DOCS_PATHS).permitAll();
                    }
                    if (h2ConsoleEnabled) {
                        auth.requestMatchers("/h2-console/**").permitAll();
                    }

                    // Everything not named above needs a valid token, including anything added
                    // later - a new endpoint is protected by default rather than by remembering.
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter(), JwtAuthFilter.class);

        return http.build();
    }

    private RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter(rateLimiter, clientIpResolver, audit, rateLimitEnabled);
    }

    private RequestMatcher docsMatcher() {
        AntPathMatcher matcher = new AntPathMatcher();
        List<RequestMatcher> matchers = Arrays.stream(DOCS_PATHS)
                .map(pattern -> (RequestMatcher) request -> matcher.match(pattern, request.getRequestURI()))
                .toList();
        return new OrRequestMatcher(matchers);
    }

    /**
     * Boot registers every {@code Filter} bean into the outer servlet chain as well as the security
     * chain. For {@link JwtAuthFilter} that means authenticating twice per request, on paths Spring
     * Security never sees. Harmless today, but it is the kind of duplication that quietly defeats a
     * later security filter, so it is switched off explicitly.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(allowedOrigins)
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        if (origins.contains("*")) {
            throw new IllegalStateException(
                    "app.security.cors.allowed-origins must name each origin explicitly - '*' would let any "
                            + "site read portfolio data through a logged-in user's browser");
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        config.setExposedHeaders(List.of("Content-Disposition", "Retry-After", "X-RateLimit-Remaining"));
        // The token is sent in a header, not a cookie, so the browser never needs to attach
        // ambient credentials - and leaving this off keeps a stolen origin from replaying a session.
        config.setAllowCredentials(false);
        config.setMaxAge(1800L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
