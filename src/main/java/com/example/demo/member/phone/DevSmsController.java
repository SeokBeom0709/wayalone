package com.example.demo.member.phone;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 개발용 문자함. 실제 발송기(solapi)를 쓰는 환경에서는 이 엔드포인트 자체가 만들어지지 않습니다. */
@RestController
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "log", matchIfMissing = true)
public class DevSmsController {

    private final LogSmsSender sender;

    public DevSmsController(LogSmsSender sender) {
        this.sender = sender;
    }

    @GetMapping("/api/dev/sms")
    public List<LogSmsSender.DevMessage> inbox() {
        return sender.recent();
    }
}
