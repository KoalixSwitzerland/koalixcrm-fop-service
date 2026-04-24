package net.koalix.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Self-contained snapshot for the two accounting FOP reports
 * (balance sheet / profit-loss statement), returned by
 * {@code GET /accounting-periods/<id>/report-data/}.
 *
 * <p>Includes both template FKs so the worker can decide — by comparing
 * to the incoming {@code template_set_id} — whether the job is rendering
 * the balance-sheet schema or the profit-loss schema.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountingPeriodReportDto(
        Long id,
        String title,
        LocalDate begin,
        LocalDate end,
        Long templateSetBalanceSheet,
        Long templateProfitLossStatement,
        BigDecimal overallEarnings,
        BigDecimal overallSpendings,
        BigDecimal overallAssets,
        BigDecimal overallLiabilities,
        List<AccountBookingSumsDto> accounts
) {
}
