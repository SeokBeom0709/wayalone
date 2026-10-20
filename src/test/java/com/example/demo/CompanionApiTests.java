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
        "spring.datasource.url=jdbc:h2:mem:companiontest;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.sms.ip-hourly-limit=1000"
})
class CompanionApiTests {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @LocalServerPort
    int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    private String createPost(String token, LocalDate start, int recruitCount) {
        String body = client().post()
                .uri("/api/companions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title":"제주 우도 자전거 동행","destination":"제주","startDate":"%s",
                         "endDate":"%s","recruitCount":%d,"content":"우도 한 바퀴 돌아요.","style":"자전거"}
                        """.formatted(start, start.plusDays(1), recruitCount))
                .retrieve()
                .body(String.class);
        return TestAuth.group(ID, body);
    }

    private String apply(String token, String postId, String message) {
        String body = client().post()
                .uri("/api/companions/" + postId + "/applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"message":"%s"}
                        """.formatted(message))
                .retrieve()
                .body(String.class);
        return TestAuth.group(ID, body);
    }

    private String applications(String token, String postId) {
        return client().get()
                .uri("/api/companions/" + postId + "/applications")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);
    }

    private String decide(String token, String postId, String applicationId, String status) {
        return client().patch()
                .uri("/api/companions/" + postId + "/applications/" + applicationId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"status":"%s"}
                        """.formatted(status))
                .retrieve()
                .body(String.class);
    }

    private void rate(String token, String postId, String targetId, int score, String tags) {
        rate(token, postId, targetId, true, score, tags);
    }

    private void rate(String token, String postId, String targetId, boolean attended, int score, String tags) {
        client().post()
                .uri("/api/companions/" + postId + "/ratings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"targetId":%s,"attended":%s,"score":%d,"tags":[%s]}
                        """.formatted(targetId, attended, score, tags))
                .retrieve()
                .toBodilessEntity();
    }

    private String memberId(String token) {
        String me = client().get().uri("/api/auth/me")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(String.class);
        return TestAuth.group(ID, me);
    }

    @Test
    void listsSeededJejuPosts() {
        String body = client().get().uri("/api/companions?q=제주").retrieve().body(String.class);
        assertThat(body).contains("제주");
        assertThat(body).doesNotContain("부산 1박 2일");
    }

    @Test
    void applicationsAreVisibleOnlyToAuthorAndApplicant() {
        String writer = TestAuth.signup(client(), "writer@example.com", "글쓴이");
        String alice = TestAuth.signup(client(), "alice@example.com", "앨리스");
        String bob = TestAuth.signup(client(), "bob@example.com", "밥아저씨");
        String postId = createPost(writer, LocalDate.now().plusDays(30), 2);
        apply(alice, postId, "앨리스 연락처 010-0000-0001");
        apply(bob, postId, "밥 연락처 010-0000-0002");

        assertThatThrownBy(() -> client().get()
                .uri("/api/companions/" + postId + "/applications")
                .retrieve()
                .body(String.class))
                .hasMessageContaining("401");
        assertThat(applications(alice, postId)).contains("앨리스 연락처").doesNotContain("밥 연락처");
        assertThat(applications(writer, postId)).contains("앨리스 연락처").contains("밥 연락처");
    }

    @Test
    void mineListsMyPostsAndMyApplications() {
        String writer = TestAuth.signup(client(), "mine-writer@example.com", "내글러");
        String guest = TestAuth.signup(client(), "mine-guest@example.com", "내신청러");
        String postId = createPost(writer, LocalDate.now().plusDays(5), 2);
        apply(guest, postId, "끼워 주세요");

        assertThat(client().get().uri("/api/companions/mine")
                .header("Authorization", "Bearer " + writer).retrieve().body(String.class))
                .contains("\"posts\":[{\"id\":" + postId)
                .contains("\"applications\":[]");
        assertThat(client().get().uri("/api/companions/mine")
                .header("Authorization", "Bearer " + guest).retrieve().body(String.class))
                .contains("\"posts\":[]")
                .contains("\"status\":\"PENDING\"");
    }

    @Test
    void selfAndDuplicateApplicationsAreRejected() {
        String writer = TestAuth.signup(client(), "self@example.com", "셀프");
        String fan = TestAuth.signup(client(), "fan@example.com", "열혈팬");
        String postId = createPost(writer, LocalDate.now().plusDays(30), 2);

        assertThatThrownBy(() -> apply(writer, postId, "내 글에 신청")).hasMessageContaining("400");
        apply(fan, postId, "같이 가요");
        assertThatThrownBy(() -> apply(fan, postId, "한 번 더")).hasMessageContaining("409");
    }

    @Test
    void onlyAuthorDecidesAndRecruitLimitIsEnforced() {
        String writer = TestAuth.signup(client(), "host@example.com", "호스트");
        String first = TestAuth.signup(client(), "first1@example.com", "첫신청");
        String second = TestAuth.signup(client(), "second2@example.com", "둘째신청");
        String postId = createPost(writer, LocalDate.now().plusDays(30), 1);
        String firstApp = apply(first, postId, "저 먼저요");
        String secondApp = apply(second, postId, "저도요");

        assertThatThrownBy(() -> decide(first, postId, firstApp, "ACCEPTED")).hasMessageContaining("403");
        assertThat(decide(writer, postId, firstApp, "ACCEPTED")).contains("\"status\":\"ACCEPTED\"");
        assertThatThrownBy(() -> decide(writer, postId, secondApp, "ACCEPTED")).hasMessageContaining("409");
        assertThat(decide(writer, postId, secondApp, "REJECTED")).contains("\"status\":\"REJECTED\"");
    }

    @Test
    void acceptedCompanionsRateEachOtherOnce() {
        String writer = TestAuth.signup(client(), "leader@example.com", "리더");
        String mate = TestAuth.signup(client(), "mate@example.com", "메이트");
        String stranger = TestAuth.signup(client(), "stranger@example.com", "구경꾼");
        String postId = createPost(writer, LocalDate.now(), 2);
        String mateApp = apply(mate, postId, "함께해요");
        apply(stranger, postId, "저도 끼워 주세요");
        decide(writer, postId, mateApp, "ACCEPTED");
        String writerId = memberId(writer);

        String targets = client().get().uri("/api/companions/" + postId + "/ratings/targets")
                .header("Authorization", "Bearer " + mate)
                .retrieve()
                .body(String.class);
        assertThat(targets).contains("리더").contains("\"rated\":false").doesNotContain("구경꾼");

        rate(mate, postId, writerId, 5, "\"PUNCTUAL\",\"KIND\"");
        assertThatThrownBy(() -> rate(mate, postId, writerId, 4, "")).hasMessageContaining("409");
        assertThatThrownBy(() -> rate(stranger, postId, writerId, 1, "\"RUDE\"")).hasMessageContaining("403");

        String profile = client().get().uri("/api/members/" + writerId + "/profile").retrieve().body(String.class);
        assertThat(profile)
                .contains("\"phoneVerified\":true")
                .contains("\"average\":5.0")
                .contains("\"count\":1")
                .contains("\"temperature\":37.0")
                .doesNotContain("attendanceRate")
                .contains("시간 약속을 잘 지켜요");
    }

    @Test
    void noShowTagIsCountedForOperatorsButOnlyScoreMovesTemperature() {
        String writer = TestAuth.signup(client(), "noshow-host@example.com", "기다린사람");
        String ghost = TestAuth.signup(client(), "ghost@example.com", "못온사람");
        String postId = createPost(writer, LocalDate.now(), 2);
        decide(writer, postId, apply(ghost, postId, "꼭 갈게요"), "ACCEPTED");
        String ghostId = memberId(ghost);

        client().post()
                .uri("/api/companions/" + postId + "/ratings")
                .header("Authorization", "Bearer " + writer)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"targetId":%s,"score":1,"tags":["NO_SHOW"]}
                        """.formatted(ghostId))
                .retrieve()
                .toBodilessEntity();

        String profile = client().get().uri("/api/members/" + ghostId + "/profile").retrieve().body(String.class);
        assertThat(profile)
                .contains("\"noShows\":1")
                .contains("\"temperature\":36.4")
                .doesNotContain("약속 장소에 나오지 않았어요");
    }

    @Test
    void ratingBeforeTripStartIsRejected() {
        String writer = TestAuth.signup(client(), "early@example.com", "얼리버드");
        String mate = TestAuth.signup(client(), "early-mate@example.com", "얼리메이트");
        String postId = createPost(writer, LocalDate.now().plusDays(10), 2);
        decide(writer, postId, apply(mate, postId, "미리 신청"), "ACCEPTED");

        assertThatThrownBy(() -> rate(mate, postId, memberId(writer), 5, "")).hasMessageContaining("400");
    }
}
