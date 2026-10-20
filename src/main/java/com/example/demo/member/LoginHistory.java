package com.example.demo.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 로그인 시도 기록(성공/실패). 계정 도용 추적과 신고 대응에 씁니다.
 * IP 같은 접속 정보가 담겨 있어 보관 기간(app.log.login-retention-days)이 지나면 삭제합니다.
 */
@Entity
@Table(name = "login_history", indexes = {
        @Index(name = "idx_login_history_member", columnList = "memberId, createdAt"),
        @Index(name = "idx_login_history_created", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 없는 이메일로 시도했다면 비어 있습니다. */
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private LoginResult result;

    @Column(length = 45)
    private String ip;

    @Column(length = 200)
    private String userAgent;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
