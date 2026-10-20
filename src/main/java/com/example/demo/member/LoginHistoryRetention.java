package com.example.demo.member;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** 보관 기간이 지난 로그인 기록을 매일 새벽에 지웁니다. */
@Component
public class LoginHistoryRetention {

    private static final Logger log = LoggerFactory.getLogger(LoginHistoryRetention.class);

    private final LoginHistoryRepository repository;
    private final int retentionDays;

    public LoginHistoryRetention(LoginHistoryRepository repository,
                                 @Value("${app.log.login-retention-days:365}") int retentionDays) {
        this.repository = repository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void purge() {
        int deleted = repository.deleteOlderThan(Instant.now().minus(Duration.ofDays(retentionDays)));
        if (deleted > 0) {
            log.info("보관 기간({}일)이 지난 로그인 기록 {}건을 삭제했어요.", retentionDays, deleted);
        }
    }
}
