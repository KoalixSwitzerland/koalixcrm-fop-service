package net.koalix.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata plus sub-resource URLs for a {@code DocumentTemplate}.
 *
 * <p>The asset URLs ({@code xslHref}, {@code fopConfigHref}, {@code logoHref})
 * are Django endpoints that respond with HTTP 302 to a short-lived presigned
 * S3 URL. Follow the redirect manually — do not carry the caller's OIDC token
 * to S3.
 *
 * <p>The chrome fields ({@code addresser}, {@code pageFooterLeft},
 * {@code pageFooterMiddle}, {@code bankingAccountReference}) are the static
 * header/footer text the XSL-FO templates print. {@code addresser} is the
 * sender line above the recipient address (sourced from the TemplateSet);
 * the {@code pageFooter*} / {@code bankingAccountReference} fields populate
 * the page-footer band.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentTemplateDto(
        Long id,
        String title,
        String xslHref,
        String fopConfigHref,
        String logoHref,
        String addresser,
        String pageFooterLeft,
        String pageFooterMiddle,
        String bankingAccountReference
) {
}
