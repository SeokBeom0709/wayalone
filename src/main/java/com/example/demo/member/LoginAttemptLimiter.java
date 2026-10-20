package com.example.demo.member;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 같은 이메일로 로그인에 연속 실패하면 잠시 막습니다. (서버 1대 기준, 메모리 저장) */
@Component
public class LoginAttemptLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final int MAX_TRACKED = 10_000;

    private record Attempt(int count, Instant since) {
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public void check(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return;
        }
        if (attempt.since().plus(WINDOW).isBefore(Instant.now())) {
            attempts.remove(key);
            return;
        }
        if (attempt.count() >= MAX_FAILURES) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "로그인 시도가 너무 많아요. 10분 뒤에 다시 시도해 주세요.");
        }
    }

    public void fail(String key) {
        if (attempts.size() > MAX_TRACKED) {
            attempts.clear();
        }
        attempts.merge(key, new Attempt(1, Instant.now()),
                (old, ignored) -> new Attempt(old.count() + 1, old.since()));
    }

    public void reset(String key) {
        attempts.remove(key);
    }
}
