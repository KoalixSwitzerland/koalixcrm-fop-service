package net.koalix.pdf.xml.builders;

import net.koalix.api.dto.AccountBookingSumsDto;
import net.koalix.api.dto.AccountingPeriodReportDto;
import net.koalix.pdf.xml.XmlAggregator;
import net.koalix.pdf.xml.builders.AccountingReportType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static net.koalix.pdf.xml.builders.AccountingReportType.BALANCE_SHEET;
import static net.koalix.pdf.xml.builders.AccountingReportType.PROFIT_LOSS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the accounting XML shape matches what balancesheet.xsl /
 * profitlossstatement.xsl expect:
 * <ul>
 *   <li>BALANCE_SHEET selects accountType A + L, uses sumThroughNow as currentValue.</li>
 *   <li>PROFIT_LOSS selects accountType E + S, uses sumWithinAccountingPeriod.</li>
 *   <li>TotalProfitLoss = overallEarnings - overallSpendings in both.</li>
 *   <li>Non-matching account types are filtered out.</li>
 * </ul>
 */
class AccountingXmlBuilderTest {

    private AccountingPeriodReportDto sampleReport() {
        return new AccountingPeriodReportDto(
                1L, "FY 2018", LocalDate.of(2018, 1, 1), LocalDate.of(2018, 12, 31),
                10L, 11L,
                new BigDecimal("500.00"), new BigDecimal("200.00"),
                new BigDecimal("350.00"), new BigDecimal("0.00"),
                List.of(
                        new AccountBookingSumsDto(100L, 1000, "Cash", "A",
                                new BigDecimal("250"), new BigDecimal("350"),
                                new BigDecimal("100"), new BigDecimal("350")),
                        new AccountBookingSumsDto(101L, 2000, "Loan", "L",
                                new BigDecimal("0"), new BigDecimal("0"),
                                new BigDecimal("0"), new BigDecimal("0")),
                        new AccountBookingSumsDto(102L, 4000, "Sales", "E",
                                new BigDecimal("500"), new BigDecimal("600"),
                                new BigDecimal("100"), new BigDecimal("600")),
                        new AccountBookingSumsDto(103L, 5000, "Rent", "S",
                                new BigDecimal("200"), new BigDecimal("250"),
                                new BigDecimal("50"), new BigDecimal("250"))
                ));
    }

    private XmlAggregator aggregator() {
        return new XmlAggregator(null, null, new AccountingXmlBuilder());
    }

    @Test
    void balanceSheet_selectsAssetsAndLiabilities_andUsesThroughNow() throws Exception {
        byte[] xml = aggregator().buildAccounting(
                sampleReport(), BALANCE_SHEET, "ACME SA", "/tmp/logo.jpg");
        String s = new String(xml, StandardCharsets.UTF_8);

        assertThat(s).contains("<koalixaccountingbalacesheet>");
        assertThat(s).contains("<organisationname>ACME SA</organisationname>");
        assertThat(s).contains("<headerpicture>/tmp/logo.jpg</headerpicture>");
        assertThat(s).contains("<Account accountType=\"A\">");
        assertThat(s).contains("<Account accountType=\"L\">");
        // Balance-sheet currentValue = sumThroughNow (350 on Cash).
        assertThat(s).contains("<AccountNumber>1000</AccountNumber>");
        assertThat(s).contains("<accountName>Cash</accountName>");
        assertThat(s).contains("<currentValue>350</currentValue>");
        // E/S accounts must be filtered out.
        assertThat(s).doesNotContain("accountType=\"E\"");
        assertThat(s).doesNotContain("accountType=\"S\"");
        // TotalProfitLoss = 500 - 200 = 300.
        assertThat(s).contains("<TotalProfitLoss>300.00</TotalProfitLoss>");
    }

    @Test
    void profitLoss_selectsEarningsAndSpendings_andUsesWithinPeriod() throws Exception {
        byte[] xml = aggregator().buildAccounting(
                sampleReport(), PROFIT_LOSS, "ACME SA", "/tmp/logo.jpg");
        String s = new String(xml, StandardCharsets.UTF_8);

        assertThat(s).contains("<koalixaccountingprofitlossstatement>");
        assertThat(s).contains("<Account accountType=\"E\">");
        assertThat(s).contains("<Account accountType=\"S\">");
        // Profit-loss currentValue = sumWithinAccountingPeriod (500 on Sales).
        assertThat(s).contains("<AccountNumber>4000</AccountNumber>");
        assertThat(s).contains("<currentValue>500</currentValue>");
        assertThat(s).doesNotContain("accountType=\"A\"");
        assertThat(s).doesNotContain("accountType=\"L\"");
        assertThat(s).contains("<TotalProfitLoss>300.00</TotalProfitLoss>");
    }

    @Test
    void missingOrganisationName_omitsElementRatherThanEmitBlank() throws Exception {
        byte[] xml = aggregator().buildAccounting(
                sampleReport(), BALANCE_SHEET, null, "/tmp/logo.jpg");
        String s = new String(xml, StandardCharsets.UTF_8);
        assertThat(s).doesNotContain("<organisationname>");
    }

    @Test
    void reportTypeResolve_throwsIfTemplateIdMatchesNeitherFk() {
        AccountingPeriodReportDto period = sampleReport();
        assertThatThrownBy(() -> AccountingReportType.resolve(period, 999L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("matches neither");
    }

    @Test
    void reportTypeResolve_picksBalanceSheetWhenTemplateMatches() {
        AccountingPeriodReportDto period = sampleReport();
        assertThat(AccountingReportType.resolve(period, 10L)).isEqualTo(BALANCE_SHEET);
        assertThat(AccountingReportType.resolve(period, 11L)).isEqualTo(PROFIT_LOSS);
    }
}
