package com.kitchensys.finance.repository;

import com.kitchensys.finance.domain.Receipt;
import com.kitchensys.finance.domain.ReceiptCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    Optional<Receipt> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);

    // A-1 Free 플랜 월 한도 — from(월 1일 00:00) <= createdAt < to(다음 달 1일 00:00).
    @Query("""
        select count(r) from Receipt r
        where r.companyId = :companyId and r.deletedAt is null
          and r.createdAt >= :from and r.createdAt < :to
        """)
    long countInPeriod(@Param("companyId") UUID companyId, @Param("from") Instant from, @Param("to") Instant to);

    // A-2 목록·필터 — createdBy는 null이면 전체(관리자), 값이 있으면 본인 등록건만(직원).
    @Query("""
        select r from Receipt r
        where r.companyId = :companyId
          and r.deletedAt is null
          and r.receiptDate between :from and :to
          and (:clientId is null or r.clientId = :clientId)
          and (:category is null or r.category = :category)
          and (:createdBy is null or r.createdBy = :createdBy)
        order by r.receiptDate desc, r.createdAt desc
        """)
    Page<Receipt> search(
        @Param("companyId") UUID companyId, @Param("from") LocalDate from, @Param("to") LocalDate to,
        @Param("clientId") UUID clientId, @Param("category") ReceiptCategory category, @Param("createdBy") UUID createdBy,
        Pageable pageable
    );

    @Query("""
        select coalesce(sum(r.amount), 0) from Receipt r
        where r.companyId = :companyId
          and r.deletedAt is null
          and r.receiptDate between :from and :to
          and (:clientId is null or r.clientId = :clientId)
          and (:category is null or r.category = :category)
          and (:createdBy is null or r.createdBy = :createdBy)
        """)
    long sumAmount(
        @Param("companyId") UUID companyId, @Param("from") LocalDate from, @Param("to") LocalDate to,
        @Param("clientId") UUID clientId, @Param("category") ReceiptCategory category, @Param("createdBy") UUID createdBy
    );
}
