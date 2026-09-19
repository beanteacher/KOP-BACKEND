package com.kop.finance.service;

import com.kop.finance.domain.Client;
import com.kop.finance.domain.Receipt;
import com.kop.finance.domain.ReceiptItem;
import com.kop.finance.domain.TaxInvoice;
import com.kop.finance.repository.ClientRepository;
import com.kop.finance.repository.TaxInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 입금 매칭(PaymentMatchingService)이나 관리자의 수동 "입금 확인"으로 영수증이 PAID가 되는
 * 순간, 거래처가 지정된 건이라면 세금계산서를 관리자 개입 없이 바로 만든다(로드맵 마지막 조각
 * — 03-feature-spec.md 모듈 A·B를 잇는 자동화). 거래처 없는(자유 입력) 영수증은 세금계산서를
 * 발행할 공급받는자 정보가 없어 조용히 건너뛴다 — 에러가 아니라 정상 경로다.
 *
 * 영수증 금액(receipt.amount)은 항상 "실제로 받기로/받은 최종 총액"이다(부가세 포함 여부와 무관하게
 * 등록자가 합의된 실제 금액을 그대로 입력) — 그래서 세금계산서로 옮길 때는 거래처 과세유형과
 * 상관없이 항상 그 금액을 부가세 포함 총액으로 보고 거꾸로 공급가액·세액을 나눈다
 * ({@link TaxInvoice#replaceItemsInclusive}). 일반과세자면 원래 받기로 한 부가세가 그 총액 안에
 * 이미 포함돼 있고, 간이과세자/면세/비영리처럼 부가세를 별도로 못 받은 거래처라면 실제로 받은
 * 금액에서 우리가 부가세를 떠안는 구조가 그대로 드러난다.
 */
@Service
@RequiredArgsConstructor
public class TaxInvoiceAutoIssuanceService {

    private final ClientRepository clientRepository;
    private final TaxInvoiceRepository taxInvoiceRepository;

    @Transactional
    public void issueIfEligible(Receipt receipt) {
        if (receipt.getClientId() == null) {
            return;
        }
        Client client = clientRepository.findByIdAndCompanyId(receipt.getClientId(), receipt.getCompanyId()).orElse(null);
        if (client == null) {
            return;
        }

        TaxInvoice taxInvoice = TaxInvoice.createFromReceipt(
            receipt.getCompanyId(), receipt.getCreatedBy(), client, receipt.getReceiptDate(),
            "입금 확인 자동발행 (영수증 " + receipt.getId() + ")", receipt.getId()
        );
        taxInvoice.replaceItemsInclusive(toItemInputs(receipt.getItems()));
        taxInvoiceRepository.save(taxInvoice);
    }

    private List<TaxInvoice.ItemInput> toItemInputs(List<ReceiptItem> items) {
        return items.stream().map(i -> new TaxInvoice.ItemInput(i.getName(), i.getSpec(), i.getQuantity(), i.getUnitPrice())).toList();
    }
}
