package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admintest;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.sms.ip-hourly-limit=1000",
        "app.admin.email=boss@wayalone.test",
        "app.admin.password=BossPass123"
})
class AdminApiTests {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @LocalServerPort
    int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private String admin() {
        return TestAuth.login(client(), "boss@wayalone.test", "BossPass123");
    }

    private String get(String token, String uri) {
        return client().get().uri(uri).header("Authorization", "Bearer " + token).retrieve().body(String.class);
    }

    private String send(String method, String token, String uri, String json) {
        return client().method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .body(String.class);
    }

    private String memberId(String token) {
        return TestAuth.group(ID, get(token, "/api/auth/me"));
    }

    @Test
    void bootstrapAdminCanSeeStatsButMembersCannot() {
        String admin = admin();
        assertThat(get(admin, "/api/auth/me")).contains("\"role\":\"ADMIN\"");
        assertThat(get(admin, "/api/admin/stats"))
                .contains("\"members\"")
                .contains("\"openVocs\"")
                .contains("\"daily\":[{\"date\":");

        String user = TestAuth.signup(client(), "plain@example.com", "평범이");
        assertThatThrownBy(() -> get(user, "/api/admin/stats")).hasMessageContaining("403");
    }

    @Test
    void suspendBlocksLoginAndReleaseRestoresIt() {
        String user = TestAuth.signup(client(), "trouble@example.com", "말썽이");
        String userId = memberId(user);

        send("POST", admin(), "/api/admin/members/" + userId + "/sanction",
                "{\"action\":\"SUSPEND\",\"days\":7,\"reason\":\"반복 노쇼\"}");

        assertThatThrownBy(() -> get(user, "/api/auth/me")).as("기존 로그인도 끊겨요").hasMessageContaining("401");
        assertThatThrownBy(() -> TestAuth.login(client(), "trouble@example.com", TestAuth.PASSWORD))
                .hasMessageContaining("403");
        assertThat(get(admin(), "/api/admin/members/" + userId))
                .contains("\"status\":\"SUSPENDED\"")
                .contains("반복 노쇼");

        send("POST", admin(), "/api/admin/members/" + userId + "/sanction", "{\"action\":\"RELEASE\"}");
        assertThat(TestAuth.login(client(), "trouble@example.com", TestAuth.PASSWORD)).isNotBlank();
    }

    @Test
    void sanctionRequiresReasonAndCannotTargetAdmin() {
        String user = TestAuth.signup(client(), "noreason@example.com", "사유없음");
        String userId = memberId(user);
        assertThatThrownBy(() -> send("POST", admin(), "/api/admin/members/" + userId + "/sanction",
                "{\"action\":\"BAN\"}")).hasMessageContaining("400");

        String adminId = memberId(admin());
        assertThatThrownBy(() -> send("POST", admin(), "/api/admin/members/" + adminId + "/sanction",
                "{\"action\":\"BAN\",\"reason\":\"테스트\"}")).hasMessageContaining("400");
    }

    @Test
    void reportFlowsToAdminAndAnswerReturnsToReporter() {
        String reporter = TestAuth.signup(client(), "reporter@example.com", "제보자");
        String rude = TestAuth.signup(client(), "rude@example.com", "무례한이");
        String rudeId = memberId(rude);

        String created = send("POST", reporter, "/api/vocs",
                "{\"type\":\"REPORT_MEMBER\",\"targetMemberId\":%s,\"content\":\"약속에 안 나왔어요\"}".formatted(rudeId));
        String vocId = TestAuth.group(ID, created);

        assertThat(get(admin(), "/api/admin/vocs?status=OPEN")).contains("약속에 안 나왔어요").contains("무례한이");
        assertThat(get(admin(), "/api/admin/members?q=무례")).contains("\"reportCount\":1");

        send("PATCH", admin(), "/api/admin/vocs/" + vocId,
                "{\"status\":\"RESOLVED\",\"answer\":\"확인 후 경고 조치했어요.\",\"adminMemo\":\"1차 경고\"}");

        String mine = get(reporter, "/api/vocs/mine");
        assertThat(mine).contains("\"status\":\"RESOLVED\"").contains("확인 후 경고 조치했어요.");
        assertThat(mine).as("내부 메모는 회원에게 보이지 않아요").doesNotContain("1차 경고");
        assertThat(get(admin(), "/api/admin/actions")).contains("VOC_UPDATE");
    }

    @Test
    void hiddenPostDisappearsFromPublicButStaysInAuthorsList() {
        String writer = TestAuth.signup(client(), "spam@example.com", "광고러");
        String post = send("POST", writer, "/api/companions", """
                {"title":"광고성 모집글","destination":"서울","startDate":"%s","endDate":"%s",
                 "recruitCount":2,"content":"홍보 링크","style":"광고"}
                """.formatted(LocalDate.now().plusDays(3), LocalDate.now().plusDays(3)));
        String postId = TestAuth.group(ID, post);

        send("PATCH", admin(), "/api/admin/companions/" + postId + "/visibility",
                "{\"hidden\":true,\"reason\":\"광고\"}");

        assertThat(client().get().uri("/api/companions").retrieve().body(String.class)).doesNotContain("광고성 모집글");
        assertThatThrownBy(() -> client().get().uri("/api/companions/" + postId).retrieve().body(String.class))
                .hasMessageContaining("404");
        assertThat(get(writer, "/api/companions/mine")).contains("광고성 모집글").contains("\"hidden\":true");
    }

    private String latestActionId(String token) {
        return TestAuth.group(ID, get(token, "/api/admin/actions"));
    }

    @Test
    void mistakenSuspensionCanBeRevertedAndIsLogged() {
        String user = TestAuth.signup(client(), "oops@example.com", "억울한이");
        String userId = memberId(user);
        String admin = admin();
        send("POST", admin, "/api/admin/members/" + userId + "/sanction",
                "{\"action\":\"BAN\",\"reason\":\"다른 회원과 착각\"}");
        String banId = latestActionId(admin);

        send("POST", admin, "/api/admin/actions/" + banId + "/revert", "{\"reason\":\"동명이인 착각\"}");

        assertThat(TestAuth.login(client(), "oops@example.com", TestAuth.PASSWORD)).isNotBlank();
        String actions = get(admin, "/api/admin/actions");
        assertThat(actions).contains("\"action\":\"REVERT\"").contains("\"revertOfId\":" + banId)
                .contains("동명이인 착각");
        assertThatThrownBy(() -> send("POST", admin, "/api/admin/actions/" + banId + "/revert",
                "{\"reason\":\"한 번 더\"}")).hasMessageContaining("409");
    }

    @Test
    void onlyTheLatestActionOnATargetCanBeReverted() {
        String user = TestAuth.signup(client(), "chain@example.com", "연속처리");
        String userId = memberId(user);
        String admin = admin();
        send("POST", admin, "/api/admin/members/" + userId + "/sanction",
                "{\"action\":\"SUSPEND\",\"days\":3,\"reason\":\"1차\"}");
        String first = latestActionId(admin);
        send("POST", admin, "/api/admin/members/" + userId + "/sanction",
                "{\"action\":\"BAN\",\"reason\":\"2차\"}");
        String second = latestActionId(admin);

        assertThatThrownBy(() -> send("POST", admin, "/api/admin/actions/" + first + "/revert",
                "{\"reason\":\"순서 무시\"}")).hasMessageContaining("409");

        send("POST", admin, "/api/admin/actions/" + second + "/revert", "{\"reason\":\"2차 취소\"}");
        assertThat(get(admin, "/api/admin/members/" + userId)).contains("\"status\":\"SUSPENDED\"");
        String revertEntry = latestActionId(admin);
        assertThatThrownBy(() -> send("POST", admin, "/api/admin/actions/" + revertEntry + "/revert",
                "{\"reason\":\"되돌리기의 되돌리기\"}")).hasMessageContaining("400");

        send("POST", admin, "/api/admin/actions/" + first + "/revert", "{\"reason\":\"1차도 취소\"}");
        assertThat(get(admin, "/api/admin/members/" + userId)).contains("\"status\":\"ACTIVE\"");
    }

    @Test
    void hiddenPostCanBeRestoredByRevert() {
        String writer = TestAuth.signup(client(), "restore@example.com", "복구될글");
        String postId = TestAuth.group(ID, send("POST", writer, "/api/companions", """
                {"title":"억울하게 숨겨진 글","destination":"서울","startDate":"%s","endDate":"%s",
                 "recruitCount":2,"content":"정상 모집글","style":"맛집"}
                """.formatted(LocalDate.now().plusDays(2), LocalDate.now().plusDays(2))));
        String admin = admin();
        send("PATCH", admin, "/api/admin/companions/" + postId + "/visibility", "{\"hidden\":true,\"reason\":\"광고 의심\"}");
        send("POST", admin, "/api/admin/actions/" + latestActionId(admin) + "/revert", "{\"reason\":\"정상 글 확인\"}");

        assertThat(client().get().uri("/api/companions/" + postId).retrieve().body(String.class))
                .contains("억울하게 숨겨진 글");
    }

    @Test
    void staffLikeNicknamesAreReserved() {
        String phone = TestAuth.nextPhone();
        String token = TestAuth.verifiedPhoneToken(client(), phone);
        assertThatThrownBy(() -> TestAuth.signupBody(client(), "fake@example.com", "진짜관리자", phone, token))
                .hasMessageContaining("400");
    }
}
