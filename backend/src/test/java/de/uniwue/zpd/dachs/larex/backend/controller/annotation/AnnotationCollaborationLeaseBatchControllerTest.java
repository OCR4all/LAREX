package de.uniwue.zpd.dachs.larex.backend.controller.annotation;

import de.uniwue.zpd.dachs.larex.backend.dto.AnnotationCollaborationDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Dataset;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItemCopyFile;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import de.uniwue.zpd.dachs.larex.backend.service.dataset.DatasetCopyAnnotationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class AnnotationCollaborationLeaseBatchControllerTest {

    @Mock
    private AnnotationLeaseService annotationLeaseService;
    @Mock
    private DatasetCopyAnnotationService datasetCopyAnnotationService;

    private AnnotationCollaborationLeaseBatchController controller;

    @BeforeEach
    void setUp() {
        controller = new AnnotationCollaborationLeaseBatchController(
                annotationLeaseService,
                datasetCopyAnnotationService
        );
    }

    @Test
    void renewsProjectTargetsInOneBatch() {
        AnnotationCollaborationDto.LeaseRenewalTarget target =
                new AnnotationCollaborationDto.LeaseRenewalTarget(
                        AnnotationCollaborationDto.AnnotationScope.PROJECT,
                        null,
                        "project-1",
                        "page-1",
                        null,
                        null,
                        "xml-1"
                );
        AnnotationLeaseService.RoomAccessContext context = new AnnotationLeaseService.RoomAccessContext(
                "workspace-1",
                "project-1",
                "page-1",
                "xml-1",
                "project-1:page-1:xml-1",
                "Project",
                "Page",
                true,
                false,
                new AnnotationCollaborationDto.UserSummary("user-1", "user", "User", null),
                null
        );
        AnnotationCollaborationDto.LeaseState lease = new AnnotationCollaborationDto.LeaseState(
                null,
                null,
                true,
                1,
                "2099-01-01T00:00:00Z"
        );

        when(annotationLeaseService.resolveRoomAccess("project-1", "page-1", "xml-1", "user-1"))
                .thenReturn(context);
        when(annotationLeaseService.heartbeat(context, "instance-1")).thenReturn(lease);

        AnnotationCollaborationDto.LeaseRenewalBatchResponse response = controller.renew(
                new AnnotationCollaborationDto.LeaseRenewalBatchRequest("instance-1", List.of(target)),
                "user-1"
        ).getBody();

        assertEquals(1, response.renewals().size());
        assertEquals("project-1:page-1:xml-1", response.renewals().getFirst().roomKey());
        assertEquals(lease, response.renewals().getFirst().lease());
        verify(annotationLeaseService).heartbeat(context, "instance-1");
    }

    @Test
    void rejectsProjectTargetsWithoutRouteIds() {
        AnnotationCollaborationDto.LeaseRenewalTarget target =
                new AnnotationCollaborationDto.LeaseRenewalTarget(
                        AnnotationCollaborationDto.AnnotationScope.PROJECT,
                        null,
                        null,
                        "page-1",
                        null,
                        null,
                        "xml-1"
                );

        AnnotationCollaborationDto.LeaseRenewalBatchRequest request =
                new AnnotationCollaborationDto.LeaseRenewalBatchRequest("instance-1", List.of(target));

        assertThrows(IllegalArgumentException.class, () -> controller.renew(request, "user-1"));
    }

    @Test
    void renewsDatasetTargetsUsingTheirDatasetRoute() {
        AnnotationCollaborationDto.LeaseRenewalTarget target =
                new AnnotationCollaborationDto.LeaseRenewalTarget(
                        AnnotationCollaborationDto.AnnotationScope.DATASET,
                        "workspace-1",
                        null,
                        null,
                        "dataset-1",
                        "item-1",
                        "copy-xml-1"
                );
        Dataset dataset = new Dataset();
        dataset.setName("Dataset");
        DatasetItem item = new DatasetItem();
        item.setSourcePageName("Source page");
        DatasetItemCopyFile copyXml = new DatasetItemCopyFile();
        copyXml.setId("copy-xml-1");
        DatasetCopyAnnotationService.DatasetCopyXmlAccessContext datasetContext =
                new DatasetCopyAnnotationService.DatasetCopyXmlAccessContext(
                        dataset,
                        item,
                        copyXml,
                        null,
                        true,
                        false,
                        "source-project",
                        "source-page",
                        "Source project",
                        "Source page",
                        "revision-1",
                        null,
                        "dataset-1:item-1:copy-xml-1"
                );
        AnnotationCollaborationDto.UserSummary user =
                new AnnotationCollaborationDto.UserSummary("user-1", "user", "User", null);
        AnnotationCollaborationDto.LeaseState lease =
                new AnnotationCollaborationDto.LeaseState(null, null, true, 1, "2099-01-01T00:00:00Z");

        when(datasetCopyAnnotationService.resolveAccessContext(
                "workspace-1", "dataset-1", "item-1", "copy-xml-1", "user-1"
        )).thenReturn(datasetContext);
        when(annotationLeaseService.resolveUserSummary("user-1")).thenReturn(user);
        when(annotationLeaseService.heartbeat(any(), eq("instance-1"))).thenReturn(lease);

        AnnotationCollaborationDto.LeaseRenewalBatchResponse response = controller.renew(
                new AnnotationCollaborationDto.LeaseRenewalBatchRequest("instance-1", List.of(target)),
                "user-1"
        ).getBody();

        assertEquals("dataset-1:item-1:copy-xml-1", response.renewals().getFirst().roomKey());
        ArgumentCaptor<AnnotationLeaseService.RoomAccessContext> contextCaptor =
                ArgumentCaptor.forClass(AnnotationLeaseService.RoomAccessContext.class);
        verify(annotationLeaseService).heartbeat(contextCaptor.capture(), eq("instance-1"));
        assertEquals("source-project", contextCaptor.getValue().projectId());
        assertEquals("source-page", contextCaptor.getValue().pageId());
    }
}
