package com.example.demo.manner;

import com.example.demo.companion.ApplicationStatus;
import com.example.demo.companion.CompanionApplicationRepository;
import com.example.demo.companion.CompanionPost;
import com.example.demo.companion.CompanionPostRepository;
import com.example.demo.manner.dto.MannerSummary;
import com.example.demo.manner.dto.RatingRequest;
import com.example.demo.manner.dto.RatingTargetResponse;
import com.example.demo.member.Member;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MannerService {

    private final MannerRatingRepository ratingRepository;
    private final CompanionPostRepository postRepository;
    private final CompanionApplicationRepository applicationRepository;

    public MannerService(MannerRatingRepository ratingRepository,
                         CompanionPostRepository postRepository,
                         CompanionApplicationRepository applicationRepository) {
        this.ratingRepository = ratingRepository;
        this.postRepository = postRepository;
        this.applicationRepository = applicationRepository;
    }

    public MannerSummary summary(Long memberId) {
        return summaries(List.of(memberId)).getOrDefault(memberId, MannerSummary.EMPTY);
    }

    public Map<Long, MannerSummary> summaries(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<MannerRating>> byTarget = ratingRepository.findByTargetIdIn(memberIds).stream()
                .collect(Collectors.groupingBy(MannerRating::getTargetId));
        Map<Long, MannerSummary> result = new HashMap<>();
        byTarget.forEach((id, ratings) -> result.put(id, MannerSummary.of(ratings)));
        return result;
    }

    /** 함께한 동행 중 내가 평가할 수 있는 사람들. 일정이 시작되기 전이거나 참여자가 아니면 비어 있습니다. */
    public List<RatingTargetResponse> targets(Long postId, Member viewer) {
        CompanionPost post = findPost(postId);
        Map<Long, String> participants = participants(post);
        if (!started(post) || !participants.containsKey(viewer.getId())) {
            return List.of();
        }
        Set<Long> rated = ratingRepository.findByPostIdAndRaterId(postId, viewer.getId()).stream()
                .map(MannerRating::getTargetId)
                .collect(Collectors.toSet());
        return participants.entrySet().stream()
                .filter(e -> !e.getKey().equals(viewer.getId()))
                .map(e -> new RatingTargetResponse(e.getKey(), e.getValue(), rated.contains(e.getKey())))
                .toList();
    }

    @Transactional
    public void rate(Long postId, Member rater, RatingRequest request) {
        CompanionPost post = findPost(postId);
        if (!started(post)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "여행이 시작된 뒤에 평가할 수 있어요.");
        }
        if (rater.getId().equals(request.targetId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "나 자신은 평가할 수 없어요.");
        }
        Map<Long, String> participants = participants(post);
        if (!participants.containsKey(rater.getId()) || !participants.containsKey(request.targetId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "함께한 동행끼리만 평가할 수 있어요.");
        }
        if (ratingRepository.existsByPostIdAndRaterIdAndTargetId(postId, rater.getId(), request.targetId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 평가를 남겼어요.");
        }
        MannerRating rating = new MannerRating();
        rating.setPostId(postId);
        rating.setRaterId(rater.getId());
        rating.setTargetId(request.targetId());
        boolean attended = request.attended() == null || request.attended();
        rating.setAttended(attended);
        List<MannerTag> tags;
        if (attended) {
            rating.setScore(request.score());
            tags = request.tags() == null ? List.of() : request.tags().stream().distinct().toList();
        } else {
            rating.setScore(1);
            tags = List.of(MannerTag.NO_SHOW);
        }
        rating.setTags(tags.stream().map(Enum::name).collect(Collectors.joining(",")));
        rating.setCreatedAt(Instant.now());
        ratingRepository.save(rating);
    }

    /** 작성자 + 수락된 신청자 (회원 id → 닉네임) */
    private Map<Long, String> participants(CompanionPost post) {
        Map<Long, String> participants = new LinkedHashMap<>();
        if (post.getMemberId() != null) {
            participants.put(post.getMemberId(), post.getNickname());
        }
        applicationRepository.findByPostIdAndStatus(post.getId(), ApplicationStatus.ACCEPTED).stream()
                .filter(a -> a.getMemberId() != null)
                .forEach(a -> participants.putIfAbsent(a.getMemberId(), a.getNickname()));
        return participants;
    }

    private static boolean started(CompanionPost post) {
        return !post.getStartDate().isAfter(LocalDate.now());
    }

    private CompanionPost findPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없어요."));
    }
}
