package net.koalix.pdf.xml.builders;

import net.koalix.api.dto.AccountBookingSumsDto;
import net.koalix.api.dto.AccountingPeriodReportDto;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.math.BigDecimal;
import java.util.Set;

import static net.koalix.pdf.xml.XmlWriteSupport.writeAttribute;
import static net.koalix.pdf.xml.XmlWriteSupport.writeText;

/**
 * Writes the XML consumed by the accounting XSL-FO templates
 * ({@code balancesheet.xsl} / {@code profitlossstatement.xsl}).
 *
 * <p>Element names match the XSL's XPath expressions verbatim:
 * <pre>
 *   &lt;koalixaccountingbalacesheet&gt;                              (or ...profitlossstatement)
 *     &lt;organisationname&gt;...&lt;/organisationname&gt;
 *     &lt;headerpicture&gt;/path/to/logo&lt;/headerpicture&gt;
 *     &lt;Account accountType="A"&gt;
 *       &lt;AccountNumber&gt;1000&lt;/AccountNumber&gt;
 *       &lt;accountName&gt;Cash&lt;/accountName&gt;
 *       &lt;currentValue&gt;1234.00&lt;/currentValue&gt;
 *     &lt;/Account&gt;
 *     ...
 *     &lt;TotalProfitLoss&gt;...&lt;/TotalProfitLoss&gt;
 *   &lt;/koalixaccountingbalacesheet&gt;
 * </pre>
 *
 * <p>Account filtering differs by report type:
 * <ul>
 *   <li>{@link AccountingReportType#BALANCE_SHEET} — asset (A) + liability (L)
 *       accounts. {@code currentValue} = {@code sumThroughNow}.</li>
 *   <li>{@link AccountingReportType#PROFIT_LOSS} — earnings (E) + spendings (S)
 *       accounts. {@code currentValue} = {@code sumWithinAccountingPeriod}.</li>
 * </ul>
 *
 * <p>{@code TotalProfitLoss} = {@code overallEarnings - overallSpendings} in
 * both reports.
 */
@Component
public class AccountingXmlBuilder {

    private static final Set<String> BALANCE_SHEET_TYPES = Set.of("A", "L");
    private static final Set<String> PROFIT_LOSS_TYPES = Set.of("E", "S");

    public void write(XMLStreamWriter writer,
                      AccountingPeriodReportDto period,
                      AccountingReportType type,
                      String organisationName,
                      String headerPicturePath) throws XMLStreamException {
        writer.writeStartElement(type.rootElement());
        writeText(writer, "organisationname", organisationName);
        writeText(writer, "headerpicture", headerPicturePath);

        Set<String> filter = (type == AccountingReportType.BALANCE_SHEET)
                ? BALANCE_SHEET_TYPES : PROFIT_LOSS_TYPES;

        for (AccountBookingSumsDto account : period.accounts()) {
            if (!filter.contains(account.accountType())) {
                continue;
            }
            writer.writeStartElement("Account");
            writeAttribute(writer, "accountType", account.accountType());
            writeText(writer, "AccountNumber", account.accountNumber());
            writeText(writer, "accountName", account.title());
            writeText(writer, "currentValue", currentValue(account, type));
            writer.writeEndElement();
        }

        writeText(writer, "TotalProfitLoss", totalProfitLoss(period));
        writer.writeEndElement();
    }

    private BigDecimal currentValue(AccountBookingSumsDto account, AccountingReportType type) {
        return type == AccountingReportType.BALANCE_SHEET
                ? account.sumThroughNow()
                : account.sumWithinAccountingPeriod();
    }

    private BigDecimal totalProfitLoss(AccountingPeriodReportDto period) {
        BigDecimal earnings = period.overallEarnings() == null ? BigDecimal.ZERO : period.overallEarnings();
        BigDecimal spendings = period.overallSpendings() == null ? BigDecimal.ZERO : period.overallSpendings();
        return earnings.subtract(spendings);
    }
}
