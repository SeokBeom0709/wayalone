package com.example.demo.account;

import com.example.demo.member.LoginMember;
import com.example.demo.member.Member;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final WithdrawalService withdrawalService;

    public AccountController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    /** 회원 탈퇴. 되돌릴 수 없으므로 비밀번호를 한 번 더 확인합니다. */
    @PostMapping("/withdraw")
    public Map<String, Boolean> withdraw(@LoginMember Member member, @Valid @RequestBody WithdrawRequest request) {
        withdrawalService.withdraw(member, request.password(), request.reason());
        return Map.of("ok", true);
    }
}
