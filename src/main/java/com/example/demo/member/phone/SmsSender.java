package com.example.demo.member.phone;

public interface SmsSender {

    void send(String to, String text);

    /** 실제 문자를 보내지 않는 개발용 발송기인지 여부 */
    default boolean isFake() {
        return false;
    }
}
