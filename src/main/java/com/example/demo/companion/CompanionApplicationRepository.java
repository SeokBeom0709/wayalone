package com.example.demo.companion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CompanionApplicationRepository extends JpaRepository<CompanionApplication, Long> {

    List<CompanionApplication> findByPostIdOrderByCreatedAtDesc(Long postId);

    List<CompanionApplication> findByPostIdAndMemberIdOrderByCreatedAtDesc(Long postId, Long memberId);

    List<CompanionApplication> findByPostIdAndStatus(Long postId, ApplicationStatus status);

    List<CompanionApplication> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    Optional<CompanionApplication> findByIdAndPostId(Long id, Long postId);

    boolean existsByPostIdAndMemberId(Long postId, Long memberId);

    long countByPostId(Long postId);

    long countByPostIdAndStatus(Long postId, ApplicationStatus status);

    @Query("SELECT a.createdAt FROM CompanionApplication a WHERE a.createdAt >= :since")
    List<Instant> createdSince(@Param("since") Instant since);

    /** 탈퇴하는 회원이 아직 답을 받지 못한 신청은 취소(삭제)합니다. */
    @Modifying
    @Query("DELETE FROM CompanionApplication a WHERE a.memberId = :memberId AND (a.status IS NULL OR a.status = com.example.demo.companion.ApplicationStatus.PENDING)")
    int deletePendingByMember(@Param("memberId") Long memberId);

    @Modifying
    @Query("UPDATE CompanionApplication a SET a.nickname = :nickname WHERE a.memberId = :memberId")
    void renameApplicant(@Param("memberId") Long memberId, @Param("nickname") String nickname);
}
