package com.portiq.controller;

import com.portiq.dto.AuthResponse;
import com.portiq.dto.LoginRequest;
import com.portiq.model.User;
import com.portiq.security.ClientIpResolver;
import com.portiq.security.JwtService;
import com.portiq.security.LoginAttemptService;
import com.portiq.security.SecurityAuditLogger;
import com.portiq.security.TokenDenylist;
import com.portiq.security.webauthn.WebAuthnService;
import com.portiq.service.UserService;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Login and biometric credential operations")
public class AuthController {

    /**
     * The single message returned for every failed login. Saying "no such user" versus "wrong
     * password" would confirm which usernames exist, turning one unknown into two known halves of
     * a credential.
     */
    private static final String LOGIN_FAILED_MESSAGE = "Invalid username or password";

    private final UserService userService;
    private final JwtService jwtService;
    private final WebAuthnService webAuthnService;
    private final LoginAttemptService loginAttemptService;
    private final TokenDenylist tokenDenylist;
    private final ClientIpResolver clientIpResolver;
    private final SecurityAuditLogger audit;

    public AuthController(UserService userService, JwtService jwtService, WebAuthnService webAuthnService,
                          LoginAttemptService loginAttemptService, TokenDenylist tokenDenylist,
                          ClientIpResolver clientIpResolver, SecurityAuditLogger audit) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.webAuthnService = webAuthnService;
        this.loginAttemptService = loginAttemptService;
        this.tokenDenylist = tokenDenylist;
        this.clientIpResolver = clientIpResolver;
        this.audit = audit;
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with username and password")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String username = request.getUsername();
        String clientIp = clientIpResolver.resolve(httpRequest);

        long blockedFor = loginAttemptService.blockedForSeconds(username, clientIp);
        if (blockedFor > 0) {
            audit.loginBlocked(username, httpRequest, blockedFor);
            // Deliberately not "this account is locked": that confirms the username exists. The
            // caller learns only that they must wait, which is all they need to act on.
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(blockedFor))
                    .body(Map.of("message",
                            "Too many failed sign-in attempts. Try again in " + blockedFor + " seconds."));
        }

        Optional<User> authenticated = userService.authenticate(username, request.getPassword());
        if (authenticated.isEmpty()) {
            loginAttemptService.recordFailure(username, clientIp);
            audit.loginFailed(username, httpRequest, "bad credentials");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", LOGIN_FAILED_MESSAGE));
        }

        User user = authenticated.get();
        loginAttemptService.recordSuccess(username, clientIp);
        audit.loginSucceeded(user.getUsername(), httpRequest);
        return ResponseEntity.ok(new AuthResponse(
                jwtService.generateToken(user.getUsername()),
                user.getUsername(),
                userService.hasBiometricCredential(user.getId())));
    }

    /**
     * Revokes the presented token so it stops working server-side, rather than trusting the client
     * to throw it away. Always answers 200: a caller with an already-expired token is in exactly
     * the state they asked for, and an error there would only add a retry loop.
     */
    @PostMapping("/logout")
    @Operation(summary = "Revoke the current session token")
    public ResponseEntity<?> logout(HttpServletRequest httpRequest, Authentication authentication) {
        String header = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            jwtService.parse(header.substring(7).trim()).ifPresent(this::revoke);
        }
        if (authentication != null) {
            audit.logout(authentication.getName(), httpRequest);
        }
        return ResponseEntity.ok(Map.of("loggedOut", true));
    }

    private void revoke(Claims claims) {
        Instant expiresAt = claims.getExpiration() != null
                ? claims.getExpiration().toInstant()
                : Instant.now().plusSeconds(60);
        tokenDenylist.revoke(claims.getId(), expiresAt);
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current logged in user")
    public ResponseEntity<?> me(Authentication authentication) {
        User user = userService.getByUsername(authentication.getName());
        Map<String, Object> body = new HashMap<>();
        body.put("username", user.getUsername());
        body.put("biometricEnabled", userService.hasBiometricCredential(user.getId()));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/webauthn/registration/options")
    @Operation(summary = "Get options to register a biometric credential for the logged in user")
    public ResponseEntity<?> registrationOptions(Authentication authentication) {
        User user = userService.getByUsername(authentication.getName());
        return ResponseEntity.ok(webAuthnService.createRegistrationOptions(user));
    }

    @PostMapping("/webauthn/registration/verify")
    @Operation(summary = "Verify and store a newly registered biometric credential")
    public ResponseEntity<?> registrationVerify(Authentication authentication, @RequestBody Map<String, Object> body) {
        User user = userService.getByUsername(authentication.getName());
        String label = body.get("label") instanceof String s ? s : null;
        try {
            webAuthnService.verifyRegistration(user, body, label);
            audit.webauthnEvent("REGISTERED", user.getUsername(), "new credential stored");
            return ResponseEntity.ok(Map.of("registered", true));
        } catch (Exception e) {
            audit.webauthnEvent("REGISTRATION_REJECTED", user.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/webauthn/login/options")
    @Operation(summary = "Get options to log in with a biometric credential")
    public ResponseEntity<?> loginOptions() {
        User user = userService.getSoleUser();
        if (!userService.hasBiometricCredential(user.getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "No biometric credential is registered yet"));
        }
        return ResponseEntity.ok(webAuthnService.createLoginOptions(user));
    }

    @PostMapping("/webauthn/login/verify")
    @Operation(summary = "Verify a biometric assertion and issue a session token")
    public ResponseEntity<?> loginVerify(@RequestBody Map<String, Object> body, HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        // Biometric login is subject to the same lockout as the password path. It is the weaker of
        // the two to leave open, because it needs no username at all to attempt.
        long blockedFor = loginAttemptService.blockedForSeconds(webauthnAccountKey(), clientIp);
        if (blockedFor > 0) {
            audit.loginBlocked(webauthnAccountKey(), httpRequest, blockedFor);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(blockedFor))
                    .body(Map.of("message",
                            "Too many failed sign-in attempts. Try again in " + blockedFor + " seconds."));
        }

        try {
            User user = webAuthnService.verifyLogin(body);
            loginAttemptService.recordSuccess(webauthnAccountKey(), clientIp);
            audit.loginSucceeded(user.getUsername(), httpRequest);
            return ResponseEntity.ok(new AuthResponse(
                    jwtService.generateToken(user.getUsername()),
                    user.getUsername(),
                    true));
        } catch (Exception e) {
            loginAttemptService.recordFailure(webauthnAccountKey(), clientIp);
            audit.loginFailed(webauthnAccountKey(), httpRequest, "webauthn assertion rejected: " + e.getMessage());
            // The underlying reason names internal checks (challenge, signature, counter). It is
            // useful in the log and useless to a legitimate user, so it stays out of the response.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Biometric sign-in could not be verified"));
        }
    }

    /** Biometric login carries no username, so failures are counted against one shared key. */
    private String webauthnAccountKey() {
        return "webauthn";
    }
}
