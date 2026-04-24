package net.koalix.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One task row inside the project report. Per-task aggregates feed
 * {@code project_report.xsl} columns; nested works are emitted as
 * sibling {@code <object model="crm.work">} elements (the XSL filters
 * works by {@code task = $current_task_id}).
 */
public record TaskReportDto(
        Long id,
        String title,
        String description,
        Long project,
        Long status,
        LocalDate lastStatusChange,
        List<WorkRowDto> works,
        BigDecimal effectiveCostsConfirmedOverall,
        BigDecimal effectiveCostsNotConfirmedOverall,
        BigDecimal effectiveEffortOverall,
        BigDecimal effectiveCostsInPeriod,
        BigDecimal effectiveEffortInPeriod,
        BigDecimal plannedEffort,
        String effectiveDuration,
        String plannedDuration
) {}
