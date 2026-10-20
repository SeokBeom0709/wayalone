package com.example.demo.review;

import com.example.demo.member.LoginMember;
import com.example.demo.member.Member;
import com.example.demo.review.dto.ReviewRequest;
import com.example.demo.review.dto.ReviewResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/places/{placeId}/reviews")
public class SoloReviewController {

    private final SoloReviewService reviewService;

    public SoloReviewController(SoloReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public List<ReviewResponse> list(@PathVariable Long placeId) {
        return reviewService.list(placeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@PathVariable Long placeId,
                                 @LoginMember Member member,
                                 @Valid @RequestBody ReviewRequest request) {
        return reviewService.create(placeId, request, member);
    }
}
