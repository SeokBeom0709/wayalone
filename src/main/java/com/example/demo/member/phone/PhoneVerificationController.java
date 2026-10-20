package com.example.demo.member.phone;

import com.example.demo.member.ClientInfo;
import com.example.demo.member.LoginMember;
import com.example.demo.member.Member;
import com.example.demo.member.dto.MemberResponse;
import com.example.demo.member.dto.PhoneAttachRequest;
import com.example.demo.member.dto.PhoneSendRequest;
import com.example.demo.member.dto.PhoneVerifyRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/phone")
public class PhoneVerificationController {

    private final PhoneVerificationService service;

    public PhoneVerificationController(PhoneVerificationService service) {
        this.service = service;
    }

    @PostMapping("/send")
    public PhoneVerificationService.SendResult send(@Valid @RequestBody PhoneSendRequest request,
                                                    HttpServletRequest http) {
        return service.send(request.phone(), ClientInfo.from(http).ip());
    }

    @PostMapping("/verify")
    public Map<String, String> verify(@Valid @RequestBody PhoneVerifyRequest request) {
        return Map.of("verificationToken", service.verify(request.phone(), request.code()));
    }

    @PostMapping
    public MemberResponse attach(@LoginMember Member member, @Valid @RequestBody PhoneAttachRequest request) {
        return MemberResponse.from(service.attach(member, request.phone(), request.phoneVerificationToken()));
    }
}
