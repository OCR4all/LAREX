package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import java.nio.file.Path;
import java.util.List;

record ExportPage(
        Page page,
        PageXml pageXml,
        PageDto pageDto,
        List<ExportRegion> regions,
        PageImage image,
        Path imagePath
) {
}
