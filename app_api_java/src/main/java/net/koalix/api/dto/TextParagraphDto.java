package net.koalix.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A free-text block attached to a commercial document.
 *
 * <p>Mirrors {@code crm_textparagraphincommercialdocument}. {@code purpose}
 * is the placement code the XSL-FO templates switch on — e.g. {@code BS}
 * (before subject), {@code AS} (after subject / before the positions table),
 * {@code AT} (after the table). {@code textParagraph} is the rendered body.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TextParagraphDto(
        String purpose,
        String textParagraph
) {
}
