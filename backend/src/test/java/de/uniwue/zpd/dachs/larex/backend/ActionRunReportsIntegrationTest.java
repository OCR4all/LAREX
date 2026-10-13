package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.exception.ActionPageResultRejectedException;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunPageResultRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunRepository;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionRunService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ActionRunReportsIntegrationTest {
    @Autowired ActionRunService actions;
    @Autowired ActionProcessorDefinitionRepository definitions;
    @Autowired ActionRunRepository runs;
    @Autowired ActionRunPageResultRepository pages;
    @MockitoBean GlobalAdminService globalAdmin;
    @MockitoBean WorkspaceAccessService access;

    @Test
    void rejectedPageCommitsBefore422AndLaterCompletionRetainsItsFailure() throws Exception {
        ActionProcessorDefinition definition = new ActionProcessorDefinition();
        definition.setProcessorKey("reports-" + UUID.randomUUID());
        definition.setName("Report integration test");
        definition.setYamlSource("");
        definition.setParsedJson("{}");
        definition.setEndpointUrl("http://processor/dispatch");
        definition.setCreatedByUserId("admin");
        definition.setUpdatedByUserId("admin");
        definition.setOutputsXml(true);
        definition = definitions.saveAndFlush(definition);

        String secret = "report-test-secret";
        ActionRun run = new ActionRun();
        run.setProcessorDefinition(definition);
        run.setWorkspaceId("workspace");
        run.setProjectId("project");
        run.setCreatedByUserId("admin");
        run.setPageIdsJson("[\"bad\",\"good\"]");
        run.setStatus(ActionRun.Status.RUNNING);
        run.setSecretHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8))));
        run.setSecretPrefix("report-test");
        run.setSecretExpiresAt(LocalDateTime.now().plusMinutes(10));
        String id = runs.saveAndFlush(run).getId();
        var files = new LinkedMultiValueMap<String, MultipartFile>();
        files.add("xml", new MockMultipartFile("xml", "invalid.xml", "application/xml", "<broken".getBytes(StandardCharsets.UTF_8)));
        var invalid = new ActionDto.ResultManifest(1, "running", null, "bad",
                List.of(new ActionDto.ResultFile("xml", "bad", "xml", null, "invalid.xml")), List.of());

        for (int replay = 0; replay < 2; replay++) {
            assertThatThrownBy(() -> actions.receiveResults(id, "Bearer " + secret, invalid, files))
                    .isInstanceOf(ActionPageResultRejectedException.class);
            assertThat(pages.findByRunIdAndPageId(id, "bad")).get()
                    .extracting(result -> result.isValidationRejected()).isEqualTo(true);
            assertThat(runs.findById(id)).get().extracting(ActionRun::getStatus).isEqualTo(ActionRun.Status.RUNNING);
        }
        actions.receiveResults(id, "Bearer " + secret,
                new ActionDto.ResultManifest(1, "running", null, "good", List.of(), List.of()), new LinkedMultiValueMap<>());
        var completed = actions.receiveResults(id, "Bearer " + secret,
                new ActionDto.ResultManifest(1, "completed", null, null, List.of(), List.of()), new LinkedMultiValueMap<>());
        assertThat(completed.status()).isEqualTo(ActionRun.Status.COMPLETED_WITH_ISSUES);
        assertThat(completed.reportCounts()).isEqualTo(new ActionDto.ReportCounts(2, 1, 1, 0, 0, 0));
        assertThat(pages.countByRunId(id)).isEqualTo(2);
        assertThat(runs.findById(id)).get().extracting(ActionRun::getStatus).isEqualTo(ActionRun.Status.COMPLETED_WITH_ISSUES);
    }
}
