package com.example.demo.place;

import com.example.demo.place.dto.LocationResponse;
import com.example.demo.place.dto.NearbyPlaceResponse;
import com.example.demo.place.dto.NearbyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 카카오 로컬 API 의 카테고리 검색으로 주변 음식점을 찾습니다. 키는 서버에만 둡니다. */
@Component
public class KakaoLocalClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoLocalClient.class);
    private static final String RESTAURANT_CATEGORY = "FD6";
    private static final int PAGE_SIZE = 15;
    private static final int MAX_PAGE = 45;
    private static final int LOCATION_SIZE = 5;

    private final String restKey;
    private final RestClient client;

    public KakaoLocalClient(@Value("${kakao.rest-key:}") String restKey) {
        this.restKey = restKey;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.client = RestClient.builder()
                .baseUrl("https://dapi.kakao.com")
                .requestFactory(factory)
                .build();
    }

    public NearbyResponse nearbyRestaurants(double lat, double lng, int radius, int page) {
        Map<String, Object> body = get(uri -> uri.path("/v2/local/search/category.json")
                        .queryParam("category_group_code", RESTAURANT_CATEGORY)
                        .queryParam("x", lng)
                        .queryParam("y", lat)
                        .queryParam("radius", radius)
                        .queryParam("sort", "distance")
                        .queryParam("size", PAGE_SIZE)
                        .queryParam("page", Math.min(page, MAX_PAGE))
                        .build(),
                "주변 식당을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
        if (body == null) {
            return new NearbyResponse(List.of(), true);
        }

        List<NearbyPlaceResponse> places = asList(body.get("documents")).stream()
                .map(KakaoLocalClient::toPlace)
                .toList();
        Map<?, ?> meta = body.get("meta") instanceof Map<?, ?> m ? m : Map.of();
        boolean end = !Boolean.FALSE.equals(meta.get("is_end")) || page >= MAX_PAGE;
        return new NearbyResponse(places, end);
    }

    /** "성수역", "강남 교보문고" 같은 이름으로 기준 위치 후보를 찾습니다. */
    public List<LocationResponse> searchLocations(String query) {
        Map<String, Object> body = get(uri -> uri.path("/v2/local/search/keyword.json")
                        .queryParam("query", query)
                        .queryParam("size", LOCATION_SIZE)
                        .build(),
                "위치를 찾지 못했어요. 잠시 후 다시 시도해 주세요.");
        if (body == null) {
            return List.of();
        }
        return asList(body.get("documents")).stream()
                .map(doc -> {
                    String road = text(doc, "road_address_name");
                    return new LocationResponse(
                            text(doc, "place_name"),
                            road.isBlank() ? text(doc, "address_name") : road,
                            parseDouble(text(doc, "y")),
                            parseDouble(text(doc, "x")));
                })
                .toList();
    }

    private Map<String, Object> get(Function<UriBuilder, URI> uri, String failMessage) {
        if (restKey == null || restKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "주변 검색이 아직 설정되지 않았어요.");
        }
        try {
            return client.get()
                    .uri(uri)
                    .header("Authorization", "KakaoAK " + restKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (RestClientResponseException e) {
            log.warn("Kakao local API failed: status={} body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, failMessage);
        } catch (RestClientException e) {
            log.warn("Kakao local API unreachable: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, failMessage);
        }
    }

    private static NearbyPlaceResponse toPlace(Map<?, ?> doc) {
        String road = text(doc, "road_address_name");
        return new NearbyPlaceResponse(
                text(doc, "id"),
                text(doc, "place_name"),
                lastCategory(text(doc, "category_name")),
                road.isBlank() ? text(doc, "address_name") : road,
                text(doc, "phone"),
                parseInt(text(doc, "distance")),
                parseDouble(text(doc, "y")),
                parseDouble(text(doc, "x")),
                text(doc, "place_url")
        );
    }

    @SuppressWarnings("unchecked")
    private static List<Map<?, ?>> asList(Object value) {
        return value instanceof List<?> list ? (List<Map<?, ?>>) list : List.of();
    }

    private static String text(Map<?, ?> doc, String key) {
        Object value = doc.get(key);
        return value == null ? "" : value.toString();
    }

    /** "음식점 > 한식 > 국수" 처럼 오는 값을 가장 구체적인 분류만 남깁니다. */
    private static String lastCategory(String category) {
        int idx = category.lastIndexOf('>');
        return idx < 0 ? category.trim() : category.substring(idx + 1).trim();
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
