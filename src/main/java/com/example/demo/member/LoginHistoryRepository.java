package com.example.demo.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    List<LoginHistory> findTop10ByMemberIdOrderByIdDesc(Long memberId);

    List<LoginHistory> findTop20ByMemberIdOrderByIdDesc(Long memberId);

    @Modifying
    @Query("DELETE FROM LoginHistory h WHERE h.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
