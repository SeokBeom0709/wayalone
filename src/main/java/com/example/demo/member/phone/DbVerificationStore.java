package com.example.demo.member.phone;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

/**
 * DB 에 저장하는 기본 구현. 서버를 여러 대로 늘리지 않는 환경에서는 이것으로 충분합니다.
 * 동시 요청에서 깨지기 쉬운 부분(틀린 횟수, 인증 성공, 토큰 사용)은 조건부 UPDATE 한 번으로 처리합니다.
 */
@Component
@ConditionalOnProperty(name = "app.verification.store", havingValue = "db", matchIfMissing = true)
public class DbVerificationStore implements VerificationStore {

    private static final Duration DAY = Duration.ofDays(1);

    private final PhoneVerificationRepository repository;
    private final SmsRateLimiter ipLimiter;

    public DbVerificationStore(PhoneVerificationRepository repository, SmsRateLimiter ipLimiter) {
        this.repository = repository;
        this.ipLimiter = ipLimiter;
    }

    @Override
    public boolean allowIp(String ip) {
        return ipLimiter.tryAcquire(ip);
    }

    @Override
    @Transactional
    public IssueResult issue(String phoneHash, String codeHash, Duration codeTtl, Duration cooldown, int dailyLimit) {
        Instant now = Instant.now();
        var last = repository.findTopByPhoneHashOrderByCreatedAtDesc(phoneHash);
        if (last.isPresent() && last.get().getCreatedAt().plus(cooldown).isAfter(now)) {
            return IssueResult.COOLDOWN;
        }
        if (repository.countByPhoneHashAndCreatedAtAfter(phoneHash, now.minus(DAY)) >= dailyLimit) {
            return IssueResult.DAILY_LIMIT;
        }
        repository.deleteOlderThan(now.minus(DAY.multipliedBy(2)));

        PhoneVerification verification = new PhoneVerification();
        verification.setPhoneHash(phoneHash);
        verification.setCodeHash(codeHash);
        verification.setExpiresAt(now.plus(codeTtl));
        verification.setCreatedAt(now);
        repository.save(verification);
        return IssueResult.OK;
    }

    @Override
    @Transactional
    public VerifyResult verify(String phoneHash, String codeHash, int maxAttempts, String tokenHash, Duration tokenTtl) {
        PhoneVerification verification = repository.findTopByPhoneHashOrderByCreatedAtDesc(phoneHash).orElse(null);
        if (verification == null) {
            return VerifyResult.of(VerifyStatus.NOT_FOUND);
        }
        Instant now = Instant.now();
        if (verification.getVerifiedAt() != null) {
            return VerifyResult.of(VerifyStatus.ALREADY_USED);
        }
        if (verification.getExpiresAt().isBefore(now)) {
            return VerifyResult.of(VerifyStatus.EXPIRED);
        }
        if (verification.getAttempts() >= maxAttempts) {
            return VerifyResult.of(VerifyStatus.LOCKED);
        }

        boolean matches = MessageDigest.isEqual(
                verification.getCodeHash().getBytes(StandardCharsets.UTF_8),
                codeHash.getBytes(StandardCharsets.UTF_8));
        if (matches) {
            int updated = repository.markVerified(verification.getId(), now, tokenHash, now.plus(tokenTtl));
            return VerifyResult.of(updated == 1 ? VerifyStatus.OK : VerifyStatus.ALREADY_USED);
        }
        if (repository.incrementAttempts(verification.getId(), maxAttempts) == 0) {
            return VerifyResult.of(VerifyStatus.LOCKED);
        }
        int remaining = maxAttempts - repository.attemptsOf(verification.getId());
        return new VerifyResult(VerifyStatus.MISMATCH, Math.max(remaining, 0));
    }

    @Override
    @Transactional
    public boolean consumeToken(String tokenHash, String phoneHash) {
        return repository.claimToken(tokenHash, phoneHash, Instant.now()) == 1;
    }
}
