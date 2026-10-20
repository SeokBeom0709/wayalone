package com.example.demo.config;

import com.example.demo.member.Member;
import com.example.demo.member.MemberRepository;
import com.example.demo.member.MemberRole;
import com.example.demo.member.MemberStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

/**
 * 시작할 때 app.admin.email 계정을 관리자로 만들거나 승격합니다.
 * 이미 있는 계정의 비밀번호는 건드리지 않습니다.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final String ADMIN_NICKNAME = "운영자";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(MemberRepository memberRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.admin.email:}") String email,
                          @Value("${app.admin.password:}") String password) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        memberRepository.fillMissingRoles();
        memberRepository.fillMissingStatuses();
        if (email.isBlank()) {
            return;
        }
        Member admin = memberRepository.findByEmail(email).orElse(null);
        if (admin == null) {
            if (password.isBlank()) {
                log.warn("ADMIN_EMAIL 은 있지만 ADMIN_PASSWORD 가 없어 관리자 계정을 만들지 않았어요.");
                return;
            }
            admin = new Member();
            admin.setEmail(email);
            admin.setNickname(memberRepository.existsByNickname(ADMIN_NICKNAME) ? ADMIN_NICKNAME + "1" : ADMIN_NICKNAME);
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setCreatedAt(Instant.now());
        }
        admin.setRole(MemberRole.ADMIN);
        admin.setStatus(MemberStatus.ACTIVE);
        memberRepository.save(admin);
        log.info("관리자 계정 준비 완료: {}", email);
    }
}
