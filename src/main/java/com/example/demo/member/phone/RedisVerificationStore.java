package com.example.demo.member.phone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 에 저장하는 구현 (app.verification.store=redis).
 * 유효시간은 키의 TTL 이 관리하므로 만료된 기록을 지우는 작업이 필요 없습니다.
 * 여러 키를 함께 읽고 쓰는 동작은 Lua 스크립트 하나로 실행해서(Redis 는 스크립트를 한 번에 하나씩 처리) 동시 요청에서도 중간 상태가 보이지 않습니다.
 *
 * 키 (phoneHash 는 HMAC 해시이므로 전화번호 원문은 Redis 에 저장되지 않습니다)
 *   wayalone:v:code:{phoneHash}      인증번호 해시, TTL = 인증번호 유효시간
 *   wayalone:v:attempts:{phoneHash}  틀린 횟수, TTL = 인증번호와 같음
 *   wayalone:v:cooldown:{phoneHash}  재발송 간격
 *   wayalone:v:daily:{phoneHash}     하루 발송 횟수
 *   wayalone:v:token:{tokenHash}     값 = phoneHash, TTL = 토큰 유효시간
 *   wayalone:v:ip:{ip}               시간당 요청 횟수
 */
@Component
@ConditionalOnProperty(name = "app.verification.store", havingValue = "redis")
public class RedisVerificationStore implements VerificationStore {

    private static final String PREFIX = "wayalone:v:";

    /** KEYS: cooldown, daily, code, attempts / ARGV: cooldownSec, dailyLimit, dailyTtlSec, codeHash, codeTtlSec → 0 OK, 1 COOLDOWN, 2 DAILY_LIMIT */
    private static final DefaultRedisScript<Long> ISSUE = script("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return 1 end
            local daily = tonumber(redis.call('GET', KEYS[2]) or '0')
            if daily >= tonumber(ARGV[2]) then return 2 end
            if redis.call('INCR', KEYS[2]) == 1 then redis.call('EXPIRE', KEYS[2], ARGV[3]) end
            redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
            redis.call('SET', KEYS[3], ARGV[4], 'EX', ARGV[5])
            redis.call('DEL', KEYS[4])
            return 0
            """, Long.class);

    /**
     * KEYS: code, attempts, token / ARGV: codeHash, maxAttempts, tokenTtlSec, phoneHash
     * → {0 NOT_FOUND | 1 OK | 2 MISMATCH(남은 횟수) | 3 LOCKED, remaining}
     */
    private static final DefaultRedisScript<List> VERIFY = script("""
            local stored = redis.call('GET', KEYS[1])
            if not stored then return {0, 0} end
            local max = tonumber(ARGV[2])
            if tonumber(redis.call('GET', KEYS[2]) or '0') >= max then return {3, 0} end
            if stored == ARGV[1] then
              redis.call('DEL', KEYS[1], KEYS[2])
              redis.call('SET', KEYS[3], ARGV[4], 'EX', ARGV[3])
              return {1, 0}
            end
            local attempts = redis.call('INCR', KEYS[2])
            if attempts == 1 then
              local ttl = redis.call('TTL', KEYS[1])
              if ttl > 0 then redis.call('EXPIRE', KEYS[2], ttl) end
            end
            return {2, math.max(max - attempts, 0)}
            """, List.class);

    /** KEYS: token / ARGV: phoneHash → {1, 남은 TTL(ms)} 성공, {0, 0} 실패. 읽기와 삭제가 한 번에 일어납니다. */
    private static final DefaultRedisScript<List> CONSUME = script("""
            local value = redis.call('GET', KEYS[1])
            if value ~= ARGV[1] then return {0, 0} end
            local ttl = redis.call('PTTL', KEYS[1])
            redis.call('DEL', KEYS[1])
            return {1, ttl}
            """, List.class);

    /** KEYS: ip / ARGV: windowSec → 이번 요청까지의 횟수 */
    private static final DefaultRedisScript<Long> COUNT_IP = script("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return n
            """, Long.class);

    private static final long IP_WINDOW_SECONDS = Duration.ofHours(1).toSeconds();
    private static final long DAY_SECONDS = Duration.ofDays(1).toSeconds();

    private final StringRedisTemplate redis;
    private final int ipHourlyLimit;

    public RedisVerificationStore(StringRedisTemplate redis, @Value("${app.sms.ip-hourly-limit:30}") int ipHourlyLimit) {
        this.redis = redis;
        this.ipHourlyLimit = ipHourlyLimit;
    }

    private static <T> DefaultRedisScript<T> script(String lua, Class<T> type) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>(lua, type);
        return script;
    }

    @Override
    public boolean allowIp(String ip) {
        Long count = redis.execute(COUNT_IP, List.of(PREFIX + "ip:" + ip), String.valueOf(IP_WINDOW_SECONDS));
        return count != null && count <= ipHourlyLimit;
    }

    @Override
    public IssueResult issue(String phoneHash, String codeHash, Duration codeTtl, Duration cooldown, int dailyLimit) {
        Long result = redis.execute(ISSUE,
                List.of(PREFIX + "cooldown:" + phoneHash, PREFIX + "daily:" + phoneHash,
                        PREFIX + "code:" + phoneHash, PREFIX + "attempts:" + phoneHash),
                String.valueOf(cooldown.toSeconds()), String.valueOf(dailyLimit), String.valueOf(DAY_SECONDS),
                codeHash, String.valueOf(codeTtl.toSeconds()));
        return switch (result == null ? -1 : result.intValue()) {
            case 0 -> IssueResult.OK;
            case 1 -> IssueResult.COOLDOWN;
            case 2 -> IssueResult.DAILY_LIMIT;
            default -> throw new IllegalStateException("Redis 응답을 해석할 수 없어요: " + result);
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public VerifyResult verify(String phoneHash, String codeHash, int maxAttempts, String tokenHash, Duration tokenTtl) {
        List<Long> result = redis.execute(VERIFY,
                List.of(PREFIX + "code:" + phoneHash, PREFIX + "attempts:" + phoneHash, PREFIX + "token:" + tokenHash),
                codeHash, String.valueOf(maxAttempts), String.valueOf(tokenTtl.toSeconds()), phoneHash);
        if (result == null || result.size() < 2) {
            throw new IllegalStateException("Redis 응답을 해석할 수 없어요.");
        }
        return switch (result.get(0).intValue()) {
            case 0 -> VerifyResult.of(VerifyStatus.NOT_FOUND);
            case 1 -> VerifyResult.of(VerifyStatus.OK);
            case 2 -> new VerifyResult(VerifyStatus.MISMATCH, result.get(1).intValue());
            case 3 -> VerifyResult.of(VerifyStatus.LOCKED);
            default -> throw new IllegalStateException("알 수 없는 결과: " + result);
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean consumeToken(String tokenHash, String phoneHash) {
        String key = PREFIX + "token:" + tokenHash;
        List<Long> result = redis.execute(CONSUME, List.of(key), phoneHash);
        if (result == null || result.isEmpty() || result.get(0) != 1L) {
            return false;
        }
        restoreIfRolledBack(key, phoneHash, result.get(1));
        return true;
    }

    /**
     * Redis 는 DB 트랜잭션에 참여하지 않으므로, 토큰을 쓴 뒤 회원가입 트랜잭션이 롤백되면 직접 되돌립니다.
     * (DB 구현에서는 롤백이 UPDATE 를 되돌려서 같은 효과가 납니다.)
     */
    private void restoreIfRolledBack(String key, String phoneHash, Long remainingMillis) {
        if (!TransactionSynchronizationManager.isSynchronizationActive() || remainingMillis == null || remainingMillis <= 0) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    redis.opsForValue().set(key, phoneHash, remainingMillis, TimeUnit.MILLISECONDS);
                }
            }
        });
    }
}
