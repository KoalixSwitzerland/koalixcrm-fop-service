package net.koalix.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.koalix.api.dto.AccountingPeriodReportDto;
import net.koalix.api.dto.CommercialDocumentDto;
import net.koalix.api.dto.CommercialDocumentMediaDto;
import net.koalix.api.dto.DocumentTemplateDto;
import net.koalix.api.dto.HumanResourceWorkReportDto;
import net.koalix.api.dto.PdfExportProcessDto;
import net.koalix.api.dto.PdfExportProcessPatchDto;
import net.koalix.api.dto.ProjectReportDto;
import net.koalix.api.dto.UserExtensionDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Hand-written REST client for the koalixCRM JSON endpoints consumed by the
 * PDF worker. Every call attaches an OIDC bearer token via
 * {@link OidcTokenProvider} and retries once on 401/403 with a freshly-minted
 * token (mirrors {@code koalixcrm/shared/api_client.py}).
 *
 * <p>URLs follow the per-app workspace-scoped layout served by Django:
 * {@code <baseUrl>/koalixcrm_<app>/api/v1/<workspaceId>/<resource>/...} with
 * hyphenated resource names. The producing app prefix differs per resource —
 * {@code pdf-export-processes}, {@code document-templates} and
 * {@code user-extensions} live under {@code koalixcrm_core}; commercial
 * documents and {@code commercial-document-media} under {@code koalixcrm_contracts};
 * accounting reports under {@code koalixcrm_accounting}; project / reporting-period /
 * human-resource reports under {@code koalixcrm_reporting}.
 */
public class CrmApiClient {

    private static final String CORE_PREFIX = "/koalixcrm_core/api/v1";
    private static final String CONTRACTS_PREFIX = "/koalixcrm_contracts/api/v1";
    private static final String ACCOUNTING_PREFIX = "/koalixcrm_accounting/api/v1";
    private static final String REPORTING_PREFIX = "/koalixcrm_reporting/api/v1";

    private final WebClient webClient;
    private final OidcTokenProvider tokenProvider;
    private final String baseUrl;
    private final String originHeaderName;
    private final String originHeaderValue;
    private final ObjectMapper objectMapper;

    public CrmApiClient(WebClient webClient, OidcTokenProvider tokenProvider, String baseUrl,
                        String originHeaderName, String originHeaderValue) {
        this.webClient = webClient;
        this.tokenProvider = tokenProvider;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.originHeaderName = originHeaderName;
        this.originHeaderValue = originHeaderValue;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public PdfExportProcessDto getPdfExportProcess(long workspaceId, long id) {
        return get(corePath(workspaceId, "pdf-export-processes/" + id + "/"),
                PdfExportProcessDto.class);
    }

    public PdfExportProcessDto patchPdfExportProcess(long workspaceId, long id,
                                                     PdfExportProcessPatchDto patch) {
        return patch(corePath(workspaceId, "pdf-export-processes/" + id + "/"),
                patch, PdfExportProcessDto.class);
    }

    public DocumentTemplateDto getDocumentTemplate(long workspaceId, long id) {
        return get(corePath(workspaceId, "document-templates/" + id + "/"),
                DocumentTemplateDto.class);
    }

    public UserExtensionDto getUserExtension(long workspaceId, long id) {
        return get(corePath(workspaceId, "user-extensions/" + id + "/"),
                UserExtensionDto.class);
    }

    public CommercialDocumentDto getCommercialDocumentNested(long workspaceId,
                                                             String sourceModel, long id) {
        String resource = switch (sourceModel) {
            case "Invoice" -> "invoices";
            case "Quotation" -> "quotations";
            case "DeliveryNote", "DespatchAdvice" -> "despatch-advices";
            case "PurchaseOrder" -> "purchase-orders";
            case "PaymentReminder" -> "payment-reminders";
            case "CreditNote" -> "credit-notes";
            default -> throw new IllegalArgumentException("Unsupported source_model: " + sourceModel);
        };
        return get(contractsPath(workspaceId, resource + "/" + id + "/nested/"),
                CommercialDocumentDto.class);
    }

    public CommercialDocumentMediaDto createCommercialDocumentMedia(long workspaceId,
                                                                    CommercialDocumentMediaDto body) {
        return post(contractsPath(workspaceId, "commercial-document-media/"),
                body, CommercialDocumentMediaDto.class);
    }

    /**
     * Self-contained snapshot driving the accounting XSL-FO reports
     * (balance sheet / profit-loss statement).
     */
    public AccountingPeriodReportDto getAccountingPeriodReport(long workspaceId, long id) {
        return get(accountingPath(workspaceId, "accounting-periods/" + id + "/report-data/"),
                AccountingPeriodReportDto.class);
    }

    /**
     * Self-contained snapshot driving the project_report XSL. Used by
     * Project (no period scoping → overall report) and ReportingPeriod
     * (period-scoped — the endpoint is on the reporting-period resource
     * but returns the same payload shape with {@code reporting_period}
     * populated).
     */
    public ProjectReportDto getProjectReport(long workspaceId, long id) {
        return get(reportingPath(workspaceId, "projects/" + id + "/report-data/"),
                ProjectReportDto.class);
    }

    public ProjectReportDto getReportingPeriodReport(long workspaceId, long id) {
        return get(reportingPath(workspaceId, "reporting-periods/" + id + "/report-data/"),
                ProjectReportDto.class);
    }

    /**
     * Self-contained snapshot driving the work_report XSL.
     * TODO(#404): accept date_from / date_to once PDFExportProcess carries
     * a params field; for now the endpoint defaults to a 60-day trailing window.
     */
    public HumanResourceWorkReportDto getHumanResourceWorkReport(long workspaceId, long id) {
        return get(reportingPath(workspaceId, "human-resources/" + id + "/work-report-data/"),
                HumanResourceWorkReportDto.class);
    }

    /**
     * Resolve an href returned by the DocumentTemplate endpoints (xsl/fop-config/logo)
     * to its presigned S3 URL. Does NOT follow the redirect — it returns the
     * short-lived S3 URL so the caller can fetch it without the bearer token.
     */
    public URI resolvePresignedAssetUrl(String href) {
        URI uri = URI.create(href.startsWith("http") ? href : baseUrl + href);
        ClientResponse response = webClient.get()
                .uri(uri)
                .headers(authHeaders())
                .exchangeToMono(r -> {
                    if (r.statusCode().is3xxRedirection()) {
                        return Mono.just(r);
                    }
                    return r.createException().flatMap(Mono::error);
                })
                .block();
        if (response == null) {
            throw new IllegalStateException("No response from " + uri);
        }
        String location = response.headers().asHttpHeaders().getFirst(HttpHeaders.LOCATION);
        if (location == null) {
            throw new IllegalStateException("Redirect from " + uri + " had no Location header");
        }
        return URI.create(location);
    }

    // ---- internals ---------------------------------------------------------

    private static String corePath(long workspaceId, String suffix) {
        return CORE_PREFIX + "/" + workspaceId + "/" + suffix;
    }

    private static String contractsPath(long workspaceId, String suffix) {
        return CONTRACTS_PREFIX + "/" + workspaceId + "/" + suffix;
    }

    private static String accountingPath(long workspaceId, String suffix) {
        return ACCOUNTING_PREFIX + "/" + workspaceId + "/" + suffix;
    }

    private static String reportingPath(long workspaceId, String suffix) {
        return REPORTING_PREFIX + "/" + workspaceId + "/" + suffix;
    }

    private Consumer<HttpHeaders> authHeaders() {
        return headers -> {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.accessToken());
            if (originHeaderName != null && originHeaderValue != null) {
                headers.set(originHeaderName, originHeaderValue);
            }
        };
    }

    private <T> T get(String path, Class<T> type) {
        return execute(() -> webClient.get()
                .uri(baseUrl + path)
                .headers(authHeaders())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(), type);
    }

    private <T> T post(String path, Object body, Class<T> type) {
        return execute(() -> webClient.post()
                .uri(baseUrl + path)
                .headers(authHeaders())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(), type);
    }

    private <T> T patch(String path, Object body, Class<T> type) {
        return execute(() -> webClient.patch()
                .uri(baseUrl + path)
                .headers(authHeaders())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(), type);
    }

    private <T> T execute(Supplier<JsonNode> call, Class<T> type) {
        try {
            JsonNode body = call.get();
            return body == null ? null : objectMapper.convertValue(body, type);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                tokenProvider.refresh();
                JsonNode body = call.get();
                return body == null ? null : objectMapper.convertValue(body, type);
            }
            throw e;
        }
    }
}
