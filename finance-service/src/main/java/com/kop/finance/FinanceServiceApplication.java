package com.kop.finance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * finance-service — 04-system-architecture.md 마이크로서비스 목록.
 * EnableScheduling은 PaymentMatchingScheduler(입금-영수증 자동 매칭 배치)가 쓴다.
 */
@SpringBootApplication(scanBasePackages = "com.kop")
@EnableScheduling
public class FinanceServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinanceServiceApplication.class, args);
    }
}
