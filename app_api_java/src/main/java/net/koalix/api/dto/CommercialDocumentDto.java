package net.koalix.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Generic nested shape shared by every commercial-document endpoint
 * (post-v2.0.0 / issue #395 G3).
 *
 * <p>The {@code type} field ({@code "Invoice"}, {@code "Quotation"}, ...)
 * tells the dispatcher in {@code pdf-export-service} which
 * {@code XmlBuilder<T>} to pick. Subclass-specific fields live in
 * {@code extra} (payable_until, valid_until, tracking_reference,
 * iteration_number, corrects_invoice, issue_date, reason, ...) to avoid a
 * seven-way subclass hierarchy on the Java side. {@code extra} is populated
 * by {@link JsonAnySetter}: every JSON property the nested serializer emits
 * that does not bind to a named component above is collected here under its
 * raw (snake_case) key, so the XSL reads it as
 * {@code commercial_document/extra/payable_until}.
 *
 * <p>Party semantics: every commercial document carries exactly one
 * {@code party} — the buyer for sales-side documents (Invoice, Quotation,
 * CreditNote, DespatchAdvice, PaymentReminder) and the supplier for
 * PurchaseOrder. The legacy separate {@code customer}/{@code supplier}
 * blocks were dropped in v2.0.0.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommercialDocumentDto(
        Long id,
        String type,
        Long contract,
        NestedPartyDto party,
        Long staff,
        CurrencyDto currency,
        // The nested serializer emits the customer's reference as
        // `party_reference` (renamed from the legacy `external_reference` in
        // v2.0.0); the invoice's "your reference" line reads it via
        // commercial_document/external_reference, so keep that element name.
        @JsonProperty("party_reference") String externalReference,
        String description,
        BigDecimal discount,
        LocalDate lastPricingDate,
        BigDecimal lastCalculatedPrice,
        BigDecimal lastCalculatedTax,
        OffsetDateTime dateOfCreation,
        OffsetDateTime lastModification,
        LocalDate customDateField,
        Long templateSet,
        List<CommercialDocumentPositionDto> items,
        List<TaxSummaryEntry> taxSummary,
        List<TextParagraphDto> textParagraphs,
        Long userExtension,
        @JsonAnySetter Map<String, Object> extra
) {
}
