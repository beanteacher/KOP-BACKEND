package com.kop.finance.issuance;

import com.kop.finance.domain.Client;
import com.kop.finance.domain.ClientTaxType;
import com.kop.finance.domain.TaxInvoice;
import com.kop.finance.domain.TaxInvoiceStatus;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelIssuanceGatewayTest {

    ExcelIssuanceGateway gateway = new ExcelIssuanceGateway();

    private Client clientOf() {
        Client client = Client.register(
            UUID.randomUUID(), "1234567890", "한성식자재", "홍길동", "도소매", "식자재", "서울", null, null, null, ClientTaxType.GENERAL
        );
        ReflectionTestUtils.setField(client, "id", UUID.randomUUID());
        return client;
    }

    @Test
    void 헤더와_품목_금액이_올바른_엑셀을_만든다() throws IOException {
        Client client = clientOf();
        TaxInvoice taxInvoice = TaxInvoice.create(UUID.randomUUID(), UUID.randomUUID(), client, LocalDate.of(2026, 9, 11), TaxInvoiceStatus.COMPLETED, "9월 정기 납품");
        taxInvoice.replaceItems(List.of(
            new TaxInvoice.ItemInput("업소용 냉장고 900L", "2도어", 1, 1_800_000L),
            new TaxInvoice.ItemInput("설치비", null, 1, 150_000L)
        ));

        byte[] bytes = gateway.issue(taxInvoice, new TaxInvoiceSupplierInfo("9198271234", "영수증 검증 업체"));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("세금계산서");
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("종류");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("공급자 등록번호");
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("품목명1");

            Row row = sheet.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("세금계산서");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("9198271234");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("영수증 검증 업체");
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("1234567890");
            assertThat(row.getCell(4).getStringCellValue()).isEqualTo("한성식자재");
            assertThat(row.getCell(5).getStringCellValue()).isEqualTo("2026-09-11");

            // 품목1: 업소용 냉장고 900L
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("업소용 냉장고 900L");
            assertThat(row.getCell(7).getStringCellValue()).isEqualTo("2도어");
            assertThat(row.getCell(8).getNumericCellValue()).isEqualTo(1.0);
            assertThat(row.getCell(9).getNumericCellValue()).isEqualTo(1_800_000.0);
            assertThat(row.getCell(10).getNumericCellValue()).isEqualTo(1_800_000.0);
            assertThat(row.getCell(11).getNumericCellValue()).isEqualTo(180_000.0);

            // 품목2: 설치비 (빈 규격)
            assertThat(row.getCell(12).getStringCellValue()).isEqualTo("설치비");
            assertThat(row.getCell(13).getStringCellValue()).isEqualTo("");

            // 품목3 슬롯은 비어있어야 한다(등록 안 됨)
            assertThat(row.getCell(18)).isNull();

            // 합계금액·세액합계는 6(기본) + 16*6 = 102, 103번 칸
            assertThat(row.getCell(102).getNumericCellValue()).isEqualTo(1_950_000.0);
            assertThat(row.getCell(103).getNumericCellValue()).isEqualTo(195_000.0);
        }
    }
}
