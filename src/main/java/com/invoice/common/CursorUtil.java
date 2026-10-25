package com.invoice.common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes and decodes opaque pagination cursors.
 * <p>
 * A cursor is a pipe-delimited string (e.g. "2026-10-24T10:00:00Z|550e8400-...")
 * Base64URL-encoded so it is safe to pass as a query parameter.
 */
public final class CursorUtil {

    private CursorUtil() {}

    public static String encode(String... parts) {
        String joined = String.join("|", parts);
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(joined.getBytes(StandardCharsets.UTF_8));
    }

    public static String[] decode(String cursor) {
        String joined = new String(
            Base64.getUrlDecoder().decode(cursor),
            StandardCharsets.UTF_8
        );
        return joined.split("\\|", -1);
    }
}
