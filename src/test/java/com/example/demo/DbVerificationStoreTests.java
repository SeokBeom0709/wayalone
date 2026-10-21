package com.example.demo;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** 기본 저장소(DB)로 공통 규칙을 확인합니다. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:verifydb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.verification.store=db",
        "app.sms.ip-hourly-limit=100000"
})
class DbVerificationStoreTests extends AbstractVerificationStoreTests {
}
