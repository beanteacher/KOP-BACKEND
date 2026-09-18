package com.kop.finance.service;

import com.kop.common.exception.BusinessException;
import com.kop.finance.domain.Client;
import com.kop.finance.domain.ClientStatus;
import com.kop.finance.dto.ClientDto;
import com.kop.finance.repository.ClientRepository;
import com.kop.finance.repository.TaxInvoiceRepository;
import com.kop.finance.security.EmployeePrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock ClientRepository clientRepository;
    @Mock TaxInvoiceRepository taxInvoiceRepository;

    ClientService clientService;

    UUID companyId = UUID.randomUUID();
    EmployeePrincipal admin;
    EmployeePrincipal staff;

    @BeforeEach
    void setUp() {
        clientService = new ClientService(clientRepository, taxInvoiceRepository);
        admin = new EmployeePrincipal(UUID.randomUUID().toString(), companyId.toString(), "ADMIN", "FREE");
        staff = new EmployeePrincipal(UUID.randomUUID().toString(), companyId.toString(), "STAFF", "FREE");
    }

    private ClientDto.RegisterRequest request() {
        return new ClientDto.RegisterRequest("1234567890", "한성식자재", "홍길동", "도소매", "식자재", "서울시 강남구", null, null, null);
    }

    private Client existingClient() {
        Client client = Client.register(companyId, "1234567890", "한성식자재", "홍길동", "도소매", "식자재", "서울시 강남구", null, null, null);
        ReflectionTestUtils.setField(client, "id", UUID.randomUUID());
        return client;
    }

    @Test
    void register_정상_등록한다() {
        when(clientRepository.existsByCompanyIdAndBusinessRegistrationNumber(companyId, "1234567890")).thenReturn(false);
        when(clientRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Client c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            return c;
        });

        ClientDto.Response response = clientService.register(admin, request());

        assertThat(response.name()).isEqualTo("한성식자재");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void register_사업자번호가_중복이면_CONFLICT를_던진다() {
        when(clientRepository.existsByCompanyIdAndBusinessRegistrationNumber(companyId, "1234567890")).thenReturn(true);

        assertThatThrownBy(() -> clientService.register(admin, request()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "CLIENT_DUPLICATE");

        verify(clientRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_직원이_호출하면_FORBIDDEN을_던진다() {
        assertThatThrownBy(() -> clientService.register(staff, request()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "FORBIDDEN");

        verifyNoInteractions(clientRepository);
    }

    @Test
    void update_사업자번호를_다른_거래처와_중복되게_바꾸면_CONFLICT를_던진다() {
        Client client = existingClient();
        when(clientRepository.findByIdAndCompanyId(client.getId(), companyId)).thenReturn(Optional.of(client));
        when(clientRepository.existsByCompanyIdAndBusinessRegistrationNumberAndIdNot(companyId, "9999999999", client.getId()))
            .thenReturn(true);
        var request = new ClientDto.UpdateRequest("9999999999", "새이름", "홍길동", "도소매", "식자재", "서울", null, null, null, "ACTIVE");

        assertThatThrownBy(() -> clientService.update(admin, client.getId(), request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "CLIENT_DUPLICATE");
    }

    @Test
    void update_status를_INACTIVE로_바꿀_수_있다() {
        Client client = existingClient();
        when(clientRepository.findByIdAndCompanyId(client.getId(), companyId)).thenReturn(Optional.of(client));
        var request = new ClientDto.UpdateRequest("1234567890", "한성식자재", "홍길동", "도소매", "식자재", "서울", null, null, null, "INACTIVE");

        ClientDto.Response response = clientService.update(admin, client.getId(), request);

        assertThat(response.status()).isEqualTo("INACTIVE");
    }

    @Test
    void delete_세금계산서_이력이_없으면_삭제한다() {
        Client client = existingClient();
        when(clientRepository.findByIdAndCompanyId(client.getId(), companyId)).thenReturn(Optional.of(client));
        when(taxInvoiceRepository.existsByClientId(client.getId())).thenReturn(false);

        clientService.delete(admin, client.getId());

        verify(clientRepository).delete(client);
    }

    @Test
    void delete_세금계산서_이력이_있으면_CLIENT_HAS_INVOICES를_던진다() {
        Client client = existingClient();
        when(clientRepository.findByIdAndCompanyId(client.getId(), companyId)).thenReturn(Optional.of(client));
        when(taxInvoiceRepository.existsByClientId(client.getId())).thenReturn(true);

        assertThatThrownBy(() -> clientService.delete(admin, client.getId()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "CLIENT_HAS_INVOICES");

        verify(clientRepository, never()).delete(any());
    }

    @Test
    void getDetail_존재하지_않으면_NOT_FOUND를_던진다() {
        UUID id = UUID.randomUUID();
        when(clientRepository.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.getDetail(admin, id))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
    }

    @Test
    void list_직원이_호출하면_FORBIDDEN을_던진다() {
        assertThatThrownBy(() -> clientService.list(staff, null, null, PageRequest.of(0, 20)))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }
}
