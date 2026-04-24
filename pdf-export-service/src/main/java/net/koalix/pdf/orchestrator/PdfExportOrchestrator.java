package net.koalix.pdf.orchestrator;

import net.koalix.api.CrmApiClient;
import net.koalix.api.dto.AccountingPeriodReportDto;
import net.koalix.api.dto.CommercialDocumentDto;
import net.koalix.api.dto.CommercialDocumentMediaDto;
import net.koalix.api.dto.DocumentTemplateDto;
import net.koalix.api.dto.HumanResourceWorkReportDto;
import net.koalix.api.dto.PdfExportProcessDto;
import net.koalix.api.dto.PdfExportProcessPatchDto;
import net.koalix.api.dto.ProjectReportDto;
import net.koalix.api.dto.UserExtensionDto;
import net.koalix.pdf.render.FopRenderer;
import net.koalix.pdf.sqs.PdfExportCommand;
import net.koalix.pdf.storage.S3PdfUploader;
import net.koalix.pdf.template.TemplateAssets;
import net.koalix.pdf.template.TemplateFetcher;
import net.koalix.pdf.xml.XmlAggregator;
import net.koalix.pdf.xml.builders.AccountingReportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * End-to-end orchestration of a single PDF export job.
 *
 * <p>Dispatches on {@link PdfExportCommand#sourceModel()}:
 * <ul>
 *   <li>commercial documents (Invoice, Quotation, …) → fetch nested
 *       document, render, upload, POST a {@code CommercialDocumentMedia}
 *       row pointing at the PDF.</li>
 *   <li>{@code AccountingPeriod} → fetch report snapshot, pick
 *       BALANCE_SHEET vs PROFIT_LOSS by template id, render, upload,
 *       PATCH the process row.</li>
 *   <li>{@code Project} / {@code ReportingPeriod} → project_report flow:
 *       fetch report snapshot, download the SVG cost-overview chart,
 *       build XML with the local chart filename, render, upload, PATCH.</li>
 *   <li>{@code HumanResource} → work_report flow.</li>
 * </ul>
 *
 * <p>Retry policy mirrors the Celery worker: 3 attempts, exponential
 * backoff capped at 30 s. Terminal failure PATCHes the process row to
 * {@code failed} and the SQS message is acked anyway — Spring Cloud AWS
 * moves it to the DLQ once redrive policy is exhausted.
 */
@Component
public class PdfExportOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(PdfExportOrchestrator.class);

    private static final String ACCOUNTING_PERIOD_MODEL = "AccountingPeriod";
    private static final String PROJECT_MODEL = "Project";
    private static final String REPORTING_PERIOD_MODEL = "ReportingPeriod";
    private static final String HUMAN_RESOURCE_MODEL = "HumanResource";
    private static final Set<String> COMMERCIAL_MODELS = Set.of(
            "Invoice", "Quotation", "DeliveryNote", "DespatchAdvice",
            "PurchaseOrder", "PaymentReminder", "CreditNote");

    private final CrmApiClient crm;
    private final TemplateFetcher templateFetcher;
    private final XmlAggregator xmlAggregator;
    private final FopRenderer renderer;
    private final S3PdfUploader uploader;
    private final WebClient webClient;

    public PdfExportOrchestrator(CrmApiClient crm, TemplateFetcher templateFetcher,
                                 XmlAggregator xmlAggregator, FopRenderer renderer,
                                 S3PdfUploader uploader, WebClient webClient) {
        this.crm = crm;
        this.templateFetcher = templateFetcher;
        this.xmlAggregator = xmlAggregator;
        this.renderer = renderer;
        this.uploader = uploader;
        this.webClient = webClient;
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 30_000))
    public void handle(PdfExportCommand cmd) throws Exception {
        LOG.info("Starting PDF export process_id={} source_model={} source_id={}",
                cmd.processId(), cmd.sourceModel(), cmd.sourceId());

        if (cmd.templateSetId() == 0L) {
            throw new IllegalStateException(
                    "template_set_id is 0 (not set) for process_id=" + cmd.processId());
        }

        crm.patchPdfExportProcess(cmd.processId(),
                new PdfExportProcessPatchDto("processing", null, null));

        switch (cmd.sourceModel()) {
            case ACCOUNTING_PERIOD_MODEL -> handleAccounting(cmd);
            case PROJECT_MODEL -> handleProjectReport(cmd, /*period*/ false);
            case REPORTING_PERIOD_MODEL -> handleProjectReport(cmd, /*period*/ true);
            case HUMAN_RESOURCE_MODEL -> handleWorkReport(cmd);
            default -> {
                if (COMMERCIAL_MODELS.contains(cmd.sourceModel())) {
                    handleCommercial(cmd);
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported source_model: " + cmd.sourceModel());
                }
            }
        }
    }

    private void handleCommercial(PdfExportCommand cmd) throws Exception {
        PdfExportProcessDto process = crm.getPdfExportProcess(cmd.processId());
        CommercialDocumentDto document = crm.getCommercialDocumentNested(cmd.sourceModel(), cmd.sourceId());
        DocumentTemplateDto template = crm.getDocumentTemplate(cmd.templateSetId());
        UserExtensionDto userExtension = document.userExtension() != null
                ? crm.getUserExtension(document.userExtension())
                : null;

        TemplateAssets assets = templateFetcher.fetch(template);
        byte[] xmlDocument = xmlAggregator.build(document, userExtension);
        byte[] pdf = renderer.render(xmlDocument, assets);

        String s3Key = uploader.keyFor(cmd.sourceModel(), cmd.sourceId(), cmd.processId());
        URI s3Url = uploader.upload(s3Key, pdf);

        CommercialDocumentMediaDto media = new CommercialDocumentMediaDto(
                null,
                cmd.sourceId(),
                cmd.processId(),
                s3Url.toString(),
                s3Key,
                "completed",
                "application/pdf",
                cmd.printedByUserId() == 0 ? null : cmd.printedByUserId(),
                null,
                null);
        crm.createCommercialDocumentMedia(media);

        crm.patchPdfExportProcess(cmd.processId(),
                new PdfExportProcessPatchDto("completed", s3Url.toString(), null));

        LOG.info("PDF export complete process_id={} url={} (fetched process status={})",
                cmd.processId(), s3Url, process.status());
    }

    private void handleAccounting(PdfExportCommand cmd) throws Exception {
        AccountingPeriodReportDto period = crm.getAccountingPeriodReport(cmd.sourceId());
        AccountingReportType reportType = AccountingReportType.resolve(period, cmd.templateSetId());
        DocumentTemplateDto template = crm.getDocumentTemplate(cmd.templateSetId());

        TemplateAssets assets = templateFetcher.fetch(template);
        String headerPicture = assets.logoFile() == null ? null : assets.logoFile().toString();
        // TODO(#404): wire organisationname from a user-scoped source.
        byte[] xmlDocument = xmlAggregator.buildAccounting(period, reportType, null, headerPicture);
        byte[] pdf = renderer.render(xmlDocument, assets);

        String s3Key = uploader.keyFor(cmd.sourceModel(), cmd.sourceId(), cmd.processId());
        URI s3Url = uploader.upload(s3Key, pdf);

        crm.patchPdfExportProcess(cmd.processId(),
                new PdfExportProcessPatchDto("completed", s3Url.toString(), null));

        LOG.info("Accounting PDF export complete process_id={} report={} url={}",
                cmd.processId(), reportType, s3Url);
    }

    private void handleProjectReport(PdfExportCommand cmd, boolean periodScoped) throws Exception {
        ProjectReportDto report = periodScoped
                ? crm.getReportingPeriodReport(cmd.sourceId())
                : crm.getProjectReport(cmd.sourceId());
        DocumentTemplateDto template = crm.getDocumentTemplate(cmd.templateSetId());
        TemplateAssets assets = templateFetcher.fetch(template);

        // Download the cost-overview SVG into the FOP working directory
        // so <fo:external-graphic> can resolve it as a local file.
        String chartFilename = null;
        if (report.projectCostOverviewUrl() != null) {
            chartFilename = "project_cost_overview.svg";
            Path chartPath = assets.xslFile().getParent().resolve(chartFilename);
            downloadToFile(URI.create(report.projectCostOverviewUrl()), chartPath);
        }

        byte[] xmlDocument = xmlAggregator.buildProjectReport(report, chartFilename);
        byte[] pdf = renderer.render(xmlDocument, assets);

        String s3Key = uploader.keyFor(cmd.sourceModel(), cmd.sourceId(), cmd.processId());
        URI s3Url = uploader.upload(s3Key, pdf);

        crm.patchPdfExportProcess(cmd.processId(),
                new PdfExportProcessPatchDto("completed", s3Url.toString(), null));

        LOG.info("Project report PDF complete process_id={} period_scoped={} url={}",
                cmd.processId(), periodScoped, s3Url);
    }

    private void handleWorkReport(PdfExportCommand cmd) throws Exception {
        HumanResourceWorkReportDto report = crm.getHumanResourceWorkReport(cmd.sourceId());
        DocumentTemplateDto template = crm.getDocumentTemplate(cmd.templateSetId());
        TemplateAssets assets = templateFetcher.fetch(template);

        byte[] xmlDocument = xmlAggregator.buildWorkReport(report);
        byte[] pdf = renderer.render(xmlDocument, assets);

        String s3Key = uploader.keyFor(cmd.sourceModel(), cmd.sourceId(), cmd.processId());
        URI s3Url = uploader.upload(s3Key, pdf);

        crm.patchPdfExportProcess(cmd.processId(),
                new PdfExportProcessPatchDto("completed", s3Url.toString(), null));

        LOG.info("Work report PDF complete process_id={} url={}", cmd.processId(), s3Url);
    }

    private void downloadToFile(URI uri, Path target) throws IOException {
        byte[] bytes = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
        if (bytes == null) {
            throw new IOException("Empty body for chart URL " + uri);
        }
        Files.write(target, bytes);
    }

    @Recover
    public void recover(Exception ex, PdfExportCommand cmd) {
        LOG.error("Terminal failure for process_id={}: {}", cmd.processId(), ex.toString(), ex);
        crm.patchPdfExportProcess(cmd.processId(),
                new PdfExportProcessPatchDto("failed", null, ex.toString()));
    }
}
