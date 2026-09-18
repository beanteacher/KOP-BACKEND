package com.kop.finance.service;

import com.kop.finance.domain.PaymentStatus;
import com.kop.finance.domain.Receipt;
import com.kop.finance.dto.ReceiptDto;
import com.kop.finance.repository.ReceiptRepository;
import com.kop.finance.security.EmployeePrincipal;
import com.kop.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    @Mock ReceiptRepository receiptRepository;

    ReceiptService receiptService;

    UUID companyId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    UUID staffId = UUID.randomUUID();
    EmployeePrincipal admin;
    EmployeePrincipal staff;

    @BeforeEach
    void setUp() {
        receiptService = new ReceiptService(receiptRepository);
        admin = new EmployeePrincipal(adminId.toString(), companyId.toString(), "ADMIN", "FREE");
        staff = new EmployeePrincipal(staffId.toString(), companyId.toString(), "STAFF", "FREE");
    }

    /** 이미 저장된 영수증을 흉내낸다 — 실제로는 register() 시점에 flush되어 품목 id도 이미 채워져 있다. */
    private Receipt receiptOf(UUID createdBy, Instant createdAt) {
        Receipt receipt = Receipt.register(companyId, createdBy, LocalDate.now(), "한성식자재", null, null);
        receipt.replaceItems(List.of(new Receipt.ItemInput("돼지고기", null, 1, 10000L)));
        ReflectionTestUtils.setField(receipt, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(receipt, "createdAt", createdAt);
        receipt.getItems().forEach(i -> ReflectionTestUtils.setField(i, "id", UUID.randomUUID()));
        return receipt;
    }

    private ReceiptDto.ItemRequest item(String name, int quantity, long unitPrice) {
        return new ReceiptDto.ItemRequest(name, null, quantity, unitPrice);
    }

    /**
     * 실제 Hibernate는 cascade PERSIST로 새로 추가된 품목의 id(@GeneratedValue)를 flush 시점에
     * 채운다 — saveAndFlush() 목이 그 시점을 흉내낸다(register()/update() 둘 다 이 메서드를 거친다).
     */
    private void stubSaveAndFlush() {
        when(receiptRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Receipt r = inv.getArgument(0);
            if (r.getId() == null) {
                ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
            }
            if (r.getCreatedAt() == null) {
                ReflectionTestUtils.setField(r, "createdAt", Instant.now());
            }
            r.getItems().forEach(i -> {
                if (i.getId() == null) {
                    ReflectionTestUtils.setField(i, "id", UUID.randomUUID());
                }
            });
            return r;
        });
    }

    @Test
    void register_품목_합계가_금액으로_저장된다() {
        var request = new ReceiptDto.RegisterRequest(
            LocalDate.now(), "한성식자재", null, "9월 재료비",
            List.of(item("돼지고기 앞다리살", 10, 8000L), item("배추", 5, 1000L))
        );
        when(receiptRepository.countInPeriod(eq(companyId), any(), any())).thenReturn(5L);
        stubSaveAndFlush();

        ReceiptDto.Response response = receiptService.register(staff, request);

        assertThat(response.amount()).isEqualTo(85_000L); // 10*8000 + 5*1000
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).amount()).isEqualTo(80_000L);
        assertThat(response.items().get(1).amount()).isEqualTo(5_000L);
    }

    @Test
    void register_등록시_기본_상태는_PENDING이다() {
        var request = new ReceiptDto.RegisterRequest(LocalDate.now(), "한성식자재", null, null, List.of(item("고기", 1, 85000L)));
        when(receiptRepository.countInPeriod(eq(companyId), any(), any())).thenReturn(0L);
        stubSaveAndFlush();

        ReceiptDto.Response response = receiptService.register(staff, request);

        assertThat(response.paymentStatus()).isEqualTo("PENDING");
        assertThat(response.paidAt()).isNull();
    }

    @Test
    void register_품목에_규격을_입력하면_그대로_저장된다() {
        var request = new ReceiptDto.RegisterRequest(
            LocalDate.now(), "한성식자재", null, null,
            List.of(new ReceiptDto.ItemRequest("스텐 작업대", "900*700*850", 1, 300_000L))
        );
        when(receiptRepository.countInPeriod(eq(companyId), any(), any())).thenReturn(0L);
        stubSaveAndFlush();

        ReceiptDto.Response response = receiptService.register(staff, request);

        assertThat(response.items().get(0).spec()).isEqualTo("900*700*850");
    }

    @Test
    void register_규격을_입력하지_않으면_null로_저장된다() {
        var request = new ReceiptDto.RegisterRequest(LocalDate.now(), "한성식자재", null, null, List.of(item("고기", 1, 85000L)));
        when(receiptRepository.countInPeriod(eq(companyId), any(), any())).thenReturn(0L);
        stubSaveAndFlush();

        ReceiptDto.Response response = receiptService.register(staff, request);

        assertThat(response.items().get(0).spec()).isNull();
    }

    @Test
    void register_품목이_없으면_VALIDATION_ERROR를_던진다() {
        var request = new ReceiptDto.RegisterRequest(LocalDate.now(), "한성식자재", null, null, List.of());
        when(receiptRepository.countInPeriod(eq(companyId), any(), any())).thenReturn(0L);

        assertThatThrownBy(() -> receiptService.register(staff, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");

        verify(receiptRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_FREE_플랜_월20건_초과시_한도초과_예외를_던진다() {
        var request = new ReceiptDto.RegisterRequest(LocalDate.now(), "한성식자재", null, null, List.of(item("고기", 1, 85000L)));
        when(receiptRepository.countInPeriod(eq(companyId), any(), any())).thenReturn(20L);

        assertThatThrownBy(() -> receiptService.register(staff, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "RECEIPT_PLAN_LIMIT_EXCEEDED");

        verify(receiptRepository, never()).saveAndFlush(any());
    }

    @Test
    void list_직원은_본인_등록건만_조회한다() {
        Page<Receipt> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(receiptRepository.search(eq(companyId), any(), any(), isNull(), isNull(), eq(staffId), isNull(), any())).thenReturn(page);
        when(receiptRepository.sumAmount(eq(companyId), any(), any(), isNull(), isNull(), eq(staffId), isNull())).thenReturn(0L);

        receiptService.list(staff, LocalDate.now().withDayOfMonth(1), LocalDate.now(), null, null, null, null, PageRequest.of(0, 20));

        verify(receiptRepository).search(eq(companyId), any(), any(), isNull(), isNull(), eq(staffId), isNull(), any());
    }

    @Test
    void list_관리자는_등록자_필터를_지정하지_않으면_전체를_조회한다() {
        Page<Receipt> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(receiptRepository.search(eq(companyId), any(), any(), isNull(), isNull(), isNull(), isNull(), any())).thenReturn(page);
        when(receiptRepository.sumAmount(eq(companyId), any(), any(), isNull(), isNull(), isNull(), isNull())).thenReturn(0L);

        receiptService.list(admin, LocalDate.now().withDayOfMonth(1), LocalDate.now(), null, null, null, null, PageRequest.of(0, 20));

        verify(receiptRepository).search(eq(companyId), any(), any(), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void list_paymentStatus_필터로_조회한다() {
        Page<Receipt> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(receiptRepository.search(eq(companyId), any(), any(), isNull(), eq(PaymentStatus.PAID), isNull(), isNull(), any())).thenReturn(page);
        when(receiptRepository.sumAmount(eq(companyId), any(), any(), isNull(), eq(PaymentStatus.PAID), isNull(), isNull())).thenReturn(0L);

        receiptService.list(admin, LocalDate.now().withDayOfMonth(1), LocalDate.now(), null, "PAID", null, null, PageRequest.of(0, 20));

        verify(receiptRepository).search(eq(companyId), any(), any(), isNull(), eq(PaymentStatus.PAID), isNull(), isNull(), any());
    }

    @Test
    void list_지원하지_않는_paymentStatus면_VALIDATION_ERROR를_던진다() {
        assertThatThrownBy(() ->
            receiptService.list(admin, LocalDate.now().withDayOfMonth(1), LocalDate.now(), null, "INVALID", null, null, PageRequest.of(0, 20))
        )
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
    }

    @Test
    void update_직원이_본인_등록건을_24시간_이내_수정한다() {
        Receipt receipt = receiptOf(staffId, Instant.now().minusSeconds(3600));
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));
        stubSaveAndFlush();
        var request = new ReceiptDto.UpdateRequest(LocalDate.now(), "새거래처", null, null, List.of(item("새품목", 2, 2500L)));

        ReceiptDto.Response response = receiptService.update(staff, receipt.getId(), request);

        assertThat(response.amount()).isEqualTo(5000L);
        assertThat(response.vendorName()).isEqualTo("새거래처");
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void update_직원이_24시간_초과건_수정시_FORBIDDEN을_던진다() {
        Receipt receipt = receiptOf(staffId, Instant.now().minus(java.time.Duration.ofHours(25)));
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));
        var request = new ReceiptDto.UpdateRequest(LocalDate.now(), "새거래처", null, null, List.of(item("새품목", 2, 2500L)));

        assertThatThrownBy(() -> receiptService.update(staff, receipt.getId(), request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    @Test
    void update_직원이_타인_등록건_수정시_FORBIDDEN을_던진다() {
        Receipt receipt = receiptOf(adminId, Instant.now());
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));
        var request = new ReceiptDto.UpdateRequest(LocalDate.now(), "새거래처", null, null, List.of(item("새품목", 2, 2500L)));

        assertThatThrownBy(() -> receiptService.update(staff, receipt.getId(), request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    @Test
    void update_관리자는_기간_제한없이_수정한다() {
        Receipt receipt = receiptOf(staffId, Instant.now().minus(java.time.Duration.ofDays(10)));
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));
        stubSaveAndFlush();
        var request = new ReceiptDto.UpdateRequest(LocalDate.now(), "새거래처", null, null, List.of(item("새품목", 2, 2500L)));

        ReceiptDto.Response response = receiptService.update(admin, receipt.getId(), request);

        assertThat(response.amount()).isEqualTo(5000L);
    }

    @Test
    void update_PAID_상태면_RECEIPT_NOT_PENDING을_던진다() {
        Receipt receipt = receiptOf(staffId, Instant.now());
        receipt.markPaid(null);
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));
        var request = new ReceiptDto.UpdateRequest(LocalDate.now(), "새거래처", null, null, List.of(item("새품목", 2, 2500L)));

        assertThatThrownBy(() -> receiptService.update(admin, receipt.getId(), request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "RECEIPT_NOT_PENDING");
    }

    @Test
    void delete_소프트_삭제한다() {
        Receipt receipt = receiptOf(staffId, Instant.now());
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));

        receiptService.delete(staff, receipt.getId());

        assertThat(receipt.getDeletedAt()).isNotNull();
    }

    @Test
    void delete_PAID_상태면_RECEIPT_NOT_PENDING을_던진다() {
        Receipt receipt = receiptOf(staffId, Instant.now());
        receipt.markPaid(null);
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> receiptService.delete(admin, receipt.getId()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "RECEIPT_NOT_PENDING");
    }

    @Test
    void getDetail_존재하지_않으면_NOT_FOUND를_던진다() {
        UUID id = UUID.randomUUID();
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> receiptService.getDetail(staff, id))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
    }

    @Test
    void getDetail_직원이_타인_등록건_조회시_FORBIDDEN을_던진다() {
        Receipt receipt = receiptOf(adminId, Instant.now());
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> receiptService.getDetail(staff, receipt.getId()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    @Test
    void markPaid_관리자가_PENDING_영수증을_PAID로_전이한다() {
        Receipt receipt = receiptOf(staffId, Instant.now());
        UUID transactionId = UUID.randomUUID();
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));

        ReceiptDto.Response response = receiptService.markPaid(admin, receipt.getId(), new ReceiptDto.MarkPaidRequest(transactionId.toString()));

        assertThat(response.paymentStatus()).isEqualTo("PAID");
        assertThat(response.paidAt()).isNotNull();
    }

    @Test
    void markPaid_body가_없어도_PAID로_전이한다() {
        Receipt receipt = receiptOf(staffId, Instant.now());
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));

        ReceiptDto.Response response = receiptService.markPaid(admin, receipt.getId(), null);

        assertThat(response.paymentStatus()).isEqualTo("PAID");
    }

    @Test
    void markPaid_PENDING이_아니면_RECEIPT_NOT_PENDING을_던진다() {
        Receipt receipt = receiptOf(staffId, Instant.now());
        receipt.cancel();
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> receiptService.markPaid(admin, receipt.getId(), null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "RECEIPT_NOT_PENDING");
    }

    @Test
    void markPaid_직원이_호출하면_FORBIDDEN을_던진다() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> receiptService.markPaid(staff, id, null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "FORBIDDEN");

        verifyNoInteractions(receiptRepository);
    }

    @Test
    void cancel_관리자가_PENDING_영수증을_CANCELED로_전이한다() {
        Receipt receipt = receiptOf(staffId, Instant.now());
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));

        ReceiptDto.Response response = receiptService.cancel(admin, receipt.getId());

        assertThat(response.paymentStatus()).isEqualTo("CANCELED");
    }

    @Test
    void cancel_PENDING이_아니면_RECEIPT_NOT_PENDING을_던진다() {
        Receipt receipt = receiptOf(staffId, Instant.now());
        receipt.markPaid(null);
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> receiptService.cancel(admin, receipt.getId()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "RECEIPT_NOT_PENDING");
    }

    @Test
    void cancel_직원이_호출하면_FORBIDDEN을_던진다() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> receiptService.cancel(staff, id))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "FORBIDDEN");

        verifyNoInteractions(receiptRepository);
    }
}
