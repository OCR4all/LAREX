package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;

record ResolvedDocxOptions(
        boolean preserveLineBreaks,
        boolean forcePageBreaks,
        boolean includeImageNames,
        boolean markUnclearWords
) {
    static ResolvedDocxOptions from(DocumentExportDto.DocxOptions options, boolean pageScope) {
        return new ResolvedDocxOptions(
                options == null || options.preserveLineBreaks() == null || options.preserveLineBreaks(),
                !pageScope && (options == null || options.forcePageBreaks() == null || options.forcePageBreaks()),
                options != null && Boolean.TRUE.equals(options.includeImageNames()),
                options != null && Boolean.TRUE.equals(options.markUnclearWords())
        );
    }
}
