package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;

record ResolvedDocxOptions(
        boolean preserveLineBreaks,
        boolean forcePageBreaks,
        boolean includeImageNames,
        boolean markUnclearWords,
        double unclearConfidenceThreshold
) {
    private static final double DEFAULT_UNCLEAR_CONFIDENCE_THRESHOLD = 0.75d;

    static ResolvedDocxOptions from(DocumentExportDto.DocxOptions options, boolean pageScope) {
        return new ResolvedDocxOptions(
                options == null || options.preserveLineBreaks() == null || options.preserveLineBreaks(),
                !pageScope && (options == null || options.forcePageBreaks() == null || options.forcePageBreaks()),
                options != null && Boolean.TRUE.equals(options.includeImageNames()),
                options != null && Boolean.TRUE.equals(options.markUnclearWords()),
                resolveUnclearConfidenceThreshold(options)
        );
    }

    private static double resolveUnclearConfidenceThreshold(DocumentExportDto.DocxOptions options) {
        if (options == null || options.unclearConfidenceThreshold() == null
                || !Double.isFinite(options.unclearConfidenceThreshold())) {
            return DEFAULT_UNCLEAR_CONFIDENCE_THRESHOLD;
        }
        return Math.min(1d, Math.max(0d, options.unclearConfidenceThreshold()));
    }
}
