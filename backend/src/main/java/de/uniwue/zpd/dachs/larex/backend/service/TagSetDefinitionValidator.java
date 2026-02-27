package de.uniwue.zpd.dachs.larex.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import de.uniwue.zpd.dachs.larex.backend.dto.TagSetDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Component
public class TagSetDefinitionValidator {

    private final Validator validator;

    public TagSetDefinitionValidator(Validator validator) {
        this.validator = validator;
    }

    public void rejectUnknownFields(JsonNode root) {
        requireObject(root, "$", Set.of("meta", "tags"));

        JsonNode meta = root.get("meta");
        requireObject(meta, "$.meta", Set.of("name", "description", "tags"));

        JsonNode tags = root.get("tags");
        if (tags == null || !tags.isArray()) {
            throw new IllegalArgumentException("$.tags must be an array");
        }

        for (int index = 0; index < tags.size(); index++) {
            validateTagNode(tags.get(index), "$.tags[" + index + "]");
        }
    }

    private void validateTagNode(JsonNode tag, String path) {
        requireObject(tag, path, Set.of("id", "title", "description", "color", "children"));

        JsonNode children = tag.get("children");
        if (children != null && !children.isNull()) {
            if (!children.isArray()) {
                throw new IllegalArgumentException(path + ".children must be an array");
            }
            for (int index = 0; index < children.size(); index++) {
                validateTagNode(children.get(index), path + ".children[" + index + "]");
            }
        }
    }

    public void validate(TagSetDto.CreateOrUpdateRequest request) {
        Set<ConstraintViolation<TagSetDto.CreateOrUpdateRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            ConstraintViolation<TagSetDto.CreateOrUpdateRequest> violation = violations.iterator().next();
            throw new IllegalArgumentException(violation.getPropertyPath() + ": " + violation.getMessage());
        }

        Set<String> ids = new HashSet<>();
        for (TagSetDto.TagNode tag : request.tags()) {
            collectAndValidateIds(tag, ids);
        }

        validateUniqueTitles(request.tags());
    }

    private void validateUniqueTitles(List<TagSetDto.TagNode> tags) {
        Set<String> titles = new HashSet<>();
        collectAndValidateTitles(tags, titles);
    }

    private void collectAndValidateTitles(List<TagSetDto.TagNode> tags, Set<String> titles) {
        for (TagSetDto.TagNode tag : tags) {
            String normalizedTitle = tag.title().trim().toLowerCase();
            if (!titles.add(normalizedTitle)) {
                throw new IllegalArgumentException("Duplicate tag title: " + tag.title());
            }
            if (tag.children() != null) {
                collectAndValidateTitles(tag.children(), titles);
            }
        }
    }

    private void collectAndValidateIds(TagSetDto.TagNode tag, Set<String> ids) {
        if (!ids.add(tag.id())) {
            throw new IllegalArgumentException("Duplicate tag id: " + tag.id());
        }
        if (tag.children() != null) {
            for (TagSetDto.TagNode child : tag.children()) {
                collectAndValidateIds(child, ids);
            }
        }
    }

    private void requireObject(JsonNode node, String path, Set<String> allowedFields) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(path + " must be an object");
        }

        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!allowedFields.contains(fieldName)) {
                throw new IllegalArgumentException("Unknown field: " + path + "." + fieldName);
            }
        }
    }

    public int countTags(List<TagSetDto.TagNode> tags) {
        if (tags == null) return 0;
        int count = 0;
        for (TagSetDto.TagNode tag : tags) {
            count += countTagsRecursive(tag);
        }
        return count;
    }

    private int countTagsRecursive(TagSetDto.TagNode tag) {
        int count = 1;
        if (tag.children() != null) {
            for (TagSetDto.TagNode child : tag.children()) {
                count += countTagsRecursive(child);
            }
        }
        return count;
    }

    public Set<String> collectAllTagIds(List<TagSetDto.TagNode> tags) {
        Set<String> ids = new HashSet<>();
        if (tags != null) {
            for (TagSetDto.TagNode tag : tags) {
                collectTagIdsRecursive(tag, ids);
            }
        }
        return ids;
    }

    private void collectTagIdsRecursive(TagSetDto.TagNode tag, Set<String> ids) {
        ids.add(tag.id());
        if (tag.children() != null) {
            for (TagSetDto.TagNode child : tag.children()) {
                collectTagIdsRecursive(child, ids);
            }
        }
    }
}
