package com.example.paginationdemo.util;

import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encodes a KeysetScrollPosition's key values (created_at, id) into an opaque,
 * URL-safe base64 string the client can pass back as ?cursor=... on the next
 * request, and decodes it back into a ScrollPosition Spring Data can consume.
 *
 * Keeping the cursor opaque (rather than exposing raw created_at/id query params)
 * means the client never needs to know which columns the keyset is built on --
 * that's an implementation detail the server is free to change.
 */
public final class CursorCodec {

    private static final String DELIMITER = "|";

    private CursorCodec() {
    }

    public static String encode(KeysetScrollPosition position) {
        Map<String, Object> keys = position.getKeys();
        String raw = keys.get("createdAt") + DELIMITER + keys.get("id");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static ScrollPosition decode(String cursor) {
        String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        String[] parts = raw.split("\\" + DELIMITER);

        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("createdAt", Instant.parse(parts[0]));
        keys.put("id", Long.parseLong(parts[1]));

        // "forward" == scrolling to newer/next rows in ascending sort order.
        return ScrollPosition.forward(keys);
    }
}
