package com.example.demo.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SoloReviewRepository extends JpaRepository<SoloReview, Long> {

    List<SoloReview> findByPlaceIdOrderByCreatedAtDesc(Long placeId);

    long countByPlaceId(Long placeId);

    @Query("SELECT AVG(r.rating) FROM SoloReview r WHERE r.place.id = :placeId")
    Double averageRating(@Param("placeId") Long placeId);

    /** 탈퇴한 회원의 후기는 남기되 닉네임만 비식별 값으로 바꿉니다. */
    @Modifying
    @Query("UPDATE SoloReview r SET r.nickname = :nickname WHERE r.memberId = :memberId")
    void renameAuthor(@Param("memberId") Long memberId, @Param("nickname") String nickname);
}
