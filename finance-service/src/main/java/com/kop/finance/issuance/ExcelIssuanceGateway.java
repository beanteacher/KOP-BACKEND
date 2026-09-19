package com.kop.finance.issuance;

import com.kop.finance.domain.TaxInvoice;
import com.kop.finance.domain.TaxInvoiceItem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;

/**
 * 03-feature-spec.md B-2 "출력 — 홈택스 엑셀 파일 규격"을 그대로 따른다: 세금계산서 1건 = 1행,
 * 품목은 최대 16개까지 칸을 반복한다(홈택스 bulk 업로드 양식 제한). 컬럼 이름·순서는 스펙 문서의
 * 표를 옮긴 것 — 실제 홈택스 공식 템플릿의 정확한 바이트 단위 규격까지 검증된 것은 아니다.
 * 처음 업로드해볼 때 홈택스가 요구하는 정확한 서식과 다르면 여기 컬럼 매핑을 조정해야 한다.
 */
@Component
public class ExcelIssuanceGateway implements TaxInvoiceIssuanceGateway {

    private static final int MAX_ITEMS = 16;
    private static final DateTimeFormatter ISSUE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public byte[] issue(TaxInvoice taxInvoice, TaxInvoiceSupplierInfo supplier) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("세금계산서");
            writeHeaderRow(sheet);
            writeInvoiceRow(sheet, taxInvoice, supplier);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("세금계산서 엑셀 생성에 실패했습니다", e);
        }
    }

    private void writeHeaderRow(Sheet sheet) {
        Row header = sheet.createRow(0);
        int col = 0;
        setString(header, col++, "종류");
        setString(header, col++, "공급자 등록번호");
        setString(header, col++, "공급자 상호");
        setString(header, col++, "공급받는자 등록번호");
        setString(header, col++, "공급받는자 상호");
        setString(header, col++, "작성일자");
        for (int i = 1; i <= MAX_ITEMS; i++) {
            setString(header, col++, "품목명" + i);
            setString(header, col++, "규격" + i);
            setString(header, col++, "수량" + i);
            setString(header, col++, "단가" + i);
            setString(header, col++, "공급가액" + i);
            setString(header, col++, "세액" + i);
        }
        setString(header, col++, "합계금액");
        setString(header, col, "세액합계");
    }

    private void writeInvoiceRow(Sheet sheet, TaxInvoice taxInvoice, TaxInvoiceSupplierInfo supplier) {
        Row row = sheet.createRow(1);
        int col = 0;
        setString(row, col++, "세금계산서");
        setString(row, col++, supplier.businessRegistrationNumber());
        setString(row, col++, supplier.name());
        setString(row, col++, taxInvoice.getClient().getBusinessRegistrationNumber());
        setString(row, col++, taxInvoice.getClient().getName());
        setString(row, col++, taxInvoice.getIssueDate().format(ISSUE_DATE_FORMAT));

        var items = taxInvoice.getItems();
        for (int i = 0; i < MAX_ITEMS; i++) {
            if (i < items.size()) {
                TaxInvoiceItem item = items.get(i);
                setString(row, col++, item.getName());
                setString(row, col++, item.getSpec() == null ? "" : item.getSpec());
                setNumber(row, col++, item.getQuantity());
                setNumber(row, col++, item.getUnitPrice());
                setNumber(row, col++, item.getSupplyAmount());
                setNumber(row, col++, item.getTaxAmount());
            } else {
                col += 6; // 빈 칸으로 남긴다
            }
        }
        setNumber(row, col++, taxInvoice.getSupplyAmount());
        setNumber(row, col, taxInvoice.getTaxAmount());
    }

    private void setString(Row row, int col, String value) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
    }

    private void setNumber(Row row, int col, Number value) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value.doubleValue());
    }
}
