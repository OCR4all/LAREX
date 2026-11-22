package de.uniwue.zpd.dachs.larex.backend.service.xml;

import com.maxnth.page4j.formats.AltoReadResult;
import com.maxnth.page4j.formats.AltoXml;
import com.maxnth.page4j.formats.FineReaderXml;
import com.maxnth.page4j.formats.Format;
import com.maxnth.page4j.formats.Hocr;
import com.maxnth.page4j.formats.MeasurementUnit;
import com.maxnth.page4j.formats.ReadDiagnostic;
import com.maxnth.page4j.formats.ReadResult;
import com.maxnth.page4j.formats.SourceImage;
import com.maxnth.page4j.model.Label;
import com.maxnth.page4j.model.LabelGroup;
import com.maxnth.page4j.model.MetadataItem;
import com.maxnth.page4j.model.Page;
import com.maxnth.page4j.model.value.MetadataItemType;
import com.maxnth.page4j.model.value.PageDateTime;
import com.maxnth.page4j.pagexml.PageReadException;
import com.maxnth.page4j.pagexml.PageXml;
import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlTextDto;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.exception.InvalidPageXmlException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class OcrFormatToPageXml {

    public static final String CONVERSION_NOTE_LABEL = "conversion-note";
    public static final Set<String> FILE_EXTENSIONS = Set.of(".xml", ".hocr", ".html", ".xhtml");

    private OcrFormatToPageXml() {
    }

    public static Optional<XmlSchema> sourceFormat(byte[] xml) {
        try {
            return switch (Format.detect(xml)) {
                case ALTO -> Optional.of(XmlSchema.ALTO_XML);
                case FINE_READER -> Optional.of(XmlSchema.FINE_READER_XML);
                case HOCR -> Optional.of(XmlSchema.HOCR);
                case PAGE_XML, METS -> Optional.empty();
            };
        } catch (PageReadException e) {
            return Optional.empty();
        }
    }

    public static Optional<XmlSchema> sourceFormat(Path xmlPath) throws IOException {
        return sourceFormat(Files.readAllBytes(xmlPath));
    }

    public static boolean isConvertible(byte[] xml) {
        return sourceFormat(xml).isPresent();
    }

    public static boolean hasSupportedExtension(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return FILE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    public static String pageXmlFileName(String fileName) {
        if (fileName == null || !hasSupportedExtension(fileName) || fileName.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            return fileName;
        }
        return fileName.substring(0, fileName.lastIndexOf('.')) + ".xml";
    }

    public static String importStepName(XmlSchema format) {
        return "LAREX " + label(format) + " import";
    }

    public static String label(XmlSchema format) {
        return switch (format) {
            case ALTO_XML -> "ALTO";
            case FINE_READER_XML -> "FineReader XML";
            case HOCR -> "hOCR";
            case PAGE_XML, UNKNOWN -> format.getDisplayName();
        };
    }

    public static Converted convert(byte[] xml) {
        return convert(xml, imageFilename -> Optional.empty());
    }

    public static Converted convert(byte[] xml, Function<String, Optional<SourceImage>> pageImage) {
        XmlSchema format = sourceFormat(xml).orElseThrow(() -> new IllegalArgumentException(
                "Not an ALTO, FineReader XML or hOCR document"));
        ReadResult read = format == XmlSchema.ALTO_XML ? readAlto(xml, pageImage) : read(format, xml);
        List<String> messages = read.diagnostics().stream().map(ReadDiagnostic::message).toList();
        try {
            byte[] pageXml = PageXml.create().write(withImportStep(read.page(), format, messages),
                    PageXmlConversionService.PRIMARY_VERSION);
            return new Converted(format, pageXml, messages);
        } catch (IOException e) {
            throw invalid(format, "CONVERSION_ERROR",
                    "Could not convert " + label(format) + " to PAGE XML: " + e.getMessage());
        }
    }

    private static ReadResult readAlto(byte[] alto, Function<String, Optional<SourceImage>> pageImage) {
        AltoReadResult read = readAlto(alto, (SourceImage) null);
        if (read.measurementUnit() != MeasurementUnit.PIXEL) {
            String unit = read.measurementUnit().name().toLowerCase(Locale.ROOT);
            SourceImage image = pageImage.apply(read.page().imageFilename()).orElseThrow(() -> invalid(
                    XmlSchema.ALTO_XML,
                    "IMAGE_REQUIRED",
                    "Coordinates in " + unit + " can only be converted to pixels with the page image. "
                            + "Upload the image together with the ALTO file."));
            read = readAlto(alto, image);
            if (read.page().imageWidth() != image.width() || read.page().imageHeight() != image.height()) {
                throw invalid(XmlSchema.ALTO_XML, "UNSUPPORTED_UNIT", "Coordinates in " + unit
                        + " could not be converted to pixels: "
                        + "the page image has no resolution and its size does not match the ALTO page.");
            }
        }
        return new ReadResult(read.page(), read.diagnostics());
    }

    private static AltoReadResult readAlto(byte[] alto, SourceImage image) {
        try {
            return image == null ? AltoXml.create().read(alto) : AltoXml.create().read(alto, image);
        } catch (PageReadException e) {
            throw invalid(XmlSchema.ALTO_XML, "READ_ERROR", "Could not read ALTO XML: " + e.getMessage());
        }
    }

    private static ReadResult read(XmlSchema format, byte[] xml) {
        try {
            return format == XmlSchema.HOCR ? Hocr.create().read(xml) : FineReaderXml.create().read(xml);
        } catch (PageReadException e) {
            throw invalid(format, "READ_ERROR", "Could not read " + label(format) + ": " + e.getMessage());
        }
    }

    public static Converted convertInPlace(Path xmlPath, Function<String, Optional<SourceImage>> pageImage)
            throws IOException {
        Converted converted = convert(Files.readAllBytes(xmlPath), pageImage);
        writeAtomically(xmlPath, converted.pageXml());
        return converted;
    }

    public static void writeAtomically(Path target, byte[] content) throws IOException {
        Path tempPath = Files.createTempFile(target.toAbsolutePath().getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.write(tempPath, content);
            try {
                Files.move(tempPath, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private static Page withImportStep(Page page, XmlSchema format, List<String> messages) {
        List<LabelGroup> notes = messages.isEmpty()
                ? List.of()
                : List.of(new LabelGroup(null, null, null, null,
                        messages.stream().map(message -> new Label(message, CONVERSION_NOTE_LABEL, null)).toList()));
        List<MetadataItem> items = new ArrayList<>(page.metadata().metadataItems());
        items.add(new MetadataItem(
                MetadataItemType.PROCESSING_STEP,
                importStepName(format),
                "Converted from " + label(format) + " to PAGE XML. " + label(format)
                        + " content without a PAGE equivalent was not kept.",
                PageDateTime.now(),
                notes));
        return page.withMetadata(page.metadata().withMetadataItems(items));
    }

    private static InvalidPageXmlException invalid(XmlSchema format, String code, String message) {
        String prefix = switch (format) {
            case FINE_READER_XML -> "FINEREADER_";
            case HOCR -> "HOCR_";
            default -> "ALTO_";
        };
        return new InvalidPageXmlException(new PageXmlTextDto.XmlValidationResult(
                false,
                List.of(new PageXmlTextDto.XmlValidationError(1, 1, "error", prefix + code, message)),
                null,
                format.getNamespace()));
    }

    public record Converted(XmlSchema source, byte[] pageXml, List<String> messages) {
    }
}
