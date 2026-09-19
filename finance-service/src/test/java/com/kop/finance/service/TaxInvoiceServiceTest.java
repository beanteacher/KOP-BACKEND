package com.kop.finance.service;

import com.kop.common.exception.BusinessException;
import com.kop.finance.domain.Client;
import com.kop.finance.domain.TaxInvoice;
import com.kop.finance.dto.TaxInvoiceDto;
import com.kop.finance.issuance.TaxInvoiceIssuanceGateway;
import com.kop.finance.repository.ClientRepository;
import com.kop.finance.repository.TaxInvoiceRepository;
import com.kop.finance.security.EmployeePrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxInvoiceServiceTest {

    @Mock TaxInvoiceRepository taxInvoiceRepository;
    @Mock ClientRepository clientRepository;
    @Mock TaxInvoiceIssuanceGateway issuanceGateway;

    TaxInvoiceService taxInvoiceService;

    UUID companyId = UUID.randomUUID();
    EmployeePrincipal admin;
    EmployeePrincipal staff;

    @BeforeEach
    void setUp() {
        taxInvoiceService = new TaxInvoiceService(taxInvoiceRepository, clientRepository, issuanceGateway);
        admin = new EmployeePrincipal(UUID.randomUUID().toString(), companyId.toString(), "ADMIN", "FREE");
        staff = new EmployeePrincipal(UUID.randomUUID().toString(), companyId.toString(), "STAFF", "FREE");
    }

    private Client clientOf() {
        Client client = Client.register(companyId, "1234567890", "한성식자재", "홍길동", "도소매", "식자재", "서울", null, null, null);
        ReflectionTestUtils.setField(client, "id", UUID.randomUUID());
        return client;
    }

    private TaxInvoiceDto.ItemRequest item(String name, int quantity, long unitPrice) {
        return new TaxInvoiceDto.ItemRequest(name, null, quantity, unitPrice);
    }

    private TaxInvoice taxInvoiceOf(Client client) {
        TaxInvoice ti = TaxInvoice.create(companyId, UUID.randomUUID(), client, LocalDate.now(), com.kop.finance.domain.TaxInvoiceStatus.COMPLETED, null);
        ti.replaceItems(List.of(new TaxInvoice.ItemInput("업소용 냉장고", "2도어", 1, 1_800_000L)));
        ReflectionTestUtils.setField(ti, "id", UUID.randomUUID());
        return ti;
    }

    private void stubSaveAndFlush() {
        when(taxInvoiceRepository.saveAndFlush(any())).thenAnswer(inv -> {
            TaxInvoice ti = inv.getArgument(0);
            if (ti.getId() == null) ReflectionTestUtils.setField(ti, "id", UUID.randomUUID());
            return ti;
        });
    }

    @Test
    void register_공급가액과_세액을_계산해_저장한다() {
        Client client = clientOf();
        when(clientRepository.findByIdAndCompanyId(client.getId(), companyId)).thenReturn(Optional.of(client));
        stubSaveAndFlush();
        var request = new TaxInvoiceDto.RegisterRequest(
            client.getId().toString(), LocalDate.now(), "COMPLETED",
            List.of(item("업소용 냉장고 900L", 1, 1_800_000L), item("설치비", 1, 150_000L)), "9월 정기 납품"
        );

        TaxInvoiceDto.Response response = taxInvoiceService.register(admin, request);

        assertThat(response.supplyAmount()).isEqualTo(1_950_000L);
        assertThat(response.taxAmount()).isEqualTo(195_000L);
        assertThat(response.totalAmount()).isEqualTo(2_145_000L);
        assertThat(response.status()).isEqualTo("COMPLETED");
    }

    @Test
    void register_DRAFT나_COMPLETED가_아니면_VALIDATION_ERROR를_던진다() {
        Client client = clientOf();
        var request = new TaxInvoiceDto.RegisterRequest(client.getId().toString(), LocalDate.now(), "CANCELED", List.of(item("a", 1, 1000L)), null);

        assertThatThrownBy(() -> taxInvoiceService.register(admin, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");

        verifyNoInteractions(clientRepository);
    }

    @Test
    void register_품목이_17개_이상이면_VALIDATION_ERROR를_던진다() {
        Client client = clientOf();
        when(clientRepository.findByIdAndCompanyId(client.getId(), companyId)).thenReturn(Optional.of(client));
        var items = java.util.stream.IntStream.range(0, 17).mapToObj(i -> item("품목" + i, 1, 1000L)).toList();
        var request = new TaxInvoiceDto.RegisterRequest(client.getId().toString(), LocalDate.now(), "DRAFT", items, null);

        assertThatThrownBy(() -> taxInvoiceService.register(admin, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
    }

    @Test
    void register_존재하지_않는_거래처면_NOT_FOUND를_던진다() {
        UUID clientId = UUID.randomUUID();
        when(clientRepository.findByIdAndCompanyId(clientId, companyId)).thenReturn(Optional.empty());
        var request = new TaxInvoiceDto.RegisterRequest(clientId.toString(), LocalDate.now(), "DRAFT", List.of(item("a", 1, 1000L)), null);

        assertThatThrownBy(() -> taxInvoiceService.register(admin, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
    }

    @Test
    void register_직원이_호출하면_FORBIDDEN을_던진다() {
        var request = new TaxInvoiceDto.RegisterRequest(UUID.randomUUID().toString(), LocalDate.now(), "DRAFT", List.of(item("a", 1, 1000L)), null);

        assertThatThrownBy(() -> taxInvoiceService.register(staff, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    @Test
    void list_공급가액과_세액_합계를_함께_반환한다() {
        Page<TaxInvoice> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(taxInvoiceRepository.search(eq(companyId), any(), any(), isNull(), isNull(), any())).thenReturn(page);
        when(taxInvoiceRepository.sumSupplyAmount(eq(companyId), any(), any(), isNull(), isNull())).thenReturn(1_950_000L);
        when(taxInvoiceRepository.sumTaxAmount(eq(companyId), any(), any(), isNull(), isNull())).thenReturn(195_000L);

        var result = taxInvoiceService.list(admin, LocalDate.now().withDayOfMonth(1), LocalDate.now(), null, null, PageRequest.of(0, 20));

        assertThat(result.supplyAmount()).isEqualTo(1_950_000L);
        assertThat(result.taxAmount()).isEqualTo(195_000L);
    }

    @Test
    void issueExcel_엑셀다운로드완료로_전이하고_파일을_반환한다() {
        Client client = clientOf();
        TaxInvoice ti = taxInvoiceOf(client);
        when(taxInvoiceRepository.findByIdAndCompanyId(ti.getId(), companyId)).thenReturn(Optional.of(ti));
        byte[] fakeFile = {1, 2, 3};
        when(issuanceGateway.issue(eq(ti), any())).thenReturn(fakeFile);

        byte[] result = taxInvoiceService.issueExcel(admin, ti.getId(), new com.kop.finance.issuance.TaxInvoiceSupplierInfo("9198271234", "영수증 검증 업체"));

        assertThat(result).isEqualTo(fakeFile);
        assertThat(ti.getStatus()).isEqualTo(com.kop.finance.domain.TaxInvoiceStatus.EXCEL_DOWNLOADED);
        assertThat(ti.getExcelDownloadedAt()).isNotNull();
    }

    @Test
    void issueExcel_취소된_건이면_TAX_INVOICE_CANCELED를_던진다() {
        Client client = clientOf();
        TaxInvoice ti = taxInvoiceOf(client);
        ti.cancel("고객 요청");
        when(taxInvoiceRepository.findByIdAndCompanyId(ti.getId(), companyId)).thenReturn(Optional.of(ti));

        assertThatThrownBy(() -> taxInvoiceService.issueExcel(admin, ti.getId(), new com.kop.finance.issuance.TaxInvoiceSupplierInfo("a", "b")))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "TAX_INVOICE_CANCELED");

        verifyNoInteractions(issuanceGateway);
    }

    @Test
    void revise_새_건을_만들고_원본은_REVISED로_전환한다() {
        Client client = clientOf();
        TaxInvoice original = taxInvoiceOf(client);
        when(taxInvoiceRepository.findByIdAndCompanyId(original.getId(), companyId)).thenReturn(Optional.of(original));
        when(clientRepository.findByIdAndCompanyId(client.getId(), companyId)).thenReturn(Optional.of(client));
        stubSaveAndFlush();
        var request = new TaxInvoiceDto.RegisterRequest(
            client.getId().toString(), LocalDate.now(), "COMPLETED", List.of(item("수정품목", 2, 500_000L)), "수정 발행"
        );

        TaxInvoiceDto.Response response = taxInvoiceService.revise(admin, original.getId(), request);

        assertThat(response.revisedFromId()).isEqualTo(original.getId().toString());
        assertThat(response.supplyAmount()).isEqualTo(1_000_000L);
        assertThat(original.getStatus()).isEqualTo(com.kop.finance.domain.TaxInvoiceStatus.REVISED);
    }

    @Test
    void cancel_사유와_함께_취소한다() {
        Client client = clientOf();
        TaxInvoice ti = taxInvoiceOf(client);
        when(taxInvoiceRepository.findByIdAndCompanyId(ti.getId(), companyId)).thenReturn(Optional.of(ti));

        TaxInvoiceDto.Response response = taxInvoiceService.cancel(admin, ti.getId(), new TaxInvoiceDto.CancelRequest("고객 요청으로 취소"));

        assertThat(response.status()).isEqualTo("CANCELED");
        assertThat(response.cancelReason()).isEqualTo("고객 요청으로 취소");
    }

    @Test
    void cancel_이미_취소된_건이면_TAX_INVOICE_CANCELED를_던진다() {
        Client client = clientOf();
        TaxInvoice ti = taxInvoiceOf(client);
        ti.cancel("1차 취소");
        when(taxInvoiceRepository.findByIdAndCompanyId(ti.getId(), companyId)).thenReturn(Optional.of(ti));

        assertThatThrownBy(() -> taxInvoiceService.cancel(admin, ti.getId(), new TaxInvoiceDto.CancelRequest("2차 취소")))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "TAX_INVOICE_CANCELED");
    }

    @Test
    void getDetail_존재하지_않으면_NOT_FOUND를_던진다() {
        UUID id = UUID.randomUUID();
        when(taxInvoiceRepository.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taxInvoiceService.getDetail(admin, id))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
    }
}
