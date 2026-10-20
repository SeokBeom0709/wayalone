package com.example.demo;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 테스트에서 휴대폰 인증 → 회원가입 후 토큰을 꺼내 쓰기 위한 도우미 */
final class TestAuth {

    static final String PASSWORD = "Passw0rdA1";
    private static final Pattern TOKEN = Pattern.compile("\"token\":\"([^\"]+)\"");
    private static final Pattern DEV_CODE = Pattern.compile("\"devCode\":\"(\\d{6})\"");
    private static final Pattern VERIFICATION_TOKEN = Pattern.compile("\"verificationToken\":\"([^\"]+)\"");
    private static final AtomicInteger PHONE_SEQ = new AtomicInteger(ThreadLocalRandom.current().nextInt(10_000_000));

    private TestAuth() {
    }

    static String nextPhone() {
        return "010" + "%08d".formatted(PHONE_SEQ.incrementAndGet() % 100_000_000);
    }

    static String sendCode(RestClient client, String phone) {
        String body = client.post()
                .uri("/api/auth/phone/send")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"phone":"%s"}
                        """.formatted(phone))
                .retrieve()
                .body(String.class);
        return group(DEV_CODE, body);
    }

    static String verifyCode(RestClient client, String phone, String code) {
        String body = client.post()
                .uri("/api/auth/phone/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"phone":"%s","code":"%s"}
                        """.formatted(phone, code))
                .retrieve()
                .body(String.class);
        return group(VERIFICATION_TOKEN, body);
    }

    static String verifiedPhoneToken(RestClient client, String phone) {
        return verifyCode(client, phone, sendCode(client, phone));
    }

    static String signupBody(RestClient client, String email, String nickname, String phone, String phoneToken) {
        return client.post()
                .uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"email":"%s","nickname":"%s","password":"%s","phone":"%s","phoneVerificationToken":"%s"}
                        """.formatted(email, nickname, PASSWORD, phone, phoneToken))
                .retrieve()
                .body(String.class);
    }

    static String signup(RestClient client, String email, String nickname) {
        String phone = nextPhone();
        return extractToken(signupBody(client, email, nickname, phone, verifiedPhoneToken(client, phone)));
    }

    static String login(RestClient client, String email, String password) {
        String body = client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password))
                .retrieve()
                .body(String.class);
        return extractToken(body);
    }

    static String extractToken(String body) {
        return group(TOKEN, body);
    }

    static String group(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        assertThat(matcher.find()).as("응답에서 %s 를 찾지 못했습니다: %s", pattern, body).isTrue();
        return matcher.group(1);
    }
}
