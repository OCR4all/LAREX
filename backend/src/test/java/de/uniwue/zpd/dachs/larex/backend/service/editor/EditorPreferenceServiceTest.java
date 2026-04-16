package de.uniwue.zpd.dachs.larex.backend.service.editor;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.controller.editor.EditorPreferenceController.EditorPreferenceDto;
import de.uniwue.zpd.dachs.larex.backend.entity.EditorPreference;
import de.uniwue.zpd.dachs.larex.backend.repository.editor.EditorPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EditorPreferenceServiceTest {

    @Mock
    private EditorPreferenceRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void updatePersistsShortcutBindingsWhenProvided() {
        EditorPreference existing = new EditorPreference("user-1");
        when(repository.findByUserId("user-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(EditorPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EditorPreferenceService service = new EditorPreferenceService(repository);
        var shortcutBindings = objectMapper.createObjectNode()
                .put("version", 1)
                .set("bindings", objectMapper.createObjectNode().putArray("redo").add("meta_y"));

        EditorPreference updated = service.update("user-1", new EditorPreferenceDto(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                shortcutBindings,
                null,
                null,
                null,
                null
        ));

        assertEquals(shortcutBindings, updated.getShortcutBindings());
        verify(repository).save(existing);
    }

    @Test
    void updateLeavesShortcutBindingsUntouchedWhenMissing() {
        EditorPreference existing = new EditorPreference("user-1");
        existing.setShortcutBindings(objectMapper.createObjectNode()
                .put("version", 1)
                .set("bindings", objectMapper.createObjectNode().putArray("save").add("meta_s")));

        when(repository.findByUserId("user-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(EditorPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EditorPreferenceService service = new EditorPreferenceService(repository);

        EditorPreference updated = service.update("user-1", new EditorPreferenceDto(
                "#fff",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertEquals("#fff", updated.getBackgroundColor());
        assertEquals(existing.getShortcutBindings(), updated.getShortcutBindings());
    }

    @Test
    void updatePersistsOnboardingStateWhenProvided() {
        EditorPreference existing = new EditorPreference("user-1");
        when(repository.findByUserId("user-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(EditorPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EditorPreferenceService service = new EditorPreferenceService(repository);
        var completion = objectMapper.createObjectNode().put("tasks-index", true).put("editor-layout", true);

        EditorPreference updated = service.update("user-1", new EditorPreferenceDto(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                completion,
                true
        ));

        assertEquals(completion, updated.getOnboardingTourCompletion());
        assertEquals(Boolean.TRUE, updated.getOnboardingToursOptedOut());
        verify(repository).save(existing);
    }

    @Test
    void getOrCreateCreatesPreferenceForMissingUser() {
        when(repository.findByUserId("missing")).thenReturn(Optional.empty());
        when(repository.save(any(EditorPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EditorPreferenceService service = new EditorPreferenceService(repository);

        EditorPreference created = service.getOrCreate("missing");

        assertEquals("missing", created.getUserId());
        assertNull(created.getShortcutBindings());
        verify(repository).save(any(EditorPreference.class));
    }
}
