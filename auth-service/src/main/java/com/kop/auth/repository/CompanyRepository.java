package com.kop.auth.repository;

import com.kop.auth.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    boolean existsByBusinessRegistrationNumber(String businessRegistrationNumber);
}
