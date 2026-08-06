package com.portiq.service;

import com.portiq.model.User;
import com.portiq.repository.UserRepository;
import com.portiq.repository.WebauthnCredentialRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    public Optional<User> authenticate(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()));
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
