package com.example.demo.member.phone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** 개발용: 문자를 보내는 대신 서버 로그와 메모리 문자함(최근 30건)에 남깁니다. */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "log", matchIfMissing = true)
public class LogSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LogSmsSender.class);
    private static final int INBOX_SIZE = 30;

    public record DevMessage(String to, String text, Instant sentAt) {
    }

    private final Deque<DevMessage> inbox = new ArrayDeque<>();

    @Override
    public void send(String to, String text) {
        String masked = to.substring(0, 3) + "-****-" + to.substring(to.length() - 4);
        log.info("[SMS 개발 모드] to={} text={}", masked, text);
        synchronized (inbox) {
            inbox.addFirst(new DevMessage(masked, text, Instant.now()));
            while (inbox.size() > INBOX_SIZE) {
                inbox.removeLast();
            }
        }
    }

    @Override
    public boolean isFake() {
        return true;
    }

    public List<DevMessage> recent() {
        synchronized (inbox) {
            return new ArrayList<>(inbox);
        }
    }
}
