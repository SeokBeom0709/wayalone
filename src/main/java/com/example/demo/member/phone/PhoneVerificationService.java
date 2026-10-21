package com.example.demo.member.phone;

import com.example.demo.member.Member;
import com.example.demo.member.MemberRepository;
import com.example.demo.member.PhoneBlacklistRepository;
import com.example.demo.member.phone.VerificationStore.IssueResult;
import com.example.demo.member.phone.VerificationStore.VerifyResult;
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
 * 인증번호와 토큰의 저장, 유효시간, 동시 요청 처리는 {@link VerificationStore} 가 맡습니다.
 */
@Service
public class PhoneVerificationService {

    private static final Pattern MOBILE = Pattern.compile("^01[016789]\\d{7,8}$");
    private static final Duration CODE_TTL = Duration.ofMinutes(3);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final int MAX_ATTEMPTS = 5;
    private static final int DAILY_LIMIT = 5;

    public record SendResult(long expiresIn, String devCode) {
    }

    private final VerificationStore store;
    private final MemberRepository memberRepository;
    private final PhoneBlacklistRepository blacklistRepository;
    private final SmsSender smsSender;
    private final byte[] pepper;
    private final boolean exposeDevCode;
    private final SecureRandom random = new SecureRandom();

    public PhoneVerificationService(VerificationStore store,
                                    MemberRepository memberRepository,
                                    PhoneBlacklistRepository blacklistRepository,
                                    SmsSender smsSender,
                                    @Value("${app.phone.pepper}") String pepper,
                                    @Value("${app.sms.expose-dev-code:false}") boolean exposeDevCode) {
        this.store = store;
        this.memberRepository = memberRepository;
        this.blacklistRepository = blacklistRepository;
        this.smsSender = smsSender;
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
        this.exposeDevCode = exposeDevCode;
    }

    public SendResult send(String rawPhone, String clientIp) {
        String phone = normalize(rawPhone);
        String phoneHash = phoneHash(phone);
        if (memberRepository.existsByPhoneHash(phoneHash) || blacklistRepository.existsByPhoneHash(phoneHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 휴대폰 번호예요.");
        }
        if (!store.allowIp(clientIp)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "인증번호 요청이 너무 많아요. 잠시 후 다시 시도해 주세요.");
        }

        String code = "%06d".formatted(random.nextInt(1_000_000));
        IssueResult issued = store.issue(phoneHash, codeHash(phoneHash, code), CODE_TTL, RESEND_COOLDOWN, DAILY_LIMIT);
        switch (issued) {
            case COOLDOWN -> throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "인증번호는 1분에 한 번만 받을 수 있어요.");
            case DAILY_LIMIT -> throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "오늘은 인증번호를 더 받을 수 없어요. 내일 다시 시도해 주세요.");
            case OK -> {
            }
        }

        smsSender.send(phone, "[WAYALONE] 인증번호 [%s] 3분 안에 입력해 주세요.".formatted(code));
        return new SendResult(CODE_TTL.toSeconds(), smsSender.isFake() && exposeDevCode ? code : null);
    }

    /** 맞으면 1회용 토큰을 돌려줍니다. 같은 인증번호로 동시에 여러 번 확인해도 토큰은 한 번만 발급됩니다. */
    public String verify(String rawPhone, String code) {
        String phoneHash = phoneHash(normalize(rawPhone));
        String token = newToken();
        VerifyResult result = store.verify(phoneHash, codeHash(phoneHash, code), MAX_ATTEMPTS, sha256(token), TOKEN_TTL);
        switch (result.status()) {
            case OK -> {
                return token;
            }
            case NOT_FOUND, EXPIRED -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "인증번호를 받지 않았거나 만료됐어요. 다시 받아 주세요.");
            case ALREADY_USED -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "이미 사용한 인증번호예요. 다시 받아 주세요.");
            case LOCKED -> throw tooManyWrong();
            case MISMATCH -> throw result.remaining() > 0
                    ? new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "인증번호가 맞지 않아요. (남은 기회 %d번)".formatted(result.remaining()))
                    : tooManyWrong();
            default -> throw new IllegalStateException("알 수 없는 인증 결과: " + result.status());
        }
    }

    /** 인증 토큰을 한 번 쓰고, 해당 번호의 해시를 돌려줍니다. 호출한 쪽 트랜잭션이 실패하면 함께 되돌려집니다. */
    @Transactional
    public String consume(String rawPhone, String token) {
        String phoneHash = phoneHash(normalize(rawPhone));
        if (token == null || !store.consumeToken(sha256(token), phoneHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "휴대폰 인증을 다시 해 주세요.");
        }
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

    private static ResponseStatusException tooManyWrong() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "인증번호를 너무 많이 틀렸어요. 다시 받아 주세요.");
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String codeHash(String phoneHash, String code) {
        return sha256(phoneHash + ":" + code);
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
