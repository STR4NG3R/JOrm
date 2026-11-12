package io.github.str4ng3r.sql;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Encodes the last-seen order-by values as an opaque, URL-safe token and back.
 * Each value is a one-letter type tag plus its text, Base64url-encoded; values
 * are joined with '.', which Base64url never emits. No external dependencies.
 * <p>
 * The token is NOT signed: values are always bound as SQL parameters (no
 * injection), so a tampered token can only change which rows are returned.
 */
final class CursorCodec {

    private CursorCodec() {
    }

    static String encode(List<Object> values) {
        StringBuilder sb = new StringBuilder();
        for (Object v : values) {
            if (sb.length() > 0)
                sb.append('.');
            String raw = tag(v) + text(v);
            sb.append(Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        }
        return sb.toString();
    }

    /** @return decoded values, or null for a null/empty cursor (first page) */
    static List<Object> decode(String cursor) {
        if (cursor == null || cursor.isEmpty())
            return null;
        try {
            List<Object> values = new ArrayList<>();
            for (String part : cursor.split("\\.", -1)) {
                String raw = new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
                values.add(parse(raw.charAt(0), raw.substring(1)));
            }
            return values;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid cursor", e);
        }
    }

    private static char tag(Object v) {
        if (v == null)
            throw new IllegalStateException("Cursor order columns must be NOT NULL, found a null value");
        if (v instanceof Integer) return 'i';
        if (v instanceof Long) return 'l';
        if (v instanceof Short) return 's';
        if (v instanceof BigDecimal) return 'n';
        if (v instanceof BigInteger) return 'g';
        if (v instanceof Double) return 'd';
        if (v instanceof Float) return 'f';
        if (v instanceof String) return 'S';
        if (v instanceof Boolean) return 'b';
        if (v instanceof UUID) return 'u';
        if (v instanceof Timestamp) return 't';
        if (v instanceof java.sql.Date) return 'D';
        if (v instanceof java.util.Date) return 't';
        throw new IllegalArgumentException("Unsupported cursor value type: " + v.getClass().getName());
    }

    private static String text(Object v) {
        if (v instanceof Timestamp) return ((Timestamp) v).toInstant().toString();
        if (v instanceof java.sql.Date) return v.toString();
        if (v instanceof java.util.Date) return Instant.ofEpochMilli(((java.util.Date) v).getTime()).toString();
        return v.toString();
    }

    private static Object parse(char tag, String s) {
        switch (tag) {
            case 'i': return Integer.valueOf(s);
            case 'l': return Long.valueOf(s);
            case 's': return Short.valueOf(s);
            case 'n': return new BigDecimal(s);
            case 'g': return new BigInteger(s);
            case 'd': return Double.valueOf(s);
            case 'f': return Float.valueOf(s);
            case 'S': return s;
            case 'b': return Boolean.valueOf(s);
            case 'u': return UUID.fromString(s);
            case 't': return Timestamp.from(Instant.parse(s));
            case 'D': return java.sql.Date.valueOf(s);
            default: throw new IllegalArgumentException("Unknown tag " + tag);
        }
    }
}
