package com.example.demo.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    @Query("SELECT t FROM AuthToken t JOIN FETCH t.member WHERE t.tokenHash = :hash")
    Optional<AuthToken> findWithMember(@Param("hash") String hash);

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.tokenHash = :hash")
    void deleteByHash(@Param("hash") String hash);

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.member.id = :memberId")
    void deleteAllByMember(@Param("memberId") Long memberId);

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
