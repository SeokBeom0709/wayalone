package com.example.demo.member.phone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;

/** 솔라피(SOLAPI) 문자 API 로 실제 SMS 를 보냅니다. 발신번호는 솔라피 콘솔에 사전 등록돼 있어야 합니다. */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "solapi")
public class SolapiSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(SolapiSmsSender.class);

    private final String apiKey;
    private final String apiSecret;
    private final String from;
    private final RestClient client;
    private final SecureRandom random = new SecureRandom();

    public SolapiSmsSender(@Value("${app.sms.solapi.api-key:}") String apiKey,
                           @Value("${app.sms.solapi.api-secret:}") String apiSecret,
                           @Value("${app.sms.from:}") String from) {
        if (apiKey.isBlank() || apiSecret.isBlank() || from.isBlank()) {
            throw new IllegalStateException("SOLAPI_API_KEY, SOLAPI_API_SECRET, SMS_FROM 환경변수를 모두 설정해 주세요.");
        }
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.from = from.replaceAll("\\D", "");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.client = RestClient.builder()
                .baseUrl("https://api.solapi.com")
                .requestFactory(factory)
                .build();
    }

    @Override
    public void send(String to, String text) {
        try {
            client.post()
                    .uri("/messages/v4/send")
                    .header("Authorization", authorization())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", Map.of("to", to, "from", from, "text", text)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("SOLAPI send failed: status={} body={}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "문자를 보내지 못했어요. 잠시 후 다시 시도해 주세요.");
        } catch (RestClientException e) {
            log.warn("SOLAPI unreachable: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "문자를 보내지 못했어요. 잠시 후 다시 시도해 주세요.");
        }
    }

    private String authorization() {
        String date = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = HexFormat.of().formatHex(mac.doFinal((date + salt).getBytes(StandardCharsets.UTF_8)));
            return "HMAC-SHA256 apiKey=%s, date=%s, salt=%s, signature=%s".formatted(apiKey, date, salt, signature);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
