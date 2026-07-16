package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationBridgeClient;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ActionRealtimePublisherTest {

    @Test
    void bridgeFailuresDoNotEscapeAndPageEventsAlsoRefreshTheRun() {
        NotificationBridgeClient bridge = mock(NotificationBridgeClient.class);
        TaskExecutor executor = mock(TaskExecutor.class);
        ActionMetrics metrics = mock(ActionMetrics.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
        doThrow(new IllegalStateException("bridge offline"))
                .when(bridge).pushActionEvent(anyString(), anyMap(), anyString());
        ActionRealtimePublisher publisher = new ActionRealtimePublisher(bridge, executor, metrics);
        ActionRun run = new ActionRun();
        run.setId("run-1");
        run.setWorkspaceId("workspace-1");
        run.setProjectId("project-1");

        assertThatCode(() -> publisher.publishPageResult(run, "page-1", Set.of("xml")))
                .doesNotThrowAnyException();

        verify(bridge, times(2)).pushActionEvent(anyString(), anyMap(), anyString());
        verify(metrics, times(2)).recordNotificationFailure();
    }
}
