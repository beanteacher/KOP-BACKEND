package com.kitchensys.auth.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "employees")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Employee {

    private static final int MAX_FAILED_LOGIN = 5;
    private static final int LOCK_MINUTES = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "failed_login_count", nullable = false)
    private short failedLoginCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Employee(Company company, String email, String passwordHash, String name, EmployeeRole role) {
        this.company = company;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.status = EmployeeStatus.ACTIVE;
    }

    /** X-1 가입 시 관리자 1건이 회사와 함께 생성된다. */
    public static Employee createAdmin(Company company, String email, String passwordHash, String name) {
        return new Employee(company, email, passwordHash, name, EmployeeRole.ADMIN);
    }

    /** X-2 초대 수락 — 초대에 지정된 역할로 생성된다. */
    public static Employee createFromInvitation(Company company, String email, String passwordHash, String name, EmployeeRole role) {
        return new Employee(company, email, passwordHash, name, role);
    }

    public UUID getCompanyId() {
        return company.getId();
    }

    public boolean isAdmin() {
        return role == EmployeeRole.ADMIN;
    }

    public boolean isActive() {
        return status == EmployeeStatus.ACTIVE && deletedAt == null;
    }

    /** X-3 보안 규칙 — 5회 연속 실패 시 10분 잠금. */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public void recordFailedLogin() {
        this.failedLoginCount++;
        if (this.failedLoginCount >= MAX_FAILED_LOGIN) {
            this.lockedUntil = Instant.now().plus(LOCK_MINUTES, ChronoUnit.MINUTES);
        }
    }

    public void resetFailedLogin() {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    public void verifyEmail() {
        this.emailVerifiedAt = Instant.now();
    }

    /** 비밀번호 재설정 — 세션(Refresh Token) 무효화는 별도 RefreshTokenService가 처리한다. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        resetFailedLogin();
    }

    public void changeRole(EmployeeRole role) {
        this.role = role;
    }

    public void activate() {
        this.status = EmployeeStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = EmployeeStatus.INACTIVE;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.status = EmployeeStatus.INACTIVE;
    }
}
