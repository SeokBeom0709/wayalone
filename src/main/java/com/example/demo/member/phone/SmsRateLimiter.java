package com.example.demo.member.phone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    /** 이번 요청을 센 뒤 한도 안이면 true. */
    public boolean tryAcquire(String ip) {
        if (windows.size() > MAX_TRACKED) {
            windows.clear();
        }
        Instant now = Instant.now();
        Window updated = windows.compute(ip, (key, old) ->
                old == null || old.since().plus(WINDOW).isBefore(now)
                        ? new Window(1, now)
                        : new Window(old.count() + 1, old.since()));
        return updated.count() <= maxPerHour;
    }
}
