package com.example.demo.companion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CompanionPostRepository extends JpaRepository<CompanionPost, Long> {

    @Query("""
            SELECT p FROM CompanionPost p
            WHERE (p.hidden IS NULL OR p.hidden = FALSE)
              AND p.deletedAt IS NULL
              AND (:q IS NULL
                   OR LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(p.destination) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(p.content) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY p.startDate ASC, p.createdAt DESC
            """)
    List<CompanionPost> search(@Param("q") String q);

    List<CompanionPost> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<CompanionPost> findByMemberIdAndDeletedAtIsNull(Long memberId);

    /** 탈퇴한 회원의 닉네임을 비식별 값으로 바꿉니다. */
    @Modifying
    @Query("UPDATE CompanionPost p SET p.nickname = :nickname WHERE p.memberId = :memberId")
    void renameAuthor(@Param("memberId") Long memberId, @Param("nickname") String nickname);

    @Query("SELECT COUNT(p) FROM CompanionPost p WHERE p.hidden = TRUE")
    long countHidden();

    @Query("SELECT p.createdAt FROM CompanionPost p WHERE p.createdAt >= :since")
    List<Instant> createdSince(@Param("since") Instant since);
}
