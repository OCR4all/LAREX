package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
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
import org.springframework.stereotype.Component;

@Component
public class PdfExportWriter {

    private static final String PDF_FONT_RESOURCE_PATH = "/fonts/Junicode.ttf";
    private static final String PDF_A_ICC_RESOURCE_PATH = "/color/sRGB.icc";

    private static final float TEXT_FONT_SIZE = 11f;
    private static final float TEXT_LINE_LEADING = TEXT_FONT_SIZE * 1.4f;
    private static final float TEXT_MARGIN = 72f;

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
                    case TEXT_PAGES -> addPage(document, font, exportPage, false, true, RenderingMode.FILL);
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
        PDRectangle pageSize = drawImage
                ? new PDRectangle(exportPage.pageDto().imageWidth(), exportPage.pageDto().imageHeight())
                : PDRectangle.A4;
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
            renderText(contentStream, font, exportPage, pageSize);
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

    private void renderText(PDPageContentStream contentStream,
                            PDFont font,
                            ExportPage exportPage,
                            PDRectangle pageSize) throws IOException {
        List<String> lines = collectTextLines(exportPage);
        if (lines.isEmpty()) {
            return;
        }

        contentStream.setFont(font, TEXT_FONT_SIZE);

        float maxWidth = pageSize.getWidth() - 2 * TEXT_MARGIN;
        List<String> wrapped = wrapLines(font, lines, maxWidth);

        float y = pageSize.getHeight() - TEXT_MARGIN - TEXT_FONT_SIZE;
        contentStream.beginText();
        contentStream.newLineAtOffset(TEXT_MARGIN, y);
        for (String line : wrapped) {
            contentStream.showText(line);
            contentStream.newLineAtOffset(0, -TEXT_LINE_LEADING);
        }
        contentStream.endText();
    }

    private List<String> collectTextLines(ExportPage page) {
        List<String> lines = new ArrayList<>();
        for (ExportRegion region : page.regions()) {
            if (!region.lines().isEmpty()) {
                for (ExportTextLine line : region.lines()) {
                    addText(lines, line.text());
                }
            } else {
                addText(lines, region.text());
            }
        }
        return lines;
    }

    private void addText(List<String> lines, String text) {
        if (text == null) {
            return;
        }
        for (String part : text.split("\\R")) {
            String sanitized = sanitizePdfText(part);
            if (sanitized != null && !sanitized.isBlank()) {
                lines.add(sanitized);
            }
        }
    }

    private List<String> wrapLines(PDFont font, List<String> lines, float maxWidth) throws IOException {
        if (maxWidth <= 0f) {
            return lines;
        }
        List<String> wrapped = new ArrayList<>();
        for (String line : lines) {
            StringBuilder current = new StringBuilder();
            for (String word : line.split(" ")) {
                if (word.isEmpty()) {
                    continue;
                }
                String candidate = current.length() == 0 ? word : current + " " + word;
                if (textWidth(font, candidate) <= maxWidth) {
                    current = new StringBuilder(candidate);
                } else {
                    if (current.length() > 0) {
                        wrapped.add(current.toString());
                    }
                    current = new StringBuilder(word);
                }
            }
            if (current.length() > 0) {
                wrapped.add(current.toString());
            }
        }
        return wrapped;
    }

    private float textWidth(PDFont font, String text) throws IOException {
        return font.getStringWidth(text) / 1000f * TEXT_FONT_SIZE;
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
        return text.replaceAll("\\s+", " ").trim();
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
}
