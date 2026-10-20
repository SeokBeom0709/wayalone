package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 회원 탈퇴, 로그인 기록, 블랙리스트, 사용자 차단, 모집글 삭제 기록 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:accounttest;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.sms.ip-hourly-limit=1000",
        "app.admin.email=boss@wayalone.test",
        "app.admin.password=BossPass123"
})
class AccountApiTests {

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
        var request = client().get().uri(uri);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request.retrieve().body(String.class);
    }

    private String send(HttpMethod method, String token, String uri, String json) {
        var request = client().method(method).uri(uri).header("Authorization", "Bearer " + token);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(json);
        }
        return request.retrieve().body(String.class);
    }

    private String memberId(String token) {
        return TestAuth.group(ID, get(token, "/api/auth/me"));
    }

    private String createPost(String token, LocalDate start, int recruitCount) {
        String body = send(HttpMethod.POST, token, "/api/companions", """
                {"title":"제주 우도 자전거 동행","destination":"제주","startDate":"%s",
                 "endDate":"%s","recruitCount":%d,"content":"우도 한 바퀴 돌아요.","style":"자전거"}
                """.formatted(start, start.plusDays(1), recruitCount));
        return TestAuth.group(ID, body);
    }

    private String apply(String token, String postId) {
        return TestAuth.group(ID, send(HttpMethod.POST, token, "/api/companions/" + postId + "/applications",
                "{\"message\":\"같이 가고 싶어요\"}"));
    }

    private static String idMarker(String id) {
        return "\"id\":" + id + ",";
    }

    // ---------- 로그인 기록 ----------

    @Test
    void loginHistoryRecordsSuccessAndFailureAndShowsInAdminDetail() {
        String token = TestAuth.signup(client(), "history@example.com", "기록이");
        String id = memberId(token);

        assertThatThrownBy(() -> TestAuth.login(client(), "history@example.com", "Wrong1234x"))
                .hasMessageContaining("401");
        TestAuth.login(client(), "history@example.com", TestAuth.PASSWORD);

        String mine = get(token, "/api/auth/logins");
        assertThat(mine).contains("FAIL_CREDENTIALS").contains("SUCCESS").contains("\"ip\":\"");

        assertThat(get(admin(), "/api/admin/members/" + id)).contains("\"recentLogins\":[{").contains("FAIL_CREDENTIALS");
    }

    @Test
    void loginHistoryNeedsLogin() {
        assertThatThrownBy(() -> get(null, "/api/auth/logins")).hasMessageContaining("401");
    }

    // ---------- 회원 탈퇴 ----------

    @Test
    void withdrawalAnonymizesMemberDeletesPostsAndKeepsAuditTrail() {
        String host = TestAuth.signup(client(), "host@example.com", "모집장");
        String leaver = TestAuth.signup(client(), "leaver@example.com", "떠날사람");
        String leaverId = memberId(leaver);

        String hostPost = createPost(host, LocalDate.now().plusDays(10), 2);
        String leaverPost = createPost(leaver, LocalDate.now().plusDays(12), 2);
        apply(leaver, hostPost);
        assertThat(get(host, "/api/companions/" + hostPost + "/applications")).contains("떠날사람");

        assertThatThrownBy(() -> send(HttpMethod.POST, leaver, "/api/account/withdraw",
                "{\"password\":\"Wrong1234x\"}")).as("비밀번호가 틀리면 탈퇴되지 않아요").hasMessageContaining("400");
        assertThat(get(leaver, "/api/auth/me")).contains("leaver@example.com");

        send(HttpMethod.POST, leaver, "/api/account/withdraw",
                "{\"password\":\"%s\",\"reason\":\"더 이상 안 써요\"}".formatted(TestAuth.PASSWORD));

        assertThatThrownBy(() -> get(leaver, "/api/auth/me")).as("로그인이 풀려요").hasMessageContaining("401");
        assertThatThrownBy(() -> TestAuth.login(client(), "leaver@example.com", TestAuth.PASSWORD))
                .as("예전 이메일로는 로그인할 수 없어요").hasMessageContaining("401");
        assertThat(get(null, "/api/companions")).doesNotContain(idMarker(leaverPost));
        assertThat(get(host, "/api/companions/" + hostPost + "/applications"))
                .as("답을 못 받은 신청은 취소돼요").doesNotContain("떠날사람").doesNotContain("탈퇴한회원");

        String detail = get(admin(), "/api/admin/members/" + leaverId);
        assertThat(detail)
                .contains("\"status\":\"WITHDRAWN\"")
                .contains("\"email\":\"withdrawn-" + leaverId + "@deleted.invalid\"")
                .contains("\"nickname\":\"탈퇴한회원" + leaverId + "\"")
                .contains("\"phoneVerified\":false")
                .contains("\"deletedPosts\":1")
                .contains("\"canceledApplications\":1")
                .contains("더 이상 안 써요")
                .contains("\"reason\":\"WITHDRAWAL\"")
                .contains("우도 한 바퀴")
                .doesNotContain("leaver@example.com");
        assertThat(get(admin(), "/api/admin/stats")).doesNotContain("\"withdrawn\":0");
    }

    @Test
    void withdrawnMemberCannotBeSanctionedAndAdminCannotWithdraw() {
        String user = TestAuth.signup(client(), "gone@example.com", "사라질이");
        String id = memberId(user);
        send(HttpMethod.POST, user, "/api/account/withdraw", "{\"password\":\"%s\"}".formatted(TestAuth.PASSWORD));

        assertThatThrownBy(() -> send(HttpMethod.POST, admin(), "/api/admin/members/" + id + "/sanction",
                "{\"action\":\"BAN\",\"reason\":\"테스트\"}")).hasMessageContaining("400");
        assertThatThrownBy(() -> send(HttpMethod.POST, admin(), "/api/account/withdraw",
                "{\"password\":\"BossPass123\"}")).hasMessageContaining("400");
    }

    @Test
    void impersonatingWithdrawnNicknameIsRejected() {
        String phone = TestAuth.nextPhone();
        String phoneToken = TestAuth.verifiedPhoneToken(client(), phone);
        assertThatThrownBy(() -> TestAuth.signupBody(client(), "fake@example.com", "탈퇴한회원77", phone, phoneToken))
                .hasMessageContaining("400");
    }

    // ---------- 블랙리스트 ----------

    @Test
    void banPutsPhoneOnBlacklistAndReleaseTakesItOff() {
        String phone = TestAuth.nextPhone();
        String token = TestAuth.extractToken(TestAuth.signupBody(client(), "banned@example.com", "차단될사람",
                phone, TestAuth.verifiedPhoneToken(client(), phone)));
        String id = memberId(token);
        String before = get(admin(), "/api/admin/blacklist");
        assertThat(before).doesNotContain("\"sourceMemberId\":" + id + ",");

        send(HttpMethod.POST, admin(), "/api/admin/members/" + id + "/sanction",
                "{\"action\":\"BAN\",\"reason\":\"반복 신고\"}");
        assertThat(get(admin(), "/api/admin/blacklist"))
                .contains("\"sourceMemberId\":" + id + ",")
                .contains("반복 신고")
                .doesNotContain(phone);
        assertThat(get(admin(), "/api/admin/stats")).contains("\"blacklistedPhones\":");
        assertThatThrownBy(() -> TestAuth.sendCode(client(), phone)).as("차단된 번호는 인증번호도 못 받아요")
                .hasMessageContaining("409");

        send(HttpMethod.POST, admin(), "/api/admin/members/" + id + "/sanction", "{\"action\":\"RELEASE\"}");
        assertThat(get(admin(), "/api/admin/blacklist")).doesNotContain("\"sourceMemberId\":" + id + ",");
    }

    @Test
    void blacklistIsAdminOnly() {
        String user = TestAuth.signup(client(), "peek@example.com", "엿보는이");
        assertThatThrownBy(() -> get(user, "/api/admin/blacklist")).hasMessageContaining("403");
    }

    // ---------- 사용자 차단 ----------

    @Test
    void blockingHidesPostsBothWaysAndBlocksApplying() {
        String author = TestAuth.signup(client(), "author@example.com", "글쓴이");
        String viewer = TestAuth.signup(client(), "viewer@example.com", "구경꾼");
        String authorId = memberId(author);
        String viewerId = memberId(viewer);
        String authorPost = createPost(author, LocalDate.now().plusDays(7), 2);
        String viewerPost = createPost(viewer, LocalDate.now().plusDays(8), 2);

        assertThat(get(viewer, "/api/companions")).contains(idMarker(authorPost));

        send(HttpMethod.POST, viewer, "/api/blocks/" + authorId, null);
        send(HttpMethod.POST, viewer, "/api/blocks/" + authorId, null); // 두 번 눌러도 괜찮아요

        assertThat(get(viewer, "/api/blocks")).contains("\"nickname\":\"글쓴이\"");
        assertThat(get(viewer, "/api/companions")).doesNotContain(idMarker(authorPost));
        assertThat(get(author, "/api/companions")).as("차단당한 쪽도 서로 보이지 않아요").doesNotContain(idMarker(viewerPost));
        assertThat(get(null, "/api/companions")).as("로그인하지 않으면 그대로 보여요").contains(idMarker(authorPost));
        assertThatThrownBy(() -> get(viewer, "/api/companions/" + authorPost)).hasMessageContaining("404");
        assertThatThrownBy(() -> apply(viewer, authorPost)).hasMessageContaining("404");
        assertThatThrownBy(() -> apply(author, viewerPost)).hasMessageContaining("404");

        send(HttpMethod.DELETE, viewer, "/api/blocks/" + authorId, null);
        assertThat(get(viewer, "/api/blocks")).doesNotContain("글쓴이");
        assertThat(get(viewer, "/api/companions")).contains(idMarker(authorPost));
        assertThat(apply(viewer, authorPost)).isNotBlank();
        assertThat(viewerId).isNotEqualTo(authorId);
    }

    @Test
    void blockedApplicantDisappearsFromAuthorsList() {
        String author = TestAuth.signup(client(), "owner1@example.com", "방장님");
        String pest = TestAuth.signup(client(), "pest1@example.com", "귀찮은이");
        String pestId = memberId(pest);
        String post = createPost(author, LocalDate.now().plusDays(7), 2);
        apply(pest, post);
        assertThat(get(author, "/api/companions/" + post + "/applications")).contains("귀찮은이");

        send(HttpMethod.POST, author, "/api/blocks/" + pestId, null);
        assertThat(get(author, "/api/companions/" + post + "/applications")).doesNotContain("귀찮은이");
    }

    @Test
    void cannotBlockSelfOrAdmin() {
        String user = TestAuth.signup(client(), "selfblock@example.com", "자기자신");
        String userId = memberId(user);
        String adminId = memberId(admin());
        assertThatThrownBy(() -> send(HttpMethod.POST, user, "/api/blocks/" + userId, null)).hasMessageContaining("400");
        assertThatThrownBy(() -> send(HttpMethod.POST, user, "/api/blocks/" + adminId, null)).hasMessageContaining("400");
        assertThatThrownBy(() -> send(HttpMethod.POST, user, "/api/blocks/999999", null)).hasMessageContaining("404");
    }

    // ---------- 모집글 삭제 ----------

    @Test
    void authorDeleteHidesPostAndLeavesDeletionLog() {
        String author = TestAuth.signup(client(), "deleter@example.com", "지우는이");
        String guest = TestAuth.signup(client(), "guest9@example.com", "참가희망");
        String authorId = memberId(author);
        String post = createPost(author, LocalDate.now().plusDays(7), 2);
        String applicationId = apply(guest, post);

        assertThatThrownBy(() -> send(HttpMethod.DELETE, guest, "/api/companions/" + post, null))
                .as("남의 글은 지울 수 없어요").hasMessageContaining("403");

        send(HttpMethod.PATCH, author, "/api/companions/" + post + "/applications/" + applicationId,
                "{\"status\":\"ACCEPTED\"}");
        assertThatThrownBy(() -> send(HttpMethod.DELETE, author, "/api/companions/" + post, null))
                .as("수락한 동행이 있으면 먼저 취소해야 해요").hasMessageContaining("409");

        send(HttpMethod.PATCH, author, "/api/companions/" + post + "/applications/" + applicationId,
                "{\"status\":\"REJECTED\"}");
        send(HttpMethod.DELETE, author, "/api/companions/" + post, null);

        assertThat(get(null, "/api/companions")).doesNotContain(idMarker(post));
        assertThatThrownBy(() -> get(null, "/api/companions/" + post)).hasMessageContaining("404");
        assertThat(get(author, "/api/companions/mine")).doesNotContain(idMarker(post));
        assertThatThrownBy(() -> send(HttpMethod.DELETE, author, "/api/companions/" + post, null))
                .hasMessageContaining("404");

        assertThat(get(admin(), "/api/admin/members/" + authorId))
                .contains("\"reason\":\"AUTHOR\"")
                .contains("우도 한 바퀴")
                .contains("\"deleted\":true");
    }
}
