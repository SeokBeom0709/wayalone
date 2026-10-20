package com.example.demo.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 회원이 다른 회원을 차단한 기록. 차단하면 서로의 모집글이 보이지 않고 신청할 수 없습니다. */
@Entity
@Table(name = "member_blocks", uniqueConstraints = @UniqueConstraint(
        name = "uk_member_blocks_pair", columnNames = {"blockerId", "blockedId"}))
@Getter
@Setter
@NoArgsConstructor
public class MemberBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long blockerId;

    @Column(nullable = false)
    private Long blockedId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
