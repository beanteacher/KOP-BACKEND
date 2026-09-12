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
}
