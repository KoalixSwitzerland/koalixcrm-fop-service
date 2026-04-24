package net.koalix.api.dto;

import java.time.LocalDate;

/** Reporting-period header for project_report when the report is period-scoped. */
public record ReportingPeriodRefDto(
        Long id,
        String title,
        LocalDate begin,
        LocalDate end
) {}
