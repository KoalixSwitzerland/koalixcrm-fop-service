package net.koalix.pdf.render;

import net.koalix.api.dto.CommercialDocumentDto;
import net.koalix.api.dto.UserExtensionDto;
import net.koalix.pdf.support.DocumentFixtures;
import net.koalix.pdf.template.TemplateAssets;
import net.koalix.pdf.xml.XmlAggregator;
import net.koalix.pdf.xml.builders.CommercialDocumentXmlBuilder;
import net.koalix.pdf.xml.builders.PartyXmlBuilder;
import net.koalix.pdf.xml.builders.PositionXmlBuilder;
import net.koalix.pdf.xml.builders.UserExtensionXmlBuilder;
import org.apache.fop.apps.FopFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 2 of the pdf-export-service integration-test plan
 * (see INTEGRATION_TEST_CONCEPT.md).
 *
 * <p>Feeds the current {@code <koalixcrm-export>} XML shape through every
 * real XSL template bundled under
 * {@code src/test/resources/fixtures/templates/}. The assertion is
 * deliberately loose — a valid PDF byte-stream is good enough. Blank pages
 * are the expected outcome until the XSL-reconciliation work in planned
 * step #4 of {@code CELERY_TO_JAVA_MIGRATION.md} lands.
 *
 * <p>The rendered PDF size is printed so a future PR can diff against it
 * once reconciliation adds real layout.
 */
class FopRendererIT {

    @TempDir
    static Path sharedTempDir;

    private static Path fopBaseDir;
    private static FopFactory fopFactory;
    private static XmlAggregator aggregator;
    private static FopRenderer renderer;

    @BeforeAll
    static void bootstrap() throws Exception {
        fopBaseDir = sharedTempDir.resolve("fop-base");
        Files.createDirectories(fopBaseDir);

        copyClasspath("/fixtures/fop/fop.xconf",            fopBaseDir.resolve("fop.xconf"));
        copyClasspath("/fixtures/fop/DejaVuSans.ttf",        fopBaseDir.resolve("DejaVuSans.ttf"));
        copyClasspath("/fixtures/fop/DejaVuSans-Bold.ttf",   fopBaseDir.resolve("DejaVuSans-Bold.ttf"));
        copyClasspath("/fixtures/fop/dejavusans.xml",        fopBaseDir.resolve("dejavusans.xml"));
        copyClasspath("/fixtures/fop/dejavusans-bold.xml",   fopBaseDir.resolve("dejavusans-bold.xml"));

        try (InputStream conf = Files.newInputStream(fopBaseDir.resolve("fop.xconf"))) {
            fopFactory = FopFactory.newInstance(fopBaseDir.toUri(), conf);
        }

        aggregator = new XmlAggregator(
                new CommercialDocumentXmlBuilder(new PartyXmlBuilder(), new PositionXmlBuilder()),
                new UserExtensionXmlBuilder(),
                new net.koalix.pdf.xml.builders.AccountingXmlBuilder(),
                new net.koalix.pdf.xml.builders.ProjectReportXmlBuilder(),
                new net.koalix.pdf.xml.builders.WorkReportXmlBuilder());
        renderer = new FopRenderer(fopFactory);
    }

    // Scope note: balancesheet.xsl and profitlossstatement.xsl are intentionally
    // excluded. Their XSL root is koalixaccountingbalacesheet /
    // koalixaccountingprofitlossstatement — they render from a different data
    // aggregate than <koalixcrm-export>. They belong to the accounting-export
    // flow, which is not yet migrated; once it is, it deserves its own IT.
    @ParameterizedTest(name = "renders {0} without exception")
    @ValueSource(strings = {
            "invoice.xsl",
            "quote.xsl",
            "deliveryorder.xsl",
            "purchaseorder.xsl",
            "purchaseconfirmation.xsl",
            "koalix_invoice_20180518.xsl",
            "acme_quote.xsl",
            "sample_quote_20210412.xsl",
            "project_report.xsl",
            "project_report_acme.xsl",
            "project_report_actual_effort_acme.xsl",
            "work_report.xsl",
    })
    void rendersWithKoalixcrmExportShape(String templateFileName) throws Exception {
        CommercialDocumentDto doc = DocumentFixtures.invoice();
        UserExtensionDto user = DocumentFixtures.userExtension();
        byte[] xml = aggregator.build(doc, user, DocumentFixtures.documentTemplate());

        Path xslCopy = fopBaseDir.resolve(templateFileName);
        copyClasspath("/fixtures/templates/" + templateFileName, xslCopy);
        TemplateAssets assets = new TemplateAssets(xslCopy, fopBaseDir.resolve("fop.xconf"), null);

        byte[] pdf = renderer.render(xml, assets);

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(0);
        String header = new String(pdf, 0, Math.min(5, pdf.length), StandardCharsets.US_ASCII);
        assertThat(header)
                .as("first 5 bytes should be a PDF signature for template " + templateFileName)
                .isEqualTo("%PDF-");

        Path outDir = Path.of("build", "test-output", "pdfs");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve(templateFileName.replace(".xsl", ".pdf"));
        Files.write(outFile, pdf);

        // Signal bytes for humans scanning the CI log. Not an assertion.
        System.out.printf(Locale.ROOT, "[FopRendererIT] %-50s pdf=%,8d bytes  -> %s%n",
                templateFileName, pdf.length, outFile.toAbsolutePath());
    }

    /**
     * Tier-B reconciliation guard: the invoice template must now render the
     * actual document data, not a blank page. Extracts the PDF text and asserts
     * the recipient, positions, totals, dates, and subclass-specific fields
     * (payable_until / iteration_number, routed through {@code extra}) all land.
     */
    @org.junit.jupiter.api.Test
    void rendersInvoiceWithPopulatedContent() throws Exception {
        byte[] xml = aggregator.build(
                DocumentFixtures.invoice(),
                DocumentFixtures.userExtension(),
                DocumentFixtures.documentTemplate());

        Path xslCopy = fopBaseDir.resolve("invoice.xsl");
        copyClasspath("/fixtures/templates/invoice.xsl", xslCopy);
        TemplateAssets assets = new TemplateAssets(xslCopy, fopBaseDir.resolve("fop.xconf"), null);

        byte[] pdf = renderer.render(xml, assets);

        String text;
        try (org.apache.pdfbox.pdmodel.PDDocument pd =
                     org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
            text = new org.apache.pdfbox.text.PDFTextStripper().getText(pd);
        }
        String flat = text.replaceAll("\\s+", " ");

        assertThat(flat)
                .as("recipient party + address")
                .contains("ACME SA")
                .contains("Bahnhofstrasse 1")
                .contains("Zurich");
        assertThat(flat)
                .as("position line + product type")
                .contains("Consulting hour");
        assertThat(flat)
                .as("document numbers / reference")
                .contains("KUN-42")
                .contains("REC-17")
                .contains("EXT-1");
        assertThat(flat)
                .as("totals formatted with the european decimal-format")
                .contains("1.200,00");
        assertThat(flat)
                .as("date_of_creation-less invoice still prints payable_until from extra")
                .contains("15.02.2025");
        assertThat(flat)
                .as("issuing user (user_extension)")
                .contains("Aaron");
        assertThat(flat)
                .as("document_meta footer / addresser chrome")
                .contains("Irgendeine Firma GmbH");
    }

    /**
     * Tier-B accounting reconciliation: the balance-sheet template renders the
     * asset/liability accounts and total via the {@code buildAccounting} path
     * (its XSL root is {@code koalixaccountingbalacesheet}, NOT wrapped in
     * {@code <koalixcrm-export>}).
     */
    @org.junit.jupiter.api.Test
    void rendersBalanceSheetWithContent() throws Exception {
        byte[] xml = aggregator.buildAccounting(
                DocumentFixtures.accountingPeriod(),
                net.koalix.pdf.xml.builders.AccountingReportType.BALANCE_SHEET,
                "Koalix GmbH", null);
        String flat = renderToText("balancesheet.xsl", xml);
        assertThat(flat)
                .contains("Koalix GmbH")
                .contains("Cash").contains("Receivables").contains("Payables")
                .contains("12.000,00");
    }

    /** Profit-loss flavour: earnings/spending accounts + TotalProfitLoss. */
    @org.junit.jupiter.api.Test
    void rendersProfitLossWithContent() throws Exception {
        byte[] xml = aggregator.buildAccounting(
                DocumentFixtures.accountingPeriod(),
                net.koalix.pdf.xml.builders.AccountingReportType.PROFIT_LOSS,
                "Koalix GmbH", null);
        String flat = renderToText("profitlossstatement.xsl", xml);
        assertThat(flat)
                .contains("Koalix GmbH")
                .contains("Sales").contains("Rent")
                .contains("50.000,00")
                .contains("38.000,00");
    }

    /** Render an accounting XML through the named XSL and return its flattened text. */
    private String renderToText(String templateFileName, byte[] xml) throws Exception {
        Path xsl = fopBaseDir.resolve(templateFileName);
        copyClasspath("/fixtures/templates/" + templateFileName, xsl);
        byte[] pdf = renderer.render(xml,
                new TemplateAssets(xsl, fopBaseDir.resolve("fop.xconf"), null));
        try (org.apache.pdfbox.pdmodel.PDDocument pd =
                     org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(pd)
                    .replaceAll("\\s+", " ");
        }
    }

    private static void copyClasspath(String resource, Path target) throws Exception {
        try (InputStream in = FopRendererIT.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Fixture missing on classpath: " + resource);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
