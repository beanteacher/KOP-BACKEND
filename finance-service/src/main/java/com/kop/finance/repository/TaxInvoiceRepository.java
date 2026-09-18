package com.kop.finance.repository;

import com.kop.finance.domain.TaxInvoice;
import com.kop.finance.domain.TaxInvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TaxInvoiceRepository extends JpaRepository<TaxInvoice, UUID> {

    Optional<TaxInvoice> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByClientId(UUID clientId);

    // B-3 이력 조회 — 기간·거래처·상태 필터. client를 함께 로딩해 목록 N+1을 피한다.
    @Query("""
        select ti from TaxInvoice ti join fetch ti.client c
        where ti.companyId = :companyId
          and ti.issueDate between :from and :to
          and (:clientId is null or ti.client.id = :clientId)
          and (:status is null or ti.status = :status)
        order by ti.issueDate desc, ti.createdAt desc
        """)
    Page<TaxInvoice> search(
        @Param("companyId") UUID companyId, @Param("from") LocalDate from, @Param("to") LocalDate to,
        @Param("clientId") UUID clientId, @Param("status") TaxInvoiceStatus status, Pageable pageable
    );

    // B-3 상단 집계 — 취소 건은 제외(03-feature-spec.md "취소 후 집계에서 제외").
    @Query("""
        select coalesce(sum(ti.supplyAmount), 0) from TaxInvoice ti
        where ti.companyId = :companyId and ti.issueDate between :from and :to
          and (:clientId is null or ti.client.id = :clientId)
          and (:status is null or ti.status = :status)
          and ti.status <> com.kop.finance.domain.TaxInvoiceStatus.CANCELED
          and ti.status <> com.kop.finance.domain.TaxInvoiceStatus.DRAFT
        """)
    long sumSupplyAmount(
        @Param("companyId") UUID companyId, @Param("from") LocalDate from, @Param("to") LocalDate to,
        @Param("clientId") UUID clientId, @Param("status") TaxInvoiceStatus status
    );

    @Query("""
        select coalesce(sum(ti.taxAmount), 0) from TaxInvoice ti
        where ti.companyId = :companyId and ti.issueDate between :from and :to
          and (:clientId is null or ti.client.id = :clientId)
          and (:status is null or ti.status = :status)
          and ti.status <> com.kop.finance.domain.TaxInvoiceStatus.CANCELED
          and ti.status <> com.kop.finance.domain.TaxInvoiceStatus.DRAFT
        """)
    long sumTaxAmount(
        @Param("companyId") UUID companyId, @Param("from") LocalDate from, @Param("to") LocalDate to,
        @Param("clientId") UUID clientId, @Param("status") TaxInvoiceStatus status
    );
}
