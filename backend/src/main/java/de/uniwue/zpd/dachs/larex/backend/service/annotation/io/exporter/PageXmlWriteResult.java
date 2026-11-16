package de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;

import java.util.List;

public record PageXmlWriteResult(
        long bytesWritten,
        List<String> warnings,
        String schemaVersion,
        PageDto written
) {}
