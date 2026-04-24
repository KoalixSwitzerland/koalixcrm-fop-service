package net.koalix.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Snapshot driving the work_report XSL — one HumanResource, a date range,
 * the projects that resource contributed to, every Work record in the
 * range, and pre-computed day/week/month buckets (per total + per
 * project) the XSL pivots over.
 */
public record HumanResourceWorkReportDto(
        Long id,
        Long userId,
        String username,
        LocalDate rangeFrom,
        LocalDate rangeTo,
        List<ProjectRefDto> projects,
        List<WorkRowDto> works,
        List<BucketAggregateDto> dayBuckets,
        List<BucketAggregateDto> dayProjectBuckets,
        List<BucketAggregateDto> weekBuckets,
        List<BucketAggregateDto> weekProjectBuckets,
        List<BucketAggregateDto> monthBuckets,
        List<BucketAggregateDto> monthProjectBuckets
) {}
