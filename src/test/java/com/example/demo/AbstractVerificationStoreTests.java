package com.example.demo;

import com.example.demo.member.phone.PhoneVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 인증번호 저장소(DB / Redis)가 공통으로 지켜야 하는 규칙.
 * 같은 규칙을 두 구현에 모두 돌려서, 저장소를 바꿔도 동작이 같음을 확인합니다.
 */
abstract class AbstractVerificationStoreTests {

    static final int THREADS = 24;

    @Autowired
    protected PhoneVerificationService service;

    @Autowired
    protected TransactionTemplate transactions;

    /** 매번 다른 번호를 써서, 이전 실행(특히 Redis 에 남은 키)의 영향을 받지 않게 합니다. */
    protected static String randomPhone() {
        return "010" + "%08d".formatted(ThreadLocalRandom.current().nextInt(100_000_000));
    }

    protected static String randomIp() {
        return "10.%d.%d.%d".formatted(ThreadLocalRandom.current().nextInt(256),
                ThreadLocalRandom.current().nextInt(256), ThreadLocalRandom.current().nextInt(256));
    }

    /** 모든 작업이 동시에 출발하도록 맞춰서 실행하고, 값 또는 예외를 모아 돌려줍니다. */
    protected static <T> List<Object> runConcurrently(int count, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return task.call();
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        ready.await();
        go.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            results.add(future.get());
        }
        pool.shutdown();
        return results;
    }

    @Test
    void 인증_토큰은_동시에_써도_한_번만_소비된다() throws Exception {
        String phone = randomPhone();
        String token = service.verify(phone, service.send(phone, randomIp()).devCode());

        List<Object> results = runConcurrently(THREADS, () -> service.consume(phone, token));

        assertThat(results.stream().filter(r -> r instanceof String).count())
                .as("같은 토큰으로 성공한 요청 수").isEqualTo(1);
    }

    @Test
    void 틀린_인증번호를_동시에_보내도_시도_횟수는_5번까지만_인정된다() throws Exception {
        String phone = randomPhone();
        String code = service.send(phone, randomIp()).devCode();
        String wrong = code.equals("000000") ? "111111" : "000000";

        List<Object> results = runConcurrently(THREADS, () -> service.verify(phone, wrong));

        long guided = results.stream()
                .filter(r -> r instanceof ResponseStatusException e && e.getStatusCode() == HttpStatus.BAD_REQUEST
                        && e.getReason() != null && e.getReason().contains("남은 기회"))
                .count();
        // 5번째 실패부터는 "남은 기회"가 아니라 429 로 응답하므로, 안내가 나간 시도는 최대 4번입니다.
        assertThat(guided).as("틀렸다고 안내받은 횟수").isLessThanOrEqualTo(4);
        // 한도를 넘긴 뒤에는 맞는 인증번호로도 통과할 수 없습니다.
        assertThatThrownBy(() -> service.verify(phone, code))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void 맞는_인증번호로_동시에_확인해도_토큰은_한_번만_발급된다() throws Exception {
        String phone = randomPhone();
        String code = service.send(phone, randomIp()).devCode();

        List<Object> results = runConcurrently(THREADS, () -> service.verify(phone, code));

        assertThat(results.stream().filter(r -> r instanceof String).count())
                .as("발급된 토큰 수").isEqualTo(1);
    }

    @Test
    void 가입이_롤백되면_소비한_토큰은_다시_쓸_수_있다() {
        String phone = randomPhone();
        String token = service.verify(phone, service.send(phone, randomIp()).devCode());

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            service.consume(phone, token);
            throw new IllegalStateException("회원가입 중 다른 검증에서 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(service.consume(phone, token)).as("롤백 뒤에는 같은 토큰으로 다시 가입할 수 있어요").isNotBlank();
        assertThatThrownBy(() -> service.consume(phone, token))
                .as("성공한 뒤에는 다시 쓸 수 없어요")
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void 다른_번호로는_토큰을_쓸_수_없고_토큰도_소모되지_않는다() {
        String phone = randomPhone();
        String token = service.verify(phone, service.send(phone, randomIp()).devCode());

        assertThatThrownBy(() -> service.consume(randomPhone(), token)).isInstanceOf(ResponseStatusException.class);

        assertThat(service.consume(phone, token)).isNotBlank();
    }
}
