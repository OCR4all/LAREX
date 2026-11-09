package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.repository.editor.EditorPreferenceRepository;
import de.uniwue.zpd.dachs.larex.backend.service.editor.EditorPreferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EditorPreferenceIntegrationTest {

    @Autowired private EditorPreferenceService service;
    @Autowired private EditorPreferenceRepository repository;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void getReturnsEmptyObjectWithoutCreatingRowForNewUser() {
        String userId = newUserId();

        assertThat(service.get(userId).isEmpty()).isTrue();
        assertThat(repository.findByUserId(userId)).isEmpty();
    }

    @Test
    void updateMergesKeysAndPreservesUnrelatedOnes() {
        String userId = newUserId();
        service.update(userId, json("""
                {"autoOpenJobsPanel": false, "shortcutBindings": {"version": 1, "bindings": {"save": ["meta_s"]}}}
                """));

        ObjectNode merged = service.update(userId, json("{\"backgroundColor\": \"#fff\"}"));

        assertThat(merged.path("backgroundColor").asString()).isEqualTo("#fff");
        assertThat(merged.path("autoOpenJobsPanel").isBoolean()).isTrue();
        assertThat(merged.path("autoOpenJobsPanel").asBoolean()).isFalse();
        assertThat(merged.path("shortcutBindings").path("bindings").path("save").get(0).asString()).isEqualTo("meta_s");
        assertThat(service.get(userId)).isEqualTo(merged);
    }

    @Test
    void updatePersistsKeysTheBackendDoesNotKnowAbout() {
        String userId = newUserId();

        service.update(userId, json("{\"virtualKeyboardMode\": \"docked\", \"selectedVirtualKeyboardId\": \"kb-1\"}"));

        ObjectNode stored = service.get(userId);
        assertThat(stored.path("virtualKeyboardMode").asString()).isEqualTo("docked");
        assertThat(stored.path("selectedVirtualKeyboardId").asString()).isEqualTo("kb-1");
    }

    @Test
    void updateReplacesNestedObjectsAsAWhole() {
        String userId = newUserId();
        service.update(userId, json("{\"tablePageSizes\": {\"project-pages-v2\": 50, \"workspace-datasets\": 25}}"));

        ObjectNode merged = service.update(userId, json("{\"tablePageSizes\": {\"project-pages-v2\": 100}}"));

        assertThat(merged.path("tablePageSizes")).isEqualTo(json("{\"project-pages-v2\": 100}"));
    }

    @Test
    void nullValueRemovesKeyAndKeepsNestedNulls() {
        String userId = newUserId();
        service.update(userId, json("{\"defaultVertexSize\": \"small\", \"onboardingTourCompletion\": {\"a\": null}}"));

        ObjectNode merged = service.update(userId, json("{\"defaultVertexSize\": null}"));

        assertThat(merged.has("defaultVertexSize")).isFalse();
        assertThat(merged.path("onboardingTourCompletion").has("a")).isTrue();
    }

    @Test
    void concurrentUpdatesOfDifferentKeysAreAllKept() throws Exception {
        String userId = newUserId();
        int writers = 16;
        List<Callable<ObjectNode>> tasks = new ArrayList<>();
        for (int i = 0; i < writers; i++) {
            String key = "key" + i;
            tasks.add(() -> service.update(userId, objectMapper.createObjectNode().put(key, true)));
        }

        try (var executor = Executors.newFixedThreadPool(8)) {
            for (Future<ObjectNode> future : executor.invokeAll(tasks)) {
                future.get();
            }
        }

        assertThat(service.get(userId).size()).isEqualTo(writers);
    }

    @Test
    void updateRejectsNonObjectPatch() {
        String userId = newUserId();

        assertThatThrownBy(() -> service.update(userId, objectMapper.readTree("[1, 2]")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(repository.findByUserId(userId)).isEmpty();
    }

    @Test
    void oversizedUpdateIsRejectedAndLeavesStoredPreferencesUnchanged() {
        String userId = newUserId();
        service.update(userId, json("{\"backgroundColor\": \"#fff\"}"));
        ObjectNode oversized = objectMapper.createObjectNode().put("blob", "x".repeat(70 * 1024));

        assertThatThrownBy(() -> service.update(userId, oversized))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE));
        assertThat(service.get(userId)).isEqualTo(json("{\"backgroundColor\": \"#fff\"}"));
    }

    private ObjectNode json(String value) {
        return (ObjectNode) objectMapper.readTree(value);
    }

    private static String newUserId() {
        return "pref-test-" + UUID.randomUUID();
    }
}
