package com.example.demo.place;

import com.example.demo.place.dto.PlaceResponse;
import com.example.demo.review.SoloReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PlaceService {

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
        return placeRepository.search(category, foodOnly, spotOnly, areaFilter, query, soloDining)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public PlaceResponse get(Long id) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "장소를 찾을 수 없어요."));
        return toResponse(place);
    }

    public Place getEntity(Long id) {
        return placeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "장소를 찾을 수 없어요."));
    }

    private PlaceResponse toResponse(Place place) {
        Double avg = reviewRepository.averageRating(place.getId());
        long count = reviewRepository.countByPlaceId(place.getId());
        return PlaceResponse.from(place, avg == null ? 0 : Math.round(avg * 10.0) / 10.0, count);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
