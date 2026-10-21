package com.example.demo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Redis 저장소로 같은 공통 규칙을 확인하고, TTL 과 발송 한도 같은 Redis 고유 동작도 확인합니다.
 * Redis 가 없으면 건너뜁니다. (docker compose up -d redis, 또는 REDIS_TEST_HOST / REDIS_TEST_PORT / REDIS_TEST_PASSWORD)
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:verifyredis;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.verification.store=redis",
        "app.sms.ip-hourly-limit=100000",
        "spring.data.redis.host=${REDIS_TEST_HOST:localhost}",
        "spring.data.redis.port=${REDIS_TEST_PORT:6379}",
        "spring.data.redis.password=${REDIS_TEST_PASSWORD:}"
})
class RedisVerificationStoreTests extends AbstractVerificationStoreTests {

    private static final String PREFIX = "wayalone:v:";

    @Autowired
    StringRedisTemplate redis;

    @BeforeAll
    static void redisIsReachable() {
        String host = System.getenv().getOrDefault("REDIS_TEST_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_PORT", "6379"));
        boolean reachable;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            reachable = true;
        } catch (Exception e) {
            reachable = false;
        }
        assumeTrue(reachable, "Redis(" + host + ":" + port + ")에 연결할 수 없어 건너뜁니다.");
    }

    /** PhoneVerificationService 와 같은 방식(HMAC-SHA256, 기본 pepper)으로 키에 쓰이는 해시를 계산합니다. */
    private static String phoneHash(String phone) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("dev-only-phone-pepper".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(phone.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void 인증번호와_토큰에는_유효시간이_걸리고_번호_원문은_저장되지_않는다() throws Exception {
        String phone = randomPhone();
        String hash = phoneHash(phone);
        String code = service.send(phone, randomIp()).devCode();

        Long codeTtl = redis.getExpire(PREFIX + "code:" + hash, TimeUnit.SECONDS);
        assertThat(codeTtl).as("인증번호 TTL(초)").isBetween(1L, 180L);
        assertThat(redis.getExpire(PREFIX + "cooldown:" + hash, TimeUnit.SECONDS)).isBetween(1L, 60L);
        assertThat(redis.keys(PREFIX + "*" + phone + "*")).as("전화번호 원문이 키에 없어야 해요").isEmpty();

        service.verify(phone, code);
        assertThat(redis.hasKey(PREFIX + "code:" + hash)).as("성공하면 인증번호는 바로 사라져요").isFalse();
        List<String> tokenKeys = redis.keys(PREFIX + "token:*").stream()
                .filter(key -> hash.equals(redis.opsForValue().get(key))).toList();
        assertThat(tokenKeys).hasSize(1);
        assertThat(redis.getExpire(tokenKeys.get(0), TimeUnit.SECONDS)).isBetween(1L, 1800L);
    }

    @Test
    void 유효시간이_지나면_인증번호가_만료된다() throws Exception {
        String phone = randomPhone();
        String code = service.send(phone, randomIp()).devCode();
        redis.expire(PREFIX + "code:" + phoneHash(phone), 1, TimeUnit.SECONDS);
        Thread.sleep(1_300);

        assertThatThrownBy(() -> service.verify(phone, code))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).contains("만료");
                });
    }

    @Test
    void 같은_번호로_동시에_발송을_요청해도_한_번만_발송된다() throws Exception {
        String phone = randomPhone();

        List<Object> results = runConcurrently(THREADS, () -> service.send(phone, randomIp()));

        assertThat(results.stream().filter(r -> !(r instanceof Exception)).count())
                .as("발송에 성공한 요청 수 (재발송 간격 1분)").isEqualTo(1);
    }

    @Test
    void 새_인증번호를_받으면_이전_틀린_횟수는_초기화된다() throws Exception {
        String phone = randomPhone();
        String first = service.send(phone, randomIp()).devCode();
        String wrong = first.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.verify(phone, wrong)).isInstanceOf(ResponseStatusException.class);
        }
        redis.delete(PREFIX + "cooldown:" + phoneHash(phone));

        String second = service.send(phone, randomIp()).devCode();

        assertThatThrownBy(() -> service.verify(phone, wrong))
                .hasMessageContaining("남은 기회 4번");
        assertThat(service.verify(phone, second)).isNotBlank();
    }
}
