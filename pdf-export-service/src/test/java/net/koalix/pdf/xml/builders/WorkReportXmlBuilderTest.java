package net.koalix.pdf.xml.builders;

import net.koalix.api.dto.BucketAggregateDto;
import net.koalix.api.dto.HumanResourceWorkReportDto;
import net.koalix.api.dto.ProjectRefDto;
import net.koalix.api.dto.WorkRowDto;
import net.koalix.pdf.xml.XmlAggregator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the work_report XML matches what work_report.xsl pivots over:
 * synthetic user_extension/user element, range markers with calendar
 * attributes, and the four bucket flavours under the userextension object.
 */
class WorkReportXmlBuilderTest {

    private XmlAggregator aggregator() {
        return new XmlAggregator(null, null, null,
                new ProjectReportXmlBuilder(), new WorkReportXmlBuilder());
    }

    private HumanResourceWorkReportDto sample() {
        return new HumanResourceWorkReportDto(
                42L, 7L, "jdoe",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                List.of(new ProjectRefDto(11L, "Demo Project")),
                List.of(new WorkRowDto(
                        100L, 1L, 5L, 42L,
                        LocalDate.of(2026, 3, 15), "short", "did stuff",
                        new BigDecimal("8.00"))),
                // day buckets
                List.of(new BucketAggregateDto("8", null, "15", "11", "7", "3", "2026")),
                // day-project buckets
                List.of(new BucketAggregateDto("8", 11L, "15", "11", "7", "3", "2026")),
                // week buckets
                List.of(new BucketAggregateDto("40", null, null, "11", null, null, "2026")),
                // week-project buckets
                List.of(new BucketAggregateDto("40", 11L, null, "11", null, null, "2026")),
                // month buckets
                List.of(new BucketAggregateDto("160", null, null, null, null, "3", "2026")),
                // month-project buckets
                List.of(new BucketAggregateDto("160", 11L, null, null, null, "3", "2026"))
        );
    }

    @Test
    void emitsUserExtensionRangeBucketsAndWorks() throws Exception {
        String s = new String(aggregator().buildWorkReport(sample()), StandardCharsets.UTF_8);

        assertThat(s).contains("<koalixcrm-export>");
        // Synthetic user_extension element the XSL queries via XPath.
        assertThat(s).contains("<user_extension><user>7</user></user_extension>");
        // auth.user object so the username can be looked up by pk.
        assertThat(s).contains("<object model=\"auth.user\" pk=\"7\">");
        assertThat(s).contains("<username>jdoe</username>");
        // Range markers with the calendar attributes the XSL reads.
        assertThat(s).contains("<range_from");
        assertThat(s).contains("month=\"3\"");
        assertThat(s).contains("year=\"2026\"");
        assertThat(s).contains(">2026-03-01</range_from>");
        assertThat(s).contains(">2026-03-31</range_to>");
        // Project ref for the pivot.
        assertThat(s).contains("<object model=\"crm.project\" pk=\"11\">");
        assertThat(s).contains("<project_name>Demo Project</project_name>");
        // Userextension wrapper with all six bucket flavours.
        assertThat(s).contains("<object model=\"djangoUserExtension.userextension\" pk=\"42\">");
        assertThat(s).contains("<Day_Work_Hours");
        assertThat(s).contains("<Day_Project_Work_Hours");
        assertThat(s).contains("project=\"11\"");
        assertThat(s).contains("<Week_Work_Hours");
        assertThat(s).contains("<Week_Project_Work_Hours");
        assertThat(s).contains("<Month_Work_Hours");
        assertThat(s).contains("<Month_Project_Work_Hours");
        // Work row.
        assertThat(s).contains("<object model=\"crm.work\" pk=\"100\">");
        assertThat(s).contains("<task>1</task>");
        assertThat(s).contains("<description>did stuff</description>");
    }

    @Test
    void omitsAttributesNotApplicableToBucketFlavour() throws Exception {
        // A week bucket carries year+week but no day/month/week_day attrs.
        var report = new HumanResourceWorkReportDto(
                1L, 1L, "u", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7),
                List.of(), List.of(),
                List.of(),
                List.of(),
                List.of(new BucketAggregateDto("0", null, null, "1", null, null, "2026")),
                List.of(),
                List.of(),
                List.of());
        String s = new String(aggregator().buildWorkReport(report), StandardCharsets.UTF_8);
        // No day=, month=, week_day= on the Week_Work_Hours element.
        int weekIdx = s.indexOf("<Week_Work_Hours");
        int weekClose = s.indexOf(">", weekIdx);
        String weekTag = s.substring(weekIdx, weekClose + 1);
        assertThat(weekTag).contains("week=\"1\"");
        assertThat(weekTag).contains("year=\"2026\"");
        assertThat(weekTag).doesNotContain("day=");
        assertThat(weekTag).doesNotContain("month=");
        assertThat(weekTag).doesNotContain("week_day=");
    }
}
