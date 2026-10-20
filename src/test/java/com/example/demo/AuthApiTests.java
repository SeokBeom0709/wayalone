package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:authtest;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.sms.ip-hourly-limit=1000"
})
class AuthApiTests {

    @LocalServerPort
    int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private String login(String email, String password) {
        return client().post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password))
                .retrieve()
                .body(String.class);
    }

    @Test
    void signupThenMe() {
        String token = TestAuth.signup(client(), "me@example.com", "미니");
        String me = client().get()
                .uri("/api/auth/me")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);
        assertThat(me).contains("me@example.com").contains("미니").contains("\"phoneVerified\":true");
        assertThat(me).doesNotContain("passwordHash").doesNotContain("phoneHash");
    }

    @Test
    void signupWithoutPhoneVerificationIsRejected() {
        assertThatThrownBy(() -> client().post()
                .uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"email":"nophone@example.com","nickname":"번호없음","password":"%s"}
                        """.formatted(TestAuth.PASSWORD))
                .retrieve()
                .body(String.class))
                .hasMessageContaining("400");
    }

    @Test
    void wrongCodeIsRejectedWithRemainingAttempts() {
        String phone = TestAuth.nextPhone();
        String code = TestAuth.sendCode(client(), phone);
        String wrong = code.equals("000000") ? "111111" : "000000";
        assertThatThrownBy(() -> TestAuth.verifyCode(client(), phone, wrong))
                .hasMessageContaining("400")
                .hasMessageContaining("남은 기회 4번");
    }

    @Test
    void devInboxShowsSentMessageWithMaskedNumber() {
        String phone = TestAuth.nextPhone();
        String code = TestAuth.sendCode(client(), phone);
        String inbox = client().get().uri("/api/dev/sms").retrieve().body(String.class);
        assertThat(inbox)
                .contains(code)
                .contains(phone.substring(0, 3) + "-****-" + phone.substring(7))
                .doesNotContain(phone);
    }

    @Test
    void resendWithinCooldownIsBlocked() {
        String phone = TestAuth.nextPhone();
        TestAuth.sendCode(client(), phone);
        assertThatThrownBy(() -> TestAuth.sendCode(client(), phone)).hasMessageContaining("429");
    }

    @Test
    void samePhoneCannotBeUsedTwice() {
        String phone = TestAuth.nextPhone();
        String token = TestAuth.verifiedPhoneToken(client(), phone);
        TestAuth.signupBody(client(), "first@example.com", "첫번째", phone, token);

        assertThatThrownBy(() -> TestAuth.signupBody(client(), "second@example.com", "두번째", phone, token))
                .as("인증 토큰은 한 번만 쓸 수 있어요")
                .hasMessageContaining("400");
        assertThatThrownBy(() -> TestAuth.sendCode(client(), phone))
                .as("이미 가입된 번호로는 인증번호를 받을 수 없어요")
                .hasMessageContaining("409");
    }

    @Test
    void duplicateEmailIsRejected() {
        TestAuth.signup(client(), "dup@example.com", "중복1");
        assertThatThrownBy(() -> TestAuth.signup(client(), "dup@example.com", "중복2"))
                .hasMessageContaining("409");
    }

    @Test
    void weakPasswordIsRejected() {
        assertThatThrownBy(() -> client().post()
                .uri("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"email":"weak@example.com","nickname":"약한비번","password":"abc"}
                        """)
                .retrieve()
                .body(String.class))
                .hasMessageContaining("400");
    }

    @Test
    void wrongPasswordIsUnauthorized() {
        TestAuth.signup(client(), "wrong@example.com", "틀린사람");
        assertThatThrownBy(() -> login("wrong@example.com", "Wrong0000"))
                .hasMessageContaining("401");
    }

    @Test
    void loginWorksWithRightPassword() {
        TestAuth.signup(client(), "ok@example.com", "성공이");
        String body = login("OK@Example.com", TestAuth.PASSWORD);
        assertThat(body).contains("\"token\"").contains("성공이");
    }

    @Test
    void changePasswordInvalidatesOldPasswordAndOldToken() {
        String oldToken = TestAuth.signup(client(), "change@example.com", "바꾸는이");
        String changed = client().post()
                .uri("/api/auth/password")
                .header("Authorization", "Bearer " + oldToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"newPassword":"NewPassw0rd9"}
                        """)
                .retrieve()
                .body(String.class);
        assertThat(TestAuth.extractToken(changed)).isNotEqualTo(oldToken);

        assertThatThrownBy(() -> login("change@example.com", TestAuth.PASSWORD)).hasMessageContaining("401");
        assertThat(login("change@example.com", "NewPassw0rd9")).contains("\"token\"");
        assertThatThrownBy(() -> client().get()
                .uri("/api/auth/me")
                .header("Authorization", "Bearer " + oldToken)
                .retrieve()
                .body(String.class))
                .hasMessageContaining("401");
    }

    @Test
    void changingToSamePasswordIsRejected() {
        String token = TestAuth.signup(client(), "same@example.com", "같은비번");
        assertThatThrownBy(() -> client().post()
                .uri("/api/auth/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"newPassword":"%s"}
                        """.formatted(TestAuth.PASSWORD))
                .retrieve()
                .body(String.class))
                .hasMessageContaining("400");
    }

    @Test
    void logoutRevokesToken() {
        String token = TestAuth.signup(client(), "bye@example.com", "나가는이");
        client().post()
                .uri("/api/auth/logout")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);
        assertThatThrownBy(() -> client().get()
                .uri("/api/auth/me")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class))
                .hasMessageContaining("401");
    }

    @Test
    void writingRequiresLogin() {
        assertThatThrownBy(() -> client().post()
                .uri("/api/places/1/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"rating":5,"content":"로그인 없이 쓰기"}
                        """)
                .retrieve()
                .body(String.class))
                .hasMessageContaining("401");
    }
}
