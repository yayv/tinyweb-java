package demo;

import java.security.MessageDigest;
import java.util.*;

public class TokenUtils {
    private static final String SECRET = "tinyweb-demo-secret-key-change-in-production";
    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 24 hours in milliseconds
    private static final long CLOCK_SKEW = 30 * 1000; // 30 seconds

    public static String generateToken(String username) {
        long now = System.currentTimeMillis();
        long exp = now + EXPIRATION_TIME;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", username);
        payload.put("iat", now / 1000); // issued at (seconds)
        payload.put("exp", exp / 1000); // expiration (seconds)

        String header = encodeBase64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payloadStr = encodeBase64Url(mapToJson(payload));

        String signature = generateSignature(header + "." + payloadStr);

        return header + "." + payloadStr + "." + signature;
    }

    public static String validateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }

        try {
            // Verify signature
            String signature = generateSignature(parts[0] + "." + parts[1]);
            if (!constantTimeEquals(signature, parts[2])) {
                return null;
            }

            // Decode and verify payload
            String payloadJson = decodeBase64Url(parts[1]);
            Map<String, Object> payload = parseJson(payloadJson);

            // Check expiration
            if (payload.containsKey("exp")) {
                long exp = ((Number) payload.get("exp")).longValue();
                long now = System.currentTimeMillis() / 1000;
                if (now > exp + (CLOCK_SKEW / 1000)) {
                    return null; // token expired
                }
            }

            return (String) payload.get("sub");
        } catch (Exception e) {
            return null;
        }
    }

    private static String generateSignature(String data) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey =
                new javax.crypto.spec.SecretKeySpec(SECRET.getBytes(), "HmacSHA256");
            mac.init(secretKey);
            byte[] signature = mac.doFinal(data.getBytes());
            return encodeBase64Url(signature);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        byte[] aBytes = a.getBytes();
        byte[] bBytes = b.getBytes();
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    private static String encodeBase64Url(String str) {
        return encodeBase64Url(str.getBytes());
    }

    private static String encodeBase64Url(byte[] bytes) {
        String encoded = Base64.getEncoder().encodeToString(bytes);
        return encoded.replace('+', '-').replace('/', '_').replace("=", "");
    }

    private static String decodeBase64Url(String str) {
        String padded = str;
        switch (str.length() % 4) {
            case 1:
                padded += "===";
                break;
            case 2:
                padded += "==";
                break;
            case 3:
                padded += "=";
                break;
        }
        padded = padded.replace('-', '+').replace('_', '/');
        byte[] decoded = Base64.getDecoder().decode(padded);
        return new String(decoded);
    }

    private static String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else {
                sb.append(value);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static Map<String, Object> parseJson(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
        }

        if (json.isBlank()) {
            return result;
        }

        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replaceAll("\"", "");
                String value = kv[1].trim();
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    result.put(key, value.substring(1, value.length() - 1));
                } else if ("null".equals(value)) {
                    result.put(key, null);
                } else {
                    try {
                        if (value.contains(".")) {
                            result.put(key, Double.parseDouble(value));
                        } else {
                            result.put(key, Long.parseLong(value));
                        }
                    } catch (NumberFormatException e) {
                        result.put(key, value);
                    }
                }
            }
        }
        return result;
    }
}
