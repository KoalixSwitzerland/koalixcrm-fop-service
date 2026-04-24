package net.koalix.pdf.xml.builders;

import net.koalix.api.dto.ProjectReportDto;
import net.koalix.api.dto.ReportingPeriodRefDto;
import net.koalix.api.dto.TaskReportDto;
import net.koalix.api.dto.UserExtensionRefDto;
import net.koalix.api.dto.WorkRowDto;
import net.koalix.pdf.xml.XmlAggregator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the project_report XML matches the field-as-element shape that
 * project_report.xsl actually queries (e.g. {@code object[@model='crm.project']/project_name},
 * NOT Django's stock {@code <field name="project_name">} wrapping).
 */
class ProjectReportXmlBuilderTest {

    private XmlAggregator aggregator() {
        return new XmlAggregator(null, null, null,
                new ProjectReportXmlBuilder(), new WorkReportXmlBuilder());
    }

    private ProjectReportDto report(boolean withPeriod) {
        return new ProjectReportDto(
                7L, "Demo Project", "desc", 5L, 11L, 1L, 99L,
                withPeriod ? new ReportingPeriodRefDto(
                        42L, "March 2025", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31)) : null,
                new UserExtensionRefDto(11L, 99L, "jdoe"),
                List.of(
                        new TaskReportDto(
                                1L, "Task A", "tdesc", 7L, 3L, LocalDate.of(2025, 1, 1),
                                List.of(new WorkRowDto(
                                        100L, 1L, 42L, 11L,
                                        LocalDate.of(2025, 3, 5), "short", "did stuff",
                                        new BigDecimal("4.50"))),
                                new BigDecimal("100"), new BigDecimal("0"),
                                new BigDecimal("8"),
                                withPeriod ? new BigDecimal("50") : null,
                                withPeriod ? new BigDecimal("4") : null,
                                new BigDecimal("12"), "5", "10")),
                new BigDecimal("100"), new BigDecimal("0"),
                new BigDecimal("8"),
                withPeriod ? new BigDecimal("50") : null,
                withPeriod ? new BigDecimal("4") : null,
                new BigDecimal("250"), "5", "10",
                "https://example.com/chart.svg");
    }

    @Test
    void overallReport_omitsInPeriodElements() throws Exception {
        byte[] bytes = aggregator().buildProjectReport(report(false), "chart.svg");
        String s = new String(bytes, StandardCharsets.UTF_8);

        assertThat(s).startsWith("<?xml");
        assertThat(s).contains("<koalixcrm-export>");
        assertThat(s).doesNotContain("crm.reportingperiod");
        assertThat(s).contains("<object model=\"crm.project\" pk=\"7\">");
        assertThat(s).contains("<project_name>Demo Project</project_name>");
        assertThat(s).contains("<Effective_Costs_Confirmed>100</Effective_Costs_Confirmed>");
        assertThat(s).contains("<Planned_Total_Costs>250</Planned_Total_Costs>");
        // No period scoping → none of the *_InPeriod elements emitted at any level.
        assertThat(s).doesNotContain("Effective_Costs_InPeriod");
        assertThat(s).doesNotContain("Effective_Effort_InPeriod");
        assertThat(s).contains("<project_cost_overview>chart.svg</project_cost_overview>");
        assertThat(s).contains("<object model=\"crm.task\" pk=\"1\">");
        assertThat(s).contains("<title>Task A</title>");
        assertThat(s).contains("<Planned_Effort>12</Planned_Effort>");
        assertThat(s).contains("<object model=\"crm.work\" pk=\"100\">");
        assertThat(s).contains("<task>1</task>");
        assertThat(s).contains("<description>did stuff</description>");
    }

    @Test
    void periodScopedReport_emitsReportingPeriodAndInPeriodElements() throws Exception {
        byte[] bytes = aggregator().buildProjectReport(report(true), "chart.svg");
        String s = new String(bytes, StandardCharsets.UTF_8);

        assertThat(s).contains("<object model=\"crm.reportingperiod\" pk=\"42\">");
        assertThat(s).contains("<title>March 2025</title>");
        // *_InPeriod present on both project and task.
        assertThat(s).contains("<Effective_Costs_InPeriod>50</Effective_Costs_InPeriod>");
        assertThat(s).contains("<Effective_Effort_InPeriod>4</Effective_Effort_InPeriod>");
    }

    @Test
    void chartElement_omittedWhenNoChartFilenameProvided() throws Exception {
        byte[] bytes = aggregator().buildProjectReport(report(false), null);
        String s = new String(bytes, StandardCharsets.UTF_8);
        assertThat(s).doesNotContain("project_cost_overview");
    }
}
