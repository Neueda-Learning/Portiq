package com.portiq.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Holds the AES key used by the encrypted JPA converters. JPA AttributeConverters are
 * instantiated by Hibernate outside the Spring context, so the key is exposed through a
 * static field populated once at startup rather than via constructor injection.
 */
@Component
public class EncryptionKeyHolder {

    private static volatile SecretKey key;

    @Value("${app.encryption.key:}")
    private String configuredKey;

    @PostConstruct
    public void init() {
        if (configuredKey != null && !configuredKey.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(configuredKey.trim());
            key = new SecretKeySpec(decoded, "AES");
        } else {
            key = generateEphemeralKey();
        }
    }

    public static SecretKey getKey() {
        if (key == null) {
            key = generateEphemeralKey();
        }
        return key;
    }

    private static SecretKey generateEphemeralKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            return generator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to generate fallback encryption key", e);
        }
    }
}
