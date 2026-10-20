package com.example.demo.member.phone;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 인증번호 1회 발송 기록. 번호와 코드는 모두 해시로만 남깁니다. */
@Entity
@Table(name = "phone_verifications", indexes = {
        @Index(name = "idx_phone_verification_phone", columnList = "phoneHash"),
        @Index(name = "idx_phone_verification_token", columnList = "tokenHash")
})
@Getter
@Setter
@NoArgsConstructor
public class PhoneVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String phoneHash;

    @Column(nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private int attempts;

    private Instant verifiedAt;

    /** 인증 성공 후 회원가입/번호 등록에 한 번 쓰는 토큰의 해시 */
    @Column(length = 64)
    private String tokenHash;

    private Instant tokenExpiresAt;

    private Instant usedAt;

    @Column(nullable = false)
    private Instant createdAt;
}
