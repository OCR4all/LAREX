package de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration;

import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import de.uniwue.zpd.dachs.larex.backend.dto.AnnotationCollaborationDto;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import static org.awaitility.Awaitility.await;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AnnotationLeaseEpochSourceIntegrationTest {
    @Test
    void migrationKeepsLeaseVersionsOrderedAcrossBackendAndDatabaseRestarts() {
        try (var postgres = new PostgreSQLContainer<>("postgres:latest")) {
            postgres.start();
            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .baselineVersion("42").target("43").load();
            flyway.baseline();
            flyway.migrate();
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
            var first = service(jdbc);
            var user = new AnnotationCollaborationDto.UserSummary("owner", "owner", "Owner", null);
            var context = new AnnotationLeaseService.RoomAccessContext("workspace", "project", "page", "xml",
                    "project:page:xml", "Project", "Page", true, false, user, null);
            first.joinLease(context, "instance");
            var remembered = first.releaseLease(context, "instance");

            // A restarted service receives a higher version from the same durable sequence.
            var restarted = service(jdbc);
            var empty = restarted.getLeaseState(context);
            assertNull(empty.editor());
            assertTrue(empty.leaseEpoch() > remembered.leaseEpoch());
            var renewed = restarted.joinLease(context, "instance");
            assertEquals("owner", renewed.editor().user().id());
            assertTrue(renewed.leaseEpoch() > empty.leaseEpoch());

            // Restart PostgreSQL in the same container, preserving its data directory.
            postgres.getDockerClient().restartContainerCmd(postgres.getContainerId()).exec();
            // Docker may assign a different ephemeral host port after restarting.
            var binding = postgres.getDockerClient().inspectContainerCmd(postgres.getContainerId()).exec()
                    .getNetworkSettings().getPorts().getBindings()
                    .get(com.github.dockerjava.api.model.ExposedPort.tcp(5432))[0];
            JdbcTemplate afterRestart = new JdbcTemplate(new DriverManagerDataSource(
                    "jdbc:postgresql://" + postgres.getHost() + ":" + binding.getHostPortSpec() + "/" + postgres.getDatabaseName(),
                    postgres.getUsername(), postgres.getPassword()));
            await().atMost(Duration.ofSeconds(30)).ignoreExceptions().untilAsserted(() ->
                    assertTrue(service(afterRestart).getLeaseState(context).leaseEpoch() > renewed.leaseEpoch()));

            var readOnly = new TransactionTemplate(new DataSourceTransactionManager(afterRestart.getDataSource()));
            readOnly.setReadOnly(true);
            Long reserved = readOnly.execute(status -> {
                var epoch = new AnnotationLeaseEpochSource(afterRestart, new DataSourceTransactionManager(afterRestart.getDataSource())).nextEpoch();
                status.setRollbackOnly();
                return epoch;
            });
            assertNotNull(reserved);
            assertTrue(service(afterRestart).getLeaseState(context).leaseEpoch() > reserved);
            assertEquals(1L,
                    afterRestart.queryForObject("SELECT increment_by FROM pg_sequences WHERE sequencename = 'annotation_lease_epochs'", Long.class));
            afterRestart.execute("ALTER SEQUENCE annotation_lease_epochs RESTART WITH 9007199254740992");
            var beyondJavaScriptPrecision = service(afterRestart).joinLease(context, "instance");
            var json = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(beyondJavaScriptPrecision);
            assertTrue(json.contains("\"leaseEpoch\":\"9007199254740993\""));
        }
    }

    private AnnotationLeaseService service(JdbcTemplate jdbc) {
        var service = new AnnotationLeaseService(mock(PageService.class), mock(AuthorizationPolicyService.class),
                mock(UserService.class), mock(NotificationService.class), new AnnotationLeaseEpochSource(jdbc, new DataSourceTransactionManager(jdbc.getDataSource())));
        ReflectionTestUtils.setField(service, "leaseTtlMs", 45_000L);
        return service;
    }
}
