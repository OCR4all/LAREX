package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.config.ProjectPackageProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectPackageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ToolkitPackageDto;
import de.uniwue.zpd.dachs.larex.backend.service.backup.ArchiveIoService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;

@Component
public class ProjectPackageArchiveService {

    private final ArchiveIoService archiveIoService;
    private final ObjectMapper objectMapper;
    private final ProjectPackageProperties projectPackageProperties;

    public ProjectPackageArchiveService(ArchiveIoService archiveIoService,
                                        ObjectMapper objectMapper,
                                        ProjectPackageProperties projectPackageProperties) {
        this.archiveIoService = archiveIoService;
        this.objectMapper = objectMapper;
        this.projectPackageProperties = projectPackageProperties;
    }

    public void writeZip(OutputStream outputStream, ExportPackage exportPackage) throws IOException {
        archiveIoService.writeZip(outputStream, zipOut -> writeEntries(zipOut, exportPackage, ""));
    }

    public void writeZip(Path outputPath, ExportPackage exportPackage) throws IOException {
        archiveIoService.writeZip(outputPath, zipOut -> writeEntries(zipOut, exportPackage, ""));
    }

    public void writeEntries(ZipOutputStream zipOut,
                             ExportPackage exportPackage,
                             String entryPrefix) throws IOException {
        archiveIoService.writeJsonEntry(
                zipOut,
                prefixed(entryPrefix, "manifest.json"),
                exportPackage.manifest()
        );
        for (String descriptorPath : safeList(exportPackage.manifest().pages())) {
            ProjectPackageDto.PageDescriptor descriptor = exportPackage.pages().get(descriptorPath);
            if (descriptor == null) {
                throw new IllegalArgumentException("Missing page descriptor for archive path: " + descriptorPath);
            }
            archiveIoService.writeJsonEntry(zipOut, prefixed(entryPrefix, descriptorPath), descriptor);
        }
        for (Map.Entry<ToolkitPackageDto.ToolkitType, String> resource
                : exportPackage.manifest().resources().entrySet()) {
            ProjectPackageDto.ResourceDescriptor descriptor = exportPackage.resources().get(resource.getValue());
            if (descriptor == null) {
                throw new IllegalArgumentException("Missing resource descriptor for archive path: " + resource.getValue());
            }
            archiveIoService.writeJsonEntry(zipOut, prefixed(entryPrefix, resource.getValue()), descriptor);
        }
        for (BinaryEntry entry : exportPackage.binaryEntries()) {
            archiveIoService.writeStreamEntry(zipOut, prefixed(entryPrefix, entry.archivePath()), entry.writer()::write);
        }
    }

    public ImportedPackage extractAndValidate(InputStream inputStream) throws IOException {
        ProjectPackageProperties.Archive archive = projectPackageProperties.getArchive();
        ArchiveIoService.ExtractionResult extraction = archiveIoService.extractZipToTempDirWithReport(
                inputStream,
                "larex-project-import-",
                new ArchiveIoService.ExtractionLimits(
                        archive.getMaxArchiveBytes(),
                        archive.getMaxEntries(),
                        archive.getMaxEntryBytes(),
                        archive.getMaxTotalBytes(),
                        archive.getMaxCompressionRatio()
                )
        );
        Path tempDir = extraction.directory();
        try {
            return validateExtracted(tempDir, extraction.extractedBytes());
        } catch (IOException | RuntimeException e) {
            deleteRecursively(tempDir);
            throw e;
        }
    }

    private ImportedPackage validateExtracted(Path tempDir, long extractedBytes) throws IOException {
        Path manifestPath = requireDescriptorFile(tempDir, "manifest.json");
        ProjectPackageDto.PackageManifest manifest = archiveIoService.readJson(
                manifestPath,
                ProjectPackageDto.PackageManifest.class
        );
        if (!ProjectPackageDto.DEFAULT_SCHEMA_VERSION.equals(manifest.schemaVersion())) {
            throw new IllegalArgumentException(
                    "Unsupported project package schema version: " + manifest.schemaVersion()
                            + " (expected " + ProjectPackageDto.DEFAULT_SCHEMA_VERSION + ")"
            );
        }
        if (manifest.project() == null) {
            throw new IllegalArgumentException("Project package manifest is missing project metadata");
        }

        Set<String> declaredPaths = new HashSet<>();
        declaredPaths.add("manifest.json");
        Set<String> pageNames = new HashSet<>();
        List<ImportedPage> pages = new ArrayList<>();

        List<String> pageDescriptorPaths = manifest.pages() == null ? List.of() : manifest.pages();
        for (String requestedPath : pageDescriptorPaths) {
            String descriptorPath = normalizeDeclaredPath(requestedPath, "pages/", "/page.json");
            requireUnique(declaredPaths, descriptorPath, "Duplicate declared archive path: ");
            Path absoluteDescriptor = requireDescriptorFile(tempDir, descriptorPath);
            ProjectPackageDto.PageDescriptor descriptor = archiveIoService.readJson(
                    absoluteDescriptor,
                    ProjectPackageDto.PageDescriptor.class
            );
            if (descriptor.name() == null || descriptor.name().isBlank()) {
                throw new IllegalArgumentException("Page name is required in " + descriptorPath);
            }
            if (!pageNames.add(descriptor.name().trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate page name in project package: " + descriptor.name());
            }

            String pageDirectory = descriptorPath.substring(0, descriptorPath.lastIndexOf('/'));
            validatePageFiles(tempDir, pageDirectory, descriptor, declaredPaths, manifest.includesXmlHistory());
            pages.add(new ImportedPage(descriptorPath, pageDirectory, descriptor));
        }

        Map<ToolkitPackageDto.ToolkitType, ImportedResource> resources = new LinkedHashMap<>();
        Map<ToolkitPackageDto.ToolkitType, String> resourcePaths =
                manifest.resources() == null ? Map.of() : manifest.resources();
        for (Map.Entry<ToolkitPackageDto.ToolkitType, String> entry : resourcePaths.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("Project package resource type must not be null");
            }
            String resourcePath = normalizeDeclaredPath(entry.getValue(), "resources/", ".json");
            requireUnique(declaredPaths, resourcePath, "Duplicate declared archive path: ");
            ProjectPackageDto.ResourceDescriptor descriptor = archiveIoService.readJson(
                    requireDescriptorFile(tempDir, resourcePath),
                    ProjectPackageDto.ResourceDescriptor.class
            );
            if (descriptor.type() != entry.getKey()) {
                throw new IllegalArgumentException("Resource type does not match manifest entry: " + resourcePath);
            }
            if (descriptor.name() == null || descriptor.name().isBlank() || descriptor.payload() == null) {
                throw new IllegalArgumentException("Resource name and payload are required: " + resourcePath);
            }
            resources.put(entry.getKey(), new ImportedResource(resourcePath, descriptor));
        }

        rejectUndeclaredFiles(tempDir, declaredPaths);
        return new ImportedPackage(tempDir, manifest, pages, resources, extractedBytes);
    }

    private void validatePageFiles(Path tempDir,
                                   String pageDirectory,
                                   ProjectPackageDto.PageDescriptor descriptor,
                                   Set<String> declaredPaths,
                                   boolean includesXmlHistory) throws IOException {
        for (ProjectPackageDto.FileDescriptor image : safeList(descriptor.images())) {
            String path = resolvePagePath(pageDirectory, image.path(), "images/");
            validateCommonFileDescriptor(image.path(), image.fileName(), image.variant(), image.baseName(), path);
            requireUnique(declaredPaths, path, "Duplicate declared archive path: ");
            validateImage(requireFile(tempDir, path), path);
        }

        for (ProjectPackageDto.XmlFileDescriptor xml : safeList(descriptor.xml())) {
            String path = resolvePagePath(pageDirectory, xml.path(), "xml/");
            validateCommonFileDescriptor(xml.path(), xml.fileName(), xml.variant(), xml.baseName(), path);
            requireUnique(declaredPaths, path, "Duplicate declared archive path: ");
            validateXml(requireFile(tempDir, path), path);

            List<ProjectPackageDto.XmlVersionDescriptor> history = safeList(xml.history());
            if (!includesXmlHistory && !history.isEmpty()) {
                throw new IllegalArgumentException("XML history is present although includesXmlHistory is false: " + path);
            }
            Set<Integer> versionNumbers = new HashSet<>();
            for (ProjectPackageDto.XmlVersionDescriptor version : history) {
                if (version.versionNumber() == null || version.versionNumber() < 1
                        || !versionNumbers.add(version.versionNumber())) {
                    throw new IllegalArgumentException("XML history version numbers must be unique positive integers: " + path);
                }
                String historyPath = resolvePagePath(pageDirectory, version.path(), "history/");
                requireUnique(declaredPaths, historyPath, "Duplicate declared archive path: ");
                validateXml(requireFile(tempDir, historyPath), historyPath);
            }
        }
    }

    private void validateCommonFileDescriptor(String requestedPath,
                                              String fileName,
                                              String variant,
                                              String baseName,
                                              String resolvedPath) {
        if (requestedPath == null || fileName == null || fileName.isBlank()
                || variant == null || variant.isBlank() || baseName == null || baseName.isBlank()) {
            throw new IllegalArgumentException(
                    "File path, fileName, variant, and baseName are required: " + resolvedPath
            );
        }
    }

    private void validateImage(Path path, String archivePath) throws IOException {
        String lower = archivePath.toLowerCase(Locale.ROOT);
        boolean supported = lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".tif")
                || lower.endsWith(".tiff") || lower.endsWith(".webp");
        byte[] header;
        try (InputStream input = Files.newInputStream(path)) {
            header = input.readNBytes(12);
        }
        boolean validSignature = lower.endsWith(".png")
                ? startsWith(header, 0x89, 0x50, 0x4e, 0x47)
                : lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                ? startsWith(header, 0xff, 0xd8)
                : lower.endsWith(".gif")
                ? startsWith(header, 0x47, 0x49, 0x46, 0x38)
                : lower.endsWith(".bmp")
                ? startsWith(header, 0x42, 0x4d)
                : lower.endsWith(".tif") || lower.endsWith(".tiff")
                ? startsWith(header, 0x49, 0x49, 0x2a, 0x00) || startsWith(header, 0x4d, 0x4d, 0x00, 0x2a)
                : lower.endsWith(".webp")
                && startsWith(header, 0x52, 0x49, 0x46, 0x46)
                && header.length >= 12
                && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50;
        if (!supported || !validSignature) {
            throw new IllegalArgumentException("Unsupported or empty image file: " + archivePath);
        }
    }

    private boolean startsWith(byte[] value, int... prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((value[index] & 0xff) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private void validateXml(Path path, String archivePath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(true);
            factory.newDocumentBuilder().parse(path.toFile());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid XML file: " + archivePath, e);
        }
    }

    private void rejectUndeclaredFiles(Path tempDir, Set<String> declaredPaths) throws IOException {
        try (Stream<Path> paths = Files.walk(tempDir)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relative = tempDir.relativize(path).toString().replace('\\', '/');
                if ((relative.startsWith("pages/") || relative.startsWith("resources/"))
                        && !declaredPaths.contains(relative)) {
                    throw new IllegalArgumentException("Undeclared file in project package: " + relative);
                }
            }
        }
    }

    private String resolvePagePath(String pageDirectory, String relativePath, String requiredDirectory) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Page file path must not be empty");
        }
        String normalizedRelative = archiveIoService.normalizeArchivePath(relativePath);
        if (!normalizedRelative.startsWith(requiredDirectory)) {
            throw new IllegalArgumentException(
                    "Page file path must be inside " + requiredDirectory + ": " + relativePath
            );
        }
        return archiveIoService.normalizeArchivePath(pageDirectory + "/" + normalizedRelative);
    }

    private String normalizeDeclaredPath(String requestedPath, String requiredPrefix, String requiredSuffix) {
        String normalized = archiveIoService.normalizeArchivePath(requestedPath);
        if (!normalized.startsWith(requiredPrefix) || !normalized.endsWith(requiredSuffix)) {
            throw new IllegalArgumentException(
                    "Archive path must match " + requiredPrefix + "*" + requiredSuffix + ": " + requestedPath
            );
        }
        return normalized;
    }

    private Path requireFile(Path tempDir, String archivePath) {
        Path resolved = tempDir.resolve(archivePath).normalize();
        if (!resolved.startsWith(tempDir) || !Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("Declared package file is missing: " + archivePath);
        }
        return resolved;
    }

    private Path requireDescriptorFile(Path tempDir, String archivePath) throws IOException {
        Path descriptor = requireFile(tempDir, archivePath);
        long maxDescriptorBytes = projectPackageProperties.getArchive().getMaxDescriptorBytes();
        long descriptorBytes = Files.size(descriptor);
        if (descriptorBytes > maxDescriptorBytes) {
            throw new IllegalArgumentException(
                    "Package descriptor exceeds the allowed size of " + maxDescriptorBytes
                            + " bytes: " + archivePath
            );
        }
        return descriptor;
    }

    private void requireUnique(Set<String> paths, String path, String message) {
        if (!paths.add(path)) {
            throw new IllegalArgumentException(message + path);
        }
    }

    private String prefixed(String entryPrefix, String entryPath) {
        String normalizedPath = archiveIoService.normalizeArchivePath(entryPath);
        if (entryPrefix == null || entryPrefix.isBlank()) {
            return normalizedPath;
        }
        return archiveIoService.normalizeArchivePath(entryPrefix) + "/" + normalizedPath;
    }

    private <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path candidate : paths.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList()) {
                Files.deleteIfExists(candidate);
            }
        } catch (IOException ignored) {
        }
    }

    @FunctionalInterface
    public interface EntryWriter {
        void write(OutputStream outputStream) throws IOException;
    }

    public record BinaryEntry(String archivePath, long contentLength, EntryWriter writer) {
    }

    public record ExportPackage(
            ProjectPackageDto.PackageManifest manifest,
            Map<String, ProjectPackageDto.PageDescriptor> pages,
            Map<String, ProjectPackageDto.ResourceDescriptor> resources,
            List<BinaryEntry> binaryEntries
    ) {
    }

    public record ImportedPage(
            String descriptorPath,
            String directory,
            ProjectPackageDto.PageDescriptor descriptor
    ) {
        public Path resolve(Path root, String relativePath) {
            return root.resolve(directory).resolve(relativePath).normalize();
        }
    }

    public record ImportedResource(
            String path,
            ProjectPackageDto.ResourceDescriptor descriptor
    ) {
    }

    public final class ImportedPackage implements AutoCloseable {
        private final Path root;
        private final ProjectPackageDto.PackageManifest manifest;
        private final List<ImportedPage> pages;
        private final Map<ToolkitPackageDto.ToolkitType, ImportedResource> resources;
        private final long extractedBytes;

        private ImportedPackage(Path root,
                                ProjectPackageDto.PackageManifest manifest,
                                List<ImportedPage> pages,
                                Map<ToolkitPackageDto.ToolkitType, ImportedResource> resources,
                                long extractedBytes) {
            this.root = root;
            this.manifest = manifest;
            this.pages = List.copyOf(pages);
            this.resources = Map.copyOf(resources);
            this.extractedBytes = extractedBytes;
        }

        public Path root() {
            return root;
        }

        public ProjectPackageDto.PackageManifest manifest() {
            return manifest;
        }

        public List<ImportedPage> pages() {
            return pages;
        }

        public Map<ToolkitPackageDto.ToolkitType, ImportedResource> resources() {
            return resources;
        }

        public long extractedBytes() {
            return extractedBytes;
        }

        @Override
        public void close() {
            deleteRecursively(root);
        }
    }
}
