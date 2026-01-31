package com.pyrem.leetcodebot.discord;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

/**
 * Verifies Discord interaction request signatures using Ed25519.
 * Discord requires all interaction endpoint requests to be verified.
 */
@Slf4j
public class DiscordSignatureVerifier {

    private static final String DISCORD_PUBLIC_KEY_ENV = "DISCORD_PUBLIC_KEY";
    private final String publicKeyHex;

    public DiscordSignatureVerifier() {
        this.publicKeyHex = System.getenv(DISCORD_PUBLIC_KEY_ENV);
        if (publicKeyHex == null || publicKeyHex.isBlank()) {
            log.warn("DISCORD_PUBLIC_KEY environment variable not set");
        }
    }

    /**
     * Verify the Discord request signature.
     *
     * @param signature  The X-Signature-Ed25519 header value
     * @param timestamp  The X-Signature-Timestamp header value
     * @param body       The raw request body
     * @return true if the signature is valid, false otherwise
     */
    public boolean verify(String signature, String timestamp, String body) {
        if (publicKeyHex == null || publicKeyHex.isBlank()) {
            log.error("Cannot verify signature: DISCORD_PUBLIC_KEY not configured");
            return false;
        }

        try {
            // The message to verify is timestamp + body
            String message = timestamp + body;
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

            // Decode the signature from hex
            byte[] signatureBytes = HexFormat.of().parseHex(signature);

            // Decode the public key from hex
            byte[] publicKeyBytes = HexFormat.of().parseHex(publicKeyHex);

            // Ed25519 public keys need to be in X.509 format for Java
            // The raw Ed25519 key is 32 bytes, we need to wrap it
            byte[] x509Key = wrapEd25519PublicKey(publicKeyBytes);

            KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(x509Key));

            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(messageBytes);

            return sig.verify(signatureBytes);

        } catch (Exception e) {
            log.error("Error verifying Discord signature: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Wrap a raw Ed25519 public key (32 bytes) in X.509 SubjectPublicKeyInfo format.
     * The X.509 format adds a header that identifies the algorithm.
     */
    private byte[] wrapEd25519PublicKey(byte[] rawKey) {
        // Ed25519 X.509 header (for a 32-byte key)
        byte[] header = new byte[] {
            0x30, 0x2a,             // SEQUENCE, 42 bytes
            0x30, 0x05,             // SEQUENCE, 5 bytes
            0x06, 0x03,             // OID, 3 bytes
            0x2b, 0x65, 0x70,       // 1.3.101.112 (Ed25519)
            0x03, 0x21, 0x00        // BIT STRING, 33 bytes, 0 unused bits
        };

        byte[] result = new byte[header.length + rawKey.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(rawKey, 0, result, header.length, rawKey.length);

        return result;
    }
}
