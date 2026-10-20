package com.example.demo.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface MemberBlockRepository extends JpaRepository<MemberBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    List<MemberBlock> findByBlockerIdOrderByCreatedAtDesc(Long blockerId);

    @Modifying
    @Query("DELETE FROM MemberBlock b WHERE b.blockerId = :blockerId AND b.blockedId = :blockedId")
    int deletePair(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    /** 내가 차단했거나 나를 차단한 회원 id (모집글을 서로 숨길 때 씁니다) */
    @Query("""
            SELECT CASE WHEN b.blockerId = :me THEN b.blockedId ELSE b.blockerId END
            FROM MemberBlock b
            WHERE b.blockerId = :me OR b.blockedId = :me
            """)
    Set<Long> relatedIds(@Param("me") Long me);

    @Query("""
            SELECT COUNT(b) > 0 FROM MemberBlock b
            WHERE (b.blockerId = :a AND b.blockedId = :b) OR (b.blockerId = :b AND b.blockedId = :a)
            """)
    boolean existsEitherWay(@Param("a") Long a, @Param("b") Long b);
}
