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

    /** 여러 장소의 평균 별점과 후기 수를 한 번에 가져옵니다. 행: [placeId, 평균, 개수] (후기가 없는 장소는 나오지 않음) */
    @Query("SELECT r.place.id, AVG(r.rating), COUNT(r) FROM SoloReview r WHERE r.place.id IN :placeIds GROUP BY r.place.id")
    List<Object[]> statsByPlaceIds(@Param("placeIds") java.util.Collection<Long> placeIds);

    /** 탈퇴한 회원의 후기는 남기되 닉네임만 비식별 값으로 바꿉니다. */
    @Modifying
    @Query("UPDATE SoloReview r SET r.nickname = :nickname WHERE r.memberId = :memberId")
    void renameAuthor(@Param("memberId") Long memberId, @Param("nickname") String nickname);
}
