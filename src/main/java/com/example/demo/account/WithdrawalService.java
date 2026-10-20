package com.example.demo.account;

import com.example.demo.companion.CompanionApplicationRepository;
import com.example.demo.companion.CompanionPost;
import com.example.demo.companion.CompanionPostRepository;
import com.example.demo.companion.CompanionService;
import com.example.demo.companion.ContentDeletionLog;
import com.example.demo.member.AuthTokenRepository;
import com.example.demo.member.LoginAttemptLimiter;
import com.example.demo.member.Member;
import com.example.demo.member.MemberRepository;
import com.example.demo.member.MemberStatus;
import com.example.demo.member.MemberWithdrawal;
import com.example.demo.member.MemberWithdrawalRepository;
import com.example.demo.review.SoloReviewRepository;
import com.example.demo.voc.VocRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * 회원 탈퇴. 행을 지우지 않고 개인정보만 비식별화합니다.
 * <ul>
 *   <li>이메일·닉네임·비밀번호·휴대폰 번호 해시를 지우거나 의미 없는 값으로 바꿉니다.</li>
 *   <li>내 모집글은 삭제 기록을 남기고 삭제 처리하고, 답을 받지 못한 신청은 취소합니다.</li>
 *   <li>신고·평가·후기·제재 이력은 분쟁 대응을 위해 남기되 닉네임은 "탈퇴한회원N" 으로 바뀝니다.</li>
 *   <li>로그인 기록은 보관 기간이 지날 때까지 남습니다.</li>
 * </ul>
 */
@Service
public class WithdrawalService {

    private final MemberRepository memberRepository;
    private final AuthTokenRepository tokenRepository;
    private final CompanionPostRepository postRepository;
    private final CompanionApplicationRepository applicationRepository;
    private final CompanionService companionService;
    private final SoloReviewRepository reviewRepository;
    private final VocRepository vocRepository;
    private final MemberWithdrawalRepository withdrawalRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter limiter;
    private final SecureRandom random = new SecureRandom();

    public WithdrawalService(MemberRepository memberRepository, AuthTokenRepository tokenRepository,
                             CompanionPostRepository postRepository,
                             CompanionApplicationRepository applicationRepository,
                             CompanionService companionService, SoloReviewRepository reviewRepository,
                             VocRepository vocRepository, MemberWithdrawalRepository withdrawalRepository,
                             PasswordEncoder passwordEncoder, LoginAttemptLimiter limiter) {
        this.memberRepository = memberRepository;
        this.tokenRepository = tokenRepository;
        this.postRepository = postRepository;
        this.applicationRepository = applicationRepository;
        this.companionService = companionService;
        this.reviewRepository = reviewRepository;
        this.vocRepository = vocRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.passwordEncoder = passwordEncoder;
        this.limiter = limiter;
    }

    @Transactional
    public void withdraw(Member current, String password, String reason) {
        Member member = memberRepository.findById(current.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."));
        if (member.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "운영자 계정은 탈퇴할 수 없어요.");
        }
        // 로그인된 토큰만 있으면 누구나 탈퇴시킬 수 없도록 비밀번호를 한 번 더 확인하고, 틀리면 횟수를 제한합니다.
        String key = "withdraw:" + member.getId();
        limiter.check(key);
        if (!passwordEncoder.matches(password, member.getPasswordHash())) {
            limiter.fail(key);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호가 맞지 않아요.");
        }
        limiter.reset(key);

        Long id = member.getId();
        String anonymousName = "탈퇴한회원" + id;

        List<CompanionPost> posts = postRepository.findByMemberIdAndDeletedAtIsNull(id);
        posts.forEach(p -> companionService.softDelete(p, id, ContentDeletionLog.BY_WITHDRAWAL));
        int canceled = applicationRepository.deletePendingByMember(id);

        postRepository.renameAuthor(id, anonymousName);
        applicationRepository.renameApplicant(id, anonymousName);
        reviewRepository.renameAuthor(id, anonymousName);
        vocRepository.renameReporter(id, anonymousName);
        tokenRepository.deleteAllByMember(id);

        Instant now = Instant.now();
        member.setEmail("withdrawn-" + id + "@deleted.invalid");
        member.setNickname(anonymousName);
        member.setPasswordHash(passwordEncoder.encode(randomSecret()));
        member.setPhoneHash(null);
        member.setPhoneVerifiedAt(null);
        member.setStatus(MemberStatus.WITHDRAWN);
        member.setSuspendedUntil(null);
        member.setStatusReason(null);
        member.setWithdrawnAt(now);
        memberRepository.save(member);

        MemberWithdrawal record = new MemberWithdrawal();
        record.setMemberId(id);
        record.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        record.setDeletedPosts(posts.size());
        record.setCanceledApplications(canceled);
        record.setWithdrawnAt(now);
        withdrawalRepository.save(record);
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
