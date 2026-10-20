package com.example.demo.member;

public enum MemberStatus {
    ACTIVE,
    /** 기간 정지. suspendedUntil 이 지나면 자동으로 풀린 것으로 봅니다. */
    SUSPENDED,
    /** 영구 차단. 휴대폰 번호 해시가 남아 있어 같은 번호로 다시 가입할 수 없습니다. */
    BANNED,
    /** 스스로 탈퇴한 회원. 개인정보는 비식별화되고 로그인할 수 없습니다. */
    WITHDRAWN
}
