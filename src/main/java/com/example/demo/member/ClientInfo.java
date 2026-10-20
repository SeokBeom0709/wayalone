package com.example.demo.member;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;

/** 요청한 쪽의 IP 와 브라우저 정보. 로그인 기록에 남깁니다. */
public record ClientInfo(String ip, String userAgent) {

    private static final int MAX_UA = 200;

    public static ClientInfo from(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        // 직접 붙은 곳이 같은 서버/사설망의 프록시(로드밸런서, 터널)일 때만 전달된 IP 를 믿습니다.
        // 바깥에서 X-Forwarded-For 를 위조해 기록을 속이는 것을 막기 위해서예요.
        if (forwarded != null && !forwarded.isBlank() && isInternal(ip)) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty() && first.length() <= 45) {
                ip = first;
            }
        }
        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.length() > MAX_UA) {
            ua = ua.substring(0, MAX_UA);
        }
        return new ClientInfo(ip, ua);
    }

    static boolean isInternal(String ip) {
        if (ip == null) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
