package com.kitchensys.finance.service;

import com.kitchensys.finance.domain.Receipt;
import com.kitchensys.finance.domain.ReceiptCategory;
import com.kitchensys.finance.dto.ReceiptDto;
import com.kitchensys.finance.repository.ReceiptRepository;
import com.kitchensys.finance.security.EmployeePrincipal;
import com.kitchensys.common.exception.BusinessException;
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

    private Receipt receiptOf(UUID createdBy, Instant createdAt) {
        Receipt receipt = Receipt.register(companyId, createdBy, LocalDate.now(), 10000L, "한성식자재", null, ReceiptCategory.MATERIAL, null);
        ReflectionTestUtils.setField(receipt, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(receipt, "createdAt", createdAt);
        return receipt;
    }

    @Test
    void register_정상적으로_등록된다() {
        var request = new ReceiptDto.RegisterRequest(LocalDate.now(), 85000L, "한성식자재", null, "MATERIAL", "9월 재료비");
        when(receiptRepository.countInPeriod(eq(companyId), any(), any())).thenReturn(5L);
        when(receiptRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Receipt r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(r, "createdAt", Instant.now());
            return r;
        });

        ReceiptDto.Response response = receiptService.register(staff, request);

        assertThat(response.amount()).isEqualTo(85000L);
        assertThat(response.category()).isEqualTo("MATERIAL");
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void register_FREE_플랜_월20건_초과시_한도초과_예외를_던진다() {
        var request = new ReceiptDto.RegisterRequest(LocalDate.now(), 85000L, "한성식자재", null, "MATERIAL", null);
        when(receiptRepository.countInPeriod(eq(companyId), any(), any())).thenReturn(20L);

        assertThatThrownBy(() -> receiptService.register(staff, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "RECEIPT_PLAN_LIMIT_EXCEEDED");

        verify(receiptRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_지원하지_않는_카테고리면_VALIDATION_ERROR를_던진다() {
        var request = new ReceiptDto.RegisterRequest(LocalDate.now(), 85000L, "한성식자재", null, "INVALID", null);
        when(receiptRepository.countInPeriod(eq(companyId), any(), any())).thenReturn(0L);

        assertThatThrownBy(() -> receiptService.register(staff, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");
    }

    @Test
    void list_직원은_본인_등록건만_조회한다() {
        Page<Receipt> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(receiptRepository.search(eq(companyId), any(), any(), isNull(), isNull(), eq(staffId), any())).thenReturn(page);
        when(receiptRepository.sumAmount(eq(companyId), any(), any(), isNull(), isNull(), eq(staffId))).thenReturn(0L);

        receiptService.list(staff, LocalDate.now().withDayOfMonth(1), LocalDate.now(), null, null, null, PageRequest.of(0, 20));

        verify(receiptRepository).search(eq(companyId), any(), any(), isNull(), isNull(), eq(staffId), any());
    }

    @Test
    void list_관리자는_등록자_필터를_지정하지_않으면_전체를_조회한다() {
        Page<Receipt> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(receiptRepository.search(eq(companyId), any(), any(), isNull(), isNull(), isNull(), any())).thenReturn(page);
        when(receiptRepository.sumAmount(eq(companyId), any(), any(), isNull(), isNull(), isNull())).thenReturn(0L);

        receiptService.list(admin, LocalDate.now().withDayOfMonth(1), LocalDate.now(), null, null, null, PageRequest.of(0, 20));

        verify(receiptRepository).search(eq(companyId), any(), any(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void update_직원이_본인_등록건을_24시간_이내_수정한다() {
        Receipt receipt = receiptOf(staffId, Instant.now().minusSeconds(3600));
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));
        var request = new ReceiptDto.UpdateRequest(LocalDate.now(), 5000L, "새거래처", null, "OTHER", null);

        ReceiptDto.Response response = receiptService.update(staff, receipt.getId(), request);

        assertThat(response.amount()).isEqualTo(5000L);
        assertThat(response.vendorName()).isEqualTo("새거래처");
    }

    @Test
    void update_직원이_24시간_초과건_수정시_FORBIDDEN을_던진다() {
        Receipt receipt = receiptOf(staffId, Instant.now().minus(java.time.Duration.ofHours(25)));
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));
        var request = new ReceiptDto.UpdateRequest(LocalDate.now(), 5000L, "새거래처", null, "OTHER", null);

        assertThatThrownBy(() -> receiptService.update(staff, receipt.getId(), request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    @Test
    void update_직원이_타인_등록건_수정시_FORBIDDEN을_던진다() {
        Receipt receipt = receiptOf(adminId, Instant.now());
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));
        var request = new ReceiptDto.UpdateRequest(LocalDate.now(), 5000L, "새거래처", null, "OTHER", null);

        assertThatThrownBy(() -> receiptService.update(staff, receipt.getId(), request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    @Test
    void update_관리자는_기간_제한없이_수정한다() {
        Receipt receipt = receiptOf(staffId, Instant.now().minus(java.time.Duration.ofDays(10)));
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));
        var request = new ReceiptDto.UpdateRequest(LocalDate.now(), 5000L, "새거래처", null, "OTHER", null);

        ReceiptDto.Response response = receiptService.update(admin, receipt.getId(), request);

        assertThat(response.amount()).isEqualTo(5000L);
    }

    @Test
    void delete_소프트_삭제한다() {
        Receipt receipt = receiptOf(staffId, Instant.now());
        when(receiptRepository.findByIdAndCompanyIdAndDeletedAtIsNull(receipt.getId(), companyId)).thenReturn(Optional.of(receipt));

        receiptService.delete(staff, receipt.getId());

        assertThat(receipt.getDeletedAt()).isNotNull();
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
}
