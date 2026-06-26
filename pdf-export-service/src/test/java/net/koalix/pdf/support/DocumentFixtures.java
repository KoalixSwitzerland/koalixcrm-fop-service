package net.koalix.pdf.support;

import net.koalix.api.dto.AccountBookingSumsDto;
import net.koalix.api.dto.AccountingPeriodReportDto;
import net.koalix.api.dto.CommercialDocumentDto;
import net.koalix.api.dto.CommercialDocumentPositionDto;
import net.koalix.api.dto.CurrencyDto;
import net.koalix.api.dto.DocumentTemplateDto;
import net.koalix.api.dto.NestedEmailAssignmentDto;
import net.koalix.api.dto.NestedPartyDto;
import net.koalix.api.dto.NestedPartyDto.NestedOrganizationBlock;
import net.koalix.api.dto.NestedPhoneAssignmentDto;
import net.koalix.api.dto.NestedPostalAddressDto;
import net.koalix.api.dto.ProductTypeDto;
import net.koalix.api.dto.TaxSummaryEntry;
import net.koalix.api.dto.TextParagraphDto;
import net.koalix.api.dto.UserDto;
import net.koalix.api.dto.UserExtensionDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Canonical DTO fixtures for integration tests. Minimal but non-null for
 * every required field so XML aggregation + XSL transform have something to
 * traverse.
 *
 * <p>Keep these in sync with the DTO records in {@code app_api_java} —
 * a record ctor change will surface here at compile time.
 */
public final class DocumentFixtures {

    private DocumentFixtures() {}

    public static CommercialDocumentDto invoice() {
        return new CommercialDocumentDto(
                17L, "Invoice", 12L,
                new NestedPartyDto(
                        42L,
                        "ACME SA",
                        "organization",
                        new NestedOrganizationBlock("ACME SA", "ag", null, "CH"),
                        null,
                        List.of(new NestedPostalAddressDto(
                                1L, "billing", Boolean.TRUE, null, null,
                                "Bahnhofstrasse 1", null, null, null,
                                "8001", "Zurich", null, "CH", null)),
                        List.of(), List.of()),
                5L,
                new CurrencyDto(1L, "Swiss Franc", "CHF", new BigDecimal("0.05")),
                "EXT-1", "Integration test invoice",
                BigDecimal.ZERO, null,
                new BigDecimal("1200.00"), new BigDecimal("97.20"),
                null, null, null, 3L,
                List.of(new CommercialDocumentPositionDto(
                        201L, 10, "Consulting hour", new BigDecimal("10.00"),
                        null,
                        new ProductTypeDto(301L, "CONS-H", "Consulting hour", null, null, null, "8.1"),
                        BigDecimal.ZERO, new BigDecimal("120.00"),
                        null,
                        null,
                        new BigDecimal("1200.00"), new BigDecimal("97.20"),
                        Boolean.FALSE)),
                List.of(new TaxSummaryEntry("8.1", "1200.00", "97.20")),
                List.of(
                        new TextParagraphDto("BS", "Sehr geehrte Damen und Herren"),
                        new TextParagraphDto("AS", "wir erlauben uns, Ihnen folgende Leistungen zu verrechnen."),
                        new TextParagraphDto("AT", "Vielen Dank fuer Ihren Auftrag.")),
                7L,
                // Subclass-specific fields the InvoiceNestedSerializer emits at
                // top level; they arrive here via @JsonAnySetter in production.
                Map.of("payable_until", "2025-02-15", "iteration_number", 1));
    }

    /**
     * DocumentTemplate fixture carrying the header/footer chrome the XSL prints
     * via the {@code <document_meta>} block. {@code logoHref} is null so the
     * aggregator emits no {@code logo_filename} (the IT has no logo asset).
     */
    public static DocumentTemplateDto documentTemplate() {
        return new DocumentTemplateDto(
                3L, "Default invoice template",
                "https://crm.example/koalixcrm_core/api/v1/1/document-templates/3/xsl/",
                "https://crm.example/koalixcrm_core/api/v1/1/document-templates/3/fop-config/",
                null,
                "Irgendeine Firma GmbH, Irgendwostrasse 12, CH-8000 Zuerich",
                "Irgendeine Firma GmbH",
                "www.koalix.org",
                "CHF IBAN CH00 0000 0000 0000 0000 0");
    }

    /**
     * Accounting-period snapshot with asset/liability (balance sheet) and
     * earnings/spending (profit-loss) accounts. {@code sumThroughNow} drives
     * the balance sheet, {@code sumWithinAccountingPeriod} the P&amp;L; overall
     * earnings 50000 − spendings 12000 = TotalProfitLoss 38000 ("Gewinn").
     */
    public static AccountingPeriodReportDto accountingPeriod() {
        return new AccountingPeriodReportDto(
                9L, "FY2025", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31),
                100L, 200L,
                new BigDecimal("50000.00"), new BigDecimal("12000.00"),
                new BigDecimal("15400.00"), new BigDecimal("5000.00"),
                List.of(
                        new AccountBookingSumsDto(1L, 1000, "Cash", "A",
                                BigDecimal.ZERO, new BigDecimal("12000.00"), BigDecimal.ZERO, new BigDecimal("12000.00")),
                        new AccountBookingSumsDto(2L, 1100, "Receivables", "A",
                                BigDecimal.ZERO, new BigDecimal("3400.00"), BigDecimal.ZERO, new BigDecimal("3400.00")),
                        new AccountBookingSumsDto(3L, 2000, "Payables", "L",
                                BigDecimal.ZERO, new BigDecimal("5000.00"), BigDecimal.ZERO, new BigDecimal("5000.00")),
                        new AccountBookingSumsDto(4L, 3000, "Sales", "E",
                                new BigDecimal("50000.00"), new BigDecimal("50000.00"), BigDecimal.ZERO, new BigDecimal("50000.00")),
                        new AccountBookingSumsDto(5L, 4000, "Rent", "S",
                                new BigDecimal("12000.00"), new BigDecimal("12000.00"), BigDecimal.ZERO, new BigDecimal("12000.00"))));
    }

    public static UserExtensionDto userExtension() {
        return new UserExtensionDto(
                7L,
                new UserDto(5L, "a.riedener", "Aaron", "Riedener", "a@example.com"),
                3L,
                new CurrencyDto(1L, null, "CHF", null),
                List.of(), List.of(), List.of());
    }

    /**
     * UserExtension fixture with a fully-populated multi-line address, phone,
     * and email — exercises address_line_3/4, state, subdivision_code, and the
     * is_primary + valid_from/valid_to fields introduced in issue #396.
     */
    public static UserExtensionDto userExtensionFull() {
        return new UserExtensionDto(
                8L,
                new UserDto(6L, "b.muster", "Beat", "Muster", "b@example.com"),
                3L,
                new CurrencyDto(1L, null, "CHF", null),
                List.of(new NestedPostalAddressDto(
                        2L, "primary", Boolean.TRUE,
                        LocalDate.of(2024, 1, 1), null,
                        "c/o QuantalQ AG", "Technopark", "Floor 4", "Room 42",
                        "8005", "Zurich", "ZH", "CH", "CH-ZH")),
                List.of(new NestedPhoneAssignmentDto(
                        3L, "primary", Boolean.TRUE, null, null, "+41441234567")),
                List.of(new NestedEmailAssignmentDto(
                        4L, "primary", Boolean.TRUE, null, null, "b@example.com")));
    }
}
