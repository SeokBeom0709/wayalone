package com.example.demo.member;

public final class TokenHeader {

    private TokenHeader() {
    }

    /** "Authorization: Bearer xxx" 헤더에서 토큰만 꺼냅니다. */
    public static String extract(String header) {
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
