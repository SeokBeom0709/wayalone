package com.example.demo.place;

import com.example.demo.place.dto.PlaceAroundResponse;
import com.example.demo.place.dto.PlaceResponse;
import com.example.demo.review.SoloReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class PlaceService {

    private static final double EARTH_RADIUS_M = 6_371_000;
    private static final double METERS_PER_DEGREE_LAT = 111_320;
    static final int MAX_AROUND_RADIUS_M = 5_000;

    private record Stats(double average, long count) {
        static final Stats NONE = new Stats(0, 0);
    }

    private final PlaceRepository placeRepository;
    private final SoloReviewRepository reviewRepository;

    public PlaceService(PlaceRepository placeRepository, SoloReviewRepository reviewRepository) {
        this.placeRepository = placeRepository;
        this.reviewRepository = reviewRepository;
    }

    public List<PlaceResponse> search(String intent, String area, String q, SoloDining soloDining, PlaceCategory category) {
        boolean foodOnly = "food".equalsIgnoreCase(intent);
        boolean spotOnly = "spot".equalsIgnoreCase(intent);
        String areaFilter = blankToNull(area);
        String query = blankToNull(q);
        return toResponses(placeRepository.search(category, foodOnly, spotOnly, areaFilter, query, soloDining));
    }

    /** 기준 위치에서 radiusMeters 안에 있는 장소를 가까운 순으로 돌려줍니다. */
    public List<PlaceAroundResponse> around(double lat, double lng, int radiusMeters, int limit) {
        int radius = Math.max(50, Math.min(radiusMeters, MAX_AROUND_RADIUS_M));
        int cap = Math.max(1, Math.min(limit, 100));

        // 반경을 감싸는 좌표 사각형. 경도 1도의 길이는 위도에 따라 줄어듭니다.
        double dLat = radius / METERS_PER_DEGREE_LAT;
        double dLng = radius / (METERS_PER_DEGREE_LAT * Math.max(Math.cos(Math.toRadians(lat)), 0.01));
        List<Place> places = placeRepository.findWithinRadius(
                lat, lng, lat - dLat, lat + dLat, lng - dLng, lng + dLng, radius, cap);

        Map<Long, Stats> stats = statsFor(places);
        return places.stream()
                .map(place -> new PlaceAroundResponse(
                        toResponse(place, stats),
                        (int) Math.round(haversineMeters(lat, lng, place.getLatitude(), place.getLongitude()))))
                .toList();
    }

    public PlaceResponse get(Long id) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "장소를 찾을 수 없어요."));
        return toResponses(List.of(place)).get(0);
    }

    public Place getEntity(Long id) {
        return placeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "장소를 찾을 수 없어요."));
    }

    /** 평균 별점과 후기 수를 장소마다 따로 조회하지 않고, 목록 전체를 쿼리 한 번으로 가져옵니다. */
    private List<PlaceResponse> toResponses(List<Place> places) {
        Map<Long, Stats> stats = statsFor(places);
        return places.stream().map(place -> toResponse(place, stats)).toList();
    }

    private Map<Long, Stats> statsFor(Collection<Place> places) {
        Map<Long, Stats> stats = new HashMap<>();
        if (places.isEmpty()) {
            return stats;
        }
        List<Long> ids = places.stream().map(Place::getId).toList();
        for (Object[] row : reviewRepository.statsByPlaceIds(ids)) {
            double average = Math.round(((Number) row[1]).doubleValue() * 10.0) / 10.0;
            stats.put((Long) row[0], new Stats(average, ((Number) row[2]).longValue()));
        }
        return stats;
    }

    private static PlaceResponse toResponse(Place place, Map<Long, Stats> stats) {
        Stats s = stats.getOrDefault(place.getId(), Stats.NONE);
        return PlaceResponse.from(place, s.average(), s.count());
    }

    static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
