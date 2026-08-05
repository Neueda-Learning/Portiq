package com.portiq.controller;

import com.portiq.dto.AuthResponse;
import com.portiq.dto.LoginRequest;
import com.portiq.model.User;
import com.portiq.security.JwtService;
import com.portiq.security.webauthn.WebAuthnService;
import com.portiq.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Login and biometric credential operations")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final WebAuthnService webAuthnService;

    public AuthController(UserService userService, JwtService jwtService, WebAuthnService webAuthnService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.webAuthnService = webAuthnService;
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with username and password")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        return userService.authenticate(request.getUsername(), request.getPassword())
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(new AuthResponse(
                        jwtService.generateToken(user.getUsername()),
                        user.getUsername(),
                        userService.hasBiometricCredential(user.getId()))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid username or password")));
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
            return ResponseEntity.ok(Map.of("registered", true));
        } catch (Exception e) {
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
    public ResponseEntity<?> loginVerify(@RequestBody Map<String, Object> body) {
        try {
            User user = webAuthnService.verifyLogin(body);
            return ResponseEntity.ok(new AuthResponse(
                    jwtService.generateToken(user.getUsername()),
                    user.getUsername(),
                    true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }
}
