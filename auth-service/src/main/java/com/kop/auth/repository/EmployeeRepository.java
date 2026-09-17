package com.kop.auth.repository;

import com.kop.auth.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByEmail(String email);

    boolean existsByEmail(String email);

    // company_id 스코프는 09-security.md 멀티테넌시 원칙에 따라 쿼리 메서드에 항상 명시한다.
    // Employee는 companyId를 연관관계(company)로만 갖고 있어 "_" 로 중첩 경로를 탄다.
    List<Employee> findByCompany_IdAndDeletedAtIsNull(UUID companyId);

    Optional<Employee> findByIdAndCompany_Id(UUID id, UUID companyId);
}
