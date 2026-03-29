package de.uniwue.zpd.dachs.larex.backend.service.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.IiifImportDto;
import de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.repository.importing.IiifImportJobRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IiifImportServiceTest {

    private static final String WORKSPACE_ID = "ws-1";
    private static final String PROJECT_ID = "project-1";
    private static final String USER_ID = "user-1";
    private static final long UNKNOWN_IMAGE_SIZE_ESTIMATE_BYTES = 10L * 1024L * 1024L;

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private PageRepository pageRepository;
    @Mock
    private PageImageRepository pageImageRepository;
    @Mock
    private IiifImportJobRepository iiifImportJobRepository;
    @Mock
    private WorkspaceAccessService workspaceAccessService;
    @Mock
    private WorkspaceQuotaGuardService workspaceQuotaGuardService;
    @Mock
    private AsyncIiifImportProcessor asyncIiifImportProcessor;
    @Mock
    private IiifRemoteRequestThrottler iiifRemoteRequestThrottler;
    @Mock
    private TaskExecutor previewTaskExecutor;

    private IiifImportService service;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new IiifImportService(
                projectRepository,
                pageRepository,
                pageImageRepository,
                iiifImportJobRepository,
                workspaceAccessService,
                workspaceQuotaGuardService,
                asyncIiifImportProcessor,
                iiifRemoteRequestThrottler,
                new ObjectMapper(),
                previewTaskExecutor
        );

        Library library = new Library(WORKSPACE_ID, "Library");
        project = new Project("Project", "desc", library);
        project.setId(PROJECT_ID);

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(pageRepository.findByProjectIdAndLowerNameIn(eq(PROJECT_ID), anyCollection())).thenReturn(List.of());
        when(pageImageRepository.findByPageIds(anyCollection())).thenReturn(List.of());
    }

    @Test
    void previewFromManifestFile_parsesV3ManifestWithDirectAndServiceImages() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manifest.json",
                "application/json",
                """
                {
                  "@context": "http://iiif.io/api/presentation/3/context.json",
                  "id": "https://example.org/iiif/manifest/1",
                  "type": "Manifest",
                  "label": { "en": ["Example Manifest"] },
                  "provider": [{ "label": { "en": ["Example Provider"] } }],
                  "thumbnail": [{ "id": "https://example.org/thumb.jpg" }],
                  "items": [
                    {
                      "id": "https://example.org/canvas/1",
                      "type": "Canvas",
                      "label": { "en": ["Page 1"] },
                      "items": [
                        {
                          "items": [
                            {
                              "body": {
                                "type": "Image",
                                "id": "https://example.org/images/1/full.jpg"
                              }
                            }
                          ]
                        }
                      ]
                    },
                    {
                      "id": "https://example.org/canvas/2",
                      "type": "Canvas",
                      "items": [
                        {
                          "items": [
                            {
                              "body": {
                                "service": [{ "id": "https://example.org/image-service/2" }]
                              }
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8)
        );

        IiifImportDto.PreviewResponse response = service.previewFromManifestFile(WORKSPACE_ID, PROJECT_ID, USER_ID, file);

        assertNotNull(response.previewToken());
        assertEquals("3", response.manifest().presentationVersion());
        assertEquals("Example Manifest", response.manifest().label());
        assertEquals("Example Provider", response.manifest().provider());
        assertEquals("https://example.org/thumb.jpg", response.manifest().thumbnailUrl());
        assertEquals(2, response.totalCanvases());
        assertEquals(2, response.importableCanvasCount());
        assertEquals(2, response.unknownSizeCanvasCount());
        assertEquals(2L * UNKNOWN_IMAGE_SIZE_ESTIMATE_BYTES, response.estimatedStorageBytes());

        IiifImportDto.CanvasPreview first = response.canvases().get(0);
        assertEquals("Page 1", first.pageName());
        assertEquals("https://example.org/images/1/full.jpg", first.imageUrl());

        IiifImportDto.CanvasPreview second = response.canvases().get(1);
        assertEquals("Example Manifest-0002", second.pageName());
        assertEquals("https://example.org/image-service/2/full/max/0/default.jpg", second.imageUrl());
        assertTrue(response.warnings().stream().anyMatch(warning -> warning.contains("using a default estimate")));
    }

    @Test
    void previewFromManifestFile_parsesV2ManifestAndDetectsExistingIiifConflict() throws Exception {
        Page existingPage = new Page();
        existingPage.setId("page-1");
        existingPage.setName("folio 1r");

        PageImage existingIiifImage = new PageImage();
        existingIiifImage.setVariant("iiif");

        when(pageRepository.findByProjectIdAndLowerNameIn(eq(PROJECT_ID), anyCollection())).thenReturn(List.of(existingPage));
        when(pageImageRepository.findByPageIds(anyCollection())).thenReturn(List.<Object[]>of(new Object[]{"page-1", existingIiifImage}));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manifest-v2.json",
                "application/json",
                """
                {
                  "@context": "http://iiif.io/api/presentation/2/context.json",
                  "@id": "https://example.org/iiif/manifest/2",
                  "label": "Manifest Two",
                  "sequences": [
                    {
                      "canvases": [
                        {
                          "@id": "https://example.org/canvas/1",
                          "label": "folio 1r",
                          "images": [
                            {
                              "resource": {
                                "service": { "@id": "https://example.org/image-service/1" }
                              }
                            }
                          ]
                        },
                        {
                          "@id": "https://example.org/canvas/2",
                          "label": "folio 1v",
                          "images": []
                        }
                      ]
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8)
        );

        IiifImportDto.PreviewResponse response = service.previewFromManifestFile(WORKSPACE_ID, PROJECT_ID, USER_ID, file);

        assertEquals("2", response.manifest().presentationVersion());
        assertEquals(2, response.totalCanvases());
        assertEquals(1, response.importableCanvasCount());

        IiifImportDto.CanvasPreview first = response.canvases().get(0);
        assertNotNull(first.conflict());
        assertEquals("IMAGE_VARIANT_EXISTS", first.conflict().conflictType());
        assertTrue(first.conflict().existingIiifImage());
        assertEquals("https://example.org/image-service/1/full/full/0/default.jpg", first.imageUrl());

        IiifImportDto.CanvasPreview second = response.canvases().get(1);
        assertFalse(second.importable());
        assertNull(second.imageUrl());
        assertTrue(second.warnings().stream().anyMatch(warning -> warning.contains("No downloadable canvas image")));
        assertTrue(response.warnings().stream().anyMatch(warning -> warning.contains("will be skipped")));
    }

    @Test
    void previewFromManifestFile_parsesCollectionAndFlattensMemberManifests() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "collection.json",
                "application/json",
                """
                {
                  "@context": "http://iiif.io/api/presentation/3/context.json",
                  "id": "https://example.org/iiif/collection/1",
                  "type": "Collection",
                  "label": { "en": ["Collection One"] },
                  "items": [
                    {
                      "id": "https://example.org/iiif/manifest/1",
                      "type": "Manifest",
                      "label": { "en": ["Manifest One"] },
                      "items": [
                        {
                          "id": "https://example.org/canvas/1",
                          "type": "Canvas",
                          "label": { "en": ["Page A"] },
                          "items": [
                            {
                              "items": [
                                {
                                  "body": {
                                    "type": "Image",
                                    "id": "https://example.org/images/1/full.jpg"
                                  }
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    },
                    {
                      "id": "https://example.org/iiif/manifest/2",
                      "type": "Manifest",
                      "label": { "en": ["Manifest Two"] },
                      "items": [
                        {
                          "id": "https://example.org/canvas/2",
                          "type": "Canvas",
                          "label": { "en": ["Page B"] },
                          "items": [
                            {
                              "items": [
                                {
                                  "body": {
                                    "service": [{ "id": "https://example.org/image-service/2" }]
                                  }
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8)
        );

        IiifImportDto.PreviewResponse response = service.previewFromManifestFile(WORKSPACE_ID, PROJECT_ID, USER_ID, file);

        assertEquals("COLLECTION", response.manifest().resourceType());
        assertEquals(2, response.manifest().manifestCount());
        assertEquals(2, response.totalCanvases());
        assertEquals("Manifest One", response.canvases().get(0).sourceManifestLabel());
        assertEquals("Manifest Two", response.canvases().get(1).sourceManifestLabel());
        assertNotNull(response.canvases().get(0).thumbnailUrl());
        assertTrue(response.warnings().stream().anyMatch(warning -> warning.contains("Expanded IIIF collection into 2 manifests")));
    }

    @Test
    void startImportJob_reservesEstimatedBytesForUnknownSizesAndSerializesPayloads() throws Exception {
        Page existingPage = new Page();
        existingPage.setId("page-1");
        existingPage.setName("folio 1r");

        when(pageRepository.findByProjectIdAndLowerNameIn(eq(PROJECT_ID), anyCollection())).thenReturn(List.of(existingPage));
        when(pageRepository.findPageNamesByProjectId(PROJECT_ID)).thenReturn(List.of("folio 1r"));
        when(iiifImportJobRepository.findActiveJobsForProject(eq(PROJECT_ID), anyList())).thenReturn(List.of());
        when(workspaceQuotaGuardService.reserveBytesOrThrow(eq(WORKSPACE_ID), eq(2L * UNKNOWN_IMAGE_SIZE_ESTIMATE_BYTES), eq("iiif-import-job")))
                .thenReturn(2L * UNKNOWN_IMAGE_SIZE_ESTIMATE_BYTES);

        AtomicReference<IiifImportJob> savedJob = new AtomicReference<>();
        when(iiifImportJobRepository.save(any(IiifImportJob.class))).thenAnswer(invocation -> {
            IiifImportJob job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId("job-1");
            }
            savedJob.set(job);
            return job;
        });

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manifest.json",
                "application/json",
                """
                {
                  "@context": "http://iiif.io/api/presentation/3/context.json",
                  "id": "https://example.org/iiif/manifest/3",
                  "type": "Manifest",
                  "label": { "en": ["Rename Test"] },
                  "items": [
                    {
                      "id": "canvas-1",
                      "type": "Canvas",
                      "label": { "en": ["folio 1r"] },
                      "items": [{ "items": [{ "body": { "type": "Image", "id": "https://example.org/images/1.jpg" } }] }]
                    },
                    {
                      "id": "canvas-2",
                      "type": "Canvas",
                      "label": { "en": ["folio 1v"] },
                      "items": [{ "items": [{ "body": { "type": "Image", "id": "https://example.org/images/2.jpg" } }] }]
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8)
        );

        IiifImportDto.PreviewResponse preview = service.previewFromManifestFile(WORKSPACE_ID, PROJECT_ID, USER_ID, file);

        IiifImportDto.JobResponse response = service.startImportJob(
                WORKSPACE_ID,
                PROJECT_ID,
                USER_ID,
                new IiifImportDto.StartJobRequest(
                        preview.previewToken(),
                        null,
                        List.of(new IiifImportDto.Resolution("canvas-1", "RENAME", "folio 1r copy"))
                )
        );

        assertEquals("job-1", response.id());
        assertEquals("PENDING", response.status());
        assertEquals(2L * UNKNOWN_IMAGE_SIZE_ESTIMATE_BYTES, response.estimatedStorageBytes());
        verify(workspaceQuotaGuardService).reserveBytesOrThrow(WORKSPACE_ID, 2L * UNKNOWN_IMAGE_SIZE_ESTIMATE_BYTES, "iiif-import-job");
        verify(asyncIiifImportProcessor).processImportJob("job-1");

        List<IiifJobCanvasPayload> payloads = service.readJobPayloads(savedJob.get());
        assertEquals(2, payloads.size());
        assertEquals("RENAME", payloads.get(0).action());
        assertEquals("folio 1r copy", payloads.get(0).finalPageName());
        assertNull(payloads.get(0).targetPageId());
        assertEquals("IMPORT", payloads.get(1).action());
        assertEquals("folio 1v", payloads.get(1).finalPageName());
    }

    @Test
    void startImportJob_importsOnlySelectedCanvasIds() throws Exception {
        when(pageRepository.findByProjectIdAndLowerNameIn(eq(PROJECT_ID), anyCollection())).thenReturn(List.of());
        when(pageRepository.findPageNamesByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(iiifImportJobRepository.findActiveJobsForProject(eq(PROJECT_ID), anyList())).thenReturn(List.of());
        when(workspaceQuotaGuardService.reserveBytesOrThrow(eq(WORKSPACE_ID), eq(UNKNOWN_IMAGE_SIZE_ESTIMATE_BYTES), eq("iiif-import-job")))
                .thenReturn(UNKNOWN_IMAGE_SIZE_ESTIMATE_BYTES);

        AtomicReference<IiifImportJob> savedJob = new AtomicReference<>();
        when(iiifImportJobRepository.save(any(IiifImportJob.class))).thenAnswer(invocation -> {
            IiifImportJob job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId("job-selected");
            }
            savedJob.set(job);
            return job;
        });

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manifest.json",
                "application/json",
                """
                {
                  "@context": "http://iiif.io/api/presentation/3/context.json",
                  "id": "https://example.org/iiif/manifest/selected",
                  "type": "Manifest",
                  "label": { "en": ["Selection Test"] },
                  "items": [
                    {
                      "id": "canvas-1",
                      "type": "Canvas",
                      "label": { "en": ["folio 1r"] },
                      "items": [{ "items": [{ "body": { "type": "Image", "id": "https://example.org/images/1.jpg" } }] }]
                    },
                    {
                      "id": "canvas-2",
                      "type": "Canvas",
                      "label": { "en": ["folio 1v"] },
                      "items": [{ "items": [{ "body": { "type": "Image", "id": "https://example.org/images/2.jpg" } }] }]
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8)
        );

        IiifImportDto.PreviewResponse preview = service.previewFromManifestFile(WORKSPACE_ID, PROJECT_ID, USER_ID, file);

        IiifImportDto.JobResponse response = service.startImportJob(
                WORKSPACE_ID,
                PROJECT_ID,
                USER_ID,
                new IiifImportDto.StartJobRequest(preview.previewToken(), List.of("canvas-2"), List.of())
        );

        assertEquals("job-selected", response.id());

        List<IiifJobCanvasPayload> payloads = service.readJobPayloads(savedJob.get());
        assertEquals(1, payloads.size());
        assertEquals("canvas-2", payloads.get(0).canvasId());
        assertEquals("folio 1v", payloads.get(0).finalPageName());
    }

    @Test
    void retryFailedImportJob_createsNewJobFromFailedPayloadsOnly() throws Exception {
        when(iiifImportJobRepository.findActiveJobsForProject(eq(PROJECT_ID), anyList())).thenReturn(List.of());
        when(workspaceQuotaGuardService.reserveBytesOrThrow(eq(WORKSPACE_ID), eq(UNKNOWN_IMAGE_SIZE_ESTIMATE_BYTES), eq("iiif-import-job")))
                .thenReturn(UNKNOWN_IMAGE_SIZE_ESTIMATE_BYTES);

        IiifImportJob sourceJob = new IiifImportJob();
        sourceJob.setId("job-source");
        sourceJob.setProjectId(PROJECT_ID);
        sourceJob.setWorkspaceId(WORKSPACE_ID);
        sourceJob.setSourceType(IiifImportJob.SourceType.MANIFEST_URL);
        sourceJob.setSourceReference("https://example.org/iiif/manifest/retry");
        sourceJob.setManifestSummaryJson(new ObjectMapper().writeValueAsString(new IiifImportDto.ManifestSummary(
                "https://example.org/iiif/manifest/retry",
                "https://example.org/iiif/manifest/retry",
                "MANIFEST_URL",
                "https://example.org/iiif/manifest/retry",
                "MANIFEST",
                "Retry Manifest",
                null,
                null,
                "3",
                1
        )));
        sourceJob.setWarningsJson("[]");
        sourceJob.setCanvasPayloadJson(new ObjectMapper().writeValueAsString(List.of(
                new IiifJobCanvasPayload("canvas-1", "Canvas 1", 1, "Page 1", "Page 1", "desc", "IMPORT", "https://example.org/images/1.jpg", UNKNOWN_IMAGE_SIZE_ESTIMATE_BYTES, null, "canvas-1", "{}", null, null, null, null),
                new IiifJobCanvasPayload("canvas-2", "Canvas 2", 2, "Page 2", "Page 2", "desc", "IMPORT", "https://example.org/images/2.jpg", UNKNOWN_IMAGE_SIZE_ESTIMATE_BYTES, null, "canvas-2", "{}", null, null, null, null)
        )));
        sourceJob.setResultsJson(new ObjectMapper().writeValueAsString(List.of(
                new IiifImportDto.ItemResult("canvas-1", "Canvas 1", 1, "Page 1", "Page 1", "IMPORT", "FAILED", null, "HTTP 403"),
                new IiifImportDto.ItemResult("canvas-2", "Canvas 2", 2, "Page 2", "Page 2", "IMPORT", "IMPORTED", "page-2", "Imported")
        )));

        when(iiifImportJobRepository.findByIdAndWorkspaceIdAndProjectId("job-source", WORKSPACE_ID, PROJECT_ID))
                .thenReturn(Optional.of(sourceJob));

        AtomicReference<IiifImportJob> savedJob = new AtomicReference<>();
        when(iiifImportJobRepository.save(any(IiifImportJob.class))).thenAnswer(invocation -> {
            IiifImportJob job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId("job-retry");
            }
            savedJob.set(job);
            return job;
        });

        IiifImportDto.JobResponse response = service.retryFailedImportJob(WORKSPACE_ID, PROJECT_ID, USER_ID, "job-source");

        assertEquals("job-retry", response.id());

        List<IiifJobCanvasPayload> payloads = service.readJobPayloads(savedJob.get());
        assertEquals(1, payloads.size());
        assertEquals("canvas-1", payloads.get(0).canvasId());
    }

    @Test
    void startImportJob_rejectsExpiredPreviewToken() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.startImportJob(
                        WORKSPACE_ID,
                        PROJECT_ID,
                        USER_ID,
                        new IiifImportDto.StartJobRequest("expired-token", null, List.of())
                )
        );

        assertEquals("IIIF preview has expired. Preview the manifest again.", exception.getMessage());
    }
}
