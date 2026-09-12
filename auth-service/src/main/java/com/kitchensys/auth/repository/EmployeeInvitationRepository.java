package com.kitchensys.auth.repository;

import com.kitchensys.auth.domain.EmployeeInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeInvitationRepository extends JpaRepository<EmployeeInvitation, UUID> {

    Optional<EmployeeInvitation> findByToken(String token);

    List<EmployeeInvitation> findByCompany_IdOrderByCreatedAtDesc(UUID companyId);
}
