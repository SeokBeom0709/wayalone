package com.example.demo.member.phone;

import java.time.Duration;

/**
 * 인증번호와 1회용 토큰의 저장소. 유효시간과 "한 번만 성공" 규칙을 저장소가 원자적으로 지킵니다.
 * 구현: DB(기본) / Redis (app.verification.store=redis)
 */
public interface VerificationStore {

    enum IssueResult { OK, COOLDOWN, DAILY_LIMIT }

    enum VerifyStatus { OK, NOT_FOUND, EXPIRED, ALREADY_USED, MISMATCH, LOCKED }

    /** remaining 은 MISMATCH 일 때 남은 시도 횟수입니다. */
    record VerifyResult(VerifyStatus status, int remaining) {
        static VerifyResult of(VerifyStatus status) {
            return new VerifyResult(status, 0);
        }
    }

    /** 같은 IP 의 시간당 요청 횟수를 세고, 한도 안이면 true. */
    boolean allowIp(String ip);

    /** 재발송 간격과 하루 한도를 확인하고 통과하면 새 인증번호(해시)를 저장합니다. 이전 인증번호는 무효가 됩니다. */
    IssueResult issue(String phoneHash, String codeHash, Duration codeTtl, Duration cooldown, int dailyLimit);

    /**
     * 인증번호를 확인합니다. 맞으면 인증번호를 소비하고 같은 순간에 토큰(해시)을 저장합니다.
     * 여러 요청이 동시에 와도 OK 는 한 번만, 틀린 횟수는 maxAttempts 를 넘겨 인정되지 않습니다.
     */
    VerifyResult verify(String phoneHash, String codeHash, int maxAttempts, String tokenHash, Duration tokenTtl);

    /**
     * 토큰을 한 번만 쓰도록 소비합니다. 번호가 다르거나 만료/이미 사용이면 false.
     * 호출한 쪽 트랜잭션이 롤백되면 소비도 함께 되돌려집니다.
     */
    boolean consumeToken(String tokenHash, String phoneHash);
}
