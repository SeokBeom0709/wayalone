package com.example.demo.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberWithdrawalRepository extends JpaRepository<MemberWithdrawal, Long> {

    Optional<MemberWithdrawal> findByMemberId(Long memberId);
}
