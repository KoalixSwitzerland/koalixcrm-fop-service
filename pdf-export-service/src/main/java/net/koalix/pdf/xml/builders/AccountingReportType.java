package net.koalix.pdf.xml.builders;

import net.koalix.api.dto.AccountingPeriodReportDto;

/**
 * Which of the two accounting XSL schemas a given render targets.
 *
 * <p>The decision is made by comparing the incoming {@code template_set_id}
 * to the two template FKs on the AccountingPeriod row — Django exposes both
 * via {@link AccountingPeriodReportDto#templateSetBalanceSheet()} and
 * {@link AccountingPeriodReportDto#templateProfitLossStatement()}.
 */
public enum AccountingReportType {
    BALANCE_SHEET("koalixaccountingbalacesheet"),
    PROFIT_LOSS("koalixaccountingprofitlossstatement");

    private final String rootElement;

    AccountingReportType(String rootElement) {
        this.rootElement = rootElement;
    }

    public String rootElement() {
        return rootElement;
    }

    /**
     * Resolve which report type corresponds to {@code templateSetId} for the
     * given period. Throws if the ID matches neither template FK.
     */
    public static AccountingReportType resolve(AccountingPeriodReportDto period, long templateSetId) {
        Long bs = period.templateSetBalanceSheet();
        Long pl = period.templateProfitLossStatement();
        if (bs != null && bs == templateSetId) {
            return BALANCE_SHEET;
        }
        if (pl != null && pl == templateSetId) {
            return PROFIT_LOSS;
        }
        throw new IllegalStateException(
                "template_set_id=" + templateSetId + " matches neither template_set_balance_sheet"
                        + " (" + bs + ") nor template_profit_loss_statement (" + pl + ")"
                        + " on AccountingPeriod " + period.id());
    }
}
