package net.koalix.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One Work record as shipped under a Task in the project_report payload.
 * Fields match the legacy Django XML serializer output for crm.work; the
 * project_report XSL only reads {@code task} and {@code description} but
 * we ship the rest for parity with the work_report XSL.
 */
public record WorkRowDto(
        Long id,
        Long task,
        Long reportingPeriod,
        Long humanResource,
        LocalDate date,
        String shortDescription,
        String description,
        BigDecimal workedHours
) {}
