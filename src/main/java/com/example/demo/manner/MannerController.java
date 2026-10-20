package com.example.demo.manner;

import com.example.demo.manner.dto.MemberProfileResponse;
import com.example.demo.manner.dto.RatingRequest;
import com.example.demo.manner.dto.RatingTargetResponse;
import com.example.demo.member.LoginMember;
import com.example.demo.member.Member;
import com.example.demo.member.MemberRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class MannerController {

    private final MannerService mannerService;
    private final MemberRepository memberRepository;

    public MannerController(MannerService mannerService, MemberRepository memberRepository) {
        this.mannerService = mannerService;
        this.memberRepository = memberRepository;
    }

    @GetMapping("/api/companions/{postId}/ratings/targets")
    public List<RatingTargetResponse> targets(@PathVariable Long postId, @LoginMember Member member) {
        return mannerService.targets(postId, member);
    }

    @PostMapping("/api/companions/{postId}/ratings")
    @ResponseStatus(HttpStatus.CREATED)
    public void rate(@PathVariable Long postId, @LoginMember Member member,
                     @Valid @RequestBody RatingRequest request) {
        mannerService.rate(postId, member, request);
    }

    @GetMapping("/api/members/{id}/profile")
    public MemberProfileResponse profile(@PathVariable Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없어요."));
        return new MemberProfileResponse(member.getId(), member.getNickname(), member.isPhoneVerified(),
                mannerService.summary(id));
    }
}
