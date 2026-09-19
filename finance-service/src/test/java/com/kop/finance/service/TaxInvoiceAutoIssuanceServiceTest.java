package com.kop.finance.service;

import com.kop.finance.domain.Client;
import com.kop.finance.domain.ClientTaxType;
import com.kop.finance.domain.Receipt;
import com.kop.finance.domain.TaxInvoice;
import com.kop.finance.repository.ClientRepository;
import com.kop.finance.repository.TaxInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaxInvoiceAutoIssuanceServiceTest {

    @Mock ClientRepository clientRepository;
    @Mock TaxInvoiceRepository taxInvoiceRepository;

    TaxInvoiceAutoIssuanceService service;

    UUID companyId = UUID.randomUUID();
    UUID createdBy = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TaxInvoiceAutoIssuanceService(clientRepository, taxInvoiceRepository);
    }

    private Client clientOf(UUID id, ClientTaxType taxType) {
        Client client = Client.register(
            companyId, "1234567890", "한성식자재", "홍길동", "도소매", "식자재", "서울", null, null, null, taxType
        );
        ReflectionTestUtils.setField(client, "id", id);
        return client;
    }

    private Receipt receiptWithClient(UUID clientId) {
        Receipt receipt = Receipt.register(companyId, createdBy, LocalDate.now(), "한성식자재", clientId, null);
        receipt.replaceItems(List.of(new Receipt.ItemInput("돼지고기", "1kg", 2, 10000L)));
        ReflectionTestUtils.setField(receipt, "id", UUID.randomUUID());
        return receipt;
    }

    @Test
    void 거래처가_지정되지_않은_영수증은_건너뛴다() {
        Receipt receipt = receiptWithClient(null);

        service.issueIfEligible(receipt);

        verifyNoInteractions(clientRepository, taxInvoiceRepository);
    }

    @Test
    void 거래처를_찾을_수_없으면_건너뛴다() {
        UUID clientId = UUID.randomUUID();
        Receipt receipt = receiptWithClient(clientId);
        when(clientRepository.findByIdAndCompanyId(clientId, companyId)).thenReturn(Optional.empty());

        service.issueIfEligible(receipt);

        verify(taxInvoiceRepository, never()).save(any());
    }

    @Test
    void 거래처가_있으면_COMPLETED_상태로_세금계산서를_자동발행한다() {
        UUID clientId = UUID.randomUUID();
        Client client = clientOf(clientId, ClientTaxType.GENERAL);
        Receipt receipt = receiptWithClient(clientId);
        when(clientRepository.findByIdAndCompanyId(clientId, companyId)).thenReturn(Optional.of(client));

        service.issueIfEligible(receipt);

        ArgumentCaptor<TaxInvoice> captor = ArgumentCaptor.forClass(TaxInvoice.class);
        verify(taxInvoiceRepository).save(captor.capture());
        TaxInvoice saved = captor.getValue();
        assertThat(saved.getSourceReceiptId()).isEqualTo(receipt.getId());
        assertThat(saved.getStatus().name()).isEqualTo("COMPLETED");
        assertThat(saved.getClient()).isEqualTo(client);
        assertThat(saved.getItems()).hasSize(1);
        // 영수증 금액(receipt.amount)은 항상 실제 수령 총액 — 거꾸로 쪼개도 합계는 그대로 보존된다.
        assertThat(saved.getSupplyAmount() + saved.getTaxAmount()).isEqualTo(saved.getTotalAmount());
        assertThat(saved.getTotalAmount()).isEqualTo(receipt.getAmount());
    }

    @Test
    void 간이과세자_거래처여도_영수증_금액에서_부가세를_거꾸로_나눠_발행한다() {
        UUID clientId = UUID.randomUUID();
        Client client = clientOf(clientId, ClientTaxType.SIMPLIFIED);
        Receipt receipt = receiptWithClient(clientId); // amount = 20,000
        when(clientRepository.findByIdAndCompanyId(clientId, companyId)).thenReturn(Optional.of(client));

        service.issueIfEligible(receipt);

        ArgumentCaptor<TaxInvoice> captor = ArgumentCaptor.forClass(TaxInvoice.class);
        verify(taxInvoiceRepository).save(captor.capture());
        TaxInvoice saved = captor.getValue();
        assertThat(saved.getTotalAmount()).isEqualTo(20_000L);
        assertThat(saved.getSupplyAmount()).isEqualTo(18_182L); // round(20000 / 1.1)
        assertThat(saved.getTaxAmount()).isEqualTo(1_818L); // 20000 - 18182
    }
}
