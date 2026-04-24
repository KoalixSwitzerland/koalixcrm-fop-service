package net.koalix.api.dto;

/** Compact user-extension reference for the report-of user. */
public record UserExtensionRefDto(
        Long id,
        Long userId,
        String username
) {}
