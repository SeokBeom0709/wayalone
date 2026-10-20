package com.example.demo.member;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByPhoneHash(String phoneHash);

    long countByCreatedAtAfter(Instant since);

    long countByPhoneHashIsNotNull();

    long countByStatus(MemberStatus status);

    @Query("SELECT m.createdAt FROM Member m WHERE m.createdAt >= :since")
    List<Instant> createdSince(@Param("since") Instant since);

    @Query("""
            SELECT m FROM Member m
            WHERE (:q IS NULL
                   OR LOWER(m.email) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(m.nickname) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:status IS NULL OR m.status = :status)
            ORDER BY m.createdAt DESC
            """)
    Page<Member> adminSearch(@Param("q") String q, @Param("status") MemberStatus status, Pageable pageable);

    /** 역할/상태 컬럼이 생기기 전에 가입한 회원의 빈 값을 채웁니다. */
    @Modifying
    @Query("UPDATE Member m SET m.role = com.example.demo.member.MemberRole.USER WHERE m.role IS NULL")
    int fillMissingRoles();

    @Modifying
    @Query("UPDATE Member m SET m.status = com.example.demo.member.MemberStatus.ACTIVE WHERE m.status IS NULL")
    int fillMissingStatuses();
}
