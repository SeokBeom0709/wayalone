package com.example.demo.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false, unique = true, length = 20)
    private String nickname;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    /** 휴대폰 번호 원문은 저장하지 않고, 중복 가입 확인용 HMAC 값만 둡니다. */
    @Column(unique = true, length = 64)
    private String phoneHash;

    private Instant phoneVerifiedAt;

    // MySQL/H2 의 네이티브 ENUM 대신 VARCHAR 로 둡니다. (ENUM 은 값이 늘 때마다 ALTER 가 필요해 운영에서 번거롭습니다.)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 10)
    private MemberRole role = MemberRole.USER;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 12)
    private MemberStatus status = MemberStatus.ACTIVE;

    private Instant suspendedUntil;

    @Column(length = 200)
    private String statusReason;

    private Instant createdAt = Instant.now();

    /** 탈퇴한 시각. 탈퇴하지 않았으면 비어 있습니다. */
    private Instant withdrawnAt;

    public boolean isPhoneVerified() {
        return phoneHash != null;
    }

    public boolean isAdmin() {
        return role == MemberRole.ADMIN;
    }

    public boolean isWithdrawn() {
        return status == MemberStatus.WITHDRAWN;
    }

    public boolean isBlocked(Instant now) {
        if (status == MemberStatus.BANNED || status == MemberStatus.WITHDRAWN) {
            return true;
        }
        return status == MemberStatus.SUSPENDED && (suspendedUntil == null || suspendedUntil.isAfter(now));
    }
}
