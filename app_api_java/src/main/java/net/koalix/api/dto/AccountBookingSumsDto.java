package net.koalix.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Period-scoped booking aggregates for a single account, as produced by
 * {@code GET /accounting-periods/<id>/report-data/} (nested) and
 * {@code GET /accounts/<id>/booking-sums/?accounting_period=<id>}.
 *
 * <p>All four sums are computed server-side by Django against the booking
 * table so the Java service never has to replicate the sign-flip rules for
 * earnings/liability accounts — whatever Django returns is authoritative.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountBookingSumsDto(
        Long id,
        Integer accountNumber,
        String title,
        String accountType,
        BigDecimal sumWithinAccountingPeriod,
        BigDecimal sumThroughNow,
        BigDecimal sumBeforeAccountingPeriod,
        BigDecimal sumTotal
) {
}
