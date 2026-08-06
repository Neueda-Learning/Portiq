package com.portiq.security.webauthn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portiq.model.User;
import com.portiq.model.WebauthnCredential;
import com.portiq.repository.WebauthnCredentialRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WebAuthnService {

    private final WebAuthnChallengeStore challengeStore;
    private final WebauthnCredentialRepository credentialRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.webauthn.rp-id:localhost}")
    private String rpId;

    @Value("${app.webauthn.rp-name:Portiq}")
    private String rpName;

    @Value("${app.webauthn.origin:http://localhost:5173}")
    private String origin;

    public WebAuthnService(WebAuthnChallengeStore challengeStore, WebauthnCredentialRepository credentialRepository) {
        this.challengeStore = challengeStore;
        this.credentialRepository = credentialRepository;
    }

    public Map<String, Object> createRegistrationOptions(User user) {
        byte[] challenge = challengeStore.newChallenge("reg:" + user.getUsername());

        Map<String, Object> rp = new LinkedHashMap<>();
        rp.put("id", rpId);
        rp.put("name", rpName);

        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("id", WebAuthnCrypto.base64UrlEncode(String.valueOf(user.getId()).getBytes()));
        userMap.put("name", user.getUsername());
        userMap.put("displayName", user.getUsername());

        Map<String, Object> es256 = new LinkedHashMap<>();
        es256.put("type", "public-key");
        es256.put("alg", -7);

        Map<String, Object> authenticatorSelection = new LinkedHashMap<>();
        authenticatorSelection.put("authenticatorAttachment", "platform");
        authenticatorSelection.put("userVerification", "required");
        authenticatorSelection.put("residentKey", "preferred");

        List<Map<String, Object>> excludeCredentials = new ArrayList<>();
        for (WebauthnCredential cred : credentialRepository.findByUserId(user.getId())) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "public-key");
            entry.put("id", cred.getCredentialId());
            excludeCredentials.add(entry);
        }

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("challenge", WebAuthnCrypto.base64UrlEncode(challenge));
        options.put("rp", rp);
        options.put("user", userMap);
        options.put("pubKeyCredParams", List.of(es256));
        options.put("timeout", 60000);
        options.put("attestation", "none");
        options.put("authenticatorSelection", authenticatorSelection);
        options.put("excludeCredentials", excludeCredentials);
        return options;
    }

    public WebauthnCredential verifyRegistration(User user, Map<String, Object> credentialJson, String label) {
        byte[] expectedChallenge = challengeStore.consume("reg:" + user.getUsername());

        Map<String, Object> response = castMap(credentialJson.get("response"));
        byte[] clientDataJSON = WebAuthnCrypto.base64UrlDecode((String) response.get("clientDataJSON"));
        byte[] attestationObject = WebAuthnCrypto.base64UrlDecode((String) response.get("attestationObject"));

        validateClientData(clientDataJSON, expectedChallenge, "webauthn.create");

        CborReader reader = new CborReader(attestationObject, 0);
        @SuppressWarnings("unchecked")
        Map<Object, Object> attestation = (Map<Object, Object>) reader.readValue();
        byte[] authData = (byte[]) attestation.get("authData");

        WebAuthnCrypto.AuthenticatorData parsed = WebAuthnCrypto.parseAuthenticatorData(authData);
        byte[] expectedRpIdHash = WebAuthnCrypto.sha256(rpId.getBytes());
        if (!Arrays.equals(expectedRpIdHash, parsed.rpIdHash)) {
            throw new IllegalStateException("Relying party mismatch");
        }
        if (!parsed.userPresent) {
            throw new IllegalStateException("User presence not confirmed");
        }
        if (parsed.credentialId == null || parsed.coseKey == null) {
            throw new IllegalStateException("No credential data returned by authenticator");
        }

        byte[] rawXY = WebAuthnCrypto.coseKeyToRawXY(parsed.coseKey);

        WebauthnCredential credential = new WebauthnCredential();
        credential.setUser(user);
        credential.setCredentialId(WebAuthnCrypto.base64UrlEncode(parsed.credentialId));
        credential.setPublicKeyCose(rawXY);
        credential.setSignCount(parsed.signCount);
        credential.setLabel(label != null && !label.isBlank() ? label : "Biometric credential");
        return credentialRepository.save(credential);
    }

    public Map<String, Object> createLoginOptions(User user) {
        byte[] challenge = challengeStore.newChallenge("login");

        List<Map<String, Object>> allowCredentials = new ArrayList<>();
        for (WebauthnCredential cred : credentialRepository.findByUserId(user.getId())) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "public-key");
            entry.put("id", cred.getCredentialId());
            allowCredentials.add(entry);
        }

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("challenge", WebAuthnCrypto.base64UrlEncode(challenge));
        options.put("rpId", rpId);
        options.put("timeout", 60000);
        options.put("userVerification", "required");
        options.put("allowCredentials", allowCredentials);
        return options;
    }

    public User verifyLogin(Map<String, Object> credentialJson) {
        byte[] expectedChallenge = challengeStore.consume("login");

        String id = (String) credentialJson.get("id");
        WebauthnCredential stored = credentialRepository.findByCredentialId(id)
                .orElseThrow(() -> new IllegalStateException("Unknown credential"));

        Map<String, Object> response = castMap(credentialJson.get("response"));
        byte[] clientDataJSON = WebAuthnCrypto.base64UrlDecode((String) response.get("clientDataJSON"));
        byte[] authenticatorData = WebAuthnCrypto.base64UrlDecode((String) response.get("authenticatorData"));
        byte[] signature = WebAuthnCrypto.base64UrlDecode((String) response.get("signature"));

        validateClientData(clientDataJSON, expectedChallenge, "webauthn.get");

        WebAuthnCrypto.AuthenticatorData parsed = WebAuthnCrypto.parseAuthenticatorData(authenticatorData);
        byte[] expectedRpIdHash = WebAuthnCrypto.sha256(rpId.getBytes());
        if (!Arrays.equals(expectedRpIdHash, parsed.rpIdHash)) {
            throw new IllegalStateException("Relying party mismatch");
        }
        if (!parsed.userPresent) {
            throw new IllegalStateException("User presence not confirmed");
        }

        byte[] signedData = new byte[authenticatorData.length + 32];
        System.arraycopy(authenticatorData, 0, signedData, 0, authenticatorData.length);
        System.arraycopy(WebAuthnCrypto.sha256(clientDataJSON), 0, signedData, authenticatorData.length, 32);

        PublicKey publicKey = WebAuthnCrypto.rawXYToPublicKey(stored.getPublicKeyCose());
        if (!WebAuthnCrypto.verifySignature(publicKey, signedData, signature)) {
            throw new IllegalStateException("Signature verification failed");
        }

        if (parsed.signCount != 0 && parsed.signCount <= stored.getSignCount()) {
            throw new IllegalStateException("Replay detected - sign count did not increase");
        }
        stored.setSignCount(parsed.signCount);
        credentialRepository.save(stored);

        return stored.getUser();
    }

    private void validateClientData(byte[] clientDataJSON, byte[] expectedChallenge, String expectedType) {
        if (expectedChallenge == null) {
            throw new IllegalStateException("Challenge expired or missing, please try again");
        }
        try {
            JsonNode node = objectMapper.readTree(clientDataJSON);
            String type = node.get("type").asText();
            String challenge = node.get("challenge").asText();
            String receivedOrigin = node.get("origin").asText();

            if (!expectedType.equals(type)) {
                throw new IllegalStateException("Unexpected WebAuthn ceremony type");
            }
            if (!Arrays.equals(WebAuthnCrypto.base64UrlDecode(challenge), expectedChallenge)) {
                throw new IllegalStateException("Challenge mismatch");
            }
            if (!origin.equals(receivedOrigin)) {
                throw new IllegalStateException("Origin mismatch - expected " + origin);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid client data", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
