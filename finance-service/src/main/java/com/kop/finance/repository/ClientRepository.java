package com.kop.finance.repository;

import com.kop.finance.domain.Client;
import com.kop.finance.domain.ClientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    Optional<Client> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndBusinessRegistrationNumber(UUID companyId, String businessRegistrationNumber);

    boolean existsByCompanyIdAndBusinessRegistrationNumberAndIdNot(UUID companyId, String businessRegistrationNumber, UUID id);

    // B-1 목록·검색 — query는 상호/사업자번호 부분일치, status는 null이면 전체.
    @Query("""
        select c from Client c
        where c.companyId = :companyId
          and (:status is null or c.status = :status)
          and (:query is null or lower(c.name) like lower(concat('%', cast(:query as string), '%'))
               or c.businessRegistrationNumber like concat('%', cast(:query as string), '%'))
        order by c.name asc
        """)
    Page<Client> search(
        @Param("companyId") UUID companyId, @Param("status") ClientStatus status, @Param("query") String query, Pageable pageable
    );
}
