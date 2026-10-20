package com.example.demo.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 회원 탈퇴 기록. 개인정보는 담지 않고, 언제 무엇을 정리했는지만 남깁니다. */
@Entity
@Table(name = "member_withdrawals")
@Getter
@Setter
@NoArgsConstructor
public class MemberWithdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long memberId;

    /** 탈퇴 사유(선택, 회원이 직접 입력) */
    @Column(length = 200)
    private String reason;

    /** 탈퇴하면서 삭제 처리된 내 모집글 수 */
    private int deletedPosts;

    /** 탈퇴하면서 취소된, 아직 답을 받지 못한 신청 수 */
    private int canceledApplications;

    @Column(nullable = false)
    private Instant withdrawnAt = Instant.now();
}
