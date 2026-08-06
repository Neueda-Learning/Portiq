package com.portiq.security.webauthn;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * Crypto helpers for a minimal WebAuthn implementation supporting the ES256 (P-256) algorithm,
 * which covers Windows Hello, Touch ID, and Android biometric platform authenticators.
 */
public final class WebAuthnCrypto {

    private WebAuthnCrypto() {}

    public static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    public static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static AuthenticatorData parseAuthenticatorData(byte[] authData) {
        int pos = 0;
        byte[] rpIdHash = Arrays.copyOfRange(authData, 0, 32);
        pos += 32;

        int flags = authData[pos] & 0xFF;
        pos += 1;

        long signCount = ((authData[pos] & 0xFFL) << 24) | ((authData[pos + 1] & 0xFFL) << 16)
                | ((authData[pos + 2] & 0xFFL) << 8) | (authData[pos + 3] & 0xFFL);
        pos += 4;

        AuthenticatorData result = new AuthenticatorData();
        result.rpIdHash = rpIdHash;
        result.flags = flags;
        result.signCount = signCount;
        result.userPresent = (flags & 0x01) != 0;
        result.userVerified = (flags & 0x04) != 0;
        boolean attestedCredentialDataIncluded = (flags & 0x40) != 0;

        if (attestedCredentialDataIncluded) {
            pos += 16; // aaguid, not used
            int credentialIdLength = ((authData[pos] & 0xFF) << 8) | (authData[pos + 1] & 0xFF);
            pos += 2;
            byte[] credentialId = Arrays.copyOfRange(authData, pos, pos + credentialIdLength);
            pos += credentialIdLength;

            CborReader reader = new CborReader(authData, pos);
            @SuppressWarnings("unchecked")
            Map<Object, Object> coseKey = (Map<Object, Object>) reader.readValue();

            result.credentialId = credentialId;
            result.coseKey = coseKey;
        }

        return result;
    }

    public static byte[] coseKeyToRawXY(Map<Object, Object> coseKey) {
        byte[] x = (byte[]) coseKey.get(-2L);
        byte[] y = (byte[]) coseKey.get(-3L);
        if (x == null || y == null) {
            throw new IllegalStateException("Unsupported credential public key - only ES256 (P-256) is supported");
        }
        byte[] out = new byte[x.length + y.length];
        System.arraycopy(x, 0, out, 0, x.length);
        System.arraycopy(y, 0, out, x.length, y.length);
        return out;
    }

    public static PublicKey rawXYToPublicKey(byte[] xy) {
        try {
            byte[] x = Arrays.copyOfRange(xy, 0, 32);
            byte[] y = Arrays.copyOfRange(xy, 32, 64);

            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec ecParameterSpec = params.getParameterSpec(ECParameterSpec.class);

            ECPoint point = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
            ECPublicKeySpec pubSpec = new ECPublicKeySpec(point, ecParameterSpec);
            return KeyFactory.getInstance("EC").generatePublic(pubSpec);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to rebuild credential public key", e);
        }
    }

    public static boolean verifySignature(PublicKey publicKey, byte[] signedData, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(signedData);
            return verifier.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    public static class AuthenticatorData {
        public byte[] rpIdHash;
        public int flags;
        public long signCount;
        public boolean userPresent;
        public boolean userVerified;
        public byte[] credentialId;
        public Map<Object, Object> coseKey;
    }
}
