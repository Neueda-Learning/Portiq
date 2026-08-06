package com.portiq.security.webauthn;

import com.portiq.model.User;
import com.portiq.model.WebauthnCredential;
import com.portiq.repository.WebauthnCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Exercises the WebAuthn crypto pipeline (CBOR parsing, COSE key extraction, ECDSA signature
 * verification, challenge/replay checks) end to end with a synthetic authenticator built from a
 * real EC key pair. This is the strongest verification available without an actual browser and
 * platform authenticator (Windows Hello, Touch ID, etc.) - it proves the server-side logic is
 * cryptographically correct; only the real browser/OS ceremony is untested here.
 */
@ExtendWith(MockitoExtension.class)
class WebAuthnServiceTest {

    @Mock
    private WebauthnCredentialRepository credentialRepository;

    private WebAuthnService webAuthnService;
    private User user;

    @BeforeEach
    void setUp() {
        WebAuthnChallengeStore challengeStore = new WebAuthnChallengeStore();
        webAuthnService = new WebAuthnService(challengeStore, credentialRepository);
        ReflectionTestUtils.setField(webAuthnService, "rpId", "localhost");
        ReflectionTestUtils.setField(webAuthnService, "rpName", "Portiq");
        ReflectionTestUtils.setField(webAuthnService, "origin", "http://localhost:5173");

        user = new User("owner", "hash");
        user.setId(1L);
    }

    @Test
    void registrationAndLogin_roundTripSucceeds() throws Exception {
        when(credentialRepository.findByUserId(1L)).thenReturn(List.of());
        when(credentialRepository.save(any(WebauthnCredential.class))).thenAnswer(inv -> inv.getArgument(0));

        KeyPair keyPair = generateEcKeyPair();
        byte[] credentialId = randomBytes(32);

        // --- Registration ---
        Map<String, Object> regOptions = webAuthnService.createRegistrationOptions(user);
        String regChallenge = (String) regOptions.get("challenge");

        Map<String, Object> registrationCredential = buildRegistrationCredential(
                credentialId, (ECPublicKey) keyPair.getPublic(), regChallenge);

        WebauthnCredential saved = webAuthnService.verifyRegistration(user, registrationCredential, "Test device");
        assertThat(saved.getCredentialId()).isEqualTo(WebAuthnCrypto.base64UrlEncode(credentialId));
        assertThat(saved.getSignCount()).isZero();

        // --- Login ---
        when(credentialRepository.findByUserId(1L)).thenReturn(List.of(saved));
        when(credentialRepository.findByCredentialId(WebAuthnCrypto.base64UrlEncode(credentialId)))
                .thenReturn(Optional.of(saved));

        Map<String, Object> loginOptions = webAuthnService.createLoginOptions(user);
        String loginChallenge = (String) loginOptions.get("challenge");

        Map<String, Object> loginCredential = buildLoginCredential(
                credentialId, keyPair, loginChallenge, 1L);

        User result = webAuthnService.verifyLogin(loginCredential);
        assertThat(result.getUsername()).isEqualTo("owner");
        assertThat(saved.getSignCount()).isEqualTo(1L);
    }

    @Test
    void verifyLogin_rejectsForgedSignature() throws Exception {
        when(credentialRepository.findByUserId(1L)).thenReturn(List.of());
        when(credentialRepository.save(any(WebauthnCredential.class))).thenAnswer(inv -> inv.getArgument(0));

        KeyPair legitimateKeyPair = generateEcKeyPair();
        KeyPair attackerKeyPair = generateEcKeyPair();
        byte[] credentialId = randomBytes(32);

        Map<String, Object> regOptions = webAuthnService.createRegistrationOptions(user);
        WebauthnCredential saved = webAuthnService.verifyRegistration(
                user,
                buildRegistrationCredential(credentialId, (ECPublicKey) legitimateKeyPair.getPublic(),
                        (String) regOptions.get("challenge")),
                "Test device");

        when(credentialRepository.findByUserId(1L)).thenReturn(List.of(saved));
        when(credentialRepository.findByCredentialId(WebAuthnCrypto.base64UrlEncode(credentialId)))
                .thenReturn(Optional.of(saved));

        Map<String, Object> loginOptions = webAuthnService.createLoginOptions(user);
        // Sign with the attacker's private key instead of the one whose public key was registered.
        Map<String, Object> forgedCredential = buildLoginCredential(
                credentialId, attackerKeyPair, (String) loginOptions.get("challenge"), 1L);

        assertThatThrownBy(() -> webAuthnService.verifyLogin(forgedCredential))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Signature verification failed");
    }

    // --- Synthetic authenticator helpers ---

    private KeyPair generateEcKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private Map<String, Object> buildRegistrationCredential(byte[] credentialId, ECPublicKey publicKey, String challenge)
            throws Exception {
        String clientDataJson = clientDataJson("webauthn.create", challenge);
        byte[] attestationObject = buildAttestationObject(credentialId, publicKey);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("clientDataJSON", WebAuthnCrypto.base64UrlEncode(clientDataJson.getBytes(StandardCharsets.UTF_8)));
        response.put("attestationObject", WebAuthnCrypto.base64UrlEncode(attestationObject));

        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("id", WebAuthnCrypto.base64UrlEncode(credentialId));
        credential.put("response", response);
        return credential;
    }

    private Map<String, Object> buildLoginCredential(byte[] credentialId, KeyPair keyPair, String challenge, long signCount)
            throws Exception {
        String clientDataJson = clientDataJson("webauthn.get", challenge);
        byte[] authenticatorData = buildAssertionAuthenticatorData(signCount);

        byte[] signedData = concat(authenticatorData, sha256(clientDataJson.getBytes(StandardCharsets.UTF_8)));
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(signedData);
        byte[] signature = signer.sign();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("clientDataJSON", WebAuthnCrypto.base64UrlEncode(clientDataJson.getBytes(StandardCharsets.UTF_8)));
        response.put("authenticatorData", WebAuthnCrypto.base64UrlEncode(authenticatorData));
        response.put("signature", WebAuthnCrypto.base64UrlEncode(signature));

        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("id", WebAuthnCrypto.base64UrlEncode(credentialId));
        credential.put("response", response);
        return credential;
    }

    private String clientDataJson(String type, String challengeB64) {
        return "{\"type\":\"" + type + "\",\"challenge\":\"" + challengeB64 + "\",\"origin\":\"http://localhost:5173\"}";
    }

    private byte[] sha256(byte[] input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private byte[] rpIdHash() throws Exception {
        return sha256("localhost".getBytes(StandardCharsets.UTF_8));
    }

    /** UP flag only, no attested credential data - matches a real assertion's authenticatorData. */
    private byte[] buildAssertionAuthenticatorData(long signCount) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(rpIdHash());
        out.write(0x01);
        out.write((int) ((signCount >> 24) & 0xFF));
        out.write((int) ((signCount >> 16) & 0xFF));
        out.write((int) ((signCount >> 8) & 0xFF));
        out.write((int) (signCount & 0xFF));
        return out.toByteArray();
    }

    /** attestationObject = CBOR {"fmt":"none","attStmt":{},"authData":<UP+AT authData with COSE key>}. */
    private byte[] buildAttestationObject(byte[] credentialId, ECPublicKey publicKey) throws Exception {
        ByteArrayOutputStream authData = new ByteArrayOutputStream();
        authData.write(rpIdHash());
        authData.write(0x41); // UP | AT
        authData.write(new byte[]{0, 0, 0, 0}); // signCount = 0 at registration
        authData.write(new byte[16]); // aaguid, zeroed
        authData.write((credentialId.length >> 8) & 0xFF);
        authData.write(credentialId.length & 0xFF);
        authData.write(credentialId);
        authData.write(encodeCoseKey(publicKey));

        ByteArrayOutputStream cbor = new ByteArrayOutputStream();
        cbor.write(0xA3); // map, 3 pairs
        writeCborTextString(cbor, "fmt");
        writeCborTextString(cbor, "none");
        writeCborTextString(cbor, "attStmt");
        cbor.write(0xA0); // empty map
        writeCborTextString(cbor, "authData");
        writeCborByteString(cbor, authData.toByteArray());
        return cbor.toByteArray();
    }

    private byte[] encodeCoseKey(ECPublicKey publicKey) {
        byte[] x = toFixedLength(publicKey.getW().getAffineX(), 32);
        byte[] y = toFixedLength(publicKey.getW().getAffineY(), 32);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xA5); // map, 5 pairs: kty, alg, crv, x, y
        writeCborUnsigned(out, 1);
        writeCborUnsigned(out, 2); // kty = EC2
        writeCborUnsigned(out, 3);
        writeCborNegative(out, -7); // alg = ES256
        writeCborNegative(out, -1);
        writeCborUnsigned(out, 1); // crv = P-256
        writeCborNegative(out, -2);
        writeCborByteString(out, x);
        writeCborNegative(out, -3);
        writeCborByteString(out, y);
        return out.toByteArray();
    }

    private byte[] toFixedLength(BigInteger value, int length) {
        byte[] raw = value.toByteArray();
        byte[] fixed = new byte[length];
        int copyLen = Math.min(raw.length, length);
        System.arraycopy(raw, raw.length - copyLen, fixed, length - copyLen, copyLen);
        return fixed;
    }

    private void writeCborTextString(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.write(0x60 | bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    private void writeCborByteString(ByteArrayOutputStream out, byte[] value) {
        if (value.length < 24) {
            out.write(0x40 | value.length);
        } else {
            out.write(0x58);
            out.write(value.length);
        }
        out.write(value, 0, value.length);
    }

    private void writeCborUnsigned(ByteArrayOutputStream out, int value) {
        out.write(value);
    }

    private void writeCborNegative(ByteArrayOutputStream out, int value) {
        int n = -1 - value;
        out.write(0x20 | n);
    }
}
