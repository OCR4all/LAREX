package de.uniwue.zpd.dachs.larex.backend.service.notification;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JobRealtimePublisherTest {

    @Test
    void publishesIdentifierOnlyJobUpdateForTheRequestedUser() {
        NotificationBridgeClient bridge = mock(NotificationBridgeClient.class);
        TaskExecutor executor = mock(TaskExecutor.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));

        JobRealtimePublisher publisher = new JobRealtimePublisher(bridge, executor);
        publisher.publish("IIIF_IMPORT", "job-1", "workspace-1", "project-1", "IMPORTING", "user-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(bridge).pushJobEvent(org.mockito.ArgumentMatchers.eq("user-1"), payload.capture(), org.mockito.ArgumentMatchers.eq("job-service"));
        assertThat(payload.getValue()).containsExactly(
                Map.entry("kind", "IIIF_IMPORT"),
                Map.entry("jobId", "job-1"),
                Map.entry("workspaceId", "workspace-1"),
                Map.entry("projectId", "project-1"),
                Map.entry("status", "IMPORTING")
        );
    }
}
