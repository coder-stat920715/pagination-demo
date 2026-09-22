package com.example.paginationdemo.dto;

import java.util.List;

/**
 * Window<T> itself isn't a great wire format (no stable JSON shape across versions),
 * so we flatten it into content + hasNext + an opaque, base64-encoded nextCursor
 * that the client echoes back on the next call.
 */
public record ScrollResponseDto<T>(
        List<T> content,
        boolean hasNext,
        String nextCursor
) {
}
