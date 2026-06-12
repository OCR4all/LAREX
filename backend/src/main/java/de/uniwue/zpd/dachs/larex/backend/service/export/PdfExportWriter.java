package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PointDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PolygonDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.util.CoordinateUtils;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

@Component
public class PdfExportWriter {

    private static final String PDF_FONT_RESOURCE_PATH = "/fonts/Junicode.ttf";
    private static final String PDF_A_ICC_RESOURCE_PATH = "/color/sRGB.icc";

    DocumentExportService.StreamingDocumentExportResult render(String baseName,
                                                               Project project,
                                                               List<ExportPage> pages,
                                                               DocumentExportDto.PdfProfile pdfProfile) {
        return new DocumentExportService.StreamingDocumentExportResult(
                baseName + ".pdf",
                DocumentExportDto.ExportFormat.PDF.getContentType(),
                outputStream -> write(outputStream, project, pages, pdfProfile)
        );
    }

    private void write(OutputStream outputStream,
                       Project project,
                       List<ExportPage> pages,
                       DocumentExportDto.PdfProfile pdfProfile) throws IOException {
        DocumentExportDto.PdfProfile resolvedProfile = resolvePdfProfile(pdfProfile);
        try (PDDocument document = new PDDocument()) {
            PDFont font = loadPdfFont(document);

            for (ExportPage exportPage : pages) {
                switch (resolvedProfile) {
                    case SEARCHABLE, PDFA_SEARCHABLE -> addPage(document, font, exportPage, true, true, RenderingMode.NEITHER);
                    case IMAGES_ONLY -> addPage(document, font, exportPage, true, false, null);
                    case TEXT_PAGES -> {
                        addPage(document, font, exportPage, true, false, null);
                        addPage(document, font, exportPage, false, true, RenderingMode.FILL);
                    }
                }
            }

            if (resolvedProfile == DocumentExportDto.PdfProfile.PDFA_SEARCHABLE) {
                applyPdfaMetadata(document, pages.size() == 1 ? pages.get(0).page().getName() : project.getName());
            }

            document.save(outputStream);
        }
    }

    private void addPage(PDDocument document,
                         PDFont font,
                         ExportPage exportPage,
                         boolean drawImage,
                         boolean drawText,
                         RenderingMode renderingMode) throws IOException {
        PDRectangle pageSize = new PDRectangle(exportPage.pageDto().imageWidth(), exportPage.pageDto().imageHeight());
        PDPage pdfPage = new PDPage(pageSize);
        document.addPage(pdfPage);

        try (PDPageContentStream contentStream = new PDPageContentStream(document, pdfPage)) {
            if (drawImage) {
                BufferedImage image = readImage(exportPage.imagePath());
                if (image != null) {
                    var pdImage = LosslessFactory.createFromImage(document, image);
                    contentStream.drawImage(pdImage, 0, 0, pageSize.getWidth(), pageSize.getHeight());
                }
            }

            if (!drawText || renderingMode == null) {
                return;
            }

            contentStream.setRenderingMode(renderingMode);
            contentStream.setFont(font, 1);

            for (ExportTextLine line : collectLines(exportPage)) {
                renderTextLine(contentStream, font, line, exportPage.pageDto());
            }
        }
    }

    private void applyPdfaMetadata(PDDocument document, String title) throws IOException {
        document.setVersion(1.7f);
        PDDocumentInformation info = document.getDocumentInformation();
        info.setTitle(title);
        info.setProducer("LAREX");
        info.setCreator("LAREX");

        PDDocumentCatalog catalog = document.getDocumentCatalog();
        catalog.setLanguage("en-US");

        try (InputStream iccStream = PdfExportWriter.class.getResourceAsStream(PDF_A_ICC_RESOURCE_PATH)) {
            if (iccStream == null) {
                throw new IOException("Bundled PDF/A ICC profile not found: " + PDF_A_ICC_RESOURCE_PATH);
            }
            PDOutputIntent outputIntent = new PDOutputIntent(document, iccStream);
            outputIntent.setInfo("sRGB IEC61966-2.1");
            outputIntent.setOutputCondition("sRGB IEC61966-2.1");
            outputIntent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
            outputIntent.setRegistryName("http://www.color.org");
            catalog.addOutputIntent(outputIntent);
        }

        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String xmp = """
                <?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <dc:title>
                        <rdf:Alt>
                          <rdf:li xml:lang="x-default">%s</rdf:li>
                        </rdf:Alt>
                      </dc:title>
                    </rdf:Description>
                    <rdf:Description rdf:about="" xmlns:pdf="http://ns.adobe.com/pdf/1.3/">
                      <pdf:Producer>LAREX</pdf:Producer>
                    </rdf:Description>
                    <rdf:Description rdf:about="" xmlns:xmp="http://ns.adobe.com/xap/1.0/">
                      <xmp:CreatorTool>LAREX</xmp:CreatorTool>
                      <xmp:CreateDate>%s</xmp:CreateDate>
                      <xmp:ModifyDate>%s</xmp:ModifyDate>
                    </rdf:Description>
                    <rdf:Description rdf:about="" xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/">
                      <pdfaid:part>2</pdfaid:part>
                      <pdfaid:conformance>B</pdfaid:conformance>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                <?xpacket end="w"?>
                """.formatted(escapeXml(title), timestamp, timestamp);

        PDMetadata metadata = new PDMetadata(document);
        metadata.importXMPMetadata(xmp.getBytes(StandardCharsets.UTF_8));
        catalog.setMetadata(metadata);
    }

    private List<ExportTextLine> collectLines(ExportPage page) {
        List<ExportTextLine> lines = new ArrayList<>();
        for (ExportRegion region : page.regions()) {
            if (!region.lines().isEmpty()) {
                lines.addAll(region.lines().stream().filter(ExportTextLine::hasText).toList());
                continue;
            }
            if (region.hasText()) {
                lines.add(new ExportTextLine(
                        region.id(),
                        region.text(),
                        region.coords(),
                        null,
                        null,
                        null,
                        List.of()
                ));
            }
        }
        return lines;
    }

    private void renderTextLine(PDPageContentStream contentStream,
                                PDFont font,
                                ExportTextLine line,
                                PageDto pageDto) throws IOException {
        String pdfText = sanitizePdfText(line.text());
        if (pdfText == null || pdfText.isBlank()) {
            return;
        }

        LinePlacement placement = computePlacement(line, pageDto);
        if (placement == null) {
            return;
        }

        float textWidthUnits = font.getStringWidth(pdfText) / 1000f;
        if (textWidthUnits <= 0.001f) {
            return;
        }

        float horizontalScale = placement.length() / textWidthUnits;
        float verticalScale = placement.fontSize();
        float cos = (float) Math.cos(placement.angleRadians());
        float sin = (float) Math.sin(placement.angleRadians());

        Matrix matrix = new Matrix(
                horizontalScale * cos,
                horizontalScale * sin,
                -verticalScale * sin,
                verticalScale * cos,
                placement.startX(),
                placement.startY()
        );

        contentStream.beginText();
        contentStream.setTextMatrix(matrix);
        contentStream.showText(pdfText);
        contentStream.endText();
    }

    private LinePlacement computePlacement(ExportTextLine line, PageDto pageDto) {
        PolygonDto baseline = line.baseline();
        if (baseline != null && baseline.points() != null && baseline.points().size() >= 2) {
            PointDto start = baseline.points().getFirst();
            PointDto end = baseline.points().getLast();
            float startX = CoordinateUtils.worldToPixelX(start.x(), pageDto.imageWidth());
            float startY = pageDto.imageHeight() - CoordinateUtils.worldToPixelY(start.y(), pageDto.imageHeight());
            float endX = CoordinateUtils.worldToPixelX(end.x(), pageDto.imageWidth());
            float endY = pageDto.imageHeight() - CoordinateUtils.worldToPixelY(end.y(), pageDto.imageHeight());

            PolygonDto.BoundingBoxDto box = line.coords() == null ? null : line.coords().getBoundingBox();
            float boxHeight = box == null
                    ? 12f
                    : Math.max(8f, Math.abs(CoordinateUtils.worldToPixelY(box.y(), pageDto.imageHeight())
                    - CoordinateUtils.worldToPixelY(box.y() + box.height(), pageDto.imageHeight())) * 0.8f);

            return new LinePlacement(
                    startX,
                    startY,
                    (float) Math.atan2(endY - startY, endX - startX),
                    (float) Math.max(1d, Math.hypot(endX - startX, endY - startY)),
                    boxHeight
            );
        }

        PolygonDto coords = line.coords();
        if (coords == null || coords.points() == null || coords.points().isEmpty()) {
            return null;
        }

        PolygonDto.BoundingBoxDto box = coords.getBoundingBox();
        float minX = CoordinateUtils.worldToPixelX(box.x(), pageDto.imageWidth());
        float maxX = CoordinateUtils.worldToPixelX(box.x() + box.width(), pageDto.imageWidth());
        float topY = CoordinateUtils.worldToPixelY(box.y() + box.height(), pageDto.imageHeight());
        float bottomY = CoordinateUtils.worldToPixelY(box.y(), pageDto.imageHeight());
        float fontSize = Math.max(8f, Math.abs(bottomY - topY) * 0.8f);

        return new LinePlacement(
                minX,
                pageDto.imageHeight() - bottomY + (fontSize * 0.1f),
                0f,
                Math.max(1f, maxX - minX),
                fontSize
        );
    }

    private BufferedImage readImage(Path imagePath) throws IOException {
        if (imagePath == null || !Files.exists(imagePath)) {
            return null;
        }
        return ImageIO.read(imagePath.toFile());
    }

    private PDType0Font loadPdfFont(PDDocument document) throws IOException {
        try (InputStream inputStream = PdfExportWriter.class.getResourceAsStream(PDF_FONT_RESOURCE_PATH)) {
            if (inputStream == null) {
                throw new IOException("Bundled PDF font not found: " + PDF_FONT_RESOURCE_PATH);
            }
            return PDType0Font.load(document, inputStream, true);
        }
    }

    private String sanitizePdfText(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("\\R+", " ").trim();
    }

    private DocumentExportDto.PdfProfile resolvePdfProfile(DocumentExportDto.PdfProfile pdfProfile) {
        return pdfProfile == null ? DocumentExportDto.PdfProfile.SEARCHABLE : pdfProfile;
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private record LinePlacement(
            float startX,
            float startY,
            float angleRadians,
            float length,
            float fontSize
    ) {
    }
}
