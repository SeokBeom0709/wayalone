package com.example.demo.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PhoneBlacklistRepository extends JpaRepository<PhoneBlacklist, Long> {

    boolean existsByPhoneHash(String phoneHash);

    List<PhoneBlacklist> findAllByOrderByIdDesc();

    @Modifying
    @Query("DELETE FROM PhoneBlacklist b WHERE b.phoneHash = :phoneHash")
    void deleteByPhoneHash(@Param("phoneHash") String phoneHash);
}
