package com.kop.finance.domain;

/** 03-feature-spec.md B-2~B-4, 05-database-schema.md finance.tax_invoices.status. */
public enum TaxInvoiceStatus {
    DRAFT,
    COMPLETED,
    EXCEL_DOWNLOADED,
    REVISED,
    CANCELED
}
