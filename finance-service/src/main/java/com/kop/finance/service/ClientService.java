package com.kop.finance.service;

import com.kop.common.exception.BusinessException;
import com.kop.common.exception.EntityNotFoundException;
import com.kop.finance.domain.Client;
import com.kop.finance.domain.ClientStatus;
import com.kop.finance.dto.ClientDto;
import com.kop.finance.repository.ClientRepository;
import com.kop.finance.repository.TaxInvoiceRepository;
import com.kop.finance.security.EmployeePrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 03-feature-spec.md 모듈 B-1(거래처 등록·관리). 전부 관리자 전용. */
@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final TaxInvoiceRepository taxInvoiceRepository;

    @Transactional
    public ClientDto.Response register(EmployeePrincipal principal, ClientDto.RegisterRequest request) {
        requireAdmin(principal);
        UUID companyId = UUID.fromString(principal.companyId());

        if (clientRepository.existsByCompanyIdAndBusinessRegistrationNumber(companyId, request.businessRegistrationNumber())) {
            throw new BusinessException(HttpStatus.CONFLICT, "CLIENT_DUPLICATE", "이미 등록된 사업자번호입니다");
        }

        Client client = Client.register(
            companyId, request.businessRegistrationNumber(), request.name(), request.representativeName(),
            request.businessType(), request.businessItem(), request.address(),
            request.email(), request.contactName(), request.contactPhone()
        );
        return ClientDto.Response.from(clientRepository.saveAndFlush(client));
    }

    @Transactional(readOnly = true)
    public Page<ClientDto.ListItem> list(EmployeePrincipal principal, String query, String statusParam, Pageable pageable) {
        requireAdmin(principal);
        UUID companyId = UUID.fromString(principal.companyId());
        ClientStatus status = statusParam == null || statusParam.isBlank() ? null : parseStatus(statusParam);
        String queryFilter = (query == null || query.isBlank()) ? null : query.trim();

        return clientRepository.search(companyId, status, queryFilter, pageable).map(ClientDto.ListItem::from);
    }

    @Transactional(readOnly = true)
    public ClientDto.Response getDetail(EmployeePrincipal principal, UUID id) {
        requireAdmin(principal);
        return ClientDto.Response.from(findOwned(principal, id));
    }

    @Transactional
    public ClientDto.Response update(EmployeePrincipal principal, UUID id, ClientDto.UpdateRequest request) {
        requireAdmin(principal);
        Client client = findOwned(principal, id);
        UUID companyId = UUID.fromString(principal.companyId());

        if (!client.getBusinessRegistrationNumber().equals(request.businessRegistrationNumber())
            && clientRepository.existsByCompanyIdAndBusinessRegistrationNumberAndIdNot(companyId, request.businessRegistrationNumber(), id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "CLIENT_DUPLICATE", "이미 등록된 사업자번호입니다");
        }

        client.update(
            request.businessRegistrationNumber(), request.name(), request.representativeName(),
            request.businessType(), request.businessItem(), request.address(),
            request.email(), request.contactName(), request.contactPhone(), parseStatus(request.status())
        );
        return ClientDto.Response.from(client);
    }

    /** B-1 "세금계산서 있는 거래처 삭제 시도 → 삭제 불가, 비활성화 권장". */
    @Transactional
    public void delete(EmployeePrincipal principal, UUID id) {
        requireAdmin(principal);
        Client client = findOwned(principal, id);

        if (taxInvoiceRepository.existsByClientId(id)) {
            throw new BusinessException(
                HttpStatus.CONFLICT, "CLIENT_HAS_INVOICES", "세금계산서 발행 이력이 있어 삭제할 수 없습니다. 비활성화를 이용해주세요."
            );
        }
        clientRepository.delete(client);
    }

    private Client findOwned(EmployeePrincipal principal, UUID id) {
        UUID companyId = UUID.fromString(principal.companyId());
        return clientRepository.findByIdAndCompanyId(id, companyId)
            .orElseThrow(() -> new EntityNotFoundException("거래처", id));
    }

    private void requireAdmin(EmployeePrincipal principal) {
        if (!principal.isAdmin()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "이 작업을 수행할 권한이 없습니다");
        }
    }

    private ClientStatus parseStatus(String value) {
        try {
            return ClientStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "지원하지 않는 상태값입니다: " + value);
        }
    }
}
