package com.example.demo.voc;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface VocRepository extends JpaRepository<Voc, Long> {

    List<Voc> findByReporterIdOrderByCreatedAtDesc(Long reporterId);

    List<Voc> findByTargetMemberIdOrderByCreatedAtDesc(Long targetMemberId);

    List<Voc> findAllByOrderByCreatedAtDesc();

    List<Voc> findByStatusOrderByCreatedAtDesc(VocStatus status);

    long countByStatus(VocStatus status);

    long countByReporterIdAndStatus(Long reporterId, VocStatus status);

    @Query("SELECT v.createdAt FROM Voc v WHERE v.createdAt >= :since")
    List<Instant> createdSince(@Param("since") Instant since);

    /** 탈퇴한 회원이 남긴 문의는 처리 이력을 위해 두고 닉네임만 비식별 값으로 바꿉니다. */
    @Modifying
    @Query("UPDATE Voc v SET v.reporterNickname = :nickname WHERE v.reporterId = :memberId")
    void renameReporter(@Param("memberId") Long memberId, @Param("nickname") String nickname);

    /** [대상 회원 id, 신고 수] */
    @Query("""
            SELECT v.targetMemberId, COUNT(v) FROM Voc v
            WHERE v.targetMemberId IN :ids AND v.type IN :types
            GROUP BY v.targetMemberId
            """)
    List<Object[]> countReportsByTarget(@Param("ids") Collection<Long> ids, @Param("types") Collection<VocType> types);
}
