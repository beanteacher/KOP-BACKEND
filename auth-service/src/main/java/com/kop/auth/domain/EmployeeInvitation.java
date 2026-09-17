package com.kop.auth.domain;

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
@Table(name = "employee_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmployeeInvitation {

    private static final int EXPIRES_HOURS = 72;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeRole role;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", nullable = false)
    private Employee invitedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private EmployeeInvitation(Company company, String email, EmployeeRole role, String token, Employee invitedBy) {
        this.company = company;
        this.email = email;
        this.role = role;
        this.token = token;
        this.invitedBy = invitedBy;
        this.expiresAt = Instant.now().plus(EXPIRES_HOURS, ChronoUnit.HOURS);
    }

    /** X-2 직원 초대 — 토큰은 서비스 레이어에서 난수로 생성해 주입한다. */
    public static EmployeeInvitation create(Company company, String email, EmployeeRole role, String token, Employee invitedBy) {
        return new EmployeeInvitation(company, email, role, token, invitedBy);
    }

    public UUID getCompanyId() {
        return company.getId();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isAccepted() {
        return acceptedAt != null;
    }

    public void accept() {
        this.acceptedAt = Instant.now();
    }
}
