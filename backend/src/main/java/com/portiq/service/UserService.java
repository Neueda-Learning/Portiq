package com.portiq.service;

import com.portiq.model.User;
import com.portiq.repository.UserRepository;
import com.portiq.repository.WebauthnCredentialRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final WebauthnCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, WebauthnCredentialRepository credentialRepository,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * A hash of a random value nobody knows, verified against when the username does not exist.
     *
     * <p>Without it an unknown username returns in microseconds while a known one costs a full
     * BCrypt verification, and that difference is measurable over the network - it tells an
     * attacker which usernames are real before they have guessed a single password. Doing the same
     * work either way removes the signal.
     *
     * <p>Generated at startup rather than hard-coded, so it is guaranteed to be a well-formed hash
     * at the encoder's own cost factor - a malformed literal would be rejected on sight and skip
     * the very work it exists to perform.
     */
    private volatile String dummyHash;

    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            synchronized (this) {
                if (dummyHash == null) {
                    byte[] noise = new byte[32];
                    new SecureRandom().nextBytes(noise);
                    dummyHash = passwordEncoder.encode(Base64.getEncoder().encodeToString(noise));
                }
                hash = dummyHash;
            }
        }
        return hash;
    }

    public Optional<User> authenticate(String username, String password) {
        Optional<User> user = username == null ? Optional.empty() : userRepository.findByUsername(username);
        String hash = user.map(User::getPasswordHash).orElseGet(this::dummyHash);

        boolean matches = passwordEncoder.matches(password == null ? "" : password, hash);
        return matches ? user : Optional.empty();
    }

    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));
    }

    public User getSoleUser() {
        return userRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No user account has been set up"));
    }

    public boolean hasBiometricCredential(Long userId) {
        return !credentialRepository.findByUserId(userId).isEmpty();
    }
}
