package rathi.prakhar.ai.rca.spring.boot.core.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class ExceptionFingerprint {

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

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
