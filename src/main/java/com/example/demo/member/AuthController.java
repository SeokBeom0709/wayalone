package com.example.demo.member;

import com.example.demo.member.dto.AuthResponse;
import com.example.demo.member.dto.LoginHistoryResponse;
import com.example.demo.member.dto.LoginRequest;
import com.example.demo.member.dto.MemberResponse;
import com.example.demo.member.dto.PasswordChangeRequest;
import com.example.demo.member.dto.SignupRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, ClientInfo.from(http));
    }

    /** 내 계정의 최근 로그인 기록 (성공/실패) */
    @GetMapping("/logins")
    public List<LoginHistoryResponse> logins(@LoginMember Member member) {
        return authService.recentLogins(member).stream().map(LoginHistoryResponse::from).toList();
    }

    @GetMapping("/me")
    public MemberResponse me(@LoginMember Member member) {
        return MemberResponse.from(member);
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(@RequestHeader(value = "Authorization", required = false) String header) {
        authService.logout(TokenHeader.extract(header));
        return Map.of("ok", true);
    }

    @PostMapping("/password")
    public AuthResponse changePassword(@LoginMember Member member,
                                       @Valid @RequestBody PasswordChangeRequest request) {
        return authService.changePassword(member, request);
    }
}
