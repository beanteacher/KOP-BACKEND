package com.kitchensys.auth.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "companies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "business_registration_number", nullable = false, length = 10, unique = true)
    private String businessRegistrationNumber;

    @Column(name = "representative_name", nullable = false, length = 50)
    private String representativeName;

    @Column(length = 20)
    private String phone;

    @Column(length = 200)
    private String address;

    @Column(length = 20)
    private String fax;

    @Column(name = "bank_name", length = 50)
    private String bankName;

    @Column(name = "bank_account_holder", length = 50)
    private String bankAccountHolder;

    /** 평문이 아니라 AesEncryptor로 암호화한 암호문을 저장한다 — 09-security.md 예외 조항 참고. */
    @Column(name = "bank_account_number", length = 255)
    private String bankAccountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan = Plan.FREE;

    @Column(name = "plan_updated_at")
    private Instant planUpdatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Company(String name, String businessRegistrationNumber, String representativeName, String phone) {
        this.name = name;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.representativeName = representativeName;
        this.phone = phone;
        this.plan = Plan.FREE;
    }

    /** X-1 업체 계정 생성 — 가입 시 Free 플랜 자동 적용. */
    public static Company register(String name, String businessRegistrationNumber, String representativeName, String phone) {
        return new Company(name, businessRegistrationNumber, representativeName, phone);
    }

    /**
     * 사업자 프로필 부분 수정 — null인 파라미터는 변경하지 않는다(EmployeeDto.UpdateRequest와 동일한 규칙).
     * bankAccountNumber는 호출 측(AuthService)이 이미 암호화한 값을 넘겨야 한다 — 이 엔티티는 암호화를 모른다.
     */
    public void updateProfile(String phone, String fax, String address, String bankName, String bankAccountHolder, String bankAccountNumber) {
        if (phone != null) this.phone = phone;
        if (fax != null) this.fax = fax;
        if (address != null) this.address = address;
        if (bankName != null) this.bankName = bankName;
        if (bankAccountHolder != null) this.bankAccountHolder = bankAccountHolder;
        if (bankAccountNumber != null) this.bankAccountNumber = bankAccountNumber;
    }
}
