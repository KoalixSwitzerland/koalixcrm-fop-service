package net.koalix.api.dto;

/**
 * One row of a (day|week|month, optional project) effort aggregate for the
 * work_report XSL. {@code effort} is a String because the legacy XML
 * carried the literal token {@code "-"} for days outside the requested
 * range (the XSL distinguishes 0, "-", and a number to drive cell rendering).
 *
 * <p>Which attribute fields are populated depends on the bucket flavour:
 * <ul>
 *   <li>day buckets → day, week, week_day, month, year</li>
 *   <li>week buckets → week, year</li>
 *   <li>month buckets → month, year</li>
 * </ul>
 * The "_project_" variants additionally carry {@code projectId}.
 */
public record BucketAggregateDto(
        String effort,
        Long projectId,
        String day,
        String week,
        String weekDay,
        String month,
        String year
) {}
