package net.koalix.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Full snapshot for a project_report PDF render. Mirrors the legacy
 * {@code Project.serialize_to_xml} output 1:1 in JSON form: every
 * aggregate the XSL reads is a top-level field, tasks/works are nested.
 *
 * <p>Built and returned by {@code GET /projects/<id>/report-data/} (and
 * {@code /reporting-periods/<id>/report-data/} which sets
 * {@code reportingPeriod} to the period).
 */
public record ProjectReportDto(
        Long id,
        String projectName,
        String description,
        Long projectStatus,
        Long projectManager,
        Long defaultCurrency,
        Long defaultTemplateSet,
        ReportingPeriodRefDto reportingPeriod,
        UserExtensionRefDto userExtension,
        List<TaskReportDto> tasks,
        BigDecimal effectiveCostsConfirmed,
        BigDecimal effectiveCostsNotConfirmed,
        BigDecimal effectiveEffortOverall,
        BigDecimal effectiveCostsInPeriod,
        BigDecimal effectiveEffortInPeriod,
        BigDecimal plannedTotalCosts,
        String effectiveDuration,
        String plannedDuration,
        String projectCostOverviewUrl
) {}
