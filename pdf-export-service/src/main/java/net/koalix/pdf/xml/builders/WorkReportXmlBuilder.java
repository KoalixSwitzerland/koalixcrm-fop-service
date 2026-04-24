package net.koalix.pdf.xml.builders;

import net.koalix.api.dto.BucketAggregateDto;
import net.koalix.api.dto.HumanResourceWorkReportDto;
import net.koalix.api.dto.ProjectRefDto;
import net.koalix.api.dto.WorkRowDto;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import static net.koalix.pdf.xml.XmlWriteSupport.writeAttribute;
import static net.koalix.pdf.xml.XmlWriteSupport.writeText;

/**
 * Writes the XML consumed by {@code work_report.xsl}.
 *
 * <p>Element shape (siblings under {@code <koalixcrm-export>}):
 * <pre>
 *   &lt;user_extension&gt;&lt;user&gt;{user_pk}&lt;/user&gt;&lt;/user_extension&gt;
 *   &lt;object model="auth.user" pk="…"&gt;&lt;username&gt;…&lt;/username&gt;&lt;/object&gt;
 *   &lt;range_from day="…" week="…" week_day="…" month="…" year="…"&gt;YYYY-MM-DD&lt;/range_from&gt;
 *   &lt;range_to … &gt;YYYY-MM-DD&lt;/range_to&gt;
 *   &lt;object model="crm.project" pk="…"&gt;&lt;project_name&gt;…&lt;/project_name&gt;&lt;/object&gt;
 *   &lt;object model="djangoUserExtension.userextension" pk="…"&gt;
 *     &lt;Day_Work_Hours day="…" week="…" week_day="…" month="…" year="…"&gt;7.5&lt;/Day_Work_Hours&gt;
 *     &lt;Day_Project_Work_Hours … project="…"&gt;…&lt;/Day_Project_Work_Hours&gt;
 *     &lt;Week_Work_Hours week="…" year="…"&gt;…&lt;/Week_Work_Hours&gt;
 *     &lt;Week_Project_Work_Hours … project="…"&gt;…&lt;/Week_Project_Work_Hours&gt;
 *     &lt;Month_Work_Hours month="…" year="…"&gt;…&lt;/Month_Work_Hours&gt;
 *     &lt;Month_Project_Work_Hours … project="…"&gt;…&lt;/Month_Project_Work_Hours&gt;
 *   &lt;/object&gt;
 *   &lt;object model="crm.work" pk="…"&gt; … &lt;/object&gt; (one per Work in range)
 * </pre>
 */
@Component
public class WorkReportXmlBuilder {

    public void write(XMLStreamWriter writer, HumanResourceWorkReportDto report)
            throws XMLStreamException {
        // Synthetic <user_extension><user>pk</user></user_extension> — the XSL
        // reads `user_extension/user` as a stand-alone XPath at the root.
        writer.writeStartElement("user_extension");
        writeText(writer, "user", report.userId());
        writer.writeEndElement();

        writer.writeStartElement("object");
        writeAttribute(writer, "model", "auth.user");
        writeAttribute(writer, "pk", report.userId());
        writeText(writer, "username", report.username());
        writer.writeEndElement();

        writeRange(writer, "range_from", report.rangeFrom());
        writeRange(writer, "range_to", report.rangeTo());

        if (report.projects() != null) {
            for (ProjectRefDto project : report.projects()) {
                writer.writeStartElement("object");
                writeAttribute(writer, "model", "crm.project");
                writeAttribute(writer, "pk", project.id());
                writeText(writer, "project_name", project.projectName());
                writer.writeEndElement();
            }
        }

        writer.writeStartElement("object");
        writeAttribute(writer, "model", "djangoUserExtension.userextension");
        writeAttribute(writer, "pk", report.id());
        writeBuckets(writer, "Day_Work_Hours", report.dayBuckets(), false);
        writeBuckets(writer, "Day_Project_Work_Hours", report.dayProjectBuckets(), true);
        writeBuckets(writer, "Week_Work_Hours", report.weekBuckets(), false);
        writeBuckets(writer, "Week_Project_Work_Hours", report.weekProjectBuckets(), true);
        writeBuckets(writer, "Month_Work_Hours", report.monthBuckets(), false);
        writeBuckets(writer, "Month_Project_Work_Hours", report.monthProjectBuckets(), true);
        writer.writeEndElement();

        if (report.works() != null) {
            for (WorkRowDto work : report.works()) {
                writer.writeStartElement("object");
                writeAttribute(writer, "model", "crm.work");
                writeAttribute(writer, "pk", work.id());
                writeText(writer, "task", work.task());
                writeText(writer, "reporting_period", work.reportingPeriod());
                writeText(writer, "human_resource", work.humanResource());
                writeText(writer, "date", work.date());
                writeText(writer, "short_description", work.shortDescription());
                writeText(writer, "description", work.description());
                writeText(writer, "worked_hours", work.workedHours());
                writer.writeEndElement();
            }
        }
    }

    private void writeRange(XMLStreamWriter writer, String name, java.time.LocalDate date)
            throws XMLStreamException {
        if (date == null) {
            return;
        }
        java.time.temporal.WeekFields weekFields = java.time.temporal.WeekFields.ISO;
        writer.writeStartElement(name);
        writeAttribute(writer, "day", String.valueOf(date.getDayOfMonth()));
        writeAttribute(writer, "week", String.valueOf(date.get(weekFields.weekOfWeekBasedYear())));
        writeAttribute(writer, "week_day", String.valueOf(date.getDayOfWeek().getValue()));
        writeAttribute(writer, "month", String.valueOf(date.getMonthValue()));
        writeAttribute(writer, "year", String.valueOf(date.getYear()));
        writer.writeCharacters(date.toString());
        writer.writeEndElement();
    }

    private void writeBuckets(XMLStreamWriter writer, String name,
                              java.util.List<BucketAggregateDto> buckets,
                              boolean withProject) throws XMLStreamException {
        if (buckets == null) {
            return;
        }
        for (BucketAggregateDto b : buckets) {
            writer.writeStartElement(name);
            // Skip null attrs — writeAttribute already does that.
            writeAttribute(writer, "day", b.day());
            writeAttribute(writer, "week", b.week());
            writeAttribute(writer, "week_day", b.weekDay());
            writeAttribute(writer, "month", b.month());
            writeAttribute(writer, "year", b.year());
            if (withProject) {
                writeAttribute(writer, "project", b.projectId());
            }
            writer.writeCharacters(b.effort() == null ? "" : b.effort());
            writer.writeEndElement();
        }
    }
}
