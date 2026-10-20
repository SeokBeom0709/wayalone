package com.example.demo.member;

import com.example.demo.member.dto.AuthResponse;
import com.example.demo.member.dto.LoginRequest;
import com.example.demo.member.dto.MemberResponse;
import com.example.demo.member.dto.PasswordChangeRequest;
import com.example.demo.member.dto.SignupRequest;
import com.example.demo.member.phone.PhoneVerificationService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private static final Duration TOKEN_TTL = Duration.ofDays(30);
    private static final List<String> RESERVED_NICKNAME_WORDS = List.of("운영자", "관리자", "운영진", "admin", "wayalone", "탈퇴한회원");
    private static final DateTimeFormatter UNTIL_FORMAT =
            DateTimeFormatter.ofPattern("M월 d일 HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    private final MemberRepository memberRepository;
    private final AuthTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter limiter;
    private final PhoneVerificationService phoneVerificationService;
    private final LoginHistoryRepository loginHistoryRepository;
    private final PhoneBlacklistRepository blacklistRepository;
    private final SecureRandom random = new SecureRandom();
    /** 없는 이메일로 로그인할 때도 같은 시간이 걸리도록 비교용으로 쓰는 해시 */
    private final String dummyHash;

    public AuthService(MemberRepository memberRepository,
                       AuthTokenRepository tokenRepository,
                       PasswordEncoder passwordEncoder,
                       LoginAttemptLimiter limiter,
                       PhoneVerificationService phoneVerificationService,
                       LoginHistoryRepository loginHistoryRepository,
                       PhoneBlacklistRepository blacklistRepository) {
        this.memberRepository = memberRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.limiter = limiter;
        this.phoneVerificationService = phoneVerificationService;
        this.loginHistoryRepository = loginHistoryRepository;
        this.blacklistRepository = blacklistRepository;
        this.dummyHash = passwordEncoder.encode("not-a-real-password-1");
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalize(request.email());
        String nickname = request.nickname().trim();
        if (memberRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일이에요.");
        }
        if (isReservedNickname(nickname)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "운영진으로 오해할 수 있는 닉네임은 쓸 수 없어요.");
        }
        if (memberRepository.existsByNickname(nickname)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 쓰고 있는 닉네임이에요.");
        }
        String phoneHash = phoneVerificationService.consume(request.phone(), request.phoneVerificationToken());
        // 차단 사실을 알려 주지 않도록, 가입된 번호와 같은 안내 문구를 씁니다.
        if (memberRepository.existsByPhoneHash(phoneHash) || blacklistRepository.existsByPhoneHash(phoneHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 휴대폰 번호예요.");
        }
        Instant now = Instant.now();
        Member member = new Member();
        member.setEmail(email);
        member.setNickname(nickname);
        member.setPasswordHash(passwordEncoder.encode(request.password()));
        member.setPhoneHash(phoneHash);
        member.setPhoneVerifiedAt(now);
        member.setCreatedAt(now);
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 정보예요.");
        }
        return issue(member);
    }

    /** 실패해도 로그인 기록은 남아야 하므로 예외에 롤백하지 않습니다. (실패 경로에서는 다른 쓰기가 없습니다.) */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthResponse login(LoginRequest request, ClientInfo client) {
        String email = normalize(request.email());
        Member member = memberRepository.findByEmail(email).orElse(null);
        Long memberId = member == null ? null : member.getId();
        try {
            limiter.check(email);
        } catch (ResponseStatusException e) {
            record(memberId, LoginResult.FAIL_LOCKED, client);
            throw e;
        }
        boolean matches = passwordEncoder.matches(
                request.password(), member == null ? dummyHash : member.getPasswordHash());
        if (member == null || !matches) {
            limiter.fail(email);
            record(memberId, LoginResult.FAIL_CREDENTIALS, client);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않아요.");
        }
        limiter.reset(email);
        if (member.isBlocked(Instant.now())) {
            record(memberId, LoginResult.FAIL_BLOCKED, client);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, blockedMessage(member));
        }
        record(memberId, LoginResult.SUCCESS, client);
        return issue(member);
    }

    /** 내 계정의 최근 로그인 기록(성공과 실패). 모르는 시도가 있었는지 스스로 확인할 수 있습니다. */
    public List<LoginHistory> recentLogins(Member member) {
        return loginHistoryRepository.findTop10ByMemberIdOrderByIdDesc(member.getId());
    }

    private void record(Long memberId, LoginResult result, ClientInfo client) {
        LoginHistory history = new LoginHistory();
        history.setMemberId(memberId);
        history.setResult(result);
        history.setIp(client == null ? null : client.ip());
        history.setUserAgent(client == null ? null : client.userAgent());
        history.setCreatedAt(Instant.now());
        loginHistoryRepository.save(history);
    }

    @Transactional
    public AuthResponse changePassword(Member current, PasswordChangeRequest request) {
        Member member = memberRepository.findById(current.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."));
        if (passwordEncoder.matches(request.newPassword(), member.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지금과 다른 비밀번호를 입력해 주세요.");
        }
        member.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        memberRepository.save(member);
        // 비밀번호를 바꾸면 다른 기기의 로그인은 모두 풀고, 이 기기용 새 토큰만 발급합니다.
        tokenRepository.deleteAllByMember(member.getId());
        return issue(member);
    }

    @Transactional
    public Optional<Member> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Optional<AuthToken> found = tokenRepository.findWithMember(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AuthToken token = found.get();
        Instant now = Instant.now();
        if (token.getExpiresAt().isBefore(now)) {
            tokenRepository.delete(token);
            return Optional.empty();
        }
        if (token.getMember().isBlocked(now)) {
            return Optional.empty();
        }
        return Optional.of(token.getMember());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            tokenRepository.deleteByHash(hash(rawToken));
        }
    }

    private AuthResponse issue(Member member) {
        Instant now = Instant.now();
        tokenRepository.deleteExpired(now);

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        AuthToken token = new AuthToken();
        token.setTokenHash(hash(raw));
        token.setMember(member);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(TOKEN_TTL));
        tokenRepository.save(token);
        return new AuthResponse(raw, MemberResponse.from(member));
    }

    private static boolean isReservedNickname(String nickname) {
        String lower = nickname.toLowerCase(Locale.ROOT);
        return RESERVED_NICKNAME_WORDS.stream().anyMatch(lower::contains);
    }

    static String blockedMessage(Member member) {
        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            return "탈퇴한 계정이에요.";
        }
        String reason = member.getStatusReason() == null ? "" : " (사유: " + member.getStatusReason() + ")";
        if (member.getStatus() == MemberStatus.BANNED) {
            return "이용이 영구 제한된 계정이에요." + reason;
        }
        String until = member.getSuspendedUntil() == null ? "별도 안내 전"
                : UNTIL_FORMAT.format(member.getSuspendedUntil()) + "까지";
        return until + " 이용이 제한된 계정이에요." + reason;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
