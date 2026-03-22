package de.uniwue.zpd.dachs.larex.backend.service.xml;

import com.maxnth.page4j.dla.page.Page;
import com.maxnth.page4j.dla.page.layout.converter.ConversionMessage;
import com.maxnth.page4j.dla.page.io.xml.PageXmlInputOutput;
import com.maxnth.page4j.io.FormatModel;
import com.maxnth.page4j.io.UnsupportedFormatVersionException;
import com.maxnth.page4j.io.xml.XmlFormatVersion;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PageXmlConversionService {

    public static final String PRIMARY_PAGE_VERSION = "2019-07-15";
    private static final XmlFormatVersion PRIMARY_FORMAT_VERSION = new XmlFormatVersion(PRIMARY_PAGE_VERSION);
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    private static final Set<String> SUPPORTED_EXPORT_VERSIONS = Set.of(
            "2010-03-19",
            "2013-07-15",
            "2016-07-15",
            "2017-07-15",
            "2018-07-15",
            "2019-07-15"
    );

    public List<String> getSupportedExportVersions() {
        return SUPPORTED_EXPORT_VERSIONS.stream().sorted().toList();
    }

    public String normalizeTargetVersion(String requestedVersion) {
        if (requestedVersion == null || requestedVersion.isBlank()) {
            return PRIMARY_PAGE_VERSION;
        }
        String normalized = requestedVersion.trim();
        Matcher matcher = VERSION_PATTERN.matcher(normalized);
        if (matcher.find()) {
            normalized = matcher.group(1);
        }
        if (!SUPPORTED_EXPORT_VERSIONS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported PAGE XML target version: " + normalized);
        }
        return normalized;
    }

    public boolean isLegacyTargetVersion(String requestedVersion) {
        String normalized = normalizeTargetVersion(requestedVersion);
        return !PRIMARY_PAGE_VERSION.equals(normalized);
    }

    public String detectPageVersion(Path xmlPath) throws IOException {
        Page page = readPage(xmlPath);
        return extractVersion(page.getFormatVersion() != null ? page.getFormatVersion().toString() : null);
    }

    public boolean isOlderThanPrimary(String version) {
        try {
            return new XmlFormatVersion(version).isOlderThan(PRIMARY_FORMAT_VERSION);
        } catch (Exception e) {
            return false;
        }
    }

    public ConversionOutcome convertFileInPlace(Path xmlPath, String targetVersion) throws IOException {
        String normalizedTarget = normalizeTargetVersion(targetVersion);
        Page page = readPage(xmlPath);
        String sourceVersion = extractVersion(page.getFormatVersion() != null ? page.getFormatVersion().toString() : null);

        if (normalizedTarget.equals(sourceVersion)) {
            return new ConversionOutcome(sourceVersion, normalizedTarget, false, List.of());
        }

        List<String> messages = convertPageToVersion(page, normalizedTarget);
        Path tempPath = Files.createTempFile(xmlPath.getParent(), xmlPath.getFileName().toString(), ".tmp");
        try {
            writePage(page, tempPath, normalizedTarget);
            replaceAtomically(tempPath, xmlPath);
        } finally {
            Files.deleteIfExists(tempPath);
        }
        return new ConversionOutcome(sourceVersion, normalizedTarget, true, messages);
    }

    public byte[] convertFileToVersion(Path xmlPath, String targetVersion) throws IOException {
        String normalizedTarget = normalizeTargetVersion(targetVersion);
        Page page = readPage(xmlPath);
        String sourceVersion = extractVersion(page.getFormatVersion() != null ? page.getFormatVersion().toString() : null);

        if (normalizedTarget.equals(sourceVersion)) {
            return Files.readAllBytes(xmlPath);
        }

        convertPageToVersion(page, normalizedTarget);
        Path tempPath = Files.createTempFile(xmlPath.getParent(), xmlPath.getFileName().toString(), ".tmp-export");
        try {
            writePage(page, tempPath, normalizedTarget);
            return Files.readAllBytes(tempPath);
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private Page readPage(Path xmlPath) throws IOException {
        if (!Files.exists(xmlPath)) {
            throw new IOException("XML file not found: " + xmlPath);
        }
        try {
            Page page = PageXmlInputOutput.readPage(xmlPath.toString());
            if (page == null) {
                throw new IOException("Failed to parse PAGE XML file: " + xmlPath + " (parser returned null)");
            }
            return page;
        } catch (UnsupportedFormatVersionException e) {
            throw new IOException("Unsupported PAGE XML schema version in file: " + xmlPath, e);
        } catch (Exception e) {
            throw new IOException("Failed to parse PAGE XML file: " + xmlPath, e);
        }
    }

    private List<String> convertPageToVersion(Page page, String targetVersion) throws IOException {
        try {
            FormatModel targetModel = PageXmlInputOutput.getInstance().getFormatModel(new XmlFormatVersion(targetVersion));
            List<ConversionMessage> messages = page.setFormatVersion(targetModel);
            if (messages == null || messages.isEmpty()) {
                return List.of();
            }
            return messages.stream()
                    .map(message -> message.type() + ": " + message.text())
                    .collect(Collectors.toList());
        } catch (UnsupportedFormatVersionException e) {
            throw new IOException("Unsupported PAGE XML target version during conversion: " + targetVersion, e);
        } catch (Exception e) {
            throw new IOException("Could not convert to target XML schema format " + targetVersion, e);
        }
    }

    private void writePage(Page page, Path outputPath, String targetVersion) throws IOException {
        try {
            if (!PageXmlInputOutput.writePage(page, outputPath.toString())) {
                throw new IOException("Error writing target PAGE XML file for version " + targetVersion);
            }
        } catch (Exception e) {
            throw new IOException("Could not save target PAGE XML file: " + outputPath, e);
        }
    }

    private void replaceAtomically(Path tempPath, Path targetPath) throws IOException {
        try {
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String extractVersion(String rawVersion) {
        if (rawVersion == null || rawVersion.isBlank()) {
            return PRIMARY_PAGE_VERSION;
        }
        Matcher matcher = VERSION_PATTERN.matcher(rawVersion);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return rawVersion.trim();
    }

    public record ConversionOutcome(
            String sourceVersion,
            String targetVersion,
            boolean converted,
            List<String> messages
    ) {
    }
}
