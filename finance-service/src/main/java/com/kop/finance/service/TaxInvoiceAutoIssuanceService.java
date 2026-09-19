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
        taxInvoice.replaceItems(toItemInputs(receipt.getItems()));
        taxInvoiceRepository.save(taxInvoice);
    }

    private List<TaxInvoice.ItemInput> toItemInputs(List<ReceiptItem> items) {
        return items.stream().map(i -> new TaxInvoice.ItemInput(i.getName(), i.getSpec(), i.getQuantity(), i.getUnitPrice())).toList();
    }
}
