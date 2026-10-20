package com.example.demo.member.phone;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

    Optional<PhoneVerification> findTopByPhoneHashOrderByCreatedAtDesc(String phoneHash);

    Optional<PhoneVerification> findByTokenHash(String tokenHash);

    long countByPhoneHashAndCreatedAtAfter(String phoneHash, Instant since);

    @Modifying
    @Query("delete from PhoneVerification v where v.createdAt < :before")
    void deleteOlderThan(Instant before);
}
