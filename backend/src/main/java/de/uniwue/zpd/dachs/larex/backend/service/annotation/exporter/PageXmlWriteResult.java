package de.uniwue.zpd.dachs.larex.backend.service.annotation.exporter;

import java.util.List;

public record PageXmlWriteResult(
        long bytesWritten,
        List<String> warnings,
        String schemaVersion
) {}
