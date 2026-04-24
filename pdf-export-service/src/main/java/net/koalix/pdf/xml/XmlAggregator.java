package net.koalix.pdf.xml;

import net.koalix.api.dto.AccountingPeriodReportDto;
import net.koalix.api.dto.CommercialDocumentDto;
import net.koalix.api.dto.HumanResourceWorkReportDto;
import net.koalix.api.dto.ProjectReportDto;
import net.koalix.api.dto.UserExtensionDto;
import net.koalix.pdf.xml.builders.AccountingReportType;
import net.koalix.pdf.xml.builders.AccountingXmlBuilder;
import net.koalix.pdf.xml.builders.CommercialDocumentXmlBuilder;
import net.koalix.pdf.xml.builders.ProjectReportXmlBuilder;
import net.koalix.pdf.xml.builders.UserExtensionXmlBuilder;
import net.koalix.pdf.xml.builders.WorkReportXmlBuilder;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Glue that assembles the final XML input to Apache FOP. Each
 * {@code build*} method produces a self-contained byte array whose root
 * element matches the corresponding XSL's {@code <xsl:template match=…>}.
 */
@Component
public class XmlAggregator {

    private final XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();

    private final CommercialDocumentXmlBuilder documentBuilder;
    private final UserExtensionXmlBuilder userExtensionBuilder;
    private final AccountingXmlBuilder accountingBuilder;
    private final ProjectReportXmlBuilder projectReportBuilder;
    private final WorkReportXmlBuilder workReportBuilder;

    public XmlAggregator(CommercialDocumentXmlBuilder documentBuilder,
                         UserExtensionXmlBuilder userExtensionBuilder,
                         AccountingXmlBuilder accountingBuilder,
                         ProjectReportXmlBuilder projectReportBuilder,
                         WorkReportXmlBuilder workReportBuilder) {
        this.documentBuilder = documentBuilder;
        this.userExtensionBuilder = userExtensionBuilder;
        this.accountingBuilder = accountingBuilder;
        this.projectReportBuilder = projectReportBuilder;
        this.workReportBuilder = workReportBuilder;
    }

    public byte[] build(CommercialDocumentDto document, UserExtensionDto userExtension)
            throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XMLStreamWriter writer = outputFactory.createXMLStreamWriter(out, "UTF-8");
            try {
                writer.writeStartDocument("UTF-8", "1.0");
                writer.writeStartElement("koalixcrm-export");
                documentBuilder.write(writer, document);
                if (userExtension != null) {
                    userExtensionBuilder.write(writer, userExtension);
                }
                writer.writeEndElement();
                writer.writeEndDocument();
            } finally {
                writer.close();
            }
            return out.toByteArray();
        } catch (javax.xml.stream.XMLStreamException e) {
            throw new IOException("XML aggregation failed", e);
        }
    }

    /**
     * Accounting-report flavour: the XSL's {@code <xsl:template match="...">}
     * matches the root element directly, so the accounting report must
     * NOT be wrapped in {@code <koalixcrm-export>}.
     */
    public byte[] buildAccounting(AccountingPeriodReportDto period,
                                  AccountingReportType type,
                                  String organisationName,
                                  String headerPicturePath) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XMLStreamWriter writer = outputFactory.createXMLStreamWriter(out, "UTF-8");
            try {
                writer.writeStartDocument("UTF-8", "1.0");
                accountingBuilder.write(writer, period, type, organisationName, headerPicturePath);
                writer.writeEndDocument();
            } finally {
                writer.close();
            }
            return out.toByteArray();
        } catch (javax.xml.stream.XMLStreamException e) {
            throw new IOException("XML aggregation failed", e);
        }
    }

    /**
     * project_report flavour: wrapped in {@code <koalixcrm-export>}.
     * {@code chartLocalFilename} is the basename of the SVG inside the
     * FOP working directory (orchestrator already downloaded the
     * presigned URL to disk).
     */
    public byte[] buildProjectReport(ProjectReportDto report, String chartLocalFilename)
            throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XMLStreamWriter writer = outputFactory.createXMLStreamWriter(out, "UTF-8");
            try {
                writer.writeStartDocument("UTF-8", "1.0");
                writer.writeStartElement("koalixcrm-export");
                projectReportBuilder.write(writer, report, chartLocalFilename);
                writer.writeEndElement();
                writer.writeEndDocument();
            } finally {
                writer.close();
            }
            return out.toByteArray();
        } catch (javax.xml.stream.XMLStreamException e) {
            throw new IOException("XML aggregation failed", e);
        }
    }

    /** work_report flavour: wrapped in {@code <koalixcrm-export>}. */
    public byte[] buildWorkReport(HumanResourceWorkReportDto report) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XMLStreamWriter writer = outputFactory.createXMLStreamWriter(out, "UTF-8");
            try {
                writer.writeStartDocument("UTF-8", "1.0");
                writer.writeStartElement("koalixcrm-export");
                workReportBuilder.write(writer, report);
                writer.writeEndElement();
                writer.writeEndDocument();
            } finally {
                writer.close();
            }
            return out.toByteArray();
        } catch (javax.xml.stream.XMLStreamException e) {
            throw new IOException("XML aggregation failed", e);
        }
    }
}
