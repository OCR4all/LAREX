package de.uniwue.zpd.dachs.larex.backend.controller.editor;

import de.uniwue.zpd.dachs.larex.backend.service.editor.EditorPreferenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Stores the frontend's UI preferences as an opaque JSON object. Settings the user has never
 * changed are absent from the response; the frontend fills them with its defaults.
 */
@RestController
@RequestMapping("/editor/preferences")
public class EditorPreferenceController {

    private final EditorPreferenceService service;

    public EditorPreferenceController(EditorPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ObjectNode> get(@AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(service.get(userId));
    }

    /** Merges the given keys into the stored preferences; a {@code null} value resets that key. */
    @PutMapping
    public ResponseEntity<ObjectNode> update(
            @RequestBody JsonNode patch,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(service.update(userId, patch));
    }
}
