package com.portiq.security.webauthn;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebAuthnChallengeStore {

    private final Map<String, byte[]> challenges = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public byte[] newChallenge(String key) {
        byte[] challenge = new byte[32];
        random.nextBytes(challenge);
        challenges.put(key, challenge);
        return challenge;
    }

    public byte[] consume(String key) {
        return challenges.remove(key);
    }
}
