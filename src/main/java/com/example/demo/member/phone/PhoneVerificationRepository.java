package com.example.demo.member.phone;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

    Optional<PhoneVerification> findTopByPhoneHashOrderByCreatedAtDesc(String phoneHash);

    long countByPhoneHashAndCreatedAtAfter(String phoneHash, Instant since);

    @Modifying
    @Query("delete from PhoneVerification v where v.createdAt < :before")
    void deleteOlderThan(Instant before);

    /** 틀린 횟수를 DB 에서 직접 올립니다. 한도에 도달했으면 0행이 갱신됩니다. */
    @Modifying
    @Query("update PhoneVerification v set v.attempts = v.attempts + 1 where v.id = :id and v.attempts < :max")
    int incrementAttempts(@Param("id") Long id, @Param("max") int max);

    @Query("select v.attempts from PhoneVerification v where v.id = :id")
    int attemptsOf(@Param("id") Long id);

    /** 아직 인증되지 않은 기록만 인증 처리합니다. 동시에 여러 요청이 와도 1행만 갱신됩니다. */
    @Modifying
    @Query("""
            update PhoneVerification v
               set v.verifiedAt = :now, v.tokenHash = :tokenHash, v.tokenExpiresAt = :tokenExpiresAt
             where v.id = :id and v.verifiedAt is null
            """)
    int markVerified(@Param("id") Long id, @Param("now") Instant now,
                     @Param("tokenHash") String tokenHash, @Param("tokenExpiresAt") Instant tokenExpiresAt);

    /** 토큰을 사용 처리합니다. 번호가 같고, 만료 전이고, 아직 안 쓴 경우에만 1행이 갱신됩니다. */
    @Modifying
    @Query("""
            update PhoneVerification v set v.usedAt = :now
             where v.tokenHash = :tokenHash and v.phoneHash = :phoneHash
               and v.usedAt is null and v.tokenExpiresAt > :now
            """)
    int claimToken(@Param("tokenHash") String tokenHash, @Param("phoneHash") String phoneHash, @Param("now") Instant now);
}
