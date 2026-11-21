package de.uniwue.zpd.dachs.larex.backend.service.xml;

import com.maxnth.page4j.formats.AltoReadResult;
import com.maxnth.page4j.formats.AltoXml;
import com.maxnth.page4j.formats.Format;
import com.maxnth.page4j.formats.MeasurementUnit;
import com.maxnth.page4j.formats.ReadDiagnostic;
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
import java.util.Optional;
import java.util.function.Function;

public final class AltoToPageXml {

    public static final String IMPORT_STEP_NAME = "LAREX ALTO import";
    public static final String CONVERSION_NOTE_LABEL = "conversion-note";

    private AltoToPageXml() {
    }

    public static boolean isAlto(byte[] xml) {
        try {
            return Format.detect(xml) == Format.ALTO;
        } catch (PageReadException e) {
            return false;
        }
    }

    public static boolean isAlto(Path xmlPath) throws IOException {
        return isAlto(Files.readAllBytes(xmlPath));
    }

    public static Converted convert(byte[] alto) {
        return convert(alto, imageFilename -> Optional.empty());
    }

    public static Converted convert(byte[] alto, Function<String, Optional<SourceImage>> pageImage) {
        AltoReadResult read = read(alto, null);
        if (read.measurementUnit() != MeasurementUnit.PIXEL) {
            String unit = read.measurementUnit().name().toLowerCase();
            SourceImage image = pageImage.apply(read.page().imageFilename()).orElseThrow(() -> invalid(
                    "ALTO_IMAGE_REQUIRED",
                    "Coordinates in " + unit + " can only be converted to pixels with the page image. "
                            + "Upload the image together with the ALTO file."));
            read = read(alto, image);
            if (read.page().imageWidth() != image.width() || read.page().imageHeight() != image.height()) {
                throw invalid("ALTO_UNSUPPORTED_UNIT", "Coordinates in " + unit + " could not be converted to pixels: "
                        + "the page image has no resolution and its size does not match the ALTO page.");
            }
        }
        List<String> messages = read.diagnostics().stream().map(ReadDiagnostic::message).toList();
        try {
            byte[] pageXml = PageXml.create().write(withImportStep(read.page(), messages), PageXmlConversionService.PRIMARY_VERSION);
            return new Converted(pageXml, messages);
        } catch (IOException e) {
            throw invalid("ALTO_CONVERSION_ERROR", "Could not convert ALTO XML to PAGE XML: " + e.getMessage());
        }
    }

    private static AltoReadResult read(byte[] alto, SourceImage image) {
        try {
            return image == null ? AltoXml.create().read(alto) : AltoXml.create().read(alto, image);
        } catch (PageReadException e) {
            throw invalid("ALTO_READ_ERROR", "Could not read ALTO XML: " + e.getMessage());
        }
    }

    public static List<String> convertInPlace(Path xmlPath, Function<String, Optional<SourceImage>> pageImage)
            throws IOException {
        Converted converted = convert(Files.readAllBytes(xmlPath), pageImage);
        writeAtomically(xmlPath, converted.pageXml());
        return converted.messages();
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

    private static Page withImportStep(Page page, List<String> messages) {
        List<LabelGroup> notes = messages.isEmpty()
                ? List.of()
                : List.of(new LabelGroup(null, null, null, null,
                        messages.stream().map(message -> new Label(message, CONVERSION_NOTE_LABEL, null)).toList()));
        List<MetadataItem> items = new ArrayList<>(page.metadata().metadataItems());
        items.add(new MetadataItem(
                MetadataItemType.PROCESSING_STEP,
                IMPORT_STEP_NAME,
                "Converted from ALTO to PAGE XML. ALTO content without a PAGE equivalent was not kept.",
                PageDateTime.now(),
                notes));
        return page.withMetadata(page.metadata().withMetadataItems(items));
    }

    private static InvalidPageXmlException invalid(String code, String message) {
        return new InvalidPageXmlException(new PageXmlTextDto.XmlValidationResult(
                false,
                List.of(new PageXmlTextDto.XmlValidationError(1, 1, "error", code, message)),
                null,
                XmlSchema.ALTO_XML.getNamespace()));
    }

    public record Converted(byte[] pageXml, List<String> messages) {
    }
}
