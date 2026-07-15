package de.uniwue.zpd.dachs.larex.backend.controller.project;

import de.uniwue.zpd.dachs.larex.backend.dto.ProjectTransferDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ProjectTransferRequest;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectTransferService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTransferControllerTest {

    @Mock
    private ProjectTransferService projectTransferService;

    @Test
    void bulkTransferReportsSuccessfulAndFailedProjectIds() {
        ProjectTransferController controller = new ProjectTransferController(projectTransferService);
        ProjectTransferRequest created = mock(ProjectTransferRequest.class);
        when(created.getProjectId()).thenReturn("project-1");
        when(projectTransferService.requestProjectTransfers(
                List.of("project-1", "project-2"),
                "target-workspace",
                "user-1",
                "Please copy",
                ProjectTransferRequest.TransferType.COPY
        )).thenReturn(List.of(created));
        ProjectTransferDto.Response response = new ProjectTransferDto.Response(
                "request-1", "project-1", "Project one", "source-workspace", "Source",
                "target-workspace", "Target", "user-1", null,
                ProjectTransferRequest.Status.PENDING, ProjectTransferRequest.TransferType.COPY,
                "Please copy", null, null, null
        );
        when(projectTransferService.toResponses(List.of(created))).thenReturn(List.of(response));

        ResponseEntity<ProjectTransferDto.BatchCreateResponse> result = controller.requestTransfers(
                new ProjectTransferDto.BatchCreateRequest(
                        List.of("project-1", "project-2", "project-1"),
                        "target-workspace",
                        "Please copy",
                        ProjectTransferRequest.TransferType.COPY
                ),
                "user-1"
        );

        assertEquals(201, result.getStatusCode().value());
        assertEquals(1, result.getBody().successCount());
        assertEquals(1, result.getBody().failedCount());
        assertEquals(List.of("project-2"), result.getBody().failedProjectIds());
        verify(projectTransferService).requestProjectTransfers(
                List.of("project-1", "project-2"),
                "target-workspace",
                "user-1",
                "Please copy",
                ProjectTransferRequest.TransferType.COPY
        );
    }
}
