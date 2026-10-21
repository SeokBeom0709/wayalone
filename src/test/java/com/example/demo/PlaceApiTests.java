package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:honhaengtest;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "kakao.rest-key="
})
class PlaceApiTests {

    @LocalServerPort
    int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    @Test
    void listsSeededPlaces() {
        String body = client().get().uri("/api/places").retrieve().body(String.class);
        assertThat(body).contains("을지로");
    }

    @Test
    void filtersSoloDiningFood() {
        String body = client().get()
                .uri("/api/places?intent=food&soloDining=YES")
                .retrieve()
                .body(String.class);
        assertThat(body).contains("YES");
        assertThat(body).doesNotContain("\"soloDining\":\"NO\"");
        assertThat(body).doesNotContain("\"soloDining\":\"COUNTER\"");
    }

    private static List<Integer> distances(String body) {
        Matcher matcher = Pattern.compile("\"distanceMeters\":(\\d+)").matcher(body);
        List<Integer> distances = new ArrayList<>();
        while (matcher.find()) {
            distances.add(Integer.parseInt(matcher.group(1)));
        }
        return distances;
    }

    @Test
    void aroundReturnsPlacesInsideRadiusNearestFirst() {
        // 시드 데이터 중 첫 번째 장소의 좌표 (37.5663, 126.9910)
        String body = client().get()
                .uri("/api/places/around?lat=37.5663&lng=126.9910&radius=3000")
                .retrieve().body(String.class);

        List<Integer> distances = distances(body);
        assertThat(distances).hasSizeGreaterThan(1);
        assertThat(distances.get(0)).as("기준 위치 바로 그 장소").isZero();
        assertThat(distances).as("가까운 순").isSorted();
        assertThat(distances).as("반경 3km 안").allMatch(d -> d <= 3000);
        assertThat(body).as("부산 장소는 제외").doesNotContain("해운대");
    }

    @Test
    void aroundExcludesPlacesOutsideSmallRadius() {
        String body = client().get()
                .uri("/api/places/around?lat=37.5663&lng=126.9910&radius=100")
                .retrieve().body(String.class);

        assertThat(distances(body)).containsExactly(0);
    }

    @Test
    void aroundRejectsInvalidCoordinates() {
        assertThatThrownBy(() -> client().get()
                .uri("/api/places/around?lat=123&lng=127")
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    void nearbyRejectsInvalidCoordinates() {
        assertThatThrownBy(() -> client().get()
                .uri("/api/places/nearby?lat=123&lng=127")
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    void nearbyReportsMissingKakaoKey() {
        assertThatThrownBy(() -> client().get()
                .uri("/api/places/nearby?lat=37.5665&lng=126.9780")
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
    }

    @Test
    void locationSearchRejectsBlankQuery() {
        assertThatThrownBy(() -> client().get()
                .uri(b -> b.path("/api/places/locations").queryParam("q", " ").build())
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    void locationSearchReportsMissingKakaoKey() {
        assertThatThrownBy(() -> client().get()
                .uri("/api/places/locations?q=성수역")
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
    }

    @Test
    void createsSoloReviewWithMemberNickname() {
        String token = TestAuth.signup(client(), "reviewer@example.com", "리뷰어");
        String created = client().post()
                .uri("/api/places/3/reviews")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"rating":5,"content":"혼자 창가석이 편했어요.","wentAlone":true}
                        """)
                .retrieve()
                .body(String.class);
        assertThat(created).contains("혼자 창가석이 편했어요").contains("리뷰어");
    }
}
