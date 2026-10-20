package com.example.demo.member.phone;

import com.example.demo.member.Member;
import com.example.demo.member.MemberRepository;
import com.example.demo.member.PhoneBlacklistRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 휴대폰 문자 인증. 흐름: 인증번호 발송 → 번호 확인(1회용 토큰 발급) → 회원가입/번호 등록에서 토큰 사용.
 */
@Service
public class PhoneVerificationService {

    private static final Pattern MOBILE = Pattern.compile("^01[016789]\\d{7,8}$");
    private static final Duration CODE_TTL = Duration.ofMinutes(3);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final Duration DAY = Duration.ofDays(1);
    private static final int MAX_ATTEMPTS = 5;
    private static final int DAILY_LIMIT = 5;

    public record SendResult(long expiresIn, String devCode) {
    }

    private final PhoneVerificationRepository repository;
    private final MemberRepository memberRepository;
    private final PhoneBlacklistRepository blacklistRepository;
    private final SmsSender smsSender;
    private final SmsRateLimiter rateLimiter;
    private final byte[] pepper;
    private final boolean exposeDevCode;
    private final SecureRandom random = new SecureRandom();

    public PhoneVerificationService(PhoneVerificationRepository repository,
                                    MemberRepository memberRepository,
                                    PhoneBlacklistRepository blacklistRepository,
                                    SmsSender smsSender,
                                    SmsRateLimiter rateLimiter,
                                    @Value("${app.phone.pepper}") String pepper,
                                    @Value("${app.sms.expose-dev-code:false}") boolean exposeDevCode) {
        this.repository = repository;
        this.memberRepository = memberRepository;
        this.blacklistRepository = blacklistRepository;
        this.smsSender = smsSender;
        this.rateLimiter = rateLimiter;
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
        this.exposeDevCode = exposeDevCode;
    }

    @Transactional
    public SendResult send(String rawPhone, String clientIp) {
        String phone = normalize(rawPhone);
        String phoneHash = phoneHash(phone);
        if (memberRepository.existsByPhoneHash(phoneHash) || blacklistRepository.existsByPhoneHash(phoneHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 휴대폰 번호예요.");
        }
        Instant now = Instant.now();
        repository.findTopByPhoneHashOrderByCreatedAtDesc(phoneHash).ifPresent(last -> {
            if (last.getCreatedAt().plus(RESEND_COOLDOWN).isAfter(now)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "인증번호는 1분에 한 번만 받을 수 있어요.");
            }
        });
        if (repository.countByPhoneHashAndCreatedAtAfter(phoneHash, now.minus(DAY)) >= DAILY_LIMIT) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "오늘은 인증번호를 더 받을 수 없어요. 내일 다시 시도해 주세요.");
        }
        rateLimiter.acquire(clientIp);
        repository.deleteOlderThan(now.minus(DAY.multipliedBy(2)));

        String code = "%06d".formatted(random.nextInt(1_000_000));
        PhoneVerification verification = new PhoneVerification();
        verification.setPhoneHash(phoneHash);
        verification.setCodeHash(sha256(phoneHash + ":" + code));
        verification.setExpiresAt(now.plus(CODE_TTL));
        verification.setCreatedAt(now);
        repository.save(verification);

        smsSender.send(phone, "[WAYALONE] 인증번호 [%s] 3분 안에 입력해 주세요.".formatted(code));
        return new SendResult(CODE_TTL.toSeconds(), smsSender.isFake() && exposeDevCode ? code : null);
    }

    /** 틀린 횟수는 실패해도 저장돼야 하므로 예외에 롤백하지 않습니다. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public String verify(String rawPhone, String code) {
        String phoneHash = phoneHash(normalize(rawPhone));
        PhoneVerification verification = repository.findTopByPhoneHashOrderByCreatedAtDesc(phoneHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증번호를 먼저 받아 주세요."));
        Instant now = Instant.now();
        if (verification.getVerifiedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 사용한 인증번호예요. 다시 받아 주세요.");
        }
        if (verification.getExpiresAt().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증번호가 만료됐어요. 다시 받아 주세요.");
        }
        if (verification.getAttempts() >= MAX_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "인증번호를 너무 많이 틀렸어요. 다시 받아 주세요.");
        }
        boolean matches = MessageDigest.isEqual(
                verification.getCodeHash().getBytes(StandardCharsets.UTF_8),
                sha256(phoneHash + ":" + code).getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            verification.setAttempts(verification.getAttempts() + 1);
            repository.save(verification);
            int remaining = MAX_ATTEMPTS - verification.getAttempts();
            throw remaining > 0
                    ? new ResponseStatusException(HttpStatus.BAD_REQUEST, "인증번호가 맞지 않아요. (남은 기회 %d번)".formatted(remaining))
                    : new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "인증번호를 너무 많이 틀렸어요. 다시 받아 주세요.");
        }

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        verification.setVerifiedAt(now);
        verification.setTokenHash(sha256(token));
        verification.setTokenExpiresAt(now.plus(TOKEN_TTL));
        repository.save(verification);
        return token;
    }

    /** 인증 토큰을 한 번 쓰고, 해당 번호의 해시를 돌려줍니다. 호출한 쪽 트랜잭션이 실패하면 함께 되돌려집니다. */
    @Transactional
    public String consume(String rawPhone, String token) {
        String phoneHash = phoneHash(normalize(rawPhone));
        Instant now = Instant.now();
        PhoneVerification verification = repository.findByTokenHash(sha256(token))
                .filter(v -> v.getPhoneHash().equals(phoneHash))
                .filter(v -> v.getUsedAt() == null && v.getTokenExpiresAt() != null && v.getTokenExpiresAt().isAfter(now))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "휴대폰 인증을 다시 해 주세요."));
        verification.setUsedAt(now);
        repository.save(verification);
        return phoneHash;
    }

    /** 휴대폰 인증 없이 가입했던 기존 회원이 번호를 등록합니다. */
    @Transactional
    public Member attach(Member current, String rawPhone, String token) {
        Member member = memberRepository.findById(current.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."));
        if (member.isPhoneVerified()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 휴대폰 인증을 마쳤어요.");
        }
        String phoneHash = consume(rawPhone, token);
        if (memberRepository.existsByPhoneHash(phoneHash) || blacklistRepository.existsByPhoneHash(phoneHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 휴대폰 번호예요.");
        }
        member.setPhoneHash(phoneHash);
        member.setPhoneVerifiedAt(Instant.now());
        return memberRepository.save(member);
    }

    private static String normalize(String rawPhone) {
        String digits = rawPhone == null ? "" : rawPhone.replaceAll("\\D", "");
        if (!MOBILE.matcher(digits).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "휴대폰 번호를 확인해 주세요. (예: 010-1234-5678)");
        }
        return digits;
    }

    private String phoneHash(String phone) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(phone.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
