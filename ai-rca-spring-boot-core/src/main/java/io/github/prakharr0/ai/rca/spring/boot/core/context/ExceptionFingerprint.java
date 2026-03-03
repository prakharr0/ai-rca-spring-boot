package io.github.prakharr0.ai.rca.spring.boot.core.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Utility class responsible for generating deterministic fingerprints
 * for exception contexts.
 *
 * <p>
 * A fingerprint uniquely represents an exception occurrence based on
 * selected characteristics from a {@link ContextSnapshot}. It enables:
 * <ul>
 *     <li>AI response caching</li>
 *     <li>Duplicate failure detection</li>
 *     <li>Log aggregation and grouping</li>
 * </ul>
 *
 * <h2>Fingerprint Strategy</h2>
 * The fingerprint is generated using the SHA-256 hash of a concatenation of:
 * <ul>
 *     <li>{@code exceptionType}</li>
 *     <li>{@code rootCauseType}</li>
 *     <li>{@code stackTrace}</li>
 * </ul>
 *
 * <p>
 * This ensures:
 * <ul>
 *     <li>Deterministic output for identical failure contexts</li>
 *     <li>Low collision probability</li>
 *     <li>Stable grouping across application restarts</li>
 * </ul>
 *
 * <h2>Error Handling</h2>
 * If hashing fails for any reason, the method returns {@code "unknown"}
 * as a safe fallback.
 *
 * <h2>Thread Safety</h2>
 * This class is stateless and thread-safe.
 *
 * @see ContextSnapshot
 */
public class ExceptionFingerprint {

    /**
     * Generates a SHA-256 based fingerprint for the given {@link ContextSnapshot}.
     *
     * <p>
     * The fingerprint is derived from a concatenation of selected snapshot fields:
     * <pre>
     * exceptionType + rootCauseType + stackTrace
     * </pre>
     *
     * <p>
     * The resulting hash is encoded as a lowercase hexadecimal string.
     *
     * @param snapshot the contextual snapshot of the exception; must not be {@code null}
     * @return a deterministic hexadecimal fingerprint string,
     *         or {@code "unknown"} if hashing fails
     */
    public static String generate(ContextSnapshot snapshot) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            String raw = snapshot.exceptionType()
                    + snapshot.rootCauseType()
                    + snapshot.stackTrace();

            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));

            return bytesToHex(hash);
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Converts a byte array into a lowercase hexadecimal string.
     *
     * @param bytes the byte array to convert
     * @return hexadecimal representation of the byte array
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
