package com.example.demo.member.phone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 같은 IP 에서 인증번호를 너무 많이 요청하면 막습니다. 문자 요금 폭탄(SMS pumping) 방지용. (서버 1대 기준, 메모리 저장) */
@Component
public class SmsRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);
    private static final int MAX_TRACKED = 10_000;

    private record Window(int count, Instant since) {
    }

    private final int maxPerHour;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public SmsRateLimiter(@Value("${app.sms.ip-hourly-limit:30}") int maxPerHour) {
        this.maxPerHour = maxPerHour;
    }

    public void acquire(String ip) {
        if (windows.size() > MAX_TRACKED) {
            windows.clear();
        }
        Instant now = Instant.now();
        Window updated = windows.compute(ip, (key, old) ->
                old == null || old.since().plus(WINDOW).isBefore(now)
                        ? new Window(1, now)
                        : new Window(old.count() + 1, old.since()));
        if (updated.count() > maxPerHour) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "인증번호 요청이 너무 많아요. 잠시 후 다시 시도해 주세요.");
        }
    }
}
