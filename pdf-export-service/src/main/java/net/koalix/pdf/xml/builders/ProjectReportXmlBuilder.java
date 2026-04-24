package net.koalix.pdf.xml.builders;

import net.koalix.api.dto.ProjectReportDto;
import net.koalix.api.dto.TaskReportDto;
import net.koalix.api.dto.WorkRowDto;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import static net.koalix.pdf.xml.XmlWriteSupport.writeAttribute;
import static net.koalix.pdf.xml.XmlWriteSupport.writeText;

/**
 * Writes the XML consumed by {@code project_report.xsl} (and the
 * reporting-period flavour of the same XSL).
 *
 * <p>The XSL queries field-as-element shapes such as
 * {@code object[@model='crm.project']/project_name} — i.e. every "field"
 * is a direct child element of {@code <object>}, NOT wrapped in
 * {@code <field name="...">} as Django's stock XML serializer would emit.
 * Aggregate elements ({@code Effective_Costs_Confirmed} etc.) are
 * children of the same {@code <object>} sibling list, mirroring what the
 * legacy {@code PDFExport.append_element_to_pattern} produced.
 *
 * <p>The caller is responsible for writing the surrounding
 * {@code <koalixcrm-export>} root via
 * {@link net.koalix.pdf.xml.XmlAggregator#buildProjectReport}.
 */
@Component
public class ProjectReportXmlBuilder {

    public void write(XMLStreamWriter writer, ProjectReportDto report,
                      String chartLocalFilename) throws XMLStreamException {
        if (report.reportingPeriod() != null) {
            writer.writeStartElement("object");
            writeAttribute(writer, "model", "crm.reportingperiod");
            writeAttribute(writer, "pk", report.reportingPeriod().id());
            writeText(writer, "title", report.reportingPeriod().title());
            writeText(writer, "begin", report.reportingPeriod().begin());
            writeText(writer, "end", report.reportingPeriod().end());
            writer.writeEndElement();
        }

        writer.writeStartElement("object");
        writeAttribute(writer, "model", "crm.project");
        writeAttribute(writer, "pk", report.id());
        writeText(writer, "project_name", report.projectName());
        writeText(writer, "description", report.description());
        // Aggregates: emit *_InPeriod only when populated so the XSL's
        // <xsl:when test="Effective_Effort_InPeriod"> branch fires
        // exactly when a reporting_period was supplied (matches legacy).
        writeText(writer, "Effective_Costs_Confirmed", report.effectiveCostsConfirmed());
        writeText(writer, "Effective_Costs_Not_Confirmed", report.effectiveCostsNotConfirmed());
        writeText(writer, "Effective_Effort_Overall", report.effectiveEffortOverall());
        if (report.effectiveCostsInPeriod() != null) {
            writeText(writer, "Effective_Costs_InPeriod", report.effectiveCostsInPeriod());
        }
        if (report.effectiveEffortInPeriod() != null) {
            writeText(writer, "Effective_Effort_InPeriod", report.effectiveEffortInPeriod());
        }
        writeText(writer, "Planned_Total_Costs", report.plannedTotalCosts());
        writeText(writer, "Effective_Duration", report.effectiveDuration());
        writeText(writer, "Planned_Duration", report.plannedDuration());
        // chart: legacy emitted the local SVG path; orchestrator has
        // downloaded the presigned URL into the FOP working directory and
        // hands us its filename, so the XSL's <fo:external-graphic> can
        // resolve it relative to the FOP base URI.
        if (chartLocalFilename != null) {
            writeText(writer, "project_cost_overview", chartLocalFilename);
        }
        writer.writeEndElement();

        if (report.tasks() != null) {
            for (TaskReportDto task : report.tasks()) {
                writer.writeStartElement("object");
                writeAttribute(writer, "model", "crm.task");
                writeAttribute(writer, "pk", task.id());
                writeText(writer, "title", task.title());
                writeText(writer, "description", task.description());
                writeText(writer, "Effective_Costs_Confirmed_Overall",
                        task.effectiveCostsConfirmedOverall());
                writeText(writer, "Effective_Costs_Not_Confirmed_Overall",
                        task.effectiveCostsNotConfirmedOverall());
                writeText(writer, "Effective_Effort_Overall", task.effectiveEffortOverall());
                if (task.effectiveCostsInPeriod() != null) {
                    writeText(writer, "Effective_Costs_InPeriod", task.effectiveCostsInPeriod());
                }
                if (task.effectiveEffortInPeriod() != null) {
                    writeText(writer, "Effective_Effort_InPeriod", task.effectiveEffortInPeriod());
                }
                writeText(writer, "Planned_Effort", task.plannedEffort());
                writeText(writer, "Effective_Duration", task.effectiveDuration());
                writeText(writer, "Planned_Duration", task.plannedDuration());
                writer.writeEndElement();

                if (task.works() != null) {
                    for (WorkRowDto work : task.works()) {
                        writeWork(writer, work);
                    }
                }
            }
        }
    }

    private void writeWork(XMLStreamWriter writer, WorkRowDto work) throws XMLStreamException {
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
