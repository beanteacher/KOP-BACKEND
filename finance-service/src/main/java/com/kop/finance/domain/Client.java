package com.kop.finance.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** 03-feature-spec.md B-1, 05-database-schema.md finance.clients — 세금계산서 공급받는자 주소록. */
@Entity
@Table(name = "clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "business_registration_number", nullable = false, length = 10)
    private String businessRegistrationNumber;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "representative_name", nullable = false, length = 50)
    private String representativeName;

    @Column(name = "business_type", nullable = false, length = 100)
    private String businessType;

    @Column(name = "business_item", nullable = false, length = 100)
    private String businessItem;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(length = 255)
    private String email;

    @Column(name = "contact_name", length = 50)
    private String contactName;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientStatus status;

    /** 부가세를 별도로 더 받을 수 있는 거래처인지 — GENERAL만 해당(Receipt.replaceItems 참고). */
    @Enumerated(EnumType.STRING)
    @Column(name = "tax_type", nullable = false, length = 20)
    private ClientTaxType taxType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Client(
        UUID companyId, String businessRegistrationNumber, String name, String representativeName,
        String businessType, String businessItem, String address, String email, String contactName, String contactPhone,
        ClientTaxType taxType
    ) {
        this.companyId = companyId;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.name = name;
        this.representativeName = representativeName;
        this.businessType = businessType;
        this.businessItem = businessItem;
        this.address = address;
        this.email = email;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.status = ClientStatus.ACTIVE;
        this.taxType = taxType;
    }

    /** B-1 거래처 등록. */
    public static Client register(
        UUID companyId, String businessRegistrationNumber, String name, String representativeName,
        String businessType, String businessItem, String address, String email, String contactName, String contactPhone,
        ClientTaxType taxType
    ) {
        return new Client(
            companyId, businessRegistrationNumber, name, representativeName,
            businessType, businessItem, address, email, contactName, contactPhone, taxType
        );
    }

    /**
     * B-1 거래처 수정 — 필드 전체 교체(사업자번호 포함, 중복 검증은 Service에서). status도 여기서
     * 같이 바꾼다 — "세금계산서 있는 거래처는 삭제 대신 이 PATCH로 INACTIVE 전환"이 유일한 경로다
     * (06-api-design.md DELETE 설명 참고, 별도 비활성화 전용 엔드포인트는 없다).
     */
    public void update(
        String businessRegistrationNumber, String name, String representativeName,
        String businessType, String businessItem, String address, String email, String contactName, String contactPhone,
        ClientStatus status, ClientTaxType taxType
    ) {
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.name = name;
        this.representativeName = representativeName;
        this.businessType = businessType;
        this.businessItem = businessItem;
        this.address = address;
        this.email = email;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.status = status;
        this.taxType = taxType;
    }
}
