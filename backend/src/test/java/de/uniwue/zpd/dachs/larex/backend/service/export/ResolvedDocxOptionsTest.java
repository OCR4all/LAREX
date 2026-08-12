package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResolvedDocxOptionsTest {

    @Test
    void usesLegacyDefaultWhenThresholdIsMissing() {
        ResolvedDocxOptions options = ResolvedDocxOptions.from(
                new DocumentExportDto.DocxOptions(true, true, false, true, null),
                false
        );

        assertEquals(0.75d, options.unclearConfidenceThreshold());
    }

    @Test
    void clampsThresholdToConfidenceRange() {
        ResolvedDocxOptions belowRange = ResolvedDocxOptions.from(
                new DocumentExportDto.DocxOptions(true, true, false, true, -0.1d),
                false
        );
        ResolvedDocxOptions aboveRange = ResolvedDocxOptions.from(
                new DocumentExportDto.DocxOptions(true, true, false, true, 1.1d),
                false
        );

        assertEquals(0d, belowRange.unclearConfidenceThreshold());
        assertEquals(1d, aboveRange.unclearConfidenceThreshold());
    }
}
