package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.config.ProjectPackageProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectPackageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ToolkitPackageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.service.backup.ArchiveIoService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectPackageArchiveServiceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final ArchiveIoService archiveIoService = new ArchiveIoService(objectMapper);
    private final ProjectPackageArchiveService service =
            new ProjectPackageArchiveService(
                    archiveIoService,
                    objectMapper,
                    new ProjectPackageProperties()
            );

    @Test
    void writesAndValidatesReadablePackage() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeZip(output, validPackage(false, List.of()));

        try (ProjectPackageArchiveService.ImportedPackage imported =
                     service.extractAndValidate(new ByteArrayInputStream(output.toByteArray()))) {
            assertEquals("Project", imported.manifest().project().name());
            assertEquals("Page 1", imported.pages().getFirst().descriptor().name());
            assertEquals("images/page.png", imported.pages().getFirst().descriptor().images().getFirst().path());
            assertTrue(imported.resources().containsKey(ToolkitPackageDto.ToolkitType.CODEC));
        }
    }

    @Test
    void rejectsUnsupportedSchemaBeforeImport() throws Exception {
        ProjectPackageArchiveService.ExportPackage valid = validPackage(false, List.of());
        ProjectPackageDto.PackageManifest unsupported = new ProjectPackageDto.PackageManifest(
                "2.0",
                valid.manifest().exportedAt(),
                valid.manifest().targetPageXmlVersion(),
                false,
                valid.manifest().project(),
                valid.manifest().pages(),
                valid.manifest().resources(),
                List.of()
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeZip(output, new ProjectPackageArchiveService.ExportPackage(
                unsupported,
                valid.pages(),
                valid.resources(),
                valid.binaryEntries()
        ));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.extractAndValidate(new ByteArrayInputStream(output.toByteArray()))
        );
        assertTrue(error.getMessage().contains("Unsupported project package schema version"));
    }

    @Test
    void rejectsHistoryWhenManifestDeclaresWorkingPackage() throws Exception {
        ProjectPackageDto.XmlVersionDescriptor version = new ProjectPackageDto.XmlVersionDescriptor(
                1,
                "history/page/000001.xml",
                "user",
                "snapshot",
                LocalDateTime.now()
        );
        ProjectPackageArchiveService.ExportPackage invalid = validPackage(false, List.of(version));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeZip(output, invalid);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.extractAndValidate(new ByteArrayInputStream(output.toByteArray()))
        );
        assertTrue(error.getMessage().contains("includesXmlHistory is false"));
    }

    @Test
    void rejectsUndeclaredFilesUnderStructuredDirectories() throws Exception {
        ProjectPackageArchiveService.ExportPackage valid = validPackage(false, List.of());
        List<ProjectPackageArchiveService.BinaryEntry> entries =
                new java.util.ArrayList<>(valid.binaryEntries());
        entries.add(new ProjectPackageArchiveService.BinaryEntry(
                "pages/page/images/undeclared.png",
                1,
                out -> out.write(1)
        ));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeZip(output, new ProjectPackageArchiveService.ExportPackage(
                valid.manifest(),
                valid.pages(),
                valid.resources(),
                entries
        ));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.extractAndValidate(new ByteArrayInputStream(output.toByteArray()))
        );
        assertTrue(error.getMessage().contains("Undeclared file"));
    }

    @Test
    void rejectsArchiveEntriesThatNormalizeToTheSamePath() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write("{}".getBytes());
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("./manifest.json"));
            zip.write("{}".getBytes());
            zip.closeEntry();
        }

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.extractAndValidate(new ByteArrayInputStream(output.toByteArray()))
        );
        assertTrue(error.getMessage().contains("Duplicate archive entry"));
    }

    @Test
    void appliesConfiguredExtractionLimitsBeforePackageValidation() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeZip(output, validPackage(false, List.of()));
        ProjectPackageProperties properties = new ProjectPackageProperties();
        properties.getArchive().setMaxEntryBytes(4);
        ProjectPackageArchiveService constrainedService = new ProjectPackageArchiveService(
                archiveIoService,
                objectMapper,
                properties
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> constrainedService.extractAndValidate(
                        new ByteArrayInputStream(output.toByteArray())
                )
        );

        assertTrue(error.getMessage().contains("entry exceeds the allowed size"));
    }

    @Test
    void rejectsOversizedJsonDescriptorsBeforeParsing() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeZip(output, validPackage(false, List.of()));
        ProjectPackageProperties properties = new ProjectPackageProperties();
        properties.getArchive().setMaxDescriptorBytes(4);
        ProjectPackageArchiveService constrainedService = new ProjectPackageArchiveService(
                archiveIoService,
                objectMapper,
                properties
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> constrainedService.extractAndValidate(
                        new ByteArrayInputStream(output.toByteArray())
                )
        );

        assertTrue(error.getMessage().contains("descriptor exceeds the allowed size"));
    }

    private ProjectPackageArchiveService.ExportPackage validPackage(
            boolean includesXmlHistory,
            List<ProjectPackageDto.XmlVersionDescriptor> history) {
        String pageDescriptorPath = "pages/page/page.json";
        ProjectPackageDto.PageDescriptor page = new ProjectPackageDto.PageDescriptor(
                "Page 1",
                "Description",
                List.of("tag"),
                false,
                null,
                Page.WorkflowState.OPEN,
                null,
                List.of(new ProjectPackageDto.FileDescriptor(
                        "images/page.png",
                        "page.png",
                        "original",
                        "page"
                )),
                List.of(new ProjectPackageDto.XmlFileDescriptor(
                        "xml/page.xml",
                        "page.xml",
                        "original",
                        "page",
                        history
                ))
        );
        ProjectPackageDto.ProjectSnapshot project = new ProjectPackageDto.ProjectSnapshot(
                "Project",
                "Description",
                List.of(),
                false,
                null,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                0,
                List.of(1)
        );
        String resourcePath = "resources/codec.json";
        ProjectPackageDto.PackageManifest manifest = new ProjectPackageDto.PackageManifest(
                ProjectPackageDto.DEFAULT_SCHEMA_VERSION,
                LocalDateTime.now(),
                "2019-07-15",
                includesXmlHistory,
                project,
                List.of(pageDescriptorPath),
                Map.of(ToolkitPackageDto.ToolkitType.CODEC, resourcePath),
                List.of()
        );
        ProjectPackageDto.ResourceDescriptor resource = new ProjectPackageDto.ResourceDescriptor(
                ToolkitPackageDto.ToolkitType.CODEC,
                "Codec",
                objectMapper.createObjectNode().put("name", "Codec")
        );
        List<ProjectPackageArchiveService.BinaryEntry> binaries = new java.util.ArrayList<>();
        binaries.add(new ProjectPackageArchiveService.BinaryEntry(
                "pages/page/images/page.png",
                8,
                out -> out.write(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})
        ));
        binaries.add(new ProjectPackageArchiveService.BinaryEntry(
                "pages/page/xml/page.xml",
                8,
                out -> out.write("<PcGts/>".getBytes())
        ));
        for (ProjectPackageDto.XmlVersionDescriptor version : history) {
            binaries.add(new ProjectPackageArchiveService.BinaryEntry(
                    "pages/page/" + version.path(),
                    8,
                    out -> out.write("<PcGts/>".getBytes())
            ));
        }
        return new ProjectPackageArchiveService.ExportPackage(
                manifest,
                Map.of(pageDescriptorPath, page),
                Map.of(resourcePath, resource),
                List.copyOf(binaries)
        );
    }
}
