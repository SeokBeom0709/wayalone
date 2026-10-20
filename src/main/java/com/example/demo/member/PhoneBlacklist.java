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

/**
 * 영구 차단된 사람의 휴대폰 번호 해시. 회원 행과 따로 보관해서, 계정을 비식별화하거나 정리하더라도
 * 같은 번호로 다시 가입하는 것을 계속 막습니다. (번호 원문은 저장하지 않습니다.)
 */
@Entity
@Table(name = "phone_blacklist")
@Getter
@Setter
@NoArgsConstructor
public class PhoneBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String phoneHash;

    @Column(length = 200)
    private String reason;

    private Long sourceMemberId;

    private Long createdById;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
