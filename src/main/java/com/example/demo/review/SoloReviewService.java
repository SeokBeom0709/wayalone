package com.example.demo.review;

import com.example.demo.member.Member;
import com.example.demo.place.Place;
import com.example.demo.place.PlaceService;
import com.example.demo.review.dto.ReviewRequest;
import com.example.demo.review.dto.ReviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SoloReviewService {

    private final SoloReviewRepository reviewRepository;
    private final PlaceService placeService;

    public SoloReviewService(SoloReviewRepository reviewRepository, PlaceService placeService) {
        this.reviewRepository = reviewRepository;
        this.placeService = placeService;
    }

    public List<ReviewResponse> list(Long placeId) {
        placeService.get(placeId);
        return reviewRepository.findByPlaceIdOrderByCreatedAtDesc(placeId)
                .stream()
                .map(ReviewResponse::from)
                .toList();
    }

    @Transactional
    public ReviewResponse create(Long placeId, ReviewRequest request, Member member) {
        Place place = placeService.getEntity(placeId);
        SoloReview review = new SoloReview();
        review.setPlace(place);
        review.setMemberId(member.getId());
        review.setNickname(member.getNickname());
        review.setRating(request.rating());
        review.setContent(request.content().trim());
        review.setWentAlone(request.wentAloneOrDefault());
        review.setCreatedAt(Instant.now());
        return ReviewResponse.from(reviewRepository.save(review));
    }
}
