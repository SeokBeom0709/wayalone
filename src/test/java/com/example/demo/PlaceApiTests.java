package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

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
