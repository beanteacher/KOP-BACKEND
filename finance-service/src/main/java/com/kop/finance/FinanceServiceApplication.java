package com.kop.finance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * finance-service — 04-system-architecture.md 마이크로서비스 목록. 아직 골격만 있고
 * 도메인 기능은 순서대로(다음 스프린트) 채워 넣는다. 지금은 /actuator/health만 응답한다.
 */
@SpringBootApplication(scanBasePackages = "com.kop")
public class FinanceServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinanceServiceApplication.class, args);
    }
}
