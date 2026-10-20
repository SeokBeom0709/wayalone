package com.example.demo.member;

public enum LoginResult {
    SUCCESS,
    /** 이메일 또는 비밀번호가 맞지 않음 */
    FAIL_CREDENTIALS,
    /** 비밀번호는 맞지만 정지/차단된 계정 */
    FAIL_BLOCKED,
    /** 연속 실패로 잠시 막힘 */
    FAIL_LOCKED
}
