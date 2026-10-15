package dev.shinobi;

import java.time.Instant;

/** One structured admin audit record, serialised as a single JSON line. Pure. */
public record AuditEntry(Instant time, String adminName, String adminUuid, String targetName, String targetUuid,
                         String action, String previous, String now, boolean forced, boolean success, String reason) {

    public String toJson() {
        return "{\"time\":" + q(time.toString())
                + ",\"admin\":" + q(adminName) + ",\"adminUuid\":" + q(adminUuid)
                + ",\"target\":" + q(targetName) + ",\"targetUuid\":" + q(targetUuid)
                + ",\"action\":" + q(action) + ",\"previous\":" + q(previous) + ",\"new\":" + q(now)
                + ",\"forced\":" + forced + ",\"success\":" + success + ",\"reason\":" + q(reason) + "}";
    }

    /** JSON string literal with full escaping (quotes, backslashes, control characters). null becomes JSON null. */
    static String q(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
}
