package de.uniwue.zpd.dachs.larex.backend.service.editor;

import de.uniwue.zpd.dachs.larex.backend.entity.EditorPreference;
import de.uniwue.zpd.dachs.larex.backend.repository.editor.EditorPreferenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class EditorPreferenceService {

    /** Upper bound for a user's serialized preferences, to keep the opaque blob from growing unchecked. */
    static final int MAX_PREFERENCES_BYTES = 64 * 1024;

    private final EditorPreferenceRepository repository;
    private final ObjectMapper objectMapper;

    public EditorPreferenceService(EditorPreferenceRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ObjectNode get(String userId) {
        return repository.findByUserId(userId)
                .map(EditorPreference::getPreferences)
                .filter(JsonNode::isObject)
                .map(preferences -> (ObjectNode) preferences.deepCopy())
                .orElseGet(objectMapper::createObjectNode);
    }

    /**
     * Merges {@code patch} into the stored preferences and returns the result. A {@code null}
     * value removes the key. Rejects the patch, leaving the stored preferences unchanged, if
     * it is not a JSON object or the merged result exceeds {@link #MAX_PREFERENCES_BYTES}.
     */
    public ObjectNode update(String userId, JsonNode patch) {
        if (patch == null || !patch.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preferences must be a JSON object");
        }

        repository.mergePreferences(
                UUID.randomUUID().toString(),
                userId,
                objectMapper.writeValueAsString(patch),
                LocalDateTime.now()
        );

        ObjectNode merged = get(userId);
        if (objectMapper.writeValueAsBytes(merged).length > MAX_PREFERENCES_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Preferences exceed the size limit");
        }
        return merged;
    }
}
